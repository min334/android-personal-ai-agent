package com.minthitsaraung.personalaiagent.data.remote

import com.minthitsaraung.personalaiagent.data.model.OpenRouterRequest
import com.minthitsaraung.personalaiagent.data.model.OpenRouterResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

/**
 * Retrofit interface for the OpenRouter /v1/chat/completions endpoint.
 *
 * ─── Why OpenRouter? ───────────────────────────────────────────────────────────
 * OpenRouter is a unified API gateway that gives access to 100+ models
 * (Gemini, GPT-4o, Claude, Llama, Mistral, etc.) through a single
 * OpenAI-compatible endpoint. Switching models is as easy as changing the
 * "model" field in the request — no SDK swap required.
 *
 * ─── Authentication ───────────────────────────────────────────────────────────
 * OpenRouter uses Bearer token authentication. The API key is passed as the
 * "Authorization: Bearer <key>" header on every request.
 *
 * We pass the key as a per-call @Header parameter rather than baking it into
 * the OkHttp interceptor. This allows the key to be changed at runtime
 * (when the user updates it in Settings) without recreating the Retrofit client.
 *
 * ─── Optional headers ─────────────────────────────────────────────────────────
 * - HTTP-Referer: identifies your app on the OpenRouter dashboard (good practice).
 * - X-Title: shown next to your app name on openrouter.ai/activity.
 */
interface OpenRouterApiService {

    @POST("v1/chat/completions")
    suspend fun chatCompletion(
        @Header("Authorization") authHeader: String,
        @Header("HTTP-Referer") referer: String = "https://github.com/personal-ai-agent",
        @Header("X-Title") appTitle: String = "Personal AI Agent",
        @Body request: OpenRouterRequest
    ): Response<OpenRouterResponse>
}
