package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.FootballGameViewModel
import com.example.ui.GameScreen
import com.example.ui.components.DashboardTopBar
import com.example.ui.components.DifficultySelectionDialog
import com.example.ui.components.GridGameCard
import com.example.ui.components.OfflineModeDialog
import com.example.ui.components.RewardedAdDialog
import com.example.ui.theme.*

@Composable
fun HomeScreen(viewModel: FootballGameViewModel) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val userProfile by viewModel.userProfile.collectAsState()
    val showRewardedAd by viewModel.showRewardedAdDialog.collectAsState()
    val adCountdown by viewModel.adCountdown.collectAsState()
    val showDifficultyDialog by viewModel.showDifficultyDialog.collectAsState()
    val selectedDifficulty by viewModel.selectedDifficulty.collectAsState()
    val isOfflineMode by viewModel.isOfflineMode.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()
    val pendingSyncCount by viewModel.pendingSyncCount.collectAsState()
    val cachedQuestionsCount by viewModel.cachedOfflineQuestionsCount.collectAsState()
    val lastSyncResult by viewModel.lastSyncResult.collectAsState()
    val showOfflineDialog by viewModel.showOfflineDialog.collectAsState()
    val userChallengeCode by viewModel.userChallengeCode.collectAsState()
    var selectedCategoryFilter by remember { mutableStateOf("all") }

    Scaffold(
        containerColor = PitchDark,
        topBar = {
            DashboardTopBar(
                playerName = userProfile.playerName,
                avatarEmoji = userProfile.avatarEmoji,
                levelTitle = userProfile.levelTitle,
                levelNumber = userProfile.levelNumber,
                points = userProfile.points,
                coins = userProfile.coins,
                levelProgress = userProfile.currentLevelProgress,
                onAvatarClick = { viewModel.navigateTo(GameScreen.ProfileScreen) }
            )
        },
        bottomBar = {
            StadiumBottomNavBar(
                activeTab = "home",
                onTabSelect = { tab ->
                    when (tab) {
                        "home" -> {}
                        "leaderboard" -> viewModel.navigateTo(GameScreen.LeaderboardScreen)
                        "profile" -> viewModel.navigateTo(GameScreen.ProfileScreen)
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Stadium Atmosphere Hero Banner
            item {
                StadiumHeroCard(
                    playerName = userProfile.playerName,
                    favoriteClub = userProfile.favoriteClub,
                    levelTitle = userProfile.levelTitle,
                    levelNumber = userProfile.levelNumber,
                    levelProgress = userProfile.currentLevelProgress,
                    streak = userProfile.currentStreak,
                    onAdClick = { viewModel.openRewardedAd() }
                )
            }

            // Quick Booster Banner
            item {
                DailyBoosterBanner(
                    coins = userProfile.coins,
                    onClick = { viewModel.openRewardedAd() }
                )
            }

            // Daily Sabaho Challenge Reminder Banner
            item {
                SabahoNotificationBanner(
                    onTestNotification = {
                        val notificationManager = com.example.notifications.StadiumNotificationManager(context)
                        notificationManager.showSabahoReminderNow()
                        viewModel.audioEffects.playTick()
                    }
                )
            }

            // -------------------------------------------------------------
            // Offline Mode Status & Sync Bar (Room Database)
            // -------------------------------------------------------------
            item {
                OfflineStatusBanner(
                    isOfflineMode = isOfflineMode,
                    cachedCount = cachedQuestionsCount,
                    pendingCount = pendingSyncCount,
                    isSyncing = isSyncing,
                    onOpenOfflineDialog = { viewModel.setShowOfflineDialog(true) },
                    onSyncClick = { viewModel.syncOfflineRecords() }
                )
            }

            // -------------------------------------------------------------
            // Friend Invite & Challenge Code Banner (دعوة صديق)
            // -------------------------------------------------------------
            item {
                FriendInviteBanner(
                    myCode = userChallengeCode,
                    onOpenInvite = { viewModel.navigateTo(GameScreen.FriendInviteScreen) }
                )
            }

            // -------------------------------------------------------------
            // PvP Real-Time Match Banner (المواجهة المباشرة)
            // -------------------------------------------------------------
            item {
                PvPChallengeBanner(
                    onStartPvP = { viewModel.startPvPMatchSearch() }
                )
            }

            // -------------------------------------------------------------
            // Supabase Online Servers & Rooms Banner (السيرفرات واللعب أونلاين)
            // -------------------------------------------------------------
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.navigateTo(GameScreen.SupabaseOnlineScreen) }
                        .testTag("banner_supabase_online"),
                    colors = CardDefaults.cardColors(containerColor = PitchCard),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.5.dp, EmeraldNeon)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(CircleShape)
                                    .background(EmeraldNeon.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = "🌐", fontSize = 24.sp)
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "السيرفرات واللعب أونلاين",
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        fontSize = 15.sp
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(EmeraldNeon.copy(alpha = 0.2f))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(text = "Supabase 🟢", color = EmeraldNeon, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "إنشاء غرف بأكواد خاصة، سيرفرات إقليمية، ومنافسات مباشرة",
                                    fontSize = 11.sp,
                                    color = TextSecondary
                                )
                            }
                        }

                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                            tint = EmeraldNeon
                        )
                    }
                }
            }

            // -------------------------------------------------------------
            // Party Leaderboard & Records Banner (لوحة الصدارة التراكمية في Room)
            // -------------------------------------------------------------
            item {
                PartyLeaderboardPromoBanner(
                    onClick = { viewModel.navigateTo(GameScreen.LeaderboardScreen) }
                )
            }

            // -------------------------------------------------------------
            // Difficulty Selector Bar (سهل / متوسط / صعب)
            // -------------------------------------------------------------
            item {
                DifficultySelectorBar(
                    currentDifficulty = selectedDifficulty,
                    onChangeClick = {
                        viewModel.openDifficultySelector(GameScreen.ThirtySecondsSelect)
                    }
                )
            }

            // -------------------------------------------------------------
            // Category Filter Pills (الكل / صالون وتسلية / كرة قدم)
            // -------------------------------------------------------------
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedCategoryFilter == "all",
                        onClick = { selectedCategoryFilter = "all" },
                        label = { Text("🌟 الكل (24)") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = TrophyGold,
                            selectedLabelColor = PitchDark,
                            containerColor = PitchCard,
                            labelColor = Color.White
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = selectedCategoryFilter == "all",
                            borderColor = PitchDivider,
                            selectedBorderColor = TrophyGold
                        ),
                        modifier = Modifier.weight(1f)
                    )

                    FilterChip(
                        selected = selectedCategoryFilter == "party",
                        onClick = { selectedCategoryFilter = "party" },
                        label = { Text("🎉 صالون وتسلية (12)") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFFFF9100),
                            selectedLabelColor = Color.White,
                            containerColor = PitchCard,
                            labelColor = Color.White
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = selectedCategoryFilter == "party",
                            borderColor = PitchDivider,
                            selectedBorderColor = Color(0xFFFF9100)
                        ),
                        modifier = Modifier.weight(1.3f)
                    )

                    FilterChip(
                        selected = selectedCategoryFilter == "football",
                        onClick = { selectedCategoryFilter = "football" },
                        label = { Text("⚽ كرة قدم (12)") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = EmeraldNeon,
                            selectedLabelColor = PitchDark,
                            containerColor = PitchCard,
                            labelColor = Color.White
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = selectedCategoryFilter == "football",
                            borderColor = PitchDivider,
                            selectedBorderColor = EmeraldNeon
                        ),
                        modifier = Modifier.weight(1.1f)
                    )
                }
            }

            // =============================================================
            // SECTION 1: ألعاب الصالون والتسلية والذكاء (غير رياضية)
            // =============================================================
            if (selectedCategoryFilter == "all" || selectedCategoryFilter == "party") {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = "🎉", fontSize = 20.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "ألعاب الصالون والتسلية والذكاء",
                                style = MaterialTheme.typography.titleLarge,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFFFF9100).copy(alpha = 0.15f))
                                .border(1.dp, Color(0xFFFF9100).copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "12 لعبة حماسية 🔥",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFFF9100),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Row 1: أتوبيس كومبليت & تحدي الـ 5 ثواني
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        GridGameCard(
                            title = "أتوبيس كومبليت",
                            subtitle = "ولد، بنت، جماد، بلاد، نبات مع الكابتن بوط الآلي",
                            badgeText = "شعبية كبرى 🌟",
                            badgeColor = TrophyGold,
                            iconEmoji = "🚌",
                            gradientColors = listOf(Color(0xFFE65100), Color(0xFFFFB300)),
                            testTag = "game_autobus_complete",
                            onClick = { viewModel.startAutobusGame() },
                            modifier = Modifier.weight(1f)
                        )

                        GridGameCard(
                            title = "تحدي الـ 5 ثواني",
                            subtitle = "3 إجابات خاطفة قبل انتهاء عداد الثواني الناري",
                            badgeText = "سرعة بديهة ⚡",
                            badgeColor = EmeraldNeon,
                            iconEmoji = "⚡",
                            gradientColors = listOf(Color(0xFF880E4F), Color(0xFFFF4081)),
                            testTag = "game_five_seconds",
                            onClick = { viewModel.startFiveSecondsGame() },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Row 2: لو خيروك؟ & فوازير الذكاء
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        GridGameCard(
                            title = "لو خيروك؟",
                            subtitle = "مواقف طريفة ومحرجة واكتشف ماذا اختار أغلب الناس",
                            badgeText = "إحصاءات حية 📊",
                            badgeColor = StadiumCyan,
                            iconEmoji = "🤔",
                            gradientColors = listOf(Color(0xFF004D40), Color(0xFF00BFA5)),
                            testTag = "game_would_you_rather",
                            onClick = { viewModel.startWouldYouRatherGame() },
                            modifier = Modifier.weight(1f)
                        )

                        GridGameCard(
                            title = "فوازير الذكاء",
                            subtitle = "ألغاز ثقافية وأمثال مع نظام التلميحات المتدرجة",
                            badgeText = "تفكير وعبقرية 💡",
                            badgeColor = GoldLight,
                            iconEmoji = "💡",
                            gradientColors = listOf(Color(0xFF311B92), Color(0xFF7C4DFF)),
                            testTag = "game_general_riddles",
                            onClick = { viewModel.startGeneralRiddlesGame() },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Row 3: بدون كلام & التابو العربي
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        GridGameCard(
                            title = "بدون كلام",
                            subtitle = "تخمين أفلام ومسرحيات بالإيموجي أو تمثيل بالجلسة",
                            badgeText = "تمثيل وإيموجي 🎭",
                            badgeColor = Color(0xFFE040FB),
                            iconEmoji = "🎭",
                            gradientColors = listOf(Color(0xFF4A148C), Color(0xFFAB47BC)),
                            testTag = "game_bedoon_kalam",
                            onClick = { viewModel.startBedoonKalam() },
                            modifier = Modifier.weight(1f)
                        )

                        GridGameCard(
                            title = "التابو العربي",
                            subtitle = "اشرح الكلمة لأصحابك وتجنب الـ 5 كلمات الممنوعة",
                            badgeText = "ممنوع تقولها! 🚫",
                            badgeColor = Color(0xFFFF5252),
                            iconEmoji = "🚫",
                            gradientColors = listOf(Color(0xFFB71C1C), Color(0xFFFF5252)),
                            testTag = "game_taboo",
                            onClick = { viewModel.startTabooGame() },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Row 4: كمّل المثل & المليون العربي
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        GridGameCard(
                            title = "كمّل المثل الشعبي",
                            subtitle = "أمثال وحكم تراثية عربية مع سلاسل انتصارات مضاعفة",
                            badgeText = "تراث وأمثال 📜",
                            badgeColor = TrophyGold,
                            iconEmoji = "📜",
                            gradientColors = listOf(Color(0xFFE65100), Color(0xFFFFB300)),
                            testTag = "game_proverbs",
                            onClick = { viewModel.startProverbsGame() },
                            modifier = Modifier.weight(1f)
                        )

                        GridGameCard(
                            title = "المليون العربي",
                            subtitle = "15 سؤالاً تصاعدياً مع 50:50 والجمهور والصديق",
                            badgeText = "السلّم الثقافي 💰",
                            badgeColor = TrophyGold,
                            iconEmoji = "💰",
                            gradientColors = listOf(Color(0xFF1B5E20), Color(0xFFFFD600)),
                            testTag = "game_millionaire",
                            onClick = { viewModel.startMillionaireGame() },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Row 5: الرابط العجيب & ديربي التجمعات
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        GridGameCard(
                            title = "الرابط العجيب",
                            subtitle = "4 كلمات تشترك في سر واحد.. هل يمكنك اكتشافه؟",
                            badgeText = "كلمة السر 🔍",
                            badgeColor = StadiumCyan,
                            iconEmoji = "🔍",
                            gradientColors = listOf(Color(0xFF006064), Color(0xFF00E5FF)),
                            testTag = "game_four_clues",
                            onClick = { viewModel.startFourCluesGame() },
                            modifier = Modifier.weight(1f)
                        )

                        GridGameCard(
                            title = "ديربي التجمعات",
                            subtitle = "أحمر 🔴 ضد أزرق 🔵 على جهاز واحد بجولات ونتائج حية",
                            badgeText = "فريق ضد فريق 👥",
                            badgeColor = Color(0xFFFF8A80),
                            iconEmoji = "👥",
                            gradientColors = listOf(Color(0xFF880E4F), Color(0xFF2979FF)),
                            testTag = "game_pass_and_play",
                            onClick = { viewModel.startPassAndPlay() },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Row 6: مين الجاسوس؟ & صراحة أو جرأة
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        GridGameCard(
                            title = "مين الجاسوس؟",
                            subtitle = "لعبة برا السالفة الشهيرة في الجلسات: اكشفوا الجاسوس!",
                            badgeText = "برا السالفة 🕵️‍♂️",
                            badgeColor = Color(0xFFFF5252),
                            iconEmoji = "🕵️‍♂️",
                            gradientColors = listOf(Color(0xFF263238), Color(0xFF455A64)),
                            testTag = "game_spy",
                            onClick = { viewModel.navigateTo(GameScreen.SpyGameScreen) },
                            modifier = Modifier.weight(1f)
                        )

                        GridGameCard(
                            title = "صراحة أو جرأة",
                            subtitle = "لف الزجاجة 🍾 وأسئلة صراحة قوية وتحديات جريئة وممتعة",
                            badgeText = "ضحك وحماس 🔥",
                            badgeColor = TrophyGold,
                            iconEmoji = "💬",
                            gradientColors = listOf(Color(0xFF880E4F), Color(0xFFFF4081)),
                            testTag = "game_truth_or_dare",
                            onClick = { viewModel.navigateTo(GameScreen.TruthOrDareScreen) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // =============================================================
            // SECTION 2: ألعاب ملعب كرة القدم
            // =============================================================
            if (selectedCategoryFilter == "all" || selectedCategoryFilter == "football") {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = "⚽", fontSize = 20.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "ألعاب ملعب كرة القدم",
                                style = MaterialTheme.typography.titleLarge,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(EmeraldNeon.copy(alpha = 0.15f))
                                .border(1.dp, EmeraldNeon.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "12 تحدياً كروياً وشبكياً 🔥",
                                style = MaterialTheme.typography.bodySmall,
                                color = EmeraldNeon,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Row 1: تحدي الثلاثين & صباحو تحدي
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        GridGameCard(
                            title = "تحدي الثلاثين",
                            subtitle = "30 ثانية لسرعة البديهة والأسئلة ونجوم الفريق",
                            badgeText = "رقمك: ${userProfile.thirtySecondsHighScore}",
                            badgeColor = EmeraldNeon,
                            iconEmoji = "⏱️",
                            gradientColors = listOf(Color(0xFF004D40), Color(0xFF00BFA5)),
                            testTag = "game_thirty_seconds",
                            onClick = { viewModel.navigateTo(GameScreen.ThirtySecondsSelect) },
                            modifier = Modifier.weight(1f)
                        )

                        GridGameCard(
                            title = "صباحو تحدي",
                            subtitle = "3 مستويات تصاعدية مع 3 وسائل مساعدة تكتيكية",
                            badgeText = "${userProfile.sabahoTrophies} كؤوس",
                            badgeColor = TrophyGold,
                            iconEmoji = "🏆",
                            gradientColors = listOf(Color(0xFF5D4037), Color(0xFFFFB300)),
                            testTag = "game_sabaho",
                            onClick = { viewModel.startSabahoChallenge() },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Row 2: العب مع سري & مسيرة اللاعب
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        GridGameCard(
                            title = "العب مع سري",
                            subtitle = "تلميحات غامضة تُفتح تدريجياً، هل تكتشف النجم؟",
                            badgeText = "تلميحات ذكية",
                            badgeColor = StadiumCyan,
                            iconEmoji = "🕵️‍♂️",
                            gradientColors = listOf(Color(0xFF0D47A1), Color(0xFF00E5FF)),
                            testTag = "game_mystery",
                            onClick = { viewModel.startMysteryPlayerGame() },
                            modifier = Modifier.weight(1f)
                        )

                        GridGameCard(
                            title = "مسيرة اللاعب",
                            subtitle = "محطات الأندية مرتبة بالسنين، خمّن صاحب الإنجاز",
                            badgeText = "خط زمني",
                            badgeColor = GoldLight,
                            iconEmoji = "🗺️",
                            gradientColors = listOf(Color(0xFF311B92), Color(0xFF7C4DFF)),
                            testTag = "game_career",
                            onClick = { viewModel.startCareerChallenge() },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Row 3: صح أو غلط & ترتيب الأهداف
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        GridGameCard(
                            title = "صح أو غلط",
                            subtitle = "حقائق كروية خاطفة مع عداد سلسلة اللهب الناري",
                            badgeText = "سلسلة: ${userProfile.currentStreak}x",
                            badgeColor = CardRed,
                            iconEmoji = "⚡",
                            gradientColors = listOf(Color(0xFF4A148C), Color(0xFFFF1744)),
                            testTag = "game_true_false",
                            onClick = { viewModel.startTrueFalseGame() },
                            modifier = Modifier.weight(1f)
                        )

                        GridGameCard(
                            title = "ترتيب الأحداث",
                            subtitle = "رتب النهائيات والأهداف تاريخياً بالأسهم والترتيب",
                            badgeText = "تحدي تكتيكي",
                            badgeColor = EmeraldLight,
                            iconEmoji = "📋",
                            gradientColors = listOf(Color(0xFF1B5E20), Color(0xFF00E676)),
                            testTag = "game_ordering",
                            onClick = { viewModel.startEventOrderingGame() },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Row 4: تيك تاك تو كروي & المزاد الكروي
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        GridGameCard(
                            title = "تيك تاك تو كروي",
                            subtitle = "شبكة 3x3 لتقاطع الأندية والمنتخبات.. سمّ اللاعب وسيطر!",
                            badgeText = "شعبية طاغية ⚽",
                            badgeColor = EmeraldNeon,
                            iconEmoji = "⚽",
                            gradientColors = listOf(Color(0xFF004D40), Color(0xFF00E676)),
                            testTag = "game_tictactoe",
                            onClick = { viewModel.navigateTo(GameScreen.FootballTicTacToeScreen) },
                            modifier = Modifier.weight(1f)
                        )

                        GridGameCard(
                            title = "المزاد الكروي",
                            subtitle = "مزايدة نارية: أقدر أقول 5! ومن يرسو عليه يثبت جدارته بالمايك",
                            badgeText = "مزايدة وتحدي 🔨",
                            badgeColor = TrophyGold,
                            iconEmoji = "🔨",
                            gradientColors = listOf(Color(0xFF4E342E), Color(0xFFFFB300)),
                            testTag = "game_auction",
                            onClick = { viewModel.navigateTo(GameScreen.FootballAuctionScreen) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Row 5: تشكيلة النهائي & اكتشف الدخيل
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        GridGameCard(
                            title = "تشكيلة النهائي",
                            subtitle = "تشكيلات نهائيات المونديال والأبطال مع نجم مجهول لاكتشافه",
                            badgeText = "اللاعب الناقص ❓",
                            badgeColor = StadiumCyan,
                            iconEmoji = "🏟️",
                            gradientColors = listOf(Color(0xFF1A237E), Color(0xFF00E5FF)),
                            testTag = "game_missing_xi",
                            onClick = { viewModel.navigateTo(GameScreen.MissingXIScreen) },
                            modifier = Modifier.weight(1f)
                        )

                        GridGameCard(
                            title = "اكتشف الدخيل",
                            subtitle = "4 نجوم أو أندية بينهم سر مشترك وعنصر وحيد شاذ.. اكتشفه!",
                            badgeText = "ذكاء وتدقيق 🔍",
                            badgeColor = Color(0xFFE040FB),
                            iconEmoji = "🔍",
                            gradientColors = listOf(Color(0xFF311B92), Color(0xFFAB47BC)),
                            testTag = "game_odd_one_out",
                            onClick = { viewModel.navigateTo(GameScreen.OddOneOutScreen) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Row 6: أرقام القمصان والانتقالات & فقرات نصوح
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        GridGameCard(
                            title = "أرقام وانتقالات",
                            subtitle = "أرقام القمصان الأيقونية ومقارنة أضخم الصفقات والمليارات",
                            badgeText = "أرقام قياسية 🔢",
                            badgeColor = TrophyGold,
                            iconEmoji = "🔢",
                            gradientColors = listOf(Color(0xFFBF360C), Color(0xFFFF7043)),
                            testTag = "game_jersey_transfers",
                            onClick = { viewModel.navigateTo(GameScreen.JerseyAndTransfersScreen) },
                            modifier = Modifier.weight(1f)
                        )

                        GridGameCard(
                            title = "فقرات نصوح",
                            subtitle = "صراع الجرس 🔔، سين جيم ❓، مهنة لاعب 🗺️، ووقت التعويض 🔥",
                            badgeText = "تحدي الثلاثين ⏱️",
                            badgeColor = CardRed,
                            iconEmoji = "🔔",
                            gradientColors = listOf(Color(0xFF880E4F), Color(0xFFFF1744)),
                            testTag = "game_nasouh_rounds",
                            onClick = { viewModel.navigateTo(GameScreen.ThirtySecondsRoundsScreen) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }

    if (showRewardedAd) {
        RewardedAdDialog(
            countdown = adCountdown,
            onClaim = { viewModel.claimAdReward() },
            onDismiss = { viewModel.dismissAd() }
        )
    }

    if (showDifficultyDialog) {
        DifficultySelectionDialog(
            currentDifficulty = selectedDifficulty,
            onSelectDifficulty = { viewModel.setDifficulty(it) },
            onConfirm = { viewModel.confirmDifficultyAndStart(it) },
            onDismiss = { viewModel.dismissDifficultySelector() }
        )
    }

    if (showOfflineDialog) {
        OfflineModeDialog(
            isOfflineMode = isOfflineMode,
            cachedQuestionsCount = cachedQuestionsCount,
            pendingSyncCount = pendingSyncCount,
            isSyncing = isSyncing,
            lastSyncResult = lastSyncResult,
            onToggleOfflineMode = { viewModel.toggleOfflineMode() },
            onSyncNow = { viewModel.syncOfflineRecords() },
            onDismissResult = { viewModel.dismissLastSyncResult() },
            onDismiss = { viewModel.setShowOfflineDialog(false) }
        )
    }
}

@Composable
fun PvPChallengeBanner(
    onStartPvP: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pvp_border")
    val borderAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = EaseInOutQuad),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(Color(0xFF1A0A00), Color(0xFF381400), Color(0xFF0F261B))
                )
            )
            .border(2.dp, TrophyGold.copy(alpha = borderAlpha), RoundedCornerShape(20.dp))
            .clickable { onStartPvP() }
            .padding(16.dp)
            .testTag("pvp_banner")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF5A1E00))
                        .border(2.dp, TrophyGold, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "⚔️", fontSize = 24.sp)
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "المواجهة المباشرة (PvP)",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(CardRed)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "مباشر 🔴",
                                color = Color.White,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = "تحدَّ مشجعاً حقيقياً وجهاً لوجه مع لوحة نتائج حية!",
                        style = MaterialTheme.typography.bodySmall,
                        color = TrophyGold,
                        fontSize = 11.sp
                    )
                }
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(TrophyGold)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "العب ⚔️",
                    color = PitchBlack,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
        }
    }
}

@Composable
fun OfflineStatusBanner(
    isOfflineMode: Boolean,
    cachedCount: Int,
    pendingCount: Int,
    isSyncing: Boolean,
    onOpenOfflineDialog: () -> Unit,
    onSyncClick: () -> Unit
) {
    Surface(
        color = PitchCard,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(
            1.dp,
            if (isOfflineMode) GoldAccent.copy(alpha = 0.5f) else EmeraldGreen.copy(alpha = 0.3f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenOfflineDialog() }
            .testTag("offline_status_banner")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Action button
            if (pendingCount > 0) {
                Button(
                    onClick = onSyncClick,
                    enabled = !isSyncing,
                    colors = ButtonDefaults.buttonColors(containerColor = GoldAccent),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier.testTag("banner_sync_btn")
                ) {
                    Text(
                        text = if (isSyncing) "جاري... 🔄" else "مزامنة ($pendingCount) 🔄",
                        color = Color.Black,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                }
            } else {
                Surface(
                    color = Color.White.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.clickable { onOpenOfflineDialog() }
                ) {
                    Text(
                        text = "إعدادات الأوفلاين ⚙️",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            // Info & Status
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(horizontalAlignment = Alignment.End) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (isOfflineMode) "وضع بدون إنترنت نشط 📴" else "متصل بالسيرفر 📶",
                            color = if (isOfflineMode) GoldAccent else EmeraldGreen,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                    Text(
                        text = if (pendingCount > 0)
                            "$pendingCount مباريات معلقة • $cachedCount سؤال في Room"
                        else
                            "$cachedCount سؤال مخزن محلياً في Room Database",
                        color = TextSecondary,
                        fontSize = 10.sp
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(if (isOfflineMode) GoldAccent.copy(alpha = 0.15f) else EmeraldGreen.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isOfflineMode) "📴" else "💾",
                        fontSize = 18.sp
                    )
                }
            }
        }
    }
}

@Composable
fun FriendInviteBanner(
    myCode: String,
    onOpenInvite: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "invite_border")
    val borderAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = EaseInOutQuad),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_alpha"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(Color(0xFF0D251D), Color(0xFF1F2F12), Color(0xFF261D09))
                )
            )
            .border(1.5.dp, GoldAccent.copy(alpha = borderAlpha), RoundedCornerShape(20.dp))
            .clickable { onOpenInvite() }
            .padding(16.dp)
            .testTag("friend_invite_hero_card")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Button(
                onClick = onOpenInvite,
                colors = ButtonDefaults.buttonColors(
                    containerColor = GoldAccent,
                    contentColor = Color.Black
                ),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                modifier = Modifier.testTag("invite_cta_button")
            ) {
                Text(
                    text = "تحدَّ صديقك ⚔️",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f).padding(start = 12.dp),
                horizontalArrangement = Arrangement.End
            ) {
                Column(horizontalAlignment = Alignment.End) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = GoldAccent.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = myCode,
                                color = GoldAccent,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "دعوة صديق ومواجهة ودية 🤝",
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "شارك رمزك الخاص عبر واتساب وتواصل للعب وجهاً لوجه!",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        textAlign = TextAlign.End
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Brush.radialGradient(listOf(GoldAccent.copy(alpha = 0.3f), Color.Transparent))),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "🤝", fontSize = 24.sp)
                }
            }
        }
    }
}


@Composable
fun DifficultySelectorBar(
    currentDifficulty: com.example.data.GameDifficulty,
    onChangeClick: () -> Unit
) {
    val diffColor = Color(currentDifficulty.colorHex)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(PitchCard)
            .border(1.dp, diffColor.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
            .clickable { onChangeClick() }
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .testTag("difficulty_selector_bar")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = currentDifficulty.iconEmoji, fontSize = 20.sp)
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "مستوى الصعوبة الحالي:",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                        fontSize = 10.sp
                    )
                    Text(
                        text = currentDifficulty.titleArabic,
                        style = MaterialTheme.typography.titleSmall,
                        color = diffColor,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (currentDifficulty.pointsBonusPercent > 0) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(diffColor.copy(alpha = 0.2f))
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "+${currentDifficulty.pointsBonusPercent}% نقاط",
                            fontSize = 10.sp,
                            color = diffColor,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(PitchDark)
                        .border(1.dp, PitchCardBorder, RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "تغيير ⚙️",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
fun StadiumHeroCard(
    playerName: String,
    favoriteClub: String,
    levelTitle: String,
    levelNumber: Int,
    levelProgress: Float,
    streak: Int,
    onAdClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .clip(RoundedCornerShape(24.dp))
            .border(1.5.dp, TrophyGold.copy(alpha = 0.6f), RoundedCornerShape(24.dp))
    ) {
        // Stadium background
        Image(
            painter = painterResource(id = R.drawable.stadium_hero_bg),
            contentDescription = "ملعب البطولة",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        // Gradient dark overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            PitchBlack.copy(alpha = 0.45f),
                            PitchDark.copy(alpha = 0.8f),
                            PitchBlack.copy(alpha = 0.95f)
                        )
                    )
                )
        )

        // Player Info Overlay
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(PitchCard)
                            .border(2.dp, TrophyGold, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "👑", fontSize = 20.sp)
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = playerName,
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "$levelTitle • $favoriteClub",
                            style = MaterialTheme.typography.bodySmall,
                            color = TrophyGold
                        )
                    }
                }

                // Daily Streak Badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF3E1E02))
                        .border(1.dp, CardYellow, RoundedCornerShape(12.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "🔥", fontSize = 14.sp)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "$streak أيام",
                            color = CardYellow,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            // Pitch Info Footer
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(PitchCard.copy(alpha = 0.7f))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "🏟️", fontSize = 16.sp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "ملعب التحدي جاهز للمنافسة!",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Text(
                    text = "المستوى $levelNumber",
                    color = EmeraldNeon,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
fun DailyBoosterBanner(
    coins: Int,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(Color(0xFF2E1C03), Color(0xFF4A3004), Color(0xFF1E1402))
                )
            )
            .border(1.dp, TrophyGold.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "🎁", fontSize = 22.sp)
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "مكافأة الملعب السريعة",
                        color = TrophyGold,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Text(
                        text = "شاهد 5 ثوانٍ واحصل على ذهب ومساعدات مجانية",
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                }
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(TrophyGold)
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "شاهد 📺",
                    color = PitchBlack,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
fun StadiumBottomNavBar(
    activeTab: String,
    onTabSelect: (String) -> Unit
) {
    Surface(
        color = PitchSurface,
        tonalElevation = 8.dp,
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            BottomNavItem(
                icon = "🏟️",
                label = "الرئيسية",
                isSelected = activeTab == "home",
                onClick = { onTabSelect("home") }
            )
            BottomNavItem(
                icon = "🏆",
                label = "المتصدرين",
                isSelected = activeTab == "leaderboard",
                onClick = { onTabSelect("leaderboard") }
            )
            BottomNavItem(
                icon = "👤",
                label = "الملف",
                isSelected = activeTab == "profile",
                onClick = { onTabSelect("profile") }
            )
        }
    }
}

@Composable
fun BottomNavItem(
    icon: String,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Text(
            text = icon,
            fontSize = 20.sp
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            fontSize = 12.sp,
            color = if (isSelected) TrophyGold else TextMuted,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
fun SabahoNotificationBanner(
    onTestNotification: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(Color(0xFF0B2416), Color(0xFF133621))
                )
            )
            .border(1.dp, EmeraldNeon.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "🔔", fontSize = 22.sp)
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "تذكير يومي لـ 'صباحو تحدي'",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Text(
                        text = "مفعل تلقائياً للحفاظ على سلسلتك 🔥",
                        color = EmeraldLight,
                        fontSize = 11.sp
                    )
                }
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(EmeraldNeon.copy(alpha = 0.2f))
                    .border(1.dp, EmeraldNeon, RoundedCornerShape(8.dp))
                    .clickable { onTestNotification() }
                    .padding(horizontal = 8.dp, vertical = 5.dp)
            ) {
                Text(
                    text = "اختبر الآن ⚡",
                    color = EmeraldNeon,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
fun PartyLeaderboardPromoBanner(onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("party_leaderboard_promo_banner"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = PitchCardElevated),
        border = BorderStroke(
            1.5.dp,
            Brush.horizontalGradient(listOf(TrophyGold, EmeraldNeon))
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(listOf(Color(0xFFE65100), TrophyGold))
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "🏆", fontSize = 24.sp)
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "لوحة الصدارة والأرقام القياسية",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(TrophyGold)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "Room 💾",
                                color = PitchDark,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = "النتائج التراكمية وأبطال الألعاب الجماعية عبر SQLite",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted,
                        maxLines = 1
                    )
                }
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(TrophyGold.copy(alpha = 0.2f))
                    .border(1.dp, TrophyGold, RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "عرض 📊",
                    color = TrophyGold,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
        }
    }
}


