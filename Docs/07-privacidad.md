# Política de privacidad

Fecha: 2026-11-12. Aplica a la versión 3 del plugin.

## 1. Principio

HDramaCloudStream es un plugin local de CloudStream. No recopila, transmite ni
vende datos personales. No incluye cuentas, analítica, publicidad,
identificadores de dispositivo ni servicios de seguimiento. Tampoco envía datos
a servidores del autor del plugin: todas las peticiones van al sitio de HDrama o
a los hosts de video que el propio sitio indica.

## 2. Datos que se solicitan

| Dato / petición | Uso | Destino |
|---|---|---|
| Páginas HTML de portada, categorías, búsqueda y fichas | Catálogo, búsqueda y metadatos de series | `es.hdrama.net` |
| `/api/episode-source/<bookId>/<serial>` | Obtener la fuente HLS/MP4 del episodio | `es.hdrama.net` |
| Sitemaps (`sitemap.xml` y `/sitemaps/*`) | Respaldo del catálogo si la sesión falla | `es.hdrama.net` |
| URL de ficha importada manualmente | Abrir la ficha real o, si falla, una tarjeta de navegación | `es.hdrama.net` / navegador del usuario |
| Manifest HLS y segmentos del video | Reproducción del episodio | Hosts indicados por el sitio (por ejemplo `zshipricf.farsunpteltd.net`, `acdn-v-us.stardust-tv.com`) |
| Sonda de disponibilidad de enlaces | Descartar URLs de video muertas antes de reproducir (manifiesto HLS o 1 KB del MP4) | Los mismos hosts de video |
| Videos demo MP4/HLS | Diagnóstico del reproductor | `media.w3.org`, `test-streams.mux.dev` |
| Portadas de las tarjetas de acceso | Placeholder visual de las tarjetas locales | `placehold.co` |

No se envían credenciales de usuario, correo, número de teléfono ni
identificadores publicitarios. El plugin no pide ni almacena usuario y
contraseña porque el sitio no ofrece cuentas que el plugin utilice.

## 3. Cookies y sesión de Cloudflare

- HDrama está detrás de un Cloudflare Managed Challenge. `WebViewCloudflareSession`
  abre **un** WebView, carga `https://es.hdrama.net/` y sondea
  `CookieManager` hasta que aparece `cf_clearance`; después destruye el WebView.
  La cookie y el User-Agent del WebView se guardan en memoria y se envían
  únicamente en las peticiones a `es.hdrama.net` (HTML y `/api/`), junto con
  `Referer: https://es.hdrama.net/`.
- El plugin **lee** `CookieManager` para reutilizar esa cookie dentro del
  proceso de la app, pero no la exporta, no la escribe en disco, no la registra
  y no la envía a terceros. `Diagnostics` redacta `token`, `cookie`, `session`,
  `authorization` y `password` en los mensajes de error.
- La sesión caduca según Cloudflare y el sitio; ante un 403/503 se renueva el
  challenge una vez. Si sigue sin resolverse, el plugin cae a los sitemaps y a
  las fichas de navegación.

## 4. Almacenamiento

- Todo es **memoria del proceso**. No hay caché en disco, base de datos ni
  ficheros de preferencias propios.
- Cachés con TTL:
  - secciones y búsquedas del repositorio: 5 minutos, 64 entradas;
  - HTML, fichas y páginas de navegación del adaptador real: 5 minutos;
  - fuentes de video resueltas: 5 minutos (el token del sitio caduca);
  - snapshot de sitemaps: 15 minutos.
- Las entradas se descartan al salir del proceso o al agotarse el TTL.
- CloudStream conserva favoritos, historial y progreso según su propia
  política; el plugin no accede a esos datos.
- Las descargas de episodios las guarda **CloudStream** en la carpeta que el
  usuario elija una sola vez (selector de carpetas de Android); el plugin no
  escribe archivos ni accede a esa carpeta.

## 5. Registros

- No se registran URLs con tokens (`verify=`), cookies ni credenciales.
- `Diagnostics` sanea los mensajes y limita su longitud a 300 caracteres.
- `UrlUtils.redact` elimina query, fragmento y credenciales antes de incluir una
  URL en un mensaje.
- Los logs de Android pueden leerse con ADB; no contienen datos personales.

## 6. Enlaces externos y terceros

- La navegación al sitio original abre el navegador del usuario; a partir de
  ahí se aplican la política y los términos de HDrama.
- Los hosts de video son servicios de terceros: reciben la IP del dispositivo y
  las cabeceras normales de una petición HTTP/HTTPS. El plugin no envía
  `Referer` a esos hosts (el host directo responde 403 si lo recibe); cuando
  comprueba si una URL responde, solo pide el manifiesto HLS o los primeros
  bytes del MP4.
- Los videos demo son recursos públicos de terceros; su disponibilidad y
  condiciones dependen de sus proveedores.
- Las tarjetas de acceso usan imágenes de `placehold.co` con parámetros fijos.

## 7. Permisos

El manifest del plugin está vacío; los permisos de red los aporta la app
CloudStream. El plugin no solicita acceso a contactos, ubicación, cámara,
micrófono, almacenamiento externo ni telefonía.

## 8. Contacto

Para ejercer derechos o solicitar la eliminación de datos: el propietario del
repositorio. El plugin no mantiene datos que eliminar.
