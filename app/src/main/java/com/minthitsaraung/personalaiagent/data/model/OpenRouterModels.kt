package com.minthitsaraung.personalaiagent.data.model

import com.google.gson.annotations.SerializedName

// ─────────────────────────────────────────────────────────────────────────────
//  OpenRouter API — Request Models
//
//  OpenRouter exposes an OpenAI-compatible /chat/completions endpoint.
//  The request body uses the standard "messages" array format.
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Top-level request body for POST /v1/chat/completions.
 *
 * @param model       OpenRouter model ID, e.g. "google/gemini-2.0-flash-exp".
 *                    Fetched at runtime from SharedPreferences — never hardcoded.
 * @param messages    Conversation history starting with the system prompt.
 * @param temperature 0.0–2.0. Controls response randomness (0.7 is a good default).
 * @param maxTokens   Upper bound on response length.
 */
data class OpenRouterRequest(
    @SerializedName("model") val model: String,
    @SerializedName("messages") val messages: List<ChatMessage>,
    @SerializedName("temperature") val temperature: Float = 0.7f,
    @SerializedName("max_tokens") val maxTokens: Int = 1024,
    @SerializedName("top_p") val topP: Float = 0.95f
)

/**
 * A single message in the conversation.
 *
 * @param role     "system" | "user" | "assistant"
 * @param content  The text content of the message.
 */
data class ChatMessage(
    @SerializedName("role") val role: String,
    @SerializedName("content") val content: String
)

// ─────────────────────────────────────────────────────────────────────────────
//  OpenRouter API — Response Models
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Top-level response from the /v1/chat/completions endpoint.
 *
 * @param id      Unique ID for this completion.
 * @param choices List of generated response choices (usually just one).
 * @param usage   Token usage statistics.
 * @param error   Set if the API returned an error object inside a 200 response.
 */
data class OpenRouterResponse(
    @SerializedName("id") val id: String?,
    @SerializedName("choices") val choices: List<Choice>?,
    @SerializedName("usage") val usage: Usage?,
    @SerializedName("error") val error: OpenRouterError?
)

/**
 * One generated response choice.
 *
 * @param message      The assistant message content.
 * @param finishReason Why generation stopped ("stop", "length", "content_filter", etc.).
 */
data class Choice(
    @SerializedName("message") val message: ChatMessage?,
    @SerializedName("finish_reason") val finishReason: String?,
    @SerializedName("index") val index: Int?
)

/**
 * Token usage statistics returned by OpenRouter.
 */
data class Usage(
    @SerializedName("prompt_tokens") val promptTokens: Int?,
    @SerializedName("completion_tokens") val completionTokens: Int?,
    @SerializedName("total_tokens") val totalTokens: Int?
)

/**
 * Error object that OpenRouter embeds in a 200-body when something goes wrong.
 * Always check this even on HTTP 200 responses.
 */
data class OpenRouterError(
    @SerializedName("code") val code: Int?,
    @SerializedName("message") val message: String?,
    @SerializedName("type") val type: String?
)

// ─────────────────────────────────────────────────────────────────────────────
//  UI State wrappers — unchanged, shared across the whole app
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Sealed class representing all possible states of an AI request.
 */
sealed class AiResult {
    data object Loading : AiResult()
    data class Success(val text: String) : AiResult()
    data class Error(val message: String) : AiResult()
}

/**
 * The overall state of the AI assistant at any moment.
 */
enum class AssistantState {
    IDLE,
    LISTENING,
    THINKING,
    SPEAKING
}

/**
 * Well-known OpenRouter model IDs shown as default options in the UI Spinner.
 * The user can always type a custom model ID instead.
 */
object OpenRouterModels {
    const val GEMINI_2_FLASH_EXP = "google/gemini-2.0-flash-exp"
    const val GEMINI_FLASH_1_5  = "google/gemini-flash-1.5"
    const val GPT_4O_MINI        = "openai/gpt-4o-mini"
    const val GPT_4O             = "openai/gpt-4o"
    const val CLAUDE_HAIKU       = "anthropic/claude-3-haiku"
    const val CLAUDE_SONNET      = "anthropic/claude-3.5-sonnet"
    const val LLAMA_3_1_8B       = "meta-llama/llama-3.1-8b-instruct"
    const val MISTRAL_7B         = "mistralai/mistral-7b-instruct"

    /** Ordered list of models shown in the Spinner dropdown. */
    val PRESET_MODELS = listOf(
        GEMINI_2_FLASH_EXP,
        GEMINI_FLASH_1_5,
        GPT_4O_MINI,
        GPT_4O,
        CLAUDE_HAIKU,
        CLAUDE_SONNET,
        LLAMA_3_1_8B,
        MISTRAL_7B
    )

    const val DEFAULT_MODEL = GEMINI_2_FLASH_EXP
}
