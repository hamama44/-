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
import com.example.data.JerseyQuestion
import com.example.data.NewGamesDataProvider
import com.example.data.TransferFeeQuestion
import com.example.ui.FootballGameViewModel
import com.example.ui.components.EmeraldButton
import com.example.ui.components.GlowCard
import com.example.ui.components.StadiumTopBar
import com.example.ui.theme.*

@Composable
fun JerseyAndTransfersScreen(viewModel: FootballGameViewModel) {
    val userProfile by viewModel.userProfile.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) } // 0: أرقام القمصان, 1: بورصة الانتقالات

    // Tab 1: Jerseys
    val jerseyQuestions = remember { NewGamesDataProvider.jerseyQuestions }
    var jerseyIndex by remember { mutableIntStateOf(0) }
    val jerseyQ = jerseyQuestions[jerseyIndex % jerseyQuestions.size]
    var selectedJerseyOption by remember(jerseyIndex) { mutableStateOf<Int?>(null) }
    var isJerseyAnswered by remember(jerseyIndex) { mutableStateOf(false) }

    // Tab 2: Transfers
    val transferQuestions = remember { NewGamesDataProvider.transferFeeQuestions }
    var transferIndex by remember { mutableIntStateOf(0) }
    val transferQ = transferQuestions[transferIndex % transferQuestions.size]
    var selectedTransferOption by remember(transferIndex) { mutableStateOf<String?>(null) }
    var isTransferAnswered by remember(transferIndex) { mutableStateOf(false) }

    Scaffold(
        containerColor = PitchDark,
        topBar = {
            StadiumTopBar(
                title = "أرقام القمصان وبورصة الانتقالات 🔢💰",
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
            // Tab Selector
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = PitchCard,
                contentColor = EmeraldNeon,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, PitchDivider, RoundedCornerShape(12.dp))
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = {
                        viewModel.audioEffects.playClick()
                        selectedTab = 0
                    },
                    text = { Text("أرقام القمصان 🔢", fontWeight = FontWeight.Bold) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = {
                        viewModel.audioEffects.playClick()
                        selectedTab = 1
                    },
                    text = { Text("بورصة الانتقالات 💰", fontWeight = FontWeight.Bold) }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            when (selectedTab) {
                0 -> {
                    // ---------------------------------------------------------
                    // MODE 1: JERSEY NUMBERS
                    // ---------------------------------------------------------
                    GlowCard(
                        modifier = Modifier.fillMaxWidth(),
                        borderColor = TrophyGold.copy(alpha = 0.5f)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(text = jerseyQ.iconEmoji, fontSize = 36.sp)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = jerseyQ.playerName,
                                style = MaterialTheme.typography.titleLarge,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${jerseyQ.teamOrCountry} • ${jerseyQ.yearOrContext}",
                                style = MaterialTheme.typography.bodySmall,
                                color = TrophyGold
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "ما هو رقم القميص الأيقوني الذي ارتداه في هذه الفترة؟",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White,
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // 4 Number Choices (Grid 2x2)
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            for (i in 0..1) {
                                val num = jerseyQ.options[i]
                                JerseyNumberButton(
                                    number = num,
                                    correctNumber = jerseyQ.correctJerseyNumber,
                                    isSelected = selectedJerseyOption == num,
                                    isAnswered = isJerseyAnswered,
                                    onSelect = {
                                        selectedJerseyOption = num
                                        isJerseyAnswered = true
                                        if (num == jerseyQ.correctJerseyNumber) {
                                            viewModel.audioEffects.playVictory()
                                            viewModel.repository.addPointsAndCoins(40, 15)
                                            viewModel.recordPartyGameScore("أرقام القمصان", "football", 40, "تخمين رقم ${jerseyQ.playerName} (#$num) 🔢")
                                        } else {
                                            viewModel.audioEffects.playWrong()
                                        }
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            for (i in 2..3) {
                                val num = jerseyQ.options[i]
                                JerseyNumberButton(
                                    number = num,
                                    correctNumber = jerseyQ.correctJerseyNumber,
                                    isSelected = selectedJerseyOption == num,
                                    isAnswered = isJerseyAnswered,
                                    onSelect = {
                                        selectedJerseyOption = num
                                        isJerseyAnswered = true
                                        if (num == jerseyQ.correctJerseyNumber) {
                                            viewModel.audioEffects.playVictory()
                                            viewModel.repository.addPointsAndCoins(40, 15)
                                            viewModel.recordPartyGameScore("أرقام القمصان", "football", 40, "تخمين رقم ${jerseyQ.playerName} (#$num) 🔢")
                                        } else {
                                            viewModel.audioEffects.playWrong()
                                        }
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (isJerseyAnswered) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = if (selectedJerseyOption == jerseyQ.correctJerseyNumber)
                                    EmeraldNeon.copy(alpha = 0.15f) else CardRed.copy(alpha = 0.15f)
                            ),
                            shape = RoundedCornerShape(14.dp),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (selectedJerseyOption == jerseyQ.correctJerseyNumber) EmeraldNeon else CardRed
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = if (selectedJerseyOption == jerseyQ.correctJerseyNumber)
                                        "رائع! الرقم الصحيح هو #${jerseyQ.correctJerseyNumber} 🌟"
                                    else
                                        "غير صحيح! الرقم هو #${jerseyQ.correctJerseyNumber}",
                                    fontWeight = FontWeight.Bold,
                                    color = if (selectedJerseyOption == jerseyQ.correctJerseyNumber) EmeraldNeon else CardRed
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = jerseyQ.funFact,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White,
                                    textAlign = TextAlign.Center,
                                    lineHeight = 18.sp
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                EmeraldButton(
                                    text = "اللاعب التالي ➡️",
                                    onClick = { jerseyIndex = (jerseyIndex + 1) % jerseyQuestions.size },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }

                1 -> {
                    // ---------------------------------------------------------
                    // MODE 2: TRANSFER FEES
                    // ---------------------------------------------------------
                    GlowCard(
                        modifier = Modifier.fillMaxWidth(),
                        borderColor = EmeraldNeon.copy(alpha = 0.5f)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(text = "💰", fontSize = 36.sp)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = transferQ.title,
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "اختر الصفقة ذات القيمة المالية الأعلى في التاريخ!",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // Player A Card
                    TransferOptionCard(
                        playerName = transferQ.playerA,
                        revealedFee = if (isTransferAnswered) transferQ.feeA else null,
                        isSelected = selectedTransferOption == "A",
                        isCorrect = transferQ.whichIsHigher == "A",
                        isAnswered = isTransferAnswered,
                        onSelect = {
                            if (!isTransferAnswered) {
                                selectedTransferOption = "A"
                                isTransferAnswered = true
                                if (transferQ.whichIsHigher == "A") {
                                    viewModel.audioEffects.playVictory()
                                    viewModel.repository.addPointsAndCoins(50, 20)
                                } else {
                                    viewModel.audioEffects.playWrong()
                                }
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(text = "⚡ ضد ⚡", color = TrophyGold, fontWeight = FontWeight.Bold, fontSize = 14.sp)

                    Spacer(modifier = Modifier.height(12.dp))

                    // Player B Card
                    TransferOptionCard(
                        playerName = transferQ.playerB,
                        revealedFee = if (isTransferAnswered) transferQ.feeB else null,
                        isSelected = selectedTransferOption == "B",
                        isCorrect = transferQ.whichIsHigher == "B",
                        isAnswered = isTransferAnswered,
                        onSelect = {
                            if (!isTransferAnswered) {
                                selectedTransferOption = "B"
                                isTransferAnswered = true
                                if (transferQ.whichIsHigher == "B") {
                                    viewModel.audioEffects.playVictory()
                                    viewModel.repository.addPointsAndCoins(50, 20)
                                } else {
                                    viewModel.audioEffects.playWrong()
                                }
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    if (isTransferAnswered) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = PitchCard),
                            shape = RoundedCornerShape(14.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, TrophyGold)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = transferQ.differenceText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White,
                                    textAlign = TextAlign.Center,
                                    lineHeight = 18.sp
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                EmeraldButton(
                                    text = "المقارنة التالية ➡️",
                                    onClick = { transferIndex = (transferIndex + 1) % transferQuestions.size },
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

@Composable
private fun JerseyNumberButton(
    number: Int,
    correctNumber: Int,
    isSelected: Boolean,
    isAnswered: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isCorrect = number == correctNumber
    val containerColor = when {
        !isAnswered -> PitchCard
        isSelected && isCorrect -> EmeraldNeon.copy(alpha = 0.25f)
        isSelected && !isCorrect -> CardRed.copy(alpha = 0.25f)
        !isSelected && isCorrect -> EmeraldNeon.copy(alpha = 0.15f)
        else -> PitchCard
    }
    val borderColor = when {
        !isAnswered -> PitchDivider
        isSelected && isCorrect -> EmeraldNeon
        isSelected && !isCorrect -> CardRed
        !isSelected && isCorrect -> EmeraldNeon
        else -> PitchDivider
    }

    Card(
        modifier = modifier
            .height(80.dp)
            .clickable(enabled = !isAnswered, onClick = onSelect)
            .testTag("jersey_btn_$number"),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, borderColor),
        shape = RoundedCornerShape(12.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "#$number",
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold,
                color = if (isAnswered && isCorrect) EmeraldNeon else Color.White
            )
        }
    }
}

@Composable
private fun TransferOptionCard(
    playerName: String,
    revealedFee: String?,
    isSelected: Boolean,
    isCorrect: Boolean,
    isAnswered: Boolean,
    onSelect: () -> Unit
) {
    val borderColor = when {
        !isAnswered -> PitchDivider
        isSelected && isCorrect -> EmeraldNeon
        isSelected && !isCorrect -> CardRed
        !isSelected && isCorrect -> EmeraldNeon
        else -> PitchDivider
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !isAnswered, onClick = onSelect),
        colors = CardDefaults.cardColors(containerColor = PitchCard),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, borderColor),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = playerName,
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                revealedFee?.let { fee ->
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "القيمة: $fee",
                        fontSize = 12.sp,
                        color = if (isCorrect) EmeraldNeon else TextSecondary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (isAnswered) {
                Icon(
                    imageVector = if (isCorrect) Icons.Default.CheckCircle else Icons.Default.Close,
                    contentDescription = null,
                    tint = if (isCorrect) EmeraldNeon else CardRed
                )
            }
        }
    }
}
