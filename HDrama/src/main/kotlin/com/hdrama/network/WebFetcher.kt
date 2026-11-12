package com.hdrama.network

import com.hdrama.utils.UrlUtils

/**
 * Obtiene el cuerpo de una pagina o de una API de HDrama.
 *
 * La implementacion de produccion ([CloudstreamWebFetcher]) reutiliza la app
 * HTTP de CloudStream para pasar el challenge de Cloudflare. Las pruebas JVM
 * inyectan una implementacion falsa: no se hace red en tests.
 */
interface WebFetcher {
    /**
     * GET con las cookies de Cloudflare vigentes; devuelve el cuerpo tal cual.
     *
     * @throws WebFetchException si la URL esta fuera de la allowlist de HDrama
     *   o si la respuesta no es 2xx.
     */
    suspend fun fetch(url: String, headers: Map<String, String> = emptyMap()): String
}

/**
 * Error controlado de la capa web de HDrama.
 *
 * @property code codigo HTTP de la respuesta. Vale [NO_HTTP_CODE] cuando no
 *   hubo respuesta HTTP (fallo de red) o cuando la URL fue rechazada por la
 *   allowlist antes de abrir conexion.
 */
class WebFetchException(val code: Int, message: String) : Exception(message) {
    companion object {
        /** No hubo respuesta HTTP que reportar (red o allowlist). */
        const val NO_HTTP_CODE = 0
    }
}

/**
 * Allowlist pura de la capa web (sin Android ni CloudStream) para que la
 * validacion sea testeable en JVM.
 *
 * Solo acepta URLs https de `es.hdrama.net` (o `www.es.hdrama.net`, que se
 * normaliza); `http` se eleva a `https`. Cualquier otra cosa lanza
 * [WebFetchException] con un mensaje en espanol y sin datos sensibles.
 */
internal object HdramaFetchAllowlist {

    /** Devuelve la URL canonica permitida o lanza [WebFetchException]. */
    fun resolve(url: String): String {
        val raw = url.trim()
        if (!UrlUtils.isAllowedHdramaUrl(raw)) {
            throw WebFetchException(
                code = WebFetchException.NO_HTTP_CODE,
                message = "La URL no pertenece a la allowlist de HDrama: ${UrlUtils.redact(raw)}",
            )
        }
        return UrlUtils.normalizeHdramaUrl(raw) ?: raw
    }
}
