package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
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
import com.example.data.room.CumulativePlayerStat
import com.example.data.room.GameHighScoreStat
import com.example.data.room.PartyLeaderboardRecordEntity
import com.example.ui.FootballGameViewModel
import com.example.ui.components.EmeraldButton
import com.example.ui.components.GlowCard
import com.example.ui.components.StadiumTopBar
import com.example.ui.components.TrophyButton
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LeaderboardScreen(viewModel: FootballGameViewModel) {
    val userProfile by viewModel.userProfile.collectAsState()
    val cumulativePlayers by viewModel.cumulativePartyLeaderboard.collectAsState()
    val gameHighScores by viewModel.gameHighScores.collectAsState()
    val partyRecords by viewModel.allPartyRecords.collectAsState()
    val currentFilter by viewModel.leaderboardCategoryFilter.collectAsState()
    val selectedTab by viewModel.leaderboardTab.collectAsState()

    var showAddDialog by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = PitchDark,
        topBar = {
            StadiumTopBar(
                title = "لوحة الصدارة والأرقام القياسية 🏆",
                coins = userProfile.coins,
                points = userProfile.points,
                onBack = { viewModel.navigateBack() }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = EmeraldNeon,
                contentColor = PitchDark,
                modifier = Modifier.testTag("add_record_fab")
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "تسجيل رقم قياسي ✍️",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 80.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Room DB Status Badge Card
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(PitchCardElevated)
                        .border(1.dp, PitchCardBorder, RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = "💾", fontSize = 16.sp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "بيانات Room Database SQLite • ${partyRecords.size} نتيجة مسجلة",
                                style = MaterialTheme.typography.labelSmall,
                                color = StadiumCyan,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        IconButton(
                            onClick = { showResetDialog = true },
                            modifier = Modifier.size(28.dp).testTag("reset_leaderboard_btn")
                        ) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = "إعادة ضبط",
                                tint = TrophyGold,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            // Top Navigation Tabs: (0: الترتيب التراكمي, 1: أرقام الألعاب, 2: سجل المباريات)
            item {
                PrimaryTabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = PitchDark,
                    contentColor = TrophyGold,
                    indicator = {
                        TabRowDefaults.PrimaryIndicator(
                            color = TrophyGold,
                            modifier = Modifier.tabIndicatorOffset(selectedTabIndex = selectedTab)
                        )
                    },
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { viewModel.setLeaderboardTab(0) },
                        text = {
                            Text(
                                text = "🌟 التراكمي (${cumulativePlayers.size})",
                                fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal,
                                color = if (selectedTab == 0) TrophyGold else Color.White.copy(alpha = 0.7f),
                                fontSize = 13.sp
                            )
                        }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { viewModel.setLeaderboardTab(1) },
                        text = {
                            Text(
                                text = "🏆 الأرقام القياسية (${gameHighScores.size})",
                                fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal,
                                color = if (selectedTab == 1) TrophyGold else Color.White.copy(alpha = 0.7f),
                                fontSize = 13.sp
                            )
                        }
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { viewModel.setLeaderboardTab(2) },
                        text = {
                            Text(
                                text = "📜 السجل (${partyRecords.size})",
                                fontWeight = if (selectedTab == 2) FontWeight.Bold else FontWeight.Normal,
                                color = if (selectedTab == 2) TrophyGold else Color.White.copy(alpha = 0.7f),
                                fontSize = 13.sp
                            )
                        }
                    )
                }
            }

            // =================================================================
            // TAB 0: الترتيب التراكمي العام لكافة الألعاب
            // =================================================================
            if (selectedTab == 0) {
                // Category Filter Pills
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = currentFilter == "all",
                            onClick = { viewModel.setLeaderboardCategoryFilter("all") },
                            label = { Text("🌟 الكل") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = TrophyGold,
                                selectedLabelColor = PitchDark,
                                containerColor = PitchCard,
                                labelColor = Color.White
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = currentFilter == "party",
                            onClick = { viewModel.setLeaderboardCategoryFilter("party") },
                            label = { Text("🎉 صالون وتسلية") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFFFF9100),
                                selectedLabelColor = Color.White,
                                containerColor = PitchCard,
                                labelColor = Color.White
                            ),
                            modifier = Modifier.weight(1.3f)
                        )
                        FilterChip(
                            selected = currentFilter == "football",
                            onClick = { viewModel.setLeaderboardCategoryFilter("football") },
                            label = { Text("⚽ كرة قدم") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = EmeraldNeon,
                                selectedLabelColor = PitchDark,
                                containerColor = PitchCard,
                                labelColor = Color.White
                            ),
                            modifier = Modifier.weight(1.1f)
                        )
                    }
                }

                // Top 3 Podium
                if (cumulativePlayers.isNotEmpty()) {
                    item {
                        CumulativePodiumSection(cumulativePlayers.take(3))
                    }
                }

                // List header
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "ترتيب اللاعبين التراكمي (المحفوظ في Room):",
                            style = MaterialTheme.typography.titleSmall,
                            color = TextSecondary
                        )
                        Text(
                            text = "إجمالي النقاط ⭐",
                            style = MaterialTheme.typography.labelSmall,
                            color = TrophyGold
                        )
                    }
                }

                // Player cards
                items(cumulativePlayers.size) { index ->
                    val player = cumulativePlayers[index]
                    val rank = index + 1
                    val rankBadge = when (rank) {
                        1 -> "🥇"
                        2 -> "🥈"
                        3 -> "🥉"
                        else -> "$rank"
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (player.isCurrentUser) PitchCardElevated else PitchCard)
                            .border(
                                1.5.dp,
                                if (player.isCurrentUser) TrophyGold else PitchCardBorder,
                                RoundedCornerShape(16.dp)
                            )
                            .padding(horizontal = 14.dp, vertical = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = rankBadge,
                                    fontSize = if (rank <= 3) 22.sp else 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (rank <= 3) TrophyGold else TextSecondary,
                                    modifier = Modifier.width(32.dp)
                                )

                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(PitchDark)
                                        .border(
                                            1.5.dp,
                                            if (player.isCurrentUser) TrophyGold else PitchCardBorder,
                                            CircleShape
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(text = player.playerAvatar, fontSize = 22.sp)
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = player.playerName,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = if (player.isCurrentUser) TrophyGold else Color.White,
                                            fontWeight = FontWeight.Bold
                                        )
                                        if (player.isCurrentUser) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(TrophyGold)
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = "أنت 👤",
                                                    color = PitchBlack,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(2.dp))

                                    Text(
                                        text = "المفضلة: ${player.favoriteGame} • ${player.gamesPlayedCount} جولات",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextMuted
                                    )

                                    Text(
                                        text = "أعلى رقم قياسي فردي: ${player.highestScore} نقطة",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = EmeraldNeon.copy(alpha = 0.8f)
                                    )
                                }
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "${player.totalScore}",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = TrophyGold,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Text(
                                    text = "نقطة تراكمية",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextMuted,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                }
            }

            // =================================================================
            // TAB 1: أرقام الألعاب القياسية الفردية
            // =================================================================
            if (selectedTab == 1) {
                item {
                    Text(
                        text = "🏆 أعلى الأرقام القياسية المسجلة في كل لعبة:",
                        style = MaterialTheme.typography.titleSmall,
                        color = TrophyGold,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }

                items(gameHighScores.size) { index ->
                    val record = gameHighScores[index]
                    val gameIcon = getGameRecordIcon(record.gameType)

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = PitchCardElevated),
                        border = BorderStroke(1.dp, PitchCardBorder)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
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
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(PitchDark)
                                        .border(1.dp, TrophyGold.copy(alpha = 0.4f), RoundedCornerShape(12.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(text = gameIcon, fontSize = 24.sp)
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = record.gameType,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(if (record.category == "party") Color(0xFFFF9100).copy(alpha = 0.2f) else EmeraldNeon.copy(alpha = 0.2f))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = if (record.category == "party") "صالون" else "كرة قدم",
                                                color = if (record.category == "party") Color(0xFFFF9100) else EmeraldNeon,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(3.dp))

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(text = "👑 صاحب الرقم: ", fontSize = 11.sp, color = TextMuted)
                                        Text(text = record.playerAvatar, fontSize = 12.sp)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = record.playerName,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = TrophyGold,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    if (record.details.isNotBlank()) {
                                        Text(
                                            text = "« ${record.details} »",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.White.copy(alpha = 0.6f),
                                            fontSize = 10.sp
                                        )
                                    }
                                }
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "${record.highestScore}",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = EmeraldNeon,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Text(
                                    text = "الرقم القياسي 🎯",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TrophyGold,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                }
            }

            // =================================================================
            // TAB 2: سجل المباريات الكامل المحفوظ في SQLite Room
            // =================================================================
            if (selectedTab == 2) {
                item {
                    Text(
                        text = "📜 سجل النتائج المسجلة في Room Database:",
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }

                val dateFormat = SimpleDateFormat("d MMM - HH:mm", Locale("ar"))

                items(partyRecords.size) { index ->
                    val item = partyRecords[index]
                    val dateFormatted = try {
                        dateFormat.format(Date(item.timestamp))
                    } catch (e: Exception) {
                        "سابقاً"
                    }
                    val icon = getGameRecordIcon(item.gameType)

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(PitchCard)
                            .border(1.dp, PitchCardBorder, RoundedCornerShape(14.dp))
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(text = icon, fontSize = 22.sp)
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = item.gameType,
                                            style = MaterialTheme.typography.titleSmall,
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "(${item.playerAvatar} ${item.playerName})",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = TrophyGold
                                        )
                                    }
                                    if (item.details.isNotBlank()) {
                                        Text(
                                            text = item.details,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = TextMuted,
                                            fontSize = 11.sp
                                        )
                                    }
                                    Text(
                                        text = dateFormatted,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextMuted.copy(alpha = 0.6f),
                                        fontSize = 10.sp
                                    )
                                }
                            }

                            Text(
                                text = "+${item.score} ن",
                                style = MaterialTheme.typography.titleMedium,
                                color = EmeraldNeon,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }

    // Add Record Dialog
    if (showAddDialog) {
        AddPartyRecordDialog(
            currentUser = userProfile.playerName,
            onDismiss = { showAddDialog = false },
            onSave = { name, avatar, game, cat, score, details ->
                viewModel.addManualPartyRecord(name, avatar, game, cat, score, details)
                showAddDialog = false
            }
        )
    }

    // Reset Confirmation Dialog
    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = {
                Text(
                    text = "إعادة تعيين قاعدة بيانات الصدارة 🔄",
                    color = TrophyGold,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "هل ترغب في إعادة ضبط سجلات لوحة الصدارة المحفوظة في Room وإعادة شحن الأبطال الافتراضيين؟",
                    color = Color.White
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.resetPartyLeaderboard()
                        showResetDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5252))
                ) {
                    Text("نعم، إعادة ضبط", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("إلغاء", color = Color.White.copy(alpha = 0.7f))
                }
            },
            containerColor = PitchCardElevated,
            shape = RoundedCornerShape(16.dp)
        )
    }
}

@Composable
fun CumulativePodiumSection(topThree: List<CumulativePlayerStat>) {
    val first = topThree.getOrNull(0)
    val second = topThree.getOrNull(1)
    val third = topThree.getOrNull(2)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.Bottom
    ) {
        // 2nd Place
        if (second != null) {
            CumulativePodiumPillar(
                player = second,
                rank = 2,
                height = 135.dp,
                medal = "🥈",
                color = Color(0xFFC0C0C0)
            )
        }

        // 1st Place (Center & Highest)
        if (first != null) {
            CumulativePodiumPillar(
                player = first,
                rank = 1,
                height = 175.dp,
                medal = "🥇",
                color = TrophyGold
            )
        }

        // 3rd Place
        if (third != null) {
            CumulativePodiumPillar(
                player = third,
                rank = 3,
                height = 115.dp,
                medal = "🥉",
                color = Color(0xFFCD7F32)
            )
        }
    }
}

@Composable
fun CumulativePodiumPillar(
    player: CumulativePlayerStat,
    rank: Int,
    height: androidx.compose.ui.unit.Dp,
    medal: String,
    color: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(105.dp)
    ) {
        Text(text = medal, fontSize = 26.sp)
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(PitchDark)
                .border(1.dp, color, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(text = player.playerAvatar, fontSize = 18.sp)
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = player.playerName.split(" ").firstOrNull() ?: player.playerName,
            style = MaterialTheme.typography.labelMedium,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
        Text(
            text = "${player.totalScore} ن",
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.ExtraBold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(color.copy(alpha = 0.45f), PitchCard)
                    )
                )
                .border(1.5.dp, color.copy(alpha = 0.7f), RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "$rank",
                fontSize = 30.sp,
                fontWeight = FontWeight.Black,
                color = color
            )
        }
    }
}

@Composable
private fun AddPartyRecordDialog(
    currentUser: String,
    onDismiss: () -> Unit,
    onSave: (name: String, avatar: String, game: String, cat: String, score: Int, details: String) -> Unit
) {
    var name by remember { mutableStateOf(currentUser) }
    var avatar by remember { mutableStateOf("👑") }
    var selectedGame by remember { mutableStateOf("أتوبيس كومبليت") }
    var category by remember { mutableStateOf("party") }
    var scoreInput by remember { mutableStateOf("100") }
    var detailsInput by remember { mutableStateOf("رقم قياسي في الجلسة") }

    val gamesList = listOf(
        "أتوبيس كومبليت",
        "تحدي الـ 5 ثواني",
        "بدون كلام",
        "التابو العربي",
        "كمّل المثل الشعبي",
        "المليون العربي",
        "الرابط العجيب",
        "ديربي التجمعات",
        "فوازير الذكاء",
        "تحدي الثلاثين"
    )

    val avatars = listOf("👑", "🦁", "⚡", "🌟", "⚽", "🎯", "🚀", "💎", "🐺", "🏆")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "✍️ تسجيل رقم قياسي في Room",
                color = TrophyGold,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Name
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("اسم اللاعب / الصديق") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                // Avatar Choice
                Text(text = "اختر الأيقونة:", color = Color.White, fontSize = 12.sp)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(avatars) { av ->
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(if (avatar == av) TrophyGold.copy(alpha = 0.3f) else PitchDark)
                                .border(
                                    1.dp,
                                    if (avatar == av) TrophyGold else Color.DarkGray,
                                    CircleShape
                                )
                                .clickable { avatar = av },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = av, fontSize = 18.sp)
                        }
                    }
                }

                // Game selection
                Text(text = "اختر اللعبة:", color = Color.White, fontSize = 12.sp)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(gamesList) { g ->
                        FilterChip(
                            selected = selectedGame == g,
                            onClick = {
                                selectedGame = g
                                category = if (g == "تحدي الثلاثين") "football" else "party"
                            },
                            label = { Text(g, fontSize = 11.sp) }
                        )
                    }
                }

                // Score
                OutlinedTextField(
                    value = scoreInput,
                    onValueChange = { scoreInput = it.filter { char -> char.isDigit() } },
                    label = { Text("النقاط المحققة") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                // Details
                OutlinedTextField(
                    value = detailsInput,
                    onValueChange = { detailsInput = it },
                    label = { Text("تفاصيل / ملاحظة الإنجاز") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val s = scoreInput.toIntOrNull() ?: 50
                    onSave(name, avatar, selectedGame, category, s, detailsInput)
                },
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldNeon)
            ) {
                Text("حفظ في Room 💾", color = PitchDark, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء", color = Color.White.copy(alpha = 0.7f))
            }
        },
        containerColor = PitchCardElevated,
        shape = RoundedCornerShape(18.dp)
    )
}

fun getGameRecordIcon(gameType: String): String = when {
    gameType.contains("أتوبيس") -> "🚌"
    gameType.contains("5 ثواني") || gameType.contains("خمس ثواني") -> "⚡"
    gameType.contains("بدون كلام") -> "🎭"
    gameType.contains("التابو") -> "🚫"
    gameType.contains("المثل") -> "📜"
    gameType.contains("المليون") -> "💰"
    gameType.contains("الرابط") || gameType.contains("أربع") -> "🔍"
    gameType.contains("ديربي") || gameType.contains("فريق") -> "👥"
    gameType.contains("فوازير") -> "💡"
    gameType.contains("لو خيروك") -> "🤔"
    gameType.contains("الثلاثين") -> "⚽"
    gameType.contains("صباحو") -> "🎙️"
    gameType.contains("مسيرة") -> "🗺️"
    gameType.contains("صح") -> "✅"
    else -> "🏆"
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
