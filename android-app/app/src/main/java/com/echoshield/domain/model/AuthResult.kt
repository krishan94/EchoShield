package com.echoshield.domain.model

/**
 * Result wrapper for EchoShield authentication and security transactions.
 */
sealed class AuthResult<out T> {
    data class Success<out T>(val data: T) : AuthResult<T>()
    data class Error(
        val message: String,
        val errorCode: String? = null,
        val cause: Throwable? = null
    ) : AuthResult<Nothing>()

    val isSuccess: Boolean get() = this is Success
    val isError: Boolean get() = this is Error

    fun getOrNull(): T? = when (this) {
        is Success -> data
        is Error -> null
    }

    fun errorOrNull(): Error? = when (this) {
        is Success -> null
        is Error -> this
    }
}
