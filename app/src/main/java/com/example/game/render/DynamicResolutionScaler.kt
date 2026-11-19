package com.example.game.render

class DynamicResolutionScaler(
    private val minScale: Float = 0.75f,
    private val maxScale: Float = 1.0f,
    private val stepDown: Float = 0.05f,
    private val stepUp: Float = 0.05f,
    private val highFrameMsThreshold: Float = 16.8f,
    private val lowFrameMsThreshold: Float = 13.5f,
    private val downStreakLimit: Int = 30,
    private val upStreakLimit: Int = 90,
) {
    var currentScale: Float = maxScale
        private set
    private var highStreak: Int = 0
    private var lowStreak: Int = 0

    fun observeFrameMs(frameMs: Float): Boolean {
        if (frameMs > highFrameMsThreshold) {
            highStreak += 1
            lowStreak = 0
            if (highStreak >= downStreakLimit && currentScale > minScale) {
                currentScale = (currentScale - stepDown).coerceAtLeast(minScale)
                highStreak = 0
                return true
            }
        } else if (frameMs < lowFrameMsThreshold) {
            lowStreak += 1
            highStreak = 0
            if (lowStreak >= upStreakLimit && currentScale < maxScale) {
                currentScale = (currentScale + stepUp).coerceAtMost(maxScale)
                lowStreak = 0
                return true
            }
        } else {
            highStreak = 0
            lowStreak = 0
        }
        return false
    }

    fun scaledWidth(viewportWidth: Int): Int = (viewportWidth * currentScale).toInt().coerceAtLeast(64)
    fun scaledHeight(viewportHeight: Int): Int = (viewportHeight * currentScale).toInt().coerceAtLeast(64)

    fun reset() {
        currentScale = maxScale
        highStreak = 0
        lowStreak = 0
    }
}
