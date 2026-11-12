package com.hdrama.network

import android.content.Context

/**
 * Contexto de aplicacion capturado en [com.hdrama.HDramaPlugin.load].
 *
 * El plugin no tiene Activity propia; para crear WebViews que resuelvan el
 * challenge de Cloudflare se necesita el contexto de la app anfitriona.
 */
object HdramaRuntime {
    @Volatile
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext ?: context
    }

    fun contextOrNull(): Context? = appContext

    fun requireContext(): Context = appContext
        ?: error("HDrama: el contexto de la app no se inicializo")
}

/**
 * Crea el fetcher adecuado para el entorno actual.
 *
 * - **Android**: [HdramaHttpFetcher] con una [WebViewCloudflareSession]
 *   compartida y [WebViewFetcher] como respaldo. Todas las peticiones van por
 *   OkHttp; el WebView solo se abre una vez para obtener `cf_clearance`.
 * - **JVM sin contexto** (pruebas): [CloudstreamWebFetcher], que no necesita
 *   WebView propio porque CloudStream aporta `CloudflareKiller`.
 *
 * Se devuelve una unica instancia por proceso: la sesion de Cloudflare y las
 * cookies deben compartirse entre el catalogo y la API de fuentes.
 */
object WebFetcherFactory {

    @Volatile
    private var shared: WebFetcher? = null

    fun create(): WebFetcher = shared ?: synchronized(this) {
        shared ?: build().also { shared = it }
    }

    /**
     * Resuelve el challenge de Cloudflare en segundo plano (si el fetcher es el
     * de OkHttp). Se llama al cargar el plugin para adelantar trabajo.
     */
    suspend fun warmUp(): Boolean = (create() as? HdramaHttpFetcher)?.warmUp() ?: false

    /** Solo para pruebas: olvida la instancia compartida. */
    internal fun resetForTests() {
        shared = null
    }

    private fun build(): WebFetcher {
        val context = HdramaRuntime.contextOrNull() ?: return CloudstreamWebFetcher()
        return HdramaHttpFetcher(
            session = WebViewCloudflareSession(context),
            fallback = WebViewFetcher(context),
        )
    }
}

/**
 * Crea la sonda de disponibilidad de las URLs de video.
 *
 * En Android usa OkHttp con un timeout corto; en JVM (pruebas) devuelve
 * [com.hdrama.sources.NoSourceProbe] para no hacer red.
 */
object SourceProbeFactory {
    fun create(): com.hdrama.sources.SourceProbe {
        if (HdramaRuntime.contextOrNull() == null) {
            return com.hdrama.sources.NoSourceProbe
        }
        return com.hdrama.sources.HttpSourceProbe(CloudstreamHttpTransport())
    }
}
