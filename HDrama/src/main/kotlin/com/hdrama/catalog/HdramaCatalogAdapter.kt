package com.hdrama.catalog

import com.hdrama.models.CatalogPage
import com.hdrama.models.Episode
import com.hdrama.models.MediaSource
import com.hdrama.models.Series
import com.hdrama.network.TtlCache
import com.hdrama.network.WebFetchException
import com.hdrama.network.WebFetcher
import com.hdrama.network.WebFetcherFactory
import com.hdrama.parser.HdramaHtmlParser
import com.hdrama.parser.HdramaSeriesPage
import com.hdrama.sources.HdramaSourceClient
import com.hdrama.sources.SourceProbe
import com.hdrama.network.SourceProbeFactory
import com.hdrama.utils.EpisodeId
import com.hdrama.utils.UrlUtils
import kotlinx.coroutines.CancellationException

/**
 * Integracion real con el HTML de HDrama.
 *
 * Flujo:
 * 1. [WebFetcher] descarga paginas usando la sesion de CloudStream (WebView +
 *    cookies de Cloudflare).
 * 2. [HdramaHtmlParser] extrae JSON-LD, tarjetas, fichas y episodios.
 * 3. [HdramaSourceClient] resuelve el HLS del episodio contra
 *    `/api/episode-source/...` y descifra el payload.
 *
 * Si la red o el challenge fallan, [load] cae a la ficha de navegacion de
 * [ManualHdramaImport] y el [CatalogRepository] cae al catalogo de sitemaps.
 */
class HdramaCatalogAdapter(
    private val fetcher: WebFetcher = WebFetcherFactory.create(),
    private val parser: HdramaHtmlParser = HdramaHtmlParser(),
    sourceClient: HdramaSourceClient? = null,
    private val htmlCache: TtlCache<String, String> = TtlCache(HTML_TTL_MS, 24),
    private val pageCache: TtlCache<String, HdramaSeriesPage> = TtlCache(HTML_TTL_MS, 24),
    private val browseCache: TtlCache<String, BrowsePage> = TtlCache(HTML_TTL_MS, 12),
    private val sourceCache: TtlCache<String, List<MediaSource>> = TtlCache(SOURCE_TTL_MS, 64),
    probe: SourceProbe? = null,
) : CatalogAdapter {

    /**
     * Antes de llamar a `/api/episode-source` se carga el inicio del sitio
     * (cacheado) para que la cookie de Cloudflare exista: el WAF de `/api/` no
     * resuelve challenges, asi que un WebView lanzado sobre esa ruta solo
     * perderia tiempo y acabaria en 403.
     */
    private val sourceClient: HdramaSourceClient =
        sourceClient ?: HdramaSourceClient(fetcher, warmUp = ::warmUpSession)

    /** Sonda que descarta enlaces muertos antes de entregarlos al player. */
    private val sourceProbe: SourceProbe = probe ?: SourceProbeFactory.create()

    override suspend fun section(section: CatalogSection, page: Int): CatalogPage<Series> {
        val safePage = page.coerceAtLeast(1)
        if (section == CatalogSection.ACCESO) {
            return paginate(AccessCards.all(), safePage)
        }
        if (safePage > 1 && (section == CatalogSection.DESTACADOS ||
                section == CatalogSection.GENEROS ||
                section == CatalogSection.FUENTES)
        ) {
            return CatalogPage.empty(safePage)
        }

        val url = sectionUrl(section, safePage)
        val html = fetchHtml(url)
        val items = when (section) {
            CatalogSection.DESTACADOS ->
                parser.parseHomeSection(html, DESTACADOS_TITLE, url)
            CatalogSection.GENEROS -> parser.parseGenreCards(html)
            CatalogSection.FUENTES -> parser.parseSourceCards(html)
            else -> parser.parseCollection(html, url)
        }.distinctBy { it.id }

        val hasNext = when (section) {
            CatalogSection.DESTACADOS,
            CatalogSection.GENEROS,
            CatalogSection.FUENTES,
            -> false
            else -> items.size >= HdramaRoutes.PAGE_SIZE
        }
        return CatalogPage(items, safePage, hasNext)
    }

    override suspend fun search(query: String, page: Int): CatalogPage<Series> {
        val clean = query.trim()
        if (clean.isBlank() || page > 1) return CatalogPage.empty(page.coerceAtLeast(1))
        val url = HdramaRoutes.search(clean)
        val html = fetchHtml(url)
        val items = parser.parseSearchResults(html, url).distinctBy { it.id }
        return CatalogPage(items, 1, false)
    }

    override suspend fun load(idOrUrl: String): Series? {
        val raw = idOrUrl.trim()
        if (raw.isBlank()) return null
        val canonical = UrlUtils.normalizeHdramaUrl(raw) ?: return null

        if (HdramaRoutes.isEpisodeUrl(canonical)) {
            val seriesUrl = HdramaRoutes.seriesUrlOfEpisode(canonical) ?: return null
            return loadSeries(seriesUrl)
        }
        if (UrlUtils.isSeriesPage(canonical)) {
            return loadSeries(canonical)
        }
        return loadBrowse(canonical)
    }

    override suspend fun episodes(series: Series): List<Episode> {
        if (series.demo) return emptyList()
        if (series.browserOnly) return listOf(ManualHdramaImport.browserEpisode(series))
        // Las paginas de navegacion (generos/fuentes) no tienen episodios.
        if (browseCache.get(series.id) != null) return emptyList()
        val page = loadSeriesPage(series.pageUrl ?: HdramaRoutes.series(series.slug))
        return page?.episodes.orEmpty()
    }

    override suspend fun resolve(episode: Episode): List<MediaSource> {
        val cacheKey = "${episode.seriesId}|${episode.number}|${episode.externalId.orEmpty()}"
        sourceCache.get(cacheKey)?.let { return it }

        val external = episode.externalId ?: resolveExternalId(episode)
        val parsed = parseExternalId(external)
        if (parsed == null) {
            android.util.Log.w(
                "HDrama",
                "Episodio sin fuente: number=${episode.number} externalId=${episode.externalId}",
            )
            return listOf(
                MediaSource.Unavailable(
                    "No se pudo identificar la fuente del episodio en HDrama."
                )
            )
        }

        val rawSources = sourceClient.sources(parsed.first, parsed.second)
        // La cadena de HDrama puede traer enlaces caducados: se comprueba cada
        // URL y solo se entregan las que responden. Si ninguna responde se
        // entregan todas para que el player muestre el error real.
        val sources = runCatching { sourceProbe.filterPlayable(rawSources) }
            .getOrDefault(rawSources)
        sourceCache.put(cacheKey, sources)
        return sources
    }

    override suspend fun related(series: Series): List<Series> {
        if (series.demo) return emptyList()
        browseCache.get(series.id)?.let { return it.items }
        val page = loadSeriesPage(series.pageUrl ?: HdramaRoutes.series(series.slug))
        return page?.related.orEmpty()
    }

    /** Nota de estado usada por diagnosticos y pruebas. */
    fun implementationNote(): String =
        "Catalogo, fichas, episodios y fuentes HLS integrados desde el HTML real de HDrama."

    /**
     * Carga el inicio del sitio para asegurar la sesion de Cloudflare antes de
     * llamar a la API de fuentes. Si falla, el episodio mostrara un mensaje
     * claro y el catalogo seguira usando el respaldo por sitemaps.
     */
    private suspend fun warmUpSession() {
        runCatching { fetchHtml(UrlUtils.HDRA_BASE_URL) }
    }

    // ------------------------------------------------------------------
    // Navegacion y cache
    // ------------------------------------------------------------------

    private suspend fun loadSeries(url: String): Series? {
        val page = try {
            loadSeriesPage(url)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            null
        }
        return page?.series ?: ManualHdramaImport.fromUrl(url)
    }

    private suspend fun loadSeriesPage(url: String?): HdramaSeriesPage? {
        val canonical = UrlUtils.normalizeHdramaUrl(url) ?: return null
        pageCache.get(canonical)?.let { return it }
        val html = try {
            fetchHtml(canonical)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            android.util.Log.w("HDrama", "Ficha no disponible: ${error.message}")
            return null
        }
        val parsed = parser.parseSeriesPage(html, canonical) ?: return null
        pageCache.put(canonical, parsed)
        return parsed
    }

    private suspend fun loadBrowse(url: String): Series? {
        val canonical = UrlUtils.normalizeHdramaUrl(url) ?: return null
        val browseId = HdramaRoutes.browseId(canonical)
        browseCache.get(browseId)?.let { return it.series }

        val page = try {
            val html = fetchHtml(canonical)
            when {
                canonical.trimEnd('/').endsWith("/categories") ->
                    parser.parseGenreCards(html)
                canonical.trimEnd('/').endsWith("/sources") ->
                    parser.parseSourceCards(html)
                else -> parser.parseCollection(html, canonical)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            emptyList()
        }

        if (page.isEmpty()) {
            return ManualHdramaImport.browserCardFromUrl(canonical)
        }

        val browse = Series(
            id = browseId,
            slug = browseId,
            title = HdramaRoutes.browseTitle(canonical),
            synopsis = "Explora esta seleccion de HDrama desde el plugin. " +
                "Toca una serie en las recomendaciones para abrir su ficha completa.",
            genres = emptyList(),
            language = "es",
            source = "HDrama",
            posterUrl = page.firstOrNull { it.posterUrl != null }?.posterUrl,
            episodeCount = page.size,
            demo = false,
            browserOnly = false,
            pageUrl = canonical,
        )
        val entry = BrowsePage(browse, page)
        browseCache.put(browseId, entry)
        return browse
    }

    private suspend fun resolveExternalId(episode: Episode): String? {
        val page = loadSeriesPage(HdramaRoutes.series(episode.seriesId)) ?: return null
        return page.episodes
            .firstOrNull { it.number == episode.number }
            ?.externalId
            ?: episode.pageUrl?.let { pageUrl ->
                page.episodes.firstOrNull { it.pageUrl == pageUrl }?.externalId
            }
    }

    /**
     * Descarga el HTML con cache TTL.
     *
     * Si falta la sesion de Cloudflare, [WebFetchException] sube al llamador y
     * [CatalogRepository] usa el respaldo por sitemaps; el fetcher ya aplica su
     * propio backoff para no encadenar challenges fallidos.
     */
    private suspend fun fetchHtml(url: String): String {
        htmlCache.get(url)?.let { return it }
        val text = fetcher.fetch(url, HTML_HEADERS)
        htmlCache.put(url, text)
        return text
    }

    private fun sectionUrl(section: CatalogSection, page: Int): String = when (section) {
        CatalogSection.DESTACADOS -> HdramaRoutes.home()
        CatalogSection.NOVEDADES -> HdramaRoutes.category("new-releases", page)
        CatalogSection.MAS_VISTOS -> HdramaRoutes.category("popular", page)
        CatalogSection.TENDENCIAS -> HdramaRoutes.category("trending", page)
        CatalogSection.CATALOGO -> HdramaRoutes.seriesList(page)
        CatalogSection.GENEROS -> HdramaRoutes.categories()
        CatalogSection.FUENTES -> HdramaRoutes.sources()
        CatalogSection.ACCESO -> HdramaRoutes.home()
    }

    private fun paginate(items: List<Series>, page: Int): CatalogPage<Series> {
        val safePage = page.coerceAtLeast(1)
        val from = (safePage - 1) * HdramaRoutes.PAGE_SIZE
        if (from >= items.size) return CatalogPage(emptyList(), safePage, false)
        val slice = items.drop(from).take(HdramaRoutes.PAGE_SIZE)
        return CatalogPage(slice, safePage, from + slice.size < items.size)
    }

    /** Pagina de navegacion (genero, fuente u otra lista) con sus series. */
    data class BrowsePage(val series: Series, val items: List<Series>)


    private companion object {
        const val HTML_TTL_MS = 5 * 60 * 1000L
        const val SOURCE_TTL_MS = 5 * 60 * 1000L
        const val DESTACADOS_TITLE = "Destacados"

        val HTML_HEADERS = mapOf(
            "accept-language" to "es-ES,es;q=0.9",
            "referer" to UrlUtils.HDRA_BASE_URL,
        )

        /** `bookId:serial` tal como lo guarda el parser en `Episode.externalId`. */
        fun parseExternalId(value: String?): Pair<String, Int>? {
            val raw = value?.trim().orEmpty()
            if (raw.isEmpty()) return null
            val separator = raw.indexOf(':')
            if (separator <= 0) return null
            val bookId = raw.substring(0, separator).trim()
            val serial = raw.substring(separator + 1).trim().toIntOrNull() ?: return null
            if (bookId.isBlank() || serial < 0) return null
            return bookId to serial
        }

        @Suppress("unused")
        fun episodeIdFor(series: Series, number: Int): String = EpisodeId.encode(series.id, number)
    }
}
