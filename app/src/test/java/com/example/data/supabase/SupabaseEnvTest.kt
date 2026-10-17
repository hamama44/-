package com.example.data.supabase

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * اختبارات طبقة Supabase:
 *  1) تحليل الطوابع الزمنية القادمة من PostgreSQL (TIMESTAMPTZ).
 *  2) حسم مفاتيح الاتصال من متغيرات البيئة / BuildConfig / التخزين المحلي.
 *
 * لا تحتاج Robolectric: [parseSupabaseTimestamp] و[SupabaseCredentials] نقيتان،
 * و[SupabaseEnv] يُختبر عبر [SupabaseEnv.overrideForTesting].
 */
class SupabaseEnvTest {

    @After
    fun tearDown() {
        // مهم: لا نترك override يؤثر على اختبارات أخرى
        SupabaseEnv.clearOverride()
    }

    // =========================================================================
    // 1) الطوابع الزمنية
    // =========================================================================

    @Test
    fun `parseSupabaseTimestamp يفهم صيغة PostgreSQL الكاملة مع الكسور الست`() {
        val parsed = parseSupabaseTimestamp("2026-10-17T18:15:30.123456+00:00")
        assertNotEquals("يجب ألا يفشل التحليل", 0L, parsed)
        assertTrue(parsed > 0L)
    }

    @Test
    fun `parseSupabaseTimestamp يفهم صيغة Z المختصرة`() {
        val withZ = parseSupabaseTimestamp("2026-10-17T18:15:30Z")
        val withOffset = parseSupabaseTimestamp("2026-10-17T18:15:30+00:00")
        assertNotEquals(0L, withZ)
        assertEquals("Z و +00:00 نفس اللحظة", withOffset, withZ)
    }

    @Test
    fun `parseSupabaseTimestamp يتجاهل الكسور الزائدة عن المللي ثانية`() {
        val millis = parseSupabaseTimestamp("2026-10-17T18:15:30.123+00:00")
        val micros = parseSupabaseTimestamp("2026-10-17T18:15:30.123456+00:00")
        assertEquals(millis, micros)
    }

    @Test
    fun `parseSupabaseTimestamp يحافظ على الترتيب الزمني`() {
        val earlier = parseSupabaseTimestamp("2026-10-17T18:15:30Z")
        val later = parseSupabaseTimestamp("2026-10-17T18:16:30Z")
        assertTrue("الأحدث يجب أن يكون أكبر", later > earlier)
        assertEquals("فرق دقيقة واحدة", 60_000L, later - earlier)
    }

    @Test
    fun `parseSupabaseTimestamp يفهم الإزاحة الزمنية غير الصفرية`() {
        val utc = parseSupabaseTimestamp("2026-10-17T20:15:30+02:00")
        val expected = parseSupabaseTimestamp("2026-10-17T18:15:30Z")
        assertEquals("‎+02:00 تعادل 18:15 UTC", expected, utc)
    }

    @Test
    fun `parseSupabaseTimestamp يعيد صفر عند القيم الفارغة أو التالفة`() {
        assertEquals(0L, parseSupabaseTimestamp(null))
        assertEquals(0L, parseSupabaseTimestamp(""))
        assertEquals(0L, parseSupabaseTimestamp("   "))
        assertEquals(0L, parseSupabaseTimestamp("ليس تاريخاً"))
        assertEquals(0L, parseSupabaseTimestamp("2026-13-45T99:99:99"))
    }

    // =========================================================================
    // 2) مفاتيح الاتصال من متغيرات البيئة
    // =========================================================================

    @Test
    fun `resolve يعيد قيم override بمصدر OVERRIDE`() {
        SupabaseEnv.overrideForTesting("https://real-project.supabase.co", "eyJhbGci-real-key")

        val credentials = SupabaseEnv.resolve()

        assertEquals(SupabaseCredentials.Source.OVERRIDE, credentials.source)
        assertEquals("https://real-project.supabase.co", credentials.url)
        assertEquals("eyJhbGci-real-key", credentials.anonKey)
        assertTrue("قيم صالحة ⇒ قابل للاستخدام", credentials.isUsable)
        assertTrue(SupabaseEnv.isConfigured())
    }

    @Test
    fun `override يقص المسافات الزائدة`() {
        SupabaseEnv.overrideForTesting("  https://p.supabase.co/  ", "  key123  ")

        val credentials = SupabaseEnv.resolve()

        assertEquals("https://p.supabase.co/", credentials.url)
        assertEquals("key123", credentials.anonKey)
    }

    @Test
    fun `clearOverride يعيد القراءة من المصادر الأصلية`() {
        SupabaseEnv.overrideForTesting("https://p.supabase.co", "key123")
        SupabaseEnv.clearOverride()

        assertFalse(SupabaseEnv.hasOverride())
        assertNotEquals(SupabaseCredentials.Source.OVERRIDE, SupabaseEnv.resolve().source)
    }

    @Test
    fun `قيم placeholder الافتراضية تعتبر غير مُهيّأة`() {
        assertFalse(SupabaseCredentials.PLACEHOLDER.isUsable)
        assertFalse(
            SupabaseCredentials(
                url = "https://xyzcompany.supabase.co",
                anonKey = "eyJhbGci.dummy_anon_key",
                source = SupabaseCredentials.Source.BUILD_CONFIG
            ).isUsable
        )
        assertFalse(
            SupabaseCredentials("", "", SupabaseCredentials.Source.PLACEHOLDER).isUsable
        )
    }

    @Test
    fun `restBaseUrl يبني مسار PostgREST الصحيح`() {
        val withSlash = SupabaseCredentials(
            "https://p.supabase.co/",
            "key",
            SupabaseCredentials.Source.ENVIRONMENT
        )
        val withoutSlash = SupabaseCredentials(
            "https://p.supabase.co",
            "key",
            SupabaseCredentials.Source.ENVIRONMENT
        )

        assertEquals("https://p.supabase.co/rest/v1", withSlash.restBaseUrl)
        assertEquals("بدون تكرار الشرطة", withSlash.restBaseUrl, withoutSlash.restBaseUrl)
    }

    @Test
    fun `SupabaseConfig يفوّض إلى SupabaseEnv`() {
        SupabaseEnv.overrideForTesting("https://delegated.supabase.co", "delegated-key")

        assertEquals("https://delegated.supabase.co", SupabaseConfig.url)
        assertEquals("delegated-key", SupabaseConfig.anonKey)
        assertTrue(SupabaseConfig.isConfigured)
        assertEquals(SupabaseCredentials.Source.OVERRIDE, SupabaseConfig.credentialsSource)
    }

    @Test
    fun `describeStatus يوضّح حالة الربط بالعربية`() {
        SupabaseEnv.overrideForTesting("https://p.supabase.co", "real-key")
        assertTrue(SupabaseClientProvider.describeStatus().contains("متصل"))

        SupabaseEnv.clearOverride()
        SupabaseEnv.overrideForTesting("https://xyzcompany.supabase.co", "dummy")
        assertTrue(SupabaseClientProvider.describeStatus().contains("غير مُهيّأ"))
    }
}
