# Seguimiento del proyecto

## Datos generales

| Campo | Valor |
|---|---|
| Proyecto | HDramaCloudStream |
| Proveedor | HDrama |
| Sitio objetivo | https://es.hdrama.net/ |
| Repositorio | https://github.com/GeorgesGil/HDramaCloudStream (privado) |
| Acciones de GitHub | **Deshabilitadas** por decisión del propietario |
| Rama principal | `main` |
| Estrategia | Una rama por fase + Pull Request + merge a `main` |
| Fecha de inicio | 2026-11-11 |
| Zona horaria | America/Santo_Domingo |

## Estado por fase

| Fase | Estado | Evidencia |
|---|---|---|
| 1. Investigación | Completado | `Docs/01-investigacion-cloudstream.md`, `Docs/02-investigacion-hdrama.md`; PR #1 |
| 2. Diseño | Completado | `Docs/03-arquitectura.md`; PR #2 |
| 3. Implementación | Completado | código en `HDrama/`; rama `phase/03-implementation` |
| 4. Pruebas | Completado | 61 pruebas, 0 fallos; `Docs/05-pruebas.md` |
| 5. Corrección | Completado | 4 correcciones verificadas (episode URL, demo URL, video 403, tests) |
| 6. Entrega | En curso | `.cs3`, `Docs/`, rama `builds`, informe final |

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
  - La home y las páginas HTML responden **HTTP 403** con
    `Cf-Mitigated: challenge` (Cloudflare Managed Challenge).
  - Los sitemaps (`/sitemaps/new-es`, etc.) responden HTTP 200 y contienen
    URLs `/series/<slug>` y `lastmod`; no contienen títulos, portadas ni
    episodios.
- Conclusión: no se implementa ningún bypass. La integración con HTML
  protegido queda deshabilitada; el catálogo real se obtiene de los sitemaps
  públicos.

### 2026-11-11 — Fase 2: diseño

- Se definió la arquitectura por capas: modelos puros, adaptadores de catálogo,
  parser, red segura, resolución de enlaces y mapeo a CloudStream.
- Se fijó el principio de **adaptador HDrama protegido deshabilitado** mediante
  `AuthorizationPolicy` (bandera `HDRAMA_AUTHORIZED=false`).
- Se definió el catálogo de demostración local con secciones solicitadas,
  paginación, búsqueda normalizada y estados de reproducción.
- Se documentaron los flujos de pantalla principal, búsqueda, ficha,
  reproducción, navegador y plegables.
- Se definió el plan de 13 pruebas y los riesgos.

### 2026-11-11 — Fase 3: implementación

- Se creó el proyecto Gradle con wrapper 8.12, AGP 8.7.3, Kotlin 2.3.0,
  `compileSdk 35`, `minSdk 21`, JVM target 1.8.
- Se implementaron modelos de dominio, utilidades Unicode/URL, caché TTL,
  allowlist de red, reintentos, parser jsoup, catálogo demo, catálogo real de
  sitemaps, resolución de enlaces y el proveedor de CloudStream.
- Se añadieron 61 pruebas unitarias y una prueba de integración opcional de
  sitemaps.
- Se compiló `HDrama.cs3` (83.106 bytes) y se generó `plugins.json`.
- Se instaló CloudStream 4.9.1 en un emulador Android 15 y se verificó:
  - carga del plugin y registro del proveedor;
  - catálogo real con títulos del sitemap;
  - ficha real con botón de navegador (abre Chrome);
  - demo con 2 episodios;
  - reproducción MP4 1080p (`Rendered first frame`) con letterboxing.
- Correcciones relevantes:
  - `newEpisode` debía usar `fix = false` para conservar el esquema interno
    del episodio;
  - CloudStream antepone `mainUrl` a URLs de ficha con esquema propio; se
    añadió `recoverDemoSeriesUrl`;
  - el MP4 original (Google sample bucket) devolvía 403; se cambió a
    `media.w3.org/2010/05/sintel/trailer.mp4`;
  - se ajustaron pruebas de importación manual y de la sección de acceso.

### Declaración de autorización del propietario

El 2026-11-11 el propietario declaró tener autorización de HDrama. No se
aportó documento, credencial, API ni endpoint autorizado al repositorio. Esa
declaración **no se usa para eludir Cloudflare** ni otros controles. El
catálogo real se limita al sitemap público y la navegación al sitio.

## Decisiones registradas

1. No usar técnicas de evasión de Cloudflare, CAPTCHA, DRM, autenticación ni
   otros controles, aunque exista una autorización declarada.
2. No usar `/api/` (prohibido por `robots.txt`) sin autorización y sin
   documentación del endpoint.
3. Implementar el catálogo real con los sitemaps públicos, que están
   declarados en `robots.txt` y no requieren superar el challenge.
4. Mantener el adaptador de HTML protegido detrás de una bandera desactivada.
5. Ofrecer navegación al sitio original e importación manual de enlaces.
6. Mantener GitHub Actions deshabilitadas; la compilación y publicación se
   hace localmente y se sube a una rama `builds`.

## Próximos pasos

- Cerrar Fase 3 con PR y merge a `main`.
- Publicar el `.cs3` y `plugins.json` en la rama `builds`.
- Verificar el estado final del repositorio y el informe de entrega.
