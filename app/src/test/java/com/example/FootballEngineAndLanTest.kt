package com.example

import com.example.game.engine.FootballSimulation
import com.example.game.engine.PitchConstants
import com.example.game.model.Formation
import com.example.game.model.MatchConfig
import com.example.game.model.MatchPhase
import com.example.game.model.PlayerInput
import com.example.game.model.TeamSide
import com.example.game.network.LanProtocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FootballEngineAndLanTest {

    @Test
    fun simulationSpawns22PlayersAndTransitionsFromKickoffToPlay() {
        val sim = FootballSimulation(
            MatchConfig(
                homeFormation = Formation.F_4_3_3,
                awayFormation = Formation.F_4_2_3_1
            )
        )
        assertEquals(22, sim.players.size)
        assertEquals(MatchPhase.KICKOFF, sim.phase)

        // Step past kickoff countdown (1.5s)
        repeat(100) { sim.step(PitchConstants.FIXED_DT) }
        assertEquals(MatchPhase.PLAYING, sim.phase)
    }

    @Test
    fun shootingLaunchesBallWithRealisticVelocityAndDetectsGoal() {
        val sim = FootballSimulation(MatchConfig())
        repeat(100) { sim.step(PitchConstants.FIXED_DT) }
        assertEquals(MatchPhase.PLAYING, sim.phase)

        // Position striker in front of Away goal and fire a shot
        val striker = sim.players[sim.activeHomePlayerId]
        striker.x = PitchConstants.HALF_LENGTH - 8f
        striker.z = 0f
        striker.facingYaw = 0f
        striker.actionCooldown = 0f
        sim.ball.ownerId = striker.id
        sim.ball.x = striker.x + 0.6f
        sim.ball.z = 0f

        sim.setLocalInput(
            TeamSide.HOME,
            PlayerInput(moveX = 1f, moveY = 0f, shootPressed = true, shootCharge = 0.85f)
        )
        sim.step(PitchConstants.FIXED_DT)
        assertTrue("Ball should be kicked forward fast", sim.ball.vx > 15f)

        // Clear input and advance simulation to let ball enter goal
        sim.setLocalInput(TeamSide.HOME, PlayerInput())
        // Move away GK aside so deterministic test confirms goal line crossing
        sim.players[11].z = 12f
        repeat(60) { sim.step(PitchConstants.FIXED_DT) }
        assertEquals(1, sim.homeScore)
        assertEquals(MatchPhase.GOAL_CELEBRATION, sim.phase)
    }

    @Test
    fun lanProtocolBinaryCodecsRoundTripAccurately() {
        val input = PlayerInput(
            seq = 42,
            moveX = 0.75f,
            moveY = -0.5f,
            sprint = true,
            passPressed = false,
            throughPressed = true,
            shootPressed = true,
            tacklePressed = false,
            switchPressed = true,
            passCharge = 0.25f,
            shootCharge = 0.90f
        )
        val encodedInput = LanProtocol.encodeClientInput(input)
        val decodedInput = LanProtocol.decodeClientInput(encodedInput, encodedInput.size)
        assertNotNull(decodedInput)
        assertEquals(42, decodedInput!!.seq)
        assertEquals(0.75f, decodedInput.moveX, 0.001f)
        assertEquals(-0.5f, decodedInput.moveY, 0.001f)
        assertTrue(decodedInput.sprint)
        assertTrue(decodedInput.throughPressed)
        assertTrue(decodedInput.shootPressed)
        assertTrue(decodedInput.switchPressed)
        assertEquals(0.90f, decodedInput.shootCharge, 0.001f)

        // Test full 22-player WorldSnapshot binary packet
        val sim = FootballSimulation(MatchConfig())
        val exported = sim.exportNetworkPlayers()
        val worldBytes = LanProtocol.encodeWorldSnapshot(
            seq = 99,
            lastAckedClientInputSeq = 42,
            serverTimeMs = 1700000000000L,
            homeGoals = 2,
            awayGoals = 1,
            elapsedSec = 123.5f,
            half = 2,
            phase = MatchPhase.PLAYING,
            activeHomeId = 9,
            activeAwayId = 20,
            ballX = 12.4f,
            ballY = 0.85f,
            ballZ = -4.2f,
            ballVx = 10f,
            ballVy = 2f,
            ballVz = -3f,
            ballOwner = 9,
            stats = sim.currentStats,
            players = exported
        )
        assertTrue("World packet must fit inside single Ethernet/Wi-Fi MTU (< 1200B)", worldBytes.size < 1200)
        val decodedWorld = LanProtocol.decodeWorldSnapshot(worldBytes, worldBytes.size)
        assertNotNull(decodedWorld)
        assertEquals(99, decodedWorld!!.seq)
        assertEquals(42, decodedWorld.lastAckedClientInputSeq)
        assertEquals(2, decodedWorld.homeGoals)
        assertEquals(1, decodedWorld.awayGoals)
        assertEquals(22, decodedWorld.players.size)
        assertEquals(12.4f, decodedWorld.ballX, 0.001f)

        // Test UDP discovery beacon codec
        val beacon = LanProtocol.encodeDiscoveryBeacon("Pixel 9 Pro", "azure_city", 45678)
        val host = LanProtocol.decodeDiscoveryBeacon(beacon, beacon.size, "192.168.43.1")
        assertNotNull(host)
        assertEquals("Pixel 9 Pro", host!!.hostName)
        assertEquals("192.168.43.1", host.ipAddress)
        assertEquals(45678, host.port)
    }
}
