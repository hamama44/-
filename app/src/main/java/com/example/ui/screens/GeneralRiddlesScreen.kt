package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import com.example.ui.FootballGameViewModel
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeneralRiddlesScreen(viewModel: FootballGameViewModel) {
    val riddle by viewModel.generalRiddlePromptState.collectAsState()
    val revealedHints by viewModel.riddleRevealedHints.collectAsState()
    val userGuess by viewModel.riddleUserGuess.collectAsState()
    val isSolved by viewModel.riddleIsSolved.collectAsState()
    val feedback by viewModel.riddleFeedback.collectAsState()
    val score by viewModel.riddleScore.collectAsState()

    val focusManager = LocalFocusManager.current

    Scaffold(
        containerColor = PitchDark,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "💡", fontSize = 22.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "فوازير وألغاز الذكاء",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "مجموع النقاط: $score نقطة 🌟",
                                style = MaterialTheme.typography.labelSmall,
                                color = EmeraldNeon
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = { viewModel.navigateBack() },
                        modifier = Modifier.testTag("riddle_back_button")
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
                        onClick = { viewModel.nextRiddle() },
                        modifier = Modifier.testTag("riddle_skip_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "لغز آخر",
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
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(TrophyGold.copy(alpha = 0.15f))
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = riddle.title,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = TrophyGold
                            )
                        }

                        Text(
                            text = riddle.question,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            lineHeight = 32.sp
                        )
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = PitchCard),
                    border = BorderStroke(1.dp, PitchDivider)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(text = "🔍", fontSize = 18.sp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "التلميحات المتاحة ($revealedHints/${riddle.hints.size})",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }

                            if (revealedHints < riddle.hints.size && !isSolved) {
                                TextButton(
                                    onClick = { viewModel.revealRiddleHint() },
                                    modifier = Modifier.testTag("reveal_hint_button")
                                ) {
                                    Text(
                                        text = "+ كشف تلميح",
                                        color = StadiumCyan,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        if (revealedHints == 0) {
                            Text(
                                text = "حاول حل اللغز بدون تلميحات للحصول على أقصى عدد من النقاط (40 نقطة)!",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.5f)
                            )
                        } else {
                            for (i in 0 until revealedHints) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFF0F1E19))
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(text = "💡", fontSize = 16.sp)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = riddle.hints[i],
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }
                }
            }

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
                                onValueChange = { viewModel.setRiddleGuess(it) },
                                placeholder = {
                                    Text("اكتب إجابتك هنا أو اسم الشيء...", color = Color.White.copy(alpha = 0.4f))
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("riddle_input_field"),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = {
                                    focusManager.clearFocus()
                                    viewModel.submitRiddleGuess()
                                }),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = TrophyGold,
                                    unfocusedBorderColor = PitchDivider,
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                )
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { viewModel.giveUpAndShowRiddleAnswer() },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White.copy(alpha = 0.7f))
                                ) {
                                    Text("كشف الحل")
                                }

                                Button(
                                    onClick = {
                                        focusManager.clearFocus()
                                        viewModel.submitRiddleGuess()
                                    },
                                    enabled = userGuess.isNotBlank(),
                                    modifier = Modifier
                                        .weight(1.5f)
                                        .testTag("submit_riddle_button"),
                                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen)
                                ) {
                                    Text("تحقق من الإجابة ✨", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

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
                                    onClick = { viewModel.nextRiddle() },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("next_riddle_button"),
                                    colors = ButtonDefaults.buttonColors(containerColor = TrophyGold)
                                ) {
                                    Text("اللغز التالي ⏩", color = PitchDark, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
