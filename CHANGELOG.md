# Changelog

Todas las fechas en ISO 8601.

## [1.0.0] - 2026-11-11

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
- Workflow de GitHub Actions y `repo.json` (Actions deshabilitado; no ejecutado).

### Limitado / pendiente

- El HTML de HDrama devuelve HTTP 403 con Cloudflare Managed Challenge; el
  adaptador de fichas y episodios reales todavía no está implementado.
- El repositorio es privado; no sirve como repositorio instalable por URL.
- HLS no se confirmó en el player; sí la emisión del enlace.
- Sin prueba en Galaxy Z Fold8 físico.

### Notas

- `apiVersion = 1`; CloudStream 4.9.1 / pre-release 2026-11-09.
- JDK 21.0.13, Gradle 8.12, AGP 8.7.3, Kotlin 2.3.0, compileSdk 35.
