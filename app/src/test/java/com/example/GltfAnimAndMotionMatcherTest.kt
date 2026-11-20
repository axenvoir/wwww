package com.example

import com.example.game.render.DualQuaternion
import com.example.game.render.DualQuaternionPalette
import com.example.game.render.GltfAnimationSampler
import com.example.game.render.GltfInterpMode
import com.example.game.render.GltfRotationChannel
import com.example.game.render.MotionFeaturePose
import com.example.game.render.MotionMatcher
import com.example.game.render.SkeletonRig
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class GltfAnimAndMotionMatcherTest {

    @Test
    fun gltfSampler_linearSlerpAndLooping() {
        val halfAngle90 = (PI / 4.0).toFloat()
        val channel = GltfRotationChannel(
            targetBoneIndex = SkeletonRig.SPINE,
            timestamps = floatArrayOf(0f, 1f),
            quaternions = floatArrayOf(
                0f, 0f, 0f, 1f,
                0f, sin(halfAngle90), 0f, cos(halfAngle90),
            ),
            interpolation = GltfInterpMode.LINEAR,
        )
        val out = FloatArray(4)
        GltfAnimationSampler.sampleRotationQuat(channel, 0.5f, loop = false, outQuat = out, outOff = 0)
        val halfAngle45 = (PI / 8.0).toFloat()
        assertEquals(0f, out[0], 1e-4f)
        assertEquals(sin(halfAngle45), out[1], 1e-4f)
        assertEquals(0f, out[2], 1e-4f)
        assertEquals(cos(halfAngle45), out[3], 1e-4f)

        val looped = FloatArray(4)
        GltfAnimationSampler.sampleRotationQuat(channel, 1.5f, loop = true, outQuat = looped, outOff = 0)
        assertEquals(out[0], looped[0], 1e-4f)
        assertEquals(out[1], looped[1], 1e-4f)
        assertEquals(out[2], looped[2], 1e-4f)
        assertEquals(out[3], looped[3], 1e-4f)
    }

    @Test
    fun gltfSampler_stepInterpolation() {
        val halfAngle90 = (PI / 4.0).toFloat()
        val channel = GltfRotationChannel(
            targetBoneIndex = SkeletonRig.HEAD,
            timestamps = floatArrayOf(0f, 1f),
            quaternions = floatArrayOf(
                0f, 0f, 0f, 1f,
                0f, sin(halfAngle90), 0f, cos(halfAngle90),
            ),
            interpolation = GltfInterpMode.STEP,
        )
        val out = FloatArray(4)
        GltfAnimationSampler.sampleRotationQuat(channel, 0.75f, loop = false, outQuat = out, outOff = 0)
        assertEquals(0f, out[0], 1e-4f)
        assertEquals(0f, out[1], 1e-4f)
        assertEquals(0f, out[2], 1e-4f)
        assertEquals(1f, out[3], 1e-4f)
    }

    @Test
    fun gltfSampler_resolveBoneIndexByName_mixamoAndBlender() {
        assertEquals(SkeletonRig.PELVIS, GltfAnimationSampler.resolveBoneIndexByName("mixamorig:Hips"))
        assertEquals(SkeletonRig.L_UPPER_ARM, GltfAnimationSampler.resolveBoneIndexByName("mixamorig:LeftArm"))
        assertEquals(SkeletonRig.R_FOOT, GltfAnimationSampler.resolveBoneIndexByName("right_foot"))
        assertEquals(SkeletonRig.CHEST, GltfAnimationSampler.resolveBoneIndexByName("upper_chest"))
        assertEquals(SkeletonRig.L_SHIN, GltfAnimationSampler.resolveBoneIndexByName("left-calf"))
        assertEquals(-1, GltfAnimationSampler.resolveBoneIndexByName("unknown_bone"))
    }

    @Test
    fun motionMatcher_emptyDatabaseReturnsMinusOne() {
        val matcher = MotionMatcher()
        assertEquals(-1, matcher.selectBestPoseIndex(1f, 0f, false, 0f, 0f))
    }

    @Test
    fun motionMatcher_selectsLowestCostAndRespectsHysteresis() {
        val poses = listOf(
            MotionFeaturePose(0, 0.0f, speed = 0.0f, yawRate = 0.0f, isDribbling = false, leftFootZ = 0.0f, rightFootZ = 0.0f),
            MotionFeaturePose(1, 0.2f, speed = 5.0f, yawRate = 0.1f, isDribbling = true, leftFootZ = 0.3f, rightFootZ = -0.2f),
            MotionFeaturePose(2, 0.4f, speed = 0.2f, yawRate = 0.0f, isDribbling = false, leftFootZ = 0.0f, rightFootZ = 0.0f),
        )
        val matcher = MotionMatcher(poses, switchHysteresis = 0.18f)

        val best = matcher.selectBestPoseIndex(speed = 4.9f, yawRate = 0.1f, isDribbling = true, leftFootZ = 0.3f, rightFootZ = -0.2f)
        assertEquals(1, best)

        val kept = matcher.selectBestPoseIndex(
            speed = 0.15f,
            yawRate = 0.0f,
            isDribbling = false,
            leftFootZ = 0.0f,
            rightFootZ = 0.0f,
            currentPoseIndex = 0,
        )
        assertEquals(0, kept)
    }

    @Test
    fun dualQuaternionPalette_packs4x4TrsAndTransformsPoint() {
        val palette = DualQuaternionPalette(boneCount = 1)
        // Column-major 4x4: +90 deg rotation around Z, translation (3, -2, 5)
        val skinMatrices = floatArrayOf(
            0f, 1f, 0f, 0f,
            -1f, 0f, 0f, 0f,
            0f, 0f, 1f, 0f,
            3f, -2f, 5f, 1f,
        )
        palette.packFromSkinMatrices(skinMatrices)

        val outPt = FloatArray(3)
        DualQuaternion.transformPoint(palette.realQuats, palette.dualQuats, 1f, 0f, 0f, outPt)
        assertEquals(3f, outPt[0], 1e-4f)
        assertEquals(-1f, outPt[1], 1e-4f)
        assertEquals(5f, outPt[2], 1e-4f)
    }
}
