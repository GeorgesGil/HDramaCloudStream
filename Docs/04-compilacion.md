# Compilación (Fase 4/5)

Fecha de ejecución: 2026-11-11, America/Santo_Domingo.

## Entorno verificado

| Componente | Valor |
|---|---|
| Sistema | Windows 11 Pro x64 (build 10.0.26200) |
| JDK de compilación | Eclipse Temurin 21.0.13+9 |
| `JAVA_HOME` usado | `C:\Program Files\Eclipse Adoptium\jdk-21.0.13.9-hotspot` |
| Gradle wrapper | 8.12 |
| Android Gradle Plugin | 8.7.3 |
| Kotlin | 2.3.0 |
| `compileSdk` / `targetSdk` | 35 / 35 |
| `minSdk` | 21 |
| JVM target | 1.8 |
| Android SDK | `C:\Android` (platforms android-35, build-tools 35.0.0) |
| CloudStream de referencia | 4.9.1 / `pre-release` (2026-11-09) |
| `apiVersion` del plugin | 1 |

El `JAVA_HOME` heredado apuntaba a `C:\Program Files\Android\openjdk\jdk-21.0.8`,
que no existía. Se instaló Temurin 21.0.13 mediante `winget` y se usó
explícitamente para todas las compilaciones.

## Comandos

```powershell
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-21.0.13.9-hotspot"
$env:Path = "$env:JAVA_HOME\bin;" + $env:Path

# Desde la raíz del repositorio
.\gradlew.bat :HDrama:testDebugUnitTest
.\gradlew.bat :HDrama:make
.\gradlew.bat makePluginsJson
```

`makePluginsJson` es una tarea de la **raíz**, no del subproyecto. Ejecutar
`:HDrama:makePluginsJson` produce `Task not found`.

## Resultado

```text
> Task :HDrama:testDebugUnitTest
BUILD SUCCESSFUL

> Task :HDrama:make
Made CloudStream package at ...\HDrama\build\HDrama.cs3

> Task :makePluginsJson
Created ...\build\plugins.json

BUILD SUCCESSFUL
```

### Artefactos

| Artefacto | Ruta | Tamaño | SHA-256 |
|---|---|---|---|
| Plugin | `HDrama/build/HDrama.cs3` | 83.106 bytes | `49d93e3bc33b6ccd3a378ae5986e07ef6e1193ab561769256fa8f1f48097cfb6` |
| Índice | `build/plugins.json` | 773 bytes | — |

El `.cs3` contiene:

```text
manifest.json
classes.dex
```

Manifest generado:

```json
{"requiresResources":false,"version":1,"pluginClassName":"com.hdrama.HDramaPlugin","name":"HDrama"}
```

## Advertencias

Durante `compileDex`, D8 (AGP 8.7.3) emite advertencias de metadata Kotlin al
procesar clases compiladas con Kotlin 2.3.0:

```text
WARNING: D8: An error occurred when parsing kotlin metadata...
Compiled dex to ...\classes.dex
```

No son errores: el DEX se genera y el plugin carga correctamente en
CloudStream 4.9.1 (verificado en emulador). Se documenta para futuras
actualizaciones de AGP/Kotlin.

## Reproducibilidad

- El wrapper fija Gradle 8.12 con checksum en `gradle-wrapper.properties`.
- Las versiones de AGP, Kotlin, SDK y API están fijadas en
  `build.gradle.kts`/`HDrama/build.gradle.kts`.
- `cloudstream("com.lagradost:cloudstream3:pre-release")` descarga `classes.jar`
  del release pre-release oficial. Para builds reproducibles a largo plazo se
  recomienda fijar un tag concreto en lugar de `pre-release`.
- GitHub Actions está deshabilitado en el repositorio, por lo que no hay
  compilación remota. El workflow queda incluido pero no se ejecutó.
