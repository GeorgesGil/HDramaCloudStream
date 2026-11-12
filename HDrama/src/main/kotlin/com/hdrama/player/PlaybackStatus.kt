package com.hdrama.player

/** Estados de reproduccion que el plugin distingue explicitamente. */
enum class PlaybackStatus(val userMessage: String) {
    COMPATIBLE("Reproduccion compatible."),
    TEMPORARILY_UNAVAILABLE("Video temporalmente no disponible. Intenta de nuevo mas tarde."),
    UNSUPPORTED_SOURCE("Fuente no compatible con el reproductor de CloudStream."),
    BROWSER_REQUIRED("Este contenido solo puede abrirse en el navegador."),
    UNAUTHORIZED("No se pudo acceder a esta fuente."),
}

enum class LinkType { MP4, HLS, BROWSER }

/** Enlace reproducible ya normalizado, independiente de CloudStream. */
data class ResolvedLink(
    val label: String,
    val url: String,
    val type: LinkType,
    val quality: Int = 0,
    val headers: Map<String, String> = emptyMap(),
    val referer: String? = null,
)

/** Resultado exhaustivo de resolver las fuentes de un episodio. */
sealed interface LinkResolution {
    data class Playable(
        val links: List<ResolvedLink>,
        val status: PlaybackStatus = PlaybackStatus.COMPATIBLE,
    ) : LinkResolution

    data class Browser(
        val link: ResolvedLink,
        val status: PlaybackStatus = PlaybackStatus.BROWSER_REQUIRED,
    ) : LinkResolution

    data class Failure(
        val status: PlaybackStatus,
        val message: String,
    ) : LinkResolution
}
