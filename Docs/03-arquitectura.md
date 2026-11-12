# Arquitectura (versión 2)

Fecha: 2026-11-11. Este documento describe la arquitectura implementada en la
versión 2: integración real con el HTML de HDrama, sesión Cloudflare vía
WebView de CloudStream, API de fuentes con payload cifrado y reproducción HLS.

## 1. Resumen

La versión 2 sustituye el catálogo de solo sitemaps por una integración real
por capas con respaldos encadenados:

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
   │      ├── WebFetcher (WebViewFetcher + CloudstreamWebFetcher de respaldo)
   │      ├── HdramaHtmlParser (JSON-LD + DOM con jsoup)
   │      └── HdramaSourceClient + HdramaSourceCipher (AES-256-GCM)
   ├── SitemapCatalogAdapter  → respaldo con sitemaps públicos (sin metadatos)
   ├── DemoCatalogAdapter     → diagnóstico y último recurso (MP4/HLS públicos)
   └── ManualHdramaImport / AccessCards → fichas de navegación
   │
   ▼
LinkResolver → ExtractorLink (M3U8/VIDEO)
```

La prioridad la decide `CatalogRepository`: intenta el HTML real, si no hay
resultados cae a sitemaps y, si el sitemap también falla, al catálogo demo. Una
ficha concreta que no se puede descargar se convierte en ficha de navegación
con el sitio original.

## 2. Criterios técnicos

1. **Datos reales.** Rutas, JSON-LD, DOM y API verificados contra el sitio el
   2026-11-11 (ver [`02-investigacion-hdrama.md`](02-investigacion-hdrama.md)).
2. **Sesión.** Las peticiones HTML se cargan en un WebView del sistema y las de
   API se piden como XHR same-origin desde ese WebView (`WebViewFetcher`).
   `CloudstreamWebFetcher` (`app.get` + `CloudflareKiller`, una instancia por
   fetcher) queda como respaldo para entornos sin contexto Android.
3. **Tolerancia.** El parser no lanza: los campos ausentes quedan en `null` y
   el cliente de fuentes devuelve `MediaSource.Unavailable` con un motivo.
4. **Fallbacks.** Sitemaps, navegación y demo cubren fallos de Cloudflare, de
   red y del propio sitio.
5. **Sin DRM.** El sitio no usa DRM; solo se modelan HLS/MP4 normales.
6. **Pruebas JVM.** El cifrado, el parser, el cliente de fuentes y los
   adaptadores se prueban con fixtures y fetchers falsos, sin red.
7. **API vigente.** Se usan las factorías actuales de CloudStream
   (`newHomePageResponse`, `newSearchResponseList`, `newTvSeriesLoadResponse`,
   `newEpisode`, `newExtractorLink`), no constructores deprecados.
8. **Compatibilidad.** `minSdk 21`, `compileSdk 35`, JVM target 1.8,
   `apiVersion = 1`, `version = 2`.

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
    ├── build.gradle.kts             # version = 2, metadatos del plugin
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
        │       │   ├── WebViewFetcher.kt / HdramaRuntime.kt
│       │   ├── CloudstreamWebFetcher.kt (respaldo JVM)
        │       │   ├── SafeHttpClient.kt
        │       │   ├── UrlConnectionHttpTransport.kt
        │       │   ├── RetryPolicy.kt
        │       │   ├── SitemapClient.kt
        │       │   ├── SitemapParser.kt
        │       │   └── TtlCache.kt
        │       ├── sources/
        │       │   ├── HdramaSourceCipher.kt
        │       │   └── HdramaSourceClient.kt
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
- `HdramaCatalogAdapter`: integración real. Descarga HTML con `WebFetcher`,
  parsea con `HdramaHtmlParser`, resuelve fuentes con `HdramaSourceClient` y
  cachea HTML, fichas, páginas de navegación y fuentes.
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
}
```

`ParserContract` centraliza los selectores reales (título, póster, sinopsis,
géneros, nota, `dl dt`, episodios `a[data-serial]`, duraciones, relacionadas).
Los listados y categorías usan el JSON-LD `CollectionPage` y las tarjetas
`article` como respaldo; la búsqueda usa tarjetas.

### `network`

- `WebFetcher`: interfaz `suspend fun fetch(url, headers): String`;
  `WebFetchException(code, message)` para errores HTTP.
- `WebViewFetcher`: implementación de producción. Carga la página en un WebView
  del sistema y extrae el HTML; para la API ejecuta un `fetch()` same-origin
  dentro del WebView y sondea el resultado. Serializa peticiones con un `Mutex`
  y aplica timeout.
- `CloudstreamWebFetcher`: respaldo con `app.get` + `CloudflareKiller` (una
  instancia por fetcher, `cacheTime = 0` para que la caché la gestione el
  repositorio), allowlist de HDrama, lectura de `NiceResponse.text`, validación
  de respuesta 2xx y mensajes en español.
- `SafeHttpClient` + `UrlConnectionHttpTransport` + `RetryPolicy`: capa HTTP de
  los sitemaps, con allowlist, timeouts, reintentos con backoff y sanitización
  de errores. `TtlCache`: caché en memoria con TTL y LRU.
- `SitemapClient` + `SitemapParser`: descarga y parseo del índice y de los
  `urlset` de sitemaps (respaldo del catálogo).

### `sources`

- `HdramaSourceCipher`: descifra `enc` con un Base64 propio
  (`HdramaBase64`, compatible con `minSdk 21` y pruebas JVM), IV de 12 bytes,
  AES-256-GCM con tag de 128 bits; cualquier fallo devuelve `null`.
- `HdramaSourceClient`: `GET /api/episode-source/<bookId>/<serial>?lang=es`,
  valida que `bookId` sea numérico, parsea `descriptor.chain` con `org.json`,
  descifra cada entrada y devuelve `MediaSource.Hls`/`Mp4` directas más un
  espejo `https://cdn.dramadizilerim.xyz/https/<URL>` con
  `Referer: https://es.hdrama.net/`; deduplica por URL, descarta tipos que no
  reconoce e ignora `descriptor.subtitle` (puede venir `null` o con otra forma
  y no debe romper el parseo). Ante fallos devuelve `Unavailable`, nunca
  lanza.

### `player`

- `LinkResolver`: convierte `MediaSource` en enlaces normalizados
  (`ResolvedLink`), deduplica por `tipo|URL|calidad`, ordena por calidad
  descendente y clasifica el resultado (`Playable`, `Browser`, `Failure`).
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

## 7. Flujos

### 7.1 Pantalla principal

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

### 7.2 Búsqueda

1. `search(query, page)`:
   - si el texto es una URL de HDrama, se carga la ficha real (o navegación);
   - si empieza por `prueba`, se ofrece el catálogo demo para diagnóstico;
   - en caso contrario, `GET /search?q=<texto>`.
2. `HdramaHtmlParser.parseSearchResults` extrae títulos, pósters, notas y
   episodios de las tarjetas `article`.
3. La búsqueda real es de una sola página; si el HTML falla, se usa la
   búsqueda local del sitemap y, en último término, la del demo.

### 7.3 Ficha de serie

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

### 7.4 Reproducción

1. `loadLinks(data, ...)`: `data` es un `EpisodeId`; `findEpisode` recupera la
   serie y el episodio (desde caché, demo o reparseando la ficha).
2. `resolve(episode)`:
   - demo → fuentes del catálogo demo;
   - `episode.mediaSources` no vacío (p. ej. `BrowserOnly`) → esas fuentes;
   - caso real → `HdramaSourceClient.sources(bookId, serial)`.
3. `HdramaSourceClient` consulta la API, descifra `chain`, deduplica y añade el
   espejo. Si no hay nada, `Unavailable` con motivo.
4. `LinkResolver.resolve` clasifica: si hay enlaces, `Playable`; si solo hay
   `BrowserOnly`, `Browser`; si no, `Failure` con `PlaybackStatus`.
5. El provider emite `ExtractorLink`:
   - HLS → `ExtractorLinkType.M3U8`;
   - MP4 → `ExtractorLinkType.VIDEO`;
   - el espejo se emite con `Referer: https://es.hdrama.net/` y la etiqueta
     "(espejo)";
   - `Browser` lanza `ErrorLoadingException` invitando a abrir el sitio.

### 7.5 Navegación e importación

- El botón nativo "Abrir en navegador" de CloudStream usa la URL original de la
  ficha (`pageUrl`).
- Pegar una URL `https://es.hdrama.net/series/<slug>` en la búsqueda abre su
  ficha real; si no se puede descargar, se crea una ficha de navegación.
- Las tarjetas de "Acceso a HDrama" no hacen red salvo la demo y el sitio
  oficial.

## 8. Caché y rendimiento

| Caché | Clave | TTL | Entradas |
|---|---|---|---|
| `CatalogRepository` | `section:*`, `search:*`, `series:*` | 5 min | 64 |
| HTML de HDrama | URL | 5 min | 24 |
| Fichas parseadas | URL canónica | 5 min | 24 |
| Páginas de navegación | `browseId` | 5 min | 12 |
| Fuentes resueltas | `serie` + `número` + `externalId` | 5 min | 64 |
| Sitemaps | snapshot | 15 min | 4 |

- Sin caché en disco.
- Páginas de 24 elementos en el adaptador real (12 en sitemap y demo).
- La caché de fuentes respeta la caducidad de los tokens del sitio (no
  precachear más de 5 minutos).
- Los reintentos de la capa de sitemaps (3 intentos y backoff 250/500 ms)
  solo aplican al respaldo; el fetcher real delega en CloudStream.

## 9. Decisiones de diseño

1. **Un modelo de dominio propio** permite probar parser, cifrado y
   adaptadores en JVM sin emulador.
2. **La sesión la resuelve el WebView del sistema** (`WebViewFetcher`) en lugar de
   manipular cookies o WebView propios: menos código sensible y coherente con
   la app anfitriona.
3. **Resolución de fuentes en el momento de reproducir** para no trabajar con
   tokens caducados.
4. **Espejo con `Referer`** como segunda opción de reproducción cuando el host
   directo falla.
5. **Respaldo por sitemaps y navegación**: la app sigue siendo utilizable si
   Cloudflare, la API o los hosts de video fallan.
6. **La URL del episodio identifica la ficha**; `externalId` identifica la
   fuente (`bookId:serial`) y no se muestra al usuario.

## 10. Riesgos

| Riesgo | Impacto | Mitigación |
|---|---|---|
| Cloudflare no resuelve el challenge | Sin HTML real | Respaldo por sitemaps, navegación y demo |
| La API o la clave AES cambian | Sin HLS | Parser/cliente tolerantes; `Unavailable` claro; actualizar plugin |
| Tokens caducados | Episodio no reproduce | Resolver al reproducir, caché de 5 min |
| Hosts/proxy de terceros caídos | Sin reproducción | Cadena de fuentes: directa y espejo; mensaje de estado |
| Cambios en selectores HTML | Metadatos incompletos | Parser tolerante y fixtures reales en pruebas |
| API de CloudStream cambia | Ruptura de compilación | Versiones fijas y pruebas de compatibilidad |
| Repositorio no público | Instalación por URL no disponible | Instalación local del `.cs3` (ver [`06-instalacion.md`](06-instalacion.md)) |

## 11. Referencias

- Investigación del sitio: [`02-investigacion-hdrama.md`](02-investigacion-hdrama.md)
- Compilación: [`04-compilacion.md`](04-compilacion.md)
- Pruebas: [`05-pruebas.md`](05-pruebas.md)
- Limitaciones: [`08-limitaciones.md`](08-limitaciones.md)
