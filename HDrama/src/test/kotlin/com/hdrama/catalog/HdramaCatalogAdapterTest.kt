package com.hdrama.catalog

import com.hdrama.utils.AuthorizationPolicy
import com.hdrama.utils.AuthorizationStatus
import com.hdrama.utils.UnauthorizedAccessException
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class HdramaCatalogAdapterTest {
    private val adapter = HdramaCatalogAdapter()

    @Test
    fun `autorizacion deshabilitada por defecto`() {
        assertFalse(AuthorizationPolicy.isAuthorized())
        assertEquals(AuthorizationStatus.NOT_GRANTED, AuthorizationPolicy.status())
    }

    @Test
    fun `catalogo y busqueda lanzan error explicito sin autorizacion`(): Unit = runBlocking {
        val sectionError = runCatching { adapter.section(CatalogSection.NOVEDADES, 1) }.exceptionOrNull()
        assertIs<UnauthorizedAccessException>(sectionError)

        val searchError = runCatching { adapter.search("corazon", 1) }.exceptionOrNull()
        assertIs<UnauthorizedAccessException>(searchError)
    }

    @Test
    fun `importacion manual no descarga html ni requiere autorizacion`(): Unit = runBlocking {
        val series = adapter.load("https://es.hdrama.net/series/corazon-de-neon")
        assertNotNull(series)
        assertTrue(series.browserOnly)
        assertEquals("https://es.hdrama.net/series/corazon-de-neon", series.pageUrl)

        val episodes = adapter.episodes(series)
        assertEquals(1, episodes.size)
        assertIs<com.hdrama.models.MediaSource.BrowserOnly>(episodes.single().mediaSources.single())
    }

    @Test
    fun `la nota de autorizacion es explicita`() {
        assertTrue(adapter.authorizationNote().contains("autorizacion"))
    }
}
