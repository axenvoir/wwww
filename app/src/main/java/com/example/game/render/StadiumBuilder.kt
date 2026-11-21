package com.example.game.render

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Builds detailed, batched 3D football stadium geometry into minimal GPU VBOs:
 *  - Stepped multi-tier stadium architecture (lower bowl, VIP glass hospitality deck, upper deck,
 *    cantilever steel canopy roofs, corner bowl connectors, concrete aisles, team dugouts) -> 1 static VBO
 *  - ~1,800 stylized 3D cheering spectators across all tiers -> 1 GPU-animated CrowdGlMesh VBO
 */
object StadiumBuilder {

    private fun MutableList<Float>.addVert(
        x: Float, y: Float, z: Float,
        nx: Float, ny: Float, nz: Float,
        r: Float, g: Float, b: Float
    ) {
        add(x); add(y); add(z)
        add(nx); add(ny); add(nz)
        add(r); add(g); add(b)
    }

    private fun MutableList<Float>.addBox(
        cx: Float, cy: Float, cz: Float,
        sx: Float, sy: Float, sz: Float,
        r: Float, g: Float, b: Float
    ) {
        val hx = sx * 0.5f; val hy = sy * 0.5f; val hz = sz * 0.5f
        val x0 = cx - hx; val x1 = cx + hx
        val y0 = cy - hy; val y1 = cy + hy
        val z0 = cz - hz; val z1 = cz + hz

        fun quad(
            ax: Float, ay: Float, az: Float,
            bx: Float, by: Float, bz: Float,
            dx: Float, dy: Float, dz: Float,
            ex: Float, ey: Float, ez: Float,
            nx: Float, ny: Float, nz: Float,
            shade: Float
        ) {
            val cr = r * shade; val cg = g * shade; val cb = b * shade
            addVert(ax, ay, az, nx, ny, nz, cr, cg, cb)
            addVert(bx, by, bz, nx, ny, nz, cr, cg, cb)
            addVert(dx, dy, dz, nx, ny, nz, cr, cg, cb)
            addVert(ax, ay, az, nx, ny, nz, cr, cg, cb)
            addVert(dx, dy, dz, nx, ny, nz, cr, cg, cb)
            addVert(ex, ey, ez, nx, ny, nz, cr, cg, cb)
        }

        quad(x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, 0f, 0f, 1f, 1.00f)
        quad(x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0, 0f, 0f, -1f, 0.90f)
        quad(x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, -1f, 0f, 0f, 0.94f)
        quad(x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1, 1f, 0f, 0f, 0.94f)
        quad(x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0, 0f, 1f, 0f, 1.08f)
        quad(x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, 0f, -1f, 0f, 0.72f)
    }

    /**
     * Bakes the entire stadium bowl (terraced lower/upper stands, VIP boxes, steel roof canopies,
     * concrete stair aisles, corner walls, and technical area team dugouts) into 1 single GPU draw call!
     */
    fun buildStadiumStructureMesh(): GlMesh {
        val v = ArrayList<Float>(18000)

        // Outer concrete pitch apron border / track rim
        v.addBox(0f, 0.04f, -39.2f, 128f, 0.08f, 3.2f, 0.16f, 0.20f, 0.24f)
        v.addBox(0f, 0.04f, 39.2f, 128f, 0.08f, 3.2f, 0.16f, 0.20f, 0.24f)
        v.addBox(-59.5f, 0.04f, 0f, 4.0f, 0.08f, 82f, 0.16f, 0.20f, 0.24f)
        v.addBox(59.5f, 0.04f, 0f, 4.0f, 0.08f, 82f, 0.16f, 0.20f, 0.24f)

        // === NORTH MAIN GRANDSTAND (z < -40) — Stepped 2-tier bowl + VIP Skyboxes + Cantilever Roof ===
        // Pitchside barrier wall
        v.addBox(0f, 0.75f, -40.6f, 118f, 1.5f, 0.5f, 0.20f, 0.24f, 0.30f)
        // Lower tier: 9 stepped concrete seating terraces
        for (row in 0 until 9) {
            val y = 1.25f + row * 0.72f
            val z = -41.4f - row * 1.15f
            val seatTint = if (row % 2 == 0) 0.14f else 0.17f
            v.addBox(0f, y * 0.5f, z, 116f, y, 1.2f, seatTint, seatTint + 0.04f, seatTint + 0.11f)
        }
        // Concrete stair aisles dividing the North lower tier
        for (aisle in -4..4) {
            val ax = aisle * 13.5f
            v.addBox(ax, 3.8f, -46.2f, 1.3f, 6.6f, 10.6f, 0.32f, 0.35f, 0.39f)
        }
        // Mid-tier hospitality fascia & illuminated glass VIP skyboxes
        v.addBox(0f, 8.6f, -51.8f, 120f, 1.2f, 1.2f, 0.22f, 0.26f, 0.34f)
        v.addBox(0f, 10.2f, -52.4f, 116f, 2.2f, 0.9f, 0.45f, 0.40f, 0.30f) // Warm glass VIP gallery
        // Upper tier: 7 stepped seating terraces
        for (row in 0 until 7) {
            val y = 11.4f + row * 0.85f
            val z = -53.2f - row * 1.20f
            val shade = if (row % 2 == 0) 0.15f else 0.18f
            v.addBox(0f, y - 0.45f, z, 120f, 1.1f, 1.3f, shade, shade + 0.04f, shade + 0.10f)
        }
        // Back wall + structural roof ribs + cantilever steel roof canopy
        v.addBox(0f, 12.0f, -62.0f, 124f, 24.0f, 1.0f, 0.12f, 0.15f, 0.21f)
        v.addBox(0f, 22.8f, -52.0f, 126f, 0.75f, 21.0f, 0.22f, 0.26f, 0.32f)
        v.addBox(0f, 22.3f, -41.8f, 126f, 0.65f, 0.75f, 0.82f, 0.88f, 0.96f) // Under-roof light gantry strip
        for (pillar in -5..5) {
            val px = pillar * 11.8f
            v.addBox(px, 22.2f, -52.0f, 0.65f, 1.1f, 20.5f, 0.30f, 0.34f, 0.40f)
        }

        // === WEST & EAST GOAL-END STANDS (x < -60 and x > +60) ===
        for (sign in intArrayOf(-1, 1)) {
            val baseWallX = sign * 61.0f
            v.addBox(baseWallX, 0.75f, 0f, 0.5f, 1.5f, 80f, 0.20f, 0.24f, 0.30f)
            // Lower tier (8 rows)
            for (row in 0 until 8) {
                val y = 1.25f + row * 0.74f
                val x = sign * (61.8f + row * 1.18f)
                val t = if (row % 2 == 0) 0.14f else 0.17f
                v.addBox(x, y * 0.5f, 0f, 1.25f, y, 78f, t, t + 0.04f, t + 0.10f)
            }
            // Mid fascia + Upper tier (5 rows)
            v.addBox(sign * 71.2f, 8.2f, 0f, 1.2f, 1.4f, 82f, 0.22f, 0.26f, 0.34f)
            for (row in 0 until 5) {
                val y = 9.5f + row * 0.85f
                val x = sign * (72.2f + row * 1.2f)
                v.addBox(x, y - 0.45f, 0f, 1.3f, 1.1f, 82f, 0.15f, 0.19f, 0.26f)
            }
            // Back wall & roof canopy
            v.addBox(sign * 78.6f, 10.0f, 0f, 1.0f, 20.0f, 86f, 0.12f, 0.15f, 0.21f)
            v.addBox(sign * 70.2f, 19.6f, 0f, 18.0f, 0.7f, 86f, 0.22f, 0.26f, 0.32f)
            v.addBox(sign * 61.6f, 19.1f, 0f, 0.65f, 0.6f, 84f, 0.82f, 0.88f, 0.96f)
        }

        // === CORNER BOWL CONNECTORS (closes the arena corners for true console broadcast feel) ===
        for (sx in intArrayOf(-1, 1)) {
            v.addBox(sx * 64.0f, 5.5f, -45.5f, 14f, 11.0f, 14f, 0.13f, 0.16f, 0.23f)
            // Corner Jumbotron Scoreboard frame
            v.addBox(sx * 60.5f, 14.5f, -43.0f, 10.0f, 5.2f, 1.0f, 0.08f, 0.10f, 0.14f)
            v.addBox(sx * 60.5f, 14.5f, -42.4f, 9.0f, 4.2f, 0.2f, 0.12f, 0.52f, 0.72f)
        }

        // === PITCHSIDE TECHNICAL DUGOUTS / TEAM BENCHES (North sideline) ===
        for (benchX in floatArrayOf(-13.5f, 13.5f)) {
            // Dugout back & translucent-look canopy shell
            v.addBox(benchX, 1.05f, -39.4f, 7.2f, 2.0f, 0.22f, 0.22f, 0.28f, 0.36f)
            v.addBox(benchX, 2.05f, -38.7f, 7.2f, 0.14f, 1.55f, 0.38f, 0.48f, 0.58f)
            // Team bench seats
            v.addBox(benchX, 0.45f, -38.9f, 6.4f, 0.42f, 0.55f, 0.12f, 0.14f, 0.18f)
        }

        // === LOW SOUTH TOUCHLINE WALL (stays below the broadcast gantry
        // Segmented low South media wall panels, coping rail & broadcast desks
        v.addBox(0f, 1.15f, 38.8f, 118.4f, 0.14f, 0.55f, 0.30f, 0.34f, 0.40f)
        for (seg in -5..5) {
            val sx = seg * 10.5f
            val isAccent = (seg and 1) == 0
            v.addBox(sx, 0.65f, 38.52f, 9.6f, 0.72f, 0.12f, if (isAccent) 0.12f else 0.16f, if (isAccent) 0.24f else 0.20f, if (isAccent) 0.48f else 0.28f)
        }
        for (desk in -2..2) {
            val dx = desk * 22f
            v.addBox(dx, 0.70f, 39.55f, 5.2f, 1.30f, 0.95f, 0.22f, 0.25f, 0.32f)
            v.addBox(dx, 1.40f, 39.45f, 5.4f, 0.12f, 1.15f, 0.34f, 0.38f, 0.44f)
        }

        // === NORTH / EAST / WEST REALISTIC SEAT BLOCKS (alternating club-toned rows) ===
        for (row in 0 until 9) {
            val sy = 1.12f + row * 0.72f
            val sz = -41.4f - row * 1.15f
            val evenRow = (row and 1) == 0
            for (blk in -4..3) {
                val bx = (blk + 0.5f) * 13.5f
                val clubBlock = blk < 0
                val sr = if (clubBlock) (if (evenRow) 0.14f else 0.20f) else (if (evenRow) 0.52f else 0.40f)
                val sg = if (clubBlock) (if (evenRow) 0.28f else 0.36f) else (if (evenRow) 0.16f else 0.14f)
                val sb = if (clubBlock) (if (evenRow) 0.62f else 0.72f) else (if (evenRow) 0.20f else 0.18f)
                v.addBox(bx, sy, sz, 11.4f, 0.32f, 0.52f, sr, sg, sb)
            }
        }
        for (row in 0 until 6) {
            val sy = 11.75f + row * 0.85f
            val sz = -52.2f - row * 1.15f
            val evenRow = (row and 1) == 0
            for (blk in -4..3) {
                val bx = (blk + 0.5f) * 13.5f
                val sr = if (evenRow) 0.16f else 0.24f
                val sg = if (evenRow) 0.30f else 0.40f
                val sb = if (evenRow) 0.64f else 0.74f
                v.addBox(bx, sy, sz, 11.4f, 0.32f, 0.52f, sr, sg, sb)
            }
        }
        for (side in listOf(-1f, 1f)) {
            for (row in 0 until 7) {
                val sx = side * (63.4f + row * 1.15f)
                val sy = 1.12f + row * 0.72f
                val evenRow = (row and 1) == 0
                for (blk in -2..1) {
                    val bz = (blk + 0.5f) * 12f
                    val isHome = side < 0f
                    val sr = if (isHome) (if (evenRow) 0.15f else 0.22f) else (if (evenRow) 0.55f else 0.42f)
                    val sg = if (isHome) (if (evenRow) 0.30f else 0.38f) else (if (evenRow) 0.18f else 0.14f)
                    val sb = if (isHome) (if (evenRow) 0.64f else 0.75f) else (if (evenRow) 0.22f else 0.18f)
                    v.addBox(sx, sy, bz, 0.52f, 0.32f, 9.8f, sr, sg, sb)
                }
            }
        }

        // === STAIRWAY AISLE HANDRAILS ===
        for (aisle in -4..4) {
            val ax = aisle * 13.5f
            v.addBox(ax, 4.4f, -46.2f, 0.10f, 0.10f, 10.6f, 0.68f, 0.72f, 0.78f)
            v.addBox(ax, 14.2f, -55.2f, 0.10f, 0.10f, 7.2f, 0.68f, 0.72f, 0.78f)
            v.addBox(ax, 1.55f, -41.2f, 0.10f, 0.90f, 0.10f, 0.58f, 0.62f, 0.68f)
            v.addBox(ax, 7.20f, -50.8f, 0.10f, 0.90f, 0.10f, 0.58f, 0.62f, 0.68f)
        }
        for (side in listOf(-1f, 1f)) {
            for (aisle in -2..2) {
                val az = aisle * 12f
                v.addBox(side * 66.8f, 3.8f, az, 8.2f, 0.10f, 0.10f, 0.68f, 0.72f, 0.78f)
                v.addBox(side * 63.2f, 1.45f, az, 0.10f, 0.85f, 0.10f, 0.58f, 0.62f, 0.68f)
            }
        }

        // === ROOF STEEL TRUSS BRACING & FLOODLIGHT ROOF HOUSINGS ===
        for (truss in -5..5) {
            val tx = truss * 10.8f
            v.addBox(tx, 20.1f, -52.5f, 0.32f, 0.48f, 16.5f, 0.36f, 0.40f, 0.46f)
            v.addBox(tx + 5.4f, 20.1f, -52.5f, 0.18f, 0.24f, 15.8f, 0.42f, 0.46f, 0.52f)
        }
        v.addBox(0f, 20.0f, -46.5f, 118f, 0.34f, 0.34f, 0.40f, 0.44f, 0.50f)
        v.addBox(0f, 20.0f, -57.5f, 118f, 0.34f, 0.34f, 0.40f, 0.44f, 0.50f)
        for (side in listOf(-1f, 1f)) {
            for (truss in -3..3) {
                val tz = truss * 10.5f
                v.addBox(side * 73.5f, 14.0f, tz, 13.5f, 0.38f, 0.28f, 0.36f, 0.40f, 0.46f)
            }
            v.addBox(side * 67.5f, 13.9f, 0f, 0.30f, 0.30f, 68f, 0.40f, 0.44f, 0.50f)
        }
        val floodCorners = arrayOf(
            floatArrayOf(-66f, -47f), floatArrayOf(66f, -47f),
            floatArrayOf(-66f, 47f), floatArrayOf(66f, 47f)
        )
        for (fc in floodCorners) {
            v.addBox(fc[0], 27.6f, fc[1], 6.4f, 0.45f, 3.2f, 0.24f, 0.28f, 0.34f)
            v.addBox(fc[0], 26.2f, fc[1], 5.8f, 2.4f, 1.4f, 0.30f, 0.34f, 0.40f)
            v.addBox(fc[0], 26.2f, fc[1] + (if (fc[1] < 0f) 0.78f else -0.78f), 5.2f, 1.9f, 0.22f, 0.96f, 0.98f, 1.0f)
        }
        v.addBox(0f, 0.55f, 38.8f, 118f, 1.1f, 0.45f, 0.18f, 0.22f, 0.28f)

        return GlMesh(v.toFloatArray())
    }

    /**
     * Generates ~1,800 stylized 3D spectators (torso + head silhouettes) seated across
     * North, West, and East lower & upper stadium tiers, tinted in Home/Away/neutral fan colors,
     * packed into 1 single GPU VBO for 60 FPS vertex-shader wave & goal-jump animation.
     */
    fun buildAnimatedCrowdMesh(homeRgb: FloatArray, awayRgb: FloatArray): CrowdGlMesh {
        val v = ArrayList<Float>(1800 * 36 * 10)
        val rng = Random(2026)

        val skinPalette = arrayOf(
            floatArrayOf(0.90f, 0.73f, 0.58f),
            floatArrayOf(0.76f, 0.56f, 0.40f),
            floatArrayOf(0.52f, 0.34f, 0.22f),
            floatArrayOf(0.32f, 0.20f, 0.14f)
        )
        val neutralShirts = arrayOf(
            floatArrayOf(0.18f, 0.22f, 0.30f),
            floatArrayOf(0.32f, 0.36f, 0.42f),
            floatArrayOf(0.72f, 0.74f, 0.78f),
            floatArrayOf(0.14f, 0.26f, 0.42f)
        )

        val hairPalette = arrayOf(
            floatArrayOf(0.10f, 0.08f, 0.07f),
            floatArrayOf(0.24f, 0.16f, 0.10f),
            floatArrayOf(0.45f, 0.30f, 0.16f),
            floatArrayOf(0.72f, 0.58f, 0.32f),
            floatArrayOf(0.55f, 0.55f, 0.58f)
        )
        val jacketPalette = arrayOf(
            floatArrayOf(0.12f, 0.14f, 0.18f),
            floatArrayOf(0.28f, 0.14f, 0.16f),
            floatArrayOf(0.14f, 0.24f, 0.18f),
            floatArrayOf(0.36f, 0.34f, 0.30f),
            floatArrayOf(0.18f, 0.24f, 0.36f)
        )
        fun addSpectatorBox(
            cx: Float, cy: Float, cz: Float,
            sx: Float, sy: Float, sz: Float,
            r: Float, g: Float, b: Float,
            phase: Float
        ) {
            val hx = sx * 0.5f; val hy = sy * 0.5f; val hz = sz * 0.5f
            val x0 = cx - hx; val x1 = cx + hx
            val y0 = cy - hy; val y1 = cy + hy
            val z0 = cz - hz; val z1 = cz + hz

            fun vtx(x: Float, y: Float, z: Float, nx: Float, ny: Float, nz: Float, s: Float) {
                v.add(x); v.add(y); v.add(z)
                v.add(nx); v.add(ny); v.add(nz)
                v.add(r * s); v.add(g * s); v.add(b * s)
                v.add(phase)
            }
            // Front, Top, Left, Right faces (4 faces = 8 tris per box -> super GPU efficient)
            // +Z
            vtx(x0, y0, z1, 0f, 0f, 1f, 1.0f); vtx(x1, y0, z1, 0f, 0f, 1f, 1.0f); vtx(x1, y1, z1, 0f, 0f, 1f, 1.0f)
            vtx(x0, y0, z1, 0f, 0f, 1f, 1.0f); vtx(x1, y1, z1, 0f, 0f, 1f, 1.0f); vtx(x0, y1, z1, 0f, 0f, 1f, 1.0f)
            // +Y
            vtx(x0, y1, z1, 0f, 1f, 0f, 1.1f); vtx(x1, y1, z1, 0f, 1f, 0f, 1.1f); vtx(x1, y1, z0, 0f, 1f, 0f, 1.1f)
            vtx(x0, y1, z1, 0f, 1f, 0f, 1.1f); vtx(x1, y1, z0, 0f, 1f, 0f, 1.1f); vtx(x0, y1, z0, 0f, 1f, 0f, 1.1f)
            // -X
            vtx(x0, y0, z0, -1f, 0f, 0f, 0.88f); vtx(x0, y0, z1, -1f, 0f, 0f, 0.88f); vtx(x0, y1, z1, -1f, 0f, 0f, 0.88f)
            vtx(x0, y0, z0, -1f, 0f, 0f, 0.88f); vtx(x0, y1, z1, -1f, 0f, 0f, 0.88f); vtx(x0, y1, z0, -1f, 0f, 0f, 0.88f)
            // +X
            vtx(x1, y0, z1, 1f, 0f, 0f, 0.88f); vtx(x1, y0, z0, 1f, 0f, 0f, 0.88f); vtx(x1, y1, z0, 1f, 0f, 0f, 0.88f)
            vtx(x1, y0, z1, 1f, 0f, 0f, 0.88f); vtx(x1, y1, z0, 1f, 0f, 0f, 0.88f); vtx(x1, y1, z1, 1f, 0f, 0f, 0.88f)
        }

        fun pickFanShirt(teamRgb: FloatArray): FloatArray {
            val roll = rng.nextFloat()
            return when {
                roll < 0.56f -> floatArrayOf(
                    (teamRgb[0] * (0.82f + rng.nextFloat() * 0.28f)).coerceAtMost(1f),
                    (teamRgb[1] * (0.82f + rng.nextFloat() * 0.28f)).coerceAtMost(1f),
                    (teamRgb[2] * (0.82f + rng.nextFloat() * 0.28f)).coerceAtMost(1f)
                )
                else -> neutralShirts[rng.nextInt(neutralShirts.size)]
            }
        }

        fun addSpectator(x: Float, y: Float, z: Float, teamRgb: FloatArray) {
            val useJacket = rng.nextFloat() < 0.28f
            val shirt = if (useJacket) jacketPalette[rng.nextInt(jacketPalette.size)] else pickFanShirt(teamRgb)
            val skin = skinPalette[rng.nextInt(skinPalette.size)]
            val hair = hairPalette[rng.nextInt(hairPalette.size)]
            val twoPi = (2.0 * PI).toFloat()
            val rawPhase = (x * 0.08f + z * 0.06f) + rng.nextFloat() * 1.85f
            val phase = ((rawPhase % twoPi) + twoPi) % twoPi
            val hScale = 0.88f + rng.nextFloat() * 0.24f
            // Torso
            addSpectatorBox(x, y + 0.42f * hScale, z, 0.54f, 0.72f * hScale, 0.36f, shirt[0], shirt[1], shirt[2], phase)
            // Scarf
            if (rng.nextFloat() < 0.42f) {
                addSpectatorBox(x, y + 0.77f * hScale, z + 0.02f, 0.44f, 0.12f, 0.34f, teamRgb[0], teamRgb[1], teamRgb[2], phase)
            }
            // Head & Hair
            addSpectatorBox(x, y + 0.94f * hScale, z, 0.28f, 0.30f * hScale, 0.28f, skin[0], skin[1], skin[2], phase)
            addSpectatorBox(x, y + 1.07f * hScale, z, 0.30f, 0.11f * hScale, 0.30f, hair[0], hair[1], hair[2], phase)
        }

        // North Lower Tier (9 rows x ~64 seats, skipping stairways)
        for (row in 0 until 9) {
            val y = 1.25f + row * 0.72f
            val z = -41.4f - row * 1.15f
            var x = -55.5f
            while (x <= 55.5f) {
                val nearAisle = ((-4..4).any { kotlin.math.abs(x - it * 13.5f) < 1.1f })
                if (!nearAisle && rng.nextFloat() > 0.14f) {
                    val club = if (x < 4f) homeRgb else awayRgb
                    addSpectator(x + (rng.nextFloat() - 0.5f) * 0.25f, y, z, club)
                }
                x += 1.65f
            }
        }

        // North Upper Tier (6 rows)
        for (row in 0 until 6) {
            val y = 11.9f + row * 0.85f
            val z = -53.2f - row * 1.20f
            var x = -56f
            while (x <= 56f) {
                if (rng.nextFloat() > 0.22f) {
                    val club = if (x < 0f) homeRgb else awayRgb
                    addSpectator(x, y, z, club)
                }
                x += 2.25f
            }
        }

        // West (Home End) & East (Away End) Lower Tiers (7 rows each)
        for (sign in intArrayOf(-1, 1)) {
            val club = if (sign < 0) homeRgb else awayRgb
            for (row in 0 until 7) {
                val y = 1.25f + row * 0.74f
                val x = sign * (61.8f + row * 1.18f)
                var z = -35.5f
                while (z <= 35.5f) {
                    if (rng.nextFloat() > 0.18f) {
                        addSpectator(x, y, z + (rng.nextFloat() - 0.5f) * 0.2f, club)
                    }
                    z += 1.85f
                }
            }
        }

        return CrowdGlMesh(v.toFloatArray())
    }
}
