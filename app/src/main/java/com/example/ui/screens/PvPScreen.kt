package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.PvPMatchState
import com.example.ui.FootballGameViewModel
import com.example.ui.components.EmeraldButton
import com.example.ui.components.GlowCard
import com.example.ui.components.TrophyButton
import com.example.ui.theme.*

@Composable
fun PvPScreen(viewModel: FootballGameViewModel) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val pvpState by viewModel.pvpState.collectAsState()

    Scaffold(
        containerColor = PitchDark,
        topBar = {
            PvPTopBar(
                isSearching = pvpState.isSearching,
                currentRound = pvpState.currentRound,
                totalRounds = pvpState.totalRounds,
                isFriendlyMatch = pvpState.isFriendlyMatch,
                friendlyCode = pvpState.friendlyCode,
                onBack = { viewModel.navigateBack() }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (pvpState.isSearching) {
                PvPSearchingView(
                    commentary = pvpState.commentaryMessage,
                    user = pvpState.user
                )
            } else {
                PvPMatchView(
                    state = pvpState,
                    onOptionSelect = { viewModel.answerPvPQuestion(it) },
                    onRematch = { viewModel.rematchPvP() },
                    onShareResult = {
                        viewModel.shareMatchResult(
                            context = context,
                            oppName = pvpState.opponent.name,
                            userScore = pvpState.user.score,
                            oppScore = pvpState.opponent.score,
                            isWin = pvpState.user.score > pvpState.opponent.score
                        )
                    },
                    onExit = { viewModel.navigateBack() }
                )
            }
        }
    }
}

@Composable
fun PvPTopBar(
    isSearching: Boolean,
    currentRound: Int,
    totalRounds: Int,
    isFriendlyMatch: Boolean = false,
    friendlyCode: String? = null,
    onBack: () -> Unit
) {
    Surface(
        color = PitchDark,
        tonalElevation = 6.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(PitchCard)
                    .testTag("pvp_back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "رجوع",
                    tint = Color.White
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = if (isFriendlyMatch) "🤝" else "⚔️", fontSize = 18.sp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isFriendlyMatch && friendlyCode != null) "مواجهة ودية [$friendlyCode]" else "المواجهة المباشرة (PvP)",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (isFriendlyMatch) GoldAccent else Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
                if (!isSearching) {
                    Text(
                        text = if (isFriendlyMatch) "كلاسيكو الأصدقاء • الجولة $currentRound من $totalRounds" else "الجولة $currentRound من $totalRounds",
                        style = MaterialTheme.typography.labelSmall,
                        color = TrophyGold
                    )
                }
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(EmeraldNeon.copy(alpha = 0.2f))
                    .border(1.dp, EmeraldNeon, RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "مباشر 🔴",
                    color = EmeraldNeon,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun PvPSearchingView(
    commentary: String,
    user: com.example.data.PvPPlayer
) {
    val infiniteTransition = rememberInfiniteTransition(label = "radar")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = EaseInOutQuad),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(contentAlignment = Alignment.Center) {
            // Pulse Ring
            Box(
                modifier = Modifier
                    .size(160.dp)
                    .scale(pulseScale)
                    .clip(CircleShape)
                    .background(EmeraldNeon.copy(alpha = 0.15f))
                    .border(2.dp, EmeraldNeon.copy(alpha = 0.4f), CircleShape)
            )

            // Radar Circle
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .background(PitchCardElevated)
                    .border(3.dp, TrophyGold, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(text = user.avatarEmoji, fontSize = 52.sp)
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        Text(
            text = user.name,
            style = MaterialTheme.typography.titleLarge,
            color = Color.White,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = "فريقك: ${user.club}",
            style = MaterialTheme.typography.bodyMedium,
            color = TrophyGold
        )

        Spacer(modifier = Modifier.height(24.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(PitchCard)
                .border(1.dp, PitchCardBorder, RoundedCornerShape(16.dp))
                .padding(16.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(
                    color = EmeraldNeon,
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = commentary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimary,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun PvPMatchView(
    state: PvPMatchState,
    onOptionSelect: (Int) -> Unit,
    onRematch: () -> Unit,
    onShareResult: () -> Unit,
    onExit: () -> Unit
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Live Stadium Scoreboard
        LiveMatchScoreboard(
            user = state.user,
            opponent = state.opponent,
            roundTimer = state.roundTimer,
            totalRounds = state.totalRounds,
            currentRound = state.currentRound
        )

        // 2. Score Momentum Comparison Bar
        ScoreMomentumBar(
            userScore = state.user.score,
            opponentScore = state.opponent.score
        )

        // 3. Live Stadium Commentary Banner
        StadiumCommentaryCard(message = state.commentaryMessage)

        // 4. Current Question & Options
        if (state.currentQuestion != null && !state.isMatchFinished) {
            val q = state.currentQuestion
            GlowCard(
                modifier = Modifier.fillMaxWidth(),
                borderColor = EmeraldNeon.copy(alpha = 0.5f)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "سؤال الجولة ${state.currentRound}:",
                            style = MaterialTheme.typography.labelMedium,
                            color = TrophyGold,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "⏱️ ${state.roundTimer} ث",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (state.roundTimer <= 4) CardRed else EmeraldNeon,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = q.question,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 24.sp
                    )
                }
            }

            // 4 Options
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                q.options.forEachIndexed { index, optionText ->
                    val isUserSelected = state.userSelectedOption == index
                    val isOpponentSelected = state.opponentSelectedOption == index
                    val isCorrect = index == q.correctIndex

                    val hasUserAnswered = state.userSelectedOption != null

                    val backgroundColor = when {
                        hasUserAnswered && isCorrect -> Color(0xFF1B5E20)
                        hasUserAnswered && isUserSelected && !isCorrect -> Color(0xFFB71C1C)
                        else -> PitchCard
                    }

                    val borderColor = when {
                        hasUserAnswered && isCorrect -> EmeraldNeon
                        hasUserAnswered && isUserSelected && !isCorrect -> CardRed
                        else -> PitchCardBorder
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(backgroundColor)
                            .border(1.5.dp, borderColor, RoundedCornerShape(16.dp))
                            .clickable(enabled = state.userSelectedOption == null) {
                                onOptionSelect(index)
                            }
                            .padding(14.dp)
                            .testTag("pvp_option_$index")
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = optionText,
                                style = MaterialTheme.typography.bodyLarge,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )

                            // Status tags for user & opponent
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (isOpponentSelected) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color(0xFF0D47A1))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "${state.opponent.avatarEmoji} المنافس",
                                            fontSize = 10.sp,
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                if (isUserSelected) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (isCorrect) EmeraldNeon else CardRed)
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = if (isCorrect) "✓ هدفك" else "✗ خطأ",
                                            fontSize = 10.sp,
                                            color = PitchBlack,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 5. Match Finished Card
        if (state.isMatchFinished) {
            PvPMatchFinishedCard(
                user = state.user,
                opponent = state.opponent,
                isFriendlyMatch = state.isFriendlyMatch,
                onRematch = onRematch,
                onShareResult = onShareResult,
                onExit = onExit
            )
        }
    }
}

@Composable
fun LiveMatchScoreboard(
    user: com.example.data.PvPPlayer,
    opponent: com.example.data.PvPPlayer,
    roundTimer: Int,
    totalRounds: Int,
    currentRound: Int
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.verticalGradient(
                    listOf(PitchCardElevated, PitchCard, PitchBlack)
                )
            )
            .border(1.5.dp, TrophyGold.copy(alpha = 0.7f), RoundedCornerShape(20.dp))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Player 1 (User)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .clip(CircleShape)
                        .background(PitchDark)
                        .border(2.dp, TrophyGold, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = user.avatarEmoji, fontSize = 26.sp)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = user.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                Text(
                    text = user.club,
                    style = MaterialTheme.typography.bodySmall,
                    color = EmeraldNeon,
                    fontSize = 10.sp,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${user.score}",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Black,
                    color = TrophyGold
                )
            }

            // Center Match Clock & VS
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = 10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(PitchBlack)
                        .border(1.dp, PitchCardBorder, RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "⏱️ $roundTimer ث",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (roundTimer <= 4) CardRed else EmeraldNeon
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "VS",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White
                )

                Text(
                    text = "الجولة $currentRound/$totalRounds",
                    fontSize = 10.sp,
                    color = TextSecondary
                )
            }

            // Player 2 (Opponent)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .clip(CircleShape)
                        .background(PitchDark)
                        .border(2.dp, StadiumCyan, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = opponent.avatarEmoji, fontSize = 26.sp)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = opponent.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                Text(
                    text = opponent.club,
                    style = MaterialTheme.typography.bodySmall,
                    color = StadiumCyan,
                    fontSize = 10.sp,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${opponent.score}",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Black,
                    color = StadiumCyan
                )
            }
        }
    }
}

@Composable
fun ScoreMomentumBar(
    userScore: Int,
    opponentScore: Int
) {
    val total = (userScore + opponentScore).coerceAtLeast(1)
    val userRatio = (userScore.toFloat() / total).coerceIn(0.15f, 0.85f)

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "استحواذ النقاط: ${(userRatio * 100).toInt()}%",
                style = MaterialTheme.typography.labelSmall,
                color = TrophyGold
            )
            Text(
                text = "${((1f - userRatio) * 100).toInt()}% للمنافس",
                style = MaterialTheme.typography.labelSmall,
                color = StadiumCyan
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(CircleShape)
                .background(PitchBlack)
        ) {
            Row(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .weight(userRatio)
                        .fillMaxHeight()
                        .background(TrophyGold)
                )
                Box(
                    modifier = Modifier
                        .weight(1f - userRatio)
                        .fillMaxHeight()
                        .background(StadiumCyan)
                )
            }
        }
    }
}

@Composable
fun StadiumCommentaryCard(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF0F1E14))
            .border(1.dp, EmeraldNeon.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = "🎙️", fontSize = 18.sp)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun PvPMatchFinishedCard(
    user: com.example.data.PvPPlayer,
    opponent: com.example.data.PvPPlayer,
    isFriendlyMatch: Boolean = false,
    onRematch: () -> Unit,
    onShareResult: () -> Unit,
    onExit: () -> Unit
) {
    val won = user.score > opponent.score
    val draw = user.score == opponent.score

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.verticalGradient(
                    listOf(PitchCardElevated, PitchCard, PitchBlack)
                )
            )
            .border(2.dp, if (won) TrophyGold else if (draw) EmeraldNeon else CardRed, RoundedCornerShape(24.dp))
            .padding(20.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = if (won) {
                    if (isFriendlyMatch) "🏆 فوز ساحق على صديقك!" else "🏆 فوز ساحق في المواجهة!"
                } else if (draw) {
                    "🤝 تعادل ملحمي!"
                } else {
                    if (isFriendlyMatch) "💔 تفوق صديقك هذه المرة!" else "💔 خسارة بشرف!"
                },
                style = MaterialTheme.typography.headlineSmall,
                color = if (won) TrophyGold else if (draw) EmeraldNeon else CardRed,
                fontWeight = FontWeight.Black
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "النتيجة النهائية: ${user.score} - ${opponent.score}",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = if (won) "+120 نقطة خبرة ⭐ • +60 قطعة ذهبية 🪙" else if (draw) "+50 نقطة خبرة ⭐ • +25 قطعة ذهبية 🪙" else "+25 نقطة خبرة ⭐ • +10 قطع ذهبية 🪙",
                style = MaterialTheme.typography.bodyMedium,
                color = TrophyGold,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(20.dp))

            if (isFriendlyMatch) {
                OutlinedButton(
                    onClick = onShareResult,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = GoldAccent),
                    border = ButtonDefaults.outlinedButtonBorder.copy(
                        brush = Brush.horizontalGradient(listOf(GoldAccent.copy(alpha = 0.5f), GoldAccent.copy(alpha = 0.5f)))
                    )
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, tint = GoldAccent, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "مشاركة نتيجة التحدي مع صديقك 📲",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))
            }

            EmeraldButton(
                text = "مواجهة جديدة فوراً (Rematch) ⚔️",
                leadingEmoji = "🔄",
                onClick = onRematch,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(10.dp))

            TrophyButton(
                text = "العودة إلى الملعب الرئيسي 🏟️",
                onClick = onExit,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
