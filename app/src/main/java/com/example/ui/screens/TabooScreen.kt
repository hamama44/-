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
import com.example.ui.components.AiVoiceInputBar
import com.example.ui.components.VoiceTargetGuessCard
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TabooScreen(viewModel: FootballGameViewModel) {
    val card by viewModel.tabooCurrent.collectAsState()
    val timeLeft by viewModel.tabooTimeLeft.collectAsState()
    val score by viewModel.tabooScore.collectAsState()
    val penalties by viewModel.tabooPenalties.collectAsState()
    val isRunning by viewModel.tabooIsRunning.collectAsState()

    val isAiAuditing by viewModel.aiIsAuditing.collectAsState()
    val aiFeedback by viewModel.aiAuditFeedback.collectAsState()
    val lastEvaluation by viewModel.lastSpokenAnswerEvaluation.collectAsState()

    var voiceModeTab by remember { mutableStateOf(0) } // 0: شرح الكلمة (مراقبة المحظورات), 1: تخمين الكلمة بالمايك

    Scaffold(
        containerColor = PitchDark,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "🚫", fontSize = 22.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "التابو العربي",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "النقاط: $score • المخالفات: $penalties",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (penalties > 0) Color(0xFFFF5252) else TrophyGold
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = { viewModel.navigateBack() },
                        modifier = Modifier.testTag("taboo_back_button")
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
                        onClick = {
                            viewModel.clearAiFeedback()
                            viewModel.tabooSkip()
                        },
                        modifier = Modifier.testTag("taboo_skip_top_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "تخطي البطاقة",
                            tint = TrophyGold
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = PitchSurface)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header Stats & Timer
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(PitchCard)
                            .border(1.dp, PitchDivider, RoundedCornerShape(12.dp))
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "${card.icon} قسم: ${card.category}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = StadiumCyan
                        )
                    }

                    val timerColor = if (timeLeft <= 10) Color(0xFFFF1744) else EmeraldNeon
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(timerColor.copy(alpha = 0.15f))
                            .border(1.dp, timerColor, RoundedCornerShape(12.dp))
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "⏱️ $timeLeft ثانية",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = timerColor
                        )
                    }
                }
            }

            // Taboo Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = PitchCard),
                    border = BorderStroke(2.dp, Brush.verticalGradient(listOf(EmeraldNeon, Color(0xFFFF1744))))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Main Word
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "الكلمة المطلوب شرحها:",
                                style = MaterialTheme.typography.labelSmall,
                                color = EmeraldNeon,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = card.mainWord,
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Black,
                                color = Color.White,
                                textAlign = TextAlign.Center
                            )
                        }

                        HorizontalDivider(color = PitchDivider, thickness = 1.dp)

                        // Forbidden Words (الكلمات الممنوعة)
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "🚫 الكلمات الممنوعة (ممنوع تنطقها بالمايك أو الشرح):",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFF5252),
                                fontSize = 13.sp
                            )

                            card.forbiddenWords.forEach { forbidden ->
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color(0xFF2C1414))
                                        .border(1.dp, Color(0xFFFF5252).copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                                        .padding(vertical = 6.dp, horizontal = 12.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "❌ $forbidden",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Voice Mode Selector (شرح الكلمة vs تخمين الكلمة بالمايك)
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(PitchCard)
                        .border(1.dp, PitchDivider, RoundedCornerShape(14.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Option 0: الشرح الصوتي
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (voiceModeTab == 0) EmeraldNeon.copy(alpha = 0.2f) else Color.Transparent)
                            .border(1.dp, if (voiceModeTab == 0) EmeraldNeon else Color.Transparent, RoundedCornerShape(10.dp))
                            .clickable {
                                voiceModeTab = 0
                                viewModel.clearAiFeedback()
                            }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "شرح الكلمة 🎙️",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (voiceModeTab == 0) Color.White else TextMuted
                        )
                    }

                    // Option 1: تخمين الكلمة المطلوبة
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (voiceModeTab == 1) TrophyGold.copy(alpha = 0.2f) else Color.Transparent)
                            .border(1.dp, if (voiceModeTab == 1) TrophyGold else Color.Transparent, RoundedCornerShape(10.dp))
                            .clickable {
                                voiceModeTab = 1
                                viewModel.clearAiFeedback()
                            }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "تخمين الكلمة بالمايك 🎯",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (voiceModeTab == 1) Color.White else TextMuted
                        )
                    }
                }
            }

            // Voice Interaction Card according to selected mode
            item {
                if (voiceModeTab == 0) {
                    AiVoiceInputBar(
                        promptTitle = "مراقبة الشرح الصوتي للتابو 🎙️",
                        hintText = "اشرح الكلمة لأصدقائك بصوتك... الذكاء الاصطناعي يرصد الكلمات المحظورة!",
                        isAuditing = isAiAuditing,
                        onSpeechValidated = { spoken ->
                            viewModel.auditTabooSpeech(spoken)
                        }
                    )
                } else {
                    VoiceTargetGuessCard(
                        promptTitle = "تخمين الكلمة المطلوبة بصوتك 🎙️",
                        placeholderText = "انطق تخمينك بالمايك... يدقق الذكاء الاصطناعي في صحة الكلمة!",
                        isAuditing = isAiAuditing,
                        lastEvaluation = lastEvaluation,
                        onGuessSubmitted = { spokenGuess ->
                            viewModel.auditTabooSpokenGuess(spokenGuess)
                        }
                    )
                }
            }

            // AI Arbiter Feedback
            if (aiFeedback != null) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (aiFeedback?.contains("محظور") == true || aiFeedback?.contains("مخالفة") == true) {
                                Color(0xFF3B1515)
                            } else {
                                EmeraldGreen.copy(alpha = 0.2f)
                            }
                        ),
                        border = BorderStroke(
                            1.dp,
                            if (aiFeedback?.contains("محظور") == true) Color(0xFFFF1744) else EmeraldNeon
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (aiFeedback?.contains("محظور") == true) "🚨" else "✨",
                                fontSize = 24.sp
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "تدقيق الذكاء الاصطناعي في الكلام 🤖",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TrophyGold,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = aiFeedback ?: "",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }

            // Controls
            item {
                if (!isRunning) {
                    Button(
                        onClick = { viewModel.startTabooTimer() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .testTag("taboo_start_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(
                            text = "ابدأ الجولة الآن (60 ثانية) ⏱️",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { viewModel.tabooSuccess() },
                            modifier = Modifier
                                .weight(1.2f)
                                .height(56.dp)
                                .testTag("taboo_correct_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text("خمنوها! ✅ (+1)", fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = { viewModel.tabooForbiddenWordPenalty() },
                            modifier = Modifier
                                .weight(1.2f)
                                .height(56.dp)
                                .testTag("taboo_penalty_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF1744)),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text("ممنوعة! ❌ (-1)", fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = {
                                viewModel.clearAiFeedback()
                                viewModel.tabooSkip()
                            },
                            modifier = Modifier
                                .weight(0.9f)
                                .height(56.dp)
                                .testTag("taboo_skip_button"),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text("تخطي ⏩")
                        }
                    }
                }
            }
        }
    }
}
