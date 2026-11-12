# HDramaCloudStream

Extensión de [CloudStream](https://github.com/recloudstream/cloudstream) para
[es.hdrama.net](https://es.hdrama.net/), escrita en Kotlin.

**Estado:** beta funcional, compilada y probada en emulador Android 15 con
CloudStream 4.9.1. El catálogo real se construye desde los **sitemaps públicos**
de HDrama. Las fichas completas, portadas y episodios del sitio no están
disponibles porque su HTML responde con un Cloudflare Managed Challenge.

## Qué funciona

| Función | Estado |
|---|---|
| Plugin `.cs3` y registro del proveedor HDrama | ✅ verificado en emulador |
| Catálogo real: Novedades, Más vistos, Nuevos, Catálogo general, Búsqueda | ✅ desde sitemaps públicos |
| Ficha real con título y botón "Abrir en navegador" | ✅ verificado |
| Importación manual de URL de serie | ✅ sin descargar HTML |
| Catálogo demo local con paginación y búsqueda | ✅ |
| Reproducción MP4 1080p en el player de CloudStream | ✅ `Rendered first frame` |
| Emisión de HLS (`ExtractorLinkType.M3U8`) | ✅ implementada y probada a nivel de resolución |
| Estados de error diferenciados | ✅ |
| Subtítulos | ✅ soportados si la fuente los aporta; sin fuente real disponible |
| Favoritos, historial, continuar viendo, calidad, pantalla completa | ✅ nativos de CloudStream |
| 61 pruebas unitarias | ✅ 0 fallos |

## Capturas

| Catálogo real | Ficha real |
|---|---|
| ![Catálogo](Docs/images/01-catalogo-real-sitemap.png) | ![Ficha](Docs/images/02-ficha-real-navegador.png) |

| Demo con episodios | Reproducción MP4 |
|---|---|
| ![Demo](Docs/images/03-ficha-demo-episodios.png) | ![Player](Docs/images/04-reproduccion-mp4.png) |

## Qué no está disponible

- Portadas, sinopsis, géneros, valoración, duración y lista de episodios de
  HDrama: el HTML está detrás de Cloudflare (HTTP 403 + `Cf-Mitigated:
  challenge`).
- Enlaces de video, iframes, tokens o DRM de HDrama.
- Repositorio instalable por URL: el repositorio es **privado** y GitHub
  Actions está **deshabilitado**. La instalación es local (`.cs3`/ADB).

Ver [`Docs/08-limitaciones.md`](Docs/08-limitaciones.md).

## Compilación

Requisitos: Windows/macOS/Linux, JDK 17 o 21, Android SDK 35.

```powershell
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-21.0.13.9-hotspot"
.\gradlew.bat :HDrama:testDebugUnitTest
.\gradlew.bat :HDrama:make
.\gradlew.bat makePluginsJson
```

Artefacto: `HDrama/build/HDrama.cs3` (83 KB).
SHA-256: `49d93e3bc33b6ccd3a378ae5986e07ef6e1193ab561769256fa8f1f48097cfb6`.

## Instalación rápida

```powershell
adb install -r 4.9.1.apk
adb shell mkdir -p /sdcard/Cloudstream3/plugins
adb push HDrama.cs3 /sdcard/Cloudstream3/plugins/HDrama.cs3
adb shell appops set com.lagradost.cloudstream3 MANAGE_EXTERNAL_STORAGE allow
adb shell am force-stop com.lagradost.cloudstream3
```

Abrir CloudStream, pulsar el filtro de proveedores y elegir **HDrama**.
Procedimiento completo en [`Docs/06-instalacion.md`](Docs/06-instalacion.md).

## Arquitectura

```text
HDramaProvider (CloudStream MainAPI)
  └── CatalogRepository
       ├── SitemapCatalogAdapter   (catálogo real, sitemaps públicos)
       ├── DemoCatalogAdapter      (datos sintéticos + videos públicos)
       └── HdramaCatalogAdapter    (módulo HTML pendiente)
  └── LinkResolver → ExtractorLink
```

- `models/`: dominio puro, sin tipos de CloudStream.
- `parser/`: jsoup + contrato de selectores (fixtures locales; no validado
  contra HTML real).
- `network/`: allowlist, timeouts, reintentos, caché TTL, sitemaps.
- `player/`: resolución de MP4/HLS y estados.
- `utils/`: normalización Unicode, URLs, diagnóstico.

## Documentación

- [Seguimiento](Docs/00-seguimiento.md)
- [Investigación CloudStream](Docs/01-investigacion-cloudstream.md)
- [Investigación HDrama](Docs/02-investigacion-hdrama.md)
- [Arquitectura](Docs/03-arquitectura.md)
- [Compilación](Docs/04-compilacion.md)
- [Pruebas](Docs/05-pruebas.md)
- [Instalación](Docs/06-instalacion.md)
- [Privacidad](Docs/07-privacidad.md)
- [Limitaciones](Docs/08-limitaciones.md)
- [Informe final](Docs/09-informe-final.md)

## Licencia

Pendiente de definir por el propietario. CloudStream y sus herramientas
conservan sus licencias originales.
