package com.hdrama.sources

import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Descifra el campo `enc` de la API de fuentes de HDrama.
 *
 * Formato verificado contra el JS propio del sitio (`AES-GCM`, IV de 12 bytes):
 * Base64 estandar de `IV(12) || ciphertext || tag GCM(16)` cifrado con
 * AES-256-GCM con la clave embebida en el cliente web.
 *
 * El Base64 se decodifica con [HdramaBase64] (implementacion propia) porque
 * `android.util.Base64` no existe en las pruebas JVM y `java.util.Base64` no
 * esta disponible en minSdk 21.
 */
object HdramaSourceCipher {

    private const val KEY_BASE64 = "QC6Ir2trghxRAyyyWZEOEFR4GgLhnfQ4A19I3QBlQkc="
    private const val IV_LENGTH = 12
    private const val TAG_LENGTH = 16
    private const val TAG_BITS = TAG_LENGTH * 8
    private const val TRANSFORMATION = "AES/GCM/NoPadding"

    private val keyBytes: ByteArray? by lazy { HdramaBase64.decode(KEY_BASE64) }

    /**
     * Devuelve la URL descifrada o `null` ante cualquier fallo: Base64
     * invalido, payload demasiado corto, clave no disponible o tag GCM que no
     * valida (ciphertext manipulado o clave equivocada).
     */
    fun decrypt(enc: String): String? {
        val raw = enc.trim()
        if (raw.isEmpty()) return null
        val secret = keyBytes ?: return null
        val payload = HdramaBase64.decode(raw) ?: return null
        if (payload.size <= IV_LENGTH + TAG_LENGTH) return null

        return try {
            val iv = payload.copyOfRange(0, IV_LENGTH)
            val cipherTextWithTag = payload.copyOfRange(IV_LENGTH, payload.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                SecretKeySpec(secret, "AES"),
                GCMParameterSpec(TAG_BITS, iv),
            )
            val plain = cipher.doFinal(cipherTextWithTag)
            String(plain, Charsets.UTF_8).trim().ifEmpty { null }
        } catch (error: Exception) {
            null
        }
    }
}

/**
 * Decodificador Base64 estandar (RFC 4648) propio y sin dependencias de
 * plataforma: JVM y Android (minSdk 21).
 *
 * Acepta tanto el relleno `=` correcto como la variante sin relleno; devuelve
 * `null` si el texto contiene caracteres invalidos, relleno mal colocado o bits
 * sobrantes distintos de cero.
 */
internal object HdramaBase64 {

    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"

    private val VALUES = IntArray(128) { -1 }.also { table ->
        ALPHABET.forEachIndexed { index, char -> table[char.code] = index }
    }

    fun decode(value: String): ByteArray? {
        val raw = value.trim()
        if (raw.isEmpty()) return null

        val paddingIndex = raw.indexOf('=')
        val body: String
        val padding: Int
        if (paddingIndex >= 0) {
            if (raw.substring(paddingIndex).any { it != '=' }) return null
            body = raw.substring(0, paddingIndex)
            padding = raw.length - paddingIndex
        } else {
            body = raw
            padding = 0
        }
        if (body.isEmpty()) return null
        // 1 caracter suelto no puede formar un byte completo.
        if (body.length % 4 == 1) return null
        val expectedPadding = (4 - body.length % 4) % 4
        if (padding != 0 && padding != expectedPadding) return null

        val output = ByteArray(body.length * 3 / 4)
        var buffer = 0
        var bits = 0
        var outputIndex = 0
        for (char in body) {
            val digit = if (char.code < VALUES.size) VALUES[char.code] else -1
            if (digit < 0) return null
            buffer = (buffer shl 6) or digit
            bits += 6
            if (bits >= 8) {
                bits -= 8
                output[outputIndex] = ((buffer ushr bits) and 0xFF).toByte()
                outputIndex += 1
            }
        }
        if (bits > 0 && (buffer and ((1 shl bits) - 1)) != 0) return null
        return output
    }
}
