package com.example.game.network

import com.example.game.engine.NetPlayerState
import com.example.game.model.AnimState
import com.example.game.model.Formation
import com.example.game.model.MatchPhase
import com.example.game.model.MatchStats
import com.example.game.model.PlayerInput
import com.example.game.model.StadiumPreset
import java.nio.ByteBuffer

object LanProtocol {
    const val PORT_DISCOVERY = 45677
    const val PORT_TCP_LOBBY = 45678
    const val PORT_UDP_GAME = 45679
    const val DISCOVERY_MAGIC = "MDLAN1"

    // UDP Gameplay Packet Types
    const val PACKET_CLIENT_INPUT: Byte = 1
    const val PACKET_WORLD_SNAPSHOT: Byte = 2

    // TCP Lobby & Reliable Match Control Messages
    const val MSG_HELLO = "HELLO"
    const val MSG_LOBBY_SYNC = "LOBBY_SYNC"
    const val MSG_CLIENT_SELECT = "CLIENT_SELECT"
    const val MSG_READY = "READY"
    const val MSG_MATCH_START = "MATCH_START"
    const val MSG_PAUSE = "PAUSE"
    const val MSG_REMATCH = "REMATCH"
    const val MSG_RECONNECT = "RECONNECT"
    const val MSG_RECONNECT_OK = "RECONNECT_OK"
    const val MSG_PING = "PING"
    const val MSG_PONG = "PONG"

    fun encodeDiscoveryBeacon(hostName: String, hostTeamId: String, tcpPort: Int): ByteArray {
        val safeName = hostName.replace("|", " ").take(24)
        return "$DISCOVERY_MAGIC|$safeName|$hostTeamId|$tcpPort".toByteArray(Charsets.UTF_8)
    }

    fun decodeDiscoveryBeacon(data: ByteArray, length: Int, senderIp: String): DiscoveredHost? {
        val text = String(data, 0, length, Charsets.UTF_8)
        val parts = text.split("|")
        if (parts.size < 4 || parts[0] != DISCOVERY_MAGIC) return null
        val port = parts[3].toIntOrNull() ?: PORT_TCP_LOBBY
        return DiscoveredHost(
            hostName = parts[1],
            ipAddress = senderIp,
            port = port,
            hostTeamId = parts[2],
            lastSeenMs = System.currentTimeMillis()
        )
    }

    fun encodeLobbySync(
        homeTeamId: String,
        awayTeamId: String,
        homeFormation: Formation,
        awayFormation: Formation,
        stadium: StadiumPreset,
        matchMinutes: Int,
        hostReady: Boolean,
        clientReady: Boolean
    ): String {
        return listOf(
            MSG_LOBBY_SYNC,
            homeTeamId,
            awayTeamId,
            homeFormation.name,
            awayFormation.name,
            stadium.name,
            matchMinutes.toString(),
            if (hostReady) "1" else "0",
            if (clientReady) "1" else "0"
        ).joinToString("|")
    }

    fun encodeClientInput(input: PlayerInput): ByteArray {
        val buf = ByteBuffer.allocate(32)
        buf.put(PACKET_CLIENT_INPUT)
        buf.putInt(input.seq)
        buf.putFloat(input.moveX)
        buf.putFloat(input.moveY)
        var flags = 0
        if (input.sprint) flags = flags or 1
        if (input.passPressed) flags = flags or 2
        if (input.throughPressed) flags = flags or 4
        if (input.shootPressed) flags = flags or 8
        if (input.tacklePressed) flags = flags or 16
        if (input.switchPressed) flags = flags or 32
        buf.putInt(flags)
        buf.putFloat(input.passCharge)
        buf.putFloat(input.shootCharge)
        return buf.array()
    }

    fun decodeClientInput(bytes: ByteArray, length: Int): PlayerInput? {
        if (length < 25) return null
        val buf = ByteBuffer.wrap(bytes, 0, length)
        if (buf.get() != PACKET_CLIENT_INPUT) return null
        val seq = buf.int
        val moveX = buf.float
        val moveY = buf.float
        val flags = buf.int
        val passCharge = buf.float
        val shootCharge = buf.float
        return PlayerInput(
            seq = seq,
            moveX = moveX,
            moveY = moveY,
            sprint = (flags and 1) != 0,
            passPressed = (flags and 2) != 0,
            throughPressed = (flags and 4) != 0,
            shootPressed = (flags and 8) != 0,
            tacklePressed = (flags and 16) != 0,
            switchPressed = (flags and 32) != 0,
            passCharge = passCharge,
            shootCharge = shootCharge
        )
    }

    /**
     * Compact authoritative world snapshot (~670 bytes, well inside a single 1500B Wi-Fi MTU).
     * Includes lastAckedInputSeq for client-side server reconciliation, match stats, and all 22 players.
     */
    fun encodeWorldSnapshot(
        seq: Int,
        lastAckedClientInputSeq: Int,
        serverTimeMs: Long,
        homeGoals: Int,
        awayGoals: Int,
        elapsedSec: Float,
        half: Int,
        phase: MatchPhase,
        activeHomeId: Int,
        activeAwayId: Int,
        ballX: Float,
        ballY: Float,
        ballZ: Float,
        ballVx: Float,
        ballVy: Float,
        ballVz: Float,
        ballOwner: Int,
        stats: MatchStats,
        players: List<NetPlayerState>
    ): ByteArray {
        val playerCount = players.size.coerceAtMost(22)
        // Header: 1 + 4*10 + 8 + 4*7 + 2*10 + 4 = 101 bytes
        // Per player: 6 floats (x, z, yaw, stamina, animTime, diveDir) + 1 byte anim = 25 bytes * 22 = 550 bytes
        // Total ~ 655 bytes
        val buf = ByteBuffer.allocate(128 + playerCount * 25)
        buf.put(PACKET_WORLD_SNAPSHOT)
        buf.putInt(seq)
        buf.putInt(lastAckedClientInputSeq)
        buf.putLong(serverTimeMs)
        buf.putInt(homeGoals)
        buf.putInt(awayGoals)
        buf.putFloat(elapsedSec)
        buf.putInt(half)
        buf.putInt(phase.code)
        buf.putInt(activeHomeId)
        buf.putInt(activeAwayId)
        buf.putFloat(ballX)
        buf.putFloat(ballY)
        buf.putFloat(ballZ)
        buf.putFloat(ballVx)
        buf.putFloat(ballVy)
        buf.putFloat(ballVz)
        buf.putInt(ballOwner)

        // Compact match statistics (16-bit unsigned-range shorts)
        buf.putShort(stats.homePossessionPct.toShort())
        buf.putShort(stats.homeShots.toShort())
        buf.putShort(stats.awayShots.toShort())
        buf.putShort(stats.homeShotsOnTarget.toShort())
        buf.putShort(stats.awayShotsOnTarget.toShort())
        buf.putShort(stats.homePasses.toShort())
        buf.putShort(stats.awayPasses.toShort())
        buf.putShort(stats.homeTackles.toShort())
        buf.putShort(stats.awayTackles.toShort())
        buf.putShort(stats.homeFouls.toShort())
        buf.putShort(stats.awayFouls.toShort())

        buf.putInt(playerCount)
        for (i in 0 until playerCount) {
            val p = players[i]
            buf.putFloat(p.x)
            buf.putFloat(p.z)
            buf.putFloat(p.yaw)
            buf.putFloat(p.stamina)
            buf.putFloat(p.animTime)
            buf.putFloat(p.diveDir)
            buf.put(p.animState.code.toByte())
        }
        return buf.array().copyOf(buf.position())
    }

    fun decodeWorldSnapshot(bytes: ByteArray, length: Int, receivedAtMs: Long = System.currentTimeMillis()): DecodedWorldSnapshot? {
        if (length < 100) return null
        val buf = ByteBuffer.wrap(bytes, 0, length)
        if (buf.get() != PACKET_WORLD_SNAPSHOT) return null
        val seq = buf.int
        val ackSeq = buf.int
        val serverTimeMs = buf.long
        val homeGoals = buf.int
        val awayGoals = buf.int
        val elapsedSec = buf.float
        val half = buf.int
        val phase = MatchPhase.fromCode(buf.int)
        val activeHomeId = buf.int
        val activeAwayId = buf.int
        val ballX = buf.float
        val ballY = buf.float
        val ballZ = buf.float
        val ballVx = buf.float
        val ballVy = buf.float
        val ballVz = buf.float
        val ballOwner = buf.int

        val homePossPct = buf.short.toInt().coerceIn(0, 100)
        val stats = MatchStats(
            homePossessionTicks = homePossPct,
            awayPossessionTicks = (100 - homePossPct).coerceAtLeast(0),
            homeShots = buf.short.toInt(),
            awayShots = buf.short.toInt(),
            homeShotsOnTarget = buf.short.toInt(),
            awayShotsOnTarget = buf.short.toInt(),
            homePasses = buf.short.toInt(),
            awayPasses = buf.short.toInt(),
            homeTackles = buf.short.toInt(),
            awayTackles = buf.short.toInt(),
            homeFouls = buf.short.toInt(),
            awayFouls = buf.short.toInt()
        )

        val count = buf.int.coerceIn(0, 22)
        val players = ArrayList<NetPlayerState>(count)
        for (i in 0 until count) {
            if (buf.remaining() < 25) break
            val x = buf.float
            val z = buf.float
            val yaw = buf.float
            val stamina = buf.float
            val animTime = buf.float
            val diveDir = buf.float
            val animState = AnimState.fromCode(buf.get().toInt())
            players.add(NetPlayerState(x, z, yaw, stamina, animState, animTime, diveDir))
        }
        return DecodedWorldSnapshot(
            seq = seq,
            lastAckedClientInputSeq = ackSeq,
            serverTimeMs = serverTimeMs,
            receivedAtMs = receivedAtMs,
            homeGoals = homeGoals,
            awayGoals = awayGoals,
            elapsedSec = elapsedSec,
            half = half,
            phase = phase,
            activeHomeId = activeHomeId,
            activeAwayId = activeAwayId,
            ballX = ballX,
            ballY = ballY,
            ballZ = ballZ,
            ballVx = ballVx,
            ballVy = ballVy,
            ballVz = ballVz,
            ballOwner = ballOwner,
            stats = stats,
            players = players
        )
    }
}

data class DiscoveredHost(
    val hostName: String,
    val ipAddress: String,
    val port: Int,
    val hostTeamId: String,
    val lastSeenMs: Long
)

data class DecodedWorldSnapshot(
    val seq: Int,
    val lastAckedClientInputSeq: Int,
    val serverTimeMs: Long,
    val receivedAtMs: Long,
    val homeGoals: Int,
    val awayGoals: Int,
    val elapsedSec: Float,
    val half: Int,
    val phase: MatchPhase,
    val activeHomeId: Int,
    val activeAwayId: Int,
    val ballX: Float,
    val ballY: Float,
    val ballZ: Float,
    val ballVx: Float,
    val ballVy: Float,
    val ballVz: Float,
    val ballOwner: Int,
    val stats: MatchStats,
    val players: List<NetPlayerState>
)
