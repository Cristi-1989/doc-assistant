package com.docuro.domain

import io.micronaut.data.annotation.AutoPopulated
import io.micronaut.data.annotation.DateCreated
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import java.time.Instant
import java.util.UUID

@MappedEntity("users")
data class User(
    @field:Id
    @field:AutoPopulated
    val id: UUID = UUID.randomUUID(),
    val email: String,
    val passwordHash: String,
    val kekSalt: ByteArray,
    val encryptedDek: ByteArray,
    val dekNonce: ByteArray,
    @field:DateCreated
    val createdAt: Instant = Instant.now(),
)
