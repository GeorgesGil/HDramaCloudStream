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
 * 1. Catalogo real desde sitemaps publicos (sin eludir Cloudflare).
 * 2. Catalogo demo local si la red falla.
 * 3. Importacion manual de URLs para fichas concretas.
 *
 * El adaptador que descargaria HTML protegido sigue deshabilitado.
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
        val result = runCatching { sitemap.section(section, page) }
            .getOrElse { demo.section(section, page) }
        cache.put(key, result)
        return result
    }

    suspend fun search(query: String, page: Int): CatalogPage<Series> {
        val key = "search:${query.trim().lowercase()}:$page"
        @Suppress("UNCHECKED_CAST")
        cache.get(key)?.let { return it as CatalogPage<Series> }
        val result = runCatching { sitemap.search(query, page) }
            .getOrElse { demo.search(query, page) }
        cache.put(key, result)
        return result
    }

    suspend fun load(url: String?): Series? {
        val raw = url?.trim().orEmpty()
        if (raw.isBlank()) return null
        val series = when {
            UrlUtils.isDemoSeriesUrl(raw) -> demo.load(raw)
            UrlUtils.isAllowedHdramaUrl(raw) -> sitemap.load(raw) ?: hdrama.load(raw)
            else -> demo.load(raw)
        }
        if (series != null) cache.put("series:${series.id}", series)
        return series
    }

    suspend fun seriesById(id: String): Series? {
        @Suppress("UNCHECKED_CAST")
        cache.get("series:$id")?.let { return it as Series }
        val found = demo.byId(id)
            ?: sitemap.load(UrlUtils.seriesPageUrl(id))
            ?: hdrama.load(UrlUtils.seriesPageUrl(id))
        if (found != null) cache.put("series:$id", found)
        return found
    }

    suspend fun episodes(series: Series): List<Episode> {
        val adapter = when {
            series.demo -> demo
            series.browserOnly -> sitemap
            else -> hdrama
        }
        return adapter.episodes(series)
    }

    suspend fun resolve(episode: Episode): List<MediaSource> {
        val series = seriesById(episode.seriesId)
        return when {
            series?.demo == true -> demo.resolve(episode)
            series?.browserOnly == true -> sitemap.resolve(episode)
            else -> hdrama.resolve(episode)
        }
    }

    suspend fun findEpisode(data: String?): Episode? {
        val parsed = EpisodeId.parse(data) ?: return null
        val series = seriesById(parsed.seriesId) ?: return null
        return episodes(series).firstOrNull { it.number == parsed.number }
    }

    suspend fun related(series: Series): List<Series> =
        if (series.demo) demo.related(series) else emptyList()

    fun demoAdapter(): DemoCatalogAdapter = demo

    fun sitemapAdapter(): SitemapCatalogAdapter = sitemap

    fun hdramaAdapter(): HdramaCatalogAdapter = hdrama
}
