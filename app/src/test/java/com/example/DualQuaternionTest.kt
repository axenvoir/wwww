package com.example

import com.example.game.render.DualQuaternion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class DualQuaternionTest {

    @Test
    fun pureTranslation_transformsPointAccurately() {
        val identityQuat = floatArrayOf(0f, 0f, 0f, 1f)
        val real = FloatArray(4)
        val dual = FloatArray(4)
        DualQuaternion.fromTranslationAndUnitQuat(
            3f, -4f, 5f,
            identityQuat, 0,
            real, 0,
            dual, 0,
        )

        val outPoint = FloatArray(3)
        DualQuaternion.transformPoint(real, dual, 1f, 2f, -1f, outPoint)

        assertEquals(4f, outPoint[0], 1e-5f)
        assertEquals(-2f, outPoint[1], 1e-5f)
        assertEquals(4f, outPoint[2], 1e-5f)
    }

    @Test
    fun rotation90DegYPlusTranslation_transformsPointAccurately() {
        val halfAngle = (PI / 4.0).toFloat()
        val rotY90 = floatArrayOf(0f, sin(halfAngle), 0f, cos(halfAngle))
        val real = FloatArray(4)
        val dual = FloatArray(4)
        DualQuaternion.fromTranslationAndUnitQuat(
            10f, 20f, 30f,
            rotY90, 0,
            real, 0,
            dual, 0,
        )

        val outPoint = FloatArray(3)
        DualQuaternion.transformPoint(real, dual, 1f, 0f, 0f, outPoint)

        assertEquals(10f, outPoint[0], 1e-5f)
        assertEquals(20f, outPoint[1], 1e-5f)
        assertEquals(29f, outPoint[2], 1e-5f)
    }

    @Test
    fun blendTwo_oppositeHemisphere_noNanOrInfAndPreservesUnitLength() {
        val q0 = floatArrayOf(0f, 0f, 0f, 1f)
        val q1 = floatArrayOf(0f, 0f, 0f, -1f)

        val r0 = FloatArray(4)
        val d0 = FloatArray(4)
        val r1 = FloatArray(4)
        val d1 = FloatArray(4)

        DualQuaternion.fromTranslationAndUnitQuat(0f, 0f, 0f, q0, 0, r0, 0, d0, 0)
        DualQuaternion.fromTranslationAndUnitQuat(4f, 0f, 0f, q1, 0, r1, 0, d1, 0)

        val outReal = FloatArray(4)
        val outDual = FloatArray(4)
        DualQuaternion.blendTwo(r0, d0, 0.5f, r1, d1, 0.5f, outReal, outDual)

        for (i in 0 until 4) {
            assertTrue("outReal[$i] must be finite", outReal[i].isFinite())
            assertTrue("outDual[$i] must be finite", outDual[i].isFinite())
        }

        val realLength = sqrt(
            outReal[0] * outReal[0] +
                outReal[1] * outReal[1] +
                outReal[2] * outReal[2] +
                outReal[3] * outReal[3],
        )
        assertEquals(1f, realLength, 1e-5f)

        val outPoint = FloatArray(3)
        DualQuaternion.transformPoint(outReal, outDual, 0f, 0f, 0f, outPoint)
        assertEquals(2f, outPoint[0], 1e-5f)
        assertEquals(0f, outPoint[1], 1e-5f)
        assertEquals(0f, outPoint[2], 1e-5f)
    }
}
