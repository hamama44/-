package com.example.ui.screens

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
fun CareerPathScreen(viewModel: FootballGameViewModel) {
    val userProfile by viewModel.userProfile.collectAsState()
    val careerIndex by viewModel.careerIndex.collectAsState()
    val isGuessed by viewModel.careerGuessed.collectAsState()
    val selectedName by viewModel.careerSelected.collectAsState()
    val totalScore by viewModel.careerScore.collectAsState()

    val currentChallenge = viewModel.currentCareerChallenge

    Scaffold(
        containerColor = PitchDark,
        topBar = {
            StadiumTopBar(
                title = "مسيرة اللاعب 🗺️",
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
            // Hint Header
            if (currentChallenge != null) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(PitchCard)
                            .border(1.dp, PitchCardBorder, RoundedCornerShape(16.dp))
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = currentChallenge.nationalityFlag, fontSize = 28.sp)
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "الجنسية: ${currentChallenge.nationality}",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "المركز: ${currentChallenge.position}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TrophyGold
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(TrophyGold.copy(alpha = 0.15f))
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "50 نقطة ⭐",
                                color = TrophyGold,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                // Career Timeline
                item {
                    Text(
                        text = "محطات الأندية بالترتيب الزمني:",
                        style = MaterialTheme.typography.titleSmall,
                        color = TextSecondary
                    )
                }

                currentChallenge.careerTimeline.forEachIndexed { index, club ->
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Timeline dot & number
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(PitchCardElevated)
                                    .border(1.5.dp, TrophyGold, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "${index + 1}",
                                    color = TrophyGold,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            // Club Box
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(PitchCard)
                                    .border(1.dp, PitchCardBorder, RoundedCornerShape(14.dp))
                                    .padding(horizontal = 14.dp, vertical = 10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = "${club.countryFlag} ${club.clubName}",
                                            style = MaterialTheme.typography.titleMedium,
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = club.appearancesAndGoals,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = EmeraldLight
                                        )
                                    }
                                    Text(
                                        text = club.years,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextSecondary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                }

                // Options / Guess Section
                item {
                    Text(
                        text = if (!isGuessed) "من صاحب هذه المسيرة التاريخية؟ ⚽" else "النتيجة:",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 10.dp)
                    )
                }

                if (!isGuessed) {
                    currentChallenge.options.forEach { option ->
                        item {
                            Box(
                                modifier = Modifier
                                    .testTag("career_option_$option")
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(PitchCard)
                                    .border(1.dp, PitchCardBorder, RoundedCornerShape(14.dp))
                                    .clickable { viewModel.answerCareer(option) }
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
                        val isCorrect = selectedName == currentChallenge.playerName
                        GlowCard(
                            modifier = Modifier.fillMaxWidth(),
                            borderColor = if (isCorrect) EmeraldNeon else CardRed,
                            backgroundColor = if (isCorrect) Color(0xFF0C2B1B) else Color(0xFF381016)
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = if (isCorrect) "صحيح! أسطورة حقيقية 👑" else "إجابة غير صحيحة ❌",
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isCorrect) EmeraldNeon else CardRed
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "صاحب المسيرة هو: ${currentChallenge.playerName}",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = TrophyGold,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                TrophyButton(
                                    text = "المسيرة التالية ⬅️",
                                    onClick = { viewModel.nextCareerChallenge() },
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
