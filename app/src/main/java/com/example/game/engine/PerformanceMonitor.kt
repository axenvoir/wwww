package com.example.game.engine

data class PerfTelemetry(
    val fps: Int = 60,
    val frameTimeMs: Float = 16.6f,
    val simCpuMs: Float = 0.8f,
    val renderSubmitMs: Float = 3.2f,
    val usedMemoryMb: Int = 64,
    val maxMemoryMb: Int = 256
)

/**
 * Lightweight zero-allocation rolling profiler for Stage 6 FPS, CPU, GL submit time, and JVM heap telemetry.
 */
class PerformanceMonitor {
    private var avgFrameMs = 16.6f
    private var avgSimMs = 0.5f
    private var avgRenderMs = 3.0f
    private var frameCounter = 0
    private var cachedMemMb = 64
    private var cachedMaxMb = 256

    fun recordFrame(frameDtSec: Float, simNanos: Long, renderNanos: Long) {
        val frameMs = (frameDtSec * 1000f).coerceIn(1f, 100f)
        val simMs = (simNanos / 1_000_000f).coerceIn(0f, 50f)
        val renderMs = (renderNanos / 1_000_000f).coerceIn(0f, 50f)

        avgFrameMs += (frameMs - avgFrameMs) * 0.08f
        avgSimMs += (simMs - avgSimMs) * 0.10f
        avgRenderMs += (renderMs - avgRenderMs) * 0.10f

        frameCounter++
        if (frameCounter >= 30) {
            frameCounter = 0
            val rt = Runtime.getRuntime()
            cachedMemMb = ((rt.totalMemory() - rt.freeMemory()) / (1024L * 1024L)).toInt()
            cachedMaxMb = (rt.maxMemory() / (1024L * 1024L)).toInt()
        }
    }

    fun snapshot(): PerfTelemetry {
        val fps = (1000f / avgFrameMs.coerceAtLeast(1f)).toInt().coerceIn(1, 120)
        return PerfTelemetry(
            fps = fps,
            frameTimeMs = avgFrameMs,
            simCpuMs = avgSimMs,
            renderSubmitMs = avgRenderMs,
            usedMemoryMb = cachedMemMb,
            maxMemoryMb = cachedMaxMb
        )
    }
}
