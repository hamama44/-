package com.example.data.daily

import android.content.Context
import android.content.SharedPreferences
import com.example.notifications.StadiumNotificationManager
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class DailyChallenge(
    val id: String,
    val dateKey: String,
    val title: String,
    val gameTitle: String,
    val gameTypeTag: String, // "autobus", "five_seconds", "taboo", "bedoon_kalam", "proverbs", "millionaire", "four_clues", "sabaho", "thirty_sec"
    val gameIcon: String,
    val goalDescription: String,
    val targetScore: Int,
    val currentScore: Int = 0,
    val rewardPoints: Int = 200,
    val rewardCoins: Int = 75,
    val isCompleted: Boolean = false,
    val isClaimed: Boolean = false,
    val difficulty: String = "متوسط ⚡",
    val tip: String = "استخدم المايك والذكاء الاصطناعي للإجابة بسرعة ودقة!"
)

class DailyChallengeManager(private val context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("malab_daily_challenges", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_DATE = "current_date_key"
        private const val KEY_CHALLENGE_INDEX = "challenge_index"
        private const val KEY_PROGRESS = "current_progress"
        private const val KEY_COMPLETED = "is_completed"
        private const val KEY_CLAIMED = "is_claimed"
        private const val KEY_REMINDER_ENABLED = "daily_reminder_enabled"
        private const val KEY_REMINDER_HOUR = "reminder_hour"
        private const val KEY_REMINDER_MINUTE = "reminder_minute"

        val CHALLENGE_POOL = listOf(
            DailyChallengeTemplate(
                title = "تحدي السرعة اللغوية 🚌",
                gameTitle = "أتوبيس كومبليت",
                gameTypeTag = "autobus",
                gameIcon = "🚌",
                goalDescription = "احصل على 100 نقطة أو أكثر في جولة واحدة بحرف عشوائي",
                targetScore = 100,
                rewardPoints = 250,
                rewardCoins = 80,
                difficulty = "حماسي 🔥",
                tip = "استخدم المايك لإملاء الكلمات بسرعة قبل أن يسبقك الخصم الذكي!"
            ),
            DailyChallengeTemplate(
                title = "تحدي الـ 5 ثواني الخارق ⚡",
                gameTitle = "تحدي الـ 5 ثواني",
                gameTypeTag = "five_seconds",
                gameIcon = "⚡",
                goalDescription = "حقق 3 إجابات صحيحة عبر الميكروفون مع تدقيق الذكاء الاصطناعي",
                targetScore = 3,
                rewardPoints = 200,
                rewardCoins = 60,
                difficulty = "سرعة بديهة 🧠",
                tip = "تحدث بوضوح في المايك فور بدء العداد، والذكاء الاصطناعي سيتحقق فوراً!"
            ),
            DailyChallengeTemplate(
                title = "مراوغ التابو الذكي 🚫",
                gameTitle = "التابو العربي",
                gameTypeTag = "taboo",
                gameIcon = "🚫",
                goalDescription = "اشرح بطاقتين كاملتين بالصوت دون نطق أي كلمة محظورة",
                targetScore = 2,
                rewardPoints = 220,
                rewardCoins = 70,
                difficulty = "تكتيكي 🎯",
                tip = "راقب مراقب التابو الذكي وتجنب كل الكلمات المحظورة الخمسة!"
            ),
            DailyChallengeTemplate(
                title = "فارس السينما والأمثال 🎭",
                gameTitle = "بدون كلام",
                gameTypeTag = "bedoon_kalam",
                gameIcon = "🎭",
                goalDescription = "خمّن 3 ألغاز إيموجي لأفلام ومسرحيات عربية بدقة",
                targetScore = 3,
                rewardPoints = 180,
                rewardCoins = 50,
                difficulty = "متوسط 🌟",
                tip = "ركز في تسلسل الرموز التعبيرية، ويمكنك نطق التخمين بالمايك!"
            ),
            DailyChallengeTemplate(
                title = "حكيم التراث والأمثال 📜",
                gameTitle = "كمّل المثل الشعبي",
                gameTypeTag = "proverbs",
                gameIcon = "📜",
                goalDescription = "حقق سلسلة 4 أمثال شعبية متتالية بدون أي خطأ",
                targetScore = 4,
                rewardPoints = 190,
                rewardCoins = 55,
                difficulty = "تراثي 🏛️",
                tip = "تذكر أمثال أجدادنا، الإجابة الصحيحة تكافئك بمضاعفة النقاط!"
            ),
            DailyChallengeTemplate(
                title = "سفير المعرفة والثقافة 💰",
                gameTitle = "المليون العربي",
                gameTypeTag = "millionaire",
                gameIcon = "💰",
                goalDescription = "تجاوز المستوى 5 واكسب 10,000 نقطة على السلّم الثقافي",
                targetScore = 10000,
                rewardPoints = 300,
                rewardCoins = 100,
                difficulty = "أسطوري 👑",
                tip = "لا تتردد في استخدام وسائل المساعدة (50:50 والجمهور) عند الضرورة!"
            ),
            DailyChallengeTemplate(
                title = "مفكك الروابط العجيبة 🔍",
                gameTitle = "الرابط العجيب",
                gameTypeTag = "four_clues",
                gameIcon = "🔍",
                goalDescription = "اكتشف الكلمة المشتركة لـ 3 ألغاز لغوية متتالية",
                targetScore = 3,
                rewardPoints = 175,
                rewardCoins = 50,
                difficulty = "تحليلي 💡",
                tip = "ابحث عن القاسم المشترك بين الكلمات الأربع بدقة!"
            ),
            DailyChallengeTemplate(
                title = "كابتن التكتيك الصباحي ⚽",
                gameTitle = "صباحو تحدي",
                gameTypeTag = "sabaho",
                gameIcon = "⚽",
                goalDescription = "أنهِ جولة صباحو بنتيجة 80 نقطة كروية على الأقل",
                targetScore = 80,
                rewardPoints = 220,
                rewardCoins = 65,
                difficulty = "كروي ناري 🔥",
                tip = "الأسئلة الصعبة تمنحك نقاطاً مضاعفة، استخدم وسيلة الوقت الإضافي!"
            ),
            DailyChallengeTemplate(
                title = "صاروخ الـ 30 ثانية ⏱️",
                gameTitle = "تحدي الثلاثين ثانية",
                gameTypeTag = "thirty_sec",
                gameIcon = "⏱️",
                goalDescription = "سجل 7 إجابات كروية صحيحة قبل إطلاق صافرة النهاية",
                targetScore = 7,
                rewardPoints = 210,
                rewardCoins = 60,
                difficulty = "سريع جداً ⚡",
                tip = "السرعة هي كل شيء! لا تفكر طويلاً في السؤال الواحد."
            ),
            DailyChallengeTemplate(
                title = "داهية التيك تاك تو الكروي ⚽",
                gameTitle = "تيك تاك تو كروي",
                gameTypeTag = "tictactoe",
                gameIcon = "⚽",
                goalDescription = "حقق الفوز في شبكة كروية كاملة بربط الأندية والمنتخبات",
                targetScore = 1,
                rewardPoints = 220,
                rewardCoins = 70,
                difficulty = "تكتيكي 🧠",
                tip = "تذكر النجوم الذين تنقلوا بين كبرى الدوريات الأوروبية!"
            ),
            DailyChallengeTemplate(
                title = "ملك المزاد الكروي 🔨",
                gameTitle = "المزاد الكروي",
                gameTypeTag = "auction",
                gameIcon = "🔨",
                goalDescription = "اربح مزايدة وسرد 5 أسماء صحيحة بالمايك قبل انتهاء الوقت",
                targetScore = 1,
                rewardPoints = 250,
                rewardCoins = 80,
                difficulty = "حماسي 🔥",
                tip = "تحدث بثقة ووضوح في المايك فور رسو المزاد عليك!"
            ),
            DailyChallengeTemplate(
                title = "كاشف الجواسيس المحترف 🕵️‍♂️",
                gameTitle = "مين الجاسوس؟",
                gameTypeTag = "spy",
                gameIcon = "🕵️‍♂️",
                goalDescription = "شارك في جولة برا السالفة واكشف الجاسوس مع أصدقائك في الجلسة",
                targetScore = 1,
                rewardPoints = 200,
                rewardCoins = 60,
                difficulty = "ذكاء اجتماعي 👥",
                tip = "راقب من يتردد أو يتهرب من الإجابة المباشرة!"
            )
        )
    }

    data class DailyChallengeTemplate(
        val title: String,
        val gameTitle: String,
        val gameTypeTag: String,
        val gameIcon: String,
        val goalDescription: String,
        val targetScore: Int,
        val rewardPoints: Int,
        val rewardCoins: Int,
        val difficulty: String,
        val tip: String
    )

    private fun getTodayDateKey(): String {
        val sdf = SimpleDateFormat("yyyyMMdd", Locale.US)
        return sdf.format(Date())
    }

    fun getTodayChallenge(): DailyChallenge {
        val todayKey = getTodayDateKey()
        val savedDate = prefs.getString(KEY_DATE, "")

        if (savedDate != todayKey) {
            // New Day: Select a challenge deterministically based on day of year, or next index
            val dayOfYear = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
            val templateIndex = dayOfYear % CHALLENGE_POOL.size

            prefs.edit()
                .putString(KEY_DATE, todayKey)
                .putInt(KEY_CHALLENGE_INDEX, templateIndex)
                .putInt(KEY_PROGRESS, 0)
                .putBoolean(KEY_COMPLETED, false)
                .putBoolean(KEY_CLAIMED, false)
                .apply()
        }

        val index = prefs.getInt(KEY_CHALLENGE_INDEX, 0).coerceIn(0, CHALLENGE_POOL.size - 1)
        val template = CHALLENGE_POOL[index]
        val progress = prefs.getInt(KEY_PROGRESS, 0)
        val isCompleted = prefs.getBoolean(KEY_COMPLETED, false)
        val isClaimed = prefs.getBoolean(KEY_CLAIMED, false)

        return DailyChallenge(
            id = "${todayKey}_$index",
            dateKey = todayKey,
            title = template.title,
            gameTitle = template.gameTitle,
            gameTypeTag = template.gameTypeTag,
            gameIcon = template.gameIcon,
            goalDescription = template.goalDescription,
            targetScore = template.targetScore,
            currentScore = progress,
            rewardPoints = template.rewardPoints,
            rewardCoins = template.rewardCoins,
            isCompleted = isCompleted || progress >= template.targetScore,
            isClaimed = isClaimed,
            difficulty = template.difficulty,
            tip = template.tip
        )
    }

    /**
     * Reports score made in any game, updates daily progress if matched.
     * Returns true if challenge just got completed!
     */
    fun reportGameScore(gameType: String, score: Int): Boolean {
        val current = getTodayChallenge()
        if (current.isCompleted) return false

        val matches = when {
            gameType.contains("أتوبيس", ignoreCase = true) && current.gameTypeTag == "autobus" -> true
            gameType.contains("5 ثواني", ignoreCase = true) && current.gameTypeTag == "five_seconds" -> true
            gameType.contains("التابو", ignoreCase = true) && current.gameTypeTag == "taboo" -> true
            gameType.contains("بدون كلام", ignoreCase = true) && current.gameTypeTag == "bedoon_kalam" -> true
            gameType.contains("المثل", ignoreCase = true) && current.gameTypeTag == "proverbs" -> true
            gameType.contains("المليون", ignoreCase = true) && current.gameTypeTag == "millionaire" -> true
            (gameType.contains("الرابط", ignoreCase = true) || gameType.contains("أربع صور", ignoreCase = true)) && current.gameTypeTag == "four_clues" -> true
            gameType.contains("صباحو", ignoreCase = true) && current.gameTypeTag == "sabaho" -> true
            gameType.contains("30 ثانية", ignoreCase = true) && current.gameTypeTag == "thirty_sec" -> true
            else -> false
        }

        if (matches) {
            val newProgress = if (current.gameTypeTag == "millionaire" || current.gameTypeTag == "autobus" || current.gameTypeTag == "sabaho") {
                maxOf(current.currentScore, score)
            } else {
                current.currentScore + 1
            }

            val newlyCompleted = newProgress >= current.targetScore && !current.isCompleted

            prefs.edit()
                .putInt(KEY_PROGRESS, newProgress)
                .putBoolean(KEY_COMPLETED, newlyCompleted || current.isCompleted)
                .apply()

            return newlyCompleted
        }
        return false
    }

    fun claimReward(): Pair<Int, Int>? {
        val current = getTodayChallenge()
        if (current.isCompleted && !current.isClaimed) {
            prefs.edit().putBoolean(KEY_CLAIMED, true).apply()
            return Pair(current.rewardPoints, current.rewardCoins)
        }
        return null
    }

    fun rerollChallenge(): DailyChallenge {
        val currentIndex = prefs.getInt(KEY_CHALLENGE_INDEX, 0)
        val nextIndex = (currentIndex + 1) % CHALLENGE_POOL.size
        val todayKey = getTodayDateKey()

        prefs.edit()
            .putString(KEY_DATE, todayKey)
            .putInt(KEY_CHALLENGE_INDEX, nextIndex)
            .putInt(KEY_PROGRESS, 0)
            .putBoolean(KEY_COMPLETED, false)
            .putBoolean(KEY_CLAIMED, false)
            .apply()

        return getTodayChallenge()
    }

    fun isReminderEnabled(): Boolean = prefs.getBoolean(KEY_REMINDER_ENABLED, true)

    fun setReminderEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_REMINDER_ENABLED, enabled).apply()
        if (enabled) {
            val challenge = getTodayChallenge()
            StadiumNotificationManager(context).scheduleDailyChallengeReminder(
                challengeTitle = challenge.title,
                gameName = challenge.gameTitle,
                targetDesc = challenge.goalDescription
            )
        }
    }
}
