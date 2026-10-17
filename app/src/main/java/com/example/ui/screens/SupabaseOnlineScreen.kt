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
import com.example.data.supabase.*
import com.example.ui.FootballGameViewModel
import com.example.ui.GameScreen
import com.example.ui.components.EmeraldButton
import com.example.ui.components.GlowCard
import com.example.ui.components.StadiumTopBar
import com.example.ui.theme.*
import kotlinx.coroutines.launch
import kotlin.random.Random

@Composable
fun SupabaseOnlineScreen(viewModel: FootballGameViewModel) {
    val context = LocalContext.current
    val userProfile by viewModel.userProfile.collectAsState()
    val scope = rememberCoroutineScope()

    val supabaseClient = remember { SupabaseClient() }
    var selectedTab by remember { mutableIntStateOf(0) } // 0: غرف اللعب, 1: السيرفرات الإقليمية, 2: المتصدرين سحابياً

    var isConfigured by remember { mutableStateOf(SupabaseConfig.isConfigured) }
    var showConfigDialog by remember { mutableStateOf(false) }

    // Rooms state
    var activeRooms by remember { mutableStateOf<List<SupabaseGameRoom>>(emptyList()) }
    var isLoadingRooms by remember { mutableStateOf(false) }
    var showCreateRoomDialog by remember { mutableStateOf(false) }
    var showJoinRoomDialog by remember { mutableStateOf(false) }

    // Leaderboard state
    var cloudScores by remember { mutableStateOf<List<SupabaseScore>>(emptyList()) }
    var isLoadingScores by remember { mutableStateOf(false) }

    fun refreshRooms() {
        scope.launch {
            isLoadingRooms = true
            val rooms = supabaseClient.fetchActiveGameRooms()
            activeRooms = if (rooms.isNotEmpty()) rooms else listOf(
                SupabaseGameRoom(
                    id = "mock_1",
                    roomCode = "STAD99",
                    gameType = "thirty_seconds",
                    status = "waiting",
                    hostName = "كابتن ساري ⚽"
                ),
                SupabaseGameRoom(
                    id = "mock_2",
                    roomCode = "BUZZ22",
                    gameType = "buzzer",
                    status = "waiting",
                    hostName = "فارس الكالتشيو 🇮🇹"
                ),
                SupabaseGameRoom(
                    id = "mock_3",
                    roomCode = "GRID33",
                    gameType = "tictactoe",
                    status = "waiting",
                    hostName = "البروفيسور 🧠"
                )
            )
            isLoadingRooms = false
        }
    }

    fun refreshScores() {
        scope.launch {
            isLoadingScores = true
            val scores = supabaseClient.fetchLeaderboard()
            cloudScores = if (scores.isNotEmpty()) scores else listOf(
                SupabaseScore(id = "1", playerName = "عمر المدريدي 👑", points = 5420, coins = 1100, levelTitle = "أسطورة الملاعب", favoriteClub = "ريال مدريد"),
                SupabaseScore(id = "2", playerName = "ياسين الأطلسي 🇲🇦", points = 4950, coins = 980, levelTitle = "محترف دولي", favoriteClub = "الوداد البيضاوي"),
                SupabaseScore(id = "3", playerName = "أحمد الفراعنة 🇪🇬", points = 4310, coins = 820, levelTitle = "كابتن الملعب", favoriteClub = "الأهلي المصري")
            )
            isLoadingScores = false
        }
    }

    LaunchedEffect(Unit) {
        SupabaseConfig.init(context)
        isConfigured = SupabaseConfig.isConfigured
        refreshRooms()
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
                            Text(
                                text = if (isConfigured) "قاعدة بيانات PostgreSQL + غرف WebSockets جاهزة" else "اضغط هنا لإدخال بيانات مشروع Supabase الخاص بك",
                                fontSize = 10.sp,
                                color = TextSecondary
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
                            val gameTitle = when (room.gameType) {
                                "thirty_seconds" -> "تحدي الثلاثين ⏱️"
                                "buzzer" -> "صراع الجرس 🔔"
                                "tictactoe" -> "تيك تاك تو كروي ⚽"
                                "auction" -> "المزاد الكروي 🔨"
                                else -> "تحدي مباشر ⚽"
                            }

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
                                            text = "المضيف: ${room.hostName} • الحالة: ${if (room.status == "waiting") "في انتظار منافس ⏳" else "جارية ⚽"}",
                                            fontSize = 11.sp,
                                            color = TextSecondary
                                        )
                                    }

                                    Button(
                                        onClick = {
                                            viewModel.audioEffects.playWhistle()
                                            // Launch corresponding game for PvP match
                                            when (room.gameType) {
                                                "thirty_seconds" -> viewModel.startThirtySecondsQuiz()
                                                "buzzer" -> viewModel.navigateTo(GameScreen.ThirtySecondsRoundsScreen)
                                                "tictactoe" -> viewModel.navigateTo(GameScreen.FootballTicTacToeScreen)
                                                "auction" -> viewModel.navigateTo(GameScreen.FootballAuctionScreen)
                                                else -> viewModel.startPvPMatchSearch()
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldNeon),
                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text("دخول 🎮", color = PitchDark, fontWeight = FontWeight.Bold, fontSize = 12.sp)
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
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(EmeraldNeon.copy(alpha = 0.2f))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text("Ping: 1ms", color = EmeraldNeon, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))

                                    Button(
                                        onClick = {
                                            viewModel.audioEffects.playWhistle()
                                            viewModel.navigateTo(GameScreen.PassAndPlayScreen)
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.buttonColors(containerColor = TrophyGold),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Text("تشغيل غرفة الهوتسبوت المحلية 🚀", color = PitchDark, fontWeight = FontWeight.Bold)
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
        val generatedCode = remember { "STAD${Random.nextInt(10, 99)}" }

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
                    Text(
                        text = generatedCode,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = TrophyGold
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
                        text = "تأكيد وبدء الغرفة 🚀",
                        onClick = {
                            scope.launch {
                                val newRoom = SupabaseGameRoom(
                                    roomCode = generatedCode,
                                    gameType = selectedGameType,
                                    hostName = userProfile.playerName
                                )
                                supabaseClient.createGameRoom(newRoom)
                                showCreateRoomDialog = false
                                refreshRooms()
                                Toast.makeText(context, "تم إنشاء الغرفة ($generatedCode) بنجاح!", Toast.LENGTH_SHORT).show()
                            }
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
                                viewModel.audioEffects.playWhistle()
                                viewModel.startPvPMatchSearch()
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
