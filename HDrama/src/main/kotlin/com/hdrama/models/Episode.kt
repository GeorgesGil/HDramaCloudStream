package com.hdrama.models

/** Episodio estable de una serie. */
data class Episode(
    val id: String,
    val seriesId: String,
    val number: Int,
    val title: String? = null,
    val durationSeconds: Int? = null,
    val pageUrl: String? = null,
    val mediaSources: List<MediaSource> = emptyList(),
    val subtitles: List<SubtitleTrack> = emptyList(),
)

/** Pagina de catalogo con metadato de continuidad. */
data class CatalogPage<T>(
    val items: List<T>,
    val page: Int,
    val hasNext: Boolean,
) {
    companion object {
        fun <T> empty(page: Int = 1): CatalogPage<T> = CatalogPage(emptyList(), page, false)
    }
}
