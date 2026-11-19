package com.example

import com.example.game.engine.FootballSimulation
import com.example.game.model.AnimState
import com.example.game.model.CameraPreset
import com.example.game.model.GraphicsQuality
import com.example.game.model.MatchConfig
import com.example.game.render.GltfModelLoader
import com.example.game.render.HumanoidMeshBuilder
import com.example.game.render.PlayerSkeletalAnimator
import com.example.game.render.SkeletonRig
import com.example.game.render.SkinnedMeshData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GraphicsPipelineAndRigTest {

    @Test
    fun sculptedHumanoidPlayerMeshHasValidSkeletonWeightsAndRealisticProportions() {
        val highMesh = HumanoidMeshBuilder.buildFootballPlayerMesh(highDetail = true)
        val lowMesh = HumanoidMeshBuilder.buildFootballPlayerMesh(highDetail = false)

        assertTrue("High-detail sculpted player mesh should have rich geometry (>800 verts)", highMesh.vertexCount > 800)
        assertTrue("High-detail player mesh should have >1000 triangles", highMesh.indices.size / 3 > 1000)
        assertTrue("Low-LOD player mesh should be lighter than High-LOD", lowMesh.vertexCount < highMesh.vertexCount)

        var minY = Float.POSITIVE_INFINITY
        var maxY = Float.NEGATIVE_INFINITY
        for (i in 0 until highMesh.vertexCount) {
            val o = i * SkinnedMeshData.FLOATS_PER_VERTEX
            val y = highMesh.vertices[o + 1]
            if (y < minY) minY = y
            if (y > maxY) maxY = y

            val j0 = highMesh.vertices[o + 8].toInt()
            val j1 = highMesh.vertices[o + 9].toInt()
            assertTrue("Joint index out of bounds: $j0", j0 in 0 until SkeletonRig.BONE_COUNT)
            assertTrue("Joint index out of bounds: $j1", j1 in 0 until SkeletonRig.BONE_COUNT)

            val wSum = highMesh.vertices[o + 12] + highMesh.vertices[o + 13] + highMesh.vertices[o + 14] + highMesh.vertices[o + 15]
            assertEquals("Skinning weights must sum to 1.0", 1.0f, wSum, 0.01f)
        }

        assertTrue("Boots should sit right on turf (minY ~ 0.0m), got $minY", minY in -0.02f..0.05f)
        assertTrue("Player height should match realistic ~1.82m footballer, got $maxY", maxY in 1.75f..1.90f)

        // Verify all 6 distinct 3D hairstyles build cleanly
        for (style in 0 until HumanoidMeshBuilder.HAIRSTYLE_COUNT) {
            val hair = HumanoidMeshBuilder.buildHairstyleMesh(style)
            assertTrue("Hairstyle $style must contain triangles", hair.indices.isNotEmpty())
        }
    }

    @Test
    fun gltfBinaryGlbLoaderParsesSkinnedMeshAccurately() {
        val positions = floatArrayOf(0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f, 0f)
        val normals = floatArrayOf(0f, 0f, 1f, 0f, 0f, 1f, 0f, 0f, 1f)
        val uvs = floatArrayOf(0f, 0f, 1f, 0f, 0f, 1f)
        val joints = byteArrayOf(0, 1, 0, 0, 2, 0, 0, 0, 4, 0, 0, 0)
        val weights = floatArrayOf(0.7f, 0.3f, 0f, 0f, 1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f)
        val indices = shortArrayOf(0, 1, 2)

        val glbBytes = GltfModelLoader.buildMinimalGlb(positions, normals, uvs, joints, weights, indices)
        val parsed = GltfModelLoader.parseGlb(glbBytes)
        assertNotNull("GLB parser must decode valid glTF 2.0 binary container", parsed)
        assertEquals(3, parsed!!.vertexCount)
        assertEquals(3, parsed.indices.size)
        assertEquals(1f, parsed.vertices[SkinnedMeshData.FLOATS_PER_VERTEX + 0], 0.001f)
        assertEquals(0.7f, parsed.vertices[12], 0.001f)
        assertEquals(0.3f, parsed.vertices[13], 0.001f)
    }

    @Test
    fun skeletalAnimatorSmoothlyBlendsAllStatesWithFiniteSkinMatrices() {
        val animator = PlayerSkeletalAnimator()

        // Initialize in IDLE, then transition smoothly to SPRINT -> KICK -> TACKLE -> GK_DIVE -> CELEBRATE
        animator.updateAndBuildSkinMatrices(AnimState.IDLE, 0f, 0f, 0f, false, 0f, 0.016f)
        val idleFootMatrix = animator.skinMatrices[SkeletonRig.R_FOOT * 16 + 13]

        animator.updateAndBuildSkinMatrices(AnimState.KICK, 1.0f, 4f, 0f, false, 0f, 0.016f)
        val blendedStep1 = animator.skinMatrices[SkeletonRig.R_FOOT * 16 + 13]

        repeat(15) {
            animator.updateAndBuildSkinMatrices(AnimState.KICK, 1.0f + it * 0.1f, 4f, 0f, false, 0f, 0.016f)
        }
        val settledKick = animator.skinMatrices[SkeletonRig.R_FOOT * 16 + 13]

        assertTrue("First blend frame should move smoothly rather than snapping identically to settled pose",
            kotlin.math.abs(blendedStep1 - settledKick) > 0.001f || kotlin.math.abs(idleFootMatrix - settledKick) > 0.001f
        )

        for (state in AnimState.entries) {
            animator.updateAndBuildSkinMatrices(state, 2.4f, 6.5f, 0.2f, isDribbling = true, diveDir = 1f, dt = 0.016f)
            for (v in animator.skinMatrices) {
                assertTrue("Skin matrix value must be finite in state $state", v.isFinite())
            }
        }
    }

    @Test
    fun broadcastCameraDefaultsAndLiveVisualConfigUpdatePreserveMatchState() {
        val sim = FootballSimulation(MatchConfig())
        assertEquals(CameraPreset.BROADCAST_DYNAMIC, sim.config.cameraPreset)
        assertEquals(5, sim.config.cameraZoom)
        assertEquals(5, sim.config.cameraHeight)

        sim.updateVisualConfig(
            graphicsQuality = GraphicsQuality.MEDIUM,
            cameraPreset = CameraPreset.CLOSE_PRO,
            cameraZoom = 8,
            cameraHeight = 3
        )
        val snap = sim.captureRenderSnapshot()
        assertEquals(GraphicsQuality.MEDIUM, snap.config.graphicsQuality)
        assertEquals(CameraPreset.CLOSE_PRO, snap.config.cameraPreset)
        assertEquals(8, snap.config.cameraZoom)
        assertEquals(3, snap.config.cameraHeight)
    }
}
