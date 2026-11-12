package com.hdrama.network

import android.util.Log
import com.hdrama.utils.Diagnostics
import com.hdrama.utils.UrlUtils
import com.lagradost.cloudstream3.app
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.cancellation.CancellationException

/** Respuesta HTTP cruda usada por [HdramaHttpFetcher] (testeable en JVM). */
data class HdramaHttpResponse(
    val code: Int,
    val body: String,
)

/** Transporte HTTP minimo para el fetcher de HDrama. */
fun interface HdramaHttpTransport {
    suspend fun get(url: String, headers: Map<String, String>): HdramaHttpResponse
}

/**
 * Transporte de produccion: usa la app HTTP de CloudStream (`app.get`), que
 * aporta OkHttp con su pool de conexiones y su cache.
 *
 * `cacheTime = 0` desactiva la cache de NiceHttp: el plugin gestiona su propia
 * cache TTL y el HTML/API debe venir siempre de red.
 */
class CloudstreamHttpTransport(
    private val timeoutMs: Long = REQUEST_TIMEOUT_MS,
) : HdramaHttpTransport {

    override suspend fun get(url: String, headers: Map<String, String>): HdramaHttpResponse {
        val response = try {
            withTimeoutOrNull(timeoutMs) {
                app.get(url, headers = headers, cacheTime = 0)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            throw WebFetchException(
                code = WebFetchException.NO_HTTP_CODE,
                message = "No se pudo completar la peticion a HDrama: " +
                    Diagnostics.describe(error),
            )
        } ?: throw WebFetchException(
            code = WebFetchException.NO_HTTP_CODE,
            message = "HDrama no respondio a tiempo.",
        )
        return HdramaHttpResponse(code = response.code, body = response.text)
    }

    private companion object {
        /**
         * Holgado a proposito: la primera peticion de una sesion puede incluir
         * la resolucion del challenge en el WebView.
         */
        const val REQUEST_TIMEOUT_MS = 60_000L
    }
}

/**
 * Fetcher principal de HDrama.
 *
 * Flujo verificado contra el sitio:
 *
 * 1. Resuelve el Managed Challenge **una sola vez** ([CloudflareSession]) y
 *    guarda la cookie `cf_clearance` y el User-Agent del WebView.
 * 2. Hace todas las peticiones por OkHttp con esa cookie, ese User-Agent y
 *    `Referer: https://es.hdrama.net/` (la API `/api/` exige el Referer).
 * 3. Si el sitio invalida la sesion (HTTP 403/503) renueva el challenge y
 *    reintenta; si aun asi falla usa [fallback] (WebView) como ultimo recurso.
 *
 * Las peticiones se serializan con un [Mutex]: Cloudflare y el WebView no
 * toleran bien varios challenges simultaneos.
 */
class HdramaHttpFetcher(
    private val session: CloudflareSession = NoCloudflareSession,
    private val transport: HdramaHttpTransport = CloudstreamHttpTransport(),
    private val fallback: WebFetcher? = null,
    private val sleeper: suspend (Long) -> Unit = { delay(it) },
) : WebFetcher {

    private val mutex = Mutex()

    override suspend fun fetch(url: String, headers: Map<String, String>): String {
        val target = HdramaFetchAllowlist.resolve(url)
        return mutex.withLock { request(target, headers) }
    }

    /**
     * Adelanta la resolucion del challenge de Cloudflare. Se llama en segundo
     * plano al cargar el plugin para que la primera pantalla del catalogo no
     * tenga que esperar al WebView.
     */
    suspend fun warmUp(): Boolean = mutex.withLock { session.ensure() }

    private suspend fun request(
        target: String,
        headers: Map<String, String>,
    ): String {
        // La sesion es imprescindible: sin cookie cf_clearance el sitio
        // responde 403 a todo. Sin sesion no se abre un WebView de respaldo
        // (tardaria lo mismo y volveria a fallar): se falla rapido para que el
        // catalogo use los sitemaps.
        if (!session.ensure()) {
            throw WebFetchException(
                code = 403,
                message = "HDrama rechazo la peticion: la sesion de Cloudflare no esta " +
                    "disponible. Se usara el respaldo por sitemaps.",
            )
        }

        var lastCode = WebFetchException.NO_HTTP_CODE
        var attempt = 0
        while (attempt < MAX_ATTEMPTS) {
            attempt += 1
            val response = try {
                transport.get(target, buildHeaders(target, headers))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                Log.w(TAG, "Fallo de red en ${UrlUtils.redact(target)}: ${error::class.simpleName}")
                return fallbackOrThrow(target, headers, error)
            }

            if (response.code in 200..299) return response.body

            lastCode = response.code
            Log.w(TAG, "HTTP ${response.code} en ${UrlUtils.redact(target)}")
            if (isChallengeRejection(response.code) && attempt < MAX_ATTEMPTS) {
                Log.w(TAG, "Sesion rechazada (HTTP ${response.code}); renovando challenge")
                session.invalidate()
                if (session.ensure()) {
                    sleeper(RETRY_DELAY_MS)
                    continue
                }
            }
            break
        }

        return fallbackOrThrow(
            target,
            headers,
            WebFetchException(lastCode, describeHttpError(lastCode)),
        )
    }

    private suspend fun fallbackOrThrow(
        target: String,
        headers: Map<String, String>,
        error: Throwable,
    ): String {
        val secondary = fallback ?: throw wrap(error)
        Log.w(TAG, "Usando respaldo WebView para ${UrlUtils.redact(target)}")
        return try {
            secondary.fetch(target, headers)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (fallbackError: Throwable) {
            // Se prefiere el error principal: describe mejor el estado real
            // (por ejemplo, sesion de Cloudflare no disponible).
            throw wrap(error)
        }
    }

    private fun wrap(error: Throwable): WebFetchException = when (error) {
        is WebFetchException -> error
        else -> WebFetchException(
            code = WebFetchException.NO_HTTP_CODE,
            message = "No se pudo acceder a HDrama: ${Diagnostics.describe(error)}",
        )
    }

    private suspend fun buildHeaders(
        url: String,
        extra: Map<String, String>,
    ): Map<String, String> {
        val headers = LinkedHashMap<String, String>(8)
        if (url.contains("/api/", ignoreCase = true)) {
            headers["accept"] = "application/json"
        } else {
            headers["accept"] =
                "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"
        }
        headers["accept-language"] = "es-ES,es;q=0.9"
        // La API /api/* solo responde a peticiones same-origin: sin Referer
        // devuelve 403 `{"error":"forbidden"}` incluso con cf_clearance.
        headers["referer"] = UrlUtils.HDRA_BASE_URL
        session.userAgent()?.takeIf { it.isNotBlank() }?.let { headers["user-agent"] = it }
        session.cookieHeader()?.takeIf { it.isNotBlank() }?.let { headers["cookie"] = it }

        extra.forEach { (name, value) ->
            val previous = headers.keys.firstOrNull { it.equals(name, ignoreCase = true) }
            if (previous != null && previous != name) headers.remove(previous)
            headers[name] = value
        }
        return headers
    }

    private fun isChallengeRejection(code: Int): Boolean = code == 403 || code == 503

    private fun describeHttpError(code: Int): String = when (code) {
        403 -> "HDrama rechazo la peticion (HTTP 403): la sesion de Cloudflare no esta " +
            "disponible. Espera unos segundos y vuelve a intentarlo."
        429 -> "HDrama respondio HTTP 429: demasiadas peticiones, intenta mas tarde."
        451 -> "HDrama respondio HTTP 451: contenido bloqueado por el proveedor."
        else -> "HDrama respondio HTTP $code al solicitar la pagina."
    }

    private companion object {
        const val TAG = "HDrama"
        const val MAX_ATTEMPTS = 2
        const val RETRY_DELAY_MS = 400L
    }
}
