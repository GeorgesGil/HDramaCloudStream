package com.hdrama.catalog

import com.hdrama.models.MediaSource
import com.hdrama.utils.UrlUtils
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DemoCatalogAdapterTest {
    private val catalog = DemoCatalogAdapter(pageSize = 12)

    @Test
    fun `pagina secciones y respeta hasNext`(): Unit = runBlocking {
        val first = catalog.section(CatalogSection.NOVEDADES, 1)
        assertEquals(12, first.items.size)
        assertTrue(first.hasNext)

        val second = catalog.section(CatalogSection.NOVEDADES, 2)
        assertEquals(5, second.items.size)
        assertFalse(second.hasNext)
        assertEquals(17, (first.items + second.items).distinctBy { it.id }.size)
    }

    @Test
    fun `busca sin acentos ni mayusculas`(): Unit = runBlocking {
        val neon = catalog.search("corazon", 1)
        assertTrue(neon.items.any { it.title == "Corazón de Neón" })

        val senor = catalog.search("SENOR", 1)
        assertTrue(senor.items.any { it.title == "El Señor del Norte" })

        val jardin = catalog.search("jardin", 1)
        assertTrue(jardin.items.any { it.title == "El Jardín de Medianoche" })
    }

    @Test
    fun `busqueda paginada`(): Unit = runBlocking {
        val paged = DemoCatalogAdapter(pageSize = 2)
        val first = paged.search("de", 1)
        assertEquals(2, first.items.size)
        assertTrue(first.hasNext)
        val second = paged.search("de", 2)
        assertTrue(second.items.isNotEmpty())
    }

    @Test
    fun `carga serie demo y episodios ordenados`(): Unit = runBlocking {
        val series = catalog.load(UrlUtils.demoSeriesUrl("corazon-de-neon"))
        assertNotNull(series)
        assertEquals(3, series.episodeCount)

        val episodes = catalog.episodes(series)
        assertEquals(listOf(1, 2, 3), episodes.map { it.number })
        assertIs<MediaSource.Mp4>(episodes[0].mediaSources.single())
        assertIs<MediaSource.Hls>(episodes[1].mediaSources.single())
    }

    @Test
    fun `series de prueba demo carga con episodios reproducibles`(): Unit = runBlocking {
        val series = catalog.load(UrlUtils.demoSeriesUrl("prueba-de-reproduccion"))
        assertNotNull(series)
        assertTrue(series.demo)
        assertEquals(2, series.episodeCount)
        assertEquals(listOf(1, 2), catalog.episodes(series).map { it.number })
    }

    @Test
    fun `carga importacion manual sin red`(): Unit = runBlocking {
        val series = catalog.load("https://es.hdrama.net/series/serie-importada-desconocida")
        assertNotNull(series)
        assertTrue(series.browserOnly)
        assertFalse(series.demo)
        assertEquals("Serie Importada Desconocida", series.title)

        val episode = catalog.episodes(series).single()
        assertIs<MediaSource.BrowserOnly>(episode.mediaSources.single())
    }

    @Test
    fun `rechaza dominios ajenos`(): Unit = runBlocking {
        assertNull(catalog.load("https://evil.example/series/x"))
    }

    @Test
    fun `maneja portadas ausentes`(): Unit = runBlocking {
        val series = catalog.load(UrlUtils.demoSeriesUrl("cuatro-reyes"))
        assertNotNull(series)
        assertNull(series.posterUrl)
    }

    @Test
    fun `series relacionadas existen`(): Unit = runBlocking {
        val series = catalog.load(UrlUtils.demoSeriesUrl("corazon-de-neon"))
        assertNotNull(series)
        val related = catalog.related(series)
        assertTrue(related.isNotEmpty())
        assertTrue(related.all { it.id != series.id })
    }

    @Test
    fun `seccion de acceso contiene navegacion e importacion`(): Unit = runBlocking {
        val access = catalog.section(CatalogSection.ACCESO, 1)
        assertEquals(3, access.items.size)
        assertTrue(access.items.all { it.browserOnly || it.demo })
        assertTrue(access.items.filter { it.browserOnly }.all { it.pageUrl?.startsWith("https://es.hdrama.net/") == true })
    }
}
