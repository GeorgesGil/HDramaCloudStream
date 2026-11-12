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

/** Crea el fetcher adecuado para el entorno actual. */
object WebFetcherFactory {
    /**
     * En Android usa [WebViewFetcher] (el challenge de Cloudflare de HDrama
     * rechaza peticiones OkHttp aunque lleven cf_clearance). En pruebas JVM sin
     * contexto devuelve [CloudstreamWebFetcher] para no romper la construccion.
     */
    fun create(): WebFetcher {
        val context = HdramaRuntime.contextOrNull() ?: return CloudstreamWebFetcher()
        return WebViewFetcher(context)
    }
}
