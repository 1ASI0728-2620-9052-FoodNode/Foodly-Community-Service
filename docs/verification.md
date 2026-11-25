# Verificación de la entrega

## Auditoría previa a publicación

El **24 de noviembre de 2026** se volvió a ejecutar `./mvnw.cmd -B -ntp clean verify`:
**BUILD SUCCESS**, salida **0**, **360 pruebas, 0 fallos, 0 errores, 0 omitidas**,
58.445 segundos. Java 17.0.21+9, Maven 3.10.0 y Boot 3.5.16. Se utilizó únicamente
infraestructura temporal aislada, con los mismos alcances reales/simulados descritos
abajo. No se agregaron pruebas ni se cambió código Java o POM durante esta auditoría.

Evidencia privada: `.cache/audit-verify.log`, `.cache/audit-test-results.csv` y
`target/surefire-reports/`. La carpeta no contiene `.git`: los comandos de estado,
rama, remoto y diff solicitados fallaron por esa causa. Se realizó revisión
complementaria de archivos nuevos sin índice Git. No hubo git add, commit ni push.
El [informe de auditoría](pre-publication-audit.md) contiene inventario publicable,
revisión de secretos, clasificación de pendientes, cambios documentales y propuesta
de commits; no acredita integración productiva ni ausencia de CVE.

## Estado actual: consumidor Business

Fecha: **24 de noviembre de 2026**, zona America/Lima.

- Ejecutado: `./mvnw.cmd -B -ntp clean verify`, Java Temurin **17.0.21+9**, Boot **3.5.16**.
- **BUILD SUCCESS**, salida **0**. **360 pruebas: 0 fallos, 0 errores, 0 omitidas**.
- Se conservan las **312 ejecuciones previas**, con las mismas suites/resultados.
- **48 nuevas**: `BusinessEventConsumerTest` (28), `BusinessEventsConfigurationTest`
  (5), `BusinessEventsIntegrationTest` (15).
- Evidencia: `.cache/business-events-verify.log`, `.cache/business-events-test-results.csv`,
  `.cache/business-events-baseline-comparison.csv` y `target/surefire-reports/`.

### Infraestructura real frente a simulaciones

Las 15 pruebas integradas ejecutaron MongoDB **8.0.1** con replica set temporal
`community_test` y RabbitMQ **4.2.9** / Erlang **27.3.4.19** en procesos nuevos.
Docker no está disponible: se usaron binarios nativos, dbpaths/vhost de un broker
nuevo, puertos dinámicos loopback y datos/credenciales efímeros. El runtime portable
se copió exclusivamente desde los binarios disponibles a `.cache/rabbit-runtime`;
no se leyó configuración, claves ni datos de un broker existente. La rama Docker
de los fixtures no se ejecutó; no representa tests omitidos.

Evidencia real: routing de los tres eventos españoles, ausencia de binding de menú,
declaración durable/quorum/argumentos del retry/DLX, transacción y deduplicación por
`_id`, versiones fuera de orden/iguales, concurrencia, contador de reentregas del
broker, DLQ para JSON/esquema inválidos y agotamiento, y contexto Spring con listener
manual conectado a ambos servidores. La recuperación reproduce commit sin ACK,
cierre del canal y recreación del store; también comprueba que el contador de
reintentos sobrevive al cambio de canal. No equivale a reiniciar un cluster físico.

Los fallos Mongo son **inyectados** con Mockito: uno de ellos ejecuta una transacción
real y falla al insertar inbox, comprobando que la proyección no queda guardada;
otros ejercitan NACK/reintentos con Rabbit real y un store que simula indisponibilidad.
La observación del ACK usa un spy del canal real y consulta ambos documentos antes
de delegar el ACK real. Las 28 pruebas de parser/listener y cinco de configuración
son unitarias/de contexto; no prueban conexión a IAM ni a Atlas/CloudAMQP.

No se probaron particiones de red, reinicio del broker completo, failover multimiembro,
retención operativa, carga o replay de DLQ. No se declara entrega exactamente una vez.
Los procesos propios se cierran y sus directorios JUnit temporales se limpian.

### Límites de capas y conservación

La comparación SHA-256 contra `.cache/business-events-unchanged-baseline.csv`
confirma Domain, Application, REST, JWT y los adaptadores Mongo existentes sin cambios.
Entre los archivos previos del inventario solo cambiaron `pom.xml` (dependencia
Testcontainers RabbitMQ de test) y `TemporaryMongoServer.java` (nuevo modo replica
set; `start(Path)` sigue iniciando el mismo modo anterior). Las fuentes de las 312
pruebas previas siguen iguales. La búsqueda de imports/dependencias Spring/Mongo/
Rabbit/Infrastructure en Domain y Application no devuelve coincidencias.

No se modificaron otros repositorios ni el contrato JWT; no hubo commit/push,
acceso a infraestructura persistente o uso de secretos reales. El arranque básico
sigue sin clientes Rabbit/Mongo/JWT. Guía, contrato y pendientes: [messaging.md](messaging.md).

## Historial: JWT y composición completa

Fecha: **24 de noviembre de 2026**, zona America/Lima.

- Ejecutado: `./mvnw.cmd -B -ntp clean verify`.
- Resultado: **BUILD SUCCESS**, salida **0**; Java 17 y Boot 3.5.16 conservados.
- **312 pruebas exitosas: 0 fallos, 0 errores, 0 omitidas.**
- Las **243 ejecuciones anteriores** conservan sus resultados y fuentes sin cambios.
- **69 ejecuciones nuevas**, en cinco suites JUnit 5.
- Evidencia: `.cache/jwt-verify.log`, `.cache/jwt-test-results.csv` y `target/surefire-reports/`.
- JAR ejecutable generado; se comprobó que no contiene dependencias jjwt de pruebas.

| Suite nueva | Ejecuciones |
| --- | ---: |
| `IamJwtDecoderFactoryTest` | 38 |
| `SpringSecurityCurrentExplorerTest` | 4 |
| `RuntimeAdaptersConfigurationTest` | 2 |
| `IamJwtConfigurationTest` | 4 |
| `CommunityJwtMongoIntegrationTest` | 21 |
| **Nuevas** | **69** |
| **Previas** | **243** |
| **Total** | **312** |

### Alcance real de las comprobaciones

El decoder se prueba con tokens firmados por JJWT 0.13.0 y claves aleatorias
efímeras. Se verifican firma incorrecta, expiración, ausencia/valores inválidos
de sub/role/iat/exp, nbf futuro, duración excesiva, HS384/HS512, ausencia de firma,
tokens malformados y tipos incorrectos de claims. El contrato issuer-audience se
prueba con valores exclusivamente de test, verificando rechazo de ausencia y
valores erróneos. El formato IAM actual se acepta solo bajo selección iam-current;
no hay fallback desde el estricto y tampoco se ignoran nuevos iss/aud en el actual.

Una prueba inicial descubrió coerción de sub numérico por Nimbus. Se corrigió la
comprobación previa a normalización; el resultado final incluye su rechazo y el
de tipos inválidos de iss/aud/nbf. Ese paso no autentica: toda aceptación continúa
dependiendo de la verificación HS256 y validadores posteriores. IamJwtProperties
se comprueba con toString redactado; no se usan secretos reales.

Se probó que CurrentExplorer acepta solo el contexto JwtAuthenticationToken
autenticado, no usuario/contraseña o un JWT no autenticado. El reloj UTC y generador
UUID funcionan sin servicios externos y admiten reemplazos deterministas. La
configuración incompleta de clave/contrato/URI falla cerrada; JWT sin Mongo conserva
salud y bloqueo de negocio, sin controladores o handlers.

Las 21 pruebas integradas levantan un contexto web real con filtros de Spring
Security, controladores, handlers y adaptadores MongoDB reales. No hay mocks de
repositorio o decoder. Usan una clave efímera y tokens del productor reproducido,
con reloj/generador de IDs deterministas. Verifican creación/consulta de reseñas,
alta/consulta/eliminación de favoritos, duplicados, 401 sin autenticación, 403 sin
permisos, ausencia de sesión persistida y rechazo de un principal no JWT aun con
el mismo rol. Body/query/header no pueden suplantar la identidad; otro explorador
no puede eliminar el favorito ajeno. La composición registra una sola instancia
de cada controlador y los seis handlers, y Mongo sin JWT continúa cerrado.

Se usó mongod **8.0.1 nativo**, temporal y aislado, porque Docker no estaba disponible.
No se abrió puerto HTTP de aplicación: MockMvc usa el contexto con filtros reales.
El archivo `target/jwt-mongo-integration-runtime.txt` registra el runtime y alcance;
la suite previa registra el suyo en `target/mongo-integration-runtime.txt`. Ambos
procesos temporales se cerraron. No hubo conexión a Atlas, IAM desplegado, bases
existentes o datos de Business. La rama Testcontainers no se ejecutó aquí; las
suites completas se ejecutaron por la rama nativa, sin skips.

### Compatibilidad y preservación de capas

Se inspeccionó solo código/POM local de Business y código público de IAM vía conector
GitHub. El emisor revisado en develop coincide con el blob del commit
df6ac4581579b98eee67ab4dc55e78771d0f77f5 y no incluye iss/aud. Business los exige.
La compatibilidad comprobada es con el formato observado y la biblioteca emisora,
no con un login desplegado o la clave operativa. El contrato estricto sigue pendiente
de acuerdo/implementación en IAM. Las fuentes están en [security.md](security.md).

Domain y Application se compilaron con javac --release 17 sin classpath externo.
Los hashes de `.cache/jwt-unchanged-baseline.csv` confirmaron que sus fuentes,
persistencia, mensajería y todas las pruebas anteriores no cambiaron. El POM agrega
solo JJWT de test. `.cache/jwt-isolation.log` registra comprobaciones de capas,
regresión de las 243 ejecuciones anteriores y cierre de procesos temporales.
En los controladores solo se cambió la condición de registro a las dos propiedades
de activación para evitar dependencia del orden de descubrimiento; no su lógica.

MongoDB y JWT permanecen false por defecto; las operaciones requieren ambos y sus
dependencias válidas. Los errores JWT son públicos y genéricos. RabbitMQ permanece
inactivo. No se modificó IAM, Business u otro repositorio, ni reglas del dominio.
No se hicieron commits/push y no se incorporaron secretos reales. La guía local
y bloqueos de integración operativa están en security.md.

## Registro histórico: persistencia MongoDB

El registro siguiente corresponde a la fase anterior; el resultado vigente es el
de JWT/composición descrito arriba.

Fecha: **24 de noviembre de 2026**, zona America/Lima.

- Ejecutado: `./mvnw.cmd -B -ntp clean verify`.
- Resultado final: **BUILD SUCCESS**, salida **0**, sin errores de compilación.
- **243 pruebas exitosas: 0 fallos, 0 errores, 0 omitidas.**
- Se conservan las **202 ejecuciones anteriores** y se agregan **41**.
- Evidencia: `.cache/mongo-verify.log`, `target/surefire-reports/` y
  `.cache/mongo-test-results.csv`.
- JAR ejecutable generado: `target/community-service-0.0.1-SNAPSHOT.jar`.

| Suite nueva | Ejecuciones |
| --- | ---: |
| `MongoPersistenceConfigurationTest` | 4 |
| `DocumentMapperTest` | 7 |
| `ReviewRepositoryTest` | 6 |
| `FavoriteRepositoryTest` | 9 |
| `MongoPersistenceIntegrationTest` | 15 |
| **Nuevas** | **41** |
| **Previas** | **202** |
| **Total** | **243** |

### Integración real y aislamiento

Docker no estaba disponible. El runner detectó esa situación y utilizó un proceso
**mongod 8.0.1** nuevo, con dbpath temporal de JUnit, bind solo a loopback y puerto
dinámico. No consumió MONGODB_URI ni conectó a Atlas o servidores existentes.
La evidencia del runtime está en `target/mongo-integration-runtime.txt`. La misma
suite prefiere Testcontainers cuando Docker está disponible; la rama Docker no se
ejecutó aquí. No se usó una anotación de skip ni se omitió ninguna prueba.

Las quince pruebas reales incluyen las cinco del contrato reusable de favoritos,
roundtrip de todos los campos de reseña y nanosegundos, `_id` String aun con aspecto
de ObjectId, filtro por huarique, rechazo de reviewId repetida sin sobrescritura,
eliminación existente/inexistente y aislamiento de otras parejas. Se verificó el
índice compuesto único con listIndexes y el rechazo de una escritura duplicada
directamente por el driver (código 11000), sin depender solo de anotaciones/mocks.

Ocho inserciones concurrentes de la misma pareja, usando dos adaptadores, produjeron
un favorito y siete DuplicateFavoriteException. Dos eliminaciones concurrentes
produjeron exactamente true/false. También se comprobó fallo del constructor del
adaptador cuando no puede crear el índice por datos duplicados en su base temporal,
rechazo de un documento que viola invariantes y activación de la configuración
contra el mismo servidor temporal.

Cada prueba usa una base nueva foodly-community-it-UUID; la prueba de configuración
usa foodly-community en ese proceso efímero. Clientes y proceso se cierran antes de
limpiar el directorio. Se implementó shutdown ordenado para liberar archivos de
MongoDB/FTDC en Windows; la limpieza final terminó sin errores.

Las pruebas unitarias comprueban traducción de errores técnicos con causa preservada,
conflictos de identidad, filtros por ambos componentes al eliminar y deletedCount.
Las de configuración verifican que el arranque básico no crea MongoClient/template
o adaptadores, y que URI ausente, base Business o base diferente en la URI se rechazan
antes de crear un cliente. No se activan JWT o RabbitMQ.

### Correcciones de pruebas y límites

El primer intento detectó dos problemas, corregidos antes del resultado final:
una aserción del contrato previo exigía la misma referencia Java tras recuperar un
favorito, y la limpieza temporal de Windows encontró archivos aún abiertos.
FavoriteRepositoryContract cambió una sola aserción `containsSame(original)` por
`contains(original)`: una base real devuelve otro objeto con la misma identidad.
Se conserva el rechazo de duplicados y la comprobación de una sola pareja guardada.
No se modificaron interfaces o reglas del dominio. El runner temporal recibió el
cierre ordenado del servidor propio para completar la limpieza.

Los reportes se compararon con `.cache/rest-test-results.csv`: las 19 suites previas
mantienen sus 202 ejecuciones exitosas. `.cache/mongo-unchanged-baseline.csv` y
`.cache/mongo-isolation.log` registran que Domain, Application, Interfaces,
configuración JWT y mensajería no cambiaron; la única excepción entre fuentes de
pruebas anteriores es la aserción portable descrita. Domain y Application compilaron
además con `javac --release 17 -encoding UTF-8` sin classpath externo.

Producción continúa desactivada por defecto. Activar persistencia requiere
COMMUNITY_MONGODB_ENABLED=true, MONGODB_URI y MONGODB_DATABASE=foodly-community.
No se probó Atlas real ni se declara autenticación o servicio extremo a extremo.
No se modificaron otros microservicios, no se usaron datos Business ni credenciales
reales y no se hicieron commits/push. No se eliminaron fuentes preexistentes;
la limpieza se limitó a artefactos de build y datos temporales generados por pruebas.

### Archivos de esta fase

- Nuevos de producción: `MongoPersistenceConfiguration`, `ReviewDocument`,
  `FavoriteDocument`, `ReviewDocumentMapper`, `FavoriteDocumentMapper`,
  `ReviewRepository` y `FavoriteRepository`.
- Nuevos de pruebas: las cinco suites de la tabla y `TemporaryMongoServer`.
- Modificados: pom.xml (solo dependencia Testcontainers de test), application.properties,
  .env.example, package-info de persistencia y una aserción de FavoriteRepositoryContract.
- Documentación: README, architecture, contracts, project-tree, verification y el
  nuevo [persistence.md](persistence.md).

## Registro histórico: Interface Layer REST

El registro siguiente corresponde a la fase anterior; el resultado actual es el
de persistencia descrito arriba.

Fecha: **24 de noviembre de 2026**, zona America/Lima.

- Ejecutado: `./mvnw.cmd -B -ntp clean verify`.
- Resultado: **BUILD SUCCESS**, salida **0**, sin errores de compilación.
- **202 pruebas exitosas: 0 fallos, 0 errores, 0 omitidas.**
- **136 ejecuciones anteriores conservadas**, con sus archivos fuente intactos.
- **66 ejecuciones nuevas**, en cinco suites JUnit 5; incluyen casos parametrizados.
- Evidencia: `.cache/rest-verify.log`, `target/surefire-reports/` y
  `.cache/rest-test-results.csv`.
- JAR ejecutable generado: `target/community-service-0.0.1-SNAPSHOT.jar`.

| Suite nueva | Ejecuciones |
| --- | ---: |
| `ResourceMappingTest` | 3 |
| `ReviewControllerTest` | 31 |
| `FavoriteControllerTest` | 11 |
| `RestIdentityBoundaryTest` | 9 |
| `CommunityRestSecurityTests` | 12 |
| **Nuevas** | **66** |
| **Previas** | **136** |
| **Total** | **202** |

### Qué se probó y qué no

- Las pruebas unitarias de recursos comprueban mapeo separado del modelo, rating
  requerido/rango mediante Jakarta Bean Validation y comentario opcional sin límite
  arbitrario de longitud.
- `ReviewControllerTest` y `FavoriteControllerTest` usan MockMvc standalone con
  handlers mock y advice real. Comprueban estados 201/200/204, arrays vacíos y con
  resultados, validación de IDs/cuerpo, rechazo de rating fraccionario/coerciones,
  campos no permitidos, errores 400/401/404/409/500 y errores MVC 405/415. Verifican
  delegación y que los errores no revelan mensajes internos, causas o identidades.
- `RestIdentityBoundaryTest` usa controladores y handlers reales, con mocks solo
  para repositorios y `CurrentExplorer`. Comprueba que headers y query params no
  controlan explorerId, que un body con esa propiedad se rechaza y que la ausencia
  de identidad confiable produce 401 sin acceder a repositorios. No valida tokens.
- Las pruebas MVC aisladas no cargan filtros de autenticación. Sus identidades y
  Bearer de prueba no acreditan integración IAM y no existen en producción.
- `CommunityRestSecurityTests` carga la configuración Spring y cadena de seguridad
  reales, sin reemplazarlas. Comprueba 403 para las cinco operaciones, tanto sin
  autenticación como con usuario de prueba y CSRF válido, rechazo de headers sin
  validar y ausencia de controladores sin handlers productivos. La configuración
  de seguridad y las seis pruebas de contexto originales permanecen intactas.
- El resumen de reputación conserva sus pruebas anteriores en Application. No se
  creó un endpoint público; la prueba MVC confirma que `/reviews/summary` no existe
  bajo el path del huarique.

Se compararon reportes con `.cache/application-test-results.csv`: las 14 suites
anteriores conservan sus 136 ejecuciones exitosas. Domain y Application compilaron
también usando solo `javac --release 17 -encoding UTF-8`, sin classpath externo.
`.cache/rest-isolation.log` registra la comprobación. Los hashes de
`.cache/rest-unchanged-baseline.csv` confirman que Domain, Application, Infrastructure,
POM, propiedades, Wrapper y todos los fuentes de prueba anteriores no cambiaron.

No se implementó ni activó MongoDB, JWT o RabbitMQ; no se accedió a bases de datos,
servicios IAM o credenciales reales. No se modificaron otros repositorios ni se
hicieron commits/push. La unicidad persistente y las integraciones reales siguen
sin probarse. Los contratos y el arranque condicionado se explican en
`contracts.md` y `architecture.md`.

La interfaz REST está implementada en código. Su composición productiva, los
repositorios MongoDB y el adaptador de identidad JWT corresponden a Infrastructure.
Los controladores solo se registrarán con sus handlers disponibles y la seguridad
actual seguirá bloqueando las operaciones hasta que se complete deliberadamente
esa integración. No se declara el servicio operativo de extremo a extremo.

## Registro histórico: Application Layer

El registro siguiente corresponde a la fase anterior; el resultado vigente es el
de Interfaces descrito arriba.

Fecha: **24 de noviembre de 2026**, zona America/Lima.

- Ejecutado: `./mvnw.cmd -B -ntp clean verify`.
- Resultado: **BUILD SUCCESS**, salida **0**, sin errores de compilación.
- **136 pruebas exitosas: 0 fallos, 0 errores, 0 omitidas.**
- Se mantienen las **76 ejecuciones previas**, incluidas las seis de contexto/seguridad.
- **60 ejecuciones nuevas**, en siete clases JUnit 5, con Mockito para repositorios.
- Evidencia: `.cache/application-verify.log`, `target/surefire-reports/` y
  `.cache/application-test-results.csv`.

| Suite nueva | Ejecuciones |
| --- | ---: |
| `CreateReviewCommandHandlerTest` | 15 |
| `AddFavoriteCommandHandlerTest` | 9 |
| `RemoveFavoriteCommandHandlerTest` | 9 |
| `GetReviewsByHuariqueQueryHandlerTest` | 8 |
| `GetFavoritesByExplorerQueryHandlerTest` | 4 |
| `GetReviewSummaryByHuariqueQueryHandlerTest` | 9 |
| `CurrentExplorerTest` | 6 |
| **Nuevas** | **60** |
| **Previas** | **76** |
| **Total** | **136** |

Las pruebas incluyen creación con identidad de contexto, ID generado y reloj
inyectado; rating e IDs inválidos; comentario ausente; autenticación ausente;
duplicado atómico sin consulta previa; eliminación exitosa/inexistente; consultas
por huarique y por explorador actual; snapshots inmutables; resumen y ausencia de
reseñas; propagación de errores de persistencia y preservación de errores de dominio.
Se verifican argumentos, escrituras y ausencia de interacciones adicionales con
los repositorios. El promedio se delega al servicio de dominio real, con un spy
para comprobar esa interacción. No se usa ninguna base de datos.

Se extendió únicamente `IFavoriteRepository` con `boolean deleteById(FavoriteId)`;
su doble de pruebas existente recibió ese método sin cambiar los casos previos.
Se agregó `RepositoryAccessException` y se documentó el contrato de errores técnicos
en `AggregateRepository`. No se modificaron reglas de agregados, objetos de valor
o `ReputationService`; `IReviewRepository` conserva su interfaz.

Domain y Application compilaron además con `javac --release 17 -encoding UTF-8`,
sin classpath externo. `.cache/application-isolation.log` registra ese resultado.
Las comprobaciones SHA-256 de `.cache/application-unchanged-baseline.csv`
confirmaron que modelos y servicio de dominio, Infrastructure, Interfaces, POM,
propiedades, Wrapper y las fuentes de pruebas anteriores protegidas no cambiaron.
Se compararon también los reportes con `.cache/domain-test-results.csv` para
confirmar que las siete suites previas mantienen sus 76 ejecuciones exitosas.

Los handlers son clases Java sin anotaciones Spring: no se registran beans sin
adaptadores ni se activa autenticación. `CurrentExplorer` y `ReviewIdGenerator`
son puertos sin implementación de producción; sus valores de prueba no son usuarios
o credenciales reales. MongoDB, JWT y RabbitMQ siguen inactivos y las rutas HTTP
permanecen protegidas. La unicidad/atomicidad en MongoDB no se ha probado ni se
declara garantizada por estos mocks; exige el adaptador e índice únicos futuros.

Se generó el JAR ejecutable. No se modificaron otros microservicios ni se hicieron
commits o push. Application está lista para definir el transporte de Interfaces y
la composición futura; rutas, DTO, traducción HTTP de errores y wiring permanecen
pendientes, según `contracts.md` y `architecture.md`.

## Registro histórico: Domain Layer

El registro siguiente corresponde a la fase anterior; el resultado actual es el
de Application descrito arriba.

Fecha: **24 de noviembre de 2026**, zona America/Lima. Se implementó únicamente
Domain, junto con pruebas unitarias y documentación. Sin cambios al POM, Java 17,
Spring Boot 3.5.16, JWT, RabbitMQ, propiedades, Application o Interfaces.

- Ejecutado: `./mvnw.cmd -B -ntp clean verify`.
- Resultado: **BUILD SUCCESS**, código de salida **0**, sin errores de compilación.
- **76 pruebas ejecutadas: 0 fallos, 0 errores, 0 omitidas.**
- Se conservaron intactas y exitosas las **6 pruebas existentes** de contexto/seguridad.
- Evidencia: `.cache/domain-verify.log` y `target/surefire-reports/`.
- Resumen de reportes XML: `.cache/domain-test-results.csv`.
- Se generó el JAR ejecutable `target/community-service-0.0.1-SNAPSHOT.jar`.

| Suite | Ejecuciones |
| --- | ---: |
| `CommunityServiceApplicationTests` (existente) | 6 |
| `RatingTest` | 13 |
| `ReviewCommentTest` | 5 |
| `ReviewTest` | 21 |
| `FavoriteTest` | 13 |
| `ReputationServiceTest` | 13 |
| `InMemoryFavoriteRepositoryContractTest` | 5 |
| **Total** | **76** |

Los números incluyen cada ejecución parametrizada. Se comprueban creación válida,
IDs nulos/vacíos/en blanco, rango 1–5, rating y fecha obligatorios, comentario opcional
y sin límite arbitrario, inmutabilidad/igualdad de objetos de valor, identidad de
agregados, promedio exacto o DECIMAL128 según la división, filtrado por huarique y
ausencia de reseñas.

`FavoriteRepositoryContract` aporta cinco pruebas reutilizables de creación,
rechazo de duplicados y consultas. Solo se ejecutaron contra un doble en memoria
bajo `src/test`; no hay adaptador de producción. **No se ha probado unicidad
persistente, índices MongoDB ni concurrencia entre procesos.** El futuro adaptador
debe respaldar la inserción atómica con índice único `(explorerId, huariqueId)` y
ejecutar pruebas de integración reales.

Verificación adicional: todos los fuentes del dominio compilaron con
`javac --release 17 -encoding UTF-8`, sin classpath de Spring ni otra biblioteca
externa. Los imports son exclusivamente Java y tipos del propio dominio.
La evidencia está en `.cache/domain-isolation.log`. Comparaciones SHA-256 con
`.cache/domain-unchanged-baseline.csv` confirmaron que Application, Interfaces,
Infrastructure, las propiedades, el POM, Wrapper y la suite existente no cambiaron.

El primer intento de `clean` dentro del aislamiento falló al limpiar `target`
de la compilación anterior. La ejecución autorizada posterior completó la limpieza,
compilación y pruebas. No quedan fallos pendientes del build final.

No se activó MongoDB, RabbitMQ, JWT ni ningún servicio externo. No se modificaron
otros repositorios, no se accedió a bases persistentes y no se hicieron commits/push.

**Domain está listo para el alcance solicitado.** Siguen pendientes formato externo
de IDs, permisos, orden/paginación, redondeo de presentación y políticas adicionales
de comentarios si se solicitan. Application, REST y los adaptadores no se implementan
en esta entrega. El detalle del modelo está en `architecture.md` y `contracts.md`.

## Registro histórico: alineación tecnológica anterior

Las secciones siguientes conservan la evidencia de la etapa previa, que tenía seis
pruebas y el dominio todavía pendiente. El estado vigente es el de la sección anterior.

Fecha: **24 de noviembre de 2026**, zona America/Lima.

## Herramientas y compilación

- `java -version`: OpenJDK Temurin **17.0.21+9**.
- `./mvnw.cmd -version`: Apache Maven **3.10.0**, Java **17.0.21**.
- Spring Boot fijado en el POM: **3.5.16**, igual al POM local de Business inspeccionado.
- Compilación final ejecutada: `./mvnw.cmd -B -ntp clean verify`.
- Resultado real: **BUILD SUCCESS**, código de salida **0**.
- Compilador: `javac`, `release 17`.
- Artefacto: `target/community-service-0.0.1-SNAPSHOT.jar`, reempaquetado ejecutable.
- Evidencia completa local: `.cache/alignment-verify.log`.

Se usó Maven Central para resolver las dependencias faltantes de la versión 3.5.16.
`.mvn/maven.config` mantiene `.cache/repository` dentro de Community, sin modificar
la caché original. Una primera ejecución sin caché requiere red.

El intento inicial offline de esta alineación falló porque faltaba el starter de
seguridad 3.5.16 en la caché. La ejecución final autorizada descargó lo necesario
y completó `clean verify`. No quedan fallos de compilación o pruebas pendientes.
El Wrapper no se modificó durante esta alineación.

## Compatibilidad de dependencias

- `spring-boot-starter-webmvc` de Boot 4 se reemplazó por `spring-boot-starter-web`.
- `spring-boot-starter-webmvc-test` se retiró: Boot 3 usa `spring-boot-starter-test`
  y `org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc`.
- Se añadió `spring-boot-starter-data-mongodb` y se conservaron Validation,
  Security, OAuth2 Resource Server, Actuator, AMQP y Spring Security Test.
- Las exclusiones de RabbitMQ/usuario predeterminado se corrigieron a sus paquetes
  de Boot 3; se añadieron las tres exclusiones MongoDB.
- Inspección del JAR: Spring Boot 3.5.16, Spring Security 6.5.11,
  Spring Data MongoDB 4.5.13, driver MongoDB 5.5.2 y Tomcat 10.1.55.
  No permanecen bibliotecas Spring Boot 4 en el JAR. La versión 4.5.13 de Spring Data
  MongoDB es gestionada por Boot 3.5.16 y no implica utilizar Spring Boot 4.
- `javap` confirmó bytecode major version 61 (Java 17).
- Inventario del artefacto: `.cache/alignment-artifact-contents.txt`.

No se identificaron incompatibilidades adicionales después de estos ajustes.

## Pruebas ejecutadas

Clase: `com.foodly.community.CommunityServiceApplicationTests`.
**6 ejecutadas, 0 fallos, 0 errores, 0 omitidas.** Se conservaron las cinco pruebas
existentes y se añadió una comprobación de denegación de Bearer sin validar.

| Prueba | Qué verifica |
| --- | --- |
| `contextLoadsWithoutExternalIntegrationBeans` | Arranque sin `ConnectionFactory`, `JwtDecoder`, `UserDetailsService`, `MongoClient`, `MongoDatabaseFactory` ni `MongoOperations` |
| `healthIsPublicAndDoesNotExposeDetails` | GET salud devuelve 200 y UP, sin componentes ni detalles |
| `apiIsDeniedUntilJwtIsImplemented` | Rutas de negocio de prueba y Actuator env devuelven 403 |
| `authenticatedUsersCannotBypassInitialClosedPolicy` | Un usuario de prueba autenticado sigue sin acceso a la API |
| `unvalidatedBearerTokenCannotOpenFutureEndpoints` | Un encabezado Bearer sin validar no habilita rutas futuras |
| `csrfProtectionRemainsActive` | Presencia de `CsrfFilter` y rechazo de POST sin token CSRF |

Reportes originales: `target/surefire-reports/`. MockMvc ejecutó las peticiones
sin abrir puertos. No se inició ningún servicio externo ni se conectó a una base
de datos o broker. No se ejecutaron pruebas de integración JWT/RabbitMQ/persistencia,
ni de negocio: esas implementaciones aún no existen. No se utilizaron credenciales
reales. Las pruebas básicas no requieren MongoDB Atlas, MongoDB local, Testcontainers
ni un servidor embebido. No se probó arranque HTTP del
JAR como proceso aparte; sí la carga del contexto web y su cadena de seguridad.

## Revisión de estructura

Están presentes los 15 subpaquetes solicitados, documentados con `package-info.java`.
El único import del dominio es `java.util.Optional`; no depende de Spring.
Application e Interfaces contienen documentación de paquetes, sin dependencias
a infraestructura ni implementaciones prematuras. La configuración Spring de
seguridad reside en Infrastructure. Spring Data MongoDB es una dependencia técnica;
el dominio y su contrato de repositorio no importan Spring ni MongoDB.

No se hicieron commits, push ni repositorios remotos. No se modificaron Business,
IAM, frontend, MongoDB o RabbitMQ. De Business solo se leyeron el POM y código
de configuración de seguridad para identificar la versión y el contrato técnico;
no se consultaron secretos ni archivos de credenciales.

## Estado y límites

La base está lista para implementar Domain. La tecnología de persistencia ya es
MongoDB Atlas; su activación, documentos e índices siguen pendientes. JWT HS256
tiene variables preparadas, sin decoder ni aceptación de tokens todavía. Restan
agregados/identidades, invariantes adicionales, fórmula de reputación y contratos
REST/JWT/eventos. La conformidad con el informe completo deberá confirmarse cuando
se disponga de él; esta entrega se basa en los requisitos suministrados.

## Archivos modificados durante la alineación

- `pom.xml`
- `.env.example`
- `src/main/resources/application.properties`
- `src/main/java/com/foodly/community/infrastructure/persistence/package-info.java`
- `src/main/java/com/foodly/community/infrastructure/security/SecurityConfiguration.java`
- `src/test/java/com/foodly/community/CommunityServiceApplicationTests.java`
- `README.md`
- `docs/architecture.md`
- `docs/contracts.md`
- `docs/project-tree.md`
- `docs/verification.md`
