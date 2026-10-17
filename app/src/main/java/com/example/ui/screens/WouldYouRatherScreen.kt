package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
fun WouldYouRatherScreen(viewModel: FootballGameViewModel) {
    val question by viewModel.wouldYouRatherPromptState.collectAsState()
    val selectedOption by viewModel.wouldYouRatherSelectedOption.collectAsState()
    val totalAnswered by viewModel.wouldYouRatherTotalAnswered.collectAsState()

    Scaffold(
        containerColor = PitchDark,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "🤔", fontSize = 22.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "لو خيروك؟",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "أجبت عن $totalAnswered سؤالاً 📊",
                                style = MaterialTheme.typography.labelSmall,
                                color = TrophyGold
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = { viewModel.navigateBack() },
                        modifier = Modifier.testTag("wyr_back_button")
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
                        onClick = { viewModel.nextWouldYouRatherQuestion() },
                        modifier = Modifier.testTag("wyr_skip_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "سؤال آخر",
                            tint = TrophyGold
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = PitchSurface)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(PitchCard)
                    .border(1.dp, PitchDivider, RoundedCornerShape(12.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "قسم: ${question.category} 🎭",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = TrophyGold
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                WouldYouRatherOptionCard(
                    text = question.optionA,
                    percentage = question.percentageA,
                    isSelected = selectedOption == "A",
                    isRevealed = selectedOption != null,
                    gradientColors = listOf(Color(0xFF004D40), Color(0xFF00897B)),
                    accentColor = EmeraldNeon,
                    testTag = "wyr_option_a",
                    onClick = { viewModel.selectWouldYouRatherOption("A") }
                )

                Box(
                    modifier = Modifier
                        .offset(y = 0.dp)
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(TrophyGold)
                        .border(2.dp, Color.White, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "أو",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        color = PitchDark
                    )
                }

                WouldYouRatherOptionCard(
                    text = question.optionB,
                    percentage = 100 - question.percentageA,
                    isSelected = selectedOption == "B",
                    isRevealed = selectedOption != null,
                    gradientColors = listOf(Color(0xFF880E4F), Color(0xFFD81B60)),
                    accentColor = Color(0xFFFF4081),
                    testTag = "wyr_option_b",
                    onClick = { viewModel.selectWouldYouRatherOption("B") }
                )
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (selectedOption != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFF0F1E19))
                            .border(1.dp, TrophyGold.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                            .padding(14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "💡 ${question.funFact}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = TrophyGold,
                            textAlign = TextAlign.Center
                        )
                    }

                    Button(
                        onClick = { viewModel.nextWouldYouRatherQuestion() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .testTag("wyr_next_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(
                            text = "الموقف التالي ⏩",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    Text(
                        text = "اضغط على أحد الخيارين لتكتشف رأي الأغلبية! 👆",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}

@Composable
private fun WouldYouRatherOptionCard(
    text: String,
    percentage: Int,
    isSelected: Boolean,
    isRevealed: Boolean,
    gradientColors: List<Color>,
    accentColor: Color,
    testTag: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(130.dp)
            .padding(vertical = 4.dp)
            .clickable(enabled = !isRevealed) { onClick() }
            .testTag(testTag),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = PitchCard),
        border = BorderStroke(
            if (isSelected) 3.dp else 1.dp,
            if (isSelected) Color.White else accentColor.copy(alpha = 0.5f)
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.horizontalGradient(gradientColors))
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Start
                )

                if (isRevealed) {
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "$percentage%",
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White
                        )
                        if (isSelected) {
                            Text(
                                text = "اختيارك ✨",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = TrophyGold
                            )
                        }
                    }
                }
            }
        }
    }
}
