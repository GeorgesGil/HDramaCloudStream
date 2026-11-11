package com.hdrama.catalog

import com.hdrama.models.MediaSource
import com.hdrama.network.SitemapEntry
import com.hdrama.network.SitemapSnapshot
import com.hdrama.network.SitemapSource
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SitemapCatalogAdapterTest {
    private val snapshot = SitemapSnapshot(
        newReleases = listOf(
            SitemapEntry("https://es.hdrama.net/series/corazon-de-neon", "2026-11-10"),
            SitemapEntry("https://es.hdrama.net/series/el-senor-del-norte", "2026-11-09"),
        ),
        trending = listOf(
            SitemapEntry("https://es.hdrama.net/series/la-heredera-de-papel", "2026-11-08"),
        ),
        fresh = listOf(
            SitemapEntry("https://es.hdrama.net/series/cuatro-reyes", "2026-11-07"),
        ),
        staticPages = listOf(
            SitemapEntry("https://es.hdrama.net/categories"),
        ),
    )

    private class FakeSource(private val snapshot: SitemapSnapshot) : SitemapSource {
        override suspend fun loadSnapshot(forceRefresh: Boolean): SitemapSnapshot = snapshot
    }

    private val adapter = SitemapCatalogAdapter(FakeSource(snapshot), pageSize = 1)

    @Test
    fun `catalogo real usa sitemap y pagina`(): Unit = runBlocking {
        val first = adapter.section(CatalogSection.NOVEDADES, 1)
        assertEquals(1, first.items.size)
        assertTrue(first.hasNext)
        assertEquals("Corazon De Neon", first.items.single().title)
        assertFalse(first.items.single().demo)

        val second = adapter.section(CatalogSection.NOVEDADES, 2)
        assertEquals(1, second.items.size)
        assertFalse(second.hasNext)
    }

    @Test
    fun `busca por titulo derivado del slug`(): Unit = runBlocking {
        val result = adapter.search("corazon", 1)
        assertEquals(1, result.items.size)
        assertEquals("corazon-de-neon", result.items.single().slug)
    }

    @Test
    fun `carga ficha real en modo navegador`(): Unit = runBlocking {
        val series = adapter.load("https://es.hdrama.net/series/corazon-de-neon")
        assertNotNull(series)
        assertTrue(series.browserOnly)
        assertEquals("HDrama (sitemap publico)", series.source)

        val episode = adapter.episodes(series).single()
        assertIs<MediaSource.BrowserOnly>(episode.mediaSources.single())
    }

    @Test
    fun `categorias se ofrecen como tarjeta de navegador`(): Unit = runBlocking {
        val categories = adapter.section(CatalogSection.GENEROS, 1)
        assertTrue(categories.items.isNotEmpty())
        assertTrue(categories.items.any { it.title.contains("Categorias", ignoreCase = true) })
    }

    @Test
    fun `catalogo general deduplica entradas`(): Unit = runBlocking {
        val page = adapter.section(CatalogSection.CATALOGO, 1)
        assertTrue(page.items.isNotEmpty())
        assertEquals(page.items.distinctBy { it.id }.size, page.items.size)
    }
}
