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
queda detrás del mismo challenge de Cloudflare.

El **2026-11-11 el propietario declaró tener autorización de HDrama**. No se
aportó al repositorio un documento, credencial, API oficial ni endpoint
autorizado que permita verificar esa autorización. A efectos de este proyecto:

1. Se acepta la declaración del propietario para usar los **sitemaps públicos**
   (que además están permitidos por `robots.txt`).
2. **No se acepta** como permiso para eludir Cloudflare, CAPTCHA, DRM o
   autenticación. Una autorización legítima debe traducirse en un canal de
   acceso autorizado, no en sortear la protección.
3. El adaptador que descargaría HTML protegido permanece deshabilitado hasta
   disponer de una API/canal autorizado y verificable.

No se solicitó autorización por correo porque el alcance de esta sesión es
técnico y no se dispone de un canal verificado ni de credenciales. Queda
registrado como bloqueo externo.

## 5. Qué se ha implementado legítimamente

| Módulo | Viabilidad | Decisión |
|---|---|---|
| Catálogo real de títulos desde sitemap | Permitida | Implementado: nuevas, tendencias, nuevos, catálogo y búsqueda por slug |
| Búsqueda real | Parcial | Sobre títulos derivados del sitemap; sin metadatos de ficha |
| Ficha real completa | Bloqueada | Solo ficha mínima con URL original y navegador |
| Portadas, sinopsis, géneros, valoración, episodios | Bloqueados | Requieren HTML protegido o API autorizada |
| Enlaces de video de HDrama | Bloqueado | No se extraen iframes, tokens ni DRM |
| Navegación al sitio original | Permitida | Botón/acción de navegador de CloudStream |
| Importación manual de enlaces | Permitida | El usuario pega una URL `https://es.hdrama.net/series/...` |
| Datos de prueba locales | Permitida | Catálogo sintético con videos de dominio público |
| Reproducción de videos de demo | Permitida | MP4/HLS públicos de prueba |

## 6. Alternativas implementadas

1. **Catálogo real de sitemaps (por defecto):** se descargan los sitemaps
   públicos declarados en `robots.txt` y se construyen las secciones con los
   slugs. No se descarga HTML de fichas.
2. **Ficha real mínima:** muestra el título derivado del slug y un episodio
   "Abrir en el navegador"; el botón nativo de CloudStream abre la URL original.
3. **Modo demo (diagnóstico):** catálogo sintético con MP4/HLS públicos para
   verificar `getMainPage`, `search`, `load` y `loadLinks`, incluidos estados
   de error y fuente no disponible.
4. **Importación manual:** si la búsqueda recibe una URL de HDrama, se crea
   una ficha con el slug como título y la URL original; `load()` no descarga
   HTML.
5. **Enlaces de video:** solo se emiten para el catálogo demo o para fuentes
   públicas directas; nunca para contenido protegido de HDrama.

## 7. Requisitos para habilitar la integración completa

Para pasar de "catálogo de sitemap + navegador" a "ficha y episodios reales"
se necesita, como mínimo:

1. Autorización escrita de HDrama para acceder a su HTML/API y para mostrar
   sus metadatos en una extensión de terceros.
2. Confirmación sobre los términos de los proveedores de video y sobre la
   legitimidad de reproducir sus enlaces fuera del sitio.
3. Acceso técnico estable (sin challenge) a las páginas necesarias, o una API
   documentada con credenciales.
4. Fixtures HTML autorizados para pruebas de integración.
5. Revisión de la política de privacidad y de la licencia de distribución.

Aunque se cumplan 1-5, **no se implementará evasión de Cloudflare/DRM**: el
acceso debe llegar por un canal que el sitio autorice y que no requiera sortear
la protección. Mientras tanto, el adaptador protegido queda en
`AuthorizationStatus.NOT_GRANTED` y no realiza peticiones al HTML.

## 8. Evidencia y límites de esta investigación

- Se consultaron `robots.txt`, `sitemap.xml` y los sitemaps hijos necesarios
  para construir el catálogo real (peticiones puntuales, no rastreo masivo).
- No se descargaron páginas de series, episodios ni assets protegidos.
- No se usó ningún servicio de resolución de Cloudflare.
- No se almacenaron datos de HDrama en el repositorio; las fixtures son
  sintéticas.

## 9. Referencias

- https://es.hdrama.net/robots.txt
- https://es.hdrama.net/sitemap.xml
- https://developers.cloudflare.com/cloudflare-challenges/
- https://www.rfc-editor.org/rfc/rfc9309 (Robots Exclusion Protocol)
