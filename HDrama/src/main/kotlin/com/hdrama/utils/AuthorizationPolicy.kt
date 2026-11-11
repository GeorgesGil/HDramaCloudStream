package com.hdrama.utils

/** Estado de autorizacion para el acceso automatizado a HDrama. */
enum class AuthorizationStatus {
    GRANTED,
    NOT_GRANTED,
}

class UnauthorizedAccessException(message: String) : Exception(message)

/**
 * Politica de autorizacion del adaptador HDrama.
 *
 * `HDRAMA_AUTHORIZED` debe permanecer en `false` mientras no exista una
 * autorizacion escrita de HDrama. Cambiarlo sin autorizacion convierte al
 * plugin en una integracion no autorizada y esta fuera del alcance del
 * proyecto.
 *
 * Para habilitarlo (solo con autorizacion documentada):
 * 1. Adjuntar la autorizacion en `Docs/`.
 * 2. Cambiar `HDRAMA_AUTHORIZED` a `true`.
 * 3. Verificar el contrato HTML contra fixtures reales autorizados.
 * 4. Ejecutar la suite completa y la revision legal.
 */
object AuthorizationPolicy {
    const val HDRAMA_AUTHORIZED: Boolean = false

    fun status(): AuthorizationStatus =
        if (HDRAMA_AUTHORIZED) AuthorizationStatus.GRANTED else AuthorizationStatus.NOT_GRANTED

    fun isAuthorized(): Boolean = HDRAMA_AUTHORIZED

    fun requireAuthorized(action: String) {
        if (!HDRAMA_AUTHORIZED) {
            throw UnauthorizedAccessException(
                "Accion '$action' bloqueada: la integracion directa con HDrama requiere autorizacion escrita."
            )
        }
    }
}
