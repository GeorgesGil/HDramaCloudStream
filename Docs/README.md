# Documentación del proyecto HDramaCloudStream

Esta carpeta contiene el seguimiento vivo del proyecto: investigación,
decisiones, avances por fase, resultados de compilación, pruebas, instalación,
privacidad y limitaciones. La documentación está actualizada a la **versión 3**
del plugin (reproducción y descarga HLS reales, sesión Cloudflare resuelta una
sola vez y fetcher OkHttp).

## Índice

| Documento | Contenido |
|---|---|
| [00-seguimiento.md](00-seguimiento.md) | Bitácora, estado por fase, decisiones y próximos pasos |
| [01-investigacion-cloudstream.md](01-investigacion-cloudstream.md) | API de plugins, modelos, compilación `.cs3`, repositorios |
| [02-investigacion-hdrama.md](02-investigacion-hdrama.md) | Rutas, JSON-LD, DOM, API de fuentes y cifrado verificados |
| [03-arquitectura.md](03-arquitectura.md) | Arquitectura v3, paquetes, rutas, cachés y flujos |
| [04-compilacion.md](04-compilacion.md) | Entorno JDK/SDK, comandos y resultados de build v3 |
| [05-pruebas.md](05-pruebas.md) | Suites, fixtures, plan de pruebas y verificación en emulador |
| [06-instalacion.md](06-instalacion.md) | Instalación local y como repositorio, reproducción y descarga |
| [07-privacidad.md](07-privacidad.md) | Cookies de WebView, hosts de video, datos y minimización |
| [08-limitaciones.md](08-limitaciones.md) | Limitaciones técnicas y funciones no cubiertas |
| [09-informe-final.md](09-informe-final.md) | Informe de la versión 3 |

## Convenciones

- Fechas en formato ISO 8601 (`YYYY-MM-DD`), zona `America/Santo_Domingo`.
- Todo resultado técnico se registra con el comando ejecutado y su salida
  resumida.
- Las cifras que dependen de la ejecución final (total de pruebas, tamaño y
  SHA-256 del `.cs3`, visibilidad del repositorio o evidencias de emulador) se
  marcan con un aviso `PENDIENTE` entre corchetes dobles hasta que el
  responsable las confirme.
- Las fixtures de prueba son recortes de páginas reales del sitio (estructura
  conservada, menos de 40 KB); el catálogo demo es sintético y está etiquetado
  como tal.
- Los enlaces a otros documentos son relativos a esta carpeta.
