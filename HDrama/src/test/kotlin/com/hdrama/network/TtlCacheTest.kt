package com.hdrama.network

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TtlCacheTest {
    @Test
    fun `devuelve valor vigente y expira por ttl`() {
        var now = 1_000L
        val cache = TtlCache<String, String>(ttlMillis = 100, clock = { now })
        cache.put("a", "uno")

        assertEquals("uno", cache.get("a"))
        now = 1_101L
        assertNull(cache.get("a"))
    }

    @Test
    fun `respeta limite de entradas`() {
        val cache = TtlCache<String, Int>(ttlMillis = 10_000, maxEntries = 2)
        cache.put("a", 1)
        cache.put("b", 2)
        cache.put("c", 3)

        assertNull(cache.get("a"))
        assertEquals(2, cache.get("b"))
        assertEquals(3, cache.get("c"))
    }

    @Test
    fun `clear vacia la cache`() {
        val cache = TtlCache<String, Int>(ttlMillis = 10_000)
        cache.put("a", 1)
        cache.clear()
        assertNull(cache.get("a"))
    }
}
