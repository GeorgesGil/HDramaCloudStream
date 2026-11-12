# Instalación en Android (versión 3)

Fecha: 2026-11-12. Procedimiento de instalación del plugin **v3** en Android
con CloudStream 4.9.1. La instalación local del `.cs3` es la vía verificada;
la instalación como repositorio depende de que el repositorio sea público y de
que la rama `builds` tenga los artefactos de la versión 3.

El dispositivo objetivo final es un Samsung Galaxy Z Fold8 con Android 17/One
UI 9; no se dispone de él en esta sesión, así que la verificación se hace en
emulador (ver [`05-pruebas.md`](05-pruebas.md)).

## 1. Instalar CloudStream

1. Descargar el APK oficial desde
   <https://github.com/recloudstream/cloudstream/releases>.
   - Probado: `4.9.1.apk` (2026-11-09).
2. Permitir en Android la instalación desde esa fuente (Ajustes > Seguridad >
   Instalar apps desconocidas).
3. Instalar el APK.

Alternativa por ADB:

```powershell
adb install -r 4.9.1.apk
```

## 2. Instalar el plugin `.cs3`

CloudStream carga plugins locales desde:

```text
/sdcard/Cloudstream3/plugins/
```

### Opción A: gestor de archivos

1. Copiar `HDrama.cs3` a `Cloudstream3/plugins/` en el almacenamiento interno.
2. Si la carpeta no existe, crearla.
3. Reiniciar CloudStream (forzar detención y abrir).

### Opción B: ADB (recomendada para pruebas)

```powershell
adb shell mkdir -p /sdcard/Cloudstream3/plugins
adb push HDrama.cs3 /sdcard/Cloudstream3/plugins/HDrama.cs3

# Android 11+: permitir que CloudStream lea la carpeta local
adb shell appops set com.lagradost.cloudstream3 MANAGE_EXTERNAL_STORAGE allow

# Reiniciar la app
adb shell am force-stop com.lagradost.cloudstream3
adb shell monkey -p com.lagradost.cloudstream3 \
  -c android.intent.category.LAUNCHER 1
```

Comprobar en logs:

```powershell
adb logcat -s PluginManager PluginInstance
```

Salida esperada:

```text
Loading plugin: .../HDrama.cs3
Adding HDrama (https://es.hdrama.net/) MainAPI
Loaded plugin HDrama.cs3 successfully
```

## 3. Verificar el proveedor y las secciones

1. Abrir CloudStream.
2. En la pantalla principal, pulsar el filtro inferior derecho
   (inicialmente "Ninguno").
3. Seleccionar **HDrama**.
4. Deben aparecer las ocho secciones:
   - Destacados
   - Novedades
   - Más vistos
   - Tendencias
   - Catálogo general
   - Géneros
   - Fuentes
   - Acceso a HDrama

El plugin resuelve el Cloudflare Managed Challenge una sola vez: al cargar el
plugin lanza un warm-up en segundo plano que sondea `CookieManager` hasta
obtener `cf_clearance` (timeout 75 s en equipos lentos) y después todas las
peticiones van por OkHttp. Si el challenge no se resuelve, el catálogo cae a
los sitemaps públicos y las fichas a la navegación (ver
[`08-limitaciones.md`](08-limitaciones.md)).

Evidencia de la verificación en emulador de la versión 3 (Android 15,
CloudStream 4.9.1, plugin v3): plugin cargado, catálogo real con pósters,
búsqueda real, ficha con 125 episodios, reproducción HLS (`Rendered first
frame`) y descarga completa del episodio 1. Detalle en
[`05-pruebas.md`](05-pruebas.md).

## 4. Navegar por el catálogo real

- **Destacados** muestra la portada del sitio con pósters y notas.
- **Novedades**, **Más vistos**, **Tendencias** y **Catálogo general** están
  paginados (24 elementos por página).
- **Géneros** y **Fuentes** muestran las tarjetas del directorio del sitio; al
  abrir una se ven las series de esa categoría o fuente como recomendaciones.
- Abrir una serie muestra su ficha con póster, sinopsis, géneros, nota y la
  lista completa de episodios.
- El botón de globo de la barra superior abre la URL original en el navegador
  de Android.

## 5. Reproducir un episodio

1. Abrir una serie real y elegir un episodio de la lista.
2. El plugin consulta `/api/episode-source/<bookId>/<serial>`, descifra el
   payload y comprueba las URLs de la cadena (`SourceProbe`): cuando hay más de
   un enlace, pide el manifiesto HLS (o 1 KB del MP4) y descarta los que no
   responden. Después emite solo las URLs directas vivas, sin proxy ni
   cabeceras.
3. El reproductor de CloudStream (ExoPlayer) reproduce el HLS. En la
   verificación del 2026-11-12 se mostró `Rendered first frame` con un episodio
   real.
4. Si ninguna fuente responde, se muestra el estado "Video temporalmente no
   disponible" y se puede reintentar abriendo el episodio de nuevo (los tokens
   caducan).

## 6. Descargar un episodio

1. La primera vez, CloudStream pide elegir la carpeta de descargas (selector de
   carpetas de Android). Se recomienda `Cloudstream3` y conceder el permiso de
   acceso; solo se pregunta una vez.
2. En la ficha, pulsar el icono de descarga de un episodio (o **Descargar
   todo** para varios).
3. CloudStream resuelve el enlace `M3U8` con `loadLinks`, descarga los
   segmentos `.ts` y los concatena en un archivo con extensión `.mp4` dentro de
   la carpeta elegida (`<tipo>/<serie>/Season N Episode M - <título>.mp4`).
4. La descarga aparece en la pestaña **Descargas** de CloudStream y continúa en
   segundo plano.

Evidencia de la verificación del 2026-11-12: el episodio 1 de una serie real
quedó en
`/sdcard/Cloudstream3/AsianDramas/<serie>/Season 1 Episode 1 - Capítulo 1.mp4`
con 26.160.200 bytes, la cola de descargas terminó y el archivo (MPEG-TS con
extensión `.mp4`, comportamiento estándar de CloudStream para HLS) es
reproducible. Detalle en [`05-pruebas.md`](05-pruebas.md).

## 7. Usar el catálogo demo

La sección **Acceso a HDrama** incluye:

- **HDrama: abrir sitio oficial**: abre `es.hdrama.net` en el navegador.
- **Importar enlace de HDrama**: explica cómo pegar una URL de serie.
- **Prueba de reproducción (demo)**: serie sintética con un MP4 1080p y un HLS
  720p públicos para comprobar el reproductor. No es contenido de HDrama.

También se puede buscar `prueba` para llegar a la serie demo.

## 8. Importar una ficha de HDrama manualmente

1. Copiar una URL del tipo
   `https://es.hdrama.net/series/<slug>`.
2. Pegarla en la búsqueda de CloudStream.
3. Se abre la ficha real (si la sesión funciona) o una ficha de navegación con
   el botón de navegador.

## 9. Instalar como repositorio de CloudStream

`repo.json` del repositorio apunta a la rama `builds`:

```text
https://raw.githubusercontent.com/GeorgesGil/HDramaCloudStream/builds/repo.json
```

Para usarlo desde CloudStream (Ajustes > Extensiones > Añadir repositorio):

- El repositorio debe ser **público**: la app no envía credenciales de GitHub.
  Visibilidad actual del repositorio:
  **público**.
- La rama `builds` debe contener `repo.json`, `plugins.json` y `HDrama.cs3` de
  la versión 3. El workflow de GitHub Actions los publica en cada push a
  `main`/release cuando Actions está habilitado; también se pueden copiar a
  mano.
- Si el repositorio es privado o la rama `builds` no está actualizada, usar la
  instalación local del paso 2.

## 10. Diagnóstico por ADB

```powershell
adb logcat -s PluginManager PluginInstance CS3ExoPlayer HDrama
```

- `Loaded plugin HDrama.cs3 successfully`: plugin cargado.
- `Adding HDrama ... MainAPI`: proveedor registrado.
- `Challenge resuelto; cookie de sesion actualizada`: sesión Cloudflare lista.
- `Fuentes bookId=... serial=... resultado=N`: API de fuentes resuelta.
- `Rendered first frame`: video reproducido.
- `ErrorLoadingException`: mensaje de estado del episodio (temporalmente no
  disponible, navegador requerido, etc.).

## 11. Galaxy Z Fold8

El plugin no modifica la UI ni el player de CloudStream. La rotación, el
cambio de pantalla exterior/interior y la relación de aspecto los gestiona
CloudStream/ExoPlayer. Para verificar en el Fold8:

1. Instalar el `.cs3` con el procedimiento anterior.
2. Abrir una serie real y reproducir un episodio.
3. Girar el dispositivo y plegar/desplegar la pantalla.
4. Comprobar que el video mantiene la relación de aspecto y que el estado de
   reproducción no se pierde.

No se pudo probar en hardware Fold8 en esta sesión; queda para una sesión con
dispositivo físico. Detalle en [`08-limitaciones.md`](08-limitaciones.md).
