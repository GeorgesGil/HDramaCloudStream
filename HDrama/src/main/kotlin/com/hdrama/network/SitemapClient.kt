package com.hdrama.network

import com.hdrama.utils.UrlUtils

/** Fuente de snapshots de sitemap, inyectable en pruebas. */
interface SitemapSource {
    suspend fun loadSnapshot(forceRefresh: Boolean = false): SitemapSnapshot
}

/**
 * Cliente de sitemaps publicos de HDrama.
 *
 * Solo descarga XML de sitemaps declarados en robots.txt. No solicita HTML de
 * series ni intenta superar Cloudflare. Cachea el snapshot durante 15 minutos.
 */
class SitemapClient(
    private val http: SafeHttpClient = SafeHttpClient(UrlConnectionHttpTransport()),
    private val indexUrl: String = "${UrlUtils.HDRA_BASE_URL}sitemap.xml",
    private val cache: TtlCache<String, SitemapSnapshot> = TtlCache(15 * 60 * 1000L, 4),
) : SitemapSource {
    override suspend fun loadSnapshot(forceRefresh: Boolean): SitemapSnapshot {
        if (!forceRefresh) {
            cache.get(KEY)?.let { return it }
        }

        val indexXml = when (val result = http.getText(indexUrl)) {
            is HttpOutcome.Success -> result.body
            is HttpOutcome.Failure -> error("No se pudo leer el indice de sitemaps: ${result.reason}")
            is HttpOutcome.Blocked -> error("Sitemap bloqueado por la allowlist: ${result.reason}")
        }

        val children = SitemapParser.parseIndex(indexXml)
            .filter { UrlUtils.isAllowedHdramaUrl(it) }
        if (children.isEmpty()) error("El indice de sitemaps no contiene hijos validos.")

        val documents = children.associateWith { url ->
            when (val result = http.getText(url)) {
                is HttpOutcome.Success -> result.body
                is HttpOutcome.Failure -> null
                is HttpOutcome.Blocked -> null
            }
        }

        fun entriesFor(fragment: String): List<SitemapEntry> =
            documents.entries
                .firstOrNull { it.key.contains(fragment, ignoreCase = true) }
                ?.value
                ?.let(SitemapParser::parseUrlSet)
                .orEmpty()

        val snapshot = SitemapSnapshot(
            newReleases = entriesFor("new-es"),
            trending = entriesFor("trending-es"),
            fresh = entriesFor("fresh-es"),
            staticPages = entriesFor("static-es"),
        )
        cache.put(KEY, snapshot)
        return snapshot
    }

    private companion object {
        const val KEY = "snapshot"
    }
}
