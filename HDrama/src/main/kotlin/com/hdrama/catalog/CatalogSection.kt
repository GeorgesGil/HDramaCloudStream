package com.hdrama.catalog

/**
 * Secciones de la pantalla principal del proveedor.
 *
 * Las claves son estables: se guardan en el historial/ajustes de CloudStream,
 * asi que no deben renombrarse sin motivo. Se alinean con las secciones que
 * expone el sitio real (portada y categorias).
 */
enum class CatalogSection(val key: String, val displayName: String) {
    DESTACADOS("destacados", "Destacados"),
    NOVEDADES("novedades", "Novedades"),
    MAS_VISTOS("mas-vistos", "Más vistos"),
    TENDENCIAS("tendencias", "Tendencias"),
    CATALOGO("catalogo", "Catálogo general"),
    GENEROS("generos", "Géneros"),
    FUENTES("fuentes", "Fuentes"),
    ACCESO("acceso", "Acceso a HDrama"),
    ;

    companion object {
        fun fromKey(key: String?): CatalogSection =
            entries.firstOrNull { it.key == key } ?: NOVEDADES
    }
}
