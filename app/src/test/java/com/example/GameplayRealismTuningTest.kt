package com.example

import com.example.game.engine.GameplayRealismTuning
import com.example.game.engine.FootballAiController
import com.example.game.model.AiDifficulty
import com.example.game.model.BallState
import com.example.game.model.Formation
import com.example.game.model.PlayerEntity
import com.example.game.model.PlayerRole
import com.example.game.model.TeamSide
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI

class GameplayRealismTuningTest {

    private fun player(
        id: Int,
        side: TeamSide,
        role: PlayerRole,
        x: Float,
        z: Float
    ) = PlayerEntity(
        id = id,
        side = side,
        slotIndex = id % 11,
        shirtNumber = id + 1,
        name = "Player $id",
        role = role,
        skinToneIndex = 0,
        hairToneIndex = 0,
        baseSpeed = 7f,
        x = x,
        z = z
    )

    @Test
    fun turnBrakingFactor_straightRun_returnsOne() {
        val factor = GameplayRealismTuning.turnBrakingFactor(0f, 0f, 7.5f)
        assertEquals(1.0f, factor, 0.0001f)
    }

    @Test
    fun turnBrakingFactor_180DegreeCutAtSpeed_below070() {
        val factor = GameplayRealismTuning.turnBrakingFactor(0f, PI.toFloat(), 7.7f)
        assertTrue("Expected factor < 0.70f, got $factor", factor < 0.70f)
        assertTrue("Expected factor >= 0.58f, got $factor", factor >= 0.58f)
    }

    @Test
    fun lateralBankDegrees_staysWithinBounds() {
        val maxNeg = GameplayRealismTuning.lateralBankDegrees(12f, 1.2f)
        val maxPos = GameplayRealismTuning.lateralBankDegrees(-12f, 1.2f)
        val zero = GameplayRealismTuning.lateralBankDegrees(0f, 1.0f)

        assertTrue(maxNeg in -14f..14f)
        assertTrue(maxPos in -14f..14f)
        assertEquals(-14f, maxNeg, 0.0001f)
        assertEquals(14f, maxPos, 0.0001f)
        assertEquals(0f, zero, 0.0001f)
    }

    @Test
    fun firstTouchKnockDistance_slowBallSmall_fastBallSprintLarge() {
        val slowTouch = GameplayRealismTuning.firstTouchKnockDistance(
            incomingBallSpeed = 2.0f,
            receiverSpeed = 2.0f,
            isSprinting = false,
            stamina = 1.0f,
        )
        val fastSprintTouch = GameplayRealismTuning.firstTouchKnockDistance(
            incomingBallSpeed = 24.0f,
            receiverSpeed = 8.0f,
            isSprinting = true,
            stamina = 0.25f,
        )

        assertTrue(slowTouch in 0.28f..1.30f)
        assertTrue(fastSprintTouch in 0.28f..1.30f)
        assertTrue("Expected fastSprintTouch ($fastSprintTouch) > slowTouch ($slowTouch)", fastSprintTouch > slowTouch)
    }

    @Test
    fun passMagnusSpin_returnsZeroForSlowGroundPass_andClampsLoftedPass() {
        val groundSlow = GameplayRealismTuning.passMagnusSpin(lateralVelocity = 4f, loft = 0.5f, passSpeed = 12f)
        assertEquals(0f, groundSlow, 0.0001f)

        val lofted = GameplayRealismTuning.passMagnusSpin(lateralVelocity = 5f, loft = 5.0f, passSpeed = 20f)
        assertEquals(1.1f, lofted, 0.001f)

        val clampedMax = GameplayRealismTuning.passMagnusSpin(lateralVelocity = 30f, loft = 5.0f, passSpeed = 25f)
        val clampedMin = GameplayRealismTuning.passMagnusSpin(lateralVelocity = -30f, loft = 5.0f, passSpeed = 25f)
        assertEquals(2.2f, clampedMax, 0.0001f)
        assertEquals(-2.2f, clampedMin, 0.0001f)
    }

    @Test
    fun headLookAtYawDegrees_tracksTargetAndClampsTo38Degrees() {
        val straightAhead = GameplayRealismTuning.headLookAtYawDegrees(0f, 0f, 0f, 10f, 0f)
        assertEquals(0f, straightAhead, 0.001f)

        val sharpRight = GameplayRealismTuning.headLookAtYawDegrees(0f, 0f, 0f, 10f, -10f)
        val sharpLeft = GameplayRealismTuning.headLookAtYawDegrees(0f, 0f, 0f, 10f, 10f)
        assertTrue(sharpRight in -38f..38f)
        assertTrue(sharpLeft in -38f..38f)
        assertEquals(-38f, sharpRight, 0.001f)
        assertEquals(38f, sharpLeft, 0.001f)

        val tooClose = GameplayRealismTuning.headLookAtYawDegrees(0f, 0f, 0f, 0.05f, 0.05f)
        assertEquals(0f, tooClose, 0.0001f)
    }

    @Test
    fun goalkeeperPassesToAnUnmarkedDefenderInsteadOfAlwaysClearing() {
        val goalkeeper = player(0, TeamSide.HOME, PlayerRole.GK, -49f, 0f)
        val defender = player(1, TeamSide.HOME, PlayerRole.DF, -35f, 0f)
        val opponent = player(11, TeamSide.AWAY, PlayerRole.FW, 0f, 20f)
        val decision = FootballAiController().computePlayerDecision(
            player = goalkeeper,
            allPlayers = listOf(goalkeeper, defender, opponent),
            ball = BallState(ownerId = goalkeeper.id),
            formation = Formation.F_4_3_3,
            attackDir = 1f,
            difficulty = AiDifficulty.PROFESSIONAL,
            isTeamHumanControlled = false,
            isClosestChaser = false,
            isSecondaryPresser = false
        )

        assertTrue(decision.wantPass)
        assertEquals(1f, decision.aimX, 0.001f)
        assertEquals(0f, decision.aimZ, 0.001f)
        assertTrue(decision.kickPower in 0.28f..0.52f)
    }

    @Test
    fun goalkeeperClearsWhenEveryNearbyOptionIsMarked() {
        val goalkeeper = player(0, TeamSide.HOME, PlayerRole.GK, -49f, 0f)
        val defender = player(1, TeamSide.HOME, PlayerRole.DF, -35f, 0f)
        val opponent = player(11, TeamSide.AWAY, PlayerRole.FW, -36f, 0f)
        val decision = FootballAiController().computePlayerDecision(
            player = goalkeeper,
            allPlayers = listOf(goalkeeper, defender, opponent),
            ball = BallState(ownerId = goalkeeper.id),
            formation = Formation.F_4_3_3,
            attackDir = 1f,
            difficulty = AiDifficulty.PROFESSIONAL,
            isTeamHumanControlled = false,
            isClosestChaser = false,
            isSecondaryPresser = false
        )

        assertTrue(decision.wantPass)
        assertEquals(0.72f, decision.kickPower, 0.001f)
        assertTrue(decision.aimX > 0f)
    }
}
