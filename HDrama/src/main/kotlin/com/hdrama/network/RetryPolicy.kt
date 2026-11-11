package com.hdrama.network

import kotlin.math.min

/**
 * Politica de reintentos limitada y con backoff exponencial.
 *
 * @property maxAttempts numero total de intentos (incluido el primero).
 */
data class RetryPolicy(
    val maxAttempts: Int = 3,
    val initialDelayMs: Long = 250,
    val maxDelayMs: Long = 2_000,
) {
    init {
        require(maxAttempts >= 1) { "maxAttempts debe ser >= 1" }
        require(initialDelayMs >= 0) { "initialDelayMs debe ser >= 0" }
        require(maxDelayMs >= initialDelayMs) { "maxDelayMs debe ser >= initialDelayMs" }
    }

    fun canRetry(attempt: Int): Boolean = attempt < maxAttempts

    /** Espera antes del siguiente intento; `attempt` es 1-based. */
    fun delayMsFor(attempt: Int): Long {
        if (attempt <= 0) return 0
        val multiplier = 1L shl (attempt - 1).coerceAtMost(20)
        return min(initialDelayMs * multiplier, maxDelayMs)
    }
}

/** Resultado de una peticion HTTP controlada. */
sealed interface HttpOutcome {
    data class Success(val body: String) : HttpOutcome

    data class Failure(
        val reason: String,
        val attempts: Int,
    ) : HttpOutcome

    /** URL rechazada antes de abrir conexion (dominio/esquema no permitido). */
    data class Blocked(val reason: String) : HttpOutcome
}
