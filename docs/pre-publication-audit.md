# Auditoría previa al primer commit de Community

Fecha: **24 de noviembre de 2026**, America/Lima.

## Dictamen y alcance

El código y los documentos revisados están preparados para incorporarse a un
repositorio Git con las exclusiones indicadas. No se detectó un defecto crítico
de autorización, atomicidad o exposición de secretos dentro del alcance revisado.
Esto no certifica ausencia de vulnerabilidades ni preparación para producción.

La carpeta **todavía no es un repositorio Git**: no existe `.git`. Su inicialización,
selección de rama, revisión del índice y configuración del remoto siguen pendientes
antes del primer commit/publicación. No se ejecutó git init/add/commit/push ni se
creó un remoto. No se modificaron otros servicios.

Se ejecutó `./mvnw.cmd -B -ntp clean verify`: **BUILD SUCCESS**, salida **0**,
**360 pruebas, 0 fallos, 0 errores, 0 omitidas**, duración 58.445 segundos.
Java Temurin **17.0.21+9**, Maven **3.10.0**, Wrapper **3.3.4**, Boot **3.5.16**.
No se añadieron pruebas ni se modificó código de producción para esta auditoría.

Evidencias locales privadas:

- `.cache/audit-verify.log` y `.cache/audit-test-results.csv`.
- `.cache/audit-source-baseline.csv`: hashes para comprobar conservación de código,
  pruebas, POM y scripts Wrapper durante la auditoría.
- `.cache/audit-secret-indicators.csv`: resultados del análisis heurístico, sin valores.
- `.cache/audit-publication-candidates.txt`: manifiesto de archivos candidatos.
- `.cache/audit-no-index-whitespace.csv`: comprobación complementaria sin índice Git.
- `.cache/audit-public-artifacts.csv`: disponibilidad de artefactos públicos.

## Arquitectura y contratos

| Área | Resultado de la revisión |
| --- | --- |
| Domain | Agregados y objetos de valor inmutables; Rating entero 1–5, IDs no nulos/blancos, comentario opcional; sin Spring/Mongo/Rabbit/REST. |
| Application | Casos de uso sobre puertos y dominio; solo imports Java y paquetes internos Domain/Application. Generador y reloj inyectables. |
| Repositorios | IReviewRepository/IFavoriteRepository independientes; inserción de reseñas sin sobrescritura y unicidad atómica de favoritos. No hay ediciones/bajas de reseñas nuevas. |
| REST | Cinco operaciones documentadas; DTO separados del dominio, validación de rating entero y rechazo de propiedades desconocidas; estados 200/201/204/400/401/403/404/409/500 coherentes con los contratos. |
| Identidad | Los handlers toman explorerId exclusivamente de CurrentExplorer. El adaptador lee el JWT validado. Body, query y headers arbitrarios no permiten cambiar el sujeto. |
| Composición | REST/handlers solo con MongoDB y JWT explícitos; dependencias incompletas fallan cerradas. No hay repositorios simulados de producción. |

La lista de reseñas requiere el mismo permiso GASTRONOMIC_EXPLORER acordado en el
contrato local. No se hace pública ni se amplían roles durante la auditoría.
Los errores HTTP son genéricos y no exponen causas técnicas, tokens o claves.
Los endpoints se detallan en [contracts.md](contracts.md); el resumen de reputación
sigue siendo un resultado de Application, sin endpoint nuevo.

## Integraciones

### MongoDB

Activación explícita, URI externa y base obligatoria `foodly-community`, distinta
de Business. Se rechaza otra base en la URI antes de crear cliente. Las colecciones
de reseñas/favoritos reconstruyen el dominio mediante mappers. El índice compuesto
único `(explorerId, huariqueId)` se asegura antes de exponer el adaptador; duplicados
se traducen a DuplicateFavoriteException. La eliminación usa su resultado atómico.
Los errores técnicos se traducen según los puertos existentes.

Inbox y proyección son documentos de Infrastructure, con `_id` único por eventId
y huariqueId respectivamente. Se guardan dentro de una transacción con
SNAPSHOT/MAJORITY; su activación exige replica set. No se afirma que dos documentos
sean atómicos sobre un standalone. No se modificaron contratos de dominio.
Detalles: [persistence.md](persistence.md) y [messaging.md](messaging.md).

### JWT y compatibilidad IAM

Se reconsultó el [emisor IAM en develop](https://github.com/1ASI0728-2620-9052-FoodNode/Foodly-IAM-Service/blob/develop/src/main/java/com/foodly/iam/infrastructure/security/JwtTokenIssuer.java),
blob `9cc70dd3a64e8ef521bab19ec275cccc87e36ca5`: conserva firma HS256 y claims
`sub`, `role`, `iat`, `exp`; no emite `iss` ni `aud`. Community exige seleccionar
`iam-current` para ese formato o `issuer-audience` para el contrato estricto;
no hay fallback ni valores issuer/audience inventados. El contrato estricto
sigue siendo incompatible con el emisor observado. No se accedió a su clave.

Se revisaron verificación criptográfica, algoritmo exclusivo HS256, sujeto/rol,
expiración, emisión, nbf opcional, tipos originales y duración máxima. Endpoints
activos requieren JWT y rol GASTRONOMIC_EXPLORER; Basic/formulario no proporcionan
identidad válida. Solo GET /actuator/health es público. El modo predeterminado
mantiene el negocio bloqueado y no crea decoder ni conexión externa.

Esto prueba compatibilidad con el **código/formato** IAM, no login desplegado,
secretos operativos, sincronización de relojes, revocación o rotación. Sin iss/aud
no existe restricción criptográfica de audiencia por servicio: debe acordarse su
evolución antes de asumir aislamiento de tokens entre contextos que comparten clave.
La implementación actual no se relajó para ocultar esa limitación.

### RabbitMQ

El consumidor valida schemaVersion 1 y los tres eventos Business españoles. El
exchange `foodly.business` se verifica pasivamente; solo se declaran recursos
Community. Cola quorum durable, ACK manual tras commit, inbox persistente,
checkpoint creciente, reintentos limitados con contador del broker y DLQ propia.
Los snapshots completos permiten saltos de versión sin aplicar eventos obsoletos.
No se prometen entregas exactamente una vez ni se consumen colas Business/Radar.

Una pérdida de ACK tras commit produce una entrega repetida sin duplicar el efecto.
El consumidor permanece apagado por defecto. Para transporte remoto deben acordarse
TLS, truststore, vhost y permisos mínimos. TLS verifica certificado y nombre de host
cuando se activa; el valor false está destinado al entorno local aislado, no implica
que exista una conexión productiva segura por defecto.

## Evidencia real y simulada

La ejecución completa usó MongoDB **8.0.1** y RabbitMQ **4.2.9** / Erlang
**27.3.4.19**, creados temporalmente con puertos loopback y directorios propios.
El consumidor usa replica set temporal; los tests Mongo anteriores conservan su
fixture anterior. Docker no está disponible en esta máquina: se ejecutó la ruta
nativa. No hubo pruebas omitidas por esa elección.

Las pruebas integradas comprueban índices/duplicados concurrentes, API autenticada
con criptografía real y datos efímeros, transacciones, inbox, routing, contador de
reentregas y DLQ. Las 15 pruebas de eventos distinguen infraestructura real de
fallos Mongo inyectados con Mockito. Se reproduce commit sin ACK cerrando el canal
y recreando el store; no equivale a reiniciar todo un broker o un cluster físico.

Los tests unitarios de Application y parte de REST usan mocks; no demuestran una
integración remota. No se accedió a IAM desplegado, Atlas, CloudAMQP, Rabbit existente,
bases Business ni infraestructura de otros equipos. No se probaron failover,
particiones, carga, recuperación de un cluster o credenciales reales.

## Seguridad de archivos y cambios justificados

El análisis inicial revisó **133 archivos candidatos**, sin `.cache/`, `target/`,
`.vscode/` ni metadatos Git. Se buscaron claves privadas, tokens GitHub/AWS, JWT
literales, URIs con credenciales y asignaciones literales de secretos. Resultado:
**0 indicadores**. `.env.example` mantiene vacíos los secretos y desactivadas las
integraciones. Las claves de tests se generan efímeramente, no son credenciales IAM.
El análisis final incorpora también este informe: **134 archivos candidatos,
0 indicadores de secretos y 0 diagnósticos de whitespace**. La comparación de
hashes confirmó **0 cambios** en fuentes, pruebas, POM y Wrapper.

El escaneo es heurístico y la revisión es estática: no sustituye gitleaks,
secret scanning de GitHub o un análisis SCA/CVE actualizado. No había historial Git
que pudiera auditarse y no se declara que las dependencias estén libres de CVE.

Cambios realizados, sin tocar código Java/POM ni borrar archivos:

- `.gitignore`: cobertura de `.env` anidados, `.local`, logs rotados, temporales,
  volcados, respaldos, almacenes de claves y compilados fuera de target. Solo la
  plantilla raíz `.env.example` queda exceptuada. El índice futuro debe revisarse
  igualmente: ignorar archivos no sustituye verificar lo que se agrega.
- `README.md`: requisitos de pruebas desde un clon limpio y enlace a esta auditoría.
- `docs/project-tree.md`: inventario publicable actualizado, sin `.vscode/`, y finales
  de línea LF acordes con `.gitattributes` (se detectaron CRLF en su versión previa).
- `docs/verification.md`: evidencia de esta compilación de auditoría.
- `docs/pre-publication-audit.md`: este dictamen y plan de publicación.

### Archivos seguros para incorporar

La lista exacta está en [project-tree.md](project-tree.md). Incluye únicamente:

- `src/main/**` y `src/test/**` revisados.
- `pom.xml`, `mvnw`, `mvnw.cmd`, `.mvn/maven.config`, `.mvn/wrapper/maven-wrapper.properties`.
- `.gitignore`, `.gitattributes`, `.env.example`.
- `README.md` y `docs/*.md`.

### Archivos privados o generados que no se incorporan

`.env`/variantes reales, `.cache/**` (dependencias, binarios y evidencia local),
`target/**` (JAR/classes/reportes), `.vscode/**`, `.local/**`, claves/keystores,
logs, volcados, respaldos y datos de brokers/BD. No se copian secretos ni se publica
el runtime Rabbit/Erlang local. Los reportes de tests pueden contener datos efímeros
y rutas de máquina; el informe público solo resume resultados sin esos valores.

## Entregabilidad desde otro equipo

Las URLs públicas fijadas para Maven 3.10.0, Wrapper 3.3.4 y el POM Boot 3.5.16
respondieron HTTP **200** durante la auditoría. Se verificó el Wrapper real. No se
simuló una descarga completa desde un clon/caché vacíos ni se ejecutó Linux/macOS.

El build completo requiere JDK 17, acceso a Maven Central y Docker operativo para
Testcontainers, o los runtimes nativos documentados. La ruta Docker está implementada,
pero no fue ejecutada aquí. La caché privada presente en esta máquina no acompaña
un clon. El README ahora hace explícito ese requisito y enlaza la preparación nativa.
Spring no carga `.env` automáticamente; las variables deben exportarse/inyectarse.

Dockerfile y Compose **no existen y no son requisito del alcance actual**. No se
añaden para aparentar un despliegue probado. La suite usa infraestructura aislada,
sin depender de un Compose compartido. La decisión de empaquetado/despliegue queda
para su fase; el artefacto JAR se genera correctamente.

## Git y propuesta de commits

| Comando solicitado | Resultado real |
| --- | --- |
| `git status` | Falla: not a git repository. |
| `git branch --show-current` | Falla por ausencia de repositorio; no hay rama que reportar. |
| `git remote -v` | Falla por ausencia de repositorio; no hay remoto verificable. |
| `git diff --check` | No aplicable sobre este directorio sin repositorio; falla. |

Se complementó con comparación `git diff --no-index --check` de los candidatos
contra `/dev/null`, sin crear índice/repositorio, y revisión de marcadores de
conflicto. La comparación de archivos nuevos puede devolver 1 por diferencias;
eso no equivale a error de whitespace. Se corrigieron únicamente los finales de
línea del documento de inventario. No se afirma haber validado un diff staged.

Business se consultó **solo en lectura**: sus títulos de commits son `Initial commit`,
`feat(business): implement bounded context with tests and Maven build`,
`chore(dev): add local infrastructure and verification helpers`, y
`docs(business): document APIs, events and integration limits`.
La excepción safe.directory se pasó solo a esa invocación; no se modificó Git global.

Para Community se proponen dos commits coherentes y verificables:

1. `feat(community): implement bounded context with tests and Maven build`:
   `src/**`, POM, Wrapper completo y `.mvn/**`, `.gitignore`, `.gitattributes`,
   `.env.example`. Incluye los fixtures aislados necesarios para que las pruebas
   compilen y funcionen sin depender de un commit posterior.
2. `docs(community): document APIs, events, security and publication audit`:
   `README.md` y `docs/**`.

No se inventa un commit chore vacío: Community no tiene los helpers operativos
separados que Business añadió; sus fixtures pertenecen a las pruebas del primer
commit. Después de inicializar Git y antes de publicar se debe revisar la lista
staged y repetir git diff --check sobre el índice. Nada de ello se ejecutó aquí.

## Clasificación de pendientes

| Pendiente | Clasificación | Criterio de cierre |
| --- | --- | --- |
| Inicializar Git, seleccionar rama y revisar índice/remote | **Bloqueante para publicar** | Hacerlo en una tarea autorizada; confirmar exclusivamente el manifiesto revisado y cero archivos privados staged. |
| Exclusiones y requisitos del clon poco explícitos | **Bloqueante para publicar: resuelto** | .gitignore y README corregidos; inventario y evidencia actualizados. |
| Contrato JWT real y entorno IAM | **Necesario para integración con Foodly** | Acordar contrato, secreto mediante canal privado, relojes/permisos y prueba autorizada; si se adopta iss/aud, IAM debe emitirlos. |
| MongoDB replica set, permisos/índices y Rabbit TLS/vhost/topología | **Necesario para integración con Foodly** | Provisionar un entorno autorizado y comprobar conexión sin usar datos de otros contextos. |
| Reconciliación inicial de huariques | **Necesario para integración con Foodly** | Acordar snapshot/replay con Business: una cola nueva no recibe eventos anteriores a su creación. No cambiar reglas de reseñas sin acuerdo. |
| Replay seguro de DLQ | **Necesario para integración con Foodly** | Runbook autorizado que conserve eventId/envelope, limite intentos y evite bucles; probar en entorno aislado antes de operarlo. |
| Retención del Inbox | **Mejora operativa posterior** | Dimensionar y acordar ventana de replay antes de purgar. Actualmente no hay TTL/purga; borrar registros puede permitir reprocesamiento. |
| Monitoreo | **Mejora operativa posterior** | Métricas/alertas de lag, DLQ, errores de transacción y disponibilidad; health actual no acredita esas integraciones. |
| RatingUpdated | **Necesario para integración con Foodly, si se confirma el requisito** | Primero validar informe y acordar esquema, destinos y garantías; no existe publicación ni consumidor/exchange inventado. |
| SCA/secret scanning continuo, checksums Wrapper, CI | **Mejora operativa posterior** | Automatizar y revisar resultados antes de un despliegue; no hay certificación CVE en esta entrega. |
| Failover, particiones, carga y reinicio de cluster | **Mejora operativa posterior** | Pruebas en infraestructura controlada antes de asumir disponibilidad productiva. |

El resultado permite preparar el primer commit a partir del manifiesto revisado.
La publicación efectiva queda pendiente de los pasos Git; la integración real y
la operación productiva requieren los acuerdos y verificaciones separados arriba.
