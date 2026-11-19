package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stadium
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.game.model.AiDifficulty
import com.example.game.model.ClubCatalog
import com.example.game.model.ClubTeam
import com.example.game.model.Formation
import com.example.game.model.MatchConfig
import com.example.game.model.StadiumPreset
import com.example.ui.theme.CardSurface
import com.example.ui.theme.CardSurfaceElevated
import com.example.ui.theme.ChampionshipGold
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.PitchEmerald
import com.example.ui.theme.StadiumSlate
import com.example.ui.theme.TacticalCyan
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TunnelNavy

@Composable
fun QuickMatchSetupScreen(
    config: MatchConfig,
    onUpdateConfig: ((MatchConfig) -> MatchConfig) -> Unit,
    onKickOff: () -> Unit,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(StadiumSlate)
            .safeDrawingPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Top Header Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.testTag("setup_back_button")
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                }
                Column {
                    Text("MATCHDAY SETUP", style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
                    Text("Select clubs, formations, and stadium lighting", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                }
            }

            Button(
                onClick = onKickOff,
                colors = ButtonDefaults.buttonColors(containerColor = PitchEmerald, contentColor = StadiumSlate),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .height(48.dp)
                    .testTag("kickoff_button")
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("KICK OFF", style = MaterialTheme.typography.titleMedium)
            }
        }

        // Main 3-column Landscape Setup Grid
        Row(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Column 1: Home Team & Formation
            TeamSelectionColumn(
                label = "HOME (YOU)",
                accent = PitchEmerald,
                selectedTeam = config.homeTeam,
                selectedFormation = config.homeFormation,
                onSelectTeam = { team -> onUpdateConfig { it.copy(homeTeamId = team.id) } },
                onSelectFormation = { f -> onUpdateConfig { it.copy(homeFormation = f) } },
                modifier = Modifier.weight(1f)
            )

            // Column 2: Stadium Atmosphere & Match Rules
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = TunnelNavy,
                border = BorderStroke(1.dp, GlassBorder),
                modifier = Modifier.weight(0.95f).fillMaxHeight()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.Stadium, contentDescription = null, tint = ChampionshipGold, modifier = Modifier.size(18.dp))
                        Text("STADIUM & MATCH", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                    }

                    // Stadium Lighting Selector
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        StadiumPreset.entries.forEach { preset ->
                            val selected = config.stadium == preset
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (selected) CardSurfaceElevated else CardSurface,
                                border = BorderStroke(1.2.dp, if (selected) ChampionshipGold else GlassBorder),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("stadium_${preset.name.lowercase()}")
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { onUpdateConfig { it.copy(stadium = preset) } }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text(preset.displayName, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                                        Text(preset.subtitle, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                                    }
                                    Box(
                                        modifier = Modifier
                                            .size(14.dp)
                                            .clip(CircleShape)
                                            .background(Color(preset.sunR, preset.sunG, preset.sunB))
                                    )
                                }
                            }
                        }
                    }

                    // Match Length
                    Text("MATCH LENGTH", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        listOf(3, 5, 10).forEach { mins ->
                            val sel = config.matchMinutes == mins
                            SelectableChip(
                                label = "${mins}m",
                                selected = sel,
                                accent = PitchEmerald,
                                modifier = Modifier.weight(1f),
                                onClick = { onUpdateConfig { it.copy(matchMinutes = mins) } }
                            )
                        }
                    }

                    // AI Difficulty
                    Text("AI DIFFICULTY", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        AiDifficulty.entries.forEach { diff ->
                            SelectableChip(
                                label = diff.displayName,
                                selected = config.difficulty == diff,
                                accent = TacticalCyan,
                                modifier = Modifier.weight(1f),
                                onClick = { onUpdateConfig { it.copy(difficulty = diff) } }
                            )
                        }
                    }
                }
            }

            // Column 3: Away Team & Formation
            TeamSelectionColumn(
                label = "AWAY (AI)",
                accent = TacticalCyan,
                selectedTeam = config.awayTeam,
                selectedFormation = config.awayFormation,
                onSelectTeam = { team -> onUpdateConfig { it.copy(awayTeamId = team.id) } },
                onSelectFormation = { f -> onUpdateConfig { it.copy(awayFormation = f) } },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun TeamSelectionColumn(
    label: String,
    accent: Color,
    selectedTeam: ClubTeam,
    selectedFormation: Formation,
    onSelectTeam: (ClubTeam) -> Unit,
    onSelectFormation: (Formation) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = TunnelNavy,
        border = BorderStroke(1.2.dp, accent.copy(alpha = 0.45f)),
        modifier = modifier.fillMaxHeight()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(label, style = MaterialTheme.typography.labelLarge, color = accent)
                Text("OVR ${selectedTeam.overallRating}", style = MaterialTheme.typography.titleMedium, color = ChampionshipGold)
            }

            // Selected Club Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(selectedTeam.kit.composePrimary())
                        .border(2.dp, selectedTeam.kit.composeAccent(), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = selectedTeam.shortCode,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selectedTeam.kit.jerseyArgb == 0xFFF3F6FA || selectedTeam.kit.jerseyArgb == 0xFFFACC15) StadiumSlate else TextPrimary
                    )
                }
                Column {
                    Text(selectedTeam.name, style = MaterialTheme.typography.titleLarge, color = TextPrimary)
                    Text(
                        "ATK ${selectedTeam.attackRating} • MID ${selectedTeam.midfieldRating} • DEF ${selectedTeam.defenseRating}",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                }
            }

            // Club Swatch Picker Row
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(ClubCatalog.teams) { team ->
                    val isSelected = team.id == selectedTeam.id
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) CardSurfaceElevated else CardSurface,
                        border = BorderStroke(1.5.dp, if (isSelected) accent else GlassBorder),
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(enabled = enabled) { onSelectTeam(team) }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(team.kit.composePrimary())
                            )
                            Text(team.shortCode, style = MaterialTheme.typography.labelMedium, color = TextPrimary)
                        }
                    }
                }
            }

            // Formation Selector
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                Formation.entries.forEach { form ->
                    SelectableChip(
                        label = form.displayName,
                        selected = form == selectedFormation,
                        accent = accent,
                        enabled = enabled,
                        modifier = Modifier.weight(1f),
                        onClick = { onSelectFormation(form) }
                    )
                }
            }

            // Mini Tactical Formation Pitch Preview
            FormationPreviewPitch(
                formation = selectedFormation,
                dotColor = selectedTeam.kit.composePrimary(),
                ringColor = accent,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
        }
    }
}

@Composable
fun FormationPreviewPitch(
    formation: Formation,
    dotColor: Color,
    ringColor: Color,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF0E3820))
            .border(1.dp, GlassBorder, RoundedCornerShape(10.dp))
    ) {
        val w = size.width
        val h = size.height
        val lineColor = Color.White.copy(alpha = 0.35f)
        // Outer boundary & halfway line
        drawRect(lineColor, topLeft = Offset(8f, 8f), size = Size(w - 16f, h - 16f), style = Stroke(2f))
        drawLine(lineColor, Offset(w * 0.5f, 8f), Offset(w * 0.5f, h - 8f), strokeWidth = 2f)
        drawCircle(lineColor, radius = h * 0.22f, center = Offset(w * 0.5f, h * 0.5f), style = Stroke(2f))

        // Draw 11 formation dots mapped into pitch preview
        for (slot in formation.homeSlots()) {
            // slot.normX in [-0.94, -0.07] -> map to [0.12..0.88] of preview width
            val px = (0.10f + ((slot.normX + 1f) * 0.82f)) * w
            val pz = (0.5f + slot.normZ * 0.42f) * h
            drawCircle(ringColor, radius = 9.5f, center = Offset(px, pz))
            drawCircle(dotColor, radius = 6.5f, center = Offset(px, pz))
        }
    }
}

@Composable
fun SelectableChip(
    label: String,
    selected: Boolean,
    accent: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (selected) accent.copy(alpha = 0.22f) else CardSurface,
        border = BorderStroke(1.2.dp, if (selected) accent else GlassBorder),
        modifier = modifier
            .height(36.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, onClick = onClick)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 6.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = if (selected) accent else TextSecondary,
                maxLines = 1
            )
        }
    }
}
