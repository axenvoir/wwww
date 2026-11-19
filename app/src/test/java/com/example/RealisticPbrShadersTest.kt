package com.example

import com.example.game.render.RealisticPbrShaders
import org.junit.Assert.assertTrue
import org.junit.Test

class RealisticPbrShadersTest {
    @Test
    fun pitchPbrShaderContainsRequiredUniformsAndAces() {
        val src = RealisticPbrShaders.PITCH_PBR_FRAGMENT
        assertTrue(src.contains("#version 300 es"))
        assertTrue(src.contains("uGrassTint"))
        assertTrue(src.contains("uFloodlight"))
        assertTrue(src.contains("acesToneMap"))
    }

    @Test
    fun skinnedPlayerPbrShaderContainsRequiredUniformsAndAces() {
        val src = RealisticPbrShaders.SKINNED_PLAYER_PBR_FRAGMENT
        assertTrue(src.contains("#version 300 es"))
        assertTrue(src.contains("uKitAtlas"))
        assertTrue(src.contains("uFloodlight"))
        assertTrue(src.contains("acesToneMap"))
    }

@Test
fun ballPbrShaderContainsRequiredUniformsAndAces() {
val src = RealisticPbrShaders.BALL_PBR_FRAGMENT
assertTrue(src.contains("#version 300 es"))
assertTrue(src.contains("uFloodlight"))
assertTrue(src.contains("uCameraPos"))
assertTrue(src.contains("acesToneMap"))
}
}
