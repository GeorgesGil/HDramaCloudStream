# Informe final (versión 4)

Fecha: 2026-11-16.

## 1. Resumen de lo implementado

Se entregó la versión 4 del plugin **HDrama** para CloudStream, escrito en
Kotlin y empaquetado como `HDrama.cs3`. La versión 3 reescribió la capa de red
para que la reproducción y la descarga HLS funcionaran; la versión 4 cierra la
limpieza de producción: la extensión muestra **solo contenido real** de
`es.hdrama.net` y se retira todo lo visible de diagnóstico.

- **Siete secciones reales**: Destacados, Novedades, Más vistos, Tendencias,
  Catálogo general, Géneros y Fuentes. Ya no existe "Acceso a HDrama".
- **Sin catálogo demo en la UI**: se eliminaron `CatalogSection.ACCESO`,
  `AccessCards.kt`, el atajo de búsqueda `prueba` y las ramas de demo del
  provider y de los adaptadores.
- **Respaldo con contenido real**: la cadena es HTML real → sitemaps → página
  vacía; las fichas de URLs ajenas devuelven `null`. Se mantienen la
  importación manual y las fichas de navegación (`browserOnly`).
- **`DemoCatalogAdapter` solo para pruebas**: el catálogo sintético queda como
  utilidad interna de las suites JVM; no se instancia en producción.
- **Motor de red y reproducción de la v3**: sesión Cloudflare resuelta una sola
  vez, fetcher OkHttp con `Referer`, API de fuentes descifrada con AES-256-GCM,
  URLs HLS directas comprobadas con `SourceProbe` y descarga delegada en
  CloudStream.
- **Versión**: `version = 4` en `HDrama/build.gradle.kts` y
  `PluginInfo.VERSION = 4`; release de referencia **v1.2.1**.

## 2. Estructura de archivos

```text
HDramaCloudStream/
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── gradlew / gradlew.bat
├── gradle/wrapper/
├── README.md
├── CHANGELOG.md
├── repo.json
├── .github/workflows/build.yml
├── Docs/ (00..09 + README.md + images/)
└── HDrama/
    ├── build.gradle.kts
    └── src/
        ├── main/kotlin/com/hdrama/
        │   ├── HDramaPlugin.kt
        │   ├── HDramaProvider.kt
        │   ├── PluginInfo.kt
        │   ├── catalog/   (sin AccessCards; DemoCatalogAdapter solo pruebas)
        │   ├── models/
        │   ├── network/
        │   ├── parser/
        │   ├── player/
        │   ├── sources/
        │   └── utils/
        └── test/ (fixtures reales recortadas + suites JVM)
```

Los archivos de la capa de red de la v3 siguen en `network/`:
`CloudflareSession.kt`, `WebViewCloudflareSession.kt` y
`HdramaHttpFetcher.kt`; la sonda de enlaces vive en `sources/SourceProbe.kt`
(con `SourceProbeFactory` en `HdramaRuntime.kt`).

## 3. Versiones utilizadas

| Componente | Versión |
|---|---|
| CloudStream probado | 4.9.1 / pre-release 2026-11-09 |
| API de plugins | 1 |
| Versión del plugin | 4 |
| Release de referencia | v1.2.1 |
| JDK | Eclipse Adoptium Temurin 21.0.13+9 |
| Gradle | 8.12 |
| AGP | 8.7.3 |
| Kotlin | 2.3.0 |
| compileSdk / targetSdk | 35 / 35 |
| minSdk | 21 |
| Android de prueba | emulador x86_64 Android 15 (AOSP) con CloudStream 4.9.1 |

## 4. Resultados de compilación

```text
.\gradlew.bat :HDrama:testDebugUnitTest --console=plain  -> BUILD SUCCESSFUL
.\gradlew.bat :HDrama:make                               -> BUILD SUCCESSFUL
.\gradlew.bat makePluginsJson                            -> BUILD SUCCESSFUL
```

| Artefacto | Ruta | Tamaño | SHA-256 |
|---|---|---|---|
| Plugin | `HDrama/build/HDrama.cs3` | 140.492 bytes (137 KB) | 83d47821e4b4b5bcf8a3575fe3ef73473b37c48acf317da5c98d9f8ecb65bdee |
| Índice | `build/plugins.json` | — | — |

El `.cs3` contiene `manifest.json` y `classes.dex`; el manifest declara
`version: 4` y referencia `com.hdrama.HDramaPlugin`.

Total de pruebas de la suite: 117 pruebas unitarias en
18 suites.

## 5. Pruebas ejecutadas

- Suites JVM (sin red) de parser HTML, cifrado AES-GCM (con el vector
  Stardust), cliente de fuentes, fetcher principal con sesión y transporte
  falsos, sonda de disponibilidad de enlaces, red/allowlist, catálogo con
  respaldos, player y utilidades. Detalle en
  [`05-pruebas.md`](05-pruebas.md).
- `DemoCatalogAdapterTest` sigue como prueba unitaria aislada del catálogo
  sintético; ya no forma parte de la integración de producción.
- Prueba de integración opcional contra los sitemaps reales
  (`HDRAMA_INTEGRATION=1`).
- Comprobaciones de red del 2026-11-12: con cookie `cf_clearance` y User-Agent
  de Chrome el HTML responde 200; sin cookie, 403; `/api/episode-source`
  responde 200 solo con `Referer` (o `Sec-Fetch-Site: same-origin`).
- Análisis del flujo de descarga de CloudStream sobre su código fuente
  (`VideoDownloadManager.downloadHLS` → `M3u8Helper2.hslLazy` →
  `HlsPlaylistParser.parse`), que con la media playlist actual descarga los
  segmentos `.ts` y los concatena en un MP4.
- Verificación en emulador Android 15 con CloudStream 4.9.1 (versión 3): log
  `Challenge resuelto; cookie de sesion actualizada`, catálogo real con póster,
  búsqueda real, ficha con 125 episodios, reproducción completa (log `loadExo`
  → `Rendered first frame`) y descarga de 26.160.200 bytes.
- La versión 4 se compiló con `:HDrama:make` (140.492 bytes, SHA-256
  `83d47821…`) y se cargó en el emulador Android 15 con CloudStream 4.9.1
  (`Loaded plugin HDrama.cs3 successfully`); el challenge se resolvió
  (`Challenge resuelto; cookie de sesion actualizada`) y el catálogo real
  mostró únicamente las siete secciones, sin la fila "Acceso a HDrama"
  (capturas `10-catalogo-sin-diagnostico-v4.png` y
  `11-secciones-finales-v4.png`). La suite JVM fija siete secciones y la
  ausencia de la sección `acceso` (117 pruebas en 18 suites).

## 6. Funciones operativas

- Registro del plugin y del proveedor `HDrama`.
- Catálogo real por siete secciones, con paginación de 24 elementos.
- Búsqueda real por título en `/search?q=`.
- Ficha real con póster, sinopsis, géneros, nota, año, episodios y
  relacionadas.
- Reproducción de episodios reales mediante HLS directo sin cabeceras.
- Descarte de enlaces muertos antes de entregarlos al reproductor.
- Descarga del episodio HLS desde el gestor de descargas de CloudStream.
- Respaldo por sitemaps y fichas de navegación si no hay sesión.
- Importación manual de URLs de series.
- Solo contenido real de HDrama en la UI (sin demo ni diagnóstico).
- Estados de error diferenciados y mensajes en español.
- Caché en memoria con TTL, allowlist y reintentos en la capa de respaldo.

## 7. Limitaciones técnicas

- La sesión de Cloudflare depende del WebView de Android; si el challenge no se
  resuelve, se usa el respaldo por sitemaps y navegación. En emuladores lentos
  el primer WebView puede tardar decenas de segundos (de ahí el timeout de
  75 s).
- Si fallan la sesión, la red y los sitemaps, la sección queda vacía: no hay
  contenido sintético de relleno.
- Los tokens de video caducan; las fuentes se resuelven al reproducir y se
  cachean como máximo 5 minutos.
- Los hosts de video son de terceros y pueden fallar o cambiar (404, 403/451,
  geobloqueo); ya no hay proxy espejo.
- La comprobación de enlaces añade una petición por URL cuando la cadena trae
  más de una; si el host no permite la sonda, se entregan las URLs originales.
- El sitio (Next.js) puede cambiar rutas, selectores, la clave de cifrado o el
  formato del `bookId` sin aviso; el parser y el cliente son tolerantes, pero
  requieren mantenimiento.
- Sin DRM en el sitio: solo HLS/MP4 normales; la playlist no ofrece variantes
  de calidad, así que los enlaces se emiten como `Auto`.
- La descarga la ejecuta CloudStream; si cambia su flujo HLS, la descarga puede
  cambiar.
- D8 de AGP 8.7.3 emite advertencias de metadata Kotlin; no impiden compilar ni
  cargar el plugin.
- No se ha probado en Galaxy Z Fold8 físico ni en redes móviles.
- Repositorio **público** desde la v1.1.0; GitHub Actions habilitadas
  (workflow `Build plugin`).

Detalle completo en [`08-limitaciones.md`](08-limitaciones.md).

## 8. Ubicación del archivo compilado

```text
HDrama/build/HDrama.cs3
```

La copia de entrega se publica en la rama `builds` y en la release v1.2.1:

- Release: <https://github.com/GeorgesGil/HDramaCloudStream/releases/tag/v1.2.1>
  (adjunta `HDrama.cs3` de 140.492 bytes (137 KB), SHA-256
  `83d47821e4b4b5bcf8a3575fe3ef73473b37c48acf317da5c98d9f8ecb65bdee`, y `plugins.json` con `version: 4`).
- Rama `builds` actualizada por el workflow `Build plugin` (run 43880888322) y
  release publicada por el run 43881333699.
- Repositorio de CloudStream:
  `https://raw.githubusercontent.com/GeorgesGil/HDramaCloudStream/builds/repo.json`

## 9. Pasos para instalar en el Galaxy Z Fold8

1. Instalar CloudStream 4.9.1 desde el release oficial.
2. Copiar `HDrama.cs3` a `/sdcard/Cloudstream3/plugins/`.
3. Conceder a CloudStream acceso a archivos (Android 11+) o usar ADB.
4. Reiniciar CloudStream.
5. Elegir el proveedor **HDrama** en el filtro.
6. Comprobar que aparecen las siete secciones de contenido real (sin "Acceso a
   HDrama").
7. Abrir una sección real, una ficha con episodios y reproducir un episodio
   (HLS directo).
8. Probar la descarga del episodio desde el botón de descarga de CloudStream.
9. Verificar logs con
   `adb logcat -s PluginManager PluginInstance CS3ExoPlayer HDrama`.

Detalle en [`06-instalacion.md`](06-instalacion.md).

## 10. Mejoras recomendadas

1. Probar en Galaxy Z Fold8 (plegado/desplegado, rotación, 9:16) y en redes
   móviles.
2. Instalar desde `repo.json` público en un dispositivo limpio y verificar la
   actualización automática del plugin.
3. Mantener actualizados los selectores del parser, los vectores de cifrado y
   el tratamiento del `bookId` si el sitio cambia.
4. Actualizar AGP/Kotlin juntos para eliminar las advertencias de metadata D8.
5. Añadir pruebas instrumentadas de UI y de carga del plugin.
