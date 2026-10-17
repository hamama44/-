package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.notifications.StadiumNotificationManager
import com.example.ui.FootballGameViewModel
import com.example.ui.GameScreen
import com.example.ui.screens.*
import com.example.ui.theme.MalabAlTahaddiTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val notificationManager = StadiumNotificationManager(this)
        notificationManager.scheduleDailySabahoReminder()

        val destination = intent?.getStringExtra(StadiumNotificationManager.EXTRA_NAVIGATE_TO)

        setContent {
            MalabAlTahaddiTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        MalabAlTahaddiApp(initialDestination = destination)
                    }
                }
            }
        }
    }
}

@Composable
fun MalabAlTahaddiApp(
    viewModel: FootballGameViewModel = viewModel(),
    initialDestination: String? = null
) {
    val context = LocalContext.current
    val currentScreen by viewModel.currentScreen.collectAsState()

    // Request notification permission on Android 13+
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = {}
    )

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (initialDestination == StadiumNotificationManager.DESTINATION_SABAHO) {
            viewModel.startSabahoChallenge()
        }
    }

    // Handle Android system back button cleanly
    BackHandler(enabled = currentScreen != GameScreen.Home) {
        viewModel.navigateBack()
    }

    Crossfade(targetState = currentScreen, label = "screen_transition") { screen ->
        when (screen) {
            is GameScreen.Home -> HomeScreen(viewModel = viewModel)
            is GameScreen.ThirtySecondsSelect -> ThirtySecondsSelectScreen(viewModel = viewModel)
            is GameScreen.ThirtySecondsQuiz -> ThirtySecondsQuizScreen(viewModel = viewModel)
            is GameScreen.ThirtySecondsPlayers -> ThirtySecondsPlayersScreen(viewModel = viewModel)
            is GameScreen.Sabaho -> SabahoScreen(viewModel = viewModel)
            is GameScreen.MysteryPlayerScreen -> MysteryPlayerScreen(viewModel = viewModel)
            is GameScreen.CareerPathScreen -> CareerPathScreen(viewModel = viewModel)
            is GameScreen.TrueFalseScreen -> TrueFalseScreen(viewModel = viewModel)
            is GameScreen.EventOrderingScreen -> EventOrderingScreen(viewModel = viewModel)
            is GameScreen.LeaderboardScreen -> LeaderboardScreen(viewModel = viewModel)
            is GameScreen.ProfileScreen -> ProfileScreen(viewModel = viewModel)
            is GameScreen.PvPMatchScreen -> PvPScreen(viewModel = viewModel)
            is GameScreen.FriendInviteScreen -> FriendInviteScreen(viewModel = viewModel)
            is GameScreen.AutobusCompleteScreen -> AutobusCompleteScreen(viewModel = viewModel)
            is GameScreen.FiveSecondsScreen -> FiveSecondsScreen(viewModel = viewModel)
            is GameScreen.WouldYouRatherScreen -> WouldYouRatherScreen(viewModel = viewModel)
            is GameScreen.GeneralRiddlesScreen -> GeneralRiddlesScreen(viewModel = viewModel)
            is GameScreen.BedoonKalamScreen -> BedoonKalamScreen(viewModel = viewModel)
            is GameScreen.TabooScreen -> TabooScreen(viewModel = viewModel)
            is GameScreen.ProverbsScreen -> ProverbsScreen(viewModel = viewModel)
            is GameScreen.MillionaireScreen -> MillionaireScreen(viewModel = viewModel)
            is GameScreen.FourCluesScreen -> FourCluesScreen(viewModel = viewModel)
            is GameScreen.PassAndPlayScreen -> PassAndPlayScreen(viewModel = viewModel)
            is GameScreen.FootballTicTacToeScreen -> FootballTicTacToeScreen(viewModel = viewModel)
            is GameScreen.FootballAuctionScreen -> FootballAuctionScreen(viewModel = viewModel)
            is GameScreen.MissingXIScreen -> MissingXIScreen(viewModel = viewModel)
            is GameScreen.OddOneOutScreen -> OddOneOutScreen(viewModel = viewModel)
            is GameScreen.JerseyAndTransfersScreen -> JerseyAndTransfersScreen(viewModel = viewModel)
            is GameScreen.SpyGameScreen -> SpyGameScreen(viewModel = viewModel)
            is GameScreen.TruthOrDareScreen -> TruthOrDareScreen(viewModel = viewModel)
            is GameScreen.ThirtySecondsRoundsScreen -> ThirtySecondsRoundsScreen(viewModel = viewModel)
            is GameScreen.SupabaseOnlineScreen -> SupabaseOnlineScreen(viewModel = viewModel)
            is GameScreen.RoomMatch -> RoomMatchScreen(viewModel = viewModel)
        }
    }
}
