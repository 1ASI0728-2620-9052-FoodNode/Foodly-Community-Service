# Persistencia MongoDB de Community

## Activación explícita

`MongoPersistenceConfiguration` registra los adaptadores solo al habilitarse.
Domain, Application, sus interfaces y los controladores REST no cambian.

| Variable | Valor inicial | Uso |
| --- | --- | --- |
| `COMMUNITY_MONGODB_ENABLED` | `false` | Solo `true` activa persistencia |
| `MONGODB_URI` | vacío | URI externa del entorno configurado; sin fallback local |
| `MONGODB_DATABASE` | `foodly-community` | Base exclusiva de Community |

La configuración exige `foodly-community`, rechaza `foodly_business` y cualquier
base distinta. Si la URI contiene una base, debe coincidir; también puede omitirla.
Estas validaciones ocurren antes de crear MongoClient. No se incluyen credenciales.
Las autoconfiguraciones MongoDB genéricas permanecen excluidas: la configuración
propia crea/cierra el cliente y registra MongoTemplate y ambos adaptadores. No hace
falta retirar las exclusiones para activar esta composición explícita.

Cada adaptador asegura sus índices antes de quedar disponible. La activación sí
requiere un servidor accesible y permiso para crear índices; si falla, falla el
arranque. Con el flag false no hay clientes/conexiones aunque se defina la URI.
No se activó Atlas real. La configuración MongoDB no implementa autenticación;
la composición JWT/handlers e identidades se describe en [security.md](security.md).
MongoDB por sí solo no habilita REST. RabbitMQ permanece inactivo.

## Colecciones

| Colección | Documento | Campos |
| --- | --- | --- |
| `reviews` | `ReviewDocument` | reviewId como `_id` String; explorerId, huariqueId, rating entero, comment y createdAt |
| `favorites` | `FavoriteDocument` | `_id` ObjectId técnico, explorerId y huariqueId |

`MongoId(FieldType.STRING)` evita convertir una reviewId hexadecimal a ObjectId.
No se agrega otra identidad de reseña. El ObjectId del favorito es técnico: no
pasa al dominio ni a la API; FavoriteId sigue siendo la pareja explorador-huarique.
Spring Data puede guardar metadata de tipo interna, ajena al contrato HTTP.

createdAt se guarda como String ISO-8601 UTC para preservar exactamente los
nanosegundos del Instant. [BSON Date representa milisegundos](https://www.mongodb.com/docs/manual/reference/bson-types/#date).
El mapper reconstruye el Instant con `Instant.parse`. No se agrega orden o filtro
temporal; futuras operaciones de fecha deberán revisar expresamente este esquema.
Los comentarios conservan espacios y la representación vacía de ausencia.

Los mappers reconstruyen Review, Rating, ReviewComment y Favorite mediante sus
constructores existentes. Datos persistidos que violen invariantes producen un
error de lectura con causa preservada, no agregados inválidos o datos corregidos
silenciosamente. No se modifican las reglas del dominio.

## Operaciones e índices

`ReviewRepository` y `FavoriteRepository` usan MongoOperations de Spring Data.
Su método save ejecuta **insert**, nunca un save/upsert que sobrescriba datos.
Una reviewId repetida conserva el contrato `IllegalStateException`; una pareja de
favoritos repetida se traduce de DuplicateKeyException a DuplicateFavoriteException.
Los errores técnicos y de materialización se traducen a RepositoryAccessException
con causa. El advice HTTP existente continúa sanitizando sus respuestas.

Las consultas filtran por ID/huarique o por pareja/explorador, sin orden o paginación
añadidos. deleteById ejecuta una única eliminación por ambos componentes y devuelve
`deletedCount > 0`; no hace una lectura previa ni confunde errores con ausencia.

| Colección | Índice | Definición |
| --- | --- | --- |
| reviews | `_id_` | Único implícito de MongoDB; corresponde a reviewId |
| reviews | `reviews_huarique` | `{huariqueId: 1}`, no único |
| favorites | `_id_` | Único técnico implícito |
| favorites | `favorites_explorer_huarique_unique` | `{explorerId: 1, huariqueId: 1}`, **unique=true** |

La [unicidad compuesta de MongoDB](https://www.mongodb.com/docs/manual/core/index-unique/)
respalda escrituras concurrentes; no se sustituye por check-then-insert. El prefijo
del índice sirve a la consulta por explorador. El constructor del adaptador asegura
el índice y falla si no puede crearlo. No elimina o corrige datos existentes para
resolver conflictos del índice automáticamente.

## Pruebas aisladas y evidencia

La fase de persistencia ejecutó **243 pruebas exitosas**, con cero fallos, errores
u omisiones: las 202 previas y 41 nuevas. Quince usan MongoDB real temporal, incluidas
las cinco del contrato reusable de favoritos; las otras nuevas comprueban mappers,
errores y configuración con mocks o contextos sin servidor.

El runner prefiere [Testcontainers](https://java.testcontainers.org/modules/databases/mongodb/)
con `mongo:8.0.1`. Sin Docker inicia un mongod nuevo en loopback, con puerto dinámico
y directorio creado por JUnit. Busca el binario en PATH, `MONGOD_BINARY` o
`-Dcommunity.test.mongod`. Nunca consume MONGODB_URI ni se adjunta a un servidor
existente. Sin Docker ni binario, las pruebas fallan explícitamente; no se omiten.

En esta máquina Docker no estaba disponible: se ejecutó **mongod 8.0.1 nativo**.
Las pruebas usan bases `foodly-community-it-<UUID>` en ese proceso, y la prueba de
configuración usa foodly-community en el mismo servidor efímero. Se cierran clientes,
se ordena shutdown al proceso propio y JUnit limpia su directorio. No se usan
Atlas, volúmenes persistentes, datos o credenciales reales.

```powershell
./mvnw.cmd clean verify
# Opcional: exigir un runtime concreto, sin cambiar producción:
./mvnw.cmd '-Dcommunity.test.mongo.runtime=docker' clean verify
./mvnw.cmd '-Dcommunity.test.mongo.runtime=native' clean verify
```

El arranque básico y las pruebas unitarias/de contexto no necesitan MongoDB
externo. La verificación completa ahora incluye integración y requiere Docker o
mongod para crear el servidor temporal. La rama Docker no se ejecutó en esta máquina;
esto no equivale a pruebas omitidas: la misma suite se ejecutó con la rama nativa.

Se verificaron índice real, rechazo de duplicados con el driver directo, ocho
inserciones concurrentes por dos adaptadores (una insertada/siete conflictos), dos
eliminaciones concurrentes (true/false), precisión nanosegundo y fallo de construcción
si el índice único no puede crearse.

Una aserción previa de FavoriteRepositoryContract pasó de containsSame a contains:
la base reconstruye otro objeto con la misma identidad de dominio. La comprobación
de una sola pareja guardada se conserva. No se cambiaron las interfaces de dominio.
La evidencia está en `verification.md` y `target/mongo-integration-runtime.txt`.

La verificación de la fase JWT/composición sumó 312 pruebas: las 243 anteriores y
69 nuevas, incluidas 21 de la API autenticada con filtros y MongoDB reales temporales.
No se modificaron los adaptadores o contratos de persistencia en esa fase.

## Inbox y proyección Business

La integración asíncrona añade `community_business_inbox` (eventId como `_id` único)
y `community_known_huariques` (huariqueId como `_id` único). No modifica `reviews`,
`favorites`, los puertos o los adaptadores existentes. Ambos documentos técnicos
se guardan en la misma transacción MongoDB con SNAPSHOT/MAJORITY; el consumidor
requiere replica set y falla al arrancar contra un standalone. Sin activación del
consumidor, la persistencia REST conserva su comportamiento anterior.

No se borra el inbox ni se configura TTL sin una política acordada. El checkpoint
solo aumenta y soporta snapshots fuera de orden. Véase [messaging.md](messaging.md)
para atomicidad, recuperación, índices y límites de las pruebas.
