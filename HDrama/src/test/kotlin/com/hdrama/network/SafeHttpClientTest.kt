package com.hdrama.network

import java.io.IOException
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SafeHttpClientTest {
    private class RecordingSleeper {
        val delays = mutableListOf<Long>()
        suspend fun sleep(ms: Long) {
            delays += ms
        }
    }

    @Test
    fun `reintenta con backoff y termina en exito`(): Unit = runBlocking {
        var calls = 0
        val sleeper = RecordingSleeper()
        val transport = HttpTransport { _, _ ->
            calls += 1
            if (calls < 3) throw IOException("fallo temporal")
            "<xml>ok</xml>"
        }
        val client = SafeHttpClient(
            transport = transport,
            retryPolicy = RetryPolicy(maxAttempts = 3, initialDelayMs = 250, maxDelayMs = 2_000),
            sleeper = sleeper::sleep,
        )

        val result = client.getText("https://es.hdrama.net/sitemap.xml")

        val success = assertIs<HttpOutcome.Success>(result)
        assertEquals("<xml>ok</xml>", success.body)
        assertEquals(3, calls)
        assertEquals(listOf(250L, 500L), sleeper.delays)
    }

    @Test
    fun `agota reintentos y reporta fallo`(): Unit = runBlocking {
        var calls = 0
        val client = SafeHttpClient(
            transport = HttpTransport { _, _ ->
                calls += 1
                throw IOException("sin red")
            },
            retryPolicy = RetryPolicy(maxAttempts = 3, initialDelayMs = 0, maxDelayMs = 0),
            sleeper = { },
        )

        val result = client.getText("https://es.hdrama.net/sitemap.xml")

        val failure = assertIs<HttpOutcome.Failure>(result)
        assertEquals(3, failure.attempts)
        assertEquals(3, calls)
        assertTrue(failure.reason.contains("IOException"))
    }

    @Test
    fun `bloquea dominios fuera de la allowlist sin abrir conexion`(): Unit = runBlocking {
        var calls = 0
        val client = SafeHttpClient(
            transport = HttpTransport { _, _ ->
                calls += 1
                "no deberia llamarse"
            },
        )

        val result = client.getText("https://evil.example/data")

        assertIs<HttpOutcome.Blocked>(result)
        assertEquals(0, calls)
    }

    @Test
    fun `calcula backoff acotado`() {
        val policy = RetryPolicy(maxAttempts = 5, initialDelayMs = 250, maxDelayMs = 600)
        assertEquals(250, policy.delayMsFor(1))
        assertEquals(500, policy.delayMsFor(2))
        assertEquals(600, policy.delayMsFor(3))
        assertEquals(600, policy.delayMsFor(4))
    }
}
