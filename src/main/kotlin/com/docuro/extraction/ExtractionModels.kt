package com.docuro.extraction

import io.micronaut.serde.annotation.Serdeable

// ── Port models ──────────────────────────────────────────────────────────────

data class DocumentContent(
    val base64Image: String? = null,  // for image / PDF inputs
    val text: String? = null,          // for plain-text / DOCX inputs
    val mimeType: String,
    val documentType: String? = null,  // null = auto-classify; uppercase code e.g. "BULETIN"
)

data class ExtractionResult(
    val documentType: String,          // uppercase code from document_types.code
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
