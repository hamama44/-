package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
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
import com.example.data.room.OfflineSyncResult
import com.example.ui.theme.*

@Composable
fun OfflineModeDialog(
    isOfflineMode: Boolean,
    cachedQuestionsCount: Int,
    pendingSyncCount: Int,
    isSyncing: Boolean,
    lastSyncResult: OfflineSyncResult?,
    onToggleOfflineMode: () -> Unit,
    onSyncNow: () -> Unit,
    onDismissResult: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = PitchCard,
            tonalElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
                .border(1.5.dp, if (isOfflineMode) GoldAccent else EmeraldGreen, RoundedCornerShape(24.dp))
                .testTag("offline_mode_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header with close button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.08f))
                            .testTag("close_offline_dialog")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "إغلاق",
                            tint = TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "وضع اللعب بدون إنترنت",
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isOfflineMode) "📴" else "📶",
                            fontSize = 22.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Connection status card
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            if (isOfflineMode)
                                Brush.horizontalGradient(listOf(Color(0xFF2E2215), Color(0xFF1E1610)))
                            else
                                Brush.horizontalGradient(listOf(Color(0xFF122C20), Color(0xFF0F1E19)))
                        )
                        .border(
                            1.dp,
                            if (isOfflineMode) GoldAccent.copy(alpha = 0.4f) else EmeraldGreen.copy(alpha = 0.4f),
                            RoundedCornerShape(16.dp)
                        )
                        .padding(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Switch(
                            checked = isOfflineMode,
                            onCheckedChange = { onToggleOfflineMode() },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = GoldAccent,
                                checkedTrackColor = GoldAccent.copy(alpha = 0.3f),
                                uncheckedThumbColor = EmeraldGreen,
                                uncheckedTrackColor = EmeraldGreen.copy(alpha = 0.2f)
                            ),
                            modifier = Modifier.testTag("offline_toggle_switch")
                        )

                        Column(horizontalAlignment = Alignment.End) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = if (isOfflineMode) "وضع الأوفلاين نشط" else "متصل بالسيرفر (أونلاين)",
                                    color = if (isOfflineMode) GoldAccent else EmeraldGreen,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    imageVector = if (isOfflineMode) Icons.Default.CloudOff else Icons.Default.CloudDone,
                                    contentDescription = null,
                                    tint = if (isOfflineMode) GoldAccent else EmeraldGreen,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Text(
                                text = if (isOfflineMode)
                                    "يتم حفظ وتحديث نتائجك محلياً في Room Database"
                                else
                                    "يمكنك التحويل للأوفلاين للعب في أي وقت دون اتصال",
                                color = TextSecondary,
                                fontSize = 11.sp,
                                textAlign = TextAlign.End
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Room Stats Grid
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Cached questions in Room
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.White.copy(alpha = 0.05f))
                            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
                            .padding(12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "💾 Room DB",
                                fontSize = 12.sp,
                                color = EmeraldGreen,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "$cachedQuestionsCount",
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                text = "سؤال مخزن محلياً",
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                        }
                    }

                    // Pending sync records
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                if (pendingSyncCount > 0) GoldAccent.copy(alpha = 0.12f)
                                else Color.White.copy(alpha = 0.05f)
                            )
                            .border(
                                1.dp,
                                if (pendingSyncCount > 0) GoldAccent.copy(alpha = 0.4f)
                                else Color.White.copy(alpha = 0.08f),
                                RoundedCornerShape(14.dp)
                            )
                            .padding(12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "⏳ بانتظار المزامنة",
                                fontSize = 12.sp,
                                color = if (pendingSyncCount > 0) GoldAccent else TextSecondary,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "$pendingSyncCount",
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (pendingSyncCount > 0) GoldAccent else TextPrimary
                            )
                            Text(
                                text = "مباراة معلقة",
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                        }
                    }
                }

                // Sync Result Message Banner
                AnimatedVisibility(visible = lastSyncResult != null) {
                    lastSyncResult?.let { result ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (result.syncedCount > 0) EmeraldGreen.copy(alpha = 0.15f)
                                    else Color.White.copy(alpha = 0.06f)
                                )
                                .border(
                                    1.dp,
                                    if (result.syncedCount > 0) EmeraldGreen.copy(alpha = 0.4f)
                                    else Color.White.copy(alpha = 0.1f),
                                    RoundedCornerShape(12.dp)
                                )
                                .padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = result.message,
                                color = if (result.syncedCount > 0) EmeraldGreen else TextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Sync Button
                TrophyButton(
                    text = if (isSyncing) "جاري المزامنة مع الخادم... ⏳" else "مزامنة البيانات المعلقة الآن 🔄",
                    onClick = onSyncNow,
                    enabled = !isSyncing,
                    modifier = Modifier.fillMaxWidth().testTag("sync_offline_button")
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Explanatory note
                Text(
                    text = "💡 ميزة الأوفلاين تعتمد على Room Database لتخزين الأسئلة ومزامنة النقاط والكؤوس فور عودة اتصالك بالإنترنت لضمان عدم ضياع أي إنجاز كروي!",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        }
    }
}
