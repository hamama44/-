package com.example.data.room

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * الكاش المحلي (SQLite عبر Room) لغرف اللعب الأونلاين.
 *
 * الهدف: Offline-first — الشاشة تقرأ دائماً من الكاش عبر Flow، والمستودع
 * [GameRoomRepository] هو من يقرر متى يجلب من Supabase ومتى يكتب محلياً فقط.
 */
@Entity(tableName = "cached_rooms")
data class GameRoomEntity(
    /** كود الغرفة هو المفتاح الأساسي (فريد بطبيعته ومقروء للمستخدم). */
    @PrimaryKey val roomCode: String,
    val hostName: String,
    val remoteId: String? = null,
    val gameType: String = GameRoom.DEFAULT_GAME_TYPE,
    /** نخزّن القيمة النصية كما في قاعدة البيانات ("waiting"/"playing"/"finished"). */
    val statusWire: String = "waiting",
    val currentRound: Int = 1,
    val maxRounds: Int = GameRoom.DEFAULT_MAX_ROUNDS,
    val maxPlayers: Int = GameRoom.DEFAULT_MAX_PLAYERS,
    val isPrivate: Boolean = false,
    val region: String = GameRoom.DEFAULT_REGION,
    val participantCount: Int = 0,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val lastSyncedAtMillis: Long = 0L,
    /** `true` = الغرفة أُنشئت أوفلاين وتنتظر الدفع للسيرفر. */
    val isPendingSync: Boolean = false
)

/**
 * مشارك في غرفة. المفتاح [participantKey] = "كود الغرفة|الاسم بصيغة موحّدة"
 * حتى تعمل استراتيجية REPLACE كـ upsert حقيقي أثناء المزامنة.
 */
@Entity(
    tableName = "cached_room_participants",
    indices = [Index(value = ["roomCode"])]
)
data class RoomParticipantEntity(
    @PrimaryKey val participantKey: String,
    val roomCode: String,
    val playerName: String,
    val remoteId: String? = null,
    val avatarEmoji: String = RoomParticipant.DEFAULT_AVATAR,
    val teamColor: String = RoomParticipant.TEAM_RED,
    val score: Int = 0,
    val isReady: Boolean = false,
    val isHost: Boolean = false,
    val joinedAtMillis: Long = System.currentTimeMillis(),
    /** `true` = أُضيف محلياً ولم يصل للسيرفر بعد. */
    val isPendingSync: Boolean = false
)

// =============================================================================
// خرائط التحويل Entity <-> Domain (نقية، بلا أي اعتماد على الشبكة)
// =============================================================================

internal fun GameRoomEntity.toDomain(): GameRoom = GameRoom(
    roomCode = roomCode,
    hostName = hostName,
    gameType = gameType,
    status = RoomStatus.fromWire(statusWire),
    currentRound = currentRound,
    maxRounds = maxRounds,
    maxPlayers = maxPlayers,
    isPrivate = isPrivate,
    region = region,
    participantCount = participantCount,
    remoteId = remoteId,
    createdAtMillis = createdAtMillis,
    isOfflineOnly = isPendingSync
)

internal fun GameRoom.toEntity(
    pendingSync: Boolean,
    lastSyncedAtMillis: Long
): GameRoomEntity = GameRoomEntity(
    roomCode = roomCode,
    hostName = hostName,
    remoteId = remoteId,
    gameType = gameType,
    statusWire = status.wire,
    currentRound = currentRound,
    maxRounds = maxRounds,
    maxPlayers = maxPlayers,
    isPrivate = isPrivate,
    region = region,
    participantCount = participantCount,
    createdAtMillis = if (createdAtMillis > 0L) createdAtMillis else System.currentTimeMillis(),
    lastSyncedAtMillis = lastSyncedAtMillis,
    isPendingSync = pendingSync
)

internal fun RoomParticipantEntity.toDomain(): RoomParticipant = RoomParticipant(
    roomCode = roomCode,
    playerName = playerName,
    avatarEmoji = avatarEmoji,
    teamColor = teamColor,
    score = score,
    isReady = isReady,
    isHost = isHost,
    remoteId = remoteId,
    joinedAtMillis = joinedAtMillis,
    isOfflineOnly = isPendingSync
)

internal fun RoomParticipant.toEntity(pendingSync: Boolean): RoomParticipantEntity =
    RoomParticipantEntity(
        participantKey = localKey,
        roomCode = roomCode,
        playerName = playerName,
        remoteId = remoteId,
        avatarEmoji = avatarEmoji.ifBlank { RoomParticipant.DEFAULT_AVATAR },
        teamColor = teamColor,
        score = score,
        isReady = isReady,
        isHost = isHost,
        joinedAtMillis = if (joinedAtMillis > 0L) joinedAtMillis else System.currentTimeMillis(),
        isPendingSync = pendingSync
    )
