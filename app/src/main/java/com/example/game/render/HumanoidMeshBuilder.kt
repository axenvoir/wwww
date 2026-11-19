package com.example.game.render

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Generates an anatomically proportioned, seamless, smooth-shaded, 19-bone GPU-skinned
 * 3D human football player mesh (~1.82m tall) plus 6 distinct 3D hairstyle meshes.
 *
 * Replaces primitive cubes with continuous lofted anatomical cross-sections:
 *  - Sculpted cranium, brow, cheekbones, nose, jaw/chin, ears & neck
 *  - Broad athletic shoulders, pectorals, ribcage, tapered waist, jersey collar & short sleeves
 *  - Contoured upper arms, elbows, forearms & hands/gloves
 *  - Athletic shorts, muscular thighs, bare knees, calves in pulled-up socks, ankles & sculpted cleats
 */
object HumanoidMeshBuilder {

    const val HAIRSTYLE_COUNT = 6

    private class MeshAcc {
        val verts = ArrayList<Float>(16000)
        val inds = ArrayList<Short>(8000)

        fun addVertex(
            x: Float, y: Float, z: Float,
            nx: Float, ny: Float, nz: Float,
            u: Float, v: Float,
            j0: Int, w0: Float,
            j1: Int = 0, w1: Float = 0f,
            region: Float = 0f
        ): Short {
            val idx = (verts.size / SkinnedMeshData.FLOATS_PER_VERTEX).toShort()
            val len = sqrt(nx * nx + ny * ny + nz * nz).coerceAtLeast(1e-5f)
            val wSum = (w0 + w1).coerceAtLeast(1e-5f)
            verts.add(x); verts.add(y); verts.add(z)
            verts.add(nx / len); verts.add(ny / len); verts.add(nz / len)
            verts.add(u); verts.add(v)
            verts.add(j0.toFloat()); verts.add(j1.toFloat()); verts.add(0f); verts.add(0f)
            verts.add(w0 / wSum); verts.add(w1 / wSum); verts.add(0f); verts.add(0f)
            verts.add(region)
            return idx
        }

        fun addTri(a: Short, b: Short, c: Short) {
            inds.add(a); inds.add(b); inds.add(c)
        }

        fun toData(): SkinnedMeshData = SkinnedMeshData(
            vertices = verts.toFloatArray(),
            indices = inds.toShortArray(),
            inverseBindMatrices = SkeletonRig.inverseBindMatrices,
            jointCount = SkeletonRig.BONE_COUNT
        )
    }

    private data class RingSpec(
        val cx: Float,
        val cy: Float,
        val cz: Float,
        val rx: Float,
        val rz: Float,
        val vCoord: Float,
        val j0: Int,
        val w0: Float,
        val j1: Int = 0,
        val w1: Float = 0f,
        val region: Float = 0f,
        val frontBulge: Float = 0f,
        val backBulge: Float = 0f
    )

    private fun loftVerticalRings(
        acc: MeshAcc,
        rings: List<RingSpec>,
        segments: Int,
        uMin: Float,
        uMax: Float,
        capBottom: Boolean = false,
        capTop: Boolean = false
    ) {
        val ringStarts = IntArray(rings.size)
        val stride = segments + 1

        for (rIdx in rings.indices) {
            val r = rings[rIdx]
            val prev = rings[(rIdx - 1).coerceAtLeast(0)]
            val next = rings[(rIdx + 1).coerceAtMost(rings.lastIndex)]
            val dy = (next.cy - prev.cy).coerceAtLeast(0.001f)
            val drx = (next.rx - prev.rx) / dy
            val drz = (next.rz - prev.rz) / dy

            ringStarts[rIdx] = acc.verts.size / SkinnedMeshData.FLOATS_PER_VERTEX

            for (s in 0..segments) {
                val frac = s.toFloat() / segments
                // Angle 0 starts at +Z (front) so u=0.5 is back or we map cleanly
                val theta = (frac * 2.0 * PI).toFloat()
                val sinT = sin(theta)
                val cosT = cos(theta) // +1 at front (+Z), -1 at back (-Z)

                val bulge = if (cosT > 0f) r.frontBulge * cosT * cosT else r.backBulge * cosT * cosT
                val px = r.cx + sinT * r.rx
                val pz = r.cz + cosT * (r.rz + bulge)

                val nx = sinT / r.rx.coerceAtLeast(0.01f)
                val nz = cosT / (r.rz + bulge).coerceAtLeast(0.01f)
                val ny = -(drx * sinT * sinT + drz * cosT * cosT) * 0.65f

                // Map front hemisphere (cosT >= 0) to left half of [uMin, uMax], back to right half
                val u = uMin + frac * (uMax - uMin)
                acc.addVertex(
                    px, r.cy, pz,
                    nx, ny, nz,
                    u, r.vCoord,
                    r.j0, r.w0, r.j1, r.w1, r.region
                )
            }
        }

        for (rIdx in 0 until rings.size - 1) {
            val rowA = ringStarts[rIdx]
            val rowB = ringStarts[rIdx + 1]
            for (s in 0 until segments) {
                val a0 = (rowA + s).toShort()
                val a1 = (rowA + s + 1).toShort()
                val b0 = (rowB + s).toShort()
                val b1 = (rowB + s + 1).toShort()
                acc.addTri(a0, a1, b1)
                acc.addTri(a0, b1, b0)
            }
        }

        if (capBottom && rings.isNotEmpty()) {
            val r = rings.first()
            val center = acc.addVertex(
                r.cx, r.cy, r.cz, 0f, -1f, 0f,
                (uMin + uMax) * 0.5f, r.vCoord,
                r.j0, r.w0, r.j1, r.w1, r.region
            )
            val row = ringStarts.first()
            for (s in 0 until segments) {
                acc.addTri(center, (row + s + 1).toShort(), (row + s).toShort())
            }
        }
        if (capTop && rings.isNotEmpty()) {
            val r = rings.last()
            val center = acc.addVertex(
                r.cx, r.cy, r.cz, 0f, 1f, 0f,
                (uMin + uMax) * 0.5f, r.vCoord,
                r.j0, r.w0, r.j1, r.w1, r.region
            )
            val row = ringStarts.last()
            for (s in 0 until segments) {
                acc.addTri(center, (row + s).toShort(), (row + s + 1).toShort())
            }
        }
    }

    /**
     * Builds the full skinned football player body (head+face, neck, torso/jersey, shorts,
     * seamless arms+hands/gloves, seamless legs+socks+sculpted football boots).
     */
    fun buildFootballPlayerMesh(highDetail: Boolean = true): SkinnedMeshData {
        val acc = MeshAcc()
        val torsoSegs = if (highDetail) 16 else 10
        val limbSegs = if (highDetail) 12 else 8
        val headSegs = if (highDetail) 16 else 10

        // 1. PELVIS / SHORTS -> WAIST -> ATHLETIC TORSO / JERSEY -> COLLAR
        val torsoRings = listOf(
            // Shorts crotch / base
            RingSpec(0f, 0.80f, 0.00f, 0.19f, 0.135f, 0.47f, SkeletonRig.PELVIS, 1.0f, region = 0f),
            RingSpec(0f, 0.90f, 0.00f, 0.205f, 0.145f, 0.36f, SkeletonRig.PELVIS, 1.0f, region = 0f, backBulge = 0.025f),
            // Waistband seam (Shorts -> Jersey transition)
            RingSpec(0f, 0.99f, 0.00f, 0.185f, 0.130f, 0.27f, SkeletonRig.PELVIS, 0.65f, SkeletonRig.SPINE, 0.35f, region = 0f),
            // Lower jersey (abdomen)
            RingSpec(0f, 1.01f, 0.00f, 0.188f, 0.132f, 0.46f, SkeletonRig.PELVIS, 0.45f, SkeletonRig.SPINE, 0.55f, region = 0f),
            RingSpec(0f, 1.13f, 0.005f, 0.195f, 0.135f, 0.36f, SkeletonRig.SPINE, 0.85f, SkeletonRig.CHEST, 0.15f, region = 0f),
            // Ribcage / lower pecs
            RingSpec(0f, 1.25f, 0.01f, 0.220f, 0.148f, 0.24f, SkeletonRig.SPINE, 0.25f, SkeletonRig.CHEST, 0.75f, region = 0f, frontBulge = 0.020f),
            // Broad athletic chest / shoulders
            RingSpec(0f, 1.38f, 0.01f, 0.248f, 0.152f, 0.13f, SkeletonRig.CHEST, 1.0f, region = 0f, frontBulge = 0.022f, backBulge = 0.015f),
            // Upper trapezius / shoulder slope
            RingSpec(0f, 1.46f, 0.005f, 0.210f, 0.125f, 0.06f, SkeletonRig.CHEST, 0.85f, SkeletonRig.NECK, 0.15f, region = 0f),
            // Jersey collar rim
            RingSpec(0f, 1.51f, 0.01f, 0.095f, 0.088f, 0.025f, SkeletonRig.CHEST, 0.35f, SkeletonRig.NECK, 0.65f, region = 0f)
        )
        // Front of jersey maps cleanly to u=[0.02..0.48], shorts to same U strip via v in [0.27..0.47]
        // Split shorts & jersey cleanly so UVs hit distinct atlas quadrants:
        val shortsRings = torsoRings.subList(0, 3)
        loftVerticalRings(acc, shortsRings, torsoSegs, uMin = 0.52f, uMax = 0.96f, capBottom = true)

        val jerseyRings = torsoRings.subList(3, torsoRings.size)
        loftVerticalRings(acc, jerseyRings, torsoSegs, uMin = 0.02f, uMax = 0.48f, capBottom = true, capTop = true)

        // 2. NECK (Skin region 1.0)
        val neckRings = listOf(
            RingSpec(0f, 1.49f, 0.01f, 0.074f, 0.072f, 0.92f, SkeletonRig.CHEST, 0.3f, SkeletonRig.NECK, 0.7f, region = 1f),
            RingSpec(0f, 1.56f, 0.014f, 0.064f, 0.064f, 0.88f, SkeletonRig.NECK, 0.7f, SkeletonRig.HEAD, 0.3f, region = 1f),
            RingSpec(0f, 1.61f, 0.018f, 0.068f, 0.070f, 0.84f, SkeletonRig.HEAD, 1.0f, region = 1f)
        )
        loftVerticalRings(acc, neckRings, limbSegs, uMin = 0.04f, uMax = 0.46f)

        // 3. SCULPTED ANATOMICAL HEAD & FACE (Jaw, Chin, Cheekbones, Nose, Brow, Cranium)
        buildSculptedHead(acc, headSegs)

        // 4. LEFT & RIGHT ARMS (Deltoid Sleeve -> Bicep -> Elbow -> Forearm -> Wrist -> Hand/GK Glove)
        buildArm(acc, sideSign = 1f, shoulder = SkeletonRig.L_SHOULDER, upper = SkeletonRig.L_UPPER_ARM, forearm = SkeletonRig.L_FOREARM, hand = SkeletonRig.L_HAND, segs = limbSegs)
        buildArm(acc, sideSign = -1f, shoulder = SkeletonRig.R_SHOULDER, upper = SkeletonRig.R_UPPER_ARM, forearm = SkeletonRig.R_FOREARM, hand = SkeletonRig.R_HAND, segs = limbSegs)

        // 5. LEFT & RIGHT LEGS (Shorts Leg -> Bare Knee -> Calf Sock -> Ankle -> Sculpted Football Boot)
        buildLegAndBoot(acc, sideSign = 1f, thigh = SkeletonRig.L_THIGH, shin = SkeletonRig.L_SHIN, foot = SkeletonRig.L_FOOT, segs = limbSegs)
        buildLegAndBoot(acc, sideSign = -1f, thigh = SkeletonRig.R_THIGH, shin = SkeletonRig.R_SHIN, foot = SkeletonRig.R_FOOT, segs = limbSegs)

        return acc.toData()
    }

    private fun buildSculptedHead(acc: MeshAcc, segments: Int) {
        // Vertical slices from chin (1.58m) to crown (1.825m) with anatomical nose & brow relief
        val slices = 10
        val ringStarts = IntArray(slices + 1)

        for (i in 0..slices) {
            val t = i.toFloat() / slices // 0 = chin/jaw base, 1 = top of cranium
            val y = 1.575f + t * 0.250f

            // Ellipsoid base profile modified for human jaw & cranium
            val phi = (t - 0.5f) * PI.toFloat()
            val baseProfile = cos(phi).coerceAtLeast(0.04f)
            val jawTaper = if (t < 0.35f) 0.72f + (t / 0.35f) * 0.28f else 1.0f
            val rx = 0.092f * baseProfile * jawTaper
            val rz = 0.106f * baseProfile * (if (t < 0.25f) 0.82f + t * 0.72f else 1.0f)
            val cz = 0.020f + (if (t < 0.35f) 0.012f else 0f)

            ringStarts[i] = acc.verts.size / SkinnedMeshData.FLOATS_PER_VERTEX
            val vUv = 0.94f - t * 0.40f // Maps chin->crown into lower-left face atlas [0.54..0.94]

            for (s in 0..segments) {
                val frac = s.toFloat() / segments
                // Map frac=0.5 directly to front (+Z) so the face texture center u=0.25 sits on the nose/eyes!
                val theta = ((frac - 0.5f) * 2.0 * PI).toFloat()
                val sinT = sin(theta)
                val cosT = cos(theta) // +1 at front (+Z)

                var px = sinT * rx
                var pz = cz + cosT * rz

                // Sculpted nose bridge & tip at front center (t ~ 0.40..0.58)
                if (cosT > 0.75f && t in 0.34f..0.60f) {
                    val noseProfile = sin(((t - 0.34f) / 0.26f) * PI.toFloat())
                    val noseSharp = ((cosT - 0.75f) / 0.25f).let { it * it }
                    pz += 0.022f * noseProfile * noseSharp
                }
                // Subtle brow ridge (t ~ 0.62..0.72)
                if (cosT > 0.45f && t in 0.60f..0.74f) {
                    val brow = sin(((t - 0.60f) / 0.14f) * PI.toFloat())
                    pz += 0.008f * brow * cosT
                }
                // Subtle ears on left/right sides (t ~ 0.40..0.58)
                if (abs(sinT) > 0.92f && t in 0.38f..0.58f) {
                    val earProfile = sin(((t - 0.38f) / 0.20f) * PI.toFloat())
                    px += (if (sinT > 0f) 1f else -1f) * 0.014f * earProfile
                }

                val nx = sinT
                val ny = sin(phi) * 0.9f
                val nz = cosT
                val uUv = 0.02f + frac * 0.46f // Centered at u=0.25 for front face!

                acc.addVertex(
                    px, y, pz,
                    nx, ny, nz,
                    uUv, vUv,
                    SkeletonRig.HEAD, 1.0f,
                    region = 1.5f // 1.5 = face-detailed skin
                )
            }
        }

        for (i in 0 until slices) {
            val rowA = ringStarts[i]
            val rowB = ringStarts[i + 1]
            for (s in 0 until segments) {
                val a0 = (rowA + s).toShort()
                val a1 = (rowA + s + 1).toShort()
                val b0 = (rowB + s).toShort()
                val b1 = (rowB + s + 1).toShort()
                acc.addTri(a0, a1, b1)
                acc.addTri(a0, b1, b0)
            }
        }
    }

    private fun buildArm(
        acc: MeshAcc,
        sideSign: Float,
        shoulder: Int,
        upper: Int,
        forearm: Int,
        hand: Int,
        segs: Int
    ) {
        val sx = sideSign * 0.235f
        // Short jersey sleeve (region 0.0, sleeve atlas UV u=[0.52..0.72], v=[0.02..0.22])
        val sleeveRings = listOf(
            RingSpec(sx * 0.88f, 1.46f, 0.00f, 0.072f, 0.072f, 0.03f, SkeletonRig.CHEST, 0.5f, shoulder, 0.5f, region = 0f),
            RingSpec(sx * 1.00f, 1.40f, 0.00f, 0.068f, 0.068f, 0.11f, shoulder, 0.4f, upper, 0.6f, region = 0f),
            RingSpec(sx * 1.04f, 1.25f, 0.005f, 0.062f, 0.060f, 0.21f, upper, 1.0f, region = 0f)
        )
        loftVerticalRings(acc, sleeveRings, segs, uMin = 0.52f, uMax = 0.72f, capTop = true, capBottom = true)

        // Bare bicep -> elbow -> forearm -> wrist (Skin region 1.0)
        val armSkinRings = listOf(
            RingSpec(sx * 1.04f, 1.27f, 0.005f, 0.052f, 0.050f, 0.90f, upper, 1.0f, region = 1f),
            RingSpec(sx * 1.06f, 1.13f, 0.008f, 0.046f, 0.046f, 0.86f, upper, 0.5f, forearm, 0.5f, region = 1f),
            RingSpec(sx * 1.08f, 1.00f, 0.015f, 0.044f, 0.042f, 0.83f, forearm, 1.0f, region = 1f),
            RingSpec(sx * 1.10f, 0.88f, 0.022f, 0.034f, 0.032f, 0.80f, forearm, 0.4f, hand, 0.6f, region = 1f)
        )
        loftVerticalRings(acc, armSkinRings, segs, uMin = 0.04f, uMax = 0.14f)

        // Sculpted hand / GK glove (region 4.0 -> skin for outfield, padded glove for GK!)
        val handRings = listOf(
            RingSpec(sx * 1.10f, 0.88f, 0.022f, 0.036f, 0.028f, 0.92f, hand, 1.0f, region = 4f),
            RingSpec(sx * 1.11f, 0.81f, 0.026f, 0.044f, 0.028f, 0.85f, hand, 1.0f, region = 4f),
            RingSpec(sx * 1.11f, 0.75f, 0.028f, 0.030f, 0.018f, 0.78f, hand, 1.0f, region = 4f)
        )
        loftVerticalRings(acc, handRings, segs, uMin = 0.76f, uMax = 0.96f, capBottom = true)
    }

    private fun buildLegAndBoot(
        acc: MeshAcc,
        sideSign: Float,
        thigh: Int,
        shin: Int,
        foot: Int,
        segs: Int
    ) {
        val lx = sideSign * 0.105f

        // 1. Shorts leg cuff (upper thigh, region 0.0, shorts UV)
        val shortsLegRings = listOf(
            RingSpec(lx, 0.91f, 0.00f, 0.098f, 0.102f, 0.28f, SkeletonRig.PELVIS, 0.45f, thigh, 0.55f, region = 0f),
            RingSpec(lx, 0.77f, 0.004f, 0.094f, 0.096f, 0.38f, thigh, 1.0f, region = 0f),
            RingSpec(lx, 0.65f, 0.006f, 0.088f, 0.090f, 0.47f, thigh, 1.0f, region = 0f)
        )
        loftVerticalRings(acc, shortsLegRings, segs, uMin = 0.52f, uMax = 0.96f, capBottom = true)

        // 2. Lower bare thigh + anatomical knee (skin region 1.0)
        val kneeRings = listOf(
            RingSpec(lx, 0.68f, 0.004f, 0.074f, 0.076f, 0.90f, thigh, 1.0f, region = 1f),
            RingSpec(lx, 0.57f, 0.008f, 0.064f, 0.066f, 0.86f, thigh, 0.8f, shin, 0.2f, region = 1f),
            RingSpec(lx, 0.49f, 0.012f, 0.058f, 0.062f, 0.84f, thigh, 0.45f, shin, 0.55f, region = 1f, frontBulge = 0.010f),
            RingSpec(lx, 0.42f, 0.006f, 0.056f, 0.060f, 0.82f, shin, 1.0f, region = 1f)
        )
        loftVerticalRings(acc, kneeRings, segs, uMin = 0.04f, uMax = 0.14f)

        // 3. Pulled-up football sock + sculpted calf muscle (region 0.0, sock UV u=[0.52..0.74], v=[0.52..0.74])
        val sockRings = listOf(
            RingSpec(lx, 0.43f, 0.006f, 0.060f, 0.064f, 0.53f, shin, 1.0f, region = 0f),
            RingSpec(lx, 0.34f, -0.004f, 0.064f, 0.070f, 0.60f, shin, 1.0f, region = 0f, backBulge = 0.018f),
            RingSpec(lx, 0.21f, 0.002f, 0.048f, 0.052f, 0.68f, shin, 0.85f, foot, 0.15f, region = 0f),
            RingSpec(lx, 0.11f, 0.010f, 0.044f, 0.048f, 0.73f, shin, 0.30f, foot, 0.70f, region = 0f)
        )
        loftVerticalRings(acc, sockRings, segs, uMin = 0.52f, uMax = 0.74f, capTop = true)

        // 4. Sculpted Football Boot (heel counter -> arched instep -> tapered toe box -> stud soleplate)
        // Lofted horizontally along Z from heel (-0.06m) to toe (+0.21m)
        buildFootballBoot(acc, lx, foot, segs)
    }

    private fun buildFootballBoot(acc: MeshAcc, lx: Float, footBone: Int, segs: Int) {
        data class BootSlice(val z: Float, val cy: Float, val rx: Float, val ry: Float, val u: Float)
        val slices = listOf(
            BootSlice(-0.065f, 0.068f, 0.042f, 0.046f, 0.77f), // Heel counter
            BootSlice(-0.010f, 0.072f, 0.048f, 0.058f, 0.82f), // Ankle collar
            BootSlice(0.055f, 0.056f, 0.052f, 0.048f, 0.87f),  // Laced instep
            BootSlice(0.135f, 0.042f, 0.050f, 0.036f, 0.93f),  // Forefoot / vamp
            BootSlice(0.195f, 0.028f, 0.036f, 0.024f, 0.97f)   // Tapered toe box
        )

        val rowStarts = IntArray(slices.size)
        for (i in slices.indices) {
            val sl = slices[i]
            rowStarts[i] = acc.verts.size / SkinnedMeshData.FLOATS_PER_VERTEX
            for (s in 0..segs) {
                val frac = s.toFloat() / segs
                val theta = (frac * 2.0 * PI).toFloat()
                val cosT = cos(theta)
                val sinT = sin(theta)
                // Flatten bottom sole at y = 0.004m
                val py = (sl.cy + sinT * sl.ry).coerceAtLeast(0.004f)
                val px = lx + cosT * sl.rx
                val vUv = 0.77f + frac * 0.20f
                acc.addVertex(
                    px, py, sl.z,
                    cosT, sinT, if (i == slices.lastIndex) 0.55f else if (i == 0) -0.55f else 0f,
                    sl.u, vUv,
                    footBone, 1.0f,
                    region = 3.0f // 3.0 = football cleat
                )
            }
        }
        for (i in 0 until slices.size - 1) {
            val rA = rowStarts[i]
            val rB = rowStarts[i + 1]
            for (s in 0 until segs) {
                val a0 = (rA + s).toShort()
                val a1 = (rA + s + 1).toShort()
                val b0 = (rB + s).toShort()
                val b1 = (rB + s + 1).toShort()
                acc.addTri(a0, b1, a1)
                acc.addTri(a0, b0, b1)
            }
        }
        // Cap heel & toe
        val heelC = acc.addVertex(lx, 0.068f, -0.072f, 0f, 0f, -1f, 0.77f, 0.87f, footBone, 1.0f, region = 3f)
        for (s in 0 until segs) acc.addTri(heelC, (rowStarts.first() + s).toShort(), (rowStarts.first() + s + 1).toShort())

        val toeC = acc.addVertex(lx, 0.026f, 0.215f, 0f, 0.1f, 1f, 0.98f, 0.87f, footBone, 1.0f, region = 3f)
        for (s in 0 until segs) acc.addTri(toeC, (rowStarts.last() + s + 1).toShort(), (rowStarts.last() + s).toShort())
    }

    /**
     * Builds 6 distinct 3D hairstyles rigged 100% to SkeletonRig.HEAD so players on the pitch
     * have visibly unique silhouettes and appearances.
     */
    fun buildHairstyleMesh(styleIndex: Int): SkinnedMeshData {
        val acc = MeshAcc()
        val segs = 14
        val style = ((styleIndex % HAIRSTYLE_COUNT) + HAIRSTYLE_COUNT) % HAIRSTYLE_COUNT

        val rings = when (style) {
            0 -> listOf( // Classic Short Taper / Fade
                RingSpec(0f, 1.725f, 0.008f, 0.097f, 0.106f, 0.72f, SkeletonRig.HEAD, 1f, region = 2f),
                RingSpec(0f, 1.775f, 0.012f, 0.095f, 0.105f, 0.64f, SkeletonRig.HEAD, 1f, region = 2f),
                RingSpec(0f, 1.825f, 0.016f, 0.074f, 0.084f, 0.56f, SkeletonRig.HEAD, 1f, region = 2f),
                RingSpec(0f, 1.845f, 0.018f, 0.028f, 0.034f, 0.53f, SkeletonRig.HEAD, 1f, region = 2f)
            )
            1 -> listOf( // Textured Quiff / Swept Front
                RingSpec(0f, 1.735f, 0.012f, 0.095f, 0.104f, 0.72f, SkeletonRig.HEAD, 1f, region = 2f),
                RingSpec(0f, 1.790f, 0.022f, 0.092f, 0.112f, 0.63f, SkeletonRig.HEAD, 1f, region = 2f, frontBulge = 0.028f),
                RingSpec(0f, 1.848f, 0.030f, 0.072f, 0.095f, 0.55f, SkeletonRig.HEAD, 1f, region = 2f, frontBulge = 0.025f),
                RingSpec(0f, 1.868f, 0.026f, 0.025f, 0.036f, 0.53f, SkeletonRig.HEAD, 1f, region = 2f)
            )
            2 -> listOf( // Full Curly Afro / Volume
                RingSpec(0f, 1.705f, 0.005f, 0.114f, 0.122f, 0.73f, SkeletonRig.HEAD, 1f, region = 2f),
                RingSpec(0f, 1.775f, 0.008f, 0.126f, 0.132f, 0.64f, SkeletonRig.HEAD, 1f, region = 2f),
                RingSpec(0f, 1.845f, 0.010f, 0.110f, 0.116f, 0.56f, SkeletonRig.HEAD, 1f, region = 2f),
                RingSpec(0f, 1.885f, 0.010f, 0.052f, 0.056f, 0.53f, SkeletonRig.HEAD, 1f, region = 2f)
            )
            3 -> listOf( // Modern High-Top / Undercut
                RingSpec(0f, 1.755f, 0.016f, 0.082f, 0.098f, 0.71f, SkeletonRig.HEAD, 1f, region = 2f),
                RingSpec(0f, 1.825f, 0.018f, 0.084f, 0.102f, 0.62f, SkeletonRig.HEAD, 1f, region = 2f),
                RingSpec(0f, 1.872f, 0.020f, 0.076f, 0.094f, 0.54f, SkeletonRig.HEAD, 1f, region = 2f),
                RingSpec(0f, 1.882f, 0.020f, 0.022f, 0.028f, 0.53f, SkeletonRig.HEAD, 1f, region = 2f)
            )
            4 -> listOf( // Medium Wavy Flow (sweeping back past nape)
                RingSpec(0f, 1.630f, -0.022f, 0.088f, 0.085f, 0.74f, SkeletonRig.HEAD, 1f, region = 2f, backBulge = 0.045f),
                RingSpec(0f, 1.720f, -0.004f, 0.104f, 0.114f, 0.66f, SkeletonRig.HEAD, 1f, region = 2f, backBulge = 0.030f),
                RingSpec(0f, 1.795f, 0.012f, 0.102f, 0.112f, 0.58f, SkeletonRig.HEAD, 1f, region = 2f),
                RingSpec(0f, 1.842f, 0.014f, 0.068f, 0.076f, 0.54f, SkeletonRig.HEAD, 1f, region = 2f),
                RingSpec(0f, 1.856f, 0.014f, 0.022f, 0.026f, 0.53f, SkeletonRig.HEAD, 1f, region = 2f)
            )
            else -> listOf( // Buzz Cut / Close Shave
                RingSpec(0f, 1.730f, 0.012f, 0.092f, 0.102f, 0.72f, SkeletonRig.HEAD, 1f, region = 2f),
                RingSpec(0f, 1.785f, 0.014f, 0.088f, 0.098f, 0.62f, SkeletonRig.HEAD, 1f, region = 2f),
                RingSpec(0f, 1.830f, 0.016f, 0.056f, 0.064f, 0.54f, SkeletonRig.HEAD, 1f, region = 2f)
            )
        }

        loftVerticalRings(acc, rings, segs, uMin = 0.76f, uMax = 0.98f, capBottom = false, capTop = true)
        return acc.toData()
    }
}
