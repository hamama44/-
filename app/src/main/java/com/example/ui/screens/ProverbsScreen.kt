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
fun ProverbsScreen(viewModel: FootballGameViewModel) {
    val proverb by viewModel.proverbCurrent.collectAsState()
    val streak by viewModel.proverbStreak.collectAsState()
    val score by viewModel.proverbScore.collectAsState()
    val selectedOption by viewModel.selectedProverbOption.collectAsState()
    val isCorrect by viewModel.proverbIsCorrect.collectAsState()

    Scaffold(
        containerColor = PitchDark,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "📜", fontSize = 22.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "كمّل المثل الشعبي",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "مجموع النقاط: $score 🌟",
                                style = MaterialTheme.typography.labelSmall,
                                color = TrophyGold
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = { viewModel.navigateBack() },
                        modifier = Modifier.testTag("proverbs_back_button")
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
                        onClick = { viewModel.nextProverb() },
                        modifier = Modifier.testTag("proverbs_skip_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "مثل آخر",
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
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Origin & Streak Banner
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(TrophyGold.copy(alpha = 0.15f))
                            .border(1.dp, TrophyGold.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "تراث: ${proverb.origin} 🏺",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = TrophyGold
                        )
                    }

                    if (streak > 0) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFFFF9100).copy(alpha = 0.2f))
                                .border(1.dp, Color(0xFFFF9100), RoundedCornerShape(10.dp))
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "🔥 سلسلة: ${streak}x",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFFFF9100)
                            )
                        }
                    }
                }
            }

            // Proverb Prompt Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = PitchCard),
                    border = BorderStroke(1.5.dp, Brush.horizontalGradient(listOf(Color(0xFFFF9100), TrophyGold)))
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            text = "أكمل المثل الشعبي التالي:",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = 0.7f)
                        )

                        Text(
                            text = "« ${proverb.prompt} ... »",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Black,
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            lineHeight = 36.sp
                        )
                    }
                }
            }

            // Options (4 Choices)
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    proverb.options.forEach { option ->
                        val isSelected = selectedOption == option
                        val isAnswerRevealed = selectedOption != null
                        val isThisCorrect = option == proverb.correctAnswer

                        val cardBg = when {
                            !isAnswerRevealed -> PitchCard
                            isThisCorrect -> Color(0xFF0F3D24)
                            isSelected -> Color(0xFF3D1414)
                            else -> PitchCard
                        }

                        val cardBorder = when {
                            !isAnswerRevealed -> PitchDivider
                            isThisCorrect -> EmeraldNeon
                            isSelected -> Color.Red
                            else -> PitchDivider
                        }

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable(enabled = !isAnswerRevealed) {
                                    viewModel.selectProverbAnswer(option)
                                }
                                .testTag("proverb_opt_$option"),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = cardBg),
                            border = BorderStroke(if (isThisCorrect && isAnswerRevealed) 2.dp else 1.dp, cardBorder)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = option,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )

                                if (isAnswerRevealed) {
                                    Text(
                                        text = if (isThisCorrect) "✅ صح" else if (isSelected) "❌ خطأ" else "",
                                        fontWeight = FontWeight.Bold,
                                        color = if (isThisCorrect) EmeraldNeon else Color.Red
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Fun meaning explanation & Next button
            if (selectedOption != null) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isCorrect == true) Color(0xFF0F2B1D) else Color(0xFF2B1515)
                        ),
                        border = BorderStroke(1.dp, if (isCorrect == true) EmeraldNeon else Color.Red)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = if (isCorrect == true) "أحسنت! حافظ التراث بإتقان 🌟" else "إجابة خاطئة! المثل الصحيح: « ${proverb.prompt} ${proverb.correctAnswer} »",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                color = if (isCorrect == true) EmeraldNeon else Color(0xFFFF8A80),
                                textAlign = TextAlign.Center
                            )

                            Text(
                                text = "💡 حكاية المثل: ${proverb.funMeaning}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TrophyGold,
                                textAlign = TextAlign.Center
                            )

                            Button(
                                onClick = { viewModel.nextProverb() },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("next_proverb_button"),
                                colors = ButtonDefaults.buttonColors(containerColor = TrophyGold)
                            ) {
                                Text("المثل التالي ⏩", color = PitchDark, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}
