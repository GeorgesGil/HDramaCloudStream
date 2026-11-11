package com.hdrama.player

import com.hdrama.models.MediaSource
import com.hdrama.utils.UrlUtils

/**
 * Convierte fuentes de dominio en enlaces reproducibles, deduplicados y
 * ordenados por calidad. No conoce CloudStream.
 */
object LinkResolver {

    fun resolve(sources: List<MediaSource>): LinkResolution {
        val unique = LinkedHashMap<String, ResolvedLink>()

        sources.forEach { source ->
            when (source) {
                is MediaSource.Mp4 -> add(
                    unique,
                    build(
                        url = source.url,
                        type = LinkType.MP4,
                        quality = source.quality,
                        headers = source.headers,
                    ),
                )

                is MediaSource.Hls -> add(
                    unique,
                    build(
                        url = source.url,
                        type = LinkType.HLS,
                        quality = source.quality,
                        headers = source.headers,
                    ),
                )

                else -> Unit
            }
        }

        val playable = unique.values.sortedWith(
            compareByDescending<ResolvedLink> { it.quality }.thenBy { it.label }
        )
        if (playable.isNotEmpty()) {
            return LinkResolution.Playable(playable)
        }

        val unauthorized = sources.filterIsInstance<MediaSource.Unauthorized>().firstOrNull()
        if (unauthorized != null) {
            return LinkResolution.Failure(
                status = PlaybackStatus.UNAUTHORIZED,
                message = unauthorized.reason,
            )
        }

        val browser = sources.filterIsInstance<MediaSource.BrowserOnly>().firstOrNull()
        if (browser != null) {
            val canonical = UrlUtils.normalizeHdramaUrl(browser.pageUrl)
            if (canonical != null) {
                return LinkResolution.Browser(
                    link = ResolvedLink(
                        label = "Abrir en navegador",
                        url = canonical,
                        type = LinkType.BROWSER,
                        referer = null,
                    ),
                    status = PlaybackStatus.BROWSER_REQUIRED,
                )
            }
            return LinkResolution.Failure(
                status = PlaybackStatus.UNSUPPORTED_SOURCE,
                message = "La pagina de navegador no es una URL valida de HDrama.",
            )
        }

        val unavailable = sources.filterIsInstance<MediaSource.Unavailable>().firstOrNull()
        return LinkResolution.Failure(
            status = PlaybackStatus.TEMPORARILY_UNAVAILABLE,
            message = unavailable?.reason
                ?: "No se encontro una fuente reproducible para este episodio.",
        )
    }

    private fun build(
        url: String,
        type: LinkType,
        quality: Int?,
        headers: Map<String, String>,
    ): ResolvedLink? {
        val clean = normalizeMediaUrl(url) ?: return null
        val q = quality?.coerceAtLeast(0) ?: 0
        val label = when {
            q <= 0 -> "Auto"
            else -> "${q}p"
        }
        return ResolvedLink(
            label = label,
            url = clean,
            type = type,
            quality = q,
            headers = headers,
            referer = null,
        )
    }

    private fun add(unique: MutableMap<String, ResolvedLink>, link: ResolvedLink?) {
        if (link == null) return
        val key = "${link.type}|${link.url}|${link.quality}"
        unique.putIfAbsent(key, link)
    }

    /** Solo acepta http(s) absolutas; elimina fragmentos. */
    fun normalizeMediaUrl(url: String?): String? {
        val raw = url?.trim().orEmpty()
        if (raw.isEmpty()) return null
        if (!raw.startsWith("https://", ignoreCase = true) &&
            !raw.startsWith("http://", ignoreCase = true)
        ) {
            return null
        }
        return raw.substringBefore('#').ifBlank { null }
    }
}
