# Limitaciones técnicas

Fecha: 2026-11-11. Aplica a la versión 2 del plugin.

## 1. Dependencia de la sesión WebView y Cloudflare

`GET https://es.hdrama.net/` responde con un **Cloudflare Managed Challenge**
(HTTP 403 + `Cf-Mitigated: challenge`) cuando el cliente no tiene cookies
válidas. El plugin lo resuelve con un WebView del sistema (`WebViewFetcher`), que abre
un WebView y reutiliza las cookies obtenidas.

Consecuencias:

- La primera petición de una sesión puede tardar mientras se resuelve el
  challenge y necesita WebView funcional en el dispositivo.
- Si el challenge no se supera (WebView restringido, red bloqueada, cambio en
  la configuración de Cloudflare), el catálogo real no está disponible: el
  plugin cae a los sitemaps públicos y a las fichas de navegación.
- La API `/api/episode-source/...` también exige esas cookies; sin sesión
  devuelve HTTP 403 con `{"error":"forbidden"}` y el episodio se muestra como
  temporalmente no disponible.

## 2. Caducidad de los tokens de video

La API devuelve una cadena de fuentes con `refresh` y `refreshAfter`. Las URLs
HLS llevan tokens (`verify=...`) que caducan:

- Las fuentes se resuelven en el momento de reproducir y se cachean como
  máximo 5 minutos.
- Si el player tarda o se reanuda una reproducción antigua, el token puede
  haber caducado: hay que volver a abrir el episodio para resolver una fuente
  nueva.
- `refresh=1` fuerza una fuente nueva; el plugin no precachea enlaces de video a
  largo plazo.

## 3. Hosts y proxy de terceros

Las URLs descifradas apuntan a hosts externos al sitio, no controlados por este
proyecto:

- `zshipricf.farsunpteltd.net`, `pl.shortdizilab.com` y similares pueden
  devolver 404, 403 por geolocalización o dejar de existir.
- La URL directa responde 200 **sin** cabecera `Referer` y 403 **con**
  `Referer: https://es.hdrama.net/`; por eso se emite sin headers.
- El proxy espejo `cdn.dramadizilerim.xyz/https/<URL>` responde 200 con
  `Referer: https://es.hdrama.net/` y 451 sin él; es un servicio de terceros y
  puede fallar o cambiar de comportamiento.
- Las entradas de `pl.shortdizilab.com/v1/flickreels/...` devolvieron 404 en
  las comprobaciones. Se emiten igualmente porque el player prueba la cadena de
  enlaces, pero no hay garantía de que funcionen.

## 4. El sitio cambia sin aviso

HDrama es un objetivo en movimiento: es una aplicación Next.js con rutas,
selectores y payloads internos que pueden cambiar en cualquier despliegue. En
particular:

- La clave AES-256-GCM del payload `/api/episode-source` va embebida en el
  JavaScript del sitio; si cambia, el descifrado deja de funcionar hasta
  actualizar el plugin.
- Los selectores del DOM y el formato del payload RSC (`bookId`, `slug`) pueden
  cambiar; el parser es tolerante y deja los campos ausentes en `null`, pero
  una ficha sin título, episodios o `bookId` no se puede reproducir.
- La estructura de `chain` de la API puede variar (más fuentes, `type` nuevos,
  subtítulos). El cliente mapea `hls` y `mp4` y descarta lo que no reconoce sin
  romper el resto.

El respaldo por sitemaps mantiene títulos y navegación, pero no aporta
portadas, sinopsis, géneros, nota ni episodios: en ese modo la ficha es de
navegación.

## 5. Repositorio e instalación por URL

- El repositorio es **público** desde la v1.1.0. Un
  repositorio privado no sirve `repo.json`/`plugins.json` a CloudStream, porque
  la app no envía credenciales de GitHub.
- La instalación verificada es local: copiar `HDrama.cs3` a
  `/sdcard/Cloudstream3/plugins/` o usar ADB.
- El workflow de GitHub Actions está preparado para publicar en la rama
  `builds`, pero su ejecución remota requiere Actions habilitado
  (habilitadas en la v1.1.0).

## 6. Hardware físico no disponible

- No se dispone de un Galaxy Z Fold8 físico. La rotación, el plegado y la
  relación de aspecto 9:16 los gestiona CloudStream/ExoPlayer; el plugin no
  impone orientación ni recorta el video, pero no se ha verificado en ese
  hardware.
- La reproducción HLS real de HDrama (manifest y segmentos servidos por los
  hosts de terceros) se valida en el laboratorio Android; la verificación en
  hardware físico y en redes móviles queda para una sesión con dispositivo.
- Las pruebas instrumentadas de UI (Espresso/UIAutomator) no están
  automatizadas.

## 7. Reproducción y fuentes

- No hay DRM en el sitio: solo HLS/MP4. El plugin no implementa DRM ni
  descarga protegida.
- La cadena de fuentes no aporta metadatos de calidad; los enlaces se emiten
  como `Auto` y el player elige la variante del manifest HLS.
- El modelo soporta pistas de subtítulos si una fuente las aporta, pero la API
  de fuentes de HDrama no las emite actualmente: `descriptor.subtitle` se
  ignora de forma defensiva (llegó `null` en la muestra).
- La descarga desde CloudStream depende de que el host entregue el manifest y
  los segmentos; no se ha verificado el flujo de descarga completo.

## 8. API de CloudStream

La compilación usa la API `pre-release` actual (2026-11-09) y `apiVersion = 1`.
Si CloudStream cambia `MainAPI`, los modelos o el empaquetado `.cs3`, el plugin
puede requerir una actualización. Las pruebas fijan la versión actual; no
garantizan compatibilidad con versiones futuras.

## 9. D8 / Kotlin

D8 de AGP 8.7.3 emite advertencias de metadata al procesar Kotlin 2.3.0. El DEX
se genera y el plugin carga correctamente; la advertencia se eliminaría
actualizando AGP y Kotlin de forma conjunta.

## 10. Funciones que aporta CloudStream

Favoritos, historial, continuar viendo, selección de reproductor, pantalla
completa y descarga los gestiona CloudStream; el plugin solo entrega los
enlaces. El plugin no implementa UI ni player propios.
