# HDramaCloudStream

Extensión de [CloudStream](https://github.com/recloudstream/cloudstream) para
[es.hdrama.net](https://es.hdrama.net/), escrita en Kotlin.

> **Estado:** proyecto en desarrollo. Consulta
> [`Docs/00-seguimiento.md`](Docs/00-seguimiento.md) para el avance real y
> [`Docs/08-limitaciones.md`](Docs/08-limitaciones.md) para los bloqueos de
> acceso detectados.

## Principios

- No se eluden CAPTCHA, Cloudflare, DRM, autenticación ni controles de acceso.
- La integración directa con HDrama queda deshabilitada hasta contar con
  autorización expresa del sitio.
- El plugin incluye un catálogo de demostración local, navegación al sitio
  original e importación manual de enlaces como alternativas permitidas.
- El proyecto documenta cada fase en [`Docs/`](Docs/README.md).

## Requisitos de desarrollo

- Windows 11 x64 (probado) / macOS / Linux.
- JDK 17 o 21.
- Android SDK con `platforms;android-35` y `build-tools;35.0.0`.
- Gradle se obtiene mediante el wrapper incluido.

## Compilación rápida

```powershell
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-21.0.13.9-hotspot"
.\gradlew.bat :HDrama:make
.\gradlew.bat :HDrama:makePluginsJson
```

El artefacto se genera en `HDrama/build/HDrama.cs3`.

## Instalación

Consulta [`Docs/06-instalacion.md`](Docs/06-instalacion.md).

## Documentación

- [Seguimiento](Docs/00-seguimiento.md)
- [Investigación de CloudStream](Docs/01-investigacion-cloudstream.md)
- [Investigación de HDrama](Docs/02-investigacion-hdrama.md)
- [Arquitectura](Docs/03-arquitectura.md)
- [Compilación](Docs/04-compilacion.md)
- [Pruebas](Docs/05-pruebas.md)
- [Instalación](Docs/06-instalacion.md)
- [Privacidad](Docs/07-privacidad.md)
- [Limitaciones](Docs/08-limitaciones.md)

## Licencia

Pendiente de definir por el autor del proyecto. El código de CloudStream y sus
herramientas conservan sus licencias originales.
