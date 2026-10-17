package com.example.ui.components

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.ui.theme.*
import java.util.Locale

@Composable
fun VoiceMicButton(
    isListening: Boolean,
    onStartListening: () -> Unit,
    modifier: Modifier = Modifier,
    size: Int = 54,
    label: String = "تحدث في المايك 🎙️"
) {
    val context = LocalContext.current
    var hasRecordPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasRecordPermission = granted
        if (granted) {
            onStartListening()
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "mic_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isListening) 1.25f else 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Box(
        modifier = modifier
            .size((size * 1.3).dp),
        contentAlignment = Alignment.Center
    ) {
        if (isListening) {
            Box(
                modifier = Modifier
                    .size((size * 1.3).dp)
                    .scale(pulseScale)
                    .clip(CircleShape)
                    .background(Color(0xFFFF1744).copy(alpha = 0.25f))
            )
        }

        IconButton(
            onClick = {
                if (hasRecordPermission) {
                    onStartListening()
                } else {
                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            },
            modifier = Modifier
                .size(size.dp)
                .clip(CircleShape)
                .background(
                    if (isListening) Brush.radialGradient(listOf(Color(0xFFFF1744), Color(0xFFD50000)))
                    else Brush.radialGradient(listOf(TrophyGold, Color(0xFFFF9100)))
                )
                .border(2.dp, if (isListening) Color.White else PitchDark, CircleShape)
                .testTag("voice_mic_button")
        ) {
            Icon(
                imageVector = if (isListening) Icons.Default.Stop else Icons.Default.Mic,
                contentDescription = label,
                tint = if (isListening) Color.White else PitchDark,
                modifier = Modifier.size((size * 0.55).dp)
            )
        }
    }
}

/**
 * Full interactive AI Voice Panel with speech recognizer and direct text fallback.
 */
@Composable
fun AiVoiceInputBar(
    promptTitle: String,
    hintText: String,
    isAuditing: Boolean,
    onSpeechValidated: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var spokenText by remember { mutableStateOf("") }
    var isListening by remember { mutableStateOf(false) }

    val speechRecognizerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        isListening = false
        if (result.resultCode == Activity.RESULT_OK) {
            val spokenMatches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val recognized = spokenMatches?.firstOrNull() ?: ""
            if (recognized.isNotBlank()) {
                spokenText = recognized
                onSpeechValidated(recognized)
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            launchSpeechIntent(speechRecognizerLauncher)
            isListening = true
        }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = PitchCardElevated),
        border = BorderStroke(1.5.dp, if (isListening) Color(0xFFFF1744) else StadiumCyan.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "🎙️", fontSize = 18.sp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = promptTitle,
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(EmeraldNeon.copy(alpha = 0.15f))
                        .border(1.dp, EmeraldNeon.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "مُحكّم الذكاء الاصطناعي 🤖",
                        style = MaterialTheme.typography.labelSmall,
                        color = EmeraldNeon,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp
                    )
                }
            }

            // Input / Recognized speech field
            OutlinedTextField(
                value = spokenText,
                onValueChange = { spokenText = it },
                placeholder = {
                    Text(
                        text = if (isListening) "🎙️ جارٍ الاستماع لصوتك... تكلم الآن!" else hintText,
                        color = if (isListening) Color(0xFFFF5252) else TextMuted,
                        fontSize = 13.sp
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("voice_input_field"),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = EmeraldNeon,
                    unfocusedBorderColor = PitchDivider,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedContainerColor = PitchDark,
                    unfocusedContainerColor = PitchDark
                ),
                trailingIcon = {
                    if (isAuditing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = TrophyGold,
                            strokeWidth = 2.dp
                        )
                    } else if (spokenText.isNotBlank()) {
                        IconButton(
                            onClick = {
                                onSpeechValidated(spokenText)
                            },
                            modifier = Modifier.testTag("send_voice_text_btn")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "تدقيق", tint = EmeraldNeon)
                        }
                    }
                }
            )

            // Bottom Actions: Big Mic + Direct verify
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isListening) "🔴 الاستماع نشط... اضغط للإنهاء" else "اضغط المايك وتحدث بوضوح",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isListening) Color(0xFFFF5252) else TextSecondary,
                    fontSize = 11.sp
                )

                VoiceMicButton(
                    isListening = isListening,
                    size = 46,
                    onStartListening = {
                        val hasPerm = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED
                        if (hasPerm) {
                            launchSpeechIntent(speechRecognizerLauncher)
                            isListening = true
                        } else {
                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    }
                )
            }
        }
    }
}

private fun launchSpeechIntent(
    launcher: androidx.activity.result.ActivityResultLauncher<Intent>
) {
    try {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ar-SA")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "ar-SA")
            putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, "ar")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "تحدث الآن بوضوح في ملعب التحدي 🎙️⚽")
        }
        launcher.launch(intent)
    } catch (_: Exception) {}
}

/**
 * Animated dynamic audio waveform bars that bounce with live sound amplitude.
 */
@Composable
fun AudioWaveformVisualizer(
    soundLevel: Float,
    isListening: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val heights = listOf(0.4f, 0.8f, 1.0f, 0.7f, 0.3f)
        heights.forEachIndexed { index, baseFactor ->
            val animatedHeight by animateFloatAsState(
                targetValue = if (isListening) (8f + soundLevel * 24f * baseFactor).coerceIn(6f, 30f) else 6f,
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                label = "bar_$index"
            )
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(animatedHeight.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        if (isListening) Brush.verticalGradient(listOf(EmeraldNeon, StadiumCyan))
                        else Brush.verticalGradient(listOf(TextMuted.copy(alpha = 0.5f), PitchDivider))
                    )
            )
        }
    }
}

/**
 * Permission dialog with football stadium theme explaining why voice permission is required.
 */
@Composable
fun VoicePermissionDialog(
    onDismiss: () -> Unit,
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit,
    isPermanentlyDenied: Boolean = false
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = PitchCardElevated,
        shape = RoundedCornerShape(20.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "🎙️", fontSize = 24.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "صلاحية الميكروفون للألعاب الصوتية",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "لتفعيل الألعاب التفاعلية مثل: 'التابو العربي'، وتحدي الـ 5 ثواني، وتدقيق الإجابات مع الذكاء الاصطناعي، نحتاج إذن الوصول للميكروفون للتعرف على صوتك.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    lineHeight = 20.sp
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(PitchDark)
                        .border(1.dp, PitchDivider, RoundedCornerShape(12.dp))
                        .padding(10.dp)
                ) {
                    Text(
                        text = "🔒 خصوصيتك محفوظة: يتم استخدام الصوت فقط أثناء جلسات اللعب النشطة لتحويل الكلام لنص والتدقيق الذكي بالذكاء الاصطناعي.",
                        style = MaterialTheme.typography.labelSmall,
                        color = StadiumCyan,
                        fontSize = 11.sp
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (isPermanentlyDenied) onOpenSettings() else onRequestPermission()
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = if (isPermanentlyDenied) "فتح إعدادات الجهاز ⚙️" else "السماح بالمايك الآن 🎙️",
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("لاحقاً", color = TextMuted)
            }
        }
    )
}

/**
 * Interactive Target Guess Card with Voice and Gemini AI Verification.
 */
@Composable
fun VoiceTargetGuessCard(
    promptTitle: String,
    onGuessSubmitted: (String) -> Unit,
    isAuditing: Boolean,
    lastEvaluation: com.example.data.ai.AiSpokenAnswerEvaluation?,
    modifier: Modifier = Modifier,
    placeholderText: String = "انطق تخمينك بالمايك أو اكتبه هنا..."
) {
    val context = LocalContext.current
    var spokenText by remember { mutableStateOf("") }
    var isListening by remember { mutableStateOf(false) }

    val speechRecognizerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        isListening = false
        if (result.resultCode == Activity.RESULT_OK) {
            val spokenMatches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val recognized = spokenMatches?.firstOrNull() ?: ""
            if (recognized.isNotBlank()) {
                spokenText = recognized
                onGuessSubmitted(recognized)
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            launchSpeechIntent(speechRecognizerLauncher)
            isListening = true
        }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = PitchCardElevated),
        border = BorderStroke(1.5.dp, if (isListening) Color(0xFFFF1744) else TrophyGold.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "🎯", fontSize = 18.sp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = promptTitle,
                        style = MaterialTheme.typography.titleSmall,
                        color = TrophyGold,
                        fontWeight = FontWeight.Bold
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(EmeraldNeon.copy(alpha = 0.15f))
                        .border(1.dp, EmeraldNeon.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "تدقيق ذكي Gemini AI 🤖",
                        style = MaterialTheme.typography.labelSmall,
                        color = EmeraldNeon,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp
                    )
                }
            }

            OutlinedTextField(
                value = spokenText,
                onValueChange = { spokenText = it },
                placeholder = {
                    Text(
                        text = if (isListening) "🎙️ جارٍ الاستماع لصوتك... انطق الإجابة الآن!" else placeholderText,
                        color = if (isListening) Color(0xFFFF5252) else TextMuted,
                        fontSize = 13.sp
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("voice_guess_input_field"),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = TrophyGold,
                    unfocusedBorderColor = PitchDivider,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedContainerColor = PitchDark,
                    unfocusedContainerColor = PitchDark
                ),
                trailingIcon = {
                    if (isAuditing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = TrophyGold,
                            strokeWidth = 2.dp
                        )
                    } else if (spokenText.isNotBlank()) {
                        IconButton(
                            onClick = {
                                onGuessSubmitted(spokenText)
                            },
                            modifier = Modifier.testTag("send_voice_guess_btn")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "تدقيق الإجابة", tint = TrophyGold)
                        }
                    }
                }
            )

            // Bottom mic action bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isListening) "🔴 جارٍ الاستماع لصوتك..." else "اضغط المايك وانطق تخمينك مباشرة 🎙️",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isListening) Color(0xFFFF5252) else TextSecondary,
                    fontSize = 11.sp
                )

                VoiceMicButton(
                    isListening = isListening,
                    size = 46,
                    label = "انطق التخمين بالمايك",
                    onStartListening = {
                        val hasPerm = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED
                        if (hasPerm) {
                            launchSpeechIntent(speechRecognizerLauncher)
                            isListening = true
                        } else {
                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    }
                )
            }

            // Evaluation Card
            if (lastEvaluation != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (lastEvaluation.isCorrect) EmeraldGreen.copy(alpha = 0.2f) else Color(0xFF3B1515)
                    ),
                    border = BorderStroke(1.dp, if (lastEvaluation.isCorrect) EmeraldNeon else Color(0xFFFF1744))
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = if (lastEvaluation.isCorrect) "🎉" else "❌", fontSize = 20.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "${lastEvaluation.matchType} • دقة ${(lastEvaluation.matchConfidence * 100).toInt()}% ${if (lastEvaluation.isAiPowered) "🤖" else "⚡"}",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (lastEvaluation.isCorrect) EmeraldNeon else Color(0xFFFF5252),
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = lastEvaluation.feedback,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Overloaded VoiceMicButton that binds to FootballGameViewModel and delivers recognized speech.
 */
@Composable
fun VoiceMicButton(
    viewModel: com.example.ui.FootballGameViewModel? = null,
    isMini: Boolean = false,
    onSpeechResult: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isListening by remember { mutableStateOf(false) }

    val speechRecognizerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        isListening = false
        if (result.resultCode == Activity.RESULT_OK) {
            val spokenMatches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val recognized = spokenMatches?.firstOrNull() ?: ""
            if (recognized.isNotBlank()) {
                onSpeechResult(recognized)
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            launchSpeechIntent(speechRecognizerLauncher)
            isListening = true
        }
    }

    IconButton(
        onClick = {
            val hasPerm = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
            if (hasPerm) {
                launchSpeechIntent(speechRecognizerLauncher)
                isListening = true
            } else {
                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        },
        modifier = modifier
    ) {
        Icon(
            imageVector = Icons.Default.Mic,
            contentDescription = "تسجيل صوتي بالمايك",
            tint = if (isListening) Color(0xFFFF1744) else EmeraldNeon
        )
    }
}

