package com.hdrama.parser

import com.hdrama.models.MediaSource
import com.hdrama.utils.EpisodeId
import java.io.InputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HdramaHtmlParserTest {
    private val parser = HdramaHtmlParser()

    private fun fixture(name: String): String {
        val stream: InputStream = checkNotNull(
            javaClass.classLoader?.getResourceAsStream("fixtures/$name")
        ) { "Fixture no encontrada: $name" }
        return stream.bufferedReader().use { it.readText() }
    }

    @Test
    fun `extrae metadatos de la lista con fixtures locales`() {
        val series = parser.parseSeriesList(fixture("hdrama-series.html"))

        assertEquals(3, series.size)
        val neon = series.first { it.slug == "corazon-de-neon" }
        assertEquals("Corazón de Neón", neon.title)
        assertEquals("Neon Heart", neon.originalTitle)
        assertEquals(8.7, neon.rating)
        assertEquals(listOf("Romance", "Drama"), neon.genres)
        assertEquals(12, neon.episodeCount)
        assertEquals("https://es.hdrama.net/images/neon.jpg", neon.posterUrl)
        assertEquals("https://es.hdrama.net/series/corazon-de-neon", neon.pageUrl)
        assertTrue(neon.demo.not())
    }

    @Test
    fun `maneja campos ausentes y duplicados`() {
        val series = parser.parseSeriesList(fixture("hdrama-series.html"))
        val senor = series.first { it.slug == "el-senor-del-norte" }

        assertNull(senor.posterUrl)
        assertNull(senor.synopsis)
        assertNull(senor.rating)
        assertEquals(listOf("Fantasía"), senor.genres)
        assertEquals(1, series.count { it.slug == "corazon-de-neon" })
    }

    @Test
    fun `parsea detalle de serie`() {
        val detail = parser.parseSeriesDetail(
            fixture("hdrama-detail.html"),
            "https://es.hdrama.net/series/corazon-de-neon",
        )

        assertNotNull(detail)
        assertEquals("Corazón de Neón", detail.title)
        assertEquals("Neon Heart", detail.originalTitle)
        assertEquals(8.7, detail.rating)
        assertEquals(2026, detail.year)
        assertEquals(12, detail.episodeCount)
        assertEquals("https://es.hdrama.net/images/neon-large.jpg", detail.posterUrl)
        assertEquals(listOf("Romance", "Drama"), detail.genres)
    }

    @Test
    fun `ordena episodios numericamente y evita duplicados`() {
        val episodes = parser.parseEpisodes(
            fixture("hdrama-episodes.html"),
            seriesId = "corazon-de-neon",
        )

        assertEquals(listOf(1, 2, 10), episodes.map { it.number })
        assertEquals(3, episodes.size)
        assertEquals(EpisodeId.encode("corazon-de-neon", 1), episodes[0].id)
        assertEquals(1500, episodes[0].durationSeconds)
        assertEquals(1680, episodes[2].durationSeconds)
    }

    @Test
    fun `detecta mp4 hls subtitulos y fuentes ausentes`() {
        val episodes = parser.parseEpisodes(
            fixture("hdrama-episodes.html"),
            seriesId = "corazon-de-neon",
        )
        val one = episodes.first { it.number == 1 }
        val two = episodes.first { it.number == 2 }
        val ten = episodes.first { it.number == 10 }

        val mp4 = assertIs<MediaSource.Mp4>(one.mediaSources.single())
        assertEquals(1080, mp4.quality)
        assertTrue(mp4.url.endsWith(".mp4"))

        val unavailable = assertIs<MediaSource.Unavailable>(two.mediaSources.single())
        assertTrue(unavailable.reason.isNotBlank())

        val hls = assertIs<MediaSource.Hls>(ten.mediaSources.single())
        assertEquals(720, hls.quality)
        assertTrue(hls.url.endsWith(".m3u8"))

        assertEquals(1, one.subtitles.size)
        assertEquals("es", one.subtitles.first().lang)
        assertEquals("https://es.hdrama.net/subs/neon-1-es.vtt", one.subtitles.first().url)
    }

    @Test
    fun `html sin episodios devuelve lista vacia`() {
        assertTrue(parser.parseEpisodes("<html><body></body></html>", "x").isEmpty())
    }
}
