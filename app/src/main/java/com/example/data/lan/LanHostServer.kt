package com.example.data.lan

import com.example.data.room.GameRoom
import com.example.data.room.GameRoomRepository
import com.example.data.room.RoomParticipant
import com.example.data.room.RoomStatus
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList

/**
 * سيرفر اللعبة على الشبكة المحلية / الهوتسبوت (TCP).
 *
 * الجهاز اللي بيفتح الهوتسبوت (أو الواي فاي المنزلي) بيشغّل السيرفر ده،
 * وباقي الأجهزة بتتصل بيه كعملاء. السيرفر هو **مصدر الحقيقة الوحيد**:
 * يحتفظ بحالة الغرفة والمشاركين في الذاكرة، وأي أمر من أي عميل بيتنفّذ هنا
 * ثم تُبَثّ الحالة الجديدة لكل المتصلين ⇒ اللعب متزامن بدون إنترنت تماماً.
 *
 * ملاحظة تصميم مقصودة: سيرفر واحد = غرفة واحدة. ده مطابق لاستخدام الهوتسبوت
 * الواقعي (موبايل بيستضيف أصحابه) وبيخلي البروتوكول بسيط.
 *
 * كل الحالات محفوظة بنماذج الـdomain نفسها ([GameRoom] و[RoomParticipant])،
 * والترجمة للـwire بتحصل عند الحدود فقط — عشان نعيد استخدام منطق موازنة
 * الفرق [GameRoomRepository.pickTeamColor] بدل كتابته مرتين.
 *
 * الخيوط: `acceptThread` بيقبل الاتصالات، ولكل عميل خيط قارئ. الكتابة على
 * العميل متزامنة ([ClientChannel.send]) عشان ما تتداخلش رسائل JSON.
 */
class LanHostServer(
    /** يعيد عنوان IP للجهاز المضيف (يُحقن للاختبار). */
    private val myIpProvider: () -> String? = { null },
    private val port: Int = LanProtocol.DEFAULT_GAME_PORT,
    private val timeProvider: () -> Long = { System.currentTimeMillis() }
) {

    private val lock = Any()
    private var room: GameRoom? = null
    private val participants = mutableListOf<RoomParticipant>()
    private val clients = CopyOnWriteArrayList<ClientChannel>()

    @Volatile
    var isRunning: Boolean = false
        private set

    /** عنوان الـIP اللي السيرفر مربوط عليه (يظهر في الواجهة ليُقرأ للأصحاب). */
    @Volatile
    var boundAddress: String? = null
        private set

    /** آخر خطأ عربي — يُعرض في الواجهة بدل استثناء Socket غامض. */
    @Volatile
    var lastError: String? = null
        private set

    /** عدد العملاء المتصلين حالياً. */
    val connectedClientCount: Int get() = clients.size

    private var serverSocket: ServerSocket? = null
    private var acceptThread: Thread? = null
    private var announcer: LanAnnouncer? = null
    private var keepAliveThread: Thread? = null

    // ------------------------------------------------------------------
    // دورة الحياة
    // ------------------------------------------------------------------

    /**
     * تشغيل السيرفر + بدء البثّ التلقائي عبر UDP عشان الأجهزة تلاقيه
     * بدون كتابة الـIP يدوياً. يعيد false لو البورت مشغول.
     */
    fun start(hostName: String): Boolean {
        if (isRunning) return true
        return try {
            val socket = ServerSocket().apply {
                reuseAddress = true
                bind(InetSocketAddress(port))
            }
            serverSocket = socket
            isRunning = true
            boundAddress = myIpProvider()
            lastError = null

            acceptThread = Thread({ acceptLoop(socket) }, "lan-host-accept").apply {
                isDaemon = true
                start()
            }

            announcer = LanAnnouncer(
                hostName = hostName,
                port = port,
                roomCodeProvider = { currentRoomCode() },
                gameTypeProvider = { currentGameType() },
                playerCountProvider = { participantCount() },
                myIpProvider = myIpProvider
            ).also { it.start() }

            keepAliveThread = Thread({ keepAliveLoop() }, "lan-host-keepalive").apply {
                isDaemon = true
                start()
            }

            true
        } catch (error: Exception) {
            lastError = "تعذّر تشغيل سيرفر الشبكة المحلية على البورت $port 🔌 (${error.message ?: "خطأ غير معروف"})"
            closeQuietly()
            false
        }
    }

    /** إيقاف السيرفر وإبلاغ العملاء وإغلاق كل الاتصالات. */
    fun stop() {
        if (!isRunning && serverSocket == null) return
        isRunning = false

        broadcast(LanMessage(type = LanMessageType.BYE))
        announcer?.stop()
        announcer = null

        clients.forEach { it.close() }
        clients.clear()

        try {
            serverSocket?.close()
        } catch (_: Exception) {
        }
        serverSocket = null

        acceptThread?.interrupt()
        acceptThread = null

        keepAliveThread?.interrupt()
        keepAliveThread = null
    }

    /** مسح حالة الغرفة (لبدء جلسة جديدة على نفس السيرفر). */
    fun resetRoom() {
        synchronized(lock) {
            room = null
            participants.clear()
        }
        broadcastState()
    }

    // ------------------------------------------------------------------
    // أوامر اللعبة — تُستدعى محلياً من المضيف أو عبر الشبكة من العملاء
    // ------------------------------------------------------------------

    /** إنشاء الغرفة على السيرفر، مع إضافة المضيف كمشارك جاهز. */
    fun createRoom(
        hostName: String,
        roomCode: String,
        gameType: String,
        maxRounds: Int,
        maxPlayers: Int,
        region: String,
        hostAvatar: String
    ): LanOperationResult {
        val trimmedHost = hostName.trim()
        if (trimmedHost.isEmpty()) return LanOperationResult.Failure(GameRoomRepository.ERROR_HOST_NAME_REQUIRED)
        if (trimmedHost.length > RoomParticipant.MAX_NAME_LENGTH) {
            return LanOperationResult.Failure(GameRoomRepository.ERROR_HOST_NAME_TOO_LONG)
        }
        if (maxPlayers !in 2..GameRoom.MAX_MAX_PLAYERS) {
            return LanOperationResult.Failure("عدد اللاعبين لازم يكون بين 2 و ${GameRoom.MAX_MAX_PLAYERS} 👥")
        }
        if (maxRounds !in 1..GameRoom.MAX_ROUNDS_LIMIT) {
            return LanOperationResult.Failure("عدد الجولات لازم يكون بين 1 و ${GameRoom.MAX_ROUNDS_LIMIT} 🔁")
        }
        val code = roomCode.trim().uppercase()
        if (code.isEmpty()) return LanOperationResult.Failure(GameRoomRepository.ERROR_ROOM_CODE_REQUIRED)

        synchronized(lock) {
            if (room != null && room?.status != RoomStatus.FINISHED) {
                return LanOperationResult.Failure("فيه غرفة شغّالة بالفعل على السيرفر ده ($code) 🎮")
            }
            val now = timeProvider()
            room = GameRoom(
                roomCode = code,
                hostName = trimmedHost,
                gameType = gameType,
                status = RoomStatus.WAITING,
                currentRound = 1,
                maxRounds = maxRounds,
                maxPlayers = maxPlayers,
                region = region,
                participantCount = 1,
                createdAtMillis = now,
                isOfflineOnly = true
            )
            participants.clear()
            participants.add(
                RoomParticipant(
                    roomCode = code,
                    playerName = trimmedHost,
                    avatarEmoji = hostAvatar.ifBlank { RoomParticipant.DEFAULT_AVATAR },
                    teamColor = RoomParticipant.TEAM_RED,
                    score = 0,
                    isReady = true,
                    isHost = true,
                    joinedAtMillis = now
                )
            )
        }
        broadcastState()
        return LanOperationResult.Success("تم إنشاء الغرفة $code على الشبكة المحلية 📶")
    }

    /** انضمام لاعب للغرفة، مع موازنة الفرق تلقائياً. */
    fun joinRoom(
        roomCode: String,
        playerName: String,
        avatarEmoji: String,
        requestedTeamColor: String
    ): LanOperationResult {
        val code = roomCode.trim().uppercase()
        if (code.isEmpty()) return LanOperationResult.Failure(GameRoomRepository.ERROR_ROOM_CODE_REQUIRED)

        val name = playerName.trim()
        if (name.isEmpty()) return LanOperationResult.Failure(GameRoomRepository.ERROR_PLAYER_NAME_REQUIRED)
        if (name.length > RoomParticipant.MAX_NAME_LENGTH) {
            return LanOperationResult.Failure(GameRoomRepository.ERROR_PLAYER_NAME_TOO_LONG)
        }

        synchronized(lock) {
            val current = room
            if (current == null || current.roomCode != code) {
                return LanOperationResult.Failure(GameRoomRepository.ERROR_ROOM_NOT_FOUND)
            }
            if (current.status == RoomStatus.FINISHED) {
                return LanOperationResult.Failure(GameRoomRepository.ERROR_ROOM_FINISHED)
            }

            val existing = participants.firstOrNull {
                it.playerName.equals(name, ignoreCase = true)
            }
            if (existing != null) {
                // انضمام مكرر = رجوع لنفس المقعد (زي سلوك Supabase تماماً)
                return LanOperationResult.Success("رجعت للغرفة $code 👋")
            }
            if (participants.size >= current.maxPlayers) {
                return LanOperationResult.Failure(GameRoomRepository.ERROR_ROOM_FULL)
            }

            val balanced = GameRoomRepository.pickTeamColor(participants.toList(), requestedTeamColor)
            participants.add(
                RoomParticipant(
                    roomCode = code,
                    playerName = name,
                    avatarEmoji = avatarEmoji.ifBlank { RoomParticipant.DEFAULT_AVATAR },
                    teamColor = balanced,
                    score = 0,
                    isReady = false,
                    isHost = false,
                    joinedAtMillis = timeProvider()
                )
            )
            room = current.copy(participantCount = participants.size)
        }
        broadcastState()
        return LanOperationResult.Success("انضممت للغرفة $code على الشبكة المحلية 📶")
    }

    /** مغادرة لاعب (يُحذف من القائمة). */
    fun leaveRoom(playerName: String): LanOperationResult {
        val name = playerName.trim()
        if (name.isEmpty()) return LanOperationResult.Failure(GameRoomRepository.ERROR_PLAYER_NAME_REQUIRED)

        var removed = false
        synchronized(lock) {
            val index = participants.indexOfFirst { it.playerName.equals(name, ignoreCase = true) }
            if (index >= 0) {
                val leaving = participants.removeAt(index)
                removed = true
                // لو المضيف مشي، ننقل الاستضافة لأول مشارك باقي
                if (leaving.isHost && participants.isNotEmpty()) {
                    participants[0] = participants[0].copy(isHost = true)
                    room = room?.copy(hostName = participants[0].playerName)
                }
                room = room?.copy(participantCount = participants.size)
            }
        }
        if (removed) broadcastState()
        return LanOperationResult.Success("خرجت من الغرفة 👋")
    }

    /** تبديل جاهزية لاعب. */
    fun setReady(playerName: String, isReady: Boolean): LanOperationResult =
        updateParticipant(playerName, "الجاهزية") { it.copy(isReady = isReady) }

    /** تحديث نقاط لاعب. */
    fun updateScore(playerName: String, score: Int): LanOperationResult =
        updateParticipant(playerName, "النقاط") { it.copy(score = score.coerceAtLeast(0)) }

    private fun updateParticipant(
        playerName: String,
        what: String,
        transform: (RoomParticipant) -> RoomParticipant
    ): LanOperationResult {
        val name = playerName.trim()
        if (name.isEmpty()) return LanOperationResult.Failure(GameRoomRepository.ERROR_PLAYER_NAME_REQUIRED)

        var found = false
        synchronized(lock) {
            val index = participants.indexOfFirst { it.playerName.equals(name, ignoreCase = true) }
            if (index >= 0) {
                participants[index] = transform(participants[index])
                found = true
            }
        }
        if (!found) return LanOperationResult.Failure("مش لاقي اللاعب «$name» في الغرفة 🤔")
        broadcastState()
        return LanOperationResult.Success("تم تحديث $what ✅")
    }

    /**
     * تطبيق تحديث كامل لمشارك (الفريق/النقاط/الجاهزية/الأفاتار/الاستضافة)
     * بشكل ذرّي.
     *
     * [com.example.data.room.RoomsRemoteDataSource.updateParticipant] بيحدّث
     * الحقول دي كلها مرة واحدة، فبنوفّر نفس الدلالة بدل نداءين منفصلين
     * ممكن يوصلوا بترتيب مختلف ويسبّبوا حالة وسطية.
     */
    fun applyParticipantUpdate(participant: RoomParticipant): LanOperationResult {
        val name = participant.playerName.trim()
        if (name.isEmpty()) return LanOperationResult.Failure(GameRoomRepository.ERROR_PLAYER_NAME_REQUIRED)

        var found = false
        synchronized(lock) {
            val index = participants.indexOfFirst { it.playerName.equals(name, ignoreCase = true) }
            if (index >= 0) {
                val current = participants[index]
                participants[index] = current.copy(
                    avatarEmoji = participant.avatarEmoji.ifBlank { current.avatarEmoji },
                    teamColor = RoomParticipant.normalizeTeamColor(participant.teamColor),
                    score = participant.score.coerceAtLeast(0),
                    isReady = participant.isReady,
                    // الاستضافة ما تنتقلش عن طريق تحديث عادي — ليها مسار [leaveRoom]
                    isHost = current.isHost
                )
                found = true
            }
        }
        if (!found) return LanOperationResult.Failure("مش لاقي اللاعب «$name» في الغرفة 🤔")
        broadcastState()
        return LanOperationResult.Success("تم تحديث بيانات اللاعب ✅")
    }

    /**
     * ضبط حالة الغرفة ورقم الجولة مباشرةً.
     *
     * دي الدالة اللي بيناديها المستودع عبر `updateRoomState`، وهي اللي بتخلي
     * الشبكة المحلية تحترم نفس دلالات `startGame`/`advanceRound`/`finishGame`
     * الموجودة في Supabase.
     */
    fun setRoomState(status: RoomStatus, currentRound: Int): LanOperationResult {
        synchronized(lock) {
            val current = room ?: return LanOperationResult.Failure(GameRoomRepository.ERROR_ROOM_NOT_FOUND)
            room = current.copy(
                status = status,
                currentRound = currentRound.coerceIn(1, current.maxRounds)
            )
        }
        broadcastState()
        return LanOperationResult.Success("تم تحديث حالة الغرفة ✅")
    }

    /** بدء اللعب (يحوّل الحالة لـ PLAYING). */
    fun startGame(): LanOperationResult = changeStatus(RoomStatus.PLAYING, round = 1, label = "بدأ اللعب 🚀")

    /** الجولة التالية (تُرفض لو وصلنا للحد الأقصى). */
    fun nextRound(): LanOperationResult {
        // كتلة قفل واحدة: الفحص والتعديل لازم يكونوا ذرّيين، وإلا عميلين
        // ممكن يعدّوا نفس الجولة في نفس اللحظة
        synchronized(lock) {
            val current = room ?: return LanOperationResult.Failure(GameRoomRepository.ERROR_ROOM_NOT_FOUND)
            if (current.status == RoomStatus.FINISHED) {
                return LanOperationResult.Failure(GameRoomRepository.ERROR_ROOM_FINISHED)
            }
            if (current.currentRound >= current.maxRounds) {
                return LanOperationResult.Failure(
                    "وصلت لآخر جولة (${current.currentRound}/${current.maxRounds}) — أنهِ الغرفة 🏁"
                )
            }
            room = current.copy(
                status = RoomStatus.PLAYING,
                currentRound = current.currentRound + 1
            )
        }
        broadcastState()
        return LanOperationResult.Success("الجولة التالية 🔁")
    }

    /** إنهاء الغرفة. */
    fun finishGame(): LanOperationResult = changeStatus(RoomStatus.FINISHED, label = "انتهت الغرفة 🏁")

    private fun changeStatus(
        status: RoomStatus,
        round: Int? = null,
        label: String
    ): LanOperationResult {
        synchronized(lock) {
            val current = room ?: return LanOperationResult.Failure(GameRoomRepository.ERROR_ROOM_NOT_FOUND)
            room = current.copy(
                status = status,
                currentRound = round ?: current.currentRound
            )
        }
        broadcastState()
        return LanOperationResult.Success(label)
    }

    // ------------------------------------------------------------------
    // القراءة (للمضيف نفسه ولمصدر البيانات)
    // ------------------------------------------------------------------

    fun currentRoom(): GameRoom? = synchronized(lock) { room }

    fun currentRoomCode(): String? = synchronized(lock) { room?.roomCode }

    fun currentGameType(): String? = synchronized(lock) { room?.gameType }

    fun participantCount(): Int = synchronized(lock) { participants.size }

    fun listParticipants(): List<RoomParticipant> = synchronized(lock) { participants.toList() }

    /** لقطة الحالة كبثّ جاهز للإرسال. */
    fun stateMessage(): LanMessage? {
        synchronized(lock) {
            val current = room ?: return null
            return LanMessage(
                type = LanMessageType.STATE,
                roomCode = current.roomCode,
                hostName = current.hostName,
                gameType = current.gameType,
                status = current.status.wire,
                currentRound = current.currentRound,
                maxRounds = current.maxRounds,
                maxPlayers = current.maxPlayers,
                region = current.region,
                playerCount = participants.size,
                hostIp = boundAddress,
                port = port,
                participants = participants.map { it.toLanParticipant() }
            )
        }
    }

    // ------------------------------------------------------------------
    // الشبكة
    // ------------------------------------------------------------------

    /**
     * نبضة دورية كل [KEEP_ALIVE_INTERVAL_MS].
     *
     * وظيفتين في نفس الوقت:
     *  1. **كشف الاتصال الميت**: [broadcast] بيشيل أي عميل فشل الإرسال له —
     *     وده بيحصل لما اللاعب يخرج من مدى الهوتسبوت أو يقفل التطبيق.
     *  2. **تحديث الضيوف**: حتى لو ضيف فاته ردّ أمر، حالته بتتصحح خلال ثواني.
     */
    private fun keepAliveLoop() {
        while (isRunning) {
            try {
                Thread.sleep(KEEP_ALIVE_INTERVAL_MS)
            } catch (_: InterruptedException) {
                break
            }
            if (!isRunning) break

            // لو فيه غرفة نبعت حالتها كاملة، وإلا نبضة خفيفة تكفي للتنظيف
            val state = stateMessage()
            if (state != null) {
                broadcast(state)
            } else {
                broadcast(LanMessage(type = LanMessageType.PING))
            }
        }
    }

    private fun acceptLoop(socket: ServerSocket) {
        while (isRunning) {
            val client = try {
                socket.accept()
            } catch (_: Exception) {
                // الإغلاق أثناء accept = إيقاف طبيعي
                break
            }
            try {
                client.tcpNoDelay = true
                // مهلة قراءة = 0 (بلا مهلة): اللاعب اللي ساكت بيفكر في جولته
                // مش معناه إن الاتصال مات. كشف الاتصال الميت بيحصل بالكتابة
                // الدورية في خيط الـkeep-alive — الفشل هناك بيشيل العميل.
                client.soTimeout = 0
            } catch (_: Exception) {
            }

            val channel = ClientChannel(client)
            clients.add(channel)

            // نبعت الحالة الحالية فوراً عشان العميل الجديد يشوف الغرفة
            stateMessage()?.let { channel.send(it) }

            Thread({ clientLoop(channel) }, "lan-host-client").apply {
                isDaemon = true
                start()
            }
        }
    }

    private fun clientLoop(channel: ClientChannel) {
        try {
            while (isRunning) {
                val line = channel.readLine() ?: break
                val message = LanProtocol.decode(line)
                if (message == null) {
                    channel.send(LanMessage(type = LanMessageType.ERROR, error = "رسالة غير مفهومة 📡"))
                    continue
                }
                if (!message.isCompatibleVersion) {
                    channel.send(
                        LanMessage(
                            type = LanMessageType.ERROR,
                            error = "إصدار التطبيق عندك مختلف عن السيرفر — حدّث التطبيق 🔄"
                        )
                    )
                    continue
                }
                handleClientMessage(channel, message)
            }
        } catch (_: Exception) {
            // انقطاع الاتصال (قفل التطبيق / خرج من مدى الهوتسبوت)
        } finally {
            clients.remove(channel)
            channel.close()
        }
    }

    private fun handleClientMessage(channel: ClientChannel, message: LanMessage) {
        val result = when (message.type) {
            LanMessageType.QUERY -> {
                val state = stateMessage()
                if (state != null) {
                    channel.send(state)
                    return
                }
                LanOperationResult.Failure(GameRoomRepository.ERROR_ROOM_NOT_FOUND)
            }
            LanMessageType.CREATE_ROOM -> createRoom(
                hostName = message.hostName.orEmpty(),
                roomCode = message.roomCode.orEmpty(),
                gameType = message.gameType ?: GameRoom.DEFAULT_GAME_TYPE,
                maxRounds = message.maxRounds ?: GameRoom.DEFAULT_MAX_ROUNDS,
                maxPlayers = message.maxPlayers ?: GameRoom.DEFAULT_MAX_PLAYERS,
                region = message.region ?: GameRoom.DEFAULT_REGION,
                hostAvatar = message.avatarEmoji.orEmpty()
            )
            LanMessageType.JOIN -> joinRoom(
                roomCode = message.roomCode.orEmpty(),
                playerName = message.playerName.orEmpty(),
                avatarEmoji = message.avatarEmoji.orEmpty(),
                requestedTeamColor = message.teamColor ?: RoomParticipant.TEAM_RED
            )
            LanMessageType.LEAVE -> leaveRoom(message.playerName.orEmpty())
            LanMessageType.READY -> setReady(message.playerName.orEmpty(), message.isReady ?: true)
            LanMessageType.SCORE -> updateScore(message.playerName.orEmpty(), message.score ?: 0)
            LanMessageType.START -> startGame()
            LanMessageType.NEXT_ROUND -> nextRound()
            LanMessageType.FINISH -> finishGame()
            LanMessageType.DISCOVER -> {
                stateMessage()?.let { channel.send(it) }
                return
            }
            LanMessageType.PING -> {
                // نبضة من العميل: نرد بالحالة الحالية لو فيه غرفة
                stateMessage()?.let { channel.send(it) }
                return
            }
            else -> LanOperationResult.Failure("أمر غير معروف: ${message.type} 📡")
        }

        when (result) {
            is LanOperationResult.Success -> channel.send(
                LanMessage(type = LanMessageType.ACK, ok = true, roomCode = message.roomCode)
            )
            is LanOperationResult.Failure -> channel.send(
                LanMessage(type = LanMessageType.ERROR, ok = false, error = result.error)
            )
        }
    }

    /** بثّ الحالة لكل العملاء المتصلين. */
    fun broadcastState() {
        val state = stateMessage() ?: return
        broadcast(state)
    }

    private fun broadcast(message: LanMessage) {
        val dead = mutableListOf<ClientChannel>()
        clients.forEach { channel ->
            if (!channel.send(message)) dead.add(channel)
        }
        if (dead.isNotEmpty()) {
            clients.removeAll(dead.toSet())
            dead.forEach { it.close() }
        }
    }

    private fun closeQuietly() {
        announcer?.stop()
        announcer = null
        keepAliveThread?.interrupt()
        keepAliveThread = null
        acceptThread?.interrupt()
        acceptThread = null
        clients.forEach { it.close() }
        clients.clear()
        try {
            serverSocket?.close()
        } catch (_: Exception) {
        }
        serverSocket = null
        isRunning = false
    }

    /**
     * قناة اتصال بعميل واحد. الكتابة متزامنة عشان سطرين JSON ما يتداخلوش.
     */
    private class ClientChannel(private val socket: Socket) {
        private val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
        private val writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream(), Charsets.UTF_8))
        private val writeLock = Any()

        @Volatile
        private var closed = false

        /** يعيد true لو الإرسال نجح، false لو الاتصال مات. */
        fun send(message: LanMessage): Boolean {
            if (closed) return false
            return try {
                val payload = LanProtocol.encode(message)
                synchronized(writeLock) {
                    writer.write(payload)
                    writer.write(LanProtocol.DELIMITER)
                    writer.flush()
                }
                true
            } catch (_: Exception) {
                closed = true
                false
            }
        }

        fun readLine(): String? {
            if (closed) return null
            return try {
                reader.readLine()
            } catch (_: Exception) {
                closed = true
                null
            }
        }

        fun close() {
            closed = true
            try {
                socket.close()
            } catch (_: Exception) {
            }
        }
    }

    companion object {
        /**
         * فترة النبضة الدورية (مللي ثانية).
         * 5 ثواني: كافية لكشف الاتصال الميت بسرعة بدون إرهاق بطارية المضيف.
         */
        const val KEEP_ALIVE_INTERVAL_MS = 5_000L
    }
}

/** نتيجة أمر على الشبكة المحلية — الفشل دايماً برسالة عربية تُعرض مباشرةً. */
sealed class LanOperationResult {
    data class Success(val message: String? = null) : LanOperationResult()
    data class Failure(val error: String) : LanOperationResult()

    val isSuccess: Boolean get() = this is Success
    val errorOrNull: String? get() = (this as? Failure)?.error
    val messageOrNull: String? get() = (this as? Success)?.message
}

/** ترجمة مشارك domain → wire. */
private fun RoomParticipant.toLanParticipant() = LanParticipant(
    playerName = playerName,
    avatarEmoji = avatarEmoji,
    teamColor = teamColor,
    score = score,
    isReady = isReady,
    isHost = isHost
)
