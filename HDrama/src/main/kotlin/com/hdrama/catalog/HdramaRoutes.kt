package com.hdrama.catalog

import com.hdrama.utils.UrlUtils
import java.net.URI
import java.net.URLEncoder

/**
 * Rutas reales de HDrama (verificadas contra el sitio el 2026-11-11).
 *
 * El sitio es Next.js: las URLs con prefijo `/es/...` redirigen a las rutas
 * sin prefijo, por lo que se usan directamente las definitivas para evitar
 * redirecciones extra detras de Cloudflare.
 */
object HdramaRoutes {
    const val PAGE_SIZE = 24

    private val episodePattern = Regex("""/episode-(\d+)/?$""")

    fun home(): String = UrlUtils.HDRA_BASE_URL

    fun series(slug: String): String = "${UrlUtils.HDRA_BASE_URL}series/$slug"

    fun episode(slug: String, displayNumber: Int): String =
        "${series(slug)}/episode-$displayNumber"

    fun category(slug: String, page: Int = 1): String =
        if (page <= 1) "${UrlUtils.HDRA_BASE_URL}category/$slug"
        else "${UrlUtils.HDRA_BASE_URL}category/$slug/page/$page"

    fun seriesList(page: Int = 1): String =
        if (page <= 1) "${UrlUtils.HDRA_BASE_URL}series"
        else "${UrlUtils.HDRA_BASE_URL}series/page/$page"

    fun search(query: String): String =
        "${UrlUtils.HDRA_BASE_URL}search?q=${URLEncoder.encode(query, "UTF-8")}"

    fun categories(): String = "${UrlUtils.HDRA_BASE_URL}categories"

    fun sources(): String = "${UrlUtils.HDRA_BASE_URL}sources"

    fun isEpisodeUrl(value: String?): Boolean =
        episodeNumber(value) != null

    /** Numero mostrado del episodio (`1` para `/episode-1`). */
    fun episodeNumber(value: String?): Int? {
        val canonical = UrlUtils.normalizeHdramaUrl(value) ?: return null
        val match = episodePattern.find(canonical) ?: return null
        return match.groupValues.getOrNull(1)?.toIntOrNull()
    }

    /** URL canonica de la ficha de serie a partir de la de un episodio. */
    fun seriesUrlOfEpisode(value: String?): String? {
        val canonical = UrlUtils.normalizeHdramaUrl(value) ?: return null
        val path = runCatching { URI(canonical).path }.getOrNull() ?: return null
        val marker = episodePattern.find(path) ?: return null
        val seriesPath = path.substring(0, marker.range.first).trimEnd('/')
        return UrlUtils.normalizeHdramaUrl("https://${UrlUtils.HDRA_HOST}$seriesPath")
    }

    /** Paginas que no son fichas: directorios de generos/fuentes o listados. */
    fun isBrowseUrl(value: String?): Boolean {
        val canonical = UrlUtils.normalizeHdramaUrl(value) ?: return false
        val path = runCatching { URI(canonical).path }.getOrNull().orEmpty()
        if (path.isBlank() || path == "/") return true
        if (path == "/categories" || path == "/sources") return true
        if (path.startsWith("/category/")) return true
        if (path.startsWith("/source/")) return true
        return false
    }

    /** Identificador estable para una pagina de navegacion (genero, fuente...). */
    fun browseId(value: String?): String {
        val canonical = UrlUtils.normalizeHdramaUrl(value) ?: return "hdrama"
        val path = runCatching { URI(canonical).path }.getOrNull().orEmpty()
        return when {
            path.isBlank() || path == "/" -> "hdrama-inicio"
            else -> "hdrama-" + path.trim('/').replace('/', '-').replace('.', '-')
        }
    }

    /** Titulo legible para una pagina de navegacion. */
    fun browseTitle(value: String?): String {
        val canonical = UrlUtils.normalizeHdramaUrl(value) ?: return "HDrama"
        val path = runCatching { URI(canonical).path }.getOrNull().orEmpty()
        val slug = path.trim('/').substringAfterLast('/')
        return when {
            path.isBlank() || path == "/" -> "Inicio de HDrama"
            path == "/categories" -> "Géneros de HDrama"
            path == "/sources" -> "Fuentes de HDrama"
            path.startsWith("/category/") -> "Categoría: " + com.hdrama.utils.TextNormalizer.titleFromSlug(slug)
            path.startsWith("/source/") -> "Fuente: " + com.hdrama.utils.TextNormalizer.titleFromSlug(slug)
            else -> com.hdrama.utils.TextNormalizer.titleFromSlug(slug)
        }
    }
}
