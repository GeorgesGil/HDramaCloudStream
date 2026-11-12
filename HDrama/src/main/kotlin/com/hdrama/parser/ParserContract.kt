package com.hdrama.parser

/**
 * Contrato de selectores HTML validado contra el HTML real de HDrama
 * (Next.js App Router, es.hdrama.net, 2026-11-11).
 *
 * Los selectores viven aqui para poder ajustarlos sin tocar la logica del
 * parser. Los datos estructurados (JSON-LD) son la fuente principal; los
 * selectores DOM son el respaldo.
 */
data class ParserContract(
    // Listados / tarjetas
    val cardItem: String = "article",
    val cardLink: String = "a[href*=\"/series/\"]",
    val cardImage: String = "img[alt]",
    val cardTitleAttr: String = "alt",

    // Ficha de serie
    val detailTitle: String = "h1",
    val detailSynopsis: String = "details summary span",
    val detailRating: String = "span.text-rating",
    val detailPoster: String = "img[alt]",
    val metadataList: String = "dl",
    val metadataTerm: String = "dt",
    val metadataValue: String = "dd",
    val relatedHeading: String = "También te puede gustar",

    // Episodios
    val episodeItem: String = "a[data-serial][href*=\"/episode-\"]",
    val episodeTitle: String = "span.block.truncate",
    val episodeDuration: String = "span.tabular-nums, span.text-muted",
    val episodeLink: String = "a[href]",

    // Directorios
    val genreLink: String = "a[href*=\"/category/\"]",
    val sourceLink: String = "a[href*=\"/source/\"]",

    // JSON-LD
    val jsonLd: String = "script[type=\"application/ld+json\"]",
)
