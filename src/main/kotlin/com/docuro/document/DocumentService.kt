package com.docuro.document

import com.docuro.domain.Document
import com.docuro.extraction.DocumentContent
import com.docuro.extraction.DocumentExtractionPort
import com.docuro.repository.DocumentRepository
import com.docuro.security.EncryptionService
import com.fasterxml.jackson.databind.ObjectMapper
import io.micronaut.objectstorage.aws.AwsS3Operations
import io.micronaut.objectstorage.request.UploadRequest
import jakarta.inject.Singleton
import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.rendering.PDFRenderer
import org.apache.poi.xwpf.usermodel.XWPFDocument
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.UUID
import javax.imageio.ImageIO

// ── Sensitive fields encrypted per-value in JSONB ────────────────────────────
private val SENSITIVE_FIELDS = setOf("cnp", "iban", "pret", "valoare")

@Singleton
class DocumentService(
    private val documentRepository: DocumentRepository,
    private val extractionPort: DocumentExtractionPort,
    private val encryptionService: EncryptionService,
    private val objectStorage: AwsS3Operations,
    private val objectMapper: ObjectMapper,
) {

    fun upload(
        fileBytes: ByteArray,
        filename: String,
        mimeType: String,
        userId: UUID,
        dek: ByteArray,
    ): Document {
        // 1. Encrypt the original file and store in MinIO
        val (encryptedFile, fileNonce) = encryptionService.encrypt(fileBytes, dek)
        val storageKey = "users/$userId/documents/${UUID.randomUUID()}"
        objectStorage.upload(UploadRequest.fromBytes(encryptedFile, storageKey))

        // 2. Convert to LLM-readable format
        val content = toDocumentContent(fileBytes, mimeType)

        // 3. Persist as 'processing' before extraction (synchronous for Milestone 1)
        var doc = documentRepository.save(
            Document(
                userId = userId,
                status = "processing",
                originalFilename = filename,
                storageKey = storageKey,
                mimeType = mimeType,
                fileNonce = fileNonce,
            )
        )

        // 4. Extract fields via Gemini
        runCatching {
            val result = extractionPort.extract(content)
            val encryptedFields = encryptFields(result.fields, dek)
            doc = documentRepository.update(
                doc.copy(
                    type = result.documentType,
                    status = "ready",
                    extractionConfidence = result.confidence,
                    fields = objectMapper.writeValueAsString(encryptedFields),
                )
            )
        }.onFailure {
            documentRepository.update(doc.copy(status = "failed"))
        }

        return doc
    }

    fun getDocument(documentId: UUID, userId: UUID, dek: ByteArray): DocumentResponse? {
        val doc = documentRepository.findByIdAndUserId(documentId, userId) ?: return null
        return doc.toResponse(dek)
    }

    fun listDocuments(userId: UUID, dek: ByteArray): List<DocumentResponse> =
        documentRepository.findByUserId(userId).map { it.toResponse(dek) }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun toDocumentContent(bytes: ByteArray, mimeType: String): DocumentContent = when {
        mimeType.startsWith("image/") ->
            DocumentContent(base64Image = Base64.getEncoder().encodeToString(bytes), mimeType = mimeType)

        mimeType == "application/pdf" -> {
            val image = Loader.loadPDF(bytes).use { pdf ->
                PDFRenderer(pdf).renderImageWithDPI(0, 200f)
            }
            val out = ByteArrayOutputStream()
            ImageIO.write(image as BufferedImage, "jpeg", out)
            DocumentContent(base64Image = Base64.getEncoder().encodeToString(out.toByteArray()), mimeType = "image/jpeg")
        }

        mimeType == "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> {
            val text = XWPFDocument(bytes.inputStream()).use { doc ->
                doc.paragraphs.joinToString("\n") { it.text }
            }
            DocumentContent(text = text, mimeType = mimeType)
        }

        mimeType.startsWith("text/") ->
            DocumentContent(text = bytes.toString(Charsets.UTF_8), mimeType = mimeType)

        else -> error("Unsupported MIME type: $mimeType")
    }

    /** Encrypt sensitive field values; leave others as-is. */
    private fun encryptFields(fields: Map<String, Any?>, dek: ByteArray): Map<String, Any?> =
        fields.mapValues { (key, value) ->
            if (key in SENSITIVE_FIELDS && value != null) {
                val plaintext = value.toString().toByteArray(Charsets.UTF_8)
                val (ciphertext, nonce) = encryptionService.encrypt(plaintext, dek)
                mapOf(
                    "ciphertext" to Base64.getEncoder().encodeToString(ciphertext),
                    "nonce" to Base64.getEncoder().encodeToString(nonce),
                )
            } else {
                value
            }
        }

    /** Decrypt sensitive field values for API responses. */
    private fun decryptFields(fieldsJson: String?, dek: ByteArray): Map<String, Any?> {
        if (fieldsJson == null) return emptyMap()
        @Suppress("UNCHECKED_CAST")
        val raw = objectMapper.readValue(fieldsJson, Map::class.java) as Map<String, Any?>
        return raw.mapValues { (key, value) ->
            if (key in SENSITIVE_FIELDS && value is Map<*, *>) {
                val ciphertext = Base64.getDecoder().decode(value["ciphertext"] as String)
                val nonce = Base64.getDecoder().decode(value["nonce"] as String)
                encryptionService.decrypt(ciphertext, dek, nonce).toString(Charsets.UTF_8)
            } else {
                value
            }
        }
    }

    private fun Document.toResponse(dek: ByteArray) = DocumentResponse(
        id = id.toString(),
        type = type,
        status = status,
        confidence = extractionConfidence,
        fields = decryptFields(fields, dek),
        createdAt = createdAt.toString(),
    )
}
