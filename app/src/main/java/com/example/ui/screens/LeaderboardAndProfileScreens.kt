package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.Badge
import com.example.data.LeaderboardUser
import com.example.data.MatchHistoryItem
import com.example.ui.FootballGameViewModel
import com.example.ui.components.EmeraldButton
import com.example.ui.components.GlowCard
import com.example.ui.components.StadiumTopBar
import com.example.ui.components.TrophyButton
import com.example.ui.theme.*

@Composable
fun LeaderboardScreen(viewModel: FootballGameViewModel) {
    val userProfile by viewModel.userProfile.collectAsState()
    val leaderboard = remember(userProfile.points, userProfile.avatarEmoji) {
        viewModel.repository.getLeaderboard()
    }

    Scaffold(
        containerColor = PitchDark,
        topBar = {
            StadiumTopBar(
                title = "لوحة المتصدرين 🏆",
                coins = userProfile.coins,
                points = userProfile.points,
                onBack = { viewModel.navigateBack() }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Podium for Top 3
            item {
                PodiumSection(leaderboard.take(3))
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "ترتيب أبطال التحدي العرب والأوسمة:",
                        style = MaterialTheme.typography.titleSmall,
                        color = TextSecondary
                    )
                    Text(
                        text = "أوسمة الشرف 🎖️",
                        style = MaterialTheme.typography.labelSmall,
                        color = TrophyGold
                    )
                }
            }

            items(leaderboard) { player ->
                val isCurrent = player.isCurrentUser
                val rankBadge = when (player.rank) {
                    1 -> "🥇"
                    2 -> "🥈"
                    3 -> "🥉"
                    else -> "${player.rank}"
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (isCurrent) PitchCardElevated else PitchCard)
                        .border(
                            1.5.dp,
                            if (isCurrent) TrophyGold else PitchCardBorder,
                            RoundedCornerShape(16.dp)
                        )
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = rankBadge,
                                fontSize = if (player.rank <= 3) 22.sp else 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (player.rank <= 3) TrophyGold else TextSecondary,
                                modifier = Modifier.width(32.dp)
                            )

                            // Avatar Profile Image / Icon
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(PitchDark)
                                    .border(1.5.dp, if (isCurrent) TrophyGold else PitchCardBorder, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = player.avatarEmoji, fontSize = 20.sp)
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = player.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = if (isCurrent) TrophyGold else Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (isCurrent) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(TrophyGold)
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = "أنت",
                                                color = PitchBlack,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }

                                Text(
                                    text = "${player.levelBadge} • ${player.club}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextMuted
                                )

                                // Badges row in Leaderboard!
                                if (player.badges.isNotEmpty()) {
                                    Row(
                                        modifier = Modifier.padding(top = 3.dp),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        player.badges.forEach { badgeIcon ->
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(PitchDark)
                                                    .border(0.8.dp, TrophyGold.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                                                .padding(horizontal = 4.dp, vertical = 1.dp)
                                            ) {
                                                Text(text = badgeIcon, fontSize = 11.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "${player.points}",
                                style = MaterialTheme.typography.titleMedium,
                                color = EmeraldNeon,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "نقطة ⭐",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextMuted,
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PodiumSection(topThree: List<LeaderboardUser>) {
    val first = topThree.getOrNull(0)
    val second = topThree.getOrNull(1)
    val third = topThree.getOrNull(2)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.Bottom
    ) {
        // 2nd Place
        if (second != null) {
            PodiumPillar(player = second, rank = 2, height = 130.dp, medal = "🥈", color = Color(0xFFC0C0C0))
        }

        // 1st Place (Center & Highest)
        if (first != null) {
            PodiumPillar(player = first, rank = 1, height = 160.dp, medal = "🥇", color = TrophyGold)
        }

        // 3rd Place
        if (third != null) {
            PodiumPillar(player = third, rank = 3, height = 110.dp, medal = "🥉", color = Color(0xFFCD7F32))
        }
    }
}

@Composable
fun PodiumPillar(
    player: LeaderboardUser,
    rank: Int,
    height: androidx.compose.ui.unit.Dp,
    medal: String,
    color: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(100.dp)
    ) {
        Text(text = medal, fontSize = 24.sp)
        Text(
            text = player.name.split(" ").firstOrNull() ?: player.name,
            style = MaterialTheme.typography.labelMedium,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
        Text(
            text = "${player.points} ن",
            style = MaterialTheme.typography.labelSmall,
            color = color
        )
        Spacer(modifier = Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(color.copy(alpha = 0.4f), PitchCard)
                    )
                )
                .border(1.dp, color.copy(alpha = 0.6f), RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "$rank",
                fontSize = 28.sp,
                fontWeight = FontWeight.Black,
                color = color
            )
        }
    }
}

@Composable
fun ProfileScreen(viewModel: FootballGameViewModel) {
    val userProfile by viewModel.userProfile.collectAsState()
    var showEditDialog by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = PitchDark,
        topBar = {
            StadiumTopBar(
                title = "الملف الشخصي 👤",
                coins = userProfile.coins,
                points = userProfile.points,
                onBack = { viewModel.navigateBack() }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Profile Card with Avatar
            item {
                GlowCard(
                    modifier = Modifier.fillMaxWidth(),
                    borderColor = TrophyGold.copy(alpha = 0.5f),
                    backgroundColor = PitchCardElevated
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .clip(CircleShape)
                                .background(PitchDark)
                                .border(2.5.dp, TrophyGold, CircleShape)
                                .clickable { showEditDialog = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = userProfile.avatarEmoji, fontSize = 42.sp)
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = userProfile.playerName,
                                style = MaterialTheme.typography.headlineSmall,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            IconButton(
                                onClick = { showEditDialog = true },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "تعديل الملف",
                                    tint = TrophyGold
                                )
                            }
                        }

                        Text(
                            text = userProfile.levelTitle,
                            style = MaterialTheme.typography.titleMedium,
                            color = TrophyGold
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = "النادي المفضل: ${userProfile.favoriteClub} ⚽",
                            style = MaterialTheme.typography.bodySmall,
                            color = EmeraldNeon
                        )
                    }
                }
            }

            // Stats Grid
            item {
                Text(
                    text = "سجل الإحصائيات الشخصية:",
                    style = MaterialTheme.typography.titleSmall,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StatBox(label = "إجمالي المباريات", value = "${userProfile.gamesPlayed}", icon = "🎮", modifier = Modifier.weight(1f))
                    StatBox(label = "نسبة الفوز", value = "${userProfile.winRate}%", icon = "📈", modifier = Modifier.weight(1f))
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StatBox(label = "أعلى سلسلة", value = "${userProfile.bestStreak}x", icon = "🔥", modifier = Modifier.weight(1f))
                    StatBox(label = "رقم الـ 30 ثانية", value = "${userProfile.thirtySecondsHighScore}", icon = "⏱️", modifier = Modifier.weight(1f))
                }
            }

            // Badges System Section
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "أوسمة الشرف والإنجازات (Badges) 🎖️",
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${userProfile.earnedBadges.count { it.isUnlocked }} من ${userProfile.earnedBadges.size} مكتملة",
                        style = MaterialTheme.typography.labelSmall,
                        color = TrophyGold
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    userProfile.earnedBadges.forEach { badge ->
                        BadgeProgressItem(badge = badge)
                    }
                }
            }

            // Victory History Section
            item {
                Text(
                    text = "سجل وتاريخ الفوز الأخير (Victory History) 📜",
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    userProfile.matchHistory.forEach { history ->
                        MatchHistoryRow(history = history)
                    }
                }
            }
        }
    }

    if (showEditDialog) {
        EditProfileDialog(
            currentName = userProfile.playerName,
            currentClub = userProfile.favoriteClub,
            currentAvatar = userProfile.avatarEmoji,
            onSave = { name, club, avatar ->
                viewModel.repository.updateProfile(name, club, avatar)
                viewModel.audioEffects.playCorrect()
                showEditDialog = false
            },
            onDismiss = { showEditDialog = false }
        )
    }
}

@Composable
fun BadgeProgressItem(badge: Badge) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (badge.isUnlocked) PitchCard else PitchDark)
            .border(
                1.dp,
                if (badge.isUnlocked) EmeraldNeon.copy(alpha = 0.5f) else PitchCardBorder,
                RoundedCornerShape(16.dp)
            )
            .padding(14.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(if (badge.isUnlocked) EmeraldNeon.copy(alpha = 0.2f) else PitchCard),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = badge.iconEmoji,
                            fontSize = 20.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = badge.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = if (badge.isUnlocked) Color.White else TextMuted,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = badge.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (badge.isUnlocked) EmeraldNeon.copy(alpha = 0.2f) else PitchCard)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (badge.isUnlocked) "مكتسب 🎖️" else badge.progressText,
                        color = if (badge.isUnlocked) EmeraldNeon else TrophyGold,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            LinearProgressIndicator(
                progress = { badge.progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(RoundedCornerShape(2.5.dp)),
                color = if (badge.isUnlocked) EmeraldNeon else TrophyGold,
                trackColor = PitchCardBorder
            )
        }
    }
}

@Composable
fun MatchHistoryRow(history: MatchHistoryItem) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(PitchCard)
            .border(1.dp, PitchCardBorder, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = history.gameTitle,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${history.resultBadge} • ${history.timeAgo}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    fontSize = 11.sp
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "+${history.earnedPoints} ⭐",
                    color = EmeraldNeon,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "+${history.earnedCoins} 🪙",
                    color = TrophyGold,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
        }
    }
}

@Composable
fun StatBox(
    label: String,
    value: String,
    icon: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(PitchCard)
            .border(1.dp, PitchCardBorder, RoundedCornerShape(16.dp))
            .padding(14.dp)
    ) {
        Column {
            Text(text = icon, fontSize = 20.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                color = TrophyGold,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }
    }
}

@Composable
fun EditProfileDialog(
    currentName: String,
    currentClub: String,
    currentAvatar: String,
    onSave: (String, String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(currentName) }
    var club by remember { mutableStateOf(currentClub) }
    var selectedAvatar by remember { mutableStateOf(currentAvatar) }

    val avatarOptions = listOf("👑", "🦁", "🦅", "⚡", "🧤", "🎯", "⚽", "⭐")

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(PitchSurface)
                .border(2.dp, TrophyGold, RoundedCornerShape(24.dp))
                .padding(20.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "تعديل الملف الشخصي 👤",
                        style = MaterialTheme.typography.titleLarge,
                        color = TrophyGold,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "إغلاق", tint = TextMuted)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "اختر صورتك الرمزية:",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    avatarOptions.forEach { avatar ->
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(if (selectedAvatar == avatar) TrophyGold.copy(alpha = 0.3f) else PitchCard)
                                .border(
                                    1.5.dp,
                                    if (selectedAvatar == avatar) TrophyGold else Color.Transparent,
                                    CircleShape
                                )
                                .clickable { selectedAvatar = avatar },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = avatar, fontSize = 20.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("اسم اللاعب", color = TrophyGold) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = TrophyGold,
                        unfocusedBorderColor = PitchCardBorder,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = club,
                    onValueChange = { club = it },
                    label = { Text("النادي المفضل", color = EmeraldNeon) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = EmeraldNeon,
                        unfocusedBorderColor = PitchCardBorder,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(20.dp))

                TrophyButton(
                    text = "حفظ التغييرات 💾",
                    onClick = { onSave(name, club, selectedAvatar) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
