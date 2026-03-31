package com.docuro.security

import at.favre.lib.crypto.bcrypt.BCrypt
import jakarta.inject.Singleton
import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

@Singleton
class EncryptionService {

    private val random = SecureRandom()

    // ── Password (BCrypt) ────────────────────────────────────────────────────

    fun hashPassword(password: String): String =
        BCrypt.withDefaults().hashToString(12, password.toCharArray())

    fun verifyPassword(password: String, hash: String): Boolean =
        BCrypt.verifyer().verify(password.toCharArray(), hash).verified

    // ── KEK derivation (Argon2id) ────────────────────────────────────────────

    fun generateSalt(): ByteArray = ByteArray(32).also { random.nextBytes(it) }

    fun deriveKek(password: String, kekSalt: ByteArray): ByteArray {
        val params = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
            .withSalt(kekSalt)
            .withMemoryAsKB(65536) // 64 MB
            .withIterations(3)
            .withParallelism(4)
            .build()
        val generator = Argon2BytesGenerator()
        generator.init(params)
        val kek = ByteArray(32) // 256-bit
        generator.generateBytes(password.toByteArray(Charsets.UTF_8), kek)
        return kek
    }

    // ── DEK generation and wrapping ──────────────────────────────────────────

    fun generateDek(): ByteArray = ByteArray(32).also { random.nextBytes(it) }

    /** Encrypt DEK with KEK using AES-256-GCM. Returns Pair(ciphertext, nonce). */
    fun wrapDek(dek: ByteArray, kek: ByteArray): Pair<ByteArray, ByteArray> =
        aesGcmEncrypt(dek, kek)

    /** Decrypt DEK with KEK using AES-256-GCM. */
    fun unwrapDek(password: String, kekSalt: ByteArray, encryptedDek: ByteArray, dekNonce: ByteArray): ByteArray {
        val kek = deriveKek(password, kekSalt)
        return aesGcmDecrypt(encryptedDek, kek, dekNonce)
    }

    // ── File / field encryption (AES-256-GCM) ───────────────────────────────

    /** Encrypt arbitrary bytes with a DEK. Returns Pair(ciphertext, nonce). */
    fun encrypt(plaintext: ByteArray, dek: ByteArray): Pair<ByteArray, ByteArray> =
        aesGcmEncrypt(plaintext, dek)

    /** Decrypt arbitrary bytes with a DEK. */
    fun decrypt(ciphertext: ByteArray, dek: ByteArray, nonce: ByteArray): ByteArray =
        aesGcmDecrypt(ciphertext, dek, nonce)

    // ── AES-256-GCM primitives ───────────────────────────────────────────────

    private fun aesGcmEncrypt(plaintext: ByteArray, key: ByteArray): Pair<ByteArray, ByteArray> {
        val nonce = ByteArray(12).also { random.nextBytes(it) } // 96-bit nonce for GCM
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
        return cipher.doFinal(plaintext) to nonce
    }

    private fun aesGcmDecrypt(ciphertext: ByteArray, key: ByteArray, nonce: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
        return cipher.doFinal(ciphertext)
    }
}
