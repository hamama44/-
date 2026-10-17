package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.MissingXIMatch
import com.example.data.NewGamesDataProvider
import com.example.ui.FootballGameViewModel
import com.example.ui.components.EmeraldButton
import com.example.ui.components.GlowCard
import com.example.ui.components.StadiumTopBar
import com.example.ui.components.VoiceMicButton
import com.example.ui.theme.*

@Composable
fun MissingXIScreen(viewModel: FootballGameViewModel) {
    val userProfile by viewModel.userProfile.collectAsState()
    val finals = remember { NewGamesDataProvider.missingXIFinals }
    var matchIndex by remember { mutableIntStateOf(0) }
    val match = finals[matchIndex % finals.size]

    var guessInputText by remember { mutableStateOf("") }
    var isPlayerGuessed by remember(matchIndex) { mutableStateOf(false) }
    var hintsRevealed by remember(matchIndex) { mutableIntStateOf(0) }
    var feedbackMessage by remember { mutableStateOf<String?>(null) }

    fun checkGuess(candidate: String) {
        val trimmed = candidate.trim()
        if (trimmed.isBlank()) return

        val target = match.missingPlayerName
        val isCorrect = trimmed.contains(target, ignoreCase = true) ||
                target.contains(trimmed, ignoreCase = true) ||
                (trimmed.length >= 4 && target.split(" ").any { part -> trimmed.contains(part, ignoreCase = true) })

        if (isCorrect) {
            viewModel.audioEffects.playVictory()
            isPlayerGuessed = true
            feedbackMessage = "إجابة عبقرية! اللاعب هو $target 🎉"
            viewModel.repository.addPointsAndCoins(75, 25)
            viewModel.recordPartyGameScore(
                "تشكيلة النهائي",
                "football",
                75,
                "اكتشاف اللاعب الناقص ($target) في ${match.finalTitle} ⚽"
            )
        } else {
            viewModel.audioEffects.playWrong()
            feedbackMessage = "تخمين خاطئ! حاول مجدداً أو استخدم التلميحات ❌"
        }
    }

    Scaffold(
        containerColor = PitchDark,
        topBar = {
            StadiumTopBar(
                title = "تشكيلة النهائي / اللاعب الناقص ⚽",
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
                .padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Match Header
            GlowCard(
                modifier = Modifier.fillMaxWidth(),
                borderColor = StadiumCyan.copy(alpha = 0.5f)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = match.finalTitle,
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${match.teamLogoEmoji} ${match.teamName} ضد ${match.opponentName} • خطة ${match.formationName}",
                            style = MaterialTheme.typography.bodySmall,
                            color = StadiumCyan
                        )
                    }

                    IconButton(
                        onClick = {
                            viewModel.audioEffects.playClick()
                            matchIndex = (matchIndex + 1) % finals.size
                        },
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(PitchDark)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "نهائي آخر", tint = TrophyGold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // -----------------------------------------------------------------
            // THE FOOTBALL PITCH WITH 11 PLAYERS
            // -----------------------------------------------------------------
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF133E26)) // pitch dark grass
                    .border(2.dp, Color(0xFF2E7D32), RoundedCornerShape(16.dp))
            ) {
                // Pitch tactical lines canvas
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    val lineColor = Color.White.copy(alpha = 0.25f)
                    val stroke = Stroke(width = 2.dp.toPx())

                    // Outer border line
                    drawRect(color = lineColor, style = stroke)

                    // Halfway line
                    drawLine(lineColor, Offset(0f, h * 0.5f), Offset(w, h * 0.5f), strokeWidth = 2.dp.toPx())

                    // Center circle
                    drawCircle(lineColor, radius = w * 0.18f, center = Offset(w * 0.5f, h * 0.5f), style = stroke)

                    // Penalty area (bottom)
                    drawRect(
                        color = lineColor,
                        topLeft = Offset(w * 0.2f, h * 0.76f),
                        size = androidx.compose.ui.geometry.Size(w * 0.6f, h * 0.24f),
                        style = stroke
                    )
                }

                // Render 11 Player Nodes
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val boxWidth = maxWidth
                    val boxHeight = maxHeight

                    match.positions.forEach { pos ->
                        val isMissing = pos.isMissing
                        val posX = boxWidth * pos.xPercent - 32.dp
                        val posY = boxHeight * pos.yPercent - 22.dp

                        Box(
                            modifier = Modifier
                                .offset(x = posX, y = posY)
                                .size(64.dp, 48.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isMissing) {
                                        if (isPlayerGuessed) EmeraldNeon.copy(alpha = 0.3f)
                                        else TrophyGold.copy(alpha = 0.3f)
                                    } else PitchDark.copy(alpha = 0.85f)
                                )
                                .border(
                                    if (isMissing) 2.dp else 1.dp,
                                    if (isMissing) {
                                        if (isPlayerGuessed) EmeraldNeon else TrophyGold
                                    } else Color.White.copy(alpha = 0.4f),
                                    RoundedCornerShape(8.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(2.dp)
                            ) {
                                if (isMissing && !isPlayerGuessed) {
                                    Text(
                                        text = "?",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = TrophyGold
                                    )
                                    Text(
                                        text = pos.positionCode,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TrophyGold
                                    )
                                } else {
                                    Text(
                                        text = pos.playerName,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        textAlign = TextAlign.Center,
                                        maxLines = 2
                                    )
                                    Text(
                                        text = pos.positionCode,
                                        fontSize = 8.sp,
                                        color = EmeraldLight
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Hints Section
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "💡 التلميحات المفتوحة ($hintsRevealed/3):", fontSize = 12.sp, color = TextSecondary)
                }

                if (hintsRevealed < 3 && !isPlayerGuessed) {
                    TextButton(
                        onClick = {
                            viewModel.audioEffects.playClick()
                            hintsRevealed++
                        }
                    ) {
                        Text("كشف تلميح (-5 عملات) 🔍", fontSize = 11.sp, color = StadiumCyan)
                    }
                }
            }

            if (hintsRevealed > 0) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = PitchCard),
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, PitchDivider)
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        if (hintsRevealed >= 1) {
                            Text(text = "• الجنسية: ${match.missingPlayerNationality}", fontSize = 12.sp, color = Color.White)
                        }
                        if (hintsRevealed >= 2) {
                            Text(text = "• رقم القميص: ${match.missingPlayerJersey}", fontSize = 12.sp, color = Color.White)
                        }
                        if (hintsRevealed >= 3) {
                            Text(text = "• تلميح ذهبي: ${match.missingPlayerClubHints.joinToString(" • ")}", fontSize = 12.sp, color = TrophyGold)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            feedbackMessage?.let { msg ->
                Text(
                    text = msg,
                    fontSize = 12.sp,
                    color = if (isPlayerGuessed) EmeraldNeon else Color(0xFFFF5252),
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
            }

            if (!isPlayerGuessed) {
                // Input Field for guessing
                OutlinedTextField(
                    value = guessInputText,
                    onValueChange = {
                        guessInputText = it
                        feedbackMessage = null
                    },
                    placeholder = { Text("اكتب أو انطق اسم اللاعب الناقص...", fontSize = 12.sp) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_missing_player"),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = TrophyGold,
                        unfocusedBorderColor = PitchDivider,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    trailingIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            VoiceMicButton(
                                viewModel = viewModel,
                                isMini = true,
                                onSpeechResult = { spoken ->
                                    guessInputText = spoken
                                    checkGuess(spoken)
                                }
                            )
                            IconButton(onClick = { checkGuess(guessInputText) }) {
                                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "إرسال", tint = TrophyGold)
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { checkGuess(guessInputText) })
                )
            } else {
                EmeraldButton(
                    text = "النهائي التاريخي التالي 🏆",
                    onClick = {
                        matchIndex = (matchIndex + 1) % finals.size
                        guessInputText = ""
                        feedbackMessage = null
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
