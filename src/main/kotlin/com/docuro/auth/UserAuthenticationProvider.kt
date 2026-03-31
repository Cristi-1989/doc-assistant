package com.docuro.auth

import com.docuro.repository.UserRepository
import com.docuro.security.EncryptionService
import io.micronaut.http.HttpRequest
import io.micronaut.security.authentication.AuthenticationFailureReason
import io.micronaut.security.authentication.AuthenticationRequest
import io.micronaut.security.authentication.AuthenticationResponse
import io.micronaut.security.authentication.provider.HttpRequestAuthenticationProvider
import jakarta.inject.Singleton
import java.util.Base64

/**
 * Validates credentials, derives the user's DEK, and embeds it as a JWT claim.
 * Because the token is JWE-encrypted (configured in application.yml), the DEK
 * is opaque to anyone without the server encryption secret — protecting data at rest
 * against DB/S3 dumps even if the token is somehow intercepted.
 */
@Singleton
class UserAuthenticationProvider(
    private val userRepository: UserRepository,
    private val encryptionService: EncryptionService,
) : HttpRequestAuthenticationProvider<HttpRequest<*>> {

    override fun authenticate(
        requestContext: HttpRequest<*>?,
        authRequest: AuthenticationRequest<String, String>,
    ): AuthenticationResponse {
        val email = authRequest.identity
        val password = authRequest.secret

        val user = userRepository.findByEmail(email)
            ?: return AuthenticationResponse.failure(AuthenticationFailureReason.USER_NOT_FOUND)

        if (!encryptionService.verifyPassword(password, user.passwordHash)) {
            return AuthenticationResponse.failure(AuthenticationFailureReason.CREDENTIALS_DO_NOT_MATCH)
        }

        val dek = encryptionService.unwrapDek(password, user.kekSalt, user.encryptedDek, user.dekNonce)

        val attributes = mapOf(
            "userId" to user.id.toString(),
            "dek" to Base64.getEncoder().encodeToString(dek),
        )

        return AuthenticationResponse.success(email, emptyList(), attributes)
    }
}
