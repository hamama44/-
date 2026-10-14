package com.example.data

data class UserProfile(
    val playerName: String = "كابتن الملعب",
    val favoriteClub: String = "ريال مدريد",
    val avatarEmoji: String = "👑", // Avatar icon displayed in TopBar & Profile
    val points: Int = 1450,
    val coins: Int = 420,
    val gamesPlayed: Int = 32,
    val winRate: Int = 82,
    val currentStreak: Int = 6,
    val bestStreak: Int = 14,
    val thirtySecondsHighScore: Int = 19,
    val sabahoTrophies: Int = 6,
    val unlockedHintsMastery: Int = 14,
    val careerGuessedCount: Int = 18,
    val availableLifelines: AvailableLifelines = AvailableLifelines(),
    val matchHistory: List<MatchHistoryItem> = defaultMatchHistory()
) {
    val levelTitle: String
        get() = when {
            points < 300 -> "مشجع هاوٍ ⚽"
            points < 800 -> "لاعب صاعد 👟"
            points < 1800 -> "نجم الفريق ⭐"
            points < 3500 -> "كابتن الملعب 🏆"
            points < 6000 -> "محترف دولي 🌍"
            else -> "أسطورة كرة القدم 👑"
        }

    val levelNumber: Int
        get() = (points / 400) + 1

    val currentLevelProgress: Float
        get() = ((points % 400) / 400f).coerceIn(0f, 1f)

    /**
     * Dynamic Badges system calculated from player achievements
     */
    val earnedBadges: List<Badge>
        get() = listOf(
            Badge(
                id = "streak_10",
                title = "سلسلة اللهب الخارقة 🔥",
                description = "الفوز بـ 10 تحديات أو إجابات صحيحة متتالية",
                iconEmoji = "🔥",
                isUnlocked = bestStreak >= 10,
                progress = (bestStreak / 10f).coerceIn(0f, 1f),
                progressText = "$bestStreak / 10"
            ),
            Badge(
                id = "thirty_master",
                title = "ملك الـ 30 ثانية ⏱️",
                description = "تحقيق 15 إجابة صحيحة أو أكثر في تحدي الثلاثين",
                iconEmoji = "⏱️",
                isUnlocked = thirtySecondsHighScore >= 15,
                progress = (thirtySecondsHighScore / 15f).coerceIn(0f, 1f),
                progressText = "$thirtySecondsHighScore / 15"
            ),
            Badge(
                id = "sabaho_champion",
                title = "صائد كؤوس صباحو 🏆",
                description = "الفوز بـ 5 جولات كاملة في صباحو تحدي",
                iconEmoji = "🏆",
                isUnlocked = sabahoTrophies >= 5,
                progress = (sabahoTrophies / 5f).coerceIn(0f, 1f),
                progressText = "$sabahoTrophies / 5"
            ),
            Badge(
                id = "detective_master",
                title = "المحقق الكروي الخارق 🕵️‍♂️",
                description = "تخمين 10 لاعبين غامضين في العب مع سري",
                iconEmoji = "🕵️‍♂️",
                isUnlocked = unlockedHintsMastery >= 10,
                progress = (unlockedHintsMastery / 10f).coerceIn(0f, 1f),
                progressText = "$unlockedHintsMastery / 10"
            ),
            Badge(
                id = "career_hunter",
                title = "صياد مسيرات الأندية 🗺️",
                description = "اكتشاف 15 مسيرة لاعب بنجاح من جداول الأندية",
                iconEmoji = "🗺️",
                isUnlocked = careerGuessedCount >= 15,
                progress = (careerGuessedCount / 15f).coerceIn(0f, 1f),
                progressText = "$careerGuessedCount / 15"
            ),
            Badge(
                id = "points_legend",
                title = "أسطورة المليون نقطة ⭐",
                description = "تخطي 1000 نقطة خبرة كروية في ملعب التحدي",
                iconEmoji = "⭐",
                isUnlocked = points >= 1000,
                progress = (points / 1000f).coerceIn(0f, 1f),
                progressText = "$points / 1000"
            )
        )
}

data class Badge(
    val id: String,
    val title: String,
    val description: String,
    val iconEmoji: String,
    val isUnlocked: Boolean,
    val progress: Float,
    val progressText: String
)

data class MatchHistoryItem(
    val id: String,
    val gameTitle: String,
    val earnedPoints: Int,
    val earnedCoins: Int,
    val resultBadge: String,
    val timeAgo: String
)

private fun defaultMatchHistory() = listOf(
    MatchHistoryItem("h1", "صباحو تحدي 🏆", 75, 40, "فوز ساحق 🥇", "منذ 15 دقيقة"),
    MatchHistoryItem("h2", "تحدي الثلاثين ⏱️", 45, 20, "رقم قياسي 🔥", "اليوم 14:10"),
    MatchHistoryItem("h3", "العب مع سري 🕵️‍♂️", 85, 35, "تخمين سريع ⭐", "أمس 20:45"),
    MatchHistoryItem("h4", "مسيرة اللاعب 🗺️", 50, 20, "اكتشاف ممتاز ⚽", "أمس 18:20")
)

data class AvailableLifelines(
    val fiftyFifty: Boolean = true,
    val skipQuestion: Boolean = true,
    val extraTime: Boolean = true
)

// Game 1: 30 Seconds Speed Quiz
data class ThirtySecondsQuestion(
    val id: String,
    val question: String,
    val options: List<String>,
    val correctIndex: Int,
    val category: String
)

// Game 1 Mode B: Player Mention
data class PlayerMentionChallenge(
    val id: String,
    val clubOrCountry: String,
    val type: String, // "نادي" or "منتخب"
    val badgeEmoji: String,
    val acceptedPlayers: List<String>
)

// Game 2: Sabaho Challenge
enum class Difficulty(val labelArabic: String, val pointsMultiplier: Int) {
    EASY("مستوى سهل", 10),
    MEDIUM("مستوى متوسط", 25),
    HARD("مستوى نار 🔥", 50)
}

data class SabahoQuestion(
    val id: String,
    val difficulty: Difficulty,
    val question: String,
    val options: List<String>,
    val correctIndex: Int,
    val explanation: String
)

// Game 3: Mystery Player ("العب مع سري")
data class MysteryHint(
    val title: String,
    val value: String,
    val iconEmoji: String
)

data class MysteryPlayer(
    val id: String,
    val playerName: String,
    val hints: List<MysteryHint>,
    val options: List<String>
)

// Game 4: Career Path Challenge ("خمّن اللاعب من مسيرته")
data class CareerClub(
    val clubName: String,
    val years: String,
    val countryFlag: String,
    val appearancesAndGoals: String
)

data class CareerChallenge(
    val id: String,
    val playerName: String,
    val position: String,
    val nationality: String,
    val nationalityFlag: String,
    val careerTimeline: List<CareerClub>,
    val options: List<String>
)

// Game 5: True or False Blitz ("صح أو غلط")
data class TrueFalseQuestion(
    val id: String,
    val statement: String,
    val isTrue: Boolean,
    val explanation: String,
    val category: String
)

// Game 6: Event Ordering ("ترتيب الأهداف والأحداث")
data class FootballEventItem(
    val id: String,
    val text: String,
    val yearOrDetail: String,
    val orderRank: Int
)

data class EventOrderChallenge(
    val id: String,
    val title: String,
    val description: String,
    val items: List<FootballEventItem>
)

// Leaderboard with Badges Showcase
data class LeaderboardUser(
    val rank: Int,
    val name: String,
    val avatarEmoji: String = "⚽",
    val points: Int,
    val levelBadge: String,
    val club: String,
    val badges: List<String> = emptyList(), // Badges earned e.g. ["🔥", "⏱️", "🏆"]
    val isCurrentUser: Boolean = false
)

// Difficulty Selector model for all challenges
enum class GameDifficulty(
    val id: String,
    val titleArabic: String,
    val description: String,
    val iconEmoji: String,
    val pointsBonusPercent: Int,
    val coinsBonusPercent: Int,
    val timerSeconds: Int,
    val colorHex: Long
) {
    EASY(
        id = "easy",
        titleArabic = "سهل (الهواة)",
        description = "وقت مريح وأسئلة تمهيدية للمبتدئين في كرة القدم",
        iconEmoji = "👟",
        pointsBonusPercent = 0,
        coinsBonusPercent = 0,
        timerSeconds = 35,
        colorHex = 0xFF00E676
    ),
    MEDIUM(
        id = "medium",
        titleArabic = "متوسط (المحترفين)",
        description = "تحدي متوازن مع سرعة بديهة ونقاط مضاعفة",
        iconEmoji = "⭐",
        pointsBonusPercent = 25,
        coinsBonusPercent = 20,
        timerSeconds = 25,
        colorHex = 0xFFFFD700
    ),
    HARD(
        id = "hard",
        titleArabic = "صعب (تحدي الأساطير 🔥)",
        description = "وقت خاطف وتحدٍ ناري لأصحاب الذاكرة التكتيكية الحديدية",
        iconEmoji = "🔥",
        pointsBonusPercent = 60,
        coinsBonusPercent = 50,
        timerSeconds = 15,
        colorHex = 0xFFFF3D57
    )
}

// Real-Time PvP Match Models (المواجهة المباشرة)
data class PvPPlayer(
    val name: String,
    val avatarEmoji: String,
    val club: String,
    val score: Int = 0,
    val lastAnswerStatus: Boolean? = null
)

data class PvPQuestion(
    val id: String,
    val question: String,
    val options: List<String>,
    val correctIndex: Int
)

data class PvPMatchState(
    val isSearching: Boolean = true,
    val currentRound: Int = 1,
    val totalRounds: Int = 5,
    val user: PvPPlayer,
    val opponent: PvPPlayer,
    val currentQuestion: PvPQuestion? = null,
    val userSelectedOption: Int? = null,
    val opponentSelectedOption: Int? = null,
    val roundTimer: Int = 12,
    val commentaryMessage: String = "المباراة انطلقت! أجب أسرع لتسجيل هدف ⚽",
    val isMatchFinished: Boolean = false
)
