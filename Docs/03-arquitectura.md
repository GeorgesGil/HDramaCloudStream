# Arquitectura (versión 3)

Fecha: 2026-11-12. Este documento describe la arquitectura implementada en la
versión 3: capa de red reescrita para resolver el Cloudflare Managed Challenge
una sola vez y hacer todo lo demás por OkHttp, API de fuentes con payload
cifrado, URLs HLS directas y descarga delegada en CloudStream.

## 1. Resumen

La versión 2 usaba un WebView nuevo por petición (`WebViewFetcher` como fetcher
principal). En el emulador eso provocaba ANR, timeouts de 60 s y crash del
renderer, y la reproducción nunca llegaba a resolver el HLS. La versión 3
separa la sesión de Cloudflare de la capa HTTP:

```text
CloudStream (MainAPI)
   │
   ▼
HDramaProvider
   │  mapea modelos de dominio ↔ SearchResponse/LoadResponse/ExtractorLink
   ▼
CatalogRepository  (caché TTL + prioridad de adaptadores)
   │
   ├── HdramaCatalogAdapter   → HTML real, metadatos, episodios y HLS
   │      ├── HdramaHttpFetcher (OkHttp + CloudflareSession + Mutex)
   │      │     ├── WebViewCloudflareSession  (un WebView, cf_clearance + UA)
   │      │     └── WebViewFetcher            (respaldo puntual)
   │      ├── HdramaHtmlParser (JSON-LD + DOM con jsoup)
   │      ├── HdramaSourceClient + HdramaSourceCipher (AES-256-GCM)
   │      └── HttpSourceProbe (descarta URLs de video muertas)
   ├── SitemapCatalogAdapter  → respaldo con sitemaps públicos (sin metadatos)
   ├── DemoCatalogAdapter     → diagnóstico y último recurso (MP4/HLS públicos)
   └── ManualHdramaImport / AccessCards → fichas de navegación
   │
   ▼
LinkResolver → ExtractorLink (M3U8/VIDEO, URL directa sin headers)
   │
   ▼
ExoPlayer (reproducción) / VideoDownloadManager (descarga HLS → MP4)
```

La prioridad la decide `CatalogRepository`: intenta el HTML real, si no hay
resultados cae a sitemaps y, si el sitemap también falla, al catálogo demo. Una
ficha concreta que no se puede descargar se convierte en ficha de navegación
con el sitio original.

## 2. Criterios técnicos

1. **Datos reales.** Rutas, JSON-LD, DOM y API verificados contra el sitio el
   2026-11-11 y el 2026-11-12 (ver
   [`02-investigacion-hdrama.md`](02-investigacion-hdrama.md)).
2. **Una sola sesión.** El Managed Challenge se resuelve una vez por proceso
   (`WebViewCloudflareSession`); el resto de peticiones van por OkHttp con
   cookie `cf_clearance`, User-Agent del WebView y `Referer`. El WebView por
   petición queda eliminado.
3. **Tolerancia.** El parser no lanza: los campos ausentes quedan en `null` y
   el cliente de fuentes devuelve `MediaSource.Unavailable` con un motivo.
4. **Fallbacks.** Sitemaps, navegación y demo cubren fallos de Cloudflare, de
   red y del propio sitio. La sesión se adelanta al cargar el plugin y antes de
   la API de fuentes.
5. **Sin DRM.** El sitio no usa DRM; solo se modelan HLS/MP4 normales.
6. **Pruebas JVM.** El cifrado, el parser, el fetcher, el cliente de fuentes,
   la sonda de enlaces y los adaptadores se prueban con fixtures, transportes y
   sesiones falsas, sin red.
7. **API vigente.** Se usan las factorías actuales de CloudStream
   (`newHomePageResponse`, `newSearchResponseList`, `newTvSeriesLoadResponse`,
   `newEpisode`, `newExtractorLink`), no constructores deprecados.
8. **Compatibilidad.** `minSdk 21`, `compileSdk 35`, JVM target 1.8,
   `apiVersion = 1`, `version = 3`.

## 3. Estructura del repositorio

```text
HDramaCloudStream/
├── build.gradle.kts                 # dependencias comunes + tareas de plugins
├── settings.gradle.kts
├── gradle.properties                # Kotlin in-process, sin daemon
├── gradlew / gradlew.bat
├── gradle/wrapper/
├── README.md
├── CHANGELOG.md
├── repo.json                        # índice de repositorio de CloudStream
├── .github/workflows/build.yml      # pruebas + .cs3 + rama builds
├── Docs/
│   ├── 00-seguimiento.md … 09-informe-final.md
│   ├── README.md
│   └── images/
└── HDrama/
    ├── build.gradle.kts             # version = 3, metadatos del plugin
    └── src/
        ├── main/
        │   ├── AndroidManifest.xml
        │   └── kotlin/com/hdrama/
        │       ├── HDramaPlugin.kt
        │       ├── HDramaProvider.kt
        │       ├── PluginInfo.kt
        │       ├── catalog/
        │       │   ├── CatalogAdapter.kt
        │       │   ├── CatalogSection.kt
        │       │   ├── HdramaRoutes.kt
        │       │   ├── HdramaCatalogAdapter.kt
        │       │   ├── SitemapCatalogAdapter.kt
        │       │   ├── DemoCatalogAdapter.kt
        │       │   ├── ManualHdramaImport.kt
        │       │   ├── AccessCards.kt
        │       │   └── CatalogRepository.kt
        │       ├── parser/
        │       │   ├── HdramaHtmlParser.kt
        │       │   └── ParserContract.kt
        │       ├── network/
        │       │   ├── WebFetcher.kt
        │       │   ├── CloudflareSession.kt
        │       │   ├── WebViewCloudflareSession.kt
        │       │   ├── HdramaHttpFetcher.kt
        │       │   ├── HdramaRuntime.kt
        │       │   ├── WebViewFetcher.kt
        │       │   ├── CloudstreamWebFetcher.kt
        │       │   ├── SafeHttpClient.kt
        │       │   ├── UrlConnectionHttpTransport.kt
        │       │   ├── RetryPolicy.kt
        │       │   ├── SitemapClient.kt
        │       │   ├── SitemapParser.kt
        │       │   └── TtlCache.kt
        │       ├── sources/
        │       │   ├── HdramaSourceCipher.kt
        │       │   ├── HdramaSourceClient.kt
        │       │   └── SourceProbe.kt
        │       ├── models/
        │       │   ├── Series.kt
        │       │   ├── Episode.kt
        │       │   └── MediaSource.kt
        │       ├── player/
        │       │   ├── LinkResolver.kt
        │       │   └── PlaybackStatus.kt
        │       └── utils/
        │           ├── TextNormalizer.kt
        │           ├── UrlUtils.kt
        │           ├── EpisodeId.kt
        │           └── Diagnostics.kt
        └── test/
            ├── resources/fixtures/
            │   ├── hdrama-real-detail.html
            │   ├── hdrama-real-collection.html
            │   ├── hdrama-real-search.html
            │   ├── hdrama-real-home.html
            │   ├── hdrama-real-directories.html
            │   ├── hdrama-source-api.json
            │   ├── hdrama-source-enc.json
            │   ├── sitemap-index.xml
            │   └── sitemap-new-es.xml
            └── kotlin/com/hdrama/
                ├── parser/HdramaHtmlParserTest.kt
                ├── network/HdramaHttpFetcherTest.kt
                ├── sources/…
                ├── network/…
                ├── catalog/…
                ├── player/…
                └── utils/…
```

## 4. Secciones de la portada

`CatalogSection` define las ocho secciones de `mainPage`, con claves estables
(se guardan en el historial/ajustes de CloudStream):

| Clave | Nombre mostrado | Ruta real | Paginación |
|---|---|---|---|
| `destacados` | Destacados | `/` (portada, sección DOM "Destacados") | Una página |
| `novedades` | Novedades | `/category/new-releases` | `/category/new-releases/page/<N>` |
| `mas-vistos` | Más vistos | `/category/popular` | `/category/popular/page/<N>` |
| `tendencias` | Tendencias | `/category/trending` | `/category/trending/page/<N>` |
| `catalogo` | Catálogo general | `/series` | `/series/page/<N>` |
| `generos` | Géneros | `/categories` | Una página |
| `fuentes` | Fuentes | `/sources` | Una página |
| `acceso` | Acceso a HDrama | Tarjetas locales | Una página |

`Destacados`, `Géneros` y `Fuentes` solo tienen una página: sus elementos son
tarjetas de navegación (género/fuente) que abren una página de recomendaciones
del propio sitio. `Acceso a HDrama` se sirve de `AccessCards` sin red.

`HdramaRoutes` centraliza las rutas:

| Método | URL |
|---|---|
| `home()` | `https://es.hdrama.net/` |
| `series(slug)` | `https://es.hdrama.net/series/<slug>` |
| `episode(slug, n)` | `https://es.hdrama.net/series/<slug>/episode-<n>` |
| `category(slug, page)` | `/category/<slug>` o `/category/<slug>/page/<N>` |
| `seriesList(page)` | `/series` o `/series/page/<N>` |
| `search(q)` | `/search?q=<texto codificado>` |
| `categories()` | `/categories` |
| `sources()` | `/sources` |

`HdramaRoutes` también reconoce URLs de episodio (`episodeNumber`,
`seriesUrlOfEpisode`) y páginas de navegación (`isBrowseUrl`, `browseId`,
`browseTitle`). `PAGE_SIZE = 24`, el tamaño real de página del sitio.

## 5. Capas

### `models`

Dominio puro, sin tipos de CloudStream:

- `Series`: `id` (= slug en el sitio real), `slug`, `title`, `synopsis?`,
  `genres`, `rating?` (0..10), `language`, `source`, `posterUrl?`,
  `episodeCount?`, `pageUrl?`, `externalId?` (bookId), `demo`, `browserOnly`,
  `isNewThisWeek`, `recommended`.
- `Episode`: `id` (`hdrama-demo://episode/<slug>/<n>`), `seriesId`,
  `number` (número mostrado), `title?`, `durationSeconds?`, `pageUrl?`,
  `externalId?` (`bookId:serial`), `mediaSources`, `subtitles`.
- `MediaSource` (sealed): `Mp4`, `Hls` (URL, calidad y headers), `Unavailable`,
  `BrowserOnly`, `Unauthorized`.
- `SubtitleTrack` y `CatalogPage<T>` (`items`, `page`, `hasNext`).

### `catalog`

- `CatalogAdapter` (interfaz): `section`, `search`, `load`, `episodes`,
  `resolve` y `related`.
- `HdramaCatalogAdapter`: integración real. Descarga HTML con el `WebFetcher`
  compartido (`HdramaHttpFetcher`), parsea con `HdramaHtmlParser`, resuelve
  fuentes con `HdramaSourceClient` (con warm-up de la portada), filtra las URLs
  muertas con `SourceProbe` y cachea HTML, fichas, páginas de navegación y
  fuentes.
- `SitemapCatalogAdapter`: respaldo con sitemaps públicos; títulos derivados
  del slug y fichas de navegación.
- `DemoCatalogAdapter`: 17 series sintéticas con MP4/HLS públicos, marcadas
  como demo.
- `ManualHdramaImport`: construye fichas de navegación desde una URL de HDrama
  cuando el HTML no se puede descargar; no hace red.
- `AccessCards`: tarjetas locales de la sección "Acceso a HDrama" (abrir sitio,
  importar enlace, prueba de reproducción demo).
- `CatalogRepository`: aplica prioridad, caché TTL, deduplicación y respaldos.

### `parser`

`HdramaHtmlParser` parsea HTML real con jsoup y `org.json`:

```kotlin
data class HdramaSeriesPage(
    val series: Series,
    val bookId: String?,
    val episodes: List<Episode>,
    val related: List<Series>,
)

class HdramaHtmlParser(private val contract: ParserContract = ParserContract()) {
    fun parseSeriesPage(html: String, pageUrl: String, fallbackTitle: String? = null): HdramaSeriesPage?
    fun parseCollection(html: String, pageUrl: String): List<Series>
    fun parseSearchResults(html: String, pageUrl: String): List<Series>
    fun parseHomeSection(html: String, sectionTitle: String, pageUrl: String): List<Series>
    fun parseGenreCards(html: String): List<Series>
    fun parseSourceCards(html: String): List<Series>
    fun parseRelated(html: String, pageUrl: String, excludeSlug: String? = null): List<Series>
    fun parseBookId(html: String, slug: String?): String?
}
```

`ParserContract` centraliza los selectores reales (título, póster, sinopsis,
géneros, nota, `dl dt`, episodios `a[data-serial]`, duraciones, relacionadas).
Los listados y categorías usan el JSON-LD `CollectionPage` y las tarjetas
`article` como respaldo; la búsqueda usa tarjetas.

`parseBookId` elimina las barras invertidas del payload RSC y acepta
`bookId` numéricos, hexadecimales, UUID o slugs
(`[A-Za-z0-9_-]{1,80}`). Cuando hay varios, elige el más cercano al slug de la
ficha; si no hay coincidencia, prefiere el que va seguido de `,"episodes"`.

### `network`

La versión 3 divide la red en tres piezas:

1. **Sesión de Cloudflare.**
   - `CloudflareSession` (interfaz): `cookieHeader()`, `userAgent()`,
     `ensure()` e `invalidate()`.
   - `NoCloudflareSession`: sesión vacía para pruebas JVM o entornos sin
     contexto.
   - `WebViewCloudflareSession`: resuelve el Managed Challenge en un único
     WebView con sondeo de `CookieManager.getCookie()` cada 350 ms hasta ver
     `cf_clearance` (timeout 75 s). Guarda cookie y
     `WebSettings.getDefaultUserAgent()`; `invalidate()` fuerza un challenge
     nuevo (no borra todas las cookies de la app) y aplica un backoff de 15 s
     tras un fallo.

2. **Fetcher principal.**
   - `HdramaHttpFetcher` (`WebFetcher`): resuelve la sesión con `ensure()`,
     serializa las peticiones con un `Mutex` y envía por OkHttp:
     `cookie` (`cf_clearance`), `user-agent` (el del WebView),
     `referer: https://es.hdrama.net/` y `accept-language`. En rutas `/api/`
     envía `accept: application/json`; en HTML, un `accept` de navegador.
   - `CloudstreamHttpTransport` implementa `HdramaHttpTransport` con
     `app.get(..., cacheTime = 0)` y timeout de 60 s. Los tipos
     `HdramaHttpTransport`/`HdramaHttpResponse` son inyectables para las
     pruebas JVM.
   - Ante 403/503 renueva la sesión una vez (con espera de 400 ms) y reintenta;
     si no lo consigue, usa el respaldo `WebViewFetcher`. Un fallo de red
     también cae al respaldo.
   - Si no hay sesión desde el principio, falla rápido con
     `WebFetchException(403, "...la sesion de Cloudflare no esta disponible.
     Se usara el respaldo por sitemaps.")` y **no** abre el WebView de
     respaldo.

3. **Respaldo y utilidades.**
   - `WebViewFetcher`: WebView por petición, solo como respaldo cuando había
     sesión y aun así falló la petición (403/503/red). Para rutas `/api/`
     ejecuta un `fetch()` same-origin dentro del WebView.
   - `CloudstreamWebFetcher`: implementación para entornos sin contexto Android
     (pruebas JVM), con `app.get` + `CloudflareKiller`, `cacheTime = 0`,
     allowlist y un reintento ante 403.
   - `WebFetcherFactory` (`HdramaRuntime.kt`): devuelve **una única instancia
     por proceso** (`HdramaHttpFetcher` con contexto Android;
     `CloudstreamWebFetcher` sin contexto) para compartir cookie y sesión entre
     catálogo y API de fuentes. Expone `warmUp()`, que el plugin lanza en
     segundo plano al cargarse.
   - `SourceProbeFactory` (también en `HdramaRuntime.kt`): crea
     `HttpSourceProbe` en Android (con `CloudstreamHttpTransport`) y
     `NoSourceProbe` en JVM/pruebas.
   - `HdramaFetchAllowlist`: solo URLs https de `es.hdrama.net`; el resto se
     rechaza antes de abrir conexión.
   - `SafeHttpClient` + `UrlConnectionHttpTransport` + `RetryPolicy`: capa HTTP
     de los sitemaps, con allowlist, timeouts, reintentos con backoff y
     sanitización de errores. `TtlCache`: caché en memoria con TTL y LRU.
   - `SitemapClient` + `SitemapParser`: descarga y parseo del índice y de los
     `urlset` de sitemaps (respaldo del catálogo).

### `sources`

- `HdramaSourceCipher`: descifra `enc` con un Base64 propio
  (`HdramaBase64`, compatible con `minSdk 21` y pruebas JVM), IV de 12 bytes,
  AES-256-GCM con tag de 128 bits; cualquier fallo devuelve `null`.
- `HdramaSourceClient`: `GET /api/episode-source/<bookId>/<serial>?lang=es&refresh=1`
  con `accept: application/json` y `referer: https://es.hdrama.net/`. Antes de
  llamar a la API ejecuta un warm-up best-effort de la portada (cacheada) para
  que la cookie exista; el WAF de `/api/` no resuelve challenges. Acepta
  `bookId` alfanumérico (no solo numérico), parsea `descriptor.chain` con
  `org.json`, descifra cada entrada y emite **solo URLs directas** de
  `MediaSource.Hls`/`MediaSource.Mp4` sin headers; deduplica por URL y mapea
  `hls`→`Hls`, `mp4`→`Mp4`, infiriendo por extensión (`.m3u8`/`.mp4`) si el
  `type` es desconocido. Ignora `descriptor.subtitle` (puede venir `null` o con
  otra forma y no debe romper el parseo). Ante fallos devuelve `Unavailable`
  con un motivo en español, nunca lanza.
- `SourceProbe` (interfaz): filtra las fuentes que no responden antes de
  entregarlas al player. `NoSourceProbe` no comprueba nada (JVM/pruebas);
  `HttpSourceProbe` pide el manifiesto HLS o un rango de 1 KB del MP4 con un
  timeout de 6 s, sin `Referer`, en paralelo y con caché de 60 s. Conserva el
  orden original; con una sola fuente no hace peticiones; si ninguna responde
  devuelve las originales para que el player muestre el error real y el usuario
  pueda reintentar.

### `player`

- `LinkResolver`: convierte `MediaSource` en enlaces normalizados
  (`ResolvedLink`), deduplica por `tipo|URL|calidad`, ordena por calidad
  descendente y clasifica el resultado (`Playable`, `Browser`, `Failure`).
  Los enlaces directos se construyen sin headers y sin `Referer`.
- `PlaybackStatus`: `COMPATIBLE`, `TEMPORARILY_UNAVAILABLE`,
  `UNSUPPORTED_SOURCE`, `BROWSER_REQUIRED`, `UNAUTHORIZED`, con mensaje en
  español para el usuario.

### `utils`

- `TextNormalizer`: minúsculas, eliminación de diacríticos y espacios,
  normalización de títulos derivados de slug.
- `UrlUtils`: allowlist `es.hdrama.net`, normalización de URLs, extracción de
  slug, esquemas internos `hdrama-demo://` y `hdrama-browser://`, redacción de
  URLs para logs.
- `EpisodeId`: codifica/decodifica `hdrama-demo://episode/<slug>/<n>` y ordena
  episodios por número.
- `Diagnostics`: mensajes de error sin tokens, cookies ni credenciales.

## 6. Prioridad y respaldos

`CatalogRepository` resuelve cada operación con esta prioridad:

- **Sección / búsqueda:** `HdramaCatalogAdapter` (HTML real) → si no hay
  resultados o lanza, `SitemapCatalogAdapter` → si también falla,
  `DemoCatalogAdapter`.
- **Ficha:** URL demo → `DemoCatalogAdapter`; URL de HDrama →
  `HdramaCatalogAdapter.load` (con `ManualHdramaImport` como ficha de
  navegación si el HTML falla) → `SitemapCatalogAdapter.load`; otra URL →
  demo.
- **Episodios:** series demo → `DemoCatalogAdapter`; series `browserOnly` →
  `SitemapCatalogAdapter` (episodio "Abrir en el navegador"); series reales →
  `HdramaCatalogAdapter` y, si el HTML falla o no devuelve episodios, sitemap.
- **Fuentes:** series demo → `DemoCatalogAdapter`; episodio con
  `mediaSources` propios (p. ej. `BrowserOnly`) → esas fuentes; caso real →
  `HdramaCatalogAdapter.resolve`.
- **Relacionadas:** series demo → `DemoCatalogAdapter`; series reales → HTML
  real ("También te puede gustar").

Además, dentro del adaptador real:

- `load()` de una URL de episodio resuelve la ficha de su serie.
- Si `loadSeriesPage` falla, `load()` devuelve una ficha de navegación
  (`ManualHdramaImport.fromUrl`) en lugar de un error.
- Las URLs de directorios (`/categories`, `/sources`, `/category/<slug>`,
  `/source/<slug>`) se convierten en fichas de navegación con las tarjetas de la
  página como recomendaciones.

Si la sesión de Cloudflare no está disponible, cada sección falla rápido con
`WebFetchException(403)` y el repositorio responde con sitemaps; no hay backoff
global: cuando la sesión se resuelve, el catálogo real vuelve solo.

## 7. Flujos

### 7.1 Carga del plugin y warm-up

1. `HDramaPlugin.load(context)` inicializa `HdramaRuntime` con el contexto de la
   app, registra `HDramaProvider` y lanza en segundo plano
   `WebFetcherFactory.warmUp()` con `CoroutineScope(SupervisorJob() +
   Dispatchers.IO)`.
2. `WebFetcherFactory.warmUp()` delega en `HdramaHttpFetcher.warmUp()`, que
   llama a `CloudflareSession.ensure()` bajo el mismo `Mutex` de las peticiones.
3. Si el challenge se resuelve, la cookie queda lista antes de que el usuario
   abra el catálogo o reproduzca. Si falla, se registra el warning y la app
   sigue con el respaldo por sitemaps.

### 7.2 Pantalla principal

1. CloudStream llama `getMainPage(page, request)`; `request.data` es la clave
   de sección.
2. `CatalogSection.fromKey` la mapea (desconocidas → `Novedades`).
3. `CatalogRepository.section` consulta su caché (5 minutos) y luego el HTML
   real; si no hay resultados, sitemaps; si falla, demo.
4. `HdramaCatalogAdapter.section` construye la URL con `HdramaRoutes`,
   descarga el HTML (caché 5 minutos), parsea según la sección y calcula
   `hasNext` (24 elementos por página; las secciones de una página fuerzan
   `false`).
5. El provider mapea cada `Series` a `SearchResponse` y responde con
   `newHomePageResponse`.

### 7.3 Búsqueda

1. `search(query, page)`:
   - si el texto es una URL de HDrama, se carga la ficha real (o navegación);
   - si empieza por `prueba`, se ofrece el catálogo demo para diagnóstico;
   - en caso contrario, `GET /search?q=<texto>`.
2. `HdramaHtmlParser.parseSearchResults` extrae títulos, pósters, notas y
   episodios de las tarjetas `article`.
3. La búsqueda real es de una sola página; si el HTML falla, se usa la
   búsqueda local del sitemap y, en último término, la del demo.

### 7.4 Ficha de serie

1. `load(url)`: recupera el esquema demo si CloudStream antepuso `mainUrl`
   (`recoverDemoSeriesUrl`).
2. `CatalogRepository.load` enruta: demo → `DemoCatalogAdapter`; URL de HDrama
   → `HdramaCatalogAdapter.load` con respaldo de sitemap; otra URL → demo.
3. `HdramaCatalogAdapter.load` distingue URL de episodio, de serie o de
   directorio; descarga el HTML (caché) y parsea `HdramaSeriesPage`.
4. `episodes(series)` devuelve la lista completa del HTML (id estable
   `EpisodeId.encode(slug, númeroMostrado)`, `externalId=bookId:serial`,
   duración, URL del episodio).
5. `related(series)` devuelve las relacionadas del HTML (máximo 20 en el
   provider, sin la propia serie).
6. El provider construye `LoadResponse` con póster, sinopsis, géneros, año,
   nota (`Score.from10`), episodios y recomendaciones.

### 7.5 Reproducción y descarga

1. `loadLinks(data, ...)`: `data` es un `EpisodeId`; `findEpisode` recupera la
   serie y el episodio (desde caché, demo o reparseando la ficha).
2. `resolve(episode)`:
   - demo → fuentes del catálogo demo;
   - `episode.mediaSources` no vacío (p. ej. `BrowserOnly`) → esas fuentes;
   - caso real → `HdramaCatalogAdapter.resolve` → `HdramaSourceClient.sources`.
3. `HdramaSourceClient` hace el warm-up de la portada, consulta la API con
   `accept: application/json` y `referer`, descifra `chain`, deduplica y
   devuelve solo URLs directas. Si no hay nada, `Unavailable` con motivo; un
   403 se traduce a un mensaje claro sobre la sesión de Cloudflare.
4. `HdramaCatalogAdapter.resolve` aplica `SourceProbe.filterPlayable` a la
   lista: si hay más de una URL, comprueba que responde cada una y descarta las
   muertas; la lista filtrada se cachea con el resto de fuentes.
5. `LinkResolver.resolve` clasifica: si hay enlaces, `Playable`; si solo hay
   `BrowserOnly`, `Browser`; si no, `Failure` con `PlaybackStatus`.
6. El provider emite `ExtractorLink`:
   - HLS → `ExtractorLinkType.M3U8` con URL directa y **sin headers**;
   - MP4 → `ExtractorLinkType.VIDEO`;
   - `Browser` lanza `ErrorLoadingException` invitando a abrir el sitio.
7. `loadLinksTimeoutMs = 90_000` da margen a la primera reproducción de una
   sesión (challenge + API).
8. Descarga: CloudStream usa el mismo `ExtractorLink` con
   `hasDownloadSupport = true`; para `M3U8` llama a
   `VideoDownloadManager.downloadHLS` → `M3u8Helper2.hslLazy`. La playlist no
   tiene `#EXT-X-STREAM-INF`, así que `HlsPlaylistParser.parse` devuelve `null`,
   se descargan los segmentos `.ts` y se concatenan en un MP4.
9. Configuración del provider: `sequentialMainPage = true` y
   `sequentialMainPageDelay = 250` serializan las secciones para no abrir
   varios WebViews a la vez; `usesWebView = true` declara el uso del WebView a
   CloudStream; `hasChromecastSupport = false`.

### 7.6 Navegación e importación

- El botón nativo "Abrir en navegador" de CloudStream usa la URL original de la
  ficha (`pageUrl`).
- Pegar una URL `https://es.hdrama.net/series/<slug>` en la búsqueda abre su
  ficha real; si no se puede descargar, se crea una ficha de navegación.
- Las tarjetas de "Acceso a HDrama" no hacen red salvo la demo y el sitio
  oficial.

## 8. Caché y rendimiento

| Caché / control | Clave | TTL / valor | Entradas |
|---|---|---|---|
| `CatalogRepository` | `section:*`, `search:*`, `series:*` | 5 min | 64 |
| HTML de HDrama | URL | 5 min | 24 |
| Fichas parseadas | URL canónica | 5 min | 24 |
| Páginas de navegación | `browseId` | 5 min | 12 |
| Fuentes resueltas | `serie` + `número` + `externalId` | 5 min | 64 |
| Disponibilidad de URLs de video | URL | 60 s | 64 |
| Sitemaps | snapshot | 15 min | 4 |
| Sesión de Cloudflare | proceso | hasta que el sitio la invalide | 1 |
| Backoff tras challenge fallido | `WebViewCloudflareSession` | 15 s | 1 |
| Timeout de petición OkHttp | `CloudstreamHttpTransport` | 60 s | — |
| Timeout de `loadLinks` | `HDramaProvider` | 90 s | — |

- Sin caché en disco.
- Páginas de 24 elementos en el adaptador real (12 en sitemap y demo).
- La caché de fuentes respeta la caducidad de los tokens del sitio (no
  precachear más de 5 minutos).
- Los reintentos de la capa de sitemaps (3 intentos y backoff 250/500 ms)
  solo aplican al respaldo; el fetcher real hace como máximo 2 intentos.
- El warm-up del challenge se ejecuta una vez al cargar el plugin y otra vez,
  best-effort, antes de la API de fuentes.

## 9. Decisiones de diseño

1. **Un modelo de dominio propio** permite probar parser, cifrado y
   adaptadores en JVM sin emulador.
2. **Una sesión, muchas peticiones.** El challenge se resuelve una vez en un
   WebView y todo lo demás va por OkHttp con cookie, User-Agent y `Referer`.
   Elimina el WebView por petición que provocaba ANR y timeouts.
3. **Warm-up temprano.** El plugin adelanta el challenge al cargarse y el
   cliente de fuentes carga la portada antes de `/api/`; el WAF de esa ruta no
   resuelve challenges.
4. **Fallo rápido sin sesión.** Si no hay sesión, se responde 403 y el catálogo
   usa sitemaps en lugar de encadenar WebViews fallidos.
5. **Resolución de fuentes en el momento de reproducir** para no trabajar con
   tokens caducados.
6. **Solo URLs directas.** El proxy espejo desaparece: las URLs del CDN se
   emiten sin headers y funcionan en ExoPlayer y en el gestor de descargas de
   CloudStream.
7. **Comprobar antes de entregar.** La cadena de fuentes puede incluir enlaces
   muertos y CloudStream reproduce el primero de la lista; `SourceProbe` los
   descarta y, si no queda ninguno, entrega los originales para no ocultar el
   error real.
8. **Respaldo por sitemaps y navegación**: la app sigue siendo utilizable si
   Cloudflare, la API o los hosts de video fallan.
9. **La URL del episodio identifica la ficha**; `externalId` identifica la
   fuente (`bookId:serial`) y no se muestra al usuario.

## 10. Riesgos

| Riesgo | Impacto | Mitigación |
|---|---|---|
| Cloudflare no resuelve el challenge | Sin HTML real | Warm-up + fallo rápido; respaldo por sitemaps, navegación y demo |
| Emulador lento agota el timeout del challenge | Primera carga sin sesión | Timeout de 75 s y backoff de 15 s; reintento en la siguiente operación |
| La API o la clave AES cambian | Sin HLS | Parser/cliente tolerantes; `Unavailable` claro; actualizar plugin |
| Tokens caducados | Episodio no reproduce | Resolver al reproducir, caché de 5 min |
| Hosts de video de terceros caídos | Sin reproducción | `SourceProbe` descarta las URLs muertas; mensaje de estado y reintento al abrir el episodio |
| La sonda no puede comprobar una URL (bloqueo, timeout) | Se entrega una URL muerta | Si no responde ninguna se entregan las originales; el player muestra el error real |
| Cambios en selectores HTML o en el formato del `bookId` | Metadatos incompletos | Parser tolerante, `bookId` flexible y fixtures reales en pruebas |
| CloudStream cambia el flujo de descarga | Descarga fallida | El plugin solo entrega `M3U8` directo; el flujo es el estándar de CloudStream |
| API de CloudStream cambia | Ruptura de compilación | Versiones fijas y pruebas de compatibilidad |
| Repositorio no público | Instalación por URL no disponible | Instalación local del `.cs3` (ver [`06-instalacion.md`](06-instalacion.md)) |

## 11. Referencias

- Investigación del sitio: [`02-investigacion-hdrama.md`](02-investigacion-hdrama.md)
- Compilación: [`04-compilacion.md`](04-compilacion.md)
- Pruebas: [`05-pruebas.md`](05-pruebas.md)
- Limitaciones: [`08-limitaciones.md`](08-limitaciones.md)
