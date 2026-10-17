package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
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
import com.example.ui.FootballGameViewModel
import com.example.ui.GameScreen
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PassAndPlayScreen(viewModel: FootballGameViewModel) {
    val state by viewModel.gatheringTeamState.collectAsState()
    var showSetupDialog by remember { mutableStateOf(false) }

    var redNameInput by remember { mutableStateOf(state.teamRedName) }
    var blueNameInput by remember { mutableStateOf(state.teamBlueName) }
    var roundsSelected by remember { mutableIntStateOf(state.totalRounds) }

    Scaffold(
        containerColor = PitchDark,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "👥", fontSize = 22.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "ديربي التجمعات (فريق ضد فريق)",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "الجولة ${state.currentRound} من ${state.totalRounds} • جهاز واحد",
                                style = MaterialTheme.typography.labelSmall,
                                color = TrophyGold
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = { viewModel.navigateBack() },
                        modifier = Modifier.testTag("back_from_pass_and_play")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "الرجوع",
                            tint = Color.White
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showSetupDialog = true },
                        modifier = Modifier.testTag("setup_pass_and_play_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "إعدادات الفرق",
                            tint = TrophyGold
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = PitchDark)
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Live Team Scoreboard
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = PitchCardElevated),
                    border = BorderStroke(1.5.dp, PitchCardBorder)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Red Team Card
                            val isRedTurn = state.currentTurnTeam == "RED"
                            Card(
                                modifier = Modifier
                                    .weight(1f)
                                    .border(
                                        width = if (isRedTurn) 2.dp else 1.dp,
                                        color = if (isRedTurn) Color(0xFFFF5252) else Color(0xFFFF5252).copy(alpha = 0.3f),
                                        shape = RoundedCornerShape(14.dp)
                                    ),
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isRedTurn) Color(0xFF4A121A) else Color(0xFF2B1015)
                                )
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = state.teamRedName,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = Color(0xFFFF8A80),
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "${state.redScore}",
                                        fontSize = 32.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color.White
                                    )
                                    if (isRedTurn && !state.isGameFinished) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(Color(0xFFFF5252))
                                                .padding(horizontal = 8.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = "الدور الآن ▶",
                                                color = Color.White,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }

                            // Center VS badge
                            Column(
                                modifier = Modifier.padding(horizontal = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(TrophyGold),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "VS",
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 13.sp,
                                        color = PitchDark
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "جولة ${state.currentRound}",
                                    fontSize = 11.sp,
                                    color = Color.White.copy(alpha = 0.7f),
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            // Blue Team Card
                            val isBlueTurn = state.currentTurnTeam == "BLUE"
                            Card(
                                modifier = Modifier
                                    .weight(1f)
                                    .border(
                                        width = if (isBlueTurn) 2.dp else 1.dp,
                                        color = if (isBlueTurn) StadiumCyan else StadiumCyan.copy(alpha = 0.3f),
                                        shape = RoundedCornerShape(14.dp)
                                    ),
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isBlueTurn) Color(0xFF0F2E4A) else Color(0xFF0A1B2C)
                                )
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = state.teamBlueName,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = Color(0xFF80D8FF),
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "${state.blueScore}",
                                        fontSize = 32.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color.White
                                    )
                                    if (isBlueTurn && !state.isGameFinished) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(StadiumCyan)
                                                .padding(horizontal = 8.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = "الدور الآن ▶",
                                                color = PitchDark,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // If Game Finished: Final Winner Podia
            if (state.isGameFinished) {
                item {
                    val winnerText = when {
                        state.redScore > state.blueScore -> "👑 الفائز بالبطولة: ${state.teamRedName}!"
                        state.blueScore > state.redScore -> "👑 الفائز بالبطولة: ${state.teamBlueName}!"
                        else -> "🤝 تعادل ناري وبطولي بين الفريقين!"
                    }
                    val winnerColor = when {
                        state.redScore > state.blueScore -> Color(0xFFFF5252)
                        state.blueScore > state.redScore -> StadiumCyan
                        else -> TrophyGold
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = PitchCardElevated),
                        border = BorderStroke(2.dp, winnerColor)
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(text = "🏆", fontSize = 48.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "صافرة نهاية المباراة الجماعية!",
                                style = MaterialTheme.typography.titleMedium,
                                color = TrophyGold,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = winnerText,
                                style = MaterialTheme.typography.titleLarge,
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Button(
                                    onClick = { viewModel.resetPassAndPlay() },
                                    colors = ButtonDefaults.buttonColors(containerColor = TrophyGold),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = null, tint = PitchDark)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("مباراة جديدة", color = PitchDark, fontWeight = FontWeight.Bold)
                                }
                                OutlinedButton(
                                    onClick = { viewModel.navigateTo(GameScreen.Home) },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("القائمة الرئيسية", color = Color.White)
                                }
                            }
                        }
                    }
                }
            } else {
                // Active Turn Controller
                item {
                    val activeTeamName = if (state.currentTurnTeam == "RED") state.teamRedName else state.teamBlueName
                    val activeColor = if (state.currentTurnTeam == "RED") Color(0xFFFF5252) else StadiumCyan

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = PitchCard),
                        border = BorderStroke(1.5.dp, activeColor)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "الدور الحالي عند: $activeTeamName",
                                style = MaterialTheme.typography.titleMedium,
                                color = activeColor,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "اختر اللعبة المناسبة للجولة، أو أضف نقاط الفوز مباشرة عند انتهاء التحدي!",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.7f),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                            )

                            // Direct points addition
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { viewModel.awardPassAndPlayPoints(10) },
                                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldNeon),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f).testTag("award_10_pts")
                                ) {
                                    Text("+10 نقاط", color = PitchDark, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                                Button(
                                    onClick = { viewModel.awardPassAndPlayPoints(20) },
                                    colors = ButtonDefaults.buttonColors(containerColor = TrophyGold),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f).testTag("award_20_pts")
                                ) {
                                    Text("+20 نقطة", color = PitchDark, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                                Button(
                                    onClick = { viewModel.awardPassAndPlayPoints(30) },
                                    colors = ButtonDefaults.buttonColors(containerColor = StadiumCyan),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f).testTag("award_30_pts")
                                ) {
                                    Text("+30 نقطة", color = PitchDark, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            OutlinedButton(
                                onClick = { viewModel.switchPassAndPlayTurn() },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                border = BorderStroke(1.dp, PitchDivider),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth().testTag("switch_turn_btn")
                            ) {
                                Text("تخطي وتسليم الدور للفريق الثاني 🔄", fontSize = 13.sp)
                            }
                        }
                    }
                }

                // Launch Mini-Games Directly for this round
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "🎮 ألعاب مقترحة لجولة الفريق الحالية:",
                            style = MaterialTheme.typography.titleSmall,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            MiniLaunchCard(
                                icon = "🚌",
                                title = "أتوبيس كومبليت",
                                color = Color(0xFFFF9100),
                                modifier = Modifier.weight(1f),
                                onClick = { viewModel.startAutobusGame() }
                            )
                            MiniLaunchCard(
                                icon = "⚡",
                                title = "تحدي 5 ثواني",
                                color = Color(0xFFFF4081),
                                modifier = Modifier.weight(1f),
                                onClick = { viewModel.startFiveSecondsGame() }
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            MiniLaunchCard(
                                icon = "🎭",
                                title = "بدون كلام (تمثيل)",
                                color = Color(0xFFAB47BC),
                                modifier = Modifier.weight(1f),
                                onClick = { viewModel.startBedoonKalamGame() }
                            )
                            MiniLaunchCard(
                                icon = "🚫",
                                title = "التابو العربي",
                                color = Color(0xFFFF5252),
                                modifier = Modifier.weight(1f),
                                onClick = { viewModel.startTabooGame() }
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            MiniLaunchCard(
                                icon = "📜",
                                title = "كمّل المثل",
                                color = Color(0xFFFFB300),
                                modifier = Modifier.weight(1f),
                                onClick = { viewModel.startProverbsGame() }
                            )
                            MiniLaunchCard(
                                icon = "🔍",
                                title = "الرابط العجيب",
                                color = StadiumCyan,
                                modifier = Modifier.weight(1f),
                                onClick = { viewModel.startFourCluesGame() }
                            )
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    // Team Setup Dialog
    if (showSetupDialog) {
        AlertDialog(
            onDismissRequest = { showSetupDialog = false },
            title = {
                Text(
                    text = "⚙️ إعدادات مباراة التجمع",
                    color = TrophyGold,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = redNameInput,
                        onValueChange = { redNameInput = it },
                        label = { Text("اسم الفريق الأحمر 🔴") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )
                    OutlinedTextField(
                        value = blueNameInput,
                        onValueChange = { blueNameInput = it },
                        label = { Text("اسم الفريق الأزرق 🔵") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("عدد الجولات:", color = Color.White)
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf(3, 5, 7, 10).forEach { r ->
                                FilterChip(
                                    selected = roundsSelected == r,
                                    onClick = { roundsSelected = r },
                                    label = { Text("$r") }
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.startPassAndPlay(redNameInput, blueNameInput, roundsSelected)
                        showSetupDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = TrophyGold)
                ) {
                    Text("حفظ والبدء 🚀", color = PitchDark, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSetupDialog = false }) {
                    Text("إلغاء", color = Color.White.copy(alpha = 0.7f))
                }
            },
            containerColor = PitchCard,
            shape = RoundedCornerShape(16.dp)
        )
    }
}

@Composable
private fun MiniLaunchCard(
    icon: String,
    title: String,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .clickable(onClick = onClick)
            .height(54.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = PitchCardElevated),
        border = BorderStroke(1.dp, color.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = icon, fontSize = 20.sp)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = title,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                maxLines = 1
            )
        }
    }
}
