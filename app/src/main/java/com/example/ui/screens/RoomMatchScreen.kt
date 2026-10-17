package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.room.GameRoom
import com.example.data.room.RoomParticipant
import com.example.data.room.RoomStatus
import com.example.data.room.RoomTeamScores
import com.example.ui.FootballGameViewModel
import com.example.ui.components.EmeraldButton
import com.example.ui.components.GlowCard
import com.example.ui.components.StadiumTopBar
import com.example.ui.theme.*

/**
 * شاشة غرفة المباراة المباشرة.
 *
 * هنا بيحصل **تزامن اللعب نفسه** بين الأجهزة: كل جهاز بيشوف نفس قائمة
 * المشاركين ونفس نتائج الفرق ونفس رقم الجولة، لأن الحالة بتتسحب من
 * `GameRoomRepository` (Supabase أو الهوتسبوت) كل 3 ثواني.
 *
 * المضيف وحده يشوف أزرار التحكم (بدء/الجولة التالية/إنهاء)، والكل يشوف
 * زر «العب الجولة» اللي بيفتح اللعبة الفعلية — ونقاطها بتتدفع للغرفة
 * تلقائياً عبر `recordPartyGameScore`.
 */
@Composable
fun RoomMatchScreen(viewModel: FootballGameViewModel) {
    val context = LocalContext.current
    val userProfile by viewModel.userProfile.collectAsState()
    val room by viewModel.activeRoom.collectAsState()
    val participants by viewModel.activeRoomParticipants.collectAsState()
    val teamScores by viewModel.roomTeamScores.collectAsState()
    val isHost by viewModel.isRoomSessionHost.collectAsState()
    val roomActionMessage by viewModel.roomActionMessage.collectAsState()

    LaunchedEffect(roomActionMessage) {
        roomActionMessage?.let { message ->
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            viewModel.dismissRoomActionMessage()
        }
    }

    Scaffold(
        containerColor = PitchDark,
        topBar = {
            StadiumTopBar(
                title = "غرفة ${room?.roomCode ?: "----"} ⚽",
                coins = userProfile.coins,
                points = userProfile.points,
                onBack = { viewModel.leaveRoom() }
            )
        }
    ) { padding ->
        val currentRoom = room
        if (currentRoom == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("مفيش غرفة مفتوحة حالياً 🤔", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(12.dp))
                    EmeraldButton(text = "رجوع لغرف اللعب", onClick = { viewModel.leaveRoom() })
                }
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(14.dp)
        ) {
            // -------------------------------------------------------------
            // حالة الغرفة + الجولة
            // -------------------------------------------------------------
            GlowCard(modifier = Modifier.fillMaxWidth(), borderColor = TrophyGold) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = currentRoom.arabicGameTitle,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 15.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "المضيف: ${currentRoom.hostName} • ${currentRoom.arabicStatusLabel}",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                        Text(
                            text = if (currentRoom.isOfflineOnly) {
                                "الوضع: شبكة محلية/أوفلاين 📴 (هتتزامن لما توصل بالسيرفر)"
                            } else {
                                "الوضع: أونلاين عبر Supabase ☁️"
                            },
                            fontSize = 10.sp,
                            color = StadiumCyan
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "الجولة",
                            fontSize = 10.sp,
                            color = TextSecondary
                        )
                        Text(
                            text = "${currentRoom.currentRound}/${currentRoom.maxRounds}",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = TrophyGold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // -------------------------------------------------------------
            // لوحة نتائج الفرق (لحظية)
            // -------------------------------------------------------------
            TeamScoreBoard(teamScores = teamScores)

            Spacer(modifier = Modifier.height(12.dp))

            // -------------------------------------------------------------
            // أزرار التحكم
            // -------------------------------------------------------------
            if (isHost) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (currentRoom.status == RoomStatus.WAITING) {
                        Button(
                            onClick = { viewModel.hostStartGame() },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldNeon),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                text = if (teamScores.allReady) "ابدأ اللعب 🚀" else "ابدأ على أي حال ⚽",
                                color = PitchDark,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    } else if (currentRoom.status == RoomStatus.PLAYING) {
                        Button(
                            onClick = { viewModel.hostNextRound() },
                            enabled = !currentRoom.isRoundsFinished,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = TrophyGold),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("الجولة التالية 🔁", color = PitchDark, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }

                    OutlinedButton(
                        onClick = { viewModel.hostFinishGame() },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("إنهاء الغرفة 🏁", color = CardRed, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                if (!teamScores.allReady && currentRoom.status == RoomStatus.WAITING) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "لسه مش الكل جاهز: ${teamScores.readyCount}/${teamScores.totalCount} ✅",
                        fontSize = 11.sp,
                        color = TrophyGold,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))
            }

            if (currentRoom.status != RoomStatus.FINISHED) {
                EmeraldButton(
                    text = "العب الجولة 🎮",
                    onClick = { viewModel.launchRoomRound() },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "نقاطك في الجولة هتتضاف تلقائياً لفريقك ويشوفها الباقيين لحظياً ⚡",
                    fontSize = 10.sp,
                    color = TextSecondary,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            } else {
                MatchResultCard(
                    teamScores = teamScores,
                    onExit = { viewModel.leaveRoom() }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // -------------------------------------------------------------
            // المشاركون (لحظي)
            // -------------------------------------------------------------
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "المشاركون (${teamScores.totalCount}/${currentRoom.maxPlayers})",
                    fontSize = 13.sp,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = { viewModel.refreshRoomSession() }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Refresh, contentDescription = "تحديث", tint = StadiumCyan)
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(participants) { participant ->
                    ParticipantRow(
                        participant = participant,
                        isCurrentUser = participant.playerName.trim()
                            .equals(userProfile.playerName.trim(), ignoreCase = true),
                        onReadyToggle = { viewModel.toggleParticipantReady(participant) }
                    )
                }

                if (participants.isEmpty()) {
                    item {
                        Text(
                            text = "مفيش مشاركين ظاهرين لسه — اعمل تحديث أو شارك كود الغرفة ⚽",
                            fontSize = 12.sp,
                            color = TextSecondary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TeamScoreBoard(teamScores: RoomTeamScores) {
    GlowCard(modifier = Modifier.fillMaxWidth(), borderColor = StadiumCyan) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(horizontalAlignment = Alignment.Start, modifier = Modifier.weight(1f)) {
                Text("🔴 أحمر", color = CardRed, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text(
                    text = "${teamScores.redScore}",
                    color = Color.White,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text("${teamScores.redCount} لاعب", color = TextSecondary, fontSize = 10.sp)
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1.2f)) {
                Text(
                    text = teamScores.arabicLeaderLabel,
                    color = TrophyGold,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                if (!teamScores.isTie) {
                    Text("الفرق: ${teamScores.scoreGap}", color = TextSecondary, fontSize = 10.sp)
                }
            }

            Column(horizontalAlignment = Alignment.End, modifier = Modifier.weight(1f)) {
                Text("🔵 أزرق", color = StadiumCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text(
                    text = "${teamScores.blueScore}",
                    color = Color.White,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text("${teamScores.blueCount} لاعب", color = TextSecondary, fontSize = 10.sp)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // شريط النسبة بين الفريقين
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(PitchDivider)
        ) {
            val redRatio = teamScores.redProgressRatio.coerceIn(0.04f, 0.96f)
            Box(
                modifier = Modifier
                    .weight(redRatio)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(4.dp))
                    .background(CardRed)
            )
            Box(
                modifier = Modifier
                    .weight(1f - redRatio)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(4.dp))
                    .background(StadiumCyan)
            )
        }
    }
}

@Composable
private fun MatchResultCard(
    teamScores: RoomTeamScores,
    onExit: () -> Unit
) {
    GlowCard(modifier = Modifier.fillMaxWidth(), borderColor = TrophyGold) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("🏁 انتهت الغرفة", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = when {
                    teamScores.isTie -> "تعادل! ${teamScores.redScore} - ${teamScores.blueScore} 🤝"
                    teamScores.redScore > teamScores.blueScore ->
                        "الفريق الأحمر كسب 🏆 ${teamScores.redScore} - ${teamScores.blueScore}"
                    else -> "الفريق الأزرق كسب 🏆 ${teamScores.blueScore} - ${teamScores.redScore}"
                },
                color = TrophyGold,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(10.dp))
            EmeraldButton(
                text = "رجوع لغرف اللعب",
                onClick = onExit,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun ParticipantRow(
    participant: RoomParticipant,
    isCurrentUser: Boolean,
    onReadyToggle: () -> Unit
) {
    val teamColor = if (participant.isTeamRed) CardRed else StadiumCyan

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(PitchCard)
            .border(1.dp, PitchDivider, RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(teamColor.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) {
            Text(text = participant.avatarEmoji, fontSize = 18.sp)
        }

        Spacer(modifier = Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = participant.playerName,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 13.sp
                )
                if (participant.isHost) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("👑", fontSize = 11.sp)
                }
                if (isCurrentUser) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("(أنت)", color = EmeraldNeon, fontSize = 10.sp)
                }
            }
            Text(
                text = "${participant.arabicTeamLabel} • ${participant.arabicReadyLabel}",
                fontSize = 10.sp,
                color = TextSecondary
            )
        }

        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "${participant.score}",
                color = TrophyGold,
                fontSize = 17.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Text("نقطة", color = TextSecondary, fontSize = 9.sp)
        }

        Spacer(modifier = Modifier.width(8.dp))

        OutlinedButton(
            onClick = onReadyToggle,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text(
                text = if (participant.isReady) "✅" else "جاهز؟",
                fontSize = 10.sp,
                color = if (participant.isReady) EmeraldNeon else TextSecondary
            )
        }
    }
}
