package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.*
import com.example.ui.FootballGameViewModel
import com.example.ui.components.AiVoiceInputBar
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutobusCompleteScreen(viewModel: FootballGameViewModel) {
    val letterData by viewModel.autobusLetter.collectAsState()
    val answers by viewModel.autobusUserAnswers.collectAsState()
    val timeLeft by viewModel.autobusTimeLeft.collectAsState()
    val isRunning by viewModel.autobusIsRunning.collectAsState()
    val isFinished by viewModel.autobusIsFinished.collectAsState()
    val roundResult by viewModel.autobusRoundResult.collectAsState()
    val aiProgress by viewModel.autobusAiProgress.collectAsState()
    val totalScore by viewModel.autobusTotalScore.collectAsState()
    val roundCount by viewModel.autobusRoundsCount.collectAsState()

    var showLetterPicker by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    Scaffold(
        containerColor = PitchDark,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "🚌", fontSize = 22.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "أتوبيس كومبليت",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "الجولة $roundCount • المجموع: $totalScore نقطة",
                                style = MaterialTheme.typography.labelSmall,
                                color = EmeraldNeon
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = { viewModel.navigateBack() },
                        modifier = Modifier.testTag("autobus_back_button")
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
                        onClick = { showLetterPicker = true },
                        modifier = Modifier.testTag("change_letter_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "تغيير الحرف",
                            tint = TrophyGold
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = PitchSurface
                )
            )
        },
        bottomBar = {
            Surface(
                color = PitchSurface,
                tonalElevation = 8.dp,
                border = BorderStroke(1.dp, PitchDivider)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val filled = answers.filledCount()
                    Button(
                        onClick = {
                            focusManager.clearFocus()
                            viewModel.claimAutobusComplete()
                        },
                        enabled = isRunning && filled >= 3,
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp)
                            .testTag("autobus_complete_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (filled >= 6) Color(0xFFFF9100) else EmeraldGreen,
                            disabledContainerColor = Color(0xFF2C3E50)
                        ),
                        shape = RoundedCornerShape(16.dp),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(text = "🚌", fontSize = 22.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "أتوبيس كومبليت! ($filled/8)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                AutobusHeaderBanner(
                    letterData = letterData,
                    timeLeft = timeLeft,
                    aiProgress = aiProgress,
                    onChangeLetterClick = { showLetterPicker = true }
                )
            }

            item {
                AiVoiceInputBar(
                    promptTitle = "إملاء ذكي بالمايك لحرف (${letterData.letter}) 🎙️",
                    hintText = "انطق بالمايك: (اسم، بلد، أو أي فئة تبدأ بحرف ${letterData.letter})...",
                    isAuditing = false,
                    onSpeechValidated = { spoken ->
                        // Automatically fill the first unfilled category or update boy
                        val unfilled = AutobusFieldType.values().firstOrNull { answers.getAnswer(it).isBlank() } ?: AutobusFieldType.BOY
                        viewModel.updateAutobusAnswer(unfilled, spoken)
                    }
                )
            }

            items(AutobusFieldType.values()) { category ->
                AutobusCategoryInputCard(
                    category = category,
                    currentAnswer = answers.getAnswer(category),
                    onAnswerChange = { newText ->
                        viewModel.updateAutobusAnswer(category, newText)
                    },
                    requiredLetter = letterData.letter,
                    isEnabled = isRunning
                )
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    if (isFinished && roundResult != null) {
        AutobusRoundResultDialog(
            result = roundResult!!,
            onNextRound = { viewModel.nextAutobusRound() },
            onClose = { viewModel.navigateBack() }
        )
    }

    if (showLetterPicker) {
        AutobusLetterPickerDialog(
            currentLetter = letterData.letter,
            onSelectLetter = { selectedChar ->
                showLetterPicker = false
                viewModel.startAutobusGame(selectedChar)
            },
            onDismiss = { showLetterPicker = false }
        )
    }
}

@Composable
private fun AutobusHeaderBanner(
    letterData: AutobusLetterData,
    timeLeft: Int,
    aiProgress: Int,
    onChangeLetterClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = PitchCard),
        border = BorderStroke(1.5.dp, Brush.horizontalGradient(listOf(StadiumCyan, TrophyGold)))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(TrophyGold.copy(alpha = 0.15f))
                        .clickable { onChangeLetterClick() }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(TrophyGold),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = letterData.letter.toString(),
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Black,
                            color = PitchDark
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "الحرف المطلوب",
                            style = MaterialTheme.typography.labelSmall,
                            color = TrophyGold
                        )
                        Text(
                            text = letterData.letterDisplay,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }

                val timerColor = if (timeLeft <= 10) Color(0xFFFF1744) else EmeraldNeon
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(timerColor.copy(alpha = 0.15f))
                        .border(1.dp, timerColor, RoundedCornerShape(12.dp))
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "⏱️", fontSize = 16.sp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "$timeLeft ثانية",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                            color = timerColor
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF0F1E19))
                    .padding(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "🤖 منافسك الآلي (الكابتن بوط)",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                    Text(
                        text = "كتب $aiProgress من 8",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = StadiumCyan
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { aiProgress / 8f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(CircleShape),
                    color = StadiumCyan,
                    trackColor = Color(0xFF2C3E50)
                )
            }
        }
    }
}

@Composable
private fun AutobusCategoryInputCard(
    category: AutobusFieldType,
    currentAnswer: String,
    onAnswerChange: (String) -> Unit,
    requiredLetter: Char,
    isEnabled: Boolean
) {
    val isFilled = currentAnswer.isNotBlank()
    val startsWithLetter = if (isFilled) {
        val trimmed = currentAnswer.trim()
        val withoutAl = if (trimmed.startsWith("ال") && requiredLetter != 'أ' && requiredLetter != 'ا') {
            trimmed.removePrefix("ال")
        } else trimmed
        withoutAl.startsWith(requiredLetter)
    } else false

    val borderColor = when {
        !isFilled -> PitchDivider
        startsWithLetter -> EmeraldNeon.copy(alpha = 0.6f)
        else -> Color(0xFFFF9100).copy(alpha = 0.6f)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = PitchCard),
        border = BorderStroke(1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(PitchDark),
                contentAlignment = Alignment.Center
            ) {
                Text(text = category.icon, fontSize = 20.sp)
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = category.titleArabic,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.7f),
                    fontWeight = FontWeight.Bold
                )

                OutlinedTextField(
                    value = currentAnswer,
                    onValueChange = onAnswerChange,
                    placeholder = {
                        Text(
                            text = category.placeholder,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.35f)
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("autobus_field_${category.name.lowercase()}"),
                    singleLine = true,
                    enabled = isEnabled,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        disabledBorderColor = Color.Transparent
                    ),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    ),
                    keyboardOptions = KeyboardOptions.Default.copy(
                        imeAction = ImeAction.Next
                    )
                )
            }

            if (isFilled) {
                Text(
                    text = if (startsWithLetter) "✅" else "⚠️",
                    fontSize = 18.sp
                )
            }
        }
    }
}

@Composable
private fun AutobusRoundResultDialog(
    result: AutobusRoundResult,
    onNextRound: () -> Unit,
    onClose: () -> Unit
) {
    val isWin = result.totalScore > result.aiTotalScore

    Dialog(onDismissRequest = onClose) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = PitchCard),
            border = BorderStroke(2.dp, if (isWin) TrophyGold else StadiumCyan)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = if (isWin) "🏆 فوز ساحق في الأتوبيس!" else "🤝 جولة رائعة وممتعة!",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF0F1E19))
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "نقاطك 🌟", style = MaterialTheme.typography.labelSmall, color = EmeraldNeon)
                        Text(
                            text = "${result.totalScore}",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Black,
                            color = EmeraldNeon
                        )
                    }

                    Text(text = "ضد", style = MaterialTheme.typography.titleMedium, color = Color.White.copy(alpha = 0.5f))

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "الكابتن بوط 🤖", style = MaterialTheme.typography.labelSmall, color = StadiumCyan)
                        Text(
                            text = "${result.aiTotalScore}",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Black,
                            color = StadiumCyan
                        )
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 240.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(result.fieldResults) { field ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(PitchDark)
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(text = field.type.icon, fontSize = 16.sp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = field.type.titleArabic,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.7f)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = field.playerAnswer.ifEmpty { "(لم تُجب)" },
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (field.isValid) Color.White else Color.Red
                                )
                            }
                            Text(
                                text = "+${field.scoreAwarded}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (field.scoreAwarded > 0) EmeraldNeon else Color.Gray
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onClose,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                    ) {
                        Text("الرئيسية")
                    }

                    Button(
                        onClick = onNextRound,
                        modifier = Modifier.weight(1.5f),
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen)
                    ) {
                        Text("جولة جديدة 🔄", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun AutobusLetterPickerDialog(
    currentLetter: Char,
    onSelectLetter: (Char) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = PitchCard),
            border = BorderStroke(1.dp, TrophyGold)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "اختر حرف الجولة 🔤",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                val letters = PartyGamesRepository.autobusLetters
                val chunked = letters.chunked(6)

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    chunked.forEach { rowLetters ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            rowLetters.forEach { letterItem ->
                                val isSelected = letterItem.letter == currentLetter
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(if (isSelected) TrophyGold else PitchDark)
                                        .border(1.dp, if (isSelected) Color.White else PitchDivider, CircleShape)
                                        .clickable { onSelectLetter(letterItem.letter) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = letterItem.letter.toString(),
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) PitchDark else Color.White,
                                        fontSize = 18.sp
                                    )
                                }
                            }
                        }
                    }
                }

                TextButton(onClick = onDismiss) {
                    Text("إلغاء", color = Color.White.copy(alpha = 0.7f))
                }
            }
        }
    }
}
