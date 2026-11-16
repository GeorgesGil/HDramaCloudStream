package com.hdrama

import com.hdrama.catalog.CatalogSection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PluginInfoTest {
    @Test
    fun `metadatos coinciden con el manifest esperado`() {
        assertEquals("HDrama", PluginInfo.NAME)
        assertEquals(4, PluginInfo.VERSION)
        assertEquals(1, PluginInfo.API_VERSION)
        assertEquals("com.hdrama.HDramaPlugin", PluginInfo.PLUGIN_CLASS)
        assertEquals("es", PluginInfo.LANGUAGE)
        assertEquals("https://es.hdrama.net/", PluginInfo.MAIN_URL)
    }

    @Test
    fun `todas las secciones solicitadas existen`() {
        val keys = CatalogSection.entries.map { it.key }.toSet()
        listOf(
            "destacados",
            "novedades",
            "mas-vistos",
            "tendencias",
            "catalogo",
            "generos",
            "fuentes",
        ).forEach { assertTrue(it in keys, "Falta la seccion $it") }
    }

    @Test
    fun `la extension no expone secciones de diagnostico ni demo`() {
        val keys = CatalogSection.entries.map { it.key }
        assertEquals(7, keys.size, "La pantalla principal debe tener 7 secciones reales")
        assertFalse("acceso" in keys, "No debe existir la seccion de acceso/diagnostico")
    }
}
