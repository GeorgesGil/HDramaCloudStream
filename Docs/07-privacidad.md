# Política de privacidad

Fecha: 2026-11-11. Aplica a la versión 2 del plugin.

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
| Manifest HLS y segmentos del video | Reproducción del episodio | Hosts indicados por el sitio (p. ej. `zshipricf.farsunpteltd.net`, `pl.shortdizilab.com`) |
| Proxy espejo `cdn.dramadizilerim.xyz` | Alternativa de reproducción cuando el host directo falla | `cdn.dramadizilerim.xyz` |
| Videos demo MP4/HLS | Diagnóstico del reproductor | `media.w3.org`, `test-streams.mux.dev` |
| Portadas de las tarjetas de acceso | Placeholder visual de las tarjetas locales | `placehold.co` |

No se envían credenciales de usuario, correo, número de teléfono ni
identificadores publicitarios. El plugin no pide ni almacena usuario y
contraseña porque el sitio no ofrece cuentas que el plugin utilice.

## 3. Cookies y sesión de Cloudflare

- HDrama está detrás de un Cloudflare Managed Challenge. El plugin usa
  El `WebViewFetcher` del plugin: la app abre un WebView, resuelve el
  challenge y reutiliza las cookies (`cf_clearance`) en las peticiones
  siguientes.
- Las cookies las gestiona CloudStream/WebView dentro del proceso de la app; el
  plugin no las lee, no las exporta, no las registra y no las envía a terceros.
  `Diagnostics` redacta `token`, `cookie`, `session`, `authorization` y
  `password` en los mensajes de error.
- La sesión caduca según Cloudflare y el sitio; cuando deja de ser válida, el
  plugin cae a los sitemaps y a las fichas de navegación.

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

## 5. Registros

- No se registran URLs con tokens (`verify=`), cookies ni credenciales.
- `Diagnostics` sanea los mensajes y limita su longitud a 300 caracteres.
- `UrlUtils.redact` elimina query, fragmento y credenciales antes de incluir una
  URL en un mensaje.
- Los logs de Android pueden leerse con ADB; no contienen datos personales.

## 6. Enlaces externos y terceros

- La navegación al sitio original abre el navegador del usuario; a partir de
  ahí se aplican la política y los términos de HDrama.
- Los hosts de video y el proxy espejo son servicios de terceros: reciben la
  IP del dispositivo y las cabeceras normales de una petición HTTP/HTTPS. El
  plugin solo envía `Referer: https://es.hdrama.net/` al proxy espejo, que es
  lo que ese servicio exige para responder.
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
