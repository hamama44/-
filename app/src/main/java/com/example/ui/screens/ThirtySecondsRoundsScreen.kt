package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.NotificationsActive
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
import com.example.data.NewGamesDataProvider
import com.example.ui.FootballGameViewModel
import com.example.ui.components.EmeraldButton
import com.example.ui.components.GlowCard
import com.example.ui.components.StadiumTopBar
import com.example.ui.components.VoiceMicButton
import com.example.ui.theme.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ThirtySecondsRoundsScreen(viewModel: FootballGameViewModel) {
    val userProfile by viewModel.userProfile.collectAsState()
    val scope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current

    // 0: لعبة الجرس, 1: سين جيم, 2: مهنة لاعب, 3: وقت التعويض
    var activeRoundTab by remember { mutableIntStateOf(0) }

    // -------------------------------------------------------------------------
    // 1. BUZZER ROUND STATE
    // -------------------------------------------------------------------------
    val buzzerQuestions = remember { NewGamesDataProvider.buzzerQuestions }
    var buzzerQIndex by remember { mutableIntStateOf(0) }
    val buzzerQ = buzzerQuestions[buzzerQIndex % buzzerQuestions.size]

    var buzzerPressedBy by remember(buzzerQIndex) { mutableStateOf<TicTacToePlayer?>(null) }
    var buzzerSecondsLeft by remember(buzzerQIndex) { mutableIntStateOf(7) }
    var buzzerTimerJob by remember { mutableStateOf<Job?>(null) }
    var buzzerInputAnswer by remember { mutableStateOf("") }
    var buzzerOutcome by remember(buzzerQIndex) { mutableStateOf<String?>(null) }
    var buzzerScoreRed by remember { mutableIntStateOf(0) }
    var buzzerScoreBlue by remember { mutableIntStateOf(0) }

    fun pressBuzzer(player: TicTacToePlayer) {
        if (buzzerPressedBy != null || buzzerOutcome != null) return
        viewModel.audioEffects.playWhistle()
        buzzerPressedBy = player
        buzzerSecondsLeft = 7
        buzzerTimerJob?.cancel()
        buzzerTimerJob = scope.launch {
            while (buzzerSecondsLeft > 0 && buzzerOutcome == null) {
                delay(1000)
                buzzerSecondsLeft--
                if (buzzerSecondsLeft in 1..3) viewModel.audioEffects.playTick()
            }
            if (buzzerSecondsLeft <= 0 && buzzerOutcome == null) {
                // Timeout on buzzer answer
                viewModel.audioEffects.playWrong()
                buzzerOutcome = "انتهت الـ 7 ثوانٍ دون إجابة! الإجابة كانت: ${buzzerQ.correctAnswer} ❌"
                if (player == TicTacToePlayer.PLAYER_X) buzzerScoreBlue += buzzerQ.points
                else buzzerScoreRed += buzzerQ.points
            }
        }
    }

    fun submitBuzzerAnswer(candidate: String) {
        val trimmed = candidate.trim()
        if (trimmed.isBlank() || buzzerPressedBy == null || buzzerOutcome != null) return
        buzzerTimerJob?.cancel()
        val isCorrect = buzzerQ.acceptableKeywords.any { kw ->
            trimmed.contains(kw, ignoreCase = true) || kw.contains(trimmed, ignoreCase = true)
        }
        if (isCorrect) {
            viewModel.audioEffects.playVictory()
            buzzerOutcome = "إجابة صحيحة خارقة! (${buzzerQ.correctAnswer}) 🎉"
            if (buzzerPressedBy == TicTacToePlayer.PLAYER_X) buzzerScoreRed += buzzerQ.points
            else buzzerScoreBlue += buzzerQ.points
            viewModel.repository.addPointsAndCoins(50, 15)
        } else {
            viewModel.audioEffects.playWrong()
            buzzerOutcome = "إجابة خاطئة! الإجابة الصحيحة هي: ${buzzerQ.correctAnswer} ❌"
            if (buzzerPressedBy == TicTacToePlayer.PLAYER_X) buzzerScoreBlue += (buzzerQ.points / 2)
            else buzzerScoreRed += (buzzerQ.points / 2)
        }
        keyboardController?.hide()
    }

    // -------------------------------------------------------------------------
    // 2. SIN JEEM (Q&A) STATE
    // -------------------------------------------------------------------------
    val sinJeemQuestions = remember { NewGamesDataProvider.sinJeemQuestions }
    var sinJeemIndex by remember { mutableIntStateOf(0) }
    val sjQuestion = sinJeemQuestions[sinJeemIndex % sinJeemQuestions.size]
    var sjSelectedIndex by remember(sinJeemIndex) { mutableStateOf<Int?>(null) }
    var sjIsAnswered by remember(sinJeemIndex) { mutableStateOf(false) }

    // -------------------------------------------------------------------------
    // 3. PLAYER CAREER (JOB) STATE
    // -------------------------------------------------------------------------
    val careerJobs = remember { NewGamesDataProvider.playerCareerJobs }
    var careerIndex by remember { mutableIntStateOf(0) }
    val careerJob = careerJobs[careerIndex % careerJobs.size]
    var revealedStepsCount by remember(careerIndex) { mutableIntStateOf(1) }
    var careerGuessInput by remember { mutableStateOf("") }
    var careerIsGuessed by remember(careerIndex) { mutableStateOf(false) }
    var careerFeedback by remember { mutableStateOf<String?>(null) }

    fun checkCareerGuess(guess: String) {
        val trimmed = guess.trim()
        if (trimmed.isBlank() || careerIsGuessed) return
        val target = careerJob.playerName
        val isMatch = trimmed.contains(target, ignoreCase = true) ||
                target.contains(trimmed, ignoreCase = true) ||
                (trimmed.length >= 4 && target.split(" ").any { trimmed.contains(it, ignoreCase = true) })

        if (isMatch) {
            viewModel.audioEffects.playVictory()
            careerIsGuessed = true
            val earnedPoints = when (revealedStepsCount) {
                1 -> 150
                2 -> 100
                else -> 60
            }
            careerFeedback = "أحسنت! اللاعب هو $target (حصدت $earnedPoints نقطة) 🎉"
            viewModel.repository.addPointsAndCoins(earnedPoints, 25)
            viewModel.recordPartyGameScore("مهنة لاعب", "football", earnedPoints, "تخمين مسيرة $target 🗺️")
        } else {
            viewModel.audioEffects.playWrong()
            careerFeedback = "تخمين غير صحيح! اكشف محطة جديدة أو حاول مجدداً ❌"
        }
    }

    // -------------------------------------------------------------------------
    // 4. EXTRA TIME (INJURY TIME / التعويض) STATE
    // -------------------------------------------------------------------------
    val extraTimeQuestions = remember { NewGamesDataProvider.extraTimeQuestions }
    var extraTimeIndex by remember { mutableIntStateOf(0) }
    val etQuestion = extraTimeQuestions[extraTimeIndex % extraTimeQuestions.size]
    var etSelectedOption by remember(extraTimeIndex) { mutableStateOf<String?>(null) }
    var etIsAnswered by remember(extraTimeIndex) { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose { buzzerTimerJob?.cancel() }
    }

    Scaffold(
        containerColor = PitchDark,
        topBar = {
            StadiumTopBar(
                title = "فقرات تحدي الثلاثين (نصوح) ⏱️🔥",
                coins = userProfile.coins,
                points = userProfile.points,
                onBack = {
                    buzzerTimerJob?.cancel()
                    viewModel.navigateBack()
                }
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
            // 4 Rounds Tab Bar
            ScrollableTabRow(
                selectedTabIndex = activeRoundTab,
                containerColor = PitchCard,
                contentColor = TrophyGold,
                edgePadding = 8.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, PitchDivider, RoundedCornerShape(12.dp))
            ) {
                Tab(
                    selected = activeRoundTab == 0,
                    onClick = { activeRoundTab = 0 },
                    text = { Text("لعبة الجرس 🔔", fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                )
                Tab(
                    selected = activeRoundTab == 1,
                    onClick = { activeRoundTab = 1 },
                    text = { Text("سين جيم ❓", fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                )
                Tab(
                    selected = activeRoundTab == 2,
                    onClick = { activeRoundTab = 2 },
                    text = { Text("مهنة لاعب 🗺️", fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                )
                Tab(
                    selected = activeRoundTab == 3,
                    onClick = { activeRoundTab = 3 },
                    text = { Text("وقت التعويض ⏱️", fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            when (activeRoundTab) {
                // -------------------------------------------------------------
                // TAB 0: لعبة الجرس (BUZZER ROUND)
                // -------------------------------------------------------------
                0 -> {
                    // Scores Banner
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Card(
                            modifier = Modifier.weight(1f),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFFF1744).copy(alpha = 0.2f)),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFF1744))
                        ) {
                            Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("🔴 جرس الأحمر", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                Text("$buzzerScoreRed", fontSize = 20.sp, color = Color(0xFFFF5252), fontWeight = FontWeight.ExtraBold)
                            }
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Card(
                            modifier = Modifier.weight(1f),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF2979FF).copy(alpha = 0.2f)),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2979FF))
                        ) {
                            Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("🔵 جرس الأزرق", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                Text("$buzzerScoreBlue", fontSize = 20.sp, color = Color(0xFF448AFF), fontWeight = FontWeight.ExtraBold)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Question Card
                    GlowCard(
                        modifier = Modifier.fillMaxWidth(),
                        borderColor = TrophyGold.copy(alpha = 0.5f)
                    ) {
                        Column(modifier = Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = "سؤال جرس خاطف (+${buzzerQ.points} نقطة)", fontSize = 12.sp, color = TrophyGold, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = buzzerQ.question,
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                                textAlign = TextAlign.Center,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // THE TWO MASSIVE BUZZERS
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Red Buzzer
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .height(120.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .clickable(enabled = buzzerPressedBy == null && buzzerOutcome == null) {
                                    pressBuzzer(TicTacToePlayer.PLAYER_X)
                                }
                                .testTag("buzzer_red"),
                            colors = CardDefaults.cardColors(
                                containerColor = if (buzzerPressedBy == TicTacToePlayer.PLAYER_X) Color(0xFFFF1744) else Color(0xFFB71C1C)
                            ),
                            border = androidx.compose.foundation.BorderStroke(
                                3.dp,
                                if (buzzerPressedBy == TicTacToePlayer.PLAYER_X) Color.White else Color(0xFFFF5252)
                            )
                        ) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = Color.White, modifier = Modifier.size(36.dp))
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("جرس الأحمر! 🔴", fontWeight = FontWeight.ExtraBold, color = Color.White, fontSize = 14.sp)
                                }
                            }
                        }

                        // Blue Buzzer
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .height(120.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .clickable(enabled = buzzerPressedBy == null && buzzerOutcome == null) {
                                    pressBuzzer(TicTacToePlayer.PLAYER_O)
                                }
                                .testTag("buzzer_blue"),
                            colors = CardDefaults.cardColors(
                                containerColor = if (buzzerPressedBy == TicTacToePlayer.PLAYER_O) Color(0xFF2979FF) else Color(0xFF0D47A1)
                            ),
                            border = androidx.compose.foundation.BorderStroke(
                                3.dp,
                                if (buzzerPressedBy == TicTacToePlayer.PLAYER_O) Color.White else Color(0xFF2979FF)
                            )
                        ) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = Color.White, modifier = Modifier.size(36.dp))
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("جرس الأزرق! 🔵", fontWeight = FontWeight.ExtraBold, color = Color.White, fontSize = 14.sp)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Answering arena once buzzer pressed
                    if (buzzerPressedBy != null && buzzerOutcome == null) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = PitchCard),
                            border = androidx.compose.foundation.BorderStroke(2.dp, buzzerPressedBy?.badgeColor ?: EmeraldNeon),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "المايك مفتوح لـ: ${buzzerPressedBy?.displayName}!",
                                        color = buzzerPressedBy?.badgeColor ?: Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                    Text(
                                        text = "⏱️ $buzzerSecondsLeft ثوانٍ",
                                        color = Color(0xFFFF5252),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                OutlinedTextField(
                                    value = buzzerInputAnswer,
                                    onValueChange = { buzzerInputAnswer = it },
                                    placeholder = { Text("اكتب أو تحدث بالمايك فوراً...", fontSize = 12.sp) },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    trailingIcon = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            VoiceMicButton(
                                                viewModel = viewModel,
                                                isMini = true,
                                                onSpeechResult = { spoken ->
                                                    buzzerInputAnswer = spoken
                                                    submitBuzzerAnswer(spoken)
                                                }
                                            )
                                            IconButton(onClick = { submitBuzzerAnswer(buzzerInputAnswer) }) {
                                                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, tint = EmeraldNeon)
                                            }
                                        }
                                    },
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                    keyboardActions = KeyboardActions(onDone = { submitBuzzerAnswer(buzzerInputAnswer) })
                                )
                            }
                        }
                    }

                    buzzerOutcome?.let { outcome ->
                        Spacer(modifier = Modifier.height(10.dp))
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = if (outcome.contains("صحيحة")) EmeraldNeon.copy(alpha = 0.2f) else CardRed.copy(alpha = 0.2f)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = outcome,
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                EmeraldButton(
                                    text = "السؤال التالي في صراع الجرس 🔔",
                                    onClick = {
                                        buzzerQIndex = (buzzerQIndex + 1) % buzzerQuestions.size
                                        buzzerPressedBy = null
                                        buzzerOutcome = null
                                        buzzerInputAnswer = ""
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }

                // -------------------------------------------------------------
                // TAB 1: سين جيم (Q&A TRIVIA)
                // -------------------------------------------------------------
                1 -> {
                    GlowCard(
                        modifier = Modifier.fillMaxWidth(),
                        borderColor = StadiumCyan.copy(alpha = 0.5f)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(StadiumCyan.copy(alpha = 0.2f))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(text = sjQuestion.tournamentBadge, fontSize = 11.sp, color = StadiumCyan, fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = sjQuestion.question,
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 4 Options
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        sjQuestion.options.forEachIndexed { idx, opt ->
                            val isSelected = sjSelectedIndex == idx
                            val isCorrect = idx == sjQuestion.correctIndex

                            val bgColor = when {
                                !sjIsAnswered -> PitchCard
                                isSelected && isCorrect -> EmeraldNeon.copy(alpha = 0.25f)
                                isSelected && !isCorrect -> CardRed.copy(alpha = 0.25f)
                                !isSelected && isCorrect -> EmeraldNeon.copy(alpha = 0.15f)
                                else -> PitchCard
                            }

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = !sjIsAnswered) {
                                        sjSelectedIndex = idx
                                        sjIsAnswered = true
                                        if (isCorrect) {
                                            viewModel.audioEffects.playVictory()
                                            viewModel.repository.addPointsAndCoins(50, 15)
                                        } else {
                                            viewModel.audioEffects.playWrong()
                                        }
                                    },
                                colors = CardDefaults.cardColors(containerColor = bgColor),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (sjIsAnswered && isCorrect) EmeraldNeon else PitchDivider
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(text = "${idx + 1}.", color = TrophyGold, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(text = opt, color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }

                    if (sjIsAnswered) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = sjQuestion.explanation,
                            style = MaterialTheme.typography.bodySmall,
                            color = TrophyGold,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        EmeraldButton(
                            text = "السؤال التالي في سين جيم ➡️",
                            onClick = { sinJeemIndex = (sinJeemIndex + 1) % sinJeemQuestions.size },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // -------------------------------------------------------------
                // TAB 2: مهنة لاعب (PLAYER CAREER / JOB)
                // -------------------------------------------------------------
                2 -> {
                    GlowCard(
                        modifier = Modifier.fillMaxWidth(),
                        borderColor = EmeraldNeon.copy(alpha = 0.5f)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = "مهنة لاعب / مسيرة النجوم 🗺️",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "الجنسية: ${careerJob.nationality} • المركز: ${careerJob.position}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = EmeraldNeon
                                )
                            }

                            if (revealedStepsCount < careerJob.steps.size && !careerIsGuessed) {
                                TextButton(
                                    onClick = {
                                        viewModel.audioEffects.playClick()
                                        revealedStepsCount++
                                    }
                                ) {
                                    Text("محطة أخرى 🔍", fontSize = 11.sp, color = TrophyGold)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Steps Revealed so far
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items((0 until revealedStepsCount).toList()) { stepIdx ->
                            val step = careerJob.steps[stepIdx]
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = PitchCard),
                                border = androidx.compose.foundation.BorderStroke(1.dp, PitchDivider),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(text = "المحطة #${stepIdx + 1}", fontSize = 11.sp, color = TrophyGold, fontWeight = FontWeight.Bold)
                                        Text(text = step.years, fontSize = 11.sp, color = StadiumCyan)
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(text = step.clubOrRole, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 13.sp)
                                    Text(text = "${step.appearancesAndGoals} • ${step.achievement}", fontSize = 11.sp, color = TextSecondary)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    careerFeedback?.let { msg ->
                        Text(
                            text = msg,
                            color = if (careerIsGuessed) EmeraldNeon else Color(0xFFFF5252),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                    }

                    if (!careerIsGuessed) {
                        OutlinedTextField(
                            value = careerGuessInput,
                            onValueChange = {
                                careerGuessInput = it
                                careerFeedback = null
                            },
                            placeholder = { Text("من هو صاحب هذه المسيرة الكروية؟", fontSize = 12.sp) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            trailingIcon = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    VoiceMicButton(
                                        viewModel = viewModel,
                                        isMini = true,
                                        onSpeechResult = { spoken ->
                                            careerGuessInput = spoken
                                            checkCareerGuess(spoken)
                                        }
                                    )
                                    IconButton(onClick = { checkCareerGuess(careerGuessInput) }) {
                                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, tint = EmeraldNeon)
                                    }
                                }
                            },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { checkCareerGuess(careerGuessInput) })
                        )
                    } else {
                        EmeraldButton(
                            text = "اللاعب التالي في مهنة لاعب ➡️",
                            onClick = {
                                careerIndex = (careerIndex + 1) % careerJobs.size
                                careerGuessInput = ""
                                careerFeedback = null
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // -------------------------------------------------------------
                // TAB 3: وقت التعويض (INJURY TIME / EXTRA TIME)
                // -------------------------------------------------------------
                3 -> {
                    GlowCard(
                        modifier = Modifier.fillMaxWidth(),
                        borderColor = CardRed.copy(alpha = 0.6f)
                    ) {
                        Column(modifier = Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = "🔥 جولة التعويض الخاطفة (دبل نقاط +${etQuestion.doublePoints}) 🔥", color = Color(0xFFFF5252), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = etQuestion.question,
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                                textAlign = TextAlign.Center,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Option A Button
                    ExtraTimeButton(
                        label = etQuestion.optionA,
                        isCorrect = etQuestion.correctOption == "A",
                        isSelected = etSelectedOption == "A",
                        isAnswered = etIsAnswered,
                        onSelect = {
                            if (!etIsAnswered) {
                                etSelectedOption = "A"
                                etIsAnswered = true
                                if (etQuestion.correctOption == "A") {
                                    viewModel.audioEffects.playVictory()
                                    viewModel.repository.addPointsAndCoins(etQuestion.doublePoints, 30)
                                } else {
                                    viewModel.audioEffects.playWrong()
                                }
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Option B Button
                    ExtraTimeButton(
                        label = etQuestion.optionB,
                        isCorrect = etQuestion.correctOption == "B",
                        isSelected = etSelectedOption == "B",
                        isAnswered = etIsAnswered,
                        onSelect = {
                            if (!etIsAnswered) {
                                etSelectedOption = "B"
                                etIsAnswered = true
                                if (etQuestion.correctOption == "B") {
                                    viewModel.audioEffects.playVictory()
                                    viewModel.repository.addPointsAndCoins(etQuestion.doublePoints, 30)
                                } else {
                                    viewModel.audioEffects.playWrong()
                                }
                            }
                        }
                    )

                    if (etIsAnswered) {
                        Spacer(modifier = Modifier.height(16.dp))
                        EmeraldButton(
                            text = "السؤال التالي في وقت التعويض ⏱️",
                            onClick = { extraTimeIndex = (extraTimeIndex + 1) % extraTimeQuestions.size },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ExtraTimeButton(
    label: String,
    isCorrect: Boolean,
    isSelected: Boolean,
    isAnswered: Boolean,
    onSelect: () -> Unit
) {
    val bgColor = when {
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
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clickable(enabled = !isAnswered, onClick = onSelect),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, borderColor),
        shape = RoundedCornerShape(12.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = label,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center
            )
        }
    }
}
