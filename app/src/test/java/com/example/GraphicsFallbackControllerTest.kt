package com.example

import com.example.game.model.GraphicsPipelineMode
import com.example.game.render.GraphicsFallbackController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GraphicsFallbackControllerTest {

    @Test
    fun classicModeNeverTriggersFallback() {
        val controller = GraphicsFallbackController(lowFpsThreshold = 45, lowFpsStreakLimit = 5)
        repeat(10) {
            assertFalse(controller.observeFrame(fps = 20, activeMode = GraphicsPipelineMode.CLASSIC))
        }
        assertFalse(controller.fallbackTriggered)
        assertEquals(GraphicsPipelineMode.CLASSIC, controller.effectiveMode(GraphicsPipelineMode.CLASSIC))
    }

    @Test
    fun realisticModeFallsBackAfterSustainedLowFps() {
        val controller = GraphicsFallbackController(lowFpsThreshold = 45, lowFpsStreakLimit = 3)
        assertFalse(controller.observeFrame(fps = 30, activeMode = GraphicsPipelineMode.REALISTIC_PBR))
        assertFalse(controller.observeFrame(fps = 35, activeMode = GraphicsPipelineMode.REALISTIC_PBR))
        assertTrue(controller.observeFrame(fps = 40, activeMode = GraphicsPipelineMode.REALISTIC_PBR))
        assertTrue(controller.fallbackTriggered)
        assertNotNull(controller.fallbackReason)
        assertEquals(GraphicsPipelineMode.CLASSIC, controller.effectiveMode(GraphicsPipelineMode.REALISTIC_PBR))
    }

    @Test
    fun healthyFrameResetsLowFpsStreak() {
        val controller = GraphicsFallbackController(lowFpsThreshold = 45, lowFpsStreakLimit = 3)
        assertFalse(controller.observeFrame(fps = 30, activeMode = GraphicsPipelineMode.REALISTIC_PBR))
        assertFalse(controller.observeFrame(fps = 30, activeMode = GraphicsPipelineMode.REALISTIC_PBR))
        assertFalse(controller.observeFrame(fps = 60, activeMode = GraphicsPipelineMode.REALISTIC_PBR))
        assertFalse(controller.observeFrame(fps = 30, activeMode = GraphicsPipelineMode.REALISTIC_PBR))
        assertFalse(controller.fallbackTriggered)
        assertEquals(GraphicsPipelineMode.REALISTIC_PBR, controller.effectiveMode(GraphicsPipelineMode.REALISTIC_PBR))
    }

    @Test
    fun shaderFailureImmediatelyForcesClassicFallbackAndResetClearsIt() {
        val controller = GraphicsFallbackController()
        controller.reportShaderFailure("PBR fragment compile error")
        assertTrue(controller.fallbackTriggered)
        assertEquals(GraphicsPipelineMode.CLASSIC, controller.effectiveMode(GraphicsPipelineMode.REALISTIC_PBR))
        controller.reset()
        assertFalse(controller.fallbackTriggered)
        assertEquals(GraphicsPipelineMode.REALISTIC_PBR, controller.effectiveMode(GraphicsPipelineMode.REALISTIC_PBR))
    }
}
