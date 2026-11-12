package com.hdrama.parser

import com.hdrama.utils.EpisodeId
import java.io.InputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pruebas del parser HTML de HDrama con recortes reales del sitio
 * (es.hdrama.net, 2026-11-11).
 *
 * Todo el HTML viene de `src/test/resources/fixtures/hdrama-real-*.html`:
 * no hay peticiones de red. El parser debe ser tolerante: ante HTML vacio,
 * roto o con campos ausentes devuelve null / listas vacias, nunca lanza.
 */
class HdramaHtmlParserTest {

    private val parser = HdramaHtmlParser()

    private val detalleUrl =
        "https://es.hdrama.net/series/la-novia-sustituta-conquista-al-don-doblado"

    private val slugDetalle = "la-novia-sustituta-conquista-al-don-doblado"

    private fun fixture(nombre: String): String {
        val stream: InputStream = checkNotNull(
            javaClass.classLoader?.getResourceAsStream("fixtures/$nombre")
        ) { "Fixture no encontrada: $nombre" }
        return stream.bufferedReader().use { it.readText() }
    }

    private fun detalle(): String = fixture("hdrama-real-detail.html")

    // ------------------------------------------------------------------
    // Ficha de serie
    // ------------------------------------------------------------------

    @Test
    fun `ficha real extrae titulo sinopsis nota generos poster y recuento`() {
        val page = parser.parseSeriesPage(detalle(), detalleUrl)
        assertNotNull(page)
        val series = page.series

        assertEquals("La novia sustituta conquista al Don(Doblado)", series.title)
        assertEquals(slugDetalle, series.id)
        assertEquals(slugDetalle, series.slug)
        assertNotNull(series.synopsis)
        assertTrue(series.synopsis.startsWith("A los doce, Juana fue vendida"))
        assertEquals(7.4, series.rating)
        assertEquals(listOf("Venganza"), series.genres)
        assertEquals(
            "https://zshipubcf.farsunpteltd.com/playlet/1793701634_RaHis5zDEx.jpg",
            series.posterUrl,
        )
        assertEquals(75, series.episodeCount)
        assertEquals("es", series.language)
        assertEquals("HDrama", series.source)
        assertFalse(series.demo)
        assertFalse(series.browserOnly)
        assertEquals(detalleUrl, series.pageUrl)
    }

    @Test
    fun `ficha real extrae bookId y lo expone como externalId de la serie`() {
        val page = parser.parseSeriesPage(detalle(), detalleUrl)
        assertNotNull(page)

        assertEquals("15531", page.bookId)
        assertEquals("15531", page.series.externalId)
        assertEquals("15531", parser.parseBookId(detalle(), slugDetalle))
    }

    @Test
    fun `ficha real extrae episodios con serial titulo duracion y externalId`() {
        val page = parser.parseSeriesPage(detalle(), detalleUrl)
        assertNotNull(page)

        val episodios = page.episodes
        assertEquals(3, episodios.size)
        assertEquals(listOf(1, 2, 3), episodios.map { it.number })
        assertEquals(
            listOf("Capítulo 1", "Capítulo 2", "Capítulo 3"),
            episodios.map { it.title },
        )
        assertEquals(listOf(278, 144, 163), episodios.map { it.durationSeconds })
        assertEquals(
            listOf("15531:1", "15531:2", "15531:3"),
            episodios.map { it.externalId },
        )
        assertEquals(
            listOf(
                EpisodeId.encode(slugDetalle, 1),
                EpisodeId.encode(slugDetalle, 2),
                EpisodeId.encode(slugDetalle, 3),
            ),
            episodios.map { it.id },
        )
        assertTrue(episodios.all { it.seriesId == slugDetalle })
        assertTrue(episodios[0].pageUrl?.endsWith("/episode-1") == true)
        assertTrue(episodios[2].pageUrl?.endsWith("/episode-3") == true)
        // La capa de red resuelve las fuentes: el parser no inventa media.
        assertTrue(episodios.all { it.mediaSources.isEmpty() })
        assertTrue(episodios.all { it.subtitles.isEmpty() })
    }

    @Test
    fun `ficha real incluye relacionadas y no repite la propia serie`() {
        val page = parser.parseSeriesPage(detalle(), detalleUrl)
        assertNotNull(page)

        assertTrue(page.related.isNotEmpty())
        assertTrue(page.related.none { it.slug == slugDetalle })
        assertTrue(page.related.none { it.id == slugDetalle })
        assertEquals(page.related.size, page.related.distinctBy { it.id }.size)
        assertEquals(
            "le-pegas-a-mi-hija-adios-a-tu-empleo-doblado",
            page.related.first().slug,
        )
        assertEquals(
            "¿Le pegas a mi hija? ¡Adiós a tu empleo!(Doblado)",
            page.related.first().title,
        )
        assertEquals("HDrama", page.related.first().source)
    }

    @Test
    fun `parseRelated respeta excludeSlug y devuelve vacio sin seccion`() {
        val html = detalle()

        val todas = parser.parseRelated(html, detalleUrl)
        assertEquals(3, todas.size)

        val sinPrimera = parser.parseRelated(
            html,
            detalleUrl,
            excludeSlug = "le-pegas-a-mi-hija-adios-a-tu-empleo-doblado",
        )
        assertEquals(2, sinPrimera.size)
        assertTrue(sinPrimera.none { it.slug == "le-pegas-a-mi-hija-adios-a-tu-empleo-doblado" })

        assertTrue(parser.parseRelated("<html><body></body></html>", detalleUrl).isEmpty())
    }

    @Test
    fun `ficha sin metadatos deja los campos opcionales en null sin lanzar`() {
        val html = "<html><body><h1>Solo titulo</h1></body></html>"
        val page = parser.parseSeriesPage(html, "https://es.hdrama.net/series/solo-titulo")
        assertNotNull(page)

        assertEquals("Solo titulo", page.series.title)
        assertNull(page.series.synopsis)
        assertNull(page.series.rating)
        assertTrue(page.series.genres.isEmpty())
        assertNull(page.series.posterUrl)
        assertNull(page.series.episodeCount)
        assertNull(page.series.externalId)
        assertNull(page.bookId)
        assertTrue(page.episodes.isEmpty())
        assertTrue(page.related.isEmpty())
    }

    @Test
    fun `ficha sin evidencia de serie devuelve null`() {
        assertNull(parser.parseSeriesPage("<html><body></body></html>", detalleUrl))
        assertNull(parser.parseSeriesPage("", detalleUrl))
    }

    @Test
    fun `usa fallbackTitle cuando el html no trae h1`() {
        val html = """
            <html><body>
            <a data-serial="1" data-display="1" aria-label="Capítulo 1"
               href="/es/series/serie-fallback/episode-1">
              <span class="block truncate">Capítulo 1</span>
              <span class="tabular-nums">1:00</span>
            </a>
            </body></html>
        """.trimIndent()

        val page = parser.parseSeriesPage(
            html,
            "https://es.hdrama.net/series/serie-fallback",
            fallbackTitle = "Serie Fallback",
        )
        assertNotNull(page)
        assertEquals("Serie Fallback", page.series.title)
        assertEquals("serie-fallback", page.series.slug)
        assertEquals(1, page.episodes.size)
    }

    // ------------------------------------------------------------------
    // bookId del payload RSC
    // ------------------------------------------------------------------

    @Test
    fun `parsea bookId adyacente al slug con comillas escapadas`() {
        val html = """
            <html><body><h1>Serie Uno</h1>
            <script>self.__next_f.push([1,"{\"bookId\":\"15531\",\"slug\":\"serie-uno\"}"])</script>
            </body></html>
        """.trimIndent()

        assertEquals("15531", parser.parseBookId(html, "serie-uno"))
    }

    @Test
    fun `elige el bookId mas cercano cuando hay varios y el slug esta lejos`() {
        val html = """
            <html><body><h1>La novia</h1>
            <script>self.__next_f.push([1,"{\"bookId\":\"111\",\"slug\":\"otra-serie\"}"])</script>
            <script>self.__next_f.push([1,"${"x".repeat(400)}\", \"bookId\":\"15531\"])</script>
            <script>self.__next_f.push([1,"{\"slug\":\"la-novia-sustituta\",\"firstSerial\":1}"])</script>
            </body></html>
        """.trimIndent()

        assertEquals("15531", parser.parseBookId(html, "la-novia-sustituta"))
    }

    @Test
    fun `bookId ausente devuelve null`() {
        assertNull(parser.parseBookId("<html><body><h1>Sin payload</h1></body></html>", "x"))
        assertNull(parser.parseBookId("", null))
        // La fixture de listado no tiene payload RSC de ficha.
        assertNull(parser.parseBookId(fixture("hdrama-real-collection.html"), null))
    }

    // ------------------------------------------------------------------
    // Orden y serial de episodios
    // ------------------------------------------------------------------

    @Test
    fun `ordena episodios numericamente aunque el html venga desordenado`() {
        val html = """
            <html><body><h1>Orden</h1>
            <a data-serial="3" data-display="3" aria-label="Capítulo 3"
               href="/es/series/orden/episode-3">
              <span class="block truncate">Capítulo 3</span><span class="tabular-nums">1:03</span>
            </a>
            <a data-serial="1" data-display="1" aria-label="Capítulo 1"
               href="/es/series/orden/episode-1">
              <span class="block truncate">Capítulo 1</span><span class="tabular-nums">4:38</span>
            </a>
            <a data-serial="2" data-display="2" aria-label="Capítulo 2"
               href="/es/series/orden/episode-2">
              <span class="block truncate">Capítulo 2</span><span class="tabular-nums">2:24</span>
            </a>
            </body></html>
        """.trimIndent()

        val page = parser.parseSeriesPage(html, "https://es.hdrama.net/series/orden")
        assertNotNull(page)
        assertEquals(listOf(1, 2, 3), page.episodes.map { it.number })
        assertEquals(listOf(278, 144, 63), page.episodes.map { it.durationSeconds })
    }

    @Test
    fun `externalId usa el serial real aunque el numero mostrado sea otro`() {
        val html = """
            <html><body><h1>Serie</h1>
            <script>self.__next_f.push([1,"{\"bookId\":\"15531\",\"slug\":\"serie\"}"])</script>
            <a data-serial="42" data-display="1" aria-label="Capítulo 1"
               href="/es/series/serie/episode-1">
              <span class="block truncate">Capítulo 1</span><span class="tabular-nums">4:38</span>
            </a>
            </body></html>
        """.trimIndent()

        val page = parser.parseSeriesPage(html, "https://es.hdrama.net/series/serie")
        assertNotNull(page)
        val episodio = page.episodes.single()
        assertEquals(1, episodio.number)
        assertEquals("15531:42", episodio.externalId)
        assertEquals(EpisodeId.encode("serie", 1), episodio.id)
    }

    // ------------------------------------------------------------------
    // Listados
    // ------------------------------------------------------------------

    @Test
    fun `collection reconstruye urls canonicas del json-ld con host hdrama_net`() {
        val series = parser.parseCollection(
            fixture("hdrama-real-collection.html"),
            "https://es.hdrama.net/series",
        )

        assertEquals(3, series.size)
        val primera = series.first()
        assertEquals(slugDetalle, primera.slug)
        assertEquals("La novia sustituta conquista al Don(Doblado)", primera.title)
        assertEquals(
            "https://es.hdrama.net/series/la-novia-sustituta-conquista-al-don-doblado",
            primera.pageUrl,
        )
        assertEquals(
            "https://zshipubcf.farsunpteltd.com/playlet/1793701634_RaHis5zDEx.jpg",
            primera.posterUrl,
        )
        assertEquals("HDrama", primera.source)
        assertFalse(primera.demo)

        assertEquals(
            "la-prision-mas-letal-la-directora-esta-a-mis-pies-doblado",
            series[1].slug,
        )
        assertTrue(series[1].posterUrl?.startsWith("https://hwztchapter.dramaboxdb.com") == true)
        assertEquals(3, series.distinctBy { it.id }.size)
    }

    @Test
    fun `collection cae a las tarjetas cuando falta el json-ld`() {
        val sinJsonLd = fixture("hdrama-real-collection.html").replace(
            Regex("""<script type="application/ld\+json">.*?</script>""", RegexOption.DOT_MATCHES_ALL),
            "",
        )
        assertFalse(sinJsonLd.contains("application/ld+json"))

        val series = parser.parseCollection(sinJsonLd, "https://es.hdrama.net/series")
        assertEquals(3, series.size)
        assertEquals(slugDetalle, series.first().slug)
        assertEquals("La novia sustituta conquista al Don(Doblado)", series.first().title)
        assertEquals(7.4, series.first().rating)
        assertEquals(75, series.first().episodeCount)
        assertTrue(series.first().pageUrl?.endsWith("/series/$slugDetalle") == true)
        // El respaldo por tarjetas prefiere el candidato 256w del srcset.
        assertTrue(series[1].posterUrl?.contains("@w=256") == true)
    }

    @Test
    fun `busqueda extrae las tarjetas con titulo poster nota y episodios`() {
        val series = parser.parseSearchResults(
            fixture("hdrama-real-search.html"),
            "https://es.hdrama.net/es/search?q=venganza",
        )

        assertEquals(3, series.size)
        val primera = series.first()
        assertEquals("general-protector-la-venganza-en-binmar", primera.slug)
        assertEquals("General Protector: La Venganza en Binmar", primera.title)
        assertEquals(8.6, primera.rating)
        assertEquals(101, primera.episodeCount)
        // El póster del card debe salir del `img[src|srcset]` real del CDN;
        // con srcset se prefiere el candidato 256w (aunque la URL lleve comas).
        assertTrue(primera.posterUrl?.contains("w_256") == true)
        assertTrue(primera.pageUrl?.endsWith("/series/${primera.slug}") == true)
        assertEquals("HDrama", primera.source)

        assertEquals(
            "venganza-y-amor-en-el-mundo-mafioso",
            series[1].slug,
        )
        assertEquals(
            "doblado-venganza-y-amor-en-el-mundo-mafioso",
            series[2].slug,
        )
    }

    @Test
    fun `busqueda con html vacio o sin tarjetas devuelve lista vacia`() {
        assertTrue(parser.parseSearchResults("", "https://es.hdrama.net/es/search?q=x").isEmpty())
        assertTrue(
            parser.parseSearchResults(
                "<html><body><h1>Sin resultados</h1></body></html>",
                "https://es.hdrama.net/es/search?q=x",
            ).isEmpty()
        )
    }

    // ------------------------------------------------------------------
    // Portada
    // ------------------------------------------------------------------

    @Test
    fun `portada extrae destacados y novedades desde el dom`() {
        val html = fixture("hdrama-real-home.html")
        val pageUrl = "https://es.hdrama.net/"

        val destacados = parser.parseHomeSection(html, "Destacados", pageUrl)
        assertEquals(2, destacados.size)
        assertEquals(slugDetalle, destacados.first().slug)
        assertEquals("La novia sustituta conquista al Don(Doblado)", destacados.first().title)
        assertEquals(7.4, destacados.first().rating)
        assertEquals(
            "https://zshipubcf.farsunpteltd.com/playlet/1793701634_RaHis5zDEx.jpg",
            destacados.first().posterUrl,
        )

        val novedades = parser.parseHomeSection(html, "Novedades", pageUrl)
        assertEquals(2, novedades.size)
        assertEquals(
            listOf(
                "lazos-rotos-de-los-hermanos-fae",
                "la-novia-hacker-del-jefe-mafioso",
            ),
            novedades.map { it.slug },
        )
        assertEquals("Lazos rotos de los hermanos fae", novedades.first().title)
    }

    @Test
    fun `portada extrae mas vistos y devuelve vacio para secciones ausentes`() {
        val html = fixture("hdrama-real-home.html")
        val pageUrl = "https://es.hdrama.net/"

        val masVistos = parser.parseHomeSection(html, "Más vistos", pageUrl)
        assertEquals(1, masVistos.size)
        assertEquals(
            "nacida-para-ser-la-alma-gemela-del-rey-alfa-31001842546",
            masVistos.single().slug,
        )
        assertEquals("Nacida para ser la alma gemela del Rey Alfa", masVistos.single().title)

        assertTrue(parser.parseHomeSection(html, "Idiomas", pageUrl).isEmpty())
        assertTrue(parser.parseHomeSection(html, "No existe", pageUrl).isEmpty())
    }

    // ------------------------------------------------------------------
    // Directores de generos y fuentes
    // ------------------------------------------------------------------

    @Test
    fun `directorio de generos devuelve tarjetas navegables`() {
        val generos = parser.parseGenreCards(fixture("hdrama-real-directories.html"))

        assertEquals(2, generos.size)
        assertEquals(listOf("genero-revenge", "genero-romance"), generos.map { it.id })
        assertEquals(
            listOf("Venganza y Revancha", "Romance y Amor Dulce"),
            generos.map { it.title },
        )
        assertEquals(
            "https://es.hdrama.net/category/revenge",
            generos.first().pageUrl,
        )
        assertEquals("genero-revenge", generos.first().slug)
        assertNull(generos.first().posterUrl)
    }

    @Test
    fun `directorio de fuentes devuelve tarjetas navegables`() {
        val fuentes = parser.parseSourceCards(fixture("hdrama-real-directories.html"))

        assertEquals(2, fuentes.size)
        assertEquals(listOf("fuente-flickreels", "fuente-dramabox"), fuentes.map { it.id })
        assertEquals(listOf("FlickReels", "DramaBox"), fuentes.map { it.title })
        assertEquals(
            "https://es.hdrama.net/source/flickreels",
            fuentes.first().pageUrl,
        )
    }

    // ------------------------------------------------------------------
    // Tolerancia a HTML invalido
    // ------------------------------------------------------------------

    @Test
    fun `html vacio roto o sin datos no lanza en ningun metodo`() {
        val entradas = listOf(
            "",
            "<html><body>",
            "<<<>>> no es html",
            "<html><body><h1></h1></body></html>",
        )
        for (html in entradas) {
            assertNull(
                parser.parseSeriesPage(html, "https://es.hdrama.net/series/inexistente"),
                "parseSeriesPage deberia ser null para: $html",
            )
            assertTrue(
                parser.parseCollection(html, "https://es.hdrama.net/series").isEmpty(),
                "parseCollection deberia ser vacio para: $html",
            )
            assertTrue(parser.parseSearchResults(html, "https://es.hdrama.net/es/search?q=x").isEmpty())
            assertTrue(parser.parseHomeSection(html, "Destacados", "https://es.hdrama.net/").isEmpty())
            assertTrue(parser.parseGenreCards(html).isEmpty())
            assertTrue(parser.parseSourceCards(html).isEmpty())
            assertTrue(parser.parseRelated(html, detalleUrl).isEmpty())
            assertNull(parser.parseBookId(html, "inexistente"))
        }
    }
}
