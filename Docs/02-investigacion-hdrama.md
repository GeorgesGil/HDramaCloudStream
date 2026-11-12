# Investigación: HDrama (es.hdrama.net)

Fecha: 2026-11-11. Se realizaron consultas de reconocimiento para observar
la estructura del catálogo y las respuestas HTTP del sitio.

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

- `robots.txt` contiene reglas `Allow: /` y `Disallow: /api/` junto con otras rutas.
- Los sitemaps están publicados como fuentes de descubrimiento de URLs.

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

## 4. Disponibilidad de datos

Las solicitudes HTML realizadas durante esta investigación devolvieron HTTP 403.
Las consultas al sitemap devolvieron XML con URLs y fechas de actualización,
sin metadatos de episodios ni enlaces multimedia.

## 5. Estado de implementación

| Módulo | Viabilidad | Decisión |
|---|---|---|
| Catálogo real de títulos desde sitemap | Implementado | Implementado: nuevas, tendencias, nuevos, catálogo y búsqueda por slug |
| Búsqueda real | Parcial | Sobre títulos derivados del sitemap; sin metadatos de ficha |
| Ficha real completa | Pendiente | Solo ficha mínima con URL original y navegador |
| Portadas, sinopsis, géneros, valoración, episodios | Pendiente | Metadatos aún no disponibles |
| Enlaces de video de HDrama | Pendiente | Extracción de enlaces aún no implementada |
| Navegación al sitio original | Implementado | Botón/acción de navegador de CloudStream |
| Importación manual de enlaces | Implementado | El usuario pega una URL `https://es.hdrama.net/series/...` |
| Datos de prueba locales | Implementado | Catálogo sintético con videos de dominio público |
| Reproducción de videos de demo | Implementado | MP4/HLS públicos de prueba |

## 6. Alternativas implementadas

1. **Catálogo real de sitemaps (por defecto):** se descargan los sitemaps
   públicos de HDrama y se construyen las secciones con los
   slugs. No se descarga HTML de fichas.
2. **Ficha real mínima:** muestra el título derivado del slug y un episodio
   "Abrir en el navegador"; el botón nativo de CloudStream abre la URL original.
3. **Modo demo (diagnóstico):** catálogo sintético con MP4/HLS públicos para
   verificar `getMainPage`, `search`, `load` y `loadLinks`, incluidos estados
   de error y fuente no disponible.
4. **Importación manual:** si la búsqueda recibe una URL de HDrama, se crea
   una ficha con el slug como título y la URL original; `load()` no descarga
   HTML.
5. **Enlaces de video:** el resolver actual procesa las fuentes de demostración;
   no hay un extractor implementado para los episodios del sitio.

## 7. Trabajo pendiente para la integración completa

1. Implementar la obtención y el análisis de fichas y episodios reales.
2. Validar el contrato de selectores HTML con fixtures representativas.
3. Integrar metadatos y fuentes multimedia con el modelo del plugin.
4. Añadir pruebas de integración y compatibilidad con CloudStream.

## 8. Evidencia y límites de esta investigación

- Se consultaron `robots.txt`, `sitemap.xml` y los sitemaps hijos para
  construir el catálogo real.
- Los archivos de prueba del repositorio son fixtures sintéticas.

## 9. Referencias

- https://es.hdrama.net/robots.txt
- https://es.hdrama.net/sitemap.xml
- https://developers.cloudflare.com/cloudflare-challenges/
- https://www.rfc-editor.org/rfc/rfc9309 (Robots Exclusion Protocol)
