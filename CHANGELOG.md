# Changelog

Todas las fechas en ISO 8601. Formato basado en
[Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/).

## [v1.2.0] - 2026-11-12

Versión 3 del plugin (el número de release es `v1.2.0`; el entero interno del
`.cs3` es `version = 3`). Reescribe la capa de red para que la reproducción y la
descarga de video HLS funcionen de verdad.

### Añadido

- `CloudflareSession` (interfaz) y `NoCloudflareSession` para aislar la sesión
  de Cloudflare del resto de la red y poder probarla en JVM con dobles.
- `WebViewCloudflareSession`: resuelve el Managed Challenge **una sola vez**.
  Carga `https://es.hdrama.net/` en un único WebView, sondea
  `CookieManager.getCookie()` cada 350 ms hasta que aparece `cf_clearance`
  (timeout 75 s) y destruye el WebView. Guarda la cookie y el User-Agent por
  defecto del WebView (deben coincidir con los del challenge).
- `HdramaHttpFetcher`: fetcher principal. Todas las peticiones van por OkHttp con
  `app.get` de CloudStream (`cacheTime = 0`), enviando `cookie`, `user-agent`,
  `referer: https://es.hdrama.net/` y `accept-language`; en `/api/` envía
  `accept: application/json`. Serializa peticiones con un `Mutex`, reintenta una
  vez renovando la sesión ante 403/503 y usa `WebViewFetcher` como respaldo.
- `CloudstreamHttpTransport`, `HdramaHttpTransport` y `HdramaHttpResponse`:
  transporte inyectable para las pruebas JVM del fetcher.
- Sonda de disponibilidad de enlaces (`SourceProbe`, `HttpSourceProbe`,
  `NoSourceProbe` y `SourceProbeFactory`): si la cadena de fuentes trae más de
  una URL, pide el manifiesto HLS o 1 KB del MP4 y descarta los enlaces que no
  responden antes de entregarlos al player. Timeout de 6 s, caché de 60 s, sin
  `Referer`, resultados en paralelo y, si ninguna URL responde, se entregan las
  originales para que el player muestre el error real.
- Warm-up de la sesión al cargar el plugin: `HDramaPlugin.load()` lanza en
  segundo plano `WebFetcherFactory.warmUp()` (CoroutineScope con
  `Dispatchers.IO`) para que el catálogo y la primera reproducción no esperen al
  WebView.
- Warm-up best-effort antes de `/api/`: `HdramaSourceClient` carga el inicio
  (cacheado por `HdramaCatalogAdapter`) para que la cookie exista antes de
  llamar a la API.
- `loadLinksTimeoutMs = 90_000` en `HDramaProvider`: la primera reproducción de
  una sesión puede incluir el challenge y la llamada a la API.
- Suite JVM `HdramaHttpFetcherTest` con sesión y transporte falsos: cabeceras de
  sesión, `/api/` como JSON same-origin, renovación ante 403, respaldo, fallo
  rápido sin sesión y allowlist.
- Suite JVM `SourceProbeTest` con transporte falso: descarte del enlace muerto,
  orden de los vivos, devolución de todas si ninguna responde, atajo con una
  sola fuente, estados de error intactos y fallo de red contado como enlace
  caído.
- Vector Stardust en `HdramaSourceCipherTest` y fixture
  `hdrama-source-enc.json` actualizada al payload del 2026-11-12.
- Caso de prueba del payload RSC actual en `HdramaHtmlParserTest`: el `bookId`
  va seguido del array `episodes` y puede no ser numérico.

### Cambiado

- `HDrama/build.gradle.kts`: `version = 3` y descripción con reproducción y
  descarga.
- `PluginInfo.VERSION = 3`; `API_VERSION` sigue siendo `1`.
- `WebFetcherFactory.create()` devuelve una única instancia por proceso:
  `HdramaHttpFetcher` cuando hay contexto Android y `CloudstreamWebFetcher` en
  entornos sin contexto. La sesión y las cookies se comparten entre el catálogo
  y la API de fuentes.
- La API `/api/episode-source` ya no se pide como XHR same-origin desde un
  WebView: se pide por OkHttp con `Referer`, que es lo que exige el WAF.
- `HdramaSourceClient` emite solo URLs directas, deduplicadas y sin cabeceras;
  si `type` es desconocido, infiere HLS/MP4 por la extensión de la URL.
- La sesión caducada ya no abre un WebView de respaldo sin sesión: se falla
  rápido con HTTP 403 y el catálogo usa los sitemaps; el WebView de respaldo
  solo se usa si había sesión y aun así falló la petición.
- Documentación y metadatos actualizados a la versión 3 / release v1.2.0.

### Corregido

- ANR de CloudStream y "Skipping 3711 frames" en el emulador por abrir un
  WebView nuevo en cada petición (v2 usaba `WebViewFetcher` como fetcher
  principal).
- Timeouts de 60 s y crash del renderer del WebView durante el challenge.
- La reproducción no resolvía el HLS: ahora el enlace directo `M3U8` llega a
  ExoPlayer sin cabeceras.
- 403 de `/api/episode-source` con cookie válida por falta de `Referer`
  same-origin.
- La descarga de HLS desde CloudStream: con una media playlist sin
  `#EXT-X-STREAM-INF`, `HlsPlaylistParser.parse` devuelve `null` y se descargan
  los segmentos `.ts` concatenados en un MP4.
- La documentación describía el `bookId` como numérico; el campo admite
  números, ids hexadecimales, UUID y slugs, y se cubre con un caso nuevo del
  payload RSC actual.

### Eliminado

- El proxy espejo `https://cdn.dramadizilerim.xyz/https/<URL>`: devuelve 451 sin
  `Referer` y 403 con él, y el código lo construía mal (`/https/https://...`).
- El WebView por petición como fetcher principal (`WebViewFetcher` queda solo
  como respaldo).
- Los reintentos por carga de WebView en la API y el backoff global de 10
  minutos del catálogo.

### Notas

- `apiVersion = 1`; probado con CloudStream 4.9.1 / pre-release 2026-11-09.
- El `bookId` puede ser numérico, hexadecimal, UUID o el slug de la serie; el
  payload RSC actual lo coloca seguido del array `episodes`.
- JDK Eclipse Adoptium Temurin 21.0.13+9, Gradle 8.12, AGP 8.7.3, Kotlin 2.3.0,
  compileSdk 35, minSdk 21.
- Total de pruebas de la suite: 117 pruebas unitarias en
  18 suites.
- Artefacto `.cs3`: 143.227 bytes (140 KB) / SHA-256
  `be21cdb8464df6d44c10929d86ff16830b2fcdf5ef8498cfe99c9fcf3904fc8d`.
- Evidencia parcial en emulador Android 15 con CloudStream 4.9.1 y el plugin v3:
  catálogo real (portada y banner "Encadenadas") y ficha con 64 episodios; la
  reproducción y la descarga completas se cierran en la entrega.

## [v1.1.0] - 2026-11-11

Versión 2 del plugin (el número de release es `v1.1.0`; el entero interno del
`.cs3` es `version = 2`).

### Añadido

- Integración real con el HTML de `es.hdrama.net`: catálogo, búsqueda, fichas
  y episodios desde JSON-LD y DOM.
- Parser `HdramaHtmlParser` con `parseSeriesPage`, `parseCollection`,
  `parseSearchResults`, `parseHomeSection`, `parseGenreCards`,
  `parseSourceCards` y `parseRelated`.
- Sesión Cloudflare a través del WebView de CloudStream
  (`CloudstreamWebFetcher` + `CloudflareKiller`), con una instancia por
  fetcher.
- Cliente de fuentes `HdramaSourceClient` para
  `/api/episode-source/<bookId>/<serial>`, con descifrado AES-256-GCM
  (`HdramaSourceCipher`) y vectores de prueba.
- Emisión de HLS directo (sin headers) y proxy espejo
  `cdn.dramadizilerim.xyz` con `Referer: https://es.hdrama.net/`.
- Ocho secciones alineadas con el sitio: Destacados, Novedades, Más vistos,
  Tendencias, Catálogo general, Géneros, Fuentes y Acceso a HDrama.
- Ficha real con póster, sinopsis, géneros, nota, año, lista completa de
  episodios y relacionadas.
- Respaldo por sitemaps públicos y fichas de navegación cuando la sesión de
  Cloudflare no está disponible.
- Fixtures de prueba recortadas de páginas reales del sitio y pruebas del
  parser, del cifrado y del cliente de fuentes.
- Workflow de GitHub Actions que ejecuta pruebas, genera `.cs3` y
  `plugins.json`, calcula SHA-256, publica la rama `builds` y adjunta los
  artefactos en un release.

### Cambiado

- `HDrama/build.gradle.kts`: `version = 2` y nueva descripción del plugin.
- `PluginInfo.VERSION = 2`; `API_VERSION` sigue siendo `1`.
- `CatalogRepository` ahora prioriza el HTML real y usa sitemaps y demo como
  respaldos encadenados (antes el sitemap era la fuente principal).
- `HdramaCatalogAdapter` dejó de ser un adaptador de navegación y pasó a
  integrar el sitio real.
- `HDramaProvider` emite episodios, recomendaciones y enlaces HLS/MP4 reales
  con estados de error diferenciados.
- `repo.json` describe la integración real con respaldo por sitemaps y modo
  demo.
- Compilación documentada con el JDK de Android Studio
  (`C:\Program Files\Android\Android Studio\jbr`), `ANDROID_HOME` del SDK
  local y `GRADLE_USER_HOME` dentro del workspace.
- Dependencias: `com.github.Blatzar:NiceHttp:0.4.11` (HTTP/okhttp de la app) y
  `org.json:json:20240303` (pruebas JVM).

### Eliminado

- Sección "Recomendaciones" del catálogo y sección duplicada "Nuevos esta
  semana" (la portada real muestra "Nuevos" como subconjunto de Novedades).
- Mensajes provisionales del adaptador HTML y del catálogo (ya no aplican).
- Comportamiento principal de solo navegación: las fichas reales y los
  episodios ya se muestran dentro del plugin.

### Corregido

- Las URLs HLS directas se emiten sin `Referer` (enviarlo devolvía 403).
- El espejo se emite con `Referer` para los hosts que lo exigen (sin él
  devolvían 451).
- El `bookId` se empareja con el slug de la ficha actual para no tomar el de
  una serie relacionada.
- Base64 propio en el cifrado para compatibilidad con `minSdk 21` y pruebas
  JVM (sin `android.util.Base64` ni `java.util.Base64`).
- Normalización de rutas `/es/...` a las rutas canónicas del sitio.
- Pruebas y metadatos actualizados a la versión 2.

### Notas

- `apiVersion = 1`; probado con CloudStream 4.9.1 / pre-release 2026-11-09.
- JDK 21.0.6 (Android Studio JBR), Gradle 8.12, AGP 8.7.3, Kotlin 2.3.0,
  compileSdk 35, minSdk 21.
- Total de pruebas de la suite: 98 pruebas unitarias en 16 suites.
- El repositorio es **público**; la
  instalación local del `.cs3` es la vía verificada.

## [v1.0.0] - 2026-11-11

### Añadido

- Plugin CloudStream `HDrama` en Kotlin, empaquetado `.cs3`.
- Proveedor `HDramaProvider` con `getMainPage`, `search`, `load` y `loadLinks`.
- Catálogo real desde sitemaps públicos de HDrama:
  - Novedades (`new-es`).
  - Más vistos (`trending-es`).
  - Nuevos esta semana (`fresh-es`).
  - Catálogo general deduplicado.
  - Búsqueda por título derivado del slug.
- Fichas reales mínimas con título y navegación al sitio original.
- Importación manual de URLs de HDrama sin descargar HTML.
- Catálogo de demostración local con 17 series, paginación, búsqueda sin
  acentos, portadas ausentes y estados de reproducción.
- Reproducción de MP4 1080p y emisión de HLS en el player de CloudStream.
- Estados diferenciados: compatible, no disponible, no compatible, navegador y
  no autorizado.
- Capa de red con allowlist de dominios, timeouts, reintentos y caché TTL.
- Parser HTML con jsoup y contrato de selectores, probado con fixtures.
- 61 pruebas unitarias y una prueba de integración opcional de sitemaps.
- Documentación completa en `Docs/`, política de privacidad y guía de
  instalación.
- Workflow de GitHub Actions y `repo.json`.

### Limitaciones de la v1.0.0

- El HTML de HDrama devolvía HTTP 403 con Cloudflare Managed Challenge; la
  fuente del catálogo era el sitemap público.
- El repositorio privado no servía como repositorio instalable por URL.
- HLS no se confirmó en el player; sí la emisión del enlace.
- Sin prueba en Galaxy Z Fold8 físico.

### Notas

- `apiVersion = 1`; CloudStream 4.9.1 / pre-release 2026-11-09.
- JDK 21.0.13, Gradle 8.12, AGP 8.7.3, Kotlin 2.3.0, compileSdk 35.

[v1.2.0]: https://github.com/GeorgesGil/HDramaCloudStream/compare/v1.1.0...v1.2.0
[v1.1.0]: https://github.com/GeorgesGil/HDramaCloudStream/compare/v1.0.0...v1.1.0
[v1.0.0]: https://github.com/GeorgesGil/HDramaCloudStream/releases/tag/v1.0.0
