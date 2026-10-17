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
import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.example.ui.FootballGameViewModel
import com.example.ui.components.VoiceMicButton
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FourCluesScreen(viewModel: FootballGameViewModel) {
    val currentItem by viewModel.fourCluesCurrent.collectAsState()
    val score by viewModel.fourCluesScore.collectAsState()
    val isSolved by viewModel.fourCluesSolved.collectAsState()
    val feedback by viewModel.fourCluesFeedback.collectAsState()
    val isAiAuditing by viewModel.aiIsAuditing.collectAsState()
    val aiFeedback by viewModel.aiAuditFeedback.collectAsState()

    var userGuess by remember { mutableStateOf("") }
    var isListening by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current

    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        isListening = false
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull() ?: ""
            if (spoken.isNotBlank()) {
                userGuess = spoken
                viewModel.auditQuestionVoiceAnswer(
                    question = "الرابط بين: " + currentItem.clues.joinToString("، "),
                    correctAnswer = currentItem.answer,
                    spokenAnswer = spoken,
                    acceptableSynonyms = emptyList()
                ) {
                    viewModel.submitFourCluesAnswer(spoken)
                }
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.onMicPermissionResult(granted)
        if (granted) {
            try {
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ar-SA")
                    putExtra(RecognizerIntent.EXTRA_PROMPT, "انطق الرابط المشترك الآن 🎙️🔍")
                }
                speechLauncher.launch(intent)
                isListening = true
            } catch (_: Exception) {}
        }
    }

    // Reset user text when new item arrives
    LaunchedEffect(currentItem.id) {
        userGuess = ""
    }

    Scaffold(
        containerColor = PitchDark,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "🔍", fontSize = 22.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "الرابط العجيب",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "أربع كلمات.. ورابط واحد • النقاط: $score 🌟",
                                style = MaterialTheme.typography.labelSmall,
                                color = TrophyGold
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = { viewModel.navigateBack() },
                        modifier = Modifier.testTag("back_from_four_clues")
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
                        onClick = { viewModel.nextFourClues() },
                        modifier = Modifier.testTag("next_four_clues_icon")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "لغز آخر",
                            tint = TrophyGold
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = PitchDark)
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Category hint badge
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(PitchCardElevated)
                            .border(1.dp, StadiumCyan.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = currentItem.icon, fontSize = 16.sp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "التصنيف: ${currentItem.categoryHint}",
                                color = StadiumCyan,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }

            // 4 Clues 2x2 Grid Cards
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    val clues = currentItem.clues
                    // Row 1
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        ClueBox(clue = clues.getOrElse(0) { "" }, number = "1", modifier = Modifier.weight(1f))
                        ClueBox(clue = clues.getOrElse(1) { "" }, number = "2", modifier = Modifier.weight(1f))
                    }
                    // Row 2
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        ClueBox(clue = clues.getOrElse(2) { "" }, number = "3", modifier = Modifier.weight(1f))
                        ClueBox(clue = clues.getOrElse(3) { "" }, number = "4", modifier = Modifier.weight(1f))
                    }
                }
            }

            // Word length guide
            item {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White.copy(alpha = 0.05f))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "الكلمة السرية مكوّنة من ${currentItem.answer.length} أحرف",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Input and Submit Section
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = PitchCard),
                    border = BorderStroke(1.dp, PitchCardBorder)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "ما الرابط الذي يجمع الكلمات الأربع؟",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = userGuess,
                            onValueChange = { userGuess = it },
                            placeholder = { Text("اكتب الرابط المشترك هنا...", color = Color.Gray) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("four_clues_input"),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true,
                            enabled = !isSolved,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = EmeraldNeon,
                                unfocusedBorderColor = PitchDivider,
                                focusedContainerColor = PitchCardElevated,
                                unfocusedContainerColor = PitchCardElevated
                            ),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    focusManager.clearFocus()
                                    viewModel.submitFourCluesAnswer(userGuess)
                                }
                            ),
                            trailingIcon = {
                                if (isAiAuditing) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        color = TrophyGold,
                                        strokeWidth = 2.dp
                                    )
                                } else if (!isSolved) {
                                    IconButton(
                                        onClick = {
                                            val hasPerm = ContextCompat.checkSelfPermission(
                                                context,
                                                Manifest.permission.RECORD_AUDIO
                                            ) == PackageManager.PERMISSION_GRANTED
                                            if (hasPerm) {
                                                try {
                                                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ar-SA")
                                                        putExtra(RecognizerIntent.EXTRA_PROMPT, "انطق الرابط المشترك الآن 🎙️🔍")
                                                    }
                                                    speechLauncher.launch(intent)
                                                    isListening = true
                                                } catch (_: Exception) {}
                                            } else {
                                                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                            }
                                        },
                                        modifier = Modifier.testTag("four_clues_mic_btn")
                                    ) {
                                        Text(text = if (isListening) "🔴" else "🎙️", fontSize = 18.sp)
                                    }
                                }
                            }
                        )

                        if (aiFeedback != null && !isSolved) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "🤖 تدقيق صوتي: $aiFeedback",
                                style = MaterialTheme.typography.labelSmall,
                                color = TrophyGold,
                                textAlign = TextAlign.Center
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        if (!isSolved) {
                            Button(
                                onClick = {
                                    focusManager.clearFocus()
                                    viewModel.submitFourCluesAnswer(userGuess)
                                },
                                enabled = userGuess.isNotBlank(),
                                colors = ButtonDefaults.buttonColors(containerColor = EmeraldNeon),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("submit_four_clues_btn")
                            ) {
                                Text(
                                    text = "تأكيد الإجابة 🚀",
                                    color = PitchDark,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                            }
                        } else {
                            Button(
                                onClick = {
                                    userGuess = ""
                                    viewModel.nextFourClues()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = TrophyGold),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("next_four_clues_btn")
                            ) {
                                Text(
                                    text = "اللغز التالي ➡️",
                                    color = PitchDark,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                            }
                        }
                    }
                }
            }

            // Feedback Banner
            if (feedback != null) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSolved) EmeraldNeon.copy(alpha = 0.2f) else Color(0xFFFF5252).copy(alpha = 0.2f)
                        ),
                        border = BorderStroke(1.dp, if (isSolved) EmeraldNeon else Color(0xFFFF5252))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (isSolved) "🎉" else "❌",
                                fontSize = 20.sp
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = feedback ?: "",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun ClueBox(
    clue: String,
    number: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.height(95.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = PitchCardElevated),
        border = BorderStroke(
            1.5.dp,
            Brush.verticalGradient(listOf(PitchCardBorder, StadiumCyan.copy(alpha = 0.5f)))
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.1f))
                    .align(Alignment.TopStart),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = number,
                    color = TrophyGold,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                text = clue,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 18.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}
