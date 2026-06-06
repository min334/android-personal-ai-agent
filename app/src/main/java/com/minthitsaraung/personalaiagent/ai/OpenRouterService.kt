package com.minthitsaraung.personalaiagent.ai

import com.minthitsaraung.personalaiagent.data.local.SecureStorageManager
import com.minthitsaraung.personalaiagent.data.model.AiResult
import com.minthitsaraung.personalaiagent.data.model.ChatMessage
import com.minthitsaraung.personalaiagent.data.model.OpenRouterRequest
import com.minthitsaraung.personalaiagent.data.remote.OpenRouterApiService
import kotlinx.coroutines.delay
import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * OpenRouterService
 * ──────────────────────────────────────────────────────────────────────────────
 * Core AI communication layer using the OpenRouter /v1/chat/completions API.
 *
 * Responsibilities:
 *  - Read API key and model ID from [SecureStorageManager] at call time
 *    (so Settings changes take effect immediately, no restart required)
 *  - Build the system prompt and conversation messages
 *  - Call the OpenRouter endpoint via Retrofit
 *  - Parse the response and extract the assistant's text
 *  - Retry transient failures with exponential back-off
 *  - Map every failure mode to a user-friendly [AiResult.Error] message
 */
@Singleton
class OpenRouterService @Inject constructor(
    private val apiService: OpenRouterApiService,
    private val secureStorage: SecureStorageManager
) {
    companion object {
        private const val MAX_RETRIES      = 3
        private const val RETRY_DELAY_MS   = 1000L
        private const val OPENROUTER_HOST  = "https://github.com/personal-ai-agent"
        private const val APP_TITLE        = "Personal AI Agent"

        /**
         * Build the system prompt that shapes the assistant's personality.
         * Injected as the first "system" role message in every request.
         * The user's name is personalised at call time — never hardcoded.
         */
        fun buildSystemPrompt(userName: String): String = """
            You are a personal AI assistant for $userName.
            Always address the user as $userName.
            Be friendly, concise, helpful, intelligent and respectful.
            Respond naturally in the same language used by the user.
            If the user speaks Burmese, respond in Burmese.
            If the user speaks English, respond in English.
            Keep your answers conversational and to the point — no unnecessary padding.
        """.trimIndent()
    }

    /**
     * Send [userMessage] to the configured OpenRouter model and return an [AiResult].
     *
     * The API key and model ID are read from [SecureStorageManager] on every call,
     * so if the user updates them in Settings they take effect on the very next request.
     *
     * @param userMessage         The transcribed speech (or typed text) from the user.
     * @param userName            Injected into the system prompt.
     * @param conversationHistory Previous turns for multi-turn context (optional).
     */
    suspend fun generateResponse(
        userMessage: String,
        userName: String = "MinThitSarAung",
        conversationHistory: List<ChatMessage> = emptyList()
    ): AiResult {
        // ── Validate configuration ─────────────────────────────────────────────
        val apiKey = secureStorage.getApiKey()
        if (apiKey.isBlank()) {
            return AiResult.Error(
                "OpenRouter API key is not configured. " +
                "Please add your API key in the Settings section."
            )
        }

        val modelId = secureStorage.getModelId()
        if (modelId.isBlank()) {
            return AiResult.Error(
                "No model selected. " +
                "Please choose a model ID in the Settings section."
            )
        }

        // ── Build message list ─────────────────────────────────────────────────
        // OpenRouter follows the OpenAI format:
        //   system message → (optional history) → latest user message
        val messages = buildList {
            add(ChatMessage(role = "system", content = buildSystemPrompt(userName)))
            addAll(conversationHistory)
            add(ChatMessage(role = "user", content = userMessage))
        }

        val request = OpenRouterRequest(
            model      = modelId,
            messages   = messages,
            temperature = 0.7f,
            maxTokens  = 1024
        )

        // ── Retry loop with exponential back-off ───────────────────────────────
        var lastError: AiResult.Error = AiResult.Error("Unknown error")

        repeat(MAX_RETRIES) { attempt ->
            try {
                val response = apiService.chatCompletion(
                    authHeader = "Bearer $apiKey",
                    referer    = OPENROUTER_HOST,
                    appTitle   = APP_TITLE,
                    request    = request
                )

                if (response.isSuccessful) {
                    val body = response.body()

                    // OpenRouter can embed an error object inside a 200 response
                    body?.error?.let { err ->
                        return AiResult.Error(
                            "Model error: ${err.message ?: "Unknown error from ${modelId}"}"
                        )
                    }

                    val text = body
                        ?.choices
                        ?.firstOrNull()
                        ?.message
                        ?.content

                    return if (!text.isNullOrBlank()) {
                        AiResult.Success(text.trim())
                    } else {
                        AiResult.Error("Received an empty response from the model.")
                    }

                } else {
                    val httpCode = response.code()
                    val errorBody = response.errorBody()?.string() ?: ""

                    lastError = when (httpCode) {
                        401 -> AiResult.Error(
                            "Invalid API key (401). " +
                            "Please check your OpenRouter API key in Settings."
                        )
                        402 -> AiResult.Error(
                            "Insufficient OpenRouter credits (402). " +
                            "Please top up your account at openrouter.ai."
                        )
                        403 -> AiResult.Error(
                            "Access denied (403). " +
                            "Your API key may not have access to model: $modelId"
                        )
                        404 -> AiResult.Error(
                            "Model not found (404): $modelId. " +
                            "Please check the model ID in Settings."
                        )
                        429 -> AiResult.Error(
                            "Rate limit exceeded (429). " +
                            "Please wait a moment and try again."
                        )
                        500, 502, 503 -> AiResult.Error(
                            "OpenRouter service error ($httpCode). Please try again."
                        )
                        else -> AiResult.Error(
                            "API error ($httpCode). Please try again."
                        )
                    }

                    // Do not retry 4xx client errors (except 429)
                    if (httpCode in 400..499 && httpCode != 429) return lastError
                }

            } catch (e: SocketTimeoutException) {
                lastError = AiResult.Error(
                    "Request timed out. Please check your internet connection."
                )
            } catch (e: IOException) {
                lastError = AiResult.Error(
                    "No internet connection. Please check your network."
                )
            } catch (e: HttpException) {
                lastError = AiResult.Error("Network error: ${e.message}")
            } catch (e: Exception) {
                return AiResult.Error("Unexpected error: ${e.message ?: "Unknown"}")
            }

            // Exponential back-off: 1s → 2s → 4s
            if (attempt < MAX_RETRIES - 1) {
                delay(RETRY_DELAY_MS * (1L shl attempt))
            }
        }

        return lastError
    }
}
