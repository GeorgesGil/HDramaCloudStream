package com.hdrama.catalog

import com.hdrama.models.Episode
import com.hdrama.models.MediaSource
import com.hdrama.models.Series
import com.hdrama.utils.EpisodeId
import com.hdrama.utils.TextNormalizer
import com.hdrama.utils.UrlUtils

/**
 * Importacion manual / ficha de respaldo de una pagina de HDrama.
 *
 * Se usa cuando el HTML real no puede descargarse (Cloudflare sin sesion,
 * red caida...) o cuando la URL no es una ficha con episodios. No realiza
 * peticiones de red: construye una ficha de navegacion y remite al sitio.
 */
object ManualHdramaImport {
    fun fromUrl(url: String?): Series? {
        val series = browserCardFromUrl(url, source = "HDrama (respaldo sin HTML)") ?: return null
        if (UrlUtils.extractSeriesSlug(series.pageUrl) == null) return null
        return series.copy(
            synopsis = "No se pudo descargar la ficha desde HDrama en este momento. " +
                "Abre la pagina original en el navegador para ver la informacion oficial.",
        )
    }

    /**
     * Convierte cualquier URL permitida de HDrama (serie, categoria, guia) en
     * una tarjeta de navegacion.
     */
    fun browserCardFromUrl(
        url: String?,
        titleOverride: String? = null,
        source: String = "HDrama (respaldo sin HTML)",
    ): Series? {
        val canonical = UrlUtils.normalizeHdramaUrl(url) ?: return null
        val slug = UrlUtils.extractSeriesSlug(canonical)
            ?: canonical.trimEnd('/').substringAfterLast('/')
        val title = titleOverride?.takeIf { it.isNotBlank() }
            ?: TextNormalizer.titleFromSlug(slug)
        if (title.isBlank()) return null
        return Series(
            id = slug,
            slug = slug,
            title = title,
            synopsis = "Pagina de HDrama para abrir en el navegador. " +
                "No se pudo descargar el HTML del sitio.",
            genres = emptyList(),
            language = "es",
            source = source,
            demo = false,
            browserOnly = true,
            pageUrl = canonical,
        )
    }

    fun browserEpisode(series: Series): Episode = Episode(
        id = EpisodeId.encode(series.id, 1),
        seriesId = series.id,
        number = 1,
        title = "Abrir en el navegador",
        pageUrl = series.pageUrl,
        mediaSources = listOf(
            MediaSource.BrowserOnly(
                pageUrl = series.pageUrl ?: UrlUtils.HDRA_BASE_URL,
                reason = "La ficha solo puede abrirse en el sitio original.",
            )
        ),
    )
}
