package com.hdrama.utils

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UrlUtilsTest {
    @Test
    fun `normaliza hosts y esquema de hdrama`() {
        assertEquals(
            "https://es.hdrama.net/series/corazon-de-neon",
            UrlUtils.normalizeHdramaUrl("http://www.es.hdrama.net/series/corazon-de-neon/"),
        )
        assertEquals(
            "https://es.hdrama.net/series/corazon-de-neon?page=2",
            UrlUtils.normalizeHdramaUrl("https://es.hdrama.net/series/corazon-de-neon?page=2#top"),
        )
    }

    @Test
    fun `rechaza dominios y esquemas no permitidos`() {
        assertNull(UrlUtils.normalizeHdramaUrl("https://evil.example/series/x"))
        assertNull(UrlUtils.normalizeHdramaUrl("ftp://es.hdrama.net/series/x"))
        assertNull(UrlUtils.normalizeHdramaUrl("javascript:alert(1)"))
        assertFalse(UrlUtils.isAllowedHdramaUrl("https://hdrama.net.evil.example/series/x"))
    }

    @Test
    fun `extrae slug de series`() {
        assertEquals(
            "corazon-de-neon",
            UrlUtils.extractSeriesSlug("https://es.hdrama.net/series/corazon-de-neon/episodio-1"),
        )
        assertNull(UrlUtils.extractSeriesSlug("https://es.hdrama.net/genero/romance"))
        assertNull(UrlUtils.extractSeriesSlug("no-es-una-url"))
    }

    @Test
    fun `normaliza url base`() {
        assertEquals("https://es.hdrama.net/", UrlUtils.normalizeBaseUrl(""))
        assertEquals(
            "https://es.hdrama.net/series/x",
            UrlUtils.normalizeBaseUrl("es.hdrama.net/series/x/"),
        )
    }

    @Test
    fun `construye y parsea urls internas de demo`() {
        val url = UrlUtils.demoSeriesUrl("corazon-de-neon")
        assertEquals("hdrama-demo://series/corazon-de-neon", url)
        assertTrue(UrlUtils.isDemoSeriesUrl(url))
        assertEquals("corazon-de-neon", UrlUtils.demoSeriesId(url))
        assertFalse(UrlUtils.isDemoSeriesUrl("https://es.hdrama.net/series/x"))
    }

    @Test
    fun `recupera url demo aunque cloudstream anteponga mainUrl`() {
        assertEquals(
            "hdrama-demo://series/corazon-de-neon",
            UrlUtils.recoverDemoSeriesUrl(
                "https://es.hdrama.net//hdrama-demo://series/corazon-de-neon"
            ),
        )
        assertEquals(
            "hdrama-demo://series/corazon-de-neon",
            UrlUtils.recoverDemoSeriesUrl("hdrama-demo://series/corazon-de-neon"),
        )
        assertNull(UrlUtils.recoverDemoSeriesUrl("https://es.hdrama.net/series/x"))
    }

    @Test
    fun `redacta query y credenciales para logs`() {
        assertEquals(
            "https://es.hdrama.net/series/x",
            UrlUtils.redact("https://user:pass@es.hdrama.net/series/x?token=abc#frag"),
        )
    }
}
