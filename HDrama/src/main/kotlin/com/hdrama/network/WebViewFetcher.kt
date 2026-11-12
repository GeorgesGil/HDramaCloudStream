package com.hdrama.network

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.WebView
import android.webkit.WebViewClient
import com.hdrama.utils.UrlUtils
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import org.json.JSONTokener
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Fetcher basado en un WebView real de Android.
 *
 * El challenge de Cloudflare de HDrama valida mas que las cookies: una peticion
 * OkHttp con `cf_clearance` sigue devolviendo 403 (huella TLS/UA distinta).
 * Cargar la pagina en el WebView del sistema resuelve el challenge de forma
 * nativa y devuelve el HTML ya renderizado.
 *
 * Para las rutas `/api/` (que el WAF solo acepta como peticion XHR de la propia
 * pagina) se carga primero el origen y despues se ejecuta un `fetch()`
 * same-origin dentro del WebView, con sondeo del resultado.
 *
 * Las peticiones se serializan con un [Mutex] y se ejecutan en el hilo
 * principal. Cada peticion crea un WebView nuevo; las cookies viven en el
 * `CookieManager` compartido, asi que el challenge solo se resuelve una vez.
 */
class WebViewFetcher(
    private val context: Context,
    private val timeoutMs: Long = DEFAULT_TIMEOUT_MS,
) : WebFetcher {

    private val mutex = Mutex()
    private val mainHandler = Handler(Looper.getMainLooper())

    override suspend fun fetch(url: String, headers: Map<String, String>): String {
        val target = HdramaFetchAllowlist.resolve(url)
        return mutex.withLock { loadInWebView(target, headers) }
    }

    private suspend fun loadInWebView(
        target: String,
        headers: Map<String, String>,
    ): String = suspendCancellableCoroutine { continuation ->
        val finished = AtomicBoolean(false)
        val isApi = target.contains("/api/", ignoreCase = true)
        var currentWebView: WebView? = null
        var timeoutTask: Runnable? = null
        var polling = false

        fun cleanup() {
            mainHandler.post {
                timeoutTask?.let { mainHandler.removeCallbacks(it) }
                currentWebView?.let { view ->
                    runCatching {
                        view.stopLoading()
                        view.destroy()
                    }
                }
                currentWebView = null
            }
        }

        fun finish(result: String?, error: Throwable?) {
            if (!finished.compareAndSet(false, true)) return
            cleanup()
            if (error != null) {
                continuation.resumeWithException(error)
            } else {
                continuation.resume(result.orEmpty())
            }
        }

        continuation.invokeOnCancellation {
            if (finished.compareAndSet(false, true)) cleanup()
        }

        mainHandler.post {
            try {
                @SuppressLint("SetJavaScriptEnabled")
                val webView = WebView(context.applicationContext).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                }
                currentWebView = webView

                fun pollApiResponse() {
                    if (finished.get()) return
                    webView.evaluateJavascript(apiPollScript(target)) { raw ->
                        if (finished.get()) return@evaluateJavascript
                        val payload = decodeApiPoll(raw)
                        when {
                            payload == null -> {
                                // Aun no hay respuesta; seguir sondeando.
                                mainHandler.postDelayed({ pollApiResponse() }, API_POLL_MS)
                            }

                            payload.startsWith(API_ERROR_PREFIX) -> finish(
                                null,
                                WebFetchException(
                                    WebFetchException.NO_HTTP_CODE,
                                    "HDrama rechazo la peticion a su API.",
                                ),
                            )

                            else -> finish(payload, null)
                        }
                    }
                }

                webView.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String?) {
                        if (finished.get()) return
                        if (isApi && polling) return
                        view.evaluateJavascript(EXTRACT_SCRIPT) { rawResult ->
                            if (finished.get() || rawResult.isNullOrBlank()) {
                                return@evaluateJavascript
                            }
                            val page = decodePage(rawResult) ?: return@evaluateJavascript
                            if (page.isChallenge) {
                                // El challenge se esta resolviendo; llegara otra
                                // navegacion con la pagina definitiva.
                                return@evaluateJavascript
                            }
                            if (isApi) {
                                polling = true
                                pollApiResponse()
                            } else {
                                if (page.html.isBlank()) {
                                    finish(
                                        null,
                                        WebFetchException(
                                            WebFetchException.NO_HTTP_CODE,
                                            "HDrama devolvio una pagina vacia.",
                                        ),
                                    )
                                } else {
                                    finish(page.html, null)
                                }
                            }
                        }
                    }
                }

                // La API solo se puede pedir desde una pagina del propio sitio.
                val loadUrl = if (isApi) UrlUtils.HDRA_BASE_URL else target
                webView.loadUrl(loadUrl, if (isApi) emptyMap() else headers)
            } catch (error: Throwable) {
                Log.w(TAG, "No se pudo crear el WebView: ${error::class.simpleName}")
                finish(
                    null,
                    WebFetchException(
                        WebFetchException.NO_HTTP_CODE,
                        "No se pudo abrir el WebView para HDrama: " +
                            (error.message ?: "error desconocido"),
                    ),
                )
            }
        }

        timeoutTask = Runnable {
            Log.w(TAG, "Timeout de ${timeoutMs / 1000}s en ${UrlUtils.redact(target)}")
            finish(
                null,
                WebFetchException(
                    WebFetchException.NO_HTTP_CODE,
                    "HDrama no respondio a tiempo (challenge de Cloudflare sin resolver).",
                ),
            )
        }
        mainHandler.postDelayed(timeoutTask, timeoutMs)
    }

    private data class PageContent(
        val title: String,
        val body: String,
        val html: String,
    ) {
        val isChallenge: Boolean
            get() {
                val normalizedTitle = title.lowercase()
                if (normalizedTitle.contains("just a moment") ||
                    normalizedTitle.contains("un momento") ||
                    normalizedTitle.contains("attention required")
                ) {
                    return true
                }
                // La pagina del challenge es pequena y contiene el script
                // orquestador de Cloudflare; la pagina real es mucho mayor.
                return html.length < 20_000 && html.contains("cdn-cgi/challenge-platform")
            }
    }

    private fun decodePage(rawResult: String?): PageContent? {
        val raw = rawResult?.trim().orEmpty()
        if (raw.isEmpty() || raw == "null") return null
        val unquoted = runCatching {
            JSONTokener(raw).nextValue() as? String
        }.getOrNull() ?: raw
        return runCatching {
            val json = JSONObject(unquoted)
            PageContent(
                title = json.optString("title"),
                body = json.optString("body"),
                html = json.optString("html"),
            )
        }.getOrNull()
    }

    /** Devuelve el texto de la API o null si aun no hay respuesta. */
    private fun decodeApiPoll(rawResult: String?): String? {
        val raw = rawResult?.trim().orEmpty()
        if (raw.isEmpty() || raw == "null") return null
        val unquoted = runCatching {
            JSONTokener(raw).nextValue() as? String
        }.getOrNull() ?: return null
        return runCatching {
            val json = JSONObject(unquoted)
            if (json.optBoolean("done")) json.optString("value") else null
        }.getOrNull()
    }

    private fun apiPollScript(target: String): String {
        val urlLiteral = JSONObject.quote(target)
        return """
            (function () {
                if (window.__hdramaApiDone) {
                    return JSON.stringify({ done: true, value: window.__hdramaApiValue });
                }
                if (!window.__hdramaApiStarted) {
                    window.__hdramaApiStarted = true;
                    fetch($urlLiteral, {
                        headers: { "accept": "application/json" },
                        credentials: "same-origin"
                    }).then(function (r) { return r.text(); })
                      .then(function (t) {
                          window.__hdramaApiValue = t;
                          window.__hdramaApiDone = true;
                      })
                      .catch(function (e) {
                          window.__hdramaApiValue = "HDREAM_ERROR:" + e;
                          window.__hdramaApiDone = true;
                      });
                }
                return JSON.stringify({ done: false, value: null });
            })()
        """
    }

    private companion object {
        const val TAG = "HDrama"
        const val DEFAULT_TIMEOUT_MS = 60_000L
        const val API_POLL_MS = 250L
        const val API_ERROR_PREFIX = "HDREAM_ERROR:"

        const val EXTRACT_SCRIPT = """
            (function () {
                var title = document.title || "";
                var body = document.body ? (document.body.innerText || document.body.textContent || "") : "";
                var html = document.documentElement ? document.documentElement.outerHTML : "";
                return JSON.stringify({ title: title, body: body, html: html });
            })()
        """
    }
}
