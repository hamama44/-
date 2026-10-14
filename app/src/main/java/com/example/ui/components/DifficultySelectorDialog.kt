package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.GameDifficulty
import com.example.ui.theme.*

@Composable
fun DifficultySelectorDialog(
    gameTitle: String,
    initialDifficulty: GameDifficulty = GameDifficulty.MEDIUM,
    onStartGame: (GameDifficulty) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedDifficulty by remember { mutableStateOf(initialDifficulty) }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(26.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(PitchSurface, PitchDark, PitchBlack)
                    )
                )
                .border(2.dp, TrophyGold, RoundedCornerShape(26.dp))
                .padding(20.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "مستوى التحدي 🎯",
                            style = MaterialTheme.typography.titleLarge,
                            color = TrophyGold,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = gameTitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = EmeraldNeon
                        )
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "إغلاق",
                            tint = TextMuted
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Difficulty Options List
                GameDifficulty.values().forEach { difficulty ->
                    val isSelected = selectedDifficulty == difficulty
                    val diffColor = Color(difficulty.colorHex)

                    Box(
                        modifier = Modifier
                            .testTag("difficulty_${difficulty.id}")
                            .fillMaxWidth()
                            .padding(vertical = 5.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(if (isSelected) diffColor.copy(alpha = 0.15f) else PitchCard)
                            .border(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) diffColor else PitchCardBorder,
                                shape = RoundedCornerShape(18.dp)
                            )
                            .clickable { selectedDifficulty = difficulty }
                            .padding(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(CircleShape)
                                        .background(diffColor.copy(alpha = 0.2f))
                                        .border(1.dp, diffColor, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(text = difficulty.iconEmoji, fontSize = 20.sp)
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column {
                                    Text(
                                        text = difficulty.titleArabic,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = if (isSelected) diffColor else Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = difficulty.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextSecondary,
                                        fontSize = 11.sp
                                    )
                                }
                            }

                            // Stakes Badge
                            Column(horizontalAlignment = Alignment.End) {
                                if (difficulty.pointsBonusPercent > 0) {
                                    Text(
                                        text = "+${difficulty.pointsBonusPercent}% نقاط",
                                        color = EmeraldNeon,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    )
                                }
                                Text(
                                    text = "${difficulty.timerSeconds} ثانية",
                                    color = TrophyGold,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                TrophyButton(
                    text = "انطلق للتحدي ⚽",
                    onClick = { onStartGame(selectedDifficulty) },
                    modifier = Modifier.fillMaxWidth(),
                    leadingEmoji = "🚀"
                )
            }
        }
    }
}
