package com.hdrama.utils

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EpisodeIdTest {
    @Test
    fun `codifica y decodifica identificadores estables`() {
        val data = EpisodeId.encode("corazon-de-neon", 10)
        assertEquals("hdrama-demo://episode/corazon-de-neon/10", data)
        val parsed = EpisodeId.parse(data)
        assertEquals("corazon-de-neon", parsed?.seriesId)
        assertEquals(10, parsed?.number)
    }

    @Test
    fun `rechaza identificadores invalidos`() {
        assertNull(EpisodeId.parse(null))
        assertNull(EpisodeId.parse(""))
        assertNull(EpisodeId.parse("hdrama-demo://episode/sin-numero"))
        assertNull(EpisodeId.parse("https://es.hdrama.net/series/x"))
    }

    @Test
    fun `ordena numericamente 1 2 10`() {
        val sorted = EpisodeOrdering.sort(
            listOf(
                EpisodeOrdering.EpisodeKey("ep-10", 10),
                EpisodeOrdering.EpisodeKey("ep-2", 2),
                EpisodeOrdering.EpisodeKey("ep-1", 1),
            )
        )
        assertEquals(listOf(1, 2, 10), sorted.map { it.number })
    }

    @Test
    fun `extrae numero de texto en espanol`() {
        assertEquals(7, EpisodeOrdering.parseNumber("Episodio 7"))
        assertEquals(12, EpisodeOrdering.parseNumber("Capítulo 12"))
        assertNull(EpisodeOrdering.parseNumber("sin numero"))
    }
}
