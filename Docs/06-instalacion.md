# Instalación en Android (Fase 6)

Fecha: 2026-11-11. Procedimiento verificado en emulador Android 15 (API 35)
con CloudStream 4.9.1. El dispositivo objetivo final es un Samsung Galaxy Z
Fold8 con Android 17/One UI 9; no se dispone de él en esta sesión.

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

## 3. Verificar el proveedor

1. Abrir CloudStream.
2. En la pantalla principal, pulsar el filtro inferior derecho
   (inicialmente "Ninguno").
3. Seleccionar **HDrama**.
4. Deben aparecer las secciones:
   - Novedades
   - Más vistos
   - Nuevos esta semana
   - Catálogo general
   - Géneros
   - Recomendaciones
   - Acceso a HDrama

La primera carga descarga el índice de sitemaps públicos (unos pocos KB) y
puede tardar unos segundos.

## 4. Navegar por el catálogo real

- Las tarjetas muestran títulos derivados del `slug` del sitemap.
- No hay portadas ni sinopsis porque el HTML de las fichas está protegido por
  Cloudflare.
- Abrir una tarjeta muestra una ficha mínima con un episodio
  "Abrir en el navegador".
- El botón de globo de la barra superior abre la URL original en el navegador
  de Android.

## 5. Probar reproducción (modo demo)

1. Usar la búsqueda e introducir `prueba`.
2. Abrir **Prueba de reproducción (demo)**.
3. Seleccionar el episodio 1 (MP4 1080p) o el 2 (HLS 720p).
4. El reproductor integrado de CloudStream debe mostrar el video con
   letterboxing, sin estiramiento.
5. Los episodios 1 y 2 usan videos públicos de prueba; no son contenido de
   HDrama.

## 6. Importar una ficha de HDrama manualmente

1. Copiar una URL del tipo
   `https://es.hdrama.net/series/<slug>`.
2. Pegarla en la búsqueda de CloudStream.
3. Se creará una ficha de navegación sin descargar HTML.
4. Abrir con el botón de navegador.

## 7. Diagnóstico por ADB

```powershell
adb logcat -s PluginManager PluginInstance CS3ExoPlayer HDrama
```

- `Loaded plugin HDrama.cs3 successfully`: plugin cargado.
- `Adding HDrama ... MainAPI`: proveedor registrado.
- `Rendered first frame`: video reproducido.
- `ErrorLoadingException`: mensaje de estado del episodio.

## 8. Limitaciones del repositorio privado

El repositorio es privado y Actions está deshabilitado. CloudStream no puede
descargar `repo.json`/`plugins.json`/`.cs3` desde un repositorio privado porque
no envía credenciales. Por eso la instalación es **local** (archivo o ADB).

Para publicar como repositorio instalable por URL haría falta:

1. Hacer público el repositorio o alojar los artefactos en un servidor
   público.
2. Publicar `repo.json` y `builds/plugins.json` accesibles por HTTPS.
3. Habilitar Actions o subir los artefactos manualmente a la rama `builds`.

## 9. Galaxy Z Fold8

El plugin no modifica la UI ni el player de CloudStream. La rotación, el
cambio de pantalla exterior/interior y la relación de aspecto los gestiona
CloudStream/ExoPlayer. Para verificar en el Fold8:

1. Instalar el `.cs3` con el procedimiento anterior.
2. Abrir un video demo.
3. Girar el dispositivo y plegar/desplegar la pantalla.
4. Comprobar que el video mantiene la relación de aspecto y que el estado de
   reproducción no se pierde.

No se pudo probar en hardware Fold8 en esta sesión; queda como verificación
pendiente.
