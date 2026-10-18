package com.example.data.room

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * اختبارات وحدة لمنطق [GameRoomRepository]:
 * إنشاء الغرف، قائمة المشاركين، الانضمام، المزامنة الأوفلاين، والترتيب.
 *
 * لا تحتاج Context ولا شبكة ولا Room حقيقي — كل شيء عبر [FakeGameRoomDao]
 * و [FakeRoomsRemoteDataSource]، وهذا سبب فصل المنطق في المستودع عن مصادر البيانات.
 */
class GameRoomRepositoryTest {

    // =========================================================================
    // إنشاء الغرف (Room Creation)
    // =========================================================================

    @Test
    fun `createRoom online ينشئ الغرفة في السيرفر ويضيف المضيف كأول مشارك`() = runTest {
        val local = FakeGameRoomDao()
        val remote = FakeRoomsRemoteDataSource()
        val repository = repository(local, remote, online = true)

        val result = repository.createRoom(hostName = "كابتن عمر", gameType = "buzzer")

        val room = result.getOrNull()
        assertNotNull("الإنشاء أونلاين يجب أن ينجح", room)
        requireNotNull(room)
        assertEquals("buzzer", room.gameType)
        assertEquals("كابتن عمر", room.hostName)
        assertEquals(1, room.participantCount)
        assertFalse("أونلاين = ليست أوفلاين", room.isOfflineOnly)

        // السيرفر استقبل الغرفة والمضيف
        assertEquals(1, remote.createdRooms.size)
        assertEquals(1, remote.participants.size)
        assertTrue(remote.participants.values.first().isHost)
        assertTrue(remote.participants.values.first().isReady)

        // الكاش المحلي مطابق
        assertNotNull(local.getRoomByCode(room.roomCode))
        assertEquals(1, local.countParticipants(room.roomCode))
        assertFalse(local.getRoomByCode(room.roomCode)!!.isPendingSync)
    }

    @Test
    fun `createRoom بدون شبكة يحفظ الغرفة محلياً بعلامة انتظار المزامنة`() = runTest {
        val local = FakeGameRoomDao()
        val remote = FakeRoomsRemoteDataSource()
        val repository = repository(local, remote, online = false)

        val result = repository.createRoom(hostName = "لاعب أوفلاين")

        val room = result.getOrNull()
        requireNotNull(room)
        assertTrue("أوفلاين = isOfflineOnly", room.isOfflineOnly)
        assertEquals("لا يجب استدعاء السيرفر إطلاقاً", 0, remote.createdRooms.size)
        assertEquals(0, remote.participants.size)

        val cached = local.getRoomByCode(room.roomCode)
        assertNotNull(cached)
        assertTrue("يجب تعليمها بانتظار المزامنة", cached!!.isPendingSync)
        assertEquals(1, local.getPendingSyncParticipants().size)
    }

    @Test
    fun `createRoom عند فشل السيرفر يرجع للوضع الأوفلاين بدل فقدان الغرفة`() = runTest {
        val local = FakeGameRoomDao()
        val remote = FakeRoomsRemoteDataSource().apply { failWrites = true }
        val repository = repository(local, remote, online = true)

        val result = repository.createRoom(hostName = "كابتن")

        val room = result.getOrNull()
        requireNotNull(room)
        assertTrue("فشل الشبكة ⇒ أوفلاين", room.isOfflineOnly)
        assertTrue(local.getRoomByCode(room.roomCode)!!.isPendingSync)
    }

    @Test
    fun `createRoom يرفض اسم مضيف فارغ برسالة عربية`() = runTest {
        val repository = repository(FakeGameRoomDao(), FakeRoomsRemoteDataSource())

        val result = repository.createRoom(hostName = "   ")

        assertTrue(result is RoomResult.Failure)
        assertEquals(GameRoomRepository.ERROR_HOST_NAME_REQUIRED, result.failureMessageOrNull())
    }

    @Test
    fun `createRoom يرفض عدد لاعبين خارج النطاق`() = runTest {
        val repository = repository(FakeGameRoomDao(), FakeRoomsRemoteDataSource())

        assertEquals(
            GameRoomRepository.ERROR_INVALID_MAX_PLAYERS,
            repository.createRoom(hostName = "كابتن", maxPlayers = 1).failureMessageOrNull()
        )
        assertEquals(
            GameRoomRepository.ERROR_INVALID_MAX_PLAYERS,
            repository.createRoom(hostName = "كابتن", maxPlayers = 99).failureMessageOrNull()
        )
    }

    @Test
    fun `createRoom يرفض اسم مضيف طويلاً جداً`() = runTest {
        val repository = repository(FakeGameRoomDao(), FakeRoomsRemoteDataSource())
        val longName = "ا".repeat(RoomParticipant.MAX_NAME_LENGTH + 5)

        assertEquals(
            GameRoomRepository.ERROR_HOST_NAME_TOO_LONG,
            repository.createRoom(hostName = longName).failureMessageOrNull()
        )
    }

    // =========================================================================
    // توليد أكواد الغرف
    // =========================================================================

    @Test
    fun `generateRoomCode ينتج 4 أحرف ورقمين بدون أحرف ملتبسة`() {
        val repository = repository(FakeGameRoomDao(), FakeRoomsRemoteDataSource(), random = Random(7))

        repeat(500) {
            val code = repository.generateRoomCode()
            assertTrue("طول الكود يجب أن يكون 6: $code", code.length == 6)
            assertTrue("أول 4 أحرف: $code", code.take(4).all { it in 'A'..'Z' })
            assertTrue("آخر رقمين: $code", code.takeLast(2).all { it.isDigit() })
            assertFalse("بدون I: $code", code.contains('I'))
            assertFalse("بدون O: $code", code.contains('O'))
        }
    }

    @Test
    fun `generateRoomCode لا يكرر نفس الكود كثيراً`() {
        val repository = repository(FakeGameRoomDao(), FakeRoomsRemoteDataSource(), random = Random(99))
        val codes = (1..200).map { repository.generateRoomCode() }.toSet()
        assertTrue("يجب أن يكون التنوع عالياً، وجدنا ${codes.size}", codes.size > 150)
    }

    @Test
    fun `createRoom يعتمد الكود المفضّل عندما يكون فريداً`() = runTest {
        val repository = repository(FakeGameRoomDao(), FakeRoomsRemoteDataSource(), online = false)

        val room = repository.createRoom(hostName = "كابتن", preferredCode = "STAD77").getOrNull()

        requireNotNull(room)
        assertEquals("STAD77", room.roomCode)
    }

    @Test
    fun `createRoom يستبدل الكود المفضّل المحجوز بكود آخر`() = runTest {
        val local = FakeGameRoomDao()
        val repository = repository(local, FakeRoomsRemoteDataSource(), online = false)

        repository.createRoom(hostName = "المضيف الأول", preferredCode = "STAD77")
        val second = repository.createRoom(hostName = "المضيف الثاني", preferredCode = "STAD77").getOrNull()

        requireNotNull(second)
        assertTrue("يجب ألا يعيد نفس الكود", second.roomCode != "STAD77")
        assertEquals("كود جديد بصيغة صحيحة", 6, second.roomCode.length)
    }

    @Test
    fun `createRoom يوحّد شكل الكود المفضّل (حروف كبيرة وبدون رموز)`() = runTest {
        val repository = repository(FakeGameRoomDao(), FakeRoomsRemoteDataSource(), online = false)

        val room = repository.createRoom(hostName = "كابتن", preferredCode = " stad-77 ").getOrNull()

        requireNotNull(room)
        assertEquals("STAD77", room.roomCode)
    }

    // =========================================================================
    // قائمة المشاركين (Participant Listing)
    // =========================================================================

    @Test
    fun `listParticipants يعيد المضيف أولاً ثم الجاهزين فالأعلى نقاطاً`() = runTest {
        val local = FakeGameRoomDao()
        val remote = FakeRoomsRemoteDataSource().apply {
            seedRoom("STAD77")
            seedParticipant("STAD77", "لاعب ثالث", isHost = false, isReady = false, score = 50, joinedAt = 3L)
            seedParticipant("STAD77", "لاعب جاهز", isHost = false, isReady = true, score = 10, joinedAt = 2L)
            seedParticipant("STAD77", "المضيف", isHost = true, isReady = true, score = 0, joinedAt = 1L)
        }
        val repository = repository(local, remote, online = true)

        val participants = repository.listParticipants("stad77").getOrNull()

        requireNotNull(participants)
        assertEquals(3, participants.size)
        assertEquals("المضيف", participants[0].playerName)
        assertEquals("لاعب جاهز", participants[1].playerName)
        assertEquals("لاعب ثالث", participants[2].playerName)
        // الكاش المحلي تمت تحديثه
        assertEquals(3, local.countParticipants("STAD77"))
    }

    @Test
    fun `listParticipants بدون شبكة يعيد الكاش المحلي`() = runTest {
        val local = FakeGameRoomDao()
        val remote = FakeRoomsRemoteDataSource()
        val offlineRepository = repository(local, remote, online = false)
        offlineRepository.createRoom(hostName = "المضيف", preferredCode = "GRID33")

        val participants = offlineRepository.listParticipants("GRID33").getOrNull()

        requireNotNull(participants)
        assertEquals(1, participants.size)
        assertEquals("المضيف", participants[0].playerName)
        assertEquals(0, remote.fetchParticipantsCalls)
    }

    @Test
    fun `listParticipants يحافظ على المشارك المحلي المنتظر مزامنة أثناء الدمج`() = runTest {
        val local = FakeGameRoomDao()
        val remote = FakeRoomsRemoteDataSource().apply {
            seedRoom("BUZZ22")
            seedParticipant("BUZZ22", "المضيف", isHost = true, isReady = true, joinedAt = 1L)
        }
        // إنشاء أوفلاين ⇒ مشارك محلي بعلامة isPendingSync
        val offlineRepository = repository(local, remote, online = false)
        offlineRepository.createRoom(hostName = "لاعب محلي", preferredCode = "BUZZ22")

        val onlineRepository = repository(local, remote, online = true)
        val participants = onlineRepository.listParticipants("BUZZ22").getOrNull()

        requireNotNull(participants)
        val names = participants.map { it.playerName }.toSet()
        assertTrue("المضيف من السيرفر", "المضيف" in names)
        assertTrue("المحلي المنتظر مزامنة يجب ألا يضيع", "لاعب محلي" in names)
    }

    @Test
    fun `listParticipants يرجع للكاش عند فشل السيرفر`() = runTest {
        val local = FakeGameRoomDao()
        val remote = FakeRoomsRemoteDataSource().apply {
            seedRoom("STAD11")
            seedParticipant("STAD11", "المضيف", isHost = true, joinedAt = 1L)
            failReads = true
        }
        val repository = repository(local, remote, online = true)
        // تعبئة الكاش أولاً بصف محلي مباشر
        local.upsertParticipant(
            RoomParticipantEntity(
                participantKey = RoomParticipant.participantKey("STAD11", "من الكاش"),
                roomCode = "STAD11",
                playerName = "من الكاش",
                isHost = true,
                isPendingSync = false
            )
        )

        val participants = repository.listParticipants("STAD11").getOrNull()

        requireNotNull(participants)
        assertEquals(1, participants.size)
        assertEquals("من الكاش", participants[0].playerName)
    }

    @Test
    fun `listParticipants بكود فارغ يفشل برسالة عربية`() = runTest {
        val repository = repository(FakeGameRoomDao(), FakeRoomsRemoteDataSource())

        assertEquals(
            GameRoomRepository.ERROR_ROOM_CODE_REQUIRED,
            repository.listParticipants("  ---  ").failureMessageOrNull()
        )
    }

    // =========================================================================
    // الانضمام للغرفة
    // =========================================================================

    @Test
    fun `joinRoom يرفض غرفة غير موجودة`() = runTest {
        val repository = repository(FakeGameRoomDao(), FakeRoomsRemoteDataSource(), online = true)

        assertEquals(
            GameRoomRepository.ERROR_ROOM_NOT_FOUND,
            repository.joinRoom("NOPE99", "لاعب").failureMessageOrNull()
        )
    }

    @Test
    fun `joinRoom يرفض غرفة ممتلئة`() = runTest {
        val local = FakeGameRoomDao()
        val remote = FakeRoomsRemoteDataSource().apply {
            seedRoom("FULL01", maxPlayers = 2, participantCount = 2)
        }
        val repository = repository(local, remote, online = true)

        assertEquals(
            GameRoomRepository.ERROR_ROOM_FULL,
            repository.joinRoom("FULL01", "لاعب جديد").failureMessageOrNull()
        )
        assertEquals("لم يُضف أحد", 0, remote.participants.size)
    }

    @Test
    fun `joinRoom يرفض غرفة منتهية`() = runTest {
        val remote = FakeRoomsRemoteDataSource().apply {
            seedRoom("DONE01", status = RoomStatus.FINISHED)
        }
        val repository = repository(FakeGameRoomDao(), remote, online = true)

        assertEquals(
            GameRoomRepository.ERROR_ROOM_FINISHED,
            repository.joinRoom("DONE01", "لاعب").failureMessageOrNull()
        )
    }

    @Test
    fun `joinRoom بنفس الاسم مرتين لا يكرر المشارك`() = runTest {
        val local = FakeGameRoomDao()
        val remote = FakeRoomsRemoteDataSource().apply { seedRoom("STAD77") }
        val repository = repository(local, remote, online = true)

        val first = repository.joinRoom("STAD77", "عمر")
        val second = repository.joinRoom("STAD77", "عمر")

        assertTrue(first.isSuccess)
        assertTrue(second.isSuccess)
        assertEquals("صف واحد فقط في السيرفر", 1, remote.participants.size)
        assertEquals(1, local.countParticipants("STAD77"))
    }

    @Test
    fun `joinRoom يضيف المشارك ويحدّث عدد المشاركين`() = runTest {
        val local = FakeGameRoomDao()
        val remote = FakeRoomsRemoteDataSource().apply {
            seedRoom("STAD77", maxPlayers = 6)
            seedParticipant("STAD77", "المضيف", isHost = true, teamColor = RoomParticipant.TEAM_RED)
        }
        val repository = repository(local, remote, online = true)
        // تعبئة الكاش بالغرف والمشاركين من السيرفر (كما تفعل الشاشة عند فتحها)
        repository.refreshRooms()
        repository.listParticipants("STAD77")

        val result = repository.joinRoom("STAD77", "منافس", teamColor = RoomParticipant.TEAM_BLUE)

        val room = result.getOrNull()
        requireNotNull(room)
        assertEquals("المضيف + المنافس", 2, local.countParticipants("STAD77"))
        assertEquals(2, remote.participants.size)
        assertEquals(
            RoomParticipant.TEAM_BLUE,
            remote.participants.values.first { it.playerName == "منافس" }.teamColor
        )
        assertFalse("المنافس ليس مضيفاً", remote.participants.values.first { it.playerName == "منافس" }.isHost)
    }

    // =========================================================================
    // المزامنة
    // =========================================================================

    @Test
    fun `syncPendingRooms يدفع الغرف الأوفلاين ويعيد عددها`() = runTest {
        val local = FakeGameRoomDao()
        val remote = FakeRoomsRemoteDataSource()
        val offlineRepository = repository(local, remote, online = false)
        offlineRepository.createRoom(hostName = "كابتن أ", preferredCode = "AAAA11")
        offlineRepository.createRoom(hostName = "كابتن ب", preferredCode = "BBBB22")
        assertEquals(2, local.getPendingSyncRooms().size)

        val onlineRepository = repository(local, remote, online = true)
        val synced = onlineRepository.syncPendingRooms()

        assertEquals(2, synced)
        assertEquals(2, remote.createdRooms.size)
        assertEquals("لا شيء متبقٍ", 0, local.getPendingSyncRooms().size)
        assertEquals(2, remote.participants.size)
    }

    @Test
    fun `syncPendingRooms بدون شبكة يعيد صفر`() = runTest {
        val local = FakeGameRoomDao()
        val repository = repository(local, FakeRoomsRemoteDataSource(), online = false)
        repository.createRoom(hostName = "كابتن")

        assertEquals(0, repository.syncPendingRooms())
    }

    @Test
    fun `refreshRooms ينظّف الغرف القديمة ويُبقي المنتظرة مزامنة`() = runTest {
        val local = FakeGameRoomDao()
        val remote = FakeRoomsRemoteDataSource().apply {
            seedRoom("NEW001")
        }
        val repository = repository(local, remote, online = true)
        // غرفة قديمة في الكاش (ليست pending) + غرفة أوفلاين pending
        local.upsertRoom(
            GameRoomEntity(
                roomCode = "OLD999",
                hostName = "قديم",
                createdAtMillis = 1L,
                isPendingSync = false
            )
        )
        local.upsertRoom(
            GameRoomEntity(
                roomCode = "PEND01",
                hostName = "منتظر",
                createdAtMillis = 2L,
                isPendingSync = true
            )
        )

        repository.refreshRooms()

        assertNotNull("الغرفة الجديدة", local.getRoomByCode("NEW001"))
        assertNull("القديمة تُحذف", local.getRoomByCode("OLD999"))
        assertNotNull("المنتظرة مزامنة تبقى", local.getRoomByCode("PEND01"))
    }

    // =========================================================================
    // منطق نقي: موازنة الفرق والترتيب وتوحيد الأكواد
    // =========================================================================

    @Test
    fun `pickTeamColor يحترم الاختيار عند التعادل`() {
        val existing = listOf(
            participant("أ", teamColor = RoomParticipant.TEAM_RED),
            participant("ب", teamColor = RoomParticipant.TEAM_BLUE)
        )
        assertEquals(
            RoomParticipant.TEAM_BLUE,
            GameRoomRepository.pickTeamColor(existing, "blue")
        )
    }

    @Test
    fun `pickTeamColor يوازن الفرق عند التفاوت الكبير`() {
        val existing = listOf(
            participant("أ", teamColor = RoomParticipant.TEAM_RED),
            participant("ب", teamColor = RoomParticipant.TEAM_RED),
            participant("ج", teamColor = RoomParticipant.TEAM_RED),
            participant("د", teamColor = RoomParticipant.TEAM_BLUE)
        )
        // طلب أزرق سيزيد الفارق ⇒ يُوضع في الفريق الأقل (الأزرق هنا أقل عدداً فعلاً)
        assertEquals(
            RoomParticipant.TEAM_BLUE,
            GameRoomRepository.pickTeamColor(existing, RoomParticipant.TEAM_BLUE)
        )
        // طلب أحمر (الفريق الأكبر) ⇒ يُحوَّل للأزرق
        assertEquals(
            RoomParticipant.TEAM_BLUE,
            GameRoomRepository.pickTeamColor(existing, RoomParticipant.TEAM_RED)
        )
    }

    @Test
    fun `PARTICIPANT_COMPARATOR يرتب المضيف ثم الجاهز ثم النقاط`() {
        val list = listOf(
            participant("عادي", isReady = false, score = 100, joinedAt = 4L),
            participant("جاهز", isReady = true, score = 10, joinedAt = 3L),
            participant("المضيف", isHost = true, isReady = true, score = 0, joinedAt = 1L),
            participant("نقاط أعلى", isReady = true, score = 90, joinedAt = 5L)
        )

        val sorted = list.sortedWith(GameRoomRepository.PARTICIPANT_COMPARATOR).map { it.playerName }

        assertEquals(listOf("المضيف", "نقاط أعلى", "جاهز", "عادي"), sorted)
    }

    @Test
    fun `ROOM_COMPARATOR يضع الغرف المنتهية في الآخر`() {
        val rooms = listOf(
            GameRoom(roomCode = "FIN1", hostName = "أ", status = RoomStatus.FINISHED, createdAtMillis = 5L),
            GameRoom(roomCode = "WAIT1", hostName = "ب", status = RoomStatus.WAITING, createdAtMillis = 1L),
            GameRoom(roomCode = "PLAY1", hostName = "ج", status = RoomStatus.PLAYING, createdAtMillis = 9L)
        )

        val sorted = rooms.sortedWith(GameRoomRepository.ROOM_COMPARATOR).map { it.roomCode }

        assertEquals(listOf("PLAY1", "WAIT1", "FIN1"), sorted)
    }

    @Test
    fun `normalizeRoomCode يبقي الأحرف الإنجليزية الكبيرة والأرقام فقط`() {
        assertEquals("STAD77", RoomParticipant.normalizeRoomCode(" stad-77 "))
        assertEquals("AB12", RoomParticipant.normalizeRoomCode("ab 12"))
        assertEquals("", RoomParticipant.normalizeRoomCode("غرفة-١٢"))
    }

    @Test
    fun `participantKey يوحّد الاسم ولا يفرّق بين حالة الأحرف`() {
        assertEquals(
            RoomParticipant.participantKey("STAD77", "عمر"),
            RoomParticipant.participantKey("stad77", "  عمر ")
        )
        assertEquals(
            RoomParticipant.participantKey("STAD77", "OMAR"),
            RoomParticipant.participantKey("STAD77", "omar")
        )
    }

    @Test
    fun `RoomStatus fromWire يتسامح مع القيم غير المعروفة`() {
        assertEquals(RoomStatus.WAITING, RoomStatus.fromWire("waiting"))
        assertEquals(RoomStatus.PLAYING, RoomStatus.fromWire(" PLAYING "))
        assertEquals(RoomStatus.FINISHED, RoomStatus.fromWire("finished"))
        assertEquals(RoomStatus.WAITING, RoomStatus.fromWire("قيمة غريبة"))
        assertEquals(RoomStatus.WAITING, RoomStatus.fromWire(null))
    }

    @Test
    fun `GameRoom يحسب السعة والحالة بشكل صحيح`() {
        val room = GameRoom(roomCode = "X1", hostName = "أ", participantCount = 6, maxPlayers = 6)
        assertTrue(room.isFull)
        assertEquals(0, room.seatsLeft)
        assertEquals("6/6 لاعب 👥", room.capacityLabel)

        val open = room.copy(participantCount = 2)
        assertFalse(open.isFull)
        assertEquals(4, open.seatsLeft)

        val rounds = GameRoom(roomCode = "X2", hostName = "ب", currentRound = 6, maxRounds = 5)
        assertTrue(rounds.isRoundsFinished)
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private fun repository(
        local: GameRoomDao,
        remote: RoomsRemoteDataSource,
        online: Boolean = true,
        random: Random = Random(1234),
        startTime: Long = 1_700_000_000_000L
    ): GameRoomRepository {
        var clock = startTime
        return GameRoomRepository(
            localDao = local,
            remoteDao = remote,
            isOnline = { online },
            random = random,
            timeProvider = { clock++ }
        )
    }

    private fun participant(
        name: String,
        isHost: Boolean = false,
        isReady: Boolean = false,
        score: Int = 0,
        joinedAt: Long = 0L,
        teamColor: String = RoomParticipant.TEAM_RED
    ) = RoomParticipant(
        roomCode = "TEST01",
        playerName = name,
        isHost = isHost,
        isReady = isReady,
        score = score,
        joinedAtMillis = joinedAt,
        teamColor = teamColor
    )
}

// =============================================================================
// Fakes
// =============================================================================

/** كاش محلي في الذاكرة ينفّذ كل عقود [GameRoomDao]. */
private class FakeGameRoomDao : GameRoomDao {

    private val rooms = LinkedHashMap<String, GameRoomEntity>()
    private val participants = LinkedHashMap<String, RoomParticipantEntity>()
    private val roomsFlow = MutableStateFlow<List<GameRoomEntity>>(emptyList())
    private val participantsFlow = MutableStateFlow<List<RoomParticipantEntity>>(emptyList())

    private fun refreshRoomsFlow() {
        roomsFlow.value = rooms.values.toList()
    }

    private fun refreshParticipantsFlow() {
        participantsFlow.value = participants.values.toList()
    }

    override fun observeRooms(): Flow<List<GameRoomEntity>> = roomsFlow

    override suspend fun getRecentRooms(limit: Int): List<GameRoomEntity> =
        rooms.values.sortedByDescending { it.createdAtMillis }.take(limit)

    override suspend fun getRoomByCode(roomCode: String): GameRoomEntity? = rooms[roomCode]

    override suspend fun countRoomsWithCode(roomCode: String): Int =
        if (rooms.containsKey(roomCode)) 1 else 0

    override suspend fun getRoomsCount(): Int = rooms.size

    override suspend fun upsertRoom(room: GameRoomEntity) {
        rooms[room.roomCode] = room
        refreshRoomsFlow()
    }

    override suspend fun upsertRooms(rooms: List<GameRoomEntity>) {
        rooms.forEach { this.rooms[it.roomCode] = it }
        refreshRoomsFlow()
    }

    override suspend fun deleteRoom(roomCode: String) {
        rooms.remove(roomCode)
        refreshRoomsFlow()
    }

    override suspend fun clearAllRooms() {
        rooms.clear()
        refreshRoomsFlow()
    }

    override suspend fun getPendingSyncRooms(): List<GameRoomEntity> =
        rooms.values.filter { it.isPendingSync }.sortedBy { it.createdAtMillis }

    override suspend fun markRoomSynced(roomCode: String, syncedAtMillis: Long) {
        rooms[roomCode]?.let { rooms[roomCode] = it.copy(isPendingSync = false, lastSyncedAtMillis = syncedAtMillis) }
        refreshRoomsFlow()
    }

    override suspend fun updateParticipantCount(roomCode: String, count: Int) {
        rooms[roomCode]?.let { rooms[roomCode] = it.copy(participantCount = count) }
        refreshRoomsFlow()
    }

    override fun observeParticipants(roomCode: String): Flow<List<RoomParticipantEntity>> = participantsFlow

    override suspend fun getParticipants(roomCode: String): List<RoomParticipantEntity> =
        participants.values.filter { it.roomCode == roomCode }

    override suspend fun countParticipants(roomCode: String): Int =
        participants.values.count { it.roomCode == roomCode }

    override suspend fun upsertParticipant(participant: RoomParticipantEntity) {
        participants[participant.participantKey] = participant
        refreshParticipantsFlow()
    }

    override suspend fun upsertParticipants(participants: List<RoomParticipantEntity>) {
        participants.forEach { this.participants[it.participantKey] = it }
        refreshParticipantsFlow()
    }

    override suspend fun deleteParticipant(participantKey: String) {
        participants.remove(participantKey)
        refreshParticipantsFlow()
    }

    override suspend fun clearParticipants(roomCode: String) {
        participants.values.filter { it.roomCode == roomCode }.forEach { participants.remove(it.participantKey) }
        refreshParticipantsFlow()
    }

    override suspend fun clearAllParticipants() {
        participants.clear()
        refreshParticipantsFlow()
    }

    override suspend fun getPendingSyncParticipants(): List<RoomParticipantEntity> =
        participants.values.filter { it.isPendingSync }.sortedBy { it.joinedAtMillis }

    override suspend fun markParticipantSynced(participantKey: String) {
        participants[participantKey]?.let {
            participants[participantKey] = it.copy(isPendingSync = false)
        }
        refreshParticipantsFlow()
    }
}

/**
 * سيرفر وهمي: يخزن الغرف/المشاركين في الذاكرة مع مفاتيح لمحاكاة فشل الشبكة
 * وعدّادات للتأكد من عدد الاستدعاءات.
 */
private class FakeRoomsRemoteDataSource : RoomsRemoteDataSource {

    val createdRooms = LinkedHashMap<String, GameRoom>()
    val participants = LinkedHashMap<String, RoomParticipant>()

    var failReads = false
    var failWrites = false
    var fetchParticipantsCalls = 0
    var fetchRoomsCalls = 0

    fun seedRoom(
        roomCode: String,
        gameType: String = "thirty_seconds",
        status: RoomStatus = RoomStatus.WAITING,
        hostName: String = "مضيف",
        maxPlayers: Int = 6,
        participantCount: Int = 0
    ) {
        createdRooms[roomCode] = GameRoom(
            roomCode = roomCode,
            hostName = hostName,
            gameType = gameType,
            status = status,
            maxPlayers = maxPlayers,
            participantCount = participantCount,
            remoteId = "uuid-$roomCode",
            createdAtMillis = System.currentTimeMillis()
        )
    }

    fun seedParticipant(
        roomCode: String,
        playerName: String,
        isHost: Boolean = false,
        isReady: Boolean = false,
        score: Int = 0,
        joinedAt: Long = 0L,
        teamColor: String = RoomParticipant.TEAM_RED
    ) {
        val participant = RoomParticipant(
            roomCode = roomCode,
            playerName = playerName,
            isHost = isHost,
            isReady = isReady,
            score = score,
            joinedAtMillis = joinedAt,
            teamColor = teamColor,
            remoteId = "uuid-$roomCode-$playerName"
        )
        participants[participant.localKey] = participant
        // حدّث عدد المشاركين في الغرفة المزروعة
        createdRooms[roomCode]?.let { room ->
            createdRooms[roomCode] = room.copy(participantCount = room.participantCount + 1)
        }
    }

    override suspend fun fetchRooms(limit: Int): List<GameRoom>? {
        fetchRoomsCalls++
        if (failReads) return null
        return createdRooms.values.sortedByDescending { it.createdAtMillis }.take(limit)
    }

    override suspend fun fetchRoomByCode(roomCode: String): GameRoom? {
        if (failReads) return null
        return createdRooms.values.firstOrNull {
            it.roomCode.equals(roomCode.trim(), ignoreCase = true)
        }
    }

    override suspend fun createRoom(room: GameRoom): GameRoom? {
        if (failWrites) return null
        val created = room.copy(remoteId = "uuid-${room.roomCode}")
        createdRooms[created.roomCode] = created
        return created
    }

    override suspend fun fetchParticipants(roomCode: String): List<RoomParticipant>? {
        fetchParticipantsCalls++
        if (failReads) return null
        return participants.values.filter { it.roomCode == roomCode }.sortedBy { it.joinedAtMillis }
    }

    override suspend fun addParticipant(participant: RoomParticipant): Boolean {
        if (failWrites) return false
        participants[participant.localKey] = participant.copy(remoteId = "uuid-${participant.localKey}")
        createdRooms[participant.roomCode]?.let { room ->
            createdRooms[participant.roomCode] = room.copy(participantCount = room.participantCount + 1)
        }
        return true
    }

    override suspend fun updateParticipant(participant: RoomParticipant): Boolean {
        if (failWrites) return false
        val key = participant.localKey
        if (!participants.containsKey(key)) return false
        participants[key] = participant
        return true
    }

    override suspend fun updateRoomState(
        roomCode: String,
        status: RoomStatus,
        currentRound: Int
    ): Boolean {
        if (failWrites) return false
        val room = createdRooms[roomCode] ?: return false
        createdRooms[roomCode] = room.copy(status = status, currentRound = currentRound)
        return true
    }
}
