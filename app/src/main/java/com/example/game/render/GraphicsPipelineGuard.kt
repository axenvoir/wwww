package com.example.game.render

import android.opengl.GLES30
import android.util.Log
import com.example.game.model.GraphicsPipelineMode

class GraphicsFallbackController(
    private val lowFpsThreshold: Int = 45,
    private val lowFpsStreakLimit: Int = 120,
) {
    private var lowFpsStreakFrames: Int = 0
    var fallbackTriggered: Boolean = false
        private set
    var fallbackReason: String? = null
        private set

    fun effectiveMode(requested: GraphicsPipelineMode): GraphicsPipelineMode {
        return if (fallbackTriggered) GraphicsPipelineMode.CLASSIC else requested
    }

    fun reportShaderFailure(reason: String) {
        if (!fallbackTriggered) {
            fallbackTriggered = true
            fallbackReason = "Shader fallback: $reason"
        }
    }

    fun observeFrame(fps: Int, activeMode: GraphicsPipelineMode): Boolean {
        if (activeMode == GraphicsPipelineMode.CLASSIC || fallbackTriggered) {
            lowFpsStreakFrames = 0
            return false
        }
        if (fps in 1 until lowFpsThreshold) {
            lowFpsStreakFrames += 1
            if (lowFpsStreakFrames >= lowFpsStreakLimit) {
                fallbackTriggered = true
                fallbackReason = "Sustained FPS below $lowFpsThreshold for $lowFpsStreakLimit frames"
                return true
            }
        } else {
            lowFpsStreakFrames = 0
        }
        return false
    }

    fun reset() {
        lowFpsStreakFrames = 0
        fallbackTriggered = false
        fallbackReason = null
    }
}

object SafeShaderCompiler {
    private const val TAG = "SafeShaderCompiler"

    fun buildProgramOrZero(vertexSource: String, fragmentSource: String): Int {
        var vertexShader = 0
        var fragmentShader = 0
        var program = 0
        return try {
            vertexShader = compileShader(GLES30.GL_VERTEX_SHADER, vertexSource)
            if (vertexShader == 0) return 0
            fragmentShader = compileShader(GLES30.GL_FRAGMENT_SHADER, fragmentSource)
            if (fragmentShader == 0) {
                GLES30.glDeleteShader(vertexShader)
                return 0
            }
            program = GLES30.glCreateProgram()
            if (program == 0) {
                GLES30.glDeleteShader(vertexShader)
                GLES30.glDeleteShader(fragmentShader)
                return 0
            }
            GLES30.glAttachShader(program, vertexShader)
            GLES30.glAttachShader(program, fragmentShader)
            GLES30.glLinkProgram(program)

            val linkStatus = IntArray(1)
            GLES30.glGetProgramiv(program, GLES30.GL_LINK_STATUS, linkStatus, 0)
            GLES30.glDeleteShader(vertexShader)
            GLES30.glDeleteShader(fragmentShader)

            if (linkStatus[0] == 0) {
                Log.w(TAG, "Program link failed: ${GLES30.glGetProgramInfoLog(program)}")
                GLES30.glDeleteProgram(program)
                0
            } else {
                program
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Unexpected shader compile error", t)
            if (vertexShader != 0) GLES30.glDeleteShader(vertexShader)
            if (fragmentShader != 0) GLES30.glDeleteShader(fragmentShader)
            if (program != 0) GLES30.glDeleteProgram(program)
            0
        }
    }

    private fun compileShader(type: Int, source: String): Int {
        val shader = GLES30.glCreateShader(type)
        if (shader == 0) return 0
        GLES30.glShaderSource(shader, source)
        GLES30.glCompileShader(shader)
        val compiled = IntArray(1)
        GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, compiled, 0)
        if (compiled[0] == 0) {
            Log.w(TAG, "Shader compile failed: ${GLES30.glGetShaderInfoLog(shader)}")
            GLES30.glDeleteShader(shader)
            return 0
        }
        return shader
    }
}
