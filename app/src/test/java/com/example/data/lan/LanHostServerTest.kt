package com.example.data.lan

import com.example.data.room.GameRoom
import com.example.data.room.GameRoomRepository
import com.example.data.room.RoomParticipant
import com.example.data.room.RoomStatus
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * اختبارات سيرفر الشبكة المحلية ومصدر بياناته.
 *
 * مفيش sockets حقيقية هنا: [LanHostServer] بيحتفظ بحالة الغرفة في الذاكرة،
 * و`start()` (اللي بيفتح البورت) مش بيُستدعى — فكل منطق اللعبة قابل للاختبار
 * على JVM. البثّ على قائمة عملاء فاضية عبارة عن no-op آمن.
 *
 * الترجمة wire ↔ domain بتُختبر **بشكل غير مباشر** عبر [LanRoomsDataSource]
 * (وهي دوال `internal`، فالاختبار من خلالها أضمن من الاعتماد على رؤيتها).
 */
class LanHostServerTest {

    private val server = LanHostServer(
        myIpProvider = { "192.168.43.1" },
        port = LanProtocol.DEFAULT_GAME_PORT,
        timeProvider = { FIXED_TIME }
    )

    // ------------------------------------------------------------------
    // إنشاء الغرفة
    // ------------------------------------------------------------------

    @Test
    fun `createRoom يضيف المضيف كمشارك جاهز على الفريق الأحمر`() {
        val result = server.createRoom(
            hostName = "أحمد",
            roomCode = "STAD99",
            gameType = "thirty_seconds",
            maxRounds = 5,
            maxPlayers = 6,
            region = "MENA",
            hostAvatar = "👑"
        )

        assertTrue(result.isSuccess)

        val room = server.currentRoom()
        assertNotNull(room)
        assertEquals("STAD99", room!!.roomCode)
        assertEquals("أحمد", room.hostName)
        assertEquals(RoomStatus.WAITING, room.status)
        assertEquals(1, room.currentRound)
        assertEquals(1, room.participantCount)
        assertTrue("الغرفة محلية ⇒ isOfflineOnly", room.isOfflineOnly)

        val participants = server.listParticipants()
        assertEquals(1, participants.size)
        assertTrue(participants[0].isHost)
        assertTrue("المضيف جاهز تلقائياً", participants[0].isReady)
        assertEquals(RoomParticipant.TEAM_RED, participants[0].teamColor)
        assertEquals("👑", participants[0].avatarEmoji)
        assertEquals(FIXED_TIME, participants[0].joinedAtMillis)
    }

    @Test
    fun `createRoom يحول الكود لأحرف كبيرة ويقلّم المسافات`() {
        server.createRoom("  أحمد  ", "stad99", "buzzer", 5, 6, "MENA", "")

        assertEquals("STAD99", server.currentRoom()?.roomCode)
        assertEquals("أحمد", server.currentRoom()?.hostName)
        assertEquals("أفاتار افتراضي للأفاتار الفاضي", RoomParticipant.DEFAULT_AVATAR, server.listParticipants()[0].avatarEmoji)
    }

    @Test
    fun `createRoom يرفض اسم المضيف الفاضي أو الطويل`() {
        assertEquals(
            GameRoomRepository.ERROR_HOST_NAME_REQUIRED,
            server.createRoom("   ", "CODE01", "buzzer", 5, 6, "MENA", "").errorOrNull
        )

        val longName = "ا".repeat(RoomParticipant.MAX_NAME_LENGTH + 1)
        assertEquals(
            GameRoomRepository.ERROR_HOST_NAME_TOO_LONG,
            server.createRoom(longName, "CODE01", "buzzer", 5, 6, "MENA", "").errorOrNull
        )
        assertNull("مفيش غرفة اتنشأت", server.currentRoom())
    }

    @Test
    fun `createRoom يرفض الكود الفاضي`() {
        assertEquals(
            GameRoomRepository.ERROR_ROOM_CODE_REQUIRED,
            server.createRoom("أحمد", "  ", "buzzer", 5, 6, "MENA", "").errorOrNull
        )
    }

    @Test
    fun `createRoom يرفض حدود اللاعبين والجولات غير الصالحة`() {
        assertNotNull(
            "لاعب واحد أقل من الحد الأدنى",
            server.createRoom("أ", "CODE1", "buzzer", 5, 1, "MENA", "").errorOrNull
        )
        assertNotNull(
            "لاعبين فوق الحد الأقصى",
            server.createRoom("أ", "CODE2", "buzzer", 5, GameRoom.MAX_MAX_PLAYERS + 1, "MENA", "").errorOrNull
        )
        assertNotNull(
            "صفر جولات",
            server.createRoom("أ", "CODE3", "buzzer", 0, 6, "MENA", "").errorOrNull
        )
        assertNotNull(
            "جولات فوق الحد الأقصى",
            server.createRoom("أ", "CODE4", "buzzer", GameRoom.MAX_ROUNDS_LIMIT + 1, 6, "MENA", "").errorOrNull
        )
        assertNull("مفيش غرفة اتنشأت من أي محاولة فاشلة", server.currentRoom())
    }

    @Test
    fun `createRoom يقبل الحدود الصالحة عند الطرفين`() {
        assertTrue(
            server.createRoom("أ", "MIN001", "buzzer", 1, 2, "MENA", "").isSuccess
        )
        server.resetRoom()

        assertTrue(
            server.createRoom(
                "أ",
                "MAX001",
                "buzzer",
                GameRoom.MAX_ROUNDS_LIMIT,
                GameRoom.MAX_MAX_PLAYERS,
                "MENA",
                ""
            ).isSuccess
        )
        assertEquals(GameRoom.MAX_ROUNDS_LIMIT, server.currentRoom()?.maxRounds)
        assertEquals(GameRoom.MAX_MAX_PLAYERS, server.currentRoom()?.maxPlayers)
    }

    @Test
    fun `createRoom يرفض غرفة تانية والسيرفر مستضيف غرفة شغّالة`() {
        server.createRoom("أحمد", "FIRST1", "buzzer", 5, 6, "MENA", "")

        val second = server.createRoom("سارة", "SECND2", "buzzer", 5, 6, "MENA", "")

        assertFalse(second.isSuccess)
        assertTrue(second.errorOrNull!!.contains("FIRST1"))
        assertEquals("الغرفة الأولى فضلت زي ما هي", "FIRST1", server.currentRoom()?.roomCode)
    }

    @Test
    fun `createRoom يسمح بغرفة جديدة بعد إنهاء السابقة`() {
        server.createRoom("أحمد", "FIRST1", "buzzer", 5, 6, "MENA", "")
        server.finishGame()

        val second = server.createRoom("سارة", "SECND2", "buzzer", 5, 6, "MENA", "")

        assertTrue(second.isSuccess)
        assertEquals("SECND2", server.currentRoom()?.roomCode)
        assertEquals("المشاركين اتصفّوا", 1, server.listParticipants().size)
        assertEquals("سارة", server.listParticipants()[0].playerName)
    }

    // ------------------------------------------------------------------
    // الانضمام
    // ------------------------------------------------------------------

    @Test
    fun `joinRoom يضيف لاعب على الفريق الأزرق لموازنة الفرق`() {
        server.createRoom("أحمد", "STAD99", "buzzer", 5, 6, "MENA", "")

        val result = server.joinRoom("STAD99", "سارة", "⚽", RoomParticipant.TEAM_RED)

        assertTrue(result.isSuccess)
        val participants = server.listParticipants()
        assertEquals(2, participants.size)
        assertEquals(
            "طلب أحمر لكن الموازنة نقلتها لأزرق",
            RoomParticipant.TEAM_BLUE,
            participants[1].teamColor
        )
        assertFalse("ضيف جديد مش جاهز تلقائياً", participants[1].isReady)
        assertFalse("مش مضيف", participants[1].isHost)
        assertEquals(2, server.currentRoom()?.participantCount)
    }

    @Test
    fun `joinRoom يحترم الطلب لو الفرق متوازنة`() {
        server.createRoom("أحمد", "STAD99", "buzzer", 5, 8, "MENA", "")
        server.joinRoom("STAD99", "سارة", "⚽", RoomParticipant.TEAM_BLUE)

        // أحمر=1، أزرق=1 ⇒ الانضمام لأزرق يخليه 2 مقابل 1 (فرق 1) مسموح
        val third = server.joinRoom("STAD99", "عمر", "🎮", RoomParticipant.TEAM_RED)

        assertTrue(third.isSuccess)
        val teams = server.listParticipants().map { it.teamColor }
        assertEquals(2, teams.count { it == RoomParticipant.TEAM_RED })
        assertEquals(1, teams.count { it == RoomParticipant.TEAM_BLUE })
    }

    @Test
    fun `joinRoom مكرر يعيد نفس اللاعب بدون إضافة نسخة`() {
        server.createRoom("أحمد", "STAD99", "buzzer", 5, 6, "MENA", "")
        server.joinRoom("STAD99", "سارة", "⚽", RoomParticipant.TEAM_BLUE)

        val rejoined = server.joinRoom("STAD99", "سارة", "🎮", RoomParticipant.TEAM_RED)

        assertTrue("انضمام مكرر = نجاح (سلوك Supabase نفسه)", rejoined.isSuccess)
        assertEquals(2, server.listParticipants().size)
        assertEquals(
            "الفريق الأصلي ما يتغيرش",
            RoomParticipant.TEAM_BLUE,
            server.listParticipants().first { it.playerName == "سارة" }.teamColor
        )
    }

    @Test
    fun `joinRoom يعتبر الاسم نفسه بحروف مختلفة مكرراً`() {
        server.createRoom("Ahmed", "STAD99", "buzzer", 5, 6, "MENA", "")

        val result = server.joinRoom("STAD99", "ahmed", "⚽", RoomParticipant.TEAM_BLUE)

        assertTrue(result.isSuccess)
        assertEquals(1, server.listParticipants().size)
    }

    @Test
    fun `joinRoom يرفض الغرفة المليانة`() {
        server.createRoom("أحمد", "STAD99", "buzzer", 5, 2, "MENA", "")
        server.joinRoom("STAD99", "سارة", "⚽", RoomParticipant.TEAM_BLUE)

        val third = server.joinRoom("STAD99", "عمر", "🎮", RoomParticipant.TEAM_RED)

        assertEquals(GameRoomRepository.ERROR_ROOM_FULL, third.errorOrNull)
        assertEquals(2, server.listParticipants().size)
    }

    @Test
    fun `joinRoom يرفض كود غير موجود أو غرفة منتهية`() {
        assertEquals(
            GameRoomRepository.ERROR_ROOM_NOT_FOUND,
            server.joinRoom("NOPE01", "سارة", "⚽", "red").errorOrNull
        )

        server.createRoom("أحمد", "STAD99", "buzzer", 5, 6, "MENA", "")
        server.finishGame()

        assertEquals(
            GameRoomRepository.ERROR_ROOM_FINISHED,
            server.joinRoom("STAD99", "سارة", "⚽", "red").errorOrNull
        )
    }

    @Test
    fun `joinRoom يرفض الاسم الفاضي أو الطويل والكود الفاضي`() {
        server.createRoom("أحمد", "STAD99", "buzzer", 5, 6, "MENA", "")

        assertEquals(
            GameRoomRepository.ERROR_ROOM_CODE_REQUIRED,
            server.joinRoom("  ", "سارة", "⚽", "red").errorOrNull
        )
        assertEquals(
            GameRoomRepository.ERROR_PLAYER_NAME_REQUIRED,
            server.joinRoom("STAD99", " ", "⚽", "red").errorOrNull
        )
        assertEquals(
            GameRoomRepository.ERROR_PLAYER_NAME_TOO_LONG,
            server.joinRoom("STAD99", "ا".repeat(RoomParticipant.MAX_NAME_LENGTH + 1), "⚽", "red").errorOrNull
        )
    }

    // ------------------------------------------------------------------
    // المغادرة ونقل الاستضافة
    // ------------------------------------------------------------------

    @Test
    fun `leaveRoom يحذف اللاعب ويحدّث العدد`() {
        server.createRoom("أحمد", "STAD99", "buzzer", 5, 6, "MENA", "")
        server.joinRoom("STAD99", "سارة", "⚽", RoomParticipant.TEAM_BLUE)

        val result = server.leaveRoom("سارة")

        assertTrue(result.isSuccess)
        assertEquals(1, server.listParticipants().size)
        assertEquals(1, server.currentRoom()?.participantCount)
    }

    @Test
    fun `leaveRoom للمضيف ينقل الاستضافة لأول مشارك باقي`() {
        server.createRoom("أحمد", "STAD99", "buzzer", 5, 6, "MENA", "")
        server.joinRoom("STAD99", "سارة", "⚽", RoomParticipant.TEAM_BLUE)
        server.joinRoom("STAD99", "عمر", "🎮", RoomParticipant.TEAM_RED)

        server.leaveRoom("أحمد")

        val remaining = server.listParticipants()
        assertEquals(2, remaining.size)
        assertTrue("سارة بقت المضيفة", remaining.first { it.playerName == "سارة" }.isHost)
        assertEquals("سارة", server.currentRoom()?.hostName)
        assertEquals(1, remaining.count { it.isHost })
    }

    @Test
    fun `leaveRoom للاعب مش موجود بيعيد نجاح بدون تغيير`() {
        server.createRoom("أحمد", "STAD99", "buzzer", 5, 6, "MENA", "")

        val result = server.leaveRoom("حد تاني")

        assertTrue(result.isSuccess)
        assertEquals(1, server.listParticipants().size)
    }

    @Test
    fun `leaveRoom يرفض الاسم الفاضي`() {
        assertEquals(
            GameRoomRepository.ERROR_PLAYER_NAME_REQUIRED,
            server.leaveRoom("  ").errorOrNull
        )
    }

    // ------------------------------------------------------------------
    // الجاهزية والنقاط
    // ------------------------------------------------------------------

    @Test
    fun `setReady يبدّل جاهزية اللاعب`() {
        server.createRoom("أحمد", "STAD99", "buzzer", 5, 6, "MENA", "")
        server.joinRoom("STAD99", "سارة", "⚽", RoomParticipant.TEAM_BLUE)

        assertTrue(server.setReady("سارة", true).isSuccess)
        assertTrue(server.listParticipants().first { it.playerName == "سارة" }.isReady)

        assertTrue(server.setReady("سارة", false).isSuccess)
        assertFalse(server.listParticipants().first { it.playerName == "سارة" }.isReady)
    }

    @Test
    fun `updateScore يحدّث النقاط ويرفض السالب`() {
        server.createRoom("أحمد", "STAD99", "buzzer", 5, 6, "MENA", "")

        assertTrue(server.updateScore("أحمد", 120).isSuccess)
        assertEquals(120, server.listParticipants()[0].score)

        assertTrue(server.updateScore("أحمد", -50).isSuccess)
        assertEquals("النقاط السالبة بتتصفّر", 0, server.listParticipants()[0].score)
    }

    @Test
    fun `setReady و updateScore يرفضا لاعب مش موجود`() {
        server.createRoom("أحمد", "STAD99", "buzzer", 5, 6, "MENA", "")

        assertNotNull(server.setReady("حد تاني", true).errorOrNull)
        assertNotNull(server.updateScore("حد تاني", 10).errorOrNull)
    }

    @Test
    fun `applyParticipantUpdate يحدّث الحقول بدون نقل الاستضافة`() {
        server.createRoom("أحمد", "STAD99", "buzzer", 5, 6, "MENA", "")
        server.joinRoom("STAD99", "سارة", "⚽", RoomParticipant.TEAM_BLUE)

        val sara = server.listParticipants().first { it.playerName == "سارة" }
        val result = server.applyParticipantUpdate(
            sara.copy(teamColor = "blue", score = 75, isReady = true, isHost = true, avatarEmoji = "🏆")
        )

        assertTrue(result.isSuccess)
        val updated = server.listParticipants().first { it.playerName == "سارة" }
        assertEquals(75, updated.score)
        assertTrue(updated.isReady)
        assertEquals("🏆", updated.avatarEmoji)
        assertFalse("الاستضافة ما تنتقلش بتحديث عادي", updated.isHost)
    }

    @Test
    fun `applyParticipantUpdate يعامل أي لون غير blue كأحمر ويحتفظ بالأفاتار لو فاضي`() {
        server.createRoom("أحمد", "STAD99", "buzzer", 5, 6, "MENA", "")

        val ahmed = server.listParticipants()[0]
        server.applyParticipantUpdate(ahmed.copy(teamColor = "قيمة غريبة", avatarEmoji = "  ", score = -5))

        val updated = server.listParticipants()[0]
        assertEquals(RoomParticipant.TEAM_RED, updated.teamColor)
        assertEquals("أفاتار فاضي ⇒ يحتفظ بالقديم", "👑", updated.avatarEmoji)
        assertEquals(0, updated.score)
    }

    @Test
    fun `applyParticipantUpdate يرفض اسم فاضي أو لاعب مش موجود`() {
        server.createRoom("أحمد", "STAD99", "buzzer", 5, 6, "MENA", "")

        assertEquals(
            GameRoomRepository.ERROR_PLAYER_NAME_REQUIRED,
            server.applyParticipantUpdate(server.listParticipants()[0].copy(playerName = " ")).errorOrNull
        )
        assertNotNull(
            server.applyParticipantUpdate(
                RoomParticipant(roomCode = "STAD99", playerName = "حد تاني", score = 5)
            ).errorOrNull
        )
    }

    // ------------------------------------------------------------------
    // حالة الغرفة والجولات
    // ------------------------------------------------------------------

    @Test
    fun `startGame يحوّل الحالة لجارية ويعيد الجولة للأولى`() {
        server.createRoom("أحمد", "STAD99", "buzzer", 5, 6, "MENA", "")
        server.setReady("أحمد", true)
        server.nextRound()

        val result = server.startGame()

        assertTrue(result.isSuccess)
        assertEquals(RoomStatus.PLAYING, server.currentRoom()?.status)
        assertEquals(1, server.currentRoom()?.currentRound)
    }

    @Test
    fun `nextRound بيزوّد الجولة حتى الحد الأقصى`() {
        server.createRoom("أحمد", "STAD99", "buzzer", 3, 6, "MENA", "")
        server.startGame()

        assertTrue(server.nextRound().isSuccess)
        assertEquals(2, server.currentRoom()?.currentRound)

        assertTrue(server.nextRound().isSuccess)
        assertEquals(3, server.currentRoom()?.currentRound)
    }

    @Test
    fun `nextRound يرفض بعد آخر جولة`() {
        server.createRoom("أحمد", "STAD99", "buzzer", 1, 6, "MENA", "")
        server.startGame()

        val result = server.nextRound()

        assertFalse(result.isSuccess)
        assertTrue(result.errorOrNull!!.contains("آخر جولة"))
        assertEquals(1, server.currentRoom()?.currentRound)
    }

    @Test
    fun `nextRound يرفض في غرفة منتهية`() {
        server.createRoom("أحمد", "STAD99", "buzzer", 5, 6, "MENA", "")
        server.finishGame()

        assertEquals(GameRoomRepository.ERROR_ROOM_FINISHED, server.nextRound().errorOrNull)
    }

    @Test
    fun `finishGame يحوّل الحالة لمنتهية`() {
        server.createRoom("أحمد", "STAD99", "buzzer", 5, 6, "MENA", "")

        assertTrue(server.finishGame().isSuccess)
        assertEquals(RoomStatus.FINISHED, server.currentRoom()?.status)
    }

    @Test
    fun `أوامر الحالة بدون غرفة تعيد خطأ وليس استثناء`() {
        assertEquals(GameRoomRepository.ERROR_ROOM_NOT_FOUND, server.startGame().errorOrNull)
        assertEquals(GameRoomRepository.ERROR_ROOM_NOT_FOUND, server.nextRound().errorOrNull)
        assertEquals(GameRoomRepository.ERROR_ROOM_NOT_FOUND, server.finishGame().errorOrNull)
        assertEquals(
            GameRoomRepository.ERROR_ROOM_NOT_FOUND,
            server.setRoomState(RoomStatus.PLAYING, 2).errorOrNull
        )
    }

    @Test
    fun `setRoomState يضبط الحالة ويحصر الجولة داخل الحدود`() {
        server.createRoom("أحمد", "STAD99", "buzzer", 5, 6, "MENA", "")

        assertTrue(server.setRoomState(RoomStatus.PLAYING, 3).isSuccess)
        assertEquals(RoomStatus.PLAYING, server.currentRoom()?.status)
        assertEquals(3, server.currentRoom()?.currentRound)

        server.setRoomState(RoomStatus.PLAYING, 99)
        assertEquals("محصورة في maxRounds", 5, server.currentRoom()?.currentRound)

        server.setRoomState(RoomStatus.PLAYING, 0)
        assertEquals("محصورة في 1", 1, server.currentRoom()?.currentRound)
    }

    @Test
    fun `resetRoom يمسح الغرفة والمشاركين`() {
        server.createRoom("أحمد", "STAD99", "buzzer", 5, 6, "MENA", "")
        server.joinRoom("STAD99", "سارة", "⚽", RoomParticipant.TEAM_BLUE)

        server.resetRoom()

        assertNull(server.currentRoom())
        assertTrue(server.listParticipants().isEmpty())
        assertEquals(0, server.participantCount())
    }

    // ------------------------------------------------------------------
    // لقطة الحالة (الترجمة للـwire)
    // ------------------------------------------------------------------

    @Test
    fun `stateMessage يعيد لقطة كاملة بحالة wire صحيحة`() {
        server.createRoom("أحمد", "STAD99", "thirty_seconds", 5, 6, "MENA", "👑")
        server.joinRoom("STAD99", "سارة", "⚽", RoomParticipant.TEAM_BLUE)
        server.startGame()

        val state = server.stateMessage()

        assertNotNull(state)
        assertEquals(LanMessageType.STATE, state!!.type)
        assertEquals("STAD99", state.roomCode)
        assertEquals("أحمد", state.hostName)
        assertEquals("thirty_seconds", state.gameType)
        assertEquals("playing", state.status)
        assertEquals(5, state.maxRounds)
        assertEquals(6, state.maxPlayers)
        assertEquals(2, state.playerCount)
        assertEquals("192.168.43.1", state.hostIp)
        assertEquals(2, state.participants?.size)
        assertEquals("سارة", state.participants?.get(1)?.playerName)
        assertEquals(RoomParticipant.TEAM_BLUE, state.participants?.get(1)?.teamColor)
        assertTrue(state.isCompatibleVersion)
    }

    @Test
    fun `stateMessage يعيد null لو مفيش غرفة`() {
        assertNull(server.stateMessage())
    }

    @Test
    fun `لقطة الحالة بترجع لنموذج domain سليم بعد دورة الترميز الكاملة`() {
        server.createRoom("أحمد", "STAD99", "buzzer", 5, 6, "MENA", "👑")
        server.joinRoom("STAD99", "سارة", "⚽", RoomParticipant.TEAM_BLUE)

        val state = server.stateMessage()!!
        val decoded = LanProtocol.decode(LanProtocol.encode(state))

        assertNotNull(decoded)
        assertEquals(state.roomCode, decoded!!.roomCode)
        assertEquals(state.participants?.size, decoded.participants?.size)
        assertEquals(
            "النقاط والجاهزية بتعبر الترميز سليمة",
            state.participants?.map { it.score to it.isReady },
            decoded.participants?.map { it.score to it.isReady }
        )
    }

    // ------------------------------------------------------------------
    // مصدر البيانات فوق المضيف (بدون sockets)
    // ------------------------------------------------------------------

    private fun dataSource() = LanRoomsDataSource(
        hostServerProvider = { server },
        clientProvider = { null },
        // المضيف بيخدم حالته من الذاكرة؛ الحقن ده بيخلي الاختبار يشتغل
        // من غير ما نفتح socket فعلي على بورت حقيقي
        isHostActive = { true }
    )

    @Test
    fun `مصدر البيانات في وضع المضيف يعيد الغرفة والمشاركين`() = runTest {
        server.createRoom("أحمد", "STAD99", "buzzer", 5, 6, "MENA", "👑")
        server.joinRoom("STAD99", "سارة", "⚽", RoomParticipant.TEAM_BLUE)

        val source = dataSource()
        assertTrue("وضع مضيف نشط", source.isHosting)
        assertTrue(source.isAvailable)

        val rooms = source.fetchRooms(10)
        assertNotNull(rooms)
        assertEquals(1, rooms!!.size)
        assertEquals("STAD99", rooms[0].roomCode)
        assertEquals(2, rooms[0].participantCount)

        val participants = source.fetchParticipants("STAD99")
        assertNotNull(participants)
        assertEquals(2, participants!!.size)

        assertEquals("STAD99", source.fetchRoomByCode("stad99")?.roomCode)
        assertNull("كود غلط ⇒ null", source.fetchRoomByCode("NOPE01"))
    }

    @Test
    fun `مصدر البيانات يعيد قائمة فاضية مش null لو مفيش غرفة`() = runTest {
        val source = dataSource()

        val rooms = source.fetchRooms(10)

        assertNotNull("مفيش غرفة ≠ فشل النقل", rooms)
        assertTrue(rooms!!.isEmpty())
    }

    @Test
    fun `مصدر البيانات بدون مضيف ولا عميل يعيد null و false حسب العقد`() = runTest {
        val source = LanRoomsDataSource(hostServerProvider = { null }, clientProvider = { null })

        assertFalse("النقل غير متاح", source.isAvailable)
        assertNull(source.fetchRooms(10))
        assertNull(source.fetchRoomByCode("STAD99"))
        assertNull(source.fetchParticipants("STAD99"))
        assertFalse(source.addParticipant(RoomParticipant(roomCode = "STAD99", playerName = "سارة")))
        assertFalse(
            source.updateParticipant(RoomParticipant(roomCode = "STAD99", playerName = "سارة", score = 5))
        )
        assertFalse(source.updateRoomState("STAD99", RoomStatus.PLAYING, 2))
        assertEquals(GameRoomRepositoryErrors.NOT_CONNECTED, source.lastTransportError)
    }

    @Test
    fun `مصدر البيانات ينشئ غرفة عبر المضيف ويرجّعها`() = runTest {
        val source = dataSource()

        val created = source.createRoom(
            GameRoom(
                roomCode = "GRID33",
                hostName = "سارة",
                gameType = "tictactoe",
                maxRounds = 4,
                maxPlayers = 4,
                region = "MENA"
            )
        )

        assertNotNull(created)
        assertEquals("GRID33", created!!.roomCode)
        assertEquals("tictactoe", created.gameType)
        assertEquals(1, created.participantCount)
        assertNull(source.lastTransportError)
    }

    @Test
    fun `مصدر البيانات يضيف ويحدّث مشارك عبر المضيف`() = runTest {
        val source = dataSource()
        source.createRoom(
            GameRoom(roomCode = "STAD99", hostName = "أحمد", gameType = "buzzer", maxRounds = 5, maxPlayers = 6)
        )

        assertTrue(
            source.addParticipant(
                RoomParticipant(roomCode = "STAD99", playerName = "سارة", teamColor = RoomParticipant.TEAM_BLUE)
            )
        )
        assertEquals(2, server.listParticipants().size)

        val sara = server.listParticipants().first { it.playerName == "سارة" }
        assertTrue(source.updateParticipant(sara.copy(score = 90, isReady = true)))

        val updated = server.listParticipants().first { it.playerName == "سارة" }
        assertEquals(90, updated.score)
        assertTrue(updated.isReady)
    }

    @Test
    fun `مصدر البيانات يحدّث حالة الغرفة عبر المضيف`() = runTest {
        val source = dataSource()
        source.createRoom(
            GameRoom(roomCode = "STAD99", hostName = "أحمد", gameType = "buzzer", maxRounds = 5, maxPlayers = 6)
        )

        assertTrue(source.updateRoomState("STAD99", RoomStatus.PLAYING, 3))
        assertEquals(RoomStatus.PLAYING, server.currentRoom()?.status)
        assertEquals(3, server.currentRoom()?.currentRound)

        assertTrue(source.updateRoomState("STAD99", RoomStatus.FINISHED, 3))
        assertEquals(RoomStatus.FINISHED, server.currentRoom()?.status)
    }

    @Test
    fun `مصدر البيانات يرفض إنشاء غرفة من الضيف`() = runTest {
        // ضيف متصل لكن مش مضيف
        val source = LanRoomsDataSource(
            hostServerProvider = { null },
            clientProvider = { LanClient() }
        )

        val created = source.createRoom(
            GameRoom(roomCode = "STAD99", hostName = "سارة", maxRounds = 5, maxPlayers = 6)
        )

        assertNull(created)
        assertNotNull(source.lastTransportError)
        assertTrue(source.lastTransportError!!.contains("المضيف"))
    }

    private companion object {
        const val FIXED_TIME = 1_700_000_000_000L
    }
}
