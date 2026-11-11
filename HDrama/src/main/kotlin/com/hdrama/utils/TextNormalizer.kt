package com.hdrama.utils

import java.text.Normalizer
import java.util.Locale

/**
 * Normalizacion de texto para busquedas en espanol.
 *
 * - Descompone Unicode (NFKD) y elimina marcas diacriticas: "Corazon" ==
 *   "Corazón".
 * - Ignora mayusculas/minusculas con [Locale.ROOT].
 * - Colapsa espacios repetidos.
 */
object TextNormalizer {
    private val combiningMarks = Regex("\\p{M}+")
    private val whitespace = Regex("\\s+")

    fun normalize(value: String?): String {
        if (value.isNullOrBlank()) return ""
        val decomposed = Normalizer.normalize(value, Normalizer.Form.NFKD)
        return decomposed
            .replace(combiningMarks, "")
            .lowercase(Locale.ROOT)
            .replace(whitespace, " ")
            .trim()
    }

    /** Coincidencia parcial, sin acentos ni mayusculas. */
    fun matches(haystack: String?, needle: String?): Boolean {
        val normalizedNeedle = normalize(needle)
        if (normalizedNeedle.isEmpty()) return false
        return normalize(haystack).contains(normalizedNeedle)
    }

    /**
     * Convierte un slug URL en un titulo legible.
     * "el-senor-multimillonario" -> "El senor multimillonario".
     */
    fun titleFromSlug(slug: String): String {
        if (slug.isBlank()) return ""
        return slug
            .substringAfterLast('/')
            .replace('-', ' ')
            .replace('_', ' ')
            .replace(whitespace, " ")
            .trim()
            .split(' ')
            .filter { it.isNotBlank() }
            .joinToString(" ") { word ->
                word.replaceFirstChar { char ->
                    if (char.isLowerCase()) char.titlecase(Locale("es")) else char.toString()
                }
            }
    }

    /** Slug estable y ASCII para IDs internos. */
    fun slugify(value: String): String {
        return normalize(value)
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .ifBlank { "sin-titulo" }
    }
}
