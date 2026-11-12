# Compilación (versión 2)

Fecha de ejecución: 2026-11-11, America/Santo_Domingo.

Este documento recoge el entorno y los comandos con los que se compila y prueba
el plugin **v2** (`HDrama/build.gradle.kts` fija `version = 2`). El build se
ejecuta en Windows con el JDK que trae Android Studio y una caché de Gradle
dentro del propio workspace.

## 1. Entorno de compilación

| Componente | Valor |
|---|---|
| Sistema | Windows 11 Pro x64 (build 10.0.26200) |
| JDK | Android Studio JBR 21.0.6 (JetBrains) |
| `JAVA_HOME` | `C:\Program Files\Android\Android Studio\jbr` |
| `ANDROID_HOME` | `C:\Users\elbac\AppData\Local\Android\Sdk` |
| Plataformas SDK | `android-33`, `android-34`, `android-35`, `android-36` |
| Build tools | `35.0.0`, `35.0.1`, `36.0.0` |
| `GRADLE_USER_HOME` | `<raíz del repo>\.gradle-home` |
| Gradle wrapper | 8.12 |
| Android Gradle Plugin | 8.7.3 |
| Kotlin | 2.3.0 |
| `compileSdk` / `targetSdk` | 35 / 35 |
| `minSdk` | 21 |
| JVM target | 1.8 |
| CloudStream de referencia | `pre-release` (2026-11-09, probado con 4.9.1) |
| `apiVersion` del plugin | 1 |
| Versión del plugin | 2 |

`GRADLE_USER_HOME` **debe** apuntar a un directorio dentro del workspace: el
entorno de ejecución del agente bloquea la escritura en
`C:\Users\<usuario>\.gradle`. La caché `.gradle-home/` está en `.gitignore`.

### Dependencias del build

- `com.lagradost:cloudstream3:pre-release`: API de plugins de CloudStream.
- `org.jsoup:jsoup:1.18.3`: parseo de HTML.
- `org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2`.
- `com.github.Blatzar:NiceHttp:0.4.11`: cliente HTTP que usa la propia app
  (`app.get`, `NiceResponse`) y que aporta `okhttp3` para los interceptores de
  Cloudflare (`WebViewFetcher`; `CloudflareKiller` como respaldo).
- `org.json:json:20240303` (solo pruebas): en Android `org.json` lo aporta el
  framework, pero en las pruebas JVM el `android.jar` devuelve valores por
  defecto.

`gradle.properties` fija `kotlin.compiler.execution.strategy=in-process`: el
daemon de Kotlin no arranca en algunos entornos de CI o areneros.

## 2. Bloqueo de compilación

Solo puede haber una compilación a la vez en el workspace. Antes de ejecutar
Gradle se crea el directorio `.build-lock` de forma atómica; si ya existe, se
espera y se reintenta (máximo 10 minutos). Al terminar se elimina:

```powershell
New-Item -ItemType Directory .build-lock
try {
    # comandos de Gradle
} finally {
    Remove-Item .build-lock -Recurse -Force
}
```

## 3. Comandos

```powershell
# Desde la raíz del repositorio
$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"
$env:ANDROID_HOME="C:\Users\elbac\AppData\Local\Android\Sdk"
$env:GRADLE_USER_HOME="C:\Users\elbac\Desktop\Georges\AI\HDramaCloudStream\.gradle-home"

# Pruebas unitarias JVM
.\gradlew.bat :HDrama:testDebugUnitTest --console=plain

# Paquete .cs3
.\gradlew.bat :HDrama:make

# Índice de plugins (tarea de la raíz, no del subproyecto)
.\gradlew.bat makePluginsJson
```

`makePluginsJson` es una tarea de la **raíz**: `:HDrama:makePluginsJson`
produce `Task not found`. En CI los tres comandos se ejecutan con
`--console=plain` dentro del workflow.

## 4. Resultado

```text
> Task :HDrama:testDebugUnitTest
BUILD SUCCESSFUL

> Task :HDrama:make
Made CloudStream package at ...\HDrama\build\HDrama.cs3

> Task :makePluginsJson
Created ...\build\plugins.json

BUILD SUCCESSFUL
```

Total de pruebas de la suite: **98 pruebas unitarias en 16 suites**
(0 fallos esperados; ver [`05-pruebas.md`](05-pruebas.md)).

### Artefactos

| Artefacto | Ruta | Tamaño | SHA-256 |
|---|---|---|---|
| Plugin | `HDrama/build/HDrama.cs3` | 123.299 bytes (123 KB) | 7fbcccb77000ab15ccaa81605b5bc4e11762c9777f83d0f2e62101152e1394e4 |
| Índice | `build/plugins.json` | 761 bytes | — |

El `.cs3` contiene:

```text
manifest.json
classes.dex
```

El `manifest.json` generado declara `version: 2`, `name: "HDrama"` y
`pluginClassName: "com.hdrama.HDramaPlugin"`.

## 5. Advertencias

Durante `compileDex`, D8 (AGP 8.7.3) emite advertencias de metadata Kotlin al
procesar clases compiladas con Kotlin 2.3.0:

```text
WARNING: D8: An error occurred when parsing kotlin metadata...
Compiled dex to ...\classes.dex
```

No son errores: el DEX se genera y el plugin carga en CloudStream 4.9.1. Se
documentan para futuras actualizaciones de AGP/Kotlin (ver
[`08-limitaciones.md`](08-limitaciones.md)).

## 6. Reproducibilidad

- El wrapper fija Gradle 8.12 con checksum en `gradle-wrapper.properties`.
- AGP, Kotlin, SDK y versión del plugin están fijados en `build.gradle.kts` y
  `HDrama/build.gradle.kts`.
- `cloudstream("com.lagradost:cloudstream3:pre-release")` descarga la API del
  release pre-release oficial; para builds reproducibles a largo plazo conviene
  fijar un tag concreto.
- `com.github.Blatzar:NiceHttp:0.4.11` se resuelve desde JitPack; la primera
  resolución necesita red.
- La caché `.gradle-home/` es local y no se versiona; en CI se usa la caché de
  `gradle/actions/setup-gradle`.

## 7. Integración continua

El workflow [`.github/workflows/build.yml`](../.github/workflows/build.yml)
ejecuta las pruebas, genera `HDrama.cs3` y `plugins.json`, calcula el SHA-256,
sube los artefactos y publica `HDrama.cs3`, `plugins.json`, `repo.json` y
`BUILDS.md` en la rama `builds`; en un evento `release` adjunta el `.cs3` al
release.

La ejecución remota requiere que **GitHub Actions esté habilitado** en el
repositorio. Estado en la verificación local:
Las GitHub Actions están habilitadas: el workflow \Build plugin\ ejecuta pruebas, compila el .cs3, publica la rama \uilds\ y adjunta el artefacto en cada release.
La compilación local con los comandos de este documento es la vía de entrega
verificada.
