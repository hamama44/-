package com.example.data.voice

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.speech.SpeechRecognizer
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Status of the Microphone permission within the app.
 */
enum class VoicePermissionStatus {
    NOT_REQUESTED,
    GRANTED,
    DENIED,
    PERMANENTLY_DENIED
}

/**
 * Repository to manage microphone permissions, availability, and voice gaming preferences.
 */
class VoicePermissionRepository(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _permissionStatus = MutableStateFlow(determineCurrentStatus())
    val permissionStatus: StateFlow<VoicePermissionStatus> = _permissionStatus.asStateFlow()

    private val _isVoiceGamingEnabled = MutableStateFlow(
        prefs.getBoolean(KEY_VOICE_GAMING_ENABLED, true)
    )
    val isVoiceGamingEnabled: StateFlow<Boolean> = _isVoiceGamingEnabled.asStateFlow()

    private val _voiceSoundFeedbackEnabled = MutableStateFlow(
        prefs.getBoolean(KEY_SOUND_FEEDBACK, true)
    )
    val voiceSoundFeedbackEnabled: StateFlow<Boolean> = _voiceSoundFeedbackEnabled.asStateFlow()

    init {
        // Refresh status at startup
        refreshPermissionStatus()
    }

    /**
     * Checks if RECORD_AUDIO is currently granted in the OS.
     */
    fun hasRecordAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Refreshes the cached permission state.
     */
    fun refreshPermissionStatus(): VoicePermissionStatus {
        val newStatus = determineCurrentStatus()
        _permissionStatus.value = newStatus
        return newStatus
    }

    private fun determineCurrentStatus(): VoicePermissionStatus {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (granted) {
            return VoicePermissionStatus.GRANTED
        }

        val requestedBefore = prefs.getBoolean(KEY_PERMISSION_REQUESTED_BEFORE, false)
        return if (requestedBefore) {
            VoicePermissionStatus.DENIED
        } else {
            VoicePermissionStatus.NOT_REQUESTED
        }
    }

    /**
     * Updates status after an ActivityResult permission result.
     */
    fun onPermissionResult(isGranted: Boolean) {
        prefs.edit().putBoolean(KEY_PERMISSION_REQUESTED_BEFORE, true).apply()
        _permissionStatus.value = if (isGranted) {
            VoicePermissionStatus.GRANTED
        } else {
            VoicePermissionStatus.DENIED
        }
    }

    /**
     * Checks if the device has actual microphone hardware.
     */
    fun isMicrophoneHardwareAvailable(): Boolean {
        return context.packageManager.hasSystemFeature(PackageManager.FEATURE_MICROPHONE)
    }

    /**
     * Checks if SpeechRecognizer service is available on this device.
     */
    fun isSpeechRecognitionAvailable(): Boolean {
        return SpeechRecognizer.isRecognitionAvailable(context)
    }

    /**
     * Toggle voice gaming mode in app settings.
     */
    fun setVoiceGamingEnabled(enabled: Boolean) {
        _isVoiceGamingEnabled.value = enabled
        prefs.edit().putBoolean(KEY_VOICE_GAMING_ENABLED, enabled).apply()
    }

    /**
     * Toggle audio feedback beep when listening starts/stops.
     */
    fun setVoiceSoundFeedback(enabled: Boolean) {
        _voiceSoundFeedbackEnabled.value = enabled
        prefs.edit().putBoolean(KEY_SOUND_FEEDBACK, enabled).apply()
    }

    /**
     * Helper Intent to open the App's system permission settings.
     */
    fun createAppSettingsIntent(): Intent {
        return Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
    }

    /**
     * Explanatory text in Arabic for why microphone is required.
     */
    fun getPermissionRationale(): String {
        return "صلاحية الميكروفون مطلوبة لتفعيل الألعاب الصوتية التفاعلية: التابو العربي، تخمين الكلمات المنطوقة، وتحدي الـ 5 ثواني مع التحكيم الذكي بالذكاء الاصطناعي."
    }

    companion object {
        private const val PREFS_NAME = "voice_permission_prefs"
        private const val KEY_PERMISSION_REQUESTED_BEFORE = "key_perm_requested"
        private const val KEY_VOICE_GAMING_ENABLED = "key_voice_gaming_enabled"
        private const val KEY_SOUND_FEEDBACK = "key_sound_feedback"
    }
}
