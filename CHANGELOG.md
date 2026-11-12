# Changelog

Todas las fechas en ISO 8601. Formato basado en
[Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/).

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

[v1.1.0]: https://github.com/GeorgesGil/HDramaCloudStream/compare/v1.0.0...v1.1.0
[v1.0.0]: https://github.com/GeorgesGil/HDramaCloudStream/releases/tag/v1.0.0
