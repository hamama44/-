package com.example.data.supabase

import android.content.Context
import android.content.SharedPreferences

object SupabaseConfig {
    private var customUrl: String? = null
    private var customAnonKey: String? = null
    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        prefs = context.getSharedPreferences("malab_supabase_config", Context.MODE_PRIVATE)
        customUrl = prefs?.getString("custom_supabase_url", null)
        customAnonKey = prefs?.getString("custom_supabase_anon_key", null)
    }

    /**
     * القيمة المحفوظة محلياً فقط (بدون .env ولا متغيرات البيئة).
     * تُستخدم من [SupabaseEnv.resolve] كأول مصدر بعد الـ override.
     */
    val storedUrl: String?
        get() = customUrl?.takeIf { it.isNotBlank() }

    /** مرادف [storedUrl] لمفتاح Anon. */
    val storedAnonKey: String?
        get() = customAnonKey?.takeIf { it.isNotBlank() }

    /** من أين جاءت قيم الاتصال الحالية (للعرض في شاشة الإعدادات/التشخيص). */
    val credentialsSource: SupabaseCredentials.Source
        get() = SupabaseEnv.resolve().source

    fun saveCredentials(context: Context, url: String, anonKey: String) {
        val sp = prefs ?: context.getSharedPreferences("malab_supabase_config", Context.MODE_PRIVATE)
        sp.edit()
            .putString("custom_supabase_url", url.trim())
            .putString("custom_supabase_anon_key", anonKey.trim())
            .apply()
        customUrl = url.trim()
        customAnonKey = anonKey.trim()
    }

    /**
     * رابط مشروع Supabase.
     * يُحسم عبر [SupabaseEnv.resolve]: التخزين المحلي ← متغيرات البيئة ← BuildConfig (.env).
     */
    val url: String
        get() = SupabaseEnv.resolve().url

    /**
     * مفتاح Anon العام.
     * يُحسم بنفس أولوية [url].
     */
    val anonKey: String
        get() = SupabaseEnv.resolve().anonKey

    /** هل التطبيق مربوط فعلياً بمشروع Supabase صالح؟ */
    val isConfigured: Boolean
        get() = SupabaseEnv.isConfigured()
}
