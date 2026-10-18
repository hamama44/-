package com.example.data.lan

import com.example.data.room.GameRoom
import com.example.data.room.RoomParticipant
import com.example.data.room.RoomStatus
import com.example.data.room.RoomsRemoteDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * مصدر بيانات الغرف فوق **الشبكة المحلية / الهوتسبوت**.
 *
 * بينفّذ نفس عقد [RoomsRemoteDataSource] اللي بينفّذه `SupabaseRoomsDao`،
 * وده مقصود: [com.example.data.room.GameRoomRepository] بيبدّل بين النقلين
 * (Supabase أونلاين ↔ هوتسبوت محلي) **بدون أي تعديل في كوده**، فكل منطق
 * الكاش والتحقق وموازنة الفرق بيشتغل زي ما هو على الاتنين.
 *
 * نفس اصطلاح العقد محفوظ بدقة:
 *  - `null` = فشل النقل/غير متصل (المستودع هيرجع للكاش المحلي).
 *  - `emptyList()` = نجح بس مفيش صفوف.
 *  - `false` = الأمر فشل.
 *
 * وضعان للتشغيل:
 *  - **مضيف**: الجهاز ده هو السيرفر ⇒ نتعامل مع الحالة في الذاكرة مباشرةً
 *    (بدون socket لنفس الجهاز، أسرع وأبسط).
 *  - **ضيف**: متصل بسيرفر جهاز تاني ⇒ أوامر TCP فوق [LanClient].
 */
class LanRoomsDataSource(
    private val hostServerProvider: () -> LanHostServer?,
    private val clientProvider: () -> LanClient?,
    /**
     * هل المضيف نشط وقادر يخدم حالة الغرفة؟
     *
     * محقون عشان منطق المضيف في الذاكرة يكون قابلاً للاختبار على JVM بدون
     * فتح socket فعلي — بنفس أسلوب [com.example.data.room.GameRoomRepository]
     * اللي بيحقن `isOnline` و`random` و`timeProvider`.
     */
    private val isHostActive: () -> Boolean = { hostServerProvider()?.isRunning == true }
) : RoomsRemoteDataSource {

    /** آخر خطأ عربي من النقل — يُعرض في واجهة الهوتسبوت للتشخيص. */
    @Volatile
    var lastTransportError: String? = null
        private set

    /** هل الجهاز ده بيستضيف سيرفر شغّال؟ */
    val isHosting: Boolean get() = hostServerProvider() != null && isHostActive()

    /** هل متصل كضيف بسيرفر تاني؟ */
    val isJoined: Boolean get() = clientProvider()?.isConnected == true

    /** هل النقل المحلي متاح أصلاً (مضيف أو ضيف)؟ */
    val isAvailable: Boolean get() = isHosting || isJoined

    override suspend fun fetchRooms(limit: Int): List<GameRoom>? = withContext(Dispatchers.IO) {
        val host = hostServerProvider()
        if (host != null && isHostActive()) {
            val room = host.currentRoom()
            lastTransportError = null
            return@withContext if (room == null) emptyList() else listOf(room)
        }

        val client = clientProvider()
        if (client?.isConnected != true) {
            lastTransportError = GameRoomRepositoryErrors.NOT_CONNECTED
            return@withContext null
        }

        val state = client.queryState()
        if (state == null) {
            lastTransportError = client.lastError ?: GameRoomRepositoryErrors.CONNECTION_LOST
            return@withContext null
        }
        lastTransportError = null
        val room = state.toGameRoom()
        if (room == null) emptyList() else listOf(room)
    }

    override suspend fun fetchRoomByCode(roomCode: String): GameRoom? = withContext(Dispatchers.IO) {
        val code = roomCode.trim().uppercase()
        if (code.isEmpty()) return@withContext null

        val rooms = fetchRooms(ROOM_FETCH_LIMIT) ?: return@withContext null
        rooms.firstOrNull { it.roomCode == code }
    }

    override suspend fun createRoom(room: GameRoom): GameRoom? = withContext(Dispatchers.IO) {
        val host = hostServerProvider()
        if (host != null && isHostActive()) {
            val result = host.createRoom(
                hostName = room.hostName,
                roomCode = room.roomCode,
                gameType = room.gameType,
                maxRounds = room.maxRounds,
                maxPlayers = room.maxPlayers,
                region = room.region,
                hostAvatar = DEFAULT_HOST_AVATAR
            )
            lastTransportError = result.errorOrNull
            return@withContext if (result.isSuccess) host.currentRoom() else null
        }

        // الضيف مينشئش غرفة — السيرفر هو اللي بينشئ. دي حماية مقصودة.
        lastTransportError = "إنشاء الغرف متاح على جهاز المضيف بس 🎮"
        return@withContext null
    }

    override suspend fun fetchParticipants(roomCode: String): List<RoomParticipant>? =
        withContext(Dispatchers.IO) {
            val host = hostServerProvider()
            if (host != null && isHostActive()) {
                lastTransportError = null
                return@withContext host.listParticipants()
            }

            val client = clientProvider()
            if (client?.isConnected != true) {
                lastTransportError = GameRoomRepositoryErrors.NOT_CONNECTED
                return@withContext null
            }

            val state = client.queryState() ?: client.latestState()
            if (state == null) {
                lastTransportError = client.lastError ?: GameRoomRepositoryErrors.CONNECTION_LOST
                return@withContext null
            }
            lastTransportError = null
            state.toParticipants(roomCode)
        }

    override suspend fun addParticipant(participant: RoomParticipant): Boolean =
        withContext(Dispatchers.IO) {
            val host = hostServerProvider()
            if (host != null && isHostActive()) {
                val result = host.joinRoom(
                    roomCode = participant.roomCode,
                    playerName = participant.playerName,
                    avatarEmoji = participant.avatarEmoji,
                    requestedTeamColor = participant.teamColor
                )
                lastTransportError = result.errorOrNull
                return@withContext result.isSuccess
            }

            val client = clientProvider()
            if (client?.isConnected != true) {
                lastTransportError = GameRoomRepositoryErrors.NOT_CONNECTED
                return@withContext false
            }

            val response = client.request(
                LanMessage(
                    type = LanMessageType.JOIN,
                    roomCode = participant.roomCode,
                    playerName = participant.playerName,
                    avatarEmoji = participant.avatarEmoji,
                    teamColor = participant.teamColor
                )
            )
            lastTransportError = response?.error
            response != null && response.ok == true
        }

    override suspend fun updateParticipant(participant: RoomParticipant): Boolean =
        withContext(Dispatchers.IO) {
            val host = hostServerProvider()
            if (host != null && isHostActive()) {
                val result = host.applyParticipantUpdate(participant)
                lastTransportError = result.errorOrNull
                return@withContext result.isSuccess
            }

            val client = clientProvider()
            if (client?.isConnected != true) {
                lastTransportError = GameRoomRepositoryErrors.NOT_CONNECTED
                return@withContext false
            }

            // البروتوكول بيفصل الجاهزية عن النقاط، فنبعت الأمرين المطلوبين
            val readyResponse = client.request(
                LanMessage(
                    type = LanMessageType.READY,
                    roomCode = participant.roomCode,
                    playerName = participant.playerName,
                    isReady = participant.isReady
                )
            )
            val scoreResponse = client.request(
                LanMessage(
                    type = LanMessageType.SCORE,
                    roomCode = participant.roomCode,
                    playerName = participant.playerName,
                    score = participant.score
                )
            )

            val failed = listOfNotNull(readyResponse, scoreResponse)
                .firstOrNull { it.ok == false }
            lastTransportError = failed?.error
            readyResponse?.ok == true && scoreResponse?.ok == true
        }

    override suspend fun updateRoomState(
        roomCode: String,
        status: RoomStatus,
        currentRound: Int
    ): Boolean = withContext(Dispatchers.IO) {
        val host = hostServerProvider()
        if (host != null && isHostActive()) {
            val result = host.setRoomState(status, currentRound)
            lastTransportError = result.errorOrNull
            return@withContext result.isSuccess
        }

        val client = clientProvider()
        if (client?.isConnected != true) {
            lastTransportError = GameRoomRepositoryErrors.NOT_CONNECTED
            return@withContext false
        }

        // نترجم الحالة المطلوبة لأمر بروتوكول (المستودع بيستدعيها بـ3 دلالات فقط)
        val type = when {
            status == RoomStatus.FINISHED -> LanMessageType.FINISH
            currentRound <= 1 -> LanMessageType.START
            else -> LanMessageType.NEXT_ROUND
        }

        val response = client.request(
            LanMessage(type = type, roomCode = roomCode, currentRound = currentRound)
        )
        lastTransportError = response?.error
        response != null && response.ok == true
    }

    /** مسح خطأ النقل السابق (بعد نجاح عملية). */
    fun clearError() {
        lastTransportError = null
    }

    companion object {
        /** الحد الأقصى للغرف المسحوبة — الشبكة المحلية فيها غرفة واحدة عملياً. */
        const val ROOM_FETCH_LIMIT = 10

        /** أفاتار المضيف الافتراضي لو مش محدّد. */
        const val DEFAULT_HOST_AVATAR = "👑"
    }
}

// ----------------------------------------------------------------------
// ترجمة wire ↔ domain
// ----------------------------------------------------------------------

/** لقطة حالة من السيرفر ⇒ نموذج غرفة. null لو الرسالة ناقصة الكود. */
internal fun LanMessage.toGameRoom(): GameRoom? {
    val code = roomCode?.trim()?.uppercase()
    if (code.isNullOrEmpty()) return null

    return GameRoom(
        roomCode = code,
        hostName = hostName.orEmpty(),
        gameType = gameType ?: GameRoom.DEFAULT_GAME_TYPE,
        status = RoomStatus.fromWire(status),
        currentRound = currentRound ?: 1,
        maxRounds = maxRounds ?: GameRoom.DEFAULT_MAX_ROUNDS,
        maxPlayers = maxPlayers ?: GameRoom.DEFAULT_MAX_PLAYERS,
        region = region ?: GameRoom.DEFAULT_REGION,
        // العدد الحقيقي من القائمة نفسها، ونرجع لحقل البثّ لو القائمة غايبة
        participantCount = participants?.size ?: (playerCount ?: 0),
        createdAtMillis = 0L,
        // دايماً true: الشبكة المحلية معناها مفيش سيرفر سحابي يتزامن معاه
        isOfflineOnly = true
    )
}

/** قائمة مشاركين من لقطة الحالة. */
internal fun LanMessage.toParticipants(roomCode: String): List<RoomParticipant> {
    val code = roomCode.trim().uppercase()
    val list = participants ?: return emptyList()

    return list.map { wire ->
        RoomParticipant(
            roomCode = code,
            playerName = wire.playerName,
            avatarEmoji = wire.avatarEmoji.ifBlank { RoomParticipant.DEFAULT_AVATAR },
            teamColor = RoomParticipant.normalizeTeamColor(wire.teamColor),
            score = wire.score.coerceAtLeast(0),
            isReady = wire.isReady,
            isHost = wire.isHost,
            joinedAtMillis = 0L
        )
    }
}
