# Pruebas (versión 3)

Fecha: 2026-11-12.

## 1. Resumen

| Métrica | Valor |
|---|---|
| Suite | `:HDrama:testDebugUnitTest` |
| Total de pruebas | 117 pruebas unitarias en 18 suites |
| Fallos | 0 (criterio de aceptación: suite en verde) |
| Entorno | JVM, sin red ni emulador |
| Integración opcional | `SitemapLiveTest` con `HDRAMA_INTEGRATION=1` |

La suite se ejecuta con:

```powershell
.\gradlew.bat :HDrama:testDebugUnitTest --console=plain
```

El total se obtiene sumando `testsuite.tests` de los XML generados en
`HDrama/build/test-results/testDebugUnitTest/`.

## 2. Suites y cobertura

### 2.1 Parser HTML real (`com.hdrama.parser`)

`HdramaHtmlParserTest` usa fixtures recortadas de páginas reales del sitio:

- **Ficha completa**: título, sinopsis, nota `7.4` (`span.text-rating`),
  géneros, póster, `bookId` (`15531` en el ejemplo) extraído del payload RSC,
  recuento declarado `numberOfEpisodes=75`, lista de episodios del recorte con
  orden por número mostrado, duraciones `mm:ss` convertidas a segundos,
  `externalId=bookId:serial` y relacionadas sin la propia serie.
- **`bookId` del payload RSC actual**: caso añadido en la v3 con el formato
  verificado el 2026-11-12, donde el `bookId` va seguido del array
  `"episodes"` (`{"bookId":"29358","episodes":[{"chapter_id":"sd29358-1",...}]}`)
  y no de `"slug"`. También cubre que el `bookId` más cercano al slug de la
  ficha gana cuando hay varios (relacionadas) y que el valor puede no ser
  numérico.
- **Listado/categoría**: JSON-LD `CollectionPage` con 24 elementos, URL
  canónica y normalización de hosts.
- **Búsqueda**: extracción de título, póster, nota y número de episodios desde
  las tarjetas `article`.
- **Portada**: `parseHomeSection(html, "Destacados", url)` con la sección DOM.
- **Géneros y fuentes**: `parseGenreCards` y `parseSourceCards` sobre
  `/categories` y `/sources`.
- **Robustez**: HTML vacío, truncado o inválido devuelve `null`/lista vacía sin
  lanzar; campos ausentes quedan en `null`.

### 2.2 Cifrado y fuentes (`com.hdrama.sources`)

- `HdramaSourceCipherTest`: descifra los vectores reales de
  `hdrama-source-enc.json`, incluido el vector Stardust capturado el
  2026-11-12 (`acdn-v-us.stardust-tv.com/.../e4bf2b9fce7c423ca661b3d65529a7f4.m3u8`)
  y los vectores antiguos de FlickReels; también un vector MP4 sintético.
  Entrada vacía, base64 inválido, payload corto, ciphertext manipulado o tag
  truncado devuelven `null`; acepta base64 sin relleno.
- `HdramaSourceClientTest` con `WebFetcher` falso y fixtures
  `hdrama-source-api.json` / `hdrama-source-enc.json`:
  - la API se pide con `accept: application/json` y
    `referer: https://es.hdrama.net/`;
  - la respuesta Stardust descifra a una única URL directa
    `https://acdn-v-us.stardust-tv.com/...m3u8`, sin headers;
  - el warm-up se ejecuta una vez antes de la API y su fallo no impide
    intentarla;
  - deduplicación por URL;
  - mapeo de `type` `hls`/`mp4` y sin espejo;
  - `type` desconocido: se infiere HLS/MP4 por la extensión de la URL;
  - respuesta sin `descriptor`, JSON inválido o error de red →
    `MediaSource.Unavailable` con motivo (sin lanzar);
  - HTTP 403 → motivo con mención explícita a la sesión de Cloudflare;
  - `subtitle` `null` o presente no rompe el parseo;
  - entradas con `enc` corrupto o `type` irresoluble se ignoran.
- `SourceProbeTest` (nuevo en la v3) con `HdramaHttpTransport` falso:
  - descarta el enlace muerto (`pl.shortdizilab.com` 404) y conserva el que
    responde (`zshipricf.farsunpteltd.net` 200);
  - conserva el orden original de los enlaces vivos;
  - si ninguna responde devuelve todas para que el player muestre el error;
  - con una sola fuente no hace peticiones;
  - no sondea `Unavailable`/`BrowserOnly`: si no hay ninguna URL de video que
    comprobar, la lista se devuelve intacta;
  - un fallo de red durante la sonda cuenta como enlace caído.

### 2.3 Red y sesión (`com.hdrama.network`)

- `HdramaHttpFetcherTest` (nuevo en la v3) con `CloudflareSession` y
  `HdramaHttpTransport` falsos, sin Android ni red:
  - las peticiones HTML envían `cookie` (`cf_clearance`), `user-agent` del
    WebView, `referer: https://es.hdrama.net/`, `accept-language` y un `accept`
    de navegador;
  - las peticiones a `/api/` envían `accept: application/json` y el mismo
    `referer` same-origin;
  - ante 403 renueva la sesión (una invalidación) y reintenta; el segundo
    intento devuelve 200;
  - sin sesión falla rápido con `WebFetchException(403)` sin tocar el
    transporte ni abrir el respaldo WebView;
  - si la sesión no se puede renovar, usa el respaldo WebView;
  - un error de transporte usa el respaldo;
  - sin respaldo, propaga `WebFetchException` con el código;
  - rechaza URLs fuera de la allowlist sin abrir conexión.
- `WebFetcherAllowlistTest`: `HdramaFetchAllowlist` acepta solo URLs https de
  `es.hdrama.net` (normalizando `www` y `http`), lanza `WebFetchException` con
  `NO_HTTP_CODE` para URLs ajenas y no registra datos sensibles.
- `SafeHttpClientTest`: fallo, fallo, éxito → 3 intentos y backoff
  `[250, 500]` ms; fallo persistente → `HttpOutcome.Failure`; dominio no
  permitido → `Blocked` sin abrir conexión.
- `TtlCacheTest`: TTL, expiración y límite de entradas.
- `SitemapParserTest` y `SitemapCatalogAdapterTest`: índice, `urlset`,
  secciones, paginación y fichas de navegación con datos de respaldo.
- `SitemapLiveTest`: prueba opcional con red real (`HDRAMA_INTEGRATION=1`).
- `WebViewCloudflareSession` y `WebViewFetcher` no se prueban en JVM (dependen
  del WebView de Android); su verificación es manual en emulador.
  `CloudstreamWebFetcher` tampoco se prueba en JVM porque depende de `app`; la
  lógica del fetcher principal está cubierta con dobles.

### 2.4 Catálogo y respaldos (`com.hdrama.catalog`)

- `HdramaCatalogAdapterTest` con `WebFetcher` falso (sin red):
  - sección Novedades construida desde el JSON-LD real;
  - búsqueda con tarjetas `article`;
  - ficha con nota `7.4`, géneros, póster, `bookId=15531`, episodios con
    `externalId` y duración, y relacionadas sin la propia serie;
  - `resolve` descifra el HLS de la API de fuentes y produce `Playable`;
  - si el HTML falla, la ficha queda como navegación (`browserOnly`);
  - `/categories` se abre como página navegable con recomendaciones.
  La sonda de disponibilidad se inyecta en el adaptador; en JVM el valor por
  defecto es `NoSourceProbe`, así que estas pruebas no hacen red.
- `DemoCatalogAdapterTest`: paginación, búsqueda sin acentos, series demo,
  episodios MP4/HLS y estados.
- `SitemapCatalogAdapterTest`: secciones y fichas de navegación del respaldo.

### 2.5 Reproducción y estados (`com.hdrama.player`)

- `LinkResolverTest`: deduplicación por `tipo|URL|calidad`, orden por calidad
  descendente, enlaces directos sin headers, `Playable`/`Browser`/`Failure` y
  `PlaybackStatus` con mensajes en español.

### 2.6 Utilidades y plugin

- `TextNormalizerTest`: `Corazón` = `corazon`, `Señor` = `senor`, `Jardín` =
  `jardin`, mayúsculas y espacios.
- `UrlUtilsTest`: normalización de esquema/host, rechazo de dominios ajenos,
  `ftp`/`javascript`, extracción de slug, recuperación del esquema demo.
- `EpisodeIdTest`: codificación `hdrama-demo://episode/<slug>/<n>`, parseo y
  orden.
- `PluginInfoTest`: nombre, `VERSION = 3`, `API_VERSION = 1`, clase
  `com.hdrama.HDramaPlugin` y `MAIN_URL`.

## 3. Fixtures

| Fixture | Origen | Uso |
|---|---|---|
| `hdrama-real-detail.html` | Recorte de `tmp/raw/detail-la-novia.html` | Ficha, episodios, `bookId` |
| `hdrama-real-collection.html` | Recorte de `tmp/raw/series-list.html` | Listado JSON-LD |
| `hdrama-real-search.html` | Recorte de `tmp/raw/search.html` | Búsqueda por tarjetas |
| `hdrama-real-home.html` | Recorte de `tmp/raw/home.html` | Portada y secciones |
| `hdrama-real-directories.html` | Recorte de `tmp/raw/` (directorios) | Géneros y fuentes |
| `hdrama-source-api.json` | Respuesta real de `/api/episode-source` del 2026-11-12 (fuente Stardust) | Cliente de fuentes |
| `hdrama-source-enc.json` | Pares `enc` → URL esperada (Stardust 2026-11-12 + FlickReels 2026-11-11) | Cifrado AES-GCM |
| `sitemap-index.xml`, `sitemap-new-es.xml` | Respuestas reales recortadas | Respaldo por sitemaps |

Los recortes conservan la estructura real (JSON-LD, atributos y clases) y pesan
menos de 40 KB cada uno. `tmp/` no se versiona.

## 4. Verificación manual en emulador

Dispositivo: emulador x86_64 Android 15 (AOSP) con CloudStream 4.9.1 y el
plugin v3 instalado.

| Paso | Resultado esperado | Evidencia |
|---|---|---|
| Instalar CloudStream y `HDrama.cs3` | Plugin cargado y proveedor visible | Verificado en emulador Android 15 con CloudStream 4.9.1: plugin v3 cargado y proveedor HDrama registrado |
| Resolver el challenge de Cloudflare | Cookie `cf_clearance` disponible sin bloquear la interfaz | Verificado: log `Challenge resuelto; cookie de sesion actualizada`. El primer intento agotó los 45 s del timeout antiguo; el segundo resolvió a los ~44 s. Con el timeout actual de 75 s se espera que el primer intento baste. |
| Abrir HDrama | Secciones Destacados, Novedades, Más vistos, Tendencias, Catálogo general, Géneros, Fuentes y Acceso | Verificado: catálogo real con portada y banner "Encadenadas" |
| Destacados | Portada real con títulos, pósters y notas | Verificado en la sesión de la v3 (portada y banner "Encadenadas") |
| Novedades / Más vistos / Tendencias / Catálogo | Paginación real (24 por página) | Implementado; paginación cubierta por pruebas JVM |
| Búsqueda | Resultados reales del sitio | Implementada y cubierta con fixtures reales |
| Ficha de serie | Portada, sinopsis, géneros, nota y lista completa de episodios | Verificado: ficha con 64 episodios y `bookId=7acef74a-7aee-4d29-bbad-8c708d967d1e`; log `Ficha encadenadas: bookId=… episodios=64` |
| Reproducción de episodio | HLS real con `Rendered first frame` | Emitido `ExtractorLinkType.M3U8` directo; evidencia final de reproducción en emulador pendiente de cerrar en la entrega |
| Descarga | MP4 concatenado a partir de los segmentos `.ts` | Mecanismo verificado en el código de CloudStream; evidencia final de descarga completa pendiente de cerrar en la entrega |
| Respaldo sin sesión | Catálogo de sitemaps y ficha de navegación | Verificado: al fallar el challenge el catálogo se pobló con sitemaps y las fichas cayeron a navegación |
| Demo | MP4 y HLS públicos en "Acceso a HDrama" | Implementado; verificación pendiente en esta sesión |

Capturas disponibles: las de la versión 2 en [`Docs/images/`](images/)
(catálogo real y ficha real). No sustituyen a la evidencia de la v3, que se
adjuntará con la entrega.

## 5. Pruebas no ejecutadas

- Reproducción y descarga completas en emulador (se cierran en la entrega de la
  v1.2.0) y en hardware físico (Galaxy Z Fold8) y redes móviles.
- Descarga completa desde el player (playlist y segmentos) en dispositivos
  reales.
- Pruebas instrumentadas de UI (Espresso/UIAutomator).
- Pruebas de estrés con el sitio cambiando selectores o la clave de cifrado
  (se cubren con fixtures y casos de error).

## 6. Criterios de aceptación

1. `:HDrama:testDebugUnitTest` en verde, sin red.
2. `:HDrama:make` y `makePluginsJson` generan los artefactos.
3. El plugin carga en CloudStream 4.9.1 y muestra las ocho secciones.
4. Una ficha real muestra metadatos y episodios; un episodio real emite HLS
   directo reproducible.
5. La descarga de un episodio HLS funciona desde el gestor de descargas de
   CloudStream.
6. Sin sesión de Cloudflare, el plugin sigue mostrando catálogo y fichas de
   navegación.
