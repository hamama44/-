package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.NewGamesDataProvider
import com.example.data.TruthOrDareItem
import com.example.data.TruthOrDareType
import com.example.ui.FootballGameViewModel
import com.example.ui.components.EmeraldButton
import com.example.ui.components.GlowCard
import com.example.ui.components.StadiumTopBar
import com.example.ui.components.TrophyButton
import com.example.ui.theme.*
import kotlinx.coroutines.launch
import kotlin.random.Random

@Composable
fun TruthOrDareScreen(viewModel: FootballGameViewModel) {
    val userProfile by viewModel.userProfile.collectAsState()
    val scope = rememberCoroutineScope()
    val allItems = remember { NewGamesDataProvider.truthOrDareItems }

    // Bottle spin animation angle
    val rotation = remember { Animatable(0f) }
    var isSpinning by remember { mutableStateOf(false) }

    // Current challenge state
    var selectedType by remember { mutableStateOf<TruthOrDareType?>(null) }
    var currentItem by remember { mutableStateOf<TruthOrDareItem?>(null) }
    var completedCount by remember { mutableIntStateOf(0) }

    fun spinBottle() {
        if (isSpinning) return
        viewModel.audioEffects.playWhistle()
        isSpinning = true
        selectedType = null
        currentItem = null

        scope.launch {
            val targetRotation = rotation.value + 1080f + Random.nextInt(0, 360)
            rotation.animateTo(
                targetValue = targetRotation,
                animationSpec = tween(durationMillis = 2200, easing = FastOutSlowInEasing)
            )
            isSpinning = false
            viewModel.audioEffects.playTick()
        }
    }

    fun pickChallenge(type: TruthOrDareType) {
        selectedType = type
        viewModel.audioEffects.playClick()
        val pool = allItems.filter { it.type == type }
        currentItem = pool.random()
    }

    Scaffold(
        containerColor = PitchDark,
        topBar = {
            StadiumTopBar(
                title = "صراحة أو جرأة 💬🔥",
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
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "لعبة صراحة وتحديات التجمعات",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(TrophyGold.copy(alpha = 0.2f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(text = "التحديات المكتملة: $completedCount 🔥", fontSize = 11.sp, color = TrophyGold, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // -----------------------------------------------------------------
            // THE SPINNING BOTTLE / ARROW
            // -----------------------------------------------------------------
            Box(
                modifier = Modifier
                    .size(160.dp)
                    .clip(CircleShape)
                    .background(PitchCard)
                    .border(2.dp, if (isSpinning) EmeraldNeon else PitchDivider, CircleShape)
                    .clickable(enabled = !isSpinning) { spinBottle() },
                contentAlignment = Alignment.Center
            ) {
                // The spinning pointer / bottle
                Text(
                    text = "🍾",
                    fontSize = 68.sp,
                    modifier = Modifier.rotate(rotation.value)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Button(
                onClick = { spinBottle() },
                enabled = !isSpinning,
                colors = ButtonDefaults.buttonColors(containerColor = if (isSpinning) PitchDivider else EmeraldNeon),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.Casino, contentDescription = null, tint = PitchDark)
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isSpinning) "الزجاجة تدور... 🍾" else "لف الزجاجة لاختيار اللاعب 🔄",
                    color = PitchDark,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            // -----------------------------------------------------------------
            // CHOOSE TRUTH OR DARE BUTTONS
            // -----------------------------------------------------------------
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Truth Button
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .height(84.dp)
                        .clickable(enabled = !isSpinning) { pickChallenge(TruthOrDareType.TRUTH) }
                        .testTag("btn_truth"),
                    colors = CardDefaults.cardColors(
                        containerColor = if (selectedType == TruthOrDareType.TRUTH) StadiumCyan.copy(alpha = 0.25f) else PitchCard
                    ),
                    border = androidx.compose.foundation.BorderStroke(
                        2.dp,
                        if (selectedType == TruthOrDareType.TRUTH) StadiumCyan else PitchDivider
                    ),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(text = "💬", fontSize = 24.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = "صراحة", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }

                // Dare Button
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .height(84.dp)
                        .clickable(enabled = !isSpinning) { pickChallenge(TruthOrDareType.DARE) }
                        .testTag("btn_dare"),
                    colors = CardDefaults.cardColors(
                        containerColor = if (selectedType == TruthOrDareType.DARE) Color(0xFFFF5252).copy(alpha = 0.25f) else PitchCard
                    ),
                    border = androidx.compose.foundation.BorderStroke(
                        2.dp,
                        if (selectedType == TruthOrDareType.DARE) Color(0xFFFF5252) else PitchDivider
                    ),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(text = "🔥", fontSize = 24.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = "جرأة", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // -----------------------------------------------------------------
            // CHALLENGE DISPLAY CARD
            // -----------------------------------------------------------------
            currentItem?.let { item ->
                GlowCard(
                    modifier = Modifier.fillMaxWidth(),
                    borderColor = if (item.type == TruthOrDareType.TRUTH) StadiumCyan else Color(0xFFFF5252)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(TrophyGold.copy(alpha = 0.2f))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = item.category,
                                fontSize = 11.sp,
                                color = TrophyGold,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = item.content,
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            lineHeight = 22.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(18.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = {
                                    viewModel.audioEffects.playVictory()
                                    completedCount++
                                    viewModel.repository.addPointsAndCoins(item.pointsReward, 20)
                                    viewModel.recordPartyGameScore(
                                        "صراحة أو جرأة",
                                        "party",
                                        item.pointsReward,
                                        "إتمام تحدي (${if (item.type == TruthOrDareType.TRUTH) "صراحة 💬" else "جرأة 🔥"}) في الجلسة"
                                    )
                                    currentItem = null
                                    selectedType = null
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = EmeraldNeon),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = PitchDark)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("تم التنفيذ! ✓", color = PitchDark, fontWeight = FontWeight.Bold)
                            }

                            OutlinedButton(
                                onClick = {
                                    viewModel.audioEffects.playClick()
                                    pickChallenge(item.type)
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                border = androidx.compose.foundation.BorderStroke(1.dp, PitchDivider),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("تغيير السؤال 🔄", fontSize = 12.sp)
                            }
                        }
                    }
                }
            } ?: run {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = PitchCard),
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, PitchDivider)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "قم بلف الزجاجة 🍾 ثم اختر 'صراحة' أو 'جرأة' لإظهار التحدي المثير!",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}
