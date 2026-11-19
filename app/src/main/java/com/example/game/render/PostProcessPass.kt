package com.example.game.render

import android.opengl.GLES30

class PostProcessPass {

    companion object {
        val POST_VERTEX = """
            #version 300 es
            out vec2 vUv;
            void main() {
                vec2 pos = vec2((gl_VertexID << 1) & 2, gl_VertexID & 2);
                vUv = pos;
                gl_Position = vec4(pos * 2.0 - 1.0, 0.0, 1.0);
            }
        """.trimIndent()

        val POST_FRAGMENT = """
            #version 300 es
            precision highp float;

            in vec2 vUv;
            out vec4 fragColor;

            uniform sampler2D uSceneTex;
            uniform vec2 uTexelSize;
            uniform float uBloomStrength;
            uniform float uVignetteStrength;

            vec3 acesToneMap(vec3 x) {
                const float a = 2.51;
                const float b = 0.03;
                const float c = 2.43;
                const float d = 0.59;
                const float e = 0.14;
                return clamp((x * (a * x + b)) / (x * (c * x + d) + e), 0.0, 1.0);
            }

            vec3 sampleBloom(vec2 uv) {
                vec2 offsets[9] = vec2[9](
                    vec2(-1.0, -1.0), vec2(0.0, -1.0), vec2(1.0, -1.0),
                    vec2(-1.0,  0.0), vec2(0.0,  0.0), vec2(1.0,  0.0),
                    vec2(-1.0,  1.0), vec2(0.0,  1.0), vec2(1.0,  1.0)
                );
                float weights[9] = float[9](
                    0.075, 0.125, 0.075,
                    0.125, 0.200, 0.125,
                    0.075, 0.125, 0.075
                );
                vec3 bloom = vec3(0.0);
                for (int i = 0; i < 9; i++) {
                    vec3 c = texture(uSceneTex, uv + offsets[i] * uTexelSize * 2.0).rgb;
                    float luma = dot(c, vec3(0.2126, 0.7152, 0.0722));
                    float mask = smoothstep(0.75, 1.10, luma);
                    bloom += c * mask * weights[i];
                }
                return bloom;
            }

            void main() {
                vec3 scene = texture(uSceneTex, vUv).rgb;
                vec3 bloom = sampleBloom(vUv);
                vec3 color = scene + bloom * uBloomStrength;

                color = acesToneMap(color);

                // Broadcast contrast and saturation balance
                color = clamp((color - 0.5) * 1.06 + 0.5, 0.0, 1.0);
                float luma = dot(color, vec3(0.2126, 0.7152, 0.0722));
                color = mix(vec3(luma), color, 1.08);

                // Light stadium vignette
                vec2 centered = vUv - 0.5;
                float dist = dot(centered, centered);
                float vignette = clamp(1.0 - dist * uVignetteStrength, 0.0, 1.0);
                color *= vignette;

                fragColor = vec4(color, 1.0);
            }
        """.trimIndent()
    }

    var isReady: Boolean = false
        private set

    private var programId: Int = 0
    private var vaoId: Int = 0
    private var fboId: Int = 0
    private var colorTexId: Int = 0
    private var depthRboId: Int = 0
    private var width: Int = 0
    private var height: Int = 0

    private var uSceneTexLoc: Int = -1
    private var uTexelSizeLoc: Int = -1
    private var uBloomStrengthLoc: Int = -1
    private var uVignetteStrengthLoc: Int = -1

    fun init() {
        try {
            releaseProgramAndVao()
            val vs = compileShader(GLES30.GL_VERTEX_SHADER, POST_VERTEX)
            val fs = compileShader(GLES30.GL_FRAGMENT_SHADER, POST_FRAGMENT)
            if (vs == 0 || fs == 0) {
                if (vs != 0) GLES30.glDeleteShader(vs)
                if (fs != 0) GLES30.glDeleteShader(fs)
                isReady = false
                return
            }

            val prog = GLES30.glCreateProgram()
            if (prog == 0) {
                GLES30.glDeleteShader(vs)
                GLES30.glDeleteShader(fs)
                isReady = false
                return
            }

            GLES30.glAttachShader(prog, vs)
            GLES30.glAttachShader(prog, fs)
            GLES30.glLinkProgram(prog)
            GLES30.glDeleteShader(vs)
            GLES30.glDeleteShader(fs)

            val linkStatus = IntArray(1)
            GLES30.glGetProgramiv(prog, GLES30.GL_LINK_STATUS, linkStatus, 0)
            if (linkStatus[0] == 0) {
                GLES30.glDeleteProgram(prog)
                isReady = false
                return
            }

            programId = prog
            uSceneTexLoc = GLES30.glGetUniformLocation(programId, "uSceneTex")
            uTexelSizeLoc = GLES30.glGetUniformLocation(programId, "uTexelSize")
            uBloomStrengthLoc = GLES30.glGetUniformLocation(programId, "uBloomStrength")
            uVignetteStrengthLoc = GLES30.glGetUniformLocation(programId, "uVignetteStrength")

            val vaos = IntArray(1)
            GLES30.glGenVertexArrays(1, vaos, 0)
            vaoId = vaos[0]

            if (width > 0 && height > 0) {
                createFramebuffer(width, height)
            }
        } catch (_: Throwable) {
            isReady = false
        }
    }

    fun resize(width: Int, height: Int) {
        if (width <= 0 || height <= 0) {
            isReady = false
            return
        }
        if (this.width == width && this.height == height && isReady) {
            return
        }
        this.width = width
        this.height = height
        if (programId != 0) {
            createFramebuffer(width, height)
        }
    }

    fun beginSceneCapture(): Boolean {
        if (!isReady || fboId == 0) {
            return false
        }
        return try {
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fboId)
            if (GLES30.glGetError() != GLES30.GL_NO_ERROR) {
                GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
                false
            } else {
                true
            }
        } catch (_: Throwable) {
            try {
                GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
            } catch (_: Throwable) {
            }
            false
        }
    }

    fun endAndComposite(bloomStrength: Float = 0.25f, vignetteStrength: Float = 0.2f) {
        if (!isReady || programId == 0 || colorTexId == 0) {
            return
        }
        try {
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
            GLES30.glDisable(GLES30.GL_DEPTH_TEST)
            GLES30.glUseProgram(programId)

            GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, colorTexId)
            if (uSceneTexLoc >= 0) GLES30.glUniform1i(uSceneTexLoc, 0)
            if (uTexelSizeLoc >= 0 && width > 0 && height > 0) {
                GLES30.glUniform2f(uTexelSizeLoc, 1.0f / width.toFloat(), 1.0f / height.toFloat())
            }
            if (uBloomStrengthLoc >= 0) GLES30.glUniform1f(uBloomStrengthLoc, bloomStrength)
            if (uVignetteStrengthLoc >= 0) GLES30.glUniform1f(uVignetteStrengthLoc, vignetteStrength)

            GLES30.glBindVertexArray(vaoId)
            GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, 3)
            GLES30.glBindVertexArray(0)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
            GLES30.glEnable(GLES30.GL_DEPTH_TEST)
        } catch (_: Throwable) {
            try {
                GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
            } catch (_: Throwable) {
            }
        }
    }

    fun release() {
        try {
            releaseFramebuffer()
            releaseProgramAndVao()
        } catch (_: Throwable) {
        } finally {
            isReady = false
            width = 0
            height = 0
        }
    }

    private fun createFramebuffer(w: Int, h: Int) {
        try {
            releaseFramebuffer()

            val fbos = IntArray(1)
            GLES30.glGenFramebuffers(1, fbos, 0)
            fboId = fbos[0]

            val texs = IntArray(1)
            GLES30.glGenTextures(1, texs, 0)
            colorTexId = texs[0]

            val rbos = IntArray(1)
            GLES30.glGenRenderbuffers(1, rbos, 0)
            depthRboId = rbos[0]

            if (fboId == 0 || colorTexId == 0 || depthRboId == 0) {
                releaseFramebuffer()
                isReady = false
                return
            }

            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, colorTexId)
            GLES30.glTexImage2D(
                GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA8, w, h, 0,
                GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, null
            )
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)

            GLES30.glBindRenderbuffer(GLES30.GL_RENDERBUFFER, depthRboId)
            GLES30.glRenderbufferStorage(GLES30.GL_RENDERBUFFER, GLES30.GL_DEPTH_COMPONENT16, w, h)
            GLES30.glBindRenderbuffer(GLES30.GL_RENDERBUFFER, 0)

            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fboId)
            GLES30.glFramebufferTexture2D(
                GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0,
                GLES30.GL_TEXTURE_2D, colorTexId, 0
            )
            GLES30.glFramebufferRenderbuffer(
                GLES30.GL_FRAMEBUFFER, GLES30.GL_DEPTH_ATTACHMENT,
                GLES30.GL_RENDERBUFFER, depthRboId
            )

            val status = GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER)
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)

            if (status != GLES30.GL_FRAMEBUFFER_COMPLETE) {
                releaseFramebuffer()
                isReady = false
            } else {
                isReady = true
            }
        } catch (_: Throwable) {
            try {
                GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
            } catch (_: Throwable) {
            }
            releaseFramebuffer()
            isReady = false
        }
    }

    private fun releaseFramebuffer() {
        if (fboId != 0) {
            GLES30.glDeleteFramebuffers(1, intArrayOf(fboId), 0)
            fboId = 0
        }
        if (colorTexId != 0) {
            GLES30.glDeleteTextures(1, intArrayOf(colorTexId), 0)
            colorTexId = 0
        }
        if (depthRboId != 0) {
            GLES30.glDeleteRenderbuffers(1, intArrayOf(depthRboId), 0)
            depthRboId = 0
        }
        isReady = false
    }

    private fun releaseProgramAndVao() {
        if (vaoId != 0) {
            GLES30.glDeleteVertexArrays(1, intArrayOf(vaoId), 0)
            vaoId = 0
        }
        if (programId != 0) {
            GLES30.glDeleteProgram(programId)
            programId = 0
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
            GLES30.glDeleteShader(shader)
            return 0
        }
        return shader
    }
}
