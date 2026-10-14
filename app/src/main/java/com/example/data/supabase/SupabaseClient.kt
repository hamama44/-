package com.example.data.supabase

import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class SupabaseClient {
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun fetchQuestions(): List<SupabaseQuestion> = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured) return@withContext emptyList()
        try {
            val endpoint = "${SupabaseConfig.url.trimEnd('/')}/rest/v1/questions?select=*"
            val request = Request.Builder()
                .url(endpoint)
                .addHeader("apikey", SupabaseConfig.anonKey)
                .addHeader("Authorization", "Bearer ${SupabaseConfig.anonKey}")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val body = response.body?.string() ?: return@withContext emptyList()
                val listType = Types.newParameterizedType(List::class.java, SupabaseQuestion::class.java)
                val adapter = moshi.adapter<List<SupabaseQuestion>>(listType)
                adapter.fromJson(body) ?: emptyList()
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun fetchPlayers(): List<SupabasePlayer> = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured) return@withContext emptyList()
        try {
            val endpoint = "${SupabaseConfig.url.trimEnd('/')}/rest/v1/players?select=*"
            val request = Request.Builder()
                .url(endpoint)
                .addHeader("apikey", SupabaseConfig.anonKey)
                .addHeader("Authorization", "Bearer ${SupabaseConfig.anonKey}")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val body = response.body?.string() ?: return@withContext emptyList()
                val listType = Types.newParameterizedType(List::class.java, SupabasePlayer::class.java)
                val adapter = moshi.adapter<List<SupabasePlayer>>(listType)
                adapter.fromJson(body) ?: emptyList()
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun fetchLeaderboard(): List<SupabaseScore> = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured) return@withContext emptyList()
        try {
            val endpoint = "${SupabaseConfig.url.trimEnd('/')}/rest/v1/scores?select=*&order=points.desc&limit=25"
            val request = Request.Builder()
                .url(endpoint)
                .addHeader("apikey", SupabaseConfig.anonKey)
                .addHeader("Authorization", "Bearer ${SupabaseConfig.anonKey}")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val body = response.body?.string() ?: return@withContext emptyList()
                val listType = Types.newParameterizedType(List::class.java, SupabaseScore::class.java)
                val adapter = moshi.adapter<List<SupabaseScore>>(listType)
                adapter.fromJson(body) ?: emptyList()
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun postScore(score: SupabaseScore): Boolean = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured) return@withContext false
        try {
            val endpoint = "${SupabaseConfig.url.trimEnd('/')}/rest/v1/scores"
            val adapter = moshi.adapter(SupabaseScore::class.java)
            val json = adapter.toJson(score)
            val body = json.toRequestBody(jsonMediaType)

            val request = Request.Builder()
                .url(endpoint)
                .addHeader("apikey", SupabaseConfig.anonKey)
                .addHeader("Authorization", "Bearer ${SupabaseConfig.anonKey}")
                .addHeader("Prefer", "return=minimal")
                .post(body)
                .build()

            client.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        } catch (_: Exception) {
            false
        }
    }
}
