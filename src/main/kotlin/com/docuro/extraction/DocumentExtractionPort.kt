package com.docuro.extraction

/**
 * Central abstraction for all LLM calls.
 * Business logic depends only on this interface — swap the adapter via application.yml
 * (docuro.llm.provider: gemini | mock) without touching any service code.
 */
interface DocumentExtractionPort {
    fun extract(content: DocumentContent): ExtractionResult
}
