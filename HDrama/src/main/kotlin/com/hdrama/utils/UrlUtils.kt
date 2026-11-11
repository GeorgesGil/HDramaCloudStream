package com.hdrama.utils

import java.net.URI

/**
 * Utilidades de URL con una allowlist estricta para HDrama.
 *
 * Cualquier URL que no sea https en `es.hdrama.net` (o `www.es.hdrama.net`)
 * se rechaza antes de llegar a la capa de red.
 */
object UrlUtils {
    const val HDRA_HOST = "es.hdrama.net"
    const val HDRA_BASE_URL = "https://es.hdrama.net/"
    const val DEMO_SCHEME_PREFIX = "hdrama-demo://series/"
    const val BROWSER_SCHEME = "hdrama-browser://open"

    private val allowedSchemes = setOf("https", "http")

    fun isHttpUrl(value: String?): Boolean {
        val raw = value?.trim().orEmpty()
        if (raw.isEmpty()) return false
        return runCatching {
            val uri = URI(raw)
            uri.scheme?.lowercase() in allowedSchemes && !uri.host.isNullOrBlank()
        }.getOrDefault(false)
    }

    /** Normaliza la URL base: https, sin barra final (salvo raiz). */
    fun normalizeBaseUrl(value: String): String {
        val raw = value.trim()
        if (raw.isEmpty()) return HDRA_BASE_URL
        val withScheme = when {
            raw.startsWith("https://", ignoreCase = true) -> raw
            raw.startsWith("http://", ignoreCase = true) -> raw.replaceFirst("http://", "https://")
            else -> "https://$raw"
        }
        val uri = runCatching { URI(withScheme) }.getOrNull() ?: return HDRA_BASE_URL
        val host = uri.host?.lowercase()?.removePrefix("www.") ?: return HDRA_BASE_URL
        if (host != HDRA_HOST) return HDRA_BASE_URL
        val path = (uri.path ?: "").trimEnd('/')
        return if (path.isBlank()) "https://$HDRA_HOST/" else "https://$HDRA_HOST$path"
    }

    /** Devuelve la URL canonica de HDrama o null si no pertenece al dominio. */
    fun normalizeHdramaUrl(value: String?): String? {
        val raw = value?.trim().orEmpty()
        if (raw.isEmpty()) return null
        val withScheme = when {
            raw.startsWith("https://", ignoreCase = true) -> raw
            raw.startsWith("http://", ignoreCase = true) -> raw.replaceFirst("http://", "https://")
            raw.startsWith("//") -> "https:$raw"
            else -> "https://$raw"
        }
        val uri = runCatching { URI(withScheme) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase() ?: return null
        if (scheme != "https") return null
        val host = uri.host?.lowercase()?.removePrefix("www.") ?: return null
        if (host != HDRA_HOST) return null
        val path = (uri.path ?: "").trimEnd('/')
        if (path.isBlank()) return "https://$HDRA_HOST/"
        val query = uri.query?.takeIf { it.isNotBlank() }?.let { "?$it" }.orEmpty()
        return "https://$HDRA_HOST$path$query"
    }

    fun isAllowedHdramaUrl(value: String?): Boolean = normalizeHdramaUrl(value) != null

    /** Extrae el slug de `/series/<slug>`; null si la ruta no es de serie. */
    fun extractSeriesSlug(value: String?): String? {
        val canonical = normalizeHdramaUrl(value) ?: return null
        val path = runCatching { URI(canonical).path }.getOrNull().orEmpty()
        val segments = path.split('/').filter { it.isNotBlank() }
        val seriesIndex = segments.indexOf("series")
        if (seriesIndex < 0) return null
        return segments.getOrNull(seriesIndex + 1)?.takeIf { it.isNotBlank() }
    }

    fun seriesPageUrl(slug: String): String = "https://$HDRA_HOST/series/$slug"

    fun isSeriesPage(value: String?): Boolean = extractSeriesSlug(value) != null

    fun demoSeriesUrl(id: String): String = "$DEMO_SCHEME_PREFIX$id"

    fun isDemoSeriesUrl(value: String?): Boolean = value?.trim()?.startsWith(DEMO_SCHEME_PREFIX) == true

    fun demoSeriesId(value: String?): String? =
        value?.trim()?.takeIf { it.startsWith(DEMO_SCHEME_PREFIX) }?.removePrefix(DEMO_SCHEME_PREFIX)?.takeIf { it.isNotBlank() }

    /**
     * CloudStream aplica `fixUrl` al abrir una ficha y puede anteponer
     * `mainUrl` a esquemas internos. Recupera el esquema demo original a
     * partir del marcador, sin depender del prefijo anadido.
     */
    fun recoverDemoSeriesUrl(value: String?): String? {
        val raw = value?.trim().orEmpty()
        val index = raw.indexOf(DEMO_SCHEME_PREFIX)
        if (index < 0) return null
        val recovered = raw.substring(index)
        return recovered.takeIf { demoSeriesId(it) != null }
    }

    /** Elimina query, fragmento y credenciales para logs. */
    fun redact(value: String?): String {
        val raw = value?.trim().orEmpty()
        if (raw.isEmpty()) return ""
        return runCatching {
            val uri = URI(raw)
            val host = uri.host?.removePrefix("www.") ?: return raw.take(120)
            val path = uri.path.orEmpty()
            if (path.isBlank()) "https://$host/" else "https://$host$path"
        }.getOrElse { raw.take(120) }
    }
}
