package com.example.data.room

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * اختبارات منطق احتساب نتائج الفرق [RoomTeamScores].
 *
 * المنطق ده هو اللي بيحدد الفائز في شاشة المباراة المباشرة، ولازم يكون صحيحاً
 * في وضع الأونلاين ووضع الهوتسبوت المحلي على حد سواء — لذلك معزول كدالة نقية.
 */
class RoomTeamScoresTest {

    @Test
    fun `from يجمع نقاط كل فريق بشكل منفصل`() {
        val scores = RoomTeamScores.from(
            listOf(
                participant("أحمد", team = RoomParticipant.TEAM_RED, score = 50),
                participant("سارة", team = RoomParticipant.TEAM_RED, score = 30),
                participant("عمر", team = RoomParticipant.TEAM_BLUE, score = 70),
                participant("ياسين", team = RoomParticipant.TEAM_BLUE, score = 10)
            )
        )

        assertEquals(80, scores.redScore)
        assertEquals(80, scores.blueScore)
        assertEquals(2, scores.redCount)
        assertEquals(2, scores.blueCount)
        assertEquals(4, scores.totalCount)
        assertTrue("نفس النقاط ⇒ تعادل", scores.isTie)
    }

    @Test
    fun `from يحسب أي لون غير BLUE كأحمر`() {
        val scores = RoomTeamScores.from(
            listOf(
                participant("واحد", team = "red", score = 10),
                participant("اتنين", team = "RED", score = 5),
                participant("تلاتة", team = "قيمة غريبة", score = 7),
                participant("أربعة", team = "blue", score = 100)
            )
        )

        assertEquals("10 + 5 + 7", 22, scores.redScore)
        assertEquals(3, scores.redCount)
        assertEquals(100, scores.blueScore)
        assertEquals(1, scores.blueCount)
    }

    @Test
    fun `from يعامل النقاط السالبة على أنها صفر`() {
        val scores = RoomTeamScores.from(
            listOf(
                participant("لاعب", team = RoomParticipant.TEAM_RED, score = -50),
                participant("آخر", team = RoomParticipant.TEAM_BLUE, score = 20)
            )
        )

        assertEquals(0, scores.redScore)
        assertEquals(20, scores.blueScore)
    }

    @Test
    fun `allReady يتطلب وجود مشاركين وجاهزية الجميع`() {
        assertTrue(
            RoomTeamScores.from(
                listOf(
                    participant("أ", isReady = true),
                    participant("ب", isReady = true)
                )
            ).allReady
        )

        assertFalse(
            "واحد بس جاهز",
            RoomTeamScores.from(
                listOf(
                    participant("أ", isReady = true),
                    participant("ب", isReady = false)
                )
            ).allReady
        )

        assertFalse("قائمة فاضية ⇒ مش جاهزين", RoomTeamScores.EMPTY.allReady)
    }

    @Test
    fun `readyCount و totalCount صحيحين`() {
        val scores = RoomTeamScores.from(
            listOf(
                participant("أ", isReady = true),
                participant("ب", isReady = true),
                participant("ج", isReady = false)
            )
        )

        assertEquals(2, scores.readyCount)
        assertEquals(3, scores.totalCount)
        assertFalse(scores.allReady)
    }

    @Test
    fun `leaderTeamColor يحدد الفريق المتقدم أو null عند التعادل`() {
        assertEquals(
            RoomParticipant.TEAM_RED,
            RoomTeamScores(redScore = 40, blueScore = 10).leaderTeamColor
        )
        assertEquals(
            RoomParticipant.TEAM_BLUE,
            RoomTeamScores(redScore = 10, blueScore = 40).leaderTeamColor
        )
        assertNull(RoomTeamScores(redScore = 25, blueScore = 25).leaderTeamColor)
        assertNull("صفر-صفر ⇒ تعادل", RoomTeamScores.EMPTY.leaderTeamColor)
    }

    @Test
    fun `arabicLeaderLabel يعرض وصف عربي مناسب`() {
        assertTrue(
            RoomTeamScores(redScore = 5, blueScore = 1).arabicLeaderLabel.contains("الأحمر")
        )
        assertTrue(
            RoomTeamScores(redScore = 1, blueScore = 5).arabicLeaderLabel.contains("الأزرق")
        )
        assertTrue(
            RoomTeamScores(redScore = 5, blueScore = 5).arabicLeaderLabel.contains("تعادل")
        )
    }

    @Test
    fun `scoreGap هو الفرق المطلق بين الفريقين`() {
        assertEquals(30, RoomTeamScores(redScore = 50, blueScore = 20).scoreGap)
        assertEquals(30, RoomTeamScores(redScore = 20, blueScore = 50).scoreGap)
        assertEquals(0, RoomTeamScores(redScore = 7, blueScore = 7).scoreGap)
    }

    @Test
    fun `redProgressRatio تعيد نصفاً عند التعادل أو الفراغ`() {
        assertEquals(0.5f, RoomTeamScores.EMPTY.redProgressRatio, 0.0001f)
        assertEquals(0.5f, RoomTeamScores(redScore = 30, blueScore = 30).redProgressRatio, 0.0001f)
        assertEquals(0.5f, RoomTeamScores(redScore = 0, blueScore = 0).redProgressRatio, 0.0001f)
    }

    @Test
    fun `redProgressRatio تحسب النسبة الصحيحة`() {
        assertEquals(0.75f, RoomTeamScores(redScore = 75, blueScore = 25).redProgressRatio, 0.0001f)
        assertEquals(0.25f, RoomTeamScores(redScore = 25, blueScore = 75).redProgressRatio, 0.0001f)
        assertEquals(1.0f, RoomTeamScores(redScore = 40, blueScore = 0).redProgressRatio, 0.0001f)
        assertEquals(0.0f, RoomTeamScores(redScore = 0, blueScore = 40).redProgressRatio, 0.0001f)
    }

    @Test
    fun `isEmpty تكون صحيحة فقط للقائمة الفارغة`() {
        assertTrue(RoomTeamScores.EMPTY.isEmpty)
        assertFalse(RoomTeamScores.from(listOf(participant("أ"))).isEmpty)
    }

    @Test
    fun `from بقائمة فاضية يعيد الحصيلة الفارغة`() {
        assertEquals(RoomTeamScores.EMPTY, RoomTeamScores.from(emptyList()))
    }

    private fun participant(
        name: String,
        team: String = RoomParticipant.TEAM_RED,
        score: Int = 0,
        isReady: Boolean = false
    ) = RoomParticipant(
        roomCode = "TEST01",
        playerName = name,
        teamColor = team,
        score = score,
        isReady = isReady
    )
}
