# Arquitectura inicial de Community

## Responsabilidad y límites

Community es dueño de reseñas, calificaciones (1 a 5), favoritos y cálculo de
reputación. No es dueño del catálogo de huariques, identidades, reservas, búsqueda
geográfica o entrega de mensajes. Referenciará otros contextos mediante contratos
explícitos e identificadores acordados; no mediante acceso a sus bases de datos.

La sección 5.4 se interpreta únicamente a partir del extracto proporcionado.
El informe íntegro debe revisarse antes de afirmar sus detalles tácticos.

## Dependencias permitidas

```text
Interfaces (REST/DTO) ------> Application ------> Domain
Infrastructure ------------> puertos internos de Application/Domain
Arranque Spring ------------> composición de adaptadores y configuración
```

- Domain contiene entidades/agregados, objetos de valor, servicios, eventos y
  contratos de repositorio. Solo Java; sin Spring, anotaciones de persistencia,
  DTO HTTP ni bibliotecas del broker.
- Application orquesta casos de uso sobre Domain y puertos. No depende de
  implementaciones Infrastructure ni del transporte HTTP.
- Interfaces adapta HTTP a comandos/queries y resultados a recursos. No persiste
  directamente ni devuelve entidades de dominio como contrato de transporte.
- Infrastructure implementará contratos internos y conectará persistencia,
  mensajería y autenticación. La configuración técnica permanece aquí.

La aplicación Spring de la raíz es el punto de arranque. Los paquetes se conservan
con `package-info.java`; no se crean clases vacías ni controladores provisionales.
`AggregateRepository<T, ID>` es el contrato mínimo reutilizable. Ahora lo especializan
`IReviewRepository` e `IFavoriteRepository` con los agregados e identidades del dominio,
sin tipos de persistencia ni implementación técnica.

## Persistencia: MongoDB Atlas

**Estado: tecnología confirmada el 24 de noviembre de 2026 por instrucción explícita
del proyecto.** MongoDB Atlas resuelve la discrepancia del informe entre texto
relacional y diagrama MongoDB. La base predeterminada es `foodly-community`, exclusiva
de este contexto. No se accede ni se reutiliza la base de Business.

`spring-boot-starter-data-mongodb` aporta Spring Data y el driver síncrono. La URI
se reserva en `MONGODB_URI` sin valor predeterminado; la base se configura mediante
`MONGODB_DATABASE`, que debe ser `foodly-community`. Existen documentos, mappers y
adaptadores con MongoOperations, todos en Infrastructure. Los puertos de dominio y
Application no cambian. El esquema e índices están en [persistence.md](persistence.md).

Se excluyen `MongoAutoConfiguration`, `MongoDataAutoConfiguration` y
`MongoRepositoriesAutoConfiguration` de Spring Boot 3.5. La composición explícita
`MongoPersistenceConfiguration` crea cliente, template y adaptadores únicamente
con `COMMUNITY_MONGODB_ENABLED=true`, URI presente y base exclusiva de Community.
El cliente se cierra con el contexto. Las exclusiones ya no necesitan retirarse
para esta activación. `auto-index-creation` sigue false: cada adaptador asegura sus
índices explícitamente antes de estar disponible. El arranque falla si no puede
garantizarlos. Sin activación no se crean clientes, sin fallback a localhost.
No se conectó a Atlas real en esta entrega.

## Seguridad JWT y estado cerrado predeterminado

JWT y MongoDB están desactivados por defecto. En ese estado SecurityConfiguration
conserva salud GET pública, CSRF y denegación de todas las demás peticiones. No hay
usuario/contraseña generado, Basic o formulario de login. Activar una sola
integración no habilita handlers, controladores o acceso de negocio.

La revisión directa del emisor IAM confirmó HS256 y los claims sub, role, iat y exp,
sin iss/aud; Business exige estos últimos y mantiene una incompatibilidad documentada.
IamJwtDecoderFactory ofrece selección explícita iam-current o issuer-audience,
sin fallback ni valores de issuer/audience inventados. Valida firma HS256, tipos,
identidad, expiración, duración observada de una hora y nbf si aparece. El contrato
estricto exige issuer/audience configurados y rechaza el formato actual de IAM.
No se descubren claves por red ni se llama a IAM para validar cada request.

Con ambas integraciones configuradas, la API exige JwtAuthenticationToken y
ROLE_GASTRONOMIC_EXPLORER, derivado únicamente del role singular observado. No se
admiten otros tipos de principal, roles plurales o scopes como elevación de permisos.
SpringSecurityCurrentExplorer toma sub del contexto autenticado, sin leer headers
arbitrarios, parámetros o body. La API es stateless; CSRF solo se ignora en su
namespace /community/**, no globalmente. Los errores de filtros 401/403 son públicos
y genéricos; nunca incluyen tokens, secretos o causas internas.

La evidencia de IAM, políticas y activación local están en [security.md](security.md).
Se probó criptografía real con JJWT 0.13.0 de prueba y MongoDB temporal, no login IAM
desplegado. Community conserva Boot 3.5.16 aunque el POM IAM revisado use 4.1.2;
la interoperabilidad se valida en el formato JWT, sin compartir modelo o framework.

## Mensajería y operación inicial

`RabbitAutoConfiguration` continúa excluida. `BusinessEventsConfiguration` compone
un consumidor propio solo con `COMMUNITY_BUSINESS_EVENTS_ENABLED=true`; por defecto
no existen ConnectionFactory ni listener. La integración reside exclusivamente en
Infrastructure, sin cambios de reglas Domain/Application, controladores o JWT.
La proyección técnica mínima y su inbox se actualizan en una transacción MongoDB
(replica set obligatorio). El listener confirma después del commit y usa reintentos
durables del quorum con DLQ. Solo verifica pasivamente el exchange Business y
declara sus recursos propios. Contrato/topología y límites en [messaging.md](messaging.md).
No existe publicador de eventos Community: RatingUpdated carece de acuerdo.

Actuator solo expone salud sin detalles; no representa una comprobación de lag,
DLQ o conectividad de este consumidor, cuyas alertas operativas siguen pendientes.
El puerto predeterminado 8083 evita los puertos 8080 y 8082. La variable `SERVER_PORT`
permite elegir otro puerto autorizado. No se incluye Docker Compose ni se inicia
infraestructura externa.

## Comparación con Business

El POM local de Business usa Java 17 y Spring Boot **3.5.16**. Community baja de
4.1.2 a **3.5.16** para compartir esa versión. Se reemplaza el starter Web MVC de
Boot 4 por `spring-boot-starter-web`; se retira `spring-boot-starter-webmvc-test`
y se usa `spring-boot-starter-test` con el import `AutoConfigureMockMvc` de Boot 3.
Las exclusiones de RabbitMQ y del usuario predeterminado también usan los nombres
de Boot 3. Spring Security, Spring Data y los drivers quedan bajo el BOM de Boot,
sin fijar versiones incompatibles individualmente.

Business usa además MongoDB, RabbitMQ, JWT y organización por capas. Community
comparte el ecosistema técnico, pero su dependencia se orienta hacia su dominio. No comparte
modelo ni persistencia con Business. No se copia lógica de negocio de otro contexto.
Los scripts estándar de Maven Wrapper se reutilizaron en lectura desde Business;
solo la copia local de `mvnw.cmd` recibió una corrección para admitir `Target` nulo
en PowerShell. No se modificó el proyecto de origen.

## Diseño táctico implementado

Las instrucciones de la fase Domain concretan las decisiones previamente abiertas:

- **Review** es una raíz de agregado inmutable. Tiene `String reviewId`, `String
  explorerId`, `String huariqueId`, `Rating rating`, `ReviewComment comment` e
  `Instant createdAt`. El constructor preserva las invariantes; rating y fecha no
  pueden ser null. El llamador suministra ID e instante: el dominio no genera IDs,
  consulta relojes externos ni restringe fechas por una política no acordada.
  Su igualdad se basa en reviewId; no existen operaciones de edición o eliminación.
- **IDs:** cadenas opacas no nulas ni en blanco (`String.isBlank`), preservadas sin
  recorte ni cambios de mayúsculas. No se exige UUID, ObjectId o un formato externo.
  Esta validación local no comprueba existencia del explorador o huarique.
- **Rating** es un record Java 17 con un entero de 1 a 5; igualdad por valor.
- **ReviewComment** es un record sin límite arbitrario de longitud ni obligatoriedad.
  La ausencia se representa como contenido vacío (null se normaliza a `""`); el texto
  no nulo, incluidos espacios y saltos de línea, se conserva íntegro. Igualdad exacta
  por contenido tras esa representación de ausencia.
- **Favorite** es una raíz inmutable cuya identidad es `FavoriteId(explorerId,
  huariqueId)`. El record compuesto valida ambos IDs y evita un ID artificial o
  una concatenación ambigua. Dos instancias con la misma pareja representan el
  mismo agregado. Esto por sí solo no impide duplicados entre registros guardados.
- **ReputationService** es un servicio puro y sin estado. Filtra por huarique,
  suma exactamente las calificaciones válidas y divide usando BigDecimal con
  `MathContext.DECIMAL128` (34 cifras significativas, HALF_EVEN para divisiones que
  lo requieren). No fuerza una escala de visualización. `Optional.empty()` significa
  que no existen reseñas del huarique; cero no representa ausencia. No persiste
  promedios ni impone un mínimo de reseñas. Rechaza colecciones o elementos null
  en lugar de confundir datos inválidos con ausencia de reseñas.

`IReviewRepository` permite insertar y consultar por ID o huarique. `save` inserta
una identidad nueva y no actualiza una reseña existente; no se añade una restricción
de una sola reseña por pareja explorador-huarique.

`IFavoriteRepository` permite insertar, consultar por identidad compuesta o
explorador y, desde la fase Application, eliminar atómicamente por identidad.
Exige rechazo atómico de duplicados mediante `DuplicateFavoriteException`.
Una consulta previa a insertar no garantiza unicidad ante concurrencia. El futuro
adaptador crea un índice MongoDB único compuesto `(explorerId, huariqueId)`
y traduce su violación a la excepción del dominio. La integración ejecuta el
contrato contra MongoDB real temporal, además del doble en memoria previo, y
comprueba escrituras concurrentes. No se declara verificación de Atlas o IAM.

## Application implementada

Se implementan tres comandos y handlers (crear reseña, agregar favorito, eliminar
favorito) y tres queries y handlers (reseñas por huarique, favoritos propios, resumen
por huarique). Son clases Java con inyección por constructor, sin anotaciones Spring,
dependencias de REST, MongoDB o seguridad concreta. La composición de beans pertenece
a Infrastructure y ya existe en CommunityApplicationConfiguration; solo se activa
con JWT y MongoDB explícitos y sus dependencias completas.

Los comandos construyen los agregados existentes y delegan sus invariantes al
dominio. Las consultas reutilizan los mismos repositorios; no existe un modelo de
persistencia duplicado. Los resultados de listas son snapshots inmutables.
`ReviewSummary` contiene huarique, cantidad y promedio opcional calculado con el
`ReputationService` existente. Sin reseñas significa count=0 y promedio ausente.

Los puertos nuevos son `CurrentExplorer` y `ReviewIdGenerator`; se utiliza además
el `Clock` de Java. La identidad y hora de la reseña proceden de dependencias del
servidor, sin formato de ID seleccionado ni reloj oculto en el handler.

Para evitar suplantación por datos de entrada, no hay explorerId en los comandos
ni selector de explorador en `GetFavoritesByExplorerQuery`. Los casos de uso que
operan sobre un explorador obtienen su identidad exclusivamente de `CurrentExplorer`,
implementado por SpringSecurityCurrentExplorer a partir del JWT verificado. Sin
esa identidad fallan antes de acceder a persistencia. No se registra un usuario
provisional y la política HTTP sigue cerrada por defecto.

### Extensiones mínimas del contrato de dominio

`IFavoriteRepository.deleteById(FavoriteId)` devuelve si realmente eliminó el registro.
Esta extensión es necesaria para el caso de uso solicitado; una consulta previa
no resolvería la carrera con otra eliminación. Un resultado false se transforma
en `FavoriteNotFoundException` de Application. El doble de pruebas previo se adapta
con un método nuevo, sin modificar sus casos de prueba.

`RepositoryAccessException` define un error técnico portable en el puerto de
persistencia y preserva la causa. Se documenta en el contrato base, sin cambiar sus
firmas ni las reglas de los agregados. Los adaptadores futuros deben traducir los
fallos del driver; los handlers propagan el error y conservan las excepciones de
dominio. No hay reintentos o resultados vacíos que oculten un fallo técnico.

## Interface Layer REST implementada

`ReviewController` adapta creación/consulta de reseñas. `FavoriteController` adapta
agregar, eliminar y consultar favoritos propios. Reutilizan cinco handlers de
Application; no consultan repositorios ni repiten reglas del dominio. Las rutas
exactas están documentadas en `contracts.md`.

`CreateReviewResource`, `ReviewResource` y `FavoriteResource` separan el transporte
de los agregados. El recurso de creación no tiene explorerId. Jakarta Bean Validation
comprueba el rating requerido/rango y los IDs no blancos. El deserializador local
`IntegerRatingDeserializer` evita coerciones JSON que aceptarían una calificación
no entera antes de llegar al dominio; no cambia el ObjectMapper global. El recurso
rechaza propiedades desconocidas. Los comandos de favoritos solo usan el ID de
huarique de la ruta; no se vinculan headers, body o query params a la identidad.

`RestExceptionHandler` es el advice global. Traduce validación a 400, identidad
requerida a 401, favorito inexistente a 404, duplicado a 409 y errores inesperados
o de persistencia a 500. `ApiErrorResource` contiene únicamente estado, código y
mensaje público. No expone mensajes internos, causas, valores rechazados o trazas.
Conserva errores de transporte específicos como 405/415. Los errores del filtro
de seguridad siguen ocurriendo antes de MVC, con la política actual de denegación.

### Arranque predeterminado y pruebas aisladas

RuntimeAdaptersConfiguration aporta reloj UTC y generador UUID reemplazables en
pruebas. IamJwtConfiguration crea decoder e identidad solo al activar JWT con
contrato y clave válidos. CommunityApplicationConfiguration registra los seis
handlers con sus puertos obligatorios cuando JWT y MongoDB están habilitados.
Los controladores usan esa misma condición de propiedades en lugar de ConditionalOnBean,
evitando una dependencia del orden de component scan. Solo cambió su metadata de
registro, no su lógica HTTP. Faltantes o inconsistencias fallan el arranque; no hay
repositorios simulados o dependencias nulas como reserva.

El estado predeterminado conserva Actuator, no conecta a servicios externos y
deniega negocio. Las pruebas previas MVC aisladas mantienen su alcance limitado
al transporte y mocks. Las nuevas añaden filtros HS256 reales con claves efímeras,
controladores/handlers/repositorios reales y MongoDB temporal: prueban la composición
completa sin sustituir la persistencia por mocks ni consultar IAM/Atlas productivos.

El resumen de reputación sigue disponible solo en Application. No se añade una
ruta pública ni DTO HTTP de resumen sin un requisito explícito.

## Pendientes para Infrastructure

La persistencia, JWT, identidad, reloj/generador y composición están implementados.
Faltan la configuración del entorno y verificación con IAM desplegado. El contrato
issuer-audience exige acuerdo de valores y modificación del emisor en su propio
proyecto; no se realiza aquí ni se oculta su ausencia mediante fallback. Una
ampliación de permisos requiere acuerdo explícito. El consumidor RabbitMQ de
Business ya está implementado y apagado por defecto. Quedan la provisión operativa
autorizada, replay/retención/reconciliación y los contratos de publicación Community.

## Decisiones de dominio aún pendientes

Formatos y normalización de IDs acordados con IAM/Business, permisos para crear
reseñas, paginación/orden, política futura de comentarios si se solicita y redondeo
de presentación del promedio. La consistencia de las lecturas para reputación se
acordará con los casos de uso y la persistencia. No se implementan esas políticas,
ni eventos cuyos contratos sigan abiertos.
