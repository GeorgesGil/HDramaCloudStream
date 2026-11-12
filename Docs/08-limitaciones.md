# Limitaciones técnicas

Fecha: 2026-11-11.

## 1. HTML de HDrama protegido por Cloudflare

`GET https://es.hdrama.net/` responde:

```text
HTTP/1.1 403 Forbidden
Cf-Mitigated: challenge
```

Es un **Cloudflare Managed Challenge** que exige JavaScript y cookies. Un
cliente HTTP como el de CloudStream recibe 403.

**Consecuencia:** no se pueden obtener automáticamente:

- portadas,
- sinopsis,
- géneros,
- valoración,
- número real de episodios,
- enlaces de video,
- subtítulos.

## 2. Catálogo real limitado al sitemap

Sí se implementó un catálogo real con los sitemaps públicos:

- Novedades (`/sitemaps/new-es`, 222 entradas en la comprobación).
- Más vistos (`/sitemaps/trending-es`, 500 entradas).
- Nuevos esta semana (`/sitemaps/fresh-es`, 500 entradas).
- Catálogo general (unión deduplicada).
- Búsqueda por título derivado del `slug`.

Los títulos se derivan del slug, por lo que pueden aparecer sin tildes,
concatenados o con sufijos numéricos. No son metadatos oficiales de la ficha.

## 3. Rutas de API en la versión actual

La versión actual obtiene el catálogo de los sitemaps y no utiliza rutas bajo `/api/`.

## 4. Repositorio privado y Actions deshabilitado

- `raw.githubusercontent.com` no sirve archivos de repositorios privados sin
  token; CloudStream no autentica.
- Por eso `repo.json`/`plugins.json` no funcionan como repositorio instalable
  mientras el repo sea privado.
- La entrega es un `.cs3` local instalable por archivo/ADB.
- El workflow está incluido pero **no se ejecutó**; Actions está deshabilitado
  por decisión del propietario.

## 5. Reproducción de contenido real

- No hay enlaces de video de HDrama. No se extraen iframes, tokens, firmas ni
  DRM.
- El modo real ofrece navegación al sitio original.
- El modo demo reproduce MP4/HLS públicos para verificar el reproductor.
- El caso `BrowserOnly` lanza un mensaje claro y remite al botón de navegador;
  no se emite un enlace de video falso.

## 6. HLS

El parser y el resolver emiten `ExtractorLinkType.M3U8` y las pruebas cubren
la clasificación. No se confirmó la reproducción HLS completa en el player
durante la sesión (sí se confirmó MP4 1080p con `Rendered first frame`).

## 7. Pantallas plegables

No se dispone de Galaxy Z Fold8 físico. La app/ExoPlayer gestionan rotación y
relación de aspecto; el plugin no impone orientación ni recorta. La verificación
en hardware queda pendiente.

## 8. API de CloudStream

La compilación usa la API `pre-release` actual (2026-11-09) y `apiVersion = 1`.
Si CloudStream cambia `MainAPI`, los modelos o el empaquetado `.cs3`, el plugin
puede requerir actualización. Las pruebas de compatibilidad fijan la versión
actual, no garantizan versiones futuras.

## 9. D8 / Kotlin

D8 de AGP 8.7.3 emite advertencias de metadata al procesar Kotlin 2.3.0. El
DEX se genera y carga; se recomienda actualizar AGP/Kotlin de forma conjunta en
el futuro.

## 10. Funciones no implementadas

- Favoritos/historial: los aporta CloudStream, no el plugin.
- Subtítulos: se soportan si un `MediaSource` los aporta, pero no hay fuente
  real disponible.
- Selección de calidad: se soporta con varios enlaces; el demo solo tiene una
  calidad por episodio.
- Descarga: CloudStream puede descargar MP4/HLS si el enlace es accesible; no se
  probó.
- Reproducción vertical 9:16: depende del player; no se dispone de un video
  9:16 de prueba.
