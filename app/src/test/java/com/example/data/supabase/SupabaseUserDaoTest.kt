package com.example.data.supabase

import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * اختبارات طبقة المستخدمين ولوحة الصدارة.
 *
 * كل الاختبارات هنا **بدون شبكة**: قبل كل اختبار نُثبّت مفاتيح غير صالحة
 * (قيم placeholder) عبر [SupabaseEnv.overrideForTesting]، فتتوقف كل نداءات
 * [SupabaseApi] عند فحص `isUsable` وترجع `false`/`null` قبل أي طلب HTTP.
 * بهذا نختبر عقود الطبقة (validation + اتفاقية null = فشل الاتصال) بلا إنترنت.
 */
class SupabaseUserDaoTest {

    private lateinit var dao: SupabaseUserDao

    @Before
    fun setUp() {
        // مفاتيح غير صالحة عمداً ⇒ لا توجد أي مكالمة شبكة
        SupabaseEnv.overrideForTesting(
            SupabaseCredentials.PLACEHOLDER_URL,
            "eyJhbGci.dummy_anon_key"
        )
        assertFalse("المفاتيح يجب أن تكون غير صالحة للاختبار", SupabaseEnv.isConfigured())
        dao = SupabaseUserDao(SupabaseApi())
    }

    @After
    fun tearDown() {
        SupabaseEnv.clearOverride()
    }

    // =========================================================================
    // sanitizePlayerName — دالة نقية
    // =========================================================================

    @Test
    fun `sanitizePlayerName يقص المسافات ويوحّد المسافات الداخلية`() {
        assertEquals("عمر المدريدي", sanitizePlayerName("   عمر    المدريدي   "))
        assertEquals("أحمد فخر العرب", sanitizePlayerName("أحمد\tفخر\nالعرب"))
    }

    @Test
    fun `sanitizePlayerName يعيد نصاً فارغاً للمدخلات غير الصالحة`() {
        assertEquals("", sanitizePlayerName(""))
        assertEquals("", sanitizePlayerName("   "))
        assertEquals("", sanitizePlayerName("\n\t  "))
    }

    @Test
    fun `sanitizePlayerName يقيّد الطول بحد العمود`() {
        val long = "ل".repeat(SupabaseUserDao.MAX_NAME_LENGTH + 25)

        val sanitized = sanitizePlayerName(long)

        assertEquals(SupabaseUserDao.MAX_NAME_LENGTH, sanitized.length)
    }

    @Test
    fun `sanitizePlayerName يحترم حداً مخصصاً ولا يترك مسافة طرفية`() {
        assertEquals("abc", sanitizePlayerName("  abcdef  ", maxLength = 3))
        assertEquals("a", sanitizePlayerName("a b c", maxLength = 1))
        assertEquals("", sanitizePlayerName("anything", maxLength = 0))
    }

    @Test
    fun `sanitizePlayerName يحافظ على الاسم العربي الصحيح كما هو`() {
        assertEquals("ياسين الأطلسي 🇲🇦", sanitizePlayerName("ياسين الأطلسي 🇲🇦"))
    }

    // =========================================================================
    // NewPlayerDraft — القيم الافتراضية
    // =========================================================================

    @Test
    fun `NewPlayerDraft يستخدم القيم الافتراضية العربية`() {
        val draft = NewPlayerDraft(playerName = "لاعب جديد")

        assertEquals(0, draft.points)
        assertEquals(0, draft.coins)
        assertEquals("مشجع هاوٍ", draft.levelTitle)
        assertEquals("ريال مدريد", draft.favoriteClub)
        assertEquals("general", draft.challengeType)
    }

    // =========================================================================
    // عقود الـ DAO بدون شبكة
    // =========================================================================

    @Test
    fun `addPlayer يرفض الاسم الفارغ قبل أي محاولة شبكة`() = runTest {
        assertFalse(dao.addPlayer(NewPlayerDraft(playerName = "")))
        assertFalse(dao.addPlayer(NewPlayerDraft(playerName = "   ")))
        assertFalse(dao.addPlayer(NewPlayerDraft(playerName = "\n\t ")))
    }

    @Test
    fun `addPlayer يفشل بأمان عندما تكون المفاتيح غير مُهيّأة`() = runTest {
        // اسم صالح لكن لا توجد مفاتيح ⇒ false بدون استثناء
        assertFalse(dao.addPlayer(NewPlayerDraft(playerName = "كابتن الملعب 👑")))
    }

    @Test
    fun `addNewPlayer الدالة البسيطة تُعيد false للمدخلات غير الصالحة`() = runTest {
        assertFalse(addNewPlayer(""))
        assertFalse(addNewPlayer("   "))
    }

    @Test
    fun `addNewPlayer يفشل بأمان بدون مفاتيح مُهيّأة`() = runTest {
        assertFalse(addNewPlayer("لاعب جديد 🎮", points = 120, coins = 40))
    }

    @Test
    fun `fetchUsers يعيد null عند تعذّر الاتصال حسب العقد`() = runTest {
        // الاتفاقية: null = فشل الاتصال، emptyList = نجح الطلب بلا صفوف
        assertNull(dao.fetchUsers(10))
    }

    @Test
    fun `fetchLeaderboard يعيد null عند تعذّر الاتصال`() = runTest {
        assertNull(dao.fetchLeaderboard(25))
    }

    @Test
    fun `fetchUserByName يرفض الاسم الفارغ`() = runTest {
        assertNull(dao.fetchUserByName("  "))
    }

    @Test
    fun `fetchChallenges يرفض الاسم الفارغ`() = runTest {
        assertNull(dao.fetchChallenges("", 10))
    }

    @Test
    fun `recordChallenge يفشل بأمان بدون مفاتيح`() = runTest {
        assertFalse(
            dao.recordChallenge(
                SupabaseScore(playerName = "كابتن", points = 100, coins = 20)
            )
        )
    }

    @Test
    fun `isConfigured يعكس حالة المفاتيح`() {
        assertFalse(dao.isConfigured)

        SupabaseEnv.overrideForTesting("https://real.supabase.co", "real-anon-key")
        assertTrue(dao.isConfigured)
    }
}
