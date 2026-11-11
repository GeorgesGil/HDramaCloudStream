package com.hdrama.utils

/** Identificador estable de episodio usado como `data` de CloudStream. */
object EpisodeId {
    private const val PREFIX = "hdrama-demo://episode/"
    const val BROWSER_EPISODE_ID = "hdrama-browser://episode"

    data class Parsed(val seriesId: String, val number: Int)

    fun encode(seriesId: String, number: Int): String = "$PREFIX$seriesId/$number"

    fun parse(data: String?): Parsed? {
        val raw = data?.trim().orEmpty()
        if (!raw.startsWith(PREFIX)) return null
        val rest = raw.removePrefix(PREFIX)
        val slash = rest.lastIndexOf('/')
        if (slash <= 0) return null
        val seriesId = rest.substring(0, slash)
        val number = rest.substring(slash + 1).toIntOrNull() ?: return null
        if (seriesId.isBlank() || number < 0) return null
        return Parsed(seriesId, number)
    }
}

/** Orden y deduplicacion de episodios. */
object EpisodeOrdering {
    private val numberPattern = Regex("(\\d{1,4})")

    fun sort(episodes: List<EpisodeKey>): List<EpisodeKey> =
        episodes
            .distinctBy { it.data }
            .sortedWith(compareBy({ it.number }, { it.data }))

    fun parseNumber(text: String?): Int? {
        if (text.isNullOrBlank()) return null
        return numberPattern.find(text)?.groupValues?.getOrNull(1)?.toIntOrNull()
    }

    data class EpisodeKey(val data: String, val number: Int)
}
