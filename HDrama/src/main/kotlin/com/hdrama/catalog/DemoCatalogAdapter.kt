package com.hdrama.catalog

import com.hdrama.models.CatalogPage
import com.hdrama.models.Episode
import com.hdrama.models.MediaSource
import com.hdrama.models.Series
import com.hdrama.utils.EpisodeId
import com.hdrama.utils.TextNormalizer
import com.hdrama.utils.UrlUtils

/**
 * Catalogo sintetico local.
 *
 * No contiene metadatos de HDrama. Sirve para verificar todos los flujos del
 * plugin con datos permitidos y videos de dominio publico:
 * - MP4: trailer de Sintel (Blender, CC BY 3.0, alojado en media.w3.org).
 * - HLS: stream de prueba publico de Mux.
 *
 * Los titulos son ficticios y estan etiquetados como demo en la UI.
 */
class DemoCatalogAdapter(
    private val pageSize: Int = 12,
) : CatalogAdapter {
    private val seriesById: Map<String, Series>
    private val episodesBySeries: Map<String, List<Episode>>
    private val seriesOrder: List<Series>

    init {
        val built = buildDemoCatalog()
        seriesOrder = built.first
        seriesById = built.first.associateBy { it.id }
        episodesBySeries = built.second
    }

    override suspend fun section(section: CatalogSection, page: Int): CatalogPage<Series> {
        val items = when (section) {
            CatalogSection.DESTACADOS -> seriesOrder.filter { it.recommended }
            CatalogSection.NOVEDADES -> seriesOrder
            CatalogSection.MAS_VISTOS -> seriesOrder.sortedByDescending { it.rating ?: 0.0 }
            CatalogSection.TENDENCIAS -> seriesOrder.filter { it.isNewThisWeek }
            CatalogSection.CATALOGO -> seriesOrder
            CatalogSection.GENEROS -> seriesOrder.sortedBy { it.genres.firstOrNull() ?: "" }
            CatalogSection.FUENTES -> accessCards().filter { it.browserOnly }
            CatalogSection.ACCESO -> accessCards()
        }
        return paginate(items.distinctBy { it.id }, page)
    }

    override suspend fun search(query: String, page: Int): CatalogPage<Series> {
        val needle = TextNormalizer.normalize(query)
        if (needle.isBlank()) return CatalogPage.empty(page)
        val results = seriesOrder
            .filter { TextNormalizer.matches(it.searchableText(), needle) }
            .sortedWith(compareBy({ it.title }, { it.id }))
        return paginate(results, page)
    }

    override suspend fun load(idOrUrl: String): Series? {
        val raw = idOrUrl.trim()
        if (raw.isBlank()) return null
        if (UrlUtils.isSeriesPage(raw)) {
            val slug = UrlUtils.extractSeriesSlug(raw)
            seriesById[slug]?.let { return it }
            return manualHdramaImport(raw)
        }
        val id = UrlUtils.demoSeriesId(raw) ?: raw.takeIf { !it.contains("://") }
        return id?.let { seriesById[it] } ?: accessCards().firstOrNull { it.id == id }
    }

    override suspend fun episodes(series: Series): List<Episode> {
        if (series.browserOnly) return listOf(browserEpisode(series))
        return episodesBySeries[series.id].orEmpty()
    }

    override suspend fun resolve(episode: Episode): List<MediaSource> = episode.mediaSources

    override suspend fun related(series: Series): List<Series> =
        series.relatedIds.mapNotNull { seriesById[it] }

    fun allSeries(): List<Series> = seriesOrder + accessCards()

    fun byId(id: String): Series? = seriesById[id] ?: accessCards().firstOrNull { it.id == id }

    private fun paginate(items: List<Series>, page: Int): CatalogPage<Series> {
        val safePage = page.coerceAtLeast(1)
        val from = (safePage - 1) * pageSize
        if (from >= items.size) return CatalogPage(emptyList(), safePage, false)
        val slice = items.drop(from).take(pageSize)
        return CatalogPage(slice, safePage, from + slice.size < items.size)
    }

    private fun accessCards(): List<Series> = AccessCards.all()

    private fun manualHdramaImport(url: String): Series? = ManualHdramaImport.fromUrl(url)

    private fun browserEpisode(series: Series): Episode = ManualHdramaImport.browserEpisode(series)

    private fun buildDemoCatalog(): Pair<List<Series>, Map<String, List<Episode>>> {
        val seeds = listOf(
            Seed("Corazón de Neón", "Neon Heart", listOf("Romance", "Drama"), 8.7, true, true),
            Seed("El Señor del Norte", "Lord of the North", listOf("Fantasía", "Drama"), 8.2, true, true),
            Seed("La Heredera de Papel", null, listOf("Drama", "Misterio"), 7.9, true, false),
            Seed("Academia de Sombras", "Shadow Academy", listOf("Fantasía", "Romance"), 8.5, false, true),
            Seed("Cuatro Reyes", null, listOf("Fantasía", "Aventura"), 7.6, true, false),
            Seed("Sobrevivir al Amanecer", null, listOf("Acción", "Drama"), 8.0, false, true),
            Seed("La Capitana Estelar", "Star Captain", listOf("Ciencia ficción"), 7.4, true, false),
            Seed("El Jardín de Medianoche", null, listOf("Misterio", "Romance"), 8.8, true, true),
            Seed("Cartas desde el Sur", null, listOf("Drama"), 7.1, false, false),
            Seed("La Última Función", null, listOf("Comedia", "Drama"), 6.9, false, true),
            Seed("Risas en el Ático", null, listOf("Comedia"), 7.7, true, false),
            Seed("El Eco del Puerto", null, listOf("Misterio"), 8.1, false, true),
            Seed("Días de Verano", null, listOf("Romance"), 7.3, true, false),
            Seed("La Casa de Cristal", null, listOf("Drama", "Suspenso"), 8.4, false, true),
            Seed("Un Café en Madrid", null, listOf("Romance", "Comedia"), 7.8, true, false),
            Seed("Noche de Estreno", null, listOf("Drama"), 7.0, false, false),
            Seed("Prueba de reproducción", null, listOf("Demo"), 10.0, false, false),
        )

        val series = seeds.mapIndexed { index, seed ->
            val id = TextNormalizer.slugify(seed.title)
            Series(
                id = id,
                slug = id,
                title = seed.title,
                originalTitle = seed.originalTitle,
                synopsis = "Serie de demostracion ${index + 1}. Contenido ficticio para probar " +
                    "el catalogo, la busqueda y los estados de reproduccion de HDramaCloudStream.",
                genres = seed.genres,
                rating = seed.rating,
                language = "es",
                source = "HDrama (demo local)",
                posterUrl = if (index % 5 == 4) null else
                    "https://placehold.co/300x450/1f2937/ffffff/png?text=" +
                        (index + 1).toString().padStart(2, '0'),
                year = 2026,
                relatedIds = listOf(
                    TextNormalizer.slugify(seeds[(index + 1) % seeds.size].title),
                    TextNormalizer.slugify(seeds[(index + 3) % seeds.size].title),
                ),
                demo = true,
                isNewThisWeek = seed.newThisWeek,
                recommended = seed.recommended,
            )
        }

        val episodes = series.mapIndexed { index, item ->
            val count = if (index < 8) 3 else 2
            val list = (1..count).map { number ->
                val media = when {
                    index == 2 && number == 3 -> listOf(
                        MediaSource.BrowserOnly(
                            UrlUtils.HDRA_BASE_URL,
                            "Ejemplo de contenido que solo puede verse en el navegador.",
                        )
                    )
                    index == 3 && number == 3 -> listOf(
                        MediaSource.Unauthorized(
                            "Ejemplo de fuente que requiere autorizacion de HDrama."
                        )
                    )
                    number == 1 -> listOf(
                        MediaSource.Mp4(
                            url = "https://media.w3.org/2010/05/sintel/trailer.mp4",
                            quality = 1080,
                        )
                    )
                    number == 2 -> listOf(
                        MediaSource.Hls(
                            url = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8",
                            quality = 720,
                        )
                    )
                    else -> listOf(
                        MediaSource.Unavailable("Fuente de demostracion no disponible.")
                    )
                }
                Episode(
                    id = EpisodeId.encode(item.id, number),
                    seriesId = item.id,
                    number = number,
                    title = "Episodio $number",
                    durationSeconds = 1_500 + number * 60,
                    mediaSources = media,
                    subtitles = emptyList(),
                )
            }
            item.id to list.sortedBy { it.number }
        }.toMap()

        val withCounts = series.map { item ->
            item.copy(episodeCount = episodes[item.id]?.size)
        }
        return withCounts to episodes
    }

    private data class Seed(
        val title: String,
        val originalTitle: String?,
        val genres: List<String>,
        val rating: Double,
        val newThisWeek: Boolean,
        val recommended: Boolean,
    )
}
