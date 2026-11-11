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
import com.hdrama.utils.AuthorizationPolicy
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
 * Estado actual:
 * - Catalogo demo local completamente funcional (datos sinteticos y videos
 *   publicos de prueba).
 * - Navegacion al sitio original e importacion manual de enlaces.
 * - Integracion directa con HDrama **deshabilitada** hasta autorizacion.
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

    override val mainPage = mainPageOf(
        CatalogSection.NOVEDADES.key to CatalogSection.NOVEDADES.displayName,
        CatalogSection.MAS_VISTOS.key to CatalogSection.MAS_VISTOS.displayName,
        CatalogSection.NUEVOS_SEMANA.key to CatalogSection.NUEVOS_SEMANA.displayName,
        CatalogSection.CATALOGO.key to CatalogSection.CATALOGO.displayName,
        CatalogSection.GENEROS.key to CatalogSection.GENEROS.displayName,
        CatalogSection.RECOMENDACIONES.key to CatalogSection.RECOMENDACIONES.displayName,
        CatalogSection.ACCESO.key to CatalogSection.ACCESO.displayName,
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

        if (UrlUtils.isSeriesPage(clean)) {
            val imported = ManualHdramaImport.fromUrl(clean) ?: return newSearchResponseList(emptyList(), false)
            return newSearchResponseList(listOf(imported.toSearchResponse()), false)
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

        val episodes = repository.episodes(series).map { it.toCloudEpisode(series) }
        val recommendations = repository.related(series).map { it.toSearchResponse() }
        val note = buildString {
            append(series.synopsis.orEmpty())
            if (series.demo) {
                append("\n\nDatos de demostracion local. No son contenido real de HDrama.")
            } else if (series.source.contains("sitemap", ignoreCase = true)) {
                append("\n\nCatalogo real obtenido del sitemap publico de HDrama. " +
                    "La ficha completa, portada y episodios requieren un canal de acceso autorizado.")
            } else {
                append("\n\nImportacion manual: no se descargo HTML de HDrama.")
            }
            if (!AuthorizationPolicy.isAuthorized()) {
                append("\nLa integracion con el HTML protegido no usa evasiones de Cloudflare.")
            }
        }.trim()

        return newTvSeriesLoadResponse(
            name = series.title,
            url = seriesUrl(series),
            type = TvType.AsianDrama,
            episodes = episodes,
        ) {
            posterUrl = series.posterUrl
            plot = note
            tags = series.genres
            score = Score.from10(series.rating)
            year = series.year
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
                        "Usa el boton 'Abrir en navegador' de la ficha para continuar."
                )
            }

            is LinkResolution.Failure -> {
                throw ErrorLoadingException(resolution.message)
            }
        }
    }

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
            score = Score.from10(this@toSearchResponse.rating)
        }

    private fun Episode.toCloudEpisode(series: Series) =
        this@HDramaProvider.newEpisode(
            url = this.id,
            initializer = {
                name = this@toCloudEpisode.title ?: "Episodio ${this@toCloudEpisode.number}"
                episode = this@toCloudEpisode.number
                season = 1
                runTime = this@toCloudEpisode.durationSeconds
                description = if (series.demo) {
                    "Episodio de demostracion (${series.title})."
                } else {
                    "Abre la pagina original de HDrama en el navegador."
                }
            },
            fix = false,
        )

    private suspend fun ResolvedLink.toExtractorLink(): ExtractorLink {
        val cloudType = when (type) {
            LinkType.HLS -> ExtractorLinkType.M3U8
            LinkType.MP4, LinkType.BROWSER -> ExtractorLinkType.VIDEO
        }
        val qualityValue = quality.takeIf { it > 0 } ?: Qualities.Unknown.value
        val refererValue = referer ?: when (type) {
            LinkType.BROWSER -> UrlUtils.HDRA_BASE_URL
            else -> null
        }
        return newExtractorLink(
            source = SOURCE_NAME,
            name = label,
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
    }
}
