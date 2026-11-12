package com.hdrama.models

/**
 * Ficha de una serie tal como la consume la capa de dominio.
 *
 * No contiene tipos de CloudStream: esto mantiene el nucleo probable con JVM
 * y desacopla el parser/adaptador de la API de la app.
 *
 * @property demo verdadero si el registro pertenece al catalogo sintetico.
 * @property browserOnly verdadero si solo puede abrirse en el navegador.
 * @property pageUrl URL original de la ficha (solo para navegacion).
 * @property externalId identificador interno del sitio (por ejemplo `bookId`
 *   de HDrama) cuando la ficha se obtuvo del HTML real.
 */
data class Series(
    val id: String,
    val slug: String,
    val title: String,
    val originalTitle: String? = null,
    val synopsis: String? = null,
    val genres: List<String> = emptyList(),
    val rating: Double? = null,
    val language: String = "es",
    val source: String = "HDrama (demo)",
    val posterUrl: String? = null,
    val episodeCount: Int? = null,
    val year: Int? = null,
    val relatedIds: List<String> = emptyList(),
    val demo: Boolean = true,
    val browserOnly: Boolean = false,
    val pageUrl: String? = null,
    val externalId: String? = null,
    val isNewThisWeek: Boolean = false,
    val recommended: Boolean = false,
) {
    /** Texto de busqueda normalizado: titulo, titulo original, generos y slug. */
    fun searchableText(): String = listOfNotNull(
        title,
        originalTitle,
        slug.replace('-', ' '),
        genres.joinToString(" "),
    ).joinToString(" ")
}
