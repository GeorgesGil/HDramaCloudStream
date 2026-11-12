package com.hdrama.catalog

import com.hdrama.models.CatalogPage
import com.hdrama.models.Episode
import com.hdrama.models.MediaSource
import com.hdrama.models.Series
import com.hdrama.network.SitemapClient
import com.hdrama.network.SitemapEntry
import com.hdrama.network.SitemapSnapshot
import com.hdrama.network.SitemapSource
import com.hdrama.utils.TextNormalizer
import com.hdrama.utils.UrlUtils

/**
 * Catalogo real construido exclusivamente con los sitemaps publicos de HDrama.
 *
 * Aporta titulos (derivados del slug), URLs y orden de novedades/tendencias.
 * Esta implementacion utiliza los sitemaps; la obtencion de metadatos y
 * episodios desde las fichas HTML esta pendiente.
 */
class SitemapCatalogAdapter(
    private val client: SitemapSource = SitemapClient(),
    private val pageSize: Int = 12,
) : CatalogAdapter {
    override suspend fun section(section: CatalogSection, page: Int): CatalogPage<Series> {
        val snapshot = client.loadSnapshot()
        return when (section) {
            CatalogSection.DESTACADOS -> paginate(snapshot.trending.take(48).toSeries(), page)
            CatalogSection.NOVEDADES -> paginate(snapshot.newReleases.toSeries(), page)
            CatalogSection.MAS_VISTOS -> paginate(snapshot.trending.toSeries(), page)
            CatalogSection.TENDENCIAS -> paginate(snapshot.fresh.toSeries(), page)
            CatalogSection.CATALOGO -> paginate(
                snapshot.allSeries.toSeries().distinctBy { it.id }
                    .sortedBy { TextNormalizer.normalize(it.title) },
                page,
            )
            CatalogSection.GENEROS -> paginate(snapshot.genreCards(), page)
            CatalogSection.FUENTES -> paginate(snapshot.sourceCards(), page)
            CatalogSection.ACCESO -> paginate(AccessCards.all(), page)
        }
    }

    override suspend fun search(query: String, page: Int): CatalogPage<Series> {
        val needle = TextNormalizer.normalize(query)
        if (needle.isBlank()) return CatalogPage.empty(page)
        val snapshot = client.loadSnapshot()
        val found = snapshot.allSeries
            .toSeries()
            .distinctBy { it.id }
            .filter { series ->
                TextNormalizer.matches(series.title, needle) ||
                    TextNormalizer.matches(series.slug.replace('-', ' '), needle)
            }
            .sortedBy { TextNormalizer.normalize(it.title) }
        return paginate(found, page)
    }

    override suspend fun load(idOrUrl: String): Series? {
        val canonical = UrlUtils.normalizeHdramaUrl(idOrUrl) ?: return null
        val snapshot = runCatching { client.loadSnapshot() }.getOrNull()

        snapshot?.allSeries
            ?.firstOrNull { UrlUtils.normalizeHdramaUrl(it.url) == canonical }
            ?.toSeriesOrNull()
            ?.let { return it }

        val slug = UrlUtils.extractSeriesSlug(canonical)
        if (slug != null) {
            return snapshot?.allSeries
                ?.firstOrNull { UrlUtils.extractSeriesSlug(it.url) == slug }
                ?.toSeriesOrNull()
                ?: ManualHdramaImport.fromUrl(canonical)
        }

        return when (canonical.trimEnd('/')) {
            "${UrlUtils.HDRA_BASE_URL}categories" -> ManualHdramaImport.browserCardFromUrl(
                canonical,
                titleOverride = "Categorias y generos de HDrama",
            )
            "${UrlUtils.HDRA_BASE_URL}sources" -> ManualHdramaImport.browserCardFromUrl(
                canonical,
                titleOverride = "Fuentes originales de HDrama",
            )
            "${UrlUtils.HDRA_BASE_URL}guides" -> ManualHdramaImport.browserCardFromUrl(
                canonical,
                titleOverride = "Guias de HDrama",
            )
            else -> ManualHdramaImport.browserCardFromUrl(canonical)
        }
    }

    override suspend fun episodes(series: Series): List<Episode> =
        listOf(ManualHdramaImport.browserEpisode(series))

    override suspend fun resolve(episode: Episode): List<MediaSource> = episode.mediaSources

    override suspend fun related(series: Series): List<Series> = emptyList()

    private fun paginate(items: List<Series>, page: Int): CatalogPage<Series> {
        val safePage = page.coerceAtLeast(1)
        val from = (safePage - 1) * pageSize
        if (from >= items.size) return CatalogPage(emptyList(), safePage, false)
        val slice = items.drop(from).take(pageSize)
        return CatalogPage(slice, safePage, from + slice.size < items.size)
    }

    private fun List<SitemapEntry>.toSeries(): List<Series> =
        mapNotNull { it.toSeriesOrNull() }

    private fun SitemapEntry.toSeriesOrNull(): Series? {
        val canonical = UrlUtils.normalizeHdramaUrl(url) ?: return null
        val slug = UrlUtils.extractSeriesSlug(canonical) ?: return null
        return Series(
            id = slug,
            slug = slug,
            title = TextNormalizer.titleFromSlug(slug),
            synopsis = "Serie del catalogo publico de HDrama. " +
                "Abre la ficha original en el navegador para ver portada, sinopsis y episodios.",
            genres = emptyList(),
            language = "es",
            source = "HDrama (sitemap publico)",
            demo = false,
            browserOnly = true,
            pageUrl = canonical,
        )
    }

    private fun SitemapSnapshot.genreCards(): List<Series> = listOfNotNull(
        ManualHdramaImport.browserCardFromUrl(
            "${UrlUtils.HDRA_BASE_URL}categories",
            titleOverride = "Categorias y generos de HDrama",
        )
    )

    private fun SitemapSnapshot.sourceCards(): List<Series> = listOfNotNull(
        ManualHdramaImport.browserCardFromUrl(
            "${UrlUtils.HDRA_BASE_URL}sources",
            titleOverride = "Fuentes originales de HDrama",
        )
    )
}
