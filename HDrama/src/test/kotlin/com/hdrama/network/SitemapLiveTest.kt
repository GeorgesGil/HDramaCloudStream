package com.hdrama.network

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Prueba de integracion opcional contra los sitemaps publicos.
 *
 * No se ejecuta en la suite normal (evita dependencia de red). Para ejecutarla:
 * `$env:HDRAMA_INTEGRATION="1"; .\gradlew.bat :HDrama:testDebugUnitTest`
 */
class SitemapLiveTest {
    @Test
    fun `descarga sitemaps publicos reales`(): Unit = runBlocking {
        if (System.getenv("HDRAMA_INTEGRATION") != "1") return@runBlocking

        val snapshot = SitemapClient().loadSnapshot(forceRefresh = true)
        assertTrue(snapshot.newReleases.isNotEmpty(), "new-es vacio")
        assertTrue(snapshot.trending.isNotEmpty(), "trending-es vacio")
        assertTrue(snapshot.fresh.isNotEmpty(), "fresh-es vacio")
        assertTrue(snapshot.allSeries.any { it.url.contains("/series/") })
    }
}
