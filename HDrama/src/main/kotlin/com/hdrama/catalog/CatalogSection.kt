package com.hdrama.catalog

/** Secciones de la pantalla principal solicitadas por el proyecto. */
enum class CatalogSection(val key: String, val displayName: String) {
    NOVEDADES("novedades", "Novedades"),
    MAS_VISTOS("mas-vistos", "Más vistos"),
    NUEVOS_SEMANA("nuevos-semana", "Nuevos esta semana"),
    CATALOGO("catalogo", "Catálogo general"),
    GENEROS("generos", "Géneros"),
    RECOMENDACIONES("recomendaciones", "Recomendaciones"),
    ACCESO("acceso", "Acceso a HDrama"),
    ;

    companion object {
        fun fromKey(key: String?): CatalogSection =
            entries.firstOrNull { it.key == key } ?: NOVEDADES
    }
}
