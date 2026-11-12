package com.hdrama.catalog

import com.hdrama.models.MediaSource
import com.hdrama.network.WebFetchException
import com.hdrama.network.WebFetcher
import com.hdrama.player.LinkResolution
import com.hdrama.player.LinkResolver
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Pruebas de integracion del adaptador HTML con un [WebFetcher] falso.
 *
 * Los HTML son recortes minimos pero realistas del sitio (JSON-LD, atributos
 * `data-serial`, rating y bookId escapado del payload RSC): no se usa red.
 */
class HdramaCatalogAdapterTest {

    private val encryptedSource =
        "mqDHwriFus8mkQo0eO8BMcLRbJnPJVbhugizaL7zCkqcdj3Rdv6cW2Xv4CqIhterddzzxu1GpAM7A8tD2sXXQAjnrN6M3GQ0IlfNUAGuuvBzvXk="

    private val decryptedUrl = "https://pl.shortdizilab.com/v1/flickreels/23935678.m3u8"

    private val collectionHtml = """
        <html><body><main>
        <script type="application/ld+json">{"@context":"https://schema.org","@graph":[
        {"@type":"CollectionPage","@id":"https://es.hdrama.net/category/new-releases#collection",
        "name":"Novedades","mainEntity":{"@type":"ItemList","numberOfItems":2,"itemListElement":[
        {"position":1,"url":"https://es.hdrama.net/series/serie-uno","name":"Serie Uno","image":"https://cdn.example/1.jpg"},
        {"position":2,"url":"https://es.hdrama.net/series/serie-dos","name":"Serie Dos","image":"https://cdn.example/2.jpg"}]}}]}</script>
        </main></body></html>
    """.trimIndent()

    private val searchHtml = """
        <html><body><main><div>
        <article class="group"><a aria-label="Serie Uno" href="/es/series/serie-uno">
          <img alt="Serie Uno" src="https://cdn.example/1.jpg"><span>EP 10</span><span>8.1</span>
        </a></article>
        <article class="group"><a aria-label="Otra Serie" href="/es/series/otra-serie">
          <img alt="Otra Serie" src="https://cdn.example/3.jpg">
        </a></article>
        </div></main></body></html>
    """.trimIndent()

    private val detailHtml = """
        <html><body><main>
        <script type="application/ld+json">{"@context":"https://schema.org","@graph":[
        {"@type":"TVSeries","@id":"https://es.hdrama.net/series/serie-uno#series","name":"Serie Uno",
        "url":"https://es.hdrama.net/series/serie-uno",
        "description":"Sinopsis real de la serie.",
        "image":"https://cdn.example/poster.jpg","numberOfEpisodes":2,"genre":["Venganza"]}]}</script>
        <h1>Serie Uno</h1>
        <div><span class="text-sm text-muted">Puntuación</span><span class="text-2xl text-rating">7.4</span></div>
        <dl><div><dt>Capítulos</dt><dd>2</dd></div>
        <div><dt>Géneros</dt><dd><span>Venganza</span></dd></div></dl>
        <a role="listitem" aria-label="Capítulo 1" data-serial="1" data-display="1" href="/es/series/serie-uno/episode-1">
          <span class="block truncate text-sm font-semibold">Capítulo 1</span><span class="text-muted">4:38</span></a>
        <a role="listitem" aria-label="Capítulo 2" data-serial="2" data-display="2" href="/es/series/serie-uno/episode-2">
          <span class="block truncate text-sm font-semibold">Capítulo 2</span><span class="text-muted">2:24</span></a>
        <h2>También te puede gustar</h2>
        <article class="group"><a aria-label="Serie Dos" href="/es/series/serie-dos"><img alt="Serie Dos" src="https://cdn.example/2.jpg"></a></article>
        <script>self.__next_f.push([1,"bookId\":\"15531\",\"slug\":\"serie-uno\",\"firstSerial\":1"])</script>
        </main></body></html>
    """.trimIndent()

    private val genresHtml = """
        <html><body><main><a href="/es/category/revenge"><h3>Venganza</h3></a>
        <a href="/es/category/romance"><h3>Romance</h3></a></main></body></html>
    """.trimIndent()

    private val apiJson = """
        {"descriptor":{"source":"FlickReels","chain":[
        {"enc":"$encryptedSource","type":"hls","source":"FlickReels","refresh":false,"refreshAfter":null},
        {"enc":"$encryptedSource","type":"hls","source":"FlickReels","refresh":true,"refreshAfter":1794444495}],
        "subtitle":null}}
    """.trimIndent()

    private class FakeFetcher(
        private val pages: Map<String, String>,
        private val failure: Throwable? = null,
    ) : WebFetcher {
        override suspend fun fetch(url: String, headers: Map<String, String>): String {
            failure?.let { throw it }
            pages.entries.firstOrNull { url.contains(it.key) }?.let { return it.value }
            throw WebFetchException(404, "sin fixture para $url")
        }
    }

    private fun adapter(fetcher: WebFetcher) = HdramaCatalogAdapter(fetcher = fetcher)

    private fun okFetcher() = FakeFetcher(
        mapOf(
            "/category/new-releases" to collectionHtml,
            "/search?q=" to searchHtml,
            "/series/serie-uno" to detailHtml,
            "/categories" to genresHtml,
            "/api/episode-source/15531/1" to apiJson,
        )
    )

    @Test
    fun `seccion novedades se construye desde el json-ld real`(): Unit = runBlocking {
        val page = adapter(okFetcher()).section(CatalogSection.NOVEDADES, 1)
        assertEquals(2, page.items.size)
        assertEquals("Serie Uno", page.items.first().title)
        assertEquals("serie-uno", page.items.first().slug)
        assertFalse(page.hasNext)
    }

    @Test
    fun `busqueda devuelve las tarjetas del html`(): Unit = runBlocking {
        val page = adapter(okFetcher()).search("serie", 1)
        assertEquals(2, page.items.size)
        assertEquals("Serie Uno", page.items.first().title)
        assertFalse(page.hasNext)
    }

    @Test
    fun `ficha real incluye metadatos bookId episodios y relacionadas`(): Unit = runBlocking {
        val adapter = adapter(okFetcher())
        val series = adapter.load("https://es.hdrama.net/series/serie-uno")
        assertNotNull(series)
        assertEquals("Serie Uno", series.title)
        assertEquals(7.4, series.rating ?: 0.0, 0.01)
        assertEquals("Venganza", series.genres.single())
        assertEquals("https://cdn.example/poster.jpg", series.posterUrl)
        assertEquals("15531", series.externalId)
        assertFalse(series.browserOnly)

        val episodes = adapter.episodes(series)
        assertEquals(listOf(1, 2), episodes.map { it.number })
        assertEquals("15531:1", episodes.first().externalId)
        assertEquals(278, episodes.first().durationSeconds)
        assertTrue(episodes.first().pageUrl?.endsWith("/episode-1") == true)

        val related = adapter.related(series)
        assertEquals(listOf("serie-dos"), related.map { it.slug })
    }

    @Test
    fun `resuelve hls descifrado desde la api de fuentes`(): Unit = runBlocking {
        val adapter = adapter(okFetcher())
        val series = adapter.load("https://es.hdrama.net/series/serie-uno")
        assertNotNull(series)
        val episode = adapter.episodes(series).first()

        val sources = adapter.resolve(episode)
        val hls = sources.filterIsInstance<MediaSource.Hls>()
        assertTrue(hls.isNotEmpty())
        assertTrue(hls.any { it.url == decryptedUrl })
        assertIs<LinkResolution.Playable>(LinkResolver.resolve(sources))
    }

    @Test
    fun `si el html falla la ficha queda como navegacion`(): Unit = runBlocking {
        val adapter = adapter(FakeFetcher(emptyMap(), WebFetchException(403, "challenge")))
        val series = adapter.load("https://es.hdrama.net/series/serie-uno")
        assertNotNull(series)
        assertTrue(series.browserOnly)
        assertEquals("Serie Uno", series.title)
    }

    @Test
    fun `catalogo de generos se ofrece como pagina navegable`(): Unit = runBlocking {
        val adapter = adapter(okFetcher())
        val series = adapter.load("https://es.hdrama.net/categories")
        assertNotNull(series)
        assertEquals("Géneros de HDrama", series.title)
        val related = adapter.related(series)
        assertTrue(related.isNotEmpty())
    }
}
