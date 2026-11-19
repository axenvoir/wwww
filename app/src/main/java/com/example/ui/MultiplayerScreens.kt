package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.game.model.ClubCatalog
import com.example.game.model.MatchConfig
import com.example.game.model.StadiumPreset
import com.example.game.network.DiscoveredHost
import com.example.game.network.LanConnectionState
import com.example.game.network.LanLobbyUiState
import com.example.game.network.LanRole
import com.example.ui.theme.CardSurface
import com.example.ui.theme.CardSurfaceElevated
import com.example.ui.theme.ChampionshipGold
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.PitchEmerald
import com.example.ui.theme.StadiumSlate
import com.example.ui.theme.TacticalCyan
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TunnelNavy

@Composable
fun MultiplayerHubScreen(
    lanState: LanLobbyUiState,
    onHostClick: () -> Unit,
    onJoinClick: () -> Unit,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(StadiumSlate)
            .safeDrawingPadding()
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                IconButton(onClick = onBack, modifier = Modifier.testTag("mp_hub_back_button")) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                }
                Column {
                    Text("OFFLINE LOCAL MULTIPLAYER", style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
                    Text("Zero internet required • Direct phone-to-phone Wi-Fi or Mobile Hotspot", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                }
            }

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = TunnelNavy,
                border = BorderStroke(1.dp, TacticalCyan.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Filled.Wifi, contentDescription = null, tint = TacticalCyan, modifier = Modifier.size(16.dp))
                    val ip = lanState.localAddresses.firstOrNull()?.ipAddress ?: "Connect Wi-Fi / Hotspot"
                    Text("THIS DEVICE: $ip", style = MaterialTheme.typography.labelMedium, color = TextPrimary)
                }
            }
        }

        Row(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            MultiplayerRoleCard(
                title = stringResource(R.string.menu_host_match),
                badge = "PLAYER 1 • HOME SERVER",
                description = "Starts an authoritative local match server on this phone, broadcasts its LAN room, and shows your Wi-Fi / Hotspot IP for Player 2 to join.",
                accent = PitchEmerald,
                icon = Icons.Filled.WifiTethering,
                tag = "hub_host_match_button",
                onClick = onHostClick,
                modifier = Modifier.weight(1f)
            )
            MultiplayerRoleCard(
                title = stringResource(R.string.menu_join_match),
                badge = "PLAYER 2 • AWAY CLIENT",
                description = "Automatically scans local Wi-Fi for Matchday LAN hosts or lets you type the Host IP address directly over a Mobile Hotspot.",
                accent = TacticalCyan,
                icon = Icons.Filled.Router,
                tag = "hub_join_match_button",
                onClick = onJoinClick,
                modifier = Modifier.weight(1f)
            )
        }

        Surface(
            shape = RoundedCornerShape(12.dp),
            color = TunnelNavy,
            border = BorderStroke(1.dp, GlassBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = stringResource(R.string.lobby_hotspot_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
            )
        }
    }
}

@Composable
private fun MultiplayerRoleCard(
    title: String,
    badge: String,
    description: String,
    accent: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = TunnelNavy,
        border = BorderStroke(1.5.dp, accent.copy(alpha = 0.6f)),
        modifier = modifier
            .fillMaxHeight()
            .testTag(tag)
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(22.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(accent.copy(alpha = 0.2f))
                        .border(1.dp, accent, RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = title, tint = accent, modifier = Modifier.size(28.dp))
                }
                Text(badge, style = MaterialTheme.typography.labelMedium, color = accent)
                Text(title.uppercase(), style = MaterialTheme.typography.displaySmall, color = TextPrimary)
                Text(description, style = MaterialTheme.typography.bodyLarge, color = TextSecondary)
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("OPEN LOBBY", style = MaterialTheme.typography.titleMedium, color = accent)
                Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = accent)
            }
        }
    }
}

@Composable
fun HostLobbyScreen(
    lanState: LanLobbyUiState,
    config: MatchConfig,
    onUpdateConfig: ((MatchConfig) -> MatchConfig) -> Unit,
    onToggleReady: () -> Unit,
    onStartMatch: () -> Unit,
    onRefreshIp: () -> Unit,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    val isConnected = lanState.connectionState == LanConnectionState.CONNECTED

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(StadiumSlate)
            .safeDrawingPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Header Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                IconButton(onClick = onBack, modifier = Modifier.testTag("host_lobby_back_button")) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                }
                Column {
                    Text("HOST MATCH LOBBY", style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
                    Text(lanState.statusMessage, style = MaterialTheme.typography.bodyMedium, color = if (isConnected) PitchEmerald else ChampionshipGold)
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                if (isConnected) {
                    OutlinedButton(
                        onClick = onToggleReady,
                        border = BorderStroke(1.5.dp, if (lanState.hostReady) PitchEmerald else GlassBorder),
                        modifier = Modifier.height(44.dp).testTag("host_ready_button")
                    ) {
                        Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = if (lanState.hostReady) PitchEmerald else TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (lanState.hostReady) "HOST READY" else "MARK READY",
                            color = if (lanState.hostReady) PitchEmerald else TextPrimary
                        )
                    }

                    Button(
                        onClick = onStartMatch,
                        enabled = lanState.hostReady && lanState.clientReady,
                        colors = ButtonDefaults.buttonColors(containerColor = PitchEmerald, contentColor = StadiumSlate),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.height(44.dp).testTag("host_start_match_button")
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("START MATCH", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }

        if (!isConnected) {
            // Waiting for Opponent Panel + Local IP & Hotspot Connection Card
            Row(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = TunnelNavy,
                    border = BorderStroke(1.2.dp, PitchEmerald.copy(alpha = 0.5f)),
                    modifier = Modifier.weight(1f).fillMaxHeight()
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(20.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("LOCAL SERVER ADDRESS", style = MaterialTheme.typography.labelLarge, color = PitchEmerald)
                                IconButton(onClick = onRefreshIp, modifier = Modifier.testTag("refresh_ip_button")) {
                                    Icon(Icons.Filled.Refresh, contentDescription = "Refresh IP", tint = TacticalCyan)
                                }
                            }
                            if (lanState.localAddresses.isEmpty()) {
                                Text(
                                    "No Wi-Fi or Hotspot IP detected. Connect to a Wi-Fi network or enable Android Mobile Hotspot, then tap Refresh.",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = ChampionshipGold
                                )
                            } else {
                                lanState.localAddresses.forEach { addr ->
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = CardSurfaceElevated,
                                        border = BorderStroke(1.dp, PitchEmerald.copy(alpha = 0.45f)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column {
                                                Text(
                                                    text = addr.ipAddress,
                                                    style = MaterialTheme.typography.headlineMedium,
                                                    color = TextPrimary
                                                )
                                                Text(
                                                    text = if (addr.isHotspotLikely) "Mobile Hotspot (${addr.interfaceName})" else "Local Wi-Fi (${addr.interfaceName})",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = TacticalCyan
                                                )
                                            }
                                            Text(
                                                text = "PORT ${lanState.tcpPort}",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = ChampionshipGold
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(
                                color = PitchEmerald,
                                strokeWidth = 3.dp,
                                modifier = Modifier.size(26.dp)
                            )
                            Text(
                                text = stringResource(R.string.lobby_waiting_opponent),
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary
                            )
                        }
                    }
                }

                // Host can already pick their Home team while waiting
                TeamSelectionColumn(
                    label = "HOME TEAM (PLAYER 1)",
                    accent = PitchEmerald,
                    selectedTeam = config.homeTeam,
                    selectedFormation = config.homeFormation,
                    onSelectTeam = { t -> onUpdateConfig { it.copy(homeTeamId = t.id) } },
                    onSelectFormation = { f -> onUpdateConfig { it.copy(homeFormation = f) } },
                    modifier = Modifier.weight(1f)
                )
            }
        } else {
            // Connected Dual-Player Lobby
            ConnectedLanLobbyGrid(
                role = LanRole.HOST,
                lanState = lanState,
                config = config,
                onUpdateConfig = onUpdateConfig,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun JoinLobbyScreen(
    lanState: LanLobbyUiState,
    config: MatchConfig,
    initialManualIp: String,
    onConnectToHost: (String) -> Unit,
    onRescan: () -> Unit,
    onUpdateConfig: ((MatchConfig) -> MatchConfig) -> Unit,
    onToggleReady: () -> Unit,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    var manualIp by remember(initialManualIp) { mutableStateOf(initialManualIp) }
    val isConnected = lanState.connectionState == LanConnectionState.CONNECTED

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(StadiumSlate)
            .safeDrawingPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                IconButton(onClick = onBack, modifier = Modifier.testTag("join_lobby_back_button")) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                }
                Column {
                    Text(
                        if (isConnected) "JOINED MATCH LOBBY" else "JOIN LOCAL MATCH",
                        style = MaterialTheme.typography.headlineMedium,
                        color = TextPrimary
                    )
                    Text(
                        lanState.errorMessage ?: lanState.statusMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (lanState.errorMessage != null) CrimsonAlert else if (isConnected) PitchEmerald else TacticalCyan
                    )
                }
            }

            if (isConnected) {
                Button(
                    onClick = onToggleReady,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (lanState.clientReady) PitchEmerald else TacticalCyan,
                        contentColor = StadiumSlate
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.height(44.dp).testTag("client_ready_button")
                ) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (lanState.clientReady) "READY (WAITING FOR HOST)" else "PRESS READY", style = MaterialTheme.typography.titleMedium)
                }
            } else {
                OutlinedButton(
                    onClick = onRescan,
                    border = BorderStroke(1.dp, TacticalCyan),
                    modifier = Modifier.height(42.dp).testTag("rescan_lan_button")
                ) {
                    Icon(Icons.Filled.Search, contentDescription = null, tint = TacticalCyan, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("SCAN LAN", color = TacticalCyan)
                }
            }
        }

        if (!isConnected) {
            Row(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Left: Auto-discovered LAN Hosts
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = TunnelNavy,
                    border = BorderStroke(1.dp, GlassBorder),
                    modifier = Modifier.weight(1.1f).fillMaxHeight()
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(color = TacticalCyan, strokeWidth = 2.5.dp, modifier = Modifier.size(16.dp))
                            Text("DISCOVERED WI-FI MATCHES", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                        }

                        if (lanState.discoveredHosts.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    text = stringResource(R.string.lobby_no_hosts),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = TextSecondary
                                )
                            }
                        } else {
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(lanState.discoveredHosts) { host ->
                                    DiscoveredHostRow(host = host, onJoin = { onConnectToHost(host.ipAddress) })
                                }
                            }
                        }
                    }
                }

                // Right: Manual IP Direct Connect (Hotspot Friendly)
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = TunnelNavy,
                    border = BorderStroke(1.2.dp, TacticalCyan.copy(alpha = 0.5f)),
                    modifier = Modifier.weight(0.9f).fillMaxHeight()
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(18.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("DIRECT MANUAL IP CONNECT", style = MaterialTheme.typography.titleMedium, color = TacticalCyan)
                            Text(
                                "Enter the IPv4 address shown on the Host phone's lobby screen (works over any Wi-Fi router or Mobile Hotspot).",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                            OutlinedTextField(
                                value = manualIp,
                                onValueChange = { manualIp = it },
                                label = { Text(stringResource(R.string.lobby_manual_ip_label)) },
                                placeholder = { Text(stringResource(R.string.lobby_manual_ip_placeholder)) },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = TacticalCyan,
                                    unfocusedBorderColor = GlassBorder,
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary
                                ),
                                modifier = Modifier.fillMaxWidth().testTag("manual_ip_input")
                            )
                        }

                        Button(
                            onClick = { onConnectToHost(manualIp) },
                            enabled = lanState.connectionState != LanConnectionState.CONNECTING,
                            colors = ButtonDefaults.buttonColors(containerColor = TacticalCyan, contentColor = StadiumSlate),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("connect_manual_ip_button")
                        ) {
                            Text(
                                if (lanState.connectionState == LanConnectionState.CONNECTING) "CONNECTING…" else "CONNECT TO HOST",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }
                }
            }
        } else {
            ConnectedLanLobbyGrid(
                role = LanRole.CLIENT,
                lanState = lanState,
                config = config,
                onUpdateConfig = onUpdateConfig,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun DiscoveredHostRow(host: DiscoveredHost, onJoin: () -> Unit) {
    val hostClub = ClubCatalog.findById(host.hostTeamId)
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = CardSurfaceElevated,
        border = BorderStroke(1.dp, TacticalCyan.copy(alpha = 0.5f)),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onJoin)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(host.hostName, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                Text("${host.ipAddress}:${host.port} • ${hostClub.name}", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
            }
            Text("JOIN", style = MaterialTheme.typography.labelLarge, color = PitchEmerald)
        }
    }
}

@Composable
private fun ConnectedLanLobbyGrid(
    role: LanRole,
    lanState: LanLobbyUiState,
    config: MatchConfig,
    onUpdateConfig: ((MatchConfig) -> MatchConfig) -> Unit,
    modifier: Modifier = Modifier
) {
    val isHost = role == LanRole.HOST

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Home Side (Editable by Host only)
        TeamSelectionColumn(
            label = "HOME • HOST ${if (lanState.hostReady) "✓ READY" else ""}",
            accent = PitchEmerald,
            selectedTeam = config.homeTeam,
            selectedFormation = config.homeFormation,
            enabled = isHost,
            onSelectTeam = { t -> onUpdateConfig { it.copy(homeTeamId = t.id) } },
            onSelectFormation = { f -> onUpdateConfig { it.copy(homeFormation = f) } },
            modifier = Modifier.weight(1f)
        )

        // Center Match & Link Telemetry
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = TunnelNavy,
            border = BorderStroke(1.dp, GlassBorder),
            modifier = Modifier.weight(0.82f).fillMaxHeight()
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(12.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("LAN LINK", style = MaterialTheme.typography.labelLarge, color = TacticalCyan)
                        Text("${lanState.pingMs} ms", style = MaterialTheme.typography.labelMedium, color = PitchEmerald)
                    }
                    Text(
                        text = "Peer: ${lanState.peerName.ifBlank { lanState.peerIp }}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )

                    Text("STADIUM", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    StadiumPreset.entries.forEach { preset ->
                        SelectableChip(
                            label = preset.displayName,
                            selected = config.stadium == preset,
                            accent = ChampionshipGold,
                            enabled = isHost,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { onUpdateConfig { it.copy(stadium = preset) } }
                        )
                    }

                    Text("DURATION", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        listOf(3, 5, 10).forEach { mins ->
                            SelectableChip(
                                label = "${mins}m",
                                selected = config.matchMinutes == mins,
                                accent = PitchEmerald,
                                enabled = isHost,
                                modifier = Modifier.weight(1f),
                                onClick = { onUpdateConfig { it.copy(matchMinutes = mins) } }
                            )
                        }
                    }
                }

                // Ready Status Pill
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = CardSurface,
                    border = BorderStroke(1.dp, GlassBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        ReadyDot("P1 HOST", lanState.hostReady)
                        ReadyDot("P2 CLIENT", lanState.clientReady)
                    }
                }
            }
        }

        // Away Side (Editable by Client only)
        TeamSelectionColumn(
            label = "AWAY • CLIENT ${if (lanState.clientReady) "✓ READY" else ""}",
            accent = TacticalCyan,
            selectedTeam = config.awayTeam,
            selectedFormation = config.awayFormation,
            enabled = !isHost,
            onSelectTeam = { t -> onUpdateConfig { it.copy(awayTeamId = t.id) } },
            onSelectFormation = { f -> onUpdateConfig { it.copy(awayFormation = f) } },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun ReadyDot(label: String, ready: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(if (ready) PitchEmerald else Color(0xFF475569))
        )
        Text(label, style = MaterialTheme.typography.labelSmall, color = if (ready) TextPrimary else TextSecondary)
    }
}
