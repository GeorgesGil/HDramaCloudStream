package com.hdrama.catalog

import com.hdrama.models.CatalogPage
import com.hdrama.models.Episode
import com.hdrama.models.MediaSource
import com.hdrama.models.Series
import com.hdrama.utils.UrlUtils

/**
 * Adaptador de catalogo HDrama pendiente de implementar para HTML.
 *
 * Las secciones y la busqueda aun no tienen extractor. La importacion manual
 * crea una ficha de navegacion sin descargar contenido.
 */
class HdramaCatalogAdapter : CatalogAdapter {
    override suspend fun section(section: CatalogSection, page: Int): CatalogPage<Series> {
        throw UnsupportedOperationException("Catalogo HTML de HDrama pendiente de implementar.")
    }

    override suspend fun search(query: String, page: Int): CatalogPage<Series> {
        throw UnsupportedOperationException("Busqueda HTML de HDrama pendiente de implementar.")
    }

    override suspend fun load(idOrUrl: String): Series? = ManualHdramaImport.fromUrl(idOrUrl)

    override suspend fun episodes(series: Series): List<Episode> {
        if (series.browserOnly) return listOf(ManualHdramaImport.browserEpisode(series))
        throw UnsupportedOperationException("Lectura de episodios HTML pendiente de implementar.")
    }

    override suspend fun resolve(episode: Episode): List<MediaSource> = episode.mediaSources

    override suspend fun related(series: Series): List<Series> = emptyList()

    fun implementationNote(): String =
        "Integracion de fichas HTML pendiente de implementar. " +
            "Sitio original: ${UrlUtils.HDRA_BASE_URL}"
}
