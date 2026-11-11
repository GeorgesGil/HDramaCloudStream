package com.hdrama.network

import org.jsoup.Jsoup
import org.jsoup.parser.Parser

/** Entrada individual de un sitemap. */
data class SitemapEntry(
    val url: String,
    val lastModified: String? = null,
)

/** Conjunto de sitemaps usado por el catalogo real. */
data class SitemapSnapshot(
    val newReleases: List<SitemapEntry> = emptyList(),
    val trending: List<SitemapEntry> = emptyList(),
    val fresh: List<SitemapEntry> = emptyList(),
    val staticPages: List<SitemapEntry> = emptyList(),
) {
    val allSeries: List<SitemapEntry>
        get() = (newReleases + trending + fresh).distinctBy { it.url }
}

/**
 * Parser XML de sitemaps. Usa jsoup en modo XML, sin expresiones regulares
 * fragiles.
 */
object SitemapParser {
    fun isIndex(xml: String): Boolean =
        Jsoup.parse(xml, "", Parser.xmlParser()).selectFirst("sitemapindex") != null

    fun parseIndex(xml: String): List<String> {
        val document = Jsoup.parse(xml, "", Parser.xmlParser())
        return document.select("sitemap > loc")
            .map { it.text().trim() }
            .filter { it.isNotBlank() }
    }

    fun parseUrlSet(xml: String): List<SitemapEntry> {
        val document = Jsoup.parse(xml, "", Parser.xmlParser())
        return document.select("url").mapNotNull { node ->
            val loc = node.selectFirst("loc")?.text()?.trim() ?: return@mapNotNull null
            if (loc.isBlank()) return@mapNotNull null
            val lastmod = node.selectFirst("lastmod")?.text()?.trim()?.takeIf { it.isNotBlank() }
            SitemapEntry(loc, lastmod)
        }
    }
}
