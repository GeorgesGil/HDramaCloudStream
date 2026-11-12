# Informe final (versión 3)

Fecha: 2026-11-12.

## 1. Resumen de lo implementado

Se entregó la versión 3 del plugin **HDrama** para CloudStream, escrito en
Kotlin y empaquetado como `HDrama.cs3`. La versión 2 ya integraba el HTML real
del sitio, pero su capa de red abría un WebView nuevo por petición y la
reproducción nunca llegaba a resolver el HLS. La versión 3 reescribe esa capa:

- **Una sola sesión de Cloudflare.** `WebViewCloudflareSession` resuelve el
  Managed Challenge una vez por proceso con un único WebView (cookie
  `cf_clearance` + User-Agent del WebView) y todas las peticiones posteriores
  van por OkHttp.
- **Fetcher principal por OkHttp.** `HdramaHttpFetcher` envía `cookie`,
  `user-agent`, `referer: https://es.hdrama.net/` y `accept-language`; en
  `/api/` usa `accept: application/json`. Serializa peticiones, renueva la
  sesión ante 403/503 y deja el WebView solo como respaldo.
- **Warm-up.** El plugin adelanta el challenge al cargarse y el cliente de
  fuentes carga la portada antes de `/api/`; el WAF de esa ruta no resuelve
  challenges.
- **Fuentes HLS directas.** Payload de `/api/episode-source` descifrado con
  AES-256-GCM (fuente Stardust) y `HdramaSourceClient` emitiendo solo URLs
  directas, sin proxy ni cabeceras.
- **Comprobación de enlaces.** `HttpSourceProbe` verifica las URLs de la cadena
  (manifiesto HLS o 1 KB del MP4, 6 s, sin `Referer`) y descarta las muertas
  antes de entregarlas al reproductor.
- **Reproducción y descarga.** `HDramaProvider` emite
  `ExtractorLinkType.M3U8` con URL directa y `loadLinksTimeoutMs = 90_000`;
  ExoPlayer reproduce el HLS y CloudStream descarga los segmentos `.ts`
  concatenándolos en un MP4.
- **Ocho secciones**: Destacados, Novedades, Más vistos, Tendencias, Catálogo
  general, Géneros, Fuentes y Acceso a HDrama (sitio oficial, importar enlace y
  demo).
- **Respaldo completo**: si Cloudflare, la red o los hosts de video fallan, el
  plugin sigue mostrando catálogo, fichas de navegación y el modo demo.

## 2. Estructura de archivos

```text
HDramaCloudStream/
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── gradlew / gradlew.bat
├── gradle/wrapper/
├── README.md
├── CHANGELOG.md
├── repo.json
├── .github/workflows/build.yml
├── Docs/ (00..09 + README.md + images/)
└── HDrama/
    ├── build.gradle.kts
    └── src/
        ├── main/kotlin/com/hdrama/
        │   ├── HDramaPlugin.kt
        │   ├── HDramaProvider.kt
        │   ├── PluginInfo.kt
        │   ├── catalog/
        │   ├── models/
        │   ├── network/
        │   ├── parser/
        │   ├── player/
        │   ├── sources/
        │   └── utils/
        └── test/ (fixtures reales recortadas + suites JVM)
```

Los archivos nuevos de la v3 están en `network/`:
`CloudflareSession.kt`, `WebViewCloudflareSession.kt` y
`HdramaHttpFetcher.kt`; en pruebas, `network/HdramaHttpFetcherTest.kt`.
La sonda de enlaces vive en `sources/SourceProbe.kt` (con
`SourceProbeFactory` en `HdramaRuntime.kt`) y su suite en
`sources/SourceProbeTest.kt`.

## 3. Versiones utilizadas

| Componente | Versión |
|---|---|
| CloudStream probado | 4.9.1 / pre-release 2026-11-09 |
| API de plugins | 1 |
| Versión del plugin | 3 |
| Release de referencia | v1.2.0 |
| JDK | Eclipse Adoptium Temurin 21.0.13+9 |
| Gradle | 8.12 |
| AGP | 8.7.3 |
| Kotlin | 2.3.0 |
| compileSdk / targetSdk | 35 / 35 |
| minSdk | 21 |
| Android de prueba | emulador x86_64 Android 15 (AOSP) con CloudStream 4.9.1 |

## 4. Resultados de compilación

```text
.\gradlew.bat :HDrama:testDebugUnitTest --console=plain  -> BUILD SUCCESSFUL
.\gradlew.bat :HDrama:make                               -> BUILD SUCCESSFUL
.\gradlew.bat makePluginsJson                            -> BUILD SUCCESSFUL
```

| Artefacto | Ruta | Tamaño | SHA-256 |
|---|---|---|---|
| Plugin | `HDrama/build/HDrama.cs3` | 143.227 bytes (140 KB) | be21cdb8464df6d44c10929d86ff16830b2fcdf5ef8498cfe99c9fcf3904fc8d |
| Índice | `build/plugins.json` | — | — |

El `.cs3` contiene `manifest.json` y `classes.dex`; el manifest declara
`version: 3` y referencia `com.hdrama.HDramaPlugin`.

Total de pruebas de la suite: 117 pruebas unitarias en
18 suites.

## 5. Pruebas ejecutadas

- Suites JVM (sin red) de parser HTML, cifrado AES-GCM (con el vector
  Stardust), cliente de fuentes, fetcher principal con sesión y transporte
  falsos, sonda de disponibilidad de enlaces, red/allowlist, catálogo con
  respaldos, player y utilidades. Detalle en
  [`05-pruebas.md`](05-pruebas.md).
- Prueba de integración opcional contra los sitemaps reales
  (`HDRAMA_INTEGRATION=1`).
- Comprobaciones de red del 2026-11-12: con cookie `cf_clearance` y User-Agent
  de Chrome el HTML responde 200; sin cookie, 403; `/api/episode-source`
  responde 200 solo con `Referer` (o `Sec-Fetch-Site: same-origin`).
- Análisis del flujo de descarga de CloudStream sobre su código fuente
  (`VideoDownloadManager.downloadHLS` → `M3u8Helper2.hslLazy` →
  `HlsPlaylistParser.parse`), que con la media playlist actual descarga los
  segmentos `.ts` y los concatena en un MP4.
- Verificación parcial en emulador Android 15 con CloudStream 4.9.1 y el
  plugin v3: log `Challenge resuelto; cookie de sesion actualizada`, catálogo
  real con portada y banner "Encadenadas", y ficha con 64 episodios
  (`bookId=7acef74a-7aee-4d29-bbad-8c708d967d1e`). El primer intento de
  challenge agotó los 45 s del timeout antiguo y el segundo resolvió a los
  ~44 s; con el timeout actual de 75 s se espera que el primero baste.
- La evidencia final de reproducción y descarga completas se cierra en la
  entrega de la v1.2.0.

## 6. Funciones operativas

- Registro del plugin y del proveedor `HDrama`.
- Catálogo real por secciones, con paginación de 24 elementos.
- Búsqueda real por título en `/search?q=`.
- Ficha real con póster, sinopsis, géneros, nota, año, episodios y
  relacionadas.
- Reproducción de episodios reales mediante HLS directo sin cabeceras.
- Descarte de enlaces muertos antes de entregarlos al reproductor.
- Descarga del episodio HLS desde el gestor de descargas de CloudStream.
- Respaldo por sitemaps y fichas de navegación si no hay sesión.
- Importación manual de URLs de series.
- Catálogo demo con MP4/HLS públicos para diagnóstico.
- Estados de error diferenciados y mensajes en español.
- Caché en memoria con TTL, allowlist y reintentos en la capa de respaldo.

## 7. Limitaciones técnicas

- La sesión de Cloudflare depende del WebView de Android; si el challenge no se
  resuelve, se usa el respaldo por sitemaps y navegación. En emuladores lentos
  el primer WebView puede tardar decenas de segundos (de ahí el timeout de
  75 s).
- Los tokens de video caducan; las fuentes se resuelven al reproducir y se
  cachean como máximo 5 minutos.
- Los hosts de video son de terceros y pueden fallar o cambiar (404, 403/451,
  geobloqueo); ya no hay proxy espejo.
- La comprobación de enlaces añade una petición por URL cuando la cadena trae
  más de una; si el host no permite la sonda, se entregan las URLs originales.
- El sitio (Next.js) puede cambiar rutas, selectores, la clave de cifrado o el
  formato del `bookId` sin aviso; el parser y el cliente son tolerantes, pero
  requieren mantenimiento.
- Sin DRM en el sitio: solo HLS/MP4 normales; la playlist no ofrece variantes
  de calidad, así que los enlaces se emiten como `Auto`.
- La descarga la ejecuta CloudStream; si cambia su flujo HLS, la descarga puede
  cambiar.
- D8 de AGP 8.7.3 emite advertencias de metadata Kotlin; no impiden compilar ni
  cargar el plugin.
- No se ha probado en Galaxy Z Fold8 físico ni en redes móviles.
- Repositorio **público** desde la v1.1.0; GitHub Actions habilitadas
  (workflow `Build plugin`).

Detalle completo en [`08-limitaciones.md`](08-limitaciones.md).

## 8. Ubicación del archivo compilado

```text
HDrama/build/HDrama.cs3
```

La copia de entrega se publica en la rama `builds` y en la release v1.2.0:

- Release: <https://github.com/GeorgesGil/HDramaCloudStream/releases/tag/v1.2.0>
- Repositorio de CloudStream:
  `https://raw.githubusercontent.com/GeorgesGil/HDramaCloudStream/builds/repo.json`

## 9. Pasos para instalar en el Galaxy Z Fold8

1. Instalar CloudStream 4.9.1 desde el release oficial.
2. Copiar `HDrama.cs3` a `/sdcard/Cloudstream3/plugins/`.
3. Conceder a CloudStream acceso a archivos (Android 11+) o usar ADB.
4. Reiniciar CloudStream.
5. Elegir el proveedor **HDrama** en el filtro.
6. Abrir una sección real, una ficha con episodios y reproducir un episodio
   (HLS directo).
7. Probar la descarga del episodio desde el botón de descarga de CloudStream.
8. Para el diagnóstico del player, abrir la demo desde "Acceso a HDrama".
9. Verificar logs con
   `adb logcat -s PluginManager PluginInstance CS3ExoPlayer HDrama`.

Detalle en [`06-instalacion.md`](06-instalacion.md).

## 10. Mejoras recomendadas

1. Cerrar la evidencia de reproducción y descarga completas en el emulador y
   adjuntarla a la entrega v1.2.0.
2. Probar en Galaxy Z Fold8 (plegado/desplegado, rotación, 9:16) y en redes
   móviles.
3. Instalar desde `repo.json` público en un dispositivo limpio y verificar la
   actualización automática del plugin.
4. Mantener actualizados los selectores del parser, los vectores de cifrado y
   el tratamiento del `bookId` si el sitio cambia.
5. Actualizar AGP/Kotlin juntos para eliminar las advertencias de metadata D8.
6. Añadir pruebas instrumentadas de UI y de carga del plugin.
