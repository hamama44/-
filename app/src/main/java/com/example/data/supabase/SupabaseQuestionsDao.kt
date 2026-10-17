package com.example.data.supabase

import com.example.data.Difficulty
import com.example.data.PvPQuestion
import com.example.data.SabahoQuestion
import com.example.data.ThirtySecondsQuestion
import com.example.data.TrueFalseQuestion
import java.security.MessageDigest

/**
 * قراءة الأسئلة المنشورة من `public.questions_feed` وتحويلها لأنواع اللعبة.
 *
 * ليه التطبيق بيحقّق تاني رغم إن البوت بيعمل نفس التدقيق؟
 * لأن الجدول ممكن يتكتب منه من مصدر تاني (لوحة تحكم، يدوي من SQL، بوت قديم)
 * والتطبيق لازم ما يقعش ولا يعرض سؤال مكسور. فأي سؤال مش صالح بيتخطى
 * بهدوء والباقي يكمل، بدل شاشة فاضية أو إجابة غلط.
 *
 * [normalizeForDedup] و[contentHash] لازم يفضلوا مطابقين لـ `normalize()`
 * و`content_hash()` في `scripts/question_bot.py` — التطابق بينغلب باختبار
 * القيم المرجعية في `SupabaseQuestionsDaoTest`. لو اتغيروا في لغة، غيّروهم
 * في التانية في نفس الـcommit.
 */
class SupabaseQuestionsDao(private val api: SupabaseApi) {

    /**
     * جلب أسئلة نوع لعبة معين، الأحدث أولاً.
     *
     * @return `null` عند فشل الاتصال، و`emptyList()` لو الجدول فاضي فعلاً.
     *  التفريق ده مش ترف: المُزامِن بيقرر بموجبه يحذف الكاش القديم أو يسيبه —
     *  لو رجّعنا قائمة فاضية على فشل شبكة كان الكاش السليم اتمسح وألعاب
     *  اللاعب رجعت للمحتوى المضمّن في APK بس.
     *  نفس اصطلاح بقية طبقة البيانات في المشروع.
     */
    suspend fun fetchFeed(gameType: String, limit: Int = DEFAULT_LIMIT): List<SupabaseQuestion>? {
        val rows = api.selectList(
            table = VIEW_FEED,
            params = listOf(
                "select" to "*",
                "game_type" to "eq.$gameType",
                "order" to "published_at.desc",
                "limit" to limit.toString()
            ),
            clazz = SupabaseQuestion::class.java
        ) ?: return null

        return rows.filter { isPlayable(it, gameType) }
    }

    /**
     * جلب كل الأنواع في تشغيل واحد.
     * @return النوع → صفوفه، أو `null` للنوع اللي فشل جلبه.
     */
    suspend fun fetchAll(
        gameTypeToLimit: Map<String, Int> = DEFAULT_TYPES
    ): Map<String, List<SupabaseQuestion>?> {
        val result = LinkedHashMap<String, List<SupabaseQuestion>?>()
        gameTypeToLimit.forEach { (type, limit) ->
            result[type] = fetchFeed(type, limit)
        }
        return result
    }

    val isConfigured: Boolean get() = api.isConfigured

    companion object {
        /** الـview اللي بيفلت `status = 'published'` على مستوى القاعدة. */
        const val VIEW_FEED = "questions_feed"

        /** افتراضي لكل نوع — كفاية جولة كاملة من غير ما نفخّ الـpayload. */
        /**
         * أقصى عدد سؤال بيقراه التطبيق لكل نوع في المزامنة.
         *
         * 90 مش رقم عشوائي: البوت منشّر 250 سؤال/يوم على 4 أنواع ⇒
         * ~62 سؤال جديد لكل نوع كل يوم. الحد 60 كان يعني إن اللاعب يفوّت
         * جزء من يوم النشر كله. 90 = يوم ونص من المحتوى الجديد، ولسه
         * استعلام خفيف (صف واحد = ~1KB).
         */
        const val DEFAULT_LIMIT = 90

        /** الأنواع اللي ليها شاشة في التطبيق. */
        val DEFAULT_TYPES: Map<String, Int> = mapOf(
            GAME_THIRTY_SECONDS to DEFAULT_LIMIT,
            GAME_SABAHO to DEFAULT_LIMIT,
            GAME_TRUE_FALSE to DEFAULT_LIMIT,
            GAME_PVP to 20
        )

        const val GAME_THIRTY_SECONDS = "thirty_seconds"
        const val GAME_SABAHO = "sabaho"
        const val GAME_TRUE_FALSE = "true_false"
        const val GAME_PVP = "pvp"

        /** أدنى طول سؤال مقبول — لازم يطابق MIN_QUESTION_LEN في البوت. */
        const val MIN_QUESTION_LEN = 14
        const val MAX_QUESTION_LEN = 180
        const val MIN_OPTIONS = 2
        const val MAX_OPTIONS = 5

        // ------------------------------------------------------------------
        // التحقق — نفس قواعد البوت
        // ------------------------------------------------------------------

        /** هل الصف صالح للعب في النوع ده؟ */
        fun isPlayable(row: SupabaseQuestion, expectedGameType: String): Boolean {
            if (row.gameType != expectedGameType) return false
            val question = row.question.trim()
            if (question.length < MIN_QUESTION_LEN || question.length > MAX_QUESTION_LEN) return false
            if (row.options.size > MAX_OPTIONS) return false
            if (expectedGameType == GAME_TRUE_FALSE) {
                // صح/غلط بيكفي إن الإجابة تكون صفر أو واحد
                return row.correctIndex == 0 || row.correctIndex == 1
            }
            if (row.options.size < MIN_OPTIONS) return false
            if (row.correctIndex < 0 || row.correctIndex >= row.options.size) return false
            // مفيش خيار فاضي وإلا اللعبة هترسم زرار فاضي
            if (row.options.any { it.isBlank() }) return false
            return true
        }

        // ------------------------------------------------------------------
        // التحويل لنماذج اللعبة
        // ------------------------------------------------------------------

        fun toThirtySeconds(row: SupabaseQuestion): ThirtySecondsQuestion = ThirtySecondsQuestion(
            id = row.id,
            question = row.question.trim(),
            options = row.options.map { it.trim() },
            correctIndex = row.correctIndex.coerceIn(0, (row.options.size - 1).coerceAtLeast(0)),
            category = row.category?.takeIf { it.isNotBlank() } ?: "عامة"
        )

        fun toSabaho(row: SupabaseQuestion): SabahoQuestion = SabahoQuestion(
            id = row.id,
            difficulty = parseDifficulty(row.difficulty),
            question = row.question.trim(),
            options = row.options.map { it.trim() },
            correctIndex = row.correctIndex.coerceIn(0, (row.options.size - 1).coerceAtLeast(0)),
            explanation = row.explanation?.trim().orEmpty()
        )

        /**
         * تحويل لصح/غلط. دلالة `correct_index` هنا **رقم الخيار** لا منطق:
         * 0 ⇒ «صح»، أي رقم تاني ⇒ «غلط» — مطابق لسلوك البوت بعد التصحيح.
         */
        fun toTrueFalse(row: SupabaseQuestion): TrueFalseQuestion = TrueFalseQuestion(
            id = row.id,
            statement = row.question.trim(),
            isTrue = row.correctIndex == 0,
            explanation = row.explanation?.trim().orEmpty(),
            category = row.category?.takeIf { it.isNotBlank() } ?: "عامة"
        )

        fun toPvp(row: SupabaseQuestion): PvPQuestion = PvPQuestion(
            id = row.id,
            question = row.question.trim(),
            options = row.options.map { it.trim() },
            correctIndex = row.correctIndex.coerceIn(0, (row.options.size - 1).coerceAtLeast(0))
        )

        /** تحويل نص الصعوبة للـenum؛ أي قيمة مجهولة ترجع MEDIUM مش انفجار. */
        fun parseDifficulty(raw: String?): Difficulty = when (raw?.trim()?.lowercase()) {
            "easy" -> Difficulty.EASY
            "hard" -> Difficulty.HARD
            else -> Difficulty.MEDIUM
        }

        /** عكسها — للكتابه وللمقارنة مع قيم البوت. */
        fun difficultyToWire(difficulty: Difficulty): String = when (difficulty) {
            Difficulty.EASY -> "easy"
            Difficulty.MEDIUM -> "medium"
            Difficulty.HARD -> "hard"
        }

        // ------------------------------------------------------------------
        // التطبيع والبصمة — لازم تطابق Python
        // ------------------------------------------------------------------

        /**
         * توحيد النص العربي للمقارنة وإزالة التكرار.
         *
         * بيشيل التشكيل، يوحّد الهمزات/الياء/التاء المربوطة، يوحّد الأرقام
         * العربية مع اللاتينية، يشيل الترقيم، ويقلّم المسافات.
         */
        fun normalizeForDedup(text: String): String {
            if (text.isBlank()) return ""
            val noDiacritics = buildString(text.length) {
                for (ch in text) {
                    val code = ch.code
                    // \u064B-\u0652 حركات و\u0670 superscript alef و\u0640 tatweel
                    if (code in 0x064B..0x0652 || code == 0x0670 || code == 0x0640) continue
                    append(ch)
                }
            }
            val unified = buildString(noDiacritics.length) {
                for (ch in noDiacritics) {
                    when (ch) {
                        'أ', 'إ', 'آ', 'ٱ' -> append('ا')
                        'ى', 'ئ' -> append('ي')
                        'ؤ' -> append('و')
                        'ة' -> append('ه')
                        'ک' -> append('ك')
                        '،', '٫' -> append(' ')
                        // نفس خريطة البوت حرفياً: الفاصلة العشرية العربية → فاصلة لاتينية
                        '٬' -> append(',')
                        '٠' -> append('0'); '١' -> append('1'); '٢' -> append('2')
                        '٣' -> append('3'); '٤' -> append('4'); '٥' -> append('5')
                        '٦' -> append('6'); '٧' -> append('7'); '٨' -> append('8')
                        '٩' -> append('9')
                        '۰' -> append('0'); '۱' -> append('1'); '۲' -> append('2')
                        '۳' -> append('3'); '۴' -> append('4'); '۵' -> append('5')
                        '۶' -> append('6'); '۷' -> append('7'); '۸' -> append('8')
                        '۹' -> append('9')
                        else -> append(ch)
                    }
                }
            }
            // نفس مجموعة الترقيم اللي بشيلها البوت (بما فيها الشرطة والشرطة الطويلة)
            val cleaned = unified.map { if (PUNCTUATION.contains(it)) ' ' else it }
                .joinToString("")
                .lowercase()
            return collapseSpaces(cleaned)
        }

        private const val PUNCTUATION = "،؛:.！？\"'“”‘’()-–—_/\\،٫"

        /** تجميع المسافات المتعددة في واحدة وقصّ الأطراف. */
        private fun collapseSpaces(text: String): String =
            text.trim().split(' ').filter { it.isNotBlank() }.joinToString(" ")

        /**
         * بصمة sha256 على `game_type|نص السؤال|نص الإجابة` موحّدين.
         *
         * مطابقة تماماً لـ content_hash() في البوت، فالبصمة المحفوظة في
         * القاعدة تُستخدم لكشف المكرر محلياً بدون ما نعيد حسابها بطريقة تانية.
         */
        fun contentHash(gameType: String, question: String, answer: String): String {
            val payload = listOf(
                gameType.trim().lowercase(),
                normalizeForDedup(question),
                normalizeForDedup(answer)
            ).joinToString("|")
            val digest = MessageDigest.getInstance("SHA-256")
                .digest(payload.toByteArray(Charsets.UTF_8))
            // لازم نهتمش بالإشارة: "%02x".format(byte) في كوتلن بيوسّع
            // الإشارة للـByte السالب ويطبع 8 خانات، فالبصمة ماتطابقش Python.
            return digest.joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
        }

        /** النص الصحيح للسؤال — بنفس طريقة البوت (للـhash ولمنع التكرار). */
        fun answerText(row: SupabaseQuestion): String = when (row.gameType) {
            GAME_TRUE_FALSE -> if (row.correctIndex == 0) "صح" else "غلط"
            else -> row.options.getOrNull(row.correctIndex).orEmpty()
        }
    }
}
