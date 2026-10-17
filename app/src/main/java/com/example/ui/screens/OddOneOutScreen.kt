package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Refresh
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
import com.example.data.NewGamesDataProvider
import com.example.data.OddOneOutQuestion
import com.example.ui.FootballGameViewModel
import com.example.ui.components.EmeraldButton
import com.example.ui.components.GlowCard
import com.example.ui.components.StadiumTopBar
import com.example.ui.theme.*

@Composable
fun OddOneOutScreen(viewModel: FootballGameViewModel) {
    val userProfile by viewModel.userProfile.collectAsState()
    val questions = remember { NewGamesDataProvider.oddOneOutQuestions }
    var currentIndex by remember { mutableIntStateOf(0) }
    val question = questions[currentIndex % questions.size]

    var selectedIndex by remember(currentIndex) { mutableStateOf<Int?>(null) }
    var isAnswered by remember(currentIndex) { mutableStateOf(false) }
    var isCorrect by remember(currentIndex) { mutableStateOf(false) }

    fun selectCard(index: Int) {
        if (isAnswered) return
        selectedIndex = index
        isAnswered = true
        if (index == question.oddIndex) {
            isCorrect = true
            viewModel.audioEffects.playVictory()
            viewModel.repository.addPointsAndCoins(50, 20)
            viewModel.recordPartyGameScore(
                "اكتشف الدخيل",
                "football",
                50,
                "اكتشاف الدخيل (${question.cards[index].name}) في ${question.title} 🔍"
            )
        } else {
            isCorrect = false
            viewModel.audioEffects.playWrong()
        }
    }

    Scaffold(
        containerColor = PitchDark,
        topBar = {
            StadiumTopBar(
                title = "اكتشف الدخيل 🔍⚽",
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
            // Category Badge & Question Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(TrophyGold.copy(alpha = 0.2f))
                        .border(1.dp, TrophyGold, RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = question.categoryBadge,
                        color = TrophyGold,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = "سؤال ${currentIndex + 1} من ${questions.size}",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            GlowCard(
                modifier = Modifier.fillMaxWidth(),
                borderColor = StadiumCyan.copy(alpha = 0.5f)
            ) {
                Column(modifier = Modifier.padding(6.dp)) {
                    Text(
                        text = question.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "3 عناصر يجمعهم سر واحد، وهناك عنصر وحيد دخيل.. اضغط عليه!",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // -----------------------------------------------------------------
            // 2x2 GRID OF 4 CARDS
            // -----------------------------------------------------------------
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Row 1: Cards 0 and 1
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OddCardItem(
                        card = question.cards[0],
                        index = 0,
                        selectedIndex = selectedIndex,
                        isAnswered = isAnswered,
                        correctIndex = question.oddIndex,
                        onSelect = { selectCard(0) },
                        modifier = Modifier.weight(1f)
                    )

                    OddCardItem(
                        card = question.cards[1],
                        index = 1,
                        selectedIndex = selectedIndex,
                        isAnswered = isAnswered,
                        correctIndex = question.oddIndex,
                        onSelect = { selectCard(1) },
                        modifier = Modifier.weight(1f)
                    )
                }

                // Row 2: Cards 2 and 3
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OddCardItem(
                        card = question.cards[2],
                        index = 2,
                        selectedIndex = selectedIndex,
                        isAnswered = isAnswered,
                        correctIndex = question.oddIndex,
                        onSelect = { selectCard(2) },
                        modifier = Modifier.weight(1f)
                    )

                    OddCardItem(
                        card = question.cards[3],
                        index = 3,
                        selectedIndex = selectedIndex,
                        isAnswered = isAnswered,
                        correctIndex = question.oddIndex,
                        onSelect = { selectCard(3) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Explanation & Result Card
            if (isAnswered) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isCorrect) EmeraldNeon.copy(alpha = 0.15f) else CardRed.copy(alpha = 0.15f)
                    ),
                    border = androidx.compose.foundation.BorderStroke(
                        1.5.dp,
                        if (isCorrect) EmeraldNeon else CardRed
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (isCorrect) Icons.Default.CheckCircle else Icons.Default.Close,
                                contentDescription = null,
                                tint = if (isCorrect) EmeraldNeon else CardRed
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isCorrect) "إجابة صحيحة وذكاء كروي فائق! 🌟" else "إجابة خاطئة! الدخيل هو ${question.cards[question.oddIndex].name}",
                                style = MaterialTheme.typography.titleSmall,
                                color = if (isCorrect) EmeraldNeon else CardRed,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = question.explanationReason,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            lineHeight = 20.sp
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        EmeraldButton(
                            text = "السؤال التالي ➡️",
                            onClick = {
                                currentIndex = (currentIndex + 1) % questions.size
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OddCardItem(
    card: com.example.data.OddOneOutCard,
    index: Int,
    selectedIndex: Int?,
    isAnswered: Boolean,
    correctIndex: Int,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isSelected = selectedIndex == index
    val isTarget = index == correctIndex

    val borderColor = when {
        !isAnswered -> PitchDivider
        isSelected && isTarget -> EmeraldNeon
        isSelected && !isTarget -> CardRed
        !isSelected && isTarget -> EmeraldNeon
        else -> PitchDivider
    }

    val backgroundColor = when {
        !isAnswered -> PitchCard
        isSelected && isTarget -> EmeraldNeon.copy(alpha = 0.25f)
        isSelected && !isTarget -> CardRed.copy(alpha = 0.25f)
        !isSelected && isTarget -> EmeraldNeon.copy(alpha = 0.15f)
        else -> PitchCard
    }

    Card(
        modifier = modifier
            .height(120.dp)
            .clickable(enabled = !isAnswered, onClick = onSelect)
            .testTag("odd_card_$index"),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, borderColor),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(text = card.icon, fontSize = 28.sp)
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = card.name,
                style = MaterialTheme.typography.titleSmall,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
            Text(
                text = card.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                textAlign = TextAlign.Center,
                fontSize = 10.sp,
                maxLines = 1
            )
        }
    }
}
