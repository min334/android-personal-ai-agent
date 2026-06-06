package com.minthitsaraung.personalaiagent.viewmodel

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.minthitsaraung.personalaiagent.accessibility.TapDetectionAccessibilityService
import com.minthitsaraung.personalaiagent.data.model.AiResult
import com.minthitsaraung.personalaiagent.data.model.AssistantState
import com.minthitsaraung.personalaiagent.data.model.OpenRouterModels
import com.minthitsaraung.personalaiagent.data.model.UserPreferences
import com.minthitsaraung.personalaiagent.repository.AiRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * MainViewModel
 * ──────────────────────────────────────────────────────────────────────────────
 * Survives configuration changes (screen rotation).
 * Exposes two flows to the UI:
 *  - [uiState]          — all display-facing state (status flags, last response, etc.)
 *  - [userPreferences]  — DataStore-backed live preferences
 *
 * The ViewModel holds zero View references and knows nothing about XML layouts.
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    application: Application,
    private val aiRepository: AiRepository
) : AndroidViewModel(application) {

    // ─── UI State ─────────────────────────────────────────────────────────────

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    val userPreferences: StateFlow<UserPreferences> = aiRepository.userPreferencesFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = UserPreferences()
        )

    init {
        refreshPermissionStatus()
        // Seed the settings UI with the currently stored values
        _uiState.update {
            it.copy(
                savedModelId    = aiRepository.getModelId(),
                maskedApiKey    = aiRepository.getMaskedApiKey()
            )
        }
    }

    // ─── Permission / feature status ─────────────────────────────────────────

    /**
     * Re-read all permission flags from the system and update [uiState].
     * Called from onResume() — the user may have just returned from system settings.
     */
    fun refreshPermissionStatus() {
        val context = getApplication<Application>()

        val isAccessibilityEnabled = TapDetectionAccessibilityService.isRunning
                || isAccessibilityServiceEnabled()

        val isMicrophoneGranted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        val isOverlayGranted = Settings.canDrawOverlays(context)
        val hasApiKey        = aiRepository.hasApiKey()

        _uiState.update {
            it.copy(
                isAccessibilityEnabled = isAccessibilityEnabled,
                isMicrophoneGranted    = isMicrophoneGranted,
                isOverlayGranted       = isOverlayGranted,
                hasApiKey              = hasApiKey,
                maskedApiKey           = aiRepository.getMaskedApiKey(),
                savedModelId           = aiRepository.getModelId(),
                isFullyConfigured      = isAccessibilityEnabled
                        && isMicrophoneGranted
                        && isOverlayGranted
                        && hasApiKey
            )
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val context = getApplication<Application>()
        val component = "${context.packageName}/.accessibility.TapDetectionAccessibilityService"
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabled.split(':').any { it.equals(component, ignoreCase = true) }
    }

    // ─── Settings — OpenRouter ────────────────────────────────────────────────

    /**
     * Persist the API key (encrypted) and Model ID (plain SharedPreferences).
     * Both are stored atomically before the UI is updated.
     *
     * @param apiKey   The user's OpenRouter secret key (sk-or-…).
     * @param modelId  An OpenRouter model ID, e.g. "google/gemini-2.0-flash-exp".
     */
    fun saveOpenRouterSettings(apiKey: String, modelId: String) {
        if (apiKey.isNotBlank()) aiRepository.saveApiKey(apiKey)
        aiRepository.saveModelId(modelId)

        _uiState.update {
            it.copy(
                hasApiKey       = aiRepository.hasApiKey(),
                maskedApiKey    = aiRepository.getMaskedApiKey(),
                savedModelId    = modelId,
                settingsSaved   = true,
                isFullyConfigured = it.isAccessibilityEnabled
                        && it.isMicrophoneGranted
                        && it.isOverlayGranted
                        && aiRepository.hasApiKey()
            )
        }

        // Auto-clear the "saved" confirmation after 3 seconds
        viewModelScope.launch {
            kotlinx.coroutines.delay(3_000)
            _uiState.update { it.copy(settingsSaved = false) }
        }
    }

    fun getMaskedApiKey(): String = aiRepository.getMaskedApiKey()
    fun getSavedModelId(): String = aiRepository.getModelId()

    // ─── General preferences ──────────────────────────────────────────────────

    fun setUserName(name: String)       = viewModelScope.launch { aiRepository.setUserName(name) }
    fun setRequiredTapCount(count: Int) = viewModelScope.launch { aiRepository.setRequiredTapCount(count) }
    fun setMaxTapIntervalMs(ms: Long)   = viewModelScope.launch { aiRepository.setMaxTapIntervalMs(ms) }
    fun setSpeechRate(rate: Float)      = viewModelScope.launch { aiRepository.setSpeechRate(rate) }
    fun setSpeechPitch(pitch: Float)    = viewModelScope.launch { aiRepository.setSpeechPitch(pitch) }

    // ─── Manual test ──────────────────────────────────────────────────────────

    fun testAssistant() {
        viewModelScope.launch {
            _uiState.update { it.copy(assistantState = AssistantState.THINKING, lastAiResponse = "") }
            val result = aiRepository.askAi("Hello! Please introduce yourself briefly.")
            _uiState.update {
                when (result) {
                    is AiResult.Success -> it.copy(
                        assistantState = AssistantState.IDLE,
                        lastAiResponse = result.text
                    )
                    is AiResult.Error -> it.copy(
                        assistantState = AssistantState.IDLE,
                        lastAiResponse = "⚠ ${result.message}"
                    )
                    AiResult.Loading -> it
                }
            }
        }
    }

    // ─── Broadcast callbacks ──────────────────────────────────────────────────

    fun onAssistantTriggered() { _uiState.update { it.copy(assistantState = AssistantState.LISTENING) } }
    fun onAssistantDone()      { _uiState.update { it.copy(assistantState = AssistantState.IDLE) } }
}

// ─── UI State ──────────────────────────────────────────────────────────────────

data class MainUiState(
    // Permission / setup flags
    val isAccessibilityEnabled : Boolean = false,
    val isMicrophoneGranted    : Boolean = false,
    val isOverlayGranted       : Boolean = false,
    val hasApiKey              : Boolean = false,
    val isFullyConfigured      : Boolean = false,

    // Settings
    val maskedApiKey  : String = "",
    val savedModelId  : String = OpenRouterModels.DEFAULT_MODEL,
    val settingsSaved : Boolean = false,

    // Assistant runtime
    val assistantState  : AssistantState = AssistantState.IDLE,
    val lastAiResponse  : String = ""
)
