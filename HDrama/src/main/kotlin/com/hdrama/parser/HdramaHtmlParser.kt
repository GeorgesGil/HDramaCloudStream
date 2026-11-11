package com.hdrama.parser

import com.hdrama.models.Episode
import com.hdrama.models.MediaSource
import com.hdrama.models.Series
import com.hdrama.models.SubtitleTrack
import com.hdrama.utils.EpisodeId
import com.hdrama.utils.EpisodeOrdering
import com.hdrama.utils.TextNormalizer
import com.hdrama.utils.UrlUtils
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URI

/**
 * Parser HTML basado en jsoup (no en expresiones regulares fragiles).
 *
 * Ver la advertencia de [ParserContract]: los selectores no estan validados
 * contra el HTML real de HDrama.
 */
class HdramaHtmlParser(
    private val contract: ParserContract = ParserContract(),
) {
    fun parseSeriesList(html: String, baseUrl: String = UrlUtils.HDRA_BASE_URL): List<Series> {
        val document = Jsoup.parse(html, baseUrl)
        return document.select(contract.seriesItem)
            .mapNotNull { parseSeriesItem(it, baseUrl) }
            .distinctBy { it.id }
    }

    fun parseSeriesDetail(
        html: String,
        pageUrl: String,
        fallbackTitle: String? = null,
    ): Series? {
        val document = Jsoup.parse(html, pageUrl)
        val canonical = UrlUtils.normalizeHdramaUrl(pageUrl)
        val slug = UrlUtils.extractSeriesSlug(canonical)
            ?: TextNormalizer.slugify(fallbackTitle ?: document.title())

        val title = firstText(document, contract.detailTitle)
            ?: fallbackTitle
            ?: TextNormalizer.titleFromSlug(slug)
        if (title.isBlank()) return null

        return Series(
            id = slug,
            slug = slug,
            title = title,
            originalTitle = firstText(document, contract.originalTitle),
            synopsis = firstText(document, contract.detailSynopsis),
            genres = texts(document, contract.detailGenres),
            rating = parseRating(firstText(document, contract.detailRating)),
            language = "es",
            source = "HDrama",
            posterUrl = firstAttr(document, contract.detailPoster, "src", pageUrl),
            episodeCount = parseEpisodeCount(firstText(document, contract.episodeCount)),
            year = parseYear(firstText(document, contract.detailYear)),
            demo = false,
            pageUrl = canonical,
        )
    }

    fun parseEpisodes(
        html: String,
        seriesId: String,
        baseUrl: String = UrlUtils.HDRA_BASE_URL,
    ): List<Episode> {
        val document = Jsoup.parse(html, baseUrl)
        val items = document.select(contract.episodeItem)
        if (items.isEmpty()) return emptyList()

        val parsed = items.mapIndexedNotNull { index, item ->
            val link = item.selectFirst(contract.episodeLink)
            val pageUrl = link?.attr("href")?.let { resolve(it, baseUrl) }
            val number = EpisodeOrdering.parseNumber(
                firstText(item, contract.episodeNumber)
                    ?: link?.text()
                    ?: item.attr("data-number")
            ) ?: (index + 1)

            val media = parseMedia(item)
            Episode(
                id = EpisodeId.encode(seriesId, number),
                seriesId = seriesId,
                number = number,
                title = firstText(item, contract.episodeTitle),
                durationSeconds = parseDuration(item),
                pageUrl = pageUrl,
                mediaSources = media,
                subtitles = parseSubtitles(item, baseUrl),
            )
        }
        return parsed
            .sortedBy { it.number }
            .distinctBy { it.number }
    }

    private fun parseSeriesItem(item: Element, baseUrl: String): Series? {
        val link = item.selectFirst(contract.link)
        val href = link?.attr("href")?.takeIf { it.isNotBlank() }
            ?: item.attr("data-url").takeIf { it.isNotBlank() }
        val pageUrl = href?.let { resolve(it, baseUrl) }
        val title = firstText(item, contract.title) ?: link?.text()?.trim().orEmpty()
        if (title.isBlank()) return null

        val slug = UrlUtils.extractSeriesSlug(pageUrl)
            ?: TextNormalizer.slugify(title)

        return Series(
            id = slug,
            slug = slug,
            title = title,
            originalTitle = firstText(item, contract.originalTitle),
            synopsis = firstText(item, contract.synopsis),
            genres = texts(item, contract.genres),
            rating = parseRating(firstText(item, contract.rating)),
            language = "es",
            source = "HDrama",
            posterUrl = firstAttr(item, contract.poster, "src", baseUrl)
                ?: firstAttr(item, contract.poster, "data-src", baseUrl),
            episodeCount = parseEpisodeCount(firstText(item, contract.episodeCount)),
            demo = false,
            pageUrl = pageUrl,
        )
    }

    private fun parseMedia(item: Element): List<MediaSource> {
        val source = item.selectFirst(contract.videoSource) ?: return listOf(
            MediaSource.Unavailable("No se encontro una fuente de video en el HTML.")
        )
        val url = sequenceOf("src", "data-video", "data-src")
            .map { source.attr(it).trim() }
            .firstOrNull { it.isNotBlank() }
            ?: return listOf(MediaSource.Unavailable("La fuente de video no tiene URL."))

        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return listOf(MediaSource.Unavailable("La fuente de video no es una URL absoluta."))
        }

        val quality = source.attr("data-quality").toIntOrNull()
        return when {
            url.substringBefore('?').endsWith(".m3u8", ignoreCase = true) ->
                listOf(MediaSource.Hls(url, quality))
            url.substringBefore('?').endsWith(".mp4", ignoreCase = true) ->
                listOf(MediaSource.Mp4(url, quality))
            else -> listOf(MediaSource.Unavailable("Tipo de fuente no compatible: ${url.take(80)}"))
        }
    }

    private fun parseSubtitles(item: Element, baseUrl: String): List<SubtitleTrack> =
        item.select(contract.subtitleTrack).mapNotNull { track ->
            val url = track.attr("src").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val lang = track.attr("srclang").ifBlank { "es" }
            SubtitleTrack(lang = lang, url = resolve(url, baseUrl))
        }

    private fun parseDuration(item: Element): Int? {
        val raw = item.attr("data-duration").trim()
            .ifBlank { item.selectFirst("[data-duration]")?.attr("data-duration")?.trim().orEmpty() }
        if (raw.isNotEmpty()) {
            raw.toIntOrNull()?.let { return it }
            return Regex("(\\d+)").find(raw)?.groupValues?.get(1)?.toIntOrNull()
        }
        val text = item.text()
        val minutes = Regex("(\\d+)\\s*(?:min|minutos)", RegexOption.IGNORE_CASE)
            .find(text)?.groupValues?.get(1)?.toIntOrNull()
        return minutes?.times(60)
    }

    private fun firstText(root: Element, selector: String): String? =
        root.selectFirst(selector)?.text()?.trim()?.takeIf { it.isNotBlank() }

    private fun texts(root: Element, selector: String): List<String> =
        root.select(selector)
            .map { it.text().trim() }
            .filter { it.isNotBlank() }
            .distinct()

    private fun firstAttr(root: Element, selector: String, attr: String, baseUrl: String): String? {
        val element = root.selectFirst(selector) ?: return null
        val value = element.attr(attr).trim()
        if (value.isBlank()) return null
        return resolve(value, baseUrl)
    }

    private fun resolve(value: String, baseUrl: String): String {
        return runCatching {
            URI(baseUrl).resolve(value).toString()
        }.getOrDefault(value)
    }

    private fun parseRating(value: String?): Double? {
        if (value.isNullOrBlank()) return null
        val match = Regex("(\\d{1,2}(?:[.,]\\d{1,2})?)").find(value) ?: return null
        val number = match.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return null
        return number.coerceIn(0.0, 10.0)
    }

    private fun parseEpisodeCount(value: String?): Int? {
        if (value.isNullOrBlank()) return null
        return Regex("(\\d{1,4})").find(value)?.groupValues?.get(1)?.toIntOrNull()
    }

    private fun parseYear(value: String?): Int? {
        if (value.isNullOrBlank()) return null
        return Regex("(19|20)\\d{2}").find(value)?.value?.toIntOrNull()
    }

    @Suppress("unused")
    private fun documentTitle(document: Document): String = document.title()
}
