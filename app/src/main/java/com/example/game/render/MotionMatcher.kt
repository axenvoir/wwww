package com.example.game.render

data class MotionFeaturePose(
    val clipIndex: Int,
    val timeSec: Float,
    val speed: Float,
    val yawRate: Float,
    val isDribbling: Boolean,
    val leftFootZ: Float,
    val rightFootZ: Float,
)

class MotionMatcher(
    private val poses: List<MotionFeaturePose> = emptyList(),
    private val switchHysteresis: Float = 0.18f,
) {
    fun selectBestPoseIndex(
        speed: Float,
        yawRate: Float,
        isDribbling: Boolean,
        leftFootZ: Float,
        rightFootZ: Float,
        currentPoseIndex: Int = -1,
    ): Int {
        if (poses.isEmpty()) return -1
        var bestIdx = -1
        var bestCost = Float.MAX_VALUE
        var currentCost = Float.MAX_VALUE

        for (i in poses.indices) {
            val p = poses[i]
            val ds = p.speed - speed
            val dy = (p.yawRate - yawRate) * 1.4f
            val dd = if (p.isDribbling == isDribbling) 0f else 1.8f
            val dl = (p.leftFootZ - leftFootZ) * 2.2f
            val dr = (p.rightFootZ - rightFootZ) * 2.2f
            val cost = ds * ds + dy * dy + dd * dd + dl * dl + dr * dr
            if (i == currentPoseIndex) currentCost = cost
            if (cost < bestCost) {
                bestCost = cost
                bestIdx = i
            }
        }
        if (currentPoseIndex in poses.indices && currentCost <= bestCost + switchHysteresis) {
            return currentPoseIndex
        }
        return bestIdx
    }
}
