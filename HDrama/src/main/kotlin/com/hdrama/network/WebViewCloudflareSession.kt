package com.hdrama.network

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.hdrama.utils.UrlUtils
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

/**
 * Resuelve el Managed Challenge de Cloudflare en un WebView del sistema.
 *
 * Estrategia (mucho mas ligera que descargar el HTML por WebView):
 *
 * 1. Se crea **un** WebView y se carga `https://es.hdrama.net/`.
 * 2. Se sondea `CookieManager` hasta que aparece `cf_clearance` (normalmente en
 *    pocos segundos); no se espera a que la pagina Next.js termine de cargar.
 * 3. Se destruye el WebView y se guardan para las peticiones OkHttp la cookie y
 *    el User-Agent por defecto del WebView (deben coincidir con los usados al
 *    resolver el challenge).
 *
 * Todo el ciclo de vida del WebView ocurre en el hilo principal; la espera es
 * asincrona, asi que no bloquea la UI de CloudStream.
 */
class WebViewCloudflareSession(
    private val context: Context,
    private val timeoutMs: Long = DEFAULT_TIMEOUT_MS,
) : CloudflareSession {

    private val mutex = Mutex()
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var cachedCookie: String? = null

    @Volatile
    private var cachedUserAgent: String? = null

    @Volatile
    private var lastFailureAt: Long = 0L

    /**
     * Fuerza un challenge nuevo aunque `CookieManager` aun tenga una cookie
     * vieja: se activa cuando el sitio rechaza la sesion con 403/503.
     */
    @Volatile
    private var forceChallenge: Boolean = false

    override suspend fun cookieHeader(): String? {
        cachedCookie?.let { return it }
        val current = readCookieHeader()
        if (current != null) cachedCookie = current
        return current
    }

    override suspend fun userAgent(): String? {
        cachedUserAgent?.let { return it }
        val agent = runCatching { WebSettings.getDefaultUserAgent(context) }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: DEFAULT_USER_AGENT
        cachedUserAgent = agent
        return agent
    }

    override suspend fun ensure(): Boolean = mutex.withLock {
        if (!forceChallenge) {
            val existing = cookieHeader()
            if (existing != null && existing.contains(CF_CLEARANCE_MARKER)) {
                return@withLock true
            }
        }
        forceChallenge = false

        // Tras un fallo se espera un poco antes de abrir otro WebView: evita
        // encadenar challenges cuando la red o el WebView del dispositivo no
        // colaboran.
        val sinceLastFailure = System.currentTimeMillis() - lastFailureAt
        if (lastFailureAt > 0L && sinceLastFailure < FAILURE_BACKOFF_MS) {
            return@withLock false
        }

        val solved = runCatching { solveChallenge() }.getOrDefault(false)
        if (solved) {
            lastFailureAt = 0L
            cachedCookie = readCookieHeader()
            cachedUserAgent = runCatching { WebSettings.getDefaultUserAgent(context) }
                .getOrNull()
                ?.takeIf { it.isNotBlank() }
                ?: DEFAULT_USER_AGENT
            cachedCookie?.contains(CF_CLEARANCE_MARKER) == true
        } else {
            lastFailureAt = System.currentTimeMillis()
            cachedCookie = null
            Log.w(TAG, "Cloudflare no quedo resuelto; se usara el respaldo de sitemaps")
            false
        }
    }

    override fun invalidate() {
        cachedCookie = null
        // La cookie de CookieManager puede seguir ahi pero ya no es valida: el
        // siguiente ensure() vuelve a pasar por el WebView para renovarla. No se
        // resetea lastFailureAt: si el challenge acaba de fallar, no se encadena
        // otro WebView que volveria a fallar.
        forceChallenge = true
    }

    // ------------------------------------------------------------------
    // Challenge en WebView
    // ------------------------------------------------------------------

    private suspend fun solveChallenge(): Boolean {
        val webView = createWebView() ?: return false
        return try {
            suspendCancellableCoroutine { continuation ->
                val finished = AtomicBoolean(false)
                var pollTask: Runnable? = null

                fun finish(result: Boolean) {
                    if (!finished.compareAndSet(false, true)) return
                    pollTask?.let { mainHandler.removeCallbacks(it) }
                    continuation.resume(result)
                }

                continuation.invokeOnCancellation {
                    if (finished.compareAndSet(false, true)) {
                        pollTask?.let { mainHandler.removeCallbacks(it) }
                    }
                }

                pollTask = object : Runnable {
                    override fun run() {
                        if (finished.get()) return
                        val cookie = readCookieHeader()
                        if (cookie != null && cookie.contains(CF_CLEARANCE_MARKER)) {
                            Log.d(TAG, "Challenge resuelto; cookie de sesion actualizada")
                            finish(true)
                            return
                        }
                        mainHandler.postDelayed(this, POLL_INTERVAL_MS)
                    }
                }

                mainHandler.postDelayed(pollTask!!, POLL_INTERVAL_MS)
                mainHandler.postDelayed(
                    {
                        if (!finished.get()) {
                            Log.w(TAG, "Timeout de ${timeoutMs / 1000}s resolviendo Cloudflare")
                        }
                        finish(false)
                    },
                    timeoutMs,
                )

                // loadUrl debe ejecutarse en el hilo principal.
                mainHandler.post { runCatching { webView.loadUrl(UrlUtils.HDRA_BASE_URL) } }
            }
        } finally {
            destroy(webView)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun createWebView(): WebView? = suspendCancellableCoroutine { continuation ->
        mainHandler.post {
            val view = try {
                WebView(context.applicationContext).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    // El UA se deja el del WebView: Cloudflare lo vincula a la
                    // cookie y cambiarlo invalida el challenge.
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) = Unit
                    }
                }
            } catch (error: Throwable) {
                Log.w(TAG, "No se pudo crear el WebView: ${error::class.simpleName}")
                null
            }
            continuation.resume(view)
        }
    }

    private fun destroy(webView: WebView?) {
        if (webView == null) return
        mainHandler.post {
            runCatching {
                webView.stopLoading()
                webView.destroy()
            }
        }
    }

    private fun readCookieHeader(): String? {
        val raw = runCatching {
            CookieManager.getInstance().getCookie(UrlUtils.HDRA_BASE_URL)
        }.getOrNull()
        return raw?.takeIf { it.isNotBlank() }
    }

    private companion object {
        const val TAG = "HDrama"

        /**
         * Holgado a proposito: en emuladores o equipos lentos el primer WebView
         * tarda en arrancar y el challenge de Cloudflare puede necesitar ~45 s;
         * en un telefono normal se resuelve en pocos segundos.
         */
        const val DEFAULT_TIMEOUT_MS = 75_000L
        const val POLL_INTERVAL_MS = 350L
        const val FAILURE_BACKOFF_MS = 15_000L
        const val DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
    }
}
