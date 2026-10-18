package com.example.data.supabase

import com.example.BuildConfig

/**
 * بيانات الاتصال بمشروع Supabase (الرابط + مفتاح Anon العام).
 *
 * هذا الكلاس هو "مصدر الحقيقة الوحيد" لكل ما يتعلق بمفاتيح الاتصال في التطبيق:
 * [SupabaseConfig] و [SupabaseApi] و [SupabaseClientProvider] يقرؤون منه.
 */
data class SupabaseCredentials(
    val url: String,
    val anonKey: String,
    val source: Source
) {
    /** من أين جاءت قيم الاتصال (مفيد في التشخيص وفي شاشة الإعدادات). */
    enum class Source {
        /** قيمة مُمرَّرة برمجياً (اختبارات أو تهيئة يدوية). */
        OVERRIDE,

        /** محفوظة محلياً في SharedPreferences بعد إدخالها من شاشة الإعدادات. */
        LOCAL_STORAGE,

        /** متغيرات بيئة حقيقية من نظام التشغيل (System.getenv). */
        ENVIRONMENT,

        /** محقونة وقت البناء من ملف .env عبر secrets-gradle-plugin إلى BuildConfig. */
        BUILD_CONFIG,

        /** قيم placeholder افتراضية — التطبيق يعتبرها "غير مُهيّأ". */
        PLACEHOLDER
    }

    /**
     * هل القيم صالحة للاستخدام الفعلي؟
     * نفس شروط [SupabaseConfig.isConfigured] السابقة (بدون كسر السلوك القائم).
     */
    val isUsable: Boolean
        get() = url.isNotBlank() &&
                !url.contains(PLACEHOLDER_URL_FRAGMENT) &&
                anonKey.isNotBlank() &&
                !anonKey.contains(PLACEHOLDER_KEY_FRAGMENT)

    /** رابط PostgREST الأساسي، مثال: https://xyz.supabase.co/rest/v1 */
    val restBaseUrl: String
        get() = "${url.trimEnd('/')}$REST_PATH"

    companion object {
        const val REST_PATH = "/rest/v1"
        const val PLACEHOLDER_URL = "https://xyzcompany.supabase.co"
        const val PLACEHOLDER_KEY = ""
        private const val PLACEHOLDER_URL_FRAGMENT = "xyzcompany"
        private const val PLACEHOLDER_KEY_FRAGMENT = "dummy"

        val PLACEHOLDER = SupabaseCredentials(PLACEHOLDER_URL, PLACEHOLDER_KEY, Source.PLACEHOLDER)
    }
}

/**
 * قراءة رابط Supabase و Anon Key من **متغيرات البيئة**.
 *
 * على أندرويد لا توجد متغيرات بيئة وقت التشغيل بالمعنى التقليدي، لذلك المشروع
 * يستخدم `secrets-gradle-plugin` الذي يقرأ ملف `.env` (أو `.env.example` كاحتياطي)
 * ويحقن القيم داخل `BuildConfig.SUPABASE_URL` و `BuildConfig.SUPABASE_ANON_KEY`.
 *
 * ترتيب الأولوية في [resolve]:
 *  1. [overrideForTesting] — قيمة مُمرَّرة برمجياً (اختبارات/تهيئة يدوية).
 *  2. التخزين المحلي — ما حفظه المستخدم من شاشة إعدادات Supabase.
 *  3. `System.getenv` — متغيرات بيئة حقيقية (تعمل في اختبارات JVM وأدوات surface).
 *  4. `BuildConfig` — القيم المحقونة من ملف `.env` وقت البناء.
 *  5. [SupabaseCredentials.PLACEHOLDER] — التطبيق يبقى في وضع "محاكاة بدون سيرفر".
 */
object SupabaseEnv {
    /** اسم متغير البيئة لرابط المشروع. */
    const val ENV_SUPABASE_URL = "SUPABASE_URL"

    /** اسم متغير البيئة لمفتاح Anon العام. */
    const val ENV_SUPABASE_ANON_KEY = "SUPABASE_ANON_KEY"

    @Volatile
    private var override: SupabaseCredentials? = null

    /**
     * قراءة المفاتيح بالترتيب الموثّق أعلاه. الدالة نقية وآمنة من أي Thread.
     */
    fun resolve(): SupabaseCredentials {
        override?.let { return it }

        val storedUrl = SupabaseConfig.storedUrl
        val storedKey = SupabaseConfig.storedAnonKey
        if (!storedUrl.isNullOrBlank() && !storedKey.isNullOrBlank()) {
            return SupabaseCredentials(storedUrl, storedKey, SupabaseCredentials.Source.LOCAL_STORAGE)
        }

        val envUrl = readEnvironmentVariable(ENV_SUPABASE_URL)
        val envKey = readEnvironmentVariable(ENV_SUPABASE_ANON_KEY)
        if (!envUrl.isNullOrBlank() && !envKey.isNullOrBlank()) {
            return SupabaseCredentials(envUrl.trim(), envKey.trim(), SupabaseCredentials.Source.ENVIRONMENT)
        }

        val buildUrl = readBuildConfigUrl()
        val buildKey = readBuildConfigAnonKey()
        if (buildUrl.isNotBlank() || buildKey.isNotBlank()) {
            return SupabaseCredentials(
                url = buildUrl.ifBlank { SupabaseCredentials.PLACEHOLDER_URL },
                anonKey = buildKey,
                source = SupabaseCredentials.Source.BUILD_CONFIG
            )
        }

        return SupabaseCredentials.PLACEHOLDER
    }

    /** هل المفاتيح الحالية صالحة للاتصال الفعلي بالسيرفر؟ */
    fun isConfigured(): Boolean = resolve().isUsable

    /**
     * تمرير قيم اتصال مباشرة (يتجاوز .env والتخزين المحلي).
     * تُستخدم في اختبارات الوحدة أو عند الرغبة في تبديل المشروع بدون SharedPreferences.
     */
    fun overrideForTesting(url: String, anonKey: String) {
        override = SupabaseCredentials(url.trim(), anonKey.trim(), SupabaseCredentials.Source.OVERRIDE)
    }

    /** إلغاء [overrideForTesting] والعودة لقراءة متغيرات البيئة. */
    fun clearOverride() {
        override = null
    }

    /** هل توجد قيمة override نشطة؟ */
    fun hasOverride(): Boolean = override != null

    // ---------------------------------------------------------------------
    // مصادر القراءة (معزولة في دوال حتى يسهل تجاوزها/اختبارها)
    // ---------------------------------------------------------------------

    /** قراءة متغير بيئة حقيقي — يعيد null بأمان على أندرويد. */
    private fun readEnvironmentVariable(name: String): String? = runCatching { System.getenv(name) }.getOrNull()

    private fun readBuildConfigUrl(): String = runCatching { BuildConfig.SUPABASE_URL }.getOrDefault("").orEmpty()

    private fun readBuildConfigAnonKey(): String =
        runCatching { BuildConfig.SUPABASE_ANON_KEY }.getOrDefault("").orEmpty()
}
