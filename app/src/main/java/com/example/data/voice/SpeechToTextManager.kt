package com.example.data.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * Detailed state representing the lifecycle of Speech-to-Text recognition.
 */
sealed class SpeechState {
    object Idle : SpeechState()
    data class Listening(val soundLevelRmsDb: Float = 0f) : SpeechState()
    object Processing : SpeechState()
    data class Success(
        val text: String,
        val confidence: Float = 1.0f,
        val alternatives: List<String> = emptyList()
    ) : SpeechState()
    data class Error(val message: String, val errorCode: Int) : SpeechState()
}

/**
 * Production-ready Speech-to-Text Manager library for interactive Android games.
 * Handles SpeechRecognizer lifecycle, continuous amplitude/rms callbacks, partial results,
 * and user-friendly Arabic error translations.
 */
class SpeechToTextManager(private val context: Context) {

    private val tag = "SpeechToTextManager"
    private val mainHandler = Handler(Looper.getMainLooper())

    private var speechRecognizer: SpeechRecognizer? = null

    private val _speechState = MutableStateFlow<SpeechState>(SpeechState.Idle)
    val speechState: StateFlow<SpeechState> = _speechState.asStateFlow()

    private val _partialText = MutableStateFlow("")
    val partialText: StateFlow<String> = _partialText.asStateFlow()

    private val _soundLevel = MutableStateFlow(0f)
    val soundLevel: StateFlow<Float> = _soundLevel.asStateFlow()

    private val recognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            Log.d(tag, "onReadyForSpeech")
            _speechState.value = SpeechState.Listening(0f)
            _partialText.value = ""
        }

        override fun onBeginningOfSpeech() {
            Log.d(tag, "onBeginningOfSpeech")
            _speechState.value = SpeechState.Listening(_soundLevel.value)
        }

        override fun onRmsChanged(rmsdB: Float) {
            // Normalize roughly between 0.0 and 1.0 for UI visualizer
            val normalized = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
            _soundLevel.value = normalized
            if (_speechState.value is SpeechState.Listening) {
                _speechState.value = SpeechState.Listening(normalized)
            }
        }

        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {
            Log.d(tag, "onEndOfSpeech")
            _speechState.value = SpeechState.Processing
        }

        override fun onError(error: Int) {
            val errorMsg = mapErrorCodeToArabic(error)
            Log.w(tag, "onError: code=$error msg=$errorMsg")
            _speechState.value = SpeechState.Error(errorMsg, error)
            _soundLevel.value = 0f
        }

        override fun onResults(results: Bundle?) {
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val confidences = results?.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)

            val primaryText = matches?.firstOrNull()?.trim() ?: ""
            val confidence = confidences?.firstOrNull() ?: 0.9f
            val alternatives = matches?.drop(1) ?: emptyList()

            Log.d(tag, "onResults: '$primaryText' confidence=$confidence")

            if (primaryText.isNotBlank()) {
                _speechState.value = SpeechState.Success(
                    text = primaryText,
                    confidence = confidence,
                    alternatives = alternatives
                )
                _partialText.value = primaryText
            } else {
                _speechState.value = SpeechState.Error("لم يتم التقاط أي صوت واضح، حاول مرة أخرى", -1)
            }
            _soundLevel.value = 0f
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val partial = matches?.firstOrNull()?.trim() ?: ""
            if (partial.isNotBlank()) {
                _partialText.value = partial
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    /**
     * Start listening for speech in Arabic. Must be invoked on the UI/Main thread.
     */
    fun startListening(
        language: String = "ar-SA",
        promptText: String = "تحدث الآن في ملعب التحدي 🎙️⚽"
    ) {
        mainHandler.post {
            try {
                // Cancel existing session
                cancelListeningInternal()

                if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                    _speechState.value = SpeechState.Error("خدمة التعرف على الصوت غير متوفرة في النظام", -2)
                    return@post
                }

                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                    setRecognitionListener(recognitionListener)
                }

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, language)
                    putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, language)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                    putExtra(RecognizerIntent.EXTRA_PROMPT, promptText)
                }

                _partialText.value = ""
                _soundLevel.value = 0f
                _speechState.value = SpeechState.Listening(0f)

                speechRecognizer?.startListening(intent)
            } catch (e: Exception) {
                Log.e(tag, "Error starting speech recognition: ${e.message}", e)
                _speechState.value = SpeechState.Error("تعذر بدء الميكروفون: ${e.message}", -3)
            }
        }
    }

    /**
     * Stops listening and finishes recognizing what has been spoken so far.
     */
    fun stopListening() {
        mainHandler.post {
            try {
                speechRecognizer?.stopListening()
                _speechState.value = SpeechState.Processing
            } catch (e: Exception) {
                Log.w(tag, "Error stopping listener: ${e.message}")
            }
        }
    }

    /**
     * Cancels any active recognition session and resets states.
     */
    fun cancelListening() {
        mainHandler.post {
            cancelListeningInternal()
            _speechState.value = SpeechState.Idle
            _partialText.value = ""
            _soundLevel.value = 0f
        }
    }

    /**
     * Manually set a recognized text (e.g. from an ActivityResult Intent or direct input).
     */
    fun submitSpokenText(text: String) {
        val trimmed = text.trim()
        if (trimmed.isNotBlank()) {
            _partialText.value = trimmed
            _speechState.value = SpeechState.Success(trimmed, 1.0f)
        }
    }

    /**
     * Cleans up all resources. Call in onCleared / onDestroy.
     */
    fun destroy() {
        mainHandler.post {
            cancelListeningInternal()
        }
    }

    private fun cancelListeningInternal() {
        try {
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            Log.w(tag, "Error cancelling SpeechRecognizer: ${e.message}")
        } finally {
            speechRecognizer = null
        }
    }

    private fun mapErrorCodeToArabic(errorCode: Int): String {
        return when (errorCode) {
            SpeechRecognizer.ERROR_AUDIO -> "خطأ في تسجيل الصوت، تأكد من سلامة المايك"
            SpeechRecognizer.ERROR_CLIENT -> "خطأ في الاتصال بالخدمة الصوتية"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "صلاحية تسجيل الصوت غير ممنوحة"
            SpeechRecognizer.ERROR_NETWORK -> "خطأ في الشبكة، تحقق من اتصال الإنترنت"
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "انتهت مهلة الاتصال بالشبكة"
            SpeechRecognizer.ERROR_NO_MATCH -> "لم نتمكن من فهم الكلام، تحدث بصوت أعلى وأوضح"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "خدمة الصوت مشغولة، جرب بعد لحظات"
            SpeechRecognizer.ERROR_SERVER -> "خطأ في خادم التعرف الصوتي"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "لم يتم رصد صوت، حاول التحدث فور الضغط على المايك"
            else -> "حدث خطأ غير متوقع أثناء التعرف على الصوت"
        }
    }
}
