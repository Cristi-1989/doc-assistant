package com.docuro.extraction

import com.fasterxml.jackson.databind.ObjectMapper
import io.micronaut.context.annotation.Value
import io.micronaut.http.HttpRequest
import io.micronaut.http.MediaType
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import jakarta.inject.Singleton

@Singleton
class GeminiExtractionAdapter(
    @Client("\${docuro.gemini.endpoint}") private val httpClient: HttpClient,
    @Value("\${docuro.gemini.api-key}") private val apiKey: String,
    @Value("\${docuro.gemini.model}") private val model: String,
    private val objectMapper: ObjectMapper,
) : DocumentExtractionPort {

    private val promptLoader = PromptLoader()

    override fun extract(content: DocumentContent): ExtractionResult {
        val documentType = content.documentType ?: classify(content)
        val prompt = promptLoader.load(documentType)
        val request = buildRequest(content, prompt)
        val response = callGemini(request)
        return parseResponse(response, documentType)
    }

    private fun classify(content: DocumentContent): DocumentType {
        val classifyPrompt = promptLoader.load(null) // classify.txt
        val request = buildRequest(content, classifyPrompt)
        val response = callGemini(request)
        val text = response.text() ?: return DocumentType.UNKNOWN
        return runCatching {
            DocumentType.valueOf(objectMapper.readTree(text).get("type").asText().uppercase())
        }.getOrDefault(DocumentType.UNKNOWN)
    }

    private fun buildRequest(content: DocumentContent, prompt: String): GeminiRequest {
        val parts = mutableListOf<GeminiPart>()
        if (content.base64Image != null) {
            parts += GeminiPart(inlineData = GeminiInlineData(content.mimeType, content.base64Image))
        }
        if (content.text != null) {
            parts += GeminiPart(text = "Document content:\n${content.text}")
        }
        parts += GeminiPart(text = prompt)
        return GeminiRequest(
            contents = listOf(GeminiContent(parts)),
            generationConfig = GeminiGenerationConfig(responseMimeType = "application/json"),
        )
    }

    private fun callGemini(request: GeminiRequest): GeminiResponse {
        val uri = "/v1beta/models/$model:generateContent?key=$apiKey"
        val httpRequest = HttpRequest.POST(uri, request).contentType(MediaType.APPLICATION_JSON_TYPE)
        return httpClient.toBlocking().retrieve(httpRequest, GeminiResponse::class.java)
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseResponse(response: GeminiResponse, documentType: DocumentType): ExtractionResult {
        val raw = response.text() ?: "{}"
        val tree = runCatching { objectMapper.readTree(raw) }.getOrNull()
        val fields = runCatching {
            objectMapper.convertValue(tree?.get("fields"), Map::class.java) as Map<String, Any?>
        }.getOrDefault(emptyMap())
        val confidence = tree?.get("confidence")?.asDouble() ?: 0.0
        return ExtractionResult(documentType, fields, confidence, raw)
    }
}

/** Loads prompt .txt files from src/main/resources/prompts/. */
private class PromptLoader {
    fun load(type: DocumentType?): String {
        val filename = when (type) {
            DocumentType.BULETIN -> "buletin.txt"
            DocumentType.PERMIS_CONDUCERE -> "permis_conducere.txt"
            DocumentType.POLITA_RCA -> "polita_rca.txt"
            DocumentType.CERTIFICAT_INMATRICULARE -> "certificat_inmatriculare.txt"
            else -> if (type == null) "classify.txt" else "generic.txt"
        }
        return PromptLoader::class.java.getResourceAsStream("/prompts/$filename")
            ?.bufferedReader()?.readText()
            ?: error("Prompt file not found: $filename")
    }
}
