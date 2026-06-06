package com.minthitsaraung.personalaiagent.ui.activity

import android.os.Bundle
import android.widget.SeekBar
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.minthitsaraung.personalaiagent.R
import com.minthitsaraung.personalaiagent.data.model.UserPreferences
import com.minthitsaraung.personalaiagent.databinding.ActivitySettingsBinding
import com.minthitsaraung.personalaiagent.viewmodel.MainViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * SettingsActivity
 * ──────────────────────────────────────────────────────────────────────────────
 * Allows the user to configure:
 *  - Their name (used in the Gemini system prompt)
 *  - Activation tap count (3–20 taps)
 *  - Max tap interval (200–2000ms)
 *  - TTS speech rate and pitch
 *
 * Changes are persisted immediately via the ViewModel → Repository → DataStore
 * pipeline. No "Save" button is needed — each control saves on change.
 */
@AndroidEntryPoint
class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupControls()
        observePreferences()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.settings_title)
        binding.toolbar.setNavigationOnClickListener { finish() }
    }

    private fun observePreferences() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.userPreferences.collect { prefs -> populateUi(prefs) }
            }
        }
    }

    private fun populateUi(prefs: UserPreferences) {
        // User name
        if (binding.etUserName.text?.toString() != prefs.userName) {
            binding.etUserName.setText(prefs.userName)
        }

        // Tap count (range 3–20, displayed directly)
        binding.sbTapCount.progress = prefs.requiredTapCount - 3
        binding.tvTapCountValue.text = prefs.requiredTapCount.toString()

        // Tap interval (range 200–2000ms, step 50ms)
        binding.sbTapInterval.progress = ((prefs.maxTapIntervalMs - 200) / 50).toInt()
        binding.tvTapIntervalValue.text = "${prefs.maxTapIntervalMs}ms"

        // Speech rate (0.25–3.0 in steps of 0.05)
        binding.sbSpeechRate.progress = ((prefs.speechRate - 0.25f) / 0.05f).toInt()
        binding.tvSpeechRateValue.text = "%.2f×".format(prefs.speechRate)

        // Pitch (0.5–2.0 in steps of 0.05)
        binding.sbPitch.progress = ((prefs.speechPitch - 0.5f) / 0.05f).toInt()
        binding.tvPitchValue.text = "%.2f".format(prefs.speechPitch)
    }

    private fun setupControls() {
        // ─── User name ──────────────────────────────────────────────────────
        binding.btnSaveUserName.setOnClickListener {
            val name = binding.etUserName.text?.toString()?.trim() ?: ""
            if (name.isNotBlank()) {
                viewModel.setUserName(name)
            }
        }

        // ─── Tap count (SeekBar range 0-17 → display 3-20) ─────────────────
        binding.sbTapCount.max = 17
        binding.sbTapCount.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                val count = progress + 3
                binding.tvTapCountValue.text = count.toString()
                if (fromUser) viewModel.setRequiredTapCount(count)
            }
            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) {}
        })

        // ─── Tap interval (SeekBar range 0-36 → 200–2000ms in 50ms steps) ─
        binding.sbTapInterval.max = 36
        binding.sbTapInterval.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                val ms = (progress * 50L) + 200L
                binding.tvTapIntervalValue.text = "${ms}ms"
                if (fromUser) viewModel.setMaxTapIntervalMs(ms)
            }
            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) {}
        })

        // ─── Speech rate (SeekBar 0-55 → 0.25-3.0 in 0.05 steps) ──────────
        binding.sbSpeechRate.max = 55
        binding.sbSpeechRate.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                val rate = (progress * 0.05f) + 0.25f
                binding.tvSpeechRateValue.text = "%.2f×".format(rate)
                if (fromUser) viewModel.setSpeechRate(rate)
            }
            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) {}
        })

        // ─── Pitch (SeekBar 0-30 → 0.5-2.0 in 0.05 steps) ─────────────────
        binding.sbPitch.max = 30
        binding.sbPitch.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                val pitch = (progress * 0.05f) + 0.5f
                binding.tvPitchValue.text = "%.2f".format(pitch)
                if (fromUser) viewModel.setSpeechPitch(pitch)
            }
            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) {}
        })
    }
}
