package com.hdrama.catalog

import com.hdrama.models.CatalogPage
import com.hdrama.models.Episode
import com.hdrama.models.MediaSource
import com.hdrama.models.Series
import com.hdrama.network.TtlCache
import com.hdrama.utils.EpisodeId
import com.hdrama.utils.UrlUtils

/**
 * Repositorio de catalogo.
 *
 * Prioridad:
 * 1. HTML real de HDrama (metadatos, episodios y HLS).
 * 2. Sitemaps publicos (respaldo si Cloudflare/red fallan).
 * 3. Catalogo demo local (diagnostico y ultimo recurso).
 * 4. Importacion manual de URLs para fichas concretas.
 */
class CatalogRepository(
    private val demo: DemoCatalogAdapter = DemoCatalogAdapter(),
    private val sitemap: SitemapCatalogAdapter = SitemapCatalogAdapter(),
    private val hdrama: HdramaCatalogAdapter = HdramaCatalogAdapter(),
    private val cache: TtlCache<String, Any> = TtlCache(
        ttlMillis = 5 * 60 * 1000L,
        maxEntries = 64,
    ),
) {
    suspend fun section(section: CatalogSection, page: Int): CatalogPage<Series> {
        val key = "section:${section.key}:$page"
        @Suppress("UNCHECKED_CAST")
        cache.get(key)?.let { return it as CatalogPage<Series> }

        val live = runCatching { hdrama.section(section, page) }.getOrNull()
        val result = when {
            live != null && live.items.isNotEmpty() -> live
            else -> runCatching { sitemap.section(section, page) }
                .getOrElse { demo.section(section, page) }
        }
        cache.put(key, result)
        return result
    }

    suspend fun search(query: String, page: Int): CatalogPage<Series> {
        val key = "search:${query.trim().lowercase()}:$page"
        @Suppress("UNCHECKED_CAST")
        cache.get(key)?.let { return it as CatalogPage<Series> }

        val live = runCatching { hdrama.search(query, page) }.getOrNull()
        val result = when {
            live != null && live.items.isNotEmpty() -> live
            else -> runCatching { sitemap.search(query, page) }
                .getOrElse { demo.search(query, page) }
        }
        cache.put(key, result)
        return result
    }

    suspend fun load(url: String?): Series? {
        val raw = url?.trim().orEmpty()
        if (raw.isBlank()) return null
        val series = when {
            UrlUtils.isDemoSeriesUrl(raw) -> demo.load(raw)
            UrlUtils.isAllowedHdramaUrl(raw) -> hdrama.load(raw) ?: sitemap.load(raw)
            else -> demo.load(raw)
        }
        if (series != null) cache.put("series:${series.id}", series)
        return series
    }

    suspend fun seriesById(id: String): Series? {
        @Suppress("UNCHECKED_CAST")
        cache.get("series:$id")?.let { return it as Series }
        val found = demo.byId(id)
            ?: hdrama.load(UrlUtils.seriesPageUrl(id))
            ?: sitemap.load(UrlUtils.seriesPageUrl(id))
        if (found != null) cache.put("series:$id", found)
        return found
    }

    suspend fun episodes(series: Series): List<Episode> {
        val episodes = when {
            series.demo -> demo.episodes(series)
            series.browserOnly -> sitemap.episodes(series)
            else -> hdrama.episodes(series)
        }
        if (episodes.isNotEmpty() || series.browserOnly || series.demo) return episodes
        // Respaldo: si el HTML fallo, el sitemap ofrece navegacion al sitio.
        return runCatching { sitemap.episodes(series) }.getOrElse { emptyList() }
    }

    suspend fun resolve(episode: Episode): List<MediaSource> {
        val series = seriesById(episode.seriesId)
        return when {
            series?.demo == true -> demo.resolve(episode)
            episode.mediaSources.isNotEmpty() -> episode.mediaSources
            else -> hdrama.resolve(episode)
        }
    }

    suspend fun findEpisode(data: String?): Episode? {
        val parsed = EpisodeId.parse(data) ?: return null
        val series = seriesById(parsed.seriesId) ?: return null
        return episodes(series).firstOrNull { it.number == parsed.number }
    }

    suspend fun related(series: Series): List<Series> = when {
        series.demo -> demo.related(series)
        else -> runCatching { hdrama.related(series) }.getOrElse { emptyList() }
    }

    fun demoAdapter(): DemoCatalogAdapter = demo

    fun sitemapAdapter(): SitemapCatalogAdapter = sitemap

    fun hdramaAdapter(): HdramaCatalogAdapter = hdrama
}
