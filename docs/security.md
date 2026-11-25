# Seguridad JWT y composición de Community

## Evidencia IAM y discrepancia con Business

Revisión de solo lectura del 24 de noviembre de 2026:

- No se encontró un checkout local auténtico de IAM. Se inspeccionó su código en
  GitHub mediante el conector, sin ejecutar ni modificar ese servicio.
- [JwtTokenIssuer de IAM](https://github.com/1ASI0728-2620-9052-FoodNode/Foodly-IAM-Service/blob/df6ac4581579b98eee67ab4dc55e78771d0f77f5/src/main/java/com/foodly/iam/infrastructure/security/JwtTokenIssuer.java)
  coincide con el contenido revisado de develop (blob `9cc70dd3a64e8ef521bab19ec275cccc87e36ca5`).
  Firma explícitamente HS256 con clave Base64 de al menos 32 bytes decodificados.
  Emite sub, role, iat y exp; **no emite iss ni aud**. La duración configurada es
  de 1 a 60 minutos; sub procede del ID de usuario UUID convertido a String.
- [Role de IAM](https://github.com/1ASI0728-2620-9052-FoodNode/Foodly-IAM-Service/blob/develop/src/main/java/com/foodly/iam/domain/model/enums/Role.java)
  (blob `00446ea19cbf3ebd44c354e1a7654492c885cdaa`) define GASTRONOMIC_EXPLORER y
  HUARIQUE_OWNER. El POM revisado usa JJWT 0.13.0 y Spring Boot 4.1.2. Community
  conserva Java 17/Boot 3.5.16: no necesita compartir versión Spring para validar JWT.
- La configuración local de Business exige issuer/audience. Sus propios documentos
  describen que esa propuesta estricta todavía no es compatible con el emisor IAM.
  No se copiarán sus valores de prueba ni se retirará una validación silenciosamente.

Esto verifica el formato del código, no un login desplegado, la clave operativa,
rotación, revocación o la configuración de un entorno real de IAM.

## Contratos explícitos, sin fallback

JWT está desactivado por defecto. Al activarlo, IAM_JWT_CONTRACT debe seleccionarse:

| Contrato | Comportamiento |
| --- | --- |
| `iam-current` | Formato observado de IAM. Exige ausencia de iss/aud. IAM_ISSUER e IAM_AUDIENCE deben quedar vacíos; configurarlos falla en vez de ignorarlos. |
| `issuer-audience` | Exige ambos valores externos y los claims correspondientes; verifica igualdad de issuer y pertenencia de audience. El IAM revisado no emite ese formato. |

No se elige un contrato inspeccionando el token, no se reintenta con otro contrato
cuando una validación falla y no existe modo genérico que simplemente ignore
issuer/audience. El contrato estricto continúa rechazando el formato actual de IAM.
Elegir iam-current es una decisión explícita para el productor observado, no una
afirmación de compatibilidad con Business ni un cambio realizado en otro servicio.

En ambos contratos se comprueban firma HS256, sub String no vacío/blanco, role
String no vacío/blanco, iat y exp obligatorios, expiración posterior a ahora e iat,
iat no futuro y duración máxima de una hora observada en el productor. nbf se
valida cuando aparece. Se utiliza Clock inyectado y ninguna tolerancia temporal.
No se imponen UUID o longitud arbitraria a la identidad opaca del dominio.

Se rechazan tokens malformados, sin firma y algoritmos distintos de HS256, aun
con la misma clave. Antes de normalizar claims se rechazan tipos incorrectos,
como un sub numérico: Nimbus puede convertirlo a String, pero Community no admite
que esa conversión cree una identidad autenticada. Esa inspección solo rechaza
tipos inválidos; la aceptación siempre requiere validación criptográfica posterior.
La implementación usa [Spring Security Resource Server](https://docs.spring.io/spring-security/reference/6.5/servlet/oauth2/resource-server/jwt.html),
con Nimbus y clave simétrica; no hay JWKS, descubrimiento remoto o llamadas a IAM.

## Identidad y permisos

SpringSecurityCurrentExplorer solo lee un JwtAuthenticationToken autenticado desde
SecurityContextHolder. No analiza el header por sí mismo ni admite un principal de
usuario/contraseña como identidad JWT. Los comandos existentes continúan tomando
la identidad únicamente del puerto; no se modifica Domain o Application.

Las cinco operaciones REST requieren principal JWT autenticado y la autoridad
exacta ROLE_GASTRONOMIC_EXPLORER, derivada del claim **singular role**. No se usan
roles plurales, scopes u otros claims para elevar permisos. Un token correcto de
HUARIQUE_OWNER o de rol desconocido no autoriza esas operaciones (403). IAM confirma
los nombres de roles; esta es la política mínima local para los casos de Community
del explorador. Una ampliación de permisos, por ejemplo lectura para dueños, requiere
un acuerdo explícito y no se implementa aquí.

explorerId en body se rechaza por los DTO actuales. Un query param explorerId o un
header X-Explorer-Id no cambia sub. La pareja usada para favoritos es siempre
(sub, huariqueId), y la creación de reseñas usa ese mismo sujeto validado.

En la API activa, ausencia/token inválido devuelve 401 con WWW-Authenticate: Bearer;
falta de permisos devuelve 403. Ambos errores usan ApiErrorResource con mensajes
genéricos, sin token, secreto, causas o mensajes internos. No se registran tokens
o claves; IamJwtProperties también tiene toString redactado. No hay login de
formulario o Basic. La API es stateless y no conserva autenticación en sesión.
CSRF se ignora únicamente bajo /community/** para esta API exclusivamente Bearer;
fuera de ese namespace permanece activo. En el modo cerrado predeterminado no
se cambia CSRF y las rutas de negocio siguen denegadas (403).

## Dependencias y activación segura

RuntimeAdaptersConfiguration aporta Clock.systemUTC y UuidReviewIdGenerator con
UUID.randomUUID. Ambos pueden sustituirse por beans deterministas en pruebas;
el dominio conserva sus IDs opacos. CommunityApplicationConfiguration registra
los seis handlers solo con las dos integraciones activadas; los repositorios y
puertos son dependencias obligatorias. No hay implementaciones de reserva.

Los controladores usan la misma condición de activación en vez de ConditionalOnBean,
evitando depender del orden del component scan. Solo se cambió su metadata de
registro, no endpoints ni lógica. Spring resuelve todos los handlers obligatorios
o falla antes de exponer una aplicación incompleta. No se agrega un endpoint de
resumen, login, refresh o emisión de JWT.

| JWT habilitado | Mongo habilitado | Resultado |
| --- | --- | --- |
| false | false | Sin conexiones externas; handlers/controladores ausentes; salud pública y negocio bloqueado |
| true | false | Decoder/identidad disponibles si su configuración es válida; sin Mongo ni handlers/controladores; negocio bloqueado |
| false | true | Persistencia explícita configurada; sin decoder ni handlers/controladores; negocio bloqueado |
| true | true | Composición completa si clave/contrato/URI/base/índices son válidos; las cinco rutas exigen JWT y permiso |

Una integración explícitamente habilitada con configuración inválida falla el
arranque; no degrada la seguridad ni sustituye una dependencia. Mongo inaccesible
o fallo de índices también impide arrancar la composición completa. GET
/actuator/health permanece público en los estados que arrancan correctamente.
El estado cerrado continúa disponible sin servicios externos por defecto.

| Variable | Valor inicial / requisito |
| --- | --- |
| COMMUNITY_JWT_ENABLED | false |
| IAM_JWT_CONTRACT | vacío; elegir uno de los dos contratos al activar |
| IAM_SECRET_BASE64 | vacío; clave HS256 acordada con IAM, mínimo 32 bytes tras decodificar |
| IAM_ISSUER / IAM_AUDIENCE | vacíos en iam-current; obligatorios y acordados externamente en issuer-audience |
| COMMUNITY_MONGODB_ENABLED | false |
| MONGODB_URI | vacío; URI del entorno autorizado, no de Business |
| MONGODB_DATABASE | foodly-community, obligatorio al activar persistencia |

El emisor IAM recibe su clave como JWT_SECRET_BASE64; el consumidor Community la
recibe como IAM_SECRET_BASE64. Deben representar los mismos bytes acordados, sin
copiar la clave a código, documentación o logs. No se leyó ni generó una clave real.

## Entorno local de integración

La opción reproducible sin IAM o Atlas reales es:

```powershell
./mvnw.cmd '-Dtest=CommunityJwtMongoIntegrationTest' test
./mvnw.cmd clean verify
```

La prueba crea una clave aleatoria efímera y tokens JJWT 0.13.0 con la forma
observada de IAM; usa los filtros, controladores, handlers y adaptadores MongoDB
reales. Inicia su propio MongoDB temporal (Docker/Testcontainers o mongod nativo),
sin leer MONGODB_URI del entorno ni reutilizar datos. No abre un puerto HTTP de
aplicación: usa MockMvc con el contexto web real. El reloj y generador de reviewId
son deterministas. No exporta la clave ni los tokens para usarlos en otro entorno.

Para ejecución local manual, el operador debe preparar una instancia aislada propia
y aportar URI y clave autorizadas fuera del repositorio. Tras eso se establecen
COMMUNITY_MONGODB_ENABLED=true y COMMUNITY_JWT_ENABLED=true, con selección explícita
del contrato, y se usa `./mvnw.cmd spring-boot:run` en 8083. No basta con activar
flags: faltantes o inconsistencias fallan. Los requests deben usar Authorization:
Bearer con un token emitido por el productor acordado, nunca X-Explorer-Id.

La suite prueba formatos de productor reproducidos, no login remoto de IAM. Para
issuer-audience sigue pendiente acordar valores y cambiar el emisor en su proyecto;
esta tarea no modifica IAM o Business. RabbitMQ permanece fuera de esta integración.
