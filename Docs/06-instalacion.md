# Instalación en Android (versión 2)

Fecha: 2026-11-11. Procedimiento de instalación del plugin **v2** en Android
con CloudStream 4.9.1. La instalación local del `.cs3` es la vía verificada;
la instalación como repositorio depende de que el repositorio sea público y de
que la rama `builds` tenga los artefactos de la versión 2.

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

La primera petición puede tardar mientras el WebView resuelve el
Managed Challenge en el WebView. Si la sesión no se resuelve, el catálogo cae
a los sitemaps públicos y las fichas a la navegación (ver
[`08-limitaciones.md`](08-limitaciones.md)).

Evidencia de la verificación en emulador de la versión 2:
Verificación parcial en emulador Android 14 (AOSP API 34): plugin cargado, catálogo real y ficha con episodios; la reproducción HLS y los pósters dependen de los hosts del sitio.

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
   payload y emite el HLS directo; además ofrece un espejo con `Referer` como
   segunda opción.
3. El reproductor de CloudStream (ExoPlayer) reproduce el HLS. Si el host
   directo falla, el player prueba el espejo.
4. Si ninguna fuente responde, se muestra el estado "Video temporalmente no
   disponible" y se puede reintentar abriendo el episodio de nuevo (los tokens
   caducan).

## 6. Usar el catálogo demo

La sección **Acceso a HDrama** incluye:

- **HDrama: abrir sitio oficial**: abre `es.hdrama.net` en el navegador.
- **Importar enlace de HDrama**: explica cómo pegar una URL de serie.
- **Prueba de reproducción (demo)**: serie sintética con un MP4 1080p y un HLS
  720p públicos para comprobar el reproductor. No es contenido de HDrama.

También se puede buscar `prueba` para llegar a la serie demo.

## 7. Importar una ficha de HDrama manualmente

1. Copiar una URL del tipo
   `https://es.hdrama.net/series/<slug>`.
2. Pegarla en la búsqueda de CloudStream.
3. Se abre la ficha real (si la sesión funciona) o una ficha de navegación con
   el botón de navegador.

## 8. Instalar como repositorio de CloudStream

`repo.json` del repositorio apunta a la rama `builds`:

```text
https://raw.githubusercontent.com/GeorgesGil/HDramaCloudStream/builds/repo.json
```

Para usarlo desde CloudStream (Ajustes > Extensiones > Añadir repositorio):

- El repositorio debe ser **público**: la app no envía credenciales de GitHub.
  Visibilidad actual del repositorio:
  **público**.
- La rama `builds` debe contener `repo.json`, `plugins.json` y `HDrama.cs3` de
  la versión 2. El workflow de GitHub Actions los publica en cada push a
  `main`/release cuando Actions está habilitado; también se pueden copiar a
  mano.
- Si el repositorio es privado o la rama `builds` no está actualizada, usar la
  instalación local del paso 2.

## 9. Diagnóstico por ADB

```powershell
adb logcat -s PluginManager PluginInstance CS3ExoPlayer HDrama
```

- `Loaded plugin HDrama.cs3 successfully`: plugin cargado.
- `Adding HDrama ... MainAPI`: proveedor registrado.
- `Rendered first frame`: video reproducido.
- `ErrorLoadingException`: mensaje de estado del episodio (temporalmente no
  disponible, navegador requerido, etc.).

## 10. Galaxy Z Fold8

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
