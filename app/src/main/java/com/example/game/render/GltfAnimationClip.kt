package com.example.game.render

enum class GltfInterpMode { LINEAR, STEP }

class GltfRotationChannel(
    val targetBoneIndex: Int,
    val timestamps: FloatArray,
    val quaternions: FloatArray,
    val interpolation: GltfInterpMode = GltfInterpMode.LINEAR,
)

class GltfAnimationClip(
    val name: String,
    val durationSec: Float,
    val rotationChannels: List<GltfRotationChannel>,
)

object GltfAnimationSampler {

    fun sampleRotationQuat(
        channel: GltfRotationChannel,
        timeSec: Float,
        loop: Boolean,
        outQuat: FloatArray,
        outOff: Int,
    ) {
        val times = channel.timestamps
        val quats = channel.quaternions
        val count = times.size
        if (count == 0 || quats.size < count * 4) {
            outQuat[outOff] = 0f; outQuat[outOff + 1] = 0f; outQuat[outOff + 2] = 0f; outQuat[outOff + 3] = 1f
            return
        }
        if (count == 1) {
            copyQuat(quats, 0, outQuat, outOff)
            return
        }

        val firstT = times[0]
        val lastT = times[count - 1]
        val span = (lastT - firstT).coerceAtLeast(1e-5f)
        val t = if (loop) {
            val wrapped = ((timeSec - firstT) % span + span) % span
            firstT + wrapped
        } else {
            timeSec.coerceIn(firstT, lastT)
        }

        var lo = 0
        var hi = count - 1
        while (hi - lo > 1) {
            val mid = (lo + hi) ushr 1
            if (times[mid] <= t) lo = mid else hi = mid
        }

        if (channel.interpolation == GltfInterpMode.STEP) {
            copyQuat(quats, lo * 4, outQuat, outOff)
            return
        }

        val dt = (times[hi] - times[lo]).coerceAtLeast(1e-6f)
        val alpha = ((t - times[lo]) / dt).coerceIn(0f, 1f)
        QuaternionMath.slerpShortestPath(quats, lo * 4, quats, hi * 4, alpha, outQuat, outOff)
    }

    fun resolveBoneIndexByName(rawName: String): Int {
        val n = rawName.lowercase().replace("mixamorig:", "").replace("_", "").replace("-", "").trim()
        return when {
            n == "pelvis" || n == "hips" -> SkeletonRig.PELVIS
            n == "spine" || n == "spine1" -> SkeletonRig.SPINE
            n == "chest" || n == "spine2" || n == "upperchest" -> SkeletonRig.CHEST
            n == "neck" -> SkeletonRig.NECK
            n == "head" -> SkeletonRig.HEAD
            n == "lshoulder" || n == "leftshoulder" -> SkeletonRig.L_SHOULDER
            n == "lupperarm" || n == "leftarm" || n == "leftupperarm" -> SkeletonRig.L_UPPER_ARM
            n == "lforearm" || n == "leftforearm" || n == "leftlowerarm" -> SkeletonRig.L_FOREARM
            n == "lhand" || n == "lefthand" -> SkeletonRig.L_HAND
            n == "rshoulder" || n == "rightshoulder" -> SkeletonRig.R_SHOULDER
            n == "rupperarm" || n == "rightarm" || n == "rightupperarm" -> SkeletonRig.R_UPPER_ARM
            n == "rforearm" || n == "rightforearm" || n == "rightlowerarm" -> SkeletonRig.R_FOREARM
            n == "rhand" || n == "righthand" -> SkeletonRig.R_HAND
            n == "lthigh" || n == "leftupleg" || n == "leftthigh" -> SkeletonRig.L_THIGH
            n == "lshin" || n == "leftleg" || n == "leftshin" || n == "leftcalf" -> SkeletonRig.L_SHIN
            n == "lfoot" || n == "leftfoot" -> SkeletonRig.L_FOOT
            n == "rthigh" || n == "rightupleg" || n == "rightthigh" -> SkeletonRig.R_THIGH
            n == "rshin" || n == "rightleg" || n == "rightshin" || n == "rightcalf" -> SkeletonRig.R_SHIN
            n == "rfoot" || n == "rightfoot" -> SkeletonRig.R_FOOT
            else -> -1
        }
    }

    private fun copyQuat(src: FloatArray, sOff: Int, dst: FloatArray, dOff: Int) {
        dst[dOff] = src[sOff]; dst[dOff + 1] = src[sOff + 1]
        dst[dOff + 2] = src[sOff + 2]; dst[dOff + 3] = src[sOff + 3]
        QuaternionMath.normalizeInPlace(dst, dOff)
    }
}
