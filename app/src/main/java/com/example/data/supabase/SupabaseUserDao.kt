package com.example.data.supabase

/**
 * DAO المستخدمين ولوحة الصدارة فوق Supabase.
 *
 * يخاطب الـ views المعرفة في `supabase_schema_v2.sql`:
 *  - `public.users`             ← ملف مُجمَّع لكل لاعب (**للقراءة فقط**)
 *  - `public.user_challenges`   ← سجل تحديات اللاعبين (**للقراءة فقط**)
 *  - `public.leaderboard`       ← لوحة الصدارة (قابل للكتابة، فوق جدول `scores`)
 *
 * **مهم:** بما أن view المستخدمين `users` مبني بتجميع (GROUP BY) فوق `scores` فهو غير قابل للكتابة،
 * لذلك "إضافة لاعب جديد" تتم بإدراج أول نتيجة له في `leaderboard` — فيظهر تلقائياً
 * كصف جديد في `users`. هذه هي الدالة البسيطة [addNewPlayer].
 */
class SupabaseUserDao(private val api: SupabaseApi) {

    /** هل مفاتيح Supabase صالحة؟ (بدون ذلك كل العمليات تُعيد فشل/قائمة فارغة). */
    val isConfigured: Boolean get() = api.isConfigured

    /**
     * قائمة اللاعبين (ملف مُجمَّع: إجمالي النقاط والعملات وعدد التحديات).
     * @return `null` عند فشل الاتصال، وقائمة (قد تكون فارغة) عند النجاح.
     */
    suspend fun fetchUsers(limit: Int): List<SupabaseUser>? =
        api.selectList(
            table = VIEW_USERS,
            params = listOf(
                "select" to "*",
                "order" to "total_points.desc",
                "limit" to limit.coerceIn(1, MAX_LIMIT).toString()
            ),
            clazz = SupabaseUser::class.java
        )

    /** لاعب واحد بالاسم (الاسم هو مفتاح التجميع في view المستخدمين). */
    suspend fun fetchUserByName(displayName: String): SupabaseUser? {
        val name = displayName.trim()
        if (name.isEmpty()) return null
        return api.selectOne(
            table = VIEW_USERS,
            params = listOf("select" to "*", "display_name" to "eq.$name"),
            clazz = SupabaseUser::class.java
        )
    }

    /** أعلى النتائج عالمياً (مرتبة تنازلياً). */
    suspend fun fetchLeaderboard(limit: Int): List<SupabaseScore>? =
        api.selectList(
            table = VIEW_LEADERBOARD,
            params = listOf(
                "select" to "*",
                "order" to "points.desc",
                "limit" to limit.coerceIn(1, MAX_LIMIT).toString()
            ),
            clazz = SupabaseScore::class.java
        )

    /** تحديات لاعب معين (الأحدث أولاً). */
    suspend fun fetchChallenges(displayName: String, limit: Int): List<SupabaseUserChallenge>? {
        val name = displayName.trim()
        if (name.isEmpty()) return null
        return api.selectList(
            table = VIEW_USER_CHALLENGES,
            params = listOf(
                "select" to "*",
                "display_name" to "eq.$name",
                "order" to "played_at.desc",
                "limit" to limit.coerceIn(1, MAX_LIMIT).toString()
            ),
            clazz = SupabaseUserChallenge::class.java
        )
    }

    /**
     * إضافة لاعب جديد: يُدرج أول سجل له في `public.leaderboard`
     * فيظهر فوراً كصف في `public.users`.
     *
     * @return `true` عند نجاح الإدراج في السيرفر.
     */
    suspend fun addPlayer(draft: NewPlayerDraft): Boolean {
        val name = sanitizePlayerName(draft.playerName)
        if (name.isEmpty()) return false

        val entry = SupabaseScore(
            // id = null → تحذفه Moshi من الـ JSON فتولّد القاعدة UUID تلقائياً
            id = null,
            playerName = name,
            points = draft.points.coerceAtLeast(0),
            coins = draft.coins.coerceAtLeast(0),
            levelTitle = draft.levelTitle,
            favoriteClub = draft.favoriteClub,
            challengeType = draft.challengeType
        )

        return api.insert(
            table = VIEW_LEADERBOARD,
            value = entry,
            clazz = SupabaseScore::class.java
        )
    }

    /** تسجيل نتيجة تحدي جديدة للاعب موجود (كتابة في `leaderboard`). */
    suspend fun recordChallenge(score: SupabaseScore): Boolean =
        api.insert(table = VIEW_LEADERBOARD, value = score, clazz = SupabaseScore::class.java)

    companion object {
        const val VIEW_USERS = "users"
        const val VIEW_USER_CHALLENGES = "user_challenges"
        const val VIEW_LEADERBOARD = "leaderboard"
        const val MAX_NAME_LENGTH = 40
        private const val MAX_LIMIT = 100

        const val DEFAULT_LEVEL_TITLE = "مشجع هاوٍ"
        const val DEFAULT_FAVORITE_CLUB = "ريال مدريد"
        const val DEFAULT_CHALLENGE_TYPE = "general"
    }
}

/** بيانات لاعب جديد قبل إدراجه في الجدول. */
data class NewPlayerDraft(
    val playerName: String,
    val points: Int = 0,
    val coins: Int = 0,
    val levelTitle: String = SupabaseUserDao.DEFAULT_LEVEL_TITLE,
    val favoriteClub: String = SupabaseUserDao.DEFAULT_FAVORITE_CLUB,
    val challengeType: String = SupabaseUserDao.DEFAULT_CHALLENGE_TYPE
)

/**
 * تنظيف اسم اللاعب قبل إدراجه في الجدول: قصّ المسافات، توحيد المسافات الداخلية
 * (بما فيها فواصل الأسطر) إلى مسافة واحدة، وتقييد الطول بحد العمود.
 *
 * دالة نقية حتى تُختبر بدون شبكة أو Context.
 *
 * @return الاسم الصالح للإدراج، أو نص فارغ إذا كان المدخل غير صالح.
 */
fun sanitizePlayerName(
    raw: String,
    maxLength: Int = SupabaseUserDao.MAX_NAME_LENGTH
): String = raw.trim()
    .replace(WHITESPACE_RUN_REGEX, " ")
    .take(maxLength.coerceAtLeast(0))
    .trim()

private val WHITESPACE_RUN_REGEX = Regex("\\s+")

/**
 * **الدالة البسيطة المطلوبة لإضافة لاعب جديد إلى الجدول.**
 *
 * مثال استخدام:
 * ```
 * val ok = addNewPlayer("لاعب جديد 🎮")
 * val ok2 = addNewPlayer("عمر المدريدي", points = 120, coins = 40, favoriteClub = "ريال مدريد")
 * ```
 *
 * تُدرج صفاً في `public.leaderboard` (view قابل للكتابة فوق جدول `scores`)،
 * وبما أن `public.users` تجميع فوق نفس الجدول فسيظهر اللاعب فوراً في قائمة المستخدمين.
 *
 * @return `true` عند نجاح الإضافة في Supabase، و`false` إذا كان الاسم فارغاً
 *         أو المفاتيح غير مُهيّأة أو فشل الاتصال.
 */
suspend fun addNewPlayer(
    playerName: String,
    points: Int = 0,
    coins: Int = 0,
    levelTitle: String = SupabaseUserDao.DEFAULT_LEVEL_TITLE,
    favoriteClub: String = SupabaseUserDao.DEFAULT_FAVORITE_CLUB,
    challengeType: String = SupabaseUserDao.DEFAULT_CHALLENGE_TYPE
): Boolean = SupabaseClientProvider.userDao().addPlayer(
    NewPlayerDraft(
        playerName = playerName,
        points = points,
        coins = coins,
        levelTitle = levelTitle,
        favoriteClub = favoriteClub,
        challengeType = challengeType
    )
)
