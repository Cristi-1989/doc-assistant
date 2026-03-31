package com.docuro.extraction

import io.micronaut.serde.annotation.Serdeable

// ── Port models ──────────────────────────────────────────────────────────────

enum class DocumentType {
    BULETIN,
    PERMIS_CONDUCERE,
    POLITA_RCA,
    CERTIFICAT_INMATRICULARE,
    CONTRACT_VANZARE_CUMPARARE,
    CERTIFICAT_INREGISTRARE,
    UNKNOWN,
}

data class DocumentContent(
    val base64Image: String? = null,  // for image / PDF inputs
    val text: String? = null,          // for plain-text / DOCX inputs
    val mimeType: String,
    val documentType: DocumentType? = null,  // null = auto-classify
)

data class ExtractionResult(
    val documentType: DocumentType,
    val fields: Map<String, Any?>,
    val confidence: Double,
    val rawResponse: String,
)

// ── Gemini REST API request / response models ────────────────────────────────

@Serdeable
data class GeminiRequest(val contents: List<GeminiContent>, val generationConfig: GeminiGenerationConfig? = null)

@Serdeable
data class GeminiContent(val parts: List<GeminiPart>)

@Serdeable
data class GeminiPart(
    val text: String? = null,
    val inlineData: GeminiInlineData? = null,
)

@Serdeable
data class GeminiInlineData(val mimeType: String, val data: String)

@Serdeable
data class GeminiGenerationConfig(val responseMimeType: String = "application/json")

@Serdeable
data class GeminiResponse(val candidates: List<GeminiCandidate>? = null) {
    fun text(): String? = candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
}

@Serdeable
data class GeminiCandidate(val content: GeminiContent)
