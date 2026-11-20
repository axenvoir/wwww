package com.example.game.render

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos; import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Zero-allocation unit quaternion math utilities matching SkeletonRig's Y -> Z -> X
 * rotation order and Khronos glTF 2.0 right-handed (+Y up, +Z forward) convention.
 * Quaternions are stored in flat FloatArrays as (x, y, z, w) at stride 4.
 */
object QuaternionMath {

    private const val DEG_TO_RAD = (PI / 180.0).toFloat()

    /** Writes unit quaternion for R = Ry(ryDeg) * Rz(rzDeg) * Rx(rxDeg) into out[offset..offset+3]. */
    fun fromEulerYzxDeg(rxDeg: Float, ryDeg: Float, rzDeg: Float, out: FloatArray, offset: Int) {
        val hx = rxDeg * DEG_TO_RAD * 0.5f
        val hy = ryDeg * DEG_TO_RAD * 0.5f
        val hz = rzDeg * DEG_TO_RAD * 0.5f

        val cx = cos(hx); val sx = sin(hx)
        val cy = cos(hy); val sy = sin(hy)
        val cz = cos(hz); val sz = sin(hz)

        // q = qY * qZ * qX
        val qx = cy * cz * sx + sy * sz * cx
        val qy = sy * cz * cx + cy * sz * sx
        val qz = cy * sz * cx - sy * cz * sx
        val qw = cy * cz * cx - sy * sz * sx

        out[offset] = qx
        out[offset + 1] = qy
        out[offset + 2] = qz
        out[offset + 3] = qw
        normalizeInPlace(out, offset)
    }

    fun normalizeInPlace(q: FloatArray, offset: Int) {
        val x = q[offset]; val y = q[offset + 1]; val z = q[offset + 2]; val w = q[offset + 3]
        val lenSq = x * x + y * y + z * z + w * w
        if (lenSq < 1e-12f || !lenSq.isFinite()) {
            q[offset] = 0f; q[offset + 1] = 0f; q[offset + 2] = 0f; q[offset + 3] = 1f
            return
        }
        val inv = 1f / sqrt(lenSq)
        q[offset] = x * inv
        q[offset + 1] = y * inv
        q[offset + 2] = z * inv
        q[offset + 3] = w * inv
    }

    /** Shortest-path normalized linear interpolation (nlerp) between a and b into out. */
    fun nlerpShortestPath(
        a: FloatArray, aOff: Int,
        b: FloatArray, bOff: Int,
        t: Float,
        out: FloatArray, outOff: Int,
    ) {
        val tc = t.coerceIn(0f, 1f)
        val ax = a[aOff]; val ay = a[aOff + 1]; val az = a[aOff + 2]; val aw = a[aOff + 3]
        var bx = b[bOff]; var by = b[bOff + 1]; var bz = b[bOff + 2]; var bw = b[bOff + 3]
        val dot = ax * bx + ay * by + az * bz + aw * bw
        if (dot < 0f) {
            bx = -bx; by = -by; bz = -bz; bw = -bw
        }
        val invT = 1f - tc
        out[outOff] = ax * invT + bx * tc
        out[outOff + 1] = ay * invT + by * tc
        out[outOff + 2] = az * invT + bz * tc
        out[outOff + 3] = aw * invT + bw * tc
        normalizeInPlace(out, outOff)
    }

    /** Shortest-path spherical linear interpolation (slerp) between a and b into out. */
    fun slerpShortestPath(
        a: FloatArray, aOff: Int,
        b: FloatArray, bOff: Int,
        t: Float,
        out: FloatArray, outOff: Int,
    ) {
        val tc = t.coerceIn(0f, 1f)
        val ax = a[aOff]; val ay = a[aOff + 1]; val az = a[aOff + 2]; val aw = a[aOff + 3]
        var bx = b[bOff]; var by = b[bOff + 1]; var bz = b[bOff + 2]; var bw = b[bOff + 3]
        var dot = ax * bx + ay * by + az * bz + aw * bw
        if (dot < 0f) {
            dot = -dot
            bx = -bx; by = -by; bz = -bz; bw = -bw
        }
        if (dot > 0.9995f) {
            nlerpShortestPath(a, aOff, b, bOff, tc, out, outOff)
            return
        }
        val theta0 = acos(dot.coerceIn(-1f, 1f))
        val sinTheta0 = sin(theta0)
        val theta = theta0 * tc
        val s0 = sin(theta0 - theta) / sinTheta0
        val s1 = sin(theta) / sinTheta0
        out[outOff] = ax * s0 + bx * s1
        out[outOff + 1] = ay * s0 + by * s1
        out[outOff + 2] = az * s0 + bz * s1
        out[outOff + 3] = aw * s0 + bw * s1
        normalizeInPlace(out, outOff)
    }

    /** Multiplies unit quaternions out = a * b (supports aliasing with a or b). */
    fun multiply(
        a: FloatArray, aOff: Int,
        b: FloatArray, bOff: Int,
        out: FloatArray, outOff: Int,
    ) {
        val ax = a[aOff]; val ay = a[aOff + 1]; val az = a[aOff + 2]; val aw = a[aOff + 3]
        val bx = b[bOff]; val by = b[bOff + 1]; val bz = b[bOff + 2]; val bw = b[bOff + 3]
        out[outOff] = aw * bx + ax * bw + ay * bz - az * by
        out[outOff + 1] = aw * by - ax * bz + ay * bw + az * bx
        out[outOff + 2] = aw * bz + ax * by - ay * bx + az * bw
        out[outOff + 3] = aw * bw - ax * bx - ay * by - az * bz
    }

    /** Writes a column-major OpenGL 4x4 TRS matrix (translation tx,ty,tz + unit quaternion q). */
    fun toTrsMatrix4x4(
        tx: Float, ty: Float, tz: Float,
        q: FloatArray, qOff: Int,
        outMat: FloatArray, matOff: Int = 0,
    ) {
        val x = q[qOff]; val y = q[qOff + 1]; val z = q[qOff + 2]; val w = q[qOff + 3]
        val xx = x * x; val yy = y * y; val zz = z * z
        val xy = x * y; val xz = x * z; val yz = y * z
        val wx = w * x; val wy = w * y; val wz = w * z

        outMat[matOff + 0] = 1f - 2f * (yy + zz)
        outMat[matOff + 1] = 2f * (xy + wz)
        outMat[matOff + 2] = 2f * (xz - wy)
        outMat[matOff + 3] = 0f

        outMat[matOff + 4] = 2f * (xy - wz)
        outMat[matOff + 5] = 1f - 2f * (xx + zz)
        outMat[matOff + 6] = 2f * (yz + wx)
        outMat[matOff + 7] = 0f

        outMat[matOff + 8] = 2f * (xz + wy)
        outMat[matOff + 9] = 2f * (yz - wx)
        outMat[matOff + 10] = 1f - 2f * (xx + yy)
        outMat[matOff + 11] = 0f

        outMat[matOff + 12] = tx
        outMat[matOff + 13] = ty
        outMat[matOff + 14] = tz
        outMat[matOff + 15] = 1f
    }

    fun angleBetweenRad(a: FloatArray, aOff: Int, b: FloatArray, bOff: Int): Float {
       /*  val dot = abs(
            a[aOff] * b[bOff] +
                a[aOff + 1] * b[bOff + 1] +
                a[aOff + 2] * b[bOff + 2] +
                a[aOff + 3] * b[bOff + 3]
        ).coerceIn(0f, 1f)
       */ val ax = a[aOff]; val ay = a[aOff + 1]; val az = a[aOff + 2]; val aw = a[aOff + 3]; var bx = b[bOff]; var by = b[bOff + 1]; var bz = b[bOff + 2]; var bw = b[bOff + 3]; if (ax * bx + ay * by + az * bz + aw * bw < 0f) { bx = -bx; by = -by; bz = -bz; bw = -bw } val dx = ax - bx; val dy = ay - by; val dz = az - bz; val dw = aw - bw; val sx = ax + bx; val sy = ay + by; val sz = az + bz; val sw = aw + bw; return 4f * atan2(sqrt(dx * dx + dy * dy + dz * dz + dw * dw), sqrt(sx * sx + sy * sy + sz * sz + sw * sw)) //  return 2f * acos(dot)
    }
}
