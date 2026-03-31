package com.docuro.document

import io.micronaut.http.HttpResponse
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Part
import io.micronaut.http.annotation.PathVariable
import io.micronaut.http.annotation.Post
import io.micronaut.http.multipart.CompletedFileUpload
import io.micronaut.security.annotation.Secured
import io.micronaut.security.authentication.Authentication
import io.micronaut.security.rules.SecurityRule
import io.micronaut.serde.annotation.Serdeable
import java.util.Base64
import java.util.UUID

@Serdeable
data class UploadResponse(val documentId: String, val status: String)

@Serdeable
data class DocumentResponse(
    val id: String,
    val type: String?,
    val status: String,
    val confidence: Double?,
    val fields: Map<String, Any?>,
    val createdAt: String,
)

@Controller("/api/documents")
@Secured(SecurityRule.IS_AUTHENTICATED)
class DocumentController(private val documentService: DocumentService) {

    @Post("/upload", consumes = [MediaType.MULTIPART_FORM_DATA])
    fun upload(
        @Part file: CompletedFileUpload,
        authentication: Authentication,
    ): HttpResponse<UploadResponse> {
        val userId = UUID.fromString(authentication.attributes["userId"] as String)
        val dek = Base64.getDecoder().decode(authentication.attributes["dek"] as String)
        val doc = documentService.upload(
            fileBytes = file.bytes,
            filename = file.filename,
            mimeType = file.contentType.orElse(MediaType.APPLICATION_OCTET_STREAM_TYPE).toString(),
            userId = userId,
            dek = dek,
        )
        return HttpResponse.created(UploadResponse(doc.id.toString(), doc.status))
    }

    @Get("/{documentId}")
    fun getDocument(
        @PathVariable documentId: UUID,
        authentication: Authentication,
    ): HttpResponse<DocumentResponse> {
        val userId = UUID.fromString(authentication.attributes["userId"] as String)
        val dek = Base64.getDecoder().decode(authentication.attributes["dek"] as String)
        val doc = documentService.getDocument(documentId, userId, dek)
            ?: return HttpResponse.notFound()
        return HttpResponse.ok(doc)
    }

    @Get
    fun listDocuments(authentication: Authentication): List<DocumentResponse> {
        val userId = UUID.fromString(authentication.attributes["userId"] as String)
        val dek = Base64.getDecoder().decode(authentication.attributes["dek"] as String)
        return documentService.listDocuments(userId, dek)
    }
}
