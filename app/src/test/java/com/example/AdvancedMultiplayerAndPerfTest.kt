package com.example

import com.example.game.engine.FootballSimulation
import com.example.game.engine.NetPlayerState
import com.example.game.engine.PitchConstants
import com.example.game.model.AnimState
import com.example.game.model.MatchConfig
import com.example.game.model.MatchPhase
import com.example.game.model.MatchStats
import com.example.game.model.PlayerInput
import com.example.game.model.TeamSide
import com.example.game.network.DecodedWorldSnapshot
import com.example.game.network.LanProtocol
import com.example.game.network.SnapshotInterpolator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import kotlin.math.PI

class AdvancedMultiplayerAndPerfTest {

    private fun buildSnap(
        seq: Int,
        ackSeq: Int,
        receivedMs: Long,
        ballX: Float,
        ballVx: Float,
        awayStrikerX: Float,
        yaw: Float = 0f
    ): DecodedWorldSnapshot {
        val players = List(22) { idx ->
            if (idx == 20) NetPlayerState(awayStrikerX, 0f, yaw, 1f, AnimState.RUN, seq * 0.2f, 0f)
            else NetPlayerState(idx.toFloat() - 10f, 0f, 0f, 1f, AnimState.IDLE, 0f, 0f)
        }
        return DecodedWorldSnapshot(
            seq = seq,
            lastAckedClientInputSeq = ackSeq,
            serverTimeMs = receivedMs,
            receivedAtMs = receivedMs,
            homeGoals = 1,
            awayGoals = 0,
            elapsedSec = 30f,
            half = 1,
            phase = MatchPhase.PLAYING,
            activeHomeId = 9,
            activeAwayId = 20,
            ballX = ballX,
            ballY = PitchConstants.BALL_RADIUS,
            ballZ = 0f,
            ballVx = ballVx,
            ballVy = 0f,
            ballVz = 0f,
            ballOwner = -1,
            stats = MatchStats(homePossessionTicks = 55, awayPossessionTicks = 45, homeShots = 3, awayShots = 2),
            players = players
        )
    }

    @Test
    fun snapshotInterpolatorBlendsSmoothlyAndTracksPacketLoss() {
        val interp = SnapshotInterpolator()
        interp.pushSnapshot(buildSnap(seq = 1, ackSeq = 0, receivedMs = 1000L, ballX = 0f, ballVx = 10f, awayStrikerX = 5f, yaw = 3.0f))
        // Simulate 1 dropped packet (seq 2 skipped -> seq 3 arrives at 1066ms)
        interp.pushSnapshot(buildSnap(seq = 3, ackSeq = 0, receivedMs = 1066L, ballX = 4f, ballVx = 10f, awayStrikerX = 7f, yaw = -3.0f))

        assertTrue("Packet loss should reflect missing seq 2", interp.packetLossPct > 0)

        // Sample midway between 1000ms and 1066ms (accounting for ~50ms render delay -> nowMs ~ 1083ms)
        val sampled = interp.sampleInterpolatedWorld(nowMs = 1083L)
        assertNotNull(sampled)
        assertTrue("Interpolated ballX should lie strictly between 0 and 4", sampled!!.ballX > 0.2f && sampled.ballX < 3.8f)

        // Shortest-angle lerp across +3.0 rad and -3.0 rad should remain near ±PI, not near 0
        val yaw = sampled.players[20].yaw
        assertTrue("Angle interpolation must take shortest arc across +-PI", kotlin.math.abs(yaw) > 2.6f)
    }

    @Test
    fun clientPredictionAndServerReconciliationConverge() {
        val interp = SnapshotInterpolator()
        interp.pushSnapshot(buildSnap(seq = 1, ackSeq = 0, receivedMs = 1000L, ballX = 0f, ballVx = 0f, awayStrikerX = 10f))

        // Client presses right joystick for 5 frames locally before Host acks
        for (s in 1..5) {
            interp.recordAndPredictLocalInput(PlayerInput(seq = s, moveX = 1f, moveY = 0f, sprint = false), 0.02f)
        }
        val predicted = interp.sampleInterpolatedWorld(nowMs = 1060L)!!
        assertTrue("Predicted Away player X should advance ahead of 10.0 immediately", predicted.players[20].x > 10.5f)

        // Host acknowledges up to seq 5 with authoritative position 10.75f
        interp.pushSnapshot(buildSnap(seq = 2, ackSeq = 5, receivedMs = 1040L, ballX = 0f, ballVx = 0f, awayStrikerX = 10.75f))
        val reconciled = interp.sampleInterpolatedWorld(nowMs = 1100L)!!
        assertEquals(10.75f, reconciled.players[20].x, 0.25f)
    }

    @Test
    fun realLocalLoopbackTcpAndUdpSocketsExchangeLiveMatchPackets() {
        val localhost = InetAddress.getByName("127.0.0.1")

        // 1. Real TCP Lobby Handshake & Safe Reconnect Handshake over Loopback
        val server = ServerSocket(0)
        val tcpPort = server.localPort
        var acceptedLines = mutableListOf<String>()

        val serverThread = Thread {
            val s1 = server.accept()
            val r1 = BufferedReader(InputStreamReader(s1.getInputStream()))
            val w1 = PrintWriter(s1.getOutputStream(), true)
            acceptedLines.add(r1.readLine())
            w1.println(LanProtocol.MSG_MATCH_START)
            s1.close()

            // Accept second connection simulating mid-match reconnection
            val s2 = server.accept()
            val r2 = BufferedReader(InputStreamReader(s2.getInputStream()))
            val w2 = PrintWriter(s2.getOutputStream(), true)
            acceptedLines.add(r2.readLine())
            w2.println(LanProtocol.MSG_RECONNECT_OK)
            s2.close()
        }
        serverThread.start()

        val c1 = Socket(localhost, tcpPort)
        PrintWriter(c1.getOutputStream(), true).println("${LanProtocol.MSG_HELLO}|TestPhone")
        val startMsg = BufferedReader(InputStreamReader(c1.getInputStream())).readLine()
        assertEquals(LanProtocol.MSG_MATCH_START, startMsg)
        c1.close()

        // Simulate reconnect
        val c2 = Socket(localhost, tcpPort)
        PrintWriter(c2.getOutputStream(), true).println(LanProtocol.MSG_RECONNECT)
        val reconMsg = BufferedReader(InputStreamReader(c2.getInputStream())).readLine()
        assertEquals(LanProtocol.MSG_RECONNECT_OK, reconMsg)
        c2.close()

        serverThread.join(2000)
        server.close()
        assertEquals(2, acceptedLines.size)

        // 2. Real UDP Gameplay Packet Round-Trip (Host WorldSnapshot -> Client DatagramSocket)
        val hostUdp = DatagramSocket(0)
        val clientUdp = DatagramSocket(0)
        clientUdp.soTimeout = 1500

        val sim = FootballSimulation(MatchConfig(isLanMultiplayer = true, localControlledSide = TeamSide.HOME))
        val payload = LanProtocol.encodeWorldSnapshot(
            seq = 12,
            lastAckedClientInputSeq = 7,
            serverTimeMs = System.currentTimeMillis(),
            homeGoals = 3,
            awayGoals = 2,
            elapsedSec = 95f,
            half = 2,
            phase = MatchPhase.PLAYING,
            activeHomeId = 9,
            activeAwayId = 20,
            ballX = 5.5f,
            ballY = 0.22f,
            ballZ = -1.2f,
            ballVx = 4f,
            ballVy = 0f,
            ballVz = 1f,
            ballOwner = 9,
            stats = sim.currentStats,
            players = sim.exportNetworkPlayers()
        )
        hostUdp.send(DatagramPacket(payload, payload.size, localhost, clientUdp.localPort))

        val recvBuf = ByteArray(1500)
        val recvPkt = DatagramPacket(recvBuf, recvBuf.size)
        clientUdp.receive(recvPkt)
        val decoded = LanProtocol.decodeWorldSnapshot(recvPkt.data, recvPkt.length)
        hostUdp.close()
        clientUdp.close()

        assertNotNull(decoded)
        assertEquals(12, decoded!!.seq)
        assertEquals(7, decoded.lastAckedClientInputSeq)
        assertEquals(3, decoded.homeGoals)
        assertEquals(2, decoded.awayGoals)
        assertEquals(22, decoded.players.size)
    }

    @Test
    fun simulationCpuBenchmarkSustainsSub3MsPerFrameFor60FpsHeadroom() {
        val sim = FootballSimulation(MatchConfig())
        // Warmup JIT
        repeat(120) { sim.step(PitchConstants.FIXED_DT) }

        val frames = 1200
        val startNs = System.nanoTime()
        repeat(frames) {
            sim.setLocalInput(TeamSide.HOME, PlayerInput(moveX = 0.8f, moveY = 0.2f, sprint = true))
            sim.step(PitchConstants.FIXED_DT)
        }
        val elapsedMs = (System.nanoTime() - startNs) / 1_000_000.0
        val avgMsPerFrame = elapsedMs / frames
        assertTrue(
            "Expected average 11v11 simulation step < 3.0 ms for 60 FPS mobile headroom, got $avgMsPerFrame ms",
            avgMsPerFrame < 3.0
        )
    }
}
