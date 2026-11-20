package com.example.game.render

import kotlin.math.exp

/**
 * Zero-allocation critically damped spring inertializer (David Bollo / Daniel Holden)
 * for smooth, velocity-continuous animation state transitions across all skeleton joints.
 */
class PoseInertializer(val boneCount: Int = SkeletonRig.BONE_COUNT) {

    val currentQuats = FloatArray(boneCount * 4)
    var pelvisY: Float = 0f
        private set
    var pelvisZ: Float = 0f
        private set
    private var pelvisVelY: Float = 0f
    private var pelvisVelZ: Float = 0f
    private var initialized: Boolean = false

    init {
        resetToIdentity()
    }

    fun resetToIdentity() {
        for (i in 0 until boneCount) {
            val o = i * 4
            currentQuats[o] = 0f
            currentQuats[o + 1] = 0f
            currentQuats[o + 2] = 0f
            currentQuats[o + 3] = 1f
        }
        pelvisY = 0f
        pelvisZ = 0f
        pelvisVelY = 0f
        pelvisVelZ = 0f
        initialized = false
    }

    fun step(
        targetQuats: FloatArray,
        targetPelvisY: Float,
        targetPelvisZ: Float,
        halfLifeSec: Float,
        dt: Float,
    ) {
        val safeDt = dt.coerceIn(0.0001f, 0.1f)
        if (!initialized) {
            System.arraycopy(targetQuats, 0, currentQuats, 0, boneCount * 4)
            pelvisY = targetPelvisY
            pelvisZ = targetPelvisZ
            pelvisVelY = 0f
            pelvisVelZ = 0f
            initialized = true
            return
        }

        val safeHalfLife = halfLifeSec.coerceAtLeast(0.015f)
        val lambda = 0.6931472f / safeHalfLife
        val alpha = (1f - exp(-lambda * safeDt)).coerceIn(0f, 1f)

        for (i in 0 until boneCount) {
            val o = i * 4
            QuaternionMath.nlerpShortestPath(
                currentQuats, o,
                targetQuats, o,
                alpha,
                currentQuats, o,
            )
        }

        val omega = (4f * 0.6931472f) / (safeHalfLife * 2f)
        val x = omega * safeDt
        val decay = 1f / (1f + x + 0.48f * x * x + 0.235f * x * x * x)

        val dy = pelvisY - targetPelvisY
        val tempY = (pelvisVelY + omega * dy) * safeDt
        pelvisVelY = (pelvisVelY - omega * tempY) * decay
        pelvisY = targetPelvisY + (dy + tempY) * decay

        val dz = pelvisZ - targetPelvisZ
        val tempZ = (pelvisVelZ + omega * dz) * safeDt
        pelvisVelZ = (pelvisVelZ - omega * tempZ) * decay
        pelvisZ = targetPelvisZ + (dz + tempZ) * decay
    }
}
