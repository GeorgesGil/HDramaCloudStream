package com.hdrama.sources

import com.hdrama.models.MediaSource
import com.hdrama.network.HdramaHttpResponse
import com.hdrama.network.HdramaHttpTransport
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pruebas JVM de la sonda de disponibilidad: sin red, con un transporte falso
 * que simula enlaces vivos y muertos como los observados en el sitio
 * (`pl.shortdizilab.com` 404, `zshipricf.farsunpteltd.net` 200).
 */
class SourceProbeTest {

    private class FakeTransport(
        private val codes: Map<String, Int>,
    ) : HdramaHttpTransport {
        val calls = mutableListOf<String>()

        override suspend fun get(
            url: String,
            headers: Map<String, String>,
        ): HdramaHttpResponse {
            calls += url
            val code = codes[url] ?: 404
            return HdramaHttpResponse(code, if (code in 200..299) "#EXTM3U" else "")
        }
    }

    private val dead = MediaSource.Hls("https://pl.shortdizilab.com/v1/flickreels/21648199.m3u8")
    private val alive = MediaSource.Hls(
        "https://zshipricf.farsunpteltd.net/playlet-hls/1793349705_hls_34114.m3u8",
    )

    @Test
    fun `descarta el enlace muerto y conserva el que responde`(): Unit = runBlocking {
        val probe = HttpSourceProbe(
            transport = FakeTransport(
                mapOf(
                    "https://pl.shortdizilab.com/v1/flickreels/21648199.m3u8" to 404,
                    "https://zshipricf.farsunpteltd.net/playlet-hls/1793349705_hls_34114.m3u8" to 200,
                )
            ),
            probeTimeoutMs = 1_000,
        )

        val result = probe.filterPlayable(listOf(dead, alive))

        assertEquals(listOf(alive), result)
    }

    @Test
    fun `conserva el orden original de los enlaces vivos`(): Unit = runBlocking {
        val second = MediaSource.Hls("https://cdn.example/b.m3u8")
        val first = MediaSource.Hls("https://cdn.example/a.m3u8")
        val probe = HttpSourceProbe(
            transport = FakeTransport(
                mapOf(
                    "https://cdn.example/b.m3u8" to 200,
                    "https://cdn.example/a.m3u8" to 200,
                )
            ),
            probeTimeoutMs = 1_000,
        )

        assertEquals(listOf(second, first), probe.filterPlayable(listOf(second, first)))
    }

    @Test
    fun `si ninguna responde devuelve todas para que el player muestre el error`(): Unit =
        runBlocking {
            val probe = HttpSourceProbe(
                transport = FakeTransport(emptyMap()),
                probeTimeoutMs = 1_000,
            )

            assertEquals(listOf(dead, alive), probe.filterPlayable(listOf(dead, alive)))
        }

    @Test
    fun `con una sola fuente no hace peticiones`(): Unit = runBlocking {
        val transport = FakeTransport(mapOf(dead.url to 200))
        val probe = HttpSourceProbe(transport = transport, probeTimeoutMs = 1_000)

        assertEquals(listOf(dead), probe.filterPlayable(listOf(dead)))
        assertTrue(transport.calls.isEmpty())
    }

    @Test
    fun `mantiene los estados de error sin sondearlos`(): Unit = runBlocking {
        val unavailable = MediaSource.Unavailable("sin fuente")
        val transport = FakeTransport(mapOf(alive.url to 200, dead.url to 404))
        val probe = HttpSourceProbe(transport = transport, probeTimeoutMs = 1_000)

        val result = probe.filterPlayable(listOf(dead, alive, unavailable))

        assertEquals(listOf(alive), result)
    }

    @Test
    fun `un fallo de red durante la sonda cuenta como enlace caido`(): Unit = runBlocking {
        val transport = object : HdramaHttpTransport {
            var calls = 0
            override suspend fun get(
                url: String,
                headers: Map<String, String>,
            ): HdramaHttpResponse {
                calls += 1
                if (calls == 1) throw java.io.IOException("sin red")
                return HdramaHttpResponse(200, "#EXTM3U")
            }
        }
        val probe = HttpSourceProbe(transport = transport, probeTimeoutMs = 1_000)

        assertEquals(listOf(alive), probe.filterPlayable(listOf(dead, alive)))
    }

    @Test
    fun `no sonda estados sin url reproducible`(): Unit = runBlocking {
        val transport = FakeTransport(mapOf(alive.url to 200))
        val probe = HttpSourceProbe(transport = transport, probeTimeoutMs = 1_000)

        val result = probe.filterPlayable(
            listOf(MediaSource.Unavailable("x"), MediaSource.BrowserOnly("https://es.hdrama.net/x", "y")),
        )

        assertEquals(2, result.size)
        assertTrue(transport.calls.isEmpty())
    }
}
