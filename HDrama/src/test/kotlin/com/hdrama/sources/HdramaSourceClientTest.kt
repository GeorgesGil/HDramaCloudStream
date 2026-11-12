package com.hdrama.sources

import com.hdrama.models.MediaSource
import com.hdrama.network.WebFetchException
import com.hdrama.network.WebFetcher
import java.io.IOException
import java.io.InputStream
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class HdramaSourceClientTest {

    private class FakeWebFetcher(
        private val responder: suspend (String) -> String,
    ) : WebFetcher {
        val calls = mutableListOf<String>()
        var lastHeaders: Map<String, String> = emptyMap()
        val callCount: Int get() = calls.size

        override suspend fun fetch(url: String, headers: Map<String, String>): String {
            calls += url
            lastHeaders = headers
            return responder(url)
        }
    }

    private data class TestVector(val enc: String, val url: String, val type: String)

    private fun fixture(name: String): String {
        val stream: InputStream = checkNotNull(
            javaClass.classLoader?.getResourceAsStream("fixtures/$name")
        ) { "Fixture no encontrada: $name" }
        return stream.bufferedReader().use { it.readText() }
    }

    private val apiJson: String by lazy { fixture("hdrama-source-api.json") }

    private val vectors: List<TestVector> by lazy {
        val array = JSONObject(fixture("hdrama-source-enc.json")).getJSONArray("vectors")
        (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            TestVector(
                enc = item.getString("enc"),
                url = item.getString("url"),
                type = item.optString("type", "hls"),
            )
        }
    }

    private fun encFor(url: String): String = vectors.first { it.url == url }.enc

    private fun chainJson(vararg entries: Pair<String, String>, subtitle: String = "null"): String {
        val chain = entries.joinToString(",") { (enc, type) ->
            """{"enc":"$enc","type":"$type","source":"FlickReels","refresh":false,"refreshAfter":null}"""
        }
        return """{"descriptor":{"source":"FlickReels","chain":[$chain],"subtitle":$subtitle}}"""
    }

    @Test
    fun `pide la api correcta y devuelve directas mas espejo`(): Unit = runBlocking {
        val fetcher = FakeWebFetcher { _ -> apiJson }

        val sources = HdramaSourceClient(fetcher).sources("15531", 1)

        assertEquals(
            listOf("https://es.hdrama.net/api/episode-source/15531/1?lang=es&refresh=1"),
            fetcher.calls,
        )
        assertEquals(4, sources.size)

        val direct1 = assertIs<MediaSource.Hls>(sources[0])
        assertEquals("https://pl.shortdizilab.com/v1/flickreels/23935678.m3u8", direct1.url)
        assertTrue(direct1.headers.isEmpty())

        val direct2 = assertIs<MediaSource.Hls>(sources[1])
        assertTrue(direct2.url.startsWith("https://zshipricf.farsunpteltd.net/playlet-hls/"))
        assertTrue(direct2.url.contains("verify="))
        assertTrue(direct2.headers.isEmpty())

        val mirror1 = assertIs<MediaSource.Hls>(sources[2])
        assertEquals("https://cdn.dramadizilerim.xyz/https/" + direct1.url, mirror1.url)
        assertEquals(mapOf("Referer" to "https://es.hdrama.net/"), mirror1.headers)

        val mirror2 = assertIs<MediaSource.Hls>(sources[3])
        assertEquals("https://cdn.dramadizilerim.xyz/https/" + direct2.url, mirror2.url)
        assertEquals(mapOf("Referer" to "https://es.hdrama.net/"), mirror2.headers)
    }

    @Test
    fun `deduplica urls repetidas en la cadena`(): Unit = runBlocking {
        val enc = encFor("https://pl.shortdizilab.com/v1/flickreels/23935678.m3u8")
        val json = chainJson(enc to "hls", enc to "hls")

        val sources = HdramaSourceClient(FakeWebFetcher { _ -> json }).sources("15531", 1)

        assertEquals(2, sources.size)
        assertIs<MediaSource.Hls>(sources[0])
        assertIs<MediaSource.Hls>(sources[1])
    }

    @Test
    fun `mapea type mp4 y genera su espejo`(): Unit = runBlocking {
        val mp4 = vectors.first { it.type == "mp4" }
        val json = chainJson(mp4.enc to "mp4")

        val sources = HdramaSourceClient(FakeWebFetcher { _ -> json }).sources("15531", 1)

        assertEquals(2, sources.size)
        val direct = assertIs<MediaSource.Mp4>(sources[0])
        assertEquals(mp4.url, direct.url)
        assertTrue(direct.headers.isEmpty())
        val mirror = assertIs<MediaSource.Hls>(sources[1])
        assertEquals("https://cdn.dramadizilerim.xyz/https/" + mp4.url, mirror.url)
    }

    @Test
    fun `subtitulo presente o ausente no rompe el parseo`(): Unit = runBlocking {
        val enc = encFor("https://pl.shortdizilab.com/v1/flickreels/23935678.m3u8")
        val withSubtitle =
            """{"descriptor":{"source":"FlickReels","chain":[{"enc":"$enc","type":"hls"}],""" +
                """"subtitle":{"lang":"es","url":"https://example.test/sub.vtt"}}}"""
        val withoutSubtitle = chainJson(enc to "hls")

        val withSources = HdramaSourceClient(FakeWebFetcher { _ -> withSubtitle }).sources("15531", 1)
        val withoutSources =
            HdramaSourceClient(FakeWebFetcher { _ -> withoutSubtitle }).sources("15531", 1)

        assertEquals(2, withSources.size)
        assertEquals(withSources, withoutSources)
    }

    @Test
    fun `respuesta sin descriptor o con chain ausente devuelve Unavailable`(): Unit = runBlocking {
        val bodies = listOf(
            "{}",
            """{"descriptor":null}""",
            """{"descriptor":{}}""",
            """{"descriptor":{"chain":[]}}""",
            "texto que no es json",
        )
        bodies.forEach { body ->
            val sources = HdramaSourceClient(FakeWebFetcher { _ -> body }).sources("15531", 1)
            assertEquals(1, sources.size, "cuerpo: $body")
            assertIs<MediaSource.Unavailable>(sources.single())
        }
    }

    @Test
    fun `fallo del fetcher devuelve Unavailable con motivo util`(): Unit = runBlocking {
        val http403 = HdramaSourceClient(
            FakeWebFetcher { _ -> throw WebFetchException(403, "HTTP 403 de HDrama") },
        ).sources("15531", 1)
        val unavailable = assertIs<MediaSource.Unavailable>(http403.single())
        assertTrue(unavailable.reason.contains("403"), "motivo: ${unavailable.reason}")

        val network = HdramaSourceClient(
            FakeWebFetcher { _ -> throw IOException("sin red") },
        ).sources("15531", 1)
        val networkUnavailable = assertIs<MediaSource.Unavailable>(network.single())
        assertTrue(networkUnavailable.reason.isNotBlank())
    }

    @Test
    fun `entradas con enc corrupto o type desconocido se ignoran`(): Unit = runBlocking {
        val good = encFor("https://pl.shortdizilab.com/v1/flickreels/23935678.m3u8")
        val mixed = chainJson("no-es-base64" to "hls", good to "hls", good to "mp4", "" to "hls")
        val sources = HdramaSourceClient(FakeWebFetcher { _ -> mixed }).sources("15531", 1)
        assertEquals(2, sources.size)
        assertEquals(
            "https://pl.shortdizilab.com/v1/flickreels/23935678.m3u8",
            assertIs<MediaSource.Hls>(sources[0]).url,
        )

        val unknownType = chainJson(good to "dash")
        val unknownSources =
            HdramaSourceClient(FakeWebFetcher { _ -> unknownType }).sources("15531", 1)
        assertIs<MediaSource.Unavailable>(unknownSources.single())
    }

    @Test
    fun `identificadores invalidos devuelven Unavailable sin pedir red`(): Unit = runBlocking {
        val fetcher = FakeWebFetcher { _ -> error("no deberia llamarse") }
        val client = HdramaSourceClient(fetcher)

        val results = listOf(
            client.sources("", 1),
            client.sources("15/531", 1),
            client.sources("a b", 1),
            client.sources("15531", 0),
            client.sources("15531", -1),
        )

        results.forEach { sources -> assertIs<MediaSource.Unavailable>(sources.single()) }
        assertEquals(0, fetcher.callCount)
    }

    @Test
    fun `acepta bookId numerico hexadecimal o slug`(): Unit = runBlocking {
        val fetcher = FakeWebFetcher { _ -> """{"descriptor":{"chain":[]}}""" }
        val client = HdramaSourceClient(fetcher)

        client.sources("15531", 1)
        client.sources("6ab4e295c0d5c20474082905", 2)
        client.sources("el-prodigio-emerge", 3)

        assertEquals(3, fetcher.callCount)
        assertTrue(fetcher.calls[0].contains("/api/episode-source/15531/1"))
        assertTrue(
            fetcher.calls[1].contains("/api/episode-source/6ab4e295c0d5c20474082905/2"),
        )
        assertTrue(
            fetcher.calls[2].contains("/api/episode-source/el-prodigio-emerge/3"),
        )
    }

    @Test
    fun `usa el idioma configurado en la query`(): Unit = runBlocking {
        val fetcher = FakeWebFetcher { _ -> """{"descriptor":{"chain":[]}}""" }

        HdramaSourceClient(fetcher, lang = "en").sources("15531", 7)

        assertEquals(
            "https://es.hdrama.net/api/episode-source/15531/7?lang=en&refresh=1",
            fetcher.calls.single(),
        )
    }

    @Test
    fun `idioma invalido cae al valor por defecto`(): Unit = runBlocking {
        val fetcher = FakeWebFetcher { _ -> """{"descriptor":{"chain":[]}}""" }

        HdramaSourceClient(fetcher, lang = "es&refresh=0").sources("15531", 1)

        assertEquals(
            "https://es.hdrama.net/api/episode-source/15531/1?lang=es&refresh=1",
            fetcher.calls.single(),
        )
    }
}
