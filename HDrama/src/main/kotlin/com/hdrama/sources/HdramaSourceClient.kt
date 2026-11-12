package com.hdrama.sources

import com.hdrama.models.MediaSource
import com.hdrama.network.WebFetchException
import com.hdrama.network.WebFetcher
import com.hdrama.utils.Diagnostics
import com.hdrama.utils.UrlUtils
import kotlin.coroutines.cancellation.CancellationException
import org.json.JSONObject

/** Entrada ya descifrada de la cadena de fuentes de la API. */
data class SourceChainEntry(
    val url: String,
    val type: String,
    val source: String?,
)

/**
 * Cliente de la API de fuentes de video de HDrama.
 *
 * Flujo verificado contra el sitio:
 * 1. GET `https://es.hdrama.net/api/episode-source/<bookId>/<serial>?lang=es&refresh=1`
 *    (requiere las cookies del challenge de Cloudflare, las aporta [WebFetcher]).
 * 2. Recorrer `descriptor.chain`, descifrar cada `enc` con [HdramaSourceCipher]
 *    y mapear `type`: `hls` -> [MediaSource.Hls], `mp4` -> [MediaSource.Mp4].
 * 3. Por cada URL directa anadir un espejo
 *    `https://cdn.dramadizilerim.xyz/https/<url>` con
 *    `Referer: https://es.hdrama.net/` (las directas NO llevan Referer: el host
 *    de video devuelve 403 si lo recibe).
 *
 * Nunca lanza por fallos de red o de parseo: devuelve una lista con un unico
 * [MediaSource.Unavailable] con un motivo en espanol.
 */
class HdramaSourceClient(
    private val fetcher: WebFetcher,
    lang: String = "es",
) {

    private val language: String = lang.trim().lowercase().takeIf {
        LANGUAGE_PATTERN.matches(it)
    } ?: DEFAULT_LANG

    /** Fuentes de video del episodio, directas primero y luego los espejos. */
    suspend fun sources(bookId: String, serial: Int): List<MediaSource> {
        val cleanBookId = bookId.trim()
        if (!BOOK_ID_PATTERN.matches(cleanBookId) || serial <= 0) {
            return unavailable("El episodio no tiene un identificador valido en HDrama.")
        }

        val body = try {
            fetcher.fetch(buildApiUrl(cleanBookId, serial))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            android.util.Log.w("HDrama", "Fuentes: fallo de red (${error.message})")
            return unavailable(describeFetchFailure(error))
        }
        val parsed = parseSources(body)
        android.util.Log.d(
            "HDrama",
            "Fuentes bookId=$cleanBookId serial=$serial bytes=${body.length} " +
                "resultado=${parsed.size}",
        )
        return parsed
    }

    internal fun buildApiUrl(bookId: String, serial: Int): String =
        "${UrlUtils.HDRA_BASE_URL}api/episode-source/" +
            "${java.net.URLEncoder.encode(bookId, "UTF-8")}/$serial" +
            "?lang=$language&refresh=1"

    /** Parsea la respuesta de la API; tolerante a campos ausentes o corruptos. */
    internal fun parseSources(body: String): List<MediaSource> {
        val root = try {
            JSONObject(body)
        } catch (error: Exception) {
            return unavailable("La respuesta de fuentes de HDrama no es JSON valido.")
        }
        val descriptor = root.optJSONObject("descriptor")
            ?: return unavailable("La respuesta de HDrama no incluye el descriptor de fuentes.")
        val chain = descriptor.optJSONArray("chain")
            ?: return unavailable("La respuesta de HDrama no incluye la cadena de fuentes.")
        if (chain.length() == 0) {
            return unavailable("La cadena de fuentes de HDrama esta vacia.")
        }
        // descriptor.subtitle se ignora a proposito: no es obligatorio para esta
        // tarea y puede venir null, objeto o lista; no debe romper el parseo.

        val direct = ArrayList<MediaSource>(chain.length())
        val directUrls = ArrayList<String>(chain.length())
        val seenUrls = HashSet<String>(chain.length())
        for (index in 0 until chain.length()) {
            val item = chain.optJSONObject(index) ?: continue
            val enc = item.optString("enc", "").trim()
            if (enc.isEmpty()) continue
            val type = item.optString("type", "hls").trim().lowercase()
            val source = item.optString("source", "").trim().ifEmpty { null }
            val url = HdramaSourceCipher.decrypt(enc) ?: continue
            if (!UrlUtils.isHttpUrl(url)) continue
            val mapped = mapDirect(SourceChainEntry(url = url, type = type, source = source))
                ?: continue
            if (!seenUrls.add(url)) continue
            direct += mapped
            directUrls += url
        }
        if (direct.isEmpty()) {
            return unavailable("No se pudo descifrar ninguna fuente de HDrama para este episodio.")
        }

        val result = ArrayList<MediaSource>(direct.size + directUrls.size)
        result += direct
        directUrls.forEach { url -> result += proxyMirror(url) }
        return result
    }

    private fun mapDirect(entry: SourceChainEntry): MediaSource? = when (entry.type) {
        "hls" -> MediaSource.Hls(url = entry.url)
        "mp4" -> MediaSource.Mp4(url = entry.url)
        else -> null
    }

    private fun proxyMirror(url: String): MediaSource.Hls = MediaSource.Hls(
        url = PROXY_PREFIX + url,
        headers = mapOf(REFERER_HEADER to REFERER_VALUE),
    )

    private fun describeFetchFailure(error: Throwable): String {
        if (error is WebFetchException) {
            val prefix = if (error.code > 0) {
                "HDrama rechazo la peticion de fuentes (HTTP ${error.code})."
            } else {
                "No se pudo acceder a HDrama."
            }
            return "$prefix ${Diagnostics.sanitize(error.message)}"
        }
        return "No se pudieron obtener las fuentes de HDrama: ${Diagnostics.describe(error)}"
    }

    private fun unavailable(reason: String): List<MediaSource> =
        listOf(MediaSource.Unavailable(reason))

    private companion object {
        const val DEFAULT_LANG = "es"
        const val PROXY_PREFIX = "https://cdn.dramadizilerim.xyz/https/"
        const val REFERER_HEADER = "Referer"
        const val REFERER_VALUE = "https://es.hdrama.net/"
        val LANGUAGE_PATTERN = Regex("[a-z]{2,3}(?:-[a-z0-9]{2,8})?")

        /**
         * El `bookId` de HDrama no siempre es numerico: en el HTML real aparece
         * como numero, como id hexadecimal o incluso como el slug de la serie.
         */
        val BOOK_ID_PATTERN = Regex("[A-Za-z0-9_-]{1,80}")
    }
}
