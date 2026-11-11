# Investigación: API y ecosistema de CloudStream

Fecha: 2026-11-11. Fuentes primarias consultadas en sus commits más recientes.

## 1. Versiones observadas

| Componente | Versión / referencia |
|---|---|
| CloudStream estable | `v4.9.1` (2026-11-09) |
| CloudStream pre-release | etiqueta `pre-release` (2026-11-09) |
| API de plugins | `apiVersion = 1` (`CloudstreamExtension`) |
| Plantilla de plugins | `recloudstream/TestPlugins` @ `d87d65a` (2025-05-29) |
| Extensiones oficiales | `recloudstream/extensions` @ `cf16b3b` (2026-05-23) |
| Gradle plugin | `com.github.recloudstream:gradle:-SNAPSHOT` |
| Gradle wrapper de plantilla | 8.12 |
| AGP de plantilla | 8.7.3 |
| Kotlin de plantilla | 2.1.0 (extensiones oficiales usan 2.3.0) |
| `compileSdk` / `targetSdk` | 35 / 35 |
| `minSdk` | 21 |
| JVM target | 1.8 (obligatorio para plugins) |

> `recloudstream/plugin-template` ya no está disponible públicamente. La
> plantilla oficial accesible es `TestPlugins`. Las extensiones oficiales
> actuales usan una configuración más nueva (`library:-SNAPSHOT`), que se
> tomó como referencia para dependencias.

## 2. Punto de entrada del plugin

Un plugin es una clase Kotlin anotada con `@CloudstreamPlugin` que hereda de
`Plugin` (o `BasePlugin`). El cargador de CloudStream detecta exactamente una
clase anotada por proyecto.

```kotlin
@CloudstreamPlugin
class HDramaPlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(HDramaProvider())
    }
}
```

- `Plugin.load(context)` recibe el `Context` de Android.
- `registerMainAPI(element)` añade el proveedor a `APIHolder`.
- `BasePlugin` solo expone `registerMainAPI`/`registerExtractorAPI`; `Plugin`
  añade integración con UI de ajustes y `Context`.
- El manifest del `.cs3` guarda `name`, `pluginClassName`, `version` y
  `requiresResources`.

## 3. `MainAPI`: métodos y modelos vigentes

`MainAPI` es una clase abstracta con métodos abiertos. Los relevantes:

```kotlin
open suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse?
open suspend fun search(query: String, page: Int): SearchResponseList?
open suspend fun search(query: String): List<SearchResponse>?
open suspend fun load(url: String): LoadResponse?
open suspend fun loadLinks(
    data: String,
    isCasting: Boolean,
    subtitleCallback: (SubtitleFile) -> Unit,
    callback: (ExtractorLink) -> Unit
): Boolean
```

Propiedades clave:

- `name`, `mainUrl`, `lang` (BCP 47; `"es"` para español).
- `hasMainPage`, `hasQuickSearch`, `sequentialMainPage`.
- `supportedTypes` (`TvType.AsianDrama`, `TvType.TvSeries`, etc.).
- `mainPage: List<MainPageData>` para definir las secciones.
- `loadLinksTimeoutMs`, `searchTimeoutMs`, etc.

### Constructores recomendados (API nueva)

La API migró de constructores directos a funciones factoría. Usar las nuevas
evita `DeprecationLevel.ERROR`:

- `newHomePageResponse(...)`
- `newSearchResponseList(...)`
- `newTvSeriesSearchResponse(...)`, `newMovieSearchResponse(...)`
- `newTvSeriesLoadResponse(...)`, `newMovieLoadResponse(...)`
- `newEpisode(url) { ... }`
- `newExtractorLink(source, name, url, type) { ... }`
- `newSubtitleFile(lang, url) { ... }`
- `Score.from10(...)`, `Score.from100(...)`, `Score.from(...)`.

`SearchResponse` es una interfaz con `name`, `url`, `apiName`, `type`,
`posterUrl`, `posterHeaders`, `id`, `quality`, `score`. Las clases concretas
(`TvSeriesSearchResponse`, `MovieSearchResponse`, etc.) ya no deben construirse
directamente.

### Secciones de la pantalla principal

```kotlin
override val mainPage = mainPageOf(
    "novedades" to "Novedades",
    "mas-vistos" to "Más vistos",
    // ...
)

override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
    // request.data contiene la clave de la sección
    return newHomePageResponse(request.name, items, hasNext)
}
```

`MainPageData(name, data, horizontalImages)` permite controlar si las tarjetas
se muestran horizontales o verticales.

### Reproductores y enlaces

`ExtractorLink` describe una fuente reproducible. Campos importantes:

- `source`: nombre mostrado (por ejemplo `HDrama`).
- `name`: etiqueta de calidad/servidor.
- `url`: URL del MP4, HLS (`M3U8`), DASH, etc.
- `type`: `VIDEO`, `M3U8`, `DASH`, `TORRENT`, `MAGNET`.
- `referer`, `headers`, `quality`, `audioTracks`.

CloudStream incorpora una acción **"Play in browser"** que abre con
`Intent.ACTION_VIEW` cualquier enlace de video (tipos `VIDEO`, `DASH`, `M3U8`).
Además, la pantalla de resultado muestra un botón para abrir en navegador la
URL del `LoadResponse` cuando empieza por `http`. Esto permite ofrecer una
alternativa de navegador sin implementar Intents propios ni pedir permisos.

No existe un tipo de enlace "navegador" específico; se usa un enlace normal y
la acción de navegador del reproductor de CloudStream.

## 4. Empaquetado `.cs3`

Tareas del plugin Gradle (`com.lagradost.cloudstream3.gradle`):

| Tarea | Resultado |
|---|---|
| `make` | ZIP `.cs3` con `manifest.json` + `classes.dex` (+ recursos si `requiresResources`) |
| `makePluginsJson` | `build/plugins.json` con las entradas de todos los plugins |
| `ensureJarCompatibility` | Comprueba JAR multiplataforma (solo `isCrossPlatform`) |
| `deployWithAdb` | Instala el `.cs3` en un dispositivo conectado |

El `.cs3` **no es un APK**: es un ZIP con DEX y un manifest propio. El nombre
del archivo debe ser `<NombreDelProyecto>.cs3`; el proyecto Gradle debe
llamarse igual que el plugin que se quiere mostrar.

Ejemplo de manifest generado:

```json
{
  "name": "HDrama",
  "pluginClassName": "com.hdrama.HDramaPlugin",
  "version": 1,
  "requiresResources": false
}
```

## 5. Repositorios de plugins

Formato `repo.json`:

```json
{
  "name": "HDramaCloudStream",
  "description": "Extensión HDrama para CloudStream",
  "manifestVersion": 1,
  "pluginLists": [
    "https://raw.githubusercontent.com/<usuario>/<repo>/builds/plugins.json"
  ]
}
```

`plugins.json` lo genera `makePluginsJson` a partir de los `plugin-entry.json`
de cada plugin. Incluye URL del `.cs3`, versión, autores, estado, tamaño,
hash, tipos de TV, idioma, icono y `apiVersion`.

### Limitación crítica del repositorio privado

CloudStream descarga `repo.json`, `plugins.json` y `.cs3` por HTTP sin
credenciales. Un repositorio **privado** de GitHub no permite esas descargas
anónimas:

- `raw.githubusercontent.com` devuelve 404 para repos privados sin token.
- CloudStream no dispone de un mecanismo de autenticación para repositorios.
- GitHub Pages en repos privados requiere plan de pago y no sirve para este
  caso sin configuración adicional.

Por tanto:

1. El `.cs3` se entregará como archivo local para instalación manual/ADB.
2. `repo.json` y `plugins.json` se generarán y se subirán a la rama `builds`
   como evidencia y para uso futuro.
3. Para publicar como repositorio instalable por URL, el repositorio deberá
   hacerse público (o alojar los artefactos en un servidor público) y
   habilitar Actions o publicar manualmente. Esto se documentará como
   decisión pendiente del propietario.

## 6. Funcionalidades nativas de CloudStream

Se heredan automáticamente si el proveedor devuelve modelos correctos:

- Favoritos, historial y "continuar viendo".
- Seguimiento de progreso y cambio de episodio.
- Selección de calidad entre varios `ExtractorLink`.
- Subtítulos si se emiten `SubtitleFile`.
- Pantalla completa, rotación y relaciones de aspecto (ExoPlayer).
- Apertura en navegador de la URL de la ficha.
- "Play in browser" para un enlace concreto.

No es necesario reimplementar estas funciones. El plugin solo debe entregar
metadatos, episodios y enlaces válidos. La compatibilidad con pantallas
plegables y 9:16 depende del player de CloudStream; el plugin no modifica la
UI de la app.

## 7. Dependencias y compilación

Configuración de referencia (plantilla):

```kotlin
buildscript {
    dependencies {
        classpath("com.android.tools.build:gradle:8.7.3")
        classpath("com.github.recloudstream:gradle:-SNAPSHOT")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.1.0")
    }
}
```

Dependencias típicas del plugin:

```kotlin
cloudstream("com.lagradost:cloudstream3:pre-release") // stubs de la API
implementation("com.github.Blatzar:NiceHttp:0.4.11")
implementation("org.jsoup:jsoup:1.18.3")
implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.13.1")
```

La configuración `cloudstream` descarga `classes.jar` del release
`pre-release` de CloudStream y lo añade como `compileOnly`. No se debe subir
Jackson por encima de 2.13.1 por compatibilidad con Android antiguo.

## 8. Referencias

- https://github.com/recloudstream/cloudstream
- https://github.com/recloudstream/TestPlugins
- https://github.com/recloudstream/extensions
- https://github.com/recloudstream/csdocs
- https://github.com/recloudstream/gradle
- https://recloudstream.github.io/csdocs/devs/gettingstarted.html
- https://recloudstream.github.io/csdocs/devs/create-your-own-json-repository.html
- https://recloudstream.github.io/csdocs/devs/using-plugin-template.html
