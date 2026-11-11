package com.hdrama.utils

/** Mensajes de diagnostico sin datos sensibles. */
object Diagnostics {
    private val sensitive = Regex(
        "(?i)(token|api[_-]?key|authorization|cookie|session|password|passwd)=([^&\\s]+)"
    )

    fun sanitize(message: String?): String {
        if (message.isNullOrBlank()) return "sin detalle"
        return sensitive.replace(message, "$1=***").take(300)
    }

    fun describe(error: Throwable): String {
        val name = error::class.simpleName ?: "Error"
        val message = sanitize(error.message)
        return "$name: $message"
    }
}
