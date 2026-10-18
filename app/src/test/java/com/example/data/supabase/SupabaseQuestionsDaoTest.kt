package com.example.data.supabase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * اختبارات طبقة قراءة الأسئلة، وأهمها **اختبار التطابق مع البوت**.
 *
 * القيم المرجعية هنا مش من تأليف الاختبار: طلّعتوها بتشغيل
 * `scripts/question_bot.py` نفسه على نفس المدخلات. ليه مهم؟
 * لأن البوت بيحسب `content_hash` في Python والتطبيق بيعيد حسابها في Kotlin
 * للمقارنة ومنع التكرار — لو الدالتين اختلفت أي سؤال هيتعامل معه على أنه
 * جديد في طبقة ومكرر في التانية، فبنقفل السلوك ده هنا.
 *
 * كل الاختبارات بدون شبكة: بنمرّر [SupabaseApi] بمفاتيح غير صالحة فترجع
 * `null` قبل أي طلب HTTP.
 */
class SupabaseQuestionsDaoTest {

    // =========================================================================
    // التطابق العابر للغات — القيم من question_bot.normalize()/content_hash()
    // =========================================================================

    @Test
    fun `normalizeForDedup يطابق Python في التشكيل والهمزات`() {
        assertEquals("كره القدم", SupabaseQuestionsDao.normalizeForDedup("كُرة القَدَم"))
        assertEquals("ارجنتين", SupabaseQuestionsDao.normalizeForDedup("آرْجنتين"))
        assertEquals("مسافات كثيره", SupabaseQuestionsDao.normalizeForDedup("  مسافات    كثيرة  "))
    }

    @Test
    fun `normalizeForDedup يطابق Python في الترقيم والفاصلة المنقوطة`() {
        assertEquals("فواصل و نقاط", SupabaseQuestionsDao.normalizeForDedup("فواصل، و؛نقاط:"))
        assertEquals("مين كسب", SupabaseQuestionsDao.normalizeForDedup("مين كسب؟"))
    }

    @Test
    fun `normalizeForDedup يطابق Python في توحيد الأرقام العربية`() {
        assertEquals("ارقام 0123456789", SupabaseQuestionsDao.normalizeForDedup("أرقام ٠١٢٣٤٥٦٧٨٩"))
        assertEquals(
            SupabaseQuestionsDao.normalizeForDedup("كأس العالم 2022"),
            SupabaseQuestionsDao.normalizeForDedup("كأس العالم ٢٠٢٢")
        )
    }

    @Test
    fun `contentHash يطابق البوت حرفياً`() {
        // المراجع من: python3 -c "import question_bot as q; print(q.content_hash(...))"
        assertEquals(
            "f8377289581414c76ec4a663c7137f27607e7dcb054aa83333abb7aa7312bbd1",
            SupabaseQuestionsDao.contentHash(
                "thirty_seconds",
                "مين كسب كأس العالم ٢٠٢٢ في قطر؟",
                "الأرجنتين"
            )
        )
        assertEquals(
            "ed7558332df5bc53b397cf2fec1b5812a4b8d10af8077c7fa4c63119a4c6d18b",
            SupabaseQuestionsDao.contentHash(
                "true_false",
                "كأس العالم بيتلعب كل أربع سنين.",
                "صح"
            )
        )
        assertEquals(
            "65dd541ad294c7fcfac08d64c1ee2cd97000fe09579b7d4eb8906cd9c40dd7d7",
            SupabaseQuestionsDao.contentHash(
                "pvp",
                "أنهي نادٍ كسب التشامبيونزليج 15 مرة؟",
                "ريال مدريد"
            )
        )
        assertEquals(
            "aed053b12e79db9964f366f2899a9146e136f50f9efc0326002d6c0dd4e4c7a1",
            SupabaseQuestionsDao.contentHash("sabaho", "Hello, World? (test) — 1,2", "None")
        )
    }

    @Test
    fun `البصمة ما تتأثرش بالتشكيل والترقيم والأرقام العربية`() {
        val a = SupabaseQuestionsDao.contentHash(
            "thirty_seconds", "مين كسب كأس العالم ٢٠٢٢ في قطر؟", "الأرجنتين"
        )
        val b = SupabaseQuestionsDao.contentHash(
            "thirty_seconds", "مَيْن كَسَبَ كَأْس العَالَم 2022 في قطر", "الارجنتين"
        )
        assertEquals("نفس السؤال بصياغة منقّحة = نفس البصمة", a, b)
    }

    @Test
    fun `البصمة حساسة للنوع والإجابة`() {
        val base = SupabaseQuestionsDao.contentHash("thirty_seconds", "سؤال كافي الطول هنا", "جواب")
        assertNotEqual(base, SupabaseQuestionsDao.contentHash("pvp", "سؤال كافي الطول هنا", "جواب"))
        assertNotEqual(base, SupabaseQuestionsDao.contentHash("thirty_seconds", "سؤال كافي الطول هنا", "جواب تاني"))
    }

    @Test
    fun `contentHash دايماً 60 خانة هكساي صغيرة`() {
        val hash = SupabaseQuestionsDao.contentHash("pvp", "أي سؤال", "أي جواب")

        assertEquals(64, hash.length)
        assertEquals(hash.lowercase(), hash)
        assertTrue(hash.all { it in "0123456789abcdef" })
    }

    private fun assertNotEqual(expected: String, actual: String) {
        assertFalse("القيمة نفسها اتكررت: $expected", expected == actual)
    }

    // =========================================================================
    // التحقق من صلاحية السؤال للعب
    // =========================================================================

    private fun row(
        gameType: String = "thirty_seconds",
        question: String = "مين كسب كأس العالم ٢٠٢٢ في قطر؟",
        options: List<String> = listOf("الأرجنتين", "فرنسا", "البرازيل"),
        correctIndex: Int = 0,
        difficulty: String? = "medium",
        category: String? = "كأس العالم",
        explanation: String? = "فازت الأرجنتين بركلات الترجيح."
    ) = SupabaseQuestion(
        id = "q_test_1",
        gameType = gameType,
        question = question,
        options = options,
        correctIndex = correctIndex,
        difficulty = difficulty,
        category = category,
        explanation = explanation,
        source = "bot",
        publishedAt = "2026-10-18T09:00:00+00:00"
    )

    @Test
    fun `isPlayable يقبل السؤال السليم`() {
        assertTrue(SupabaseQuestionsDao.isPlayable(row(), "thirty_seconds"))
        assertTrue(SupabaseQuestionsDao.isPlayable(row(gameType = "sabaho"), "sabaho"))
        assertTrue(SupabaseQuestionsDao.isPlayable(row(gameType = "pvp"), "pvp"))
    }

    @Test
    fun `isPlayable يرفض نوع مختلف عن المطلوب`() {
        // سؤال sabaho مخزّن غلط تحت thirty_seconds ⇒ نرفضه مش نرسمه في لعبة غلط
        assertFalse(SupabaseQuestionsDao.isPlayable(row(gameType = "sabaho"), "thirty_seconds"))
    }

    @Test
    fun `isPlayable يرفض correct_index خارج حدود الخيارات`() {
        assertFalse(SupabaseQuestionsDao.isPlayable(row(correctIndex = 7), "thirty_seconds"))
        assertFalse(SupabaseQuestionsDao.isPlayable(row(correctIndex = -1), "thirty_seconds"))
        assertFalse(SupabaseQuestionsDao.isPlayable(row(correctIndex = 3), "thirty_seconds"))
        assertTrue("آخر خيار صالح", SupabaseQuestionsDao.isPlayable(row(correctIndex = 2), "thirty_seconds"))
    }

    @Test
    fun `isPlayable يرفض الخيارات الناقصة أو الفاضية`() {
        assertFalse(SupabaseQuestionsDao.isPlayable(row(options = listOf("خيار واحد بس")), "thirty_seconds"))
        assertFalse(SupabaseQuestionsDao.isPlayable(row(options = emptyList()), "thirty_seconds"))
        assertFalse(
            "خيار فاضي ⇒ زرار فاضي في الشاشة",
            SupabaseQuestionsDao.isPlayable(row(options = listOf("الأرجنتين", "  ")), "thirty_seconds")
        )
        assertFalse(
            "خيارات أكتر من الحد",
            SupabaseQuestionsDao.isPlayable(
                row(options = listOf("ا", "ب", "ت", "ث", "ج", "ح")), "thirty_seconds"
            )
        )
    }

    @Test
    fun `isPlayable يرفض السؤال القصير أو الطويل`() {
        assertFalse(SupabaseQuestionsDao.isPlayable(row(question = "مين؟"), "thirty_seconds"))
        assertFalse(
            SupabaseQuestionsDao.isPlayable(row(question = "س".repeat(200)), "thirty_seconds")
        )
        assertTrue(
            "الحد الأدنى نفسه مقبول",
            SupabaseQuestionsDao.isPlayable(
                row(question = "ك".repeat(SupabaseQuestionsDao.MIN_QUESTION_LEN)), "thirty_seconds"
            )
        )
    }

    @Test
    fun `true_false ليه قواعد خاصة مفيهاش شروط خيارات`() {
        // مفيش خيارات مخزّنة لنوع صح/غلط، ومع ذلك السؤال صالح
        assertTrue(SupabaseQuestionsDao.isPlayable(row(gameType = "true_false", options = emptyList()), "true_false"))
        assertTrue(
            SupabaseQuestionsDao.isPlayable(
                row(gameType = "true_false", options = listOf("صح", "غلط"), correctIndex = 1), "true_false"
            )
        )
        assertFalse(
            "رقم تاني غير 0 و1 معناه بيانات فاسدة",
            SupabaseQuestionsDao.isPlayable(row(gameType = "true_false", correctIndex = 2), "true_false")
        )
    }

    // =========================================================================
    // التحويل لنماذج اللعبة
    // =========================================================================

    @Test
    fun `toThirtySeconds بينضّف النص ويرجّع الفئة الافتراضية`() {
        val converted = SupabaseQuestionsDao.toThirtySeconds(
            row(question = "  مين كسب المونديال؟  ", category = "  ")
        )

        assertEquals("مين كسب المونديال؟", converted.question)
        assertEquals("عامة", converted.category)
        assertEquals(3, converted.options.size)
        assertEquals(0, converted.correctIndex)
    }

    @Test
    fun `toTrueFalse بيترجم 0 لصح وأي رقم تاني لغلط`() {
        assertTrue(
            SupabaseQuestionsDao.toTrueFalse(
                row(gameType = "true_false", correctIndex = 0)
            ).isTrue
        )
        assertFalse(
            SupabaseQuestionsDao.toTrueFalse(
                row(gameType = "true_false", correctIndex = 1)
            ).isTrue
        )

        // الاختبار ده بيغطي التصحيح اللي حصل في البوت: الرقم مش truthiness عامة
        assertEquals(
            "البيان ده غلط", false,
            SupabaseQuestionsDao.toTrueFalse(row(gameType = "true_false", correctIndex = 1)).isTrue
        )
    }

    @Test
    fun `toSabaho بيحول الصعوبة المجهولة لمتوسط بدل انفجار`() {
        assertEquals(
            com.example.data.Difficulty.HARD,
            SupabaseQuestionsDao.toSabaho(row(gameType = "sabaho", difficulty = "hard")).difficulty
        )
        assertEquals(
            com.example.data.Difficulty.EASY,
            SupabaseQuestionsDao.toSabaho(row(gameType = "sabaho", difficulty = "EASY")).difficulty
        )
        assertEquals(
            "قيمة صعبة التفسير ⇒ متوسط",
            com.example.data.Difficulty.MEDIUM,
            SupabaseQuestionsDao.toSabaho(row(gameType = "sabaho", difficulty = "خرافية")).difficulty
        )
        assertEquals(
            com.example.data.Difficulty.MEDIUM,
            SupabaseQuestionsDao.toSabaho(row(gameType = "sabaho", difficulty = null)).difficulty
        )
    }

    @Test
    fun `difficultyToWire بيرجع قيم مطابقة لقيد القاعدة`() {
        assertEquals("easy", SupabaseQuestionsDao.difficultyToWire(com.example.data.Difficulty.EASY))
        assertEquals("medium", SupabaseQuestionsDao.difficultyToWire(com.example.data.Difficulty.MEDIUM))
        assertEquals("hard", SupabaseQuestionsDao.difficultyToWire(com.example.data.Difficulty.HARD))
    }

    @Test
    fun `answerText بيطلع النص الصحيح لكل نوع`() {
        assertEquals("الأرجنتين", SupabaseQuestionsDao.answerText(row()))
        assertEquals("فرنسا", SupabaseQuestionsDao.answerText(row(correctIndex = 1)))
        assertEquals("صح", SupabaseQuestionsDao.answerText(row(gameType = "true_false", correctIndex = 0)))
        assertEquals("غلط", SupabaseQuestionsDao.answerText(row(gameType = "true_false", correctIndex = 1)))
        assertEquals("خارج الحدود ⇒ فارغ", "", SupabaseQuestionsDao.answerText(row(correctIndex = 99)))
    }

    @Test
    fun `correctIndex الخارج بيتم حصره بدل ما ينفجر في الرسم`() {
        val converted = SupabaseQuestionsDao.toPvp(row(gameType = "pvp", correctIndex = 99))

        assertTrue(converted.correctIndex in converted.options.indices)
    }

    // =========================================================================
    // العقد بدون شبكة
    // =========================================================================

    private fun offlineDao(): SupabaseQuestionsDao {
        SupabaseEnv.overrideForTesting(
            SupabaseCredentials.PLACEHOLDER_URL,
            "eyJhbGci.dummy_anon_key"
        )
        return SupabaseQuestionsDao(SupabaseApi())
    }

    @Test
    fun `fetchFeed بيرجع null عند تعذر الاتصال مش قائمة فاضية`() {
        val dao = offlineDao()
        try {
            assertNull("null = فشل نقل، وده اللي بيحمي الكاش من المسح", dao.fetchFeed("thirty_seconds", 10))
        } finally {
            SupabaseEnv.clearOverride()
        }
    }

    @Test
    fun `fetchAll بيشمل كل الأنواع حتى عند الفشل`() {
        val dao = offlineDao()
        try {
            val all = dao.fetchAll()

            assertEquals(SupabaseQuestionsDao.DEFAULT_TYPES.size, all.size)
            assertTrue(all.values.all { it == null })
        } finally {
            SupabaseEnv.clearOverride()
        }
    }

    @Test
    fun `أسماء أنواع اللعبة مطابقة لقيم الجدول والكيان المحلي`() {
        assertEquals("thirty_seconds", SupabaseQuestionsDao.GAME_THIRTY_SECONDS)
        assertEquals("sabaho", SupabaseQuestionsDao.GAME_SABAHO)
        assertEquals("true_false", SupabaseQuestionsDao.GAME_TRUE_FALSE)
        assertEquals("pvp", SupabaseQuestionsDao.GAME_PVP)
        assertEquals("questions_feed", SupabaseQuestionsDao.VIEW_FEED)
    }
}
