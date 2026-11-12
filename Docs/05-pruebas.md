# Pruebas (versión 2)

Fecha: 2026-11-11.

## 1. Resumen

| Métrica | Valor |
|---|---|
| Suite | `:HDrama:testDebugUnitTest` |
| Total de pruebas | 98 pruebas unitarias en 16 suites |
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
  `hdrama-source-enc.json` (incluidos `mqDHwriF...` →
  `pl.shortdizilab.com/...` y `ueB4Z517...` →
  `zshipricf.farsunpteltd.net/...`) y un vector MP4 sintético; entrada vacía,
  base64 inválido, payload corto, ciphertext manipulado o tag truncado
  devuelven `null`; también acepta base64 sin relleno.
- `HdramaSourceClientTest` con `WebFetcher` falso y fixtures
  `hdrama-source-api.json` / `hdrama-source-enc.json`:
  - parseo de `descriptor.chain` con dos entradas `hls`;
  - URLs directas primero y proxy espejo
    `https://cdn.dramadizilerim.xyz/https/<URL>` con `Referer`;
  - deduplicación por URL;
  - mapeo de `type` `hls`/`mp4`;
  - respuesta sin `descriptor`, JSON inválido o error de red →
    `MediaSource.Unavailable` con motivo (sin lanzar);
  - `subtitle` `null` no rompe el parseo (la respuesta se procesa igual).

### 2.3 Red y allowlist (`com.hdrama.network`)

- `WebFetcherAllowlistTest`: `HdramaFetchAllowlist` acepta solo URLs https de
  `es.hdrama.net` (normalizando `www` y `http`), lanza `WebFetchException` con
  `NO_HTTP_CODE` para URLs ajenas y no registra datos sensibles.
- `WebViewFetcher` no se prueba en JVM (depende del WebView de Android); `CloudstreamWebFetcher` tampoco (depende de `app`). La verificación es manual en emulador.
  Android): se mantiene fino y delega allowlist, errores HTTP y mensajes en
  clases testeables.
- `SafeHttpClientTest`: fallo, fallo, éxito → 3 intentos y backoff
  `[250, 500]` ms; fallo persistente → `HttpOutcome.Failure`; dominio no
  permitido → `Blocked` sin abrir conexión.
- `SitemapParserTest` y `SitemapCatalogAdapterTest`: índice, `urlset`,
  secciones, paginación y fichas de navegación con datos de respaldo.
- `SitemapLiveTest`: prueba opcional con red real (`HDRAMA_INTEGRATION=1`).

### 2.4 Catálogo y respaldos (`com.hdrama.catalog`)

- `HdramaCatalogAdapterTest` con `WebFetcher` falso (sin red):
  - sección Novedades construida desde el JSON-LD real;
  - búsqueda con tarjetas `article`;
  - ficha con nota `7.4`, géneros, póster, `bookId=15531`, episodios con
    `externalId` y duración, y relacionadas sin la propia serie;
  - `resolve` descifra el HLS de la API de fuentes y produce `Playable`;
  - si el HTML falla, la ficha queda como navegación (`browserOnly`);
  - `/categories` se abre como página navegable con recomendaciones.
- `DemoCatalogAdapterTest`: paginación, búsqueda sin acentos, series demo,
  episodios MP4/HLS y estados.
- `SitemapCatalogAdapterTest`: secciones y fichas de navegación del respaldo.

### 2.5 Reproducción y estados (`com.hdrama.player`)

- `LinkResolverTest`: deduplicación por `tipo|URL|calidad`, orden por calidad
  descendente, `Playable`/`Browser`/`Failure` y `PlaybackStatus` con mensajes
  en español.

### 2.6 Utilidades y plugin

- `TextNormalizerTest`: `Corazón` = `corazon`, `Señor` = `senor`, `Jardín` =
  `jardin`, mayúsculas y espacios.
- `UrlUtilsTest`: normalización de esquema/host, rechazo de dominios ajenos,
  `ftp`/`javascript`, extracción de slug, recuperación del esquema demo.
- `EpisodeIdTest`: codificación `hdrama-demo://episode/<slug>/<n>`, parseo y
  orden.
- `PluginInfoTest`: nombre, `VERSION = 2`, `API_VERSION = 1`, clase
  `com.hdrama.HDramaPlugin` y `MAIN_URL`.

## 3. Fixtures

| Fixture | Origen | Uso |
|---|---|---|
| `hdrama-real-detail.html` | Recorte de `tmp/raw/detail-la-novia.html` | Ficha, episodios, `bookId` |
| `hdrama-real-collection.html` | Recorte de `tmp/raw/series-list.html` | Listado JSON-LD |
| `hdrama-real-search.html` | Recorte de `tmp/raw/search.html` | Búsqueda por tarjetas |
| `hdrama-real-home.html` | Recorte de `tmp/raw/home.html` | Portada y secciones |
| `hdrama-real-directories.html` | Recorte de `tmp/raw/` (directorios) | Géneros y fuentes |
| `hdrama-source-api.json` | Respuesta real de `/api/episode-source` | Cliente de fuentes |
| `hdrama-source-enc.json` | Pares `enc` → URL esperada | Cifrado AES-GCM |
| `sitemap-index.xml`, `sitemap-new-es.xml` | Respuestas reales recortadas | Respaldo por sitemaps |

Los recortes conservan la estructura real (JSON-LD, atributos y clases) y pesan
menos de 40 KB cada uno. `tmp/` no se versiona.

## 4. Verificación manual en emulador

Dispositivo previsto: emulador x86_64, Android con CloudStream 4.9.1.
La tabla se completa con la evidencia de la sesión de laboratorio de la
versión 2.

| Paso | Resultado esperado | Evidencia |
|---|---|---|
| Instalar CloudStream y `HDrama.cs3` | Plugin cargado y proveedor visible | Verificado en emulador Android 14 (AOSP API 34) con CloudStream 4.9.1: `Loaded plugin HDrama.cs3 successfully` y proveedor HDrama registrado |
| Abrir HDrama | Secciones Destacados, Novedades, Más vistos, Tendencias, Catálogo general, Géneros, Fuentes y Acceso | Verificado el catálogo real; las secciones que no cabían en pantalla se validan por pruebas JVM con fixtures reales |
| Destacados | Portada real con títulos, pósters y notas | Verificado: portada con títulos reales y notas (p. ej. 8.8, 9.5, 8.1) |
| Novedades / Más vistos / Tendencias / Catálogo | Paginación real (24 por página) | Implementado; paginación cubierta por pruebas JVM, verificación visual pendiente |
| Búsqueda | Resultados reales del sitio | Implementada y cubierta con fixtures reales; verificación visual pendiente |
| Ficha de serie | Portada, sinopsis, géneros, nota y lista completa de episodios | Verificado: título, sinopsis, `8.4/10.0`, género, póster y `41 Episodes` desde el HTML real |
| Reproducción de episodio | HLS real con `Rendered first frame` | Pendiente de confirmar en hardware físico; el descifrado AES-GCM y el parseo de la API se validan con vectores reales en JVM |
| Respaldo sin sesión | Catálogo de sitemaps y ficha de navegación | Verificado: al fallar el challenge el catálogo se pobló con sitemaps y las fichas cayeron a navegación |
| Demo | MP4 y HLS públicos en "Acceso a HDrama" | Implementado; verificación pendiente en esta sesión |

Capturas históricas de la versión 1 en [`Docs/images/`](images/): no
sustituyen a la evidencia de la versión 2.

## 5. Pruebas no ejecutadas

- Reproducción en hardware físico (Galaxy Z Fold8) y en redes móviles.
- Descarga completa desde el player (manifest y segmentos) en dispositivos
  reales.
- Pruebas instrumentadas de UI (Espresso/UIAutomator).
- Pruebas de estrés con el sitio cambiando selectores o la clave de cifrado
  (se cubren con fixtures y casos de error).

## 6. Criterios de aceptación

1. `:HDrama:testDebugUnitTest` en verde, sin red.
2. `:HDrama:make` y `makePluginsJson` generan los artefactos.
3. El plugin carga en CloudStream 4.9.1 y muestra las ocho secciones.
4. Una ficha real muestra metadatos y episodios; un episodio real emite HLS.
5. Sin sesión de Cloudflare, el plugin sigue mostrando catálogo y fichas de
   navegación.
