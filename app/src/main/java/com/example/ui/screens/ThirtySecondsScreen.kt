package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.ui.FootballGameViewModel
import com.example.ui.GameScreen
import com.example.ui.components.EmeraldButton
import com.example.ui.components.GlowCard
import com.example.ui.components.StadiumTopBar
import com.example.ui.components.TrophyButton
import com.example.ui.theme.*

@Composable
fun ThirtySecondsSelectScreen(viewModel: FootballGameViewModel) {
    val userProfile by viewModel.userProfile.collectAsState()

    Scaffold(
        containerColor = PitchDark,
        topBar = {
            StadiumTopBar(
                title = "تحدي الـ 30 ثانية ⏱️",
                coins = userProfile.coins,
                points = userProfile.points,
                onBack = { viewModel.navigateBack() }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(20.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(90.dp)
                    .clip(CircleShape)
                    .background(PitchCard)
                    .border(2.dp, EmeraldNeon, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "⏱️", fontSize = 42.sp)
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "اختر وضع التحدي",
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "لديك 30 ثانية فقط لإثبات معرفتك الكروية السريعة!",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Mode 1 Card: Speed Quiz
            GlowCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("mode_speed_quiz"),
                borderColor = EmeraldNeon.copy(alpha = 0.5f),
                onClick = { viewModel.startThirtySecondsQuiz() }
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(EmeraldNeon.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "⚡", fontSize = 26.sp)
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "النمط الأول: أسئلة السرعة",
                            style = MaterialTheme.typography.titleMedium,
                            color = EmeraldNeon,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "أسئلة اختيار من متعدد متتالية، كل إجابة صحيحة تمنحك نقطة سريعة.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Mode 2 Card: Player Mention
            GlowCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("mode_player_mention"),
                borderColor = TrophyGold.copy(alpha = 0.5f),
                onClick = { viewModel.startPlayerMentionChallenge() }
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(TrophyGold.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "✍️", fontSize = 26.sp)
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "النمط الثاني: اذكر اللاعبين",
                            style = MaterialTheme.typography.titleMedium,
                            color = TrophyGold,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "نادي أو منتخب محدد، اذكر أكبر عدد من نجومه مع تصحيح تلقائي وذكي للأسماء.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ThirtySecondsQuizScreen(viewModel: FootballGameViewModel) {
    val userProfile by viewModel.userProfile.collectAsState()
    val seconds by viewModel.tsSecondsRemaining.collectAsState()
    val score by viewModel.tsScore.collectAsState()
    val isGameOver by viewModel.tsIsGameOver.collectAsState()
    val selectedAnswer by viewModel.tsSelectedAnswer.collectAsState()
    val currentQuestion = viewModel.currentTsQuestion

    Scaffold(
        containerColor = PitchDark,
        topBar = {
            StadiumTopBar(
                title = "أسئلة السرعة ⚡",
                coins = userProfile.coins,
                points = userProfile.points,
                onBack = { viewModel.navigateBack() }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Live Stats Bar: Seconds + Score
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(PitchCard)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "⏱️ $seconds ثانية",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (seconds <= 5) CardRed else TrophyGold,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "النقاط: $score ⚽",
                        style = MaterialTheme.typography.titleMedium,
                        color = EmeraldNeon,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Countdown progress bar
            LinearProgressIndicator(
                progress = { (seconds / 30f).coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = if (seconds <= 5) CardRed else EmeraldNeon,
                trackColor = PitchCardBorder
            )

            Spacer(modifier = Modifier.height(24.dp))

            if (currentQuestion != null) {
                // Category Chip
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(EmeraldNeon.copy(alpha = 0.15f))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = currentQuestion.category,
                        color = EmeraldNeon,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Question Card
                GlowCard(
                    modifier = Modifier.fillMaxWidth(),
                    borderColor = PitchCardBorder,
                    backgroundColor = PitchCardElevated
                ) {
                    Text(
                        text = currentQuestion.question,
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        lineHeight = 28.sp
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Options
                currentQuestion.options.forEachIndexed { index, option ->
                    val isSelected = selectedAnswer == index
                    val isCorrect = index == currentQuestion.correctIndex

                    val (bgColor, borderColor) = when {
                        isSelected && isCorrect -> Color(0xFF1B5E20) to EmeraldNeon
                        isSelected && !isCorrect -> Color(0xFF5A141A) to CardRed
                        else -> PitchCard to PitchCardBorder
                    }

                    Box(
                        modifier = Modifier
                            .testTag("ts_option_$index")
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(bgColor)
                            .border(1.5.dp, borderColor, RoundedCornerShape(16.dp))
                            .clickable(enabled = selectedAnswer == null) {
                                viewModel.answerThirtySecondsQuestion(index)
                            }
                            .padding(horizontal = 18.dp, vertical = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = option,
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }

    if (isGameOver) {
        Dialog(onDismissRequest = { viewModel.navigateBack() }) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(PitchSurface)
                    .border(2.dp, TrophyGold, RoundedCornerShape(24.dp))
                    .padding(24.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    com.example.ui.components.LottieVictoryCelebration(modifier = Modifier.size(105.dp))
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "انتهت الـ 30 ثانية!",
                        style = MaterialTheme.typography.headlineSmall,
                        color = TrophyGold,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "حققت $score إجابات صحيحة في وقت قياسي! ⚽",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "+${score * 15} نقطة خبرة • +${score * 5} ذهب 🪙",
                        color = EmeraldNeon,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    TrophyButton(
                        text = "العب جولة أخرى 🔄",
                        onClick = { viewModel.startThirtySecondsQuiz() },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    TextButton(
                        onClick = { viewModel.navigateBack() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(text = "العودة للقائمة الرئيسية", color = TextSecondary)
                    }
                }
            }
        }
    }
}

@Composable
fun ThirtySecondsPlayersScreen(viewModel: FootballGameViewModel) {
    val userProfile by viewModel.userProfile.collectAsState()
    val challenge by viewModel.pmCurrentChallenge.collectAsState()
    val guessedPlayers by viewModel.pmGuessedPlayers.collectAsState()
    val seconds by viewModel.pmSecondsRemaining.collectAsState()
    val isGameOver by viewModel.pmIsGameOver.collectAsState()
    val feedbackMessage by viewModel.pmFeedbackMessage.collectAsState()

    var textInput by remember { mutableStateOf("") }

    Scaffold(
        containerColor = PitchDark,
        topBar = {
            StadiumTopBar(
                title = "اذكر نجوم الفريق ✍️",
                coins = userProfile.coins,
                points = userProfile.points,
                onBack = { viewModel.navigateBack() }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Stats bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(PitchCard)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "⏱️ $seconds ثانية",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (seconds <= 5) CardRed else TrophyGold,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "اللاعبين: ${guessedPlayers.size} 🏃",
                    style = MaterialTheme.typography.titleMedium,
                    color = EmeraldNeon,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Timer bar
            LinearProgressIndicator(
                progress = { (seconds / 30f).coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = if (seconds <= 5) CardRed else EmeraldNeon,
                trackColor = PitchCardBorder
            )

            Spacer(modifier = Modifier.height(16.dp))

            val activeChallenge = challenge
            if (activeChallenge != null) {
                // Team Card
                GlowCard(
                    modifier = Modifier.fillMaxWidth(),
                    borderColor = TrophyGold.copy(alpha = 0.5f),
                    backgroundColor = PitchCardElevated
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(text = activeChallenge.badgeEmoji, fontSize = 42.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = activeChallenge.clubOrCountry,
                            style = MaterialTheme.typography.headlineMedium,
                            color = TrophyGold,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "اذكر أكبر عدد من اللاعبين (الحاليين أو التاريخيين)",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Input Bar with Send button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = textInput,
                        onValueChange = { textInput = it },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("player_input_field"),
                        placeholder = { Text("اكتب اسم اللاعب هنا...", color = TextMuted) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = EmeraldNeon,
                            unfocusedBorderColor = PitchCardBorder,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedContainerColor = PitchCard,
                            unfocusedContainerColor = PitchCard
                        ),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            if (textInput.isNotBlank()) {
                                viewModel.submitPlayerName(textInput)
                                textInput = ""
                            }
                        })
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    IconButton(
                        onClick = {
                            if (textInput.isNotBlank()) {
                                viewModel.submitPlayerName(textInput)
                                textInput = ""
                            }
                        },
                        modifier = Modifier
                            .size(54.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(EmeraldNeon)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Send,
                            contentDescription = "إرسال",
                            tint = PitchBlack
                        )
                    }
                }

                // Feedback message
                AnimatedVisibility(visible = feedbackMessage != null) {
                    Text(
                        text = feedbackMessage ?: "",
                        color = if (feedbackMessage?.contains("صحيحة") == true) EmeraldNeon else CardYellow,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Guessed players chips
                Text(
                    text = "اللاعبون المكتشفون (${guessedPlayers.size}):",
                    style = MaterialTheme.typography.titleSmall,
                    color = TextSecondary,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(guessedPlayers) { player ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(PitchCard)
                                .border(1.dp, PitchCardBorder, RoundedCornerShape(12.dp))
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(text = "⚽", fontSize = 16.sp)
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = player,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = EmeraldNeon,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (isGameOver) {
        Dialog(onDismissRequest = { viewModel.navigateBack() }) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(PitchSurface)
                    .border(2.dp, TrophyGold, RoundedCornerShape(24.dp))
                    .padding(24.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    com.example.ui.components.LottieVictoryCelebration(modifier = Modifier.size(105.dp))
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "انتهى الوقت!",
                        style = MaterialTheme.typography.headlineSmall,
                        color = TrophyGold,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "استطعت تذكر ${guessedPlayers.size} لاعبين بنجاح في 30 ثانية!",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "+${guessedPlayers.size * 20} نقطة • +${guessedPlayers.size * 8} ذهب 🪙",
                        color = EmeraldNeon,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    TrophyButton(
                        text = "تحدي نادي آخر 🔄",
                        onClick = { viewModel.startPlayerMentionChallenge() },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    TextButton(
                        onClick = { viewModel.navigateBack() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(text = "العودة للرئيسية", color = TextSecondary)
                    }
                }
            }
        }
    }
}
