# Pruebas (Fase 4/5)

Fecha: 2026-11-11.

## Resumen

| Métrica | Resultado |
|---|---|
| Suite | `:HDrama:testDebugUnitTest` |
| Total de pruebas | **61** |
| Fallos | **0** |
| Errores | **0** |
| Resultado | `BUILD SUCCESSFUL` |

La cifra se obtuvo sumando `testsuite.tests` de los XML generados en
`HDrama/build/test-results/testDebugUnitTest/`.

## Cobertura funcional

### 1. Compilación y carga del plugin

- `:HDrama:make` genera `HDrama.cs3` con `manifest.json` y `classes.dex`.
- El manifest apunta a `com.hdrama.HDramaPlugin` y versión `1`.
- Verificado en emulador Android 15 (API 35) con CloudStream 4.9.1:
  - `PluginManager: Loaded plugin HDrama.cs3 successfully`
  - `PluginInstance: Adding HDrama (https://es.hdrama.net/) MainAPI`
  - El proveedor HDrama aparece en el selector de proveedores.

### 2. Registro y metadatos

- `PluginInfoTest` comprueba nombre, versión, `apiVersion`, clase y URL.
- El proveedor declara `lang = "es"`, `TvType.TvSeries` y `TvType.AsianDrama`.

### 3. Metadatos con fixtures locales

- `HdramaHtmlParserTest` usa `hdrama-series.html`, `hdrama-detail.html` y
  `hdrama-episodes.html`.
- Comprueba título, título original, sinopsis, géneros, valoración, año,
  portada, número de episodios y URL canónica.
- Los selectores son un contrato **no validado** contra el HTML real; ver
  `Docs/08-limitaciones.md`.

### 4. Normalización de URLs

- `UrlUtilsTest`: esquema, host, `www`, barras finales, consultas, rechazo de
  dominios ajenos, `ftp`/`javascript`, extracción de slug, redacción de logs.
- Recuperación de URLs demo cuando CloudStream antepone `mainUrl`.

### 5. Campos ausentes

- Artículo sin portada, sin sinopsis, sin rating o sin título.
- Duplicados descartados por `id`.
- Valoración con coma decimal (`7,9`) convertida a `7.9`.

### 6. Orden numérico de episodios

- Fixture con episodios 10, 2, 1 y un duplicado.
- Resultado: `[1, 2, 10]`, sin duplicados.
- `EpisodeOrdering` y `EpisodeId` probados por separado.

### 7. Enlaces duplicados

- `LinkResolverTest` verifica deduplicación por `tipo|URL|calidad`.
- Verifica orden por calidad descendente (1080 > 720 > 480).

### 8. Paginación

- `DemoCatalogAdapter` con páginas de 12 y de 2 elementos.
- `SitemapCatalogAdapter` con páginas de 1 elemento y `hasNext`.
- Se comprueba que no se repiten elementos entre páginas.

### 9. Unicode y español

- `TextNormalizer`: `Corazón` == `corazon`, `Señor` == `senor`,
  `Jardín` == `jardin`, mayúsculas y espacios múltiples.
- Títulos con acentos en fixtures y catálogo demo.

### 10. Errores de red

- `SafeHttpClientTest` con transporte falso:
  - fallo, fallo, éxito -> 3 intentos y backoff `[250, 500]` ms.
  - fallo persistente -> `HttpOutcome.Failure` con 3 intentos.
  - dominio no permitido -> `Blocked` sin abrir conexión.

### 11. Reproductores no compatibles

- `LinkResolution.Failure` para fuente no disponible, no autorizada o URL
  inválida.
- `PlaybackStatus` diferencia `COMPATIBLE`, `TEMPORARILY_UNAVAILABLE`,
  `UNSUPPORTED_SOURCE`, `BROWSER_REQUIRED`, `UNAUTHORIZED`.

### 12. Compatibilidad de versión

- `PluginInfoTest` fija `VERSION=1` y `API_VERSION=1`.
- El `.cs3` se inspeccionó manualmente para confirmar `manifest.json`.

### 13. Autorización

- `HdramaCatalogAdapterTest` confirma que el catálogo/búsqueda protegidos
  lanzan `UnauthorizedAccessException` y que la importación manual no descarga
  HTML.
- La bandera `HDRAMA_AUTHORIZED` permanece en `false`.

### 14. Sitemaps reales

- `SitemapParserTest` con XML de índice y `urlset`.
- `SitemapCatalogAdapterTest` con una fuente falsa: secciones, búsqueda,
  paginación, ficha navegable y deduplicación.
- `SitemapLiveTest` (opcional, `HDRAMA_INTEGRATION=1`) descargó los sitemaps
  reales y confirmó que `new-es`, `trending-es` y `fresh-es` no están vacíos.
  **Ejecutada y superada** el 2026-11-11.

## Verificación manual en emulador

Dispositivo: emulador x86_64, Android 15 (API 35), CloudStream 4.9.1.

| Paso | Resultado |
|---|---|
| Instalar CloudStream | OK |
| Copiar `HDrama.cs3` a `/sdcard/Cloudstream3/plugins/` | OK |
| Cargar plugin al iniciar | OK |
| Proveedor HDrama en el selector | OK |
| Catálogo real (Novedades/Más vistos) | OK, títulos reales del sitemap |
| Ficha de serie real | OK, metadatos mínimos + navegador |
| Botón de navegador | Abre Chrome con la URL de HDrama |
| Tarjeta demo "Prueba de reproducción" | OK, 2 episodios |
| Episodio MP4 1080p | OK, `Rendered first frame` |
| Relación de aspecto | Letterboxing correcto, sin estiramiento |
| Episodio HLS | Implementado y probado a nivel de resolución; no se confirmó reproducción en player por tiempo de sesión |

Capturas en [`Docs/images/`](images/):

- `01-catalogo-real-sitemap.png`
- `02-ficha-real-navegador.png`
- `03-ficha-demo-episodios.png`
- `04-reproduccion-mp4.png`
- `05-ficha-real-boton-navegador.png`

## Pruebas no ejecutadas

- Galaxy Z Fold8 físico / Android 17 / One UI 9.
- Reproducción HLS en ExoPlayer (solo se validó la emisión del enlace y el
  parser).
- Pruebas instrumentadas (Espresso/UIAutomator) automatizadas.
- Pruebas contra HTML real de HDrama (bloqueado por Cloudflare).
- DRM, autenticación o contenido protegido: fuera de alcance.
