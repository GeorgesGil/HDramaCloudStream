# Investigación: HDrama (es.hdrama.net)

Fecha: 2026-11-11. Actualizado el 2026-11-12 para la versión 3 del plugin.

La investigación inicial (2026-11-11) observó los sitemaps públicos y el
Cloudflare Managed Challenge. Para la versión 2 se repitió el análisis con una
sesión de navegador Chromium real (Playwright) que superó el challenge: se
descargaron las páginas HTML del sitio y se analizaron las rutas, los datos
estructurados JSON-LD, el DOM de las fichas y la API interna de fuentes de
video. Los HTML completos de referencia quedaron en `tmp/raw/` (no
versionados); las fixtures de las pruebas son versiones recortadas.

La versión 3 corrige la capa de red con evidencia adicional tomada el
2026-11-12: comprobaciones `curl` de la sesión (cookie `cf_clearance`,
User-Agent y `Referer`), respuesta real de la API de fuentes con la fuente
Stardust y verificación del flujo de descarga que usa CloudStream. Esa evidencia
está en las secciones 3, 8 y 9.

## 1. Identidad del sitio

| Campo | Valor |
|---|---|
| URL | https://es.hdrama.net/ |
| Idioma | Español |
| Plataforma | Next.js App Router (Turbopack) |
| Datos estructurados | JSON-LD `application/ld+json` en todas las páginas relevantes |
| Fichas de serie | `/series/<slug>` (`/es/series/<slug>` redirige) |
| Episodios | `/series/<slug>/episode-<numeroMostrado>` |
| API de fuentes | `/api/episode-source/<bookId>/<serial>?lang=es&refresh=1` |
| Sitemap índice | `https://es.hdrama.net/sitemap.xml` |
| Sitemaps hijos | `/sitemaps/new-es`, `/sitemaps/trending-es`, `/sitemaps/fresh-es`, `/sitemaps/static-es` |

El `bookId` que usa la API no tiene un formato único: en las muestras aparece
como número (`15531`, `29358`), como id hexadecimal, como UUID
(`7acef74a-7aee-4d29-bbad-8c708d967d1e` en el emulador del 2026-11-12) o como
el propio slug de la serie. El parser y el cliente de fuentes lo aceptan como
texto alfanumérico.

## 2. `robots.txt`

Contenido relevante (2026-11-11):

```text
User-Agent: *
Allow: /
Disallow: /api/
Disallow: /flags/
Disallow: /source-icons/

User-Agent: AhrefsBot
User-Agent: SemrushBot
...
Disallow: /

Sitemap: https://es.hdrama.net/sitemap.xml
```

Interpretación:

- `robots.txt` permite el sitio HTML y prohíbe `/api/` a los rastreadores,
  además de bloquear bots de SEO.
- Los sitemaps están publicados como fuentes de descubrimiento de URLs.
- La API `/api/episode-source/...` es una ruta interna que usa el propio
  reproductor del sitio. El plugin la consulta solo cuando el usuario abre la
  reproducción de un episodio concreto; no se rastrea ni se precachea el
  catálogo completo por esa vía. Es una dependencia no documentada y puede
  cambiar sin aviso.

## 3. Cloudflare

Solicitud `GET https://es.hdrama.net/` con un cliente HTTP simple (sin
JavaScript) devuelve:

```text
HTTP/1.1 403 Forbidden
Server: cloudflare
Cf-Mitigated: challenge
Content-Type: text/html; charset=UTF-8
```

El cuerpo es la página "Just a moment..." de **Cloudflare Managed Challenge**,
que exige JavaScript y cookies. Los sitemaps (`/sitemap.xml` y
`/sitemaps/*`) siguen respondiendo HTTP 200 sin challenge.

### Evidencia de la sesión (2026-11-12)

Comprobaciones con `curl` contra el sitio:

| Petición | Resultado |
|---|---|
| `GET /` sin cookie | 403 (challenge) |
| `GET /` con cookie `cf_clearance` y User-Agent de Chrome | 200 (HTML real) |
| `GET /api/episode-source/...` con cookie y sin `Referer` | 403 `{"error":"forbidden"}` |
| `GET /api/episode-source/...` con cookie y `Referer: https://es.hdrama.net/` | 200 |
| `GET /api/episode-source/...` con cookie y `Sec-Fetch-Site: same-origin` | 200 |

Conclusión: la API exige una petición **same-origin** a nivel de cabeceras
(`Referer` o `Sec-Fetch-Site`), no un XHR dentro de un WebView. Basta con
resolver el challenge una vez y usar un cliente HTTP normal para todo.

### Cómo se resuelve en el plugin (versión 3)

1. `WebViewCloudflareSession` crea **un solo** WebView y carga
   `https://es.hdrama.net/`.
2. Sondea `CookieManager.getCookie()` cada 350 ms hasta que aparece
   `cf_clearance` (timeout 75 s en emuladores lentos; en un teléfono normal se
   resuelve en pocos segundos).
3. Destruye el WebView y guarda la cookie y `WebSettings.getDefaultUserAgent()`.
   El User-Agent debe coincidir con el que resolvió el challenge.
4. Todas las peticiones posteriores (HTML y API) van por OkHttp con esa cookie,
   ese User-Agent y `Referer: https://es.hdrama.net/`.
5. `invalidate()` fuerza un challenge nuevo tras un 403/503 sin borrar todas
   las cookies de la app; tras un fallo hay un backoff de 15 s para no encadenar
   WebViews.

El WAF de `/api/` **no** resuelve challenges: lanzar un WebView sobre esa ruta
solo perdería tiempo y acabaría en 403. Por eso el cliente de fuentes hace un
warm-up de la portada antes de llamar a la API y el plugin adelanta el
challenge al cargarse.

## 4. Rutas reales verificadas

| Uso | Ruta | Notas |
|---|---|---|
| Inicio | `/` | Portada; JSON-LD `ItemList` "Novedades" y secciones en el DOM |
| Catálogo general | `/series` | Paginado `/series/page/<N>`; 84 páginas, 24 por página |
| Novedades | `/category/new-releases` | Paginado `/category/new-releases/page/<N>` |
| Más vistos | `/category/popular` | Paginado `/category/popular/page/<N>` |
| Tendencias | `/category/trending` | Paginado; 13 páginas en la comprobación |
| Géneros | `/categories` | Directorio; cada género en `/category/<slug>` |
| Fuentes | `/sources` | Directorio; cada fuente en `/source/<slug>` |
| Búsqueda | `/search?q=<texto>` | Una sola página, sin paginación |
| Ficha de serie | `/series/<slug>` | Los enlaces usan `/es/series/<slug>`; ambas resuelven |
| Episodio | `/series/<slug>/episode-<n>` | `<n>` es el número mostrado (puede no ser correlativo) |
| Fuente de video | `/api/episode-source/<bookId>/<serial>` | Requiere `lang=es`; `refresh=1` fuerza token nuevo |

## 5. Datos estructurados JSON-LD

### Ficha de serie: nodo `TVSeries` dentro de `@graph`

```json
{"@type":"TVSeries","name":"...","url":"https://es.hdrama.net/series/<slug>",
 "description":"...","image":"https://...jpg","numberOfEpisodes":75,
 "genre":["Venganza"],"inLanguage":"es-ES"}
```

El nodo **no** incluye `aggregateRating`: la nota se lee del DOM
(`span.text-rating`, por ejemplo `7.4`).

### Listados y categorías: nodo `CollectionPage`

```json
{"@type":"CollectionPage","name":"...","mainEntity":{"@type":"ItemList",
 "numberOfItems":24,"itemListElement":[{"position":1,
  "url":"https://es.hdrama.net/series/<slug>","name":"Título","image":"https://...jpg"}]}}
```

Los `url` usan el host `https://es.hdrama.net/series/...` (a veces
`https://hdrama.net/es/series/...` en migas de pan); se normalizan con
`UrlUtils`.

### Portada: `@graph` con `Organization`, `WebSite` e `ItemList`

El `ItemList` de la portada se llama "Novedades" y trae 24 elementos. Las demás
secciones (Destacados, Más vistos, nuevos de la semana, Géneros, Fuentes) solo
existen en el DOM.

### Página de episodio: `TVEpisode`

Incluye `name`, `episodeNumber`, `partOfSeries{name,url}`, `image` y
`description`.

## 6. DOM de la ficha de serie

Selectores verificados con jsoup sobre `tmp/raw/detail-la-novia.html`:

- Título: `h1`.
- Sinopsis: `description` del JSON-LD (preferible) o el primer
  `details > summary > span`.
- Nota: `span.text-rating` (no confundir con el `span` "Puntuación" del
  contenedor).
- Metadatos: `dl dt` con etiquetas `Capítulos`, `Géneros` y `Fuente`; los `dd`
  contienen el valor y los géneros son `dd span`.
- Póster: `image` del JSON-LD; alternativa `img[alt="<título>"]` (el primer
  `img` de la página es un icono de bandera y no debe usarse).
- Lista de episodios: `a[data-serial][href*="/episode-"]` con:
  - `data-serial`: serial real que espera la API;
  - `data-display`: número mostrado al usuario;
  - `aria-label="Capítulo N"`;
  - título en la primera `span.block.truncate`;
  - duración en la siguiente `span` con formato `m:ss` o `mm:ss`.
  Se muestran todos los episodios (75 en el ejemplo, 64 en el del emulador),
  no solo los primeros 30.
- Relacionadas: bajo el `h2` "También te puede gustar" hay tarjetas
  `article` con `a[href*="/series/"]`, `img[alt]` y `aria-label`. Se excluye la
  propia ficha.
- `bookId`: no está en un atributo visible; vive en el payload RSC embebido en
  scripts inline y con barras invertidas de escape. La técnica fiable es
  eliminar todas las barras invertidas del HTML
  (`html.replace("\\", "")`) y buscar
  `bookId"\s*:\s*"([A-Za-z0-9_-]{1,80})"`. En la ficha aparecen `bookId` de
  otras series (relacionadas), así que se elige el más cercano al slug de la
  ficha actual.
- Formato RSC actual (2026-11-12): el `bookId` ya no va seguido de `"slug"`,
  sino del array de episodios:
  `{"bookId":"29358","episodes":[{"chapter_id":"sd29358-1","serial_number":1,...}]}`.
  El parser prefiere el `bookId` seguido de `,"episodes"` cuando no puede
  emparejarlo por cercanía al slug.
- El `bookId` puede ser un UUID (evidencia del emulador:
  `7acef74a-7aee-4d29-bbad-8c708d967d1e`), no solo un número.
- `firstSerial` (también en el payload RSC) es el serial base; los
  `data-serial` de los episodios ya vienen resueltos.

## 7. Tarjetas de listado y búsqueda

Cada tarjeta es un `article` con esta estructura:

```html
<article class="group">
  <a aria-label="Título" href="/es/series/<slug>"><img alt="Título" src="https://...">
    <span ...>EP 75</span><span ...>7.4</span>...
  </a>
</article>
```

- Título: `img[alt]` (más fiable) o `aria-label`.
- Póster: `img[src]`; para reducir peso se usa el primer candidato del
  `srcset` (256w) si existe.
- Nota y número de episodios: spans del overlay (`EP 75`, `7.4`); son
  opcionales y no bloquean si faltan.
- URL normalizada y deduplicada por slug.
- En los listados se prefiere el JSON-LD `CollectionPage`; las tarjetas se usan
  como respaldo y para búsqueda y portada.

## 8. API de fuentes de video y cifrado

`GET https://es.hdrama.net/api/episode-source/<bookId>/<serial>?lang=es&refresh=1`

Respuesta real del 2026-11-12 (fixture `hdrama-source-api.json`, `enc`
abreviado):

```json
{"descriptor":{"source":"Stardust","chain":[
 {"enc":"bA0EB72B...","type":"hls","source":"Stardust","refresh":false,"refreshAfter":null}
],"subtitle":null}}
```

La URL descifrada es:

```text
https://acdn-v-us.stardust-tv.com/prod/29358/1/e4bf2b9fce7c423ca661b3d65529a7f4.m3u8
```

Es una **media playlist** HLS: no tiene `#EXT-X-STREAM-INF` ni variantes de
calidad, solo segmentos `.ts` relativos. La fuente anterior era FlickReels.

### Descifrado de `enc`

1. Base64 estándar a bytes.
2. IV = primeros 12 bytes; el resto es ciphertext + tag GCM (últimos 16
   bytes).
3. AES-256-GCM con la clave estática que usa el propio bundle del sitio:
   `Base64("QC6Ir2trghxRAyyyWZEOEFR4GgLhnfQ4A19I3QBlQkc=")` (32 bytes). No es un
   secreto de usuario: va embebida en el JavaScript público del sitio.
4. `Cipher.getInstance("AES/GCM/NoPadding")` con
   `GCMParameterSpec(128, iv)`; el texto plano es la URL.

Vectores de prueba (fixture `hdrama-source-enc.json`), con `enc` abreviado:

| `enc` (prefijo) | URL descifrada | Origen |
|---|---|---|
| `bA0EB72B...` | `https://acdn-v-us.stardust-tv.com/prod/29358/1/e4bf2b9fce7c423ca661b3d65529a7f4.m3u8` | 2026-11-12, fuente Stardust |
| `eomFYhl/...` | `https://pl.shortdizilab.com/v1/flickreels/23935678.m3u8` | 2026-11-11, FlickReels |
| `bT3GSNnw...` | `https://zshipricf.farsunpteltd.net/playlet-hls/1793611042_hls_47068.m3u8?verify=...` | 2026-11-11, FlickReels |
| `fUWHIriZ...` | `https://pl.shortdizilab.com/v1/flickreels/23935680.m3u8` | 2026-11-11, FlickReels |

`refresh` y `refreshAfter` indican que el token caduca: hay que resolver la
fuente en el momento de reproducir y no reutilizarla más de unos minutos.
`subtitle` fue `null` en la muestra; el cliente lo ignora de forma defensiva
(puede venir `null`, objeto o lista) y no emite subtítulos desde esta API.

## 9. Reproducción y descarga de las URLs obtenidas

Comprobaciones con peticiones desde el host:

- URL directa del CDN de Stardust: **200 sin cabecera `Referer`**; el plugin la
  emite sin headers y ExoPlayer reproduce el HLS.
- La API que entrega la URL **sí** exige `Referer: https://es.hdrama.net/`
  (sección 3); la URL del CDN no.
- Proxy espejo `https://cdn.dramadizilerim.xyz/https/<URL directa>`: devuelve
  **451 sin `Referer`** y **403 con `Referer`**. Además el código de la v2 lo
  construía mal (`/https/https://...`). Se eliminó: ya no se emite.
- Las entradas antiguas de `pl.shortdizilab.com/v1/flickreels/...` devolvieron
  404 en las comprobaciones; el cliente descarta lo que no puede descifrar o
  mapear y el player prueba los enlaces que sí se emiten.
- La cadena de fuentes puede traer varios enlaces y alguno muerto: en la
  comprobación del 2026-11-12 el primer enlace
  (`pl.shortdizilab.com/v1/flickreels/21648199.m3u8`) devolvía 404 y el segundo
  (`zshipricf.farsunpteltd.net/playlet-hls/...`) respondía 200. CloudStream
  reproduce el primero de la lista, por eso el plugin comprueba cada URL antes
  de entregarla (ver "Selección del enlace").
- No hay DRM en el sitio: solo HLS/MP4 normales. `MediaSource` no modela DRM.

### Selección del enlace

`HttpSourceProbe` (en `sources/SourceProbe.kt`) comprueba la disponibilidad de
cada URL antes de entregarla al reproductor:

- Con una sola URL no hace ninguna petición.
- Para HLS pide el inicio del manifiesto (es pequeño); para MP4 pide un rango de
  1 KB. Timeout de 6 s por URL, sin `Referer` y en paralelo.
- Conserva el orden original, descarta las URLs que no responden y cachea el
  resultado 60 s.
- Si ninguna responde, devuelve las originales para que el player muestre el
  error real y el usuario pueda reintentar.

### Descarga desde CloudStream

CloudStream descarga el mismo enlace que reproduce el plugin:

1. `HDramaProvider` emite `ExtractorLinkType.M3U8` con la URL directa y sin
   headers (`hasDownloadSupport = true`).
2. Para `ExtractorLinkType.M3U8`, CloudStream usa
   `VideoDownloadManager.downloadHLS` → `M3u8Helper2.hslLazy`.
3. `HlsPlaylistParser.parse` devuelve `null` porque la playlist no tiene
   `#EXT-X-STREAM-INF`; el flujo continúa como "media segments".
4. Se descargan los segmentos `.ts` (relativos a la URL del playlist) y se
   concatenan en un archivo MP4. No hace falta proxy ni cabeceras especiales.

## 10. Estado de implementación (versión 3)

| Módulo | Estado | Implementación |
|---|---|---|
| Catálogo real por secciones | Implementado | `HdramaCatalogAdapter` + `HdramaHtmlParser`, JSON-LD y DOM de portada/categorías |
| Búsqueda real | Implementado | `/search?q=` con tarjetas `article` |
| Ficha de serie completa | Implementado | JSON-LD `TVSeries`, DOM, nota y `bookId` del payload RSC |
| Lista de episodios | Implementado | `data-serial`/`data-display`, duraciones y `externalId=bookId:serial` |
| Fuentes de video | Implementado | API `/api/episode-source`, `HdramaSourceCipher` AES-256-GCM, solo URLs directas |
| Comprobación de enlaces | Implementado | `HttpSourceProbe` (HLS/MP4, 6 s, sin `Referer`, caché de 60 s) descarta URLs muertas antes del player |
| Reproducción HLS | Implementado | `ExtractorLinkType.M3U8` con URL directa y sin headers; ExoPlayer reproduce el HLS |
| Descarga HLS | Implementado | CloudStream descarga el playlist, concatena los segmentos `.ts` en un MP4 |
| Sesión Cloudflare | Implementado | `WebViewCloudflareSession` resuelve el challenge una vez; `HdramaHttpFetcher` hace el resto por OkHttp; warm-up al cargar el plugin |
| Respaldo sin sesión | Implementado | Sitemaps públicos, fichas de navegación e importación manual |
| Catálogo demo | Implementado | 17 series sintéticas y MP4/HLS públicos en "Acceso a HDrama" |

## 11. Evidencia y límites de esta investigación

- Sesión Chromium real (Playwright) que superó el Cloudflare Managed
  Challenge el 2026-11-11; los HTML completos están en `tmp/raw/` (no
  versionados) y las versiones recortadas en
  `HDrama/src/test/resources/fixtures/`.
- Sesión de comprobación del 2026-11-12 con `curl` para la cookie, el
  User-Agent y el `Referer` de la API, y con el emulador Android 15 para el
  challenge y el catálogo real (`bookId` UUID con 64 episodios).
- El sitio es un objetivo en movimiento: rutas, selectores, claves de
  cifrado, formato del `bookId` y hosts de video pueden cambiar sin aviso. La
  capa de respaldo (sitemaps, navegación y demo) cubre esos fallos, y las
  limitaciones conocidas están en [`08-limitaciones.md`](08-limitaciones.md).
- La nota y los metadatos dependen de la estructura del HTML; el parser es
  tolerante y deja campos opcionales en `null` cuando no los encuentra.
- La verificación de reproducción y descarga completas en emulador se cierra en
  la entrega v1.2.0; las suites JVM y el análisis del código de CloudStream
  cubren el mecanismo.

## 12. Referencias

- https://es.hdrama.net/ (sitio objetivo)
- https://es.hdrama.net/robots.txt
- https://es.hdrama.net/sitemap.xml
- https://developers.cloudflare.com/cloudflare-challenges/ (Managed Challenge)
- https://www.rfc-editor.org/rfc/rfc9309 (Robots Exclusion Protocol)
