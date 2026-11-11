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
| 1. Investigación | En curso | `Docs/01-investigacion-cloudstream.md`, `Docs/02-investigacion-hdrama.md` |
| 2. Diseño | Pendiente | — |
| 3. Implementación | Pendiente | — |
| 4. Pruebas | Pendiente | — |
| 5. Corrección | Pendiente | — |
| 6. Entrega | Pendiente | — |

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
- Conclusión: no existe acceso automatizado legítimo y verificable al HTML
  de HDrama sin superar un control de acceso. **No se implementará ningún
  bypass.** La integración directa quedará deshabilitada hasta autorización.

## Decisiones registradas

1. No usar técnicas de evasión de Cloudflare ni de otros controles.
2. No usar `/api/` (prohibido por `robots.txt`) sin autorización.
3. Implementar el catálogo solicitable con un proveedor de demostración local,
   claramente etiquetado, y dejar el adaptador HDrama detrás de una bandera de
   autorización desactivada por defecto.
4. Ofrecer navegación al sitio original e importación manual de enlaces como
   alternativas permitidas.
5. Mantener GitHub Actions deshabilitadas; la compilación y publicación de
   artefactos se hará localmente y se subirá a una rama `builds`.

## Próximos pasos

- Cerrar Fase 1 con PR.
- Diseñar arquitectura (Fase 2).
- Crear esqueleto Gradle y verificar que la cadena de compilación resuelve
  dependencias con JDK 21.
- Implementar, probar y compilar.
