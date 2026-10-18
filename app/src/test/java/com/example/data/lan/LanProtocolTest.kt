package com.example.data.lan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * اختبارات بروتوكول الشبكة المحلية وأدوات الشبكة.
 *
 * كل ده منطق نقي (مفيش sockets حقيقية ولا Android) ⇒ بيشتغل على JVM مباشرةً.
 * التركيز على حاجتين بيكسروا اللعب فعلاً:
 *  - تحقق العنوان/البورت (رسالة عربية واضحة بدل استثناء غامض).
 *  - الترميز/فك الترميز (رسالة تالفة من جهاز تاني لازم ما تقعّش السيرفر).
 */
class LanProtocolTest {

    // ------------------------------------------------------------------
    // تحقق عنوان الـIP (الطريقة اليدوية للاكتشاف)
    // ------------------------------------------------------------------

    @Test
    fun `validateHostAddress يقبل عنوان IPv4 صالح`() {
        assertNull(LanProtocol.validateHostAddress("192.168.43.1"))
        assertNull(LanProtocol.validateHostAddress("10.0.0.5"))
        assertNull(LanProtocol.validateHostAddress("172.20.10.1"))
        assertNull(LanProtocol.validateHostAddress("255.255.255.255"))
        assertNull(LanProtocol.validateHostAddress("0.0.0.0"))
    }

    @Test
    fun `validateHostAddress يتجاهل المسافات الزائدة حول العنوان`() {
        assertNull(LanProtocol.validateHostAddress("  192.168.43.1  "))
    }

    @Test
    fun `validateHostAddress يرفض العنوان الفاضي`() {
        assertNotNull(LanProtocol.validateHostAddress(""))
        assertNotNull(LanProtocol.validateHostAddress("   "))
        assertNotNull(LanProtocol.validateHostAddress(null))
    }

    @Test
    fun `validateHostAddress يرفض العنوان الناقص أو الزيادة`() {
        assertNotNull(LanProtocol.validateHostAddress("192.168.43"))
        assertNotNull(LanProtocol.validateHostAddress("192.168.43.1.7"))
        assertNotNull(LanProtocol.validateHostAddress("192"))
    }

    @Test
    fun `validateHostAddress يرفض الأجزاء خارج نطاق 0-255`() {
        assertNotNull(LanProtocol.validateHostAddress("192.168.43.256"))
        assertNotNull(LanProtocol.validateHostAddress("999.1.1.1"))
        assertNotNull(LanProtocol.validateHostAddress("192.168.43.-1"))
    }

    @Test
    fun `validateHostAddress يرفض الأجزاء غير الرقمية والفارغة`() {
        assertNotNull(LanProtocol.validateHostAddress("192.168.43.a"))
        assertNotNull(LanProtocol.validateHostAddress("192.168..1"))
        assertNotNull(LanProtocol.validateHostAddress("abcd.efgh.ijkl.mnop"))
    }

    @Test
    fun `validateHostAddress يرفض العنوان اللي فيه مسافة داخلية`() {
        assertNotNull(LanProtocol.validateHostAddress("192.168. 43.1"))
        assertNotNull(LanProtocol.validateHostAddress("192 168 43 1"))
    }

    @Test
    fun `validateHostAddress يوجّه المستخدم بعيداً عن localhost`() {
        val error = LanProtocol.validateHostAddress("localhost")
        assertNotNull(error)
        assertTrue("لازم يقترح البديل", error!!.contains("127.0.0.1"))
    }

    @Test
    fun `رسائل الأخطاء كلها بالعربي`() {
        listOf(
            LanProtocol.validateHostAddress(""),
            LanProtocol.validateHostAddress("1.2.3"),
            LanProtocol.validateHostAddress("1.2.3.999"),
            LanProtocol.validateHostAddress("localhost")
        ).forEach { error ->
            assertNotNull(error)
            assertFalse("لازم يحتوي حرف عربي", error!!.none { it.code > 0x0600 && it.code < 0x06FF })
        }
    }

    // ------------------------------------------------------------------
    // تحقق البورت
    // ------------------------------------------------------------------

    @Test
    fun `validatePort يقبل النطاق الصالح`() {
        assertNull(LanProtocol.validatePort(1024))
        assertNull(LanProtocol.validatePort(LanProtocol.DEFAULT_GAME_PORT))
        assertNull(LanProtocol.validatePort(65535))
    }

    @Test
    fun `validatePort يرفض خارج النطاق`() {
        assertNotNull(LanProtocol.validatePort(0))
        assertNotNull(LanProtocol.validatePort(-1))
        assertNotNull(LanProtocol.validatePort(80))
        assertNotNull(LanProtocol.validatePort(65536))
    }

    // ------------------------------------------------------------------
    // الترميز وفك الترميز
    // ------------------------------------------------------------------

    @Test
    fun `encode يعيد سطراً واحداً بدون فاصل الأسطر`() {
        val encoded = LanProtocol.encode(LanProtocol.discover())

        assertFalse("الفاصل جوه الرسالة هيكسر البروتوكول", encoded.contains("\n"))
        assertTrue(encoded.contains("discover"))
        assertTrue("لازم يتبعت إصدار البروتوكول", encoded.contains("protocol_version"))
    }

    @Test
    fun `decode يعيد نفس الرسالة بعد دورة كاملة`() {
        val original = LanProtocol.hello(
            hostIp = "192.168.43.1",
            port = 47778,
            hostName = "موبايل أحمد",
            roomCode = "STAD99",
            gameType = "thirty_seconds",
            playerCount = 4
        )

        val decoded = LanProtocol.decode(LanProtocol.encode(original))

        assertNotNull(decoded)
        assertEquals(LanMessageType.HELLO, decoded!!.type)
        assertEquals("192.168.43.1", decoded.hostIp)
        assertEquals(47778, decoded.port)
        assertEquals("موبايل أحمد", decoded.hostName)
        assertEquals("STAD99", decoded.roomCode)
        assertEquals("thirty_seconds", decoded.gameType)
        assertEquals(4, decoded.playerCount)
    }

    @Test
    fun `decode يحافظ على قائمة المشاركين والأسماء العربية`() {
        val original = LanMessage(
            type = LanMessageType.STATE,
            roomCode = "GRID33",
            hostName = "المضيف 👑",
            status = "playing",
            currentRound = 3,
            maxRounds = 5,
            participants = listOf(
                LanParticipant(playerName = "أحمد", teamColor = "red", score = 50, isReady = true, isHost = true),
                LanParticipant(playerName = "سارة ⚽", teamColor = "blue", score = 30)
            )
        )

        val decoded = LanProtocol.decode(LanProtocol.encode(original))

        assertNotNull(decoded)
        assertEquals(2, decoded!!.participants?.size)
        assertEquals("أحمد", decoded.participants?.get(0)?.playerName)
        assertEquals("سارة ⚽", decoded.participants?.get(1)?.playerName)
        assertEquals(50, decoded.participants?.get(0)?.score)
        assertEquals("blue", decoded.participants?.get(1)?.teamColor)
        assertEquals(3, decoded.currentRound)
        assertEquals("playing", decoded.status)
    }

    @Test
    fun `decode يعيد null للرسائل التالفة بدل رمي استثناء`() {
        assertNull(LanProtocol.decode(null))
        assertNull(LanProtocol.decode(""))
        assertNull(LanProtocol.decode("   "))
        assertNull(LanProtocol.decode("مش JSON خالص"))
        assertNull(LanProtocol.decode("{"))
        assertNull(LanProtocol.decode("{\"type\": 12345}"))
    }

    @Test
    fun `decode يتجاهل رسالة أطول من الحد الأقصى`() {
        val huge = "x".repeat(LanProtocol.MAX_MESSAGE_LENGTH + 1)
        assertNull(LanProtocol.decode(huge))
    }

    @Test
    fun `isCompatibleVersion يرفض إصدار مختلف`() {
        assertTrue(LanMessage(type = LanMessageType.QUERY).isCompatibleVersion)
        assertFalse(
            LanMessage(type = LanMessageType.QUERY, protocolVersion = LanProtocol.VERSION + 1).isCompatibleVersion
        )
        assertFalse(
            LanMessage(type = LanMessageType.QUERY, protocolVersion = 0).isCompatibleVersion
        )
    }

    @Test
    fun `hello بيحمل إصدار البروتوكول الافتراضي`() {
        val message = LanProtocol.hello("192.168.1.5", 47778, "مضيف", "CODE01", "buzzer", 2)
        assertEquals(LanProtocol.VERSION, message.protocolVersion)
        assertEquals(LanMessageType.HELLO, message.type)
    }

    // ------------------------------------------------------------------
    // أدوات الشبكة
    // ------------------------------------------------------------------

    @Test
    fun `subnetBroadcastAddress يستنتج عنوان بثّ الشبكة الفرعية`() {
        assertEquals("192.168.43.255", LanNetworkUtils.subnetBroadcastAddress("192.168.43.1"))
        assertEquals("192.168.43.255", LanNetworkUtils.subnetBroadcastAddress("192.168.43.87"))
        assertEquals("10.0.0.255", LanNetworkUtils.subnetBroadcastAddress("10.0.0.15"))
    }

    @Test
    fun `subnetBroadcastAddress يعيد null لعنوان غير صالح`() {
        assertNull(LanNetworkUtils.subnetBroadcastAddress("مش عنوان"))
        assertNull(LanNetworkUtils.subnetBroadcastAddress("192.168.1"))
        assertNull(LanNetworkUtils.subnetBroadcastAddress("192.168.1.999"))
    }

    @Test
    fun `isSameSubnet يقارن أول 3 octets`() {
        assertTrue(LanNetworkUtils.isSameSubnet("192.168.43.10", "192.168.43.1"))
        assertFalse(LanNetworkUtils.isSameSubnet("192.168.43.10", "192.168.1.1"))
        assertFalse(LanNetworkUtils.isSameSubnet("192.168.43.10", "10.0.0.1"))
    }

    @Test
    fun `isSameSubnet يعيد false للقيم الفارغة أو التالفة`() {
        assertFalse(LanNetworkUtils.isSameSubnet(null, "192.168.43.1"))
        assertFalse(LanNetworkUtils.isSameSubnet("192.168.43.1", null))
        assertFalse(LanNetworkUtils.isSameSubnet("", ""))
        assertFalse(LanNetworkUtils.isSameSubnet("192.168", "192.168"))
    }

    // ------------------------------------------------------------------
    // السيرفر المكتشف
    // ------------------------------------------------------------------

    @Test
    fun `arabicLabel يعرض وصف كامل للسيرفر المكتشف`() {
        val host = DiscoveredHost(
            hostIp = "192.168.43.1",
            port = 47778,
            hostName = "موبايل أحمد",
            roomCode = "STAD99",
            gameType = "thirty_seconds",
            playerCount = 3,
            discoveredAt = 0L
        )

        val label = host.arabicLabel

        assertTrue(label.contains("موبايل أحمد"))
        assertTrue(label.contains("192.168.43.1"))
        assertTrue(label.contains("STAD99"))
        assertTrue(label.contains("3"))
    }

    @Test
    fun `arabicLabel يتعامل مع اسم فاضي وكود غايب`() {
        val host = DiscoveredHost(
            hostIp = "10.0.0.1",
            port = 47778,
            hostName = "",
            roomCode = null,
            gameType = null,
            playerCount = 0,
            discoveredAt = 0L
        )

        val label = host.arabicLabel

        assertTrue("لازم يعرض بديل للاسم الفاضي", label.contains("سيرفر مجهول"))
        assertFalse("مفيش كود ⇒ ما يذكرش غرفة", label.contains("غرفة"))
    }

    @Test
    fun `ثوابت البروتوكول متسقة`() {
        assertEquals(47777, LanProtocol.DISCOVERY_PORT)
        assertEquals(47778, LanProtocol.DEFAULT_GAME_PORT)
        assertEquals("255.255.255.255", LanProtocol.BROADCAST_ADDRESS)
        assertEquals("\n", LanProtocol.DELIMITER)
        assertTrue(
            "بورت اللعب لازم يكون صالح حسب تحقق البروتوكول نفسه",
            LanProtocol.validatePort(LanProtocol.DEFAULT_GAME_PORT) == null
        )
        assertTrue(
            "بورت الاكتشاف لازم يكون صالح",
            LanProtocol.validatePort(LanProtocol.DISCOVERY_PORT) == null
        )
    }
}
