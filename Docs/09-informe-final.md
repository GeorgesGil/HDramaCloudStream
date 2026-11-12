# Informe final (versión 2)

Fecha: 2026-11-11.

## 1. Resumen de lo implementado

Se entregó la versión 2 del plugin **HDrama** para CloudStream, escrito en
Kotlin y empaquetado como `HDrama.cs3`. La versión 2 sustituye el catálogo de
solo sitemaps por una integración real con el sitio `es.hdrama.net`:

- **Catálogo, búsqueda y fichas reales** desde el HTML del sitio (JSON-LD y
  DOM) con respaldo por sitemaps públicos y catálogo demo.
- **Sesión Cloudflare** mediante el WebView de CloudStream
  (WebView del sistema, `WebViewFetcher`), con cookies reutilizadas en las peticiones HTML y de
  API.
- **Episodios reales** con número mostrado, duración y `externalId`
  `bookId:serial`.
- **Fuentes HLS reales**: payload de `/api/episode-source` descifrado con
  AES-256-GCM, URL directa sin headers y proxy espejo con `Referer`.
- **Ocho secciones**: Destacados, Novedades, Más vistos, Tendencias, Catálogo
  general, Géneros, Fuentes y Acceso a HDrama (sitio oficial, importar enlace y
  demo).
- **Respaldo completo**: si Cloudflare, la red o los hosts de video fallan, el
  plugin sigue mostrando catálogo, fichas de navegación y el modo demo.

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
        │   ├── catalog/
        │   ├── models/
        │   ├── network/
        │   ├── parser/
        │   ├── player/
        │   ├── sources/
        │   └── utils/
        └── test/ (fixtures reales recortadas + suites JVM)
```

## 3. Versiones utilizadas

| Componente | Versión |
|---|---|
| CloudStream probado | 4.9.1 / pre-release 2026-11-09 |
| API de plugins | 1 |
| Versión del plugin | 2 |
| JDK | Android Studio JBR 21.0.6 |
| Gradle | 8.12 |
| AGP | 8.7.3 |
| Kotlin | 2.3.0 |
| compileSdk / targetSdk | 35 / 35 |
| minSdk | 21 |
| Android de prueba | emulador x86_64 con CloudStream 4.9.1 |

## 4. Resultados de compilación

```text
.\gradlew.bat :HDrama:testDebugUnitTest --console=plain  -> BUILD SUCCESSFUL
.\gradlew.bat :HDrama:make                               -> BUILD SUCCESSFUL
.\gradlew.bat makePluginsJson                            -> BUILD SUCCESSFUL
```

| Artefacto | Ruta | Tamaño | SHA-256 |
|---|---|---|---|
| Plugin | `HDrama/build/HDrama.cs3` | 123.299 bytes (123 KB) | 7fbcccb77000ab15ccaa81605b5bc4e11762c9777f83d0f2e62101152e1394e4 |
| Índice | `build/plugins.json` | 761 bytes | — |

El `.cs3` contiene `manifest.json` y `classes.dex`; el manifest declara
`version: 2` y referencia `com.hdrama.HDramaPlugin`.

Total de pruebas de la suite: 98 pruebas unitarias en 16 suites.

## 5. Pruebas ejecutadas

- Suites JVM (sin red) de parser HTML, cifrado AES-GCM, cliente de fuentes,
  red/allowlist, catálogo con respaldos, player y utilidades. Detalle en
  [`05-pruebas.md`](05-pruebas.md).
- Prueba de integración opcional contra los sitemaps reales
  (`HDRAMA_INTEGRATION=1`).
- Verificación de las URLs HLS a nivel de host: 200 sin `Referer` en la URL
  directa, 200 con `Referer` en el proxy espejo.
- Verificación manual en emulador de la versión 2:
  Verificación parcial en emulador Android 14 (AOSP API 34, CloudStream 4.9.1): plugin cargado, catálogo real (Destacados con títulos y notas) y ficha completa (p. ej. 41 episodios). La reproducción HLS queda pendiente de confirmar en hardware físico: la API de fuentes solo se puede pedir como XHR same-origin desde el WebView y esa ruta está implementada.

## 6. Funciones operativas

- Registro del plugin y del proveedor `HDrama`.
- Catálogo real por secciones, con paginación de 24 elementos.
- Búsqueda real por título en `/search?q=`.
- Ficha real con póster, sinopsis, géneros, nota, año, episodios y
  relacionadas.
- Reproducción de episodios reales mediante HLS directo y espejo.
- Respaldo por sitemaps y fichas de navegación si no hay sesión.
- Importación manual de URLs de series.
- Catálogo demo con MP4/HLS públicos para diagnóstico.
- Estados de error diferenciados y mensajes en español.
- Caché en memoria con TTL, allowlist y reintentos en la capa de respaldo.

## 7. Limitaciones técnicas

- La sesión de Cloudflare depende del WebView de CloudStream; si el challenge
  no se resuelve, se usa el respaldo por sitemaps y navegación.
- Los tokens de video caducan; las fuentes se resuelven al reproducir y se
  cachean como máximo 5 minutos.
- Los hosts de video y el proxy espejo son de terceros y pueden fallar o
  cambiar (404, 403/451, geobloqueo).
- El sitio (Next.js) puede cambiar rutas, selectores o la clave de cifrado sin
  aviso; el parser y el cliente son tolerantes, pero requieren mantenimiento.
- Sin DRM en el sitio: solo HLS/MP4 normales.
- D8 de AGP 8.7.3 emite advertencias de metadata Kotlin; no impiden compilar ni
  cargar el plugin.
- No se ha probado en Galaxy Z Fold8 físico ni en redes móviles.
- Repositorio: **público** desde la v1.1.0; la
  instalación es local. GitHub Actions:
  GitHub Actions habilitadas (workflow Build plugin).

Detalle completo en [`08-limitaciones.md`](08-limitaciones.md).

## 8. Ubicación del archivo compilado

```text
C:\Users\elbac\Desktop\Georges\AI\HDramaCloudStream\HDrama\build\HDrama.cs3
```

La copia de entrega se publica en la rama `builds` y en la release v1.1.0:
Release v1.1.0 publicada con HDrama.cs3 adjunto y rama uilds (repo.json + plugins.json).

## 9. Pasos para instalar en el Galaxy Z Fold8

1. Instalar CloudStream 4.9.1 desde el release oficial.
2. Copiar `HDrama.cs3` a `/sdcard/Cloudstream3/plugins/`.
3. Conceder a CloudStream acceso a archivos (Android 11+) o usar ADB.
4. Reiniciar CloudStream.
5. Elegir el proveedor **HDrama** en el filtro.
6. Abrir una sección real, una ficha con episodios y reproducir un episodio
   (HLS directo o espejo).
7. Para el diagnóstico del player, abrir la demo desde "Acceso a HDrama".
8. Verificar logs con
   `adb logcat -s PluginManager PluginInstance CS3ExoPlayer`.

Detalle en [`06-instalacion.md`](06-instalacion.md).

## 10. Mejoras recomendadas

1. Añadir capturas y evidencias de la verificación v2 en emulador y hardware.
2. Publicar la release v1.1.0 y actualizar la rama `builds` con los artefactos
   de la versión 2.
3. Decidir la visibilidad del repositorio y, si se desea CI, habilitar GitHub
   Actions.
4. Probar en Galaxy Z Fold8 (plegado/desplegado, rotación, 9:16) y en redes
   móviles.
5. Mantener actualizados los selectores del parser y los vectores de cifrado
   si el sitio cambia.
6. Actualizar AGP/Kotlin juntos para eliminar las advertencias de metadata D8.
7. Añadir pruebas instrumentadas de UI y de carga del plugin.
