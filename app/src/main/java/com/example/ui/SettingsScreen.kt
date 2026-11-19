package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.game.model.AiDifficulty
import com.example.game.model.CameraPreset
import com.example.game.model.GraphicsPipelineMode
import com.example.game.model.GraphicsQuality
import com.example.game.model.StadiumPreset
import com.example.game.model.UserSettings
import com.example.ui.theme.ChampionshipGold
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.PitchEmerald
import com.example.ui.theme.StadiumSlate
import com.example.ui.theme.TacticalCyan
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TunnelNavy

@Composable
fun SettingsScreen(
    settings: UserSettings,
    onUpdateGraphicsPipelineMode: (GraphicsPipelineMode) -> Unit = {},
    onUpdateGraphics: (GraphicsQuality) -> Unit,
    onUpdateCamera: (CameraPreset) -> Unit,
    onUpdateCameraZoom: (Int) -> Unit,
    onUpdateCameraHeight: (Int) -> Unit,
    onUpdateStadium: (StadiumPreset) -> Unit,
    onUpdateMinutes: (Int) -> Unit,
    onUpdateDifficulty: (AiDifficulty) -> Unit,
    onUpdateSoundEnabled: (Boolean) -> Unit,
    onUpdateShowPerfHud: (Boolean) -> Unit,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(StadiumSlate)
            .safeDrawingPadding()
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            IconButton(onClick = onBack, modifier = Modifier.testTag("settings_back_button")) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
            }
            Column {
                Text("MATCH, AUDIO & PERFORMANCE SETTINGS", style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
                Text("Saved locally on this device • Applies immediately to 3D rendering & HUD", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
            }
        }

        Row(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Left: 3D Graphics, Camera, Audio & Profiler Toggles
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = TunnelNavy,
                border = BorderStroke(1.dp, GlassBorder),
                modifier = Modifier.weight(1f).fillMaxHeight()
            ) {
                Column(
                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("3D GRAPHICS QUALITY", style = MaterialTheme.typography.titleMedium, color = PitchEmerald)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        GraphicsQuality.entries.forEach { q ->
                            SelectableChip(
                                label = q.displayName,
                                selected = settings.graphicsQuality == q,
                                accent = PitchEmerald,
                                modifier = Modifier.weight(1f),
                                onClick = { onUpdateGraphics(q) }
                            )
                        }
                    }
                                    Text("RENDER PIPELINE", style = MaterialTheme.typography.titleMedium, color = PitchEmerald)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    GraphicsPipelineMode.entries.forEach { mode ->
                        SelectableChip(
                            label = mode.displayName,
                            selected = settings.graphicsPipelineMode == mode,
                            accent = PitchEmerald,
                            modifier = Modifier.weight(1f),
                            onClick = { onUpdateGraphicsPipelineMode(mode) }
                        )
                    }
                }                    

                    Text("BROADCAST CAMERA", style = MaterialTheme.typography.titleMedium, color = TacticalCyan)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        CameraPreset.entries.forEach { cam ->
                            SelectableChip(
                                label = cam.displayName,
                                selected = settings.cameraPreset == cam,
                                accent = TacticalCyan,
                                modifier = Modifier.weight(1f),
                                onClick = { onUpdateCamera(cam) }
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("CAMERA ZOOM: ${settings.cameraZoom}/10", style = MaterialTheme.typography.labelMedium, color = TextPrimary)
                            Slider(
                                value = settings.cameraZoom.toFloat(),
                                onValueChange = { onUpdateCameraZoom(it.toInt()) },
                                valueRange = 0f..10f,
                                steps = 9,
                                colors = SliderDefaults.colors(thumbColor = TacticalCyan, activeTrackColor = TacticalCyan),
                                modifier = Modifier.testTag("settings_camera_zoom_slider")
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("CAMERA HEIGHT: ${settings.cameraHeight}/10", style = MaterialTheme.typography.labelMedium, color = TextPrimary)
                            Slider(
                                value = settings.cameraHeight.toFloat(),
                                onValueChange = { onUpdateCameraHeight(it.toInt()) },
                                valueRange = 0f..10f,
                                steps = 9,
                                colors = SliderDefaults.colors(thumbColor = PitchEmerald, activeTrackColor = PitchEmerald),
                                modifier = Modifier.testTag("settings_camera_height_slider")
                            )
                        }
                    }

                    Text("DEFAULT STADIUM LIGHTING", style = MaterialTheme.typography.titleMedium, color = ChampionshipGold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        StadiumPreset.entries.forEach { st ->
                            SelectableChip(
                                label = st.displayName.substringBefore(" "),
                                selected = settings.stadium == st,
                                accent = ChampionshipGold,
                                modifier = Modifier.weight(1f),
                                onClick = { onUpdateStadium(st) }
                            )
                        }
                    }

                    // Audio & Performance Profiler Toggles
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("STADIUM AUDIO & WHISTLE SFX", style = MaterialTheme.typography.labelLarge, color = TextPrimary)
                            Text("Referee whistles, ball kicks, tackles & goal roar", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                        }
                        Switch(
                            checked = settings.soundEnabled,
                            onCheckedChange = onUpdateSoundEnabled,
                            colors = SwitchDefaults.colors(checkedThumbColor = StadiumSlate, checkedTrackColor = PitchEmerald),
                            modifier = Modifier.testTag("settings_sound_switch")
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("PERFORMANCE & LAN PROFILER HUD", style = MaterialTheme.typography.labelLarge, color = TextPrimary)
                            Text("Shows live FPS, CPU/GPU ms, RAM & Wi-Fi jitter/loss", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                        }
                        Switch(
                            checked = settings.showPerfHud,
                            onCheckedChange = onUpdateShowPerfHud,
                            colors = SwitchDefaults.colors(checkedThumbColor = StadiumSlate, checkedTrackColor = TacticalCyan),
                            modifier = Modifier.testTag("settings_perf_switch")
                        )
                    }
                }
            }

            // Right: Match Defaults & Controls Guide
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = TunnelNavy,
                border = BorderStroke(1.dp, GlassBorder),
                modifier = Modifier.weight(1f).fillMaxHeight()
            ) {
                Column(
                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("DEFAULT MATCH LENGTH", style = MaterialTheme.typography.titleMedium, color = PitchEmerald)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        listOf(3, 5, 10).forEach { mins ->
                            SelectableChip(
                                label = "$mins Minutes",
                                selected = settings.matchMinutes == mins,
                                accent = PitchEmerald,
                                modifier = Modifier.weight(1f),
                                onClick = { onUpdateMinutes(mins) }
                            )
                        }
                    }

                    Text("SOLO AI DIFFICULTY", style = MaterialTheme.typography.titleMedium, color = TacticalCyan)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        AiDifficulty.entries.forEach { d ->
                            SelectableChip(
                                label = d.displayName,
                                selected = settings.difficulty == d,
                                accent = TacticalCyan,
                                modifier = Modifier.weight(1f),
                                onClick = { onUpdateDifficulty(d) }
                            )
                        }
                    }

                    Text("CONTROLS GUIDE", style = MaterialTheme.typography.titleMedium, color = ChampionshipGold)
                    Text(
                        "• Left Virtual Joystick: 360° responsive dribble & aim\n" +
                            "• PASS (tap/hold): Short pass or hold for lofted long pass (Press off ball)\n" +
                            "• THROUGH: Weighted through-ball into space ahead of runners\n" +
                            "• SHOOT (hold): Power-bar shot with curve & loft control\n" +
                            "• SPRINT / SWITCH / TACKLE: Turbo run, manual defender switch, slide lunge",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
            }
        }
    }
}
