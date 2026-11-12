package com.hdrama.sources

import com.hdrama.models.MediaSource
import com.hdrama.network.HdramaHttpTransport
import com.hdrama.network.TtlCache
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.cancellation.CancellationException

/**
 * Comprueba que las URLs de video de la cadena responden antes de entregarlas
 * al reproductor.
 *
 * HDrama devuelve a veces una cadena con enlaces caducados o muertos: en la
 * comprobacion del 2026-11-12 el primer enlace (`pl.shortdizilab.com/...`) daba
 * 404 y el segundo (`zshipricf.farsunpteltd.net/...`) respondia 200. CloudStream
 * reproduce el primer enlace de la lista y, si falla, muestra el error; por eso
 * conviene detectar los muertos aqui y entregar solo los que responden.
 */
interface SourceProbe {
    /**
     * Devuelve las fuentes reproducibles conservando el orden original. Si
     * ninguna responde, devuelve las originales para que el player muestre el
     * error real y el usuario pueda cambiar de enlace.
     */
    suspend fun filterPlayable(sources: List<MediaSource>): List<MediaSource>
}

/** Sin comprobacion: entornos JVM/pruebas o sin contexto Android. */
object NoSourceProbe : SourceProbe {
    override suspend fun filterPlayable(sources: List<MediaSource>): List<MediaSource> = sources
}

/**
 * Sonda HTTP: pide los primeros bytes de cada URL con un timeout corto.
 *
 * - HLS: se descarga el inicio del manifiesto (es pequeno).
 * - MP4: se pide un rango de 1 KB para no descargar el archivo entero.
 *
 * No envia `Referer`: los hosts directos de HDrama responden 403 si lo reciben.
 * Los resultados se cachean brevemente para no repetir la comprobacion al
 * reproducir, descargar o reintentar.
 */
class HttpSourceProbe(
    private val transport: HdramaHttpTransport,
    private val probeTimeoutMs: Long = DEFAULT_TIMEOUT_MS,
    private val cache: TtlCache<String, Boolean> = TtlCache(CACHE_TTL_MS, 64),
) : SourceProbe {

    override suspend fun filterPlayable(sources: List<MediaSource>): List<MediaSource> {
        val candidates = sources.filter { it is MediaSource.Hls || it is MediaSource.Mp4 }
        // Con una sola fuente no hay nada que elegir: ahorra una peticion.
        if (candidates.size <= 1) return sources

        val alive = coroutineScope {
            candidates.map { source -> async { source to isAvailable(source) } }
                .map { it.await() }
                .filter { it.second }
                .map { it.first }
        }
        return if (alive.isEmpty()) sources else alive
    }

    private suspend fun isAvailable(source: MediaSource): Boolean {
        val url = when (source) {
            is MediaSource.Hls -> source.url
            is MediaSource.Mp4 -> source.url
            else -> return false
        }
        cache.get(url)?.let { return it }

        val headers = when (source) {
            is MediaSource.Hls -> HLS_PROBE_HEADERS
            else -> MP4_PROBE_HEADERS
        }
        val available = try {
            withTimeoutOrNull(probeTimeoutMs) {
                transport.get(url, headers).code in 200..299
            } ?: false
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            false
        }
        cache.put(url, available)
        return available
    }

    private companion object {
        const val DEFAULT_TIMEOUT_MS = 6_000L
        const val CACHE_TTL_MS = 60_000L
        val HLS_PROBE_HEADERS = mapOf(
            "accept" to "application/vnd.apple.mpegurl,application/x-mpegURL,*/*",
        )
        val MP4_PROBE_HEADERS = mapOf(
            "accept" to "*/*",
            "range" to "bytes=0-1023",
        )
    }
}
