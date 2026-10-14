package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
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
fun EventOrderingScreen(viewModel: FootballGameViewModel) {
    val userProfile by viewModel.userProfile.collectAsState()
    val challengeIndex by viewModel.eventChallengeIndex.collectAsState()
    val orderItems by viewModel.currentOrderItems.collectAsState()
    val isValidated by viewModel.orderValidated.collectAsState()
    val isAllCorrect by viewModel.orderIsAllCorrect.collectAsState()

    val challenges = viewModel.repository.getEventOrderChallenges()
    val currentChallenge = challenges.getOrNull(challengeIndex)

    Scaffold(
        containerColor = PitchDark,
        topBar = {
            StadiumTopBar(
                title = "ترتيب الأحداث 📋",
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
            if (currentChallenge != null) {
                // Header card
                item {
                    GlowCard(
                        modifier = Modifier.fillMaxWidth(),
                        borderColor = TrophyGold.copy(alpha = 0.5f),
                        backgroundColor = PitchCardElevated
                    ) {
                        Text(
                            text = currentChallenge.title,
                            style = MaterialTheme.typography.titleLarge,
                            color = TrophyGold,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = currentChallenge.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                item {
                    Text(
                        text = "استخدم الأسهم لإعادة ترتيب البطاقات من الأقدم إلى الأحدث:",
                        style = MaterialTheme.typography.labelSmall,
                        color = EmeraldNeon
                    )
                }

                // Reorderable cards
                itemsIndexed(orderItems) { index, item ->
                    val isTop = index == 0
                    val isBottom = index == orderItems.size - 1

                    Box(
                        modifier = Modifier
                            .testTag("order_item_$index")
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(PitchCard)
                            .border(1.dp, PitchCardBorder, RoundedCornerShape(16.dp))
                            .padding(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Rank Number
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(PitchDark)
                                    .border(1.5.dp, TrophyGold, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "${index + 1}",
                                    color = TrophyGold,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            // Text details
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.text,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold
                                )
                                if (isValidated) {
                                    Text(
                                        text = "السنة: ${item.yearOrDetail}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = EmeraldNeon,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            // Up / Down controls
                            Column {
                                IconButton(
                                    onClick = { viewModel.moveOrderItem(index, up = true) },
                                    enabled = !isTop && !isValidated,
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.KeyboardArrowUp,
                                        contentDescription = "تحريك لأعلى",
                                        tint = if (!isTop && !isValidated) TrophyGold else TextMuted
                                    )
                                }
                                IconButton(
                                    onClick = { viewModel.moveOrderItem(index, up = false) },
                                    enabled = !isBottom && !isValidated,
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.KeyboardArrowDown,
                                        contentDescription = "تحريك لأسفل",
                                        tint = if (!isBottom && !isValidated) TrophyGold else TextMuted
                                    )
                                }
                            }
                        }
                    }
                }

                // Check or Next button
                item {
                    Spacer(modifier = Modifier.height(10.dp))
                    if (!isValidated) {
                        TrophyButton(
                            text = "تحقق من الترتيب 🏆",
                            onClick = { viewModel.validateOrder() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("validate_order_button")
                        )
                    } else {
                        GlowCard(
                            modifier = Modifier.fillMaxWidth(),
                            borderColor = if (isAllCorrect) EmeraldNeon else CardRed,
                            backgroundColor = if (isAllCorrect) Color(0xFF0C2B1B) else Color(0xFF381016)
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = if (isAllCorrect) "ترتيبك مثالي ودقيق 100%! 🥇" else "الترتيب غير صحيح، حاول ثانية! ❌",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isAllCorrect) EmeraldNeon else CardRed,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = if (isAllCorrect) "+60 نقطة خبرة • +30 ذهب 🪙" else "تم إظهار التواريخ لمساعدتك في المرة القادمة",
                                    color = Color.White,
                                    fontSize = 14.sp
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                TrophyButton(
                                    text = "التحدي التالي ⬅️",
                                    onClick = { viewModel.nextEventChallenge() },
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
