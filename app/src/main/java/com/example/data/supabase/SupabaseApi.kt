package com.example.data.supabase

import com.squareup.moshi.JsonAdapter
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

/**
 * طبقة HTTP موحّدة للتعامل مع PostgREST في Supabase.
 *
 * كل الـ DAOs الجديدة ([SupabaseRoomsDao] و [SupabaseUserDao]) تُبنى فوق هذه الطبقة
 * بدل تكرار كود OkHttp/Moshi في كل مكان.
 *
 * ملاحظة: [SupabaseClient] القديم ما زال موجوداً كما هو (للأسئلة والنتائج) حتى لا
 * ننكسر أي كود شغّال؛ هذه الطبقة هي المسار المعتمد لأي DAO جديد.
 *
 * **الاتفاقية المهمة:** أي دالة قراءة تُعيد `null` يعني "فشل الاتصال/الصلاحيات"،
 * بينما القائمة الفارغة `emptyList()` تعني "نجح الطلب ولا توجد صفوف".
 * هذا التفريق هو ما يسمح للـ Repository بالرجوع للكاش المحلي عند انقطاع الشبكة.
 */
class SupabaseApi(
    private val credentialsProvider: () -> SupabaseCredentials = { SupabaseEnv.resolve() },
    private val httpClient: OkHttpClient = defaultHttpClient()
) {

    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    /** هل توجد مفاتيح Supabase صالحة حالياً؟ */
    val isConfigured: Boolean
        get() = credentialsProvider().isUsable

    /** مصدر المفاتيح الحالي (ENVIRONMENT / BUILD_CONFIG / LOCAL_STORAGE ...). */
    val credentialsSource: SupabaseCredentials.Source
        get() = credentialsProvider().source

    // =========================================================================
    // READ
    // =========================================================================

    /**
     * قراءة صفوف من جدول أو view.
     * @param params أزواج اسم/قيمة تُمرَّر كـ query string لصيغة PostgREST
     *               (مثل `"select" to "*"` و `"order" to "created_at.desc"`).
     * @return `null` عند الفشل، وقائمة (قد تكون فارغة) عند النجاح.
     */
    suspend fun <T : Any> selectList(
        table: String,
        params: List<Pair<String, String>>,
        clazz: Class<T>
    ): List<T>? = withContext(Dispatchers.IO) {
        val body = execute(buildGet(table, params)) ?: return@withContext null
        val listType = Types.newParameterizedType(List::class.java, clazz)
        val adapter: JsonAdapter<List<T>> = moshi.adapter(listType)
        runCatching { adapter.fromJson(body) }.getOrNull() ?: return@withContext null
    }

    /** قراءة صف واحد (يُضيف `limit=1` تلقائياً). `null` = فشل أو لا يوجد. */
    suspend fun <T : Any> selectOne(
        table: String,
        params: List<Pair<String, String>>,
        clazz: Class<T>
    ): T? {
        val withLimit = if (params.any { it.first == "limit" }) params else params + ("limit" to "1")
        return selectList(table, withLimit, clazz)?.firstOrNull()
    }

    // =========================================================================
    // WRITE
    // =========================================================================

    /**
     * إدراج صف مع إرجاع الصف المُنشأ (يشمل `id` المولّد من قاعدة البيانات).
     * يعمل على الـ updatable views مثل `public.rooms`.
     * @return الصف المُنشأ، أو `null` عند الفشل.
     */
    suspend fun <T : Any> insertReturning(
        table: String,
        value: T,
        clazz: Class<T>
    ): T? = withContext(Dispatchers.IO) {
        val credentials = credentialsProvider()
        if (!credentials.isUsable) return@withContext null

        val adapter: JsonAdapter<T> = moshi.adapter(clazz)
        val json = runCatching { adapter.toJson(value) }.getOrNull() ?: return@withContext null
        val request = baseRequestBuilder(credentials, table, emptyList())
            .addHeader("Prefer", "return=representation")
            .post(json.toRequestBody(jsonMediaType))
            .build()

        val body = execute(request) ?: return@withContext null
        parseSingleOrArray(body, clazz)
    }

    /** إدراج صف بدون قراءة الرد (أسرع) — يعيد `true` فقط عند نجاح HTTP. */
    suspend fun <T : Any> insert(
        table: String,
        value: T,
        clazz: Class<T>
    ): Boolean = withContext(Dispatchers.IO) {
        val credentials = credentialsProvider()
        if (!credentials.isUsable) return@withContext false

        val adapter: JsonAdapter<T> = moshi.adapter(clazz)
        val json = runCatching { adapter.toJson(value) }.getOrNull() ?: return@withContext false
        val request = baseRequestBuilder(credentials, table, emptyList())
            .addHeader("Prefer", "return=minimal")
            .post(json.toRequestBody(jsonMediaType))
            .build()

        executeForStatus(request)
    }

    /**
     * تعديل صفوف (PATCH) مع شرط تصفية في [params] مثل `"room_code" to "eq.STAD77"`.
     * @param body خريطة الأعمدة الجديدة؛ القيم `null` تُتجاهل أثناء التسلسل.
     */
    suspend fun patch(
        table: String,
        params: List<Pair<String, String>>,
        body: Map<String, Any?>
    ): Boolean = withContext(Dispatchers.IO) {
        val credentials = credentialsProvider()
        if (!credentials.isUsable) return@withContext false
        if (params.isEmpty()) return@withContext false // حماية من تعديل الجدول كاملاً بالخطأ

        val json = encodeMap(body) ?: return@withContext false
        val request = baseRequestBuilder(credentials, table, params)
            .addHeader("Prefer", "return=minimal")
            .patch(json.toRequestBody(jsonMediaType))
            .build()

        executeForStatus(request)
    }

    /** حذف صفوف بشرط تصفية (لا يُنفَّذ بدون شرط حمايةً للبيانات). */
    suspend fun delete(table: String, params: List<Pair<String, String>>): Boolean =
        withContext(Dispatchers.IO) {
            val credentials = credentialsProvider()
            if (!credentials.isUsable) return@withContext false
            if (params.isEmpty()) return@withContext false

            val request = baseRequestBuilder(credentials, table, params)
                .addHeader("Prefer", "return=minimal")
                .delete()
                .build()

            executeForStatus(request)
        }

    // =========================================================================
    // INTERNALS
    // =========================================================================

    private fun baseRequestBuilder(
        credentials: SupabaseCredentials,
        table: String,
        params: List<Pair<String, String>>
    ): Request.Builder {
        val url = buildString {
            append(credentials.restBaseUrl)
            append('/')
            append(table)
            if (params.isNotEmpty()) {
                append('?')
                params.forEachIndexed { index, (key, value) ->
                    if (index > 0) append('&')
                    append(encode(key))
                    append('=')
                    append(encode(value))
                }
            }
        }

        return Request.Builder()
            .url(url)
            .addHeader("apikey", credentials.anonKey)
            .addHeader("Authorization", "Bearer ${credentials.anonKey}")
            .addHeader("Accept", "application/json")
    }

    private fun buildGet(table: String, params: List<Pair<String, String>>): Request {
        val credentials = credentialsProvider()
        return baseRequestBuilder(credentials, table, params).get().build()
    }

    /** ينفّذ الطلب ويعيد نص الاستجابة فقط عند النجاح. */
    private fun execute(request: Request): String? {
        if (!credentialsProvider().isUsable) return null
        return runCatching {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) null else response.body?.string()
            }
        }.getOrNull()
    }

    /** ينفّذ الطلب ويكتفي بفحص كود الحالة (للكتابات return=minimal). */
    private fun executeForStatus(request: Request): Boolean {
        if (!credentialsProvider().isUsable) return false
        return runCatching {
            httpClient.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        }.getOrDefault(false)
    }

    private fun encodeMap(body: Map<String, Any?>): String? {
        val mapType = Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
        val adapter: JsonAdapter<Map<String, Any?>> = moshi.adapter(mapType)
        return runCatching { adapter.toJson(body) }.getOrNull()
    }

    /**
     * PostgREST قد يُعيد كائناً واحداً أو مصفوفة حسب الإصدار وطريقة الإدراج،
     * لذلك نتعامل مع الحالتين معاً.
     */
    private fun <T : Any> parseSingleOrArray(body: String, clazz: Class<T>): T? {
        val trimmed = body.trim()
        if (trimmed.isEmpty()) return null

        return if (trimmed.startsWith("[")) {
            val listType = Types.newParameterizedType(List::class.java, clazz)
            val adapter: JsonAdapter<List<T>> = moshi.adapter(listType)
            runCatching { adapter.fromJson(trimmed)?.firstOrNull() }.getOrNull()
        } else {
            val adapter: JsonAdapter<T> = moshi.adapter(clazz)
            runCatching { adapter.fromJson(trimmed) }.getOrNull()
        }
    }

    /** ترميز آمن لقيم الـ query string (مهم لأن أسماء اللاعبين قد تحتوي مسافات/عربية). */
    private fun encode(value: String): String =
        java.net.URLEncoder.encode(value, "UTF-8").replace("+", "%20")

    companion object {
        fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }
}
