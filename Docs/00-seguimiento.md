# Seguimiento del proyecto

## Datos generales

| Campo | Valor |
|---|---|
| Proyecto | HDramaCloudStream |
| Proveedor | HDrama |
| Sitio objetivo | https://es.hdrama.net/ |
| Repositorio | https://github.com/GeorgesGil/HDramaCloudStream |
| Visibilidad | Público desde la v1.1.0 |
| Versión del plugin | 3 |
| Release de referencia | v1.2.0 |
| Rama principal | `main` |
| Rama de artefactos | `builds` (publicada por el workflow) |
| GitHub Actions | Habilitadas: workflow Build plugin (pruebas, .cs3, rama builds y adjunto en releases) |
| Fecha de inicio | 2026-11-11 |
| Fecha de esta revisión | 2026-11-12 |
| Zona horaria | America/Santo_Domingo |

## Estado por fase

| Fase | Estado | Evidencia |
|---|---|---|
| 1. Investigación | Completado | [`02-investigacion-hdrama.md`](02-investigacion-hdrama.md): rutas, JSON-LD, DOM, API y cifrado verificados con navegador real |
| 2. Diseño | Completado | [`03-arquitectura.md`](03-arquitectura.md): capas, rutas, cachés y flujos |
| 3. Implementación | Completado | Código en `HDrama/`: parser, red, fuentes, catálogo y provider |
| 4. Pruebas | Completado | Suites JVM de parser, cifrado, fetcher, red, catálogo y player; 117 pruebas en 18 suites |
| 5. Corrección | Completado | Correcciones de v1 (URL de episodio, URL demo, MP4 403) y ajustes de la v2 |
| 6. Entrega v1.0.0 | Completado | `.cs3` v1 en la rama `builds` y release v1.0.0 |
| 7. Integración HTML real (v2) | Completado | Catálogo, búsqueda, fichas, episodios y HLS reales con respaldos |
| 8. Entrega v1.1.0 | Completado | `.cs3` v2 (123.299 bytes, SHA-256 `7fbc…94e4`), release v1.1.0 con artefacto adjunto y rama `builds` |
| 9. Red v3: reproducción y descarga HLS | Completado | Sesión Cloudflare resuelta una vez, fetcher OkHttp con `Referer`, URLs directas comprobadas con `SourceProbe` y descarga delegada en CloudStream |
| 10. Entrega v1.2.0 | Completado | Artefacto `.cs3` v3 (143.227 bytes (140 KB), SHA-256 `be21cdb8464df6d44c10929d86ff16830b2fcdf5ef8498cfe99c9fcf3904fc8d`), rama `builds` publicada por CI y release v1.2.0 con el `.cs3` adjunto |

## Bitácora

### 2026-11-11 — Preparación del entorno y Fase 1

- Se verificó Git 2.54, GitHub CLI 2.95 con sesión `GeorgesGil`, JDK 8/11
  preinstalados y Android SDK en `C:\Android`.
- Se instaló **Temurin JDK 21.0.13+9** en
  `C:\Program Files\Eclipse Adoptium\jdk-21.0.13.9-hotspot`, porque el
  `JAVA_HOME` heredado apuntaba a una ruta inexistente.
- Se creó el repositorio privado `GeorgesGil/HDramaCloudStream`.
- Se deshabilitaron GitHub Actions mediante
  `PUT /repos/GeorgesGil/HDramaCloudStream/actions/permissions` con
  `{"enabled": false}`; verificado con `GET` del mismo endpoint.
- Se clonaron y revisaron las fuentes oficiales:
  - `recloudstream/cloudstream` (commit `5d66bbe`, 2026-11-09)
  - `recloudstream/TestPlugins` (commit `d87d65a`, 2025-05-29)
  - `recloudstream/extensions` (commit `cf16b3b`, 2026-05-23)
  - `recloudstream/csdocs`
  - `recloudstream/gradle` (plugin Gradle de CloudStream)
- Se detectó que `recloudstream/plugin-template` ya no es público; la
  plantilla vigente accesible es `TestPlugins`.
- Se consultaron `robots.txt`, `sitemap.xml` y una página HTML de HDrama:
  - `robots.txt` permite `/` y prohíbe `/api/`, `/flags/`, `/source-icons/`
    y varios bots de SEO. Declara cuatro sitemaps.
  - La home y las páginas HTML respondían **HTTP 403** con
    `Cf-Mitigated: challenge` (Cloudflare Managed Challenge).
  - Los sitemaps (`/sitemaps/new-es`, etc.) respondían HTTP 200 y contienen
    URLs `/series/<slug>` y `lastmod`; no contienen títulos, portadas ni
    episodios.
- Conclusión de la primera pasada: el catálogo inicial se construyó con los
  sitemaps públicos, a la espera de resolver la sesión de Cloudflare.

### 2026-11-11 — Fase 2: diseño

- Se definió la arquitectura por capas: modelos puros, adaptadores de catálogo,
  parser, red segura, resolución de enlaces y mapeo a CloudStream.
- Se definió el catálogo de demostración local con paginación, búsqueda
  normalizada y estados de reproducción.
- Se documentaron los flujos de pantalla principal, búsqueda, ficha,
  reproducción, navegador y plegables.
- Se definió el plan de pruebas y los riesgos.

### 2026-11-11 — Fase 3: implementación v1

- Se creó el proyecto Gradle con wrapper 8.12, AGP 8.7.3, Kotlin 2.3.0,
  `compileSdk 35`, `minSdk 21`, JVM target 1.8.
- Se implementaron modelos de dominio, utilidades Unicode/URL, caché TTL,
  allowlist de red, reintentos, parser jsoup inicial, catálogo demo, catálogo
  real de sitemaps, resolución de enlaces y el proveedor de CloudStream.
- Se compiló `HDrama.cs3` y se generó `plugins.json`.
- Se instaló CloudStream 4.9.1 en un emulador Android 15 y se verificó:
  - carga del plugin y registro del proveedor;
  - catálogo real con títulos del sitemap;
  - ficha real con botón de navegador (abre Chrome);
  - demo con 2 episodios;
  - reproducción MP4 1080p (`Rendered first frame`) con letterboxing.
- Correcciones relevantes de la v1:
  - `newEpisode` debía usar `fix = false` para conservar el esquema interno
    del episodio;
  - CloudStream antepone `mainUrl` a URLs de ficha con esquema propio; se
    añadió `recoverDemoSeriesUrl`;
  - el MP4 original (Google sample bucket) devolvía 403; se cambió a
    `media.w3.org/2010/05/sintel/trailer.mp4`;
  - se ajustaron pruebas de importación manual y de la sección de acceso.

### 2026-11-11 — Fase 6: entrega v1.0.0

- PR #3 (Fase 3) mergeado a `main`.
- Artefactos publicados en la rama `builds`: `HDrama.cs3`, `plugins.json`,
  `repo.json` y `BUILDS.md`.
- Release `v1.0.0` creado con el `.cs3` adjunto.
- GitHub Actions quedó deshabilitado (`enabled=false` verificado).
- El repositorio siguió siendo privado; la instalación de referencia fue
  local y la guía quedó en [`06-instalacion.md`](06-instalacion.md).

### 2026-11-11 — Fase 7: verificación real del sitio

- Se repitió el reconocimiento con un navegador Chromium real (Playwright) que
  superó el Cloudflare Managed Challenge; los HTML completos quedaron en
  `tmp/raw/` (no versionados) y las versiones recortadas en las fixtures.
- Hallazgos verificados (detalle en [`02-investigacion-hdrama.md`](02-investigacion-hdrama.md)):
  - rutas reales de portada, catálogo (`/series`, 84 páginas), Novedades,
    Más vistos, Tendencias, Géneros, Fuentes, búsqueda, fichas y episodios;
  - JSON-LD `TVSeries`, `CollectionPage`/`ItemList`, `TVEpisode` y portada;
  - `bookId` en el payload RSC de la ficha, junto al slug de la serie;
  - lista completa de episodios con `data-serial` y `data-display`;
  - API `/api/episode-source/<bookId>/<serial>` con `chain` de fuentes;
  - payload cifrado con AES-256-GCM y vectores de prueba verificados;
  - reproducción HLS directa sin `Referer`.

### 2026-11-11 — Fase 7: implementación v2

- Se añadieron los paquetes `parser`, `network` y `sources`:
  - `HdramaHtmlParser` con JSON-LD, tarjetas, fichas, episodios y
    relacionadas; `ParserContract` con selectores reales.
  - `WebFetcher`/`WebViewFetcher` con WebView del sistema, `CloudstreamWebFetcher` como respaldo, allowlist y
    mensajes en español.
  - `HdramaSourceCipher` (Base64 propio + AES-256-GCM) y
    `HdramaSourceClient` (chain y HLS directo, más un proxy espejo con
    `Referer` heredado de las primeras pruebas).
- Se reescribió `HdramaCatalogAdapter` como integración real y
  `CatalogRepository` con prioridad HTML real → sitemaps → demo.
- Las secciones de portada quedaron alineadas con el sitio:
  Destacados, Novedades, Más vistos, Tendencias, Catálogo general, Géneros,
  Fuentes y Acceso a HDrama. No se duplica "Nuevos" como sección aparte.
- `HDramaProvider` emite HLS/MP4 reales, recomendaciones y estados de error;
  la demo sigue accesible desde "Acceso a HDrama" y con la búsqueda `prueba`.
- Se actualizaron `repo.json`, el workflow de CI y las dependencias
  (`NiceHttp` y `org.json` para pruebas).

### 2026-11-11 — Fase 7: pruebas y compilación v2

- Se actualizaron las pruebas del parser, del cifrado, de la red, del catálogo
  y del provider. Detalle en [`05-pruebas.md`](05-pruebas.md).
- Total de pruebas de la suite v2: 98 pruebas unitarias en 16 suites.
- Compilación con JDK de Android Studio JBR, `ANDROID_HOME` del SDK local y
  `GRADLE_USER_HOME` dentro del workspace; comandos en
  [`04-compilacion.md`](04-compilacion.md).
- Artefacto `.cs3` v2: 123.299 bytes (123 KB) /
  7fbcccb77000ab15ccaa81605b5bc4e11762c9777f83d0f2e62101152e1394e4.

### 2026-11-11 — Fase 7: documentación y entrega v1.1.0

- Se actualizaron `Docs/00`–`Docs/09`, `Docs/README.md`, `README.md` y
  `CHANGELOG.md` para la versión 2.
- Evidencias de emulador de la v2:
  Verificación parcial en emulador Android 14 (AOSP API 34) con CloudStream 4.9.1: plugin cargado, catálogo real y ficha completa con 41 episodios; reproducción HLS pendiente de confirmar en hardware.
- Release v1.1.0 y actualización de la rama `builds`:
  Release v1.1.0 publicada con HDrama.cs3 adjunto y rama builds actualizada.

### 2026-11-12 — Problema de la v2: WebView por petición

- Con la v2 instalada en el emulador, cada petición abría un WebView nuevo
  (`WebViewFetcher` como fetcher principal). El resultado fue:
  - ANR de CloudStream ("Skipping 3711 frames");
  - timeouts de 60 s (`W HDrama: Timeout de 60s en https://es.hdrama.net/`);
  - crash del renderer de WebView.
- La reproducción nunca llegaba a resolver el HLS: el catálogo funcionaba a
  veces, pero el flujo de fuentes se quedaba sin sesión ni tiempo.
- Diagnóstico: el coste de abrir un WebView por petición no era asumible en el
  emulador y el challenge se repetía en cada carga.

### 2026-11-12 — Evidencia de la sesión (curl)

- Con la cookie `cf_clearance` y el User-Agent de Chrome, `https://es.hdrama.net/`
  devolvía **200**; sin cookie, todo **403**.
- `/api/episode-source/...` seguía devolviendo **403** hasta añadir
  `Referer: https://es.hdrama.net/` (o `Sec-Fetch-Site: same-origin`).
- Conclusión: la API exige una petición same-origin a nivel de cabeceras
  (`Referer`), no un XHR dentro de un WebView. Desde ese momento el plan fue
  resolver el challenge una vez y usar OkHttp para todo.

### 2026-11-12 — API de fuentes y descifrado

- La respuesta real de
  `GET /api/episode-source/<bookId>/<serial>?lang=es&refresh=1` es
  `{"descriptor":{"source":"Stardust","chain":[{"enc":"...","type":"hls","source":"Stardust",...}],"subtitle":null}}`.
- El descifrado AES-256-GCM con la misma clave del sitio sigue funcionando; la
  URL descifrada es
  `https://acdn-v-us.stardust-tv.com/prod/29358/1/e4bf2b9fce7c423ca661b3d65529a7f4.m3u8`,
  una media playlist HLS con segmentos `.ts` relativos. Antes la fuente era
  FlickReels.
- El `bookId` del sitio actual puede ser numérico, hexadecimal, UUID o el slug
  de la serie; el emulador mostró
  `bookId=7acef74a-7aee-4d29-bbad-8c708d967d1e` con 64 episodios.

### 2026-11-12 — Proxy espejo eliminado

- El proxy `https://cdn.dramadizilerim.xyz/https/<url>` devolvía 451 sin
  `Referer` y 403 con `Referer`; además el código lo construía mal
  (`/https/https://...`).
- Se eliminó del cliente de fuentes: ahora `HdramaSourceClient` emite solo URLs
  directas, sin cabeceras (el host del CDN responde 200 sin `Referer`), y
  deduplica. Si `type` es desconocido, infiere HLS/MP4 por la extensión.

### 2026-11-12 — Fase 9: red v3

- Se añadieron `CloudflareSession`, `NoCloudflareSession` y
  `WebViewCloudflareSession`: el challenge se resuelve **una sola vez** por
  proceso, en un único WebView, con sondeo de `CookieManager` cada 350 ms y
  timeout de 75 s (en emuladores lentos el primer WebView tarda en arrancar).
  `invalidate()` fuerza un challenge nuevo sin borrar todas las cookies de la
  app; tras un fallo hay un backoff de 15 s para no encadenar WebViews.
- Se añadió `HdramaHttpFetcher`: todas las peticiones (HTML y `/api/`) van por
  OkHttp con `cookie`, `user-agent` del WebView, `referer` y
  `accept-language`; en `/api/` se envía `accept: application/json`. Las
  peticiones se serializan con un `Mutex`; ante 403/503 se renueva la sesión una
  vez y, si no, se usa `WebViewFetcher` como respaldo.
- Sin sesión, el fetcher falla rápido con HTTP 403 y un mensaje que remite al
  respaldo por sitemaps; no se abre un WebView de respaldo sin sesión.
- `WebFetcherFactory.create()` devuelve una única instancia por proceso
  (`HdramaHttpFetcher` con contexto Android; `CloudstreamWebFetcher` sin
  contexto) y se comparte entre el catálogo y la API de fuentes.
- `HDramaPlugin.load()` lanza en segundo plano el warm-up
  (`WebFetcherFactory.warmUp()` → `HdramaHttpFetcher.warmUp()` →
  `CloudflareSession.ensure()`) para adelantar el challenge.
- `HdramaSourceClient` hace un warm-up best-effort de la portada (cacheada por
  `HdramaCatalogAdapter`) antes de llamar a `/api/`: el WAF de esa ruta no
  resuelve challenges.
- Se eliminó el backoff global de 10 minutos del catálogo: si falla una
  sección, cada una cae a sitemaps y el catálogo real vuelve solo cuando la
  sesión se resuelve.

### 2026-11-12 — Fase 9: reproducción y descarga

- `HDramaProvider` mantiene `hasDownloadSupport = true` y añade
  `loadLinksTimeoutMs = 90_000` (la primera reproducción puede incluir el
  challenge y la API).
- Emite `ExtractorLinkType.M3U8` con la URL directa y sin headers; ExoPlayer
  reproduce el HLS.
- La descarga la gestiona CloudStream: para `ExtractorLinkType.M3U8` usa
  `VideoDownloadManager.downloadHLS` → `M3u8Helper2.hslLazy`. Al ser una media
  playlist sin `#EXT-X-STREAM-INF`, `HlsPlaylistParser.parse` devuelve `null` y
  se descargan los segmentos `.ts` concatenados en un MP4 (no requiere proxy ni
  headers).

### 2026-11-12 — Fase 9: sonda de disponibilidad de enlaces

- Se añadió `sources/SourceProbe.kt` (`SourceProbe`, `NoSourceProbe`,
  `HttpSourceProbe`) y `SourceProbeFactory` en `HdramaRuntime.kt`.
- La cadena de HDrama puede traer enlaces caducados o muertos: en la
  comprobación del 2026-11-12 el primer enlace
  (`pl.shortdizilab.com/...`) daba 404 y el segundo
  (`zshipricf.farsunpteltd.net/...`) respondía 200. CloudStream reproduce el
  primer enlace, así que conviene filtrar antes.
- `HttpSourceProbe` pide el manifiesto HLS o 1 KB del MP4 (timeout 6 s, sin
  `Referer`, en paralelo) y descarta lo que no responde; con una sola fuente no
  hace peticiones; si ninguna responde devuelve las originales para que el
  player muestre el error real; cachea el resultado 60 s.
- `HdramaCatalogAdapter.resolve` aplica la sonda después de `HdramaSourceClient`
  y cachea la lista filtrada.

### 2026-11-12 — Fase 9: pruebas y compilación v3

- Suites JVM actualizadas: `HdramaSourceClientTest` sin espejo, con cabeceras y
  warm-up; `HdramaSourceCipherTest` con el vector Stardust; nuevo
  `HdramaHttpFetcherTest` con sesión y transporte falsos (403, renovación,
  respaldo, fallo rápido sin sesión y allowlist); nuevo `SourceProbeTest` con
  transporte falso (enlace muerto, orden, fallback y atajo de una sola fuente);
  nuevo caso de `bookId` del payload RSC actual en `HdramaHtmlParserTest`.
- Total de la suite v3: 117 pruebas unitarias en 18
  suites.
- Compilación verificada con Eclipse Adoptium Temurin 21.0.13+9,
  `ANDROID_HOME=C:\Android`, Gradle 8.12, AGP 8.7.3 y Kotlin 2.3.0; comandos en
  [`04-compilacion.md`](04-compilacion.md).
- Artefacto `.cs3` v3: 143.227 bytes (140 KB) / `be21cdb8464df6d44c10929d86ff16830b2fcdf5ef8498cfe99c9fcf3904fc8d`.

### 2026-11-12 — Evidencia en emulador de la v3

- Emulador Android 15 (AOSP) con CloudStream 4.9.1 y el plugin v3 instalado.
- Primer intento de challenge: agotó los 45 s del timeout antiguo. Segundo
  intento: el challenge se resolvió a los ~44 s. Con el timeout actual de 75 s
  se espera que el primer intento baste en emuladores lentos.
- Logs reales de la sesión:
  - `Challenge resuelto; cookie de sesion actualizada`.
  - `Ficha encadenadas: bookId=7acef74a-7aee-4d29-bbad-8c708d967d1e episodios=64`.
- Catálogo real cargado: portada con banner "Encadenadas" y ficha con 64
  episodios. La evidencia de reproducción y descarga completas se cerró en la
  misma sesión (ver más abajo y [`05-pruebas.md`](05-pruebas.md)).

### 2026-11-12 — Fase 9: reproducción y descarga verificadas en emulador

- Búsqueda real de "encadenadas" con tarjetas del sitio y ficha con 125
  episodios ("Esposa condenada: mi apuesta por el Rey Dragón", `bookId=14380`).
- Reproducción del episodio 1: log
  `Fuentes bookId=14380 serial=1 bytes=565 resultado=2`; la sonda descartó el
  primer enlace (`pl.shortdizilab.com`, 404) y entregó el segundo
  (`zshipricf.farsunpteltd.net`, 200); ExoPlayer reprodujo el HLS
  (`loadExo` → `Rendered first frame`).
- Descarga del episodio 1 desde CloudStream (carpeta `Cloudstream3` elegida una
  vez en el selector de Android): archivo
  `/sdcard/Cloudstream3/AsianDramas/<serie>/Season 1 Episode 1 - Capítulo 1.mp4`
  de 26.160.200 bytes; `DownloadQueueService: Download queue service stopped`
  al terminar.
- Capturas adjuntas: `Docs/images/07-ficha-real-v3.png`,
  `08-reproduccion-hls-v3.png` y `09-busqueda-real-v3.png`.

### 2026-11-12 — Documentación y entrega v1.2.0

- Se actualizan `README.md`, `CHANGELOG.md`, `repo.json` y `Docs/00` a `Docs/09`
  (incluidos `Docs/README.md`, `06-instalacion.md` y `07-privacidad.md`) para la versión 3.
- Release de referencia: v1.2.0 (`.cs3` con `version = 3`).
- PR #10 mergeado a `main`; el workflow `Build plugin` ejecutó las 117
  pruebas, compiló el `.cs3` y publicó la rama `builds` con `plugins.json`
  (`version: 3`, `fileHash: sha256-be21cdb8…`). La release v1.2.0 adjunta
  `HDrama.cs3` (143.227 bytes, SHA-256
  `be21cdb8464df6d44c10929d86ff16830b2fcdf5ef8498cfe99c9fcf3904fc8d`) y
  `plugins.json`.

## Decisiones de implementación

1. El HTML real de HDrama es la fuente principal del catálogo, la búsqueda, las
   fichas y los episodios.
2. Los sitemaps públicos son el respaldo cuando la sesión de Cloudflare o la
   red fallan; las fichas se convierten entonces en navegación al sitio.
3. El catálogo demo queda como diagnóstico del reproductor y último recurso.
4. El challenge de Cloudflare se resuelve **una sola vez** por proceso en un
   WebView (`WebViewCloudflareSession`) y todas las peticiones van después por
   OkHttp con la cookie, el User-Agent del WebView y `Referer`. Esto elimina el
   WebView por petición que provocaba ANR y timeouts.
5. Las fuentes de video se resuelven al reproducir, se descifran con
   AES-256-GCM y se emiten **solo URLs directas**, sin proxy y sin cabeceras;
   la descarga la gestiona CloudStream con el mismo enlace `M3U8`.
6. Si la cadena trae más de una URL, se comprueba la disponibilidad de cada una
   (`SourceProbe`) antes de entregarla al reproductor: la cadena puede incluir
   enlaces muertos y CloudStream reproduce el primero de la lista.
7. El warm-up del challenge se lanza al cargar el plugin y antes de la API de
   fuentes; el WAF de `/api/` no resuelve challenges, así que no se abre un
   WebView sobre esa ruta.
8. Sin sesión no se intenta el respaldo WebView: se falla rápido y el catálogo
   usa sitemaps, de modo que la interfaz no se bloquea. El respaldo WebView
   queda para peticiones con sesión que aun así fallan.
9. El `bookId` se trata como identificador flexible (numérico, hexadecimal,
   UUID o slug) porque el sitio lo ha mostrado en varios formatos.
10. Las ocho secciones se alinean con las del sitio; "Nuevos" no se duplica
    porque la portada lo muestra como subconjunto de Novedades.
11. El repositorio es público y GitHub Actions está habilitado; el workflow
    `Build plugin` ejecuta pruebas, compila el `.cs3`, actualiza la rama
    `builds` y adjunta el artefacto en cada release. La instalación local
    (`.cs3`/ADB) sigue disponible como alternativa.

## Próximos pasos

- Instalar desde `repo.json` público en un dispositivo limpio.
- Verificar en Galaxy Z Fold8 (plegado/desplegado, rotación, 9:16) y en redes
  móviles.
- Mantener el parser, la clave de cifrado y el formato del `bookId` al día si el
  sitio cambia.
