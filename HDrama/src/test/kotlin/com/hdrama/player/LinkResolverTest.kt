package com.hdrama.player

import com.hdrama.models.MediaSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class LinkResolverTest {
    @Test
    fun `deduplica enlaces iguales`() {
        val result = LinkResolver.resolve(
            listOf(
                MediaSource.Mp4("https://cdn.example/video.mp4", 1080),
                MediaSource.Mp4("https://cdn.example/video.mp4", 1080),
            )
        )
        val playable = assertIs<LinkResolution.Playable>(result)
        assertEquals(1, playable.links.size)
    }

    @Test
    fun `ordena calidades de mayor a menor`() {
        val result = LinkResolver.resolve(
            listOf(
                MediaSource.Mp4("https://cdn.example/720.mp4", 720),
                MediaSource.Mp4("https://cdn.example/1080.mp4", 1080),
                MediaSource.Hls("https://cdn.example/index.m3u8", 480),
            )
        )
        val playable = assertIs<LinkResolution.Playable>(result)
        assertEquals(listOf(1080, 720, 480), playable.links.map { it.quality })
        assertEquals(LinkType.HLS, playable.links.last().type)
    }

    @Test
    fun `fuente de navegador produce browser`() {
        val result = LinkResolver.resolve(
            listOf(
                MediaSource.BrowserOnly(
                    "https://es.hdrama.net/series/corazon-de-neon",
                    "solo navegador",
                )
            )
        )
        val browser = assertIs<LinkResolution.Browser>(result)
        assertEquals(PlaybackStatus.BROWSER_REQUIRED, browser.status)
        assertEquals(LinkType.BROWSER, browser.link.type)
    }

    @Test
    fun `fuente no disponible informa estado temporal`() {
        val result = LinkResolver.resolve(
            listOf(MediaSource.Unavailable("sin fuente"))
        )
        val failure = assertIs<LinkResolution.Failure>(result)
        assertEquals(PlaybackStatus.TEMPORARILY_UNAVAILABLE, failure.status)
        assertTrue(failure.message.contains("sin fuente"))
    }

    @Test
    fun `fuente no autorizada informa estado unauthorized`() {
        val result = LinkResolver.resolve(
            listOf(MediaSource.Unauthorized("requiere autorizacion"))
        )
        val failure = assertIs<LinkResolution.Failure>(result)
        assertEquals(PlaybackStatus.UNAUTHORIZED, failure.status)
    }

    @Test
    fun `url invalida no se convierte en enlace`() {
        val result = LinkResolver.resolve(
            listOf(MediaSource.Mp4("no-es-url", 1080))
        )
        assertIs<LinkResolution.Failure>(result)
    }

    @Test
    fun `elimina fragmentos de la url`() {
        assertEquals(
            "https://cdn.example/video.mp4",
            LinkResolver.normalizeMediaUrl("https://cdn.example/video.mp4#t=10"),
        )
    }
}
