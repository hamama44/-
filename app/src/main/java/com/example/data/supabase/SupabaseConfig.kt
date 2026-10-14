package com.example.data.supabase

import com.example.BuildConfig

object SupabaseConfig {
    val url: String
        get() = try {
            BuildConfig.SUPABASE_URL
        } catch (_: Exception) {
            "https://xyzcompany.supabase.co"
        }

    val anonKey: String
        get() = try {
            BuildConfig.SUPABASE_ANON_KEY
        } catch (_: Exception) {
            ""
        }

    val isConfigured: Boolean
        get() = url.isNotBlank() &&
                !url.contains("xyzcompany") &&
                anonKey.isNotBlank() &&
                !anonKey.contains("dummy")
}
