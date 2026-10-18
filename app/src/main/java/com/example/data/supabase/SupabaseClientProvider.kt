package com.example.data.supabase

import android.content.Context

/**
 * نقطة التهيئة الوحيدة لعميل Supabase في التطبيق.
 *
 * يقرأ الرابط ومفتاح Anon من **متغيرات البيئة** عبر [SupabaseEnv] بالترتيب:
 * التخزين المحلي (شاشة الإعدادات) ← `System.getenv` ← `BuildConfig` (المحقون من
 * ملف `.env` بواسطة secrets-gradle-plugin وقت البناء).
 *
 * كل الكائنات تُبنى مرة واحدة (Singleton) وبأمان من عدة Threads، لكنها تقرأ
 * المفاتيح **عند كل طلب** (وليس عند البناء) — لذلك إدخال المستخدم رابطاً جديداً
 * من شاشة الإعدادات يسري فوراً بدون إعادة تشغيل التطبيق.
 *
 * الاستخدام:
 * ```
 * // مرة واحدة عند الإقلاع (Application/Activity/ViewModel)
 * SupabaseClientProvider.initialize(context)
 *
 * val api      = SupabaseClientProvider.api()
 * val roomsDao = SupabaseClientProvider.roomsDao()
 * ```
 */
object SupabaseClientProvider {

    @Volatile
    private var apiInstance: SupabaseApi? = null

    @Volatile
    private var legacyClientInstance: SupabaseClient? = null

    @Volatile
    private var roomsDaoInstance: SupabaseRoomsDao? = null

    @Volatile
    private var userDaoInstance: SupabaseUserDao? = null

    private val lock = Any()

    /**
     * تهيئة المزوّد: يربط تخزين المفاتيح المحلي بالـ [context] ثم يُعيد [SupabaseApi].
     * آمنة للاستدعاء المتكرر (idempotent).
     */
    fun initialize(context: Context): SupabaseApi {
        SupabaseConfig.init(context.applicationContext)
        return api()
    }

    /** طبقة PostgREST المشتركة لكل الـ DAOs الجديدة. */
    fun api(): SupabaseApi = apiInstance ?: synchronized(lock) {
        apiInstance ?: SupabaseApi().also { apiInstance = it }
    }

    /**
     * العميل القديم (أسئلة/لاعبون/نتائج) — ما زال مستخدماً من
     * `FootballRepository` و `SupabaseOnlineScreen`، ويُبنى عبر نفس المفاتيح.
     */
    fun client(): SupabaseClient = legacyClientInstance ?: synchronized(lock) {
        legacyClientInstance ?: SupabaseClient().also { legacyClientInstance = it }
    }

    /** DAO الغرف البعيد (views: rooms / rooms_overview / room_participants). */
    fun roomsDao(): SupabaseRoomsDao = roomsDaoInstance ?: synchronized(lock) {
        roomsDaoInstance ?: SupabaseRoomsDao(api()).also { roomsDaoInstance = it }
    }

    /** DAO المستخدمين ولوحة الصدارة (views: users / user_challenges / leaderboard). */
    fun userDao(): SupabaseUserDao = userDaoInstance ?: synchronized(lock) {
        userDaoInstance ?: SupabaseUserDao(api()).also { userDaoInstance = it }
    }

    /** المفاتيح الحالية ومصدرها (للعرض في شاشة التشخيص). */
    fun credentials(): SupabaseCredentials = SupabaseEnv.resolve()

    /** هل التطبيق مربوط بمشروع Supabase صالح؟ */
    fun isConfigured(): Boolean = SupabaseEnv.isConfigured()

    /** وصف عربي جاهز للعرض يوضّح من أين جاءت المفاتيح وحالة الربط. */
    fun describeStatus(): String {
        val credentials = credentials()
        val sourceLabel = when (credentials.source) {
            SupabaseCredentials.Source.ENVIRONMENT -> "متغيرات البيئة (System.getenv)"
            SupabaseCredentials.Source.BUILD_CONFIG -> "ملف .env عبر BuildConfig"
            SupabaseCredentials.Source.LOCAL_STORAGE -> "محفوظة محلياً من شاشة الإعدادات"
            SupabaseCredentials.Source.OVERRIDE -> "قيمة مُمرَّرة برمجياً"
            SupabaseCredentials.Source.PLACEHOLDER -> "غير مُهيّأ (قيم افتراضية)"
        }
        return if (credentials.isUsable) {
            "متصل ☁️ • المصدر: $sourceLabel • ${credentials.url}"
        } else {
            "غير مُهيّأ 🟡 • المصدر: $sourceLabel"
        }
    }

    /**
     * تفريغ كل الكائنات المكاشّة — تُستدعى في الاختبارات، أو إذا رغبت في فرض
     * إعادة البناء (المفاتيح نفسها تُقرأ حياً لذلك التفريغ اختياري عادةً).
     */
    fun reset() = synchronized(lock) {
        apiInstance = null
        legacyClientInstance = null
        roomsDaoInstance = null
        userDaoInstance = null
    }
}
