package com.hdrama.network

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Pruebas JVM del fetcher principal: sin Android, con transporte y sesion
 * falsos. Cubren las cabeceras que exige el sitio (cookie de Cloudflare,
 * User-Agent del WebView y `Referer`), la renovacion ante 403 y el respaldo.
 */
class HdramaHttpFetcherTest {

    private class FakeSession(
        private var cookie: String? = "cf_clearance=abc; otras=1",
        private val userAgent: String = "UA-WebView/1.0",
        private var solve: Boolean = true,
    ) : CloudflareSession {
        var ensureCalls = 0
        var invalidations = 0

        override suspend fun cookieHeader(): String? = cookie

        override suspend fun userAgent(): String? = userAgent

        override suspend fun ensure(): Boolean {
            ensureCalls += 1
            if (cookie == null && solve) cookie = "cf_clearance=nueva"
            return cookie != null
        }

        override fun invalidate() {
            invalidations += 1
            cookie = null
        }
    }

    private class FakeTransport(
        private val responder: suspend (String, Map<String, String>) -> HdramaHttpResponse,
    ) : HdramaHttpTransport {
        val calls = mutableListOf<Pair<String, Map<String, String>>>()

        override suspend fun get(
            url: String,
            headers: Map<String, String>,
        ): HdramaHttpResponse {
            calls += url to headers
            return responder(url, headers)
        }
    }

    private class FakeFallback(
        private val body: String = "respado",
        private val failure: Throwable? = null,
    ) : WebFetcher {
        var calls = 0
        var lastHeaders: Map<String, String> = emptyMap()

        override suspend fun fetch(url: String, headers: Map<String, String>): String {
            calls += 1
            lastHeaders = headers
            failure?.let { throw it }
            return body
        }
    }

    @Test
    fun `envia cookie user-agent y referer en las peticiones html`(): Unit = runBlocking {
        val transport = FakeTransport { _, _ -> HdramaHttpResponse(200, "<html>ok</html>") }
        val fetcher = HdramaHttpFetcher(session = FakeSession(), transport = transport)

        val body = fetcher.fetch("https://es.hdrama.net/series/una")

        assertEquals("<html>ok</html>", body)
        val headers = transport.calls.single().second
        assertEquals("cf_clearance=abc; otras=1", headers["cookie"])
        assertEquals("UA-WebView/1.0", headers["user-agent"])
        assertEquals("https://es.hdrama.net/", headers["referer"])
        assertTrue(headers["accept"].orEmpty().contains("text/html"))
        assertTrue(headers["accept-language"].orEmpty().startsWith("es"))
    }

    @Test
    fun `la api se pide como json y con referer same-origin`(): Unit = runBlocking {
        val transport = FakeTransport { _, _ -> HdramaHttpResponse(200, "{}") }
        val fetcher = HdramaHttpFetcher(session = FakeSession(), transport = transport)

        fetcher.fetch("https://es.hdrama.net/api/episode-source/15531/1?lang=es&refresh=1")

        val (url, headers) = transport.calls.single()
        assertTrue(url.contains("/api/episode-source/15531/1"))
        assertEquals("application/json", headers["accept"])
        assertEquals("https://es.hdrama.net/", headers["referer"])
    }

    @Test
    fun `ante 403 renueva el challenge y reintenta`(): Unit = runBlocking {
        var calls = 0
        val session = FakeSession()
        val transport = FakeTransport { _, _ ->
            calls += 1
            if (calls == 1) HdramaHttpResponse(403, "forbidden") else HdramaHttpResponse(200, "ok")
        }
        val fetcher = HdramaHttpFetcher(session = session, transport = transport, sleeper = {})

        assertEquals("ok", fetcher.fetch("https://es.hdrama.net/series/una"))
        assertEquals(1, session.invalidations)
        assertEquals(2, transport.calls.size)
    }

    @Test
    fun `sin sesion falla rapido sin abrir el respaldo WebView`(): Unit = runBlocking {
        val session = FakeSession(cookie = null, solve = false)
        val transport = FakeTransport { _, _ -> error("no deberia llamarse") }
        val fallback = FakeFallback()
        val fetcher = HdramaHttpFetcher(
            session = session,
            transport = transport,
            fallback = fallback,
            sleeper = {},
        )

        val error = assertFailsWith<WebFetchException> {
            fetcher.fetch("https://es.hdrama.net/series/una")
        }
        assertEquals(403, error.code)
        assertTrue(error.message.orEmpty().contains("Cloudflare"))
        assertTrue(transport.calls.isEmpty())
        assertEquals(0, fallback.calls)
    }

    @Test
    fun `si la sesion no se puede renovar usa el respaldo WebView`(): Unit = runBlocking {
        // Empieza con cookie valida; el 403 la invalida y no hay renovacion.
        val session = FakeSession(solve = false)
        val transport = FakeTransport { _, _ -> HdramaHttpResponse(403, "forbidden") }
        val fallback = FakeFallback(body = "<html>desde-webview</html>")
        val fetcher = HdramaHttpFetcher(
            session = session,
            transport = transport,
            fallback = fallback,
            sleeper = {},
        )

        assertEquals(
            "<html>desde-webview</html>",
            fetcher.fetch("https://es.hdrama.net/series/una"),
        )
        assertEquals(1, fallback.calls)
    }

    @Test
    fun `sin respaldo propaga WebFetchException con el codigo`(): Unit = runBlocking {
        val session = FakeSession(solve = false)
        val transport = FakeTransport { _, _ -> HdramaHttpResponse(403, "forbidden") }
        val fetcher = HdramaHttpFetcher(session = session, transport = transport, sleeper = {})

        val error = assertFailsWith<WebFetchException> {
            fetcher.fetch("https://es.hdrama.net/series/una")
        }
        assertEquals(403, error.code)
        assertTrue(error.message.orEmpty().contains("Cloudflare"))
    }

    @Test
    fun `rechaza urls fuera de la allowlist sin tocar la red`(): Unit = runBlocking {
        val transport = FakeTransport { _, _ -> error("no deberia llamarse") }
        val fetcher = HdramaHttpFetcher(session = FakeSession(), transport = transport)

        val error = assertFailsWith<WebFetchException> {
            fetcher.fetch("https://evil.example/x")
        }
        assertEquals(WebFetchException.NO_HTTP_CODE, error.code)
        assertTrue(transport.calls.isEmpty())
    }

    @Test
    fun `un error de transporte usa el respaldo`(): Unit = runBlocking {
        val session = FakeSession()
        val transport = FakeTransport { _, _ ->
            throw java.io.IOException("sin red")
        }
        val fallback = FakeFallback(body = "respaldo")
        val fetcher = HdramaHttpFetcher(
            session = session,
            transport = transport,
            fallback = fallback,
            sleeper = {},
        )

        assertEquals("respaldo", fetcher.fetch("https://es.hdrama.net/series/una"))
        assertEquals(1, fallback.calls)
    }
}
