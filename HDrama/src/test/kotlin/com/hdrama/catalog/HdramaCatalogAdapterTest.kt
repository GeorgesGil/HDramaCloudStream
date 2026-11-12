package com.hdrama.catalog

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class HdramaCatalogAdapterTest {
    private val adapter = HdramaCatalogAdapter()

    @Test
    fun `catalogo y busqueda informan funciones pendientes`(): Unit = runBlocking {
        val sectionError = runCatching { adapter.section(CatalogSection.NOVEDADES, 1) }.exceptionOrNull()
        assertIs<UnsupportedOperationException>(sectionError)

        val searchError = runCatching { adapter.search("corazon", 1) }.exceptionOrNull()
        assertIs<UnsupportedOperationException>(searchError)
    }

    @Test
    fun `importacion manual crea ficha de navegacion sin descargar html`(): Unit = runBlocking {
        val series = adapter.load("https://es.hdrama.net/series/corazon-de-neon")
        assertNotNull(series)
        assertTrue(series.browserOnly)
        assertEquals("https://es.hdrama.net/series/corazon-de-neon", series.pageUrl)

        val episodes = adapter.episodes(series)
        assertEquals(1, episodes.size)
        assertIs<com.hdrama.models.MediaSource.BrowserOnly>(episodes.single().mediaSources.single())
    }

    @Test
    fun `la nota de implementacion informa el estado actual`() {
        assertTrue(adapter.implementationNote().contains("pendiente"))
    }
}
