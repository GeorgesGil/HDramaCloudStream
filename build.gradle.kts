import com.android.build.gradle.BaseExtension
import com.lagradost.cloudstream3.gradle.CloudstreamExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

buildscript {
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }

    dependencies {
        classpath("com.android.tools.build:gradle:8.7.3")
        classpath("com.github.recloudstream:gradle:-SNAPSHOT")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.3.0")
    }
}

allprojects {
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
        maven("https://oss.sonatype.org/content/repositories/snapshots")
    }
}

fun Project.cloudstream(configuration: CloudstreamExtension.() -> Unit) =
    extensions.getByName<CloudstreamExtension>("cloudstream").configuration()

fun Project.android(configuration: BaseExtension.() -> Unit) =
    extensions.getByName<BaseExtension>("android").configuration()

subprojects {
    apply(plugin = "com.android.library")
    apply(plugin = "kotlin-android")
    apply(plugin = "com.lagradost.cloudstream3.gradle")

    cloudstream {
        // El repositorio es privado y Actions esta deshabilitado; esta URL queda
        // como metadato y para una futura publicacion en la rama builds.
        setRepo("GeorgesGil", "HDramaCloudStream", "github")
        buildBranch = "builds"
    }

    android {
        namespace = "com.hdrama"

        compileSdkVersion(35)

        defaultConfig {
            minSdk = 21
            targetSdkVersion(35)
        }

        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_1_8
            targetCompatibility = JavaVersion.VERSION_1_8
        }

        testOptions {
            unitTests.isReturnDefaultValues = true
        }

        tasks.withType<KotlinJvmCompile> {
            compilerOptions {
                jvmTarget.set(JvmTarget.JVM_1_8)
                freeCompilerArgs.addAll(
                    "-Xno-call-assertions",
                    "-Xno-param-assertions",
                    "-Xno-receiver-assertions"
                )
            }
        }
    }

    dependencies {
        val cloudstream by configurations

        // Stubs de la API de CloudStream (incluye la clase Plugin de Android).
        cloudstream("com.lagradost:cloudstream3:pre-release")
        add("implementation", "org.jsoup:jsoup:1.18.3")
        add("implementation", "org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
        // Libreria HTTP que usa la propia app (app.get/NiceResponse) y que
        // aporta okhttp3, necesario para los interceptores de Cloudflare.
        add("implementation", "com.github.Blatzar:NiceHttp:0.4.11")

        add("testImplementation", "junit:junit:4.13.2")
        add("testImplementation", "org.jetbrains.kotlin:kotlin-test:2.3.0")
        // En Android org.json lo aporta el framework; en pruebas JVM hace falta
        // una implementacion real (el android.jar de pruebas devuelve defaults).
        add("testImplementation", "org.json:json:20240303")
    }
}

task<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}
