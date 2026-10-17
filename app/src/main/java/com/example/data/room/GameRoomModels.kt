package com.example.data.room

/**
 * نماذج الـ Domain الخاصة بالغرف الأونلاين + عقد مصدر البيانات البعيد.
 *
 * هذه الملفات لا تعرف أي شيء عن Supabase ولا عن Room:
 *  - [GameRoom] و [RoomParticipant] نماذج نقية تستخدمها الـ ViewModel والشاشات.
 *  - [RoomsRemoteDataSource] العقد الذي ينفّذه `SupabaseRoomsDao`.
 *  - الكاش المحلي تنفّذه `GameRoomEntity` / `RoomParticipantEntity`.
 *
 * الفصل هذا هو ما يجعل [GameRoomRepository] قابلاً للاختبار بالكامل بدون شبكة
 * وبدون Context (انظر GameRoomRepositoryTest).
 */

/** حالة الغرفة، مع قيمة `wire` المطابقة لقاعدة البيانات ووصف عربي للواجهة. */
enum class RoomStatus(val wire: String, val arabicLabel: String) {
    WAITING("waiting", "في انتظار منافس ⏳"),
    PLAYING("playing", "جارية ⚽"),
    FINISHED("finished", "انتهت 🏁");

    companion object {
        /** أي قيمة غير معروفة تُعتبر "في انتظار" حتى لا تنكسر الواجهة. */
        fun fromWire(value: String?): RoomStatus =
            values().firstOrNull { it.wire.equals(value?.trim(), ignoreCase = true) } ?: WAITING
    }
}

/** غرفة لعب أونلاين (صف في view `public.rooms`). */
data class GameRoom(
    val roomCode: String,
    val hostName: String,
    val gameType: String = DEFAULT_GAME_TYPE,
    val status: RoomStatus = RoomStatus.WAITING,
    val currentRound: Int = 1,
    val maxRounds: Int = DEFAULT_MAX_ROUNDS,
    val maxPlayers: Int = DEFAULT_MAX_PLAYERS,
    val isPrivate: Boolean = false,
    val region: String = DEFAULT_REGION,
    val participantCount: Int = 0,
    val remoteId: String? = null,
    val createdAtMillis: Long = 0L,
    /** `true` إذا أُنشئت الغرفة بدون إنترنت ولم تُزامَن بعد. */
    val isOfflineOnly: Boolean = false
) {
    val isFull: Boolean get() = participantCount >= maxPlayers
    val seatsLeft: Int get() = (maxPlayers - participantCount).coerceAtLeast(0)
    val isRoundsFinished: Boolean get() = currentRound > maxRounds
    val arabicGameTitle: String get() = gameTitleFor(gameType)
    val arabicStatusLabel: String get() = status.arabicLabel
    val capacityLabel: String get() = "$participantCount/$maxPlayers لاعب 👥"

    companion object {
        const val DEFAULT_GAME_TYPE = "thirty_seconds"
        const val DEFAULT_MAX_ROUNDS = 5
        const val DEFAULT_MAX_PLAYERS = 6
        const val DEFAULT_REGION = "MENA"
        const val MIN_MAX_PLAYERS = 2
        const val MAX_MAX_PLAYERS = 12
        const val MAX_ROUNDS_LIMIT = 20

        /** عنوان اللعبة بالعربي (مطابق للمفاتيح المستخدمة في الشاشات). */
        fun gameTitleFor(gameType: String): String = when (gameType) {
            "thirty_seconds" -> "تحدي الثلاثين ⏱️"
            "buzzer" -> "صراع الجرس 🔔"
            "tictactoe" -> "تيك تاك تو كروي ⚽"
            "auction" -> "المزاد الكروي 🔨"
            "sabaho" -> "صباحو تحدي 🌅"
            else -> "تحدي مباشر ⚽"
        }
    }
}

/** لاعب مشارك داخل غرفة (صف في view `public.room_participants`). */
data class RoomParticipant(
    val roomCode: String,
    val playerName: String,
    val avatarEmoji: String = DEFAULT_AVATAR,
    val teamColor: String = TEAM_RED,
    val score: Int = 0,
    val isReady: Boolean = false,
    val isHost: Boolean = false,
    val remoteId: String? = null,
    val joinedAtMillis: Long = 0L,
    /** `true` إذا أُضيف المشارك محلياً ولم يصل للسيرفر بعد. */
    val isOfflineOnly: Boolean = false
) {
    val isTeamRed: Boolean get() = teamColor.equals(TEAM_RED, ignoreCase = true)
    val arabicTeamLabel: String get() = if (isTeamRed) "الفريق الأحمر 🔴" else "الفريق الأزرق 🔵"
    val arabicReadyLabel: String get() = if (isReady) "جاهز ✅" else "غير جاهز ⌛"

    /** المفتاح المحلي الثابت المستخدم كـ PrimaryKey في الكاش. */
    val localKey: String get() = participantKey(roomCode, playerName)

    companion object {
        const val TEAM_RED = "RED"
        const val TEAM_BLUE = "BLUE"
        const val DEFAULT_AVATAR = "👤"
        const val MAX_NAME_LENGTH = 24

        /**
         * مفتاح موحّد (كود الغرفة + الاسم بحالة ثابتة) يمنع تكرار نفس اللاعب
         * في الكاش المحلي عند المزامنة.
         */
        fun participantKey(roomCode: String, playerName: String): String =
            "${normalizeRoomCode(roomCode)}|${playerName.trim().lowercase()}"

        /** توحيد شكل كود الغرفة: أحرف إنجليزية كبيرة وأرقام فقط. */
        fun normalizeRoomCode(raw: String): String =
            raw.trim().uppercase().filter { it in 'A'..'Z' || it in '0'..'9' }

        /** أي قيمة غير BLUE تُعتبر RED. */
        fun normalizeTeamColor(raw: String): String =
            if (raw.trim().equals(TEAM_BLUE, ignoreCase = true)) TEAM_BLUE else TEAM_RED
    }
}

/**
 * نتيجة أي عملية في [GameRoomRepository].
 *
 * [Failure.message] نص عربي جاهز للعرض مباشرة في Toast أو Snackbar.
 */
sealed interface RoomResult<out T> {
    data class Success<out T>(val data: T) : RoomResult<T>
    data class Failure(val message: String) : RoomResult<Nothing>
}

/** قيمة النجاح أو `null`. */
fun <T> RoomResult<T>.getOrNull(): T? = when (this) {
    is RoomResult.Success -> data
    is RoomResult.Failure -> null
}

/** رسالة الفشل أو `null` عند النجاح. */
fun <T> RoomResult<T>.failureMessageOrNull(): String? = when (this) {
    is RoomResult.Success -> null
    is RoomResult.Failure -> message
}

val <T> RoomResult<T>.isSuccess: Boolean
    get() = this is RoomResult.Success

/**
 * مصدر البيانات البعيد (Supabase).
 *
 * **اتفاقية الإرجاع:** `null` تعني "فشل الاتصال أو تعذّر التحقق"،
 * بينما `emptyList()` تعني "نجح الطلب ولا توجد صفوف". هذا التفريق هو ما يسمح
 * للـ Repository بالرجوع للكاش المحلي بدل عرض قائمة فارغة عند انقطاع الشبكة.
 */
interface RoomsRemoteDataSource {
    /** آخر الغرف (مرتبة من الأحدث) مع عدد المشاركين. `null` عند فشل الاتصال. */
    suspend fun fetchRooms(limit: Int): List<GameRoom>?

    /** غرفة واحدة بكودها. `null` = غير موجودة أو فشل الاتصال. */
    suspend fun fetchRoomByCode(roomCode: String): GameRoom?

    /** إنشاء غرفة، ويعيد الصف المُنشأ من السيرفر (يشمل `remoteId`). `null` = فشل. */
    suspend fun createRoom(room: GameRoom): GameRoom?

    /** كل مشاركي الغرفة. `null` عند فشل الاتصال. */
    suspend fun fetchParticipants(roomCode: String): List<RoomParticipant>?

    /** إضافة مشارك. `false` عند الفشل. */
    suspend fun addParticipant(participant: RoomParticipant): Boolean

    /** تحديث مشارك (الجاهزية/النقاط/الفريق). `false` عند الفشل. */
    suspend fun updateParticipant(participant: RoomParticipant): Boolean

    /** تحديث حالة الغرفة ورقم الجولة. `false` عند الفشل. */
    suspend fun updateRoomState(roomCode: String, status: RoomStatus, currentRound: Int): Boolean
}
