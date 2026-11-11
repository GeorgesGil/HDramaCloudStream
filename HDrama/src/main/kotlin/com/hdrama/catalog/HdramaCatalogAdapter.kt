package com.hdrama.catalog

import com.hdrama.models.CatalogPage
import com.hdrama.models.Episode
import com.hdrama.models.MediaSource
import com.hdrama.models.Series
import com.hdrama.utils.UrlUtils

/**
 * Adaptador HDrama pendiente de habilitacion.
 *
 * Mientras [com.hdrama.utils.AuthorizationPolicy.HDRAMA_AUTHORIZED] sea false,
 * cualquier operacion de catalogo/busqueda lanza una excepcion explicita y no
 * se abre ninguna conexion. La unica operacion permitida es la importacion
 * manual de una URL, que no descarga contenido.
 */
class HdramaCatalogAdapter : CatalogAdapter {
    override suspend fun section(section: CatalogSection, page: Int): CatalogPage<Series> {
        throw com.hdrama.utils.UnauthorizedAccessException(
            "Catalogo HDrama deshabilitado: falta autorizacion escrita del sitio."
        )
    }

    override suspend fun search(query: String, page: Int): CatalogPage<Series> {
        throw com.hdrama.utils.UnauthorizedAccessException(
            "Busqueda HDrama deshabilitada: falta autorizacion escrita del sitio."
        )
    }

    override suspend fun load(idOrUrl: String): Series? = ManualHdramaImport.fromUrl(idOrUrl)

    override suspend fun episodes(series: Series): List<Episode> {
        if (series.browserOnly) return listOf(ManualHdramaImport.browserEpisode(series))
        throw com.hdrama.utils.UnauthorizedAccessException(
            "Lista de episodios HDrama deshabilitada: falta autorizacion escrita del sitio."
        )
    }

    override suspend fun resolve(episode: Episode): List<MediaSource> = episode.mediaSources

    override suspend fun related(series: Series): List<Series> = emptyList()

    fun authorizationNote(): String =
        "Integracion HDrama pendiente de autorizacion. " +
            "URL permitida para navegacion: ${UrlUtils.HDRA_BASE_URL}"
}
