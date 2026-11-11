package com.hdrama

import com.hdrama.catalog.CatalogSection
import com.hdrama.utils.AuthorizationPolicy
import com.hdrama.utils.AuthorizationStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PluginInfoTest {
    @Test
    fun `metadatos coinciden con el manifest esperado`() {
        assertEquals("HDrama", PluginInfo.NAME)
        assertEquals(1, PluginInfo.VERSION)
        assertEquals(1, PluginInfo.API_VERSION)
        assertEquals("com.hdrama.HDramaPlugin", PluginInfo.PLUGIN_CLASS)
        assertEquals("es", PluginInfo.LANGUAGE)
        assertEquals("https://es.hdrama.net/", PluginInfo.MAIN_URL)
    }

    @Test
    fun `la integracion protegida sigue deshabilitada`() {
        assertFalse(AuthorizationPolicy.isAuthorized())
        assertEquals(AuthorizationStatus.NOT_GRANTED, AuthorizationPolicy.status())
    }

    @Test
    fun `todas las secciones solicitadas existen`() {
        val keys = CatalogSection.entries.map { it.key }.toSet()
        listOf(
            "novedades",
            "mas-vistos",
            "nuevos-semana",
            "catalogo",
            "generos",
            "recomendaciones",
        ).forEach { assertTrue(it in keys, "Falta la seccion $it") }
    }
}
