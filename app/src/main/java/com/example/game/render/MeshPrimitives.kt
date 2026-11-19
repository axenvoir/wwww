package com.example.game.render

import android.opengl.GLES30
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Interleaved VBO mesh: pos(3) + normal(3) + color(3) = 9 floats per vertex.
 */
class GlMesh(vertices: FloatArray, private val drawMode: Int = GLES30.GL_TRIANGLES) {
    private val vboId: Int
    val vertexCount: Int = vertices.size / 9

    init {
        val ids = IntArray(1)
        GLES30.glGenBuffers(1, ids, 0)
        vboId = ids[0]
        val buffer = ByteBuffer.allocateDirect(vertices.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
        buffer.put(vertices)
        buffer.position(0)

        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vboId)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, vertices.size * 4, buffer, GLES30.GL_STATIC_DRAW)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
    }

    fun draw() {
        val stride = 9 * 4
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vboId)
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, stride, 0)
        GLES30.glEnableVertexAttribArray(1)
        GLES30.glVertexAttribPointer(1, 3, GLES30.GL_FLOAT, false, stride, 3 * 4)
        GLES30.glEnableVertexAttribArray(2)
        GLES30.glVertexAttribPointer(2, 3, GLES30.GL_FLOAT, false, stride, 6 * 4)
        GLES30.glDrawArrays(drawMode, 0, vertexCount)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
    }

    fun release() {
        GLES30.glDeleteBuffers(1, intArrayOf(vboId), 0)
    }
}

/**
 * GPU-skinned indexed mesh (VBO + IBO) supporting up to 4 bone influences per vertex.
 * Vertex format matches SkinnedMeshData (17 floats / 68 bytes per vertex).
 */
class SkinnedGlMesh(data: SkinnedMeshData) {
    private val vboId: Int
    private val iboId: Int
    val indexCount: Int = data.indices.size

    init {
        val ids = IntArray(2)
        GLES30.glGenBuffers(2, ids, 0)
        vboId = ids[0]
        iboId = ids[1]

        val vBuf = ByteBuffer.allocateDirect(data.vertices.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
        vBuf.put(data.vertices)
        vBuf.position(0)

        val iBuf = ByteBuffer.allocateDirect(data.indices.size * 2)
            .order(ByteOrder.nativeOrder())
            .asShortBuffer()
        iBuf.put(data.indices)
        iBuf.position(0)

        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vboId)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, data.vertices.size * 4, vBuf, GLES30.GL_STATIC_DRAW)

        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, iboId)
        GLES30.glBufferData(GLES30.GL_ELEMENT_ARRAY_BUFFER, data.indices.size * 2, iBuf, GLES30.GL_STATIC_DRAW)

        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, 0)
    }

    fun draw() {
        val stride = SkinnedMeshData.STRIDE_BYTES
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vboId)
        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, iboId)

        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, stride, 0)
        GLES30.glEnableVertexAttribArray(1)
        GLES30.glVertexAttribPointer(1, 3, GLES30.GL_FLOAT, false, stride, 3 * 4)
        GLES30.glEnableVertexAttribArray(2)
        GLES30.glVertexAttribPointer(2, 2, GLES30.GL_FLOAT, false, stride, 6 * 4)
        GLES30.glEnableVertexAttribArray(3)
        GLES30.glVertexAttribPointer(3, 4, GLES30.GL_FLOAT, false, stride, 8 * 4)
        GLES30.glEnableVertexAttribArray(4)
        GLES30.glVertexAttribPointer(4, 4, GLES30.GL_FLOAT, false, stride, 12 * 4)
        GLES30.glEnableVertexAttribArray(5)
        GLES30.glVertexAttribPointer(5, 1, GLES30.GL_FLOAT, false, stride, 16 * 4)

        GLES30.glDrawElements(GLES30.GL_TRIANGLES, indexCount, GLES30.GL_UNSIGNED_SHORT, 0)

        GLES30.glDisableVertexAttribArray(3)
        GLES30.glDisableVertexAttribArray(4)
        GLES30.glDisableVertexAttribArray(5)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, 0)
    }

    fun release() {
        GLES30.glDeleteBuffers(2, intArrayOf(vboId, iboId), 0)
    }
}

/**
 * Textured mesh: pos(3) + normal(3) + uv(2) = 8 floats per vertex.
 * Used for LED advertising boards and woven goal nets.
 */
class TexturedGlMesh(vertices: FloatArray) {
    private val vboId: Int
    val vertexCount: Int = vertices.size / 8

    init {
        val ids = IntArray(1)
        GLES30.glGenBuffers(1, ids, 0)
        vboId = ids[0]
        val buffer = ByteBuffer.allocateDirect(vertices.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
        buffer.put(vertices)
        buffer.position(0)

        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vboId)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, vertices.size * 4, buffer, GLES30.GL_STATIC_DRAW)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
    }

    fun draw() {
        val stride = 8 * 4
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vboId)
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, stride, 0)
        GLES30.glEnableVertexAttribArray(1)
        GLES30.glVertexAttribPointer(1, 3, GLES30.GL_FLOAT, false, stride, 3 * 4)
        GLES30.glEnableVertexAttribArray(2)
        GLES30.glVertexAttribPointer(2, 2, GLES30.GL_FLOAT, false, stride, 6 * 4)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, vertexCount)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
    }

    fun release() {
        GLES30.glDeleteBuffers(1, intArrayOf(vboId), 0)
    }
}

/**
 * Batched GPU-animated crowd mesh: pos(3) + normal(3) + color(3) + animPhase(1) = 10 floats per vertex.
 * Renders ~1,800 cheering spectators across all stadium tiers in 1 draw call.
 */
class CrowdGlMesh(vertices: FloatArray) {
    private val vboId: Int
    val vertexCount: Int = vertices.size / 10

    init {
        val ids = IntArray(1)
        GLES30.glGenBuffers(1, ids, 0)
        vboId = ids[0]
        val buffer = ByteBuffer.allocateDirect(vertices.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
        buffer.put(vertices)
        buffer.position(0)

        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vboId)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, vertices.size * 4, buffer, GLES30.GL_STATIC_DRAW)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
    }

    fun draw() {
        val stride = 10 * 4
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vboId)
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, stride, 0)
        GLES30.glEnableVertexAttribArray(1)
        GLES30.glVertexAttribPointer(1, 3, GLES30.GL_FLOAT, false, stride, 3 * 4)
        GLES30.glEnableVertexAttribArray(2)
        GLES30.glVertexAttribPointer(2, 3, GLES30.GL_FLOAT, false, stride, 6 * 4)
        GLES30.glEnableVertexAttribArray(3)
        GLES30.glVertexAttribPointer(3, 1, GLES30.GL_FLOAT, false, stride, 9 * 4)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, vertexCount)
        GLES30.glDisableVertexAttribArray(3)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
    }

    fun release() {
        GLES30.glDeleteBuffers(1, intArrayOf(vboId), 0)
    }
}

object MeshPrimitives {

    private fun MutableList<Float>.addVert(
        x: Float, y: Float, z: Float,
        nx: Float, ny: Float, nz: Float,
        r: Float = 1f, g: Float = 1f, b: Float = 1f
    ) {
        add(x); add(y); add(z)
        add(nx); add(ny); add(nz)
        add(r); add(g); add(b)
    }

    fun createPitchPlane(halfWidthX: Float, halfDepthZ: Float): GlMesh {
        val v = ArrayList<Float>(6 * 9)
        v.addVert(-halfWidthX, 0f, -halfDepthZ, 0f, 1f, 0f)
        v.addVert(-halfWidthX, 0f, halfDepthZ, 0f, 1f, 0f)
        v.addVert(halfWidthX, 0f, halfDepthZ, 0f, 1f, 0f)

        v.addVert(-halfWidthX, 0f, -halfDepthZ, 0f, 1f, 0f)
        v.addVert(halfWidthX, 0f, halfDepthZ, 0f, 1f, 0f)
        v.addVert(halfWidthX, 0f, -halfDepthZ, 0f, 1f, 0f)
        return GlMesh(v.toFloatArray())
    }

    fun createUnitCube(): GlMesh {
        val v = ArrayList<Float>(36 * 9)
        val h = 0.5f
        fun quad(
            x0: Float, y0: Float, z0: Float,
            x1: Float, y1: Float, z1: Float,
            x2: Float, y2: Float, z2: Float,
            x3: Float, y3: Float, z3: Float,
            nx: Float, ny: Float, nz: Float
        ) {
            v.addVert(x0, y0, z0, nx, ny, nz)
            v.addVert(x1, y1, z1, nx, ny, nz)
            v.addVert(x2, y2, z2, nx, ny, nz)
            v.addVert(x0, y0, z0, nx, ny, nz)
            v.addVert(x2, y2, z2, nx, ny, nz)
            v.addVert(x3, y3, z3, nx, ny, nz)
        }
        quad(-h, -h, h, h, -h, h, h, h, h, -h, h, h, 0f, 0f, 1f)
        quad(h, -h, -h, -h, -h, -h, -h, h, -h, h, h, -h, 0f, 0f, -1f)
        quad(-h, -h, -h, -h, -h, h, -h, h, h, -h, h, -h, -1f, 0f, 0f)
        quad(h, -h, h, h, -h, -h, h, h, -h, h, h, h, 1f, 0f, 0f)
        quad(-h, h, h, h, h, h, h, h, -h, -h, h, -h, 0f, 1f, 0f)
        quad(-h, -h, -h, h, -h, -h, h, -h, h, -h, -h, h, 0f, -1f, 0f)
        return GlMesh(v.toFloatArray())
    }

    /**
     * Smooth geodesic icosphere (subdivisions=3 -> 1,280 triangles) for the 3D FIFA match football.
     * Zero polar pinching so the fragment shader's truncated-icosahedron 32-panel geometry is razor sharp.
     */
    fun createMatchBallIcosphere(subdivisions: Int = 3): GlMesh {
        val phi = ((1.0 + sqrt(5.0)) / 2.0).toFloat()
        fun norm(x: Float, y: Float, z: Float): FloatArray {
            val l = sqrt(x * x + y * y + z * z).coerceAtLeast(1e-6f)
            return floatArrayOf(x / l, y / l, z / l)
        }

        val baseVerts = arrayOf(
            norm(-1f, phi, 0f), norm(1f, phi, 0f), norm(-1f, -phi, 0f), norm(1f, -phi, 0f),
            norm(0f, -1f, phi), norm(0f, 1f, phi), norm(0f, -1f, -phi), norm(0f, 1f, -phi),
            norm(phi, 0f, -1f), norm(phi, 0f, 1f), norm(-phi, 0f, -1f), norm(-phi, 0f, 1f)
        )
        var tris = arrayListOf(
            Triple(baseVerts[0], baseVerts[11], baseVerts[5]),
            Triple(baseVerts[0], baseVerts[5], baseVerts[1]),
            Triple(baseVerts[0], baseVerts[1], baseVerts[7]),
            Triple(baseVerts[0], baseVerts[7], baseVerts[10]),
            Triple(baseVerts[0], baseVerts[10], baseVerts[11]),
            Triple(baseVerts[1], baseVerts[5], baseVerts[9]),
            Triple(baseVerts[5], baseVerts[11], baseVerts[4]),
            Triple(baseVerts[11], baseVerts[10], baseVerts[2]),
            Triple(baseVerts[10], baseVerts[7], baseVerts[6]),
            Triple(baseVerts[7], baseVerts[1], baseVerts[8]),
            Triple(baseVerts[3], baseVerts[9], baseVerts[4]),
            Triple(baseVerts[3], baseVerts[4], baseVerts[2]),
            Triple(baseVerts[3], baseVerts[2], baseVerts[6]),
            Triple(baseVerts[3], baseVerts[6], baseVerts[8]),
            Triple(baseVerts[3], baseVerts[8], baseVerts[9]),
            Triple(baseVerts[4], baseVerts[9], baseVerts[5]),
            Triple(baseVerts[2], baseVerts[4], baseVerts[11]),
            Triple(baseVerts[6], baseVerts[2], baseVerts[10]),
            Triple(baseVerts[8], baseVerts[6], baseVerts[7]),
            Triple(baseVerts[9], baseVerts[8], baseVerts[1])
        )

        repeat(subdivisions) {
            val next = ArrayList<Triple<FloatArray, FloatArray, FloatArray>>(tris.size * 4)
            for ((a, b, c) in tris) {
                val ab = norm(a[0] + b[0], a[1] + b[1], a[2] + b[2])
                val bc = norm(b[0] + c[0], b[1] + c[1], b[2] + c[2])
                val ca = norm(c[0] + a[0], c[1] + a[1], c[2] + a[2])
                next.add(Triple(a, ab, ca))
                next.add(Triple(b, bc, ab))
                next.add(Triple(c, ca, bc))
                next.add(Triple(ab, bc, ca))
            }
            tris = next
        }

        val v = ArrayList<Float>(tris.size * 3 * 9)
        for ((a, b, c) in tris) {
            v.addVert(a[0], a[1], a[2], a[0], a[1], a[2])
            v.addVert(b[0], b[1], b[2], b[0], b[1], b[2])
            v.addVert(c[0], c[1], c[2], c[0], c[1], c[2])
        }
        return GlMesh(v.toFloatArray())
    }

    /**
     * Smooth unit cylinder (radius 1, height 1 centered at origin along Y) for cylindrical goalposts,
     * crossbars, corner flag poles, and stadium floodlight masts.
     */
    fun createUnitCylinder(segments: Int = 14): GlMesh {
        val v = ArrayList<Float>(segments * 6 * 9)
        val h = 0.5f
        for (i in 0 until segments) {
            val a0 = (2.0 * PI * i / segments).toFloat()
            val a1 = (2.0 * PI * (i + 1) / segments).toFloat()
            val c0 = cos(a0); val s0 = sin(a0)
            val c1 = cos(a1); val s1 = sin(a1)

            v.addVert(c0, -h, s0, c0, 0f, s0)
            v.addVert(c1, -h, s1, c1, 0f, s1)
            v.addVert(c1, h, s1, c1, 0f, s1)

            v.addVert(c0, -h, s0, c0, 0f, s0)
            v.addVert(c1, h, s1, c1, 0f, s1)
            v.addVert(c0, h, s0, c0, 0f, s0)
        }
        return GlMesh(v.toFloatArray())
    }

    fun createRing(innerR: Float = 0.72f, outerR: Float = 1.0f, segments: Int = 32): GlMesh {
        val v = ArrayList<Float>(segments * 6 * 9)
        for (i in 0 until segments) {
            val a0 = (2.0 * PI * i / segments).toFloat()
            val a1 = (2.0 * PI * (i + 1) / segments).toFloat()
            val c0 = cos(a0); val s0 = sin(a0)
            val c1 = cos(a1); val s1 = sin(a1)

            v.addVert(c0 * innerR, 0f, s0 * innerR, 0f, 1f, 0f)
            v.addVert(c0 * outerR, 0f, s0 * outerR, 0f, 1f, 0f)
            v.addVert(c1 * outerR, 0f, s1 * outerR, 0f, 1f, 0f)

            v.addVert(c0 * innerR, 0f, s0 * innerR, 0f, 1f, 0f)
            v.addVert(c1 * outerR, 0f, s1 * outerR, 0f, 1f, 0f)
            v.addVert(c1 * innerR, 0f, s1 * innerR, 0f, 1f, 0f)
        }
        return GlMesh(v.toFloatArray())
    }

    fun createDisc(segments: Int = 24): GlMesh {
        val v = ArrayList<Float>(segments * 3 * 9)
        for (i in 0 until segments) {
            val a0 = (2.0 * PI * i / segments).toFloat()
            val a1 = (2.0 * PI * (i + 1) / segments).toFloat()
            v.addVert(0f, 0f, 0f, 0f, 1f, 0f)
            v.addVert(cos(a0), 0f, sin(a0), 0f, 1f, 0f)
            v.addVert(cos(a1), 0f, sin(a1), 0f, 1f, 0f)
        }
        return GlMesh(v.toFloatArray())
    }

    fun createTexturedQuad(width: Float, height: Float, repeatU: Float = 1f, repeatV: Float = 1f): TexturedGlMesh {
        val hw = width * 0.5f
        val hh = height * 0.5f
        val verts = floatArrayOf(
            -hw, -hh, 0f, 0f, 0f, 1f, 0f, repeatV,
            hw, -hh, 0f, 0f, 0f, 1f, repeatU, repeatV,
            hw, hh, 0f, 0f, 0f, 1f, repeatU, 0f,

            -hw, -hh, 0f, 0f, 0f, 1f, 0f, repeatV,
            hw, hh, 0f, 0f, 0f, 1f, repeatU, 0f,
            -hw, hh, 0f, 0f, 0f, 1f, 0f, 0f
        )
        return TexturedGlMesh(verts)
    }
}
