package com.example.data.lan

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi

/**
 * بروتوكول الشبكة المحلية / الهوتسبوت.
 *
 * التصميم: **JSON على سطر واحد** (`\n` كفاصل) فوق TCP للأوامر، و UDP
 * للبثّ التلقائي (اكتشاف السيرفر بدون معرفة الـIP).
 *
 * ليه مش Moshi كائنات متداخلة كتيرة؟ لأن الرسالة المسطّحة أسهل في الإصدار
 * والتوافق: الجهاز القديم بيتجاهل الحقول اللي مش عارفها.
 *
 * الملف ده **نقي تماماً** (مفيش Android ولا Sockets) ⇒ قابل للاختبار على JVM.
 */

/** أنواع الرسائل المتبادلة بين السيرفر (المضيف) والعملاء. */
object LanMessageType {
    // من المضيف للعميل
    const val STATE = "state"           // لقطة كاملة لحالة الغرفة
    const val ACK = "ack"               // نجاح الأمر
    const val ERROR = "error"           // فشل الأمر + السبب
    const val HELLO = "hello"           // بثّ UDP للتعريف بالسيرفر
    const val BYE = "bye"               // السيرفر هيقفل
    const val PING = "ping"             // نبضة دورية: تكشف الاتصال الميت

    // من العميل للمضيف
    const val DISCOVER = "discover"     // بثّ UDP: "فيه سيرفر هنا؟"
    const val QUERY = "query"           // اطلب الحالة الحالية
    const val CREATE_ROOM = "create_room"
    const val JOIN = "join"
    const val LEAVE = "leave"
    const val READY = "ready"
    const val SCORE = "score"
    const val START = "start"
    const val NEXT_ROUND = "next_round"
    const val FINISH = "finish"
}

/**
 * رسالة البروتوكول الموحّدة.
 *
 * كل الحقول اختيارية ما عدا [type]؛ كل نوع رسالة بيستخدم مجموعة منها.
 * [protocolVersion] بيتبعت دايماً عشان المضيف يرفض الإصدارات غير المتوافقة.
 */
@JsonClass(generateAdapter = true)
data class LanMessage(
    @Json(name = "type") val type: String,
    @Json(name = "protocol_version") val protocolVersion: Int = LanProtocol.VERSION,

    // بيانات الغرفة
    @Json(name = "room_code") val roomCode: String? = null,
    @Json(name = "host_name") val hostName: String? = null,
    @Json(name = "game_type") val gameType: String? = null,
    @Json(name = "status") val status: String? = null,
    @Json(name = "current_round") val currentRound: Int? = null,
    @Json(name = "max_rounds") val maxRounds: Int? = null,
    @Json(name = "max_players") val maxPlayers: Int? = null,
    @Json(name = "region") val region: String? = null,

    // بيانات اللاعب
    @Json(name = "player_name") val playerName: String? = null,
    @Json(name = "avatar_emoji") val avatarEmoji: String? = null,
    @Json(name = "team_color") val teamColor: String? = null,
    @Json(name = "score") val score: Int? = null,
    @Json(name = "is_ready") val isReady: Boolean? = null,
    @Json(name = "is_host") val isHost: Boolean? = null,

    // بيانات الشبكة (للاكتشاف عبر UDP)
    @Json(name = "host_ip") val hostIp: String? = null,
    @Json(name = "port") val port: Int? = null,
    @Json(name = "player_count") val playerCount: Int? = null,

    // قائمة المشاركين داخل رسالة STATE
    @Json(name = "participants") val participants: List<LanParticipant>? = null,

    // نتيجة الأمر
    @Json(name = "ok") val ok: Boolean? = null,
    @Json(name = "error") val error: String? = null
) {
    /** هل الرسالة من إصدار بروتوكول متوافق؟ */
    val isCompatibleVersion: Boolean get() = protocolVersion == LanProtocol.VERSION
}

/** مشارك واحد داخل لقطة الحالة — مطابق لحقول [com.example.data.room.RoomParticipant]. */
@JsonClass(generateAdapter = true)
data class LanParticipant(
    @Json(name = "player_name") val playerName: String,
    @Json(name = "avatar_emoji") val avatarEmoji: String = "⚽",
    @Json(name = "team_color") val teamColor: String = "red",
    @Json(name = "score") val score: Int = 0,
    @Json(name = "is_ready") val isReady: Boolean = false,
    @Json(name = "is_host") val isHost: Boolean = false
)

/** سيرفر شبكة محلية مكتشف عبر البثّ التلقائي (UDP). */
data class DiscoveredHost(
    val hostIp: String,
    val port: Int,
    val hostName: String,
    val roomCode: String?,
    val gameType: String?,
    val playerCount: Int,
    val discoveredAt: Long
) {
    /** وصف عربي يظهر في قائمة السيرفرات المكتشفة. */
    val arabicLabel: String
        get() = buildString {
            append(hostName.ifBlank { "سيرفر مجهول" })
            append(" • ")
            append(hostIp)
            if (!roomCode.isNullOrBlank()) {
                append(" • غرفة ")
                append(roomCode)
            }
            append(" • ")
            append(playerCount)
            append(" لاعب")
        }
}

/**
 * ترميز/فك ترميز رسائل البروتوكول.
 *
 * دوال نقية: [encode] تعيد JSON في سطر واحد (بدون `\n` داخلي)، و[decode]
 * تعيد null عند أي خطأ بدل رمي استثناء — لأن الشبكة المحلية غير موثوقة
 * والسيرفر لازم ميقعش بسبب رسالة تالفة من جهاز تاني.
 */
object LanProtocol {
    /** إصدار البروتوكول. زوّده عند أي تغيير غير متوافق. */
    const val VERSION = 1

    /** بورت بثّ الاكتشاف (UDP). */
    const val DISCOVERY_PORT = 47777

    /** بورت اللعب الافتراضي (TCP). */
    const val DEFAULT_GAME_PORT = 47778

    /** عنوان البثّ المحدود — يشتغل على الهوتسبوت وعلى الواي فاي المنزلي. */
    const val BROADCAST_ADDRESS = "255.255.255.255"

    /** فاصل الرسائل: سطر جديد. */
    const val DELIMITER = "\n"

    /** أقصى حجم رسالة مقبول (حماية من ذاكرة منفوخة برسالة تالفة). */
    const val MAX_MESSAGE_LENGTH = 64 * 1024

    private val moshi: Moshi = Moshi.Builder().build()
    private val messageAdapter = moshi.adapter(LanMessage::class.java)

    /** ترميز رسالة إلى JSON في سطر واحد. */
    fun encode(message: LanMessage): String = messageAdapter.toJson(message)

    /** فك ترميز JSON إلى رسالة، أو null لو تالفة/غير متوافقة. */
    fun decode(raw: String?): LanMessage? {
        val trimmed = raw?.trim()
        if (trimmed.isNullOrEmpty()) return null
        if (trimmed.length > MAX_MESSAGE_LENGTH) return null
        return runCatching { messageAdapter.fromJson(trimmed) }.getOrNull()
    }

    /** رسالة تعريف بالسيرفر للبثّ عبر UDP. */
    fun hello(hostIp: String, port: Int, hostName: String, roomCode: String?, gameType: String?, playerCount: Int) =
        LanMessage(
            type = LanMessageType.HELLO,
            hostIp = hostIp,
            port = port,
            hostName = hostName,
            roomCode = roomCode,
            gameType = gameType,
            playerCount = playerCount
        )

    /** رسالة طلب اكتشاف (يبثّها العميل). */
    fun discover() = LanMessage(type = LanMessageType.DISCOVER)

    /**
     * هل عنوان IP صالح للاستخدام كسيرفر؟
     *
     * يمنع العناوين الفارغة/المحجوزة اللي بتفشل الاتصال فوراً، ويعطي رسالة
     * خطأ عربية واضحة بدل استثناء Socket غامض.
     */
    fun validateHostAddress(raw: String?): String? {
        val ip = raw?.trim().orEmpty()
        if (ip.isEmpty()) return "اكتب عنوان الـIP أولاً 🔌"
        if (ip.equals("localhost", ignoreCase = true)) return "استخدم 127.0.0.1 أو عنوان الجهاز نفسه 📶"
        if (ip.contains(' ')) return "عنوان الـIP مفيهوش مسافات 📶"

        val parts = ip.split('.')
        if (parts.size != 4) return "عنوان IPv4 لازم يكون 4 أرقام مفصولة بنقطة (مثال: 192.168.43.1) 📶"

        for (part in parts) {
            if (part.isEmpty() || part.length > 3) return "جزء غير صالح في العنوان: $part 📶"
            val value = part.toIntOrNull() ?: return "جزء غير رقمي في العنوان: $part 📶"
            if (value !in 0..255) return "كل جزء لازم يكون بين 0 و 255 📶"
        }
        return null
    }

    /** هل البورت صالح؟ */
    fun validatePort(port: Int): String? =
        if (port in 1024..65535) null else "البورت لازم يكون بين 1024 و 65535 🔌"
}
