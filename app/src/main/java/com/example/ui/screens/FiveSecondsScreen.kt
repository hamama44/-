package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
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
import androidx.compose.material.icons.filled.Mic
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
import com.example.ui.components.VoiceMicButton
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FiveSecondsScreen(viewModel: FootballGameViewModel) {
    val prompt by viewModel.fiveSecondsPromptState.collectAsState()
    val timeLeft by viewModel.fiveSecondsTimeLeft.collectAsState()
    val count by viewModel.fiveSecondsCount.collectAsState()
    val score by viewModel.fiveSecondsScore.collectAsState()
    val isRunning by viewModel.fiveSecondsIsRunning.collectAsState()
    val roundFinished by viewModel.fiveSecondsRoundFinished.collectAsState()
    val isSuccess by viewModel.fiveSecondsSuccess.collectAsState()

    val isAiAuditing by viewModel.aiIsAuditing.collectAsState()
    val aiFeedback by viewModel.aiAuditFeedback.collectAsState()
    val aiItems by viewModel.aiAuditedItems.collectAsState()

    var showExamples by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = PitchDark,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "⚡", fontSize = 22.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "تحدي الـ 5 ثواني",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "النقاط المحققة: $score نجاحات 🌟",
                                style = MaterialTheme.typography.labelSmall,
                                color = EmeraldNeon
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = { viewModel.navigateBack() },
                        modifier = Modifier.testTag("five_sec_back_button")
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
                            viewModel.nextFiveSecondsPrompt()
                        },
                        modifier = Modifier.testTag("five_sec_skip_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "تحدي آخر",
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
            // Category & Info header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(StadiumCyan.copy(alpha = 0.15f))
                            .border(1.dp, StadiumCyan, RoundedCornerShape(10.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "${prompt.icon} قسم: ${prompt.category}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = StadiumCyan
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(TrophyGold.copy(alpha = 0.15f))
                            .border(1.dp, TrophyGold.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "🤖 تدقيق صوتي ذكي بالمايك",
                            style = MaterialTheme.typography.labelSmall,
                            color = TrophyGold,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Big Circular Countdown Timer
            item {
                val timerColor = when {
                    timeLeft > 2.5f -> EmeraldNeon
                    timeLeft > 1.0f -> TrophyGold
                    else -> Color(0xFFFF1744)
                }

                Box(
                    modifier = Modifier
                        .size(150.dp)
                        .clip(CircleShape)
                        .background(PitchCard)
                        .border(6.dp, timerColor.copy(alpha = 0.3f), CircleShape)
                        .border(3.dp, timerColor, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = String.format("%.1f", timeLeft),
                            fontSize = 40.sp,
                            fontWeight = FontWeight.Black,
                            color = timerColor
                        )
                        Text(
                            text = "ثواني",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }
                }
            }

            // Prompt Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = PitchCard),
                    border = BorderStroke(1.5.dp, Brush.horizontalGradient(listOf(Color(0xFFFF9100), TrophyGold)))
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = prompt.prompt,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            lineHeight = 30.sp
                        )

                        if (showExamples) {
                            Text(
                                text = "أمثلة صحيحة: ${prompt.exampleAnswers.joinToString("، ")}",
                                style = MaterialTheme.typography.bodySmall,
                                color = TrophyGold,
                                textAlign = TextAlign.Center
                            )
                        } else {
                            Text(
                                text = "💡 اضغط هنا لإظهار أمثلة",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.5f),
                                modifier = Modifier.clickable { showExamples = true }
                            )
                        }
                    }
                }
            }

            // Three Answer Step Badges (1 - 2 - 3)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    for (i in 1..3) {
                        val isDone = count >= i
                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .clip(CircleShape)
                                .background(if (isDone) EmeraldNeon else PitchCard)
                                .border(2.dp, if (isDone) Color.White else PitchDivider, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (isDone) "✓" else "$i",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Black,
                                color = if (isDone) PitchDark else Color.White
                            )
                        }
                    }
                }
            }

            // AI Voice Input and Auditor Section
            item {
                AiVoiceInputBar(
                    promptTitle = "إجابة صوتية عبر المايك 🎙️",
                    hintText = "انطق الإجابات الثلاثة معاً (مثل: تفاح، موز، تين)...",
                    isAuditing = isAiAuditing,
                    onSpeechValidated = { spoken ->
                        viewModel.auditFiveSecondsSpeech(spoken)
                    }
                )
            }

            // AI Feedback Card
            if (aiFeedback != null) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSuccess) EmeraldGreen.copy(alpha = 0.2f) else PitchCardElevated
                        ),
                        border = BorderStroke(
                            1.dp,
                            if (isSuccess) EmeraldNeon else TrophyGold
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(text = "🤖 حكم الذكاء الاصطناعي: ", fontWeight = FontWeight.Bold, color = TrophyGold, fontSize = 12.sp)
                                Text(text = if (isSuccess) "إجابة مقبولة ✓" else "ملاحظة", color = Color.White, fontSize = 12.sp)
                            }
                            Text(
                                text = aiFeedback ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White
                            )
                            if (aiItems.isNotEmpty()) {
                                Text(
                                    text = "العناصر المستخرجة: ${aiItems.joinToString(" • ")}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = EmeraldNeon,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            // Round Finished Banner
            if (roundFinished) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (isSuccess) EmeraldGreen.copy(alpha = 0.25f) else Color.Red.copy(alpha = 0.25f))
                            .border(1.5.dp, if (isSuccess) EmeraldNeon else Color.Red, RoundedCornerShape(14.dp))
                            .padding(14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (isSuccess) "🔥 نجحت! أحسنت الرد السريع (+30 نقطة)" else "⌛ انتهى الوقت! حاول في التحدي القادم",
                            fontWeight = FontWeight.Bold,
                            color = if (isSuccess) EmeraldNeon else Color.Red,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            // Action Buttons (Start / Manual Tap / Next)
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (!isRunning && !roundFinished) {
                        Button(
                            onClick = { viewModel.startFiveSecondsCountdown() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .testTag("five_sec_start_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text(
                                text = "ابدأ العداد الآن ⏱️",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else if (isRunning) {
                        Button(
                            onClick = { viewModel.tapFiveSecondsCount() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(60.dp)
                                .testTag("five_sec_tap_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9100)),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text(
                                text = "قلت إجابة يدوياً! اضغط هنا (${count}/3) 🗣️",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    } else {
                        Button(
                            onClick = {
                                showExamples = false
                                viewModel.clearAiFeedback()
                                viewModel.nextFiveSecondsPrompt()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .testTag("five_sec_next_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = StadiumCyan),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text(
                                text = "التحدي التالي ⏩",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = PitchDark
                            )
                        }
                    }
                }
            }
        }
    }
}
