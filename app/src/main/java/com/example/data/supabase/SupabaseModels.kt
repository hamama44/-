package com.example.data.supabase

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class SupabaseQuestion(
    @Json(name = "id") val id: String,
    @Json(name = "game_type") val gameType: String,
    @Json(name = "question") val question: String,
    @Json(name = "options") val options: List<String> = emptyList(),
    @Json(name = "correct_index") val correctIndex: Int = 0,
    @Json(name = "difficulty") val difficulty: String? = "medium",
    @Json(name = "category") val category: String? = "عامة",
    @Json(name = "explanation") val explanation: String? = null,
    /**
     * مصدر السؤال: `seed` | `bot` | `curated` | `admin`.
     * بيتستخدم للتمييز في الواجهة وللتحليل، و`null` لو الـview مابيعرضوش.
     */
    @Json(name = "source") val source: String? = null,
    /** وقت النشر الفعلي — أساس ترتيب «الأحدث الأول» في كاش التطبيق. */
    @Json(name = "published_at") val publishedAt: String? = null
)

@JsonClass(generateAdapter = true)
data class SupabasePlayer(
    @Json(name = "id") val id: String,
    @Json(name = "name") val name: String,
    @Json(name = "nationality") val nationality: String? = null,
    @Json(name = "current_club") val currentClub: String? = null,
    @Json(name = "position") val position: String? = null,
    @Json(name = "shirt_number") val shirtNumber: Int? = null
)

@JsonClass(generateAdapter = true)
data class SupabaseScore(
    @Json(name = "id") val id: String? = null,
    @Json(name = "player_name") val playerName: String,
    @Json(name = "points") val points: Int,
    @Json(name = "coins") val coins: Int,
    @Json(name = "level_title") val levelTitle: String? = "مشجع هاوٍ",
    @Json(name = "favorite_club") val favoriteClub: String? = "ريال مدريد",
    /** نوع التحدي (العمود المضاف في supabase_schema_v2.sql) — له قيمة افتراضية في القاعدة. */
    @Json(name = "challenge_type") val challengeType: String? = "general"
)

@JsonClass(generateAdapter = true)
data class SupabaseGameRoom(
    @Json(name = "id") val id: String? = null,
    @Json(name = "room_code") val roomCode: String,
    @Json(name = "game_type") val gameType: String = "thirty_seconds",
    @Json(name = "status") val status: String = "waiting", // "waiting", "playing", "finished"
    @Json(name = "current_round") val currentRound: Int = 1,
    @Json(name = "max_rounds") val maxRounds: Int = 5,
    @Json(name = "host_name") val hostName: String,
    // --- أعمدة supabase_schema_v2.sql (كلها اختيارية حتى يعمل النموذج على المخطط القديم أيضاً)
    @Json(name = "max_players") val maxPlayers: Int = 6,
    @Json(name = "is_private") val isPrivate: Boolean = false,
    @Json(name = "region") val region: String = "MENA",
    @Json(name = "created_at") val createdAt: String? = null,
    @Json(name = "updated_at") val updatedAt: String? = null
)

/**
 * صف من view `public.rooms_overview` (للقراءة فقط): غرفة + عدد المشاركين.
 *
 * نفصلها عن [SupabaseGameRoom] عمداً، لأن إرسال `participant_count` أثناء INSERT
 * في view الكتابة `public.rooms` سيرفضه PostgREST (حقل غير موجود هناك).
 */
@JsonClass(generateAdapter = true)
data class SupabaseRoomOverview(
    @Json(name = "id") val id: String? = null,
    @Json(name = "room_code") val roomCode: String,
    @Json(name = "game_type") val gameType: String = "thirty_seconds",
    @Json(name = "status") val status: String = "waiting",
    @Json(name = "current_round") val currentRound: Int = 1,
    @Json(name = "max_rounds") val maxRounds: Int = 5,
    @Json(name = "max_players") val maxPlayers: Int = 6,
    @Json(name = "is_private") val isPrivate: Boolean = false,
    @Json(name = "region") val region: String = "MENA",
    @Json(name = "host_name") val hostName: String,
    @Json(name = "created_at") val createdAt: String? = null,
    @Json(name = "updated_at") val updatedAt: String? = null,
    @Json(name = "participant_count") val participantCount: Int = 0,
    @Json(name = "ready_count") val readyCount: Int = 0
)

@JsonClass(generateAdapter = true)
data class SupabaseRoomPlayer(
    @Json(name = "id") val id: String? = null,
    @Json(name = "room_code") val roomCode: String,
    @Json(name = "player_name") val playerName: String,
    @Json(name = "team_color") val teamColor: String = "RED",
    @Json(name = "score") val score: Int = 0,
    @Json(name = "is_ready") val isReady: Boolean = false,
    @Json(name = "is_host") val isHost: Boolean = false,
    // --- أعمدة supabase_schema_v2.sql
    @Json(name = "avatar_emoji") val avatarEmoji: String = "👤",
    @Json(name = "joined_at") val joinedAt: String? = null,
    @Json(name = "last_ping") val lastPing: String? = null
)

/** صف من view `public.users` (للقراءة فقط): ملف لاعب مُجمَّع من نتائج لوحة الصدارة. */
@JsonClass(generateAdapter = true)
data class SupabaseUser(
    @Json(name = "id") val id: String,
    @Json(name = "display_name") val displayName: String,
    @Json(name = "level_title") val levelTitle: String? = "مشجع هاوٍ",
    @Json(name = "favorite_club") val favoriteClub: String? = "ريال مدريد",
    @Json(name = "total_points") val totalPoints: Int = 0,
    @Json(name = "total_coins") val totalCoins: Int = 0,
    @Json(name = "challenges_played") val challengesPlayed: Int = 0,
    @Json(name = "best_challenge_points") val bestChallengePoints: Int = 0,
    @Json(name = "joined_at") val joinedAt: String? = null,
    @Json(name = "last_seen_at") val lastSeenAt: String? = null
)

/** صف من view `public.user_challenges` (للقراءة فقط): تحدٍّ واحد خاضه لاعب. */
@JsonClass(generateAdapter = true)
data class SupabaseUserChallenge(
    @Json(name = "challenge_id") val challengeId: String,
    @Json(name = "user_id") val userId: String? = null,
    @Json(name = "display_name") val displayName: String,
    @Json(name = "challenge_type") val challengeType: String = "general",
    @Json(name = "points") val points: Int = 0,
    @Json(name = "coins") val coins: Int = 0,
    @Json(name = "level_title") val levelTitle: String? = null,
    @Json(name = "favorite_club") val favoriteClub: String? = null,
    @Json(name = "played_at") val playedAt: String? = null
)
