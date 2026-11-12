package com.hdrama

import android.content.Context
import com.hdrama.network.HdramaRuntime
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

/**
 * Punto de entrada del plugin HDrama.
 *
 * CloudStream detecta exactamente una clase anotada con [CloudstreamPlugin]
 * por proyecto y llama a [load] cuando el plugin se activa.
 */
@CloudstreamPlugin
class HDramaPlugin : Plugin() {
    override fun load(context: Context) {
        // El fetcher basado en WebView necesita el contexto de la app.
        HdramaRuntime.init(context)
        registerMainAPI(HDramaProvider())
    }
}
