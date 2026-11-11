# Política de privacidad

Fecha: 2026-11-11.

## Principio

HDramaCloudStream es un plugin local de CloudStream. No recopila, transmite ni
vende datos personales. No incluye analítica, publicidad, identificadores de
dispositivo ni servicios de seguimiento.

## Datos que se solicitan

| Dato | Uso | Destino |
|---|---|---|
| `sitemap.xml` y sitemaps hijos | Construir el catálogo real | `es.hdrama.net` |
| URL de ficha importada manualmente | Construir una ficha de navegación | Solo se abre en el navegador del usuario |
| Videos demo MP4/HLS | Probar el reproductor | `media.w3.org`, `test-streams.mux.dev` |

No se envían cookies, credenciales, tokens ni identificadores de usuario.

## Almacenamiento

- El catálogo de sitemaps se guarda **solo en memoria** durante 15 minutos.
- Los resultados de secciones/búsquedas se guardan en memoria 5 minutos.
- No hay caché en disco ni base de datos.
- CloudStream conserva favoritos, historial y progreso según su propia política;
  el plugin no accede a esos datos.

## Registros

- No se registran URLs con tokens, cookies ni credenciales.
- `Diagnostics` redacta parámetros sensibles en mensajes de error.
- Los logs de Android pueden ser leídos por el usuario mediante ADB; no
  contienen datos personales.

## Enlaces externos

- La navegación al sitio original abre el navegador del usuario; a partir de
  ahí se aplican la política y los términos de HDrama.
- Los videos demo son recursos públicos de terceros; su disponibilidad y
  condiciones dependen de sus proveedores.

## Autorización

El propietario del proyecto declara contar con autorización de HDrama. No se
incluye documentación de esa autorización en el repositorio. El plugin no usa
esa declaración para eludir Cloudflare ni otros controles; solo consume
sitemaps públicos y abre páginas en el navegador.

## Contacto

Para ejercer derechos o solicitar la eliminación de datos: el propietario del
repositorio. El plugin no mantiene datos que eliminar.
