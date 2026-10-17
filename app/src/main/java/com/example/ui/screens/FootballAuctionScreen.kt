package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.FootballAuctionRound
import com.example.data.NewGamesDataProvider
import com.example.ui.FootballGameViewModel
import com.example.ui.components.EmeraldButton
import com.example.ui.components.GlowCard
import com.example.ui.components.StadiumTopBar
import com.example.ui.components.TrophyButton
import com.example.ui.components.VoiceMicButton
import com.example.ui.theme.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class AuctionPhase {
    BIDDING,     // مرحلة المزايدة بين اللاعبين
    EXECUTING,   // مرحلة سرد وتسمية الأسماء بالمايك/الكتابة
    ROUND_OVER   // انتهاء الجولة وإعلان النتيجة
}

@Composable
fun FootballAuctionScreen(viewModel: FootballGameViewModel) {
    val userProfile by viewModel.userProfile.collectAsState()
    val scope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current

    val rounds = remember { NewGamesDataProvider.auctionRounds }
    var currentRoundIndex by remember { mutableIntStateOf(0) }
    val round = rounds[currentRoundIndex % rounds.size]

    // Scores between Player 1 (Red) and Player 2 (Blue)
    var scoreRed by remember { mutableIntStateOf(0) }
    var scoreBlue by remember { mutableIntStateOf(0) }

    // Auction State
    var phase by remember(currentRoundIndex) { mutableStateOf(AuctionPhase.BIDDING) }
    var currentBid by remember(currentRoundIndex) { mutableIntStateOf(round.minimumBid) }
    var currentBidder by remember(currentRoundIndex) { mutableStateOf(TicTacToePlayer.PLAYER_X) }
    var winnerBidder by remember(currentRoundIndex) { mutableStateOf<TicTacToePlayer?>(null) }

    // Execution state
    var timeLeft by remember(currentRoundIndex) { mutableIntStateOf(round.targetDurationSeconds) }
    var timerJob by remember { mutableStateOf<Job?>(null) }
    val recognizedAnswers = remember(currentRoundIndex) { mutableStateListOf<String>() }
    var inputAnswerText by remember { mutableStateOf("") }
    var executionSuccess by remember(currentRoundIndex) { mutableStateOf<Boolean?>(null) }
    var feedbackMessage by remember { mutableStateOf<String?>(null) }

    fun finishRound(success: Boolean) {
        timerJob?.cancel()
        phase = AuctionPhase.ROUND_OVER
        executionSuccess = success
        if (success) {
            viewModel.audioEffects.playVictory()
            if (winnerBidder == TicTacToePlayer.PLAYER_X) scoreRed++ else scoreBlue++
            viewModel.repository.addPointsAndCoins(75, 25)
            viewModel.recordPartyGameScore(
                "المزاد الكروي",
                "football",
                75,
                "إتمام مزاد (${round.categoryTitle}) بنجاح 🔨🎯"
            )
        } else {
            viewModel.audioEffects.playWrong()
            // Opponent gets the point
            if (winnerBidder == TicTacToePlayer.PLAYER_X) scoreBlue++ else scoreRed++
        }
    }

    fun startExecutionPhase(winner: TicTacToePlayer) {
        winnerBidder = winner
        phase = AuctionPhase.EXECUTING
        timeLeft = round.targetDurationSeconds
        viewModel.audioEffects.playWhistle()

        timerJob?.cancel()
        timerJob = scope.launch {
            while (timeLeft > 0 && phase == AuctionPhase.EXECUTING) {
                delay(1000)
                timeLeft--
                if (timeLeft in 1..5) {
                    viewModel.audioEffects.playTick()
                }
            }
            if (timeLeft <= 0 && phase == AuctionPhase.EXECUTING) {
                finishRound(success = recognizedAnswers.size >= currentBid)
            }
        }
    }

    fun checkAndAddAnswer(candidate: String) {
        val trimmed = candidate.trim()
        if (trimmed.isBlank()) return

        val isAlreadyAdded = recognizedAnswers.any { it.equals(trimmed, ignoreCase = true) }
        if (isAlreadyAdded) {
            feedbackMessage = "الاسم مكرر وتم احتسابه مسبقاً! ⚠️"
            return
        }

        val match = round.validAnswers.find { validName ->
            trimmed.contains(validName, ignoreCase = true) || validName.contains(trimmed, ignoreCase = true)
        }

        if (match != null) {
            viewModel.audioEffects.playCorrect()
            recognizedAnswers.add(match)
            inputAnswerText = ""
            feedbackMessage = "إجابة صحيحة: $match ✓"
            keyboardController?.hide()

            // Check if goal reached
            if (recognizedAnswers.size >= currentBid) {
                finishRound(success = true)
            }
        } else {
            viewModel.audioEffects.playWrong()
            feedbackMessage = "اسم غير صحيح لهذه الفئة! حاول مجدداً ❌"
        }
    }

    DisposableEffect(Unit) {
        onDispose { timerJob?.cancel() }
    }

    Scaffold(
        containerColor = PitchDark,
        topBar = {
            StadiumTopBar(
                title = "المزاد الكروي 🔨⚽",
                coins = userProfile.coins,
                points = userProfile.points,
                onBack = {
                    timerJob?.cancel()
                    viewModel.navigateBack()
                }
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
            // Team Scores Banner
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Red Team Score
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFF1744).copy(alpha = 0.2f)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFF1744)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(text = "🔴 الفريق الأحمر", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Bold)
                        Text(text = "$scoreRed", fontSize = 24.sp, color = Color(0xFFFF5252), fontWeight = FontWeight.ExtraBold)
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Blue Team Score
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF2979FF).copy(alpha = 0.2f)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2979FF)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(text = "🔵 الفريق الأزرق", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Bold)
                        Text(text = "$scoreBlue", fontSize = 24.sp, color = Color(0xFF448AFF), fontWeight = FontWeight.ExtraBold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Category Card
            GlowCard(
                modifier = Modifier.fillMaxWidth(),
                borderColor = TrophyGold.copy(alpha = 0.6f)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(TrophyGold.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = round.icon, fontSize = 28.sp)
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "فئة المزاد الكروي",
                            style = MaterialTheme.typography.bodySmall,
                            color = TrophyGold,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = round.categoryTitle,
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = round.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // -----------------------------------------------------------------
            // CONDITIONAL PHASES
            // -----------------------------------------------------------------
            when (phase) {
                AuctionPhase.BIDDING -> {
                    // BIDDING ARENA
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = PitchCard),
                        border = androidx.compose.foundation.BorderStroke(2.dp, currentBidder.badgeColor),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "مطرقة المزاد 🔨",
                                style = MaterialTheme.typography.titleMedium,
                                color = TrophyGold,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "دور المزايدة الآن عند: ${currentBidder.displayName}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            // Current Bid Display
                            Box(
                                modifier = Modifier
                                    .size(100.dp)
                                    .clip(CircleShape)
                                    .background(PitchDark)
                                    .border(3.dp, currentBidder.badgeColor, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "$currentBid",
                                        fontSize = 38.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = currentBidder.badgeColor
                                    )
                                    Text(text = "أسماء", fontSize = 11.sp, color = TextSecondary)
                                }
                            }

                            Spacer(modifier = Modifier.height(18.dp))

                            // Bidding Action Buttons
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Button(
                                    onClick = {
                                        viewModel.audioEffects.playTick()
                                        currentBid += 1
                                        currentBidder = if (currentBidder == TicTacToePlayer.PLAYER_X) TicTacToePlayer.PLAYER_O else TicTacToePlayer.PLAYER_X
                                    },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = currentBidder.badgeColor),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("أقدر أقول ${currentBid + 1} ✋", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }

                                Button(
                                    onClick = {
                                        viewModel.audioEffects.playTick()
                                        currentBid += 2
                                        currentBidder = if (currentBidder == TicTacToePlayer.PLAYER_X) TicTacToePlayer.PLAYER_O else TicTacToePlayer.PLAYER_X
                                    },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = TrophyGold),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("أرفع لـ ${currentBid + 2} 🔥", color = PitchDark, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Pass / Concede Button
                            OutlinedButton(
                                onClick = {
                                    viewModel.audioEffects.playWhistle()
                                    // The opponent wins the auction at currentBid
                                    val winner = if (currentBidder == TicTacToePlayer.PLAYER_X) TicTacToePlayer.PLAYER_O else TicTacToePlayer.PLAYER_X
                                    startExecutionPhase(winner)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF5252)),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFF5252)),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("انسحاب / ارسى المزاد على الخصم 🏳️", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                AuctionPhase.EXECUTING -> {
                    // EXECUTION ARENA
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = PitchCard),
                        border = androidx.compose.foundation.BorderStroke(2.dp, winnerBidder?.badgeColor ?: EmeraldNeon),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "المنفّذ: ${winnerBidder?.displayName}",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = winnerBidder?.badgeColor ?: Color.White,
                                    fontWeight = FontWeight.Bold
                                )

                                // Countdown Timer
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (timeLeft <= 5) CardRed else EmeraldNeon.copy(alpha = 0.2f))
                                        .border(1.dp, if (timeLeft <= 5) CardRed else EmeraldNeon, RoundedCornerShape(8.dp))
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "⏱️ $timeLeft ث",
                                        fontWeight = FontWeight.Bold,
                                        color = if (timeLeft <= 5) Color.White else EmeraldNeon
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Target Progress
                            Text(
                                text = "المطلوب: سرد $currentBid أسماء صحيحة",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            LinearProgressIndicator(
                                progress = { (recognizedAnswers.size.toFloat() / currentBid).coerceIn(0f, 1f) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                color = EmeraldNeon,
                                trackColor = PitchDark
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "تم ذكر: ${recognizedAnswers.size} من $currentBid",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            // Recognized names so far
                            if (recognizedAnswers.isNotEmpty()) {
                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 100.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    items(recognizedAnswers) { ans ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(EmeraldNeon.copy(alpha = 0.15f))
                                                .padding(horizontal = 8.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Default.Check, contentDescription = null, tint = EmeraldNeon, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(text = ans, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                            }

                            feedbackMessage?.let { msg ->
                                Text(
                                    text = msg,
                                    color = if (msg.contains("✓")) EmeraldNeon else Color(0xFFFF5252),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                            }

                            // Speech-to-Text Mic or text input
                            OutlinedTextField(
                                value = inputAnswerText,
                                onValueChange = {
                                    inputAnswerText = it
                                    feedbackMessage = null
                                },
                                placeholder = { Text("اكتب أو انطق الاسم بالمايك...", fontSize = 12.sp) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("input_auction_answer"),
                                singleLine = true,
                                trailingIcon = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        VoiceMicButton(
                                            viewModel = viewModel,
                                            isMini = true,
                                            onSpeechResult = { spoken ->
                                                inputAnswerText = spoken
                                                checkAndAddAnswer(spoken)
                                            }
                                        )
                                        IconButton(onClick = { checkAndAddAnswer(inputAnswerText) }) {
                                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "إرسال", tint = EmeraldNeon)
                                        }
                                    }
                                },
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = { checkAndAddAnswer(inputAnswerText) }),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = EmeraldNeon,
                                    unfocusedBorderColor = PitchDivider,
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                )
                            )
                        }
                    }
                }

                AuctionPhase.ROUND_OVER -> {
                    // ROUND RESULT CARD
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = PitchCard),
                        border = androidx.compose.foundation.BorderStroke(
                            2.dp,
                            if (executionSuccess == true) EmeraldNeon else Color(0xFFFF5252)
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = if (executionSuccess == true) "🎉 نجح المزاد واكتمل التحدي!" else "⏰ انتهى الوقت ولم يكتمل المزاد!",
                                style = MaterialTheme.typography.titleMedium,
                                color = if (executionSuccess == true) EmeraldNeon else Color(0xFFFF5252),
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = if (executionSuccess == true)
                                    "أثبت ${winnerBidder?.displayName} جدارته ونجح في ذكر $currentBid أسماء كاملة!"
                                else
                                    "فشل المنفّذ وتنتقل النقطة للخصم المنافس!",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            EmeraldButton(
                                text = "الجولة التالية من المزاد 🔄",
                                onClick = {
                                    currentRoundIndex = (currentRoundIndex + 1) % rounds.size
                                    phase = AuctionPhase.BIDDING
                                    recognizedAnswers.clear()
                                    executionSuccess = null
                                    feedbackMessage = null
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
    }
}
