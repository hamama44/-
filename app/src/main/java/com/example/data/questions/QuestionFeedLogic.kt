package com.example.data.questions

import com.example.data.supabase.SupabaseQuestionsDao

/**
 * منطق نقي لدمج الأسئلة السحابية مع الأسئلة المضمّنة في APK.
 *
 * مفصول في كائن بدون Android ولا Room عشان:
 *  - يبقى قابل للاختبار مباشرةً على JVM.
 *  - يحدد بوضوح سياسة «مين يكسب» لما يكون نفس السؤال موجود في الاتنين،
 *    ودي سياسة لازم تكون ثابتة مش صدفة ترتيب.
 */
object QuestionFeedLogic {

    /**
     * دمج قائمتين مع إزالة التكرار على نص السؤال الموحّد.
     *
     * الأسئلة السحابية بتاخد الأولوية على المضمّنة: البوت بصلّح الإجابات
     * الغلط وبيحدّث الصياغة، فالنسخة الأحدث هي اللي تُلعب. ولو السحابة
     * فاضية (مفيش نت / مفيش مزامنة بعد) القائمة المضمّنة ترجع كما هي —
     * يعني مافيش حالة يفقد فيها اللاعب المحتوى خالص.
     *
     * التكرار بيتحدّد بالنص الموحّد (تشكيل/ترقيم/همزات/أرقام متطبعة) مش
     * بالحرفي، لأن البوت بيقدر يعيد نفس السؤال بترقيم مختلف.
     */
    fun <T> mergeDistinct(
        cloud: List<T>,
        builtin: List<T>,
        questionOf: (T) -> String
    ): List<T> {
        if (cloud.isEmpty()) return builtin
        if (builtin.isEmpty()) return cloud

        val seen = cloud.mapTo(HashSet()) { SupabaseQuestionsDao.normalizeForDedup(questionOf(it)) }
        val kept = builtin.filter { SupabaseQuestionsDao.normalizeForDedup(questionOf(it)) !in seen }
        return cloud + kept
    }

    /**
     * إزالة التكرار داخل قائمة واحدة (مثلاً سيرفر راجع نفس السؤال مرتين
     * بنسختين مختلفتي الـid). أول نسخة بتفضل — والقائمة داخلياً مرتبة
     * «الأحدث أولاً» من الـDAO، فالأحدث هو اللي بيتختار.
     */
    fun <T> dedupeWithin(list: List<T>, questionOf: (T) -> String): List<T> {
        if (list.size < 2) return list
        val seen = HashSet<String>(list.size)
        return list.filter { seen.add(SupabaseQuestionsDao.normalizeForDedup(questionOf(it))) }
    }

    /**
     * هل فيه محتوى سحابي كفاية يخلّي المزامنة مجدية؟
     *
     * بنعتبر أقل من [MIN_CLOUD_QUESTIONS] سؤال «مش كافي» — ده بيحمي من
     * حالة نصرف فيها طلبات الشبكة كل مرة على جدول لسه ما اتغذّاش.
     */
    fun hasMeaningfulCloudContent(cachedCount: Int): Boolean = cachedCount >= MIN_CLOUD_QUESTIONS

    /** تحت العدد ده بنعتبر الكاش مجرد بداية مش مصدر محتوى حقيقي. */
    const val MIN_CLOUD_QUESTIONS = 3

    /**
     * وصف عربي للكاش — يُعرض في الإعدادات عشان اللاعب/المطوّر يعرف
     * المزامنة جابت حاجة ولا لأ بدل ما يخمّن.
     */
    fun describeFeed(cloudCount: Int, builtinCount: Int): String = when {
        cloudCount <= 0 -> "مفيش أسئلة سحابية لسه — بنلعّب الـ$builtinCount المضمّنة 📦"
        cloudCount < MIN_CLOUD_QUESTIONS ->
            "فيه $cloudCount سؤال سحابي جديد + $builtinCount مضمّن — البوت شغال 🤖"
        else ->
            "${cloudCount + builtinCount} سؤال متاح ($cloudCount من البوت 🤖 + $builtinCount مضمّن)"
    }
}
