package com.minthitsaraung.personalaiagent.repository

import com.minthitsaraung.personalaiagent.ai.OpenRouterService
import com.minthitsaraung.personalaiagent.data.local.PreferencesDataSource
import com.minthitsaraung.personalaiagent.data.local.SecureStorageManager
import com.minthitsaraung.personalaiagent.data.model.AiResult
import com.minthitsaraung.personalaiagent.data.model.ChatMessage
import com.minthitsaraung.personalaiagent.data.model.UserPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AiRepository — single source of truth for all AI-related data operations.
 *
 * The ViewModel talks only to this repository; it is unaware of whether
 * data comes from the network, DataStore, or SharedPreferences.
 *
 * Delegating to OpenRouterService for AI calls and SecureStorageManager
 * for API key / model ID persistence.
 */
@Singleton
class AiRepository @Inject constructor(
    private val openRouterService: OpenRouterService,
    private val preferencesDataSource: PreferencesDataSource,
    private val secureStorage: SecureStorageManager
) {
    // ─── User preferences (DataStore) ─────────────────────────────────────────

    val userPreferencesFlow: Flow<UserPreferences> = preferencesDataSource.userPreferencesFlow

    // ─── OpenRouter API key ───────────────────────────────────────────────────

    fun hasApiKey(): Boolean = secureStorage.hasApiKey()
    fun saveApiKey(apiKey: String) = secureStorage.saveApiKey(apiKey)
    fun getApiKey(): String = secureStorage.getApiKey()
    fun getMaskedApiKey(): String = secureStorage.getMaskedApiKey()

    // ─── Model ID ─────────────────────────────────────────────────────────────

    fun saveModelId(modelId: String) = secureStorage.saveModelId(modelId)
    fun getModelId(): String = secureStorage.getModelId()

    // ─── AI requests ──────────────────────────────────────────────────────────

    /**
     * Send [userMessage] to the currently configured OpenRouter model.
     * Automatically reads the user name from preferences and the API key /
     * model ID from SecureStorageManager — so Settings changes apply instantly.
     */
    suspend fun askAi(
        userMessage: String,
        conversationHistory: List<ChatMessage> = emptyList()
    ): AiResult {
        val prefs = preferencesDataSource.userPreferencesFlow.first()
        return openRouterService.generateResponse(
            userMessage         = userMessage,
            userName            = prefs.userName,
            conversationHistory = conversationHistory
        )
    }

    // ─── Preference writes ────────────────────────────────────────────────────

    suspend fun setUserName(name: String)          = preferencesDataSource.setUserName(name)
    suspend fun setRequiredTapCount(count: Int)    = preferencesDataSource.setRequiredTapCount(count)
    suspend fun setMaxTapIntervalMs(ms: Long)      = preferencesDataSource.setMaxTapIntervalMs(ms)
    suspend fun setSpeechRate(rate: Float)         = preferencesDataSource.setSpeechRate(rate)
    suspend fun setSpeechPitch(pitch: Float)       = preferencesDataSource.setSpeechPitch(pitch)
}
