package com.example.game.render

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Raw CPU-side skinned mesh data compatible with both imported glTF 2.0 (.glb) assets
 * and our built-in anatomically sculpted humanoid football rig.
 *
 * Interleaved vertex layout (17 floats per vertex):
 *  - position: vec3 (0..2)
 *  - normal:   vec3 (3..5)
 *  - texCoord: vec2 (6..7)
 *  - joints:   vec4 (8..11)  (bone indices 0..18 stored as float for universal GLES 3.0 VBO portability)
 *  - weights:  vec4 (12..15) (normalized blend weights summing to 1.0)
 *  - regionId: float (16)    (0=kit/cloth, 1=skin/face, 2=hair, 3=boots/leather, 4=gloves)
 */
data class SkinnedMeshData(
    val vertices: FloatArray,
    val indices: ShortArray,
    val inverseBindMatrices: FloatArray? = null,
    val jointCount: Int = SkeletonRig.BONE_COUNT
) {
    val vertexCount: Int get() = vertices.size / FLOATS_PER_VERTEX

    companion object {
        const val FLOATS_PER_VERTEX = 17
        const val STRIDE_BYTES = FLOATS_PER_VERTEX * 4
    }
}

/**
 * Native glTF 2.0 binary (.glb) parser for Android OpenGL ES 3.0.
 *
 * Parses standard Khronos GLB containers (magic 0x46546C67, version 2):
 *  - JSON chunk (0x4E4F534A)
 *  - BIN chunk  (0x004E4942)
 * Extracts POSITION, NORMAL, TEXCOORD_0, JOINTS_0, WEIGHTS_0, triangle INDICES,
 * and skin inverseBindMatrices so external .glb 3D football players or balls
 * placed in app/src/main/assets/models/ can be rendered directly on the GPU.
 */
object GltfModelLoader {

    private const val TAG = "GltfModelLoader"
    const val GLB_MAGIC = 0x46546C67 // "glTF" little-endian
    const val GLB_VERSION = 2
    const val CHUNK_TYPE_JSON = 0x4E4F534A // "JSON"
    const val CHUNK_TYPE_BIN = 0x004E4942  // "BIN\0"

    private const val COMP_UNSIGNED_BYTE = 5121
    private const val COMP_UNSIGNED_SHORT = 5123
    private const val COMP_UNSIGNED_INT = 5125
    private const val COMP_FLOAT = 5126

    fun loadFromAssetsIfPresent(context: Context, assetPath: String): SkinnedMeshData? {
        return try {
            val bytes = context.assets.open(assetPath).use { it.readBytes() }
            parseGlb(bytes)
        } catch (_: Exception) {
            // Optional asset not bundled; caller uses sculpted native rig
            null
        }
    }

    fun parseGlb(bytes: ByteArray): SkinnedMeshData? {
        if (bytes.size < 20) return null
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val magic = buf.int
        val version = buf.int
        val totalLength = buf.int
        if (magic != GLB_MAGIC || version != GLB_VERSION || totalLength > bytes.size) {
            Log.w(TAG, "Invalid GLB header: magic=$magic version=$version len=$totalLength")
            return null
        }

        var jsonText: String? = null
        var binBytes: ByteArray? = null

        while (buf.remaining() >= 8) {
            val chunkLength = buf.int
            val chunkType = buf.int
            if (chunkLength < 0 || chunkLength > buf.remaining()) break
            val chunkData = ByteArray(chunkLength)
            buf.get(chunkData)
            when (chunkType) {
                CHUNK_TYPE_JSON -> jsonText = String(chunkData, Charsets.UTF_8).trimEnd('\u0000', ' ')
                CHUNK_TYPE_BIN -> binBytes = chunkData
            }
        }

        val rootJson = jsonText?.let { JSONObject(it) } ?: return null
        val bin = binBytes ?: return null
        return extractFirstSkinnedPrimitive(rootJson, bin)
    }

    private fun extractFirstSkinnedPrimitive(root: JSONObject, bin: ByteArray): SkinnedMeshData? {
        val meshes = root.optJSONArray("meshes") ?: return null
        if (meshes.length() == 0) return null
        val primitives = meshes.getJSONObject(0).optJSONArray("primitives") ?: return null
        if (primitives.length() == 0) return null

        val prim = primitives.getJSONObject(0)
        val attrs = prim.optJSONObject("attributes") ?: return null
        val accessors = root.optJSONArray("accessors") ?: return null
        val bufferViews = root.optJSONArray("bufferViews") ?: return null

        fun readFloatAccessor(accessorIndex: Int, expectedComponents: Int): FloatArray? {
            if (accessorIndex < 0 || accessorIndex >= accessors.length()) return null
            val acc = accessors.getJSONObject(accessorIndex)
            val bvIdx = acc.optInt("bufferView", -1)
            if (bvIdx < 0 || bvIdx >= bufferViews.length()) return null
            val bv = bufferViews.getJSONObject(bvIdx)
            val count = acc.optInt("count", 0)
            val compType = acc.optInt("componentType", COMP_FLOAT)
            val byteOffset = bv.optInt("byteOffset", 0) + acc.optInt("byteOffset", 0)
            val defaultStride = expectedComponents * when (compType) {
                COMP_UNSIGNED_BYTE -> 1
                COMP_UNSIGNED_SHORT -> 2
                else -> 4
            }
            val stride = bv.optInt("byteStride", defaultStride).coerceAtLeast(defaultStride)
            val bb = ByteBuffer.wrap(bin).order(ByteOrder.LITTLE_ENDIAN)
            val out = FloatArray(count * expectedComponents)
            for (i in 0 until count) {
                val base = byteOffset + i * stride
                if (base + defaultStride > bin.size) break
                for (c in 0 until expectedComponents) {
                    out[i * expectedComponents + c] = when (compType) {
                        COMP_FLOAT -> bb.getFloat(base + c * 4)
                        COMP_UNSIGNED_BYTE -> (bin[base + c].toInt() and 0xFF).toFloat()
                        COMP_UNSIGNED_SHORT -> (bb.getShort(base + c * 2).toInt() and 0xFFFF).toFloat()
                        else -> 0f
                    }
                }
            }
            return out
        }

        fun readIndexAccessor(accessorIndex: Int): ShortArray? {
            if (accessorIndex < 0 || accessorIndex >= accessors.length()) return null
            val acc = accessors.getJSONObject(accessorIndex)
            val bvIdx = acc.optInt("bufferView", -1)
            if (bvIdx < 0 || bvIdx >= bufferViews.length()) return null
            val bv = bufferViews.getJSONObject(bvIdx)
            val count = acc.optInt("count", 0)
            val compType = acc.optInt("componentType", COMP_UNSIGNED_SHORT)
            val byteOffset = bv.optInt("byteOffset", 0) + acc.optInt("byteOffset", 0)
            val bb = ByteBuffer.wrap(bin).order(ByteOrder.LITTLE_ENDIAN)
            val out = ShortArray(count)
            for (i in 0 until count) {
                out[i] = when (compType) {
                    COMP_UNSIGNED_BYTE -> (bin[byteOffset + i].toInt() and 0xFF).toShort()
                    COMP_UNSIGNED_SHORT -> bb.getShort(byteOffset + i * 2)
                    COMP_UNSIGNED_INT -> bb.getInt(byteOffset + i * 4).toShort()
                    else -> i.toShort()
                }
            }
            return out
        }

        val positions = readFloatAccessor(attrs.optInt("POSITION", -1), 3) ?: return null
        val vertCount = positions.size / 3
        if (vertCount == 0) return null

        val normals = readFloatAccessor(attrs.optInt("NORMAL", -1), 3)
        val uvs = readFloatAccessor(attrs.optInt("TEXCOORD_0", -1), 2)
        val joints = readFloatAccessor(attrs.optInt("JOINTS_0", -1), 4)
        val weights = readFloatAccessor(attrs.optInt("WEIGHTS_0", -1), 4)

        val indices = if (prim.has("indices")) {
            readIndexAccessor(prim.optInt("indices", -1)) ?: ShortArray(vertCount) { it.toShort() }
        } else {
            ShortArray(vertCount) { it.toShort() }
        }

        // Optional skin inverseBindMatrices
        var ibms: FloatArray? = null
        var jointCount = SkeletonRig.BONE_COUNT
        val skins = root.optJSONArray("skins")
        if (skins != null && skins.length() > 0) {
            val skin = skins.getJSONObject(0)
            val jointArr = skin.optJSONArray("joints")
            if (jointArr != null) jointCount = jointArr.length().coerceAtMost(SkeletonRig.BONE_COUNT)
            val ibmIdx = skin.optInt("inverseBindMatrices", -1)
            if (ibmIdx >= 0) {
                ibms = readFloatAccessor(ibmIdx, 16)
            }
        }

        val interleaved = FloatArray(vertCount * SkinnedMeshData.FLOATS_PER_VERTEX)
        for (i in 0 until vertCount) {
            val o = i * SkinnedMeshData.FLOATS_PER_VERTEX
            interleaved[o + 0] = positions[i * 3]
            interleaved[o + 1] = positions[i * 3 + 1]
            interleaved[o + 2] = positions[i * 3 + 2]

            if (normals != null && i * 3 + 2 < normals.size) {
                interleaved[o + 3] = normals[i * 3]
                interleaved[o + 4] = normals[i * 3 + 1]
                interleaved[o + 5] = normals[i * 3 + 2]
            } else {
                interleaved[o + 3] = 0f; interleaved[o + 4] = 1f; interleaved[o + 5] = 0f
            }

            if (uvs != null && i * 2 + 1 < uvs.size) {
                interleaved[o + 6] = uvs[i * 2]
                interleaved[o + 7] = uvs[i * 2 + 1]
            } else {
                interleaved[o + 6] = 0.5f; interleaved[o + 7] = 0.5f
            }

            if (joints != null && weights != null && i * 4 + 3 < joints.size && i * 4 + 3 < weights.size) {
                val w0 = weights[i * 4]
                val w1 = weights[i * 4 + 1]
                val w2 = weights[i * 4 + 2]
                val w3 = weights[i * 4 + 3]
                val wSum = (w0 + w1 + w2 + w3).coerceAtLeast(0.0001f)
                interleaved[o + 8] = joints[i * 4].coerceIn(0f, (SkeletonRig.BONE_COUNT - 1).toFloat())
                interleaved[o + 9] = joints[i * 4 + 1].coerceIn(0f, (SkeletonRig.BONE_COUNT - 1).toFloat())
                interleaved[o + 10] = joints[i * 4 + 2].coerceIn(0f, (SkeletonRig.BONE_COUNT - 1).toFloat())
                interleaved[o + 11] = joints[i * 4 + 3].coerceIn(0f, (SkeletonRig.BONE_COUNT - 1).toFloat())
                interleaved[o + 12] = w0 / wSum
                interleaved[o + 13] = w1 / wSum
                interleaved[o + 14] = w2 / wSum
                interleaved[o + 15] = w3 / wSum
            } else {
                interleaved[o + 8] = 0f; interleaved[o + 9] = 0f; interleaved[o + 10] = 0f; interleaved[o + 11] = 0f
                interleaved[o + 12] = 1f; interleaved[o + 13] = 0f; interleaved[o + 14] = 0f; interleaved[o + 15] = 0f
            }
            interleaved[o + 16] = 0f
        }

        return SkinnedMeshData(
            vertices = interleaved,
            indices = indices,
            inverseBindMatrices = ibms,
            jointCount = jointCount
        )
    }

    /**
     * Packages a minimal valid glTF 2.0 binary (.glb) buffer in memory (useful for exporting or testing).
     */
    fun buildMinimalGlb(
        positions: FloatArray,
        normals: FloatArray,
        uvs: FloatArray,
        joints: ByteArray,
        weights: FloatArray,
        indices: ShortArray
    ): ByteArray {
        val vCount = positions.size / 3
        val posBytes = positions.size * 4
        val normBytes = normals.size * 4
        val uvBytes = uvs.size * 4
        val jointBytes = joints.size
        val weightBytes = weights.size * 4
        val idxBytes = indices.size * 2

        val offPos = 0
        val offNorm = offPos + posBytes
        val offUv = offNorm + normBytes
        val offJoint = offUv + uvBytes
        val offWeight = (offJoint + jointBytes + 3) and -4
        val offIdx = offWeight + weightBytes
        val rawBinLen = offIdx + idxBytes
        val paddedBinLen = (rawBinLen + 3) and -4

        val binBuf = ByteBuffer.allocate(paddedBinLen).order(ByteOrder.LITTLE_ENDIAN)
        for (f in positions) binBuf.putFloat(f)
        for (f in normals) binBuf.putFloat(f)
        for (f in uvs) binBuf.putFloat(f)
        binBuf.put(joints)
        while (binBuf.position() < offWeight) binBuf.put(0)
        for (f in weights) binBuf.putFloat(f)
        for (s in indices) binBuf.putShort(s)

        val json = """
            {
              "asset":{"version":"2.0"},
              "buffers":[{"byteLength":$paddedBinLen}],
              "bufferViews":[
                {"buffer":0,"byteOffset":$offPos,"byteLength":$posBytes},
                {"buffer":0,"byteOffset":$offNorm,"byteLength":$normBytes},
                {"buffer":0,"byteOffset":$offUv,"byteLength":$uvBytes},
                {"buffer":0,"byteOffset":$offJoint,"byteLength":$jointBytes},
                {"buffer":0,"byteOffset":$offWeight,"byteLength":$weightBytes},
                {"buffer":0,"byteOffset":$offIdx,"byteLength":$idxBytes}
              ],
              "accessors":[
                {"bufferView":0,"componentType":5126,"count":$vCount,"type":"VEC3"},
                {"bufferView":1,"componentType":5126,"count":$vCount,"type":"VEC3"},
                {"bufferView":2,"componentType":5126,"count":$vCount,"type":"VEC2"},
                {"bufferView":3,"componentType":5121,"count":$vCount,"type":"VEC4"},
                {"bufferView":4,"componentType":5126,"count":$vCount,"type":"VEC4"},
                {"bufferView":5,"componentType":5123,"count":${indices.size},"type":"SCALAR"}
              ],
              "meshes":[{"primitives":[{"attributes":{"POSITION":0,"NORMAL":1,"TEXCOORD_0":2,"JOINTS_0":3,"WEIGHTS_0":4},"indices":5}]}]
            }
        """.trimIndent()
        val rawJsonBytes = json.toByteArray(Charsets.UTF_8)
        val paddedJsonLen = (rawJsonBytes.size + 3) and -4
        val totalLen = 12 + 8 + paddedJsonLen + 8 + paddedBinLen

        val glb = ByteBuffer.allocate(totalLen).order(ByteOrder.LITTLE_ENDIAN)
        glb.putInt(GLB_MAGIC)
        glb.putInt(GLB_VERSION)
        glb.putInt(totalLen)

        glb.putInt(paddedJsonLen)
        glb.putInt(CHUNK_TYPE_JSON)
        glb.put(rawJsonBytes)
        repeat(paddedJsonLen - rawJsonBytes.size) { glb.put(' '.code.toByte()) }

        glb.putInt(paddedBinLen)
        glb.putInt(CHUNK_TYPE_BIN)
        glb.put(binBuf.array())
        return glb.array()
    }
}
