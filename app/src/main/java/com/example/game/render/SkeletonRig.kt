package com.example.game.render

import android.opengl.Matrix
import com.example.game.model.AnimState
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * 19-bone hierarchical humanoid football skeleton with inverse bind matrices,
 * biomechanical animation poses, and smooth per-player pose cross-fade blending.
 *
 * Coordinate convention in model space:
 *  +Y = up (0.0m = turf sole level, ~1.82m = crown of head)
 *  +Z = forward facing direction
 *  +X = player's left side (right-handed OpenGL coordinate system)
 */
object SkeletonRig {
    const val BONE_COUNT = 19

    const val PELVIS = 0
    const val SPINE = 1
    const val CHEST = 2
    const val NECK = 3
    const val HEAD = 4

    const val L_SHOULDER = 5
    const val L_UPPER_ARM = 6
    const val L_FOREARM = 7
    const val L_HAND = 8

    const val R_SHOULDER = 9
    const val R_UPPER_ARM = 10
    const val R_FOREARM = 11
    const val R_HAND = 12

    const val L_THIGH = 13
    const val L_SHIN = 14
    const val L_FOOT = 15

    const val R_THIGH = 16
    const val R_SHIN = 17
    const val R_FOOT = 18

    val parentIndex = intArrayOf(
        -1,           // 0 PELVIS
        PELVIS,       // 1 SPINE
        SPINE,        // 2 CHEST
        CHEST,        // 3 NECK
        NECK,         // 4 HEAD
        CHEST,        // 5 L_SHOULDER
        L_SHOULDER,   // 6 L_UPPER_ARM
        L_UPPER_ARM,  // 7 L_FOREARM
        L_FOREARM,    // 8 L_HAND
        CHEST,        // 9 R_SHOULDER
        R_SHOULDER,   // 10 R_UPPER_ARM
        R_UPPER_ARM,  // 11 R_FOREARM
        R_FOREARM,    // 12 R_HAND
        PELVIS,       // 13 L_THIGH
        L_THIGH,      // 14 L_SHIN
        L_SHIN,       // 15 L_FOOT
        PELVIS,       // 16 R_THIGH
        R_THIGH,      // 17 R_SHIN
        R_SHIN        // 18 R_FOOT
    )

    // Bind-pose joint world positions (meters, anatomical ~1.82m athletic footballer)
    val bindPosX = floatArrayOf(
        0.00f, 0.00f, 0.00f, 0.00f, 0.00f,
        0.16f, 0.23f, 0.25f, 0.26f,
        -0.16f, -0.23f, -0.25f, -0.26f,
        0.105f, 0.105f, 0.105f,
        -0.105f, -0.105f, -0.105f
    )
    val bindPosY = floatArrayOf(
        0.95f, 1.13f, 1.35f, 1.52f, 1.62f,
        1.45f, 1.41f, 1.13f, 0.87f,
        1.45f, 1.41f, 1.13f, 0.87f,
        0.91f, 0.49f, 0.10f,
        0.91f, 0.49f, 0.10f
    )
    val bindPosZ = floatArrayOf(
        0.00f, 0.00f, 0.01f, 0.01f, 0.02f,
        0.00f, 0.00f, 0.01f, 0.02f,
        0.00f, 0.00f, 0.01f, 0.02f,
        0.00f, 0.00f, 0.02f,
        0.00f, 0.00f, 0.02f
    )

    // Local offset from parent joint in bind pose
    val localOffsetX = FloatArray(BONE_COUNT) { i ->
        if (parentIndex[i] < 0) bindPosX[i] else bindPosX[i] - bindPosX[parentIndex[i]]
    }
    val localOffsetY = FloatArray(BONE_COUNT) { i ->
        if (parentIndex[i] < 0) bindPosY[i] else bindPosY[i] - bindPosY[parentIndex[i]]
    }
    val localOffsetZ = FloatArray(BONE_COUNT) { i ->
        if (parentIndex[i] < 0) bindPosZ[i] else bindPosZ[i] - bindPosZ[parentIndex[i]]
    }

    // Precomputed inverse bind matrices (translation-only bind pose -> inverse is -bindPos)
    val inverseBindMatrices: FloatArray = FloatArray(BONE_COUNT * 16).also { arr ->
        for (i in 0 until BONE_COUNT) {
            Matrix.setIdentityM(arr, i * 16)
            Matrix.translateM(arr, i * 16, -bindPosX[i], -bindPosY[i], -bindPosZ[i])
        }
    }
}

/**
 * Per-player skeletal pose state with smooth cross-fade blending and zero per-frame allocations.
 * Maintains blended bone Euler angles (degrees) and root pelvis offset, then computes the
 * flat 19x16 GPU skinning matrix palette for vertex shader skinning.
 */
class PlayerSkeletalAnimator {

    // Current smoothed pose (degrees per bone)
    private val curRx = FloatArray(SkeletonRig.BONE_COUNT)
    private val curRy = FloatArray(SkeletonRig.BONE_COUNT)
    private val curRz = FloatArray(SkeletonRig.BONE_COUNT)
    private var curPelvisDeltaY = 0f
    private var curPelvisDeltaZ = 0f

    // Target pose scratch buffers
    private val tgtRx = FloatArray(SkeletonRig.BONE_COUNT)
    private val tgtRy = FloatArray(SkeletonRig.BONE_COUNT)
    private val tgtRz = FloatArray(SkeletonRig.BONE_COUNT)

    // Pre-allocated matrix buffers for FK hierarchy & GPU uniform upload
    private val localMat = FloatArray(16)
    private val worldMats = FloatArray(SkeletonRig.BONE_COUNT * 16)
    val skinMatrices = FloatArray(SkeletonRig.BONE_COUNT * 16)

    private var initialized = false
    private var lastState = AnimState.IDLE
    private var kickLocalTime = 0f

    /**
     * Evaluates the target biomechanical football pose for the current animation state,
     * smoothly cross-fades from the previous frame's bone angles, and builds GPU skin matrices.
     */
    fun updateAndBuildSkinMatrices(
        state: AnimState,
        animTime: Float,
        speed: Float,
        playerY: Float,
        isDribbling: Boolean,
        diveDir: Float,
        dt: Float
    ) {
        if (state == AnimState.KICK) {
            if (lastState != AnimState.KICK) kickLocalTime = 0f
            kickLocalTime = (kickLocalTime + dt).coerceAtMost(0.32f)
        } else {
            kickLocalTime = 0f
        }
        lastState = state

        // Reset target pose arrays
        for (i in 0 until SkeletonRig.BONE_COUNT) {
            tgtRx[i] = 0f; tgtRy[i] = 0f; tgtRz[i] = 0f
        }
        var tgtPelvisY = 0f
        var tgtPelvisZ = 0f

        val s = sin(animTime)
        val c = cos(animTime)
        val isAirborne = playerY > 0.14f && state != AnimState.GK_DIVE

        when {
            state == AnimState.GK_DIVE -> {
                val dir = if (diveDir >= 0f) 1f else -1f
                tgtPelvisY = -0.34f
                tgtRz[SkeletonRig.PELVIS] = dir * 82f
                tgtRx[SkeletonRig.PELVIS] = 10f
                tgtRx[SkeletonRig.SPINE] = -6f
                tgtRy[SkeletonRig.HEAD] = -dir * 18f
                // Both gloved arms stretched overhead toward the ball
                tgtRx[SkeletonRig.L_UPPER_ARM] = -168f
                tgtRz[SkeletonRig.L_UPPER_ARM] = 12f
                tgtRx[SkeletonRig.L_FOREARM] = -10f
                tgtRx[SkeletonRig.R_UPPER_ARM] = -168f
                tgtRz[SkeletonRig.R_UPPER_ARM] = -12f
                tgtRx[SkeletonRig.R_FOREARM] = -10f
                // Dynamic scissor legs during dive
                tgtRx[SkeletonRig.L_THIGH] = -18f * dir
                tgtRx[SkeletonRig.L_SHIN] = 28f
                tgtRx[SkeletonRig.R_THIGH] = 22f * dir
                tgtRx[SkeletonRig.R_SHIN] = 36f
            }

            state == AnimState.TACKLE -> {
                // Low sweeping slide tackle: right leg extended forward, left leg tucked beneath
                tgtPelvisY = -0.66f
                tgtPelvisZ = 0.12f
                tgtRx[SkeletonRig.PELVIS] = -58f
                tgtRy[SkeletonRig.PELVIS] = 18f
                tgtRx[SkeletonRig.SPINE] = 14f
                tgtRx[SkeletonRig.CHEST] = 12f
                tgtRx[SkeletonRig.HEAD] = 28f
                // Extended tackling right leg reaching ball
                tgtRx[SkeletonRig.R_THIGH] = -28f
                tgtRx[SkeletonRig.R_SHIN] = 6f
                tgtRx[SkeletonRig.R_FOOT] = 28f
                // Tucked trailing left leg
                tgtRx[SkeletonRig.L_THIGH] = 26f
                tgtRx[SkeletonRig.L_SHIN] = 96f
                // Arms bracing for balance
                tgtRx[SkeletonRig.L_UPPER_ARM] = 38f
                tgtRz[SkeletonRig.L_UPPER_ARM] = 36f
                tgtRx[SkeletonRig.L_FOREARM] = -32f
                tgtRx[SkeletonRig.R_UPPER_ARM] = -42f
                tgtRz[SkeletonRig.R_UPPER_ARM] = -30f
                tgtRx[SkeletonRig.R_FOREARM] = -45f
            }

            state == AnimState.KICK -> {
                // Staged biomechanical kick: windup (0..0.09s) -> contact snap (0.09..0.18s) -> follow-through (0.18..0.32s)
                val t = (kickLocalTime / 0.28f).coerceIn(0f, 1f)
                val swing = sin((t * 1.15f - 0.15f) * PI.toFloat()).coerceIn(-0.6f, 1.0f)
                val thighPitch = if (t < 0.28f) {
                    // Cocking leg back
                    42f * (1f - t / 0.28f) + (-10f) * (t / 0.28f)
                } else {
                    // Whipping leg through ball into follow-through
                    -10f - 66f * sin(((t - 0.28f) / 0.72f).coerceIn(0f, 1f) * (PI.toFloat() * 0.65f))
                }
                val kneeBend = if (t < 0.35f) 84f * (1f - t * 1.8f).coerceAtLeast(0.15f) else 14f

                tgtPelvisY = -0.04f
                tgtRx[SkeletonRig.PELVIS] = -6f * swing
                tgtRy[SkeletonRig.PELVIS] = 24f * swing
                tgtRz[SkeletonRig.PELVIS] = 9f // Lean onto plant leg
                tgtRy[SkeletonRig.SPINE] = -14f * swing
                tgtRy[SkeletonRig.CHEST] = -16f * swing
                tgtRx[SkeletonRig.HEAD] = 10f

                // Planted left leg slightly flexed
                tgtRx[SkeletonRig.L_THIGH] = 18f
                tgtRx[SkeletonRig.L_SHIN] = 26f
                tgtRx[SkeletonRig.L_FOOT] = -10f

                // Striking right leg
                tgtRx[SkeletonRig.R_THIGH] = thighPitch
                tgtRx[SkeletonRig.R_SHIN] = kneeBend
                tgtRx[SkeletonRig.R_FOOT] = 32f

                // Balancing counter-arms
                tgtRx[SkeletonRig.L_UPPER_ARM] = -48f * swing
                tgtRz[SkeletonRig.L_UPPER_ARM] = 44f
                tgtRx[SkeletonRig.L_FOREARM] = -42f
                tgtRx[SkeletonRig.R_UPPER_ARM] = 44f * swing
                tgtRz[SkeletonRig.R_UPPER_ARM] = -24f
                tgtRx[SkeletonRig.R_FOREARM] = -52f
            }

            state == AnimState.CELEBRATE -> {
                val jumpPhase = abs(s)
                tgtPelvisY = jumpPhase * 0.38f
                tgtRx[SkeletonRig.SPINE] = -10f
                tgtRx[SkeletonRig.CHEST] = -10f
                tgtRy[SkeletonRig.CHEST] = s * 16f
                // Raised triumphant arms pumping
                tgtRx[SkeletonRig.L_UPPER_ARM] = -146f + c * 18f
                tgtRz[SkeletonRig.L_UPPER_ARM] = 28f
                tgtRx[SkeletonRig.L_FOREARM] = -36f + s * 14f
                tgtRx[SkeletonRig.R_UPPER_ARM] = -146f - c * 18f
                tgtRz[SkeletonRig.R_UPPER_ARM] = -28f
                tgtRx[SkeletonRig.R_FOREARM] = -36f - s * 14f
                // Energetic knees
                tgtRx[SkeletonRig.L_THIGH] = -24f * jumpPhase
                tgtRx[SkeletonRig.L_SHIN] = 46f * jumpPhase
                tgtRx[SkeletonRig.R_THIGH] = -18f * jumpPhase
                tgtRx[SkeletonRig.R_SHIN] = 38f * jumpPhase
            }

            isAirborne -> {
                // Jump / header airborne arch
                tgtPelvisY = 0.05f
                tgtRx[SkeletonRig.SPINE] = -10f
                tgtRx[SkeletonRig.CHEST] = -8f
                tgtRx[SkeletonRig.L_UPPER_ARM] = -58f
                tgtRz[SkeletonRig.L_UPPER_ARM] = 42f
                tgtRx[SkeletonRig.L_FOREARM] = -52f
                tgtRx[SkeletonRig.R_UPPER_ARM] = -58f
                tgtRz[SkeletonRig.R_UPPER_ARM] = -42f
                tgtRx[SkeletonRig.R_FOREARM] = -52f
                tgtRx[SkeletonRig.L_THIGH] = -34f
                tgtRx[SkeletonRig.L_SHIN] = 68f
                tgtRx[SkeletonRig.R_THIGH] = -22f
                tgtRx[SkeletonRig.R_SHIN] = 52f
            }

            state == AnimState.SPRINT || state == AnimState.RUN || state == AnimState.WALK -> {
                val intensity = when (state) {
                    AnimState.SPRINT -> 1.0f
                    AnimState.RUN -> 0.72f
                    else -> 0.36f
                }
                val dribbleMod = if (isDribbling) 0.82f else 1.0f
                val strideDeg = (18f + 44f * intensity) * dribbleMod
                val forwardLean = (4f + 14f * intensity) + if (isDribbling) 5f else 0f

                // Vertical running bounce (double frequency: peaks mid-flight)
                tgtPelvisY = abs(s) * (0.015f + 0.068f * intensity) - if (isDribbling) 0.045f else 0.02f
                tgtRx[SkeletonRig.PELVIS] = forwardLean * 0.45f
                tgtRy[SkeletonRig.PELVIS] = -s * (5f + 8f * intensity)
                tgtRz[SkeletonRig.PELVIS] = c * 3.5f * intensity

                tgtRx[SkeletonRig.SPINE] = forwardLean * 0.35f
                tgtRy[SkeletonRig.SPINE] = s * (6f + 7f * intensity)
                tgtRx[SkeletonRig.CHEST] = forwardLean * 0.25f
                tgtRy[SkeletonRig.CHEST] = s * (7f + 8f * intensity)
                // Head stays level looking ahead / at ball
                tgtRx[SkeletonRig.HEAD] = -forwardLean * 0.65f + if (isDribbling) 12f else 0f
                tgtRy[SkeletonRig.HEAD] = -s * 5f * intensity

                // Anatomical running leg cycle: thigh swings forward (-X) and back (+X),
                // knee bends deeply during backswing recovery and slightly on stance impact
                val lThigh = s * strideDeg - 6f * intensity
                val rThigh = -s * strideDeg - 6f * intensity
                val lKnee = max(8f, (10f + max(0f, c) * (34f + 56f * intensity)))
                val rKnee = max(8f, (10f + max(0f, -c) * (34f + 56f * intensity)))

                tgtRx[SkeletonRig.L_THIGH] = lThigh
                tgtRx[SkeletonRig.L_SHIN] = lKnee
                tgtRx[SkeletonRig.L_FOOT] = -lKnee * 0.28f + s * 14f * intensity

                tgtRx[SkeletonRig.R_THIGH] = rThigh
                tgtRx[SkeletonRig.R_SHIN] = rKnee
                tgtRx[SkeletonRig.R_FOOT] = -rKnee * 0.28f - s * 14f * intensity

                // Opposite athletic arm drive with bent elbows
                val armSwing = (18f + 44f * intensity) * if (isDribbling) 0.78f else 1.0f
                val armOut = if (isDribbling) 22f else (10f + 6f * intensity)
                val elbowFlex = -(28f + 58f * intensity)

                tgtRx[SkeletonRig.L_UPPER_ARM] = -s * armSwing
                tgtRz[SkeletonRig.L_UPPER_ARM] = armOut
                tgtRx[SkeletonRig.L_FOREARM] = elbowFlex - max(0f, s) * 22f * intensity

                tgtRx[SkeletonRig.R_UPPER_ARM] = s * armSwing
                tgtRz[SkeletonRig.R_UPPER_ARM] = -armOut
                tgtRx[SkeletonRig.R_FOREARM] = elbowFlex - max(0f, -s) * 22f * intensity
            }

            else -> {
                // Athletic IDLE stance: subtle breathing, poised slightly bent knees, relaxed arms
                val breath = sin(animTime * 0.9f)
                tgtPelvisY = -0.025f + breath * 0.008f
                tgtRx[SkeletonRig.PELVIS] = 3f
                tgtRx[SkeletonRig.SPINE] = 3f + breath * 1.2f
                tgtRx[SkeletonRig.CHEST] = 2f
                tgtRx[SkeletonRig.HEAD] = -4f
                tgtRy[SkeletonRig.HEAD] = cos(animTime * 0.5f) * 6f

                tgtRx[SkeletonRig.L_THIGH] = -6f
                tgtRz[SkeletonRig.L_THIGH] = 4f
                tgtRx[SkeletonRig.L_SHIN] = 12f
                tgtRx[SkeletonRig.L_FOOT] = -6f

                tgtRx[SkeletonRig.R_THIGH] = -6f
                tgtRz[SkeletonRig.R_THIGH] = -4f
                tgtRx[SkeletonRig.R_SHIN] = 12f
                tgtRx[SkeletonRig.R_FOOT] = -6f

                tgtRx[SkeletonRig.L_UPPER_ARM] = 6f + breath * 2f
                tgtRz[SkeletonRig.L_UPPER_ARM] = 14f
                tgtRx[SkeletonRig.L_FOREARM] = -24f

                tgtRx[SkeletonRig.R_UPPER_ARM] = 6f - breath * 2f
                tgtRz[SkeletonRig.R_UPPER_ARM] = -14f
                tgtRx[SkeletonRig.R_FOREARM] = -24f
            }
        }

        // Smooth cross-fade blend from current pose to target pose
        val blendSpeed = when (state) {
            AnimState.KICK, AnimState.TACKLE, AnimState.GK_DIVE -> 18.0f
            else -> 11.0f
        }
        val alpha = if (!initialized) 1.0f else (blendSpeed * dt).coerceIn(0.05f, 1.0f)
        initialized = true

        curPelvisDeltaY += (tgtPelvisY - curPelvisDeltaY) * alpha
        curPelvisDeltaZ += (tgtPelvisZ - curPelvisDeltaZ) * alpha
        for (i in 0 until SkeletonRig.BONE_COUNT) {
            curRx[i] += (tgtRx[i] - curRx[i]) * alpha
            curRy[i] += (tgtRy[i] - curRy[i]) * alpha
            curRz[i] += (tgtRz[i] - curRz[i]) * alpha
        }

        // Hierarchical Forward Kinematics -> GPU Skinning Palette
        for (i in 0 until SkeletonRig.BONE_COUNT) {
            Matrix.setIdentityM(localMat, 0)
            val tx = SkeletonRig.localOffsetX[i]
            val ty = SkeletonRig.localOffsetY[i] + if (i == SkeletonRig.PELVIS) curPelvisDeltaY else 0f
            val tz = SkeletonRig.localOffsetZ[i] + if (i == SkeletonRig.PELVIS) curPelvisDeltaZ else 0f
            Matrix.translateM(localMat, 0, tx, ty, tz)
            if (curRy[i] != 0f) Matrix.rotateM(localMat, 0, curRy[i], 0f, 1f, 0f)
            if (curRz[i] != 0f) Matrix.rotateM(localMat, 0, curRz[i], 0f, 0f, 1f)
            if (curRx[i] != 0f) Matrix.rotateM(localMat, 0, curRx[i], 1f, 0f, 0f)

            val parent = SkeletonRig.parentIndex[i]
            val dstOff = i * 16
            if (parent < 0) {
                System.arraycopy(localMat, 0, worldMats, dstOff, 16)
            } else {
                Matrix.multiplyMM(worldMats, dstOff, worldMats, parent * 16, localMat, 0)
            }

            Matrix.multiplyMM(
                skinMatrices, dstOff,
                worldMats, dstOff,
                SkeletonRig.inverseBindMatrices, dstOff
            )
        }
    }
}
