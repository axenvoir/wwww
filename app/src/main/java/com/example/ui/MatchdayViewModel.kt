package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.game.audio.MatchAudioEngine
import com.example.game.engine.FootballSimulation
import com.example.game.engine.PerfTelemetry
import com.example.game.engine.PerformanceMonitor
import com.example.game.engine.PitchConstants
import com.example.game.engine.RenderWorldSnapshot
import com.example.game.model.AiDifficulty
import com.example.game.model.CameraPreset
import com.example.game.model.GraphicsQuality
import com.example.game.model.GraphicsPipelineMode
import com.example.game.model.MatchConfig
import com.example.game.model.MatchHudSnapshot
import com.example.game.model.PlayerInput
import com.example.game.model.SettingsRepository
import com.example.game.model.StadiumPreset
import com.example.game.model.TeamSide
import com.example.game.model.UserSettings
import com.example.game.network.LanConnectionState
import com.example.game.network.LanLobbyUiState
import com.example.game.network.LanMultiplayerManager
import com.example.game.network.LanRole
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AppScreen {
    MAIN_MENU,
    QUICK_MATCH_SETUP,
    MULTIPLAYER_HUB,
    HOST_LOBBY,
    JOIN_LOBBY,
    SETTINGS,
    ACTIVE_MATCH
}

class MatchdayViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsRepo = SettingsRepository(application)
    private val audioEngine = MatchAudioEngine()
    private val perfMonitor = PerformanceMonitor()

    private val _screen = MutableStateFlow(AppScreen.MAIN_MENU)
    val screen: StateFlow<AppScreen> = _screen.asStateFlow()

    private val _settings = MutableStateFlow(UserSettings())
    val settings: StateFlow<UserSettings> = _settings.asStateFlow()

    private val _matchConfig = MutableStateFlow(MatchConfig())
    val matchConfig: StateFlow<MatchConfig> = _matchConfig.asStateFlow()

    private val _hudState = MutableStateFlow(MatchHudSnapshot())
    val hudState: StateFlow<MatchHudSnapshot> = _hudState.asStateFlow()

    private val _perfTelemetry = MutableStateFlow(PerfTelemetry())
    val perfTelemetry: StateFlow<PerfTelemetry> = _perfTelemetry.asStateFlow()

    val simulation = FootballSimulation(
        onSoundEvent = { event -> audioEngine.play(event) }
    )

    val lanManager = LanMultiplayerManager(
        context = application,
        onLobbyConfigSynced = { synced ->
            _matchConfig.update { current ->
                synced.copy(
                    graphicsQuality = current.graphicsQuality,
                    cameraPreset = current.cameraPreset,
                    cameraZoom = current.cameraZoom,
                    cameraHeight = current.cameraHeight,
                    difficulty = current.difficulty
                )
            }
        },
        onMatchStartRequested = { cfg ->
            val merged = cfg.copy(
                graphicsQuality = _settings.value.graphicsQuality,
                cameraPreset = _settings.value.cameraPreset,
                cameraZoom = _settings.value.cameraZoom,
                cameraHeight = _settings.value.cameraHeight
            )
            startMatchInternal(merged)
        },
        onRemotePauseChanged = { paused ->
            simulation.isPaused = paused
            _hudState.value = simulation.captureHudSnapshot()
        },
        onRematchRequested = {
            simulation.resetMatch(_matchConfig.value)
            _hudState.value = simulation.captureHudSnapshot()
        }
    )

    val lanState: StateFlow<LanLobbyUiState> = lanManager.state

    // Real-time touch input state
    private val inputLock = Any()
    private var joyX = 0f
    private var joyY = 0f
    private var sprintHeld = false
    private var passHeld = false
    private var passCharge = 0f
    private var pendingPassRelease = false
    private var pendingPassPower = 0.35f
    private var shootHeld = false
    private var shootCharge = 0f
    private var pendingShootRelease = false
    private var pendingShootPower = 0.55f
    private var pendingThrough = false
    private var pendingTackle = false
    private var pendingSwitch = false

    private var simAccumulator = 0f
    private var hudRefreshAccumulator = 0f
    private var inputSeq = 0

    init {
        lanManager.refreshLocalAddresses()
        viewModelScope.launch {
            settingsRepo.settingsFlow.collect { userSettings ->
                _settings.value = userSettings
                audioEngine.isEnabled = userSettings.soundEnabled
                _matchConfig.update { cfg ->
                    cfg.copy(
                        stadium = if (_screen.value == AppScreen.MAIN_MENU) userSettings.stadium else cfg.stadium,
                        graphicsQuality = userSettings.graphicsQuality,
                        cameraPreset = userSettings.cameraPreset,
                        cameraZoom = userSettings.cameraZoom,
                        cameraHeight = userSettings.cameraHeight,
                        matchMinutes = if (_screen.value == AppScreen.MAIN_MENU) userSettings.matchMinutes else cfg.matchMinutes,
                        difficulty = userSettings.difficulty
                    )
                }
                simulation.updateVisualConfig(
                    graphicsQuality = userSettings.graphicsQuality,
                    cameraPreset = userSettings.cameraPreset,
                    cameraZoom = userSettings.cameraZoom,
                    cameraHeight = userSettings.cameraHeight
                )
            }
        }
    }

    fun navigateTo(target: AppScreen) {
        if (target == AppScreen.MAIN_MENU) {
            lanManager.stopAll()
        } else if (target == AppScreen.MULTIPLAYER_HUB) {
            lanManager.stopAll()
            lanManager.refreshLocalAddresses()
        }
        _screen.value = target
    }

    fun openHostLobby() {
        val cfg = _matchConfig.value.copy(
            isLanMultiplayer = true,
            localControlledSide = TeamSide.HOME,
            stadium = _settings.value.stadium,
            matchMinutes = _settings.value.matchMinutes
        )
        _matchConfig.value = cfg
        lanManager.startHosting(cfg)
        _screen.value = AppScreen.HOST_LOBBY
    }

    fun openJoinLobby() {
        lanManager.startScanningForHosts()
        _screen.value = AppScreen.JOIN_LOBBY
    }

    fun connectToManualIp(ip: String) {
        viewModelScope.launch { settingsRepo.updateLastManualIp(ip) }
        lanManager.connectToHost(ip)
    }

    fun reconnectLanMatch() {
        lanManager.reconnectLastHost()
    }

    fun updateMatchConfig(transform: (MatchConfig) -> MatchConfig) {
        val updated = transform(_matchConfig.value)
        _matchConfig.value = updated
        val lan = lanState.value
        if (lan.role == LanRole.HOST && lan.connectionState == LanConnectionState.CONNECTED) {
            lanManager.updateHostLobbyConfig(updated)
        } else if (lan.role == LanRole.CLIENT && lan.connectionState == LanConnectionState.CONNECTED) {
            lanManager.updateClientTeamSelection(updated.awayTeamId, updated.awayFormation)
        }
    }

    fun updateGraphicsQuality(quality: GraphicsQuality) {
        viewModelScope.launch { settingsRepo.updateGraphicsQuality(quality) }
        _matchConfig.update { it.copy(graphicsQuality = quality) }
        simulation.updateVisualConfig(graphicsQuality = quality)
    }
    fun updateGraphicsPipelineMode(mode: GraphicsPipelineMode) {
        viewModelScope.launch { settingsRepo.updateGraphicsPipelineMode(mode) }
    }
    
    fun updateCameraPreset(preset: CameraPreset) {
        viewModelScope.launch { settingsRepo.updateCameraPreset(preset) }
        _matchConfig.update { it.copy(cameraPreset = preset) }
        simulation.updateVisualConfig(cameraPreset = preset)
    }

    fun updateCameraZoom(zoom: Int) {
        val clamped = zoom.coerceIn(0, 10)
        viewModelScope.launch { settingsRepo.updateCameraZoom(clamped) }
        _matchConfig.update { it.copy(cameraZoom = clamped) }
        simulation.updateVisualConfig(cameraZoom = clamped)
    }

    fun updateCameraHeight(height: Int) {
        val clamped = height.coerceIn(0, 10)
        viewModelScope.launch { settingsRepo.updateCameraHeight(clamped) }
        _matchConfig.update { it.copy(cameraHeight = clamped) }
        simulation.updateVisualConfig(cameraHeight = clamped)
    }

    fun updateDefaultStadium(stadium: StadiumPreset) {
        viewModelScope.launch { settingsRepo.updateStadium(stadium) }
        updateMatchConfig { it.copy(stadium = stadium) }
    }

    fun updateDefaultMatchMinutes(minutes: Int) {
        viewModelScope.launch { settingsRepo.updateMatchMinutes(minutes) }
        updateMatchConfig { it.copy(matchMinutes = minutes) }
    }

    fun updateDifficulty(difficulty: AiDifficulty) {
        viewModelScope.launch { settingsRepo.updateDifficulty(difficulty) }
        updateMatchConfig { it.copy(difficulty = difficulty) }
    }

    fun updateSoundEnabled(enabled: Boolean) {
        audioEngine.isEnabled = enabled
        viewModelScope.launch { settingsRepo.updateSoundEnabled(enabled) }
    }

    fun updateShowPerfHud(show: Boolean) {
        viewModelScope.launch { settingsRepo.updateShowPerfHud(show) }
    }

    fun startQuickMatch() {
        lanManager.stopAll()
        val cfg = _matchConfig.value.copy(
            isLanMultiplayer = false,
            localControlledSide = TeamSide.HOME,
            graphicsQuality = _settings.value.graphicsQuality,
            cameraPreset = _settings.value.cameraPreset,
            cameraZoom = _settings.value.cameraZoom,
            cameraHeight = _settings.value.cameraHeight,
            difficulty = _settings.value.difficulty
        )
        startMatchInternal(cfg)
    }

    fun startHostedLanMatch() {
        lanManager.triggerHostMatchStart()
    }

    private fun startMatchInternal(config: MatchConfig) {
        _matchConfig.value = config
        resetInputs()
        simulation.resetMatch(config)
        _hudState.value = simulation.captureHudSnapshot()
        if (config.isLanMultiplayer) {
            lanManager.startMatchUdpSync(
                simulation = simulation,
                localInputProvider = { sampleCurrentInput(consumeOneShots = true) }
            )
        }
        _screen.value = AppScreen.ACTIVE_MATCH
    }

    fun togglePause(paused: Boolean) {
        simulation.isPaused = paused
        if (_matchConfig.value.isLanMultiplayer) {
            lanManager.sendPauseToggle(paused)
        }
        _hudState.value = simulation.captureHudSnapshot()
    }

    fun triggerRematch() {
        resetInputs()
        simulation.resetMatch(_matchConfig.value)
        if (_matchConfig.value.isLanMultiplayer) {
            lanManager.sendRematchRequest()
        }
        _hudState.value = simulation.captureHudSnapshot()
    }

    fun exitMatchToMenu() {
        lanManager.stopAll()
        resetInputs()
        _screen.value = AppScreen.MAIN_MENU
    }

    // --- Real-Time Touch Controller Callbacks ---

    fun setJoystickVector(x: Float, y: Float) = synchronized(inputLock) {
        joyX = x.coerceIn(-1f, 1f)
        joyY = y.coerceIn(-1f, 1f)
    }

    fun setSprintHeld(held: Boolean) = synchronized(inputLock) {
        sprintHeld = held
    }

    fun setPassHeld(held: Boolean) = synchronized(inputLock) {
        if (held) {
            passHeld = true
        } else if (passHeld) {
            passHeld = false
            pendingPassRelease = true
            pendingPassPower = passCharge.coerceIn(0.28f, 1.0f)
            passCharge = 0f
        }
    }

    fun setShootHeld(held: Boolean) = synchronized(inputLock) {
        if (held) {
            shootHeld = true
        } else if (shootHeld) {
            shootHeld = false
            pendingShootRelease = true
            pendingShootPower = shootCharge.coerceIn(0.30f, 1.0f)
            shootCharge = 0f
        }
    }

    fun triggerThroughPass() = synchronized(inputLock) {
        pendingThrough = true
    }

    fun triggerTackle() = synchronized(inputLock) {
        pendingTackle = true
    }

    fun triggerPlayerSwitch() = synchronized(inputLock) {
        pendingSwitch = true
    }

    private fun resetInputs() = synchronized(inputLock) {
        joyX = 0f; joyY = 0f
        sprintHeld = false
        passHeld = false; passCharge = 0f; pendingPassRelease = false
        shootHeld = false; shootCharge = 0f; pendingShootRelease = false
        pendingThrough = false; pendingTackle = false; pendingSwitch = false
    }

    private fun sampleCurrentInput(consumeOneShots: Boolean): PlayerInput = synchronized(inputLock) {
        val passFired = pendingPassRelease
        val shootFired = pendingShootRelease
        val throughFired = pendingThrough
        val tackleFired = pendingTackle
        val switchFired = pendingSwitch

        val packet = PlayerInput(
            seq = ++inputSeq,
            moveX = joyX,
            moveY = joyY,
            sprint = sprintHeld,
            passPressed = passFired,
            passCharge = if (passFired) pendingPassPower else passCharge,
            throughPressed = throughFired,
            shootPressed = shootFired,
            shootCharge = if (shootFired) pendingShootPower else shootCharge,
            tacklePressed = tackleFired,
            switchPressed = switchFired
        )

        if (consumeOneShots) {
            pendingPassRelease = false
            pendingShootRelease = false
            pendingThrough = false
            pendingTackle = false
            pendingSwitch = false
        }
        packet
    }

    /**
     * Invoked at 60 FPS on the OpenGL render thread.
     * Runs fixed-timestep authoritative physics (Solo/Host) OR Stage 4 snapshot interpolation + prediction (Client),
     * and feeds Stage 6 PerformanceMonitor metrics.
     */
    fun onGlFrameTick(dt: Float, lastRenderNanos: Long) {
        val simStart = System.nanoTime()

        synchronized(inputLock) {
            if (passHeld) passCharge = (passCharge + dt * 1.45f).coerceAtMost(1.0f)
            if (shootHeld) shootCharge = (shootCharge + dt * 1.55f).coerceAtMost(1.0f)
        }

        val cfg = _matchConfig.value
        val isRemoteClient = cfg.isLanMultiplayer && cfg.localControlledSide == TeamSide.AWAY

        if (!isRemoteClient) {
            val localInput = sampleCurrentInput(consumeOneShots = true)
            simulation.setLocalInput(TeamSide.HOME, localInput)

            simAccumulator += dt.coerceAtMost(0.05f)
            var steps = 0
            while (simAccumulator >= PitchConstants.FIXED_DT && steps < 4) {
                simulation.step(PitchConstants.FIXED_DT)
                simAccumulator -= PitchConstants.FIXED_DT
                steps++
            }
        } else {
            // Client applies smooth time-aligned UDP interpolation + local input prediction & reconciliation
            val localInput = sampleCurrentInput(consumeOneShots = false)
            simulation.setLocalInput(TeamSide.AWAY, localInput)
            lanManager.applyInterpolatedFrameToClient(simulation, localInput, dt)
        }

        val simNanos = System.nanoTime() - simStart
        perfMonitor.recordFrame(dt, simNanos, lastRenderNanos)

        hudRefreshAccumulator += dt
        if (hudRefreshAccumulator >= 0.05f) { // 20 Hz UI HUD & telemetry refresh
            hudRefreshAccumulator = 0f
            _hudState.value = simulation.captureHudSnapshot()
            _perfTelemetry.value = perfMonitor.snapshot()
        }
    }

    fun captureRenderSnapshot(): RenderWorldSnapshot = simulation.captureRenderSnapshot()

    override fun onCleared() {
        super.onCleared()
        lanManager.stopAll()
        audioEngine.release()
    }
}
