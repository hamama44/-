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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.BedoonKalamMode
import com.example.ui.FootballGameViewModel
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BedoonKalamScreen(viewModel: FootballGameViewModel) {
    val mode by viewModel.bedoonKalamMode.collectAsState()
    val item by viewModel.bedoonKalamCurrent.collectAsState()
    val timeLeft by viewModel.bedoonKalamTimeLeft.collectAsState()
    val isCardRevealed by viewModel.bedoonKalamIsCardRevealed.collectAsState()
    val revealedHints by viewModel.bedoonKalamRevealedHints.collectAsState()
    val score by viewModel.bedoonKalamScore.collectAsState()
    val isSolved by viewModel.bedoonKalamSolved.collectAsState()
    val feedback by viewModel.bedoonKalamFeedback.collectAsState()

    var userGuess by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current

    Scaffold(
        containerColor = PitchDark,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "🎭", fontSize = 22.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "بدون كلام",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (mode == BedoonKalamMode.EMOJI_GUESS) "تحدي الإيموجي • $score نقطة" else "تمثيل الجلسة • $score نقطة",
                                style = MaterialTheme.typography.labelSmall,
                                color = TrophyGold
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = { viewModel.navigateBack() },
                        modifier = Modifier.testTag("bedoon_kalam_back_button")
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
                        onClick = {
                            userGuess = ""
                            viewModel.nextBedoonKalam()
                        },
                        modifier = Modifier.testTag("bedoon_kalam_skip_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "تخطي",
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
            // Mode Selector Toggle
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = mode == BedoonKalamMode.EMOJI_GUESS,
                        onClick = { viewModel.startBedoonKalam(BedoonKalamMode.EMOJI_GUESS) },
                        label = { Text("📱 تحدي الإيموجي الفردي") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = TrophyGold,
                            selectedLabelColor = PitchDark,
                            containerColor = PitchCard,
                            labelColor = Color.White
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = mode == BedoonKalamMode.EMOJI_GUESS,
                            borderColor = PitchDivider,
                            selectedBorderColor = TrophyGold
                        ),
                        modifier = Modifier.weight(1f)
                    )

                    FilterChip(
                        selected = mode == BedoonKalamMode.ACTING_CHARADES,
                        onClick = { viewModel.startBedoonKalam(BedoonKalamMode.ACTING_CHARADES) },
                        label = { Text("🤫 تمثيل صامت للجلسة") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFFFF9100),
                            selectedLabelColor = Color.White,
                            containerColor = PitchCard,
                            labelColor = Color.White
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = mode == BedoonKalamMode.ACTING_CHARADES,
                            borderColor = PitchDivider,
                            selectedBorderColor = Color(0xFFFF9100)
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Category & Timer Row
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
                            text = "تصنيف: ${item.category} 🎬",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = TrophyGold
                        )
                    }

                    val timerColor = if (timeLeft <= 10) Color(0xFFFF1744) else EmeraldNeon
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(timerColor.copy(alpha = 0.15f))
                            .border(1.dp, timerColor, RoundedCornerShape(10.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "⏱️ $timeLeft ثانية",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = timerColor
                        )
                    }
                }
            }

            // Main Challenge Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = PitchCard),
                    border = BorderStroke(1.5.dp, Brush.horizontalGradient(listOf(StadiumCyan, TrophyGold)))
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        if (mode == BedoonKalamMode.EMOJI_GUESS) {
                            Text(
                                text = "خمّن الاسم من الإيموجي 👇",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White.copy(alpha = 0.7f)
                            )

                            // Giant Emojis Box
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(PitchDark)
                                    .padding(vertical = 24.dp, horizontal = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = item.emojis,
                                    fontSize = 42.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        } else {
                            // Acting Charades Mode (Card Hidden until revealed)
                            if (!isCardRevealed) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(180.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(Color(0xFF0F1E19))
                                        .border(1.dp, PitchDivider, RoundedCornerShape(16.dp))
                                        .clickable { viewModel.revealBedoonKalamActingCard() },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(text = "🤫", fontSize = 48.sp)
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = "الممثل فقط: اضغط هنا لكشف البطاقة سراً!",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = TrophyGold
                                        )
                                        Text(
                                            text = "ممنوع الكلام أو إصدار أي صوت أثناء التمثيل 🚫",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.White.copy(alpha = 0.6f)
                                        )
                                    }
                                }
                            } else {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(PitchDark)
                                        .padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text(
                                        text = "المطلوب تمثيله الآن:",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = EmeraldNeon
                                    )
                                    Text(
                                        text = item.title,
                                        style = MaterialTheme.typography.headlineSmall,
                                        fontWeight = FontWeight.Black,
                                        color = Color.White,
                                        textAlign = TextAlign.Center
                                    )
                                    Text(
                                        text = "💡 فكرة للتمثيل: ${item.actingTips}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TrophyGold,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Mode-specific interaction
            if (mode == BedoonKalamMode.EMOJI_GUESS) {
                // Progressive Hints Card
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = PitchCard),
                        border = BorderStroke(1.dp, PitchDivider)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "🔍 تلميحات ذكية ($revealedHints/${item.hints.size})",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )

                                if (revealedHints < item.hints.size && !isSolved) {
                                    TextButton(onClick = { viewModel.revealBedoonKalamHint() }) {
                                        Text("+ كشف تلميح", color = StadiumCyan, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            for (i in 0 until revealedHints) {
                                Text(
                                    text = "💡 ${item.hints[i]}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }

                // Input Box
                if (!isSolved) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = PitchCard),
                            border = BorderStroke(1.dp, PitchDivider)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                OutlinedTextField(
                                    value = userGuess,
                                    onValueChange = { userGuess = it },
                                    placeholder = {
                                        Text("اكتب اسم الفيلم أو المسرحية أو المثل...", color = Color.White.copy(alpha = 0.4f))
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("bedoon_kalam_input"),
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Done),
                                    keyboardActions = KeyboardActions(onDone = {
                                        focusManager.clearFocus()
                                        viewModel.submitBedoonKalamAnswer(userGuess)
                                    }),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = TrophyGold,
                                        unfocusedBorderColor = PitchDivider,
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White
                                    )
                                )

                                Button(
                                    onClick = {
                                        focusManager.clearFocus()
                                        viewModel.submitBedoonKalamAnswer(userGuess)
                                    },
                                    enabled = userGuess.isNotBlank(),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(50.dp)
                                        .testTag("bedoon_kalam_submit"),
                                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Text("تأكيد التخمين ✨", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            } else {
                // Acting Mode Buttons
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = { viewModel.markBedoonKalamActingSuccess() },
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp)
                                .testTag("acting_success_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text("خمنوها صح! 🎉 (+20)", fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = { viewModel.nextBedoonKalam() },
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp)
                                .testTag("acting_next_button"),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text("البطاقة التالية ⏩")
                        }
                    }
                }
            }

            // Feedback Card
            if (feedback != null) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSolved) Color(0xFF0F2B1D) else Color(0xFF2B1515)
                        ),
                        border = BorderStroke(1.dp, if (isSolved) EmeraldNeon else Color.Red)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = feedback!!,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                color = if (isSolved) EmeraldNeon else Color(0xFFFF8A80),
                                textAlign = TextAlign.Center
                            )

                            if (isSolved) {
                                Button(
                                    onClick = {
                                        userGuess = ""
                                        viewModel.nextBedoonKalam()
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = TrophyGold)
                                ) {
                                    Text("التحدي التالي ⏩", color = PitchDark, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
