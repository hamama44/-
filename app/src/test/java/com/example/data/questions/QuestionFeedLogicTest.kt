package com.example.data.questions

import com.example.data.ThirtySecondsQuestion
import com.example.data.TrueFalseQuestion
import com.example.data.supabase.SupabaseQuestion
import com.example.data.supabase.SupabaseQuestionsDao
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * اختبارات منطق الدمج وتحويل الكاش — المنطق اللي بيحدد إيه اللي اللاعب
 * هيشوفه فعلاً بعد ما البوت ينشر.
 */
class QuestionFeedLogicTest {

    private fun ts(id: String, question: String, options: List<String> = listOf("أ", "ب")) =
        ThirtySecondsQuestion(id = id, question = question, options = options, correctIndex = 0, category = "عامة")

    // ------------------------------------------------------------------
    // mergeDistinct
    // ------------------------------------------------------------------

    @Test
    fun `mergeDistinct بيرجع المضمّن لما السحابة فاضية`() {
        val builtin = listOf(ts("b1", "سؤال أول"), ts("b2", "سؤال تاني"))

        val merged = QuestionFeedLogic.mergeDistinct(emptyList(), builtin) { it.question }

        assertEquals(builtin, merged)
    }

    @Test
    fun `mergeDistinct بيرجع السحابي لما المضمّن فاضي`() {
        val cloud = listOf(ts("c1", "سؤال جديد من البوت"))

        val merged = QuestionFeedLogic.mergeDistinct(cloud, emptyList()) { it.question }

        assertEquals(cloud, merged)
    }

    @Test
    fun `mergeDistinct بيحط السحابي أولاً وبيشيل المكرر من المضمّن`() {
        val cloud = listOf(ts("c1", "مين كسب كأس العالم ٢٠٢٢؟"))
        val builtin = listOf(
            ts("b1", "مين كسب كأس العالم ٢٠٢٢؟"),   // مكرر حرفي ⇒ يطلع
            ts("b2", "كام لاعب في الملعب؟")          // جديد ⇒ يفضل
        )

        val merged = QuestionFeedLogic.mergeDistinct(cloud, builtin) { it.question }

        assertEquals(2, merged.size)
        assertEquals("الأحدث من البوت يتقدّم", "c1", merged[0].id)
        assertEquals("b2", merged[1].id)
    }

    @Test
    fun `mergeDistinct بيعتبر نفس السؤال بترقيم مختلف مكرر`() {
        // ده الاختبار اللي بيمنع إن البوت يضيف «نفس» السؤال مرتين في الجولة
        val cloud = listOf(ts("c1", "مين كسب كأس العالم 2022؟"))
        val builtin = listOf(ts("b1", "مَيْن كَسَبَ كَأْسَ العَالَمِ ٢٠٢٢"))

        val merged = QuestionFeedLogic.mergeDistinct(cloud, builtin) { it.question }

        assertEquals("التطبيع لازم يلمس المكرر", 1, merged.size)
        assertEquals("c1", merged[0].id)
    }

    @Test
    fun `mergeDistinct بيشيل التكرار الجوه سحابي نفسه`() {
        // المناعة هنا بس على المكرر الحرفي بين طبقتين، والقائمة السحابية
        // بتتنضّف قبلها بـ dedupeWithin
        val cloud = listOf(ts("c1", "سؤال"), ts("c2", "سؤال"))
        val clean = QuestionFeedLogic.dedupeWithin(cloud) { it.question }

        val merged = QuestionFeedLogic.mergeDistinct(clean, emptyList()) { it.question }
        assertEquals(1, merged.size)
    }

    // ------------------------------------------------------------------
    // dedupeWithin
    // ------------------------------------------------------------------

    @Test
    fun `dedupeWithin بيحتفظ بأول نسخة (اللي هي الأحدث من الـDAO)`() {
        val list = listOf(
            ts("newest", "مين كسب الدوري؟"),
            ts("older", "مين كسب الدوري؟"),
            ts("other", "سؤال تاني خالص؟")
        )

        val result = QuestionFeedLogic.dedupeWithin(list) { it.question }

        assertEquals(2, result.size)
        assertEquals("newest", result[0].id)
        assertEquals("other", result[1].id)
    }

    @Test
    fun `dedupeWithin بيمرّر القوائم القصيرة بدون تغيير`() {
        val single = listOf(ts("c1", "سؤال"))
        assertEquals(single, QuestionFeedLogic.dedupeWithin(single) { it.question })
        assertEquals(
            emptyList<ThirtySecondsQuestion>(),
            QuestionFeedLogic.dedupeWithin<ThirtySecondsQuestion>(emptyList()) { it.question }
        )
    }

    @Test
    fun `dedupeWithin بيتعامل مع صح وغلط على نص البيان`() {
        val list = listOf(
            TrueFalseQuestion(id = "a", statement = "كأس العالم كل ٤ سنين", isTrue = true, explanation = "", category = ""),
            TrueFalseQuestion(id = "b", statement = "كأس العالم كل 4 سنين", isTrue = false, explanation = "", category = "")
        )

        val result = QuestionFeedLogic.dedupeWithin(list) { it.statement }

        assertEquals("نفس البيان بأرقام مختلفة = مكرر", 1, result.size)
        assertTrue("بنحتفظ بالأحدث وأول نسخة في القائمة", result[0].isTrue)
    }

    // ------------------------------------------------------------------
    // عتبات وملخّص الحالة
    // ------------------------------------------------------------------

    @Test
    fun `hasMeaningfulCloudContent بيحترم الحد الأدنى`() {
        assertFalse(QuestionFeedLogic.hasMeaningfulCloudContent(0))
        assertFalse(QuestionFeedLogic.hasMeaningfulCloudContent(2))
        assertTrue(QuestionFeedLogic.hasMeaningfulCloudContent(QuestionFeedLogic.MIN_CLOUD_QUESTIONS))
        assertTrue(QuestionFeedLogic.hasMeaningfulCloudContent(100))
    }

    @Test
    fun `describeFeed بيوصف الحالات التلاته`() {
        assertTrue(QuestionFeedLogic.describeFeed(0, 39).contains("مفيش أسئلة سحابية"))
        assertTrue(QuestionFeedLogic.describeFeed(2, 39).contains("البوت شغال"))
        val full = QuestionFeedLogic.describeFeed(61, 39)
        assertTrue("لازم يظهر الإجمالي", full.contains("100"))
        assertTrue("ولا مصدر البوت", full.contains("61"))
    }

    // ------------------------------------------------------------------
    // CloudQuestionEntity.from — التحويل من صف السيرفر للكاش
    // ------------------------------------------------------------------

    private fun cloudRow(
        gameType: String = "thirty_seconds",
        question: String = "مين كسب كأس العالم ٢٠٢٢ في قطر؟",
        options: List<String> = listOf("الأرجنتين", "فرنسا", "البرازيل"),
        correctIndex: Int = 0
    ) = SupabaseQuestion(
        id = "q_bot_1",
        gameType = gameType,
        question = question,
        options = options,
        correctIndex = correctIndex,
        difficulty = "easy",
        category = "كأس العالم",
        explanation = "فازت الأرجنتين.",
        source = "bot",
        publishedAt = "2026-10-18T09:00:00+00:00"
    )

    @Test
    fun `from بيحوّل الصف الصالح لكيان كاش كامل`() {
        val entity = CloudQuestionEntity.from(cloudRow(), nowMillis = 555L, publishedAtMillis = 999L)

        assertTrue(entity != null)
        entity!!
        assertEquals("q_bot_1", entity.id)
        assertEquals("thirty_seconds", entity.gameType)
        assertEquals("الأرجنتين|فرنسا|البرازيل", entity.optionsString)
        assertEquals(0, entity.correctIndex)
        assertEquals("easy", entity.difficulty)
        assertEquals("bot", entity.source)
        assertEquals(999L, entity.publishedAtMillis)
        assertEquals(555L, entity.cachedAtMillis)
        assertEquals(64, entity.contentHash.length)
    }

    @Test
    fun `from بيرفض الصف المش صالح بدل ما يخزنه`() {
        assertNull(
            CloudQuestionEntity.from(cloudRow(correctIndex = 9), 1L, 1L)
        )
        assertNull(
            CloudQuestionEntity.from(cloudRow(question = "قصير"), 1L, 1L)
        )
        assertNull(
            CloudQuestionEntity.from(cloudRow(options = listOf("خيار واحد")), 1L, 1L)
        )
    }

    @Test
    fun `from بيولّد خيارات صح وغلط لو النوع ماجابش خيارات`() {
        // الاختبار ده بيغطي باج حقيقي: بدون التوليد كانت كل أسئلة true_false
        // اترفضت بصمت من الكاش (مفيش خيارات جاية من الجدول للنوع ده)
        val trueRow = cloudRow(gameType = "true_false", options = emptyList(), correctIndex = 0)
        val falseRow = cloudRow(gameType = "true_false", options = listOf("صح", "غلط"), correctIndex = 1)

        val storedTrue = CloudQuestionEntity.from(trueRow, 1L, 1L)
        assertTrue("السؤال ده لازم يتخزن", storedTrue != null)
        assertEquals("صح|غلط", storedTrue!!.optionsString)
        assertEquals(0, storedTrue.correctIndex)
        assertTrue("0 ⇒ البيان صحيح", storedTrue.toTrueFalse().isTrue)

        val stored = CloudQuestionEntity.from(falseRow, 1L, 1L)!!
        assertEquals("صح|غلط", stored.optionsString)
        assertEquals(1, stored.correctIndex)
        assertFalse("1 ⇒ البيان غلط", stored.toTrueFalse().isTrue)
    }

    @Test
    fun `optionsString بيرجع القائمة نفسها بعد دورة الترميز`() {
        val entity = CloudQuestionEntity.from(cloudRow(), 1L, 1L)!!

        assertEquals(listOf("الأرجنتين", "فرنسا", "البرازيل"), entity.optionsList)
    }

    @Test
    fun `تحويل الكاش لنموذج اللعبة بيحصر الرقم الخارج`() {
        // حماية من بيانات قديمة اتخزنت قبل ما نضيف التحقق
        val entity = CloudQuestionEntity.from(cloudRow(), 1L, 1L)!!
            .copy(correctIndex = 42, optionsString = "أ|ب|ت")

        val converted = entity.toThirtySeconds()

        assertEquals(2, converted.correctIndex)
    }

    @Test
    fun `الحذف بالنص الفاضي في optionsString بيرجع قائمة فاضية بأمان`() {
        val entity = CloudQuestionEntity.from(cloudRow(), 1L, 1L)!!.copy(optionsString = "|||")

        assertTrue(entity.optionsList.isEmpty())
        // التحويل لازم ما ينفعش يفجر: index بيتحصر في 0 حتى مع قائمة فاضية
        assertEquals(0, entity.toThirtySeconds().correctIndex)
    }
}
