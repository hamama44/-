package com.example.data.supabase

import com.example.data.room.GameRoom
import com.example.data.room.RoomParticipant
import com.example.data.room.RoomStatus
import com.example.data.room.RoomsRemoteDataSource
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * DAO جدول الغرف (Rooms) — الطبقة البعيدة فوق Supabase.
 *
 * يخاطب الـ views الموحّدة المعرفة في `supabase_schema_v2.sql`:
 *  - `public.rooms`              ← القراءة الفردية والكتابة (قابل للتحديث)
 *  - `public.rooms_overview`     ← القوائم مع عدد المشاركين (للقراءة فقط)
 *  - `public.room_participants`  ← المشاركون (قابل للتحديث)
 *
 * ينفّذ [RoomsRemoteDataSource] بنماذج الـ Domain مباشرة، لذلك لا يتسرّب أي
 * نموذج شبكة (SupabaseGameRoom) إلى [com.example.data.room.GameRoomRepository].
 */
class SupabaseRoomsDao(private val api: SupabaseApi) : RoomsRemoteDataSource {

    override suspend fun fetchRooms(limit: Int): List<GameRoom>? =
        api.selectList(
            table = VIEW_ROOMS_OVERVIEW,
            params = listOf(
                "select" to "*",
                "order" to "created_at.desc",
                "limit" to limit.coerceIn(1, MAX_LIMIT).toString()
            ),
            clazz = SupabaseRoomOverview::class.java
        )?.map { it.toDomain() }

    override suspend fun fetchRoomByCode(roomCode: String): GameRoom? {
        val code = roomCode.trim()
        if (code.isEmpty()) return null
        return api.selectOne(
            table = VIEW_ROOMS_OVERVIEW,
            params = listOf("select" to "*", "room_code" to "eq.$code"),
            clazz = SupabaseRoomOverview::class.java
        )?.toDomain()
    }

    override suspend fun createRoom(room: GameRoom): GameRoom? {
        val created = api.insertReturning(
            table = VIEW_ROOMS,
            value = room.toWire(),
            clazz = SupabaseGameRoom::class.java
        ) ?: return null

        // عدد المشاركين لا يأتي من view الكتابة، فنبقي القيمة المعلومة (المضيف = 1)
        return created.toDomain().copy(
            participantCount = room.participantCount,
            createdAtMillis = created.toDomain().createdAtMillis.takeIf { it > 0L }
                ?: room.createdAtMillis
        )
    }

    override suspend fun fetchParticipants(roomCode: String): List<RoomParticipant>? {
        val code = roomCode.trim()
        if (code.isEmpty()) return null
        return api.selectList(
            table = VIEW_ROOM_PARTICIPANTS,
            params = listOf(
                "select" to "*",
                "room_code" to "eq.$code",
                "order" to "joined_at.asc"
            ),
            clazz = SupabaseRoomPlayer::class.java
        )?.map { it.toDomain() }
    }

    override suspend fun addParticipant(participant: RoomParticipant): Boolean =
        api.insert(
            table = VIEW_ROOM_PARTICIPANTS,
            value = participant.toWire(),
            clazz = SupabaseRoomPlayer::class.java
        )

    override suspend fun updateParticipant(participant: RoomParticipant): Boolean =
        api.patch(
            table = VIEW_ROOM_PARTICIPANTS,
            params = listOf(
                "room_code" to "eq.${participant.roomCode}",
                "player_name" to "eq.${participant.playerName}"
            ),
            // لا نرسل last_ping هنا عمداً: قيمته timestamptz وإرسال نص مثل "now()"
            // يعتمد على استنتاج النوع لدى PostgREST. العمود يأخذ قيمته عند الإدراج.
            body = mapOf(
                "team_color" to participant.teamColor,
                "score" to participant.score,
                "is_ready" to participant.isReady,
                "is_host" to participant.isHost,
                "avatar_emoji" to participant.avatarEmoji
            )
        )

    override suspend fun updateRoomState(
        roomCode: String,
        status: RoomStatus,
        currentRound: Int
    ): Boolean = api.patch(
        table = VIEW_ROOMS,
        params = listOf("room_code" to "eq.${roomCode.trim()}"),
        body = mapOf(
            "status" to status.wire,
            "current_round" to currentRound
        )
    )

    companion object {
        const val VIEW_ROOMS = "rooms"
        const val VIEW_ROOMS_OVERVIEW = "rooms_overview"
        const val VIEW_ROOM_PARTICIPANTS = "room_participants"
        private const val MAX_LIMIT = 100
    }
}

// =============================================================================
// خرائط التحويل: نماذج الشبكة (Wire) <-> نماذج الـ Domain
// =============================================================================

internal fun SupabaseRoomOverview.toDomain(): GameRoom = GameRoom(
    roomCode = roomCode,
    hostName = hostName,
    gameType = gameType,
    status = RoomStatus.fromWire(status),
    currentRound = currentRound,
    maxRounds = maxRounds,
    maxPlayers = maxPlayers,
    isPrivate = isPrivate,
    region = region,
    participantCount = participantCount,
    remoteId = id,
    createdAtMillis = parseSupabaseTimestamp(createdAt),
    isOfflineOnly = false
)

internal fun SupabaseGameRoom.toDomain(): GameRoom = GameRoom(
    roomCode = roomCode,
    hostName = hostName,
    gameType = gameType,
    status = RoomStatus.fromWire(status),
    currentRound = currentRound,
    maxRounds = maxRounds,
    maxPlayers = maxPlayers,
    isPrivate = isPrivate,
    region = region,
    participantCount = 0,
    remoteId = id,
    createdAtMillis = parseSupabaseTimestamp(createdAt),
    isOfflineOnly = false
)

internal fun GameRoom.toWire(): SupabaseGameRoom = SupabaseGameRoom(
    // id = null يجعل Moshi يحذف الحقل، فتولّد قاعدة البيانات UUID تلقائياً
    id = remoteId,
    roomCode = roomCode,
    gameType = gameType,
    status = status.wire,
    currentRound = currentRound,
    maxRounds = maxRounds,
    maxPlayers = maxPlayers,
    isPrivate = isPrivate,
    region = region,
    hostName = hostName
)

internal fun SupabaseRoomPlayer.toDomain(): RoomParticipant = RoomParticipant(
    roomCode = roomCode,
    playerName = playerName,
    avatarEmoji = avatarEmoji,
    teamColor = teamColor,
    score = score,
    isReady = isReady,
    isHost = isHost,
    remoteId = id,
    joinedAtMillis = parseSupabaseTimestamp(joinedAt),
    isOfflineOnly = false
)

internal fun RoomParticipant.toWire(): SupabaseRoomPlayer = SupabaseRoomPlayer(
    id = remoteId,
    roomCode = roomCode,
    playerName = playerName,
    avatarEmoji = avatarEmoji,
    teamColor = teamColor,
    score = score,
    isReady = isReady,
    isHost = isHost
)

/**
 * تحويل طابع Supabase الزمني (TIMESTAMPTZ) إلى millis.
 *
 * نتجنب `java.time` لأن minSdk = 24 (يحتاج 26 أو desugaring)، ونستخدم
 * [SimpleDateFormat] مع عدة أنماط لأن PostgreSQL يُعيد صيغاً متعددة مثل:
 * `2026-10-17T18:15:30.123456+00:00` أو `2026-10-17T18:15:30Z`.
 *
 * @return الطابع بالمللي ثانية، أو `0L` إذا تعذّر التحليل.
 */
fun parseSupabaseTimestamp(raw: String?): Long {
    if (raw.isNullOrBlank()) return 0L

    val normalized = raw.trim()
        .replace("Z", "+0000")
        .replace(Regex("(\\.\\d{3})\\d*"), "$1") // 6 خانات كسرية → 3 (مللي)
        .replace(Regex("([+-]\\d{2}):(\\d{2})$"), "$1$2") // ‎+00:00 → +0000

    TIMESTAMP_PATTERNS.forEach { pattern ->
        val parsed = runCatching {
            val format = SimpleDateFormat(pattern, Locale.US)
            format.timeZone = UTC
            // صارم: نرفض التواريخ غير المنطقية (مثل شهر 13) بدل تدويرها بهدوء
            format.isLenient = false
            format.parse(normalized)?.time
        }.getOrNull()
        if (parsed != null) return parsed
    }
    return 0L
}

private val UTC: TimeZone = TimeZone.getTimeZone("UTC")

private val TIMESTAMP_PATTERNS = listOf(
    "yyyy-MM-dd'T'HH:mm:ss.SSSZ",
    "yyyy-MM-dd'T'HH:mm:ssZ",
    "yyyy-MM-dd'T'HH:mm:ss.SSS",
    "yyyy-MM-dd'T'HH:mm:ss",
    "yyyy-MM-dd HH:mm:ss.SSS",
    "yyyy-MM-dd HH:mm:ss"
)
