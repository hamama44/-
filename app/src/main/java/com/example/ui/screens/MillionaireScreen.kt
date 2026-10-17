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
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MillionaireScreen(viewModel: FootballGameViewModel) {
    val level by viewModel.millionaireLevel.collectAsState()
    val question by viewModel.millionaireQuestion.collectAsState()
    val selectedOption by viewModel.millionaireSelectedOption.collectAsState()
    val isCorrect by viewModel.millionaireIsCorrect.collectAsState()
    val fiftyFiftyUsed by viewModel.millionaireFiftyFiftyUsed.collectAsState()
    val eliminatedIndices by viewModel.millionaireEliminatedIndices.collectAsState()
    val audienceUsed by viewModel.millionaireAudienceUsed.collectAsState()
    val showAudience by viewModel.millionaireShowAudience.collectAsState()
    val friendUsed by viewModel.millionaireFriendUsed.collectAsState()
    val friendTip by viewModel.millionaireFriendTip.collectAsState()
    val isGameOver by viewModel.millionaireGameOver.collectAsState()
    val wonMillion by viewModel.millionaireWonMillion.collectAsState()
    val prizeWon by viewModel.millionairePrizeWon.collectAsState()

    var showLadderDialog by remember { mutableStateOf(false) }

    val ladderPrizes = listOf(
        15 to 1000000,
        14 to 500000,
        13 to 250000,
        12 to 125000,
        11 to 64000,
        10 to 32000, // Safety
        9 to 16000,
        8 to 8000,
        7 to 4000,
        6 to 2000,
        5 to 1000,  // Safety
        4 to 500,
        3 to 300,
        2 to 200,
        1 to 100
    )

    Scaffold(
        containerColor = PitchDark,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "💰", fontSize = 22.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "المليون العربي",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "السؤال $level من 15 • الجائزة: ${question.prizeValue} نقطة",
                                style = MaterialTheme.typography.labelSmall,
                                color = TrophyGold
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = { viewModel.navigateBack() },
                        modifier = Modifier.testTag("back_from_millionaire")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "الرجوع",
                            tint = Color.White
                        )
                    }
                },
                actions = {
                    FilledTonalButton(
                        onClick = { showLadderDialog = true },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = PitchCardElevated,
                            contentColor = TrophyGold
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text("السلّم 🪜", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = PitchDark
                )
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
            // Lifelines Row
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = PitchCard),
                    border = BorderStroke(1.dp, PitchCardBorder)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "وسائل المساعدة المتاحة",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.7f),
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            // 50:50
                            OutlinedButton(
                                onClick = { viewModel.useMillionaireFiftyFifty() },
                                enabled = !fiftyFiftyUsed && selectedOption == null && !isGameOver,
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = if (fiftyFiftyUsed) Color.Gray else TrophyGold,
                                    disabledContentColor = Color.Gray.copy(alpha = 0.4f)
                                ),
                                border = BorderStroke(
                                    1.dp,
                                    if (fiftyFiftyUsed) Color.DarkGray else TrophyGold
                                ),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text("50:50 ✂️", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }

                            // Audience
                            OutlinedButton(
                                onClick = { viewModel.useMillionaireAudience() },
                                enabled = !audienceUsed && selectedOption == null && !isGameOver,
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = if (audienceUsed) Color.Gray else StadiumCyan,
                                    disabledContentColor = Color.Gray.copy(alpha = 0.4f)
                                ),
                                border = BorderStroke(
                                    1.dp,
                                    if (audienceUsed) Color.DarkGray else StadiumCyan
                                ),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text("الجمهور 👥", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }

                            // Friend
                            OutlinedButton(
                                onClick = { viewModel.useMillionaireFriend() },
                                enabled = !friendUsed && selectedOption == null && !isGameOver,
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = if (friendUsed) Color.Gray else EmeraldNeon,
                                    disabledContentColor = Color.Gray.copy(alpha = 0.4f)
                                ),
                                border = BorderStroke(
                                    1.dp,
                                    if (friendUsed) Color.DarkGray else EmeraldNeon
                                ),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text("صديق 📞", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }

                            // Withdraw
                            OutlinedButton(
                                onClick = { viewModel.withdrawMillionaire() },
                                enabled = !isGameOver && selectedOption == null,
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = Color(0xFFFF5252),
                                    disabledContentColor = Color.Gray.copy(alpha = 0.4f)
                                ),
                                border = BorderStroke(1.dp, Color(0xFFFF5252).copy(alpha = 0.6f)),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text("انسحاب 🏳️", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Audience Poll Card (if visible)
            if (showAudience) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF003049)),
                        border = BorderStroke(1.dp, StadiumCyan)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "📊 رأي الجمهور في الاستوديو",
                                    fontWeight = FontWeight.Bold,
                                    color = StadiumCyan,
                                    fontSize = 14.sp
                                )
                                TextButton(onClick = { viewModel.dismissMillionaireAudience() }) {
                                    Text("إغلاق ✕", color = Color.White.copy(alpha = 0.8f))
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            val prefixes = listOf("أ", "ب", "ج", "د")
                            question.audiencePercentages.forEachIndexed { idx, pct ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${prefixes.getOrNull(idx)}: ",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        modifier = Modifier.width(28.dp)
                                    )
                                    LinearProgressIndicator(
                                        progress = { pct / 100f },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(10.dp)
                                            .clip(RoundedCornerShape(5.dp)),
                                        color = if (pct >= 50) EmeraldNeon else TrophyGold,
                                        trackColor = Color.White.copy(alpha = 0.15f)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = "$pct%",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        modifier = Modifier.width(36.dp),
                                        textAlign = TextAlign.End
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Friend Call Card (if active)
            if (friendTip != null) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1B3B2B)),
                        border = BorderStroke(1.dp, EmeraldNeon)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("📞", fontSize = 18.sp)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "المتصل صديقك العبقري",
                                        fontWeight = FontWeight.Bold,
                                        color = EmeraldNeon,
                                        fontSize = 14.sp
                                    )
                                }
                                TextButton(onClick = { viewModel.dismissMillionaireFriend() }) {
                                    Text("شكراً ✕", color = Color.White.copy(alpha = 0.8f))
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "« $friendTip »",
                                color = Color.White,
                                fontSize = 14.sp,
                                lineHeight = 20.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            // The Big Question Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = PitchCardElevated),
                    border = BorderStroke(
                        2.dp,
                        Brush.horizontalGradient(listOf(TrophyGold, EmeraldNeon))
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(TrophyGold.copy(alpha = 0.15f))
                                .border(1.dp, TrophyGold.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "السؤال رقم $level • ${question.prizeValue} ر.س / نقطة",
                                color = TrophyGold,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = question.question,
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            fontWeight = FontWeight.Bold,
                            lineHeight = 26.sp
                        )
                    }
                }
            }

            // Options (أ، ب، ج، د)
            val optionPrefixes = listOf("أ", "ب", "ج", "د")
            question.options.forEachIndexed { index, optionText ->
                item {
                    val isEliminated = eliminatedIndices.contains(index)
                    val isSelected = selectedOption == index
                    val isAnswerCorrect = index == question.correctIndex

                    val cardBackground = when {
                        isEliminated -> Color.Transparent
                        isSelected && isCorrect == true -> EmeraldNeon.copy(alpha = 0.25f)
                        isSelected && isCorrect == false -> Color(0xFFFF5252).copy(alpha = 0.25f)
                        selectedOption != null && isAnswerCorrect -> EmeraldNeon.copy(alpha = 0.25f)
                        else -> PitchCard
                    }

                    val borderColor = when {
                        isEliminated -> Color.DarkGray.copy(alpha = 0.3f)
                        isSelected && isCorrect == true -> EmeraldNeon
                        isSelected && isCorrect == false -> Color(0xFFFF5252)
                        selectedOption != null && isAnswerCorrect -> EmeraldNeon
                        else -> PitchCardBorder
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(
                                enabled = selectedOption == null && !isGameOver && !isEliminated,
                                onClick = { viewModel.selectMillionaireOption(index) }
                            )
                            .testTag("millionaire_opt_$index"),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = cardBackground),
                        border = BorderStroke(1.5.dp, borderColor)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when {
                                            isEliminated -> Color.DarkGray
                                            isSelected && isCorrect == true -> EmeraldNeon
                                            isSelected && isCorrect == false -> Color(0xFFFF5252)
                                            selectedOption != null && isAnswerCorrect -> EmeraldNeon
                                            else -> PitchCardElevated
                                        }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = optionPrefixes.getOrElse(index) { "$index" },
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = if (isSelected || (selectedOption != null && isAnswerCorrect)) PitchDark else TrophyGold
                                )
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            Text(
                                text = if (isEliminated) "— — —" else optionText,
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (isEliminated) Color.Gray.copy(alpha = 0.5f) else Color.White,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // Success / Next Question or Game Over actions
            item {
                Spacer(modifier = Modifier.height(10.dp))
                if (selectedOption != null && isCorrect == true && !isGameOver) {
                    Button(
                        onClick = { viewModel.nextMillionaireQuestion() },
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldNeon),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("millionaire_next_btn")
                    ) {
                        Text(
                            text = if (level < 15) "إجابة صحيحة! انتقل للسؤال التالي ➡️" else "مبروك! فزت بالمليون 🏆",
                            color = PitchDark,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                } else if (isGameOver) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (wonMillion) Color(0xFF2E7D32) else Color(0xFF3E1F24)
                        ),
                        border = BorderStroke(1.5.dp, if (wonMillion) TrophyGold else Color(0xFFFF5252))
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = if (wonMillion) "🎉 ألف مبروك! دخلت التاريخ بالمليون 👑" else "انتهت المحاولة! حظ أوفر في المرة القادمة",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "المبلغ المحقق النهائي: $prizeWon نقطة 💰",
                                color = TrophyGold,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 18.sp
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            Button(
                                onClick = { viewModel.startMillionaireGame() },
                                colors = ButtonDefaults.buttonColors(containerColor = TrophyGold),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, tint = PitchDark)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("محاولة جديدة 🔄", color = PitchDark, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    // Ladder Dialog
    if (showLadderDialog) {
        AlertDialog(
            onDismissRequest = { showLadderDialog = false },
            title = {
                Text(
                    text = "🪜 سلّم الجوائز ومحطات الأمان",
                    color = TrophyGold,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            },
            text = {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 360.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    ladderPrizes.forEach { (lvl, prize) ->
                        val isCurrent = lvl == level
                        val isPassed = lvl < level
                        val isSafety = lvl == 5 || lvl == 10 || lvl == 15

                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        when {
                                            isCurrent -> TrophyGold.copy(alpha = 0.25f)
                                            isPassed -> EmeraldNeon.copy(alpha = 0.15f)
                                            isSafety -> Color.White.copy(alpha = 0.08f)
                                            else -> Color.Transparent
                                        }
                                    )
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "$lvl",
                                        fontWeight = FontWeight.Bold,
                                        color = if (isCurrent) TrophyGold else Color.White,
                                        modifier = Modifier.width(28.dp)
                                    )
                                    if (isSafety) {
                                        Text(text = "🛡️", fontSize = 12.sp)
                                    }
                                }
                                Text(
                                    text = "$prize 💰",
                                    fontWeight = if (isSafety || isCurrent) FontWeight.Bold else FontWeight.Normal,
                                    color = when {
                                        isCurrent -> TrophyGold
                                        isPassed -> EmeraldNeon
                                        isSafety -> Color.White
                                        else -> Color.White.copy(alpha = 0.7f)
                                    }
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLadderDialog = false }) {
                    Text("إغلاق", color = TrophyGold, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = PitchCard,
            shape = RoundedCornerShape(16.dp)
        )
    }
}
