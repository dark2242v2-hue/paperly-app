package com.example.data.ai

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import com.example.data.model.PdfAiPayload
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.URL
import javax.net.ssl.HttpsURLConnection

private const val TAG = "AiSummarizeRepository"

class AiSummarizeRepository {

    companion object {
        private const val PRIMARY_ENDPOINT =
            "https://" + "paperly-ai.dark2242v2.workers.dev" + "?model=gemini-3.5-flash-lite"
        private const val FALLBACK_ENDPOINT =
            "https://" + "paperly-ai.dark2242v2.workers.dev" + "?model=gemini-2.5-flash-lite"
        private const val FALLBACK_MAX_RETRIES = 1
        private const val FALLBACK_RETRY_DELAY_MS = 1_000L
    }

    private class ProxyHttpException(
        val statusCode: Int,
        val errorMessage: String
    ) : IllegalStateException(errorMessage) {
        val isFallbackTrigger: Boolean
            get() = statusCode == 404 || statusCode == 429 || statusCode == 502 || statusCode == 503 || statusCode == 504 ||
                errorMessage.contains("high demand", ignoreCase = true) ||
                errorMessage.contains("overloaded", ignoreCase = true) ||
                errorMessage.contains("unavailable", ignoreCase = true) ||
                errorMessage.contains("server busy", ignoreCase = true)
    }

    suspend fun summarizePdf(payload: PdfAiPayload): String = withTimeout(45_000L) {
        val requestBodyString = buildRequestPayload(payload).toString()

        // 1. Primary fast route: ?model=gemini-3.5-flash-lite (2-4s typical response)
        var lastError: Exception? = null
        try {
            return@withTimeout executeGenerateContent(
                endpointUrl = PRIMARY_ENDPOINT,
                requestBodyString = requestBodyString
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: ProxyHttpException) {
            lastError = e
            if (!e.isFallbackTrigger) {
                throw e
            }
            Log.w(TAG, "$PRIMARY_ENDPOINT failed (HTTP ${e.statusCode}), falling back immediately to $FALLBACK_ENDPOINT", e)
        }

        // 2. Immediate fallback to ?model=gemini-2.5-flash-lite on HTTP 429, 404, or 503
        for (attempt in 0..FALLBACK_MAX_RETRIES) {
            try {
                return@withTimeout executeGenerateContent(
                    endpointUrl = FALLBACK_ENDPOINT,
                    requestBodyString = requestBodyString
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: ProxyHttpException) {
                lastError = e
                if (e.isFallbackTrigger && attempt < FALLBACK_MAX_RETRIES) {
                    Log.w(TAG, "$FALLBACK_ENDPOINT busy (HTTP ${e.statusCode}) on attempt ${attempt + 1}", e)
                    delay(FALLBACK_RETRY_DELAY_MS)
                } else {
                    throw e
                }
            }
        }

        throw (lastError ?: IllegalStateException("AI service is temporarily busy. Please try again."))
    }

    private fun buildRequestPayload(payload: PdfAiPayload): JSONObject {
        val trimmedText = payload.extractedText.trim()
        val partsArray = JSONArray()
        val truncationHint = if (payload.wasTruncated) {
            "\nNote: This document is large; the text below was compressed and capped at the first 18,000 characters."
        } else {
            ""
        }

        val formattingGuidelines = """
            Automatically identify the document type (e.g., study notes, contract/agreement, invoice/financial report, resume/CV, technical manual, research paper, or article) and adapt your terminology and focus accordingly.

            Format your response strictly as a polished executive brief using EXACTLY these four Markdown section headings and concise bullet points with **bold key terms** (such as **important terms**, figures, dates, obligations, or formulas):

            ## Executive Overview
            Write a crisp 2-sentence core takeaway stating the identified document type and its primary purpose.

            ## Key Facts, Clauses & Concepts
            - Bullet points highlighting **bold key terms**, critical figures, dates, definitions, or core concepts tailored to this document type.

            ## Critical Details & Comparisons
            - Bullet points detailing important rules, obligations, clauses, formulas, pricing breakdowns, comparisons, or metrics specific to the document.

            ## Action Items & Key Takeaways
            - Top 3–5 concrete next steps, deadlines, warnings, or must-remember takeaways.

            Keep the tone authoritative, clear, and concise. Use `- ` for bullets and `**bold**` only for high-signal terms. Do not include any preamble or closing chatter outside these four sections.

            CRITICAL MATH & CHEMISTRY RULE: NEVER output LaTeX math syntax (no $, $$, \text{}, \mathrm{}, \ce{}, \rightarrow, \frac, ^, or _ formatting). Write all chemical formulas, ions, equations, reactions, exponents, and math using clean Unicode characters (e.g., Ca²⁺, CO₃²⁻, CO₂, CaCO₃, H₂O, →, ⇌, ×, ÷, ≤, ≥, ±, °, x²).
        """.trimIndent()

        if (trimmedText.length >= 60) {
            val prompt = """
                You are Paperly AI, an expert executive document analyst.
                Analyze the PDF "${payload.displayName}" (${payload.pageCount} pages).$truncationHint

                $formattingGuidelines

                Document Content:
                $trimmedText
            """.trimIndent()
            partsArray.put(JSONObject().put("text", prompt))
        } else {
            val prompt = """
                You are Paperly AI, an expert executive document analyst.
                This PDF ("${payload.displayName}", ${payload.pageCount} pages) appears scanned or image-based.
                Read the attached page images carefully and produce an executive-grade breakdown.

                $formattingGuidelines
            """.trimIndent()
            partsArray.put(JSONObject().put("text", prompt))

            payload.fallbackBitmaps.forEach { bitmap ->
                val baos = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 80, baos)
                val base64Image = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
                partsArray.put(
                    JSONObject().put(
                        "inline_data",
                        JSONObject()
                            .put("mime_type", "image/jpeg")
                            .put("data", base64Image)
                    )
                )
            }

            if (trimmedText.isNotEmpty()) {
                partsArray.put(JSONObject().put("text", "Partial extracted text:\n$trimmedText"))
            }
        }

        return JSONObject()
            .put(
                "contents",
                JSONArray().put(
                    JSONObject()
                        .put("role", "user")
                        .put("parts", partsArray)
                )
            )
            .put(
                "generationConfig",
                JSONObject()
                    .put("temperature", 0.25)
                    .put("maxOutputTokens", 1400)
            )
    }

    private suspend fun executeGenerateContent(
        endpointUrl: String,
        requestBodyString: String
    ): String = withContext(Dispatchers.IO) {
        val url = URL(endpointUrl)
        val connection = (url.openConnection() as HttpsURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 30_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            setRequestProperty("User-Agent", "PaperlyAndroidApp/1.0")
            setRequestProperty("Accept", "application/json")
        }

        try {
            connection.outputStream.use { os ->
                os.write(requestBodyString.toByteArray(Charsets.UTF_8))
            }

            val statusCode = connection.responseCode
            val responseStream = if (statusCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            }
            val rawResponse = responseStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            val trimmedResponse = rawResponse.trimStart()

            if (statusCode !in 200..299) {
                val apiError = if (trimmedResponse.startsWith("{")) {
                    runCatching {
                        val errJson = JSONObject(trimmedResponse)
                        errJson.optJSONObject("error")?.optString("message")
                            ?: errJson.optString("error")
                    }.getOrNull()
                } else {
                    null
                }
                val message = apiError?.takeIf { it.isNotBlank() }
                    ?: "Server busy, please try again (HTTP $statusCode)"
                throw ProxyHttpException(statusCode, message)
            }

            if (!trimmedResponse.startsWith("{")) {
                throw ProxyHttpException(
                    statusCode,
                    "Unexpected non-JSON response from server (HTTP $statusCode)"
                )
            }

            val json = JSONObject(trimmedResponse)

            // Parse standard Gemini response or simplified proxy response
            val candidates = json.optJSONArray("candidates")
            if (candidates != null && candidates.length() > 0) {
                val contentParts = candidates
                    .getJSONObject(0)
                    .optJSONObject("content")
                    ?.optJSONArray("parts")
                if (contentParts != null) {
                    val summaryBuilder = StringBuilder()
                    for (i in 0 until contentParts.length()) {
                        val partText = contentParts.getJSONObject(i).optString("text")
                        if (partText.isNotBlank()) {
                            if (summaryBuilder.isNotEmpty()) summaryBuilder.append("\n")
                            summaryBuilder.append(partText.trim())
                        }
                    }
                    val parsed = summaryBuilder.toString().trim()
                    if (parsed.isNotEmpty()) return@withContext cleanLatexToUnicode(parsed)
                }
            }

            val directText = json.optString("summary").ifBlank { json.optString("text") }.trim()
            if (directText.isNotEmpty()) return@withContext cleanLatexToUnicode(directText)

            throw IllegalStateException("AI service returned an empty summary.")
        } finally {
            connection.disconnect()
        }
    }

    private fun cleanLatexToUnicode(input: String): String {
        if (input.isBlank()) return input

        var text = input
            .replace("\$\$", "")
            .replace("\\(", "")
            .replace("\\)", "")
            .replace("\\[", "")
            .replace("\\]", "")
            .replace("\\$", "$")

        // Unwrap common LaTeX wrapper commands such as \text{...}, \mathrm{...}, \ce{...}
        val wrapperRegex = Regex("""\\(?:text|mathrm|mathbf|mathit|mathsf|mathcal|operatorname|ce|pu)\{([^{}]*)\}""")
        repeat(3) {
            text = wrapperRegex.replace(text) { it.groupValues[1] }
        }

        // Convert \frac{a}{b} to a/b (wrapping multi-token parts cleanly if needed)
        val fracRegex = Regex("""\\frac\{([^{}]+)\}\{([^{}]+)\}""")
        repeat(2) {
            text = fracRegex.replace(text) { m ->
                val num = m.groupValues[1].trim()
                val den = m.groupValues[2].trim()
                val numFmt = if (num.any { it.isWhitespace() || it in "+-" }) "($num)" else num
                val denFmt = if (den.any { it.isWhitespace() || it in "+-" }) "($den)" else den
                "$numFmt/$denFmt"
            }
        }

        // Replace common LaTeX symbols & operators with Unicode
        val symbolReplacements = listOf(
            "\\rightleftharpoons" to "⇌",
            "\\leftrightharpoons" to "⇌",
            "\\leftrightarrow" to "↔",
            "\\longrightarrow" to "→",
            "\\rightarrow" to "→",
            "\\leftarrow" to "←",
            "\\Rightarrow" to "⇒",
            "\\Leftarrow" to "⇐",
            "\\implies" to "⇒",
            "\\iff" to "⇔",
            "\\to" to "→",
            "\\times" to "×",
            "\\cdot" to "·",
            "\\div" to "÷",
            "\\pm" to "±",
            "\\mp" to "∓",
            "\\approx" to "≈",
            "\\neq" to "≠",
            "\\ne" to "≠",
            "\\leq" to "≤",
            "\\le" to "≤",
            "\\geq" to "≥",
            "\\ge" to "≥",
            "\\equiv" to "≡",
            "\\propto" to "∝",
            "\\infty" to "∞",
            "\\degree" to "°",
            "\\circ" to "°",
            "\\sqrt" to "√",
            "\\sum" to "∑",
            "\\prod" to "∏",
            "\\int" to "∫",
            "\\partial" to "∂",
            "\\Delta" to "Δ",
            "\\Sigma" to "Σ",
            "\\Omega" to "Ω",
            "\\alpha" to "α",
            "\\beta" to "β",
            "\\gamma" to "γ",
            "\\delta" to "δ",
            "\\epsilon" to "ε",
            "\\theta" to "θ",
            "\\lambda" to "λ",
            "\\mu" to "μ",
            "\\pi" to "π",
            "\\rho" to "ρ",
            "\\sigma" to "σ",
            "\\omega" to "ω",
            "\\%" to "%",
            "\\," to " ",
            "\\;" to " ",
            "\\:" to " ",
            "\\!" to "",
            "\\left" to "",
            "\\right" to ""
        )
        for ((latex, unicode) in symbolReplacements) {
            text = text.replace(latex, unicode)
        }

        // Convert braced and simple superscripts: ^{2+}, ^2, ^-, ^+
        text = Regex("""\^\{([^{}]+)\}""").replace(text) { toSuperscript(it.groupValues[1]) }
        text = Regex("""\^([0-9+\-=()ni°])""").replace(text) { toSuperscript(it.groupValues[1]) }

        // Convert braced and digit/formula subscripts: _{2}, _{aq}, H_2O, CO_2, x_1
        text = Regex("""_\{([^{}]+)\}""").replace(text) { toSubscript(it.groupValues[1]) }
        text = Regex("""(?<=[A-Za-z0-9)\]])_([0-9]+)(?![a-z])""").replace(text) { toSubscript(it.groupValues[1]) }

        // Strip inline $...$ LaTeX delimiters while keeping legitimate currency like $50 or $1,200
        text = Regex("""\$([^$\n]+?)\$""").replace(text) { match ->
            val inner = match.groupValues[1]
            val looksLikeCurrency = Regex("""^\d[\d,.]*(?:\s*(?:to|and|–|-|or)\s*.*)?$""").matches(inner.trim()) &&
                !inner.contains('=') &&
                !inner.contains('→') &&
                !inner.contains('×') &&
                !inner.contains('÷') &&
                !inner.contains('^') &&
                !inner.contains('_') &&
                !inner.contains('\\')
            if (looksLikeCurrency) match.value else inner
        }

        // Strip any stray $ before non-digit math/chemical tokens (e.g., $Ca²⁺)
        text = Regex("""\$(?=[A-Za-z(\[\\√∑∫Δα-ω])""").replace(text, "")

        // Remove any lingering empty braces or backslashes before spaces/letters if leftover
        text = text.replace("{", "").replace("}", "")

        return text.trim()
    }

    private fun toSuperscript(raw: String): String {
        val sb = StringBuilder(raw.length)
        for (ch in raw.trim()) {
            val mapped = when (ch) {
                '0' -> '⁰'
                '1' -> '¹'
                '2' -> '²'
                '3' -> '³'
                '4' -> '⁴'
                '5' -> '⁵'
                '6' -> '⁶'
                '7' -> '⁷'
                '8' -> '⁸'
                '9' -> '⁹'
                '+' -> '⁺'
                '-', '−' -> '⁻'
                '=' -> '⁼'
                '(' -> '⁽'
                ')' -> '⁾'
                'n' -> 'ⁿ'
                'i' -> 'ⁱ'
                '°' -> '°'
                ' ' -> null
                else -> return raw
            }
            if (mapped != null) sb.append(mapped)
        }
        return sb.toString()
    }

    private fun toSubscript(raw: String): String {
        val sb = StringBuilder(raw.length)
        for (ch in raw.trim()) {
            val mapped = when (ch) {
                '0' -> '₀'
                '1' -> '₁'
                '2' -> '₂'
                '3' -> '₃'
                '4' -> '₄'
                '5' -> '₅'
                '6' -> '₆'
                '7' -> '₇'
                '8' -> '₈'
                '9' -> '₉'
                '+' -> '₊'
                '-', '−' -> '₋'
                '=' -> '₌'
                '(' -> '₍'
                ')' -> '₎'
                'a' -> 'ₐ'
                'e' -> 'ₑ'
                'h' -> 'ₕ'
                'i' -> 'ᵢ'
                'j' -> 'ⱼ'
                'k' -> 'ₖ'
                'l' -> 'ₗ'
                'm' -> 'ₘ'
                'n' -> 'ₙ'
                'o' -> 'ₒ'
                'p' -> 'ₚ'
                'r' -> 'ᵣ'
                's' -> 'ₛ'
                't' -> 'ₜ'
                'u' -> 'ᵤ'
                'v' -> 'ᵥ'
                'x' -> 'ₓ'
                ' ' -> null
                else -> return "(${raw.trim()})"
            }
            if (mapped != null) sb.append(mapped)
        }
        return sb.toString()
    }
}
