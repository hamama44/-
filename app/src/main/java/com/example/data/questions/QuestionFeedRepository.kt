package com.example.data.questions

import android.content.Context
import com.example.data.PvPQuestion
import com.example.data.SabahoQuestion
import com.example.data.ThirtySecondsQuestion
import com.example.data.TrueFalseQuestion
import com.example.data.supabase.SupabaseClientProvider
import com.example.data.supabase.SupabaseQuestionsDao
import com.example.data.supabase.parseSupabaseTimestamp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * مستودع «الأبداً متحدّث»: بيجيب الأسئلة من Supabase (اللي بينشرها البوت)،
 * بيخزنها في Room، وبيمزجها مع الأسئلة المضمّنة في APK.
 *
 * السياسة دي هي اللي بتخلي سؤال البوت الجديد يوصل للاعب فعلاً — من غيرها
 * الجدول يبقى مزروع والتطبيق لسه بيرسم نفس الـ39 سؤال القديم.
 *
 * Design notes:
 *  - **الحذف مش افتراضي**: الكاش بيتنضّف بس لما نجيب لقطة كاملة (`rows.size < limit`).
 *    أي فشل شبكة أو لقطة مقطوعة بالـlimit مابيحذفش حاجة — لأن مسح كاش سليم
 *    بسبب انقطاع نت أسوأ بكتير من إن سؤال مرفوض يفضل مخزّن.
 *  - **الأولوية للسحابة** عند تكرار نفس السؤال، لأن البوت بيصلّح الصياغة
 *    والإجابات؛ المضمّن في APK هو الشبكة الأمان لما السحابة تكون فاضية.
 *  - كل المنطق الحساس (الدمج، إزالة التكرار) في [QuestionFeedLogic] النقي.
 *
 * كل التبعية للاختبار محقونة عبر المُنشئ، فالفصل ده بيشتغل على JVM بدون
 * Android وبدون شبكة (شوف QuestionFeedRepositoryTest).
 */
class QuestionFeedRepository(
    private val dao: CloudQuestionDao,
    /** بيوصلنا بمصدر الأسئلة السحابي؛ `null` لو الخدمة مش مُهيّأة. */
    private val remoteProvider: () -> SupabaseQuestionsDao?,
    private val isOnline: () -> Boolean,
    private val now: () -> Long = { System.currentTimeMillis() },
    private val fetchLimitPerType: Int = SupabaseQuestionsDao.DEFAULT_LIMIT
) {


    /**
     * نتيجة مزامنة واحدة — للعرض في الإعدادات وللتشخيص.
     *
     * @param fetchedTotal عدد الصفوف اللي رجعت من السيرفر (بعد فلترة غير الصالح).
     * @param cachedTotal عدد الصفوف اللي اتخزّنت فعلياً.
     * @param prunedTotal الصفوف اللي اتشالت: المكررات اللي اختفت من السيرفر
     *  (0 لو اللقطة مش كاملة) **+** أقدم صفوف الكاش اللي تجاوزت
     *  [CACHE_CAP_PER_TYPE]. الاتنين اتحطوا في رقم واحد لأن الرد بيهم
     *  المستخدم كـ«الكاش اتنضّف»، والتفريق بيتشاف في اللوج.
     * @param failedTypes الأنواع اللي فشل جلبها — بتخلي الواجهة تقول «مزامنة
     *  ناقصة» بدل «مافيش أسئلة جديدة» وده فرق كبير للمستخدم.
     */
    data class SyncResult(
        val fetchedTotal: Int = 0,
        val cachedTotal: Int = 0,
        val prunedTotal: Int = 0,
        val failedTypes: List<String> = emptyList(),
        val skippedReason: String? = null
    ) {
        val didAnything: Boolean get() = cachedTotal > 0 || prunedTotal > 0

        val arabicSummary: String
            get() = when {
                skippedReason != null -> "اتخطّت المزامنة: $skippedReason"
                failedTypes.isNotEmpty() && cachedTotal == 0 ->
                    "فشلت المزامنة (${failedTypes.joinToString()}) — بنكمّل بالكاش 📴"
                cachedTotal == 0 -> "مافيش أسئلة جديدة النهاردة ✅"
                else -> "اتزامن $cachedTotal سؤال 🆕"
            }
    }

    /**
     * مزامنة كاملة لكل الأنواع.
     *
     * بتتنادى عند فتح التطبيق وعند دخول اللاعب لعبة، مش في خلفية مستمرة:
     * كده بنوفّر البطارية والطلبات ومابنضطرش لـWorkManager في أول إصدار.
     */
    suspend fun refresh(): SyncResult {
        if (!isOnline()) {
            return SyncResult(skippedReason = "مفيش اتصال مُهيّأ بالسيرفر")
        }
        val remote = remoteProvider()
            ?: return SyncResult(skippedReason = "بيانات Supabase مش مضبوطة")

        var fetched = 0
        var cached = 0
        var pruned = 0
        var trimmed = 0
        val failed = mutableListOf<String>()

        SupabaseQuestionsDao.DEFAULT_TYPES.forEach { (gameType, configuredLimit) ->
            val limit = minOf(configuredLimit, fetchLimitPerType)
            val rows = remote.fetchFeed(gameType, limit)
            if (rows == null) {
                failed.add(gameType)
                return@forEach
            }
            fetched += rows.size

            val stampMillis = now()
            val entities = rows
                .mapNotNull { row ->
                    CloudQuestionEntity.from(
                        row = row,
                        nowMillis = stampMillis,
                        publishedAtMillis = parseSupabaseTimestamp(row.publishedAt)
                    )
                }
                // لو السيرفر رجع نفس السؤال مرتين بنسخة id مختلفة، نحتفظ
                // بالأولى (القائمة مرتبة الأحدث أولاً من الـview)
                .distinctBy { it.contentHash }

            if (entities.isNotEmpty()) {
                dao.insertAll(entities)
                cached += entities.size
            }

            // النضافة فقط مع لقطة كاملة: لو جيبنا عدد = الحد فالأكيد فيه
            // أقدم من كده في القاعدة، وحذفه هنا كان هيمسح محتوى سليم.
            val snapshotIsComplete = rows.size < limit
            if (snapshotIsComplete) {
                pruned += dao.pruneGameTypeNotIn(gameType, entities.map { it.id })
            }

            // سقف الحجم بيشتغل في كل الأحوال: ده مش «تنضيف» لبيانات مش
            // موجودة، ده رمي الأقدم من كاش بقى أكبر من اللازم.
            trimmed += dao.pruneGameTypeOverflow(gameType, CACHE_CAP_PER_TYPE)
        }

        return SyncResult(
            fetchedTotal = fetched,
            cachedTotal = cached,
            prunedTotal = pruned + trimmed,
            failedTypes = failed
        )
    }

    // ------------------------------------------------------------------
    // التدفقات المُدمجة — بترجع قائمة اللعبة جاهزة للرسم
    // ------------------------------------------------------------------

    /** أسئلة «30 ثانية»: السحابي أولاً ثم المضمّن بدون تكرار. */
    fun thirtySeconds(builtin: List<ThirtySecondsQuestion>): Flow<List<ThirtySecondsQuestion>> =
        dao.observeByGameType(SupabaseQuestionsDao.GAME_THIRTY_SECONDS)
            .map { rows ->
                QuestionFeedLogic.mergeDistinct(
                    cloud = QuestionFeedLogic.dedupeWithin(rows.map { it.toThirtySeconds() }) { it.question },
                    builtin = builtin,
                    questionOf = { it.question }
                )
            }

    /** أسئلة «صباحو» — نفس السياسة مع تحويل الصعوبة. */
    fun sabaho(builtin: List<SabahoQuestion>): Flow<List<SabahoQuestion>> =
        dao.observeByGameType(SupabaseQuestionsDao.GAME_SABAHO)
            .map { rows ->
                QuestionFeedLogic.mergeDistinct(
                    cloud = QuestionFeedLogic.dedupeWithin(rows.map { it.toSabaho() }) { it.question },
                    builtin = builtin,
                    questionOf = { it.question }
                )
            }

    /** أسئلة «صح/غلط» — البيان نفسه هو النص المُقارَن عليه. */
    fun trueFalse(builtin: List<TrueFalseQuestion>): Flow<List<TrueFalseQuestion>> =
        dao.observeByGameType(SupabaseQuestionsDao.GAME_TRUE_FALSE)
            .map { rows ->
                QuestionFeedLogic.mergeDistinct(
                    cloud = QuestionFeedLogic.dedupeWithin(rows.map { it.toTrueFalse() }) { it.statement },
                    builtin = builtin,
                    questionOf = { it.statement }
                )
            }

    /** أسئلة «ثنائي التحدي». */
    fun pvp(builtin: List<PvPQuestion>): Flow<List<PvPQuestion>> =
        dao.observeByGameType(SupabaseQuestionsDao.GAME_PVP)
            .map { rows ->
                QuestionFeedLogic.mergeDistinct(
                    cloud = QuestionFeedLogic.dedupeWithin(rows.map { it.toPvp() }) { it.question },
                    builtin = builtin,
                    questionOf = { it.question }
                )
            }

    /** عدد الأسئلة السحابية المخزّنة — لعرضه في الإعدادات. */
    fun observeCloudCount(): Flow<Int> = dao.observeTotalCount()

    suspend fun cloudCount(): Int = dao.getTotalCount()

    companion object {
        /**
         * أقصى عدد سؤال سحابي مخزّن لكل نوع لعبة على الجهاز.
         *
         * السقف ده لازم مع النشر الكثيف: البوت بيوصل ~62 سؤال جديد لكل نوع
         * في اليوم، ومن غير قصّ الهيوصل الكاش ~23 ألف صف للنوع في السنة.
         * ده مابيكسرش Room، بس بيفلتر كل جولة: القائمة بتتحمل كاملة →
         * تُدمج → تُخلَّط قبل ما الـtake يختار 15، فكل ثانية في المسار ده
         * بتتحسب على الجهاز مش على السيرفر.
         *
         * 400 = ~6 أيام نشر لكل نوع، ولسه عينة الخلط كبيرة كفاية إن اللاعب
         * مايعيدش نفس الأسئلة. لو رفعت `QUESTION_BOT_DAILY_CAP` فوق 250
         * ارفع الرقم ده معاه.
         */
        const val CACHE_CAP_PER_TYPE = 400

        @Volatile
        private var instance: QuestionFeedRepository? = null

        /** نسخة واحدة على مستوى التطبيق — الكاش مشترك بين كل الشاشات. */
        fun get(context: Context): QuestionFeedRepository =
            instance ?: synchronized(this) {
                instance ?: build(context).also { instance = it }
            }

        private fun build(context: Context): QuestionFeedRepository {
            val appContext = context.applicationContext
            val api = SupabaseClientProvider.initialize(appContext)
            return QuestionFeedRepository(
                dao = AppDatabaseHolder.cloudDao(appContext),
                remoteProvider = { SupabaseQuestionsDao(api) },
                // مفيش معنى لمحاولة المزامنة لو المفاتيح مش صالحة
                isOnline = { SupabaseClientProvider.isConfigured() }
            )
        }
    }
}

/**
 * وصول مختصر لـDAO الكاش.
 *
 * معزول كائن صغير عشان [QuestionFeedRepository.build] ما يعتمدش على ترتيب
 * تهيئة `AppDatabase` في أكثر من مكان، وعلشان الاختبارات تعدي المُنشئ ده.
 */
internal object AppDatabaseHolder {
    fun cloudDao(context: Context): CloudQuestionDao =
        com.example.data.room.AppDatabase.getDatabase(context).cloudQuestionDao()
}
