package com.example.game.render

import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Zero-allocation analytical Two-Bone Leg IK solver using the Law of Cosines
 * for foot locking, ground contact alignment, and plant-foot stability.
 */
object TwoBoneIkSolver {

    private const val RAD_TO_DEG = (180.0 / PI).toFloat()

    // Anatomical segment lengths derived from SkeletonRig bind pose (Y: 0.91 -> 0.49 -> 0.10)
    const val DEFAULT_THIGH_LEN = 0.42f
    const val DEFAULT_SHIN_LEN = 0.39f
    const val ANKLE_GROUND_HEIGHT = 0.10f

    /**
     * Solves sagittal-plane (Y, Z) two-bone leg IK for thigh->shin->ankle.
     * Writes [0] = hipPitchDeg, [1] = kneeBendDeg (anatomically clamped 4..135 deg) into outAngles.
     */
    fun solveSagittalLegDeg(
        targetDeltaY: Float,
        targetDeltaZ: Float,
        thighLen: Float = DEFAULT_THIGH_LEN,
        shinLen: Float = DEFAULT_SHIN_LEN,
        outAngles: FloatArray,
    ) {
        val l1 = thighLen.coerceAtLeast(0.05f)
        val l2 = shinLen.coerceAtLeast(0.05f)
        val maxReach = (l1 + l2) * 0.995f
        val minReach = kotlin.math.abs(l1 - l2) + 0.05f

        val rawDist = hypot(targetDeltaZ.toDouble(), targetDeltaY.toDouble()).toFloat()
        val dist = rawDist.coerceIn(minReach, maxReach)

        // Law of Cosines for internal knee angle
        val cosKnee = ((l1 * l1 + l2 * l2 - dist * dist) / (2f * l1 * l2)).coerceIn(-1f, 1f)
        val interiorKneeRad = acos(cosKnee)
        val kneeBendDeg = (180f - interiorKneeRad * RAD_TO_DEG).coerceIn(4f, 135f)

        // Law of Cosines for hip offset angle
        val cosHipOffset = ((l1 * l1 + dist * dist - l2 * l2) / (2f * l1 * dist)).coerceIn(-1f, 1f)
        val hipOffsetRad = acos(cosHipOffset)

        // Angle from straight-down (-Y) toward forward (+Z)
        val baseAngleRad = atan2(targetDeltaZ.toDouble(), (-targetDeltaY).toDouble()).toFloat()
        val hipPitchDeg = (-(baseAngleRad + hipOffsetRad) * RAD_TO_DEG).coerceIn(-115f, 85f)

        outAngles[0] = if (hipPitchDeg.isFinite()) hipPitchDeg else 0f
        outAngles[1] = if (kneeBendDeg.isFinite()) kneeBendDeg else 8f
    }

    /**
     * Computes pelvis vertical drop (<= 0) so the lower foot can reach groundY without over-stretching.
     */
    fun computePelvisDropForGround(
        leftFootTargetY: Float,
        rightFootTargetY: Float,
        restHipY: Float = 0.91f,
        maxLegReach: Float = DEFAULT_THIGH_LEN + DEFAULT_SHIN_LEN,
    ): Float {
        val lowestAnkle = minOf(leftFootTargetY, rightFootTargetY) + ANKLE_GROUND_HEIGHT
        val requiredLength = restHipY - lowestAnkle
        val excess = requiredLength - maxLegReach * 0.96f
        return if (excess > 0f) -excess.coerceAtMost(0.28f) else 0f
    }

    /**
     * Returns stance-phase foot-lock blend weight (0..1) from gait phase in radians.
     */
    fun footLockBlendWeight(gaitPhaseRad: Float): Float {
        val stance = -sin(gaitPhaseRad)
        return ((stance - 0.15f) / 0.55f).coerceIn(0f, 1f)
    }
}
