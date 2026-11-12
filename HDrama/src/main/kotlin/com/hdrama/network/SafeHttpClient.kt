package com.hdrama.network

import com.hdrama.utils.Diagnostics
import com.hdrama.utils.UrlUtils
import kotlinx.coroutines.delay
import kotlin.coroutines.cancellation.CancellationException

/** Transporte HTTP minimo e inyectable para pruebas. */
fun interface HttpTransport {
    suspend fun get(url: String, headers: Map<String, String>): String
}

/**
 * Cliente HTTP con allowlist de dominios, reintentos limitados y cancelacion.
 *
 * Capa HTTP reutilizable y testeable, desacoplada de OkHttp.
 */
class SafeHttpClient(
    private val transport: HttpTransport,
    private val retryPolicy: RetryPolicy = RetryPolicy(),
    private val sleeper: suspend (Long) -> Unit = { delay(it) },
    private val allowed: (String) -> Boolean = UrlUtils::isAllowedHdramaUrl,
) {
    suspend fun getText(
        url: String,
        headers: Map<String, String> = emptyMap(),
    ): HttpOutcome {
        if (!allowed(url)) {
            return HttpOutcome.Blocked("URL fuera de la allowlist: ${UrlUtils.redact(url)}")
        }

        var attempt = 1
        while (true) {
            try {
                return HttpOutcome.Success(transport.get(url, headers))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                if (!retryPolicy.canRetry(attempt)) {
                    return HttpOutcome.Failure(
                        reason = Diagnostics.describe(error),
                        attempts = attempt,
                    )
                }
                sleeper(retryPolicy.delayMsFor(attempt))
                attempt += 1
            }
        }
    }
}
