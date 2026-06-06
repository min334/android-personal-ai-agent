package com.minthitsaraung.personalaiagent.ui.activity

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.ArrayAdapter
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.snackbar.Snackbar
import com.minthitsaraung.personalaiagent.R
import com.minthitsaraung.personalaiagent.accessibility.TapDetectionAccessibilityService
import com.minthitsaraung.personalaiagent.data.model.AssistantState
import com.minthitsaraung.personalaiagent.data.model.OpenRouterModels
import com.minthitsaraung.personalaiagent.databinding.ActivityMainBinding
import com.minthitsaraung.personalaiagent.viewmodel.MainUiState
import com.minthitsaraung.personalaiagent.viewmodel.MainViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * MainActivity
 * ──────────────────────────────────────────────────────────────────────────────
 * The single entry-point screen. Contains three collapsible cards:
 *
 *  1. **Setup checklist** — guides the user through permissions
 *  2. **OpenRouter Settings** — API key + model ID with a Save button
 *  3. **Assistant status** — current state + last AI response + test button
 *
 * The Settings card is always visible so the user can change API key / model
 * at any time without navigating away.
 */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()

    // ─── Permission launcher ──────────────────────────────────────────────────

    private val microphonePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.refreshPermissionStatus()
        if (!granted) showSnackbar(getString(R.string.error_microphone_denied))
    }

    // ─── Broadcast receiver ───────────────────────────────────────────────────

    private val assistantReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                TapDetectionAccessibilityService.ACTION_ASSISTANT_TRIGGERED ->
                    viewModel.onAssistantTriggered()
                TapDetectionAccessibilityService.ACTION_SERVICE_STATUS_CHANGED ->
                    viewModel.refreshPermissionStatus()
            }
        }
    }

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupModelSpinner()
        setupClickListeners()
        observeUiState()
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshPermissionStatus()
        val filter = IntentFilter().apply {
            addAction(TapDetectionAccessibilityService.ACTION_ASSISTANT_TRIGGERED)
            addAction(TapDetectionAccessibilityService.ACTION_SERVICE_STATUS_CHANGED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(assistantReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(assistantReceiver, filter)
        }
    }

    override fun onPause() {
        super.onPause()
        try { unregisterReceiver(assistantReceiver) } catch (_: Exception) {}
    }

    // ─── Setup ────────────────────────────────────────────────────────────────

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        binding.toolbar.inflateMenu(R.menu.menu_main)
        binding.toolbar.setOnMenuItemClickListener { item ->
            if (item.itemId == R.id.action_settings) {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            } else false
        }
    }

    /**
     * Populate the Model ID spinner with preset OpenRouter model IDs.
     * The user can also type a custom ID in the companion EditText.
     */
    private fun setupModelSpinner() {
        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            OpenRouterModels.PRESET_MODELS
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        binding.spinnerModelId.adapter = adapter

        // Pre-select the saved model if it matches a preset
        val savedModel = viewModel.getSavedModelId()
        val presetIndex = OpenRouterModels.PRESET_MODELS.indexOf(savedModel)
        if (presetIndex >= 0) {
            binding.spinnerModelId.setSelection(presetIndex)
            binding.etCustomModelId.setText("")
        } else {
            // Custom model — show it in the text field
            binding.spinnerModelId.setSelection(0)
            binding.etCustomModelId.setText(savedModel)
        }
    }

    private fun setupClickListeners() {
        // ── Permissions ─────────────────────────────────────────────────────
        binding.btnEnableAccessibility.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            showSnackbar("Enable 'Personal AI Agent — Tap Detection' in the list")
        }
        binding.btnEnableOverlay.setOnClickListener {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            )
        }
        binding.btnGrantMicrophone.setOnClickListener {
            microphonePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
        binding.btnBatteryOptimization.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                startActivity(
                    Intent(
                        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        Uri.parse("package:$packageName")
                    )
                )
            }
        }

        // ── OpenRouter Settings — Save button ────────────────────────────────
        binding.btnSaveSettings.setOnClickListener {
            val apiKey = binding.etApiKey.text?.toString()?.trim() ?: ""

            // Resolve model ID: prefer custom text field if filled, else use Spinner selection
            val customModel = binding.etCustomModelId.text?.toString()?.trim() ?: ""
            val modelId = if (customModel.isNotBlank()) {
                customModel
            } else {
                binding.spinnerModelId.selectedItem?.toString()
                    ?: OpenRouterModels.DEFAULT_MODEL
            }

            // Validate
            var hasError = false
            if (apiKey.isBlank() && !viewModel.uiState.value.hasApiKey) {
                binding.tilApiKey.error = getString(R.string.error_api_key_required)
                hasError = true
            } else {
                binding.tilApiKey.error = null
            }
            if (modelId.isBlank()) {
                binding.tilCustomModelId.error = getString(R.string.error_model_id_required)
                hasError = true
            } else {
                binding.tilCustomModelId.error = null
            }

            if (!hasError) {
                viewModel.saveOpenRouterSettings(apiKey, modelId)
                // Clear the API key field after saving for security
                if (apiKey.isNotBlank()) binding.etApiKey.setText("")
            }
        }

        // ── Test button ──────────────────────────────────────────────────────
        binding.btnTestAssistant.setOnClickListener {
            viewModel.testAssistant()
        }
    }

    // ─── Observe & render ─────────────────────────────────────────────────────

    private fun observeUiState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state -> renderState(state) }
            }
        }
    }

    private fun renderState(state: MainUiState) {
        // ── Setup checklist ──────────────────────────────────────────────────
        renderStatus(
            tv          = binding.tvAccessibilityStatus,
            btn         = binding.btnEnableAccessibility,
            enabled     = state.isAccessibilityEnabled,
            enabledText = getString(R.string.status_accessibility_enabled),
            disabledText= getString(R.string.status_accessibility_disabled)
        )
        renderStatus(
            tv          = binding.tvOverlayStatus,
            btn         = binding.btnEnableOverlay,
            enabled     = state.isOverlayGranted,
            enabledText = getString(R.string.status_overlay_enabled),
            disabledText= getString(R.string.status_overlay_disabled)
        )
        renderStatus(
            tv          = binding.tvMicrophoneStatus,
            btn         = binding.btnGrantMicrophone,
            enabled     = state.isMicrophoneGranted,
            enabledText = getString(R.string.status_microphone_enabled),
            disabledText= getString(R.string.status_microphone_disabled)
        )

        // ── Settings card ────────────────────────────────────────────────────
        binding.tvApiKeyStatus.text = if (state.hasApiKey)
            getString(R.string.status_api_key_set, state.maskedApiKey)
        else getString(R.string.status_api_key_missing)
        binding.tvApiKeyStatus.setTextColor(statusColor(state.hasApiKey))

        binding.tvCurrentModel.text = getString(R.string.label_current_model, state.savedModelId)

        // Show "Saved ✓" snackbar when settings are persisted
        if (state.settingsSaved) {
            showSnackbar(getString(R.string.settings_saved_confirmation))
        }

        // ── Ready banner ─────────────────────────────────────────────────────
        binding.cardReadyBanner.visibility = if (state.isFullyConfigured) View.VISIBLE else View.GONE

        // ── Test button & assistant state ────────────────────────────────────
        binding.btnTestAssistant.isEnabled =
            state.isFullyConfigured && state.assistantState == AssistantState.IDLE

        binding.tvAssistantState.text = when (state.assistantState) {
            AssistantState.IDLE      -> getString(R.string.ai_state_idle)
            AssistantState.LISTENING -> getString(R.string.ai_state_listening)
            AssistantState.THINKING  -> getString(R.string.ai_state_thinking)
            AssistantState.SPEAKING  -> getString(R.string.ai_state_speaking)
        }

        // ── Last response ────────────────────────────────────────────────────
        if (state.lastAiResponse.isNotBlank()) {
            binding.tvLastResponse.text = state.lastAiResponse
            binding.cardLastResponse.visibility = View.VISIBLE
        } else {
            binding.tvLastResponse.text = getString(R.string.last_response_placeholder)
        }
    }

    private fun renderStatus(
        tv: android.widget.TextView,
        btn: com.google.android.material.button.MaterialButton,
        enabled: Boolean,
        enabledText: String,
        disabledText: String
    ) {
        tv.text = if (enabled) enabledText else disabledText
        tv.setTextColor(statusColor(enabled))
        btn.visibility = if (enabled) View.GONE else View.VISIBLE
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private fun statusColor(enabled: Boolean) = ContextCompat.getColor(
        this,
        if (enabled) R.color.ai_listening else R.color.md_theme_error
    )

    private fun showSnackbar(message: String) =
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
}
