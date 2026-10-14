package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.Difficulty
import com.example.ui.FootballGameViewModel
import com.example.ui.components.GlowCard
import com.example.ui.components.StadiumTopBar
import com.example.ui.components.TrophyButton
import com.example.ui.theme.*

@Composable
fun SabahoScreen(viewModel: FootballGameViewModel) {
    val userProfile by viewModel.userProfile.collectAsState()
    val questionIndex by viewModel.sabahoIndex.collectAsState()
    val score by viewModel.sabahoScore.collectAsState()
    val timer by viewModel.sabahoTimer.collectAsState()
    val removedOptions by viewModel.sabahoRemovedOptions.collectAsState()
    val usedFiftyFifty by viewModel.sabahoUsedFiftyFifty.collectAsState()
    val usedSkip by viewModel.sabahoUsedSkip.collectAsState()
    val usedExtraTime by viewModel.sabahoUsedExtraTime.collectAsState()
    val selectedAnswer by viewModel.sabahoSelectedAnswer.collectAsState()
    val isGameOver by viewModel.sabahoGameOver.collectAsState()

    val currentQuestion = viewModel.currentSabahoQuestion

    Scaffold(
        containerColor = PitchDark,
        topBar = {
            StadiumTopBar(
                title = "صباحو تحدي 🏆",
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
            // Header stats
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(PitchCard)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Difficulty badge
                val diffColor = when (currentQuestion?.difficulty) {
                    Difficulty.EASY -> EmeraldNeon
                    Difficulty.MEDIUM -> CardYellow
                    Difficulty.HARD -> CardRed
                    null -> TrophyGold
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(diffColor.copy(alpha = 0.2f))
                        .border(1.dp, diffColor, RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = currentQuestion?.difficulty?.labelArabic ?: "",
                        color = diffColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }

                Text(
                    text = "⏱️ $timer ث",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (timer <= 5) CardRed else TrophyGold,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "النقاط: $score 🏅",
                    style = MaterialTheme.typography.titleMedium,
                    color = TrophyGold,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Timer bar
            LinearProgressIndicator(
                progress = { (timer / 25f).coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = if (timer <= 5) CardRed else TrophyGold,
                trackColor = PitchCardBorder
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Lifelines Row (وسائل المساعدة الثلاث - كل واحدة مرة واحدة)
            Text(
                text = "وسائل المساعدة المتاحة (مرة واحدة لكل مباراة):",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 50:50 Lifeline
                LifelineChip(
                    label = "حذف إجابتين (50:50)",
                    icon = "✂️",
                    isUsed = usedFiftyFifty,
                    modifier = Modifier.weight(1f),
                    testTag = "lifeline_fifty_fifty",
                    onClick = { viewModel.useFiftyFifty() }
                )

                // Skip Question Lifeline
                LifelineChip(
                    label = "تغيير السؤال",
                    icon = "🔄",
                    isUsed = usedSkip,
                    modifier = Modifier.weight(1f),
                    testTag = "lifeline_skip",
                    onClick = { viewModel.useSkipQuestion() }
                )

                // Extra Time Lifeline
                LifelineChip(
                    label = "+15 ثانية",
                    icon = "⏳",
                    isUsed = usedExtraTime,
                    modifier = Modifier.weight(1f),
                    testTag = "lifeline_time",
                    onClick = { viewModel.useExtraTime() }
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            if (currentQuestion != null) {
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
                            .padding(vertical = 14.dp),
                        lineHeight = 28.sp
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Options
                currentQuestion.options.forEachIndexed { index, option ->
                    val isRemoved = removedOptions.contains(index)
                    val isSelected = selectedAnswer == index
                    val isCorrect = index == currentQuestion.correctIndex

                    val (bgColor, borderColor) = when {
                        isRemoved -> PitchBlack.copy(alpha = 0.3f) to Color.Transparent
                        isSelected && isCorrect -> Color(0xFF1B5E20) to EmeraldNeon
                        isSelected && !isCorrect -> Color(0xFF5A141A) to CardRed
                        selectedAnswer != null && isCorrect -> Color(0xFF1B5E20) to EmeraldNeon
                        else -> PitchCard to PitchCardBorder
                    }

                    if (!isRemoved) {
                        Box(
                            modifier = Modifier
                                .testTag("sabaho_option_$index")
                                .fillMaxWidth()
                                .padding(vertical = 5.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(bgColor)
                                .border(1.5.dp, borderColor, RoundedCornerShape(16.dp))
                                .clickable(enabled = selectedAnswer == null) {
                                    viewModel.answerSabahoQuestion(index)
                                }
                                .padding(horizontal = 18.dp, vertical = 14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = option,
                                style = MaterialTheme.typography.titleMedium,
                                color = if (selectedAnswer != null && isCorrect) EmeraldLight else Color.White,
                                textAlign = TextAlign.Center,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    } else {
                        // Place an empty faded row to maintain visual stability
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 5.dp)
                                .height(50.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(PitchDark.copy(alpha = 0.4f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "تم حذف الإجابة ❌",
                                color = TextMuted,
                                fontSize = 12.sp
                            )
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
                        text = "نهاية صباحو تحدي!",
                        style = MaterialTheme.typography.headlineSmall,
                        color = TrophyGold,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "جمعت في هذه الجولة $score نقطة كروية رائعة!",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "+${score * 2} نقطة خبرة • +${(score * 0.8).toInt()} ذهب 🪙",
                        color = EmeraldNeon,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    TrophyButton(
                        text = "جولة جديدة 🔄",
                        onClick = { viewModel.startSabahoChallenge() },
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

@Composable
fun LifelineChip(
    label: String,
    icon: String,
    isUsed: Boolean,
    modifier: Modifier = Modifier,
    testTag: String,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .testTag(testTag)
            .clip(RoundedCornerShape(12.dp))
            .background(if (isUsed) PitchBlack else PitchCard)
            .border(
                1.dp,
                if (isUsed) PitchCardBorder else TrophyGold.copy(alpha = 0.6f),
                RoundedCornerShape(12.dp)
            )
            .clickable(enabled = !isUsed) { onClick() }
            .padding(vertical = 8.dp, horizontal = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = icon, fontSize = 16.sp)
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = if (isUsed) "مستخدمة" else label,
                color = if (isUsed) TextMuted else TrophyGold,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }
    }
}
