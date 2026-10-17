package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.NewGamesDataProvider
import com.example.data.TicTacToeGridGame
import com.example.ui.FootballGameViewModel
import com.example.ui.components.EmeraldButton
import com.example.ui.components.GlowCard
import com.example.ui.components.StadiumTopBar
import com.example.ui.components.VoiceMicButton
import com.example.ui.theme.*
import kotlinx.coroutines.launch

enum class TicTacToePlayer(val displayName: String, val badgeColor: Color, val symbol: String) {
    PLAYER_X("الفريق الأحمر 🔴", Color(0xFFFF1744), "🔴"),
    PLAYER_O("الفريق الأزرق 🔵", Color(0xFF2979FF), "🔵")
}

data class GridCellState(
    val owner: TicTacToePlayer? = null,
    val answeredPlayerName: String? = null
)

@Composable
fun FootballTicTacToeScreen(viewModel: FootballGameViewModel) {
    val userProfile by viewModel.userProfile.collectAsState()
    val scope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current

    val grids = remember { NewGamesDataProvider.ticTacToeGrids }
    var currentGridIndex by remember { mutableIntStateOf(0) }
    val currentGrid = grids[currentGridIndex % grids.size]

    // 3x3 board states
    var board by remember(currentGridIndex) {
        mutableStateOf(List(9) { GridCellState() })
    }
    var activeTurn by remember(currentGridIndex) {
        mutableStateOf(TicTacToePlayer.PLAYER_X)
    }
    var winner by remember(currentGridIndex) {
        mutableStateOf<TicTacToePlayer?>(null)
    }
    var isDraw by remember(currentGridIndex) {
        mutableStateOf(false)
    }

    // Modal state for selecting / entering a player for a specific cell
    var selectedCellIndex by remember { mutableStateOf<Int?>(null) }
    var inputPlayerName by remember { mutableStateOf("") }
    var validationError by remember { mutableStateOf<String?>(null) }
    var showHintDialog by remember { mutableStateOf(false) }

    fun checkWinner(currentBoard: List<GridCellState>): TicTacToePlayer? {
        val winPatterns = listOf(
            listOf(0, 1, 2), listOf(3, 4, 5), listOf(6, 7, 8), // rows
            listOf(0, 3, 6), listOf(1, 4, 7), listOf(2, 5, 8), // cols
            listOf(0, 4, 8), listOf(2, 4, 6)             // diagonals
        )
        for (pattern in winPatterns) {
            val (a, b, c) = pattern
            val ownerA = currentBoard[a].owner
            if (ownerA != null && ownerA == currentBoard[b].owner && ownerA == currentBoard[c].owner) {
                return ownerA
            }
        }
        return null
    }

    fun submitAnswer(cellIdx: Int, answer: String) {
        val cleaned = answer.trim()
        if (cleaned.isBlank()) return

        val row = cellIdx / 3
        val col = cellIdx % 3
        val validList = currentGrid.validPlayersMatrix[row][col]

        // Check if matching valid player
        val isMatch = validList.any { validName: String ->
            cleaned.lowercase().contains(validName.lowercase()) ||
                    validName.lowercase().contains(cleaned.lowercase())
        }

        if (isMatch) {
            viewModel.audioEffects.playCorrect()
            val newBoard = board.toMutableList()
            newBoard[cellIdx] = GridCellState(
                owner = activeTurn,
                answeredPlayerName = cleaned
            )
            board = newBoard
            selectedCellIndex = null
            inputPlayerName = ""
            validationError = null
            keyboardController?.hide()

            // Check win condition
            val won = checkWinner(newBoard)
            if (won != null) {
                winner = won
                viewModel.audioEffects.playVictory()
                viewModel.repository.addPointsAndCoins(100, 40)
                viewModel.recordPartyGameScore("تيك تاك تو كروي", "football", 100, "فوز ${won.displayName} بالشبكة الكروية ⚽")
            } else if (newBoard.all { it.owner != null }) {
                isDraw = true
                viewModel.audioEffects.playWhistle()
            } else {
                // Switch turn
                activeTurn = if (activeTurn == TicTacToePlayer.PLAYER_X) TicTacToePlayer.PLAYER_O else TicTacToePlayer.PLAYER_X
            }
        } else {
            viewModel.audioEffects.playWrong()
            validationError = "اللاعب لم يلعب لكلا الفريقين أو غير مسجل في هذه الخانة! حاول مرة أخرى ❌"
        }
    }

    Scaffold(
        containerColor = PitchDark,
        topBar = {
            StadiumTopBar(
                title = "تيك تاك تو كروي ⚽",
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
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header Info & Status
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = currentGrid.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "سمّ لاعباً يربط بين الصف والعمود!",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }

                IconButton(
                    onClick = {
                        viewModel.audioEffects.playClick()
                        currentGridIndex = (currentGridIndex + 1) % grids.size
                    },
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(PitchCard)
                        .border(1.dp, PitchDivider, CircleShape)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "تغيير الشبكة", tint = TrophyGold)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Turn Indicator Banner
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (winner != null) TrophyGold.copy(alpha = 0.2f)
                    else activeTurn.badgeColor.copy(alpha = 0.15f)
                ),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (winner != null) TrophyGold else activeTurn.badgeColor
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    if (winner != null) {
                        Text(
                            text = "🎉 الفائز: ${winner?.displayName}!",
                            style = MaterialTheme.typography.titleMedium,
                            color = TrophyGold,
                            fontWeight = FontWeight.Bold
                        )
                        Button(
                            onClick = {
                                board = List(9) { GridCellState() }
                                winner = null
                                isDraw = false
                                activeTurn = TicTacToePlayer.PLAYER_X
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = TrophyGold),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text("جولة جديدة 🔄", color = PitchDark, fontWeight = FontWeight.Bold)
                        }
                    } else if (isDraw) {
                        Text(
                            text = "🤝 تعادل تكتيكي ناري بين الفريقين!",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        Button(
                            onClick = {
                                board = List(9) { GridCellState() }
                                isDraw = false
                                activeTurn = TicTacToePlayer.PLAYER_X
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldNeon),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text("إعادة 🔄", color = PitchDark, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = activeTurn.symbol, fontSize = 20.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "الدور الآن: ${activeTurn.displayName}",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = "اختر خانة للإجابة",
                            style = MaterialTheme.typography.bodySmall,
                            color = activeTurn.badgeColor,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // -----------------------------------------------------------------
            // THE 3x3 GRID WITH ROW & COL LABELS
            // -----------------------------------------------------------------
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(PitchCard)
                    .border(1.5.dp, EmeraldNeon.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                    .padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Column Header Labels (Top Row)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Empty Top-Left corner
                    Box(
                        modifier = Modifier
                            .weight(0.9f)
                            .height(48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "⚽", fontSize = 20.sp)
                    }

                    // 3 Columns
                    currentGrid.colLabels.forEach { (colName, colIcon) ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .padding(2.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF004D40).copy(alpha = 0.5f))
                                .border(1.dp, StadiumCyan.copy(alpha = 0.3f), RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(text = colIcon, fontSize = 14.sp)
                                Text(
                                    text = colName,
                                    fontSize = 11.sp,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // 3 Rows with Row Label at start
                for (r in 0..2) {
                    val (rowName, rowIcon) = currentGrid.rowLabels[r]
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Row Header Label (Left)
                        Box(
                            modifier = Modifier
                                .weight(0.9f)
                                .height(72.dp)
                                .padding(2.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF1A237E).copy(alpha = 0.5f))
                                .border(1.dp, Color(0xFF7C4DFF).copy(alpha = 0.3f), RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(text = rowIcon, fontSize = 16.sp)
                                Text(
                                    text = rowName,
                                    fontSize = 11.sp,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                    maxLines = 2
                                )
                            }
                        }

                        // 3 Grid Cells
                        for (c in 0..2) {
                            val cellIdx = r * 3 + c
                            val cellState = board[cellIdx]
                            val isClaimed = cellState.owner != null

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(72.dp)
                                    .padding(3.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        when (cellState.owner) {
                                            TicTacToePlayer.PLAYER_X -> Color(0xFFFF1744).copy(alpha = 0.25f)
                                            TicTacToePlayer.PLAYER_O -> Color(0xFF2979FF).copy(alpha = 0.25f)
                                            null -> PitchDark
                                        }
                                    )
                                    .border(
                                        1.5.dp,
                                        when (cellState.owner) {
                                            TicTacToePlayer.PLAYER_X -> Color(0xFFFF1744)
                                            TicTacToePlayer.PLAYER_O -> Color(0xFF2979FF)
                                            null -> PitchDivider
                                        },
                                        RoundedCornerShape(10.dp)
                                    )
                                    .clickable(enabled = !isClaimed && winner == null && !isDraw) {
                                        viewModel.audioEffects.playClick()
                                        selectedCellIndex = cellIdx
                                        inputPlayerName = ""
                                        validationError = null
                                    }
                                    .testTag("cell_$cellIdx"),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isClaimed) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier.padding(2.dp)
                                    ) {
                                        Text(text = cellState.owner?.symbol ?: "", fontSize = 20.sp)
                                        Text(
                                            text = cellState.answeredPlayerName ?: "",
                                            fontSize = 10.sp,
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            textAlign = TextAlign.Center,
                                            maxLines = 2
                                        )
                                    }
                                } else {
                                    Text(
                                        text = "+",
                                        fontSize = 26.sp,
                                        color = EmeraldNeon.copy(alpha = 0.4f),
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Game instructions and hint button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "💡 3 خانات متصلة تفوز بالمباراة!",
                    style = MaterialTheme.typography.bodySmall,
                    color = TrophyGold
                )

                TextButton(
                    onClick = { showHintDialog = true }
                ) {
                    Text("أمثلة وتلميحات 🔍", color = StadiumCyan)
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // INPUT DIALOG FOR CONQUERING A CELL
    // -------------------------------------------------------------------------
    selectedCellIndex?.let { cellIdx ->
        val row = cellIdx / 3
        val col = cellIdx % 3
        val rowInfo = currentGrid.rowLabels[row]
        val colInfo = currentGrid.colLabels[col]

        Dialog(onDismissRequest = { selectedCellIndex = null }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = PitchCard),
                border = androidx.compose.foundation.BorderStroke(2.dp, activeTurn.badgeColor)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "تسجيل لاعب في الخانة ⚽",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(onClick = { selectedCellIndex = null }) {
                            Icon(Icons.Default.Close, contentDescription = "إلغاء", tint = TextSecondary)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Criteria Cards
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Card(
                            modifier = Modifier.weight(1f),
                            colors = CardDefaults.cardColors(containerColor = PitchDark),
                            border = androidx.compose.foundation.BorderStroke(1.dp, PitchDivider)
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(text = rowInfo.second, fontSize = 24.sp)
                                Text(
                                    text = rowInfo.first,
                                    fontSize = 13.sp,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterVertically)
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(TrophyGold),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = "❌", fontSize = 14.sp)
                        }

                        Card(
                            modifier = Modifier.weight(1f),
                            colors = CardDefaults.cardColors(containerColor = PitchDark),
                            border = androidx.compose.foundation.BorderStroke(1.dp, PitchDivider)
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(text = colInfo.second, fontSize = 24.sp)
                                Text(
                                    text = colInfo.first,
                                    fontSize = 13.sp,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "اذكر نجماً لعب لكلا الطرفين (${rowInfo.first} و ${colInfo.first})",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Text Input & Voice Input
                    OutlinedTextField(
                        value = inputPlayerName,
                        onValueChange = {
                            inputPlayerName = it
                            validationError = null
                        },
                        placeholder = { Text("اسم اللاعب (مثل: كريستيانو رونالدو)", fontSize = 12.sp) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_player_tictactoe"),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = activeTurn.badgeColor,
                            unfocusedBorderColor = PitchDivider,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        trailingIcon = {
                            // Voice Mic Button for speech input
                            VoiceMicButton(
                                viewModel = viewModel,
                                isMini = true,
                                onSpeechResult = { spokenText ->
                                    inputPlayerName = spokenText
                                    submitAnswer(cellIdx, spokenText)
                                }
                            )
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            submitAnswer(cellIdx, inputPlayerName)
                        })
                    )

                    validationError?.let { err ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = err,
                            color = Color(0xFFFF5252),
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = { submitAnswer(cellIdx, inputPlayerName) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = activeTurn.badgeColor),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "تأكيد الإجابة وحجز الخانة ✓",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }
                }
            }
        }
    }

    // Hint Dialog
    if (showHintDialog) {
        Dialog(onDismissRequest = { showHintDialog = false }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = PitchCard),
                border = androidx.compose.foundation.BorderStroke(1.dp, StadiumCyan)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "كيف تلعب التيك تاك تو الكروي؟ ⚽",
                        style = MaterialTheme.typography.titleMedium,
                        color = StadiumCyan,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "• يلعب الفريقان (الأحمر 🔴 والأزرق 🔵) بالتبادل.\n" +
                                "• لكل خانة: يجب كتابة أو نطق اسم لاعب ارتدى قميص النادي في الصف والعمود معاً (أو مثّل المنتخب والنادي).\n" +
                                "• أول من يكوّن خطاً مستقيماً من 3 خانات (أفقي، عمودي، أو قطري) يفوز باللقب وجوائز النقاط!",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White,
                        lineHeight = 20.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    EmeraldButton(
                        text = "فهمت! لنلعب",
                        onClick = { showHintDialog = false },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}
