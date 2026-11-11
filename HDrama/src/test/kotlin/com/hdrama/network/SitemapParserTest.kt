package com.hdrama.network

import java.io.InputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SitemapParserTest {
    private fun fixture(name: String): String {
        val stream: InputStream = checkNotNull(
            javaClass.classLoader?.getResourceAsStream("fixtures/$name")
        ) { "Fixture no encontrada: $name" }
        return stream.bufferedReader().use { it.readText() }
    }

    @Test
    fun `detecta y parsea indice de sitemaps`() {
        val xml = fixture("sitemap-index.xml")
        assertTrue(SitemapParser.isIndex(xml))
        val children = SitemapParser.parseIndex(xml)
        assertEquals(4, children.size)
        assertTrue(children.all { it.startsWith("https://es.hdrama.net/sitemaps/") })
    }

    @Test
    fun `parsea urls y lastmod`() {
        val entries = SitemapParser.parseUrlSet(fixture("sitemap-new-es.xml"))
        assertEquals(2, entries.size)
        assertEquals("https://es.hdrama.net/series/corazon-de-neon", entries[0].url)
        assertEquals("2026-11-09T19:01:06Z", entries[0].lastModified)
    }
}
