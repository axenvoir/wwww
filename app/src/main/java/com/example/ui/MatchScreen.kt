package com.example.ui

import android.opengl.GLSurfaceView
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.R
import com.example.game.engine.PerfTelemetry
import com.example.game.model.CameraPreset
import com.example.game.model.GraphicsQuality
import com.example.game.model.MatchConfig
import com.example.game.model.MatchHudSnapshot
import com.example.game.model.MatchPhase
import com.example.game.model.UserSettings
import com.example.game.network.LanConnectionState
import com.example.game.network.LanLobbyUiState
import com.example.game.network.LanRole
import com.example.game.render.FootballGLRenderer
import com.example.ui.theme.CardSurface
import com.example.ui.theme.ChampionshipGold
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.GlassPanel
import com.example.ui.theme.PitchEmerald
import com.example.ui.theme.StadiumSlate
import com.example.ui.theme.TacticalCyan
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TunnelNavy
import java.util.Locale
import kotlin.math.hypot

@Composable
fun MatchScreen(
    viewModel: MatchdayViewModel,
    config: MatchConfig,
    settings: UserSettings,
    hud: MatchHudSnapshot,
    lanState: LanLobbyUiState,
    perfTelemetry: PerfTelemetry,
    onUpdateCamera: (CameraPreset) -> Unit,
    onUpdateCameraZoom: (Int) -> Unit,
    onUpdateCameraHeight: (Int) -> Unit,
    onUpdateGraphics: (GraphicsQuality) -> Unit,
    onUpdateSoundEnabled: (Boolean) -> Unit,
    onUpdateShowPerfHud: (Boolean) -> Unit,
    onReconnectLan: () -> Unit,
    onExitToMenu: () -> Unit
) {
    BackHandler {
        if (hud.phase != MatchPhase.FULL_TIME) {
            viewModel.togglePause(!hud.isPaused)
        } else {
            onExitToMenu()
        }
    }

    val context = LocalContext.current
    val glSurfaceView = remember {
        GLSurfaceView(context).apply {
            setEGLContextClientVersion(3)
            preserveEGLContextOnPause = true
            setRenderer(
                FootballGLRenderer(
                    context = context.applicationContext,
                    snapshotProvider = { viewModel.captureRenderSnapshot() },
                    onFrameTick = { dt, renderNanos -> viewModel.onGlFrameTick(dt, renderNanos) }
                )
            )
            renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
        }
    }

    DisposableEffect(glSurfaceView) {
        glSurfaceView.onResume()
        onDispose { glSurfaceView.onPause() }
    }

    Box(modifier = Modifier.fillMaxSize().background(StadiumSlate)) {
        // 1. Live 60 FPS Native OpenGL ES 3.0 3D Viewport
        AndroidView(
            factory = { glSurfaceView },
            modifier = Modifier.fillMaxSize()
        )

        // 2. Broadcast HUD Overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            // Top-Left Broadcast Scorebug + Optional Stage 6 Perf/LAN Telemetry Strip
            Column(
                modifier = Modifier.align(Alignment.TopStart),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                BroadcastScorebug(
                    config = config,
                    hud = hud,
                    lanState = lanState
                )
                if (settings.showPerfHud) {
                    PerformanceTelemetryBadge(
                        perf = perfTelemetry,
                        config = config,
                        lanState = lanState
                    )
                }
            }

            // Top-Right Pause Button + Controlled Player Tag
            Row(
                modifier = Modifier.align(Alignment.TopEnd),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = GlassPanel,
                    border = BorderStroke(1.dp, GlassBorder)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalAlignment = Alignment.End
                    ) {
                        Text(
                            text = "#${hud.activePlayerNumber} ${hud.activePlayerName.uppercase()}",
                            style = MaterialTheme.typography.labelMedium,
                            color = TextPrimary
                        )
                        Spacer(Modifier.height(3.dp))
                        LinearProgressIndicator(
                            progress = { hud.activePlayerStamina.coerceIn(0f, 1f) },
                            color = if (hud.activePlayerStamina > 0.3f) PitchEmerald else CrimsonAlert,
                            trackColor = Color(0x44FFFFFF),
                            modifier = Modifier.width(86.dp).height(4.dp).clip(RoundedCornerShape(4.dp))
                        )
                    }
                }

                Surface(
                    shape = CircleShape,
                    color = GlassPanel,
                    border = BorderStroke(1.dp, GlassBorder)
                ) {
                    IconButton(
                        onClick = { viewModel.togglePause(true) },
                        modifier = Modifier.size(44.dp).testTag("hud_pause_button")
                    ) {
                        Icon(Icons.Filled.Pause, contentDescription = "Pause Match", tint = TextPrimary)
                    }
                }
            }

            // Center-Top Goal / Set-Piece Broadcast Banner
            AnimatedVisibility(
                visible = hud.phase != MatchPhase.PLAYING && hud.phase != MatchPhase.FULL_TIME,
                enter = fadeIn() + scaleIn(initialScale = 0.85f),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (hud.phase == MatchPhase.GOAL_CELEBRATION) PitchEmerald else GlassPanel,
                    border = BorderStroke(1.5.dp, if (hud.phase == MatchPhase.GOAL_CELEBRATION) ChampionshipGold else PitchEmerald)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = hud.phase.bannerTitle,
                            style = MaterialTheme.typography.headlineMedium,
                            color = if (hud.phase == MatchPhase.GOAL_CELEBRATION) StadiumSlate else TextPrimary
                        )
                        if (hud.bannerSubtitle.isNotBlank()) {
                            Text(
                                text = hud.bannerSubtitle,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (hud.phase == MatchPhase.GOAL_CELEBRATION) StadiumSlate else ChampionshipGold
                            )
                        }
                    }
                }
            }

            // Bottom-Left: Multi-Touch Virtual Joystick
            VirtualJoystickControl(
                onVectorChanged = { x, y -> viewModel.setJoystickVector(x, y) },
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 10.dp, bottom = 8.dp)
            )

            // Bottom-Center: Live 2D Tactical Pitch Radar
            TacticalRadarMinimap(
                config = config,
                hud = hud,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 6.dp)
            )

            // Bottom-Right: Context-Aware Attack/Defense Control Arc
            ActionButtonsCluster(
                hud = hud,
                onSprintChanged = { viewModel.setSprintHeld(it) },
                onPassChanged = { viewModel.setPassHeld(it) },
                onShootChanged = { viewModel.setShootHeld(it) },
                onThroughTap = { viewModel.triggerThroughPass() },
                onTackleTap = { viewModel.triggerTackle() },
                onSwitchTap = { viewModel.triggerPlayerSwitch() },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 6.dp, bottom = 6.dp)
            )
        }

        // 3. Pause Menu Overlay
        if (hud.isPaused && hud.phase != MatchPhase.FULL_TIME &&
            (!config.isLanMultiplayer || lanState.connectionState == LanConnectionState.IN_MATCH)
        ) {
            PauseMenuOverlay(
                config = config,
                settings = settings,
                onResume = { viewModel.togglePause(false) },
                onUpdateCamera = onUpdateCamera,
                onUpdateCameraZoom = onUpdateCameraZoom,
                onUpdateCameraHeight = onUpdateCameraHeight,
                onUpdateGraphics = onUpdateGraphics,
                onUpdateSoundEnabled = onUpdateSoundEnabled,
                onUpdateShowPerfHud = onUpdateShowPerfHud,
                onRematch = { viewModel.triggerRematch() },
                onExit = onExitToMenu
            )
        }

        // 4. Safe LAN Disconnect & Mid-Match Reconnection Overlay
        if (config.isLanMultiplayer && (
                lanState.connectionState == LanConnectionState.DISCONNECTED ||
                    lanState.connectionState == LanConnectionState.RECONNECTING
                )
        ) {
            ReconnectOverlay(
                lanState = lanState,
                onReconnect = onReconnectLan,
                onReturnToMenu = onExitToMenu
            )
        }

        // 5. Full-Time Results Overlay
        if (hud.phase == MatchPhase.FULL_TIME) {
            FullTimeOverlay(
                config = config,
                hud = hud,
                onRematch = { viewModel.triggerRematch() },
                onExitToMenu = onExitToMenu
            )
        }
    }
}

@Composable
private fun PerformanceTelemetryBadge(
    perf: PerfTelemetry,
    config: MatchConfig,
    lanState: LanLobbyUiState
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color(0xC80B111A),
        border = BorderStroke(1.dp, TacticalCyan.copy(alpha = 0.55f))
    ) {
        val simMs = String.format(Locale.US, "%.1f", perf.simCpuMs)
        val glMs = String.format(Locale.US, "%.1f", perf.renderSubmitMs)
        val lanStr = if (config.isLanMultiplayer) {
            " • PING ${lanState.pingMs}ms JIT ${lanState.jitterMs}ms LOSS ${lanState.packetLossPct}%"
        } else ""
        Text(
            text = "${perf.fps} FPS • SIM ${simMs}ms • GL ${glMs}ms • RAM ${perf.usedMemoryMb}MB$lanStr",
            style = MaterialTheme.typography.labelSmall,
            color = TacticalCyan,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun BroadcastScorebug(
    config: MatchConfig,
    hud: MatchHudSnapshot,
    lanState: LanLobbyUiState,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = GlassPanel,
        border = BorderStroke(1.dp, GlassBorder),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(24.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(config.homeTeam.kit.composePrimary())
            )
            Text(config.homeTeam.shortCode, style = MaterialTheme.typography.titleMedium, color = TextPrimary)

            Surface(shape = RoundedCornerShape(6.dp), color = PitchEmerald) {
                Text(
                    text = "${hud.homeScore} - ${hud.awayScore}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = StadiumSlate,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }

            Text(config.awayTeam.shortCode, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(24.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(config.awayTeam.kit.composePrimary())
            )

            val minStr = hud.matchClockMinutes.toString().padStart(2, '0')
            val secStr = hud.matchClockSeconds.toString().padStart(2, '0')
            Text(
                text = "${if (hud.half == 1) "1H" else "2H"} $minStr:$secStr",
                style = MaterialTheme.typography.labelLarge,
                color = ChampionshipGold
            )

            if (config.isLanMultiplayer) {
                Text(
                    text = "LAN ${lanState.pingMs}ms",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (lanState.pingMs < 45) PitchEmerald else ChampionshipGold
                )
            }
        }
    }
}

@Composable
private fun VirtualJoystickControl(
    onVectorChanged: (Float, Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var knobOffset by remember { mutableStateOf(Offset.Zero) }
    val sizeDp = 136.dp

    Box(
        modifier = modifier
            .size(sizeDp)
            .testTag("virtual_joystick")
            .pointerInput(Unit) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val maxRadius = size.width * 0.38f
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    fun updatePos(pos: Offset) {
                        val dx = pos.x - center.x
                        val dy = pos.y - center.y
                        val dist = hypot(dx, dy)
                        val clamped = if (dist > maxRadius) {
                            Offset((dx / dist) * maxRadius, (dy / dist) * maxRadius)
                        } else {
                            Offset(dx, dy)
                        }
                        knobOffset = clamped
                        onVectorChanged(clamped.x / maxRadius, clamped.y / maxRadius)
                    }
                    updatePos(down.position)
                    do {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id }
                        if (change != null && change.pressed) {
                            updatePos(change.position)
                        } else {
                            break
                        }
                    } while (true)
                    knobOffset = Offset.Zero
                    onVectorChanged(0f, 0f)
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val c = Offset(size.width / 2f, size.height / 2f)
            val outerR = size.width * 0.45f
            drawCircle(Color(0x550B111A), radius = outerR, center = c)
            drawCircle(Color(0x66FFFFFF), radius = outerR, center = c, style = Stroke(2.5f))
            drawCircle(Color(0x28FFFFFF), radius = outerR * 0.5f, center = c, style = Stroke(1.5f))
            val puckCenter = c + knobOffset
            drawCircle(PitchEmerald.copy(alpha = 0.88f), radius = size.width * 0.19f, center = puckCenter)
            drawCircle(Color.White.copy(alpha = 0.9f), radius = size.width * 0.19f, center = puckCenter, style = Stroke(2.5f))
        }
    }
}

@Composable
private fun ActionButtonsCluster(
    hud: MatchHudSnapshot,
    onSprintChanged: (Boolean) -> Unit,
    onPassChanged: (Boolean) -> Unit,
    onShootChanged: (Boolean) -> Unit,
    onThroughTap: () -> Unit,
    onTackleTap: () -> Unit,
    onSwitchTap: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hasBall = hud.localTeamHasBall

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            TactileActionButton(
                label = stringResource(R.string.hud_switch),
                accent = TacticalCyan,
                size = 54.dp,
                charge = 0f,
                tag = "btn_switch",
                onPressChanged = { pressed -> if (pressed) onSwitchTap() }
            )
            TactileActionButton(
                label = stringResource(R.string.hud_tackle),
                accent = CrimsonAlert,
                size = 54.dp,
                charge = 0f,
                tag = "btn_tackle",
                onPressChanged = { pressed -> if (pressed) onTackleTap() }
            )
            TactileActionButton(
                label = stringResource(R.string.hud_sprint),
                accent = ChampionshipGold,
                size = 60.dp,
                charge = 0f,
                tag = "btn_sprint",
                onPressChanged = onSprintChanged
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            TactileActionButton(
                label = if (hasBall) stringResource(R.string.hud_through) else stringResource(R.string.hud_contain),
                accent = TacticalCyan,
                size = 62.dp,
                charge = 0f,
                tag = "btn_through",
                onPressChanged = { pressed -> if (pressed) onThroughTap() }
            )
            TactileActionButton(
                label = if (hasBall) stringResource(R.string.hud_pass) else stringResource(R.string.hud_press),
                accent = PitchEmerald,
                size = 68.dp,
                charge = hud.passCharge,
                tag = "btn_pass",
                onPressChanged = onPassChanged
            )
            TactileActionButton(
                label = if (hasBall) stringResource(R.string.hud_shoot) else stringResource(R.string.hud_clear),
                accent = if (hud.shootCharge > 0.72f) CrimsonAlert else ChampionshipGold,
                size = 72.dp,
                charge = hud.shootCharge,
                tag = "btn_shoot",
                onPressChanged = onShootChanged
            )
        }
    }
}

@Composable
private fun TactileActionButton(
    label: String,
    accent: Color,
    size: Dp,
    charge: Float,
    tag: String,
    onPressChanged: (Boolean) -> Unit
) {
    var pressed by remember { mutableStateOf(false) }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(size)
            .testTag(tag)
            .clip(CircleShape)
            .background(if (pressed) accent.copy(alpha = 0.42f) else Color(0x990B111A))
            .border(2.dp, if (pressed) Color.White else accent.copy(alpha = 0.85f), CircleShape)
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    pressed = true
                    onPressChanged(true)
                    waitForUpOrCancellation()
                    pressed = false
                    onPressChanged(false)
                }
            }
    ) {
        if (charge > 0.02f) {
            Canvas(modifier = Modifier.fillMaxSize().padding(3.dp)) {
                drawArc(
                    color = if (charge > 0.75f) CrimsonAlert else PitchEmerald,
                    startAngle = -90f,
                    sweepAngle = 360f * charge.coerceIn(0f, 1f),
                    useCenter = false,
                    style = Stroke(width = 6f, cap = StrokeCap.Round)
                )
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
            maxLines = 1
        )
    }
}

@Composable
private fun TacticalRadarMinimap(
    config: MatchConfig,
    hud: MatchHudSnapshot,
    modifier: Modifier = Modifier
) {
    val homeCol = config.homeTeam.kit.composePrimary()
    val awayCol = config.awayTeam.kit.composePrimary()

    Canvas(
        modifier = modifier
            .width(156.dp)
            .height(92.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xAA0C2E1B))
            .border(1.dp, Color(0x66FFFFFF), RoundedCornerShape(8.dp))
    ) {
        val w = size.width
        val h = size.height
        val line = Color.White.copy(alpha = 0.35f)
        drawRect(line, Offset(4f, 4f), Size(w - 8f, h - 8f), style = Stroke(1.5f))
        drawLine(line, Offset(w * 0.5f, 4f), Offset(w * 0.5f, h - 4f), strokeWidth = 1.5f)
        drawCircle(line, radius = h * 0.18f, center = Offset(w * 0.5f, h * 0.5f), style = Stroke(1.5f))

        fun mapPt(nx: Float, nz: Float): Offset =
            Offset(6f + (nx + 1f) * 0.5f * (w - 12f), 6f + (nz + 1f) * 0.5f * (h - 12f))

        hud.radarHomePlayers.forEach { (nx, nz) ->
            drawCircle(homeCol, radius = 3.2f, center = mapPt(nx, nz))
        }
        hud.radarAwayPlayers.forEach { (nx, nz) ->
            drawCircle(awayCol, radius = 3.2f, center = mapPt(nx, nz))
        }
        drawCircle(PitchEmerald, radius = 5.5f, center = mapPt(hud.controlledPlayerNorm.first, hud.controlledPlayerNorm.second), style = Stroke(1.8f))
        drawCircle(ChampionshipGold, radius = 3.6f, center = mapPt(hud.radarBallX, hud.radarBallZ))
    }
}

@Composable
private fun PauseMenuOverlay(
    config: MatchConfig,
    settings: UserSettings,
    onResume: () -> Unit,
    onUpdateCamera: (CameraPreset) -> Unit,
    onUpdateCameraZoom: (Int) -> Unit,
    onUpdateCameraHeight: (Int) -> Unit,
    onUpdateGraphics: (GraphicsQuality) -> Unit,
    onUpdateSoundEnabled: (Boolean) -> Unit,
    onUpdateShowPerfHud: (Boolean) -> Unit,
    onRematch: () -> Unit,
    onExit: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize().background(Color(0xC0060B12)),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = TunnelNavy,
            border = BorderStroke(1.2.dp, PitchEmerald.copy(alpha = 0.6f)),
            modifier = Modifier.width(500.dp)
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("MATCH PAUSED", style = MaterialTheme.typography.headlineMedium, color = TextPrimary)

                Text("BROADCAST CAMERA", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    CameraPreset.entries.forEach { cam ->
                        SelectableChip(
                            label = cam.displayName,
                            selected = config.cameraPreset == cam,
                            accent = TacticalCyan,
                            modifier = Modifier.weight(1f),
                            onClick = { onUpdateCamera(cam) }
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("ZOOM: ${config.cameraZoom}/10", style = MaterialTheme.typography.labelSmall, color = TacticalCyan)
                        Slider(
                            value = config.cameraZoom.toFloat(),
                            onValueChange = { onUpdateCameraZoom(it.toInt()) },
                            valueRange = 0f..10f,
                            steps = 9,
                            colors = SliderDefaults.colors(thumbColor = TacticalCyan, activeTrackColor = TacticalCyan),
                            modifier = Modifier.height(28.dp).testTag("pause_camera_zoom_slider")
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("HEIGHT: ${config.cameraHeight}/10", style = MaterialTheme.typography.labelSmall, color = PitchEmerald)
                        Slider(
                            value = config.cameraHeight.toFloat(),
                            onValueChange = { onUpdateCameraHeight(it.toInt()) },
                            valueRange = 0f..10f,
                            steps = 9,
                            colors = SliderDefaults.colors(thumbColor = PitchEmerald, activeTrackColor = PitchEmerald),
                            modifier = Modifier.height(28.dp).testTag("pause_camera_height_slider")
                        )
                    }
                }

                Text("GRAPHICS QUALITY", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    GraphicsQuality.entries.forEach { q ->
                        SelectableChip(
                            label = q.name,
                            selected = config.graphicsQuality == q,
                            accent = ChampionshipGold,
                            modifier = Modifier.weight(1f),
                            onClick = { onUpdateGraphics(q) }
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    SelectableChip(
                        label = if (settings.soundEnabled) "SFX: ON" else "SFX: MUTED",
                        selected = settings.soundEnabled,
                        accent = PitchEmerald,
                        modifier = Modifier.weight(1f),
                        onClick = { onUpdateSoundEnabled(!settings.soundEnabled) }
                    )
                    SelectableChip(
                        label = if (settings.showPerfHud) "PROFILER: ON" else "PROFILER: OFF",
                        selected = settings.showPerfHud,
                        accent = TacticalCyan,
                        modifier = Modifier.weight(1f),
                        onClick = { onUpdateShowPerfHud(!settings.showPerfHud) }
                    )
                }

                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = onResume,
                        colors = ButtonDefaults.buttonColors(containerColor = PitchEmerald, contentColor = StadiumSlate),
                        modifier = Modifier.weight(1f).height(46.dp).testTag("pause_resume_button")
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("RESUME")
                    }
                    OutlinedButton(
                        onClick = onRematch,
                        border = BorderStroke(1.dp, GlassBorder),
                        modifier = Modifier.weight(0.8f).height(46.dp).testTag("pause_restart_button")
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = null, tint = TextPrimary)
                        Spacer(Modifier.width(4.dp))
                        Text("RESTART", color = TextPrimary)
                    }
                    OutlinedButton(
                        onClick = onExit,
                        border = BorderStroke(1.dp, CrimsonAlert.copy(alpha = 0.7f)),
                        modifier = Modifier.weight(0.75f).height(46.dp).testTag("pause_exit_button")
                    ) {
                        Text("QUIT", color = CrimsonAlert)
                    }
                }
            }
        }
    }
}

@Composable
private fun ReconnectOverlay(
    lanState: LanLobbyUiState,
    onReconnect: () -> Unit,
    onReturnToMenu: () -> Unit
) {
    val isReconnecting = lanState.connectionState == LanConnectionState.RECONNECTING
    val isClient = lanState.role == LanRole.CLIENT

    Box(
        modifier = Modifier.fillMaxSize().background(Color(0xD5060B12)),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = TunnelNavy,
            border = BorderStroke(1.5.dp, if (isReconnecting) ChampionshipGold else CrimsonAlert),
            modifier = Modifier.width(440.dp)
        ) {
            Column(
                modifier = Modifier.padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (isReconnecting) {
                    CircularProgressIndicator(color = ChampionshipGold, strokeWidth = 3.dp, modifier = Modifier.size(34.dp))
                } else {
                    Icon(Icons.Filled.WifiOff, contentDescription = null, tint = CrimsonAlert, modifier = Modifier.size(36.dp))
                }
                Text(
                    text = if (isReconnecting) "WAITING TO RECONNECT" else stringResource(R.string.disconnect_title),
                    style = MaterialTheme.typography.headlineMedium,
                    color = TextPrimary
                )
                Text(
                    text = lanState.errorMessage ?: lanState.statusMessage.ifBlank { stringResource(R.string.disconnect_body) },
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    if (isClient && !isReconnecting) {
                        Button(
                            onClick = onReconnect,
                            colors = ButtonDefaults.buttonColors(containerColor = TacticalCyan, contentColor = StadiumSlate),
                            modifier = Modifier.weight(1f).height(46.dp).testTag("lan_reconnect_button")
                        ) {
                            Text("RECONNECT")
                        }
                    }
                    OutlinedButton(
                        onClick = onReturnToMenu,
                        border = BorderStroke(1.dp, GlassBorder),
                        modifier = Modifier.weight(1f).height(46.dp).testTag("lan_disconnect_exit_button")
                    ) {
                        Text("MAIN MENU", color = TextPrimary)
                    }
                }
            }
        }
    }
}

@Composable
private fun FullTimeOverlay(
    config: MatchConfig,
    hud: MatchHudSnapshot,
    onRematch: () -> Unit,
    onExitToMenu: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize().background(Color(0xD8060B12)),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = TunnelNavy,
            border = BorderStroke(1.5.dp, ChampionshipGold.copy(alpha = 0.75f)),
            modifier = Modifier.width(480.dp)
        ) {
            Column(
                modifier = Modifier.padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("FULL TIME RESULTS", style = MaterialTheme.typography.labelLarge, color = ChampionshipGold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(config.homeTeam.name, style = MaterialTheme.typography.titleLarge, color = TextPrimary)
                    Text("${hud.homeScore}  -  ${hud.awayScore}", style = MaterialTheme.typography.displaySmall, color = PitchEmerald)
                    Text(config.awayTeam.name, style = MaterialTheme.typography.titleLarge, color = TextPrimary)
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = CardSurface,
                    border = BorderStroke(1.dp, GlassBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        StatRow("${hud.stats.homePossessionPct}%", "POSSESSION", "${hud.stats.awayPossessionPct}%")
                        StatRow("${hud.stats.homeShots} (${hud.stats.homeShotsOnTarget})", "SHOTS (ON TARGET)", "${hud.stats.awayShots} (${hud.stats.awayShotsOnTarget})")
                        StatRow("${hud.stats.homePasses}", "PASSES", "${hud.stats.awayPasses}")
                        StatRow("${hud.stats.homeTackles}", "TACKLES WON", "${hud.stats.awayTackles}")
                        StatRow("${hud.stats.homeFouls}", "FOULS", "${hud.stats.awayFouls}")
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = onRematch,
                        colors = ButtonDefaults.buttonColors(containerColor = PitchEmerald, contentColor = StadiumSlate),
                        modifier = Modifier.weight(1f).height(46.dp).testTag("fulltime_rematch_button")
                    ) {
                        Text("REMATCH", style = MaterialTheme.typography.titleMedium)
                    }
                    OutlinedButton(
                        onClick = onExitToMenu,
                        border = BorderStroke(1.dp, GlassBorder),
                        modifier = Modifier.weight(1f).height(46.dp).testTag("fulltime_menu_button")
                    ) {
                        Text("MAIN MENU", color = TextPrimary, style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatRow(left: String, label: String, right: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(left, style = MaterialTheme.typography.titleMedium, color = PitchEmerald)
        Text(label, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
        Text(right, style = MaterialTheme.typography.titleMedium, color = TacticalCyan)
    }
}
