package com.example.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.example.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * High-performance low-latency game sound effects engine using Android SoundPool
 * with synchronized haptic feedback and ToneGenerator fallback.
 */
class StadiumAudioEffects(private val context: Context) {
    private var soundPool: SoundPool? = null
    private var toneGenerator: ToneGenerator? = null

    private var soundCorrectId: Int = 0
    private var soundWrongId: Int = 0
    private var soundWhistleId: Int = 0
    private var soundTickId: Int = 0
    private var soundGoalId: Int = 0

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        vibratorManager?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    init {
        initSoundPool()
        initToneFallback()
    }

    private fun initSoundPool() {
        try {
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            soundPool = SoundPool.Builder()
                .setMaxStreams(5)
                .setAudioAttributes(audioAttributes)
                .build()

            soundPool?.let { pool ->
                soundCorrectId = pool.load(context, R.raw.sound_correct, 1)
                soundWrongId = pool.load(context, R.raw.sound_wrong, 1)
                soundWhistleId = pool.load(context, R.raw.sound_whistle, 1)
                soundTickId = pool.load(context, R.raw.sound_tick, 1)
                soundGoalId = pool.load(context, R.raw.sound_goal, 1)
            }
        } catch (_: Exception) {}
    }

    private fun initToneFallback() {
        try {
            toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 85)
        } catch (_: Exception) {}
    }

    fun playCorrect() {
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val played = soundPool?.play(soundCorrectId, 1f, 1f, 1, 0, 1f) ?: 0
                if (played == 0) {
                    toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 120)
                }
                vibrateSuccess()
            } catch (_: Exception) {}
        }
    }

    fun playWrong() {
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val played = soundPool?.play(soundWrongId, 1f, 1f, 1, 0, 1f) ?: 0
                if (played == 0) {
                    toneGenerator?.startTone(ToneGenerator.TONE_CDMA_SOFT_ERROR_LITE, 200)
                }
                vibrateError()
            } catch (_: Exception) {}
        }
    }

    fun playWhistle() {
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val played = soundPool?.play(soundWhistleId, 1f, 1f, 1, 0, 1f) ?: 0
                if (played == 0) {
                    toneGenerator?.startTone(ToneGenerator.TONE_SUP_RINGTONE, 350)
                }
                vibrateMedium()
            } catch (_: Exception) {}
        }
    }

    fun playTick() {
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val played = soundPool?.play(soundTickId, 0.6f, 0.6f, 1, 0, 1f) ?: 0
                if (played == 0) {
                    toneGenerator?.startTone(ToneGenerator.TONE_PROP_PROMPT, 50)
                }
            } catch (_: Exception) {}
        }
    }

    fun playVictory() {
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val played = soundPool?.play(soundGoalId, 1f, 1f, 1, 0, 1f) ?: 0
                if (played == 0) {
                    toneGenerator?.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 400)
                }
                vibrateSuccess()
            } catch (_: Exception) {}
        }
    }

    fun playClick() {
        vibrateLight()
    }

    private fun vibrateSuccess() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 30, 40, 70), -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(80)
            }
        } catch (_: Exception) {}
    }

    private fun vibrateError() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(160, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(160)
            }
        } catch (_: Exception) {}
    }

    private fun vibrateMedium() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(90, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(90)
            }
        } catch (_: Exception) {}
    }

    private fun vibrateLight() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(20, VibrationEffect.EFFECT_TICK))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(20)
            }
        } catch (_: Exception) {}
    }

    fun release() {
        try {
            soundPool?.release()
            soundPool = null
            toneGenerator?.release()
            toneGenerator = null
        } catch (_: Exception) {}
    }
}
