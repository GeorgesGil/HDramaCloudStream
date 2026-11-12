package com.hdrama.network

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Prueba en JVM la allowlist pura de la capa web, sin instanciar
 * [CloudstreamWebFetcher] (depende de Android/CloudStream).
 */
class WebFetcherAllowlistTest {

    @Test
    fun `acepta las urls del dominio de hdrama`() {
        assertEquals(
            "https://es.hdrama.net/api/episode-source/15531/1?lang=es&refresh=1",
            HdramaFetchAllowlist.resolve(
                "https://es.hdrama.net/api/episode-source/15531/1?lang=es&refresh=1"
            ),
        )
        assertEquals(
            "https://es.hdrama.net/series/la-novia",
            HdramaFetchAllowlist.resolve("http://es.hdrama.net/series/la-novia/"),
        )
        assertEquals(
            "https://es.hdrama.net/",
            HdramaFetchAllowlist.resolve("https://www.es.hdrama.net/"),
        )
    }

    @Test
    fun `rechaza dominios y esquemas fuera de la allowlist`() {
        val blocked = listOf(
            "https://evil.example/phishing",
            "https://es.hdrama.net.evil.example/serie",
            "http://169.254.169.254/latest/meta-data",
            "ftp://es.hdrama.net/",
            "javascript:alert(1)",
            "",
            "   ",
        )

        blocked.forEach { url ->
            val error = assertFailsWith<WebFetchException>("se esperaba bloqueo para: $url") {
                HdramaFetchAllowlist.resolve(url)
            }
            assertEquals(WebFetchException.NO_HTTP_CODE, error.code, "codigo para: $url")
            assertTrue(
                error.message.orEmpty().contains("allowlist"),
                "mensaje para '$url': ${error.message}",
            )
        }
    }
}
