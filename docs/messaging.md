# Consumo de eventos de Business

## Contrato revisado

Fuente: [Business feature/business, docs/contracts.md](https://github.com/1ASI0728-2620-9052-FoodNode/Foodly-Business-Service/blob/feature/business/docs/contracts.md),
revisado el 24 de noviembre de 2026; blob `794cb8119534af6d7bac1fdf22ecc547b0e1e152`.
También se contrastó el publicador local de Business, sin modificarlo ni acceder a sus secretos.

| `type` JSON | Routing key |
| --- | --- |
| `HuariqueRegistrado` | `business.HuariqueRegistrado` |
| `PerfilDelHuariqueActualizado` | `business.PerfilDelHuariqueActualizado` |
| `EstadoAbiertoCerradoCambiado` | `business.EstadoAbiertoCerradoCambiado` |

Exchange existente: `foodly.business`, topic durable. El prefijo `business.` pertenece
a la routing key, no al valor JSON de `type`. Envelope: `eventId`, `type`,
`schemaVersion`, `huariqueId`, `aggregateVersion`, `occurredAt`, `data`.
Business usa UUID, schemaVersion entero 1, versión de agregado long no negativa
(inicia en 0), fecha ISO instant y snapshot completo con `data.id` y `data.version`.
`messageId` debe corresponder al eventId. Se verifican esas correspondencias,
estructura JSON, campos obligatorios, tipos, routing key y versión del esquema;
se rechazan claves JSON duplicadas y contenido adicional tras el documento.
Los campos del catálogo no utilizados pueden evolucionar sin copiarse al modelo local.
No se consumen eventos de menú: tienen otra secuencia de versiones.

## Alcance de la proyección

No existe una regla acordada que obligue a rechazar reseñas/favoritos si el huarique
no aparece localmente. No se cambia ese comportamiento del dominio ni los handlers.
La integración mantiene solamente una referencia técnica de huariques conocidos
y su checkpoint para aplicar versiones de forma segura. No replica propietario,
ubicación, perfil, menú ni estado abierto/cerrado. Tampoco expone otra API.

Colecciones nuevas, exclusivamente en `foodly-community`:

- `community_known_huariques`: `_id` = huariqueId String, aggregateVersion,
  lastEventId, lastEventType y occurredAt ISO.
- `community_business_inbox`: `_id` = eventId String, huariqueId, type,
  aggregateVersion, outcome (`APPLIED` o `OBSOLETE`) y processedAt ISO UTC.

Los índices únicos `_id` nativos de MongoDB protegen cada identidad. No hay TTL:
eliminar el inbox sin un acuerdo de retención/replay invalidaría la deduplicación.
No se almacena el payload completo ni se duplican promedios de reputación.

## Atomicidad, orden y recuperación

`MongoBusinessEventStore` usa MongoTransactionManager con readConcern SNAPSHOT
y writeConcern MAJORITY. Comprueba que MongoDB es un **replica set** antes de
declarar la topología Rabbit o arrancar el consumidor. Un standalone se rechaza.
Las colecciones se crean fuera de transacción; proyección e inbox comparten commit.

1. Si el eventId ya existe, se confirma como duplicado; una colisión de identidad
   con otro agregado/tipo/versión es inválida y termina en DLQ.
2. Si llega una versión estrictamente mayor, se actualiza el checkpoint.
3. Versiones iguales o inferiores no modifican la proyección y se registran como
   `OBSOLETE`. Son entregas terminales válidas, con ACK tras commit.
4. Se inserta el inbox dentro de la misma transacción. Si falla, se revierte también
   la proyección. Un error de commit no se interpreta como éxito.

El contrato transporta snapshots completos: se admite recibir primero la versión
8 y después el registro versión 0. No se espera un hueco ni se reconstruyen deltas.
Una carrera Mongo genera conflicto/reintento, nunca un ACK por capturar simplemente
DuplicateKeyException. Si el commit ocurrió y se perdió su respuesta o el ACK,
la siguiente entrega encuentra el inbox persistido. El reinicio del consumidor
no borra el checkpoint ni el contador de entregas del broker. No se promete entrega
exactamente una vez; se toleran duplicados con efectos idempotentes.

## Topología propia y reintentos

| Recurso Community | Configuración |
| --- | --- |
| `foodly.community.business.events` | Cola durable quorum, los tres bindings de la tabla anterior |
| `foodly.community.business.dlx` | Exchange direct durable |
| `foodly.community.business.dlq` | Cola durable quorum, enlazada al DLX con `business.dead` |

El exchange Business se comprueba con declaración **pasiva**. No se redeclara ni
modifica. No se accede a colas Business/Radar. Si el exchange no existe, el arranque
falla. Una cola preexistente incompatible también provoca fallo, sin borrarla.

El listener tiene ACK manual, prefetch 1 y un consumidor por instancia. ACK solo
después de completar el store. JSON/esquema inválidos se rechazan sin requeue.
Errores de persistencia se reintentan con NACK/requeue y pausa configurable.
Se usa el contador persistente del quorum `x-delivery-count`, no una cuenta en memoria.
Por defecto son 3 intentos totales y 250 ms entre intentos. Al agotar se rechaza
sin requeue. `x-delivery-limit=maxAttempts-1` limita también ciclos de caída antes
de que el proceso pueda emitir NACK. Un cierre sin ACK puede consumir un intento.

La cola configura `x-dead-letter-strategy=at-least-once` y `x-overflow=reject-publish`:
el quorum retiene los dead letters hasta la confirmación del destino. Requiere una
versión de RabbitMQ con soporte de estas opciones (comprobada con 4.2.9). La DLQ
no tiene consumidor ni replay automático. El broker puede entregar duplicados
también en dead lettering. No hay republish desde el listener ni ventana de pérdida
entre publicar a una cola de reintentos y confirmar el original.

## Activación explícita

El valor inicial es `COMMUNITY_BUSINESS_EVENTS_ENABLED=false`. No se crea cliente,
listener, colección ni conexión por esta integración cuando está apagada. Las
autoconfiguraciones genéricas de Rabbit siguen excluidas; la configuración propia
registra exactamente sus dependencias al activarla.

Para un entorno local de integración previamente provisionado:

1. Proporcionar MongoDB **replica set**, URI externa y base `foodly-community`;
   activar `COMMUNITY_MONGODB_ENABLED=true`.
2. Proporcionar un vhost aislado con `foodly.business` creado por su propietario,
   usuario autorizado y las variables `RABBITMQ_HOST`, `RABBITMQ_PORT`,
   `RABBITMQ_USERNAME`, `RABBITMQ_PASSWORD`, `RABBITMQ_VIRTUAL_HOST`.
   No hay fallback guest/localhost. Configurar `RABBITMQ_SSL_ENABLED=true` cuando
   corresponda: se valida certificado con el truststore JVM y nombre de host.
3. Establecer `COMMUNITY_BUSINESS_EVENTS_ENABLED=true`.
   Opcionales: `COMMUNITY_BUSINESS_MAX_ATTEMPTS=3` (mínimo 1),
   `COMMUNITY_BUSINESS_RETRY_DELAY_MS=250` (mínimo 0).
4. Ejecutar `./mvnw.cmd spring-boot:run`. El servicio conserva el puerto 8083.

`.env.example` solo documenta variables: Spring no carga automáticamente ese archivo.
Los valores sensibles deben inyectarse en el entorno, sin guardarlos en Git.
Habilitar este consumidor interno no habilita REST: se conservan los requisitos
JWT/Mongo y el contrato HS256 existente. No se modificó la seguridad HTTP.

## Pruebas y límites pendientes

JUnit/Mockito comprueban parser, orden store/ACK y decisiones de error. Las pruebas
integradas usan un MongoDB temporal con replica set y un RabbitMQ temporal real;
se ejercitan transacciones, rollback con fallo de inbox inyectado, deduplicación,
carreras, orden, routing, contador real de reentregas, DLQ y recuperación cerrando
un canal sin ACK después de commit y recreando el store. El contexto Spring se
compone contra ambos procesos reales. Los fallos de Mongo se inyectan: no se
afirma haber probado una partición de red, failover multimiembro o caída eléctrica.
Los detalles y resultados finales están en [verification.md](verification.md).

Testcontainers es la primera opción. Sin Docker se exige mongod ejecutable y un
runtime portable de Rabbit/Erlang en `.cache/rabbit-runtime` (o propiedad
`community.test.rabbit.runtime`). Este último contiene `erlang/` y
`rabbitmq_server-4.2.9/`. Las pruebas no descargan ejecutables ni usan credenciales
operativas. Crean rutas temporales, nodo/cookie/usuarios efímeros y puertos propios
de AMQP, distribución y EPMD; detienen únicamente los procesos que crean. No se
adjuntan a brokers/BD existentes. Falta de runtime provoca fallo, no test omitido.

Pendientes operativos: aprobación de permisos/topología en el entorno destino,
capacidad/disponibilidad del quorum y replica set, alertas y procedimiento de replay
de DLQ, retención del inbox y carga inicial/reconciliación de huariques anteriores
a la creación de la cola. El reinicio completo del broker/cluster y failover quedan
fuera de la evidencia de esta entrega.

`RatingUpdated` **no se publica**: no existe un contrato público acordado disponible
en los requisitos/documentación revisados, y el informe íntegro no está presente.
Faltan esquema, destinatarios, exchange/routing y garantías. No se inventan un
publicador, outbox Community o consumidores externos.
