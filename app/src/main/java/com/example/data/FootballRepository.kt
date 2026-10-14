package com.example.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

import com.example.data.supabase.SupabaseClient
import com.example.data.supabase.SupabaseScore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class FootballRepository(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("malab_tahaddi_prefs", Context.MODE_PRIVATE)
    val supabaseClient = SupabaseClient()

    private val _userProfile = MutableStateFlow(loadProfile())
    val userProfile: StateFlow<UserProfile> = _userProfile.asStateFlow()

    private fun loadProfile(): UserProfile {
        return UserProfile(
            playerName = prefs.getString("player_name", "كابتن الملعب") ?: "كابتن الملعب",
            favoriteClub = prefs.getString("fav_club", "ريال مدريد") ?: "ريال مدريد",
            avatarEmoji = prefs.getString("avatar_emoji", "👑") ?: "👑",
            points = prefs.getInt("points", 1450),
            coins = prefs.getInt("coins", 420),
            gamesPlayed = prefs.getInt("games_played", 32),
            winRate = prefs.getInt("win_rate", 82),
            currentStreak = prefs.getInt("current_streak", 5),
            bestStreak = prefs.getInt("best_streak", 16),
            thirtySecondsHighScore = prefs.getInt("thirty_score", 19),
            sabahoTrophies = prefs.getInt("sabaho_trophies", 6),
            unlockedHintsMastery = prefs.getInt("hints_mastery", 14),
            careerGuessedCount = prefs.getInt("career_guessed", 18)
        )
    }

    fun updateProfile(newName: String, newClub: String, newAvatar: String) {
        val current = _userProfile.value
        val updated = current.copy(
            playerName = newName.ifBlank { current.playerName },
            favoriteClub = newClub.ifBlank { current.favoriteClub },
            avatarEmoji = newAvatar.ifBlank { current.avatarEmoji }
        )
        _userProfile.value = updated
        prefs.edit()
            .putString("player_name", updated.playerName)
            .putString("fav_club", updated.favoriteClub)
            .putString("avatar_emoji", updated.avatarEmoji)
            .apply()
    }

    fun addPointsAndCoins(earnedPoints: Int, earnedCoins: Int, gameTitle: String = "تحدي كروي") {
        val current = _userProfile.value
        val newPoints = (current.points + earnedPoints).coerceAtLeast(0)
        val newCoins = (current.coins + earnedCoins).coerceAtLeast(0)
        val newStreak = if (earnedPoints > 0) current.currentStreak + 1 else 0
        val newBestStreak = maxOf(newStreak, current.bestStreak)
        val newGamesPlayed = current.gamesPlayed + 1

        val newHistoryItem = MatchHistoryItem(
            id = "m_${System.currentTimeMillis()}",
            gameTitle = gameTitle,
            earnedPoints = earnedPoints,
            earnedCoins = earnedCoins,
            resultBadge = if (earnedPoints >= 40) "فوز ساحق 🥇" else "فوز تكتيكي ⚽",
            timeAgo = "الآن"
        )
        val updatedHistory = listOf(newHistoryItem) + current.matchHistory.take(12)

        val updated = current.copy(
            points = newPoints,
            coins = newCoins,
            currentStreak = newStreak,
            bestStreak = newBestStreak,
            gamesPlayed = newGamesPlayed,
            matchHistory = updatedHistory
        )
        _userProfile.value = updated

        prefs.edit()
            .putInt("points", newPoints)
            .putInt("coins", newCoins)
            .putInt("current_streak", newStreak)
            .putInt("best_streak", newBestStreak)
            .putInt("games_played", newGamesPlayed)
            .apply()

        // Sync with Supabase cloud database if configured
        CoroutineScope(Dispatchers.IO).launch {
            try {
                supabaseClient.postScore(
                    SupabaseScore(
                        playerName = updated.playerName,
                        points = updated.points,
                        coins = updated.coins,
                        levelTitle = updated.levelTitle,
                        favoriteClub = updated.favoriteClub
                    )
                )
            } catch (_: Exception) {}
        }
    }

    fun updateThirtySecondsHighScore(score: Int) {
        val current = _userProfile.value
        if (score > current.thirtySecondsHighScore) {
            val updated = current.copy(thirtySecondsHighScore = score)
            _userProfile.value = updated
            prefs.edit().putInt("thirty_score", score).apply()
        }
    }

    fun rewardFromAd() {
        val current = _userProfile.value
        val updated = current.copy(
            coins = current.coins + 75,
            availableLifelines = AvailableLifelines(fiftyFifty = true, skipQuestion = true, extraTime = true)
        )
        _userProfile.value = updated
        prefs.edit().putInt("coins", updated.coins).apply()
    }

    // Smart Arabic Name Normalization
    fun normalizeArabic(text: String): String {
        return text.trim()
            .lowercase()
            .replace(Regex("[\\p{Mn}]"), "") // remove tashkeel
            .replace('أ', 'ا')
            .replace('إ', 'ا')
            .replace('آ', 'ا')
            .replace('ة', 'ه')
            .replace('ى', 'ي')
            .replace("عبد ", "عبد")
            .replace(" ", "")
    }

    fun checkPlayerMatch(input: String, acceptedList: List<String>): String? {
        val normalizedInput = normalizeArabic(input)
        if (normalizedInput.length < 3) return null
        return acceptedList.firstOrNull { accepted ->
            val normAccepted = normalizeArabic(accepted)
            normAccepted == normalizedInput ||
            normAccepted.contains(normalizedInput) ||
            normalizedInput.contains(normAccepted)
        }
    }

    // ----------------------------------------------------
    // DATA SOURCE: 30 Seconds Speed Quiz Questions
    // ----------------------------------------------------
    fun getThirtySecondsQuestions(): List<ThirtySecondsQuestion> {
        return listOf(
            ThirtySecondsQuestion(
                id = "ts_1",
                question = "من هو الهداف التاريخي لبطولة كأس العالم لكرة القدم؟",
                options = listOf("ميروسلاف كلوزه", "رونالدو البرازيلي", "ليونيل ميسي", "بيليه"),
                correctIndex = 0,
                category = "كأس العالم"
            ),
            ThirtySecondsQuestion(
                id = "ts_2",
                question = "أي نادٍ أحرز لقب دوري أبطال أوروبا في موسم 2023–2024؟",
                options = listOf("ريال مدريد", "بوروسيا دورتموند", "مانشستر سيتي", "بايرن ميونخ"),
                correctIndex = 0,
                category = "دوري الأبطال"
            ),
            ThirtySecondsQuestion(
                id = "ts_3",
                question = "كم عدد كرات الذهب (Ballon d'Or) التي حققها الأسطورة ليونيل ميسي؟",
                options = listOf("8 كرات", "7 كرات", "5 كرات", "6 كرات"),
                correctIndex = 0,
                category = "جوائز فردية"
            ),
            ThirtySecondsQuestion(
                id = "ts_4",
                question = "من هو أول منتخب عربي وإفريقي يتأهل إلى نصف نهائي كأس العالم؟",
                options = listOf("المغرب", "الجزائر", "مصر", "تونس"),
                correctIndex = 0,
                category = "المونديال العربي"
            ),
            ThirtySecondsQuestion(
                id = "ts_5",
                question = "ما هو لقب نادي تشيلسي الإنجليزي الشهير؟",
                options = listOf("البلوز (The Blues)", "الريدز", "المدفعجية", "الشياطين الحمر"),
                correctIndex = 0,
                category = "ألقاب الأندية"
            ),
            ThirtySecondsQuestion(
                id = "ts_6",
                question = "في أي عام حقق منتخب الجزائر لقب كأس أمم إفريقيا في القاهرة؟",
                options = listOf("2019", "2017", "2021", "2015"),
                correctIndex = 0,
                category = "أمم إفريقيا"
            ),
            ThirtySecondsQuestion(
                id = "ts_7",
                question = "من هو الحارس المغربي الفائز بجائزة زامورا وأفضل حارس في الدوري الإسباني؟",
                options = listOf("ياسين بونو", "منير المحمدي", "عصام الحضري", "محمد الشناوي"),
                correctIndex = 0,
                category = "حراس مرمى"
            ),
            ThirtySecondsQuestion(
                id = "ts_8",
                question = "كم مرة توج النادي الأهلي المصري بلقب دوري أبطال إفريقيا؟",
                options = listOf("12 مرة", "10 مرات", "8 مرات", "14 مرة"),
                correctIndex = 0,
                category = "إفريقيا للأندية"
            ),
            ThirtySecondsQuestion(
                id = "ts_9",
                question = "من سجل هدف 'يد الرب' الشهير في مونديال 1986؟",
                options = listOf("دييغو مارادونا", "بيليه", "زيكو", "فالدانو"),
                correctIndex = 0,
                category = "تاريخ الكرة"
            ),
            ThirtySecondsQuestion(
                id = "ts_10",
                question = "من هو الهداف التاريخي لنادي ريال مدريد؟",
                options = listOf("كريستيانو رونالدو", "كريم بنزيما", "راؤول غونزاليس", "دي ستيفانو"),
                correctIndex = 0,
                category = "ريال مدريد"
            ),
            ThirtySecondsQuestion(
                id = "ts_11",
                question = "أي منتخب فاز بأول نسخة من كأس العالم عام 1930؟",
                options = listOf("الأوروغواي", "البرازيل", "الأرجنتين", "إيطاليا"),
                correctIndex = 0,
                category = "تاريخ المونديال"
            ),
            ThirtySecondsQuestion(
                id = "ts_12",
                question = "من هو النجم المصري الملقب بفخر العرب وهداف ليفربول؟",
                options = listOf("محمد صلاح", "محمود حسن تريزيجيه", "محمد النني", "عمر مرموش"),
                correctIndex = 0,
                category = "نجوم العرب"
            ),
            ThirtySecondsQuestion(
                id = "ts_13",
                question = "ما هي المدينة التي استضافت نهائي دوري أبطال أوروبا 2005 الشهير بمعجزة إسطنبول؟",
                options = listOf("إسطنبول", "أثينا", "مدريد", "لندن"),
                correctIndex = 0,
                category = "نهائيات تاريخية"
            ),
            ThirtySecondsQuestion(
                id = "ts_14",
                question = "أي لاعب يحمل الرقم القياسي كأكثر من سجل أهداف دولية في تاريخ كرة القدم؟",
                options = listOf("كريستيانو رونالدو", "علي دائي", "ليونيل ميسي", "بيليه"),
                correctIndex = 0,
                category = "أرقام قياسية"
            ),
            ThirtySecondsQuestion(
                id = "ts_15",
                question = "ما هو الملعب التاريخي لنادي برشلونة الكتالوني؟",
                options = listOf("كامب نو", "سانتياغو برنابيو", "مستايا", "واندا ميتروبوليتانو"),
                correctIndex = 0,
                category = "ملاعب"
            )
        )
    }

    // ----------------------------------------------------
    // DATA SOURCE: Player Mention Challenges (Mode B)
    // ----------------------------------------------------
    fun getPlayerMentionChallenges(): List<PlayerMentionChallenge> {
        return listOf(
            PlayerMentionChallenge(
                id = "pm_rm",
                clubOrCountry = "ريال مدريد",
                type = "نادي",
                badgeEmoji = "👑",
                acceptedPlayers = listOf(
                    "كريستيانو رونالدو", "رونالدو", "بنزيما", "كريم بنزيما", "مودريتش", "لوكا مودريتش",
                    "كروس", "توني كروس", "راموس", "سيرجيو راموس", "فينيسيوس", "فينيسيوس جونيور",
                    "بيلينجهام", "جود بيلينجهام", "مبابي", "كيليان مبابي", "زيدان", "زين الدين زيدان",
                    "كاسيميرو", "مارسيلو", "كورتوا", "تيبو كورتوا", "راؤول", "كاسياس", "ايكر كاسياس",
                    "روبيرتو كارلوس", "فيغو", "لويس فيغو", "بيبي", "كارفخال", "رودريغو", "فالفيردي"
                )
            ),
            PlayerMentionChallenge(
                id = "pm_barca",
                clubOrCountry = "برشلونة",
                type = "نادي",
                badgeEmoji = "🔵🔴",
                acceptedPlayers = listOf(
                    "ميسي", "ليونيل ميسي", "تشافي", "انييستا", "بوسكيتس", "سواريز", "لويس سواريز",
                    "نيمار", "نيمار جونيور", "رونالدينيو", "بويول", "كارليس بويول", "بيكيه", "جيرارد بيكيه",
                    "ليفاندوفسكي", "روبرت ليفاندوفسكي", "لامين يامال", "يامال", "بيدري", "جافي",
                    "شتيغن", "تير شتيغن", "داني الفيس", "هنري", "تيري هنري", "ريفالدو", "كرويف"
                )
            ),
            PlayerMentionChallenge(
                id = "pm_morocco",
                clubOrCountry = "منتخب المغرب",
                type = "منتخب",
                badgeEmoji = "🇲🇦",
                acceptedPlayers = listOf(
                    "ياسين بونو", "بونو", "اشرف حكيمي", "حكيمي", "حكيم زياش", "زياش", "يوسف النصيري", "النصيري",
                    "سفيان امرابط", "امرابط", "عز الدين اوناحي", "اوناحي", "سفيان بوفال", "بوفال", "غانم سايس",
                    "سايس", "نايف اكرد", "اكرد", "عبد الرزاق حمد الله", "حمد الله", "ابراهيم دياز", "دياز",
                    "نور الدين امرابط", "مصطفى حجي", "احمد فرس", "بادو الزاكي", "الزاكي"
                )
            ),
            PlayerMentionChallenge(
                id = "pm_egypt",
                clubOrCountry = "منتخب مصر",
                type = "منتخب",
                badgeEmoji = "🇪🇬",
                acceptedPlayers = listOf(
                    "محمد صلاح", "صلاح", "محمد ابو تريكة", "ابو تريكة", "عصام الحضري", "الحضري", "احمد حسن",
                    "حسام حسن", "محمد الشناوي", "الشناوي", "محمد النني", "النني", "محمود تريزيجيه", "تريزيجيه",
                    "مصطفى محمد", "عمر مرموش", "مرموش", "عمرو زكي", "محمد بركات", "احمد فتحي", "وائل جمعة",
                    "عماد متعب", "حسني عبد ربه", "محمود الخطيب", "الخطيب", "حسن شحاتة", "احمد حجازي"
                )
            ),
            PlayerMentionChallenge(
                id = "pm_mancity",
                clubOrCountry = "مانشستر سيتي",
                type = "نادي",
                badgeEmoji = "🩵",
                acceptedPlayers = listOf(
                    "دي بروين", "كيفين دي بروين", "هالاند", "ايرلينغ هالاند", "رياض محرز", "محرز",
                    "برناردو سيلفا", "سيلفا", "رودري", "فودين", "فيل فودين", "اغويرو", "سيرجيو اغويرو",
                    "كومباني", "يايا توريه", "دافيد سيلفا", "غوندوغان", "ستونز", "روبن دياز", "ايدرسون"
                )
            ),
            PlayerMentionChallenge(
                id = "pm_saudi",
                clubOrCountry = "منتخب السعودية",
                type = "منتخب",
                badgeEmoji = "🇸🇦",
                acceptedPlayers = listOf(
                    "سالم الدوسري", "الدوسري", "سامي الجابر", "الجابر", "ماجد عبد الله", "سعيد العويران",
                    "العويران", "ياسر القحطاني", "القحطاني", "محمد الدعيع", "الدعيع", "سلمان الفرج", "الفرج",
                    "صالح الشهري", "الشهري", "فهد المولد", "محمد كنو", "كنو", "سعود عبد الحميد", "عبد الحميد"
                )
            )
        )
    }

    // ----------------------------------------------------
    // DATA SOURCE: Sabaho Challenge Questions (Multi-Tier)
    // ----------------------------------------------------
    fun getSabahoQuestions(): List<SabahoQuestion> {
        return listOf(
            SabahoQuestion(
                id = "sb_e1",
                difficulty = Difficulty.EASY,
                question = "كم لاعباً في التشكيلة الأساسية لفريق كرة القدم داخل الملعب؟",
                options = listOf("11 لاعباً", "10 لاعبين", "9 لاعبين", "12 لاعباً"),
                correctIndex = 0,
                explanation = "الفريق يتكون من 11 لاعباً أساسياً بما فيهم حارس المرمى."
            ),
            SabahoQuestion(
                id = "sb_e2",
                difficulty = Difficulty.EASY,
                question = "ما هي الدولة الأكثر تتويجاً ببطولة كأس العالم عبر التاريخ؟",
                options = listOf("البرازيل (5 ألقاب)", "ألمانيا (4 ألقاب)", "إيطاليا (4 ألقاب)", "الأرجنتين (3 ألقاب)"),
                correctIndex = 0,
                explanation = "البرازيل تتصدر بـ 5 كؤوس عالم (1958، 1962، 1970، 1994، 2002)."
            ),
            SabahoQuestion(
                id = "sb_e3",
                difficulty = Difficulty.EASY,
                question = "ما هو لقب مهاجم مانشستر سيتي النرويجي إيرلينغ هالاند؟",
                options = listOf("الفيكينغ المدمر", "البرغوث", "الدون", "الساحر"),
                correctIndex = 0,
                explanation = "يُلقب هالاند بالفيكينغ لقوته البدنية وأصوله النرويجية."
            ),
            SabahoQuestion(
                id = "sb_m1",
                difficulty = Difficulty.MEDIUM,
                question = "من هو المدرب الذي قاد منتخب إسبانيا للتتويج بمونديال 2010 في جنوب إفريقيا؟",
                options = listOf("فيسنتي ديل بوسكي", "لويس أراغونيس", "بيب غوارديولا", "لويس إنريكي"),
                correctIndex = 0,
                explanation = "ديل بوسكي قاد الماتادور الإسباني للقب المونديالي الأول في تاريخهم."
            ),
            SabahoQuestion(
                id = "sb_m2",
                difficulty = Difficulty.MEDIUM,
                question = "أي لاعب سجل هدف الفوز لمنتخب ألمانيا في نهائي كأس العالم 2014 ضد الأرجنتين؟",
                options = listOf("ماريو غوتزه", "توماس مولر", "توني كروس", "باستيان شفاينشتايغر"),
                correctIndex = 0,
                explanation = "سجل غوتزه الهدف القاتل في الدقيقة 113 من الشوط الإضافي الثاني."
            ),
            SabahoQuestion(
                id = "sb_m3",
                difficulty = Difficulty.MEDIUM,
                question = "من هو هداف الدوري الإنجليزي الممتاز التاريخي بأكثر من 260 هدفاً؟",
                options = listOf("آلان شيرار", "واين روني", "هاري كين", "سيرجيو أغويرو"),
                correctIndex = 0,
                explanation = "الأسطورة آلان شيرار يتربع على عرش البريميرليغ التاريخي بـ 260 هدفاً."
            ),
            SabahoQuestion(
                id = "sb_h1",
                difficulty = Difficulty.HARD,
                question = "أي نادٍ إيطالي فاز بلقب دوري أبطال أوروبا في موسم 2009–2010 تحت قيادة جوزيه مورينيو محققاً الثلاثية التاريخية؟",
                options = listOf("إنتر ميلان", "إيه سي ميلان", "يوفنتوس", "روما"),
                correctIndex = 0,
                explanation = "إنتر ميلان فاز بالدوري والكأس ودوري الأبطال عام 2010 بقيادة مورينيو."
            ),
            SabahoQuestion(
                id = "sb_h2",
                difficulty = Difficulty.HARD,
                question = "من كان حارس منتخب فرنسا الأساسي المتوج بكأس العالم 1998؟",
                options = listOf("فابيان بارتيز", "هوغو لوريس", "غريغوري كوبيه", "برنارد لاما"),
                correctIndex = 0,
                explanation = "فابيان بارتيز الشهير الذي كان بلان يقبّل رأسه قبل كل مباراة."
            ),
            SabahoQuestion(
                id = "sb_h3",
                difficulty = Difficulty.HARD,
                question = "في أي عام تأسس نادي ريال مدريد الملكي؟",
                options = listOf("1902", "1899", "1905", "1910"),
                correctIndex = 0,
                explanation = "تأسس نادي ريال مدريد في 6 مارس عام 1902."
            )
        )
    }

    // ----------------------------------------------------
    // DATA SOURCE: Mystery Player ("العب مع سري")
    // ----------------------------------------------------
    fun getMysteryPlayers(): List<MysteryPlayer> {
        return listOf(
            MysteryPlayer(
                id = "mp_1",
                playerName = "محمد صلاح",
                hints = listOf(
                    MysteryHint("الجنسية", "مصري 🇪🇬", "🌍"),
                    MysteryHint("الدوري والمركز", "الدوري الإنجليزي الممتاز • جناح أيمن", "🏃"),
                    MysteryHint("النادي الحالي", "ليفربول (The Reds)", "🔴"),
                    MysteryHint("رقم القميص", "الرقم 11 الشهير", "🔢"),
                    MysteryHint("اللقب والإنجاز", "الملك المصري (Egyptian King) • بطل دوري الأبطال والبريميرليغ", "👑")
                ),
                options = listOf("محمد صلاح", "عمر مرموش", "رياض محرز", "محمود تريزيجيه")
            ),
            MysteryPlayer(
                id = "mp_2",
                playerName = "لوكا مودريتش",
                hints = listOf(
                    MysteryHint("الجنسية", "كرواتي 🇭🇷", "🌍"),
                    MysteryHint("الدوري والمركز", "الدوري الإسباني (لاليغا) • مايسترو خط الوسط", "🏃"),
                    MysteryHint("النادي", "ريال مدريد الإسباني", "⚪"),
                    MysteryHint("رقم القميص", "الرقم 10 الأسطوري", "🔢"),
                    MysteryHint("اللقب والإنجاز", "الفائز بالكرة الذهبية 2018 و6 ألقاب دوري أبطال أوروبا", "🏆")
                ),
                options = listOf("لوكا مودريتش", "توني كروس", "إيفان راكيتيتش", "ماتيو كوفاسيتش")
            ),
            MysteryPlayer(
                id = "mp_3",
                playerName = "ياسين بونو",
                hints = listOf(
                    MysteryHint("الجنسية", "مغربي 🇲🇦", "🌍"),
                    MysteryHint("المركز والدوري", "حارس مرمى عملاق • دوري روشن السعودي", "🧤"),
                    MysteryHint("الأندية السابقة والحالية", "إشبيلية الإسباني ⬅️ الهلال السعودي", "🛡️"),
                    MysteryHint("رقم القميص", "الرقم 37 الشهير", "🔢"),
                    MysteryHint("الإنجاز الخارق", "بطل الدوري الأوروبي وبطل الملحمة المغربية في مونديال قطر 2022", "⭐")
                ),
                options = listOf("ياسين بونو", "منير المحمدي", "إدوارد ميندي", "تيبو كورتوا")
            ),
            MysteryPlayer(
                id = "mp_4",
                playerName = "كيليان مبابي",
                hints = listOf(
                    MysteryHint("الجنسية", "فرنسي 🇫🇷", "🌍"),
                    MysteryHint("المركز والسرعة", "مهاجم صاروخي سريع وخاطف", "⚡"),
                    MysteryHint("المحطة السابقة والجديدة", "موناكو ⬅️ باريس سان جيرمان ⬅️ ريال مدريد", "🏟️"),
                    MysteryHint("رقم القميص", "الرقم 9 في مدريد والرقم 10 مع فرنسا", "🔢"),
                    MysteryHint("الإنجاز التاريخي", "بطل كأس العالم 2018 وهاتريك في نهائي مونديال 2022", "🎯")
                ),
                options = listOf("كيليان مبابي", "عثمان ديمبيلي", "أنطوان غريزمان", "فينيسيوس جونيور")
            ),
            MysteryPlayer(
                id = "mp_5",
                playerName = "كريم بنزيما",
                hints = listOf(
                    MysteryHint("الجنسية والأصل", "فرنسي من أصول جزائرية 🇫🇷", "🌍"),
                    MysteryHint("المركز واللقب", "رأس حربة هداف • الحكومة KB9", "🎯"),
                    MysteryHint("الأندية", "أولمبيك ليون ⬅️ ريال مدريد ⬅️ الاتحاد السعودي", "🐅"),
                    MysteryHint("رقم القميص", "الرقم 9 الكلاسيكي", "🔢"),
                    MysteryHint("الإنجاز", "الكرة الذهبية 2022 وخمسة ألقاب دوري أبطال أوروبا", "🥇")
                ),
                options = listOf("كريم بنزيما", "روبرت ليفاندوفسكي", "ألكسندر ميتروفيتش", "رياض محرز")
            )
        )
    }

    // ----------------------------------------------------
    // DATA SOURCE: Career Path Challenge ("خمّن اللاعب من مسيرته")
    // ----------------------------------------------------
    fun getCareerChallenges(): List<CareerChallenge> {
        return listOf(
            CareerChallenge(
                id = "cp_1",
                playerName = "زلاتان إبراهيموفيتش",
                position = "مهاجم صريح",
                nationality = "السويد",
                nationalityFlag = "🇸🇪",
                careerTimeline = listOf(
                    CareerClub("مالمو", "1999–2001", "🇸🇪", "47 مباراة • 18 هدف"),
                    CareerClub("أياكس أمستردام", "2001–2004", "🇳🇱", "110 مباراة • 48 هدف"),
                    CareerClub("يوفنتوس", "2004–2006", "🇮🇹", "92 مباراة • 26 هدف"),
                    CareerClub("إنتر ميلان", "2006–2009", "🇮🇹", "117 مباراة • 66 هدف"),
                    CareerClub("برشلونة", "2009–2010", "🇪🇸", "46 مباراة • 22 هدف"),
                    CareerClub("إيه سي ميلان", "2010–2012", "🇮🇹", "85 مباراة • 56 هدف"),
                    CareerClub("باريس سان جيرمان", "2012–2016", "🇫🇷", "180 مباراة • 156 هدف"),
                    CareerClub("مانشستر يونايتد", "2016–2018", "🇬🇧", "53 مباراة • 29 هدف"),
                    CareerClub("لوس أنجلوس غالاكسي", "2018–2019", "🇺🇸", "58 مباراة • 53 هدف"),
                    CareerClub("إيه سي ميلان (العودة)", "2020–2023", "🇮🇹", "78 مباراة • 37 هدف")
                ),
                options = listOf("زلاتان إبراهيموفيتش", "إدينسون كافاني", "هنريك لارسون", "ماكسويل")
            ),
            CareerChallenge(
                id = "cp_2",
                playerName = "رياض محرز",
                position = "جناح أيمن ساحر",
                nationality = "الجزائر",
                nationalityFlag = "🇩🇿",
                careerTimeline = listOf(
                    CareerClub("كامبير", "2009–2010", "🇫🇷", "27 مباراة • هدفين"),
                    CareerClub("لوهافر", "2010–2014", "🇫🇷", "66 مباراة • 10 أهداف"),
                    CareerClub("ليستر سيتي (معجزة البريميرليغ)", "2014–2018", "🇬🇧", "179 مباراة • 48 هدف"),
                    CareerClub("مانشستر سيتي (الثلاثية التاريخية)", "2018–2023", "🇬🇧", "236 مباراة • 78 هدف"),
                    CareerClub("الأهلي السعودي", "2023–حتى الآن", "🇸🇦", "مستمر في الإبداع")
                ),
                options = listOf("رياض محرز", "سفيان فيغولي", "ياسين براهيمي", "إسلام سليماني")
            ),
            CareerChallenge(
                id = "cp_3",
                playerName = "أشرف حكيمي",
                position = "ظهير أيمن طائر",
                nationality = "المغرب",
                nationalityFlag = "🇲🇦",
                careerTimeline = listOf(
                    CareerClub("ريال مدريد (كاستيا + الأول)", "2016–2018", "🇪🇸", "بطل دوري أبطال أوروبا"),
                    CareerClub("بوروسيا دورتموند (إعارة)", "2018–2020", "🇩🇪", "73 مباراة • 12 هدف"),
                    CareerClub("إنتر ميلان (بطل الكالتشيو)", "2020–2021", "🇮🇹", "45 مباراة • 7 أهداف"),
                    CareerClub("باريس سان جيرمان", "2021–حتى الآن", "🇫🇷", "بطل الدوري الفرنسي")
                ),
                options = listOf("أشرف حكيمي", "نصير مزراوي", "سفيان أمرابط", "عطية الله")
            ),
            CareerChallenge(
                id = "cp_4",
                playerName = "كريستيانو رونالدو",
                position = "الهداف التاريخي للساحرة المستديرة",
                nationality = "البرتغال",
                nationalityFlag = "🇵🇹",
                careerTimeline = listOf(
                    CareerClub("سبورتينغ لشبونة", "2002–2003", "🇵🇹", "31 مباراة • 5 أهداف"),
                    CareerClub("مانشستر يونايتد (الحقبة الأولى)", "2003–2009", "🇬🇧", "292 مباراة • 118 هدف"),
                    CareerClub("ريال مدريد (العصر الذهبي)", "2009–2018", "🇪🇸", "438 مباراة • 450 هدف"),
                    CareerClub("يوفنتوس", "2018–2021", "🇮🇹", "134 مباراة • 101 هدف"),
                    CareerClub("مانشستر يونايتد (العودة)", "2021–2022", "🇬🇧", "54 مباراة • 27 هدف"),
                    CareerClub("النصر السعودي", "2023–حتى الآن", "🇸🇦", "قائد وهداف الفريق")
                ),
                options = listOf("كريستيانو رونالدو", "لويس ناني", "ريكاردو كواريسما", "برونو فيرنانديز")
            )
        )
    }

    // ----------------------------------------------------
    // DATA SOURCE: True or False Blitz ("صح أو غلط")
    // ----------------------------------------------------
    fun getTrueFalseQuestions(): List<TrueFalseQuestion> {
        return listOf(
            TrueFalseQuestion(
                id = "tf_1",
                statement = "فاز منتخب المغرب بالمركز الرابع في كأس العالم قطر 2022.",
                isTrue = true,
                explanation = "صحيح! أصبح المغرب أول منتخب عربي وإفريقي يبلغ المربع الذهبي للمونديال في إنجاز تاريخي.",
                category = "كأس العالم"
            ),
            TrueFalseQuestion(
                id = "tf_2",
                statement = "نادي آرسنال الإنجليزي حقق لقب دوري أبطال أوروبا مرة واحدة في تاريخه.",
                isTrue = false,
                explanation = "خطأ! آرسنال لم يفز بدوري أبطال أوروبا قط، وأفضل إنجازاته وصيف 2006 أمام برشلونة.",
                category = "دوري أبطال أوروبا"
            ),
            TrueFalseQuestion(
                id = "tf_3",
                statement = "ليونيل ميسي هو اللاعب الوحيد الذي حقق جائزة الكرة الذهبية 8 مرات.",
                isTrue = true,
                explanation = "صحيح! يتربع ميسي على العرش التاريخي للكرة الذهبية بواقع 8 كرات.",
                category = "جوائز فردية"
            ),
            TrueFalseQuestion(
                id = "tf_4",
                statement = "تقام بطولة كأس العالم كل 3 سنوات.",
                isTrue = false,
                explanation = "خطأ! تقام بطولة كأس العالم مرة كل 4 سنوات منذ انطلاقها عام 1930.",
                category = "قوانين وبطولات"
            ),
            TrueFalseQuestion(
                id = "tf_5",
                statement = "محمد صلاح هو الهداف التاريخي لنادي ليفربول في الدوري الإنجليزي الممتاز (البريميرليغ).",
                isTrue = true,
                explanation = "صحيح! تخطى محمد صلاح الأسطورة روبي فاولر ليصبح الهداف التاريخي للريدز في البريميرليغ.",
                category = "نجوم العرب"
            ),
            TrueFalseQuestion(
                id = "tf_6",
                statement = "نهائي كأس العالم 1994 بين البرازيل وإيطاليا انتهى بركلات الترجيح بدون أهداف في الأشواط الأصلية.",
                isTrue = true,
                explanation = "صحيح! كان أول نهائي مونديالي ينتهي بالتعادل 0-0 ويُحسم بركلات الترجيح وأضاع باجيو الركلة الأخيرة.",
                category = "تاريخ المونديال"
            ),
            TrueFalseQuestion(
                id = "tf_7",
                statement = "كريستيانو رونالدو لعب لصالح نادي بايرن ميونخ الألماني.",
                isTrue = false,
                explanation = "خطأ! رونالدو لم يلعب في الدوري الألماني؛ لعب لسبورتينغ، مانشستر يونايتد، ريال مدريد، يوفنتوس، والنصر.",
                category = "مسيرات اللاعبين"
            ),
            TrueFalseQuestion(
                id = "tf_8",
                statement = "الأهلي المصري هو النادي الأكثر تتويجاً بلقب دوري أبطال إفريقيا بـ 12 لقباً.",
                isTrue = true,
                explanation = "صحيح! الأهلي هو ملك القارة السمراء بدون منازع برصيد 12 لقباً إفريقياً تاريخياً.",
                category = "الكرة العربية"
            )
        )
    }

    // ----------------------------------------------------
    // DATA SOURCE: Event & Goal Ordering ("ترتيب الأحداث والأهداف")
    // ----------------------------------------------------
    fun getEventOrderChallenges(): List<EventOrderChallenge> {
        return listOf(
            EventOrderChallenge(
                id = "eo_1",
                title = "رتب نهائيات المونديال التاريخية من الأقدم إلى الأحدث",
                description = "اسحب أو حرك النهائيات لتضعها في تسلسلها الزمني الصحيح 🏆",
                items = listOf(
                    FootballEventItem("1", "نهائي مونديال إيطاليا 1990 (ألمانيا × الأرجنتين)", "1990", 1),
                    FootballEventItem("2", "نهائي مونديال فرنسا 1998 (فرنسا × البرازيل)", "1998", 2),
                    FootballEventItem("3", "نهائي مونديال كوريا واليابان 2002 (البرازيل × ألمانيا)", "2002", 3),
                    FootballEventItem("4", "نهائي مونديال قطر 2022 التاريخي (الأرجنتين × فرنسا)", "2022", 4)
                )
            ),
            EventOrderChallenge(
                id = "eo_2",
                title = "رتب ألقاب دوري أبطال أوروبا لريال مدريد تاريخياً",
                description = "رتب محطات العاشرة إلى الخامسة عشرة في مجد الملكي 👑",
                items = listOf(
                    FootballEventItem("1", "العاشرة (La Décima) بهدف راموس 92:48 أمام أتلتيكو", "2014", 1),
                    FootballEventItem("2", "الحادية عشرة بركلات الترجيح في ميلانو", "2016", 2),
                    FootballEventItem("3", "الثالثة عشرة بهدف بيل الخرافي في كييف", "2018", 3),
                    FootballEventItem("4", "الخامسة عشرة ضد دورتموند في ويمبلي", "2024", 4)
                )
            ),
            EventOrderChallenge(
                id = "eo_3",
                title = "رتب مسيرة أندية ليونيل ميسي عبر السنين",
                description = "من أكاديمية نيويلز إلى ميامي 🇦🇷",
                items = listOf(
                    FootballEventItem("1", "نيويلز أولد بويز في الأرجنتين", "1995–2000", 1),
                    FootballEventItem("2", "الفريق الأول لبرشلونة والمجد العالمي", "2004–2021", 2),
                    FootballEventItem("3", "باريس سان جيرمان في الدوري الفرنسي", "2021–2023", 3),
                    FootballEventItem("4", "إنتر ميامي في الدوري الأمريكي", "2023–حتى الآن", 4)
                )
            )
        )
    }

    // ----------------------------------------------------
    // DATA SOURCE: Leaderboard (لوحة المتصدرين)
    // ----------------------------------------------------
    fun getLeaderboard(): List<LeaderboardUser> {
        val user = _userProfile.value
        val userBadges = user.earnedBadges.filter { it.isUnlocked }.map { it.iconEmoji }

        val list = mutableListOf(
            LeaderboardUser(1, "عمر المدريدي", "👑", 4820, "أسطورة الملاعب", "ريال مدريد", listOf("🔥", "⏱️", "🏆", "⭐")),
            LeaderboardUser(2, "ياسين الأطلسي", "🇲🇦", 4350, "محترف دولي", "الوداد البيضاوي", listOf("🏆", "🕵️‍♂️", "⭐")),
            LeaderboardUser(3, "أحمد الفراعنة", "🇪🇬", 3910, "كابتن الملعب", "الأهلي", listOf("🔥", "⏱️", "🗺️")),
            LeaderboardUser(4, user.playerName, user.avatarEmoji, user.points, user.levelTitle, user.favoriteClub, userBadges, isCurrentUser = true),
            LeaderboardUser(5, "سعود الصقور", "🦅", 2150, "نجم الفريق", "الهلال", listOf("🔥", "🏆")),
            LeaderboardUser(6, "فهد الكتلوني", "⚡", 1980, "نجم الفريق", "برشلونة", listOf("⏱️", "⭐")),
            LeaderboardUser(7, "حمزة الجزائري", "🇩🇿", 1750, "لاعب صاعد", "مولودية الجزائر", listOf("🗺️", "🔥")),
            LeaderboardUser(8, "خالد التونسي", "🇹🇳", 1420, "لاعب صاعد", "الترجي", listOf("🏆")),
            LeaderboardUser(9, "زيدان الصغير", "⚽", 1210, "لاعب صاعد", "يوفنتوس", listOf("⏱️")),
            LeaderboardUser(10, "محمود التكتيكي", "📋", 980, "مشجع هاوٍ", "ليفربول", listOf("⭐"))
        )
        return list.sortedByDescending { it.points }.mapIndexed { index, item ->
            item.copy(rank = index + 1)
        }
    }

    // ----------------------------------------------------
    // DATA SOURCE: Real-Time PvP Match ("المواجهة المباشرة")
    // ----------------------------------------------------
    fun getPvPQuestions(): List<PvPQuestion> {
        return listOf(
            PvPQuestion(
                id = "pvp_1",
                question = "من هو المنتخب الأكثر تتويجاً ببطولة كأس العالم في التاريخ؟",
                options = listOf("البرازيل (5 ألقاب) 🇧🇷", "ألمانيا (4 ألقاب) 🇩🇪", "إيطاليا (4 ألقاب) 🇮🇹", "الأرجنتين (3 ألقاب) 🇦🇷"),
                correctIndex = 0
            ),
            PvPQuestion(
                id = "pvp_2",
                question = "في أي عام حقق ليستر سيتي معجزته الكروية وفاز بالدوري الإنجليزي الممتاز؟",
                options = listOf("2014", "2016", "2018", "2019"),
                correctIndex = 1
            ),
            PvPQuestion(
                id = "pvp_3",
                question = "من هو الهداف التاريخي لبطولة دوري أبطال أوروبا برصيد 140 هدفاً؟",
                options = listOf("ليونيل ميسي", "روبرت ليفاندوفسكي", "كريستيانو رونالدو", "كريم بنزيما"),
                correctIndex = 2
            ),
            PvPQuestion(
                id = "pvp_4",
                question = "ما هو النادي العربي والآسيوي الملقب بـ 'زعيم آسيا' وتوج بدوري أبطال آسيا 4 مرات؟",
                options = listOf("الهلال السعودي 🇸🇦", "العين الإماراتي 🇦🇪", "السد القطري 🇶🇦", "الاتحاد السعودي 🇸🇦"),
                correctIndex = 0
            ),
            PvPQuestion(
                id = "pvp_5",
                question = "من اللاعب الذي فاز بجائزة أفضل لاعب في كأس العالم 2018 في روسيا؟",
                options = listOf("كيليان مبابي", "لوكا مودريتش", "أنطوان غريزمان", "إدين هازارد"),
                correctIndex = 1
            ),
            PvPQuestion(
                id = "pvp_6",
                question = "أي نادٍ فاز بالسداسية التاريخية لأول مرة في تاريخ كرة القدم عام 2009 بقيادة غوارديولا؟",
                options = listOf("بايرن ميونخ", "برشلونة", "ريال مدريد", "مانشستر سيتي"),
                correctIndex = 1
            ),
            PvPQuestion(
                id = "pvp_7",
                question = "كم عدد الأهداف التي سجلها الأسطورة بيليه المعترف بها رسمياً من الفيفا في المباريات الرسمية؟",
                options = listOf("757 هدفاً", "1000 هدف", "680 هدفاً", "820 هدفاً"),
                correctIndex = 0
            )
        )
    }

    fun getRandomOpponent(): PvPPlayer {
        val rivals = listOf(
            PvPPlayer("سالم الدوسري", "⚡", "الهلال السعودي"),
            PvPPlayer("كريم الجزائري", "🇩🇿", "باريس سان جيرمان"),
            PvPPlayer("عمر الأهلاوي", "🦅", "الأهلي المصري"),
            PvPPlayer("فارس البرشا", "🔵🔴", "برشلونة"),
            PvPPlayer("سلطان المدريدي", "👑", "ريال مدريد"),
            PvPPlayer("طارق المغاربي", "🦁", "الوداد البيضاوي")
        )
        return rivals.random()
    }
}

