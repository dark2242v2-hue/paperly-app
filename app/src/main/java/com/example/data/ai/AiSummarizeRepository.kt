package com.example.data.ai

import com.example.data.model.PdfAiPayload
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.content
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout

class AiSummarizeRepository {

    private val model by lazy {
        Firebase.ai.generativeModel("gemini-3.8-flash")
    }

    fun summarizePdfStream(payload: PdfAiPayload): Flow<String> = flow {
        val trimmedText = payload.extractedText.trim()
        val promptContent = content {
            if (trimmedText.length >= 80) {
                text(
                    """
                    You are Paperly AI, a concise and accurate document analyst.
                    Summarize the following PDF document ("${payload.displayName}", ${payload.pageCount} pages).
                    Provide a crisp, scannable Markdown response with these sections:
                    ### Executive Summary
                    (2-3 sentences capturing the core purpose and conclusion)

                    ### Key Takeaways
                    - (3 to 6 concise, high-signal bullet points with concrete facts/numbers if present)

                    ### Action Items & Topics
                    - (Relevant next steps, dates, or core topics covered)

                    Document Content:
                    $trimmedText
                    """.trimIndent()
                )
            } else {
                text(
                    """
                    You are Paperly AI, a concise and accurate document analyst.
                    This PDF ("${payload.displayName}", ${payload.pageCount} pages) appears to be scanned or image-based.
                    Read the attached page images carefully and provide a crisp, scannable Markdown summary with:
                    ### Executive Summary
                    ### Key Takeaways
                    ### Action Items & Topics
                    """.trimIndent()
                )
                payload.fallbackBitmaps.forEach { bitmap ->
                    image(bitmap)
                }
                if (trimmedText.isNotEmpty()) {
                    text("Partial extracted text:\n$trimmedText")
                }
            }
        }

        val accumulated = StringBuilder()
        withTimeout(60_000L) {
            model.generateContentStream(promptContent).collect { chunk ->
                val part = chunk.text.orEmpty()
                if (part.isNotEmpty()) {
                    accumulated.append(part)
                    emit(accumulated.toString())
                }
            }
        }
    }
}
