# Investigación: HDrama (es.hdrama.net)

Fecha: 2026-11-11. Actualizado para la versión 2 del plugin.

La investigación inicial (2026-11-11) observó los sitemaps públicos y el
Cloudflare Managed Challenge. Para la versión 2 se repitió el análisis con una
sesión de navegador Chromium real (Playwright) que superó el challenge: se
descargaron las páginas HTML del sitio y se analizaron las rutas, los datos
estructurados JSON-LD, el DOM de las fichas y la API interna de fuentes de
video. Los HTML completos de referencia quedaron en `tmp/raw/` (no
versionados); las fixtures de las pruebas son versiones recortadas.

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

En el plugin la sesión se resuelve con el interceptor de CloudStream:

```kotlin
app.get(url, interceptor = CloudflareKiller())
```

`CloudflareKiller` abre un WebView, resuelve el challenge y reutiliza las
cookies (`cf_clearance`) en las peticiones posteriores. Se crea **una sola
instancia por fetcher**, porque su inicialización borra cookies. La API
`/api/episode-source/...` también exige esas cookies: sin ellas responde HTTP
403 con `{"error":"forbidden"}`.

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
  Se muestran todos los episodios (75 en el ejemplo), no solo los primeros 30.
- Relacionadas: bajo el `h2` "También te puede gustar" hay tarjetas
  `article` con `a[href*="/series/"]`, `img[alt]` y `aria-label`. Se excluye la
  propia ficha.
- `bookId`: no está en un atributo visible; vive en el payload RSC embebido en
  scripts inline y con barras invertidas de escape. La técnica fiable es
  eliminar todas las barras invertidas del HTML
  (`html.replace("\\", "")`) y buscar
  `bookId"\s*:\s*"(\d+)"\s*,\s*"slug"\s*:\s*"<slug>"`. En el ejemplo:
  `bookId":"15531","slug":"la-novia-sustituta-..."`. En la ficha aparecen
  `bookId` de otras series (relacionadas), así que hay que emparejarlo con el
  slug de la ficha actual.
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
- En los listados se prefiere el JSON-LD `CollectionPage`; las tarjetas se
  usan como respaldo y para búsqueda y portada.

## 8. API de fuentes de video y cifrado

`GET https://es.hdrama.net/api/episode-source/<bookId>/<serial>?lang=es&refresh=1`

Respuesta verificada (fixture):

```json
{"descriptor":{"source":"FlickReels","chain":[
 {"enc":"mqDHwriF...","type":"hls","source":"FlickReels","refresh":false,"refreshAfter":null},
 {"enc":"ueB4Z517...","type":"hls","source":"FlickReels","refresh":true,"refreshAfter":1794444495}
],"subtitle":null}}
```

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

| `enc` (prefijo) | URL descifrada |
|---|---|
| `mqDHwriF...` | `https://pl.shortdizilab.com/v1/flickreels/23935678.m3u8` |
| `ueB4Z517...` | `https://zshipricf.farsunpteltd.net/playlet-hls/1793611042_hls_47068.m3u8?verify=1794445095-7GTUC2QIzP8gJ3gRToR9ftplZqiamm4jAChNpAuY0uI%3D` |

`refresh` y `refreshAfter` indican que el token caduca: hay que resolver la
fuente en el momento de reproducir y no reutilizarla más de unos minutos.
`subtitle` fue `null` en la muestra; el cliente lo ignora de forma defensiva
(puede venir `null`, objeto o lista) y no emite subtítulos desde esta API.

## 9. Reproducción de las URLs obtenidas

Comprobaciones con peticiones desde el host:

- URL directa (`zshipricf...m3u8?verify=...`): **200 sin cabecera
  `Referer`**; **403 si se envía `Referer: https://es.hdrama.net/`**. Se emite
  sin headers.
- Proxy espejo `https://cdn.dramadizilerim.xyz/https/<URL directa>`: **200 con
  `Referer: https://es.hdrama.net/`**; **451 sin `Referer`**. Se ofrece como
  segunda opción.
- Las entradas `pl.shortdizilab.com/v1/flickreels/<id>.m3u8` devolvieron 404 en
  la comprobación: se emiten igualmente (el player prueba la cadena) pero no
  bloquean si fallan.
- No hay DRM en el sitio: solo HLS/MP4 normales. `MediaSource` no modela DRM.

## 10. Estado de implementación (versión 2)

| Módulo | Estado | Implementación |
|---|---|---|
| Catálogo real por secciones | Implementado | `HdramaCatalogAdapter` + `HdramaHtmlParser`, JSON-LD y DOM de portada/categorías |
| Búsqueda real | Implementado | `/search?q=` con tarjetas `article` |
| Ficha de serie completa | Implementado | JSON-LD `TVSeries`, DOM, nota y `bookId` del payload RSC |
| Lista de episodios | Implementado | `data-serial`/`data-display`, duraciones y `externalId=bookId:serial` |
| Fuentes de video | Implementado | API `/api/episode-source`, `HdramaSourceCipher` AES-256-GCM |
| Reproducción HLS | Implementado | HLS directo sin headers y proxy espejo con `Referer` |
| Sesión Cloudflare | Implementado | `WebViewFetcher` (WebView del sistema; `CloudstreamWebFetcher` como respaldo) |
| Respaldo sin sesión | Implementado | Sitemaps públicos, fichas de navegación e importación manual |
| Catálogo demo | Implementado | 17 series sintéticas y MP4/HLS públicos en "Acceso a HDrama" |

## 11. Evidencia y límites de esta investigación

- Sesión Chromium real (Playwright) que superó el Cloudflare Managed
  Challenge el 2026-11-11; los HTML completos están en `tmp/raw/` (no
  versionados) y las versiones recortadas en
  `HDrama/src/test/resources/fixtures/`.
- El sitio es un objetivo en movimiento: rutas, selectores, claves de
  cifrado y hosts de video pueden cambiar sin aviso. La capa de respaldo
  (sitemaps, navegación y demo) cubre esos fallos, y las limitaciones
  conocidas están en [`08-limitaciones.md`](08-limitaciones.md).
- La nota y los metadatos dependen de la estructura del HTML; el parser es
  tolerante y deja campos opcionales en `null` cuando no los encuentra.

## 12. Referencias

- https://es.hdrama.net/ (sitio objetivo)
- https://es.hdrama.net/robots.txt
- https://es.hdrama.net/sitemap.xml
- https://developers.cloudflare.com/cloudflare-challenges/ (Managed Challenge)
- https://www.rfc-editor.org/rfc/rfc9309 (Robots Exclusion Protocol)
