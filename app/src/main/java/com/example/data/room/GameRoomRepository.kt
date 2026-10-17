package com.example.data.room

import android.content.Context
import com.example.data.supabase.SupabaseClientProvider
import com.example.data.supabase.SupabaseRoomsDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.math.abs
import kotlin.random.Random

/**
 * مستودع غرف اللعب الأونلاين (Rooms Repository).
 *
 * يدير **إنشاء الغرف** و**عرض قائمة المشاركين** بمنطق Offline-first:
 *  - الكتابة: محاولة Supabase أولاً، وعند غياب الشبكة تُحفظ الغرفة محلياً
 *    بعلامة `isPendingSync` ثم تُدفع لاحقاً عبر [syncPendingRooms].
 *  - القراءة: جلب من السيرفر وتحديث الكاش، والرجوع للكاش تلقائياً عند الفشل
 *    (الشاشة لا ترى قائمة فارغة أبداً بسبب انقطاع الشبكة).
 *
 * كل المنطق الحساس للاختبار (توليد الكود، ترتيب المشاركين، موازنة الفرق،
 * دمج الكاش) موجود هنا كدوال نقية أو قابلة للحقن عبر المُنشئ:
 * [localDao] و [remoteDao] و [isOnline] و [random] و [timeProvider].
 */
class GameRoomRepository(
    private val localDao: GameRoomDao,
    private val remoteDao: RoomsRemoteDataSource,
    private val isOnline: () -> Boolean = { SupabaseClientProvider.isConfigured() },
    private val random: Random = Random.Default,
    private val timeProvider: () -> Long = { System.currentTimeMillis() }
) {

    // =========================================================================
    // 1) مراقبة مباشرة من الكاش المحلي (مصدر الحقيقة للواجهة)
    // =========================================================================

    /** كل الغرف المكاشّة، الأحدث أولاً وغير المنتهية في الأعلى. */
    fun observeRooms(): Flow<List<GameRoom>> =
        localDao.observeRooms().map { entities ->
            entities.map { it.toDomain() }.sortedWith(ROOM_COMPARATOR)
        }

    /** مراقبون مشاركو غرفة معينة بترتيب العرض المعتمد. */
    fun observeParticipants(roomCode: String): Flow<List<RoomParticipant>> =
        localDao.observeParticipants(normalizeCode(roomCode)).map { entities ->
            entities.map { it.toDomain() }.sortedWith(PARTICIPANT_COMPARATOR)
        }

    // =========================================================================
    // 2) إنشاء غرفة (Room Creation)
    // =========================================================================

    /**
     * إنشاء غرفة جديدة وإضافة المضيف كأول مشارك فيها.
     *
     * الخطوات: تحقق من المدخلات ← توليد/اعتماد كود فريد ← إدراج في Supabase
     * (أو محلياً عند انقطاع الشبكة) ← كاش ← إضافة المضيف كمشارك جاهز.
     *
     * @param preferredCode كود معروض مسبقاً في الواجهة؛ يُعتمد إن كان فريداً.
     * @return [RoomResult.Success] يحمل الغرفة، أو [RoomResult.Failure] برسالة عربية.
     */
    suspend fun createRoom(
        hostName: String,
        gameType: String = GameRoom.DEFAULT_GAME_TYPE,
        maxRounds: Int = GameRoom.DEFAULT_MAX_ROUNDS,
        maxPlayers: Int = GameRoom.DEFAULT_MAX_PLAYERS,
        region: String = GameRoom.DEFAULT_REGION,
        hostAvatar: String = RoomParticipant.DEFAULT_AVATAR,
        preferredCode: String? = null
    ): RoomResult<GameRoom> {
        val host = hostName.trim()
        if (host.isEmpty()) return RoomResult.Failure(ERROR_HOST_NAME_REQUIRED)
        if (host.length > RoomParticipant.MAX_NAME_LENGTH) {
            return RoomResult.Failure(ERROR_HOST_NAME_TOO_LONG)
        }
        if (maxPlayers !in GameRoom.MIN_MAX_PLAYERS..GameRoom.MAX_MAX_PLAYERS) {
            return RoomResult.Failure(ERROR_INVALID_MAX_PLAYERS)
        }
        if (maxRounds !in 1..GameRoom.MAX_ROUNDS_LIMIT) {
            return RoomResult.Failure(ERROR_INVALID_MAX_ROUNDS)
        }

        val roomCode = resolveRoomCode(preferredCode)
        val now = timeProvider()
        val online = isOnline()

        var room = GameRoom(
            roomCode = roomCode,
            hostName = host,
            gameType = gameType,
            status = RoomStatus.WAITING,
            currentRound = 1,
            maxRounds = maxRounds,
            maxPlayers = maxPlayers,
            region = region,
            participantCount = 1, // المضيف نفسه
            createdAtMillis = now,
            isOfflineOnly = !online
        )

        if (online) {
            val created = runCatching { remoteDao.createRoom(room) }.getOrNull()
            room = if (created != null) {
                created.copy(participantCount = 1, isOfflineOnly = false)
            } else {
                // السيرفر مُهيّأ لكن الطلب فشل → نكمل أوفلاين بدل فقدان الغرفة
                room.copy(isOfflineOnly = true)
            }
        }

        localDao.upsertRoom(
            room.toEntity(
                pendingSync = room.isOfflineOnly,
                lastSyncedAtMillis = if (room.isOfflineOnly) 0L else now
            )
        )

        // المضيف = أول مشارك، جاهز تلقائياً وعلى الفريق الأحمر
        persistParticipant(
            participant = RoomParticipant(
                roomCode = room.roomCode,
                playerName = host,
                avatarEmoji = hostAvatar,
                teamColor = RoomParticipant.TEAM_RED,
                score = 0,
                isReady = true,
                isHost = true,
                joinedAtMillis = now,
                isOfflineOnly = room.isOfflineOnly
            ),
            pushToRemote = !room.isOfflineOnly
        )

        return RoomResult.Success(room)
    }

    /**
     * توليد كود غرفة بصيغة مطابقة للأكواد الحالية في التطبيق (مثل `STAD77`):
     * 4 أحرف + رقمين، مع استبعاد الأحرف الملتبسة (I, O) من الأبجدية.
     */
    fun generateRoomCode(): String {
        val letters = buildString {
            repeat(CODE_LETTER_COUNT) { append(CODE_ALPHABET[random.nextInt(CODE_ALPHABET.length)]) }
        }
        val digits = buildString {
            repeat(CODE_DIGIT_COUNT) { append(random.nextInt(10)) }
        }
        return "$letters$digits"
    }

    // =========================================================================
    // 3) الانضمام لغرفة
    // =========================================================================

    /**
     * انضمام لاعب لغرفة موجودة، مع فحص السعة وتكرار الاسم وموازنة الفرق تلقائياً.
     * إعادة الانضمام بنفس الاسم عملية idempotent (تُعيد الغرفة بدون تكرار الصف).
     */
    suspend fun joinRoom(
        roomCode: String,
        playerName: String,
        avatarEmoji: String = RoomParticipant.DEFAULT_AVATAR,
        teamColor: String = RoomParticipant.TEAM_BLUE
    ): RoomResult<GameRoom> {
        val code = normalizeCode(roomCode)
        if (code.isEmpty()) return RoomResult.Failure(ERROR_ROOM_CODE_REQUIRED)

        val name = playerName.trim()
        if (name.isEmpty()) return RoomResult.Failure(ERROR_PLAYER_NAME_REQUIRED)
        if (name.length > RoomParticipant.MAX_NAME_LENGTH) {
            return RoomResult.Failure(ERROR_PLAYER_NAME_TOO_LONG)
        }

        val room = findRoom(code) ?: return RoomResult.Failure(ERROR_ROOM_NOT_FOUND)
        if (room.status == RoomStatus.FINISHED) return RoomResult.Failure(ERROR_ROOM_FINISHED)
        if (room.isFull) return RoomResult.Failure(ERROR_ROOM_FULL)

        val existing = cachedParticipants(code)
        val alreadyJoined = existing.any { it.playerName.trim().equals(name, ignoreCase = true) }
        if (alreadyJoined) return RoomResult.Success(room)

        val participant = RoomParticipant(
            roomCode = code,
            playerName = name,
            avatarEmoji = avatarEmoji,
            teamColor = pickTeamColor(existing, teamColor),
            score = 0,
            isReady = false,
            isHost = false,
            joinedAtMillis = timeProvider(),
            isOfflineOnly = room.isOfflineOnly
        )
        persistParticipant(participant, pushToRemote = isOnline() && !room.isOfflineOnly)

        val refreshed = localDao.getRoomByCode(code)?.toDomain()
            ?: room.copy(participantCount = existing.size + 1)
        return RoomResult.Success(refreshed)
    }

    // =========================================================================
    // 4) جلب الغرف
    // =========================================================================

    /**
     * تحديث قائمة الغرف من السيرفر وتحديث الكاش.
     * عند الفشل أو غياب الشبكة تُعاد الغرف المكاشّة محلياً (لا تُعيد قائمة فارغة).
     */
    suspend fun refreshRooms(limit: Int = DEFAULT_FETCH_LIMIT): RoomResult<List<GameRoom>> {
        if (isOnline()) {
            val remote = runCatching { remoteDao.fetchRooms(limit) }.getOrNull()
            if (remote != null) {
                cacheRooms(remote)
                return RoomResult.Success(remote.sortedWith(ROOM_COMPARATOR))
            }
        }
        val cached = localDao.getRecentRooms(limit).map { it.toDomain() }.sortedWith(ROOM_COMPARATOR)
        return RoomResult.Success(cached)
    }

    /** غرفة واحدة بكودها (السيرفر أولاً ثم الكاش). */
    suspend fun getRoom(roomCode: String): GameRoom? = findRoom(normalizeCode(roomCode))

    // =========================================================================
    // 5) قائمة المشاركين (Participant Listing)
    // =========================================================================

    /**
     * جلب مشاركي غرفة بترتيب العرض المعتمد: المضيف أولاً، ثم الجاهزون،
     * ثم الأعلى نقاطاً، ثم الأقدم انضماماً، وأخيراً أبجدياً.
     *
     * @param refresh جلب من السيرفر وتحديث الكاش (افتراضياً نعم).
     * @return القائمة جاهزة للعرض مباشرة — من السيرفر، أو من الكاش عند الفشل.
     */
    suspend fun listParticipants(
        roomCode: String,
        refresh: Boolean = true
    ): RoomResult<List<RoomParticipant>> {
        val code = normalizeCode(roomCode)
        if (code.isEmpty()) return RoomResult.Failure(ERROR_ROOM_CODE_REQUIRED)

        if (refresh && isOnline()) {
            val remote = runCatching { remoteDao.fetchParticipants(code) }.getOrNull()
            if (remote != null) return RoomResult.Success(mergeParticipants(code, remote))
        }
        return RoomResult.Success(cachedParticipants(code))
    }

    /** عدد المشاركين الحاليين في الكاش المحلي. */
    suspend fun participantCount(roomCode: String): Int =
        localDao.countParticipants(normalizeCode(roomCode))

    // =========================================================================
    // 6) تحديثات أثناء اللعب
    // =========================================================================

    /** تبديل جاهزية مشارك (مع تحديث الكاش فوراً حتى لو فشل السيرفر). */
    suspend fun setParticipantReady(roomCode: String, playerName: String, isReady: Boolean): Boolean {
        val code = normalizeCode(roomCode)
        val key = RoomParticipant.participantKey(code, playerName)
        val local = localDao.getParticipants(code).firstOrNull { it.participantKey == key }
            ?: return false

        val updated = local.copy(isReady = isReady)
        val synced = pushParticipantUpdate(updated)
        localDao.upsertParticipant(updated.copy(isPendingSync = !synced))
        return synced
    }

    /** تحديث نقاط مشارك (تُستخدم في جولات اللعب المباشر). */
    suspend fun updateParticipantScore(roomCode: String, playerName: String, score: Int): Boolean {
        val code = normalizeCode(roomCode)
        val key = RoomParticipant.participantKey(code, playerName)
        val local = localDao.getParticipants(code).firstOrNull { it.participantKey == key }
            ?: return false

        val updated = local.copy(score = score.coerceAtLeast(0))
        val synced = pushParticipantUpdate(updated)
        localDao.upsertParticipant(updated.copy(isPendingSync = !synced))
        return synced
    }

    /** بدء اللعب: الحالة `playing` وجولة أولى. */
    suspend fun startGame(roomCode: String): Boolean =
        updateRoomState(roomCode, RoomStatus.PLAYING, currentRound = 1)

    /** الانتقال للجولة التالية مع تحديث الحالة محلياً وبعيداً. */
    suspend fun advanceRound(roomCode: String): Boolean {
        val code = normalizeCode(roomCode)
        val local = localDao.getRoomByCode(code) ?: return false
        return updateRoomState(code, RoomStatus.PLAYING, local.currentRound + 1)
    }

    /** إنهاء الغرفة. */
    suspend fun finishGame(roomCode: String): Boolean {
        val code = normalizeCode(roomCode)
        val local = localDao.getRoomByCode(code) ?: return false
        return updateRoomState(code, RoomStatus.FINISHED, local.currentRound)
    }

    private suspend fun updateRoomState(
        roomCode: String,
        status: RoomStatus,
        currentRound: Int
    ): Boolean {
        val code = normalizeCode(roomCode)
        val local = localDao.getRoomByCode(code) ?: return false

        val synced = if (isOnline() && !local.isPendingSync) {
            runCatching { remoteDao.updateRoomState(code, status, currentRound) }.getOrDefault(false)
        } else {
            false
        }

        localDao.upsertRoom(
            local.copy(
                statusWire = status.wire,
                currentRound = currentRound,
                isPendingSync = local.isPendingSync || !synced,
                lastSyncedAtMillis = if (synced) timeProvider() else local.lastSyncedAtMillis
            )
        )
        return synced
    }

    // =========================================================================
    // 7) المزامنة (دفع الغرف/المشاركين المُنشأة أوفلاين)
    // =========================================================================

    /**
     * دفع كل ما أُنشئ بدون إنترنت إلى Supabase.
     * @return عدد الغرف التي تمت مزامنتها بنجاح.
     */
    suspend fun syncPendingRooms(): Int {
        if (!isOnline()) return 0

        var syncedRooms = 0
        localDao.getPendingSyncRooms().forEach { entity ->
            val room = entity.toDomain()
            val created = runCatching { remoteDao.createRoom(room) }.getOrNull() ?: return@forEach
            localDao.markRoomSynced(entity.roomCode, timeProvider())
            localDao.upsertRoom(
                entity.copy(
                    remoteId = created.remoteId ?: entity.remoteId,
                    isPendingSync = false,
                    lastSyncedAtMillis = timeProvider()
                )
            )
            syncedRooms++
        }

        localDao.getPendingSyncParticipants().forEach { entity ->
            val pushed = runCatching { remoteDao.addParticipant(entity.toDomain()) }.getOrDefault(false)
            if (pushed) localDao.markParticipantSynced(entity.participantKey)
        }

        return syncedRooms
    }

    /** حذف غرفة من الكاش المحلي فقط (لا تحذف من السيرفر). */
    suspend fun forgetRoom(roomCode: String) {
        val code = normalizeCode(roomCode)
        localDao.clearParticipants(code)
        localDao.deleteRoom(code)
    }

    // =========================================================================
    // INTERNALS
    // =========================================================================

    /** السيرفر أولاً (مع تحديث الكاش)، ثم الكاش المحلي كاحتياطي. */
    private suspend fun findRoom(roomCode: String): GameRoom? {
        val code = normalizeCode(roomCode)
        if (code.isEmpty()) return null

        if (isOnline()) {
            val remote = runCatching { remoteDao.fetchRoomByCode(code) }.getOrNull()
            if (remote != null) {
                val cached = localDao.getRoomByCode(code)
                val merged = remote.copy(
                    participantCount = if (remote.participantCount > 0) {
                        remote.participantCount
                    } else {
                        cached?.participantCount ?: 0
                    }
                )
                localDao.upsertRoom(merged.toEntity(pendingSync = false, lastSyncedAtMillis = timeProvider()))
                return merged
            }
        }
        return localDao.getRoomByCode(code)?.toDomain()
    }

    private suspend fun cachedParticipants(roomCode: String): List<RoomParticipant> =
        localDao.getParticipants(roomCode).map { it.toDomain() }.sortedWith(PARTICIPANT_COMPARATOR)

    /**
     * دمج مشاركين قادمين من السيرفر مع الكاش:
     * نستبدل صفوف الغرفة بما جاء من السيرفر، مع الحفاظ على الصفوف المحلية
     * التي ما زالت بانتظار المزامنة (حتى لا يضيع لاعب أُضيف أوفلاين).
     */
    private suspend fun mergeParticipants(
        roomCode: String,
        remote: List<RoomParticipant>
    ): List<RoomParticipant> {
        val remoteEntities = remote.map { it.toEntity(pendingSync = false) }
        val remoteKeys = remoteEntities.map { it.participantKey }.toSet()
        val pendingLocal = localDao.getParticipants(roomCode)
            .filter { it.isPendingSync && it.participantKey !in remoteKeys }

        localDao.clearParticipants(roomCode)
        localDao.upsertParticipants(remoteEntities + pendingLocal)
        localDao.updateParticipantCount(roomCode, remoteEntities.size + pendingLocal.size)

        return (remoteEntities + pendingLocal).map { it.toDomain() }.sortedWith(PARTICIPANT_COMPARATOR)
    }

    private suspend fun cacheRooms(rooms: List<GameRoom>) {
        val now = timeProvider()
        localDao.upsertRooms(rooms.map { it.toEntity(pendingSync = false, lastSyncedAtMillis = now) })

        // تنظيف الغرف القديمة التي لم تعد في السيرفر (بدون لمس الغرف المنتظرة مزامنة)
        val freshCodes = rooms.map { it.roomCode }.toSet()
        val stale = localDao.getRecentRooms(PRUNE_SCAN_LIMIT)
            .filter { !it.isPendingSync && it.roomCode !in freshCodes }
        stale.forEach {
            localDao.clearParticipants(it.roomCode)
            localDao.deleteRoom(it.roomCode)
        }
    }

    private suspend fun persistParticipant(participant: RoomParticipant, pushToRemote: Boolean) {
        val entity = participant.toEntity(pendingSync = false)
        val synced = if (pushToRemote) {
            runCatching { remoteDao.addParticipant(participant) }.getOrDefault(false)
        } else {
            false
        }
        localDao.upsertParticipant(entity.copy(isPendingSync = !synced))
        localDao.updateParticipantCount(participant.roomCode, localDao.countParticipants(participant.roomCode))
    }

    /**
     * دفع تعديل مشارك للسيرفر.
     * نتخطى الدفع إذا كنا أوفلاين أو إذا كان الصف أصلاً بانتظار مزامنة
     * (حتى لا نحاول تعديل صف غير موجود في السيرفر بعد).
     */
    private suspend fun pushParticipantUpdate(entity: RoomParticipantEntity): Boolean {
        if (!isOnline() || entity.isPendingSync) return false
        return runCatching { remoteDao.updateParticipant(entity.toDomain()) }.getOrDefault(false)
    }

    /** يعتمد الكود المفضّل إن كان فريداً، وإلا يولّد كوداً فريداً. */
    private suspend fun resolveRoomCode(preferredCode: String?): String {
        val preferred = normalizeCode(preferredCode.orEmpty())
        if (preferred.isNotEmpty() && isCodeAvailable(preferred)) return preferred

        repeat(MAX_CODE_ATTEMPTS) {
            val candidate = generateRoomCode()
            if (isCodeAvailable(candidate)) return candidate
        }
        // احتياط أخير: كود أطول نادراً ما يتكرر
        return generateRoomCode() + random.nextInt(10)
    }

    private suspend fun isCodeAvailable(code: String): Boolean {
        if (localDao.countRoomsWithCode(code) > 0) return false
        if (!isOnline()) return true
        // null = غير موجودة أو تعذّر التحقق → نسمح بالإنشاء (قد يكتمل أوفلاين)
        return runCatching { remoteDao.fetchRoomByCode(code) == null }.getOrDefault(true)
    }

    private fun normalizeCode(raw: String): String = RoomParticipant.normalizeRoomCode(raw)

    companion object {
        const val DEFAULT_FETCH_LIMIT = 20
        const val CODE_LETTER_COUNT = 4
        const val CODE_DIGIT_COUNT = 2

        /** أحرف بدون I و O لتجنّب الالتباس البصري عند قراءة الكود. */
        val CODE_ALPHABET: String = "ABCDEFGHJKLMNPQRSTUVWXYZ"

        private const val MAX_CODE_ATTEMPTS = 8
        private const val PRUNE_SCAN_LIMIT = 100

        // ----------------------- رسائل عربية للواجهة -----------------------
        const val ERROR_HOST_NAME_REQUIRED = "اكتب اسم المضيف أولاً قبل إنشاء الغرفة 👤"
        const val ERROR_HOST_NAME_TOO_LONG = "اسم المضيف طويل جداً (الحد 24 حرفاً) ✂️"
        const val ERROR_PLAYER_NAME_REQUIRED = "اكتب اسمك للانضمام إلى الغرفة 👤"
        const val ERROR_PLAYER_NAME_TOO_LONG = "اسم اللاعب طويل جداً (الحد 24 حرفاً) ✂️"
        const val ERROR_INVALID_MAX_PLAYERS = "عدد اللاعبين يجب أن يكون بين 2 و 12 👥"
        const val ERROR_INVALID_MAX_ROUNDS = "عدد الجولات يجب أن يكون بين 1 و 20 🔁"
        const val ERROR_ROOM_CODE_REQUIRED = "أدخل كود الغرفة أولاً 🔑"
        const val ERROR_ROOM_NOT_FOUND = "الغرفة غير موجودة أو تعذّر الوصول للسيرفر 🔌"
        const val ERROR_ROOM_FULL = "الغرفة ممتلئة، جرّب غرفة أخرى ⛔"
        const val ERROR_ROOM_FINISHED = "هذه الغرفة أنهت لعبها بالفعل 🏁"

        /** ترتيب عرض الغرف: غير المنتهية أولاً ثم الأحدث. */
        val ROOM_COMPARATOR: Comparator<GameRoom> =
            compareBy<GameRoom> { it.status == RoomStatus.FINISHED }
                .thenByDescending { it.createdAtMillis }
                .thenBy { it.roomCode }

        /** ترتيب عرض المشاركين: المضيف، فالجاهز، فالأعلى نقاطاً، فالأقدم انضماماً. */
        val PARTICIPANT_COMPARATOR: Comparator<RoomParticipant> =
            compareByDescending<RoomParticipant> { it.isHost }
                .thenByDescending { it.isReady }
                .thenByDescending { it.score }
                .thenBy { it.joinedAtMillis }
                .thenBy { it.playerName }

        /**
         * موازنة الفرق تلقائياً: نحترم اختيار اللاعب ما دام لن يزيد الفارق عن 1،
         * وإلا نضعه في الفريق الأقل عدداً.
         */
        fun pickTeamColor(existing: List<RoomParticipant>, requested: String): String {
            val red = existing.count { it.isTeamRed }
            val blue = existing.size - red
            val wanted = RoomParticipant.normalizeTeamColor(requested)

            val afterRed = if (wanted == RoomParticipant.TEAM_RED) red + 1 else red
            val afterBlue = if (wanted == RoomParticipant.TEAM_BLUE) blue + 1 else blue

            return if (abs(afterRed - afterBlue) <= 1) {
                wanted
            } else {
                if (red <= blue) RoomParticipant.TEAM_RED else RoomParticipant.TEAM_BLUE
            }
        }

        // ---------------------- بناء المستودع للتطبيق ----------------------
        @Volatile
        private var instance: GameRoomRepository? = null

        /**
         * نسخة التطبيق الواحدة: تربط DAO المحلي (Room) مع DAO البعيد (Supabase)
         * وتُهيّئ مفاتيح الاتصال من متغيرات البيئة.
         */
        fun get(context: Context): GameRoomRepository = instance ?: synchronized(this) {
            instance ?: build(context).also { instance = it }
        }

        private fun build(context: Context): GameRoomRepository {
            val appContext = context.applicationContext
            val api = SupabaseClientProvider.initialize(appContext)
            val database = AppDatabase.getDatabase(appContext)
            return GameRoomRepository(
                localDao = database.gameRoomDao(),
                remoteDao = SupabaseRoomsDao(api)
            )
        }
    }
}
