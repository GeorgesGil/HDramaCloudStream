package com.hdrama.network

/**
 * Sesion de Cloudflare necesaria para hablar con `es.hdrama.net`.
 *
 * El sitio responde **HTTP 403 + Managed Challenge** a cualquier cliente sin
 * cookies validas. La cookie `cf_clearance` solo se obtiene ejecutando el
 * challenge en un WebView real; despues, todas las peticiones (HTML y API) se
 * pueden hacer por OkHttp si se envian:
 *
 * 1. la cookie `cf_clearance` vigente,
 * 2. el **mismo User-Agent del WebView** que resolvio el challenge,
 * 3. un `Referer: https://es.hdrama.net/` (la API `/api/` lo exige).
 *
 * Esta interfaz aísla esa sesion del resto de la capa de red para poder
 * probarla en JVM con dobles.
 */
interface CloudflareSession {

    /** Valor de la cabecera `Cookie` o `null` si aun no hay challenge resuelto. */
    suspend fun cookieHeader(): String?

    /** User-Agent del WebView que resolvio el challenge (debe coincidir). */
    suspend fun userAgent(): String?

    /**
     * Garantiza que hay una sesion valida, resolviendo el challenge en un
     * WebView si hace falta.
     *
     * @return `true` si hay una cookie `cf_clearance` disponible.
     */
    suspend fun ensure(): Boolean

    /** Descarta la sesion en memoria para forzar un challenge nuevo. */
    fun invalidate()
}

/**
 * Sesion vacia para entornos sin WebView (pruebas JVM o fallback sin contexto
 * Android). Nunca resuelve el challenge: la capa de red decides como degradar.
 */
object NoCloudflareSession : CloudflareSession {
    override suspend fun cookieHeader(): String? = null

    override suspend fun userAgent(): String? = null

    override suspend fun ensure(): Boolean = false

    override fun invalidate() = Unit
}

/** La cookie que demuestra que el Managed Challenge quedo resuelto. */
internal const val CF_CLEARANCE_MARKER = "cf_clearance"
