# Limitaciones técnicas

Fecha: 2026-11-12. Aplica a la versión 3 del plugin.

## 1. Dependencia de la sesión WebView y Cloudflare

`GET https://es.hdrama.net/` responde con un **Cloudflare Managed Challenge**
(HTTP 403 + `Cf-Mitigated: challenge`) cuando el cliente no tiene cookies
válidas. La versión 3 lo resuelve una sola vez por proceso con
`WebViewCloudflareSession`: crea un único WebView, carga la portada, sondea
`CookieManager` hasta obtener `cf_clearance` (cada 350 ms, timeout 75 s) y
destruye el WebView. Todas las peticiones posteriores van por OkHttp con esa
cookie, el User-Agent del WebView y `Referer: https://es.hdrama.net/`.

Consecuencias:

- La primera carga de una sesión puede tardar mientras se resuelve el
  challenge; el plugin lo adelanta en segundo plano al cargarse. En emuladores
  lentos el primer WebView puede tardar decenas de segundos: en la sesión del
  2026-11-12 el primer intento agotó el timeout antiguo de 45 s y el segundo
  resolvió a los ~44 s; por eso el timeout actual es de 75 s.
- Si el challenge no se supera (WebView restringido, red bloqueada, cambio en
  la configuración de Cloudflare), el catálogo real no está disponible: cada
  sección falla rápido con HTTP 403 y el plugin cae a los sitemaps públicos y a
  las fichas de navegación. Cuando la sesión se resuelve, el catálogo real
  vuelve solo.
- Tras un fallo del challenge hay un backoff de 15 s para no encadenar
  WebViews. `invalidate()` fuerza un challenge nuevo sin borrar el resto de
  cookies de la app.
- La API `/api/episode-source/...` exige, además de la cookie, una petición
  same-origin (`Referer` o `Sec-Fetch-Site: same-origin`). El plugin envía
  `Referer` siempre; sin sesión, la API devuelve 403 y el episodio se muestra
  como temporalmente no disponible.

## 2. Caducidad de los tokens de video

La API devuelve una cadena de fuentes con `refresh` y `refreshAfter`. Las URLs
HLS del CDN llevan tokens y caducan:

- Las fuentes se resuelven en el momento de reproducir y se cachean como
  máximo 5 minutos.
- Si el player tarda o se reanuda una reproducción antigua, el token puede
  haber caducado: hay que volver a abrir el episodio para resolver una fuente
  nueva.
- `refresh=1` fuerza una fuente nueva; el plugin no precachea enlaces de video a
  largo plazo.

## 3. Hosts de video de terceros

Las URLs descifradas apuntan a hosts externos al sitio, no controlados por este
proyecto:

- En la muestra del 2026-11-12 la fuente es Stardust y la URL apunta a
  `acdn-v-us.stardust-tv.com`, que respondió 200 sin cabeceras. Otros hosts
  (por ejemplo `pl.shortdizilab.com` o `zshipricf.farsunpteltd.net` en muestras
  anteriores) pueden devolver 404, 403 por geolocalización o dejar de existir.
- El plugin emite las URLs **directas y sin headers**: el CDN no necesita
  `Referer`. Si el host empieza a exigir cabeceras o cambia de dominio, habrá
  que actualizar el cliente.
- Antes de entregar una URL, el plugin comprueba que responde
  (`HttpSourceProbe`: manifiesto HLS o 1 KB del MP4, timeout 6 s, sin
  `Referer`). Esta sonda añade una petición por URL cuando la cadena trae más de
  una; si el host bloquea la comprobación, se entregan igualmente las URLs
  originales cuando ninguna responde.
- El antiguo proxy espejo `cdn.dramadizilerim.xyz` ya no se usa: devolvía 451
  sin `Referer` y 403 con él, y la construcción anterior era incorrecta
  (`/https/https://...`). No hay una segunda URL de respaldo por host: la
  cadena de fuentes que devuelve la API es la única vía.

## 4. El sitio cambia sin aviso

HDrama es un objetivo en movimiento: es una aplicación Next.js con rutas,
selectores y payloads internos que pueden cambiar en cualquier despliegue. En
particular:

- La clave AES-256-GCM del payload `/api/episode-source` va embebida en el
  JavaScript del sitio; si cambia, el descifrado deja de funcionar hasta
  actualizar el plugin.
- Los selectores del DOM y el formato del payload RSC (`bookId`, `slug`,
  array de episodios) pueden cambiar. El `bookId` puede ser un número, un id
  hexadecimal, un UUID o el slug de la serie; el parser es tolerante y deja los
  campos ausentes en `null`, pero una ficha sin título, episodios o `bookId` no
  se puede reproducir.
- La estructura de `chain` de la API puede variar (más fuentes, `type` nuevos,
  subtítulos). El cliente mapea `hls` y `mp4`, infiere por extensión cuando el
  `type` es desconocido y descarta lo que no puede descifrar sin romper el
  resto.
- El formato del manifest HLS puede cambiar: hoy es una media playlist sin
  variantes de calidad; si el sitio sirviera un master playlist, el flujo de
  descarga de CloudStream elegiría la mejor variante automáticamente.

El respaldo por sitemaps mantiene títulos y navegación, pero no aporta
portadas, sinopsis, géneros, nota ni episodios: en ese modo la ficha es de
navegación.

## 5. Repositorio e instalación por URL

- El repositorio es **público** desde la v1.1.0. Un repositorio privado no
  sirve `repo.json`/`plugins.json` a CloudStream, porque la app no envía
  credenciales de GitHub.
- La instalación verificada es local: copiar `HDrama.cs3` a
  `/sdcard/Cloudstream3/plugins/` o usar ADB.
- El workflow de GitHub Actions está preparado para publicar en la rama
  `builds`, pero su ejecución remota requiere Actions habilitado (habilitadas
  desde la v1.1.0).

## 6. Hardware físico no disponible

- No se dispone de un Galaxy Z Fold8 físico. La rotación, el plegado y la
  relación de aspecto 9:16 los gestiona CloudStream/ExoPlayer; el plugin no
  impone orientación ni recorta el video, pero no se ha verificado en ese
  hardware.
- La reproducción y la descarga completas en emulador se cierran en la entrega
  v1.2.0; la verificación en hardware físico y en redes móviles queda para una
  sesión con dispositivo.
- Las pruebas instrumentadas de UI (Espresso/UIAutomator) no están
  automatizadas.

## 7. Reproducción y descarga

- No hay DRM en el sitio: solo HLS/MP4. El plugin no implementa DRM ni
  descarga protegida.
- La cadena de fuentes no aporta metadatos de calidad: la playlist del CDN es
  una media playlist sin variantes, así que los enlaces se emiten como `Auto`
  y no hay selección de calidad.
- El modelo soporta pistas de subtítulos si una fuente las aporta, pero la API
  de fuentes de HDrama no las emite actualmente: `descriptor.subtitle` se
  ignora de forma defensiva (llegó `null` en la muestra).
- La descarga la ejecuta CloudStream con el mismo `ExtractorLink`
  (`ExtractorLinkType.M3U8`): descarga la playlist, obtiene los segmentos `.ts`
  y los concatena en un MP4. El plugin no implementa la descarga ni un proxy.
  Si CloudStream cambia su flujo de descarga HLS, la descarga puede cambiar.
- La descarga depende de que el host entregue el playlist y los segmentos sin
  bloquear por región o caducidad del token.

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
