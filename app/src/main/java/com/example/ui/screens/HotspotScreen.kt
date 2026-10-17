package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.lan.DiscoveredHost
import com.example.data.lan.LanProtocol
import com.example.ui.FootballGameViewModel
import com.example.ui.GameScreen
import kotlinx.coroutines.delay
import com.example.ui.components.EmeraldButton
import com.example.ui.components.GlowCard
import com.example.ui.components.StadiumTopBar
import com.example.ui.theme.*

/**
 * شاشة اللعب عبر الهوتسبوت / الشبكة المحلية.
 *
 * بتحقّق الطريقتين اللي المستخدم طلبهم:
 *  1. **اكتشاف تلقائي** ببثّ UDP: السيرفر بيعلن عن نفسه، والأجهزة بتلاقيه
 *     في القائمة من غير ما يكتب حد أي حاجة.
 *  2. **إدخال يدوي لعنوان الـIP** كاحتياطي: لأن بعض الراوترات/الأجهزة بتمنع
 *     البثّ، فلازم يكون فيه طريق بديل دايماً شغّال.
 *
 * بعد الاتصال، الغرف بتيجي من [com.example.data.lan.LanRoomsDataSource] عبر
 * نفس المستودع — فاللعب نفسه بيتم في [RoomMatchScreen] بالظبط زي الأونلاين.
 */
@Composable
fun HotspotScreen(viewModel: FootballGameViewModel) {
    val context = LocalContext.current
    val userProfile by viewModel.userProfile.collectAsState()
    val isHosting by viewModel.lanIsHosting.collectAsState()
    val hostAddress by viewModel.lanHostAddress.collectAsState()
    val hostPort by viewModel.lanHostPort.collectAsState()
    val discoveredHosts by viewModel.lanDiscoveredHosts.collectAsState()
    val isDiscovering by viewModel.lanIsDiscovering.collectAsState()
    val joinedHost by viewModel.lanJoinedHost.collectAsState()
    val isBusy by viewModel.lanIsBusy.collectAsState()
    val lanMessage by viewModel.lanMessage.collectAsState()
    val connectedClients by viewModel.lanConnectedClientCount.collectAsState()

    var manualIp by remember { mutableStateOf("") }
    var manualPort by remember { mutableStateOf("${LanProtocol.DEFAULT_GAME_PORT}") }

    LaunchedEffect(lanMessage) {
        lanMessage?.let { message ->
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            viewModel.dismissLanMessage()
        }
    }

    // المضيف يحتاج عدّاد متصلين يتحدث دورياً
    LaunchedEffect(isHosting) {
        if (isHosting) {
            while (true) {
                viewModel.refreshLanClientCount()
                delay(HOST_COUNTER_REFRESH_MS)
            }
        }
    }

    Scaffold(
        containerColor = PitchDark,
        topBar = {
            StadiumTopBar(
                title = "الهوتسبوت 📶",
                coins = userProfile.coins,
                points = userProfile.points,
                onBack = { viewModel.navigateBack() }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 14.dp)
        ) {
            // ---------------------------------------------------------
            // حالة الشبكة
            // ---------------------------------------------------------
            item {
                GlowCard(modifier = Modifier.fillMaxWidth(), borderColor = StadiumCyan) {
                    Text(
                        text = "حالة الشبكة المحلية",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = viewModel.describeLanStatus(),
                        color = TextSecondary,
                        fontSize = 11.sp,
                        lineHeight = 18.sp
                    )
                }
            }

            // ---------------------------------------------------------
            // وضع المضيف
            // ---------------------------------------------------------
            if (isHosting) {
                item {
                    GlowCard(modifier = Modifier.fillMaxWidth(), borderColor = TrophyGold) {
                        Text(
                            text = "سيرفرك شغّال 🎮",
                            color = TrophyGold,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "${hostAddress ?: "----"}:$hostPort",
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "اقرأ العنوان ده لأصحابك، أو خليهم يلاقوك تلقائياً من قائمة البحث 📡",
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { viewModel.navigateTo(GameScreen.SupabaseOnlineScreen) },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = EmeraldNeon),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("اعمل غرفة ⚽", color = PitchDark, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                            OutlinedButton(
                                onClick = { viewModel.stopLanHosting() },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("أوقف السيرفر 📴", color = CardRed, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "الأجهزة المتصلة بالسيرفر: $connectedClients 👥",
                            color = StadiumCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            } else if (joinedHost != null) {
                // ---------------------------------------------------------
                // وضع الضيف
                // ---------------------------------------------------------
                item {
                    GlowCard(modifier = Modifier.fillMaxWidth(), borderColor = EmeraldNeon) {
                        Text(
                            text = "متصل بسيرفر ✅",
                            color = EmeraldNeon,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = joinedHost.orEmpty(),
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { viewModel.navigateTo(GameScreen.SupabaseOnlineScreen) },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = EmeraldNeon),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("ادخل الغرفة ⚽", color = PitchDark, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                            OutlinedButton(
                                onClick = { viewModel.disconnectFromLanHost() },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("اقطع الاتصال 📴", color = CardRed, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            } else {
                // ---------------------------------------------------------
                // لا مضيف ولا ضيف: اعرض الطريقتين
                // ---------------------------------------------------------
                item {
                    GlowCard(modifier = Modifier.fillMaxWidth(), borderColor = EmeraldNeon) {
                        Text(
                            text = "١) افتح سيرفر على جهازك 🎮",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "افتح الهوتسبوت من إعدادات الجهاز، خلّي أصحابك يتصلوا بيه، وبعدين شغّل السيرفر هنا. مفيش إنترنت مطلوب.",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            lineHeight = 17.sp
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        EmeraldButton(
                            text = if (isBusy) "جارٍ التشغيل..." else "شغّل سيرفر اللعبة",
                            onClick = { viewModel.startLanHosting() },
                            enabled = !isBusy,
                            modifier = Modifier.fillMaxWidth(),
                            leadingEmoji = "📡"
                        )
                    }
                }

                item {
                    GlowCard(modifier = Modifier.fillMaxWidth(), borderColor = StadiumCyan) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "٢) دور على سيرفر تلقائياً 🔍",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            IconButton(
                                onClick = { viewModel.refreshLanDiscovery() },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = "إعادة البحث", tint = StadiumCyan)
                            }
                        }
                        Text(
                            text = "اتصل بنفس شبكة الواي فاي أو الهوتسبوت، والسيرفرات هتظهر هنا لوحدها.",
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        if (!isDiscovering) {
                            Button(
                                onClick = { viewModel.startLanDiscovery() },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = StadiumCyan),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("ابدأ البحث التلقائي 📡", color = PitchDark, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        } else {
                            Text(
                                text = "بيدور على سيرفرات... 📡",
                                color = EmeraldNeon,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (discoveredHosts.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            discoveredHosts.forEach { host ->
                                DiscoveredHostRow(
                                    host = host,
                                    sameSubnet = viewModel.isSameSubnetAsDevice(host.hostIp),
                                    enabled = !isBusy,
                                    onClick = { viewModel.connectToDiscoveredHost(host) }
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                            }
                        } else if (isDiscovering) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "مفيش سيرفرات ظاهرة لسه — تأكد إن صاحبك شغّل السيرفر وإنكم على نفس الشبكة ⚠️",
                                color = TrophyGold,
                                fontSize = 10.sp,
                                lineHeight = 15.sp
                            )
                        }
                    }
                }

                item {
                    GlowCard(modifier = Modifier.fillMaxWidth(), borderColor = PitchDivider) {
                        Text(
                            text = "٣) أو اكتب عنوان الـIP يدوياً ⌨️",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "احتياطي لو البحث التلقائي مش شغّال على جهازك أو الراوتر بيمنع البثّ.",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            lineHeight = 17.sp
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = manualIp,
                            onValueChange = { manualIp = it.trim() },
                            label = { Text("عنوان السيرفر (مثال: 192.168.43.1)", fontSize = 11.sp) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                            textStyle = LocalTextStyle.current.copy(
                                color = Color.White,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp
                            )
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = manualPort,
                            onValueChange = { input -> manualPort = input.filter { it.isDigit() }.take(5) },
                            label = { Text("البورت (افتراضي ${LanProtocol.DEFAULT_GAME_PORT})", fontSize = 11.sp) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            textStyle = LocalTextStyle.current.copy(
                                color = Color.White,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp
                            )
                        )

                        // تلميح فوري لو العنوان من شبكة تانية
                        if (manualIp.isNotBlank() && !viewModel.isSameSubnetAsDevice(manualIp)) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "⚠️ العنوان ده مش على نفس شبكة جهازك — تأكد إنكم متصلين بنفس الهوتسبوت",
                                color = TrophyGold,
                                fontSize = 10.sp,
                                lineHeight = 15.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Button(
                            onClick = {
                                val port = manualPort.toIntOrNull() ?: LanProtocol.DEFAULT_GAME_PORT
                                viewModel.connectToLanHost(manualIp, port)
                            },
                            enabled = !isBusy && manualIp.isNotBlank(),
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldNeon),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                text = if (isBusy) "جارٍ الاتصال..." else "اتصل بالسيرفر 🔌",
                                color = PitchDark,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }

            // ---------------------------------------------------------
            // شرح مبسّط
            // ---------------------------------------------------------
            item {
                GlowCard(modifier = Modifier.fillMaxWidth(), borderColor = PitchDivider) {
                    Text("إزاي تلعبوا مع بعض؟ 🤝", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "١. جهاز يفتح الهوتسبوت ويشغّل السيرفر هنا.\n" +
                            "٢. الباقي يتصلوا بالهوتسبوت من إعدادات الواي فاي.\n" +
                            "٣. يلاقوا السيرفر تلقائياً أو يكتبوا عنوانه يدوياً.\n" +
                            "٤. صاحب السيرفر يعمل غرفة ويشارك كودها.\n" +
                            "٥. الكل يدخل الغرفة ويلعب — والنقاط بتتزامن لحظياً بين الأجهزة ⚡",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        lineHeight = 19.sp
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(8.dp)) }
        }
    }
}

@Composable
private fun DiscoveredHostRow(
    host: DiscoveredHost,
    sameSubnet: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(PitchCard)
            .border(
                1.dp,
                if (sameSubnet) EmeraldNeon.copy(alpha = 0.45f) else PitchDivider,
                RoundedCornerShape(10.dp)
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("📡", fontSize = 17.sp)
        Spacer(modifier = Modifier.width(9.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "${host.hostIp}:${host.port}",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = buildString {
                    append(host.hostName.ifBlank { "سيرفر" })
                    if (!host.roomCode.isNullOrBlank()) append(" • غرفة ${host.roomCode}")
                    append(" • ${host.playerCount} لاعب")
                },
                color = TextSecondary,
                fontSize = 10.sp
            )
            if (!sameSubnet) {
                Text(
                    text = "مش على نفس شبكتك ⚠️",
                    color = TrophyGold,
                    fontSize = 9.sp,
                    textAlign = TextAlign.Start
                )
            }
        }

        Text("اتصل ←", color = EmeraldNeon, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}


/** فترة تحديث عدّاد المتصلين بالسيرفر. */
private const val HOST_COUNTER_REFRESH_MS = 3_000L
