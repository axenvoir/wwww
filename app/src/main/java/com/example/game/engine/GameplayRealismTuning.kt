package com.example.game.engine

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

object GameplayRealismTuning {

    /**
     * Computes realistic turn-braking speed factor (0.58..1.0) based on the angle
     * between current facing yaw and desired movement yaw. Sharp 90-180 degree cuts
     * smoothly scrub momentum instead of snapping instantaneously.
     */
    fun turnBrakingFactor(currentYawRad: Float, targetYawRad: Float, currentSpeed: Float): Float {
        if (currentSpeed < 1.2f) return 1.0f
        val delta = abs(normalizeAngleRad(targetYawRad - currentYawRad))
        if (delta < 0.35f) return 1.0f
        val sharpness = ((delta - 0.35f) / (PI.toFloat() - 0.35f)).coerceIn(0f, 1f)
        val speedWeight = ((currentSpeed - 1.2f) / 6.5f).coerceIn(0f, 1f)
        return (1.0f - sharpness * speedWeight * 0.42f).coerceIn(0.58f, 1.0f)
    }

    /**
     * Computes lateral body banking angle in degrees (-14..+14) during turns
     * scaled by angular turn rate and running speed.
     */
    fun lateralBankDegrees(yawVelocityRadPerSec: Float, speedRatio: Float): Float {
        val clampedSpeed = speedRatio.coerceIn(0f, 1.2f)
        return (-yawVelocityRadPerSec * 3.8f * clampedSpeed).coerceIn(-14f, 14f)
    }

    /**
     * Computes realistic first-touch knock-on distance (meters) in the player's
     * travel direction when receiving a moving ball.
     */
    fun firstTouchKnockDistance(
        incomingBallSpeed: Float,
        receiverSpeed: Float,
        isSprinting: Boolean,
        stamina: Float,
    ): Float {
        val speedTerm = (incomingBallSpeed * 0.032f).coerceIn(0f, 0.75f)
        val sprintTerm = if (isSprinting) 0.28f else (receiverSpeed * 0.025f).coerceIn(0f, 0.18f)
        val fatigueTerm = ((1f - stamina.coerceIn(0.05f, 1f)) * 0.22f)
        return (0.28f + speedTerm + sprintTerm + fatigueTerm).coerceIn(0.28f, 1.30f)
    }

    /**
     * Computes realistic Magnus lateral spin for lofted or driven passes/crosses.
     */
    fun passMagnusSpin(lateralVelocity: Float, loft: Float, passSpeed: Float): Float {
        if (loft < 1.2f && passSpeed < 19f) return 0f
        val loftWeight = (loft / 5.0f).coerceIn(0.25f, 1.0f)
        return (lateralVelocity * 0.22f * loftWeight).coerceIn(-2.2f, 2.2f)
    }

    /**
     * Computes a realistic head look-at yaw offset (degrees, -38..+38) toward a target
     * point (e.g. the ball) relative to player facing yaw.
     */
    fun headLookAtYawDegrees(
        playerX: Float,
        playerZ: Float,
        facingYawRad: Float,
        targetX: Float,
        targetZ: Float,
    ): Float {
        val dx = targetX - playerX
        val dz = targetZ - playerZ
        if (dx * dx + dz * dz < 0.04f) return 0f
        // The simulation uses yaw 0 for +X and positive yaw toward +Z.
        val desiredYaw = kotlin.math.atan2(dz.toDouble(), dx.toDouble()).toFloat()
        val relRad = normalizeAngleRad(desiredYaw - facingYawRad)
        return (relRad * (180f / PI.toFloat())).coerceIn(-38f, 38f)
    }

    fun normalizeAngleRad(angle: Float): Float {
        if (!angle.isFinite()) return 0f
        var a = angle
        val twoPi = (2.0 * PI).toFloat()
        while (a > PI.toFloat()) a -= twoPi
        while (a < -PI.toFloat()) a += twoPi
        return a
    }
}
