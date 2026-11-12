# Seguimiento del proyecto

## Datos generales

| Campo | Valor |
|---|---|
| Proyecto | HDramaCloudStream |
| Proveedor | HDrama |
| Sitio objetivo | https://es.hdrama.net/ |
| Repositorio | https://github.com/GeorgesGil/HDramaCloudStream |
| Visibilidad | Público desde la v1.1.0 |
| Versión del plugin | 2 |
| Release de referencia | v1.1.0 |
| Rama principal | `main` |
| Rama de artefactos | `builds` (publicada por el workflow) |
| GitHub Actions | Habilitadas: workflow Build plugin (pruebas, .cs3, rama builds y adjunto en releases) |
| Fecha de inicio | 2026-11-11 |
| Zona horaria | America/Santo_Domingo |

## Estado por fase

| Fase | Estado | Evidencia |
|---|---|---|
| 1. Investigación | Completado | [`02-investigacion-hdrama.md`](02-investigacion-hdrama.md): rutas, JSON-LD, DOM, API y cifrado verificados con navegador real |
| 2. Diseño | Completado | [`03-arquitectura.md`](03-arquitectura.md): capas, rutas, cachés y flujos v2 |
| 3. Implementación | Completado | Código en `HDrama/`: parser, red, fuentes, catálogo y provider v2 |
| 4. Pruebas | Completado | Suites JVM de parser, cifrado, red, catálogo y player; 98 pruebas unitarias en 16 suites |
| 5. Corrección | Completado | Correcciones de v1 (URL de episodio, URL demo, MP4 403) y ajustes de la v2 |
| 6. Entrega v1.0.0 | Completado | `.cs3` v1 en la rama `builds` y release v1.0.0 |
| 7. Integración HTML real (v2) | Completado | Catálogo, búsqueda, fichas, episodios y HLS reales con respaldos |
| 8. Entrega v1.1.0 | Completado | `.cs3` v2 (123.299 bytes, SHA-256 `7fbc…94e4`), release v1.1.0 con artefacto adjunto y rama `builds` publicada por CI |

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
  - reproducción HLS directa sin `Referer` y proxy espejo con `Referer`.
- Se comprobó que `CloudflareKiller` de CloudStream resuelve el challenge con
  el WebView de la app y reutiliza las cookies; una instancia por fetcher.

### 2026-11-11 — Fase 7: implementación v2

- Se añadieron los paquetes `parser`, `network` y `sources`:
  - `HdramaHtmlParser` con JSON-LD, tarjetas, fichas, episodios y
    relacionadas; `ParserContract` con selectores reales.
  - `WebFetcher`/`WebViewFetcher` con WebView del sistema, `CloudstreamWebFetcher` como respaldo, allowlist y
    mensajes en español.
  - `HdramaSourceCipher` (Base64 propio + AES-256-GCM) y
    `HdramaSourceClient` (chain, HLS directo y espejo con `Referer`).
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

## Decisiones de implementación

1. El HTML real de HDrama es la fuente principal del catálogo, la búsqueda, las
   fichas y los episodios.
2. Los sitemaps públicos son el respaldo cuando la sesión de Cloudflare o la
   red fallan; las fichas se convierten entonces en navegación al sitio.
3. El catálogo demo queda como diagnóstico del reproductor y último recurso.
4. La sesión de Cloudflare la resuelve un WebView del sistema (`WebViewFetcher`),
   con una instancia por fetcher.
5. Las fuentes de video se resuelven en el momento de reproducir y se
   descifran con AES-256-GCM; se emite la URL directa y un espejo con
   `Referer`.
6. Las ocho secciones se alinean con las del sitio; "Nuevos" no se duplica
   porque la portada lo muestra como subconjunto de Novedades.
7. El repositorio es público y GitHub Actions está habilitado; el workflow
   `Build plugin` ejecuta pruebas, compila el `.cs3`, actualiza la rama
   `builds` y adjunta el artefacto en cada release. La instalación local
   (`.cs3`/ADB) sigue disponible como alternativa.

## Próximos pasos

- Instalar desde `repo.json` público en un dispositivo limpio.
- Confirmar la reproducción HLS en hardware físico: el emulador AOSP usado no
  completa el challenge de la API en todos los casos.
- Verificar en Galaxy Z Fold8 (plegado/desplegado, rotación, 9:16) y en redes
  móviles.
- Mantener el parser y la clave de cifrado al día si el sitio cambia.
