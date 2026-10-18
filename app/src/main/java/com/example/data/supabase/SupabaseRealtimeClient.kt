package com.example.data.supabase

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit
import kotlin.math.min

/**
 * عميل Supabase Realtime (Postgres Changes) فوق WebSocket.
 *
 * بيستبدل الانتظار الدوري (polling) بدفع لحظي: أي UPDATE/INSERT على صفوف
 * الغرفة يوصل للتطبيق في نفس اللحظة. بروتوكول Phoenix المستخدم هنا:
 *
 *  1. فتح `wss://<ref>.supabase.co/realtime/v1/websocket?apikey=..&vsn=1.0.0`
 *  2. `phx_join` على topic فيه الفلتر + `config.postgres_changes`
 *  3. نبضات `phx_heartbeat` كل [HEARTBEAT_INTERVAL_MS] عشان الاتصال مايقطعش
 *  4. ردود `postgres_changes` تُترجم لـ[RealtimeEvent] وتُمرَّر لـ[onEvent]
 *
 * ملاحظات أمان/متانة:
 *  - المفتاح المجهّز للـWebSocket هو **Anon key** العام، وRLS على الجدول هو
 *    اللي بيحدد ما يراه العميل (انظر supabase_schema_v2.sql).
 *  - أي فشل شبكة ⇒ إغلاق + إعادة اتصال بتراجع أُسّي [nextReconnectDelayMs]
 *    بدل ما تقعد الشاشة على منظر قديم.
 *  - [onEvent] بيُستدعى من خيط OkHttp، فالمستمع لازم يعمل marshal بنفسه.
 */
class SupabaseRealtimeClient(
    private val credentialsProvider: () -> SupabaseCredentials = { SupabaseEnv.resolve() },
    private val httpClient: OkHttpClient = defaultHttpClient(),
    private val onEvent: (RealtimeEvent) -> Unit = {}
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var webSocket: WebSocket? = null

    private var heartbeatJob: Job? = null
    private var reconnectJob: Job? = null

    /** الموضوع المشترك عليه حالياً، أو null لو مش مشترك. */
    @Volatile
    private var activeTopic: String? = null

    /** رقم المتصل — Phoenix بيتوقع ref متزايد كسلسلة. */
    @Volatile
    private var refCounter = 0

    @Volatile
    private var manuallyClosed = false

    private var reconnectAttempt = 0

    private val _isConnected = MutableStateFlow(false)

    /** هل الاتصال + الاشتراك شغالين دلوقتي؟ */
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)

    /** وصف عربي لحالة Realtime — يُعرض في الواجهة بدل رقم وهمي. */
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    /** عدد الأحداث اللحظية المستقبَلة (مفيد للتشخيص وللتأكد إن الدفع شغال). */
    @Volatile
    var receivedEventCount: Int = 0
        private set

    private val moshi: Moshi = Moshi.Builder().build()
    private val inboundAdapter = moshi.adapter(RealtimeInbound::class.java)
    private val outboundAdapter = moshi.adapter(RealtimeOutbound::class.java)

    // ------------------------------------------------------------------
    // الاشتراك
    // ------------------------------------------------------------------

    /**
     * الاشتراك في تغييرات جدول `game_rooms` الخاصة بكود غرفة واحد.
     *
     * بيقفل أي اشتراك سابق (غرفة واحدة في المرة). يعيد false لو مفاتيح
     * Supabase مش مُهيّأة — فمستدعيها يرجع للـpolling الاحتياطي.
     */
    fun subscribeToRoom(roomCode: String): Boolean {
        val code = roomCode.trim().uppercase()
        if (code.isEmpty()) return false

        val credentials = credentialsProvider()
        if (!credentials.isUsable) {
            _statusMessage.value = "Realtime محظور: مفاتيح Supabase مش مُهيّأة 🔌"
            _isConnected.value = false
            return false
        }

        val url = realtimeWebSocketUrl(credentials.url, credentials.anonKey)
        if (url == null) {
            _statusMessage.value = "Realtime محظور: رابط Supabase غير صالح 🔌"
            _isConnected.value = false
            return false
        }

        // اشتراك جديد = نغلق القديم ونبدأ من الصفر
        closeSocket()
        manuallyClosed = false
        reconnectAttempt = 0
        activeTopic = roomTopic(REALTIME_SCHEMA, ROOM_TABLE, code)

        val request = Request.Builder()
            .url(url)
            .addHeader("apikey", credentials.anonKey)
            .build()

        return try {
            webSocket = httpClient.newWebSocket(request, listener())
            startHeartbeat()
            true
        } catch (_: Exception) {
            _statusMessage.value = "تعذّر فتح اتصال Realtime — هيكفي التحديث الدوري ⏳"
            scheduleReconnect()
            false
        }
    }

    /**
     * مسح رسالة الحالة.
     *
     * بيُستدعى لما الجلسة تنتهي أو لما نكون في وضع لا يدعم الاشتراك اللحظي
     * (الهوتسبوت مثلاً)، عشان ما تفضلش رسالة قديمة معروضة للاعب.
     */
    fun clearStatus() {
        _statusMessage.value = null
    }

    /** إلغاء الاشتراك وإغلاق الاتصال. */
    fun unsubscribe() {
        manuallyClosed = true
        activeTopic = null
        stopHeartbeat()
        closeSocket()
        reconnectJob?.cancel()
        reconnectJob = null
        _isConnected.value = false
        _statusMessage.value = "إلغاء الاشتراك اللحظي ⏸️"
    }

    /** تحرير كل الموارد (يُستدعى من onCleared في الـViewModel). */
    fun dispose() {
        unsubscribe()
        runCatching { scope.cancel() }
    }

    // ------------------------------------------------------------------
    // الرسائل
    // ------------------------------------------------------------------

    /**
     * رسالة `phx_join` لاشتراك Postgres Changes.
     *
     * دي المصدر الوحيد لبناء الرسالة (بتُستدعى من `onOpen`) عشان الفلتر
     * واسم الموضوع ما يفترقوش. الفلتر بيستخرج كود الغرفة من اسم الموضوع
     * بدل ما يستخدم الاسم كله — الغلط ده كان هيخلي الاشتراك على صف وهمي.
     */
    internal fun buildJoinMessage(topic: String, ref: String, anonKey: String): String =
        outboundAdapter.toJson(
            RealtimeOutbound(
                topic = topic,
                event = EVENT_JOIN,
                ref = ref,
                payload = RealtimeJoinPayload(
                    config = RealtimeJoinConfig(
                        postgresChanges = listOf(
                            RealtimeChangeRequest(
                                event = CHANGE_EVENT_ALL,
                                schema = REALTIME_SCHEMA,
                                table = ROOM_TABLE,
                                filter = "$ROOM_FILTER_COLUMN=eq.${extractRoomCode(topic)}"
                            )
                        )
                    ),
                    // بدون توكن بترفض Supabase الاشتراك في جدول فيه RLS
                    accessToken = anonKey.takeIf { it.isNotBlank() }
                )
            )
        )

    /** رسالة نبضة `phx_heartbeat`. */
    internal fun buildHeartbeatMessage(ref: String): String = outboundAdapter.toJson(
        RealtimeOutbound(
            topic = PHOENIX_TOPIC,
            event = EVENT_HEARTBEAT,
            ref = ref,
            payload = RealtimeJoinPayload()
        )
    )

    private fun listener() = object : WebSocketListener() {

        override fun onOpen(webSocket: WebSocket, response: Response) {
            val topic = activeTopic ?: return
            refCounter += 1

            val message = buildJoinMessage(topic, refCounter.toString(), credentialsProvider().anonKey)
            val sent = runCatching { webSocket.send(message) }.getOrDefault(false)
            if (!sent) {
                _statusMessage.value = "تعذّر إرسال طلب الاشتراك — هعيد المحاولة 🔁"
                scheduleReconnect()
                return
            }

            _isConnected.value = true
            _statusMessage.value = "اتصال لحظي شغّال ⚡"
            reconnectAttempt = 0
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            val inbound = runCatching { inboundAdapter.fromJson(text) }.getOrNull() ?: return

            when (inbound.event) {
                EVENT_REPLY -> {
                    val status = inbound.payload?.status
                    if (status == REPLY_STATUS_ERROR) {
                        _statusMessage.value = "السيرفر رفض الاشتراك اللحظي — التحديث الدوري شغّال ⏳"
                    }
                }
                EVENT_POSTGRES_CHANGES -> {
                    receivedEventCount += 1
                    val data = inbound.payload?.data
                    onEvent(
                        RealtimeEvent(
                            type = data?.type ?: CHANGE_EVENT_ALL,
                            table = data?.table ?: ROOM_TABLE,
                            topic = inbound.topic
                        )
                    )
                }
            }
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            runCatching { webSocket.close(NORMAL_CLOSE_CODE, null) }
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            _isConnected.value = false
            if (!manuallyClosed && activeTopic != null) {
                _statusMessage.value = "الاتصال اللحظي اتقفل — هعيد الاتصال 🔁"
                scheduleReconnect()
            }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            _isConnected.value = false
            if (!manuallyClosed && activeTopic != null) {
                _statusMessage.value = "انقطع الاتصال اللحظي (${t.javaClass.simpleName}) — إعادة محاولة 🔁"
                scheduleReconnect()
            }
        }
    }

    // ------------------------------------------------------------------
    // النبضات وإعادة الاتصال
    // ------------------------------------------------------------------

    private fun startHeartbeat() {
        stopHeartbeat()
        heartbeatJob = scope.launch {
            while (isActive) {
                delay(HEARTBEAT_INTERVAL_MS)
                val socket = webSocket ?: break
                refCounter += 1
                val sent = runCatching { socket.send(buildHeartbeatMessage(refCounter.toString())) }.getOrDefault(false)
                if (!sent) break
            }
        }
    }

    private fun stopHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = null
    }

    private fun closeSocket() {
        stopHeartbeat()
        val socket = webSocket
        webSocket = null
        runCatching { socket?.close(NORMAL_CLOSE_CODE, null) }
    }

    /** إعادة اتصال بنفس الـtopic بعد تراجع أُسّي. */
    private fun scheduleReconnect() {
        reconnectJob?.cancel()
        val topic = activeTopic ?: return
        reconnectJob = scope.launch {
            reconnectAttempt += 1
            val waitMs = nextReconnectDelayMs(reconnectAttempt)
            if (waitMs == Long.MAX_VALUE) {
                // استنزفنا المحاولات: نسلّم للتحديث الدوري بدل تعليق الكوروتين
                // على delay لأبد الآبد (بيسيب شغل معلق في المتتبع)
                _isConnected.value = false
                _statusMessage.value = "الاشتراك اللحظي وقف — التحديث الدوري شغّال ⏳"
                return@launch
            }
            delay(waitMs)
            if (!isActive || manuallyClosed || activeTopic != topic) return@launch

            val credentials = credentialsProvider()
            if (!credentials.isUsable) return@launch
            val url = realtimeWebSocketUrl(credentials.url, credentials.anonKey) ?: return@launch

            val request = Request.Builder().url(url).addHeader("apikey", credentials.anonKey).build()
            runCatching { webSocket = httpClient.newWebSocket(request, listener()) }
                .onFailure {
                    _statusMessage.value = "إعادة الاتصال اللحظي فشلت — التحديث الدوري شغّال ⏳"
                }
        }
    }

    companion object {
        const val REALTIME_SCHEMA = "public"
        const val ROOM_TABLE = "game_rooms"
        const val ROOM_FILTER_COLUMN = "room_code"

        const val PHOENIX_TOPIC = "phoenix"
        const val EVENT_JOIN = "phx_join"
        const val EVENT_LEAVE = "phx_leave"
        const val EVENT_HEARTBEAT = "phx_heartbeat"
        const val EVENT_REPLY = "phx_reply"
        const val EVENT_POSTGRES_CHANGES = "postgres_changes"
        const val REPLY_STATUS_ERROR = "error"
        const val CHANGE_EVENT_ALL = "*"
        const val NORMAL_CLOSE_CODE = 1000

        /** فترة النبضة — Supabase بيقفل الاتصال الصامت بعد ~60 ثانية. */
        const val HEARTBEAT_INTERVAL_MS = 25_000L

        /** أساس التراجع عند إعادة الاتصال (مللي ثانية). */
        const val RECONNECT_BASE_DELAY_MS = 1_000L

        /** سقف التراجع — ما ينفعش نستنى دقايق قبل ما رجّع للاعب حاجة. */
        const val RECONNECT_MAX_DELAY_MS = 20_000L

        /** أقصى عدد محاولات إعادة الاتصال قبل الاعتماد على الـpolling. */
        const val MAX_RECONNECT_ATTEMPTS = 8

        private const val WEBSOCKET_PATH = "/realtime/v1/websocket"
        private const val VSN = "1.0.0"

        /**
         * بناء رابط الـWebSocket من رابط المشروع.
         *
         * بيحوّل `http`→`ws` و `https`→`wss`، بيتجاهل أي مسار `/rest/v1`
         * زائد، وبيضيف `apikey` و `vsn`. يعيد null لو المدخل مش صالح —
         * فمستدعيه يرجع للتحديث الدوري بدل يفشل بمفاجأة.
         */
        fun realtimeWebSocketUrl(baseUrl: String, anonKey: String): String? {
            val trimmed = baseUrl.trim().trimEnd('/')
            if (trimmed.isEmpty()) return null
            if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) return null
            if (anonKey.isBlank()) return null

            // نشيل أي مسار (مثل /rest/v1) عشان نبقى على جذر المشروع
            val withoutTrailingPath = trimmed
                .substringBefore(SupabaseCredentials.REST_PATH)
                .trimEnd('/')

            val scheme = if (withoutTrailingPath.startsWith("https://")) "wss://" else "ws://"
            val host = withoutTrailingPath.substringAfter("://")
            if (host.isBlank() || host.contains(' ')) return null

            return "$scheme$host$WEBSOCKET_PATH?apikey=$anonKey&vsn=$VSN"
        }

        /**
         * اسم الموضوع بصيغة Supabase:
         * `realtime:<schema>-<table>-eq.<column>.<value>`
         */
        fun roomTopic(schema: String, table: String, roomCode: String): String =
            "realtime:$schema-$table-eq.$ROOM_FILTER_COLUMN.${roomCode.trim().uppercase()}"

        /** استخراج كود الغرفة من اسم الموضوع (يُستخدم في إعادة الاتصال). */
        fun extractRoomCode(topic: String): String =
            topic.substringAfterLast('.', topic)

        /**
         * مدة الانتظار قبل محاولة إعادة الاتصال رقم [attempt].
         *
         * تراجع أُسّي مقصوص لـ[RECONNECT_MAX_DELAY_MS]، وبعد
         * [MAX_RECONNECT_ATTEMPTS] بنرجّع [Long.MAX_VALUE] دلالة إننا بنسلّم
         * للتحديث الدوري بدل استنزاف البطارية في محاولات فاشلة.
         */
        fun nextReconnectDelayMs(attempt: Int): Long {
            if (attempt <= 0) return RECONNECT_BASE_DELAY_MS
            if (attempt > MAX_RECONNECT_ATTEMPTS) return Long.MAX_VALUE
            val backoff = RECONNECT_BASE_DELAY_MS * (1L shl (attempt - 1).coerceAtMost(16))
            return min(backoff, RECONNECT_MAX_DELAY_MS)
        }

        /** هل المفاتيح تسمح باستخدام Realtime أصلاً؟ */
        fun isAvailable(credentials: SupabaseCredentials): Boolean =
            credentials.isUsable && realtimeWebSocketUrl(credentials.url, credentials.anonKey) != null

        /** عميل مخصص للـWebSocket: بلا مهلة قراءة، فال اتصال طويل طبيعي. */
        fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS) // اتصال WebSocket مفتوح
            .writeTimeout(10, TimeUnit.SECONDS)
            .pingInterval(0, TimeUnit.SECONDS) // النبضات بإيدنا عبر phx_heartbeat
            .build()
    }
}

/** حدث تغيير واحد وصل من Supabase Realtime. */
data class RealtimeEvent(
    val type: String,
    val table: String,
    val topic: String?
) {
    val isRoomChange: Boolean get() = table.equals(SupabaseRealtimeClient.ROOM_TABLE, ignoreCase = true)

    /** وصف عربي مختصر لنوع التغيير. */
    val arabicLabel: String
        get() = when (type.uppercase()) {
            "INSERT" -> "اتضاف صف جديد 🆕"
            "UPDATE" -> "تحديث في البيانات 🔄"
            "DELETE" -> "اتحذف صف 🗑️"
            else -> "تغيير 🔄"
        }
}

// ----------------------------------------------------------------------
// نماذج الرسائل (خارج/داخل)
// ----------------------------------------------------------------------

/** رسالة صادرة لـ Phoenix. */
@JsonClass(generateAdapter = true)
internal data class RealtimeOutbound(
    @Json(name = "topic") val topic: String,
    @Json(name = "event") val event: String,
    @Json(name = "ref") val ref: String?,
    @Json(name = "payload") val payload: RealtimeJoinPayload
)

@JsonClass(generateAdapter = true)
internal data class RealtimeJoinPayload(
    @Json(name = "config") val config: RealtimeJoinConfig? = null,
    @Json(name = "access_token") val accessToken: String? = null
)

@JsonClass(generateAdapter = true)
internal data class RealtimeJoinConfig(
    @Json(name = "postgres_changes") val postgresChanges: List<RealtimeChangeRequest>
)

@JsonClass(generateAdapter = true)
internal data class RealtimeChangeRequest(
    @Json(name = "event") val event: String,
    @Json(name = "schema") val schema: String,
    @Json(name = "table") val table: String,
    @Json(name = "filter") val filter: String? = null
)

/** رسالة واردة. الحقول اللي مش محتاجينها (record/columns/ids) بتتجاهل. */
@JsonClass(generateAdapter = true)
internal data class RealtimeInbound(
    @Json(name = "topic") val topic: String? = null,
    @Json(name = "event") val event: String? = null,
    @Json(name = "ref") val ref: String? = null,
    @Json(name = "payload") val payload: RealtimeInboundPayload? = null
)

@JsonClass(generateAdapter = true)
internal data class RealtimeInboundPayload(
    @Json(name = "status") val status: String? = null,
    @Json(name = "data") val data: RealtimeChangeData? = null
)

@JsonClass(generateAdapter = true)
internal data class RealtimeChangeData(
    @Json(name = "type") val type: String? = null,
    @Json(name = "schema") val schema: String? = null,
    @Json(name = "table") val table: String? = null
)
