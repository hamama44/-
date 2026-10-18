package com.example.data.supabase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * اختبارات منطق عميل الاشتراك اللحظي (بدون أي socket).
 *
 * الدوال العامة هنا كلها نقية: بناء الرابط، تسمية الموضوع، استخراج كود
 * الغرفة، وحساب التراجع أُسّي لإعادة الاتصال. دي أخطر مواضع الخطأ في
 * Realtime كلها — غلط صغير فيها يعني اشتراك على صف غلط أو اتصال بيقطع.
 */
class SupabaseRealtimeClientTest {

    private val key = "eyJhbGciOiJIUzI1NiIs-test-anon-key"

    // ------------------------------------------------------------------
    // بناء رابط الـWebSocket
    // ------------------------------------------------------------------

    @Test
    fun `realtimeWebSocketUrl يحول https إلى wss ويضيف المفتاح والإصدار`() {
        val url = SupabaseRealtimeClient.realtimeWebSocketUrl("https://proj.supabase.co", key)

        assertEquals(
            "wss://proj.supabase.co/realtime/v1/websocket?apikey=$key&vsn=1.0.0",
            url
        )
    }

    @Test
    fun `realtimeWebSocketUrl يحول http إلى ws للمضائف المحلية`() {
        val url = SupabaseRealtimeClient.realtimeWebSocketUrl("http://localhost:54321", key)

        assertTrue(url!!.startsWith("ws://localhost:54321"))
        assertFalse("ماينفعش ي فضل https", url.startsWith("wss://"))
    }

    @Test
    fun `realtimeWebSocketUrl يشيل مسار rest من الرابط`() {
        val withPath = SupabaseRealtimeClient.realtimeWebSocketUrl(
            "https://proj.supabase.co/rest/v1",
            key
        )
        val bare = SupabaseRealtimeClient.realtimeWebSocketUrl("https://proj.supabase.co", key)

        assertEquals(bare, withPath)
        assertFalse("مفيش rest/v1 في رابط websocket", withPath!!.contains("/rest/v1"))
    }

    @Test
    fun `realtimeWebSocketUrl يتحمل الشرطة المائلة الطرفية والمسافات`() {
        val expected = "wss://proj.supabase.co/realtime/v1/websocket?apikey=$key&vsn=1.0.0"

        assertEquals(expected, SupabaseRealtimeClient.realtimeWebSocketUrl("https://proj.supabase.co/", key))
        assertEquals(expected, SupabaseRealtimeClient.realtimeWebSocketUrl("  https://proj.supabase.co///  ", key))
    }

    @Test
    fun `realtimeWebSocketUrl يعيد null للمدخلات غير الصالحة`() {
        assertNull(SupabaseRealtimeClient.realtimeWebSocketUrl("", key))
        assertNull(SupabaseRealtimeClient.realtimeWebSocketUrl("   ", key))
        assertNull(SupabaseRealtimeClient.realtimeWebSocketUrl("proj.supabase.co", key))
        assertNull(SupabaseRealtimeClient.realtimeWebSocketUrl("ftp://proj.supabase.co", key))
        assertNull(SupabaseRealtimeClient.realtimeWebSocketUrl("https://", key))
    }

    @Test
    fun `realtimeWebSocketUrl يعيد null لو المفتاح فاضي`() {
        assertNull(SupabaseRealtimeClient.realtimeWebSocketUrl("https://proj.supabase.co", ""))
        assertNull(SupabaseRealtimeClient.realtimeWebSocketUrl("https://proj.supabase.co", "   "))
    }

    // ------------------------------------------------------------------
    // تسمية الموضوع + استخراج الكود (دقة الفلتر)
    // ------------------------------------------------------------------

    @Test
    fun `roomTopic يبني الاسم بصيغة Supabase`() {
        val topic = SupabaseRealtimeClient.roomTopic("public", "game_rooms", "STAD99")

        assertEquals("realtime:public-game_rooms-eq.room_code.STAD99", topic)
    }

    @Test
    fun `roomTopic يحول الكود لحروف كبيرة ويقلم المسافات`() {
        assertEquals(
            "realtime:public-game_rooms-eq.room_code.GRID33",
            SupabaseRealtimeClient.roomTopic("public", "game_rooms", "  grid33  ")
        )
    }

    @Test
    fun `extractRoomCode يرجع نفس الكود اللي اتبني في roomTopic`() {
        // دي اختبار الانحدار للفلتر: الاشتراك بيتبني بـ
        // room_code=eq.<extractRoomCode(topic)>، فأي اختلاف = اشتراك على صف غلط
        listOf("STAD99", "GRID33", "BUZZ22", "MY1234").forEach { code ->
            val topic = SupabaseRealtimeClient.roomTopic("public", "game_rooms", code)
            assertEquals(code, SupabaseRealtimeClient.extractRoomCode(topic))
        }
    }

    @Test
    fun `extractRoomCode يتعامل مع اسم موضوع مش قياسي بدون انفجار`() {
        assertEquals("other", SupabaseRealtimeClient.extractRoomCode("other"))
        assertEquals("xyz", SupabaseRealtimeClient.extractRoomCode("a.b.c.xyz"))
    }

    // ------------------------------------------------------------------
    // التراجع أُسّي لإعادة الاتصال
    // ------------------------------------------------------------------

    @Test
    fun `nextReconnectDelayMs بينمو بشكل أسّي`() {
        assertEquals(1_000L, SupabaseRealtimeClient.nextReconnectDelayMs(1))
        assertEquals(2_000L, SupabaseRealtimeClient.nextReconnectDelayMs(2))
        assertEquals(4_000L, SupabaseRealtimeClient.nextReconnectDelayMs(3))
        assertEquals(8_000L, SupabaseRealtimeClient.nextReconnectDelayMs(4))
        assertEquals(16_000L, SupabaseRealtimeClient.nextReconnectDelayMs(5))
    }

    @Test
    fun `nextReconnectDelayMs مقصوص عند الحد الأقصى`() {
        assertEquals(
            SupabaseRealtimeClient.RECONNECT_MAX_DELAY_MS,
            SupabaseRealtimeClient.nextReconnectDelayMs(6)
        )
        assertEquals(
            SupabaseRealtimeClient.RECONNECT_MAX_DELAY_MS,
            SupabaseRealtimeClient.nextReconnectDelayMs(SupabaseRealtimeClient.MAX_RECONNECT_ATTEMPTS)
        )
    }

    @Test
    fun `nextReconnectDelayMs يتصاعد بشكل رتيب قبل السقف`() {
        var previous = -1L
        for (attempt in 1..SupabaseRealtimeClient.MAX_RECONNECT_ATTEMPTS) {
            val delayMs = SupabaseRealtimeClient.nextReconnectDelayMs(attempt)
            assertTrue("كل محاولة أطول أو تساوي السابقة", delayMs >= previous)
            assertTrue("محاصرة بالسقف", delayMs <= SupabaseRealtimeClient.RECONNECT_MAX_DELAY_MS)
            previous = delayMs
        }
    }

    @Test
    fun `nextReconnectDelayMs يعيد أطول قيمة بعد استنفاد المحاولات`() {
        // الإشارة دي بتخلّي الـVM يسلّم للتحديث الدوري بدل تعليق لأبد الآبد
        assertEquals(
            Long.MAX_VALUE,
            SupabaseRealtimeClient.nextReconnectDelayMs(SupabaseRealtimeClient.MAX_RECONNECT_ATTEMPTS + 1)
        )
        assertEquals(Long.MAX_VALUE, SupabaseRealtimeClient.nextReconnectDelayMs(1_000))
    }

    @Test
    fun `nextReconnectDelayMs يعيد الأساس للمحاولة الصفر أو السالبة`() {
        assertEquals(
            SupabaseRealtimeClient.RECONNECT_BASE_DELAY_MS,
            SupabaseRealtimeClient.nextReconnectDelayMs(0)
        )
        assertEquals(
            SupabaseRealtimeClient.RECONNECT_BASE_DELAY_MS,
            SupabaseRealtimeClient.nextReconnectDelayMs(-3)
        )
    }

    // ------------------------------------------------------------------
    // توفر الخدمة
    // ------------------------------------------------------------------

    @Test
    fun `isAvailable يعيد false مع placeholder credentials`() {
        val placeholder = SupabaseCredentials.PLACEHOLDER

        assertFalse(SupabaseRealtimeClient.isAvailable(placeholder))
    }

    @Test
    fun `isAvailable يعيد true مع مفاتيح صالحة`() {
        val credentials = SupabaseCredentials(
            url = "https://proj.supabase.co",
            anonKey = key,
            source = SupabaseCredentials.Source.OVERRIDE
        )

        assertTrue(SupabaseRealtimeClient.isAvailable(credentials))
    }

    @Test
    fun `isAvailable يعيد false لو الرابط وحده غير صالح`() {
        val credentials = SupabaseCredentials(
            url = "wss://proj.supabase.co", // مش http(s) ⇒ مفيش اشتقاق رابط صحيح
            anonKey = key,
            source = SupabaseCredentials.Source.OVERRIDE
        )

        assertFalse(SupabaseRealtimeClient.isAvailable(credentials))
    }

    @Test
    fun `subscribeToRoom بيرفض بهدوء لما المفاتيح مش مهيأة`() {
        // مفيش شبكة ولا socket: الدالة بترجع false قبل ما تفتح أي اتصال
        SupabaseEnv.overrideForTesting(
            SupabaseCredentials.PLACEHOLDER_URL,
            "dummy_anon_key"
        )
        try {
            val client = SupabaseRealtimeClient(
                credentialsProvider = { SupabaseEnv.resolve() }
            )

            assertFalse(client.subscribeToRoom("STAD99"))
            assertFalse("مفيش اتصال اتفتح", client.isConnected.value)
            assertNotNull(client.statusMessage.value)
            assertEquals(0, client.receivedEventCount)
        } finally {
            SupabaseEnv.clearOverride()
        }
    }

    // ------------------------------------------------------------------
    // وصف الحدث
    // ------------------------------------------------------------------

    @Test
    fun `RealtimeEvent يعرف نفسه كحدث غرفة`() {
        assertTrue(
            RealtimeEvent(type = "UPDATE", table = "game_rooms", topic = null).isRoomChange
        )
        assertTrue(
            "غير حساس لحالة الأحرف",
            RealtimeEvent(type = "UPDATE", table = "GAME_ROOMS", topic = null).isRoomChange
        )
        assertFalse(
            RealtimeEvent(type = "UPDATE", table = "scores", topic = null).isRoomChange
        )
    }

    @Test
    fun `RealtimeEvent يصف أنواع التغيير بالعربي`() {
        assertTrue(RealtimeEvent(type = "INSERT", table = "game_rooms", topic = null).arabicLabel.contains("جديد"))
        assertTrue(RealtimeEvent(type = "UPDATE", table = "game_rooms", topic = null).arabicLabel.contains("تحديث"))
        assertTrue(RealtimeEvent(type = "DELETE", table = "game_rooms", topic = null).arabicLabel.contains("حذف"))
        assertTrue(
            "نوع مجهول ما يقعش",
            RealtimeEvent(type = "ووو", table = "game_rooms", topic = null).arabicLabel.isNotBlank()
        )
    }

    // ------------------------------------------------------------------
    // ثوابت لها معنى برتوكولي
    // ------------------------------------------------------------------

    @Test
    fun `النبضة أقصر من مهلة الخمول في Supabase`() {
        // Supabase بيقفل الاتصال الصامت حوالي كل دقيقة؛ لو النبمة أطول
        // من كده الاشتراك هيموت كل دقيقة من غير سبب واضح
        assertTrue(
            SupabaseRealtimeClient.HEARTBEAT_INTERVAL_MS < 60_000L
        )
        assertTrue(
            SupabaseRealtimeClient.HEARTBEAT_INTERVAL_MS > 0L
        )
    }

    @Test
    fun `أسماء الجدول والمخطط تطابق المخطط الفعلي`() {
        assertEquals("public", SupabaseRealtimeClient.REALTIME_SCHEMA)
        assertEquals("game_rooms", SupabaseRealtimeClient.ROOM_TABLE)
        assertEquals("room_code", SupabaseRealtimeClient.ROOM_FILTER_COLUMN)
    }
}
