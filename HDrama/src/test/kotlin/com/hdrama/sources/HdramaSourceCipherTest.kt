package com.hdrama.sources

import java.io.InputStream
import org.json.JSONObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HdramaSourceCipherTest {

    private fun fixture(name: String): String {
        val stream: InputStream = checkNotNull(
            javaClass.classLoader?.getResourceAsStream("fixtures/$name")
        ) { "Fixture no encontrada: $name" }
        return stream.bufferedReader().use { it.readText() }
    }

    private data class CipherVector(val enc: String, val url: String, val type: String)

    private fun vectors(): List<CipherVector> {
        val array = JSONObject(fixture("hdrama-source-enc.json")).getJSONArray("vectors")
        return (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            CipherVector(
                enc = item.getString("enc"),
                url = item.getString("url"),
                type = item.optString("type", "hls"),
            )
        }
    }

    @Test
    fun `descifra los vectores reales capturados del sitio`() {
        val vectors = vectors().filter { it.type == "hls" }
        assertEquals(4, vectors.size)
        vectors.forEach { vector ->
            assertEquals(vector.url, HdramaSourceCipher.decrypt(vector.enc), "vector: ${vector.url}")
        }
        // Vector capturado el 2026-11-12: fuente Stardust (antes FlickReels).
        assertEquals(
            "https://acdn-v-us.stardust-tv.com/prod/29358/1/" +
                "e4bf2b9fce7c423ca661b3d65529a7f4.m3u8",
            HdramaSourceCipher.decrypt(vectors[0].enc),
        )
        assertEquals(
            "https://pl.shortdizilab.com/v1/flickreels/23935678.m3u8",
            HdramaSourceCipher.decrypt(vectors[1].enc),
        )
    }

    @Test
    fun `descifra el vector mp4 sintetico`() {
        val mp4 = vectors().first { it.type == "mp4" }
        assertEquals(mp4.url, HdramaSourceCipher.decrypt(mp4.enc))
    }

    @Test
    fun `devuelve null con entrada vacia o no base64`() {
        assertNull(HdramaSourceCipher.decrypt(""))
        assertNull(HdramaSourceCipher.decrypt("   "))
        assertNull(HdramaSourceCipher.decrypt("esto no es base64!!!"))
        assertNull(HdramaSourceCipher.decrypt("="))
        assertNull(HdramaSourceCipher.decrypt("A"))
    }

    @Test
    fun `devuelve null con payload demasiado corto`() {
        assertNull(HdramaSourceCipher.decrypt("AAAA"))
        assertNull(HdramaSourceCipher.decrypt("QUJDREVGR0hJSktM"))
        assertNull(HdramaSourceCipher.decrypt("QUJDREVGR0hJSktMTU5PUFFSU1R1"))
    }

    @Test
    fun `devuelve null si el ciphertext fue manipulado`() {
        val enc = vectors().first().enc
        val index = enc.length / 2
        val replacement = if (enc[index] == 'A') 'B' else 'A'
        val tampered = enc.substring(0, index) + replacement + enc.substring(index + 1)
        assertNull(HdramaSourceCipher.decrypt(tampered))
    }

    @Test
    fun `devuelve null si se trunca el tag`() {
        val enc = vectors().first().enc
        assertNull(HdramaSourceCipher.decrypt(enc.dropLast(4)))
    }

    @Test
    fun `acepta base64 sin relleno`() {
        val vector = vectors().first()
        assertEquals(vector.url, HdramaSourceCipher.decrypt(vector.enc.trimEnd('=')))
    }
}
