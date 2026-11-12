package com.hdrama

import android.content.Context
import com.hdrama.network.HdramaRuntime
import com.hdrama.network.WebFetcherFactory
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Punto de entrada del plugin HDrama.
 *
 * CloudStream detecta exactamente una clase anotada con [CloudstreamPlugin]
 * por proyecto y llama a [load] cuando el plugin se activa.
 */
@CloudstreamPlugin
class HDramaPlugin : Plugin() {

    private val warmUpScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun load(context: Context) {
        // El fetcher basado en WebView necesita el contexto de la app.
        HdramaRuntime.init(context)
        registerMainAPI(HDramaProvider())

        // El challenge de Cloudflare se resuelve en segundo plano nada mas
        // cargar el plugin: asi el primer catalogo y la primera reproduccion no
        // tienen que esperar al WebView.
        warmUpScope.launch {
            runCatching { WebFetcherFactory.warmUp() }
        }
    }
}
