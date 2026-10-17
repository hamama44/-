package com.example.data

// =============================================================================
// 1. تيك تاك تو كروي (FOOTBALL TIC-TAC-TOE / GRID)
// =============================================================================
data class TicTacToeCellCriteria(
    val rowName: String,
    val rowIcon: String,
    val colName: String,
    val colIcon: String,
    val validPlayers: List<String>
)

data class TicTacToeGridGame(
    val id: String,
    val title: String,
    val rowLabels: List<Pair<String, String>>, // (Name, Icon/Flag)
    val colLabels: List<Pair<String, String>>, // (Name, Icon/Flag)
    // 3x3 matrix: row, col -> list of valid player names
    val validPlayersMatrix: List<List<List<String>>>
)

// =============================================================================
// 2. المزاد الكروي (FOOTBALL AUCTION)
// =============================================================================
data class FootballAuctionRound(
    val id: String,
    val categoryTitle: String,
    val description: String,
    val icon: String,
    val minimumBid: Int = 3,
    val targetDurationSeconds: Int = 45,
    val validAnswers: List<String>
)

// =============================================================================
// 3. تشكيلة النهائي / اللاعب الناقص (MISSING XI)
// =============================================================================
data class FormationPosition(
    val id: String,
    val positionCode: String, // "GK", "RB", "CB", "LB", "DM", "CM", "RW", "LW", "ST"
    val xPercent: Float,      // 0f to 1f on pitch
    val yPercent: Float,      // 0f to 1f on pitch
    val playerName: String,
    val isMissing: Boolean = false,
    val hints: List<String> = emptyList()
)

data class MissingXIMatch(
    val id: String,
    val finalTitle: String,
    val teamName: String,
    val teamLogoEmoji: String,
    val opponentName: String,
    val scoreLine: String,
    val year: String,
    val formationName: String, // "4-3-3", "4-2-3-1", etc.
    val missingPlayerName: String,
    val missingPlayerJersey: Int,
    val missingPlayerNationality: String,
    val missingPlayerClubHints: List<String>,
    val positions: List<FormationPosition>
)

// =============================================================================
// 4. اكتشف الدخيل (ODD ONE OUT)
// =============================================================================
data class OddOneOutCard(
    val id: String,
    val name: String,
    val subtitle: String,
    val icon: String,
    val isOdd: Boolean
)

data class OddOneOutQuestion(
    val id: String,
    val title: String,
    val cards: List<OddOneOutCard>,
    val oddIndex: Int,
    val explanationReason: String,
    val categoryBadge: String
)

// =============================================================================
// 5. تحدي أرقام القمصان وقيم الانتقال (JERSEY & TRANSFERS)
// =============================================================================
data class JerseyQuestion(
    val id: String,
    val playerName: String,
    val teamOrCountry: String,
    val yearOrContext: String,
    val iconEmoji: String,
    val correctJerseyNumber: Int,
    val options: List<Int>,
    val funFact: String
)

data class TransferFeeQuestion(
    val id: String,
    val title: String,
    val playerA: String,
    val feeA: String, // e.g. "222 مليون يورو"
    val playerB: String,
    val feeB: String, // e.g. "180 مليون يورو"
    val whichIsHigher: String, // "A" or "B"
    val differenceText: String
)

// =============================================================================
// 6. مين الجاسوس؟ (THE SPY GAME / برا السالفة)
// =============================================================================
data class SpyLocation(
    val id: String,
    val locationName: String,
    val icon: String,
    val category: String, // "كروي", "تجمعات شعبية", "عالمي"
    val roles: List<String>,
    val sampleQuestions: List<String>
)

// =============================================================================
// 7. صراحة أو جرأة (TRUTH OR DARE)
// =============================================================================
enum class TruthOrDareType {
    TRUTH, DARE
}

data class TruthOrDareItem(
    val id: String,
    val type: TruthOrDareType,
    val category: String, // "كروي ⚽", "شخصي ومضحك 😂", "إحراج واعترافات 🤫", "تحديات جريئة 🔥"
    val content: String,
    val pointsReward: Int = 50
)

// =============================================================================
// 8. فقرات تحدي الثلاثين (نصوح): الجرس، سين جيم، مهنة لاعب، والتعويض
// =============================================================================
data class BuzzerQuestion(
    val id: String,
    val question: String,
    val correctAnswer: String,
    val acceptableKeywords: List<String>,
    val difficulty: String = "متوسط",
    val points: Int = 100
)

data class SinJeemQuestion(
    val id: String,
    val question: String,
    val options: List<String>,
    val correctIndex: Int,
    val explanation: String,
    val tournamentBadge: String
)

data class CareerStep(
    val years: String,
    val clubOrRole: String,
    val appearancesAndGoals: String,
    val achievement: String
)

data class PlayerCareerJob(
    val id: String,
    val playerName: String,
    val nationality: String,
    val position: String,
    val steps: List<CareerStep>, // revealed one by one to guess
    val funFact: String
)

data class ExtraTimeQuestion(
    val id: String,
    val question: String,
    val optionA: String,
    val optionB: String,
    val correctOption: String, // "A" or "B"
    val doublePoints: Int = 200
)

// =============================================================================
// MASTER REPOSITORY DATA PROVIDER
// =============================================================================
object NewGamesDataProvider {

    // -------------------------------------------------------------------------
    // 1. TIC-TAC-TOE GRIDS
    // -------------------------------------------------------------------------
    val ticTacToeGrids = listOf(
        TicTacToeGridGame(
            id = "grid_1",
            title = "عمالقة الليغا والبريميرليغ",
            rowLabels = listOf(
                "ريال مدريد" to "⚪",
                "برشلونة" to "🔵🔴",
                "بايرن ميونخ" to "🔴"
            ),
            colLabels = listOf(
                "مانشستر يونايتد" to "🔴👹",
                "باريس سان جيرمان" to "🗼",
                "الأرجنتين" to "🇦🇷"
            ),
            validPlayersMatrix = listOf(
                // Row 0: ريال مدريد
                listOf(
                    // Real Madrid x Man Utd
                    listOf("رونالدو", "كريستيانو رونالدو", "بيكهام", "دي ماريا", "فاران", "كاسيميرو", "أوين", "فان نيستلروي", "تشيتشاريتو", "هاينزه"),
                    // Real Madrid x PSG
                    listOf("راموس", "نافاس", "دي ماريا", "مبابي", "أسينسيو", "أنيلكا", "خيسي", "حكيمي"),
                    // Real Madrid x Argentina
                    listOf("دي ماريا", "هيغواين", "ريدوندو", "فالدانو", "غاراي", "سافيولا", "كامبياسو", "سولاري")
                ),
                // Row 1: برشلونة
                listOf(
                    // Barcelona x Man Utd
                    listOf("بيكهام", "بيكيه", "إبراهيموفيتش", "فيكتور فالديز", "لارسون", "بلان"),
                    // Barcelona x PSG
                    listOf("ميسي", "نيمار", "رونالدينيو", "إبراهيموفيتش", "ديمبيلي", "ألفيس", "داني ألفيس", "ماكسويل", "رافينيا"),
                    // Barcelona x Argentina
                    listOf("ميسي", "مارادونا", "ماسكيرانو", "أغويرو", "سافيولا", "ريكيلمي", "ميليتو")
                ),
                // Row 2: بايرن ميونخ
                listOf(
                    // Bayern x Man Utd
                    listOf("شفاينشتايغر", "هارغريفز", "سابيتزر", "دي ليخت", "مزراوي"),
                    // Bayern x PSG
                    listOf("كومان", "بيرنات", "لوكاس هيرنانديز", "ريناتو سانشيز", "شوبو موتينغ"),
                    // Bayern x Argentina
                    listOf("ديميكيليس", "سوسا")
                )
            )
        ),
        TicTacToeGridGame(
            id = "grid_2",
            title = "ديربيات وجنسيات عالمية",
            rowLabels = listOf(
                "تشيلسي" to "🔵🦁",
                "يوفنتوس" to "⚪⚫",
                "مانشستر سيتي" to "🩵"
            ),
            colLabels = listOf(
                "فرنسا" to "🇫🇷",
                "البرازيل" to "🇧🇷",
                "إيطاليا" to "🇮🇹"
            ),
            validPlayersMatrix = listOf(
                // Row 0: تشيلسي
                listOf(
                    listOf("كانتي", "مالودا", "أنيلكا", "ماكيليلي", "زوما", "جيرو", "غالاس", "نكونكو"),
                    listOf("ويليان", "أوسكار", "دافيد لويز", "تياغو سيلفا", "راميريز", "بيلبتي"),
                    listOf("زولا", "فيالي", "دي ماتيو", "جورجينيو", "إيمرسون")
                ),
                // Row 1: يوفنتوس
                listOf(
                    listOf("زيدان", "بوغبا", "تريزيغيه", "تورام", "ماتويدي", "إيفرا", "رابيو"),
                    listOf("داني ألفيس", "دوغلاس كوستا", "إيمرسون", "دانيلو", "أليكس ساندرو", "بريمر", "دييغو"),
                    listOf("بوفون", "كيليني", "بونوتشي", "دل بييرو", "بيرلو", "ماركيزيو", "بارزالي", "كييزا")
                ),
                // Row 2: مانشستر سيتي
                listOf(
                    listOf("نصري", "كليشي", "سانيا", "مانغالا", "أنيلكا", "ميندي"),
                    listOf("فرناندينيو", "إيدرسون", "خيسوس", "روبينيو", "إيلانو", "دانيلو"),
                    listOf("بالوتيللي", "دوناروما", "مانشيني")
                )
            )
        )
    )

    // -------------------------------------------------------------------------
    // 2. FOOTBALL AUCTION ROUNDS
    // -------------------------------------------------------------------------
    val auctionRounds = listOf(
        FootballAuctionRound(
            id = "auc_1",
            categoryTitle = "لاعبين ارتدوا قميص ريال مدريد وبرشلونة عبر التاريخ",
            description = "اذكر نجوم مثلوا قطبي الكلاسيكو الإسباني!",
            icon = "⚔️",
            minimumBid = 3,
            targetDurationSeconds = 45,
            validAnswers = listOf(
                "فيغو", "لويس فيغو", "رونالدو", "الظاهرة رونالدو", "لويس إنريكي", "مايكل لاودروب",
                "سافيولا", "خافيير سافيولا", "إيتو", "صامويل إيتو", "برند شوستر", "جورجي هاجي",
                "ألفونسو بيريز", "روبرت بروسينتشكي", "ألبرت سيلاديس"
            )
        ),
        FootballAuctionRound(
            id = "auc_2",
            categoryTitle = "لاعبين برازيليين حققوا لقب دوري أبطال أوروبا",
            description = "السحرة البرازيليين الذين رفعوا الكأس ذات الأذنين!",
            icon = "🇧🇷",
            minimumBid = 4,
            targetDurationSeconds = 45,
            validAnswers = listOf(
                "رونالدينيو", "كاكا", "نيمار", "مارسيلو", "كاسيميرو", "فينيسيوس", "رودريغو",
                "داني ألفيس", "روبرتو كارلوس", "ريفالدو", "لوسيو", "مايكون", "جوليو سيزار",
                "أليسون", "فيرمينو", "فابينيو", "تياغو سيلفا", "إيدرسون", "دافيد لويز", "دانييل ألفيس"
            )
        ),
        FootballAuctionRound(
            id = "auc_3",
            categoryTitle = "مدربين فازوا بلقب دوري أبطال أوروبا مرتين أو أكثر",
            description = "عباقرة التدريب أصحاب الألقاب المتعددة في الشامبيونزليغ!",
            icon = "👔",
            minimumBid = 3,
            targetDurationSeconds = 40,
            validAnswers = listOf(
                "كارلو أنشيلوتي", "أنشيلوتي", "بيب غوارديولا", "غوارديولا", "زين الدين زيدان", "زيدان",
                "جوزيه مورينيو", "مورينيو", "أليكس فيرغسون", "فيرغسون", "يورغن كلوب", "فيسنتي ديل بوسكي",
                "أريغو ساكي", "أوتو هيتسفيلد", "يوب هاينكس", "براين كلوف", "بوب بيزلي"
            )
        ),
        FootballAuctionRound(
            id = "auc_4",
            categoryTitle = "لاعبين أرجنتينيين لعبوا في الدوري الإنجليزي الممتاز",
            description = "التانغو الأرجنتيني على الأراضي البريطانية!",
            icon = "🇦🇷",
            minimumBid = 4,
            targetDurationSeconds = 45,
            validAnswers = listOf(
                "سيرجيو أغويرو", "أغويرو", "كارلوس تيفيز", "تيفيز", "خافيير ماسكيرانو", "ماسكيرانو",
                "أنخيل دي ماريا", "دي ماريا", "إيميليانو مارتينيز", "إنزو فيرنانديز", "ألكسيس ماك أليستر",
                "جوليان ألفاريز", "بابلو زاباليتا", "نيكولاس أوتاميندي", "خوان سيباستيان فيرون", "فيرون",
                "إيريك لاميلا", "مانويل لانزيني", "هرنان كريسبو"
            )
        ),
        FootballAuctionRound(
            id = "auc_5",
            categoryTitle = "أندية إنجليزية توجت بلقب البريميرليغ في عهده الحديث (منذ 1992)",
            description = "الأبطال السبعة في تاريخ الدوري الإنجليزي الممتاز!",
            icon = "🏆",
            minimumBid = 4,
            targetDurationSeconds = 35,
            validAnswers = listOf(
                "مانشستر يونايتد", "مانشستر سيتي", "تشيلسي", "أرسنال", "ليفربول", "ليستر سيتي", "بلاكبيرن روفرز"
            )
        )
    )

    // -------------------------------------------------------------------------
    // 3. MISSING XI (تشكيلة النهائي)
    // -------------------------------------------------------------------------
    val missingXIFinals = listOf(
        MissingXIMatch(
            id = "final_1",
            finalTitle = "نهائي دوري أبطال أوروبا 2019 (مدريد)",
            teamName = "ليفربول",
            teamLogoEmoji = "🔴",
            opponentName = "توتنهام هوتسبير (2-0)",
            scoreLine = "2 - 0",
            year = "2019",
            formationName = "4-3-3",
            missingPlayerName = "روبرتو فيرمينو",
            missingPlayerJersey = 9,
            missingPlayerNationality = "البرازيل 🇧🇷",
            missingPlayerClubHints = listOf("المهاجم الوهمي الذهبي", "رقم 9", "ثلاثي الرعب مع صلاح وماني"),
            positions = listOf(
                FormationPosition("p1", "GK", 0.5f, 0.88f, "أليسون بيكر"),
                FormationPosition("p2", "RB", 0.85f, 0.72f, "ترينت ألكسندر أرنولد"),
                FormationPosition("p3", "CB", 0.62f, 0.74f, "جويل ماتيب"),
                FormationPosition("p4", "CB", 0.38f, 0.74f, "فيرجيل فان دايك"),
                FormationPosition("p5", "LB", 0.15f, 0.72f, "أندي روبرتسون"),
                FormationPosition("p6", "DM", 0.5f, 0.54f, "فابينيو"),
                FormationPosition("p7", "CM", 0.75f, 0.46f, "جوردان هندرسون"),
                FormationPosition("p8", "CM", 0.25f, 0.46f, "جورجينيو فينالدوم"),
                FormationPosition("p9", "RW", 0.82f, 0.22f, "محمد صلاح"),
                FormationPosition("p10", "ST", 0.5f, 0.16f, "روبرتو فيرمينو", isMissing = true, hints = listOf("برازيلي", "رقم 9", "بوبي")),
                FormationPosition("p11", "LW", 0.18f, 0.22f, "ساديو ماني")
            )
        ),
        MissingXIMatch(
            id = "final_2",
            finalTitle = "نهائي كأس العالم 2022 (قطر)",
            teamName = "الأرجنتين",
            teamLogoEmoji = "🇦🇷",
            opponentName = "فرنسا (3-3 ثم ركلات الترجيح)",
            scoreLine = "3 - 3 (4 - 2)",
            year = "2022",
            formationName = "4-3-3",
            missingPlayerName = "أنخيل دي ماريا",
            missingPlayerJersey = 11,
            missingPlayerNationality = "الأرجنتين 🇦🇷",
            missingPlayerClubHints = listOf("سجل الهدف الثاني التاريخي", "رجل النهائيات الكبرى", "الملقب بـ النودل"),
            positions = listOf(
                FormationPosition("p1", "GK", 0.5f, 0.88f, "إيميليانو مارتينيز"),
                FormationPosition("p2", "RB", 0.85f, 0.72f, "ناهويل مولينا"),
                FormationPosition("p3", "CB", 0.62f, 0.74f, "كريستيان روميرو"),
                FormationPosition("p4", "CB", 0.38f, 0.74f, "نيكولاس أوتاميندي"),
                FormationPosition("p5", "LB", 0.15f, 0.72f, "نيكولاس تاغليافيكو"),
                FormationPosition("p6", "DM", 0.5f, 0.54f, "إنزو فيرنانديز"),
                FormationPosition("p7", "CM", 0.75f, 0.46f, "رودريغو دي بول"),
                FormationPosition("p8", "CM", 0.25f, 0.46f, "ألكسيس ماك أليستر"),
                FormationPosition("p9", "RW", 0.82f, 0.22f, "ليونيل ميسي"),
                FormationPosition("p10", "ST", 0.5f, 0.16f, "جوليان ألفاريز"),
                FormationPosition("p11", "LW", 0.18f, 0.22f, "أنخيل دي ماريا", isMissing = true, hints = listOf("سجل بالنهائي", "رقم 11", "احتفال القلب"))
            )
        ),
        MissingXIMatch(
            id = "final_3",
            finalTitle = "نهائي دوري أبطال أوروبا 2015 (برلين)",
            teamName = "برشلونة",
            teamLogoEmoji = "🔵🔴",
            opponentName = "يوفنتوس (3-1)",
            scoreLine = "3 - 1",
            year = "2015",
            formationName = "4-3-3",
            missingPlayerName = "إيفان راكيتيتش",
            missingPlayerJersey = 4,
            missingPlayerNationality = "كرواتيا 🇭🇷",
            missingPlayerClubHints = listOf("سجل الهدف الأول بالدقيقة 4", "لاعب الوسط الكرواتي الأنيق", "حمل رقم 4"),
            positions = listOf(
                FormationPosition("p1", "GK", 0.5f, 0.88f, "مارك أندريه تير شتيغن"),
                FormationPosition("p2", "RB", 0.85f, 0.72f, "داني ألفيس"),
                FormationPosition("p3", "CB", 0.62f, 0.74f, "جيرارد بيكيه"),
                FormationPosition("p4", "CB", 0.38f, 0.74f, "خافيير ماسكيرانو"),
                FormationPosition("p5", "LB", 0.15f, 0.72f, "جوردي ألبا"),
                FormationPosition("p6", "DM", 0.5f, 0.54f, "سيرجيو بوسكيتس"),
                FormationPosition("p7", "CM", 0.75f, 0.46f, "إيفان راكيتيتش", isMissing = true, hints = listOf("كرواتي", "سجل هدف التقدم", "رقم 4")),
                FormationPosition("p8", "CM", 0.25f, 0.46f, "أندريس إنييستا"),
                FormationPosition("p9", "RW", 0.82f, 0.22f, "ليونيل ميسي"),
                FormationPosition("p10", "ST", 0.5f, 0.16f, "لويس سواريز"),
                FormationPosition("p11", "LW", 0.18f, 0.22f, "نيمار جونيور")
            )
        )
    )

    // -------------------------------------------------------------------------
    // 4. ODD ONE OUT (اكتشف الدخيل)
    // -------------------------------------------------------------------------
    val oddOneOutQuestions = listOf(
        OddOneOutQuestion(
            id = "odd_1",
            title = "من هو اللاعب الدخيل بين هؤلاء النجوم الأربعة؟",
            categoryBadge = "الجوائز الفردية 🏅",
            oddIndex = 2, // كيليان مبابي
            cards = listOf(
                OddOneOutCard("c1", "ليونيل ميسي", "أسطورة الأرجنتين", "🇦🇷", false),
                OddOneOutCard("c2", "كريستيانو رونالدو", "دون البرتغال", "🇵🇹", false),
                OddOneOutCard("c3", "كيليان مبابي", "صاروخ فرنسا", "🇫🇷", true),
                OddOneOutCard("c4", "لوكا مودريتش", "مايسترو كرواتيا", "🇭🇷", false)
            ),
            explanationReason = "كيليان مبابي هو الدخيل! لأنه الوحيد في القائمة الذي لم يفز بجائزة الكرة الذهبية (Ballon d'Or) حتى الآن، بينما توج بها ميسي (8) ورونالدو (5) ومودريتش (1)."
        ),
        OddOneOutQuestion(
            id = "odd_2",
            title = "أي من هذه الأندية الكبرى يعتبر الدخيل في المجموعة؟",
            categoryBadge = "دوري أبطال أوروبا 🏆",
            oddIndex = 3, // باريس سان جيرمان
            cards = listOf(
                OddOneOutCard("c1", "ريال مدريد", "الملكي الإسباني", "👑", false),
                OddOneOutCard("c2", "ميلان", "الروسونيري الإيطالي", "🔴⚫", false),
                OddOneOutCard("c3", "بايرن ميونخ", "البافاري الألماني", "🔴", false),
                OddOneOutCard("c4", "باريس سان جيرمان", "العملاق الباريسي", "🗼", true)
            ),
            explanationReason = "باريس سان جيرمان هو النادي الدخيل! لأنه الوحيد الذي لم يحقق لقب دوري أبطال أوروبا في تاريخه، بينما يملك ريال مدريد 15 لقباً وميلان 7 وبايرن ميونخ 6 ألقاب."
        ),
        OddOneOutQuestion(
            id = "odd_3",
            title = "من هو المدرب الدخيل بين عمالقة التكتيك؟",
            categoryBadge = "عباقرة التدريب 👔",
            oddIndex = 3, // أرسين فينغر
            cards = listOf(
                OddOneOutCard("c1", "بيب غوارديولا", "فيلسوف السيتي", "🩵", false),
                OddOneOutCard("c2", "جوزيه مورينيو", "السبيشال ون", "🐺", false),
                OddOneOutCard("c3", "كارلو أنشيلوتي", "ميستر الشامبيونزليغ", "⚪", false),
                OddOneOutCard("c4", "أرسين فينغر", "بروفيسور أرسنال", "🔴", true)
            ),
            explanationReason = "أرسين فينغر هو المدرب الدخيل! لأنه الوحيد الذي لم يتوج أبداً بلقب دوري أبطال أوروبا كمدرب، بينما فاز بها أنشيلوتي 5 مرات وغوارديولا 3 مرات ومورينيو مرتين."
        ),
        OddOneOutQuestion(
            id = "odd_4",
            title = "أي من هؤلاء النجوم العرب هو الدخيل في إنجلترا؟",
            categoryBadge = "البريميرليغ الإنجليزي 🦁",
            oddIndex = 3, // حكيم زياش
            cards = listOf(
                OddOneOutCard("c1", "محمد صلاح", "الملك المصري (ليفربول)", "👑", false),
                OddOneOutCard("c2", "رياض محرز", "ساحر الجزائر (مان سيتي/ليستر)", "🇩🇿", false),
                OddOneOutCard("c3", "ساديو ماني", "أسد التيرانغا (ليفربول)", "🇸🇳", false),
                OddOneOutCard("c4", "حكيم زياش", "نجم المغرب (تشيلسي)", "🇲🇦", true)
            ),
            explanationReason = "حكيم زياش هو الدخيل! لأنه الوحيد الذي لم يتوج بلقب الدوري الإنجليزي الممتاز (البريميرليغ)، بينما حققه صلاح وماني مع ليفربول ومحرز مع ليستر ومانشستر سيتي."
        )
    )

    // -------------------------------------------------------------------------
    // 5. JERSEY NUMBERS & TRANSFER FEES
    // -------------------------------------------------------------------------
    val jerseyQuestions = listOf(
        JerseyQuestion(
            id = "j_1",
            playerName = "يوهان كرويف",
            teamOrCountry = "منتخب هولندا 🇳🇱",
            yearOrContext = "الرقم الأيقوني التاريخي",
            iconEmoji = "🇳🇱",
            correctJerseyNumber = 14,
            options = listOf(10, 14, 9, 7),
            funFact = "أشهر رقم 14 في تاريخ كرة القدم، ارتداه كرويف صدفة بعدما أعطى قميصه رقم 9 لزميله غيري موهرن."
        ),
        JerseyQuestion(
            id = "j_2",
            playerName = "ليونيل ميسي",
            teamOrCountry = "باريس سان جيرمان 🇫🇷",
            yearOrContext = "أول مواسمه في حديقة الأمراء",
            iconEmoji = "🗼",
            correctJerseyNumber = 30,
            options = listOf(10, 19, 30, 7),
            funFact = "اختار ميسي الرقم 30 لأنه أول رقم لعب به في بداية مسيرته مع الفريق الأول لبرشلونة."
        ),
        JerseyQuestion(
            id = "j_3",
            playerName = "رونالدينيو",
            teamOrCountry = "إيه سي ميلان 🇮🇹",
            yearOrContext = "سنة ميلاده المطبوعة على الظهر",
            iconEmoji = "🔴⚫",
            correctJerseyNumber = 80,
            options = listOf(10, 80, 21, 99),
            funFact = "لأن الرقم 10 كان يرتديه الأسطورة كلارنس سيدورف، اختار رونالدينيو رقم 80 نسبة لسنة ميلاده 1980."
        ),
        JerseyQuestion(
            id = "j_4",
            playerName = "ماريو بالوتيللي",
            teamOrCountry = "مانشستر سيتي وإيه سي ميلان ⚽",
            yearOrContext = "رقمه المفضل طوال مسيرته",
            iconEmoji = "⚡",
            correctJerseyNumber = 45,
            options = listOf(9, 45, 17, 88),
            funFact = "اختار بالوتيللي 45 لأن 4 + 5 = 9، وحقق به لقب البريميرليغ التاريخي بصناعة هدف أغويرو الشهير 93:20."
        ),
        JerseyQuestion(
            id = "j_5",
            playerName = "ديفيد بيكهام",
            teamOrCountry = "ريال مدريد 🇪🇸",
            yearOrContext = "تيمناً بأسطورة السلة مايكل جوردان",
            iconEmoji = "⚪",
            correctJerseyNumber = 23,
            options = listOf(7, 23, 77, 10),
            funFact = "لأن راؤول غونزاليس كان يرتدي رقم 7، اختار بيكهام رقم 23 حباً في نجم شيكاغو بولز مايكل جوردان."
        ),
        JerseyQuestion(
            id = "j_6",
            playerName = "فيل فودين",
            teamOrCountry = "مانشستر سيتي 🏴󠁧󠁢󠁥󠁮󠁧󠁿",
            yearOrContext = "وفاءً لذكرى جده الراحل روني",
            iconEmoji = "🩵",
            correctJerseyNumber = 47,
            options = listOf(10, 11, 47, 8),
            funFact = "يرتدي فودين الرقم 47 وفاءً لجده روني الذي توفي عن عمر يناهز 47 عاماً، ورفض التخلي عنه حتى حين عُرض عليه الرقم 10."
        )
    )

    val transferFeeQuestions = listOf(
        TransferFeeQuestion(
            id = "tf_1",
            title = "أي الصفقتين هي الأغلى في التاريخ المالي لكرة القدم؟",
            playerA = "نيمار جونيور (من برشلونة إلى باريس سان جيرمان)",
            feeA = "222 مليون يورو",
            playerB = "كيليان مبابي (من موناكو إلى باريس سان جيرمان)",
            feeB = "180 مليون يورو",
            whichIsHigher = "A",
            differenceText = "صفقة نيمار عام 2017 بـ 222 مليون يورو هي أغلى انتقال في تاريخ كرة القدم بلا منازع بكسر الشرط الجزائي!"
        ),
        TransferFeeQuestion(
            id = "tf_2",
            title = "مقارنة صفقات خط الوسط التاريخية:",
            playerA = "فيليب كوتينيو (من ليفربول إلى برشلونة)",
            feeA = "135 مليون يورو",
            playerB = "جود بيلينغهام (من دورتموند إلى ريال مدريد)",
            feeB = "103 مليون يورو",
            whichIsHigher = "A",
            differenceText = "صفقة انتقال كوتينيو لبرشلونة عام 2018 وصلت لـ 135 مليون يورو متجاوزة صفقة بيلينغهام للملكي (103 مليون ثابتة)."
        ),
        TransferFeeQuestion(
            id = "tf_3",
            title = "صراع صفقات البريميرليغ الإنجليزي القياسية:",
            playerA = "ديكلان رايس (من وست هام إلى أرسنال)",
            feeA = "116 مليون يورو (105 مليون إسترليني)",
            playerB = "إنزو فيرنانديز (من بنفيكا إلى تشيلسي)",
            feeB = "121 مليون يورو",
            whichIsHigher = "B",
            differenceText = "إنزو فيرنانديز أغلى بـ 121 مليون يورو بعدما دفع تشيلسي شرطه الجزائي كاملاً لبنفيكا البرتغالي بعد كأس العالم."
        )
    )

    // -------------------------------------------------------------------------
    // 6. SPY LOCATIONS (برا السالفة / مين الجاسوس)
    // -------------------------------------------------------------------------
    val spyLocations = listOf(
        SpyLocation(
            id = "spy_loc_1",
            locationName = "غرفة ملابس ريال مدريد بين الشوطين",
            icon = "⚪",
            category = "كروي",
            roles = listOf(
                "كارلو أنشيلوتي (المدير الفني الهادئ)",
                "لوكا مودريتش (القائد صاحب الخبرة)",
                "فينيسيوس جونيور (الجناح المتحمس)",
                "طبيب الفريق (مجهز الثلج والضمادات)",
                "مسؤول غرفة الملابس وأطقم اللاعبين",
                "المحلل التكتيكي لشاشات الفيديو"
            ),
            sampleQuestions = listOf(
                "هل المكان بارد أم حار ومرتفع التوتر الآن؟",
                "هل يُسمح لوسائل الإعلام بالدخول هنا في هذه اللحظة؟",
                "ما هو الشيء الأكثر استخداماً في هذا المكان؟",
                "هل ترتدي حذاء كرة قدم أم بدلة رسمية؟"
            )
        ),
        SpyLocation(
            id = "spy_loc_2",
            locationName = "استوديو تحليلي لقناة بي إن سبورتس",
            icon = "📺",
            category = "كروي",
            roles = listOf(
                "مقدم الاستوديو (مدير الحوار والشاشات)",
                "المحلل الرياضي الكبير (نجم أسبق)",
                "مخرج البث والمؤثرات",
                "خبير التحكيم التلفزيوني",
                "مصور الكاميرا الرئيسية",
                "معد ومسؤول الإحصاءات والأرقام"
            ),
            sampleQuestions = listOf(
                "هل تشرب الشاي أثناء أداء عملك هنا؟",
                "كم عدد الشاشات الكبيرة أمامك؟",
                "هل كلامك يُبث لملايين المنازل حالياً؟",
                "من هو الشخص الذي يصرخ في أذنك دائماً؟"
            )
        ),
        SpyLocation(
            id = "spy_loc_3",
            locationName = "مقهى شعبي مصري أثناء مباراة القمة (الأهلي والزمالك)",
            icon = "☕",
            category = "تجمعات شعبية",
            roles = listOf(
                "قهوجي المقهى (حامل صينية الشاي والكركديه)",
                "مشجع متعصب جداً يصرخ مع كل هجمة",
                "محلل كروي شعبي يتوقع التبديلات دائماً",
                "زبون عجوز يلعب الطاولة في الركن",
                "مشجع هادئ جاء فقط للاستمتاع بالأجواء",
                "صبي المقهى مصلح الرسيفر والطبق"
            ),
            sampleQuestions = listOf(
                "ما هي أكثر جملة تتكرر في هذا المكان كل دقيقتين؟",
                "هل الرائحة هنا دخان وفحم أم عطور فرنسية؟",
                "ماذا يحدث عندما تنقطع شارة البث فجأة؟",
                "هل تجلس على كرسي مريح وثير أم كرسي بلاستيك في الشارع؟"
            )
        ),
        SpyLocation(
            id = "spy_loc_4",
            locationName = "طائرة خاصة تقل بعثة المنتخب لكأس العالم",
            icon = "✈️",
            category = "كروي",
            roles = listOf(
                "كابتن الطائرة والطيار الخاص",
                "قائد المنتخب (جالس في المقعد الأول)",
                "مدرب اللياقة البدنية ومجهز المكملات",
                "مضيفة الطيران الملكية",
                "رئيس اتحاد الكرة المرافق للبعثة",
                "اللاعب الاحتياطي الذي يمزح طوال الرحلة"
            ),
            sampleQuestions = listOf(
                "على أي ارتفاع نتواجد الآن تقريباً؟",
                "هل تستطيع فتح النافذة لاستنشاق هواء نقي؟",
                "ما هو الطعام الموزع في هذا المكان؟",
                "هل يوجد توتر وقلق أم راحة تامة واسترخاء؟"
            )
        ),
        SpyLocation(
            id = "spy_loc_5",
            locationName = "مدرجات ملعب الكامب نو أثناء الكلاسيكو",
            icon = "🏟️",
            category = "كروي",
            roles = listOf(
                "مشجع يحمل علم وشاح النادي",
                "قائد ألتراس يقود الهتافات بالميكروفون",
                "بائع السندويشات والمشروبات الغازية",
                "رجل أمن الملعب المراقب للجماهير",
                "سائح أجنبي يصور مقاطع فيديو طوال الوقت",
                "صحفي ميداني يحمل مسجل الصوت"
            ),
            sampleQuestions = listOf(
                "هل الصوت هنا صاخب جداً أم هادئ كالمكتبة؟",
                "كم ألف شخص يحيطون بك في هذا المكان؟",
                "ماذا تفعل عندما يُسجل الهدف في الدقيقة الأخيرة؟",
                "هل تحتاج لتذكرة باهظة الثمن لتكون هنا؟"
            )
        )
    )

    // -------------------------------------------------------------------------
    // 7. TRUTH OR DARE ITEMS
    // -------------------------------------------------------------------------
    val truthOrDareItems = listOf(
        // TRUTHS
        TruthOrDareItem(
            id = "t_1",
            type = TruthOrDareType.TRUTH,
            category = "كروي ⚽",
            content = "هل شجعت في أي يوم من الأيام الغريم التقليدي لفريقك المفضل سراً بدون أن يعلم أحد؟ واعترف بالسبب!",
            pointsReward = 60
        ),
        TruthOrDareItem(
            id = "t_2",
            type = TruthOrDareType.TRUTH,
            category = "كروي ⚽",
            content = "لو خُيّرت بين فوز فريقك بدوري الأبطال أو فوزك أنت بمليون دولار.. ماذا ستختار بكل صراحة وبدون نفاق؟",
            pointsReward = 70
        ),
        TruthOrDareItem(
            id = "t_3",
            type = TruthOrDareType.TRUTH,
            category = "شخصي ومضحك 😂",
            content = "ما هي أكبر كذبة كذبتها على أصدقائك أو أهلك لتتهرب من الخروج ولم يكتشفوها أبداً؟",
            pointsReward = 50
        ),
        TruthOrDareItem(
            id = "t_4",
            type = TruthOrDareType.TRUTH,
            category = "إحراج واعترافات 🤫",
            content = "من هو الشخص الموجود في الجلسة الآن الذي تعتبره الأسوأ في لعب البلايستيشن أو كرة القدم؟ وواجهه بذلك!",
            pointsReward = 80
        ),
        TruthOrDareItem(
            id = "t_5",
            type = TruthOrDareType.TRUTH,
            category = "كروي ⚽",
            content = "من هو اللاعب الذي يكرهه الجميع وأنت تراه موهوباً ومظلوماً، أو العكس: لاعب مشهور جداً وأنت تراه منفوخاً إعلامياً؟",
            pointsReward = 60
        ),
        TruthOrDareItem(
            id = "t_6",
            type = TruthOrDareType.TRUTH,
            category = "إحراج واعترافات 🤫",
            content = "ما هي آخر رسالة محرجة أرسلتها بالخطأ في جروب الأصدقاء أو العائلة وتمنيت لو اختفيت؟",
            pointsReward = 75
        ),

        // DARES
        TruthOrDareItem(
            id = "d_1",
            type = TruthOrDareType.DARE,
            category = "كروي ⚽",
            content = "قلّد طريقة تعليق عصام الشوالي أو فهد العتيبي لمدة دقيقة كاملة على هدف خيالي في الدقيقة 95!",
            pointsReward = 100
        ),
        TruthOrDareItem(
            id = "d_2",
            type = TruthOrDareType.DARE,
            category = "تحديات جريئة 🔥",
            content = "قم بتنفيذ احتفال كريستيانو رونالدو 'SIIIUUU' بصوت مرتفع جداً في الغرفة بكل حماس!",
            pointsReward = 80
        ),
        TruthOrDareItem(
            id = "d_3",
            type = TruthOrDareType.DARE,
            category = "شخصي ومضحك 😂",
            content = "اتصل بصديق لك غير موجود الآن وأخبره أنك قررت السفر غداً للانضمام لأكاديمية ريال مدريد، واقنعه لمدة دقيقة!",
            pointsReward = 120
        ),
        TruthOrDareItem(
            id = "d_4",
            type = TruthOrDareType.DARE,
            category = "تحديات جريئة 🔥",
            content = "قم بعمل 15 ضغطة تمريناً رياضياً كروياً في مكانك الآن مع عد الأرقام بأسماء نجوم البرازيل!",
            pointsReward = 90
        ),
        TruthOrDareItem(
            id = "d_5",
            type = TruthOrDareType.DARE,
            category = "كروي ⚽",
            content = "قم بتمثيل حركة مراوغة ميسي الشهيرة لبواتينغ بدون أن تتكلم واجعل الجالسين يخمنون اللقطة!",
            pointsReward = 85
        )
    )

    // -------------------------------------------------------------------------
    // 8. TAHADDI AL-THALATHIN ROUNDS (فقرات نصوح)
    // -------------------------------------------------------------------------

    // أ) لعبة الجرس (صراع الجرس التفاعلي)
    val buzzerQuestions = listOf(
        BuzzerQuestion(
            id = "bz_1",
            question = "من هو اللاعب الوحيد الذي فاز بكأس العالم 3 مرات كلاعب؟",
            correctAnswer = "بيليه",
            acceptableKeywords = listOf("بيليه", "Pelé", "بيليه البرازيلي", "اديسون ارانتيس"),
            difficulty = "متوسط",
            points = 100
        ),
        BuzzerQuestion(
            id = "bz_2",
            question = "ما هو النادي الأكثر تتويجاً بلقب الدوري الأوروبي (اليوروباليغ) عبر التاريخ؟",
            correctAnswer = "إشبيلية الإسباني",
            acceptableKeywords = listOf("إشبيلية", "اشبيلية", "Sevilla"),
            difficulty = "متوسط",
            points = 100
        ),
        BuzzerQuestion(
            id = "bz_3",
            question = "من سجل أسرع هدف في تاريخ نهائيات كأس العالم (خلال 11 ثانية)؟",
            correctAnswer = "هاكان شكور التركي",
            acceptableKeywords = listOf("هاكان شكور", "هاكان", "شكور", "تركيا"),
            difficulty = "صعب",
            points = 150
        ),
        BuzzerQuestion(
            id = "bz_4",
            question = "كم عدد الكرات الذهبية التي توج بها الأسطورة ليونيل ميسي؟",
            correctAnswer = "8 كرات ذهبية",
            acceptableKeywords = listOf("8", "ثمانية", "ثمان كرات"),
            difficulty = "سهل",
            points = 100
        ),
        BuzzerQuestion(
            id = "bz_5",
            question = "في أي عام أقيمت أول نسخة من بطولة كأس العالم في أوروغواي؟",
            correctAnswer = "1930",
            acceptableKeywords = listOf("1930", "ثلاثين", "عام ثلاثين"),
            difficulty = "متوسط",
            points = 100
        )
    )

    // ب) سين جيم (Q&A TRIVIA)
    val sinJeemQuestions = listOf(
        SinJeemQuestion(
            id = "sj_1",
            question = "من هو الهداف التاريخي لبطولة دوري أبطال أوروبا على مر العصور؟",
            options = listOf("ليونيل ميسي", "كريستيانو رونالدو", "روبرت ليفاندوفسكي", "كريم بنزيما"),
            correctIndex = 1,
            explanation = "كريستيانو رونالدو هو الهداف التاريخي للبطولة برصيد 140 هدفاً رسمياً.",
            tournamentBadge = "دوري أبطال أوروبا 🏆"
        ),
        SinJeemQuestion(
            id = "sj_2",
            question = "أي منتخب عربي وصل لأول مرة في التاريخ لنصف نهائي كأس العالم 2022؟",
            options = listOf("مصر", "الجزائر", "المغرب", "السعودية"),
            correctIndex = 2,
            explanation = "أسود الأطلس منتخب المغرب حقق الإنجاز العربي والأفريقي التاريخي في مونديال قطر.",
            tournamentBadge = "كأس العالم 🌍"
        ),
        SinJeemQuestion(
            id = "sj_3",
            question = "ما هو النادي الإيطالي الملقب بـ 'السيدة العجوز' (La Vecchia Signora)؟",
            options = listOf("إيه سي ميلان", "إنتر ميلان", "يوفنتوس", "روما"),
            correctIndex = 2,
            explanation = "يوفنتوس تورينو هو صاحب هذا اللقب العريق منذ بدايات القرن العشرين.",
            tournamentBadge = "الدوري الإيطالي 🇮🇹"
        ),
        SinJeemQuestion(
            id = "sj_4",
            question = "من هو الحارس الوحيد في التاريخ الذي فاز بالكرة الذهبية (Ballon d'Or)؟",
            options = listOf("جانلويجي بوفون", "إيكر كاسياس", "ليف ياشين", "مانويل نوير"),
            correctIndex = 2,
            explanation = "الحارس السوفيتي ليف ياشين 'العنكبوت الأسود' حققها عام 1963.",
            tournamentBadge = "جوائز تاريخية 🏅"
        )
    )

    // ج) مهنة لاعب (PLAYER CAREER / JOB)
    val playerCareerJobs = listOf(
        PlayerCareerJob(
            id = "pc_1",
            playerName = "لوكا مودريتش",
            nationality = "كرواتيا 🇭🇷",
            position = "خط وسط (مايسترو)",
            funFact = "قاد كرواتيا لنهائي مونديال 2018 وحقق الكرة الذهبية ونال 6 ألقاب دوري أبطال أوروبا!",
            steps = listOf(
                CareerStep("2003 - 2008", "دينامو زغرب (كرواتيا) مع إعارات لموستار وإنكير زابريسيتش", "94 مباراة - 27 هدفاً", "بطل الدوري الكرواتي 3 مرات"),
                CareerStep("2008 - 2012", "توتنهام هوتسبير (إنجلترا)", "127 مباراة - 13 هدفاً", "التأهل لدوري الأبطال مع هاري ريدناب"),
                CareerStep("2012 - حتى الآن", "ريال مدريد (إسبانيا)", "500+ مباراة - أسطورة النادي", "6 ألقاب دوري أبطال أوروبا + الكرة الذهبية 2018")
            )
        ),
        PlayerCareerJob(
            id = "pc_2",
            playerName = "إيرلينغ هالاند",
            nationality = "النرويج 🇳🇴",
            position = "رأس حربة (الوحش النرويجي)",
            funFact = "حطم الرقم القياسي للأهداف في موسم واحد بالبريميرليغ برصيد 36 هدفاً وحقق الثلاثية التاريخية!",
            steps = listOf(
                CareerStep("2016 - 2017", "براين (النرويج)", "16 مباراة", "الانطلاقة في دوري الدرجة الثانية النرويجي"),
                CareerStep("2017 - 2018", "مولده (النرويج) تحت قيادة أولي غونار سولشاير", "39 مباراة - 14 هدفاً", "موهبة صاعدة خارقة"),
                CareerStep("2019 - 2020", "ريد بول سالزبورغ (النمسا)", "16 مباراة - 17 هدفاً", "سجل في 5 مباريات متتالية بدوري الأبطال"),
                CareerStep("2020 - 2022", "بوروسيا دورتموند (ألمانيا)", "67 مباراة - 62 هدفاً", "هداف الشامبيونزليغ وكأس ألمانيا"),
                CareerStep("2022 - حتى الآن", "مانشستر سيتي (إنجلترا)", "100+ هدف", "الثلاثية التاريخية والحذاء الذهبي الأوروبي")
            )
        ),
        PlayerCareerJob(
            id = "pc_3",
            playerName = "أنخيل دي ماريا",
            nationality = "الأرجنتين 🇦🇷",
            position = "جناح أيمن / أيسر (رجل النهائيات)",
            funFact = "سجل في نهائي الأولمبياد 2008، نهائي كوبا أمريكا 2021، نهائي الفيناليسيما 2022، ونهائي كأس العالم 2022!",
            steps = listOf(
                CareerStep("2005 - 2007", "روزاريو سنترال (الأرجنتين)", "35 مباراة - 6 أهداف", "البداية في بلاد الفضة"),
                CareerStep("2007 - 2010", "بنفيكا (البرتغال)", "76 مباراة - 7 أهداف", "بطل الدوري البرتغالي وتألق أوروبي"),
                CareerStep("2010 - 2014", "ريال مدريد (إسبانيا)", "124 مباراة - 22 هدفاً", "العاشرة ورجل المباراة النهائية في لشبونة"),
                CareerStep("2014 - 2015", "مانشستر يونايتد (إنجلترا)", "27 مباراة - 3 أهداف", "موسم وحيد بالرقم 7"),
                CareerStep("2015 - 2022", "باريس سان جيرمان (فرنسا)", "197 مباراة - 56 هدفاً", "الهداف التاريخي لصناعة الأهداف بالنادي"),
                CareerStep("2022 - 2023", "يوفنتوس (إيطاليا)", "26 مباراة - 4 أهداف", "موسم في الكالتشيو"),
                CareerStep("2023 - حتى الآن", "بنفيكا (البرتغال)", "عودة للأصول", "بطل السوبر البرتغالي")
            )
        )
    )

    // د) التعويض (الوقت بدل الضائع / INJURY TIME ROUND)
    val extraTimeQuestions = listOf(
        ExtraTimeQuestion(
            id = "et_1",
            question = "من توج بلقب كأس أمم أفريقيا 2023 الأخيرة؟",
            optionA = "ساحل العاج (كوت ديفوار)",
            optionB = "نيجيريا",
            correctOption = "A",
            doublePoints = 200
        ),
        ExtraTimeQuestion(
            id = "et_2",
            question = "أيهما فاز بلقب الدوري الألماني (البوندسليغا) بدون أي هزيمة عام 2024؟",
            optionA = "باير ليفركوزن",
            optionB = "بايرن ميونخ",
            correctOption = "A",
            doublePoints = 200
        ),
        ExtraTimeQuestion(
            id = "et_3",
            question = "من سجل هدف تتويج إسبانيا ببطولة يورو 2024 ضد إنجلترا؟",
            optionA = "ميكيل أويارزابال",
            optionB = "نيكو ويليامز",
            correctOption = "A",
            doublePoints = 200
        ),
        ExtraTimeQuestion(
            id = "et_4",
            question = "أي نادي توج بدوري أبطال آسيا لعام 2024؟",
            optionA = "العين الإماراتي",
            optionB = "الهلال السعودي",
            correctOption = "A",
            doublePoints = 200
        ),
        ExtraTimeQuestion(
            id = "et_5",
            question = "من هو أكثر لاعب مشاركة في تاريخ مباريات كأس العالم؟",
            optionA = "ليونيل ميسي (26 مباراة)",
            optionB = "لوثار ماتيوس (25 مباراة)",
            correctOption = "A",
            doublePoints = 200
        )
    )
}
