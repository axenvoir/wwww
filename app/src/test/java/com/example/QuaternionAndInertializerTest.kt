package com.example

import com.example.game.render.PoseInertializer
import com.example.game.render.QuaternionMath
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QuaternionAndInertializerTest {

    @Test
    fun fromEulerYzxDeg_producesUnitQuaternionAndFiniteTrsMatrix() {
        val angles = listOf(
            floatArrayOf(0f, 0f, 0f),
            floatArrayOf(35f, -70f, 120f),
            floatArrayOf(-90f, 180f, 45f),
            floatArrayOf(179f, -179f, 89f),
        )
        val q = FloatArray(4)
        val mat = FloatArray(16)

        for (euler in angles) {
            QuaternionMath.fromEulerYzxDeg(euler[0], euler[1], euler[2], q, 0)
            val len = sqrt(q[0] * q[0] + q[1] * q[1] + q[2] * q[2] + q[3] * q[3])
            assertTrue("Quaternion components must be finite", q.all { it.isFinite() })
            assertEquals("Quaternion must have unit length", 1f, len, 1e-5f)

            QuaternionMath.toTrsMatrix4x4(1.5f, -2.25f, 3.75f, q, 0, mat, 0)
            assertTrue("TRS matrix must contain only finite values", mat.all { it.isFinite() })
            assertEquals(0f, mat[3], 1e-6f)
            assertEquals(0f, mat[7], 1e-6f)
            assertEquals(0f, mat[11], 1e-6f)
            assertEquals(1.5f, mat[12], 1e-6f)
            assertEquals(-2.25f, mat[13], 1e-6f)
            assertEquals(3.75f, mat[14], 1e-6f)
            assertEquals(1f, mat[15], 1e-6f)
        }
    }

    @Test
    fun slerpAndNlerp_takeShortestPathWhenDotIsNegative() {
        val a = FloatArray(4)
        val b = FloatArray(4)
        val outSlerp = FloatArray(4)
        val outNlerp = FloatArray(4)

        QuaternionMath.fromEulerYzxDeg(0f, 0f, 0f, a, 0)
        QuaternionMath.fromEulerYzxDeg(0f, 30f, 0f, b, 0)

        // Negate b so dot(a, b) < 0 while representing the same 30-degree orientation
        for (i in 0 until 4) {
            b[i] = -b[i]
        }
        val dot = a[0] * b[0] + a[1] * b[1] + a[2] * b[2] + a[3] * b[3]
        assertTrue("Expected negative dot product for antipodal representation", dot < 0f)

        QuaternionMath.slerpShortestPath(a, 0, b, 0, 0.5f, outSlerp, 0)
        QuaternionMath.nlerpShortestPath(a, 0, b, 0, 0.5f, outNlerp, 0)

        val expectedHalfAngleRad = (15.0 * PI / 180.0).toFloat()
        val slerpAngle = QuaternionMath.angleBetweenRad(a, 0, outSlerp, 0)
        val nlerpAngle = QuaternionMath.angleBetweenRad(a, 0, outNlerp, 0)

        assertTrue("Slerp output must be finite", outSlerp.all { it.isFinite() })
        assertTrue("Nlerp output must be finite", outNlerp.all { it.isFinite() })
        assertEquals("Slerp should take shortest path (~15 deg)", expectedHalfAngleRad, slerpAngle, 1e-3f)
        assertEquals("Nlerp should take shortest path (~15 deg)", expectedHalfAngleRad, nlerpAngle, 1e-3f)
    }

    @Test
    fun poseInertializer_smoothDampingOnSudden120DegTransitionAndExtremeDt() {
        val boneCount = 2
        val inertializer = PoseInertializer(boneCount = boneCount)
        val target = FloatArray(boneCount * 4)

        // Initialize at 0 degrees
        for (i in 0 until boneCount) {
            QuaternionMath.fromEulerYzxDeg(0f, 0f, 0f, target, i * 4)
        }
        inertializer.step(target, targetPelvisY = 0f, targetPelvisZ = 0f, halfLifeSec = 0.08f, dt = 1f / 60f)

        // Sudden 120-degree transition
        for (i in 0 until boneCount) {
            QuaternionMath.fromEulerYzxDeg(0f, 120f, 0f, target, i * 4)
        }
        val targetAngleRad = (120.0 * PI / 180.0).toFloat()
        var prevDist = targetAngleRad

        for (stepIdx in 0 until 60) {
            inertializer.step(target, targetPelvisY = 0.5f, targetPelvisZ = -0.3f, halfLifeSec = 0.08f, dt = 1f / 60f)

            assertTrue("pelvisY must be finite", inertializer.pelvisY.isFinite())
            assertTrue("pelvisZ must be finite", inertializer.pelvisZ.isFinite())
            assertTrue("currentQuats must contain no NaN/Inf", inertializer.currentQuats.all { it.isFinite() })

            val dist = QuaternionMath.angleBetweenRad(inertializer.currentQuats, 0, target, 0)
            assertTrue("Angle to target should decrease monotonically (step $stepIdx)", dist <= prevDist + 1e-5f)
            prevDist = dist
        }

        assertTrue("Inertializer should converge near target after 1s", prevDist < 0.02f)
        assertTrue("pelvisY should converge near target", abs(inertializer.pelvisY - 0.5f) < 0.01f)
        assertTrue("pelvisZ should converge near target", abs(inertializer.pelvisZ - (-0.3f)) < 0.01f)

        // Extreme dt and halfLifeSec stability check
        val extremeDts = floatArrayOf(0f, -1f, 1e-6f, 10f, 100f)
        for (dt in extremeDts) {
            inertializer.step(target, targetPelvisY = 1.0f, targetPelvisZ = -1.0f, halfLifeSec = 0f, dt = dt)
            assertTrue("pelvisY must remain finite for dt=$dt", inertializer.pelvisY.isFinite())
            assertTrue("pelvisZ must remain finite for dt=$dt", inertializer.pelvisZ.isFinite())
            assertTrue("currentQuats must remain finite for dt=$dt", inertializer.currentQuats.all { it.isFinite() })
            val len = sqrt(
                inertializer.currentQuats[0] * inertializer.currentQuats[0] +
                    inertializer.currentQuats[1] * inertializer.currentQuats[1] +
                    inertializer.currentQuats[2] * inertializer.currentQuats[2] +
                    inertializer.currentQuats[3] * inertializer.currentQuats[3]
            )
            assertEquals("Quaternion must remain unit length for dt=$dt", 1f, len, 1e-4f)
        }
    }
}
