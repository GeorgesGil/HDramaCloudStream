package com.hdrama.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Transporte HTTP basado en HttpURLConnection.
 *
 * Se usa solo para los sitemaps publicos de HDrama (permitidos por robots.txt).
 * No gestiona cookies, challenges ni autenticacion, y no intenta resolver
 * Cloudflare. Si el servidor responde 403, se propaga el error.
 */
class UrlConnectionHttpTransport(
    private val connectTimeoutMs: Int = 10_000,
    private val readTimeoutMs: Int = 15_000,
    private val maxBytes: Int = 4_000_000,
    private val userAgent: String = "HDramaCloudStream/1.0 (CloudStream plugin)",
) : HttpTransport {
    override suspend fun get(url: String, headers: Map<String, String>): String =
        withContext(Dispatchers.IO) {
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = connectTimeoutMs
                readTimeout = readTimeoutMs
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", userAgent)
                setRequestProperty("Accept", "application/xml,text/xml,text/plain;q=0.9,*/*;q=0.8")
                headers.forEach { (name, value) -> setRequestProperty(name, value) }
            }
            try {
                val code = connection.responseCode
                if (code !in 200..299) {
                    throw IOException("HTTP $code al solicitar ${url.take(120)}")
                }
                connection.inputStream.use { input ->
                    val buffer = ByteArray(8 * 1024)
                    val output = StringBuilder()
                    var total = 0
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        total += read
                        if (total > maxBytes) {
                            throw IOException("Respuesta demasiado grande (> $maxBytes bytes)")
                        }
                        output.append(String(buffer, 0, read, Charsets.UTF_8))
                    }
                    output.toString()
                }
            } finally {
                connection.disconnect()
            }
        }
}
