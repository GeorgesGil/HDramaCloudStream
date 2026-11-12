package com.hdrama.catalog

import com.hdrama.models.Series
import com.hdrama.utils.UrlUtils

/** Tarjetas de navegacion e importacion comunes a todos los adaptadores. */
object AccessCards {
    fun all(): List<Series> = listOf(
        Series(
            id = "acceso-hdrama",
            slug = "acceso-hdrama",
            title = "HDrama: abrir sitio oficial",
            synopsis = "Abre es.hdrama.net en el navegador de Android como alternativa " +
                "si alguna ficha no carga dentro del plugin.",
            genres = listOf("Navegacion"),
            source = "Navegacion",
            language = "es",
            demo = false,
            browserOnly = true,
            pageUrl = UrlUtils.HDRA_BASE_URL,
            posterUrl = "https://placehold.co/300x450/0f172a/ffffff/png?text=HDrama",
        ),
        Series(
            id = "importar-enlace",
            slug = "importar-enlace",
            title = "Importar enlace de HDrama",
            synopsis = "Pega una URL https://es.hdrama.net/series/... en la busqueda de " +
                "CloudStream. La ficha se carga desde el HTML real del sitio.",
            genres = listOf("Ayuda"),
            source = "Navegacion",
            language = "es",
            demo = false,
            browserOnly = true,
            pageUrl = UrlUtils.HDRA_BASE_URL,
            posterUrl = "https://placehold.co/300x450/1d4ed8/ffffff/png?text=Importar",
        ),
        Series(
            id = "prueba-de-reproduccion",
            slug = "prueba-de-reproduccion",
            title = "Prueba de reproduccion (demo)",
            synopsis = "Abre una serie de demostracion con un MP4 y un HLS publicos " +
                "para comprobar el reproductor de CloudStream. No es contenido de HDrama.",
            genres = listOf("Demo"),
            source = "Demo local",
            language = "es",
            demo = true,
            browserOnly = false,
            posterUrl = "https://placehold.co/300x450/065f46/ffffff/png?text=Demo",
        ),
    )
}
