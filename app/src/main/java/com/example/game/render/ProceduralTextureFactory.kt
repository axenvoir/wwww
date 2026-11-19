package com.example.game.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.opengl.GLES30
import android.opengl.GLUtils
import com.example.game.model.ClubTeam
import com.example.game.model.KitColors

/**
 * Generates crisp procedural OpenGL ES 3.0 textures via Android Canvas:
 *  - Club Kit + Character Face/Skin/Hair/Boot/Glove Atlas (512x512, mipmapped)
 *  - Scrolling Broadcast Pitchside LED Advertising Boards (1024x128)
 *  - Woven Diamond-Mesh Goal Net with alpha transparency (256x256)
 */
object ProceduralTextureFactory {

    /**
     * Generates a 512x512 club kit & humanoid character texture atlas.
     * UV Quadrants match HumanoidMeshBuilder:
     *  - [0.00..0.50, 0.00..0.50]: Jersey (front & back patterns, collar, crest, sponsor mark)
     *  - [0.50..0.74, 0.00..0.24]: Jersey sleeves + cuff stripes
     *  - [0.50..1.00, 0.24..0.50]: Shorts + side trim + hem stripe
     *  - [0.50..0.75, 0.50..0.75]: Football socks + turnover stripes
     *  - [0.00..0.50, 0.50..1.00]: Neutral-shaded anatomical face & skin detail map (tinted by uSkinColor)
     *  - [0.75..1.00, 0.50..0.75]: Hair strands detail (tinted by uHairColor)
     *  - [0.75..1.00, 0.75..1.00]: Football boot laces & side swoop / GK glove palm
     */
    fun createClubKitAtlas(team: ClubTeam, isGoalkeeper: Boolean): Int {
        val size = 512
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        val jerseyColor = if (isGoalkeeper) team.kit.gkArgb.toInt() else team.kit.jerseyArgb.toInt()
        val shortsColor = if (isGoalkeeper) Color.rgb(18, 22, 28) else team.kit.shortsArgb.toInt()
        val accentColor = team.kit.accentArgb.toInt()
        val darkTrim = darken(jerseyColor, 0.68f)

        c.drawColor(jerseyColor)

        // === 1. JERSEY QUADRANT [0..256, 0..256] ===
        paint.color = jerseyColor
        c.drawRect(0f, 0f, 256f, 256f, paint)

        // Club-specific real-football jersey patterns (stripes, sash, hoops, chevron)
        val patternStyle = if (isGoalkeeper) 3 else (team.id.hashCode() and 0x7FFFFFFF) % 4
        when (patternStyle) {
            0 -> { // Vertical Championship Stripes
                paint.color = accentColor
                paint.alpha = 185
                var x = 16f
                while (x < 256f) {
                    c.drawRect(x, 12f, x + 18f, 256f, paint)
                    x += 44f
                }
                paint.alpha = 255
            }
            1 -> { // Subtle Diagonal Club Sash + Jacquard Pinstripes
                paint.color = darkTrim
                paint.alpha = 85
                var x = 8f
                while (x < 256f) {
                    c.drawRect(x, 0f, x + 3f, 256f, paint)
                    x += 16f
                }
                paint.color = accentColor
                paint.alpha = 215
                val path = Path().apply {
                    moveTo(0f, 52f); lineTo(128f, 200f); lineTo(128f, 232f); lineTo(0f, 84f); close()
                }
                c.drawPath(path, paint)
                paint.alpha = 255
            }
            2 -> { // Horizontal Chest Band + Side Panels
                paint.color = accentColor
                c.drawRect(0f, 86f, 256f, 124f, paint)
                paint.color = darkTrim
                c.drawRect(0f, 124f, 256f, 132f, paint)
            }
            else -> { // Modern Subtle Athletic Chevron Weave
                paint.color = darkTrim
                paint.alpha = 110
                for (row in 0 until 6) {
                    val y = 48f + row * 32f
                    val p = Path().apply {
                        moveTo(0f, y); lineTo(64f, y + 14f); lineTo(128f, y)
                        lineTo(192f, y + 14f); lineTo(256f, y)
                        lineTo(256f, y + 8f); lineTo(192f, y + 22f); lineTo(128f, y + 8f)
                        lineTo(64f, y + 22f); lineTo(0f, y + 8f); close()
                    }
                    c.drawPath(p, paint)
                }
                paint.alpha = 255
            }
        }

        // Jersey collar trim at top of jersey UV
        paint.color = accentColor
        c.drawRect(0f, 0f, 256f, 20f, paint)
        paint.color = darkTrim
        c.drawRect(0f, 20f, 256f, 25f, paint)

        // Subtle fabric ribcage / athletic fold shading
        paint.shader = LinearGradient(
            0f, 24f, 0f, 256f,
            intArrayOf(Color.argb(30, 255, 255, 255), Color.argb(0, 0, 0, 0), Color.argb(55, 0, 0, 0)),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP
        )
        c.drawRect(0f, 24f, 256f, 256f, paint)
        paint.shader = null

        // Club Shield Crest on left chest (front center is near u=0.0/1.0 wrap or u=0.25 -> draw near front chest x=24..46 & x=212..234)
        // Note: in loftVerticalRings theta=0 is +Z (u = uMin = 0.02 -> x=10, and theta=2PI is uMax = 0.48 -> x=246; back is theta=PI -> x=128)
        drawClubCrest(c, 32f, 84f, accentColor, darkTrim)
        // Clean geometric sponsor mark on front chest (split cleanly around theta=0 seam at x=0..38 and x=218..256)
        paint.color = contrastColor(jerseyColor)
        paint.alpha = 210
        paint.textSize = 19f
        paint.isFakeBoldText = true
        paint.textAlign = Paint.Align.CENTER
        c.drawText(team.shortCode, 224f, 126f, paint)
        paint.alpha = 255

        // Back-of-shirt clean name/number panel accent at x=128 (theta = PI -> back)
        paint.color = contrastColor(jerseyColor)
        paint.alpha = 215
        paint.textSize = 13f
        c.drawText(team.shortCode, 128f, 66f, paint)
        // Crisp stylized vertical athletic back stripe mark
        paint.strokeWidth = 6f
        paint.style = Paint.Style.STROKE
        c.drawRoundRect(112f, 82f, 144f, 150f, 6f, 6f, paint)
        paint.style = Paint.Style.FILL
        paint.alpha = 255

        // === 2. SLEEVES [256..384, 0..128] ===
        paint.color = jerseyColor
        c.drawRect(256f, 0f, 384f, 128f, paint)
        paint.color = accentColor
        c.drawRect(256f, 96f, 384f, 114f, paint)
        paint.color = darkTrim
        c.drawRect(256f, 114f, 384f, 128f, paint)

        // === 3. SHORTS [256..512, 128..256] ===
        paint.color = shortsColor
        c.drawRect(256f, 128f, 512f, 256f, paint)
        paint.color = accentColor
        c.drawRect(256f, 236f, 512f, 248f, paint)
        // Side racing stripe along outer shorts seam
        c.drawRect(318f, 132f, 328f, 248f, paint)
        c.drawRect(442f, 132f, 452f, 248f, paint)

        // === 4. FOOTBALL SOCKS [256..384, 256..384] ===
        val sockColor = if (isGoalkeeper) jerseyColor else jerseyColor
        paint.color = sockColor
        c.drawRect(256f, 256f, 384f, 384f, paint)
        // Top turnover sock stripes
        paint.color = accentColor
        c.drawRect(256f, 266f, 384f, 282f, paint)
        paint.color = Color.WHITE
        paint.alpha = 200
        c.drawRect(256f, 286f, 384f, 293f, paint)
        // Subtle knit ribbing
        paint.color = Color.BLACK
        paint.alpha = 24
        var sy = 300f
        while (sy < 384f) {
            c.drawRect(256f, sy, 384f, sy + 2f, paint)
            sy += 6f
        }
        paint.alpha = 255

        // === 5. NEUTRAL FACE & SKIN DETAIL MAP [0..256, 256..512] ===
        // Front face is centered at u=0.25 -> x=128, v=0.74 -> y=379
        drawNeutralFaceDetailMap(c)

        // === 6. HAIR STRANDS DETAIL [384..512, 256..384] ===
        paint.color = Color.rgb(228, 228, 228)
        c.drawRect(384f, 256f, 512f, 384f, paint)
        for (i in 0 until 64) {
            val hx = 384f + i * 2f
            paint.color = if (i % 3 == 0) Color.rgb(172, 172, 172) else Color.rgb(245, 245, 245)
            c.drawRect(hx, 256f, hx + 1.2f, 384f, paint)
        }

        // === 7. FOOTBALL BOOT & GK GLOVE DETAIL [384..512, 384..512] ===
        paint.color = Color.rgb(236, 236, 240)
        c.drawRect(384f, 384f, 512f, 512f, paint)
        // Dark carbon soleplate band along bottom & heel
        paint.color = Color.rgb(34, 38, 46)
        c.drawRect(384f, 488f, 512f, 512f, paint)
        // Dynamic side speed swoop
        paint.color = Color.WHITE
        val swoop = Path().apply {
            moveTo(392f, 468f); lineTo(476f, 434f); lineTo(498f, 444f); lineTo(404f, 478f); close()
        }
        c.drawPath(swoop, paint)
        // Cross-lacing lines across boot instep
        paint.color = Color.rgb(42, 46, 54)
        paint.strokeWidth = 3f
        for (l in 0 until 5) {
            val lx = 414f + l * 13f
            c.drawLine(lx, 404f, lx + 8f, 424f, paint)
            c.drawLine(lx + 8f, 404f, lx, 424f, paint)
        }

        val texId = uploadTexture(bmp, repeat = false)
        bmp.recycle()
        return texId
    }

    private fun drawClubCrest(c: Canvas, cx: Float, cy: Float, accent: Int, trim: Int) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val shield = Path().apply {
            moveTo(cx, cy - 11f)
            lineTo(cx + 10f, cy - 8f)
            lineTo(cx + 9f, cy + 4f)
            lineTo(cx, cy + 12f)
            lineTo(cx - 9f, cy + 4f)
            lineTo(cx - 10f, cy - 8f)
            close()
        }
        p.color = Color.WHITE
        c.drawPath(shield, p)
        p.color = trim
        p.style = Paint.Style.STROKE
        p.strokeWidth = 2f
        c.drawPath(shield, p)
        p.style = Paint.Style.FILL
        p.color = accent
        c.drawCircle(cx, cy, 4.5f, p)
    }

    private fun drawNeutralFaceDetailMap(c: Canvas) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        // Base skin brightness (tinted cleanly by uSkinColor in fragment shader)
        p.color = Color.rgb(238, 234, 232)
        c.drawRect(0f, 256f, 256f, 512f, p)

        // Subtle jaw / cheekbone / neck shadow gradient
        p.shader = LinearGradient(
            0f, 290f, 0f, 500f,
            intArrayOf(Color.argb(35, 0, 0, 0), Color.argb(0, 0, 0, 0), Color.argb(48, 30, 18, 14)),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP
        )
        c.drawRect(0f, 256f, 256f, 512f, p)
        p.shader = null

        val cx = 128f
        val eyeY = 368f
        val eyeOffset = 19f

        // Eye sockets subtle shading
        p.color = Color.argb(46, 55, 36, 28)
        c.drawOval(cx - eyeOffset - 13f, eyeY - 9f, cx - eyeOffset + 13f, eyeY + 9f, p)
        c.drawOval(cx + eyeOffset - 13f, eyeY - 9f, cx + eyeOffset + 13f, eyeY + 9f, p)

        // Eyebrows
        p.color = Color.rgb(42, 34, 30)
        p.strokeWidth = 5.5f
        p.strokeCap = Paint.Cap.ROUND
        c.drawLine(cx - eyeOffset - 12f, eyeY - 11f, cx - eyeOffset + 10f, eyeY - 13f, p)
        c.drawLine(cx + eyeOffset - 10f, eyeY - 13f, cx + eyeOffset + 12f, eyeY - 11f, p)

        // Eye sclera (pure bright white preserved in shader)
        p.color = Color.rgb(252, 252, 254)
        c.drawOval(cx - eyeOffset - 9f, eyeY - 4.5f, cx - eyeOffset + 9f, eyeY + 4.5f, p)
        c.drawOval(cx + eyeOffset - 9f, eyeY - 4.5f, cx + eyeOffset + 9f, eyeY + 4.5f, p)

        // Irises & pupils
        p.color = Color.rgb(48, 36, 28)
        c.drawCircle(cx - eyeOffset, eyeY, 4.0f, p)
        c.drawCircle(cx + eyeOffset, eyeY, 4.0f, p)
        p.color = Color.rgb(12, 12, 14)
        c.drawCircle(cx - eyeOffset, eyeY, 2.2f, p)
        c.drawCircle(cx + eyeOffset, eyeY, 2.2f, p)
        // Specular catchlight in eyes
        p.color = Color.WHITE
        c.drawCircle(cx - eyeOffset + 1.4f, eyeY - 1.4f, 1.0f, p)
        c.drawCircle(cx + eyeOffset + 1.4f, eyeY - 1.4f, 1.0f, p)

        // Eyelash upper lid line
        p.color = Color.rgb(28, 22, 20)
        p.strokeWidth = 2.2f
        c.drawLine(cx - eyeOffset - 9f, eyeY - 4f, cx - eyeOffset + 9f, eyeY - 4f, p)
        c.drawLine(cx + eyeOffset - 9f, eyeY - 4f, cx + eyeOffset + 9f, eyeY - 4f, p)

        // Nose bridge highlight & subtle nostril shadows
        p.color = Color.argb(38, 255, 255, 255)
        c.drawRoundRect(cx - 3.5f, eyeY + 2f, cx + 3.5f, eyeY + 28f, 3f, 3f, p)
        p.color = Color.argb(72, 48, 28, 22)
        c.drawCircle(cx - 5.5f, eyeY + 30f, 3.2f, p)
        c.drawCircle(cx + 5.5f, eyeY + 30f, 3.2f, p)

        // Athletic mouth / lips
        p.color = Color.rgb(168, 112, 106)
        c.drawRoundRect(cx - 14f, eyeY + 42f, cx + 14f, eyeY + 50f, 5f, 5f, p)
        p.color = Color.rgb(92, 54, 52)
        p.strokeWidth = 2.0f
        c.drawLine(cx - 13f, eyeY + 46f, cx + 13f, eyeY + 46f, p)

        // Subtle athletic jawline shadow
        p.color = Color.argb(34, 40, 28, 22)
        c.drawOval(cx - 28f, eyeY + 54f, cx + 28f, eyeY + 68f, p)
    }

    /**
     * Generates a crisp 1024x128 stadium perimeter LED advertising board strip texture.
     */
    fun createLedBoardTexture(): Int {
        val w = 1024
        val h = 128
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)

        val panels = listOf(
            Triple("MATCHDAY LAN", Color.rgb(8, 24, 42), Color.rgb(0, 230, 118)),
            Triple("EUROPA ARENA", Color.rgb(16, 18, 34), Color.rgb(255, 200, 32)),
            Triple("PRO 11v11 LIVE", Color.rgb(12, 34, 58), Color.rgb(56, 189, 248)),
            Triple("CHAMPIONSHIP", Color.rgb(34, 14, 24), Color.rgb(248, 113, 113))
        )
        val pw = w / panels.size.toFloat()

        panels.forEachIndexed { idx, (label, bg, accent) ->
            val x0 = idx * pw
            p.color = bg
            c.drawRect(x0, 0f, x0 + pw, h.toFloat(), p)

            // Top & bottom glowing LED ribbons
            p.color = accent
            c.drawRect(x0, 0f, x0 + pw, 12f, p)
            c.drawRect(x0, h - 12f, x0 + pw, h.toFloat(), p)

            // Angled graphic bar
            val bar = Path().apply {
                moveTo(x0 + 14f, 18f); lineTo(x0 + 36f, 18f)
                lineTo(x0 + 22f, h - 18f); lineTo(x0, h - 18f); close()
            }
            p.alpha = 210
            c.drawPath(bar, p)
            p.alpha = 255

            // Bold broadcast title
            p.color = Color.WHITE
            p.textSize = 30f
            p.isFakeBoldText = true
            p.textAlign = Paint.Align.CENTER
            c.drawText(label, x0 + pw * 0.54f, h * 0.62f, p)
        }

        // Subtle LED dot-matrix scanlines
        p.color = Color.argb(42, 0, 0, 0)
        var y = 0f
        while (y < h) {
            c.drawRect(0f, y, w.toFloat(), y + 2f, p)
            y += 6f
        }

        val texId = uploadTexture(bmp, repeat = true)
        bmp.recycle()
        return texId
    }

    /**
     * Generates a crisp transparent diamond-weave football goal net texture (256x256).
     */
    fun createGoalNetTexture(): Int {
        val size = 256
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.TRANSPARENT)

        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(235, 242, 246, 250)
            strokeWidth = 5.5f
            style = Paint.Style.STROKE
        }
        val step = 32f
        var offset = -size.toFloat()
        while (offset < size * 2f) {
            c.drawLine(offset, 0f, offset + size, size.toFloat(), p)
            c.drawLine(offset, size.toFloat(), offset + size, 0f, p)
            offset += step
        }

        val texId = uploadTexture(bmp, repeat = true)
        bmp.recycle()
        return texId
    }

    private fun uploadTexture(bitmap: Bitmap, repeat: Boolean): Int {
        val ids = IntArray(1)
        GLES30.glGenTextures(1, ids, 0)
        val id = ids[0]
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, id)
        val wrap = if (repeat) GLES30.GL_REPEAT else GLES30.GL_CLAMP_TO_EDGE
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, wrap)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, wrap)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR_MIPMAP_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, bitmap, 0)
        GLES30.glGenerateMipmap(GLES30.GL_TEXTURE_2D)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
        return id
    }

    private fun darken(argb: Int, factor: Float): Int {
        val r = (Color.red(argb) * factor).toInt().coerceIn(0, 255)
        val g = (Color.green(argb) * factor).toInt().coerceIn(0, 255)
        val b = (Color.blue(argb) * factor).toInt().coerceIn(0, 255)
        return Color.rgb(r, g, b)
    }

    private fun contrastColor(argb: Int): Int {
        val lum = 0.299f * Color.red(argb) + 0.587f * Color.green(argb) + 0.114f * Color.blue(argb)
        return if (lum > 165f) Color.rgb(18, 24, 36) else Color.WHITE
    }
}
