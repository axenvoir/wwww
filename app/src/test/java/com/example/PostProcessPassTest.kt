package com.example

import com.example.game.render.PostProcessPass
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PostProcessPassTest {

    @Test
    fun postVertexShaderContainsRequiredDirectives() {
        assertTrue(PostProcessPass.POST_VERTEX.contains("#version 300 es"))
    }

    @Test
    fun postFragmentShaderContainsRequiredUniformsAndToneMap() {
        val frag = PostProcessPass.POST_FRAGMENT
        assertTrue(frag.contains("#version 300 es"))
        assertTrue(frag.contains("uSceneTex"))
        assertTrue(frag.contains("uBloomStrength"))
        assertTrue(frag.contains("uVignetteStrength"))
        assertTrue(frag.contains("acesToneMap"))
    }

    @Test
    fun beginSceneCaptureReturnsFalseWhenNotReady() {
        val pass = PostProcessPass()
        assertFalse(pass.isReady)
        assertFalse(pass.beginSceneCapture())
    }
}
