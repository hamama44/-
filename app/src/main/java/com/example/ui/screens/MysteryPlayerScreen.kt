package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.example.ui.components.EmeraldButton
import com.example.ui.components.GlowCard
import com.example.ui.components.StadiumTopBar
import com.example.ui.components.TrophyButton
import com.example.ui.theme.*

@Composable
fun MysteryPlayerScreen(viewModel: FootballGameViewModel) {
    val userProfile by viewModel.userProfile.collectAsState()
    val mysteryIndex by viewModel.mysteryIndex.collectAsState()
    val revealedCount by viewModel.revealedHintsCount.collectAsState()
    val isGuessed by viewModel.mysteryGuessed.collectAsState()
    val isCorrect by viewModel.mysteryCorrect.collectAsState()
    val totalScore by viewModel.mysteryScore.collectAsState()

    val currentMystery = viewModel.currentMysteryPlayer
    val potentialScore = (100 - (revealedCount - 1) * 15).coerceAtLeast(25)

    Scaffold(
        containerColor = PitchDark,
        topBar = {
            StadiumTopBar(
                title = "العب مع سري 🕵️‍♂️",
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
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Mystery Score Banner
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color(0xFF0A2B42), Color(0xFF041926))
                            )
                        )
                        .border(1.dp, StadiumCyan.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "جائزة التخمين الحالية",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                        Text(
                            text = "$potentialScore نقطة ⭐",
                            style = MaterialTheme.typography.titleLarge,
                            color = StadiumCyan,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(PitchCard)
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "التلميحات: $revealedCount / ${currentMystery?.hints?.size ?: 5}",
                            color = TrophyGold,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            // Hints List
            if (currentMystery != null) {
                currentMystery.hints.forEachIndexed { index, hint ->
                    val isRevealed = index < revealedCount

                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (isRevealed) PitchCard else PitchBlack)
                                .border(
                                    1.dp,
                                    if (isRevealed) StadiumCyan.copy(alpha = 0.4f) else PitchCardBorder,
                                    RoundedCornerShape(16.dp)
                                )
                                .padding(14.dp)
                        ) {
                            if (isRevealed) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(text = hint.iconEmoji, fontSize = 24.sp)
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = hint.title,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = StadiumCyan,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = hint.value,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = Color.White,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            } else {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(text = "🔒", fontSize = 20.sp)
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text(
                                            text = "تلميح مغلق (${hint.title})",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = TextMuted
                                        )
                                    }
                                    Text(
                                        text = "-15 نقطة",
                                        color = CardYellow,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                }

                // Unlock next hint button
                if (revealedCount < currentMystery.hints.size && !isGuessed) {
                    item {
                        OutlinedButton(
                            onClick = { viewModel.revealNextHint() },
                            modifier = Modifier
                                .testTag("unlock_hint_button")
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = StadiumCyan),
                            border = ButtonDefaults.outlinedButtonBorder().copy(brush = Brush.horizontalGradient(listOf(StadiumCyan, TrophyGold)))
                        ) {
                            Text(
                                text = "كشف تلميح إضافي 🔍 (-15 نقطة)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    }
                }

                // Guessing Section
                item {
                    Text(
                        text = if (!isGuessed) "من هو هذا اللاعب؟ 🤔" else "نتيجة التخمين:",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                if (!isGuessed) {
                    currentMystery.options.forEach { option ->
                        item {
                            Box(
                                modifier = Modifier
                                    .testTag("mystery_option_$option")
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(PitchCard)
                                    .border(1.dp, PitchCardBorder, RoundedCornerShape(14.dp))
                                    .clickable { viewModel.guessMysteryPlayer(option) }
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = option,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                } else {
                    item {
                        GlowCard(
                            modifier = Modifier.fillMaxWidth(),
                            borderColor = if (isCorrect) EmeraldNeon else CardRed,
                            backgroundColor = if (isCorrect) Color(0xFF0C2B1B) else Color(0xFF381016)
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(text = if (isCorrect) "🎉 أحسنت!" else "❌ إجابة خاطئة", fontSize = 28.sp)
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "اللاعب هو: ${currentMystery.playerName}",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = TrophyGold,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = if (isCorrect) "ربحت $potentialScore نقطة كروية!" else "حظ أوفر في اللاعب القادم",
                                    color = Color.White,
                                    fontSize = 14.sp
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                TrophyButton(
                                    text = "اللاعب التالي ⬅️",
                                    onClick = { viewModel.nextMysteryPlayer() },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
