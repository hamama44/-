package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.FriendlyChallengeInfo
import com.example.data.FriendlyMatchHistoryItem
import com.example.data.GameDifficulty
import com.example.ui.FootballGameViewModel
import com.example.ui.components.DifficultySelectionDialog
import com.example.ui.components.EmeraldButton
import com.example.ui.components.GlowCard
import com.example.ui.components.TrophyButton
import com.example.ui.theme.*

@Composable
fun FriendInviteScreen(viewModel: FootballGameViewModel) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    val myCode by viewModel.userChallengeCode.collectAsState()
    val enteredCode by viewModel.enteredFriendCode.collectAsState()
    val friendsList by viewModel.friendlyFriendsList.collectAsState()
    val matchHistory by viewModel.friendlyMatchHistory.collectAsState()
    val selectedDifficulty by viewModel.selectedDifficulty.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) }
    var showDifficultyDialog by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = PitchDark,
        topBar = {
            Surface(
                color = PitchDark,
                tonalElevation = 6.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(
                        onClick = { viewModel.navigateBack() },
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(PitchCard)
                            .testTag("friend_invite_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "رجوع",
                            tint = Color.White
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "دعوة صديق ومواجهة ودية 🤝",
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Text(
                            text = "تحدَّ أصدقاءك وجهاً لوجه برموز التحدي",
                            color = EmeraldGreen,
                            fontSize = 12.sp
                        )
                    }

                    // Share shortcut button
                    IconButton(
                        onClick = { viewModel.shareChallengeCode(context) },
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(GoldAccent.copy(alpha = 0.15f))
                            .testTag("top_share_code_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "مشاركة",
                            tint = GoldAccent,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Modern Tabs
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = PitchCard,
                contentColor = EmeraldGreen,
                divider = { HorizontalDivider(color = PitchCardElevated) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = {
                        viewModel.audioEffects.playClick()
                        selectedTab = 0
                    },
                    text = {
                        Text(
                            "رمزي الخاص 📲",
                            fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 13.sp,
                            color = if (selectedTab == 0) GoldAccent else TextSecondary
                        )
                    },
                    modifier = Modifier.testTag("tab_my_code")
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = {
                        viewModel.audioEffects.playClick()
                        selectedTab = 1
                    },
                    text = {
                        Text(
                            "انضمام برمز ⚔️",
                            fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 13.sp,
                            color = if (selectedTab == 1) EmeraldGreen else TextSecondary
                        )
                    },
                    modifier = Modifier.testTag("tab_join_code")
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = {
                        viewModel.audioEffects.playClick()
                        selectedTab = 2
                    },
                    text = {
                        Text(
                            "سجل الوديات 📜",
                            fontWeight = if (selectedTab == 2) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 13.sp,
                            color = if (selectedTab == 2) TextPrimary else TextSecondary
                        )
                    },
                    modifier = Modifier.testTag("tab_history")
                )
            }

            // Tab Content
            when (selectedTab) {
                0 -> MyChallengeCodeTab(
                    code = myCode,
                    difficulty = selectedDifficulty,
                    onCopyCode = {
                        clipboardManager.setText(AnnotatedString(myCode))
                        viewModel.audioEffects.playTick()
                        Toast.makeText(context, "تم نسخ رمز التحدي ($myCode) بنجاح! 📋", Toast.LENGTH_SHORT).show()
                    },
                    onShareCode = { viewModel.shareChallengeCode(context) },
                    onNewCode = { viewModel.generateNewChallengeCode() },
                    onChangeDifficulty = { showDifficultyDialog = true },
                    onStartMatch = { viewModel.startFriendlyMatch(myCode) }
                )
                1 -> JoinChallengeCodeTab(
                    enteredCode = enteredCode,
                    presetFriends = friendsList,
                    onCodeChange = { viewModel.setEnteredFriendCode(it) },
                    onJoinMatch = { code ->
                        if (code.isNotBlank()) {
                            viewModel.startFriendlyMatch(code)
                        } else {
                            Toast.makeText(context, "الرجاء كتابة رمز التحدي أولاً! ⚽", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onChallengePresetFriend = { friend ->
                        viewModel.startFriendlyMatch(friend.code, friend)
                    }
                )
                2 -> FriendlyMatchHistoryTab(
                    history = matchHistory,
                    onRematch = { item -> viewModel.startFriendlyMatch(item.challengeCode) },
                    onShareResult = { item ->
                        viewModel.shareMatchResult(
                            context = context,
                            oppName = item.friendName,
                            userScore = item.userScore,
                            oppScore = item.friendScore,
                            isWin = item.isWin
                        )
                    }
                )
            }
        }
    }

    if (showDifficultyDialog) {
        DifficultySelectionDialog(
            currentDifficulty = selectedDifficulty,
            onSelectDifficulty = { viewModel.setDifficulty(it) },
            onConfirm = { showDifficultyDialog = false },
            onDismiss = { showDifficultyDialog = false }
        )
    }
}

// -----------------------------------------------------------------------------
// TAB 0: MY CHALLENGE CODE & ROOM CREATION
// -----------------------------------------------------------------------------
@Composable
private fun MyChallengeCodeTab(
    code: String,
    difficulty: GameDifficulty,
    onCopyCode: () -> Unit,
    onShareCode: () -> Unit,
    onNewCode: () -> Unit,
    onChangeDifficulty: () -> Unit,
    onStartMatch: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            GlowCard(
                borderColor = GoldAccent.copy(alpha = 0.6f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "رمز التحدي الخاص بك 🏆",
                        color = GoldAccent,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "شارك هذا الرمز مع أصدقائك عبر وسائل التواصل لبدء مواجهة كروية ملحمية!",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    // Huge challenge code pill
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Brush.horizontalGradient(listOf(Color(0xFF261E0E), Color(0xFF191206))))
                            .border(2.dp, GoldAccent, RoundedCornerShape(16.dp))
                            .clickable { onCopyCode() }
                            .padding(vertical = 16.dp, horizontal = 12.dp)
                            .testTag("challenge_code_display"),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "📋",
                                fontSize = 20.sp
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = code,
                                color = GoldAccent,
                                fontSize = 30.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 2.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Buttons Row: Copy + New Code
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = onCopyCode,
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .testTag("copy_code_btn"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            border = ButtonDefaults.outlinedButtonBorder.copy(brush = Brush.horizontalGradient(listOf(Color.White.copy(alpha = 0.3f), Color.White.copy(alpha = 0.3f))))
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("نسخ الرمز", fontSize = 13.sp)
                        }

                        OutlinedButton(
                            onClick = onNewCode,
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .testTag("regenerate_code_btn"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = GoldAccent),
                            border = ButtonDefaults.outlinedButtonBorder.copy(brush = Brush.horizontalGradient(listOf(GoldAccent.copy(alpha = 0.4f), GoldAccent.copy(alpha = 0.4f))))
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("رمز جديد", fontSize = 13.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Primary Social Share Button (Android native share sheet)
                    TrophyButton(
                        text = "مشاركة الرمز عبر وسائل التواصل 📲",
                        onClick = onShareCode,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("share_challenge_btn")
                    )
                }
            }
        }

        // Match Settings Card
        item {
            GlowCard(
                borderColor = EmeraldGreen.copy(alpha = 0.4f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "إعدادات المواجهة الودية ⚙️",
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White.copy(alpha = 0.05f))
                            .clickable { onChangeDifficulty() }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "تعديل ✏️",
                                color = EmeraldGreen,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "${difficulty.titleArabic} ${difficulty.iconEmoji}",
                                color = Color(difficulty.colorHex),
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "مستوى الأسئلة:",
                                color = TextSecondary,
                                fontSize = 13.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White.copy(alpha = 0.05f))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "5 جولات كروية سريعة ⚽",
                            color = TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "مدة المباراة:",
                            color = TextSecondary,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }

        // Start Room Match Button
        item {
            EmeraldButton(
                text = "ابدأ المواجهة الودية الآن (غرفة التحدي) ⚽🔥",
                onClick = onStartMatch,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("start_friendly_room_btn")
            )
        }
    }
}

// -----------------------------------------------------------------------------
// TAB 1: JOIN WITH FRIEND'S CODE & PRESET ROOMS
// -----------------------------------------------------------------------------
@Composable
private fun JoinChallengeCodeTab(
    enteredCode: String,
    presetFriends: List<FriendlyChallengeInfo>,
    onCodeChange: (String) -> Unit,
    onJoinMatch: (String) -> Unit,
    onChallengePresetFriend: (FriendlyChallengeInfo) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            GlowCard(
                borderColor = EmeraldGreen.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp)
                ) {
                    Text(
                        text = "أدخل رمز التحدي الخاص بصديقك ⚔️",
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "اكتب أو ألصق الرمز الذي استلمته من صديقك لبدء المباراة مباشرة",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = enteredCode,
                        onValueChange = onCodeChange,
                        placeholder = {
                            Text(
                                "مثال: BARCA-10 أو REAL-07",
                                color = TextSecondary.copy(alpha = 0.6f),
                                fontSize = 14.sp
                            )
                        },
                        trailingIcon = {
                            Icon(Icons.Default.SportsSoccer, contentDescription = null, tint = EmeraldGreen)
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = EmeraldGreen,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                            focusedContainerColor = PitchDark,
                            unfocusedContainerColor = PitchDark,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        shape = RoundedCornerShape(14.dp),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("friend_code_input")
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    EmeraldButton(
                        text = "انضمام للمواجهة الودية الآن 🚀",
                        onClick = { onJoinMatch(enteredCode) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("join_friend_btn")
                    )
                }
            }
        }

        item {
            Text(
                text = "غرف أصدقاء جاهزة للتحدي السريع ⚡",
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }

        items(presetFriends) { friend ->
            PresetFriendCard(
                friend = friend,
                onChallenge = { onChallengePresetFriend(friend) }
            )
        }
    }
}

@Composable
private fun PresetFriendCard(
    friend: FriendlyChallengeInfo,
    onChallenge: () -> Unit
) {
    Surface(
        color = PitchCard,
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, PitchCardElevated),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Button(
                onClick = onChallenge,
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                modifier = Modifier.testTag("challenge_${friend.code}")
            ) {
                Text(
                    text = "تحدَّ الآن ⚡",
                    color = Color.Black,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(horizontalAlignment = Alignment.End) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = GoldAccent.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = friend.code,
                                color = GoldAccent,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = friend.friendName,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }
                    Text(
                        text = "${friend.friendClub} • ${friend.message}",
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.08f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = friend.friendAvatar, fontSize = 22.sp)
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------
// TAB 2: FRIENDLY MATCH HISTORY
// -----------------------------------------------------------------------------
@Composable
private fun FriendlyMatchHistoryTab(
    history: List<FriendlyMatchHistoryItem>,
    onRematch: (FriendlyMatchHistoryItem) -> Unit,
    onShareResult: (FriendlyMatchHistoryItem) -> Unit
) {
    if (history.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = "🤝", fontSize = 48.sp)
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "لم تلعب أي مواجهة ودية بعد!",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "شارك رمز التحدي الخاص بك مع أصدقائك لبدء تسجيل أول انتصار في سجلك!",
                    color = TextSecondary,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    text = "سجل كلاسيكو الأصدقاء 🏆 (${history.size} مواجهات)",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }

            items(history) { item ->
                FriendlyHistoryCard(
                    item = item,
                    onRematch = { onRematch(item) },
                    onShareResult = { onShareResult(item) }
                )
            }
        }
    }
}

@Composable
private fun FriendlyHistoryCard(
    item: FriendlyMatchHistoryItem,
    onRematch: () -> Unit,
    onShareResult: () -> Unit
) {
    Surface(
        color = PitchCard,
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (item.isWin) EmeraldGreen.copy(alpha = 0.4f) else Color.White.copy(alpha = 0.1f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Win / Loss Badge
                Surface(
                    color = if (item.isWin) EmeraldGreen.copy(alpha = 0.2f) else Color(0xFFFF5252).copy(alpha = 0.2f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = if (item.isWin) "فوز كاسح 🏆" else "مباراة مشتعلة ⚽",
                        color = if (item.isWin) EmeraldGreen else Color(0xFFFF5252),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "ضد ${item.friendName}",
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "رمز: ${item.challengeCode} • ${item.timeAgo}",
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.08f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = item.friendAvatar, fontSize = 20.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Score Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.White.copy(alpha = 0.04f))
                    .padding(vertical = 8.dp, horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                Text(
                    text = "أنت: ${item.userScore}",
                    color = if (item.isWin) EmeraldGreen else TextPrimary,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 16.sp
                )
                Text(
                    text = "VS",
                    color = GoldAccent,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
                Text(
                    text = "${item.friendName}: ${item.friendScore}",
                    color = TextSecondary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Action Buttons: Share result + Rematch
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onShareResult,
                    modifier = Modifier.weight(1f).height(38.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = GoldAccent)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("مشاركة النتيجة", fontSize = 11.sp)
                }

                Button(
                    onClick = onRematch,
                    modifier = Modifier.weight(1f).height(38.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen)
                ) {
                    Icon(Icons.Default.Replay, contentDescription = null, tint = Color.Black, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("إعادة التحدي", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }
            }
        }
    }
}
