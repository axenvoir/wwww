package com.example.game.render

import kotlin.math.sqrt

class DualQuaternionPalette(val boneCount: Int = SkeletonRig.BONE_COUNT) {
    val realQuats = FloatArray(boneCount * 4)
    val dualQuats = FloatArray(boneCount * 4)
    private val tmpQ = FloatArray(4)

    fun packFromSkinMatrices(skinMatrices: FloatArray) {
        val count = minOf(boneCount, skinMatrices.size / 16)
        for (i in 0 until count) {
            val m = i * 16
            matrix3x3ToUnitQuat(skinMatrices, m, tmpQ)
            val tx = skinMatrices[m + 12]
            val ty = skinMatrices[m + 13]
            val tz = skinMatrices[m + 14]
            DualQuaternion.fromTranslationAndUnitQuat(tx, ty, tz, tmpQ, 0, realQuats, i * 4, dualQuats, i * 4)
        }
    }

    private fun matrix3x3ToUnitQuat(mat: FloatArray, m: Int, outQ: FloatArray) {
        val m00 = mat[m + 0]; val m10 = mat[m + 1]; val m20 = mat[m + 2]
        val m01 = mat[m + 4]; val m11 = mat[m + 5]; val m21 = mat[m + 6]
        val m02 = mat[m + 8]; val m12 = mat[m + 9]; val m22 = mat[m + 10]

        val trace = m00 + m11 + m22
        if (trace > 0f) {
            val s = sqrt(trace + 1f) * 2f
            outQ[3] = 0.25f * s
            outQ[0] = (m21 - m12) / s
            outQ[1] = (m02 - m20) / s
            outQ[2] = (m10 - m01) / s
        } else if (m00 > m11 && m00 > m22) {
            val s = sqrt(1f + m00 - m11 - m22) * 2f
            outQ[3] = (m21 - m12) / s
            outQ[0] = 0.25f * s
            outQ[1] = (m01 + m10) / s
            outQ[2] = (m02 + m20) / s
        } else if (m11 > m22) {
            val s = sqrt(1f + m11 - m00 - m22) * 2f
            outQ[3] = (m02 - m20) / s
            outQ[0] = (m01 + m10) / s
            outQ[1] = 0.25f * s
            outQ[2] = (m12 + m21) / s
        } else {
            val s = sqrt(1f + m22 - m00 - m11) * 2f
            outQ[3] = (m10 - m01) / s
            outQ[0] = (m02 + m20) / s
            outQ[1] = (m12 + m21) / s
            outQ[2] = 0.25f * s
        }
        QuaternionMath.normalizeInPlace(outQ, 0)
    }
}
