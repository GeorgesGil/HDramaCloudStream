package com.hdrama

import com.hdrama.catalog.CatalogSection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PluginInfoTest {
    @Test
    fun `metadatos coinciden con el manifest esperado`() {
        assertEquals("HDrama", PluginInfo.NAME)
        assertEquals(2, PluginInfo.VERSION)
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
            "acceso",
        ).forEach { assertTrue(it in keys, "Falta la seccion $it") }
    }
}
