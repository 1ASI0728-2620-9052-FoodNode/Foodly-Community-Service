# Foodly Community Service

Bounded Context **Community** de Foodly con Domain, Application e Interfaces REST implementados. Java 17, Spring Boot
**3.5.16**, Maven Wrapper **3.3.4** con Maven **3.10.0**, empaquetado JAR.
Coordenadas: `com.foodly:community-service:0.0.1-SNAPSHOT`.

Versión alineada con el `pom.xml` local de Business, revisado el 24 de noviembre de
2026. Java 17 se conserva, conforme a los [requisitos oficiales de Spring Boot 3.5](https://docs.spring.io/spring-boot/3.5/system-requirements.html).

Community gestionará reseñas de huariques, calificaciones de 1 a 5 estrellas,
favoritos de exploradores gastronómicos y reputación de establecimientos.
Los endpoints, MongoDB, JWT y composición están implementados. MongoDB y JWT
requieren activación explícita y permanecen desactivadas por defecto; sin las dos
integraciones listas, las rutas de negocio siguen bloqueadas.

## Alcance y referencia académica

La estructura sigue los requisitos proporcionados de la sección 5.4. El informe
completo no se encuentra en este directorio: los detalles no suministrados quedan
pendientes de validación, sin atribuirles reglas o contratos inventados.

La discrepancia original entre base relacional y MongoDB queda resuelta por la
instrucción explícita del proyecto: **MongoDB Atlas**, con base independiente
`foodly-community`. Spring Data MongoDB está integrado mediante documentos, mappers
y adaptadores propios. Las autoconfiguraciones permanecen excluidas y el arranque
predeterminado sigue sin clientes o conexiones; una configuración explícita permite
activar persistencia en un entorno configurado.
`AggregateRepository<T, ID>` define únicamente `findById` y `save`, con
tipos Java y sin frameworks. `IReviewRepository` e `IFavoriteRepository` especializan
ese contrato para creación y consulta, sin actualización de agregados existentes.
El repositorio de favoritos permite además eliminar por identidad compuesta.

## Organización DDD y hexagonal

El árbol completo de archivos mantenidos está en [docs/project-tree.md](docs/project-tree.md).
Los `package-info.java` documentan y conservan los paquetes sin introducir clases vacías.

| Capa | Paquetes | Responsabilidad |
| --- | --- | --- |
| Domain | `model`, `repository`, `service`, `events` | Modelo y contratos sin Spring |
| Application | `commands`, `commandhandlers`, `queries`, `queryhandlers`, `ports`, `errors` | Casos de uso dependientes del dominio y abstracciones |
| Interfaces | `rest`, `dto` | Adaptadores HTTP y recursos de transporte |
| Infrastructure | `config`, `persistence`, `messaging`, `security` | Configuración y adaptadores técnicos |

El arranque Spring está en `com.foodly.community.CommunityServiceApplication`.
Las dependencias deben apuntar hacia Domain. Application no debe importar
Infrastructure ni Interfaces. Los adaptadores implementarán los puertos internos.
Véase [architecture.md](docs/architecture.md).

## Domain implementado

- `Review`: raíz inmutable con reviewId, explorerId, huariqueId, rating, comment y
  createdAt; identidad y fecha explícitas. Sin edición ni eliminación.
- `Favorite`: raíz con `FavoriteId(explorerId, huariqueId)`, sin ID artificial.
- `Rating`: objeto de valor inmutable para enteros de 1 a 5.
- `ReviewComment`: objeto de valor inmutable, sin límite de longitud ni texto
  obligatorio; null representa contenido vacío y el resto se conserva exactamente.
- `ReputationService`: promedio por huarique mediante BigDecimal y DECIMAL128;
  devuelve `Optional.empty()` sin reseñas y no almacena promedios.
- `IReviewRepository` e `IFavoriteRepository`: puertos sin Spring Data; favoritos
  exigen inserción atómica y rechazo de pareja duplicada mediante excepción de dominio.

Los IDs son cadenas opacas no nulas ni en blanco, sin formato o normalización
inventados. Esto no valida existencia en IAM o Business. Los detalles están en
[architecture.md](docs/architecture.md) y [contracts.md](docs/contracts.md).

## Application implementada

| Caso de uso | Entrada y handler |
| --- | --- |
| Crear reseña | `CreateReviewCommand` / `CreateReviewCommandHandler` |
| Agregar favorito | `AddFavoriteCommand` / `AddFavoriteCommandHandler` |
| Eliminar favorito | `RemoveFavoriteCommand` / `RemoveFavoriteCommandHandler` |
| Reseñas por huarique | `GetReviewsByHuariqueQuery` / `GetReviewsByHuariqueQueryHandler` |
| Favoritos propios | `GetFavoritesByExplorerQuery` / `GetFavoritesByExplorerQueryHandler` |
| Resumen por huarique | `GetReviewSummaryByHuariqueQuery` / `GetReviewSummaryByHuariqueQueryHandler` |

Son clases Java sin anotaciones Spring ni dependencia de REST o MongoDB. Reutilizan
agregados, repositorios y `ReputationService`. `ReviewSummary` devuelve huarique,
cantidad de reseñas y promedio opcional; sin reseñas devuelve cero y `Optional.empty()`.
Las consultas de listas devuelven snapshots inmutables sin imponer orden ni paginación.

`CurrentExplorer` es el puerto para la identidad de confianza. Los comandos no
aceptan explorerId y la query de favoritos no permite seleccionar otro explorador.
Sin una identidad válida se lanza `AuthenticationRequiredException` antes de acceder
al repositorio. SpringSecurityCurrentExplorer implementa el puerto leyendo solo
el principal JWT validado de Spring Security.
`ReviewIdGenerator` y `Clock` se inyectan para generar identidad y fecha del lado
servidor. La composición ofrece UUID aleatorio y reloj UTC sustituibles en pruebas;
el dominio conserva IDs opacos. No hay implementaciones provisionales de producción.

Agregar favoritos delega el rechazo atómico de duplicados al repositorio. Eliminar
usa `deleteById(FavoriteId)` y lanza `FavoriteNotFoundException` si no eliminó ninguno.
Los errores técnicos se expresarán como `RepositoryAccessException` con causa; no
se ocultan ni se convierten en colecciones vacías. Véase [contracts.md](docs/contracts.md).

## Interfaces REST implementadas

`ReviewController` expone en código POST/GET
`/community/huariques/{huariqueId}/reviews`. `FavoriteController` implementa
POST/DELETE `/community/favorites/{huariqueId}` y GET `/community/favorites/me`.
Los recursos son `CreateReviewResource`, `ReviewResource` y `FavoriteResource`;
el advice devuelve `ApiErrorResource` con errores sanitizados.

Las creaciones responden 201, las consultas 200 con arrays (incluido `[]`) y la
eliminación exitosa 204. Validación, autenticación requerida en Application,
favorito inexistente, duplicado y fallos técnicos se mapean a 400/401/404/409/500.
Esos resultados se comprueban también con JWT, handlers y MongoDB reales temporales.
**Las rutas siguen bloqueadas por defecto**. Su registro requiere ambas activaciones
y todos sus beans; la API activa exige JWT HS256 válido y GASTRONOMIC_EXPLORER.
No se usan adaptadores provisionales.

No se acepta explorerId en el body de creación de reseña ni como fuente de identidad
en headers/query params. Los handlers siguen usando exclusivamente `CurrentExplorer`.
No se añade endpoint público para el resumen de reputación. El contrato detallado
y ejemplos están en [contracts.md](docs/contracts.md).

## Componentes pendientes

| Capa | Componentes planificados; todavía no creados |
| --- | --- |
| Integración operativa | Configuración del entorno, login IAM desplegado y acuerdo iss/aud si se adopta el contrato estricto |
| Messaging operativo | Permisos del entorno, retención/replay de DLQ, reconciliación y contrato de publicación Community |

Los eventos y puertos adicionales se definirán al acordar sus contratos; no se
crean implementaciones simuladas ni adaptadores de producción en memoria.

## Compilar y ejecutar

Requisito: JDK 17 y `JAVA_HOME` apuntando a ese JDK. El Wrapper descarga Maven
desde Maven Central en el primer uso si no existe en caché; la resolución inicial
de dependencias requiere acceso a Maven Central. No requiere Maven instalado globalmente.
`.mvn/maven.config` aísla las dependencias en `.cache/repository`, ignorada por Git,
para evitar escrituras en la caché global u otros proyectos.

Para ejecutar **todas las pruebas**, además de JDK 17 se recomienda Docker operativo
y accesible por Testcontainers, con permiso para descargar `mongo:8.0.1` y
`rabbitmq:4.2.9-management`. La suite crea y elimina su infraestructura temporal;
no utiliza las variables de conexión del entorno Foodly. La alternativa sin Docker
requiere un `mongod` ejecutable y, en Windows, un runtime portable RabbitMQ/Erlang
preparado fuera de Git. Su estructura y propiedades de ubicación están en
[messaging.md](docs/messaging.md). La ausencia de runtime produce fallo, no omisión.
El runtime que existe en `.cache/` en esta máquina **no se publica ni acompaña un clon**.
La ruta Docker está implementada, pero la evidencia local de esta entrega corresponde
a la alternativa nativa. El arranque básico no requiere ninguno de esos servidores.

PowerShell, desde la raíz:

```powershell
java -version
./mvnw.cmd -version
./mvnw.cmd clean verify
$env:SERVER_PORT = '8083'
./mvnw.cmd spring-boot:run
# Alternativa después de compilar:
java -jar target/community-service-0.0.1-SNAPSHOT.jar
```

Linux/macOS: `sh ./mvnw -version`, `sh ./mvnw clean verify` y
`SERVER_PORT=8083 sh ./mvnw spring-boot:run`.

El puerto predeterminado es **8083**. No configurar 8080 ni 8082 en este entorno.
Las pruebas usan MockMvc y no abren un servidor HTTP. Tras un arranque manual:

```powershell
Invoke-RestMethod http://localhost:8083/actuator/health
```

Respuesta esperada: `{"status":"UP"}`. No acredita disponibilidad de JWT,
RabbitMQ ni base de datos: esas integraciones no están activadas.

`.env.example` es una plantilla sin secretos. Spring Boot no carga `.env`
automáticamente; exportar las variables en el entorno de ejecución. Las variables
MongoDB y JWT disponen de activaciones explícitas separadas. El consumidor RabbitMQ
también exige `COMMUNITY_BUSINESS_EVENTS_ENABLED=true` y sigue apagado por defecto.

## MongoDB implementado, desactivado por defecto

- `MONGODB_URI`: URI externa de Atlas, vacía por defecto; no se incluye un cluster
  ni credenciales de ejemplo. No usar una URI o base de Business.
- `MONGODB_DATABASE`: debe ser `foodly-community`; se rechaza foodly_business o
  una base diferente en la URI antes de crear un cliente.
- `MongoAutoConfiguration`, `MongoDataAutoConfiguration` y
  `MongoRepositoriesAutoConfiguration` están excluidas en la configuración principal,
  tanto en pruebas como en ejecución normal. Definir solo la URI no activa conexiones.
- `COMMUNITY_MONGODB_ENABLED=false` es el valor inicial. Solo true activa la
  configuración propia de cliente/template/adaptadores, sin retirar esas exclusiones.
- Los adaptadores aseguran explícitamente sus índices antes de estar disponibles,
  incluido el único compuesto de favoritos. Si falla su creación, falla el arranque.
- No se conectó a Atlas real. El esquema y pruebas se documentan en
  [persistence.md](docs/persistence.md).

El contrato de dominio permanece libre de Spring Data. Los documentos MongoDB y
los repositorios técnicos pertenecen a Infrastructure.

## Seguridad y conexiones externas

Solo `GET /actuator/health` es público y no muestra detalles ni componentes.
Sin ambas integraciones activas, el resto queda denegado (403). Con la composición
completa, las cinco operaciones requieren JWT HS256, claims válidos y el rol singular
GASTRONOMIC_EXPLORER. No se acepta identidad en headers/body/query params, Basic
o formulario. La API activa es stateless; CSRF solo se ignora en su namespace
Bearer /community/**, permaneciendo activo fuera de él y en el modo cerrado.

El IAM revisado emite sub, role, iat y exp, **sin iss ni aud**. JWT está desactivado
por defecto; activarlo exige IAM_JWT_CONTRACT explícito. `iam-current` implementa
el contrato observado. `issuer-audience` exige valores externos y sigue rechazando
el formato actual de IAM. No hay fallback entre contratos, valores inventados o
descubrimiento remoto. IAM_SECRET_BASE64 debe decodificar al menos 32 bytes.

El [contrato de seguridad y guía local](docs/security.md) documenta evidencia IAM,
la discrepancia con Business, validaciones y permisos. Se probó criptografía real
con JJWT 0.13.0 y MongoDB temporal; no login remoto de IAM, claves operativas o Atlas.

La autoconfiguración RabbitMQ está excluida deliberadamente. La configuración propia
consume tres eventos de `foodly.business` solo con activación explícita, MongoDB
replica set y variables Rabbit válidas. No modifica recursos Business/Radar. Cola
durable quorum `foodly.community.business.events`, DLX/DLQ propios, deduplicación
persistente e inbox/proyección atómicos. La [guía de mensajería](docs/messaging.md)
explica contrato real, bindings, reintentos, activación y recuperación. El arranque
predeterminado continúa sin conexiones externas. `RatingUpdated` queda pendiente.

## Diferencias respecto de Business

Business utiliza Java 17 y Spring Boot 3.5.16 según su POM local, además de MongoDB,
RabbitMQ y JWT. Ambos servicios quedan alineados en Java y Spring Boot.
Community mantiene su propio modelo DDD y puertos hexagonales, sin reutilizar
entidades, repositorios, colecciones ni configuración de Business. MongoDB Atlas
se adopta por la decisión explícita para Community, con base independiente. Los demás
contextos son IAM, Business, Radar, Reservation y Messaging; sus integraciones
no se presuponen. Véase [contracts.md](docs/contracts.md).

## Verificación

La prueba `CommunityServiceApplicationTests` comprueba el arranque del contexto sin
beans de integración externa (incluidos cliente y operaciones MongoDB), salud pública
sin detalles, bloqueo de API y de otros endpoints Actuator, denegación incluso con
usuario autenticado o Bearer token sin validar y protección CSRF.
La evidencia de la ejecución de esta entrega está en [verification.md](docs/verification.md).
La [auditoría previa al primer commit](docs/pre-publication-audit.md) identifica
archivos publicables, exclusiones privadas, estado Git, propuesta de commits y
pendientes de integración. No equivale a una certificación de producción.

Las pruebas unitarias del dominio cubren creación e invariantes, identidad compuesta,
igualdad e inmutabilidad de objetos de valor, comentario opcional y reputación.
El contrato reutilizable de favoritos se ejecuta con un doble de pruebas y con
MongoDB real temporal. Las nuevas pruebas de Infrastructure verifican el índice
único y la prevención de duplicados concurrentes, sin usar Atlas o datos existentes.

Las pruebas de Application usan JUnit 5 y Mockito con mocks de repositorios. La
verificación de esa fase fue de 136 pruebas exitosas: las 76 previas y 60 nuevas; no
requieren MongoDB, JWT o RabbitMQ. No se modificaron las reglas de los agregados
ni sus objetos de valor. La extensión del repositorio y el ajuste del doble de
pruebas están documentados, sin cambiar los métodos de las pruebas anteriores.

La verificación anterior de REST fue de 202 pruebas exitosas, incluidas las 136
anteriores y 66 nuevas. Se distinguen pruebas unitarias/MockMvc aisladas con mocks
de las pruebas de contexto con la cadena real de seguridad. Ninguna integra IAM
o bases de datos reales. Domain, Application, Infrastructure y las pruebas previas
se conservaron sin cambios durante esa fase.

La verificación de persistencia fue de 243 pruebas exitosas: las 202 anteriores y 41
nuevas, incluidas 15 contra MongoDB real temporal. Docker no estaba disponible;
se inició y cerró un mongod 8.0.1 aislado en un directorio nuevo y puerto dinámico.
La suite prefiere Testcontainers cuando Docker está disponible. El build completo
requiere uno de esos runtimes para la integración; el arranque básico y los tests
unitarios/de contexto no requieren MongoDB externo. No hubo omisiones.

La verificación actual es **BUILD SUCCESS**, con **360 pruebas exitosas**, cero
fallos, errores u omisiones. Se conservan las **312** previas y se añaden **48**.
La fase de mensajería añade parser, ACK, reintentos, DLQ,
transacciones y recuperación contra RabbitMQ y replica set MongoDB temporales.
Resultado final y distinción entre infraestructura real y fallos simulados en
[docs/verification.md](docs/verification.md). Domain, Application, REST y JWT se conservan.

No se modifican otros servicios ni frontend. No se crean repositorios remotos,
commits o credenciales. Solo las pruebas de integración crean su servidor temporal.

## Siguiente fase

Domain, Application y la interfaz REST están implementados para el alcance solicitado.
La persistencia, JWT y composición están implementados y probados con claves
efímeras y MongoDB temporal. Falta verificar un entorno autorizado contra IAM
desplegado. El contrato estricto requiere acordar iss/aud y modificar su emisor
en su propio proyecto; Community no inventa esos valores ni cambia IAM.
Siguen pendientes permisos, paginación/orden, presentación del promedio y formatos
externos de IDs. El consumidor RabbitMQ está implementado; siguen pendientes
la operación autorizada del entorno, replay/retención/reconciliación y el contrato
de publicación de reputación. No se publica RatingUpdated sin acuerdo.
