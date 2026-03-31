package com.docuro.auth

import com.docuro.repository.UserRepository
import com.docuro.domain.User
import com.docuro.security.EncryptionService
import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Post
import io.micronaut.security.annotation.Secured
import io.micronaut.security.rules.SecurityRule
import io.micronaut.serde.annotation.Serdeable

@Serdeable
data class RegisterRequest(val email: String, val password: String)

@Serdeable
data class RegisterResponse(val userId: String)

@Controller("/api/auth")
@Secured(SecurityRule.IS_ANONYMOUS)
class AuthController(
    private val userRepository: UserRepository,
    private val encryptionService: EncryptionService,
) {

    @Post("/register")
    fun register(@Body request: RegisterRequest): HttpResponse<RegisterResponse> {
        if (userRepository.existsByEmail(request.email)) {
            return HttpResponse.unprocessableEntity()
        }

        val passwordHash = encryptionService.hashPassword(request.password)
        val kekSalt = encryptionService.generateSalt()
        val dek = encryptionService.generateDek()
        val kek = encryptionService.deriveKek(request.password, kekSalt)
        val (encryptedDek, dekNonce) = encryptionService.wrapDek(dek, kek)

        val user = userRepository.save(
            User(
                email = request.email,
                passwordHash = passwordHash,
                kekSalt = kekSalt,
                encryptedDek = encryptedDek,
                dekNonce = dekNonce,
            )
        )

        return HttpResponse.created(RegisterResponse(userId = user.id.toString()))
    }
    // POST /api/auth/login is handled automatically by Micronaut Security's LoginController
    // via UserAuthenticationProvider — no explicit handler needed here.
}
