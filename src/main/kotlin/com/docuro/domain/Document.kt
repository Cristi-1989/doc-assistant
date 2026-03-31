package com.docuro.domain

import io.micronaut.data.annotation.AutoPopulated
import io.micronaut.data.annotation.DateCreated
import io.micronaut.data.annotation.DateUpdated
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.TypeDef
import io.micronaut.data.model.DataType
import java.time.Instant
import java.util.UUID

@MappedEntity("documents")
data class Document(
    @field:Id
    @field:AutoPopulated
    val id: UUID = UUID.randomUUID(),
    val userId: UUID,
    val type: String? = null,          // 'buletin', 'permis_conducere', etc.
    val status: String,                // 'processing', 'ready', 'review_needed', 'failed'
    val originalFilename: String? = null,
    val storageKey: String? = null,    // MinIO/S3 key for the encrypted file
    val mimeType: String? = null,
    val fileNonce: ByteArray? = null,  // AES-GCM nonce for the encrypted file
    val extractionConfidence: Double? = null,
    @field:TypeDef(type = DataType.JSON)
    val fields: String? = null,        // JSONB — sensitive values individually encrypted
    @field:DateCreated
    val createdAt: Instant = Instant.now(),
    @field:DateUpdated
    val updatedAt: Instant = Instant.now(),
)
