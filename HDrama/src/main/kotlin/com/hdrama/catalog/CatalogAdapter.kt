package com.hdrama.catalog

import com.hdrama.models.CatalogPage
import com.hdrama.models.Episode
import com.hdrama.models.MediaSource
import com.hdrama.models.Series

/**
 * Contrato de una fuente de catalogo. Permite intercambiar el catalogo demo
 * por un adaptador HDrama con metadatos completos sin cambiar el provider.
 */
interface CatalogAdapter {
    suspend fun section(section: CatalogSection, page: Int): CatalogPage<Series>

    suspend fun search(query: String, page: Int): CatalogPage<Series>

    suspend fun load(idOrUrl: String): Series?

    suspend fun episodes(series: Series): List<Episode>

    suspend fun resolve(episode: Episode): List<MediaSource>

    suspend fun related(series: Series): List<Series>
}
