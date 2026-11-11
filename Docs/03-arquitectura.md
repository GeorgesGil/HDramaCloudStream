# Arquitectura (Fase 2)

Fecha: 2026-11-11. Este documento define la estructura antes de implementar.
Cualquier cambio posterior debe reflejarse aquí y en `CHANGELOG.md`.

## 1. Restricciones de diseño

1. **No evadir controles de acceso.** El HTML de HDrama está detrás de un
   Cloudflare Managed Challenge (HTTP 403). No se implementa ningún mecanismo
   de resolución, camuflaje, proxy o cookie.
2. **No usar `/api/`** de HDrama sin autorización (prohibido por `robots.txt`).
3. **No inventar endpoints ni selectores.** El adaptador HTML queda
   deshabilitado y solo se prueba contra fixtures sintéticas que documentan el
   contrato esperado; no se afirma que correspondan al HTML real.
4. **Funcionalidad verificable.** El catálogo de demostración local permite
   probar todos los flujos de CloudStream sin tocar HDrama.
5. **API vigente.** Se usan las funciones factoría actuales
   (`newHomePageResponse`, `newSearchResponseList`, `newTvSeriesLoadResponse`,
   `newEpisode`, `newExtractorLink`), no constructores deprecados.
6. **Compatibilidad.** `minSdk 21`, `compileSdk 35`, JVM target 1.8,
   `apiVersion = 1`.

## 2. Estructura del repositorio

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
├── .github/workflows/build.yml      # presente, Actions deshabilitadas
├── Docs/
│   ├── 00-seguimiento.md
│   ├── 01-investigacion-cloudstream.md
│   ├── 02-investigacion-hdrama.md
│   ├── 03-arquitectura.md
│   ├── 04-compilacion.md
│   ├── 05-pruebas.md
│   ├── 06-instalacion.md
│   ├── 07-privacidad.md
│   └── 08-limitaciones.md
└── HDrama/
    ├── build.gradle.kts
    └── src/
        ├── main/
        │   ├── AndroidManifest.xml
        │   └── kotlin/com/hdrama/
        │       ├── HDramaPlugin.kt
        │       ├── HDramaProvider.kt
        │       ├── catalog/
        │       │   ├── CatalogAdapter.kt
        │       │   ├── DemoCatalogAdapter.kt
        │       │   ├── HdramaCatalogAdapter.kt
        │       │   └── CatalogRepository.kt
        │       ├── parser/
        │       │   ├── HdramaHtmlParser.kt
        │       │   └── ParserContract.kt
        │       ├── models/
        │       │   ├── Series.kt
        │       │   ├── Episode.kt
        │       │   └── MediaSource.kt
        │       ├── player/
        │       │   ├── LinkResolver.kt
        │       │   └── PlaybackStatus.kt
        │       ├── network/
        │       │   ├── HttpTransport.kt
        │       │   └── RetryPolicy.kt
        │       └── utils/
        │           ├── TextNormalizer.kt
        │           ├── UrlUtils.kt
        │           ├── Diagnostics.kt
        │           └── AuthorizationPolicy.kt
        └── test/
            ├── fixtures/
            │   ├── demo-catalog.json
            │   ├── hdrama-series.html
            │   └── hdrama-episodes.html
            └── kotlin/com/hdrama/
                ├── ...
```

## 3. Capas

```text
CloudStream
   │  MainAPI (HDramaProvider)
   ▼
CatalogRepository ── AuthorizationPolicy
   │
   ├── DemoCatalogAdapter      (activo, datos locales)
   └── HdramaCatalogAdapter    (deshabilitado sin autorización)
          │
          ├── HdramaHtmlParser (contrato + fixtures)
          └── HttpTransport    (timeouts/retry/allowlist)
   │
   ▼
LinkResolver ── MediaSource (Mp4/Hls/Unavailable/BrowserOnly/Unauthorized)
```

### `models`

- `Series`: `id`, `slug`, `title`, `originalTitle?`, `synopsis?`, `genres`,
  `rating?` (0..10), `language`, `source`, `posterUrl?`, `episodeCount?`,
  `year?`, `related`.
- `Episode`: `id`, `seriesId`, `number`, `title?`, `durationSeconds?`,
  `pageUrl?`, `media: MediaSource`.
- `MediaSource` (sealed):
  - `Mp4(url, quality, headers)`
  - `Hls(url, quality, headers)`
  - `Unavailable(reason)`
  - `BrowserOnly(pageUrl, reason)`
  - `Unauthorized(reason)`
- `CatalogPage<T>`: `items`, `page`, `hasNext`.

### `catalog`

- `CatalogAdapter`:
  - `suspend fun section(section: CatalogSection, page: Int): CatalogPage<Series>`
  - `suspend fun search(query: String, page: Int): CatalogPage<Series>`
  - `suspend fun load(idOrUrl: String): Series?`
  - `suspend fun episodes(series: Series): List<Episode>`
  - `suspend fun resolve(episode: Episode): List<MediaSource>`
- `DemoCatalogAdapter`: lee `demo-catalog.json` empaquetado como recurso de
  test y, en runtime, una copia embebida en código para no depender de I/O de
  recursos Android. Datos sintéticos; videos de dominio público.
- `HdramaCatalogAdapter`: comprueba `AuthorizationPolicy`. Sin autorización
  devuelve `CatalogAccessDenied`/`Unauthorized`, sin red. Con autorización
  (futura) usaría `HdramaHtmlParser` y `HttpTransport`; hoy no se ejecuta.
- `CatalogRepository`: selecciona el adaptador según el modo, aplica caché en
  memoria con TTL, deduplica y normaliza.

### `parser`

`HdramaHtmlParser` usa jsoup (no regex frágiles) y un `ParserContract` con
selectores explícitos:

```kotlin
data class ParserContract(
    val seriesItem: String,
    val title: String,
    val poster: String,
    val synopsis: String,
    val genres: String,
    val rating: String,
    val episodeItem: String,
    val episodeNumber: String,
    val episodeTitle: String,
    val episodeUrl: String,
    val videoSource: String,
)
```

El contrato se valida contra fixtures locales. No se asegura que coincida con
el HTML real de HDrama; queda pendiente de autorización y verificación.

### `network`

- `HttpTransport`: interfaz mínima (`get(url, headers)`), inyectable en tests.
- `SafeHttpClient`: implementación sobre OkHttp/NiceHttp de CloudStream con:
  - timeouts de conexión/lectura,
  - reintentos limitados con backoff,
  - allowlist de dominios (`es.hdrama.net` y subdominios permitidos),
  - validación de esquema `https`,
  - cancelación por corrutinas,
  - logging sin cookies/tokens/query sensibles.
- Sin autorización, el adaptador HDrama **no instancia** este cliente.

### `player`

- `LinkResolver`:
  - convierte `MediaSource` en `ExtractorLink`,
  - deduplica por URL+calidad,
  - ordena calidades de mayor a menor,
  - clasifica estados (`PlaybackStatus`).
- `PlaybackStatus`: `COMPATIBLE`, `TEMPORARILY_UNAVAILABLE`,
  `UNSUPPORTED_SOURCE`, `BROWSER_REQUIRED`, `UNAUTHORIZED`. Cada uno tiene un
  mensaje en español accionable.
- Alternativa de navegador:
  - `LoadResponse.url` apunta a la ficha original para el botón nativo
    "Abrir en navegador".
  - Para episodios `BrowserOnly` se emite un `ExtractorLink` etiquetado
    "Abrir en navegador (HDrama)" que el usuario puede abrir con la acción
    nativa "Play in browser".

### `utils`

- `TextNormalizer`: minúsculas, eliminación de diacríticos, colapso de
  espacios,normalización Unicode NFKD. Se usa en búsqueda y orden.
- `UrlUtils`: normalización de esquema/host/path, resolución relativa,
  validación de dominio, extracción de slug.
- `Diagnostics`: mensajes de error seguros y redacción de datos sensibles.
- `AuthorizationPolicy`: estado `NOT_GRANTED` por defecto, basado en
  `BuildConfig.HDRAMA_AUTHORIZED` (false). Documenta cómo cambiarlo solo con
  autorización escrita.

## 4. Flujos

### Pantalla principal

1. CloudStream pide `getMainPage(page, request)`.
2. El provider mapea `request.data` a `CatalogSection`.
3. `CatalogRepository` consulta el adaptador activo (demo por defecto).
4. Se aplican deduplicación, límite de página y `hasNext`.
5. Se mapea a `SearchResponse` con `newTvSeriesSearchResponse`.

Secciones: Novedades, Más vistos, Nuevos esta semana, Catálogo general,
Géneros, Recomendaciones.

### Búsqueda

1. `search(query, page)` normaliza el texto.
2. Coincidencia parcial por título, título original, géneros y slug.
3. Resultados paginados; sin distinción de mayúsculas ni acentos.
4. Si el texto es una URL de HDrama, se ofrece importación manual.

### Ficha

1. `load(url)` acepta IDs internos (`hdrama-demo://series/<id>`), URLs de
   HDrama (`https://es.hdrama.net/series/<slug>`) o slugs.
2. Para demo: metadatos completos y episodios ordenados numéricamente.
3. Para URL de HDrama sin autorización: ficha mínima con título derivado del
   slug, aviso de autorización y URL original para navegador. No hay red.
4. Series relacionadas si el adaptador las aporta.

### Reproducción

1. `loadLinks(data, ...)`: `data` es JSON con el ID de episodio.
2. `CatalogRepository.resolve` devuelve `MediaSource`.
3. `LinkResolver` emite `ExtractorLink` para MP4/HLS.
4. Estados no compatibles lanzan `ErrorLoadingException` con el mensaje
   específico; `BrowserOnly` emite enlace de navegador.

## 5. Pantallas plegables y 9:16

El plugin no implementa UI ni player propios. CloudStream/ExoPlayer gestionan
rotación, multi-window y relación de aspecto. El plugin:

- entrega URLs MP4/HLS con proporciones originales;
- no fuerza orientación ni modifica el manifiesto de la app;
- no recorta ni estira; la UI nativa decide;
- mantiene el estado de reproducción en CloudStream.

La verificación en Galaxy Z Fold8 queda pendiente de dispositivo físico. Se
probará en emulador y se documentará la limitación.

## 6. Caché y rendimiento

- Caché en memoria por proceso, TTL 5 minutos, tamaño máximo 64 entradas.
- Sin caché en disco (evita datos sensibles y crecimiento).
- Páginas de 12 elementos; no se descarga el catálogo completo.
- Búsqueda local O(n) sobre el catálogo demo (pequeño).
- Reintentos máximos 2 con backoff 250/500 ms; timeout 10 s.
- Deduplicación por `id`/URL antes de mapear a CloudStream.

## 7. Pruebas previstas

| # | Prueba | Tipo |
|---|---|---|
| 1 | Compilación y carga del plugin (anotación, clase, manifest) | unit/integration |
| 2 | Registro del proveedor y metadatos (`name`, `lang`, tipos) | unit |
| 3 | Extracción de metadatos desde fixture HTML | unit |
| 4 | Normalización de URLs y validación de dominio | unit |
| 5 | Campos ausentes en fixture | unit |
| 6 | Orden numérico de episodios (1, 2, 10) | unit |
| 7 | Detección de enlaces duplicados | unit |
| 8 | Paginación y `hasNext` | unit |
| 9 | Unicode, acentos y títulos en español | unit |
| 10 | Errores de red, timeouts y reintentos | unit (transporte falso) |
| 11 | Reproductores no compatibles / estados | unit |
| 12 | Compatibilidad de versión (`apiVersion`, `version`) | unit |
| 13 | `.cs3` contiene `manifest.json` y `classes.dex` | script de build |

## 8. Riesgos

| Riesgo | Impacto | Mitigación |
|---|---|---|
| Cloudflare bloquea HTML | Integración real imposible | Adaptador deshabilitado; navegador/demo |
| Sin autorización de HDrama | No publicable como integración real | Bandera explícita y docs |
| JitPack/Gradle plugin no resuelve | No compila | Probar early; fallback a stubs locales |
| Repo privado no sirve repo.json | Instalación por URL no funciona | `.cs3` local/ADB; documentar |
| Actions deshabilitadas | Sin CI | Build local reproducible + workflow listo |
| Plugin API cambia | Ruptura futura | Fijar versiones y pruebas de compatibilidad |
