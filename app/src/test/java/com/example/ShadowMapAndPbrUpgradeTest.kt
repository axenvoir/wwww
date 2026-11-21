package com.example

import com.example.game.render.PostProcessPass
import com.example.game.render.RealisticPbrShaders
import com.example.game.render.ShadowMapPass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
@RunWith(RobolectricTestRunner::class)
class ShadowMapAndPbrUpgradeTest {
@Test
fun computeLightSpaceMatrixProducesFinite16Elements() {
val pass = ShadowMapPass()
pass.computeLightSpaceMatrix(0.4f, 0.8f, 0.3f)
assertEquals(16, pass.lightSpaceMatrix.size)
assertTrue(pass.lightSpaceMatrix.all { it.isFinite() })
}

@Test
fun shadowShadersContainRequiredEntryPoints() {
assertTrue(ShadowMapPass.SHADOW_STATIC_VERTEX.contains("gl_Position"))
assertTrue(ShadowMapPass.SHADOW_SKINNED_VERTEX.contains("gl_Position"))
assertTrue(ShadowMapPass.SHADOW_DEPTH_FRAGMENT.contains("main"))
}

@Test
fun pbrAndPostShadersContainUpgradedFeatures() {
val pitchFrag = RealisticPbrShaders.PITCH_PBR_FRAGMENT
assertTrue(pitchFrag.contains("uniform float uAmbient"))
assertTrue(pitchFrag.contains("uShadowMap"))
assertTrue(pitchFrag.contains("uLightSpaceMatrix"))
assertTrue(pitchFrag.contains("samplePcfShadow"))
assertTrue(pitchFrag.contains("fwidth"))
assertTrue(RealisticPbrShaders.SKINNED_PLAYER_PBR_FRAGMENT.contains("uniform float uAmbient"))
assertTrue(PostProcessPass.POST_FRAGMENT.contains("softKneeBloom"))
}
}
