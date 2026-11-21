package com.example.game.render

import android.opengl.GLES30
import android.opengl.Matrix
import com.example.game.model.GraphicsQuality
import com.example.game.model.StadiumPreset

/**
 * Zero-allocation OpenGL ES 3.0 real-time depth-only Shadow Map pass with
 * support for both static geometry (goalposts/ball) and 19-bone GPU-skinned players.
 * Gracefully degrades (isReady = false) on unsupported GPUs without crashing.
 */
class ShadowMapPass {

    val lightSpaceMatrix = FloatArray(16)
    private val lightView = FloatArray(16)
    private val lightProj = FloatArray(16)
    private val mvpTmp = FloatArray(16)

    var shadowTextureId: Int = 0
        private set
    var mapSize: Int = 0
        private set
    var isReady: Boolean = false
        private set

    private var fboId: Int = 0
    private var staticProgram: Int = 0
    private var skinnedProgram: Int = 0
    private var uStaticMvp: Int = -1
    private var uSkinnedLightVp: Int = -1
    private var uSkinnedModel: Int = -1
    private var uSkinnedBones: Int = -1

    fun init() {
        staticProgram = SafeShaderCompiler.buildProgramOrZero(SHADOW_STATIC_VERTEX, SHADOW_DEPTH_FRAGMENT)
        skinnedProgram = SafeShaderCompiler.buildProgramOrZero(SHADOW_SKINNED_VERTEX, SHADOW_DEPTH_FRAGMENT)
        if (staticProgram == 0 || skinnedProgram == 0) {
            isReady = false
            return
        }
        uStaticMvp = GLES30.glGetUniformLocation(staticProgram, "uLightMvp")
        uSkinnedLightVp = GLES30.glGetUniformLocation(skinnedProgram, "uLightVp")
        uSkinnedModel = GLES30.glGetUniformLocation(skinnedProgram, "uModelMatrix")
        uSkinnedBones = GLES30.glGetUniformLocation(skinnedProgram, "uBoneMatrices")
        ensureResolution(1024)
    }

    fun ensureResolution(desiredSize: Int) {
        if (staticProgram == 0 || skinnedProgram == 0) {
            isReady = false
            return
        }
        val clamped = desiredSize.coerceIn(256, 2048)
        if (clamped == mapSize && isReady && fboId != 0 && shadowTextureId != 0) return
        releaseFbo()

        val fbos = IntArray(1)
        val texs = IntArray(1)
        GLES30.glGenFramebuffers(1, fbos, 0)
        GLES30.glGenTextures(1, texs, 0)
        fboId = fbos[0]
        shadowTextureId = texs[0]
        if (fboId == 0 || shadowTextureId == 0) {
            releaseFbo()
            return
        }

        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, shadowTextureId)
        GLES30.glTexImage2D(
            GLES30.GL_TEXTURE_2D, 0, GLES30.GL_DEPTH_COMPONENT24,
            clamped, clamped, 0,
            GLES30.GL_DEPTH_COMPONENT, GLES30.GL_UNSIGNED_INT, null
        )
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_NEAREST)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_NEAREST)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)

        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fboId)
        GLES30.glFramebufferTexture2D(
            GLES30.GL_FRAMEBUFFER, GLES30.GL_DEPTH_ATTACHMENT,
            GLES30.GL_TEXTURE_2D, shadowTextureId, 0
        )
        GLES30.glDrawBuffers(1, intArrayOf(GLES30.GL_NONE), 0)
        GLES30.glReadBuffer(GLES30.GL_NONE)

        val status = GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER)
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)

        if (status == GLES30.GL_FRAMEBUFFER_COMPLETE) {
            mapSize = clamped
            isReady = true
        } else {
            releaseFbo()
        }
    }

    /** Pure matrix helper testable on JVM without an active GL context. */
    fun computeLightSpaceMatrix(sunX: Float, sunY: Float, sunZ: Float) {
        val len = kotlin.math.sqrt((sunX * sunX + sunY * sunY + sunZ * sunZ).toDouble()).toFloat().coerceAtLeast(0.001f)
        val lx = (sunX / len) * 62f
        val ly = (sunY / len).coerceAtLeast(0.45f) * 62f
        val lz = (sunZ / len) * 62f
        Matrix.setLookAtM(lightView, 0, lx, ly, lz, 0f, 0f, 0f, 0f, 1f, 0f)
        Matrix.orthoM(lightProj, 0, -60f, 60f, -44f, 44f, 8f, 155f)
        Matrix.multiplyMM(lightSpaceMatrix, 0, lightProj, 0, lightView, 0)
    }

    fun beginShadowPass(stadium: StadiumPreset, quality: GraphicsQuality): Boolean {
        if (!quality.renderShadows || staticProgram == 0 || skinnedProgram == 0) return false
        val targetSize = if (quality == GraphicsQuality.HIGH) 1024 else 512
        ensureResolution(targetSize)
        if (!isReady) return false

        computeLightSpaceMatrix(stadium.sunX, stadium.sunY, stadium.sunZ)
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fboId)
        GLES30.glViewport(0, 0, mapSize, mapSize)
        GLES30.glClear(GLES30.GL_DEPTH_BUFFER_BIT)
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
        GLES30.glEnable(GLES30.GL_POLYGON_OFFSET_FILL)
        GLES30.glPolygonOffset(2.0f, 4.0f)
        return true
    }

    fun bindStaticCasterProgram() {
        GLES30.glUseProgram(staticProgram)
    }

    fun uploadStaticModel(modelMatrix: FloatArray) {
        Matrix.multiplyMM(mvpTmp, 0, lightSpaceMatrix, 0, modelMatrix, 0)
        if (uStaticMvp >= 0) {
            GLES30.glUniformMatrix4fv(uStaticMvp, 1, false, mvpTmp, 0)
        }
    }

    fun bindSkinnedCasterProgram() {
        GLES30.glUseProgram(skinnedProgram)
        if (uSkinnedLightVp >= 0) {
            GLES30.glUniformMatrix4fv(uSkinnedLightVp, 1, false, lightSpaceMatrix, 0)
        }
    }

    fun uploadSkinnedCaster(modelMatrix: FloatArray, boneMatrices: FloatArray) {
        if (uSkinnedModel >= 0) {
            GLES30.glUniformMatrix4fv(uSkinnedModel, 1, false, modelMatrix, 0)
        }
        if (uSkinnedBones >= 0) {
            GLES30.glUniformMatrix4fv(uSkinnedBones, SkeletonRig.BONE_COUNT, false, boneMatrices, 0)
        }
    }

    fun endShadowPass(restoreWidth: Int, restoreHeight: Int) {
        GLES30.glDisable(GLES30.GL_POLYGON_OFFSET_FILL)
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        GLES30.glViewport(0, 0, restoreWidth.coerceAtLeast(1), restoreHeight.coerceAtLeast(1))
    }

    private fun releaseFbo() {
        if (fboId != 0) {
            GLES30.glDeleteFramebuffers(1, intArrayOf(fboId), 0)
            fboId = 0
        }
        if (shadowTextureId != 0) {
            GLES30.glDeleteTextures(1, intArrayOf(shadowTextureId), 0)
            shadowTextureId = 0
        }
        mapSize = 0
        isReady = false
    }

    fun release() {
        releaseFbo()
        if (staticProgram != 0) {
            GLES30.glDeleteProgram(staticProgram)
            staticProgram = 0
        }
        if (skinnedProgram != 0) {
            GLES30.glDeleteProgram(skinnedProgram)
            skinnedProgram = 0
        }
    }

    companion object {
        val SHADOW_STATIC_VERTEX: String = """
            #version 300 es
            layout(location = 0) in vec3 aPos;
            uniform mat4 uLightMvp;
            void main() {
                gl_Position = uLightMvp * vec4(aPos, 1.0);
            }
        """.trimIndent()

        val SHADOW_SKINNED_VERTEX: String = """
            #version 300 es
            layout(location = 0) in vec3 aPos;
            layout(location = 3) in vec4 aJoints;
            layout(location = 4) in vec4 aWeights;
            uniform mat4 uLightVp;
            uniform mat4 uModelMatrix;
            uniform mat4 uBoneMatrices[19];
            void main() {
                ivec4 j = ivec4(aJoints);
                mat4 skinMat =
                    aWeights.x * uBoneMatrices[j.x] +
                    aWeights.y * uBoneMatrices[j.y] +
                    aWeights.z * uBoneMatrices[j.z] +
                    aWeights.w * uBoneMatrices[j.w];
                vec4 worldPos = uModelMatrix * (skinMat * vec4(aPos, 1.0));
                gl_Position = uLightVp * worldPos;
            }
        """.trimIndent()

        val SHADOW_DEPTH_FRAGMENT: String = """
            #version 300 es
            precision mediump float;
            void main() {
                // Depth-only pass
            }
        """.trimIndent()
    }
}
