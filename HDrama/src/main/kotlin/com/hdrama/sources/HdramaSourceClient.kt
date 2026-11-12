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
 * Flujo verificado contra el sitio (2026-11-12):
 * 1. La sesion de Cloudflare (`cf_clearance` + User-Agent del WebView) la
 *    resuelve [WebFetcher]; [warmUp] carga el inicio del sitio para que la
 *    cookie exista **antes** de llamar a `/api/` (el WAF no resuelve challenges
 *    en esa ruta y devolveria 403 tras esperar al WebView).
 * 2. GET `https://es.hdrama.net/api/episode-source/<bookId>/<serial>?lang=es&refresh=1`
 *    con `Referer: https://es.hdrama.net/` y `accept: application/json`.
 * 3. Recorrer `descriptor.chain`, descifrar cada `enc` con [HdramaSourceCipher]
 *    y mapear `type`: `hls` -> [MediaSource.Hls], `mp4` -> [MediaSource.Mp4].
 * 4. Emitir **solo URLs directas**: el host que sirve el HLS (por ejemplo
 *    `acdn-v-us.stardust-tv.com`) responde 200 sin cabeceras y no necesita
 *    `Referer`; el antiguo proxy espejo ya no esta disponible.
 *
 * Nunca lanza por fallos de red o de parseo: devuelve una lista con un unico
 * [MediaSource.Unavailable] con un motivo en espanol.
 */
class HdramaSourceClient(
    private val fetcher: WebFetcher,
    lang: String = "es",
    /**
     * Carga previa del sitio para asegurar la cookie de Cloudflare. Por
     * defecto no hace nada: el adaptador de catalogo pasa una carga del inicio
     * (cacheada) para no resolver el challenge dos veces.
     */
    private val warmUp: suspend () -> Unit = {},
) {

    private val language: String = lang.trim().lowercase().takeIf {
        LANGUAGE_PATTERN.matches(it)
    } ?: DEFAULT_LANG

    /** Fuentes de video del episodio, solo URLs directas y deduplicadas. */
    suspend fun sources(bookId: String, serial: Int): List<MediaSource> {
        val cleanBookId = bookId.trim()
        if (!BOOK_ID_PATTERN.matches(cleanBookId) || serial <= 0) {
            return unavailable("El episodio no tiene un identificador valido en HDrama.")
        }

        // El warm-up es best-effort: si falla, se intenta la API igualmente
        // (puede que la cookie ya exista y solo falle una recarga del inicio).
        try {
            warmUp()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            android.util.Log.w("HDrama", "Fuentes: warm-up del inicio no disponible")
        }

        val body = try {
            fetcher.fetch(buildApiUrl(cleanBookId, serial), API_HEADERS)
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
        // descriptor.subtitle se ignora a proposito: la API no emite subtitulos
        // hoy y puede venir null, objeto o lista; no debe romper el parseo.

        val result = ArrayList<MediaSource>(chain.length())
        val seenUrls = HashSet<String>(chain.length())
        for (index in 0 until chain.length()) {
            val item = chain.optJSONObject(index) ?: continue
            val enc = item.optString("enc", "").trim()
            if (enc.isEmpty()) continue
            val type = item.optString("type", "hls").trim().lowercase()
            val source = item.optString("source", "").trim().ifEmpty { null }
            val url = HdramaSourceCipher.decrypt(enc) ?: continue
            if (!UrlUtils.isHttpUrl(url)) continue
            if (!seenUrls.add(url)) continue
            val mapped = mapDirect(SourceChainEntry(url = url, type = type, source = source))
                ?: continue
            result += mapped
        }
        if (result.isEmpty()) {
            return unavailable("No se pudo descifrar ninguna fuente de HDrama para este episodio.")
        }
        return result
    }

    /**
     * Mapea una entrada a fuente reproducible.
     *
     * Se confia primero en `type` y, si viene vacio o desconocido, en la
     * extension de la URL: el sitio puede anadir tipos nuevos sin aviso y el
     * player solo necesita saber si es un manifiesto HLS o un archivo MP4.
     */
    private fun mapDirect(entry: SourceChainEntry): MediaSource? {
        val url = entry.url
        val isHlsByExtension = url.substringBefore('?').endsWith(".m3u8", ignoreCase = true)
        val isMp4ByExtension = url.substringBefore('?').endsWith(".mp4", ignoreCase = true)
        return when {
            entry.type == "hls" || (entry.type != "mp4" && isHlsByExtension) ->
                MediaSource.Hls(url = url)
            entry.type == "mp4" || isMp4ByExtension -> MediaSource.Mp4(url = url)
            else -> null
        }
    }

    private fun describeFetchFailure(error: Throwable): String {
        if (error is WebFetchException) {
            return when {
                error.code == 403 -> "HDrama rechazo la peticion de fuentes (HTTP 403). " +
                    "La sesion de Cloudflare no esta disponible; espera unos segundos y " +
                    "vuelve a intentarlo."
                error.code > 0 -> "HDrama rechazo la peticion de fuentes (HTTP ${error.code}). " +
                    Diagnostics.sanitize(error.message)
                else -> "No se pudo acceder a HDrama. ${Diagnostics.sanitize(error.message)}"
            }
        }
        return "No se pudieron obtener las fuentes de HDrama: ${Diagnostics.describe(error)}"
    }

    private fun unavailable(reason: String): List<MediaSource> =
        listOf(MediaSource.Unavailable(reason))

    private companion object {
        const val DEFAULT_LANG = "es"
        val API_HEADERS = mapOf(
            "accept" to "application/json",
            "referer" to UrlUtils.HDRA_BASE_URL,
        )
        val LANGUAGE_PATTERN = Regex("[a-z]{2,3}(?:-[a-z0-9]{2,8})?")

        /**
         * El `bookId` de HDrama no siempre es numerico: en el HTML real aparece
         * como numero, como id hexadecimal o incluso como el slug de la serie.
         */
        val BOOK_ID_PATTERN = Regex("[A-Za-z0-9_-]{1,80}")
    }
}
