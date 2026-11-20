package com.example.game.network

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import com.example.game.engine.FootballSimulation
import com.example.game.model.Formation
import com.example.game.model.MatchConfig
import com.example.game.model.PlayerInput
import com.example.game.model.StadiumPreset
import com.example.game.model.TeamSide
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket

enum class LanRole { NONE, HOST, CLIENT }

enum class LanConnectionState {
    IDLE,
    WAITING_FOR_CLIENT,
    SCANNING,
    CONNECTING,
    CONNECTED,
    IN_MATCH,
    RECONNECTING,
    DISCONNECTED
}

data class LanLobbyUiState(
    val role: LanRole = LanRole.NONE,
    val connectionState: LanConnectionState = LanConnectionState.IDLE,
    val localAddresses: List<LocalInterfaceAddress> = emptyList(),
    val tcpPort: Int = LanProtocol.PORT_TCP_LOBBY,
    val discoveredHosts: List<DiscoveredHost> = emptyList(),
    val peerName: String = "",
    val peerIp: String = "",
    val hostReady: Boolean = false,
    val clientReady: Boolean = false,
    val pingMs: Int = 0,
    val jitterMs: Int = 0,
    val packetLossPct: Int = 0,
    val statusMessage: String = "",
    val errorMessage: String? = null
)

class LanMultiplayerManager(
    private val context: Context,
    private val onLobbyConfigSynced: (MatchConfig) -> Unit,
    private val onMatchStartRequested: (MatchConfig) -> Unit,
    private val onRemotePauseChanged: (Boolean) -> Unit,
    private val onRematchRequested: () -> Unit
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow(LanLobbyUiState())
    val state: StateFlow<LanLobbyUiState> = _state.asStateFlow()

    val interpolator = SnapshotInterpolator()

    private var multicastLock: WifiManager.MulticastLock? = null
    @Volatile private var serverSocket: ServerSocket? = null
    @Volatile private var tcpSocket: Socket? = null
    @Volatile private var tcpWriter: PrintWriter? = null
    private var udpDiscoverySocket: DatagramSocket? = null
    @Volatile private var udpGameSocket: DatagramSocket? = null
    @Volatile private var peerAddress: InetAddress? = null
    private var lastConnectedHostIp: String = ""
    private var lastConnectedHostPort: Int = LanProtocol.PORT_TCP_LOBBY

    private var discoveryJob: Job? = null
    private var tcpJob: Job? = null
    private var heartbeatJob: Job? = null
    private var udpRecvJob: Job? = null
    private var udpSendJob: Job? = null

    @Volatile private var activeSimulation: FootballSimulation? = null
    @Volatile private var activeInputProvider: (() -> PlayerInput)? = null

    @Volatile
    private var currentLobbyConfig = MatchConfig(isLanMultiplayer = true)
    private var lastPingSentMs = 0L
    @Volatile
    private var lastAckedClientInputSeq = 0
    private var worldSeqCounter = 0

    private val localDeviceName: String = (Build.MODEL ?: "Android Phone").take(22)

    fun refreshLocalAddresses() {
        val addrs = LocalNetworkUtils.getLocalIpv4Addresses()
        _state.update { it.copy(localAddresses = addrs) }
    }

    /**
     * HOST FLOW: Opens TCP server on 45678 and sends UDP LAN discovery beacons on 45677.
     * Supports automatic safe mid-match reconnection if Client drops and reconnects!
     */
    fun startHosting(initialConfig: MatchConfig, tcpPort: Int = LanProtocol.PORT_TCP_LOBBY) {
        stopAll()
        currentLobbyConfig = initialConfig.copy(isLanMultiplayer = true, localControlledSide = TeamSide.HOME)
        val addresses = LocalNetworkUtils.getLocalIpv4Addresses()

        _state.value = LanLobbyUiState(
            role = LanRole.HOST,
            connectionState = LanConnectionState.WAITING_FOR_CLIENT,
            localAddresses = addresses,
            tcpPort = tcpPort,
            statusMessage = "Server active on port $tcpPort. Waiting for Player 2…"
        )

        multicastLock = LocalNetworkUtils.acquireMulticastLock(context)

        // Start UDP broadcast beacon loop
        discoveryJob = scope.launch {
            runCatching {
                val sock = DatagramSocket().apply { broadcast = true }
                udpDiscoverySocket = sock
                while (isActive && _state.value.connectionState == LanConnectionState.WAITING_FOR_CLIENT) {
                    val payload = LanProtocol.encodeDiscoveryBeacon(
                        hostName = localDeviceName,
                        hostTeamId = currentLobbyConfig.homeTeamId,
                        tcpPort = tcpPort
                    )
                    for (bcast in LocalNetworkUtils.getBroadcastAddresses()) {
                        runCatching {
                            val pkt = DatagramPacket(payload, payload.size, bcast, LanProtocol.PORT_DISCOVERY)
                            sock.send(pkt)
                        }
                    }
                    delay(1200)
                }
            }
        }

        // Start persistent TCP accept loop (handles initial join AND safe mid-match reconnection)
        tcpJob = scope.launch {
            try {
                val ss = ServerSocket().apply {
                    reuseAddress = true
                    bind(InetSocketAddress(tcpPort))
                }
                serverSocket = ss

                while (isActive && _state.value.role == LanRole.HOST) {
                    val client = ss.accept()
                    client.tcpNoDelay = true
                    tcpSocket = client
                    peerAddress = client.inetAddress
                    tcpWriter = PrintWriter(client.getOutputStream(), true)

                    val wasInMatchOrReconnecting = _state.value.connectionState == LanConnectionState.RECONNECTING ||
                        _state.value.connectionState == LanConnectionState.IN_MATCH

                    if (wasInMatchOrReconnecting) {
                        _state.update {
                            it.copy(
                                connectionState = LanConnectionState.IN_MATCH,
                                peerIp = client.inetAddress.hostAddress.orEmpty(),
                                statusMessage = "Opponent reconnected! Resuming match.",
                                errorMessage = null
                            )
                        }
                        sendTcpLine("${LanProtocol.MSG_HELLO}|$localDeviceName")
                        broadcastLobbyConfigFromHost(currentLobbyConfig)
                        sendTcpLine(LanProtocol.MSG_RECONNECT_OK)
                        onRemotePauseChanged(false)
                        activeSimulation?.let { sim ->
                            activeInputProvider?.let { provider ->
                                startMatchUdpSync(sim, provider)
                            }
                        }
                    } else {
                        _state.update {
                            it.copy(
                                connectionState = LanConnectionState.CONNECTED,
                                peerIp = client.inetAddress.hostAddress.orEmpty(),
                                statusMessage = "Opponent connected! Select teams and press Ready.",
                                errorMessage = null
                            )
                        }
                        sendTcpLine("${LanProtocol.MSG_HELLO}|$localDeviceName")
                        broadcastLobbyConfigFromHost(currentLobbyConfig)
                    }

                    startHeartbeatLoop()
                    readTcpLoop(client)

                    // If client dropped during an active match, pause safely & keep ServerSocket open for reconnection
                    if (_state.value.role == LanRole.HOST && activeSimulation != null) {
                        stopUdpGameLoops()
                        onRemotePauseChanged(true)
                        _state.update {
                            it.copy(
                                connectionState = LanConnectionState.RECONNECTING,
                                statusMessage = "Opponent Wi-Fi dropped. Waiting for safe reconnection…",
                                errorMessage = "Player 2 disconnected — match paused while awaiting reconnection."
                            )
                        }
                    } else if (_state.value.role == LanRole.HOST) {
                        _state.update {
                            it.copy(
                                connectionState = LanConnectionState.WAITING_FOR_CLIENT,
                                clientReady = false,
                                peerName = "",
                                statusMessage = "Opponent left lobby. Waiting for Player 2…"
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                if (_state.value.role == LanRole.HOST) {
                    _state.update {
                        it.copy(
                            connectionState = LanConnectionState.DISCONNECTED,
                            errorMessage = "Host socket closed: ${e.message ?: "Network closed"}"
                        )
                    }
                }
            }
        }
    }

    /**
     * JOIN FLOW: Listens for UDP LAN beacons on port 45677 to auto-discover nearby Host phones.
     */
    fun startScanningForHosts() {
        stopAll()
        val addresses = LocalNetworkUtils.getLocalIpv4Addresses()
        _state.value = LanLobbyUiState(
            role = LanRole.CLIENT,
            connectionState = LanConnectionState.SCANNING,
            localAddresses = addresses,
            statusMessage = "Scanning Wi-Fi / Hotspot for Matchday LAN hosts…"
        )
        multicastLock = LocalNetworkUtils.acquireMulticastLock(context)

        discoveryJob = scope.launch {
            try {
                val sock = DatagramSocket(null).apply {
                    reuseAddress = true
                    broadcast = true
                    bind(InetSocketAddress(LanProtocol.PORT_DISCOVERY))
                    soTimeout = 1000
                }
                udpDiscoverySocket = sock
                val buffer = ByteArray(512)
                val rooms = linkedMapOf<String, DiscoveredHost>()

                while (isActive && _state.value.connectionState == LanConnectionState.SCANNING) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    runCatching {
                        sock.receive(packet)
                        val senderIp = packet.address.hostAddress ?: return@runCatching
                        val host = LanProtocol.decodeDiscoveryBeacon(packet.data, packet.length, senderIp)
                        if (host != null) {
                            rooms[senderIp] = host
                        }
                    }
                    val now = System.currentTimeMillis()
                    rooms.entries.removeAll { now - it.value.lastSeenMs > 5000 }
                    _state.update { it.copy(discoveredHosts = rooms.values.toList()) }
                }
            } catch (e: Exception) {
                if (_state.value.connectionState == LanConnectionState.SCANNING) {
                    _state.update {
                        it.copy(statusMessage = "Auto-scan unavailable on this network; use Manual IP below.")
                    }
                }
            }
        }
    }

    /**
     * JOIN FLOW: Connects directly to a discovered or manually entered Host IPv4 address via TCP.
     */
    fun connectToHost(ipAddress: String, port: Int = LanProtocol.PORT_TCP_LOBBY, isReconnect: Boolean = false) {
        val cleanIp = ipAddress.trim()
        if (cleanIp.isEmpty()) {
            _state.update { it.copy(errorMessage = "Enter a valid Host IPv4 address.") }
            return
        }

        lastConnectedHostIp = cleanIp
        lastConnectedHostPort = port

        discoveryJob?.cancel()
        runCatching { udpDiscoverySocket?.close() }
        udpDiscoverySocket = null

        _state.update {
            it.copy(
                role = LanRole.CLIENT,
                connectionState = if (isReconnect) LanConnectionState.RECONNECTING else LanConnectionState.CONNECTING,
                peerIp = cleanIp,
                statusMessage = if (isReconnect) "Reconnecting to Host ($cleanIp:$port)…" else "Connecting to $cleanIp:$port…",
                errorMessage = null
            )
        }

        tcpJob?.cancel()
        tcpJob = scope.launch {
            try {
                val sock = Socket()
                sock.connect(InetSocketAddress(cleanIp, port), 5000)
                sock.tcpNoDelay = true
                tcpSocket = sock
                peerAddress = sock.inetAddress
                tcpWriter = PrintWriter(sock.getOutputStream(), true)

                _state.update {
                    it.copy(
                        connectionState = if (isReconnect) LanConnectionState.IN_MATCH else LanConnectionState.CONNECTED,
                        statusMessage = if (isReconnect) "Reconnected to live match!" else "Connected to Host! Choose Away team and tap Ready.",
                        errorMessage = null
                    )
                }

                sendTcpLine("${LanProtocol.MSG_HELLO}|$localDeviceName")
                if (isReconnect) {
                    sendTcpLine(LanProtocol.MSG_RECONNECT)
                    activeSimulation?.let { sim ->
                        activeInputProvider?.let { provider ->
                            startMatchUdpSync(sim, provider)
                        }
                    }
                }
                startHeartbeatLoop()
                readTcpLoop(sock)

                // Handle unexpected drop cleanly
                stopUdpGameLoops()
                if (_state.value.role == LanRole.CLIENT) {
                    _state.update {
                        it.copy(
                            connectionState = LanConnectionState.DISCONNECTED,
                            errorMessage = "Wi-Fi connection dropped. Tap Reconnect once back on the Host's Wi-Fi/Hotspot."
                        )
                    }
                }
            } catch (e: Exception) {
                if (isReconnect) {
                    _state.update {
                        it.copy(
                            connectionState = LanConnectionState.DISCONNECTED,
                            errorMessage = "Reconnect failed (${e.message ?: "Host unreachable"}). Tap Reconnect to retry."
                        )
                    }
                } else {
                    _state.update {
                        it.copy(
                            connectionState = LanConnectionState.SCANNING,
                            errorMessage = "Could not reach $cleanIp:$port — verify both phones share the same Wi-Fi or Hotspot."
                        )
                    }
                    startScanningForHosts()
                }
            }
        }
    }

    /**
     * Client triggers safe mid-match reconnection back to the last Host IP without exiting the match.
     */
    fun reconnectLastHost() {
        if (_state.value.role == LanRole.CLIENT && lastConnectedHostIp.isNotBlank()) {
            connectToHost(lastConnectedHostIp, lastConnectedHostPort, isReconnect = true)
        }
    }

    fun updateHostLobbyConfig(config: MatchConfig) {
        if (_state.value.role != LanRole.HOST) return
        currentLobbyConfig = config.copy(isLanMultiplayer = true, localControlledSide = TeamSide.HOME)
        broadcastLobbyConfigFromHost(currentLobbyConfig)
    }

    fun updateClientTeamSelection(awayTeamId: String, awayFormation: Formation) {
        if (_state.value.role != LanRole.CLIENT) return
        sendTcpLine("${LanProtocol.MSG_CLIENT_SELECT}|$awayTeamId|${awayFormation.name}")
    }

    fun toggleReady() {
        val cur = _state.value
        if (cur.role == LanRole.HOST) {
            val next = !cur.hostReady
            _state.update { it.copy(hostReady = next) }
            broadcastLobbyConfigFromHost(currentLobbyConfig)
        } else if (cur.role == LanRole.CLIENT) {
            val next = !cur.clientReady
            _state.update { it.copy(clientReady = next) }
            sendTcpLine("${LanProtocol.MSG_READY}|${if (next) "1" else "0"}")
        }
    }

    fun triggerHostMatchStart() {
        if (_state.value.role != LanRole.HOST) return
        val cfg = currentLobbyConfig.copy(isLanMultiplayer = true, localControlledSide = TeamSide.HOME)
        broadcastLobbyConfigFromHost(cfg)
        sendTcpLine(LanProtocol.MSG_MATCH_START)
        _state.update { it.copy(connectionState = LanConnectionState.IN_MATCH) }
        onMatchStartRequested(cfg)
    }

    fun sendPauseToggle(paused: Boolean) {
        sendTcpLine("${LanProtocol.MSG_PAUSE}|${if (paused) "1" else "0"}")
    }

    fun sendRematchRequest() {
        sendTcpLine(LanProtocol.MSG_REMATCH)
    }

    /**
     * Stage 4 Real-Time UDP Gameplay Loop:
     * - HOST: receives Client PlayerInput packets (50Hz) and broadcasts authoritative WorldSnapshots (30Hz)
     *   tagged with lastAckedClientInputSeq for client-side server reconciliation.
     * - CLIENT: sends predicted PlayerInput packets (50Hz) and feeds received WorldSnapshots into SnapshotInterpolator.
     */
    fun startMatchUdpSync(
        simulation: FootballSimulation,
        localInputProvider: () -> PlayerInput,
        localUdpPort: Int = LanProtocol.PORT_UDP_GAME,
        remoteUdpPort: Int = LanProtocol.PORT_UDP_GAME
    ) {
        stopUdpGameLoops()
        activeSimulation = simulation
        activeInputProvider = localInputProvider
        val targetPeer = peerAddress ?: return
        val isHost = _state.value.role == LanRole.HOST
        interpolator.reset()
        worldSeqCounter = 0
        lastAckedClientInputSeq = 0

        val sock = runCatching {
            DatagramSocket(null).apply {
                reuseAddress = true
                bind(InetSocketAddress(localUdpPort))
                soTimeout = 400
            }
        }.getOrNull() ?: return
        udpGameSocket = sock

        // UDP Receiver Loop
        udpRecvJob = scope.launch {
            val buf = ByteArray(1500)
            while (isActive) {
                val pkt = DatagramPacket(buf, buf.size)
                runCatching {
                    sock.receive(pkt)
                    if (isHost) {
                        val input = LanProtocol.decodeClientInput(pkt.data, pkt.length)
                        if (input != null && input.seq > lastAckedClientInputSeq) {
                            lastAckedClientInputSeq = input.seq
                            simulation.setLocalInput(TeamSide.AWAY, input)
                        }
                    } else {
                        val snap = LanProtocol.decodeWorldSnapshot(pkt.data, pkt.length, System.currentTimeMillis())
                        if (snap != null) {
                            interpolator.pushSnapshot(snap)
                            _state.update {
                                it.copy(
                                    jitterMs = interpolator.measuredJitterMs,
                                    packetLossPct = interpolator.packetLossPct
                                )
                            }
                        }
                    }
                }
            }
        }

        // UDP Sender Loop
        udpSendJob = scope.launch {
            while (isActive) {
                runCatching {
                    if (isHost) {
                        worldSeqCounter++
                        val world = simulation.captureNetworkWorldState()
                        val data = LanProtocol.encodeWorldSnapshot(
                            seq = worldSeqCounter,
                            lastAckedClientInputSeq = lastAckedClientInputSeq,
                            serverTimeMs = System.currentTimeMillis(),
                            homeGoals = world.homeGoals,
                            awayGoals = world.awayGoals,
                            elapsedSec = world.elapsedSec,
                            half = world.half,
                            phase = world.phase,
                            activeHomeId = world.activeHomeId,
                            activeAwayId = world.activeAwayId,
                            ballX = world.ballX,
                            ballY = world.ballY,
                            ballZ = world.ballZ,
                            ballVx = world.ballVx,
                            ballVy = world.ballVy,
                            ballVz = world.ballVz,
                            ballOwner = world.ballOwner,
                            stats = world.stats,
                            players = world.players
                        )
                        sock.send(DatagramPacket(data, data.size, targetPeer, remoteUdpPort))
                    } else {
                        val input = localInputProvider()
                        val data = LanProtocol.encodeClientInput(input)
                        sock.send(DatagramPacket(data, data.size, targetPeer, remoteUdpPort))
                    }
                }
                delay(if (isHost) 33L else 20L)
            }
        }
    }

    /**
     * Called on Client's 60 FPS render tick to apply time-interpolated + locally predicted state cleanly.
     */
    fun applyInterpolatedFrameToClient(simulation: FootballSimulation, localInput: PlayerInput, dt: Float) {
        interpolator.recordAndPredictLocalInput(localInput, dt)
        val sampled = interpolator.sampleInterpolatedWorld(System.currentTimeMillis()) ?: return
        simulation.applyNetworkSnapshot(
            homeGoals = sampled.homeGoals,
            awayGoals = sampled.awayGoals,
            elapsedSec = sampled.elapsedSec,
            currentHalf = sampled.half,
            currentPhase = sampled.phase,
            homeActiveId = sampled.activeHomeId,
            awayActiveId = sampled.activeAwayId,
            ballX = sampled.ballX,
            ballY = sampled.ballY,
            ballZ = sampled.ballZ,
            ballVx = sampled.ballVx,
            ballVy = sampled.ballVy,
            ballVz = sampled.ballVz,
            ballOwner = sampled.ballOwner,
            playerStates = sampled.players,
            syncedStats = sampled.stats,
            interpAlpha = 0.72f
        )
    }

    fun stopUdpGameLoops() {
        udpRecvJob?.cancel()
        udpSendJob?.cancel()
        runCatching { udpGameSocket?.close() }
        udpGameSocket = null
    }

    private fun broadcastLobbyConfigFromHost(config: MatchConfig) {
        val st = _state.value
        val msg = LanProtocol.encodeLobbySync(
            homeTeamId = config.homeTeamId,
            awayTeamId = config.awayTeamId,
            homeFormation = config.homeFormation,
            awayFormation = config.awayFormation,
            stadium = config.stadium,
            matchMinutes = config.matchMinutes,
            hostReady = st.hostReady,
            clientReady = st.clientReady
        )
        sendTcpLine(msg)
    }

    private fun startHeartbeatLoop() {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (isActive && tcpSocket?.isClosed == false) {
                lastPingSentMs = System.currentTimeMillis()
                sendTcpLine("${LanProtocol.MSG_PING}|$lastPingSentMs")
                delay(1500)
            }
        }
    }

    private fun readTcpLoop(socket: Socket) {
        runCatching {
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            while (true) {
                val line = reader.readLine() ?: break
                handleTcpMessage(line)
            }
        }
    }

    private fun handleTcpMessage(line: String) {
        val parts = line.split("|")
        if (parts.isEmpty()) return
        when (parts[0]) {
            LanProtocol.MSG_HELLO -> {
                val name = parts.getOrElse(1) { "Opponent" }
                _state.update { it.copy(peerName = name) }
            }
            LanProtocol.MSG_PING -> {
                val ts = parts.getOrElse(1) { "0" }
                sendTcpLine("${LanProtocol.MSG_PONG}|$ts")
            }
            LanProtocol.MSG_PONG -> {
                val sent = parts.getOrElse(1) { "0" }.toLongOrNull() ?: 0L
                if (sent > 0L) {
                    val rtt = (System.currentTimeMillis() - sent).toInt().coerceIn(1, 999)
                    _state.update { it.copy(pingMs = rtt) }
                }
            }
            LanProtocol.MSG_CLIENT_SELECT -> {
                if (_state.value.role == LanRole.HOST && parts.size >= 3) {
                    val awayId = parts[1]
                    val awayForm = runCatching { Formation.valueOf(parts[2]) }.getOrDefault(currentLobbyConfig.awayFormation)
                    currentLobbyConfig = currentLobbyConfig.copy(awayTeamId = awayId, awayFormation = awayForm)
                    onLobbyConfigSynced(currentLobbyConfig)
                    broadcastLobbyConfigFromHost(currentLobbyConfig)
                }
            }
            LanProtocol.MSG_READY -> {
                if (_state.value.role == LanRole.HOST && parts.size >= 2) {
                    val ready = parts[1] == "1"
                    _state.update { it.copy(clientReady = ready) }
                    broadcastLobbyConfigFromHost(currentLobbyConfig)
                }
            }
            LanProtocol.MSG_LOBBY_SYNC -> {
                if (_state.value.role == LanRole.CLIENT && parts.size >= 9) {
                    val synced = currentLobbyConfig.copy(
                        homeTeamId = parts[1],
                        awayTeamId = parts[2],
                        homeFormation = runCatching { Formation.valueOf(parts[3]) }.getOrDefault(Formation.F_4_3_3),
                        awayFormation = runCatching { Formation.valueOf(parts[4]) }.getOrDefault(Formation.F_4_4_2),
                        stadium = runCatching { StadiumPreset.valueOf(parts[5]) }.getOrDefault(StadiumPreset.NIGHT_FLOODLIT),
                        matchMinutes = parts[6].toIntOrNull() ?: 5,
                        isLanMultiplayer = true,
                        localControlledSide = TeamSide.AWAY
                    )
                    currentLobbyConfig = synced
                    _state.update {
                        it.copy(
                            hostReady = parts[7] == "1",
                            clientReady = parts[8] == "1"
                        )
                    }
                    onLobbyConfigSynced(synced)
                }
            }
            LanProtocol.MSG_MATCH_START -> {
                if (_state.value.role == LanRole.CLIENT) {
                    _state.update { it.copy(connectionState = LanConnectionState.IN_MATCH) }
                    onMatchStartRequested(currentLobbyConfig.copy(isLanMultiplayer = true, localControlledSide = TeamSide.AWAY))
                }
            }
            LanProtocol.MSG_RECONNECT_OK -> {
                if (_state.value.role == LanRole.CLIENT) {
                    _state.update {
                        it.copy(
                            connectionState = LanConnectionState.IN_MATCH,
                            statusMessage = "Reconnected to live match!",
                            errorMessage = null
                        )
                    }
                    onRemotePauseChanged(false)
                }
            }
            LanProtocol.MSG_PAUSE -> {
                val paused = parts.getOrElse(1) { "0" } == "1"
                onRemotePauseChanged(paused)
            }
            LanProtocol.MSG_REMATCH -> {
                onRematchRequested()
            }
        }
    }

    private fun sendTcpLine(line: String) {
        scope.launch {
            runCatching {
                tcpWriter?.println(line)
                tcpWriter?.flush()
            }
        }
    }

    fun stopAll() {
        stopUdpGameLoops()
        activeSimulation = null
        activeInputProvider = null
        interpolator.reset()
        discoveryJob?.cancel()
        tcpJob?.cancel()
        heartbeatJob?.cancel()
        runCatching { tcpWriter?.close() }
        runCatching { tcpSocket?.close() }
        runCatching { serverSocket?.close() }
        runCatching { udpDiscoverySocket?.close() }
        runCatching {
            if (multicastLock?.isHeld == true) multicastLock?.release()
        }
        tcpWriter = null
        tcpSocket = null
        serverSocket = null
        udpDiscoverySocket = null
        multicastLock = null
        peerAddress = null
        _state.value = LanLobbyUiState(localAddresses = LocalNetworkUtils.getLocalIpv4Addresses())
    }
}
