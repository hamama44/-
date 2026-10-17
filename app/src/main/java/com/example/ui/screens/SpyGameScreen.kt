package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.NewGamesDataProvider
import com.example.data.SpyLocation
import com.example.ui.FootballGameViewModel
import com.example.ui.components.EmeraldButton
import com.example.ui.components.GlowCard
import com.example.ui.components.StadiumTopBar
import com.example.ui.theme.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

enum class SpyGameState {
    SETUP,           // إعداد عدد اللاعبين والجواسيس
    PASS_AND_REVEAL, // توزيع الأدوار وتمرير الهاتف سراً
    DISCUSSION,      // مرحلة طرح الأسئلة والنقاش والمؤقت
    VOTING,          // التصويت على المشتبه به
    SPY_GUESS,       // فرصة الجاسوس لتخمين المكان وسرقة الفوز
    RESULT           // النتيجة النهائية والاحتفال
}

@Composable
fun SpyGameScreen(viewModel: FootballGameViewModel) {
    val userProfile by viewModel.userProfile.collectAsState()
    val scope = rememberCoroutineScope()
    val locations = remember { NewGamesDataProvider.spyLocations }

    // Game Config
    var playerCount by remember { mutableIntStateOf(4) }
    var spyCount by remember { mutableIntStateOf(1) }
    var selectedLocation by remember { mutableStateOf(locations[0]) }

    // Runtime state
    var gameState by remember { mutableStateOf(SpyGameState.SETUP) }
    var currentPlayerIndex by remember { mutableIntStateOf(0) }
    var isHoldingToReveal by remember { mutableStateOf(false) }

    // Player assignments: list of booleans: true = spy, false = citizen
    var spyIndices by remember { mutableStateOf(setOf<Int>()) }
    var playerRoles by remember { mutableStateOf(listOf<String>()) }

    // Discussion timer
    var discussionSeconds by remember { mutableIntStateOf(180) }
    var timerJob by remember { mutableStateOf<Job?>(null) }

    // Voting and outcome
    var accusedPlayerIndex by remember { mutableStateOf<Int?>(null) }
    var spyGuessedLocationCorrect by remember { mutableStateOf<Boolean?>(null) }
    var finalResultText by remember { mutableStateOf("") }

    fun startGameSession() {
        viewModel.audioEffects.playWhistle()
        selectedLocation = locations.random()
        val randomSpies = (0 until playerCount).shuffled().take(spyCount).toSet()
        spyIndices = randomSpies

        val rolesPool = selectedLocation.roles.shuffled()
        playerRoles = (0 until playerCount).map { idx ->
            if (randomSpies.contains(idx)) "الجاسوس السري 🕵️‍♂️"
            else rolesPool[idx % rolesPool.size]
        }

        currentPlayerIndex = 0
        gameState = SpyGameState.PASS_AND_REVEAL
        isHoldingToReveal = false
    }

    fun startDiscussionTimer() {
        discussionSeconds = 180
        timerJob?.cancel()
        timerJob = scope.launch {
            while (discussionSeconds > 0 && gameState == SpyGameState.DISCUSSION) {
                delay(1000)
                discussionSeconds--
                if (discussionSeconds in 1..5) viewModel.audioEffects.playTick()
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose { timerJob?.cancel() }
    }

    Scaffold(
        containerColor = PitchDark,
        topBar = {
            StadiumTopBar(
                title = "مين الجاسوس؟ (برا السالفة) 🕵️‍♂️",
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
            when (gameState) {
                SpyGameState.SETUP -> {
                    // ---------------------------------------------------------
                    // 1. SETUP SCREEN
                    // ---------------------------------------------------------
                    GlowCard(
                        modifier = Modifier.fillMaxWidth(),
                        borderColor = TrophyGold.copy(alpha = 0.6f)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(text = "🕵️‍♂️", fontSize = 44.sp)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "لعبة الجاسوس / برا السالفة",
                                style = MaterialTheme.typography.titleLarge,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "الجميع في الجلسة سيعرفون المكان السري ودورهم فيه، ما عدا الجاسوس! هل ستكشفونه قبل أن يهرب؟",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Player Count Config
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = PitchCard),
                        shape = RoundedCornerShape(14.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, PitchDivider)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = "عدد اللاعبين في الجلسة: $playerCount 👥", color = Color.White, fontWeight = FontWeight.Bold)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = { if (playerCount > 3) playerCount-- },
                                        enabled = playerCount > 3
                                    ) {
                                        Text("-", fontSize = 24.sp, color = if (playerCount > 3) StadiumCyan else TextSecondary)
                                    }
                                    Text(text = "$playerCount", fontSize = 18.sp, color = TrophyGold, fontWeight = FontWeight.Bold)
                                    IconButton(
                                        onClick = { if (playerCount < 8) playerCount++ },
                                        enabled = playerCount < 8
                                    ) {
                                        Text("+", fontSize = 24.sp, color = if (playerCount < 8) StadiumCyan else TextSecondary)
                                    }
                                }
                            }

                            Divider(color = PitchDivider, modifier = Modifier.padding(vertical = 10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = "عدد الجواسيس: $spyCount 🕵️‍♂️", color = Color.White, fontWeight = FontWeight.Bold)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = { if (spyCount > 1) spyCount-- },
                                        enabled = spyCount > 1
                                    ) {
                                        Text("-", fontSize = 24.sp, color = if (spyCount > 1) StadiumCyan else TextSecondary)
                                    }
                                    Text(text = "$spyCount", fontSize = 18.sp, color = Color(0xFFFF5252), fontWeight = FontWeight.Bold)
                                    IconButton(
                                        onClick = { if (spyCount < 2 && spyCount < playerCount - 2) spyCount++ },
                                        enabled = spyCount < 2 && spyCount < playerCount - 2
                                    ) {
                                        Text("+", fontSize = 24.sp, color = StadiumCyan)
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    EmeraldButton(
                        text = "ابدأ الجلسة وتوزيع الأدوار 🚀",
                        onClick = { startGameSession() },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                SpyGameState.PASS_AND_REVEAL -> {
                    // ---------------------------------------------------------
                    // 2. SECRET ROLE DISTRIBUTION (HOLD TO PEEK)
                    // ---------------------------------------------------------
                    Text(
                        text = "توزيع الأدوار السري 🤫",
                        style = MaterialTheme.typography.titleMedium,
                        color = TrophyGold,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "أعطِ الهاتف إلى اللاعب رقم (${currentPlayerIndex + 1}) وتأكد ألا ينظر أحد!",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    val isCurrentSpy = spyIndices.contains(currentPlayerIndex)
                    val assignedRole = playerRoles[currentPlayerIndex]

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onPress = {
                                        viewModel.audioEffects.playClick()
                                        isHoldingToReveal = true
                                        tryAwaitRelease()
                                        isHoldingToReveal = false
                                    }
                                )
                            },
                        colors = CardDefaults.cardColors(
                            containerColor = if (isHoldingToReveal) {
                                if (isCurrentSpy) Color(0xFFB71C1C).copy(alpha = 0.3f) else PitchCard
                            } else Color(0xFF1E2630)
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            2.dp,
                            if (isHoldingToReveal) {
                                if (isCurrentSpy) Color(0xFFFF5252) else EmeraldNeon
                            } else PitchDivider
                        ),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            if (!isHoldingToReveal) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        Icons.Default.Lock,
                                        contentDescription = null,
                                        tint = TrophyGold,
                                        modifier = Modifier.size(48.dp)
                                    )
                                    Spacer(modifier = Modifier.height(14.dp))
                                    Text(
                                        text = "اللاعب #${currentPlayerIndex + 1}",
                                        fontSize = 22.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "اضغط مطولاً هنا بإصبعك لرؤية بطاقتك 👆",
                                        fontSize = 13.sp,
                                        color = StadiumCyan,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            } else {
                                // REVEAL CONTENT
                                Column(
                                    modifier = Modifier.padding(20.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    if (isCurrentSpy) {
                                        Text(text = "🕵️‍♂️", fontSize = 54.sp)
                                        Spacer(modifier = Modifier.height(10.dp))
                                        Text(
                                            text = "أنت الجاسوس السري!",
                                            fontSize = 24.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = Color(0xFFFF5252)
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = "أنت 'برا السالفة' تماماً ولا تعرف المكان السري!\nاستمع لأسئلة الآخرين بذكاء، وتظاهر بالمعرفة، وحاول معرفة المكان دون أن يشكوا فيك!",
                                            fontSize = 13.sp,
                                            color = Color.White,
                                            textAlign = TextAlign.Center,
                                            lineHeight = 20.sp
                                        )
                                    } else {
                                        Text(text = selectedLocation.icon, fontSize = 48.sp)
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = "المكان السري: ${selectedLocation.locationName}",
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = EmeraldNeon,
                                            textAlign = TextAlign.Center
                                        )
                                        Spacer(modifier = Modifier.height(10.dp))
                                        Text(
                                            text = "دورك في هذا المكان:\n$assignedRole",
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = TrophyGold,
                                            textAlign = TextAlign.Center
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = "اطرح أسئلة ذكية للآخرين لتكشف من هو الجاسوس دون أن تذكر اسم المكان صراحة!",
                                            fontSize = 12.sp,
                                            color = TextSecondary,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    EmeraldButton(
                        text = if (currentPlayerIndex + 1 < playerCount) "تم الحفظ! مرر للاعب التالي ➡️" else "الجميع حفظ أدواره! ابدأ النقاش 🎙️",
                        onClick = {
                            if (currentPlayerIndex + 1 < playerCount) {
                                currentPlayerIndex++
                            } else {
                                gameState = SpyGameState.DISCUSSION
                                startDiscussionTimer()
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                SpyGameState.DISCUSSION -> {
                    // ---------------------------------------------------------
                    // 3. DISCUSSION PHASE (TIMER & SAMPLE QUESTIONS)
                    // ---------------------------------------------------------
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "مرحلة النقاش والاستجواب 🗣️",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )

                        // Timer Badge
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (discussionSeconds <= 30) CardRed else EmeraldNeon.copy(alpha = 0.2f))
                                .border(1.dp, if (discussionSeconds <= 30) CardRed else EmeraldNeon, RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "⏱️ ${discussionSeconds / 60}:${String.format("%02d", discussionSeconds % 60)}",
                                color = if (discussionSeconds <= 30) Color.White else EmeraldNeon,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = PitchCard),
                        shape = RoundedCornerShape(14.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, StadiumCyan.copy(alpha = 0.4f))
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = "💡 نصائح للأسئلة الذكية في الجلسة:",
                                color = TrophyGold,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            selectedLocation.sampleQuestions.forEach { q ->
                                Text(
                                    text = "• $q",
                                    fontSize = 12.sp,
                                    color = Color.White,
                                    lineHeight = 18.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "كل لاعب يسأل لاعباً آخر سؤالاً واحداً عن المكان أو طبيعة عمله، ويجب الإجابة بسرعة دون تردد!",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.weight(1f))

                    EmeraldButton(
                        text = "انتهى الوقت! الانتقال للتصويت 🗳️",
                        onClick = {
                            timerJob?.cancel()
                            gameState = SpyGameState.VOTING
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                SpyGameState.VOTING -> {
                    // ---------------------------------------------------------
                    // 4. VOTING ON ACCUSED PLAYER
                    // ---------------------------------------------------------
                    Text(
                        text = "من هو الجاسوس المشتبه به؟ 🕵️‍♂️🗳️",
                        style = MaterialTheme.typography.titleMedium,
                        color = TrophyGold,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "صوتوا معاً على اللاعب الذي كانت إجاباته مريبة أو غير منطقية!",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items((0 until playerCount).toList()) { idx ->
                            val isSelected = accusedPlayerIndex == idx
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { accusedPlayerIndex = idx },
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSelected) Color(0xFFFF5252).copy(alpha = 0.25f) else PitchCard
                                ),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (isSelected) Color(0xFFFF5252) else PitchDivider
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "اللاعب #${idx + 1}",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Text(
                                        text = if (isSelected) "المتهم الأول 🎯" else "تحديد للاتهام",
                                        fontSize = 12.sp,
                                        color = if (isSelected) Color(0xFFFF5252) else TextSecondary
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    EmeraldButton(
                        text = "تأكيد الاتهام وكشف الحقيقة ⚖️",
                        onClick = {
                            val accused = accusedPlayerIndex
                            if (accused != null) {
                                val isAccusedSpy = spyIndices.contains(accused)
                                if (isAccusedSpy) {
                                    // Caught! Spy gets a chance to guess the location
                                    gameState = SpyGameState.SPY_GUESS
                                } else {
                                    // Wrongly accused innocent!
                                    viewModel.audioEffects.playWrong()
                                    finalResultText = "❌ اتهمتم بريئاً! اللاعب #${accused + 1} لم يكن الجاسوس، بل كان (${playerRoles[accused]}).\nالجاسوس الحقيقي فاز باللعبة!"
                                    gameState = SpyGameState.RESULT
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                SpyGameState.SPY_GUESS -> {
                    // ---------------------------------------------------------
                    // 5. SPY GUESS LOCATION CHANCE
                    // ---------------------------------------------------------
                    Text(
                        text = "كُشف الجاسوس! 🎯 ولكن...",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color(0xFFFF5252),
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "أمام الجاسوس فرصة أخيرة لسرقة الفوز: خمّن المكان السري الصحيح من بين هذه الأماكن!",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    val guessOptions = remember { locations.shuffled() }

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(guessOptions) { loc ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (loc.id == selectedLocation.id) {
                                            viewModel.audioEffects.playVictory()
                                            finalResultText = "🎉 الجاسوس كان ذكياً وخمّن المكان الصحيح (${loc.locationName}) وسرق الفوز منكم!"
                                            spyGuessedLocationCorrect = true
                                        } else {
                                            viewModel.audioEffects.playVictory()
                                            finalResultText = "🏆 فوز ساحق للاعبين! الجاسوس أخطأ في تخمين المكان وكان المكان الحقيقي هو (${selectedLocation.locationName})!"
                                            spyGuessedLocationCorrect = false
                                        }
                                        viewModel.repository.addPointsAndCoins(60, 20)
                                        viewModel.recordPartyGameScore("مين الجاسوس", "party", 60, "جولة جاسوس في ${selectedLocation.locationName} 🕵️‍♂️")
                                        gameState = SpyGameState.RESULT
                                    },
                                colors = CardDefaults.cardColors(containerColor = PitchCard),
                                border = androidx.compose.foundation.BorderStroke(1.dp, PitchDivider),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(text = loc.icon, fontSize = 24.sp)
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = loc.locationName,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }

                SpyGameState.RESULT -> {
                    // ---------------------------------------------------------
                    // 6. FINAL RESULTS
                    // ---------------------------------------------------------
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = PitchCard),
                        shape = RoundedCornerShape(16.dp),
                        border = androidx.compose.foundation.BorderStroke(2.dp, TrophyGold)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(text = "🏆 نهاية الجولة 🏆", fontSize = 22.sp, color = TrophyGold, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = finalResultText,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White,
                                textAlign = TextAlign.Center,
                                lineHeight = 22.sp
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = "المكان السري كان: ${selectedLocation.locationName} ${selectedLocation.icon}",
                                style = MaterialTheme.typography.titleSmall,
                                color = EmeraldNeon,
                                fontWeight = FontWeight.Bold
                            )

                            Spacer(modifier = Modifier.height(20.dp))

                            EmeraldButton(
                                text = "جولة جديدة بمكان جديد 🔄",
                                onClick = { gameState = SpyGameState.SETUP },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
    }
}
