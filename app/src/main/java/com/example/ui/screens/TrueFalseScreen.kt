package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
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
import com.example.ui.FootballGameViewModel
import com.example.ui.components.GlowCard
import com.example.ui.components.StadiumTopBar
import com.example.ui.components.TrophyButton
import com.example.ui.theme.*

@Composable
fun TrueFalseScreen(viewModel: FootballGameViewModel) {
    val userProfile by viewModel.userProfile.collectAsState()
    val streak by viewModel.tfStreak.collectAsState()
    val score by viewModel.tfScore.collectAsState()
    val selectedAnswer by viewModel.tfSelected.collectAsState()
    val isExplanationVisible by viewModel.tfExplanationVisible.collectAsState()

    val currentQuestion = viewModel.currentTrueFalse

    Scaffold(
        containerColor = PitchDark,
        topBar = {
            StadiumTopBar(
                title = "صح أو غلط ⚡",
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
            // Streak Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(PitchCard)
                    .border(1.dp, PitchCardBorder, RoundedCornerShape(16.dp))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF3E1E02)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "🔥", fontSize = 18.sp)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "سلسلة الإجابات",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                        Text(
                            text = "$streak متتالية (${streak}x)",
                            style = MaterialTheme.typography.titleMedium,
                            color = CardYellow,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "النقاط المحققة",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                    Text(
                        text = "$score نقطة ⭐",
                        style = MaterialTheme.typography.titleMedium,
                        color = EmeraldNeon,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            if (currentQuestion != null) {
                // Category Tag
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(EmeraldNeon.copy(alpha = 0.15f))
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = currentQuestion.category,
                        color = EmeraldNeon,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Statement Card
                GlowCard(
                    modifier = Modifier.fillMaxWidth(),
                    borderColor = PitchCardBorder,
                    backgroundColor = PitchCardElevated
                ) {
                    Text(
                        text = currentQuestion.statement,
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 18.dp),
                        lineHeight = 32.sp
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Action Buttons: TRUE (صح) or FALSE (غلط)
                if (selectedAnswer == null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // TRUE Button
                        Box(
                            modifier = Modifier
                                .testTag("button_true")
                                .weight(1f)
                                .height(76.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(Color(0xFF0F3E22))
                                .border(2.dp, EmeraldNeon, RoundedCornerShape(18.dp))
                                .clickable { viewModel.answerTrueFalse(true) }
                                .padding(12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(text = "✅", fontSize = 28.sp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "صـح",
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Black,
                                    color = EmeraldNeon
                                )
                            }
                        }

                        // FALSE Button
                        Box(
                            modifier = Modifier
                                .testTag("button_false")
                                .weight(1f)
                                .height(76.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(Color(0xFF421217))
                                .border(2.dp, CardRed, RoundedCornerShape(18.dp))
                                .clickable { viewModel.answerTrueFalse(false) }
                                .padding(12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(text = "❌", fontSize = 28.sp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "غـلـط",
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Black,
                                    color = CardRed
                                )
                            }
                        }
                    }
                } else {
                    // Result & Explanation Box
                    val wasCorrect = selectedAnswer == currentQuestion.isTrue

                    GlowCard(
                        modifier = Modifier.fillMaxWidth(),
                        borderColor = if (wasCorrect) EmeraldNeon else CardRed,
                        backgroundColor = if (wasCorrect) Color(0xFF0C2B1B) else Color(0xFF381016)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = if (wasCorrect) "إجابة صحيحة! 🎉" else "إجابة خاطئة ❌",
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (wasCorrect) EmeraldNeon else CardRed
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = currentQuestion.explanation,
                                style = MaterialTheme.typography.bodyLarge,
                                color = Color.White,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            TrophyButton(
                                text = "السؤال التالي ⬅️",
                                onClick = { viewModel.nextTrueFalse() },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
    }
}
