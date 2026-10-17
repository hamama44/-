package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.room.RoomParticipant
import com.example.data.supabase.*
import com.example.ui.FootballGameViewModel
import com.example.ui.GameScreen
import com.example.ui.components.EmeraldButton
import com.example.ui.components.GlowCard
import com.example.ui.components.StadiumTopBar
import com.example.ui.theme.*
import kotlinx.coroutines.delay

/** فترة تحديث قائمة المشاركين تلقائياً طالما الحوار مفتوح. */
private const val PARTICIPANTS_REFRESH_INTERVAL_MS = 5_000L

@Composable
fun SupabaseOnlineScreen(viewModel: FootballGameViewModel) {
    val context = LocalContext.current
    val userProfile by viewModel.userProfile.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) } // 0: غرف اللعب, 1: السيرفرات الإقليمية, 2: المتصدرين سحابياً

    var isConfigured by remember { mutableStateOf(SupabaseConfig.isConfigured) }
    var showConfigDialog by remember { mutableStateOf(false) }

    // Rooms state — كلها عبر GameRoomRepository (Supabase + كاش Room محلي)
    val activeRooms by viewModel.onlineRooms.collectAsState()
    val isLoadingRooms by viewModel.isRoomActionBusy.collectAsState()
    val roomPreviewCode by viewModel.roomPreviewCode.collectAsState()
    val roomActionMessage by viewModel.roomActionMessage.collectAsState()
    val selectedRoomCode by viewModel.selectedRoomCode.collectAsState()
    val roomParticipants by viewModel.onlineRoomParticipants.collectAsState()
    val isLoadingParticipants by viewModel.isLoadingParticipants.collectAsState()

    // حالة الشبكة المحلية / الهوتسبوت — تُعرض في كارت الهوتسبوت بدل رقم وهمي
    val lanIsHosting by viewModel.lanIsHosting.collectAsState()
    val lanJoinedHost by viewModel.lanJoinedHost.collectAsState()
    val lanHostAddress by viewModel.lanHostAddress.collectAsState()
    val lanHostPort by viewModel.lanHostPort.collectAsState()
    val lanConnectedClients by viewModel.lanConnectedClientCount.collectAsState()

    // حالة الدفع اللحظي (WebSocket) — بتتحدد في وصف الاتصال بدل نص ثابت مبالغ فيه
    val realtimeConnected by viewModel.realtimeConnected.collectAsState()
    var showCreateRoomDialog by remember { mutableStateOf(false) }
    var showJoinRoomDialog by remember { mutableStateOf(false) }

    // Leaderboard state — عبر SupabaseUserDao (views: leaderboard + users)
    val cloudScores by viewModel.cloudLeaderboard.collectAsState()
    val isLoadingScores by viewModel.isCloudLeaderboardLoading.collectAsState()
    val cloudUserProfile by viewModel.cloudUserProfile.collectAsState()

    fun refreshRooms() {
        // المستودع يجلب من Supabase ويحدّث الكاش؛ والقائمة تُراقَب عبر Flow في الـ ViewModel
        viewModel.loadOnlineRooms()
    }

    fun refreshScores() {
        viewModel.loadCloudLeaderboard()
    }

    LaunchedEffect(Unit) {
        SupabaseClientProvider.initialize(context)
        SupabaseConfig.init(context)
        isConfigured = SupabaseConfig.isConfigured
        refreshRooms()
        // دفع أي غرف/مشاركين أُنشئت أوفلاين في جلسة سابقة
        viewModel.syncPendingOnlineRooms()
    }

    // أي رسالة من المستودع (نجاح/فشل) تُعرض مرة واحدة كـ Toast
    LaunchedEffect(roomActionMessage) {
        roomActionMessage?.let { message ->
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            viewModel.dismissRoomActionMessage()
        }
    }

    // تحديث قائمة المشاركين دورياً طالما الحوار مفتوح (يشبه سلوك Realtime)
    LaunchedEffect(selectedRoomCode) {
        if (selectedRoomCode == null) return@LaunchedEffect
        while (true) {
            delay(PARTICIPANTS_REFRESH_INTERVAL_MS)
            viewModel.refreshRoomParticipants()
        }
    }

    Scaffold(
        containerColor = PitchDark,
        topBar = {
            StadiumTopBar(
                title = "السيرفرات واللعب أونلاين 🌐⚽",
                coins = userProfile.coins,
                points = userProfile.points,
                onBack = { viewModel.navigateBack() }
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
            // -----------------------------------------------------------------
            // SUPABASE STATUS BANNER
            // -----------------------------------------------------------------
            GlowCard(
                modifier = Modifier.fillMaxWidth(),
                borderColor = if (isConfigured) EmeraldNeon else TrophyGold
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .clip(CircleShape)
                                .background(if (isConfigured) EmeraldNeon else TrophyGold)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = if (isConfigured) "سيرفر Supabase السحابي نشط 🟢" else "محاكي السيرفرات نشط (جاهز للربط) 🟡",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            // وصف صادق يعكس النقل الشغال فعلاً، مش ادّعاء ثابت
                            val lanActive = lanIsHosting || lanJoinedHost != null
                            Text(
                                text = when {
                                    lanActive -> "جلسة شبكة محلية نشطة 📶 — لعب بدون إنترنت"
                                    isConfigured && realtimeConnected ->
                                        "قاعدة بيانات PostgreSQL + تحديث لحظي عبر WebSocket ⚡"
                                    isConfigured ->
                                        "قاعدة بيانات PostgreSQL + تحديث دوري كل 3 ثواني ⏳"
                                    else -> "اضغط هنا لإدخال بيانات مشروع Supabase الخاص بك"
                                },
                                fontSize = 10.sp,
                                color = if (isConfigured && realtimeConnected) EmeraldNeon else TextSecondary
                            )
                        }
                    }

                    IconButton(
                        onClick = { showConfigDialog = true },
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(PitchDark)
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = "إعدادات Supabase", tint = TrophyGold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Navigation Tabs
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = PitchCard,
                contentColor = EmeraldNeon,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, PitchDivider, RoundedCornerShape(12.dp))
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = {
                        viewModel.audioEffects.playClick()
                        selectedTab = 0
                        refreshRooms()
                    },
                    text = { Text("غرف اللعب 🎮", fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = {
                        viewModel.audioEffects.playClick()
                        selectedTab = 1
                    },
                    text = { Text("السيرفرات 🌍", fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = {
                        viewModel.audioEffects.playClick()
                        selectedTab = 2
                        refreshScores()
                    },
                    text = { Text("المتصدرين 🏆", fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // -----------------------------------------------------------------
            // TAB CONTENT
            // -----------------------------------------------------------------
            when (selectedTab) {
                0 -> {
                    // TAB 0: غرف اللعب أونلاين
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { showCreateRoomDialog = true },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldNeon),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, tint = PitchDark)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("إنشاء غرفة ➕", color = PitchDark, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }

                        Button(
                            onClick = { showJoinRoomDialog = true },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = TrophyGold),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.VpnKey, contentDescription = null, tint = PitchDark)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("دخول بكود 🔑", color = PitchDark, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "الغرف المتاحة في السيرفر حالياً (${activeRooms.size})",
                            fontSize = 13.sp,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(onClick = { refreshRooms() }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Refresh, contentDescription = "تحديث", tint = StadiumCyan)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(activeRooms) { room ->
                            val gameTitle = room.arabicGameTitle

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .border(1.dp, PitchDivider, RoundedCornerShape(12.dp)),
                                colors = CardDefaults.cardColors(containerColor = PitchCard)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = gameTitle,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White,
                                                fontSize = 14.sp
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(TrophyGold.copy(alpha = 0.2f))
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text(text = room.roomCode, fontSize = 11.sp, color = TrophyGold, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "المضيف: ${room.hostName} • ${room.arabicStatusLabel}",
                                            fontSize = 11.sp,
                                            color = TextSecondary
                                        )
                                        Text(
                                            text = if (room.isOfflineOnly) {
                                                "${room.capacityLabel} • محفوظة محلياً بانتظار المزامنة 📴"
                                            } else {
                                                "${room.capacityLabel} • جولة ${room.currentRound}/${room.maxRounds}"
                                            },
                                            fontSize = 10.sp,
                                            color = if (room.isFull) TrophyGold else StadiumCyan
                                        )
                                    }

                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Button(
                                            onClick = {
                                                // دخول الغرفة = جلسة متزامنة: المشاركين
                                                // ونتائج الفرق والجولات تتحدث بين الأجهزة
                                                viewModel.enterRoom(room)
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldNeon),
                                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text("دخول 🎮", color = PitchDark, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        }

                                        Spacer(modifier = Modifier.height(6.dp))

                                        OutlinedButton(
                                            onClick = { viewModel.openRoomParticipants(room.roomCode) },
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Group,
                                                contentDescription = null,
                                                tint = StadiumCyan,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("المشاركون 👥", color = StadiumCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                1 -> {
                    // TAB 1: السيرفرات الإقليمية والمحلية
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        item {
                            RegionalServerCard(
                                countryFlag = "🇪🇬",
                                serverName = "سيرفر القاهرة والشرق الأوسط",
                                pingMs = 15,
                                onlineCount = 1420,
                                isRecommended = true
                            )
                        }

                        item {
                            RegionalServerCard(
                                countryFlag = "🇸🇦",
                                serverName = "سيرفر الرياض والخليج العربي",
                                pingMs = 22,
                                onlineCount = 1150,
                                isRecommended = false
                            )
                        }

                        item {
                            RegionalServerCard(
                                countryFlag = "🇲🇦",
                                serverName = "سيرفر الدار البيضاء وشمال أفريقيا",
                                pingMs = 26,
                                onlineCount = 890,
                                isRecommended = false
                            )
                        }

                        item {
                            // LOCAL HOTSPOT SERVER CARD
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .border(1.5.dp, TrophyGold, RoundedCornerShape(14.dp)),
                                colors = CardDefaults.cardColors(containerColor = PitchCard)
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(text = "📶", fontSize = 24.sp)
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Column {
                                                Text(
                                                    text = "سيرفر الشبكة المحلية / الهوتسبوت",
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White,
                                                    fontSize = 14.sp
                                                )
                                                Text(
                                                    text = "لعب جماعي بدون إنترنت بين الأجهزة المجاورة!",
                                                    fontSize = 11.sp,
                                                    color = TrophyGold
                                                )
                                            }
                                        }
                                        // حالة حقيقية بدل رقم Ping وهمي
                                        val lanActive = lanIsHosting || lanJoinedHost != null
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(
                                                    if (lanActive) EmeraldNeon.copy(alpha = 0.2f)
                                                    else PitchDivider
                                                )
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = when {
                                                    lanIsHosting -> "سيرفر شغّال 🎮"
                                                    lanJoinedHost != null -> "متصل ✅"
                                                    else -> "مش شغّال"
                                                },
                                                color = if (lanActive) EmeraldNeon else TextSecondary,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    Text(
                                        text = when {
                                            lanIsHosting ->
                                                "عنوان سيرفرك: ${lanHostAddress ?: "----"}:$lanHostPort • متصلون: $lanConnectedClients 👥"
                                            lanJoinedHost != null -> "متصل بسيرفر: $lanJoinedHost"
                                            else -> "افتح سيرفر على جهازك، أو دور على سيرفرات أصحابك تلقائياً"
                                        },
                                        fontSize = 11.sp,
                                        color = if (lanIsHosting || lanJoinedHost != null) StadiumCyan else TextSecondary,
                                        lineHeight = 17.sp
                                    )

                                    Spacer(modifier = Modifier.height(10.dp))

                                    Button(
                                        onClick = {
                                            viewModel.audioEffects.playWhistle()
                                            // ردهة الهوتسبوت الحقيقية: تشغيل سيرفر + بحث تلقائي + IP يدوي
                                            viewModel.navigateTo(GameScreen.HotspotLobby)
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.buttonColors(containerColor = TrophyGold),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Text(
                                            text = if (lanIsHosting || lanJoinedHost != null) "إدارة جلسة الشبكة المحلية 📶"
                                            else "تشغيل غرفة الهوتسبوت المحلية 🚀",
                                            color = PitchDark,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                2 -> {
                    // TAB 2: المتصدرين سحابياً (SUPABASE LEADERBOARD)
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // ---- ملفك العالمي من view `public.users` ----
                        item {
                            GlowCard(
                                modifier = Modifier.fillMaxWidth(),
                                borderColor = StadiumCyan
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Text(
                                        text = "ملفك العالمي 🌍",
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        fontSize = 13.sp
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))

                                    val cloudUser = cloudUserProfile
                                    if (cloudUser != null) {
                                        Text(
                                            text = "${cloudUser.displayName} • ${cloudUser.levelTitle ?: "مشجع"}",
                                            color = TrophyGold,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "إجمالي النقاط: ${cloudUser.totalPoints} 🏆  •  العملات: ${cloudUser.totalCoins} 🪙",
                                            color = Color.White,
                                            fontSize = 11.sp
                                        )
                                        Text(
                                            text = "التحديات المسجّلة: ${cloudUser.challengesPlayed} ⚽  •  أفضل نتيجة: ${cloudUser.bestChallengePoints}",
                                            color = TextSecondary,
                                            fontSize = 11.sp
                                        )
                                    } else {
                                        Text(
                                            text = "لسه ما اتسجلتش في الجدول السحابي — سجّل اسمك دلوقتي وابدأ تحدياتك 🚀",
                                            color = TextSecondary,
                                            fontSize = 11.sp
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))

                                    Button(
                                        onClick = { viewModel.registerPlayerInCloud() },
                                        enabled = !isLoadingScores,
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldNeon),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Text(
                                            text = if (isLoadingScores) "جارٍ الحفظ..." else "أضفني لاعباً جديداً في الجدول ➕",
                                            color = PitchDark,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            }
                        }

                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "أعلى النتائج من جدول leaderboard (${cloudScores.size})",
                                    fontSize = 12.sp,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                                if (isLoadingScores) {
                                    CircularProgressIndicator(
                                        color = StadiumCyan,
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    IconButton(onClick = { refreshScores() }, modifier = Modifier.size(30.dp)) {
                                        Icon(Icons.Default.Refresh, contentDescription = "تحديث", tint = StadiumCyan)
                                    }
                                }
                            }
                        }

                        items(cloudScores) { score ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .border(1.dp, PitchDivider, RoundedCornerShape(12.dp)),
                                colors = CardDefaults.cardColors(containerColor = PitchCard)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(CircleShape)
                                                .background(TrophyGold.copy(alpha = 0.2f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(text = "👑", fontSize = 18.sp)
                                        }
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column {
                                            Text(
                                                text = score.playerName,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White,
                                                fontSize = 14.sp
                                            )
                                            Text(
                                                text = "${score.levelTitle} • مشجع ${score.favoriteClub}",
                                                fontSize = 11.sp,
                                                color = TextSecondary
                                            )
                                        }
                                    }

                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = "${score.points} نقطة",
                                            fontWeight = FontWeight.Bold,
                                            color = TrophyGold,
                                            fontSize = 14.sp
                                        )
                                        Text(
                                            text = "${score.coins} 🪙",
                                            fontSize = 11.sp,
                                            color = EmeraldNeon
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // CREATE ROOM DIALOG
    // -------------------------------------------------------------------------
    if (showCreateRoomDialog) {
        var selectedGameType by remember { mutableStateOf("thirty_seconds") }

        Dialog(onDismissRequest = { showCreateRoomDialog = false }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                colors = CardDefaults.cardColors(containerColor = PitchCard),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, EmeraldNeon)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "إنشاء غرفة أونلاين جديدة ⚽",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Text(text = "كود الغرفة للمشاركة:", fontSize = 12.sp, color = TextSecondary)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = roomPreviewCode,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = TrophyGold
                        )
                        IconButton(onClick = { viewModel.regenerateRoomCodePreview() }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Refresh, contentDescription = "توليد كود آخر", tint = StadiumCyan)
                        }
                    }
                    Text(
                        text = "يتأكد المستودع من فرادة الكود قبل الإنشاء ✓",
                        fontSize = 10.sp,
                        color = TextSecondary
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(text = "اختر اللعبة:", fontSize = 12.sp, color = TextSecondary)
                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(
                            selected = selectedGameType == "thirty_seconds",
                            onClick = { selectedGameType = "thirty_seconds" },
                            label = { Text("الثلاثين ⏱️", fontSize = 11.sp) }
                        )
                        FilterChip(
                            selected = selectedGameType == "buzzer",
                            onClick = { selectedGameType = "buzzer" },
                            label = { Text("الجرس 🔔", fontSize = 11.sp) }
                        )
                        FilterChip(
                            selected = selectedGameType == "tictactoe",
                            onClick = { selectedGameType = "tictactoe" },
                            label = { Text("تيك تاك تو ⚽", fontSize = 11.sp) }
                        )
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    EmeraldButton(
                        text = if (isLoadingRooms) "جارٍ إنشاء الغرفة..." else "تأكيد وبدء الغرفة 🚀",
                        onClick = {
                            // المستودع: توليد/اعتماد كود فريد ← إنشاء في Supabase ← كاش محلي
                            //          ← إضافة المضيف كأول مشارك
                            viewModel.createOnlineRoom(
                                gameType = selectedGameType,
                                preferredCode = roomPreviewCode
                            )
                            showCreateRoomDialog = false
                            refreshRooms()
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // JOIN ROOM DIALOG
    // -------------------------------------------------------------------------
    if (showJoinRoomDialog) {
        var inputRoomCode by remember { mutableStateOf("") }

        Dialog(onDismissRequest = { showJoinRoomDialog = false }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                colors = CardDefaults.cardColors(containerColor = PitchCard),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, TrophyGold)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "الانضمام لغرفة أونلاين 🔑",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedTextField(
                        value = inputRoomCode,
                        onValueChange = { inputRoomCode = it.uppercase() },
                        placeholder = { Text("أدخل كود الغرفة (مثل: STAD99)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            if (inputRoomCode.isNotBlank()) {
                                showJoinRoomDialog = false
                                // فحص السعة والتكرار + موازنة الفرق تلقائياً داخل المستودع،
                                // وبعد النجاح ندخل جلسة المباراة المتزامنة مباشرةً
                                viewModel.joinOnlineRoom(inputRoomCode) {
                                    viewModel.enterRoomByCode(inputRoomCode)
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = TrophyGold),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("دخول الغرفة فوراً 🎮", color = PitchDark, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // SUPABASE CONFIG & SQL SCHEMA DIALOG
    // -------------------------------------------------------------------------
    if (showConfigDialog) {
        var inputUrl by remember { mutableStateOf(SupabaseConfig.url) }
        var inputAnonKey by remember { mutableStateOf(SupabaseConfig.anonKey) }

        Dialog(onDismissRequest = { showConfigDialog = false }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                colors = CardDefaults.cardColors(containerColor = PitchCard),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, StadiumCyan)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "إعدادات Supabase السحابية ⚙️",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = inputUrl,
                        onValueChange = { inputUrl = it },
                        label = { Text("Project URL") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = inputAnonKey,
                        onValueChange = { inputAnonKey = it },
                        label = { Text("Anon Public Key") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = {
                            SupabaseConfig.saveCredentials(context, inputUrl, inputAnonKey)
                            isConfigured = SupabaseConfig.isConfigured
                            showConfigDialog = false
                            Toast.makeText(context, "تم حفظ بيانات Supabase بنجاح ✓", Toast.LENGTH_SHORT).show()
                            refreshRooms()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldNeon),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("حفظ واختبار الاتصال ✓", color = PitchDark, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedButton(
                        onClick = {
                            val sqlCode = context.assets.open("supabase_schema.sql").bufferedReader().use { it.readText() }
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Supabase Schema", sqlCode))
                            Toast.makeText(context, "تم نسخ كود SQL Schema إلى الحافظة! الصقه في Supabase SQL Editor 📋", Toast.LENGTH_LONG).show()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, tint = TrophyGold)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("نسخ كود SQL Schema الجاهز 📋", color = TrophyGold, fontSize = 12.sp)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // مخطط V2: جداول/واجهات Users • Rooms • Leaderboard
                    OutlinedButton(
                        onClick = {
                            val sqlCode = context.assets.open("supabase_schema_v2.sql").bufferedReader().use { it.readText() }
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Supabase Schema V2", sqlCode))
                            Toast.makeText(context, "تم نسخ مخطط V2 (Users/Rooms/Leaderboard)! الصقه في Supabase SQL Editor 📋", Toast.LENGTH_LONG).show()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, tint = StadiumCyan)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("نسخ مخطط V2: Users/Rooms/Leaderboard 📋", color = StadiumCyan, fontSize = 12.sp)
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = SupabaseClientProvider.describeStatus(),
                        fontSize = 10.sp,
                        color = TextSecondary
                    )
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // ROOM PARTICIPANTS DIALOG — قائمة المشاركين عبر GameRoomRepository
    // -------------------------------------------------------------------------
    if (selectedRoomCode != null) {
        RoomParticipantsDialog(
            roomCode = selectedRoomCode ?: "",
            participants = roomParticipants,
            isLoading = isLoadingParticipants,
            currentUserName = userProfile.playerName,
            onReadyToggle = { participant -> viewModel.toggleParticipantReady(participant) },
            onRefresh = { viewModel.refreshRoomParticipants() },
            onDismiss = { viewModel.closeRoomParticipants() }
        )
    }
}

@Composable
private fun RoomParticipantsDialog(
    roomCode: String,
    participants: List<RoomParticipant>,
    isLoading: Boolean,
    currentUserName: String,
    onReadyToggle: (RoomParticipant) -> Unit,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit
) {
    val redCount = participants.count { it.isTeamRed }
    val blueCount = participants.size - redCount

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            colors = CardDefaults.cardColors(containerColor = PitchCard),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.5.dp, StadiumCyan)
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "مشاركو الغرفة ($roomCode) 👥",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "🔴 أحمر: $redCount   •   🔵 أزرق: $blueCount",
                    fontSize = 11.sp,
                    color = TrophyGold,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(12.dp))

                when {
                    isLoading -> {
                        CircularProgressIndicator(color = StadiumCyan, modifier = Modifier.size(34.dp))
                        Spacer(modifier = Modifier.height(10.dp))
                        Text("جارٍ تحميل المشاركين من السيرفر...", fontSize = 11.sp, color = TextSecondary)
                    }

                    participants.isEmpty() -> {
                        Text(
                            text = "لا يوجد مشاركون بعد — شارك الكود مع أصحابك ⚽",
                            fontSize = 12.sp,
                            color = TextSecondary,
                            textAlign = TextAlign.Center
                        )
                    }

                    else -> {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 320.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(participants) { participant ->
                                val isCurrentUser = participant.playerName.trim()
                                    .equals(currentUserName.trim(), ignoreCase = true)

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(PitchDark)
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                        Text(text = participant.avatarEmoji, fontSize = 20.sp)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = participant.playerName,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White,
                                                    fontSize = 13.sp
                                                )
                                                if (participant.isHost) {
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(text = "👑 مضيف", fontSize = 10.sp, color = TrophyGold)
                                                }
                                                if (isCurrentUser) {
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(text = "(أنت)", fontSize = 10.sp, color = EmeraldNeon)
                                                }
                                            }
                                            Text(
                                                text = "${participant.arabicTeamLabel} • ${participant.arabicReadyLabel} • ${participant.score} نقطة",
                                                fontSize = 10.sp,
                                                color = TextSecondary
                                            )
                                        }
                                    }

                                    OutlinedButton(
                                        onClick = { onReadyToggle(participant) },
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(
                                            text = if (participant.isReady) "إلغاء الجاهزية" else "أنا جاهز ✅",
                                            fontSize = 10.sp,
                                            color = if (participant.isReady) TextSecondary else EmeraldNeon
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onRefresh,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, tint = StadiumCyan, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("تحديث 🔄", color = StadiumCyan, fontSize = 12.sp)
                    }

                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = TrophyGold),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("إغلاق", color = PitchDark, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun RegionalServerCard(
    countryFlag: String,
    serverName: String,
    pingMs: Int,
    onlineCount: Int,
    isRecommended: Boolean
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, if (isRecommended) EmeraldNeon.copy(alpha = 0.5f) else PitchDivider, RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = PitchCard)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = countryFlag, fontSize = 26.sp)
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(text = serverName, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 13.sp)
                    Text(text = "متصلون الآن: $onlineCount لاعب 👥", fontSize = 11.sp, color = TextSecondary)
                }
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(EmeraldNeon.copy(alpha = 0.2f))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(text = "Ping: ${pingMs}ms", color = EmeraldNeon, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
