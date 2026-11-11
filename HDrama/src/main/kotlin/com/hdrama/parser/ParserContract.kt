package com.hdrama.parser

/**
 * Contrato de selectores HTML para el adaptador HDrama.
 *
 * IMPORTANTE: este contrato es una hipotesis de trabajo derivada de la
 * estructura publica observada y de las fixtures sinteticas. **No ha sido
 * validado contra el HTML real** porque el sitio responde con un Cloudflare
 * Managed Challenge (HTTP 403). Solo debe habilitarse con autorizacion y
 * fixtures reales autorizados.
 */
data class ParserContract(
    val seriesItem: String = "article.serie-card, article.serie, .serie-card, .item",
    val title: String = ".serie-title, .title, h2, h3",
    val link: String = "a[href]",
    val poster: String = "img",
    val synopsis: String = ".synopsis, .description, .plot",
    val genres: String = ".genres a, .genres span, .genre",
    val rating: String = ".rating, .score",
    val episodeCount: String = ".episode-count, .episodes",
    val originalTitle: String = ".original-title, .original",
    val detailTitle: String = "h1, .serie-title",
    val detailSynopsis: String = ".synopsis, .description, .plot",
    val detailPoster: String = ".poster img, img.poster, img",
    val detailGenres: String = ".genres a, .genres span, .genre, [itemprop=genre]",
    val detailRating: String = ".rating, .score, [itemprop=ratingValue]",
    val detailYear: String = ".year, [itemprop=datePublished]",
    val episodeItem: String = ".episode-item, .episode, li.episode",
    val episodeNumber: String = ".episode-number, .num, [data-number]",
    val episodeTitle: String = ".episode-title, .title",
    val episodeLink: String = "a[href]",
    val videoSource: String = "video source, source, [data-video]",
    val subtitleTrack: String = "track[kind=subtitles], track",
)
