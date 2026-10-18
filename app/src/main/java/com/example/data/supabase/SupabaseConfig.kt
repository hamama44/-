package com.example.data.supabase

import android.content.Context
import android.content.SharedPreferences
import com.example.BuildConfig

object SupabaseConfig {
    private var customUrl: String? = null
    private var customAnonKey: String? = null
    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        prefs = context.getSharedPreferences("malab_supabase_config", Context.MODE_PRIVATE)
        customUrl = prefs?.getString("custom_supabase_url", null)
        customAnonKey = prefs?.getString("custom_supabase_anon_key", null)
    }

    fun saveCredentials(context: Context, url: String, anonKey: String) {
        val sp = prefs ?: context.getSharedPreferences("malab_supabase_config", Context.MODE_PRIVATE)
        sp.edit()
            .putString("custom_supabase_url", url.trim())
            .putString("custom_supabase_anon_key", anonKey.trim())
            .apply()
        customUrl = url.trim()
        customAnonKey = anonKey.trim()
    }

    val url: String
        get() {
            if (!customUrl.isNullOrBlank()) return customUrl!!
            return try {
                BuildConfig.SUPABASE_URL
            } catch (_: Exception) {
                "https://xyzcompany.supabase.co"
            }
        }

    val anonKey: String
        get() {
            if (!customAnonKey.isNullOrBlank()) return customAnonKey!!
            return try {
                BuildConfig.SUPABASE_ANON_KEY
            } catch (_: Exception) {
                ""
            }
        }

    val isConfigured: Boolean
        get() = url.isNotBlank() &&
                !url.contains("xyzcompany") &&
                anonKey.isNotBlank() &&
                !anonKey.contains("dummy")
}
