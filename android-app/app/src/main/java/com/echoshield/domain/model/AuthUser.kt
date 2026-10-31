package com.echoshield.domain.model

/**
 * Domain representation of an authenticated EchoShield user session.
 *
 * @property uid The globally unique Firebase user ID.
 * @property email The registered family account email address.
 * @property displayName The family member's display name, if configured.
 * @property isEmailVerified Whether the user has completed cryptographic email verification.
 * @property photoUrl URL for the user's avatar image, if available.
 */
data class AuthUser(
    val uid: String,
    val email: String,
    val displayName: String? = null,
    val isEmailVerified: Boolean = false,
    val photoUrl: String? = null
)
