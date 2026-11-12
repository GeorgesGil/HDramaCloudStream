# Compilación (versión 3)

Fecha de ejecución: 2026-11-12, America/Santo_Domingo.

Este documento recoge el entorno y los comandos con los que se compila y prueba
el plugin **v3** (`HDrama/build.gradle.kts` fija `version = 3`). El build se
ejecuta en Windows con Eclipse Adoptium Temurin 21.0.13+9 y el SDK de Android en
`C:\Android`.

## 1. Entorno de compilación

| Componente | Valor |
|---|---|
| Sistema | Windows 11 Pro x64 (build 10.0.26200) |
| JDK | Eclipse Adoptium Temurin 21.0.13+9 (build 21.0.13+9-LTS) |
| `JAVA_HOME` | `C:\Program Files\Eclipse Adoptium\jdk-21.0.13.9-hotspot` |
| `ANDROID_HOME` | `C:\Android` |
| `sdk.dir` (`local.properties`) | `C:\Android` |
| Plataformas SDK | `android-34`, `android-35`, `android-36`, `android-36.1` (y antiguas `android-14`, `android-15`, `android-21`, `android-22`) |
| Build tools | `34.0.0`, `35.0.0`, `36.0.0` |
| `GRADLE_USER_HOME` | `<raíz del repo>\.gradle-home` |
| Gradle wrapper | 8.12 |
| Android Gradle Plugin | 8.7.3 |
| Kotlin | 2.3.0 |
| `compileSdk` / `targetSdk` | 35 / 35 |
| `minSdk` | 21 |
| JVM target | 1.8 |
| CloudStream de referencia | `pre-release` (2026-11-09, probado con 4.9.1) |
| `apiVersion` del plugin | 1 |
| Versión del plugin | 3 |

El `JAVA_HOME` heredado del equipo puede apuntar a una ruta inexistente
(`C:\Program Files\Android\openjdk\jdk-21.0.8`); por eso cada sesión fija el JDK
de Adoptium de forma explícita antes de compilar.

En las compilaciones verificadas se usó una caché de Gradle dentro del
workspace (`<raíz del repo>\.gradle-home`), que está en `.gitignore`; también
vale la caché global del usuario. Si el entorno bloquea la escritura en
`C:\Users\<usuario>\.gradle`, basta con fijar `GRADLE_USER_HOME` dentro del
workspace como en los comandos de la sección 3.

### Dependencias del build

- `com.lagradost:cloudstream3:pre-release`: API de plugins de CloudStream.
- `org.jsoup:jsoup:1.18.3`: parseo de HTML.
- `org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2`.
- `com.github.Blatzar:NiceHttp:0.4.11`: cliente HTTP que usa la propia app
  (`app.get`, `NiceResponse`) y que aporta `okhttp3`; base del fetcher principal
  `HdramaHttpFetcher` y del respaldo `CloudstreamWebFetcher`
  (`CloudflareKiller`).
- `org.json:json:20240303` (solo pruebas): en Android `org.json` lo aporta el
  framework, pero en las pruebas JVM el `android.jar` devuelve valores por
  defecto.
- `junit:junit:4.13.2` y `org.jetbrains.kotlin:kotlin-test:2.3.0` (pruebas).

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
$env:JAVA_HOME="C:\Program Files\Eclipse Adoptium\jdk-21.0.13.9-hotspot"
$env:ANDROID_HOME="C:\Android"
$env:GRADLE_USER_HOME="$PWD\.gradle-home"  # opcional: caché dentro del repo

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

Total de pruebas de la suite: **117 pruebas unitarias en
18 suites** (0 fallos esperados; ver
[`05-pruebas.md`](05-pruebas.md)).

### Artefactos

| Artefacto | Ruta | Tamaño | SHA-256 |
|---|---|---|---|
| Plugin | `HDrama/build/HDrama.cs3` | 143.227 bytes (140 KB) | be21cdb8464df6d44c10929d86ff16830b2fcdf5ef8498cfe99c9fcf3904fc8d |
| Índice | `build/plugins.json` | — | — |

El `.cs3` contiene:

```text
manifest.json
classes.dex
```

El `manifest.json` generado declara `version: 3`, `name: "HDrama"` y
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

- El wrapper fija Gradle 8.12 (`distributionUrl` en
  `gradle-wrapper.properties`).
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
repositorio. Estado en la verificación local: las GitHub Actions están
habilitadas y el workflow `Build plugin` ejecuta pruebas, compila el `.cs3`,
publica la rama `builds` y adjunta el artefacto en cada release. La compilación
local con los comandos de este documento es la vía de entrega verificada.
