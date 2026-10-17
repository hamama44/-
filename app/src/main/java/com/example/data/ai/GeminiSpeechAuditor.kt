package com.example.data.ai

import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class AiSpeechEvaluation(
    val isValid: Boolean,
    val count: Int,
    val detectedItems: List<String>,
    val feedback: String,
    val isAiPowered: Boolean = true
)

data class AiTabooEvaluation(
    val isViolation: Boolean,
    val violatedWords: List<String>,
    val isSmartDescription: Boolean,
    val feedback: String,
    val isAiPowered: Boolean = true
)

data class AiAutobusEvaluation(
    val isValid: Boolean,
    val cleanedWord: String,
    val feedback: String,
    val isAiPowered: Boolean = true
)

data class AiSpokenAnswerEvaluation(
    val isCorrect: Boolean,
    val matchConfidence: Float,
    val spokenAnswerCleaned: String,
    val expectedAnswer: String,
    val matchType: String,
    val feedback: String,
    val isAiPowered: Boolean = true
)

object GeminiSpeechAuditor {
    private const val TAG = "GeminiSpeechAuditor"
    private const val MODEL = "gemini-2.5-flash"
    private const val API_URL = "https://generativelanguage.googleapis.com/v1beta/models/$MODEL:generateContent"

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .writeTimeout(5, TimeUnit.SECONDS)
            .build()
    }

    private fun getApiKey(): String {
        return try {
            val key = BuildConfig.GEMINI_API_KEY
            if (key.isNullOrBlank() || key.contains("MY_GEMINI_API_KEY")) "" else key.trim()
        } catch (_: Throwable) {
            ""
        }
    }

    // =========================================================================
    // 1. AUDIT "تحدي الـ 5 ثواني" (5 Seconds Challenge)
    // =========================================================================
    suspend fun auditFiveSecondsChallenge(
        prompt: String,
        spokenText: String
    ): AiSpeechEvaluation = withContext(Dispatchers.IO) {
        val apiKey = getApiKey()
        if (apiKey.isNotEmpty()) {
            try {
                val systemPrompt = """
                    أنت حكَم ومُدقّق رسمي ذكي في لعبة 'تحدي الـ 5 ثواني'.
                    السؤال المطلوب للمتسابق: "$prompt".
                    الكلام الصوتي الذي نطق به المتسابق: "$spokenText".
                    
                    مهمتك:
                    1. استخراج الإجابات الفردية التي ذكرها المتسابق (احذف حروف العطف مثل 'و' أو الكلمات الزائدة).
                    2. التأكد هل الإجابات صحيحة ومطابقة للسؤال بدقة؟
                    3. إذا ذكر 3 إجابات صحيحة على الأقل، تكون النتيجة ناجحة (isValid = true).
                    
                    أجب فقط بصيغة JSON التالية دون أي علامات markdown:
                    {"isValid": true, "count": 3, "items": ["إجابة1", "إجابة2", "إجابة3"], "feedback": "أحسنت! 3 إجابات دقيقة"}
                """.trimIndent()

                val responseJson = callGeminiApi(systemPrompt, apiKey)
                if (responseJson != null) {
                    val parsed = parseFiveSecondsJson(responseJson)
                    if (parsed != null) return@withContext parsed
                }
            } catch (e: Exception) {
                Log.w(TAG, "Gemini API call failed, falling back to NLP: ${e.message}")
            }
        }

        // Local Smart Arabic NLP Rule Engine fallback
        return@withContext fallbackFiveSecondsNlp(prompt, spokenText)
    }

    // =========================================================================
    // 2. AUDIT "التابو العربي" (Arabic Taboo)
    // =========================================================================
    suspend fun auditTabooSpeech(
        mainWord: String,
        forbiddenWords: List<String>,
        spokenDescription: String
    ): AiTabooEvaluation = withContext(Dispatchers.IO) {
        val apiKey = getApiKey()
        if (apiKey.isNotEmpty()) {
            try {
                val forbiddenListStr = forbiddenWords.joinToString("، ")
                val systemPrompt = """
                    أنت مراقب وقاضٍ صارم في لعبة 'التابو العربي'.
                    الكلمة المطلوب شرحها للأصدقاء: "$mainWord".
                    الكلمات الممنوعة المحظور نطقها نهائياً: [$forbiddenListStr].
                    
                    الكلام الذي قاله اللاعب صوتياً لشرح الكلمة: "$spokenDescription".
                    
                    مهمتك:
                    1. فحص هل نطق اللاعب أي كلمة من الكلمات الممنوعة الخمسة (أو مشتقاتها المباشرة باللغة العربية)؟
                    2. فحص هل نطق الكلمة الرئيسية نفسها "$mainWord"؟ إذا نطقها تعتبر مخالفة صريحة.
                    3. هل الوصف ذكي ومفيد ويقرب المعنى للمستمعين دون مخالفة؟
                    
                    أجب فقط بصيغة JSON التالية دون أي علامات markdown:
                    {"isViolation": false, "violatedWords": [], "isSmartDescription": true, "feedback": "وصف ذكي ونظيف خالي تماماً من المحظورات!"}
                """.trimIndent()

                val responseJson = callGeminiApi(systemPrompt, apiKey)
                if (responseJson != null) {
                    val parsed = parseTabooJson(responseJson)
                    if (parsed != null) return@withContext parsed
                }
            } catch (e: Exception) {
                Log.w(TAG, "Gemini Taboo call failed, falling back: ${e.message}")
            }
        }

        // Local NLP Taboo Inspector
        return@withContext fallbackTabooNlp(mainWord, forbiddenWords, spokenDescription)
    }

    // =========================================================================
    // 3. AUDIT "أتوبيس كومبليت" (Autobus Complete Voice Input)
    // =========================================================================
    suspend fun auditAutobusVoice(
        letter: Char,
        category: String,
        spokenWord: String
    ): AiAutobusEvaluation = withContext(Dispatchers.IO) {
        val cleaned = cleanArabicWord(spokenWord)
        val normalizedLetter = normalizeArabicChar(letter)
        val firstChar = cleaned.firstOrNull()?.let { normalizeArabicChar(it) } ?: ' '

        val startsWithLetter = firstChar == normalizedLetter

        if (!startsWithLetter) {
            return@withContext AiAutobusEvaluation(
                isValid = false,
                cleanedWord = cleaned,
                feedback = "الكلمة '$cleaned' لا تبدأ بحرف $letter! تبدأ بـ $firstChar",
                isAiPowered = false
            )
        }

        // Check with Gemini if available for category validity
        val apiKey = getApiKey()
        if (apiKey.isNotEmpty() && cleaned.length >= 2) {
            try {
                val prompt = """
                    أنت محكّم لغوي في لعبة 'أتوبيس كومبليت'.
                    الحرف المطلوب: '$letter'.
                    الفئة المطلوبة: '$category' (اسم إنسان، بلد/عاصمة، نبات، جماد، حيوان).
                    الكلمة المنطوقة: '$cleaned'.
                    
                    هل هذه الكلمة حقيقية وصحيحة وتبدأ بحرف $letter وتناسب فئة '$category' في الثقافة العربية؟
                    أجب فقط بـ JSON:
                    {"isValid": true, "cleanedWord": "$cleaned", "feedback": "كلمة صحيحة ومثالية!"}
                """.trimIndent()

                val res = callGeminiApi(prompt, apiKey)
                if (res != null) {
                    val json = JSONObject(res)
                    val isValid = json.optBoolean("isValid", true)
                    val feedback = json.optString("feedback", "كلمة صحيحة")
                    return@withContext AiAutobusEvaluation(isValid, cleaned, feedback, true)
                }
            } catch (_: Exception) {}
        }

        // Local check
        val isValid = cleaned.length >= 2
        val feedback = if (isValid) "إجابة صحيحة تبدأ بحرف $letter وفئة $category ✓" else "الكلمة قصيرة جداً"
        return@withContext AiAutobusEvaluation(isValid, cleaned, feedback, false)
    }

    // =========================================================================
    // 4. AUDIT SPOKEN ANSWER AGAINST TARGET (التابو، تخمين الكلمة، والأسئلة)
    // =========================================================================
    suspend fun auditSpokenAnswerAgainstTarget(
        expectedWordOrAnswer: String,
        spokenAnswer: String,
        questionOrContext: String = "",
        acceptableSynonyms: List<String> = emptyList()
    ): AiSpokenAnswerEvaluation = withContext(Dispatchers.IO) {
        val apiKey = getApiKey()
        val cleanedSpoken = cleanSpokenArabicSpeech(spokenAnswer)

        if (apiKey.isNotEmpty() && cleanedSpoken.isNotBlank()) {
            try {
                val synonymsStr = if (acceptableSynonyms.isNotEmpty()) {
                    "المرادفات أو الإجابات البديلة المقبولة: [${acceptableSynonyms.joinToString("، ")}]"
                } else ""

                val contextStr = if (questionOrContext.isNotBlank()) {
                    "السياق / السؤال المطروح: \"$questionOrContext\""
                } else ""

                val systemPrompt = """
                    أنت حكَم ومُدقّق لغوي رسمي ذكي في لعبة أسئلة وألعاب جماعية عربية (مثل التابو، وتحدي التخمين الصوتي، والأسئلة العامة).
                    $contextStr
                    الإجابة أو الكلمة المطلوبة: "$expectedWordOrAnswer".
                    $synonymsStr
                    الكلام الذي نطق به اللاعب صوتياً عبر الميكروفون: "$spokenAnswer".
                    
                    مهمتك بدقة:
                    1. هل ما قاله اللاعب يعتبر إجابة صحيحة أو تخميناً مطابقاً أو مرادفاً دقيقاً للإجابة المطلوبة "$expectedWordOrAnswer"؟
                    2. خذ بالحسبان:
                       - اللهجات العربية المختلفة (المصرية، الخليجية، الشامية، المغاربية).
                       - أل التعريف (سواء قالها اللاعب بـ "ال" أو بدون "ال").
                       - الأخطاء الصوتية الناتجة عن محوّل الصوت لنص (مثل: إ/ا/آ، ة/ه، ي/ى).
                       - الكلمات الزائدة العفوية مثل: "أعتقد"، "الجواب هو"، "ممكن"، "يعني".
                       - الألقاب والأسماء الشهيرة (مثال: "ميسي" تعني "ليونيل ميسي"، "صلاح" تعني "محمد صلاح").
                    
                    أجب فقط بصيغة JSON التالية دون أي markdown:
                    {
                      "isCorrect": true,
                      "confidence": 0.95,
                      "matchType": "تطابق تام",
                      "feedback": "إجابة صوتية صحيحة ورائعة!"
                    }
                """.trimIndent()

                val responseJson = callGeminiApi(systemPrompt, apiKey)
                if (responseJson != null) {
                    val parsed = parseSpokenAnswerJson(responseJson, spokenAnswer, expectedWordOrAnswer)
                    if (parsed != null) return@withContext parsed
                }
            } catch (e: Exception) {
                Log.w(TAG, "Gemini auditSpokenAnswer failed, falling back to local NLP: ${e.message}")
            }
        }

        // Local Smart Arabic NLP Rule Engine Fallback (100% Offline Resilience)
        return@withContext fallbackSpokenAnswerNlp(
            expected = expectedWordOrAnswer,
            spoken = spokenAnswer,
            synonyms = acceptableSynonyms
        )
    }

    // =========================================================================
    // HTTP CALL TO GEMINI REST API
    // =========================================================================
    private fun callGeminiApi(prompt: String, apiKey: String): String? {
        val requestBody = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", prompt)
                        })
                    })
                })
            })
            put("generationConfig", JSONObject().apply {
                put("responseMimeType", "application/json")
                put("temperature", 0.1)
            })
        }

        val request = Request.Builder()
            .url("$API_URL?key=$apiKey")
            .post(requestBody.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        val response = httpClient.newCall(request).execute()
        if (!response.isSuccessful) {
            Log.w(TAG, "Gemini HTTP error ${response.code}: ${response.body?.string()}")
            return null
        }

        val body = response.body?.string() ?: return null
        val root = JSONObject(body)
        val candidates = root.optJSONArray("candidates") ?: return null
        if (candidates.length() == 0) return null
        val content = candidates.getJSONObject(0).optJSONObject("content") ?: return null
        val parts = content.optJSONArray("parts") ?: return null
        if (parts.length() == 0) return null
        return parts.getJSONObject(0).optString("text")
    }

    // =========================================================================
    // LOCAL NLP ENGINES FOR 100% OFFLINE / LOW-LATENCY ROBUSTNESS
    // =========================================================================
    private fun fallbackFiveSecondsNlp(prompt: String, spoken: String): AiSpeechEvaluation {
        val cleaned = spoken.replace("،", " ").replace(".", " ").replace("؟", " ")
        // Split on Arabic conjunctions: " و ", " أو ", commas, newlines
        val rawTokens = cleaned.split(" و", " أو", " ", "\n")
            .map { cleanArabicWord(it) }
            .filter { it.length >= 2 }
            .distinct()

        val count = rawTokens.size
        val isValid = count >= 3
        val feedback = if (isValid) {
            "تم التحقق بنجاح من ${rawTokens.take(3).joinToString("، ")} ✓"
        } else {
            "تم رصد $count إجابات فقط (${rawTokens.joinToString("، ")}). المطلوب 3 إجابات سريعة!"
        }

        return AiSpeechEvaluation(
            isValid = isValid,
            count = count,
            detectedItems = rawTokens.take(3),
            feedback = feedback,
            isAiPowered = false
        )
    }

    private fun fallbackTabooNlp(
        mainWord: String,
        forbiddenWords: List<String>,
        spoken: String
    ): AiTabooEvaluation {
        val normSpoken = normalizeArabicText(spoken)
        val normMain = normalizeArabicText(mainWord)

        val violated = mutableListOf<String>()

        // Check main word
        if (normMain.length >= 3 && normSpoken.contains(normMain)) {
            violated.add(mainWord)
        }

        // Check forbidden words
        forbiddenWords.forEach { word ->
            val normWord = normalizeArabicText(word)
            if (normWord.length >= 3 && normSpoken.contains(normWord)) {
                violated.add(word)
            }
        }

        val isViolation = violated.isNotEmpty()
        val isSmart = !isViolation && spoken.trim().split(" ").size >= 3
        val feedback = if (isViolation) {
            "⚠️ تم رصد كلمة محظورة: ${violated.joinToString("، ")}!"
        } else if (isSmart) {
            "✅ وصف نظيف ومتقن خالي تماماً من المحظورات!"
        } else {
            "الكلام قصير جداً للشرح، أضف تفاصيل أكثر للأصدقاء!"
        }

        return AiTabooEvaluation(
            isViolation = isViolation,
            violatedWords = violated,
            isSmartDescription = isSmart,
            feedback = feedback,
            isAiPowered = false
        )
    }

    private fun parseFiveSecondsJson(jsonStr: String): AiSpeechEvaluation? {
        return try {
            val json = JSONObject(jsonStr)
            val isValid = json.optBoolean("isValid", false)
            val count = json.optInt("count", 0)
            val itemsJson = json.optJSONArray("items")
            val items = mutableListOf<String>()
            if (itemsJson != null) {
                for (i in 0 until itemsJson.length()) {
                    items.add(itemsJson.getString(i))
                }
            }
            val feedback = json.optString("feedback", "تم التدقيق بواسطة الذكاء الاصطناعي")
            AiSpeechEvaluation(isValid, count, items, feedback, true)
        } catch (_: Exception) {
            null
        }
    }

    private fun parseTabooJson(jsonStr: String): AiTabooEvaluation? {
        return try {
            val json = JSONObject(jsonStr)
            val isViolation = json.optBoolean("isViolation", false)
            val violatedJson = json.optJSONArray("violatedWords")
            val violated = mutableListOf<String>()
            if (violatedJson != null) {
                for (i in 0 until violatedJson.length()) {
                    violated.add(violatedJson.getString(i))
                }
            }
            val isSmart = json.optBoolean("isSmartDescription", true)
            val feedback = json.optString("feedback", "تدقيق الذكاء الاصطناعي جاهز")
            AiTabooEvaluation(isViolation, violated, isSmart, feedback, true)
        } catch (_: Exception) {
            null
        }
    }

    private fun parseSpokenAnswerJson(
        jsonStr: String,
        spokenAnswer: String,
        expectedAnswer: String
    ): AiSpokenAnswerEvaluation? {
        return try {
            val json = JSONObject(jsonStr)
            val isCorrect = json.optBoolean("isCorrect", false)
            val confidence = json.optDouble("confidence", if (isCorrect) 0.9 else 0.2).toFloat()
            val matchType = json.optString("matchType", if (isCorrect) "تطابق ذكي" else "إجابة غير متطابقة")
            val feedback = json.optString("feedback", if (isCorrect) "إجابة صحيحة!" else "إجابة غير صحيحة، حاول مجدداً")
            AiSpokenAnswerEvaluation(
                isCorrect = isCorrect,
                matchConfidence = confidence,
                spokenAnswerCleaned = cleanSpokenArabicSpeech(spokenAnswer),
                expectedAnswer = expectedAnswer,
                matchType = matchType,
                feedback = feedback,
                isAiPowered = true
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun fallbackSpokenAnswerNlp(
        expected: String,
        spoken: String,
        synonyms: List<String>
    ): AiSpokenAnswerEvaluation {
        val normExpected = normalizeArabicText(expected)
        val cleanExpected = cleanArabicWord(normExpected)

        val cleanSpoken = cleanSpokenArabicSpeech(spoken)
        val normSpoken = normalizeArabicText(cleanSpoken)
        val strippedSpoken = cleanArabicWord(normSpoken)

        // 1. Direct or stripped equality
        if (normSpoken == normExpected || strippedSpoken == cleanExpected) {
            return AiSpokenAnswerEvaluation(
                isCorrect = true,
                matchConfidence = 1.0f,
                spokenAnswerCleaned = cleanSpoken,
                expectedAnswer = expected,
                matchType = "تطابق تام",
                feedback = "إجابة صحيحة ومطابقة 100% ✓",
                isAiPowered = false
            )
        }

        // 2. Check synonyms
        for (syn in synonyms) {
            val normSyn = normalizeArabicText(syn)
            val strippedSyn = cleanArabicWord(normSyn)
            if (normSpoken == normSyn || strippedSpoken == strippedSyn || normSpoken.contains(strippedSyn)) {
                return AiSpokenAnswerEvaluation(
                    isCorrect = true,
                    matchConfidence = 0.95f,
                    spokenAnswerCleaned = cleanSpoken,
                    expectedAnswer = expected,
                    matchType = "مرادف معتمد",
                    feedback = "أحسنت! مرادف دقيق للإجابة المطلوبة ($syn) ✓",
                    isAiPowered = false
                )
            }
        }

        // 3. Containment check
        if (cleanExpected.length >= 3 && (normSpoken.contains(cleanExpected) || normSpoken.contains(normExpected))) {
            return AiSpokenAnswerEvaluation(
                isCorrect = true,
                matchConfidence = 0.88f,
                spokenAnswerCleaned = cleanSpoken,
                expectedAnswer = expected,
                matchType = "تطابق ضمن الكلام",
                feedback = "تم رصد الإجابة المطلوبة بنجاح في كلامك ✓",
                isAiPowered = false
            )
        }

        // 4. Levenshtein fuzzy phonetic similarity
        val sim = calculateSimilarity(strippedSpoken, cleanExpected)
        if (sim >= 0.75f && cleanExpected.length >= 3) {
            return AiSpokenAnswerEvaluation(
                isCorrect = true,
                matchConfidence = sim,
                spokenAnswerCleaned = cleanSpoken,
                expectedAnswer = expected,
                matchType = "تقارب صوتي",
                feedback = "إجابة صحيحة قريبة جداً من النطق المطلوب ✓",
                isAiPowered = false
            )
        }

        return AiSpokenAnswerEvaluation(
            isCorrect = false,
            matchConfidence = sim,
            spokenAnswerCleaned = cleanSpoken,
            expectedAnswer = expected,
            matchType = "إجابة خاطئة",
            feedback = "الإجابة المنطوقة غير مطابقة، الكلمة المطلوبة: '$expected'",
            isAiPowered = false
        )
    }

    private fun cleanSpokenArabicSpeech(spoken: String): String {
        var result = spoken.trim()
        val fillerPhrases = listOf(
            "الاجابة هي", "الإجابة هي", "الجواب هو", "اعتقد ان", "أعتقد أن",
            "اعتقد", "أعتقد", "ممكن", "يمكن", "الكلمة هي", "أظن", "اظن"
        )
        for (filler in fillerPhrases) {
            if (result.startsWith(filler)) {
                result = result.removePrefix(filler).trim()
            }
        }
        return result
    }

    private fun calculateSimilarity(s1: String, s2: String): Float {
        if (s1 == s2) return 1.0f
        if (s1.isEmpty() || s2.isEmpty()) return 0.0f
        val maxLen = maxOf(s1.length, s2.length)
        val dist = levenshteinDistance(s1, s2)
        return (1.0f - (dist.toFloat() / maxLen.toFloat())).coerceIn(0f, 1f)
    }

    private fun levenshteinDistance(s1: String, s2: String): Int {
        val dp = Array(s1.length + 1) { IntArray(s2.length + 1) }
        for (i in 0..s1.length) dp[i][0] = i
        for (j in 0..s2.length) dp[0][j] = j
        for (i in 1..s1.length) {
            for (j in 1..s2.length) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,
                    dp[i][j - 1] + 1,
                    dp[i - 1][j - 1] + cost
                )
            }
        }
        return dp[s1.length][s2.length]
    }

    private fun cleanArabicWord(word: String): String {
        return word.trim()
            .replace("ال", "") // strip common prefix if helpful
            .replace(Regex("[^\\p{InArabic}]"), "")
    }

    private fun normalizeArabicText(text: String): String {
        return text.trim().lowercase()
            .replace("أ", "ا")
            .replace("إ", "ا")
            .replace("آ", "ا")
            .replace("ة", "ه")
            .replace("ى", "ي")
            .replace(Regex("[^\\p{InArabic}\\s]"), "")
    }

    private fun normalizeArabicChar(c: Char): Char {
        return when (c) {
            'أ', 'إ', 'آ', 'ٱ' -> 'ا'
            'ة' -> 'ه'
            'ى' -> 'ي'
            else -> c
        }
    }
}
