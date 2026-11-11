package com.drumsheesh.platform.ai

import com.drumsheesh.core.ai.AiGrooveGeneratorPort
import com.drumsheesh.core.ai.AiGroovePromptBuilder
import com.drumsheesh.core.ai.AiGrooveRequest
import com.drumsheesh.core.ai.GrooveJsonCodec
import com.drumsheesh.core.ai.GroovePattern
import com.drumsheesh.core.ai.GrooveSource
import com.drumsheesh.core.ai.ProceduralGrooveGenerator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

/**
 * Android `:platform` implementation of [AiGrooveGeneratorPort] (`Req 1.1, 1.2`).
 *
 * Calls the Gemini `gemini-2.5-flash` structured JSON HTTPS endpoint when an API key is configured
 * and network is available, and automatically falls back to `:core`'s [ProceduralGrooveGenerator]
 * whenever offline, unconfigured, or if the remote response fails schema validation.
 */
class GeminiAiGrooveAdapter(
    private val apiKeyProvider: () -> String? = { System.getenv("GEMINI_API_KEY") },
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val fallbackGenerator: AiGrooveGeneratorPort = ProceduralGrooveGenerator,
    private val httpPostExecutor: ((url: String, jsonBody: String) -> String)? = null
) : AiGrooveGeneratorPort {

    companion object {
        private const val GEMINI_ENDPOINT_TEMPLATE =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=%s"
        private const val CONNECT_TIMEOUT_MS = 6_000
        private const val READ_TIMEOUT_MS = 8_000
    }

    override suspend fun generateGroove(request: AiGrooveRequest): GroovePattern {
        val apiKey = apiKeyProvider()?.trim().orEmpty()
        if (apiKey.isEmpty()) {
            return fallbackGenerator.generateGroove(request)
        }

        return try {
            withContext(ioDispatcher) {
                val url = String.format(GEMINI_ENDPOINT_TEMPLATE, apiKey)
                val payload = buildGeminiRequestJson(request)
                val rawResponse = httpPostExecutor?.invoke(url, payload)
                    ?: executeHttpsPost(url, payload)

                val grooveJson = extractGrooveJsonFromGeminiResponse(rawResponse)
                val decoded = GrooveJsonCodec.decodeGroovePattern(
                    rawJson = grooveJson,
                    defaultSource = GrooveSource.CLOUD_AI
                )
                val targetKit = request.targetKit
                if (targetKit != null) {
                    decoded.copy(kitId = targetKit, source = GrooveSource.CLOUD_AI)
                } else {
                    decoded.copy(source = GrooveSource.CLOUD_AI)
                }
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (_: Throwable) {
            fallbackGenerator.generateGroove(request)
        }
    }

    internal fun buildGeminiRequestJson(request: AiGrooveRequest): String {
        val systemInstruction = escapeJsonString(AiGroovePromptBuilder.buildSystemInstruction())
        val userPrompt = escapeJsonString(AiGroovePromptBuilder.buildUserPrompt(request))
        return """
            {
              "systemInstruction": {
                "parts": [{ "text": "$systemInstruction" }]
              },
              "contents": [
                {
                  "role": "user",
                  "parts": [{ "text": "$userPrompt" }]
                }
              ],
              "generationConfig": {
                "temperature": 0.4,
                "responseMimeType": "application/json"
              }
            }
        """.trimIndent()
    }

    internal fun extractGrooveJsonFromGeminiResponse(rawResponse: String): String {
        val trimmed = rawResponse.trim()
        // If response is already a direct GroovePattern JSON object containing "steps"
        if (trimmed.contains("\"steps\"") && !trimmed.contains("\"candidates\"")) {
            return trimmed
        }

        // Extract the first candidate's parts[0].text string
        val textFieldRegex = Regex(""""text"\s*:\s*"((?:\\.|[^"\\])*)"""")
        val match = textFieldRegex.find(trimmed)
            ?: throw IllegalArgumentException("Gemini response missing candidate text field")

        val escapedText = match.groupValues[1]
        return unescapeJsonString(escapedText)
    }

    private fun executeHttpsPost(endpointUrl: String, requestBodyJson: String): String {
        val connection = (URL(endpointUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            setRequestProperty("Accept", "application/json")
        }

        return try {
            OutputStreamWriter(connection.outputStream, StandardCharsets.UTF_8).use { writer ->
                writer.write(requestBodyJson)
                writer.flush()
            }

            val status = connection.responseCode
            if (status !in 200..299) {
                throw IllegalStateException("Gemini HTTPS request failed with HTTP $status")
            }

            BufferedReader(InputStreamReader(connection.inputStream, StandardCharsets.UTF_8)).use { reader ->
                reader.readText()
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun escapeJsonString(value: String): String = buildString(value.length + 16) {
        for (ch in value) {
            when (ch) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(ch)
            }
        }
    }

    private fun unescapeJsonString(escaped: String): String = buildString(escaped.length) {
        var i = 0
        while (i < escaped.length) {
            val ch = escaped[i]
            if (ch == '\\' && i + 1 < escaped.length) {
                when (val next = escaped[i + 1]) {
                    '"' -> append('"')
                    '\\' -> append('\\')
                    '/' -> append('/')
                    'n' -> append('\n')
                    'r' -> append('\r')
                    't' -> append('\t')
                    else -> append(next)
                }
                i += 2
            } else {
                append(ch)
                i += 1
            }
        }
    }
}
