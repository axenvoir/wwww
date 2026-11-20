package com.example.game.render

import kotlin.math.sqrt

/**
 * Zero-allocation Unit Dual Quaternion Skinning (DQS) math (Kavan et al., 2007).
 * Eliminates Linear Blend Skinning "candy-wrapper" volume collapse on high-twist
 * shoulder, hip, and goalkeeper dive joints while halving per-bone GPU uniform bandwidth.
 * Each dual quaternion is stored as two vec4 arrays: real (x, y, z, w) and dual (x, y, z, w).
 */
object DualQuaternion {

    /**
     * Converts a translation (tx, ty, tz) + unit rotation quaternion q into a unit dual quaternion.
     * real = q, dual = 0.5 * (t_quat * q) where t_quat = (tx, ty, tz, 0).
     */
    fun fromTranslationAndUnitQuat(
        tx: Float, ty: Float, tz: Float,
        q: FloatArray, qOff: Int,
        outReal: FloatArray, rOff: Int,
        outDual: FloatArray, dOff: Int,
    ) {
        val qx = q[qOff]; val qy = q[qOff + 1]; val qz = q[qOff + 2]; val qw = q[qOff + 3]
        outReal[rOff] = qx
        outReal[rOff + 1] = qy
        outReal[rOff + 2] = qz
        outReal[rOff + 3] = qw

        outDual[dOff] = 0.5f * (tx * qw + ty * qz - tz * qy)
        outDual[dOff + 1] = 0.5f * (-tx * qz + ty * qw + tz * qx)
        outDual[dOff + 2] = 0.5f * (tx * qy - ty * qx + tz * qw)
        outDual[dOff + 3] = -0.5f * (tx * qx + ty * qy + tz * qz)
    }

    /**
     * Hemisphere-aligned Dual Quaternion Linear Blending (DLB) of two unit dual quaternions.
     */
    fun blendTwo(
        r0: FloatArray, d0: FloatArray, w0: Float,
        r1: FloatArray, d1: FloatArray, w1: Float,
        outReal: FloatArray, outDual: FloatArray,
    ) {
        val dot = r0[0] * r1[0] + r0[1] * r1[1] + r0[2] * r1[2] + r0[3] * r1[3]
        val sign = if (dot < 0f) -1f else 1f
        val sw1 = w1 * sign

        val rx = r0[0] * w0 + r1[0] * sw1
        val ry = r0[1] * w0 + r1[1] * sw1
        val rz = r0[2] * w0 + r1[2] * sw1
        val rw = r0[3] * w0 + r1[3] * sw1

        val dx = d0[0] * w0 + d1[0] * sw1
        val dy = d0[1] * w0 + d1[1] * sw1
        val dz = d0[2] * w0 + d1[2] * sw1
        val dw = d0[3] * w0 + d1[3] * sw1

        val lenSq = rx * rx + ry * ry + rz * rz + rw * rw
        if (lenSq < 1e-12f || !lenSq.isFinite()) {
            outReal[0] = 0f; outReal[1] = 0f; outReal[2] = 0f; outReal[3] = 1f
            outDual[0] = 0f; outDual[1] = 0f; outDual[2] = 0f; outDual[3] = 0f
            return
        }
        val invLen = 1f / sqrt(lenSq)
        outReal[0] = rx * invLen; outReal[1] = ry * invLen; outReal[2] = rz * invLen; outReal[3] = rw * invLen
        outDual[0] = dx * invLen; outDual[1] = dy * invLen; outDual[2] = dz * invLen; outDual[3] = dw * invLen
    }

    /**
     * Transforms a 3D point (px, py, pz) by a unit dual quaternion (real, dual) into outPoint[0..2].
     */
    fun transformPoint(
        real: FloatArray, dual: FloatArray,
        px: Float, py: Float, pz: Float,
        outPoint: FloatArray,
    ) {
        val rx = real[0]; val ry = real[1]; val rz = real[2]; val rw = real[3]
        val dx = dual[0]; val dy = dual[1]; val dz = dual[2]; val dw = dual[3]

        // Rotate point by real quaternion: p' = p + 2 * cross(r.xyz, cross(r.xyz, p) + rw * p)
        val cx1 = ry * pz - rz * py + rw * px
        val cy1 = rz * px - rx * pz + rw * py
        val cz1 = rx * py - ry * px + rw * pz

        val rotX = px + 2f * (ry * cz1 - rz * cy1)
        val rotY = py + 2f * (rz * cx1 - rx * cz1)
        val rotZ = pz + 2f * (rx * cy1 - ry * cx1)

        // Extract translation: t = 2 * (dual * conjugate(real))
        val tx = 2f * (-dw * rx + dx * rw - dy * rz + dz * ry)
        val ty = 2f * (-dw * ry + dx * rz + dy * rw - dz * rx)
        val tz = 2f * (-dw * rz - dx * ry + dy * rx + dz * rw)

        outPoint[0] = rotX + tx
        outPoint[1] = rotY + ty
        outPoint[2] = rotZ + tz
    }
}
