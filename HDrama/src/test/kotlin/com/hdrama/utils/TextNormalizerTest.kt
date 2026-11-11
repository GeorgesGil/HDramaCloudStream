package com.hdrama.utils

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TextNormalizerTest {
    @Test
    fun `normaliza acentos mayusculas y espacios`() {
        assertEquals(
            "corazon de neon",
            TextNormalizer.normalize("  Corazón   de NEÓN "),
        )
    }

    @Test
    fun `coincidencia parcial sin acentos ni mayusculas`() {
        assertTrue(TextNormalizer.matches("El Señor del Norte", "senor"))
        assertTrue(TextNormalizer.matches("Corazón de Neón", "CORAZON"))
        assertTrue(TextNormalizer.matches("La Heredera de Papel", "heredera"))
        assertFalse(TextNormalizer.matches("La Heredera de Papel", "jardin"))
    }

    @Test
    fun `convierte slug en titulo legible`() {
        assertEquals("El Senor Del Norte", TextNormalizer.titleFromSlug("el-senor-del-norte"))
        assertEquals("Corazon De Neon", TextNormalizer.titleFromSlug("corazon-de-neon"))
    }

    @Test
    fun `slugify elimina acentos y caracteres raros`() {
        assertEquals("corazon-de-neon", TextNormalizer.slugify("Corazón de Neón"))
        assertEquals("sin-titulo", TextNormalizer.slugify("!!!"))
    }

    @Test
    fun `texto vacio no coincide`() {
        assertFalse(TextNormalizer.matches("algo", ""))
        assertFalse(TextNormalizer.matches(null, "algo"))
    }
}
