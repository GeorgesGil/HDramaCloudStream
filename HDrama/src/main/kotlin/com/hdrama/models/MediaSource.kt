package com.hdrama.models

/** Pista de subtitulos externa asociada a un episodio. */
data class SubtitleTrack(
    val lang: String,
    val url: String,
    val headers: Map<String, String> = emptyMap(),
)

/**
 * Fuente de video de un episodio.
 *
 * Se modela como sealed interface para que el resolver pueda distinguir de
 * forma exhaustiva entre contenido reproducible y estados de error.
 */
sealed interface MediaSource {
    data class Mp4(
        val url: String,
        val quality: Int? = null,
        val headers: Map<String, String> = emptyMap(),
    ) : MediaSource

    data class Hls(
        val url: String,
        val quality: Int? = null,
        val headers: Map<String, String> = emptyMap(),
    ) : MediaSource

    /** La fuente existe pero no esta disponible temporalmente. */
    data class Unavailable(val reason: String) : MediaSource

    /** Solo puede abrirse la pagina original en el navegador. */
    data class BrowserOnly(val pageUrl: String, val reason: String) : MediaSource

    /** Requiere autorizacion que no se ha concedido. */
    data class Unauthorized(val reason: String) : MediaSource
}
