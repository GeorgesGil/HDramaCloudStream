package com.hdrama.network

import android.util.Log
import com.hdrama.utils.Diagnostics
import com.hdrama.utils.UrlUtils
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.network.CloudflareKiller
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.cancellation.CancellationException

/**
 * Fetcher de produccion: usa la app HTTP de CloudStream con un
 * [CloudflareKiller] como interceptor.
 *
 * CloudflareKiller abre un WebView, resuelve el challenge y reutiliza las
 * cookies resultantes. Su `init` borra las cookies guardadas, por eso se
 * instancia UNA sola vez por fetcher. Nunca se registran cookies ni cabeceras
 * de sesion.
 *
 * CloudStream pide varias secciones de la portada a la vez, y CloudflareKiller
 * crea un WebView por peticion concurrente; por eso las peticiones se serializan
 * con un [Mutex] y cada una tiene un limite de tiempo. Si alguna pagina sigue
 * devolviendo 403 (cookie no aceptada para esa ruta), se recrea el
 * CloudflareKiller para forzar un challenge nuevo y se reintenta una vez. Si no
 * se resuelve, el adaptador cae al catalogo de sitemaps.
 *
 * `cacheTime = 0` desactiva la cache interna de NiceHttp: el repositorio del
 * plugin gestiona su propia cache TTL y el HTML/API debe venir siempre de red.
 */
class CloudstreamWebFetcher : WebFetcher {

    private var cloudflare = CloudflareKiller()
    private val mutex = Mutex()

    override suspend fun fetch(url: String, headers: Map<String, String>): String {
        val target = HdramaFetchAllowlist.resolve(url)
        return mutex.withLock { requestWithRetry(target, headers) }
    }

    private suspend fun requestWithRetry(
        target: String,
        headers: Map<String, String>,
    ): String {
        var attempt = 0
        while (true) {
            val response = try {
                withTimeoutOrNull(FETCH_TIMEOUT_MS) {
                    app.get(
                        target,
                        headers = headers,
                        interceptor = cloudflare,
                        cacheTime = 0,
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                Log.w(
                    TAG,
                    "Fallo de red en ${UrlUtils.redact(target)}: " +
                        (error::class.simpleName ?: "Error"),
                )
                throw WebFetchException(
                    code = WebFetchException.NO_HTTP_CODE,
                    message = "No se pudo completar la peticion a HDrama: " +
                        Diagnostics.describe(error),
                )
            } ?: run {
                Log.w(TAG, "Timeout de ${FETCH_TIMEOUT_MS / 1000}s en ${UrlUtils.redact(target)}")
                throw WebFetchException(
                    code = WebFetchException.NO_HTTP_CODE,
                    message = "HDrama no respondio a tiempo (challenge de Cloudflare sin resolver).",
                )
            }

            if (response.isSuccessful) return response.text

            // 403 de Cloudflare con la cookie guardada: se descarta la sesion y
            // se resuelve un challenge nuevo una sola vez.
            if (response.code == 403 && attempt == 0) {
                Log.w(
                    TAG,
                    "403 con sesion previa; renovando challenge en ${UrlUtils.redact(target)}",
                )
                attempt += 1
                cloudflare = CloudflareKiller()
                continue
            }

            Log.w(TAG, "HTTP ${response.code} en ${UrlUtils.redact(target)}")
            throw WebFetchException(response.code, describeHttpError(response.code))
        }
    }

    private fun describeHttpError(code: Int): String = when (code) {
        403 -> "HDrama respondio HTTP 403: no se pudo superar el challenge de Cloudflare."
        429 -> "HDrama respondio HTTP 429: demasiadas peticiones, intenta mas tarde."
        else -> "HDrama respondio HTTP $code al solicitar la pagina."
    }

    private companion object {
        const val TAG = "HDrama"

        /**
         * CloudflareKiller espera hasta 60 s al WebView; se deja margen para
         * que termine de resolver antes de devolver el control y permitir el
         * respaldo por sitemaps.
         */
        const val FETCH_TIMEOUT_MS = 75_000L
    }
}
