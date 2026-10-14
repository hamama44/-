package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stars
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.GameDifficulty
import com.example.ui.theme.*

/**
 * Enhanced Dashboard Top Bar displaying user points, level, and XP progress bar
 */
@Composable
fun DashboardTopBar(
    playerName: String,
    avatarEmoji: String = "👑",
    levelTitle: String,
    levelNumber: Int,
    points: Int,
    coins: Int,
    levelProgress: Float,
    onAvatarClick: (() -> Unit)? = null
) {
    Surface(
        color = PitchDark,
        tonalElevation = 6.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            // Top Row: App Title & Coins + Points
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Branding & Avatar
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(Brush.radialGradient(listOf(GoldDark, PitchCard)))
                            .border(1.5.dp, TrophyGold, CircleShape)
                            .clickable(enabled = onAvatarClick != null) { onAvatarClick?.invoke() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = avatarEmoji, fontSize = 22.sp)
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "ملعب التحدي ⚽",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "$levelTitle • المستوى $levelNumber",
                            style = MaterialTheme.typography.bodySmall,
                            color = TrophyGold,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                // Balance Pills (Coins & Points)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Points Pill
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(PitchCard)
                            .border(1.dp, EmeraldNeon.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 8.dp, vertical = 5.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = "⭐", fontSize = 12.sp)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "$points",
                                color = EmeraldNeon,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    // Coins Pill
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Brush.horizontalGradient(listOf(Color(0xFF2E2002), Color(0xFF453005))))
                            .border(1.dp, TrophyGold.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 8.dp, vertical = 5.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = "🪙", fontSize = 12.sp)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "$coins",
                                color = TrophyGold,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Integrated XP Progress Bar
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "الخبرة الكروية (XP): $points نقطة",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                    Text(
                        text = "${(levelProgress * 100).toInt()}% نحو المستوى ${levelNumber + 1}",
                        style = MaterialTheme.typography.labelSmall,
                        color = EmeraldNeon,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                }

                Spacer(modifier = Modifier.height(5.dp))

                LinearProgressIndicator(
                    progress = { levelProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = EmeraldNeon,
                    trackColor = PitchCardBorder
                )
            }
        }
    }
}

/**
 * Standard Stadium Top Bar for Sub-screens
 */
@Composable
fun StadiumTopBar(
    title: String,
    coins: Int,
    points: Int? = null,
    onBack: (() -> Unit)? = null
) {
    Surface(
        color = PitchDark,
        tonalElevation = 4.dp,
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onBack != null) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .testTag("back_button")
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(PitchCard)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "رجوع",
                            tint = TrophyGold
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (points != null) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(PitchCard)
                            .border(1.dp, PitchCardBorder, RoundedCornerShape(12.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = "⭐", fontSize = 14.sp)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "$points",
                                color = EmeraldNeon,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Brush.horizontalGradient(listOf(Color(0xFF261D04), Color(0xFF382904))))
                        .border(1.dp, TrophyGold.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "🪙", fontSize = 14.sp)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "$coins",
                            color = TrophyGold,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }
}

/**
 * Grid Game Card with Spring Scale Animation upon tap
 */
@Composable
fun GridGameCard(
    title: String,
    subtitle: String,
    badgeText: String,
    badgeColor: Color,
    iconEmoji: String,
    gradientColors: List<Color>,
    testTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "grid_card_scale"
    )

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(22.dp))
            .background(
                Brush.verticalGradient(
                    listOf(
                        PitchCardElevated,
                        PitchCard,
                        PitchDark
                    )
                )
            )
            .border(
                width = if (isPressed) 2.dp else 1.2.dp,
                brush = Brush.verticalGradient(
                    if (isPressed) {
                        listOf(TrophyGold, EmeraldNeon)
                    } else {
                        listOf(PitchCardBorder, PitchCardBorder.copy(alpha = 0.4f))
                    }
                ),
                shape = RoundedCornerShape(22.dp)
            )
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(color = TrophyGold),
                onClick = onClick
            )
            .testTag(testTag)
            .padding(14.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top: Icon + Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                // 3D Stadium Game Badge Icon
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Brush.linearGradient(gradientColors))
                        .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(16.dp))
                        .shadow(6.dp, RoundedCornerShape(16.dp), spotColor = gradientColors.firstOrNull() ?: TrophyGold),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = iconEmoji, fontSize = 26.sp)
                }

                // Status Chip
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(badgeColor.copy(alpha = 0.15f))
                        .border(1.dp, badgeColor.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 7.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = badgeText,
                        color = badgeColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Game Title
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Game Subtitle / Description
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                fontSize = 11.sp,
                lineHeight = 15.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Play Arrow Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(if (isPressed) TrophyGold else PitchCardBorder),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "العب الآن",
                        tint = if (isPressed) PitchBlack else EmeraldNeon,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun GlowCard(
    modifier: Modifier = Modifier,
    borderColor: Color = PitchCardBorder,
    backgroundColor: Color = PitchCard,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val clickableModifier = if (onClick != null) {
        modifier.clickable { onClick() }
    } else {
        modifier
    }

    Box(
        modifier = clickableModifier
            .clip(RoundedCornerShape(20.dp))
            .background(backgroundColor)
            .border(1.dp, borderColor, RoundedCornerShape(20.dp))
            .padding(16.dp)
    ) {
        Column(content = content)
    }
}

@Composable
fun TrophyButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingEmoji: String? = null
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = TrophyGold,
            contentColor = PitchBlack,
            disabledContainerColor = PitchCardBorder,
            disabledContentColor = TextMuted
        ),
        modifier = modifier
            .height(52.dp)
            .shadow(if (enabled) 8.dp else 0.dp, shape = RoundedCornerShape(16.dp), spotColor = TrophyGold)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (leadingEmoji != null) {
                Text(text = leadingEmoji, fontSize = 18.sp)
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = text,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        }
    }
}

@Composable
fun EmeraldButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingEmoji: String? = null
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = EmeraldNeon,
            contentColor = PitchBlack,
            disabledContainerColor = PitchCardBorder,
            disabledContentColor = TextMuted
        ),
        modifier = modifier
            .height(52.dp)
            .shadow(if (enabled) 8.dp else 0.dp, shape = RoundedCornerShape(16.dp), spotColor = EmeraldNeon)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (leadingEmoji != null) {
                Text(text = leadingEmoji, fontSize = 18.sp)
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = text,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        }
    }
}

@Composable
fun RewardedAdDialog(
    countdown: Int,
    onClaim: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(PitchDark, PitchSurface, PitchBlack)
                    )
                )
                .border(2.dp, TrophyGold, RoundedCornerShape(24.dp))
                .padding(24.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "📺 راعي المباراة",
                        style = MaterialTheme.typography.titleMedium,
                        color = TrophyGold
                    )
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

                Spacer(modifier = Modifier.height(16.dp))

                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .clip(CircleShape)
                        .background(PitchCard)
                        .border(3.dp, if (countdown > 0) EmeraldNeon else TrophyGold, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (countdown > 0) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "$countdown",
                                fontSize = 36.sp,
                                fontWeight = FontWeight.Black,
                                color = TrophyGold
                            )
                            Text(
                                text = "ثوانٍ",
                                fontSize = 12.sp,
                                color = TextSecondary
                            )
                        }
                    } else {
                        Text(
                            text = "🎁",
                            fontSize = 44.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = if (countdown > 0) "شاهد الإعلان للحصول على المساعدات والذهب!" else "تهانينا! جاهز لاستلام المكافأة 🎉",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "+75 قطعة ذهبية 🪙 • إعادة تعبئة المساعدات الثلاث 🛟",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                    color = EmeraldLight
                )

                Spacer(modifier = Modifier.height(24.dp))

                TrophyButton(
                    text = if (countdown > 0) "انتظر $countdown ث..." else "استلم الجائزة فوراً! 🏆",
                    onClick = onClaim,
                    enabled = countdown <= 0,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/**
 * Interactive Difficulty Selection Component (سهل / متوسط / صعب)
 * Allows players to choose difficulty with visual perks & bonus percentages
 */
@Composable
fun DifficultySelectionDialog(
    currentDifficulty: GameDifficulty,
    onSelectDifficulty: (GameDifficulty) -> Unit,
    onConfirm: (GameDifficulty) -> Unit,
    onDismiss: () -> Unit
) {
    var selected by remember { mutableStateOf(currentDifficulty) }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(PitchDark, PitchSurface, PitchBlack)
                    )
                )
                .border(2.dp, TrophyGold.copy(alpha = 0.8f), RoundedCornerShape(24.dp))
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "⚡", fontSize = 22.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "مستوى التحدي والصعوبة",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "حدد درجة الصعوبة قبل النزول للملعب",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                                fontSize = 11.sp
                            )
                        }
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

                Spacer(modifier = Modifier.height(16.dp))

                // 3 Difficulty Options
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    GameDifficulty.values().forEach { diff ->
                        val isSelected = selected == diff
                        val diffColor = Color(diff.colorHex)

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (isSelected) PitchCardElevated else PitchCard)
                                .border(
                                    if (isSelected) 2.dp else 1.dp,
                                    if (isSelected) diffColor else PitchCardBorder,
                                    RoundedCornerShape(16.dp)
                                )
                                .clickable {
                                    selected = diff
                                    onSelectDifficulty(diff)
                                }
                                .padding(14.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(42.dp)
                                            .clip(CircleShape)
                                            .background(diffColor.copy(alpha = 0.2f))
                                            .border(1.5.dp, diffColor.copy(alpha = 0.6f), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(text = diff.iconEmoji, fontSize = 20.sp)
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Column {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = diff.titleArabic,
                                                style = MaterialTheme.typography.titleSmall,
                                                color = if (isSelected) diffColor else Color.White,
                                                fontWeight = FontWeight.Bold
                                            )
                                            if (diff.pointsBonusPercent > 0) {
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(6.dp))
                                                        .background(diffColor.copy(alpha = 0.25f))
                                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                                ) {
                                                    Text(
                                                        text = "+${diff.pointsBonusPercent}% نقاط",
                                                        fontSize = 10.sp,
                                                        color = diffColor,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(2.dp))

                                        Text(
                                            text = diff.description,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = TextSecondary,
                                            fontSize = 11.sp
                                        )
                                    }
                                }

                                // Selection checkmark or radio
                                Box(
                                    modifier = Modifier
                                        .size(22.dp)
                                        .clip(CircleShape)
                                        .background(if (isSelected) diffColor else Color.Transparent)
                                        .border(2.dp, if (isSelected) diffColor else TextMuted, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isSelected) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(PitchBlack)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Action Button
                EmeraldButton(
                    text = "انزل لأرضية الملعب والعب الآن ⚽",
                    leadingEmoji = "👟",
                    onClick = { onConfirm(selected) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

