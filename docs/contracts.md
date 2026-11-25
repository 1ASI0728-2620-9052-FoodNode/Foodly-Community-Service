# Contratos de integración pendientes

Los endpoints REST requeridos están implementados en código, con recursos propios
y con persistencia MongoDB y JWT activables explícitamente. Por defecto permanecen
bloqueados; la composición completa exige ambas activaciones y sus dependencias
válidas. El consumidor Business usa su esquema publicado; no existen clientes remotos IAM. Los contratos definidos
en esta fase se distinguen abajo de los acuerdos de integración todavía pendientes.

| Contexto | Acuerdo pendiente |
| --- | --- |
| IAM | Formato revisado y soportado con selección explícita; pendientes entorno operativo, login remoto y acuerdo/cambio del emisor si se adopta issuer-audience |
| Business | Identificador y referencia del huarique, validación de existencia/estado, acceso autorizado, política ante indisponibilidad y cambios/bajas |
| Radar | Confirmar si necesita reputación/resumen de reseñas y si el intercambio es por API o eventos |
| Reservation | Confirmar si existe alguna interacción; no se presupone requisito de reserva para reseñar |
| Messaging | Confirmar si consume hechos de Community, sus destinatarios y responsabilidades |

## API REST de Community

| Método y ruta | Entrada | Resultado del controlador |
| --- | --- | --- |
| `POST /community/huariques/{huariqueId}/reviews` | `CreateReviewResource` | 201 y `ReviewResource` |
| `GET /community/huariques/{huariqueId}/reviews` | ID en ruta | 200 y array de `ReviewResource`; `[]` si no hay reseñas |
| `POST /community/favorites/{huariqueId}` | ID en ruta, sin recurso de entrada | 201 y `FavoriteResource` |
| `DELETE /community/favorites/{huariqueId}` | ID en ruta, sin recurso de entrada | 204 sin cuerpo, o 404 si no existía |
| `GET /community/favorites/me` | Sin selector de explorador | 200 y array de `FavoriteResource`; `[]` si no hay favoritos |

Estos resultados se verificaron con filtros JWT, handlers y adaptadores MongoDB
reales contra un servidor temporal. **Las rutas no se habilitan por defecto**:
requieren ambas activaciones y autenticación/autorización válidas. No se agrega
un GET individual, paginación, orden o formato de ID del dominio.
Los paths antiguos `/reviews` y `/favorites` se conservan solo en pruebas históricas
de denegación; no son aliases de los nuevos endpoints.

### Recursos y validación

`CreateReviewResource` admite únicamente `rating` y `comment`:

```json
{"rating": 5, "comment": "Buen sabor"}
```

`rating` es obligatorio, entero JSON entre 1 y 5. Jakarta Bean Validation aplica
`NotNull`, `Min` y `Max`; un deserializador local impide convertir decimales,
booleanos o cadenas a enteros. También se rechazan enteros fuera del rango Java int.
`comment` es opcional y no tiene límite de longitud arbitrario; el dominio conserva
el contenido y representa la ausencia con texto vacío. Las propiedades adicionales,
incluidas `explorerId`, `reviewId` y `createdAt`, se rechazan con 400; no se ignoran
silenciosamente aunque la configuración global de Jackson permita ignorar campos.

`ReviewResource` devuelve `reviewId`, `explorerId`, `huariqueId`, `rating` entero,
`comment` como texto y `createdAt` ISO-8601 UTC. `FavoriteResource` devuelve
`explorerId` y `huariqueId`, sin inventar una identidad adicional. Ningún controlador
serializa entidades u objetos de valor del dominio directamente.

POST y DELETE de favoritos no necesitan cuerpo. Los objetos JSON enviados como
cuerpo, incluido un selector explorerId, se rechazan con 400. Las identidades que
aparecen en respuestas proceden del resultado del caso de uso, nunca de parámetros
o headers del cliente. Los IDs de ruta se validan con `NotBlank`, sin exigir UUID
u ObjectId; ausencia del segmento implica que no existe esa ruta HTTP.

### Errores HTTP

`RestExceptionHandler` usa `RestControllerAdvice` y un recurso público fijo:

```json
{"status": 400, "code": "INVALID_REQUEST", "message": "Invalid request"}
```

| Condición | HTTP | Código |
| --- | ---: | --- |
| JSON mal formado, entrada o validación incorrecta | 400 | `INVALID_REQUEST` |
| `AuthenticationRequiredException` de Application | 401 | `AUTHENTICATION_REQUIRED` |
| Favorito inexistente | 404 | `FAVORITE_NOT_FOUND` |
| Favorito duplicado | 409 | `FAVORITE_ALREADY_EXISTS` |
| Fallo de persistencia o inesperado | 500 | `INTERNAL_ERROR` |

401 incluye `WWW-Authenticate: Bearer`. La cadena JWT activa devuelve el mismo
formato público para errores de autenticación y 403 ACCESS_DENIED para permisos.
El advice conserva los estados MVC correspondientes, por ejemplo 405 y 415, con
un mensaje público genérico. No publica exception.message, causas, trazas, datos
rechazados, rutas con parámetros o credenciales. Los errores de filtros de seguridad
ocurren antes de MVC y no son traducidos por este advice.

### Autenticación y registro de controladores

Los controladores no reciben ni construyen explorerId. Los handlers lo obtienen
únicamente de `CurrentExplorer`, cuyo adaptador SpringSecurityCurrentExplorer usa un principal
verificado por seguridad. `X-Explorer-Id`, un Bearer sin verificar o un query param
explorerId no alimentan ese puerto. No existe autenticación de prueba en producción.

Solo GET /actuator/health es público. Con JWT o MongoDB desactivados el negocio
continúa denegado (403) y los handlers/controladores no se registran. Con ambas
integraciones listas, las cinco operaciones requieren JwtAuthenticationToken
autenticado y ROLE_GASTRONOMIC_EXPLORER. Solo el claim singular role concede esa
autoridad; roles plurales y scopes no elevan permisos. Un dueño o rol desconocido
no autoriza las operaciones del explorador. No se agrega acceso administrativo.

Los controladores cambian únicamente su condición de registro: usan las mismas
dos propiedades que CommunityApplicationConfiguration, evitando depender del orden
de descubrimiento de beans. Handlers y puertos son obligatorios; una configuración
incompleta falla el arranque en vez de exponer una aplicación parcial. No se usan
dependencias nulas o repositorios simulados. La API activa es stateless y Bearer;
CSRF se ignora solo bajo /community/**, no globalmente ni en el modo cerrado.
Evidencia IAM y guía de activación: [security.md](security.md).

### Resumen de reputación

`GetReviewSummaryByHuariqueQueryHandler` y `ReviewSummary` permanecen como contrato
de Application: huarique, cantidad y promedio opcional, con count=0/promedio ausente
sin reseñas. **Esta fase no añade endpoint HTTP ni DTO público de resumen**; tampoco
implementa `/reviews/summary`. Su exposición y presentación quedan pendientes de
requisitos explícitos. No se agregan operaciones CRUD, moderación o respuestas a
reseñas adicionales; `ReviewComment` no implica un caso de uso nuevo.

## Repositorios

`AggregateRepository<T, ID>` conserva `Optional<T> findById(ID)` y `T save(T)` sin
dependencias de tecnología. Las especializaciones de esta fase son:

| Contrato | Operaciones y tipos |
| --- | --- |
| `IReviewRepository` | `save(Review)`, `findById(String)` y `List<Review> findByHuariqueId(String)` |
| `IFavoriteRepository` | `save(Favorite)`, `findById(FavoriteId)`, `List<Favorite> findByExplorerId(String)` y `boolean deleteById(FavoriteId)` |

Los métodos de consulta devuelven vacío cuando no hay resultados; no se acuerda
orden ni paginación todavía. `save` significa insertar, sin sobrescribir ni editar
un agregado existente. Para una reviewId repetida se exige `IllegalStateException`;
no se impone unicidad de reseña por pareja explorador-huarique.

La identidad del favorito es la pareja `FavoriteId(explorerId, huariqueId)`.
`IFavoriteRepository.save` exige inserción atómica y lanza `DuplicateFavoriteException`
cuando esa pareja existe. El índice único MongoDB `(explorerId, huariqueId)`
ya lo respalda en el adaptador, incluyendo escrituras concurrentes. No alcanza con consultar
existencia antes de insertar. El adaptador traduce el error técnico a la excepción
de dominio, sin exponer el driver en el puerto.

`FavoriteRepositoryContract` se ejecuta tanto contra el doble previo en memoria
como contra MongoDB real temporal. Se corrigió una aserción containsSame a contains:
el contrato requiere identidad de dominio, no conservar la instancia Java tras
persistir. Las nuevas pruebas verifican el índice real y escrituras concurrentes.
No se ha probado una instalación Atlas real. Véase [persistence.md](persistence.md).

La fase Application agrega únicamente `deleteById(FavoriteId)` al puerto de favoritos.
Debe eliminar la pareja de forma atómica y devolver true si eliminó o false si no
existía al intentar eliminar. No se realiza consulta previa: evita reportar éxito
si el registro desaparece entre lectura y escritura. El handler transforma false
en `FavoriteNotFoundException`. No se modifica ninguna regla de los agregados.

Los fallos técnicos de todos los métodos del puerto se expresarán mediante
`RepositoryAccessException`, conservando la causa original. Es una excepción
independiente de infraestructura; el adaptador futuro traducirá errores del driver.
No se envuelve `DuplicateFavoriteException` ni el conflicto de reviewId en ese tipo.
Los handlers propagan esas excepciones sin reintentar escrituras ni devolver un
resultado vacío o exitoso cuando hubo un fallo. Interfaces traduce el fallo técnico
a 500 genérico; duplicados y favoritos inexistentes tienen su mapeo específico.

El doble existente de favoritos recibió únicamente el método necesario para
implementar la interfaz ampliada; sus cinco pruebas de contrato previas se conservan.
La eliminación se prueba además con MongoDB real temporal, incluidas dos llamadas
concurrentes que reportan una sola eliminación exitosa.
No se usan documentos MongoDB o entidades JPA en los contratos.

## Identidad y entradas de Application

- `CreateReviewCommand(huariqueId, rating, comment)` no contiene explorerId,
  reviewId ni fecha. `ReviewIdGenerator` provee el ID y `Clock` el instante.
- `AddFavoriteCommand(huariqueId)` y `RemoveFavoriteCommand(huariqueId)` no
  contienen un selector de explorador.
- `GetFavoritesByExplorerQuery()` no tiene campos: consulta al explorador actual.
- Las queries de reseñas y resumen reciben solamente huariqueId, validado como
  cadena no nula ni en blanco; no se presupone formato, orden o paginación.

`CurrentExplorer.explorerId()` devuelve un Optional no nulo con la identidad del
principal JWT autenticado, o vacío cuando no existe esa autenticación. Su adaptador
lee el sujeto del token previamente validado según el contrato IAM;
ni cuerpos, rutas ni parámetros del cliente son fuentes válidas de esa identidad.
`requireExplorerId()` rechaza ausencia, vacío y blanco con
`AuthenticationRequiredException`. No se aceptan valores alternativos del comando
ni se implementa acceso administrativo a favoritos de otro explorador.

Este puerto es un límite de confianza, no un autenticador. La composición inyecta
SpringSecurityCurrentExplorer; el Resource Server valida criptografía y claims
antes de ejecutar el controlador. El acceso permanece cerrado por defecto y al
activarlo aplica el permiso mínimo GASTRONOMIC_EXPLORER. Ampliarlo requiere acuerdo
explícito. No se ha probado login remoto o una clave operativa de IAM.

Los comandos delegan invariantes a `Review`, `Favorite`, `Rating` y `ReviewComment`.
No validan existencia remota de huariques o exploradores. Los resultados de creación
son los agregados inmutables existentes; no se duplican modelos del dominio.

## Resultados de Application

Las consultas de reseñas/favoritos devuelven listas inmutables y vacías si no hay
resultados. No ordenan ni paginan. `ReviewSummary(huariqueId, reviewCount,
averageRating)` es un resultado de aplicación, no un DTO HTTP. Obtiene cantidad
y promedio de la misma lista de reseñas; delega la media a `ReputationService`.
Sin reseñas: count=0 y promedio vacío. No se agrega escala de presentación ni
promedio persistido. El repositorio debe respetar su contrato de filtro por huarique.

No se devuelven listas vacías frente a `RepositoryAccessException`. Las causas
técnicas deben registrarse/gestionarse en el adaptador futuro, sin exponerlas como
respuesta HTTP. El advice actual responde 500 genérico. Las reglas de reintento,
transacciones y consistencia siguen abiertas.

## Eventos y RabbitMQ

Se implementó el consumidor del exchange topic existente `foodly.business` según
el [contrato real de Business](https://github.com/1ASI0728-2620-9052-FoodNode/Foodly-Business-Service/blob/feature/business/docs/contracts.md).
Las routing keys son `business.HuariqueRegistrado`,
`business.PerfilDelHuariqueActualizado` y `business.EstadoAbiertoCerradoCambiado`.
El `type` JSON conserva el nombre español sin prefijo. Se admite schemaVersion 1.
La proyección guarda únicamente identidad y checkpoint; no impone otra regla REST.

Cola propia `foodly.community.business.events`; DLX `foodly.community.business.dlx`;
DLQ `foodly.community.business.dlq`, routing key de descarte `business.dead`.
No se modifica el exchange Business ni se consumen colas de otros contextos.
Activación explícita `COMMUNITY_BUSINESS_EVENTS_ENABLED=false` por defecto;
requiere MongoDB replica set y configuración Rabbit externa válida.
La autoconfiguración genérica sigue excluida: la composición propia controla el listener.

ACK manual tras commit atómico de inbox/proyección, deduplicación persistente por
eventId y descarte de versiones iguales/inferiores. Reintentos limitados mediante
quorum/x-delivery-count y DLQ. Detalles del envelope, índices, topología, permisos,
recuperación y limitaciones en [messaging.md](messaging.md).

`RatingUpdated` queda pendiente: no se dispone de un esquema/acuerdo público ni
del informe íntegro para confirmarlo. No se publican eventos Community ni se
inventan destinatarios, exchanges o contratos de reputación.

## JWT HS256 implementado y contratos explícitos

Se revisó IAM develop y su emisor en df6ac4581579b98eee67ab4dc55e78771d0f77f5.
Firma HS256 con clave Base64 de al menos 32 bytes y emite sub, role, iat y exp,
sin iss/aud. Business exige esos dos últimos claims y continúa siendo incompatible
con ese formato. No se modificó Business/IAM ni se leyeron claves privadas.

COMMUNITY_JWT_ENABLED=false es el valor inicial. Al activar se exige seleccionar
IAM_JWT_CONTRACT=iam-current o issuer-audience. El primero exige el formato observado
sin iss/aud y rechaza valores issuer/audience configurados que quedarían ignorados.
El segundo exige esos valores externos y los valida, sin fallback; el acuerdo e
implementación en IAM siguen pendientes. No se inventan issuer o audience.
Ambos validan firma/algoritmo HS256, sujeto/rol String no blanco, iat/exp obligatorios,
vigencia y duración máxima de una hora observada en IAM; nbf se valida si existe.
Se comprueban tipos originales para evitar coerciones de claims registrados.
Las diferencias completas y evidencias están en [security.md](security.md).

## Persistencia y reputación

La elección de **MongoDB Atlas** queda confirmada por la instrucción explícita del
24 de noviembre de 2026. La base `foodly-community` es independiente de Business;
`MONGODB_URI` y `MONGODB_DATABASE` proporcionan la configuración externa. Las
autoconfiguraciones genéricas permanecen excluidas, pero la configuración propia
activa los adaptadores al definir `COMMUNITY_MONGODB_ENABLED=true`. La base debe
ser foodly-community; una base diferente en la URI también se rechaza antes de
crear un cliente. Sin activación no hay conexión ni fallback local. No se accedió
a Atlas o bases persistentes existentes durante esta entrega; las pruebas solo
usaron su MongoDB temporal. Los documentos, mappers e índices de ReviewRepository
y FavoriteRepository ya están implementados en Infrastructure, sin cambios en los
puertos del dominio. El detalle está en [persistence.md](persistence.md).

La instrucción de la fase Domain define reputación como promedio de las calificaciones
de las reseñas del huarique. Se implementa con suma exacta y división BigDecimal
DECIMAL128; la ausencia de reseñas se representa con `Optional.empty()`. No existe
umbral mínimo, promedio persistido ni sincronización con Business. La escala de
presentación y la consistencia de lectura siguen pendientes.

El comentario no es obligatorio ni tiene un límite de longitud acordado. Se representa
la ausencia con contenido vacío; no se recorta texto. Los IDs se validan únicamente
como cadenas no nulas ni en blanco hasta acordar su formato externo.
