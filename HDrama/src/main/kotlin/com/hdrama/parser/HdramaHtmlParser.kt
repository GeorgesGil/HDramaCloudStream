package com.hdrama.parser

import com.hdrama.models.Episode
import com.hdrama.models.Series
import com.hdrama.utils.EpisodeId
import com.hdrama.utils.TextNormalizer
import com.hdrama.utils.UrlUtils
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URI

/** Ficha completa extraida de una pagina real de HDrama. */
data class HdramaSeriesPage(
    val series: Series,
    val bookId: String?,
    val episodes: List<Episode>,
    val related: List<Series>,
)

/**
 * Parser HTML validado contra el HTML real de HDrama (Next.js App Router).
 *
 * Fuente principal: JSON-LD (`TVSeries`, `CollectionPage`) y el payload RSC de
 * Next.js (`bookId`). Respaldo: selectores DOM de [ParserContract].
 * Nunca lanza por HTML inesperado: devuelve null o listas vacias.
 */
class HdramaHtmlParser(
    private val contract: ParserContract = ParserContract(),
) {
    // ------------------------------------------------------------------
    // Ficha de serie
    // ------------------------------------------------------------------

    fun parseSeriesPage(
        html: String,
        pageUrl: String,
        fallbackTitle: String? = null,
    ): HdramaSeriesPage? {
        val document = Jsoup.parse(html, pageUrl)
        val canonical = UrlUtils.normalizeHdramaUrl(pageUrl) ?: UrlUtils.HDRA_BASE_URL

        val slug = UrlUtils.extractSeriesSlug(canonical)
            ?: TextNormalizer.slugify(fallbackTitle ?: document.title())
        if (slug.isBlank()) return null

        val seriesNode = findTypedNode(document, "TVSeries")
        val heading = firstText(document, contract.detailTitle)

        val title = seriesNode?.optString("name")?.trim()?.takeIf { it.isNotEmpty() }
            ?: heading
            ?: fallbackTitle
            ?: TextNormalizer.titleFromSlug(slug)
        if (title.isBlank()) return null

        val synopsis = seriesNode?.optString("description")?.trim()?.takeIf { it.isNotEmpty() }
            ?: firstText(document, contract.detailSynopsis)

        val genres = nodeStringList(seriesNode, "genre")
            .ifEmpty { metadataValues(document, "Géneros") }

        val posterUrl = seriesNode?.let { node -> nodeString(node.opt("image")) }
            ?.takeIf { it.isNotBlank() }
            ?: posterFromDom(document, title, canonical)

        val episodeCount = seriesNode?.optInt("numberOfEpisodes", 0)?.takeIf { it > 0 }
            ?: metadataValues(document, "Capítulos").firstOrNull()?.let(::parseIntOrNull)

        val rating = parseRating(
            firstText(document, contract.detailRating)
                ?: metadataValues(document, "Puntuación").firstOrNull()
        )

        val bookId = parseBookId(html, slug)
        val episodes = parseEpisodes(document, slug, bookId)
        android.util.Log.d(
            "HDrama",
            "Ficha $slug: bookId=$bookId episodios=${episodes.size}",
        )
        val related = parseRelated(document, canonical, slug)

        // Sin evidencia de ficha (ni JSON-LD, ni h1, ni episodios) no se
        // inventa una serie a partir del slug de la URL.
        if (seriesNode == null && heading == null && episodes.isEmpty()) return null

        val series = Series(
            id = slug,
            slug = slug,
            title = title,
            synopsis = synopsis,
            genres = genres,
            rating = rating,
            language = "es",
            source = "HDrama",
            posterUrl = posterUrl,
            episodeCount = episodeCount ?: episodes.size.takeIf { it > 0 },
            demo = false,
            browserOnly = false,
            pageUrl = canonical,
            externalId = bookId,
        )
        return HdramaSeriesPage(series = series, bookId = bookId, episodes = episodes, related = related)
    }

    // ------------------------------------------------------------------
    // Listados y directorios
    // ------------------------------------------------------------------

    /** Listado de series: JSON-LD `CollectionPage` y respaldo por tarjetas. */
    fun parseCollection(html: String, pageUrl: String): List<Series> {
        val document = Jsoup.parse(html, pageUrl)
        val items = findItemListElement(document)
        val fromJson = items.mapNotNull { node -> collectionItemToSeries(node) }
            .distinctBy { it.id }
        if (fromJson.isNotEmpty()) return fromJson
        return parseCards(document, pageUrl)
    }

    /** Resultados de busqueda: el sitio no publica JSON-LD, solo tarjetas. */
    fun parseSearchResults(html: String, pageUrl: String): List<Series> {
        val document = Jsoup.parse(html, pageUrl)
        return parseCards(document, pageUrl)
    }

    /** Seccion de la portada (Destacados, Novedades, Mas vistos...). */
    fun parseHomeSection(html: String, sectionTitle: String, pageUrl: String): List<Series> {
        val document = Jsoup.parse(html, pageUrl)
        val heading = document.select("h2").firstOrNull {
            TextNormalizer.normalize(it.text()) == TextNormalizer.normalize(sectionTitle)
        } ?: return emptyList()

        val container = sectionContainer(heading) ?: document
        return parseCards(container, pageUrl).distinctBy { it.id }
    }

    /** Directorio de generos (`/categories`). */
    fun parseGenreCards(html: String): List<Series> =
        parseDirectory(Jsoup.parse(html, UrlUtils.HDRA_BASE_URL), contract.genreLink, "genero-")

    /** Directorio de fuentes (`/sources`). */
    fun parseSourceCards(html: String): List<Series> =
        parseDirectory(Jsoup.parse(html, UrlUtils.HDRA_BASE_URL), contract.sourceLink, "fuente-")

    /** Series relacionadas de una ficha (excluye la propia). */
    fun parseRelated(html: String, pageUrl: String, excludeSlug: String? = null): List<Series> =
        parseRelated(Jsoup.parse(html, pageUrl), pageUrl, excludeSlug)

    /**
     * Extrae el `bookId` interno del payload RSC de Next.js.
     *
     * En el HTML real las comillas van escapadas (`bookId\":\"...\"`), por lo
     * que se eliminan las barras invertidas. El valor **no siempre es numerico**:
     * puede ser un id hexadecimal o el propio slug de la serie. Se elige el
     * `bookId` mas cercano al slug de la ficha para no coger el de una serie
     * relacionada.
     */
    fun parseBookId(html: String, slug: String?): String? {
        val normalized = html.replace("\\", "")
        val matches = BOOK_ID.findAll(normalized).toList()
        if (matches.isEmpty()) return null
        if (matches.size == 1) return matches.first().groupValues.getOrNull(1)

        if (!slug.isNullOrBlank()) {
            val slugIndex = Regex("\"slug\"\\s*:\\s*\"" + Regex.escape(slug) + "\"")
                .find(normalized)?.range?.first
            if (slugIndex != null) {
                return matches.minByOrNull { match ->
                    kotlin.math.abs(match.range.first - slugIndex)
                }?.groupValues?.getOrNull(1)
            }
        }
        // Preferir el bookId del bloque principal (seguido del array de episodios).
        val episodesMarker = ",\"episodes\""
        return matches.firstOrNull { match ->
            normalized.regionMatches(
                match.range.last + 1,
                episodesMarker,
                0,
                episodesMarker.length,
            )
        }?.groupValues?.getOrNull(1) ?: matches.first().groupValues.getOrNull(1)
    }

    // ------------------------------------------------------------------
    // Interno: fichas y tarjetas
    // ------------------------------------------------------------------

    private fun parseRelated(
        document: Document,
        pageUrl: String,
        excludeSlug: String?,
    ): List<Series> {
        val heading = document.select("h2").firstOrNull {
            TextNormalizer.normalize(it.text()).contains(
                TextNormalizer.normalize(contract.relatedHeading)
            )
        }
        val scope: Element = heading?.let { sectionContainer(it) } ?: document
        return parseCards(scope, pageUrl)
            .filter { excludeSlug == null || it.slug != excludeSlug }
            .distinctBy { it.id }
            .take(MAX_RELATED)
    }

    private fun parseCards(root: Element, pageUrl: String): List<Series> {
        val anchors = if (root.tagName() == "a") listOf(root) else root.select(contract.cardLink)
        return anchors.mapNotNull { anchor -> cardToSeries(anchor, pageUrl) }.distinctBy { it.id }
    }

    private fun cardToSeries(anchor: Element, pageUrl: String): Series? {
        val href = anchor.absUrl("href").ifBlank { anchor.attr("href") }
        val canonical = canonicalSeriesUrl(href) ?: return null
        val slug = UrlUtils.extractSeriesSlug(canonical) ?: return null
        if (slug.isBlank()) return null

        val image = anchor.selectFirst(contract.cardImage) ?: anchor.selectFirst("img")
        val title = image?.attr(contract.cardTitleAttr)?.trim()?.takeIf { it.isNotEmpty() }
            ?: anchor.attr("aria-label").trim().takeIf { it.isNotEmpty() }
            ?: anchor.text().trim().takeIf { it.isNotEmpty() }
            ?: TextNormalizer.titleFromSlug(slug)

        val cardText = anchor.text()

        return Series(
            id = slug,
            slug = slug,
            title = title,
            language = "es",
            source = "HDrama",
            posterUrl = image?.let { pickPoster(it, pageUrl) },
            episodeCount = EPISODE_BADGE.find(cardText)?.groupValues?.getOrNull(1)?.toIntOrNull(),
            rating = RATING_BADGE.find(cardText)?.groupValues?.getOrNull(1)
                ?.replace(',', '.')?.toDoubleOrNull()?.coerceIn(0.0, 10.0),
            demo = false,
            browserOnly = false,
            pageUrl = canonical,
        )
    }

    private fun collectionItemToSeries(node: JSONObject): Series? {
        val url = node.optString("url").trim().ifEmpty { return null }
        val canonical = canonicalSeriesUrl(url) ?: return null
        val slug = UrlUtils.extractSeriesSlug(canonical) ?: return null
        val title = node.optString("name").trim().takeIf { it.isNotEmpty() }
            ?: TextNormalizer.titleFromSlug(slug)
        return Series(
            id = slug,
            slug = slug,
            title = title,
            language = "es",
            source = "HDrama",
            posterUrl = nodeString(node.opt("image"))?.takeIf { it.isNotBlank() },
            demo = false,
            browserOnly = false,
            pageUrl = canonical,
        )
    }

    /**
     * El JSON-LD de los listados usa el host `hdrama.net` (sin `es.`) en las
     * URLs, que [UrlUtils.normalizeHdramaUrl] rechaza. Se reconstruye la URL
     * canonica a partir del path `/series/<slug>`.
     */
    private fun canonicalSeriesUrl(value: String?): String? {
        val raw = value?.trim().orEmpty()
        if (raw.isEmpty()) return null
        UrlUtils.normalizeHdramaUrl(raw)?.let { return it }
        val withScheme = when {
            raw.startsWith("http://") || raw.startsWith("https://") -> raw
            raw.startsWith("//") -> "https:$raw"
            else -> "https://es.hdrama.net/${raw.trimStart('/')}"
        }
        val path = runCatching { URI(withScheme).path }.getOrNull() ?: return null
        val segments = path.split('/').filter { it.isNotBlank() }
        val seriesIndex = segments.indexOf("series")
        if (seriesIndex < 0) return null
        val slug = segments.getOrNull(seriesIndex + 1)?.takeIf { it.isNotBlank() } ?: return null
        return UrlUtils.seriesPageUrl(slug)
    }

    private fun parseDirectory(
        document: Document,
        selector: String,
        idPrefix: String,
    ): List<Series> = document.select(selector).mapNotNull { anchor ->
        val href = anchor.absUrl("href").ifBlank { anchor.attr("href") }
        val canonical = UrlUtils.normalizeHdramaUrl(href) ?: return@mapNotNull null
        val path = runCatching { URI(canonical).path }.getOrNull().orEmpty()
        val segments = path.split('/').filter { it.isNotBlank() }
            .dropWhile { it == "es" }
        val expectedRoot = if (idPrefix == "genero-") "category" else "source"
        if (segments.size != 2 || segments[0] != expectedRoot) return@mapNotNull null
        val slug = segments[1]
        if (slug.isBlank()) return@mapNotNull null
        if (idPrefix == "genero-" && slug in RESERVED_CATEGORY_SLUGS) return@mapNotNull null

        val title = anchor.selectFirst("h2, h3, strong, .title")
            ?.text()?.trim()?.takeIf { it.isNotEmpty() }
            ?: anchor.attr("title").trim().takeIf { it.isNotEmpty() }
            ?: anchor.text().trim().takeIf { it.isNotEmpty() }
            ?: TextNormalizer.titleFromSlug(slug)

        Series(
            id = idPrefix + slug,
            slug = idPrefix + slug,
            title = title,
            language = "es",
            source = "HDrama",
            posterUrl = anchor.selectFirst("img")?.let { pickPoster(it, canonical) },
            demo = false,
            browserOnly = false,
            pageUrl = UrlUtils.normalizeHdramaUrl("https://es.hdrama.net/$expectedRoot/$slug"),
        )
    }.distinctBy { it.id }

    // ------------------------------------------------------------------
    // Interno: episodios
    // ------------------------------------------------------------------

    private fun parseEpisodes(
        document: Document,
        seriesId: String,
        bookId: String?,
    ): List<Episode> {
        val items = document.select(contract.episodeItem)
        if (items.isEmpty()) return emptyList()

        return items.mapIndexedNotNull { index, item ->
            val href = item.absUrl("href").ifBlank { item.attr("href") }
            val pageUrl = UrlUtils.normalizeHdramaUrl(href)
            val display = item.attr("data-display").trim().toIntOrNull()
                ?: UrlUtils.extractEpisodeNumber(pageUrl)
                ?: (index + 1)
            val serial = item.attr("data-serial").trim().toIntOrNull() ?: display

            val title = item.selectFirst(contract.episodeTitle)?.text()?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?: item.attr("aria-label").trim().takeIf { it.isNotEmpty() }
                ?: "Capítulo $display"

            val duration = item.select(contract.episodeDuration)
                .mapNotNull { parseDuration(it.text()) }
                .firstOrNull()

            Episode(
                id = EpisodeId.encode(seriesId, display),
                seriesId = seriesId,
                number = display,
                title = title,
                durationSeconds = duration,
                pageUrl = pageUrl,
                externalId = bookId?.let { "$it:$serial" },
            )
        }
            .sortedBy { it.number }
            .distinctBy { it.number }
    }

    private fun parseDuration(text: String?): Int? {
        val raw = text?.trim().orEmpty()
        if (raw.isEmpty()) return null
        val match = DURATION.find(raw) ?: return null
        val groups = match.groupValues
        val hours = groups.getOrNull(1)?.toIntOrNull() ?: 0
        val minutes = groups.getOrNull(2)?.toIntOrNull() ?: return null
        val seconds = groups.getOrNull(3)?.toIntOrNull() ?: 0
        return hours * 3600 + minutes * 60 + seconds
    }

    private fun parseRating(value: String?): Double? {
        val raw = value?.trim().orEmpty()
        if (raw.isEmpty()) return null
        val match = RATING_VALUE.find(raw) ?: return null
        return match.groupValues[1].replace(',', '.').toDoubleOrNull()?.coerceIn(0.0, 10.0)
    }

    private fun posterFromDom(document: Document, title: String, baseUrl: String): String? {
        document.select(contract.detailPoster)
            .firstOrNull { it.attr("alt").trim() == title }
            ?.let { return pickPoster(it, baseUrl) }
        return document.selectFirst("main img[alt]")?.let { pickPoster(it, baseUrl) }
    }

    private fun pickPoster(image: Element, baseUrl: String): String? {
        val srcset = image.attr("srcset")
        if (srcset.isNotBlank()) {
            // Las URLs del sitio llevan comas dentro de la query
            // (`x-oss-process=m_fill,w_256,h_341`), asi que solo se corta el
            // candidato cuando la coma va seguida de otra URL.
            val first = srcset.split(SRCSET_SEPARATOR).firstOrNull()
                ?.trim()?.substringBefore(' ')
            if (!first.isNullOrBlank()) return resolveUrl(first, baseUrl)
        }
        val src = image.attr("src").trim()
        if (src.isNotBlank()) return resolveUrl(src, baseUrl)
        val dataSrc = image.attr("data-src").trim()
        if (dataSrc.isNotBlank()) return resolveUrl(dataSrc, baseUrl)
        return null
    }

    private fun resolveUrl(value: String, baseUrl: String): String? {
        if (value.startsWith("http://") || value.startsWith("https://")) return value
        return runCatching { URI(baseUrl).resolve(value).toString() }.getOrNull()
    }

    private fun sectionContainer(heading: Element): Element? {
        // Primer ancestro que contiene solo esta seccion y al menos una tarjeta.
        heading.parents().toList().forEach { ancestor ->
            if (ancestor.select("h2").size <= 1 &&
                ancestor.select(contract.cardLink).isNotEmpty()
            ) {
                return ancestor
            }
        }
        // Respaldo: hermanos siguientes hasta el proximo h2.
        val wrapper = heading.parent() ?: return null
        var sibling = heading.nextElementSibling()
        if (sibling == null) return wrapper
        val collected = mutableListOf<Element>()
        while (sibling != null && sibling.tagName() != "h2") {
            collected += sibling
            sibling = sibling.nextElementSibling()
        }
        if (collected.isEmpty()) return wrapper
        val container = Element("section")
        collected.forEach { container.appendChild(it.clone()) }
        return container
    }

    private fun metadataValues(document: Document, term: String): List<String> {
        val normalizedTerm = TextNormalizer.normalize(term)
        return document.select(contract.metadataList)
            .flatMap { list ->
                list.select("div").mapNotNull { row ->
                    val dt = row.selectFirst(contract.metadataTerm)?.text()?.trim()
                    val dd = row.selectFirst(contract.metadataValue)?.text()?.trim()
                    if (dt != null && dd != null &&
                        TextNormalizer.normalize(dt) == normalizedTerm
                    ) {
                        dd
                    } else {
                        null
                    }
                }
            }
    }

    // ------------------------------------------------------------------
    // Interno: JSON-LD
    // ------------------------------------------------------------------

    private fun jsonLdRoots(document: Document): List<Any> =
        document.select(contract.jsonLd).mapNotNull { script ->
            runCatching { org.json.JSONTokener(script.data()).nextValue() }.getOrNull()
        }

    private fun findItemListElement(document: Document): List<JSONObject> {
        jsonLdRoots(document).forEach { root ->
            val found = findArrayRecursive(root, "itemListElement")
            if (found != null) {
                return (0 until found.length()).mapNotNull { index -> found.optJSONObject(index) }
            }
        }
        return emptyList()
    }

    private fun findTypedNode(document: Document, type: String): JSONObject? {
        jsonLdRoots(document).forEach { root ->
            findTypedRecursive(root, type)?.let { return it }
        }
        return null
    }

    private fun findArrayRecursive(value: Any?, key: String): JSONArray? {
        when (value) {
            is JSONObject -> {
                val direct = value.optJSONArray(key)
                if (direct != null && direct.length() > 0) return direct
                val names = value.names() ?: return null
                for (index in 0 until names.length()) {
                    findArrayRecursive(value.opt(names.getString(index)), key)?.let { return it }
                }
            }
            is JSONArray -> {
                for (index in 0 until value.length()) {
                    findArrayRecursive(value.opt(index), key)?.let { return it }
                }
            }
        }
        return null
    }

    private fun findTypedRecursive(value: Any?, type: String): JSONObject? {
        when (value) {
            is JSONObject -> {
                val nodeType = value.opt("@type")
                val types = when (nodeType) {
                    is String -> listOf(nodeType)
                    is JSONArray -> (0 until nodeType.length()).mapNotNull { nodeType.optString(it) }
                    else -> emptyList()
                }
                if (types.any { it.equals(type, ignoreCase = true) }) return value
                val names = value.names() ?: return null
                for (index in 0 until names.length()) {
                    findTypedRecursive(value.opt(names.getString(index)), type)?.let { return it }
                }
            }
            is JSONArray -> {
                for (index in 0 until value.length()) {
                    findTypedRecursive(value.opt(index), type)?.let { return it }
                }
            }
        }
        return null
    }

    private fun nodeString(value: Any?): String? = when (value) {
        null -> null
        is String -> value
        is JSONArray -> value.optString(0).takeIf { it.isNotBlank() }
        is JSONObject -> value.optString("url").takeIf { it.isNotBlank() }
        else -> null
    }

    private fun nodeStringList(node: JSONObject?, key: String): List<String> {
        val value = node?.opt(key) ?: return emptyList()
        return when (value) {
            is String -> listOf(value)
            is JSONArray -> (0 until value.length()).mapNotNull {
                value.optString(it).takeIf { text -> text.isNotBlank() }
            }
            else -> emptyList()
        }
    }

    private fun firstText(root: Element, selector: String): String? =
        root.selectFirst(selector)?.text()?.trim()?.takeIf { it.isNotEmpty() }

    private fun parseIntOrNull(value: String): Int? =
        Regex("(\\d{1,4})").find(value)?.groupValues?.getOrNull(1)?.toIntOrNull()

    private companion object {
        const val MAX_RELATED = 20
        val EPISODE_BADGE = Regex("EP\\s*(\\d{1,4})", RegexOption.IGNORE_CASE)
        val RATING_BADGE = Regex("(\\d{1,2}[.,]\\d)")
        val RATING_VALUE = Regex("(\\d{1,2}(?:[.,]\\d{1,2})?)")
        val DURATION = Regex("^(?:(\\d{1,2}):)?(\\d{1,3}):(\\d{2})$")
        val SRCSET_SEPARATOR = Regex(""",\s*(?=https?://|//)""")
        val BOOK_ID = Regex("bookId\"\\s*:\\s*\"([A-Za-z0-9_-]{1,80})\"")
        val RESERVED_CATEGORY_SLUGS = setOf("popular", "new-releases", "trending")
    }
}
