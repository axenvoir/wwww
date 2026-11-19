package com.example

import com.example.game.render.DynamicResolutionScaler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DynamicResolutionScalerTest {

    @Test
    fun initialScaleIsMax() {
        val scaler = DynamicResolutionScaler()
        assertEquals(1.0f, scaler.currentScale, 0.0001f)
    }

    @Test
    fun scalesDownToMinOnHighFrameTimes() {
        val scaler = DynamicResolutionScaler()
        repeat(29) {
            assertFalse(scaler.observeFrameMs(18.0f))
        }
        assertTrue(scaler.observeFrameMs(18.0f))
        assertEquals(0.95f, scaler.currentScale, 0.001f)

        repeat(200) {
            scaler.observeFrameMs(18.0f)
        }
        assertEquals(0.75f, scaler.currentScale, 0.001f)
    }

    @Test
    fun scalesBackUpToMaxOnFastFrames() {
        val scaler = DynamicResolutionScaler()
        repeat(200) {
            scaler.observeFrameMs(18.0f)
        }
        assertEquals(0.75f, scaler.currentScale, 0.001f)

        repeat(500) {
            scaler.observeFrameMs(10.0f)
        }
        assertEquals(1.0f, scaler.currentScale, 0.001f)
    }

    @Test
    fun calculatesScaledDimensionsCorrectly() {
        val scaler = DynamicResolutionScaler()
        assertEquals(1920, scaler.scaledWidth(1920))
        assertEquals(1080, scaler.scaledHeight(1080))

        repeat(200) {
            scaler.observeFrameMs(18.0f)
        }
        assertEquals(1440, scaler.scaledWidth(1920))
        assertEquals(810, scaler.scaledHeight(1080))
        assertEquals(64, scaler.scaledWidth(50))
        assertEquals(64, scaler.scaledHeight(50))
    }
}
