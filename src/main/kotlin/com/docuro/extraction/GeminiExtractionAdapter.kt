package com.docuro.extraction

import com.docuro.domain.DocumentTypeEntity
import com.docuro.repository.DocumentTypeRepository
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
    private val documentTypeRepository: DocumentTypeRepository,
) : DocumentExtractionPort {

    private val promptLoader = PromptLoader()

    override fun extract(content: DocumentContent): ExtractionResult {
        val documentType = content.documentType ?: classify(content)
        val promptFile = documentTypeRepository.findById(documentType)
            .map { it.promptFile }
            .orElse("generic.txt")
        val prompt = promptLoader.load(promptFile)
        val request = buildRequest(content, prompt)
        val response = callGemini(request)
        return parseResponse(response, documentType)
    }

    private fun classify(content: DocumentContent): String {
        val allTypes = documentTypeRepository.findAll().toList()
        val classifyPrompt = buildClassifyPrompt(allTypes)
        val request = buildRequest(content, classifyPrompt)
        val response = callGemini(request)
        val text = response.text() ?: return "UNKNOWN"
        return runCatching {
            objectMapper.readTree(text).get("type").asText().uppercase()
        }.getOrDefault("UNKNOWN")
    }

    private fun buildClassifyPrompt(types: List<DocumentTypeEntity>): String {
        val template = promptLoader.load("classify.txt")
        val typeList = types.joinToString("\n") { "- ${it.code}: ${it.description}" }
        return template.replace("{{DOCUMENT_TYPES}}", typeList)
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
    private fun parseResponse(response: GeminiResponse, documentType: String): ExtractionResult {
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
    fun load(filename: String): String =
        PromptLoader::class.java.getResourceAsStream("/prompts/$filename")
            ?.bufferedReader()?.readText()
            ?: error("Prompt file not found: $filename")
}
