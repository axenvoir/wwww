package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.game.network.LocalInterfaceAddress
import com.example.ui.theme.CardSurface
import com.example.ui.theme.ChampionshipGold
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.GlassPanel
import com.example.ui.theme.PitchEmerald
import com.example.ui.theme.StadiumSlate
import com.example.ui.theme.TacticalCyan
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun MainMenuScreen(
    localAddresses: List<LocalInterfaceAddress>,
    onQuickMatchClick: () -> Unit,
    onOfflineMultiplayerClick: () -> Unit,
    onHostDirectClick: () -> Unit,
    onJoinDirectClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize().background(StadiumSlate)) {
        // Cinematic floodlit stadium hero backdrop
        Image(
            painter = painterResource(id = R.drawable.img_stadium_hero_1795080724893),
            contentDescription = "Floodlit football stadium atmosphere",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        // Deep stadium directional scrim for razor-sharp broadcast legibility
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color(0xF0080D14),
                            Color(0xCC0B111A),
                            Color(0x990B111A)
                        )
                    )
                )
        )

        Row(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left Broadcast Brand Column
            Column(
                modifier = Modifier
                    .weight(0.45f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top LAN Telemetry Pill
                Surface(
                    shape = RoundedCornerShape(99.dp),
                    color = GlassPanel,
                    border = BorderStroke(1.dp, GlassBorder)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (localAddresses.isNotEmpty()) PitchEmerald else ChampionshipGold)
                        )
                        Icon(
                            imageVector = Icons.Filled.Wifi,
                            contentDescription = "Local Wi-Fi status",
                            tint = TacticalCyan,
                            modifier = Modifier.size(16.dp)
                        )
                        val ipLabel = localAddresses.firstOrNull()?.ipAddress ?: "Offline Wi-Fi Ready"
                        Text(
                            text = "LOCAL LAN • $ipLabel",
                            style = MaterialTheme.typography.labelMedium,
                            color = TextPrimary
                        )
                    }
                }

                // Hero Title & Crest
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.img_launcher_crest_1795080724285),
                            contentDescription = "Matchday LAN crest",
                            modifier = Modifier
                                .size(64.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .border(1.5.dp, PitchEmerald.copy(alpha = 0.7f), RoundedCornerShape(14.dp))
                        )
                        Column {
                            Text(
                                text = stringResource(R.string.tagline_subtitle),
                                style = MaterialTheme.typography.labelMedium,
                                color = PitchEmerald
                            )
                            Text(
                                text = stringResource(R.string.app_name).uppercase(),
                                style = MaterialTheme.typography.displayLarge,
                                color = TextPrimary
                            )
                        }
                    }

                    Text(
                        text = "Full 11 vs. 11 native 3D simulation. Play solo against tactical AI or link two Android phones directly over Wi-Fi or Hotspot with zero internet.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextSecondary
                    )
                }

                // Bottom Match Engine Badges
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    InfoBadgeChip("OPENGL ES 3.0")
                    InfoBadgeChip("ZERO-INTERNET LAN")
                    InfoBadgeChip("11v11 TACTICAL AI")
                }
            }

            // Right Asymmetric Action Deck
            Column(
                modifier = Modifier
                    .weight(0.55f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically)
            ) {
                // Primary Hero CTA: Quick Match
                MenuHeroCard(
                    title = stringResource(R.string.menu_quick_match),
                    subtitle = stringResource(R.string.menu_quick_match_desc),
                    badge = "SOLO 11v11",
                    accentColor = PitchEmerald,
                    icon = Icons.Filled.PlayArrow,
                    tag = "quick_match_button",
                    onClick = onQuickMatchClick
                )

                // Featured LAN Multiplayer Card
                MenuHeroCard(
                    title = stringResource(R.string.menu_offline_multiplayer),
                    subtitle = stringResource(R.string.menu_offline_multiplayer_desc),
                    badge = "2-PHONE WI-FI",
                    accentColor = TacticalCyan,
                    icon = Icons.Filled.WifiTethering,
                    tag = "offline_multiplayer_button",
                    onClick = onOfflineMultiplayerClick
                )

                // Direct Shortcut Row: Host / Join / Settings
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CompactActionTile(
                        title = stringResource(R.string.menu_host_match),
                        icon = Icons.Filled.WifiTethering,
                        accent = ChampionshipGold,
                        tag = "main_host_button",
                        modifier = Modifier.weight(1f),
                        onClick = onHostDirectClick
                    )
                    CompactActionTile(
                        title = stringResource(R.string.menu_join_match),
                        icon = Icons.Filled.SportsSoccer,
                        accent = TacticalCyan,
                        tag = "main_join_button",
                        modifier = Modifier.weight(1f),
                        onClick = onJoinDirectClick
                    )
                    CompactActionTile(
                        title = "Settings",
                        icon = Icons.Filled.Settings,
                        accent = TextSecondary,
                        tag = "main_settings_button",
                        modifier = Modifier.weight(0.85f),
                        onClick = onSettingsClick
                    )
                }
            }
        }
    }
}

@Composable
private fun InfoBadgeChip(label: String) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = CardSurface.copy(alpha = 0.85f),
        border = BorderStroke(1.dp, GlassBorder)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        )
    }
}

@Composable
private fun MenuHeroCard(
    title: String,
    subtitle: String,
    badge: String,
    accentColor: Color,
    icon: ImageVector,
    tag: String,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = GlassPanel,
        border = BorderStroke(1.5.dp, accentColor.copy(alpha = 0.55f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(tag)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        listOf(accentColor.copy(alpha = 0.16f), Color.Transparent)
                    )
                )
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(accentColor.copy(alpha = 0.2f))
                        .border(1.dp, accentColor, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = accentColor,
                        modifier = Modifier.size(26.dp)
                    )
                }
                Column {
                    Text(
                        text = badge,
                        style = MaterialTheme.typography.labelSmall,
                        color = accentColor,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = title.uppercase(),
                        style = MaterialTheme.typography.headlineMedium,
                        color = TextPrimary
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
            }
            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

@Composable
private fun CompactActionTile(
    title: String,
    icon: ImageVector,
    accent: Color,
    tag: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = GlassPanel,
        border = BorderStroke(1.dp, GlassBorder),
        modifier = modifier
            .height(64.dp)
            .testTag(tag)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = accent,
                modifier = Modifier.size(22.dp)
            )
            Text(
                text = title.uppercase(),
                style = MaterialTheme.typography.labelLarge,
                color = TextPrimary,
                maxLines = 1
            )
        }
    }
}
