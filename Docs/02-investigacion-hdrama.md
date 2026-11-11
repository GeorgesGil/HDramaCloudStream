# Investigación: HDrama (es.hdrama.net)

Fecha: 2026-11-11. Se realizaron únicamente consultas puntuales de
reconocimiento, respetando `robots.txt`. No se ejecutaron rastreadores
masivos ni se intentó superar ningún control de acceso.

## 1. Identidad del sitio

| Campo | Valor |
|---|---|
| URL | https://es.hdrama.net/ |
| Idioma | Español |
| Tipo aparente | Catálogo de series/doramas en línea |
| Patrón de fichas observado en sitemap | `/series/<slug>` |
| Sitemap índice | `https://es.hdrama.net/sitemap.xml` |
| Sitemaps hijos | `/sitemaps/new-es`, `/sitemaps/trending-es`, `/sitemaps/fresh-es`, `/sitemaps/static-es` |

## 2. `robots.txt`

Contenido relevante (2026-11-11):

```text
User-Agent: *
Allow: /
Disallow: /api/
Disallow: /flags/
Disallow: /source-icons/

User-Agent: AhrefsBot
User-Agent: SemrushBot
...
Disallow: /

Sitemap: https://es.hdrama.net/sitemap.xml
```

Interpretación:

- El sitio **no prohíbe a todos los agentes** el HTML público; permite `/`.
- **Prohíbe `/api/`**. Por tanto, no se usará ninguna interfaz bajo `/api/`
  sin autorización expresa.
- Los sitemaps están declarados oficialmente para descubrimiento.
- La prohibición de bots de SEO no convierte al sitio en "abierto": los
  términos de servicio pueden imponer condiciones adicionales.

## 3. Comportamiento HTTP observado

Solicitud `GET https://es.hdrama.net/` con user-agent de navegador:

```text
HTTP/1.1 403 Forbidden
Server: cloudflare
Cf-Mitigated: challenge
Content-Type: text/html; charset=UTF-8
```

El cuerpo es la página "Just a moment..." de **Cloudflare Managed Challenge**,
que exige JavaScript y cookies para continuar.

Consecuencias:

- Un cliente HTTP como el de CloudStream (OkHttp) recibe 403. No hay HTML que
  parsear.
- Superar el reto requiere ejecutar el JavaScript de Cloudflare, resolver el
  challenge y reutilizar cookies. Eso es exactamente una **evasión de control
  de acceso**, prohibida por el encargo y por los principios del proyecto.
- No se intentó usar navegador headless, cookies de terceros, proxies,
  servicios de "bypass" ni técnicas de camuflaje.

Solicitud `GET https://es.hdrama.net/sitemap.xml`: HTTP 200, XML válido de
índice con cuatro sitemaps. Una consulta puntual a `/sitemaps/new-es` devolvió
HTTP 200 con entradas:

```xml
<url>
  <loc>https://es.hdrama.net/series/<slug></loc>
  <lastmod>2026-11-09T19:01:06Z</lastmod>
</url>
```

Los sitemaps **solo aportan URL y `lastmod`**. No incluyen título, portada,
sinopsis, géneros, valoración, número de episodios ni enlaces de video. Por sí
solos no permiten construir las pantallas solicitadas.

## 4. Términos de servicio y autorización

No fue posible leer la página de términos: cualquier ruta HTML del dominio
queda detrás del mismo challenge de Cloudflare. El encargo del propietario
afirma expresamente que **HDrama prohíbe la extracción automatizada sin
autorización**. Ante la combinación de:

1. la afirmación del propietario,
2. el challenge activo de Cloudflare, y
3. la ausencia de una autorización documentada,

la conclusión es que **no existe base legítima para implementar una
integración automatizada activa** en este momento.

No se solicitó autorización por correo porque el alcance de esta sesión es
técnico y no se dispone de un canal verificado ni de credenciales. Queda
registrado como bloqueo externo.

## 5. Qué se puede implementar legítimamente

| Módulo | Viabilidad | Decisión |
|---|---|---|
| Catálogo automático de HDrama | Bloqueado | Adaptador deshabilitado por defecto; requiere autorización |
| Búsqueda automática | Bloqueada | No se inventa endpoint; se usa búsqueda local de demo |
| Ficha de serie automática | Bloqueada | Solo importación manual de URL + apertura en navegador |
| Episodios y enlaces | Bloqueado | No se extraen enlaces ni se evaden iframes/DRM |
| Navegación al sitio original | Permitida | Botón/acción de navegador de CloudStream |
| Importación manual de enlaces | Permitida | El usuario pega una URL `https://es.hdrama.net/series/...` |
| Datos de prueba locales | Permitida | Catálogo sintético con videos de dominio público |
| Reproducción de videos de demo | Permitida | MP4/HLS públicos de prueba |

## 6. Alternativas implementables (diseño)

1. **Modo demostración local (por defecto):** un catálogo sintético y
   etiquetado como demo permite verificar `getMainPage`, `search`, `load` y
   `loadLinks`, incluyendo MP4, HLS, fuente no disponible y caso de navegador.
2. **Modo HDrama (navegador):** las tarjetas de ficha muestran un aviso de
   autorización pendiente y un botón "Abrir en el navegador" que usa la URL
   original. No se hace ninguna petición al sitio.
3. **Importación manual:** si la búsqueda recibe una URL de HDrama, se crea
   una ficha con el slug como título y la URL original; `load()` no descarga
   HTML. El usuario puede abrirla en el navegador.
4. **Enlaces de video:** solo se emitirán para el catálogo de demo o para
   fuentes que el usuario importe manualmente y que sean URLs directas
   públicas, nunca para contenido protegido de HDrama.

## 7. Requisitos para habilitar la integración real

Para pasar de "adaptador deshabilitado" a "funcional" se necesita, como
mínimo:

1. Autorización escrita de HDrama para acceder a su HTML/API y para mostrar
   sus metadatos en una extensión de terceros.
2. Confirmación sobre los términos de los proveedores de video y sobre la
   legitimidad de reproducir sus enlaces fuera del sitio.
3. Acceso técnico estable (sin challenge) a las páginas necesarias, o una API
   documentada con credenciales.
4. Fixtures HTML autorizados para pruebas de integración.
5. Revisión de la política de privacidad y de la licencia de distribución.

Mientras no se cumplan, el código dejará el adaptador en estado
`AuthorizationStatus.NOT_GRANTED` y **no realizará peticiones a HDrama**.

## 8. Evidencia y límites de esta investigación

- Se consultaron `robots.txt`, `sitemap.xml` y un sitemap hijo (tres
  peticiones) más una petición a la home para comprobar el estado HTTP.
- No se descargaron páginas de series, episodios ni assets.
- No se usó ningún servicio de resolución de Cloudflare.
- No se almacenaron datos de HDrama en el repositorio; las fixtures serán
  sintéticas.

## 9. Referencias

- https://es.hdrama.net/robots.txt
- https://es.hdrama.net/sitemap.xml
- https://developers.cloudflare.com/cloudflare-challenges/
- https://www.rfc-editor.org/rfc/rfc9309 (Robots Exclusion Protocol)
