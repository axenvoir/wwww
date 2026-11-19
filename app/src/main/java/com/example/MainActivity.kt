package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.AppScreen
import com.example.ui.HostLobbyScreen
import com.example.ui.JoinLobbyScreen
import com.example.ui.MainMenuScreen
import com.example.ui.MatchScreen
import com.example.ui.MatchdayViewModel
import com.example.ui.MultiplayerHubScreen
import com.example.ui.QuickMatchSetupScreen
import com.example.ui.SettingsScreen
import com.example.ui.theme.MatchdayLanTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        configureImmersiveLandscapeBars()
        setContent {
            MatchdayLanTheme {
                MatchdayLanApp()
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) configureImmersiveLandscapeBars()
    }

    private fun configureImmersiveLandscapeBars() {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
    }
}

@Composable
fun MatchdayLanApp(viewModel: MatchdayViewModel = viewModel()) {
    val screen by viewModel.screen.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val matchConfig by viewModel.matchConfig.collectAsStateWithLifecycle()
    val hudState by viewModel.hudState.collectAsStateWithLifecycle()
    val lanState by viewModel.lanState.collectAsStateWithLifecycle()
    val perfTelemetry by viewModel.perfTelemetry.collectAsStateWithLifecycle()

    when (screen) {
        AppScreen.MAIN_MENU -> MainMenuScreen(
            localAddresses = lanState.localAddresses,
            onQuickMatchClick = { viewModel.navigateTo(AppScreen.QUICK_MATCH_SETUP) },
            onOfflineMultiplayerClick = { viewModel.navigateTo(AppScreen.MULTIPLAYER_HUB) },
            onHostDirectClick = { viewModel.openHostLobby() },
            onJoinDirectClick = { viewModel.openJoinLobby() },
            onSettingsClick = { viewModel.navigateTo(AppScreen.SETTINGS) }
        )

        AppScreen.QUICK_MATCH_SETUP -> QuickMatchSetupScreen(
            config = matchConfig,
            onUpdateConfig = { viewModel.updateMatchConfig(it) },
            onKickOff = { viewModel.startQuickMatch() },
            onBack = { viewModel.navigateTo(AppScreen.MAIN_MENU) }
        )

        AppScreen.MULTIPLAYER_HUB -> MultiplayerHubScreen(
            lanState = lanState,
            onHostClick = { viewModel.openHostLobby() },
            onJoinClick = { viewModel.openJoinLobby() },
            onBack = { viewModel.navigateTo(AppScreen.MAIN_MENU) }
        )

        AppScreen.HOST_LOBBY -> HostLobbyScreen(
            lanState = lanState,
            config = matchConfig,
            onUpdateConfig = { viewModel.updateMatchConfig(it) },
            onToggleReady = { viewModel.lanManager.toggleReady() },
            onStartMatch = { viewModel.startHostedLanMatch() },
            onRefreshIp = { viewModel.lanManager.refreshLocalAddresses() },
            onBack = { viewModel.navigateTo(AppScreen.MULTIPLAYER_HUB) }
        )

        AppScreen.JOIN_LOBBY -> JoinLobbyScreen(
            lanState = lanState,
            config = matchConfig,
            initialManualIp = settings.lastManualIp,
            onConnectToHost = { ip -> viewModel.connectToManualIp(ip) },
            onRescan = { viewModel.openJoinLobby() },
            onUpdateConfig = { viewModel.updateMatchConfig(it) },
            onToggleReady = { viewModel.lanManager.toggleReady() },
            onBack = { viewModel.navigateTo(AppScreen.MULTIPLAYER_HUB) }
        )

        AppScreen.SETTINGS -> SettingsScreen(
            settings = settings,
            onUpdateGraphics = { viewModel.updateGraphicsQuality(it) },
                    onUpdateGraphicsPipelineMode = { viewModel.updateGraphicsPipelineMode(it) },
            onUpdateCamera = { viewModel.updateCameraPreset(it) },
            onUpdateCameraZoom = { viewModel.updateCameraZoom(it) },
            onUpdateCameraHeight = { viewModel.updateCameraHeight(it) },
            onUpdateStadium = { viewModel.updateDefaultStadium(it) },
            onUpdateMinutes = { viewModel.updateDefaultMatchMinutes(it) },
            onUpdateDifficulty = { viewModel.updateDifficulty(it) },
            onUpdateSoundEnabled = { viewModel.updateSoundEnabled(it) },
            onUpdateShowPerfHud = { viewModel.updateShowPerfHud(it) },
            onBack = { viewModel.navigateTo(AppScreen.MAIN_MENU) }
        )

        AppScreen.ACTIVE_MATCH -> MatchScreen(
            viewModel = viewModel,
            config = matchConfig,
            settings = settings,
            hud = hudState,
            lanState = lanState,
            perfTelemetry = perfTelemetry,
            onUpdateCamera = { viewModel.updateCameraPreset(it) },
            onUpdateCameraZoom = { viewModel.updateCameraZoom(it) },
            onUpdateCameraHeight = { viewModel.updateCameraHeight(it) },
            onUpdateGraphics = { viewModel.updateGraphicsQuality(it) },
            onUpdateSoundEnabled = { viewModel.updateSoundEnabled(it) },
            onUpdateShowPerfHud = { viewModel.updateShowPerfHud(it) },
            onReconnectLan = { viewModel.reconnectLanMatch() },
            onExitToMenu = { viewModel.exitMatchToMenu() }
        )
    }
}
