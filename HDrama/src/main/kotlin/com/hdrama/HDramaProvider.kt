package com.hdrama

import com.hdrama.catalog.CatalogRepository
import com.hdrama.catalog.CatalogSection
import com.hdrama.catalog.ManualHdramaImport
import com.hdrama.models.CatalogPage
import com.hdrama.models.Episode
import com.hdrama.models.Series
import com.hdrama.player.LinkResolution
import com.hdrama.player.LinkResolver
import com.hdrama.player.LinkType
import com.hdrama.player.ResolvedLink
import com.hdrama.utils.TextNormalizer
import com.hdrama.utils.UrlUtils
import com.lagradost.cloudstream3.ErrorLoadingException
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SearchResponseList
import com.lagradost.cloudstream3.Score
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newSearchResponseList
import com.lagradost.cloudstream3.newSubtitleFile
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.newTvSeriesSearchResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink

/**
 * Proveedor CloudStream de HDrama.
 *
 * Fuente principal: HTML real de `es.hdrama.net` (JSON-LD, fichas, episodios y
 * API de fuentes HLS) mediante una sesion con Cloudflare resuelto por WebView.
 * Respaldo: sitemaps publicos y fichas de navegacion; el catalogo demo sigue
 * disponible desde la seccion "Acceso a HDrama" para diagnostico del player.
 */
class HDramaProvider(
    private val repository: CatalogRepository = CatalogRepository(),
) : MainAPI() {
    override var name = "HDrama"
    override var mainUrl = UrlUtils.HDRA_BASE_URL
    override var lang = "es"

    override val hasMainPage = true
    override val supportedTypes = setOf(TvType.TvSeries, TvType.AsianDrama)
    override val hasChromecastSupport = false
    override val hasDownloadSupport = true

    // El HTML de HDrama exige una sesion de Cloudflare: las secciones se piden
    // en serie para no abrir varios WebView a la vez y se declara el uso de
    // WebView para que CloudStream lo tenga en cuenta.
    override var sequentialMainPage = true
    override var sequentialMainPageDelay = 250L
    override val usesWebView = true

    override val mainPage = mainPageOf(
        *CatalogSection.entries
            .map { it.key to it.displayName }
            .toTypedArray(),
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest,
    ): HomePageResponse {
        val section = CatalogSection.fromKey(request.data)
        val pageData = runCatching { repository.section(section, page) }
            .getOrElse { CatalogPage.empty(page) }
        return newHomePageResponse(
            name = request.name,
            list = pageData.items.map { it.toSearchResponse() },
            hasNext = pageData.hasNext,
        )
    }

    override suspend fun search(query: String, page: Int): SearchResponseList {
        val clean = query.trim()
        if (clean.isEmpty()) return newSearchResponseList(emptyList(), false)

        // Pegar una URL de HDrama abre directamente su ficha real.
        if (UrlUtils.isSeriesPage(clean) || UrlUtils.isAllowedHdramaUrl(clean)) {
            val series = runCatching { repository.load(clean) }.getOrNull()
                ?: ManualHdramaImport.fromUrl(clean)
            return newSearchResponseList(listOfNotNull(series?.toSearchResponse()), false)
        }

        // Atajo de diagnostico: permite abrir el contenido demo sin depender
        // de que la seccion "Acceso a HDrama" este visible.
        if (TextNormalizer.normalize(clean).startsWith("prueba")) {
            val demoResults = runCatching { repository.demoAdapter().search(clean, page) }
                .getOrElse { CatalogPage.empty(page) }
            if (demoResults.items.isNotEmpty()) {
                return newSearchResponseList(
                    demoResults.items.map { it.toSearchResponse() },
                    demoResults.hasNext,
                )
            }
        }

        val results = runCatching { repository.search(clean, page) }
            .getOrElse { CatalogPage.empty(page) }
        return newSearchResponseList(results.items.map { it.toSearchResponse() }, results.hasNext)
    }

    override suspend fun search(query: String): List<SearchResponse> =
        search(query, 1).items

    override suspend fun load(url: String): LoadResponse? {
        val effectiveUrl = UrlUtils.recoverDemoSeriesUrl(url) ?: url
        val series = repository.load(effectiveUrl)
            ?: throw ErrorLoadingException(
                "No se pudo cargar la ficha. Verifica que la URL sea de HDrama " +
                    "o que el contenido de demostracion exista."
            )

        val episodes = runCatching { repository.episodes(series) }
            .getOrDefault(emptyList())
            .map { it.toCloudEpisode(series) }
        val recommendations = runCatching { repository.related(series) }
            .getOrDefault(emptyList())
            .filter { it.id != series.id }
            .distinctBy { it.id }
            .take(MAX_RECOMMENDATIONS)
            .map { it.toSearchResponse() }

        return newTvSeriesLoadResponse(
            name = series.title,
            url = seriesUrl(series),
            type = TvType.AsianDrama,
            episodes = episodes,
        ) {
            posterUrl = series.posterUrl
            plot = buildPlot(series)
            tags = series.genres
            year = series.year
            series.rating?.let { score = Score.from10(it) }
            this.recommendations = recommendations
            comingSoon = episodes.isEmpty()
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (com.lagradost.cloudstream3.SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ): Boolean {
        val episode = repository.findEpisode(data)
            ?: throw ErrorLoadingException(
                "Episodio no reconocido. Vuelve a abrir la ficha desde HDrama."
            )

        episode.subtitles.forEach { subtitle ->
            subtitleCallback(
                newSubtitleFile(subtitle.lang, subtitle.url) {
                    headers = subtitle.headers
                }
            )
        }

        val sources = repository.resolve(episode)
        return when (val resolution = LinkResolver.resolve(sources)) {
            is LinkResolution.Playable -> {
                for (link in resolution.links) {
                    callback(link.toExtractorLink())
                }
                resolution.links.isNotEmpty()
            }

            is LinkResolution.Browser -> {
                throw ErrorLoadingException(
                    "${resolution.status.userMessage} " +
                        "Abre es.hdrama.net en el navegador para continuar."
                )
            }

            is LinkResolution.Failure -> {
                throw ErrorLoadingException(resolution.message)
            }
        }
    }

    private fun buildPlot(series: Series): String = buildString {
        append(series.synopsis.orEmpty().trim())
        when {
            series.demo -> append("\n\nDatos de demostracion local. No son contenido real de HDrama.")
            series.browserOnly -> append(
                "\n\nNo se pudo cargar el HTML de HDrama. Abre la pagina original en el navegador."
            )
            else -> {
                val source = series.source.takeIf { it.isNotBlank() && it != "HDrama" }
                append("\n\nFuente: HDrama")
                if (source != null) append(" · $source")
                append(". Reproduccion HLS desde la fuente original.")
            }
        }
    }.trim()

    private fun seriesUrl(series: Series): String =
        if (series.demo) {
            UrlUtils.demoSeriesUrl(series.id)
        } else {
            series.pageUrl ?: UrlUtils.seriesPageUrl(series.slug)
        }

    private fun Series.toSearchResponse(): SearchResponse =
        newTvSeriesSearchResponse(
            name = if (demo) "$title (demo)" else title,
            url = seriesUrl(this),
            type = TvType.AsianDrama,
            fix = false,
        ) {
            posterUrl = this@toSearchResponse.posterUrl
            year = this@toSearchResponse.year
            episodes = this@toSearchResponse.episodeCount
            score = this@toSearchResponse.rating?.let { Score.from10(it) }
        }

    private fun Episode.toCloudEpisode(series: Series) =
        this@HDramaProvider.newEpisode(
            url = this.id,
            initializer = {
                name = this@toCloudEpisode.title ?: "Capítulo ${this@toCloudEpisode.number}"
                episode = this@toCloudEpisode.number
                season = 1
                runTime = this@toCloudEpisode.durationSeconds
                    ?.let { seconds -> (seconds / 60).coerceAtLeast(1) }
                description = when {
                    series.demo -> "Episodio de demostracion (${series.title})."
                    else -> "Capítulo ${this@toCloudEpisode.number} de ${series.title}."
                }
                posterUrl = series.posterUrl
            },
            fix = false,
        )

    private suspend fun ResolvedLink.toExtractorLink(): ExtractorLink {
        val cloudType = when (type) {
            LinkType.HLS -> ExtractorLinkType.M3U8
            LinkType.MP4, LinkType.BROWSER -> ExtractorLinkType.VIDEO
        }
        val qualityValue = quality.takeIf { it > 0 } ?: Qualities.Unknown.value
        val isMirror = headers.keys.any { it.equals("Referer", ignoreCase = true) }
        val refererValue = referer ?: when (type) {
            LinkType.BROWSER -> UrlUtils.HDRA_BASE_URL
            else -> null
        }
        return newExtractorLink(
            source = SOURCE_NAME,
            name = if (isMirror) "$label (espejo)" else label,
            url = url,
            type = cloudType,
        ) {
            quality = qualityValue
            headers = this@toExtractorLink.headers
            refererValue?.let { referer = it }
        }
    }

    companion object {
        const val SOURCE_NAME = "HDrama"
        private const val MAX_RECOMMENDATIONS = 20
    }
}
