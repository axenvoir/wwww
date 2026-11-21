package com.example

import com.example.game.audio.MatchAudioEngine
import com.example.game.render.RealisticPbrShaders
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class AaaFinalPolishTest {

    @Test
    fun skinnedPlayerDqsVertexShaderHasDualQuaternionUniformsAndHemisphereAlignment() {
        val shader = RealisticPbrShaders.SKINNED_PLAYER_DQS_VERTEX
        assertTrue(shader.contains("#version 300 es"))
        assertTrue(shader.contains("uBoneDualReal"))
        assertTrue(shader.contains("uBoneDualDual"))
        assertTrue(shader.contains("uModelMatrix"))
        assertTrue(shader.contains("uVpMatrix"))
        assertTrue(shader.contains("dot("))
    }

    @Test
    fun synthAmbientCrowdLoopProducesExpectedSamplesAndNonZeroPcmAmplitude() {
        val engine = MatchAudioEngine()
        val sampleRate = 22050
        val expectedSamples = (sampleRate * 3.5f).toInt()
        val pcm = engine.synthAmbientCrowdLoop(sampleRate)

        assertTrue(pcm.isNotEmpty())
        assertEquals(expectedSamples, pcm.size)

        val maxAmplitude = pcm.maxOf { abs(it.toInt()) }
        assertTrue("Expected non-zero PCM amplitude, got $maxAmplitude", maxAmplitude > 0)
        engine.release()
    }
}
