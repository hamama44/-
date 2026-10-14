package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
            // PvP Real-Time Match Banner (المواجهة المباشرة)
            // -------------------------------------------------------------
            item {
                PvPChallengeBanner(
                    onStartPvP = { viewModel.startPvPMatchSearch() }
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

            // Grid Section Header
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
                            text = "ألعاب ملعب التحدي",
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
                            text = "6 تحديات شبكية",
                            style = MaterialTheme.typography.bodySmall,
                            color = EmeraldNeon,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // -------------------------------------------------------------
            // 2-Column Grid Layout with Scale Animations upon Press
            // -------------------------------------------------------------

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

