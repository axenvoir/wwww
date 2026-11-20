package com.example

import com.example.game.render.TwoBoneIkSolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI

class TwoBoneIkSolverTest {

    @Test
    fun solveSagittalLegDeg_normalDistance_producesFiniteAndClampedAngles() {
        val outAngles = FloatArray(2)
        TwoBoneIkSolver.solveSagittalLegDeg(
            targetDeltaY = -0.65f,
            targetDeltaZ = 0.15f,
            outAngles = outAngles
        )

        assertTrue("Hip pitch must be finite", outAngles[0].isFinite())
        assertTrue("Knee bend must be finite", outAngles[1].isFinite())
        assertTrue("Hip pitch within [-115, 85]", outAngles[0] in -115f..85f)
        assertTrue("Knee bend within [4, 135]", outAngles[1] in 4f..135f)
    }

    @Test
    fun solveSagittalLegDeg_outOfRangeTooFarAndTooClose_noNanOrInfAndKneeClamped() {
        val outAngles = FloatArray(2)

        // Too far target
        TwoBoneIkSolver.solveSagittalLegDeg(
            targetDeltaY = -5.0f,
            targetDeltaZ = 5.0f,
            outAngles = outAngles
        )
        assertTrue(outAngles[0].isFinite())
        assertTrue(outAngles[1].isFinite())
        assertTrue(outAngles[1] in 4f..135f)

        // Too close (zero) target
        TwoBoneIkSolver.solveSagittalLegDeg(
            targetDeltaY = 0f,
            targetDeltaZ = 0f,
            outAngles = outAngles
        )
        assertTrue(outAngles[0].isFinite())
        assertTrue(outAngles[1].isFinite())
        assertTrue(outAngles[1] in 4f..135f)
    }

    @Test
    fun computePelvisDropForGround_flatAndLowTargets() {
        val flatDrop = TwoBoneIkSolver.computePelvisDropForGround(
            leftFootTargetY = 0.05f,
            rightFootTargetY = 0.05f
        )
        assertEquals(0f, flatDrop, 1e-5f)

        val lowDrop = TwoBoneIkSolver.computePelvisDropForGround(
            leftFootTargetY = -0.20f,
            rightFootTargetY = 0.05f
        )
        assertTrue("Low target must produce negative pelvis drop", lowDrop < 0f)
        assertTrue("Pelvis drop must be clamped >= -0.28f", lowDrop >= -0.28f)
    }

    @Test
    fun footLockBlendWeight_staysWithinZeroToOneRange() {
        val phases = floatArrayOf(
            0f,
            (PI / 2.0).toFloat(),
            PI.toFloat(),
            (3.0 * PI / 2.0).toFloat(),
            (-PI / 2.0).toFloat()
        )
        for (phase in phases) {
            val weight = TwoBoneIkSolver.footLockBlendWeight(phase)
            assertTrue("Weight must be finite", weight.isFinite())
            assertTrue("Weight must be in [0, 1]", weight in 0f..1f)
        }
        assertEquals(1f, TwoBoneIkSolver.footLockBlendWeight((-PI / 2.0).toFloat()), 1e-5f)
        assertEquals(0f, TwoBoneIkSolver.footLockBlendWeight((PI / 2.0).toFloat()), 1e-5f)
    }
}
