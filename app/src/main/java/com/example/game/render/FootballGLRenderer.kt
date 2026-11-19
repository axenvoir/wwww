package com.example.game.render

import android.content.Context
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import com.example.game.engine.PitchConstants
import com.example.game.engine.RenderPlayer
import com.example.game.engine.RenderWorldSnapshot
import com.example.game.model.ClubTeam
import com.example.game.model.GraphicsPipelineMode
import com.example.game.model.GraphicsQuality
import com.example.game.model.MatchPhase
import com.example.game.model.PlayerRole
import com.example.game.model.StadiumPreset
import com.example.game.model.TeamSide
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * Upgraded OpenGL ES 3.0 3D Football Renderer:
 *  - Anatomically sculpted 19-bone GPU-skinned 3D human football players (with glTF 2.0 .glb asset hook)
 *  - Smooth cross-faded skeletal animation per player (running, sprinting, dribbling, kicking, tackling, jumping, GK dives, celebrations)
 *  - Distinct player appearances (6 skin tones, 6 hair colors, 6 3D hairstyles, 5 cleat colors, dynamic club kit textures & build scales)
 *  - High-resolution 1,280-triangle 32-panel FIFA match football with realistic rotation & foot contact
 *  - Multi-tier 3D football stadium, ~1,800 GPU-animated cheering spectators, scrolling LED ad boards, cylindrical goalposts & woven nets
 *  - eFootball-style elevated diagonal sideline broadcast camera with dynamic attack zoom & user Zoom/Height tuning (0..10)
 */
class FootballGLRenderer(
    private val context: Context,
    private val snapshotProvider: () -> RenderWorldSnapshot,
    private val onFrameTick: (dt: Float, lastRenderNanos: Long) -> Unit
) : GLSurfaceView.Renderer {

private val fallbackController = GraphicsFallbackController()
private var pitchPbrProgram = 0
private var skinnedPbrProgram = 0
private var activePipelineMode = GraphicsPipelineMode.CLASSIC
    // Shader programs
    private var skinnedProgram = 0
    private var ballProgram = 0
    private var pitchProgram = 0
    private var crowdProgram = 0
    private var texturedProgram = 0
    private var entityProgram = 0

    // Skinned player uniforms
    private var uSkVp = 0; private var uSkModel = 0; private var uSkBones = 0
    private var uSkAtlas = 0; private var uSkSkin = 0; private var uSkHair = 0
    private var uSkBoot = 0; private var uSkGlove = 0; private var uSkIsGk = 0
    private var uSkSunDir = 0; private var uSkSunCol = 0; private var uSkAmb = 0
    private var uSkFlood = 0; private var uSkCamPos = 0; private var uSkSky = 0

    // Ball uniforms
    private var uBallMvp = 0; private var uBallModel = 0
    private var uBallSunDir = 0; private var uBallSunCol = 0; private var uBallAmb = 0
    private var uBallFlood = 0; private var uBallCamPos = 0; private var uBallSky = 0

    // Pitch uniforms
    private var uPitchMvp = 0; private var uPitchModel = 0; private var uPitchGrass = 0
    private var uPitchSunCol = 0; private var uPitchAmb = 0; private var uPitchFlood = 0
    private var uPitchSky = 0; private var uPitchCamPos = 0; private var uPitchHighDetail = 0

    // Crowd uniforms
    private var uCrowdVp = 0; private var uCrowdTime = 0; private var uCrowdExcite = 0
    private var uCrowdSunDir = 0; private var uCrowdSunCol = 0; private var uCrowdAmb = 0
    private var uCrowdCamPos = 0; private var uCrowdSky = 0

    // Textured LED / Net uniforms
    private var uTexMvp = 0; private var uTexModel = 0; private var uTexSampler = 0
    private var uTexUvOff = 0; private var uTexTint = 0; private var uTexEmissive = 0
    private var uTexNetWave = 0; private var uTexTime = 0

    // Generic lit entity uniforms
    private var uEntMvp = 0; private var uEntModel = 0; private var uEntBaseColor = 0
    private var uEntUseVertColor = 0; private var uEntEmissive = 0
    private var uEntSunDir = 0; private var uEntSunCol = 0; private var uEntAmb = 0
    private var uEntFlood = 0; private var uEntCamPos = 0; private var uEntSky = 0

    // GPU Meshes
    private lateinit var pitchMesh: GlMesh
    private lateinit var stadiumStructureMesh: GlMesh
    private var crowdMesh: CrowdGlMesh? = null
    private lateinit var cubeMesh: GlMesh
    private lateinit var cylinderMesh: GlMesh
    private lateinit var ballMeshHigh: GlMesh
    private lateinit var ballMeshLow: GlMesh
    private lateinit var ringMesh: GlMesh
    private lateinit var discMesh: GlMesh
    private lateinit var ledLongQuad: TexturedGlMesh
    private lateinit var ledEndQuad: TexturedGlMesh
    private lateinit var netBackQuad: TexturedGlMesh
    private lateinit var netSideQuad: TexturedGlMesh
    private lateinit var netTopQuad: TexturedGlMesh

    // Skinned 3D human meshes & 6 distinct 3D hairstyles
    private lateinit var playerMeshHigh: SkinnedGlMesh
    private lateinit var playerMeshLow: SkinnedGlMesh
    private lateinit var hairMeshes: Array<SkinnedGlMesh>

    // Dynamic textures
    private var ledTextureId = 0
    private var netTextureId = 0
    private var homeOutfieldAtlas = 0
    private var homeGkAtlas = 0
    private var awayOutfieldAtlas = 0
    private var awayGkAtlas = 0
    private var cachedHomeTeamId = ""
    private var cachedAwayTeamId = ""

    // Per-player skeletal animators (pre-allocated for 22 players -> zero per-frame allocations)
    private val animators = Array(22) { PlayerSkeletalAnimator() }

    // Pre-allocated matrices
    private val projMatrix = FloatArray(16)
    private val viewMatrix = FloatArray(16)
    private val vpMatrix = FloatArray(16)
    private val modelMatrix = FloatArray(16)
    private val mvpMatrix = FloatArray(16)
    private val identityMatrix = FloatArray(16).also { Matrix.setIdentityM(it, 0) }

    // Palettes for distinct realistic player appearances
    private val skinPalette = arrayOf(
        floatArrayOf(0.95f, 0.78f, 0.65f), // Fair European
        floatArrayOf(0.88f, 0.69f, 0.54f), // Mediterranean / Olive
        floatArrayOf(0.79f, 0.57f, 0.41f), // Warm Tan / Latin
        floatArrayOf(0.62f, 0.41f, 0.28f), // Medium Brown
        floatArrayOf(0.45f, 0.28f, 0.19f), // Deep Brown
        floatArrayOf(0.32f, 0.20f, 0.14f)  // Rich Ebony
    )
    private val hairPalette = arrayOf(
        floatArrayOf(0.09f, 0.08f, 0.08f), // Jet Black
        floatArrayOf(0.18f, 0.13f, 0.10f), // Dark Espresso
        floatArrayOf(0.34f, 0.22f, 0.13f), // Chestnut Brown
        floatArrayOf(0.55f, 0.32f, 0.16f), // Auburn
        floatArrayOf(0.76f, 0.62f, 0.34f), // Golden Blonde
        floatArrayOf(0.86f, 0.80f, 0.62f)  // Bleached Platinum
    )
    private val bootPalette = arrayOf(
        floatArrayOf(0.82f, 0.96f, 0.12f), // Neon Volt
        floatArrayOf(0.94f, 0.18f, 0.24f), // Crimson Strike
        floatArrayOf(0.14f, 0.76f, 0.98f), // Electric Cyan
        floatArrayOf(0.98f, 0.48f, 0.08f), // Solar Orange
        floatArrayOf(0.16f, 0.18f, 0.22f)  // Carbon Stealth
    )
    private val gkGloveColor = floatArrayOf(0.94f, 0.97f, 0.98f)

    private val cornerTowerCoords = arrayOf(
        floatArrayOf(-63f, -43f),
        floatArrayOf(63f, -43f),
        floatArrayOf(-63f, 43f),
        floatArrayOf(63f, 43f)
    )
    private val cornerFlagCoords = arrayOf(
        floatArrayOf(-PitchConstants.HALF_LENGTH, -PitchConstants.HALF_WIDTH),
        floatArrayOf(PitchConstants.HALF_LENGTH, -PitchConstants.HALF_WIDTH),
        floatArrayOf(-PitchConstants.HALF_LENGTH, PitchConstants.HALF_WIDTH),
        floatArrayOf(PitchConstants.HALF_LENGTH, PitchConstants.HALF_WIDTH)
    )

    // eFootball-style elevated diagonal broadcast camera state
    private var camInitialized = false
    private var camTargetX = 0f
    private var camTargetY = 1.0f
    private var camTargetZ = 0f
    private var camPosX = 0f
    private var camPosY = 13.0f
    private var camPosZ = 24.5f
    private var currentFov = 34f
    private var smoothedAttackZoom = 1.0f
    private var smoothedPlayDirX = 1f
    private var aspectRatio = 16f / 9f
    private var lastFrameNanos = 0L
    private var lastRenderNanos = 0L
    private var elapsedAnimTime = 0f

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)
        GLES30.glEnable(GLES30.GL_CULL_FACE)
        GLES30.glCullFace(GLES30.GL_BACK)

        // Link all 6 GLES 3.0 shader programs
        skinnedProgram = StadiumShaders.linkProgram(StadiumShaders.SKINNED_PLAYER_VERTEX, StadiumShaders.SKINNED_PLAYER_FRAGMENT)
        ballProgram = StadiumShaders.linkProgram(StadiumShaders.BALL_VERTEX, StadiumShaders.BALL_FRAGMENT)
        pitchProgram = StadiumShaders.linkProgram(StadiumShaders.PITCH_VERTEX, StadiumShaders.PITCH_FRAGMENT)
        crowdProgram = StadiumShaders.linkProgram(StadiumShaders.CROWD_VERTEX, StadiumShaders.CROWD_FRAGMENT)
        texturedProgram = StadiumShaders.linkProgram(StadiumShaders.TEXTURED_VERTEX, StadiumShaders.TEXTURED_FRAGMENT)
        entityProgram = StadiumShaders.linkProgram(StadiumShaders.ENTITY_VERTEX, StadiumShaders.ENTITY_FRAGMENT)
        pitchPbrProgram = SafeShaderCompiler.buildProgramOrZero(StadiumShaders.PITCH_VERTEX, RealisticPbrShaders.PITCH_PBR_FRAGMENT)
        skinnedPbrProgram = SafeShaderCompiler.buildProgramOrZero(StadiumShaders.SKINNED_PLAYER_VERTEX, RealisticPbrShaders.SKINNED_PLAYER_PBR_FRAGMENT)
        if (pitchPbrProgram == 0 || skinnedPbrProgram == 0) {
            fallbackController.reportShaderFailure("PBR shader compile/link failed")
        }

        resolveUniformLocations()

        // Build / load 3D geometry
        pitchMesh = MeshPrimitives.createPitchPlane(68f, 46f)
        stadiumStructureMesh = StadiumBuilder.buildStadiumStructureMesh()
        cubeMesh = MeshPrimitives.createUnitCube()
        cylinderMesh = MeshPrimitives.createUnitCylinder(14)
        ballMeshHigh = MeshPrimitives.createMatchBallIcosphere(subdivisions = 3) // 1,280 tris
        ballMeshLow = MeshPrimitives.createMatchBallIcosphere(subdivisions = 2)  // 320 tris
        ringMesh = MeshPrimitives.createRing(0.76f, 1.0f, 32)
        discMesh = MeshPrimitives.createDisc(24)

        ledLongQuad = MeshPrimitives.createTexturedQuad(112f, 1.15f, repeatU = 6f, repeatV = 1f)
        ledEndQuad = MeshPrimitives.createTexturedQuad(74f, 1.15f, repeatU = 4f, repeatV = 1f)
        netBackQuad = MeshPrimitives.createTexturedQuad(PitchConstants.GOAL_HALF_WIDTH * 2f, PitchConstants.GOAL_HEIGHT, repeatU = 6.5f, repeatV = 2.4f)
        netSideQuad = MeshPrimitives.createTexturedQuad(PitchConstants.GOAL_DEPTH, PitchConstants.GOAL_HEIGHT, repeatU = 2.2f, repeatV = 2.4f)
        netTopQuad = MeshPrimitives.createTexturedQuad(PitchConstants.GOAL_HALF_WIDTH * 2f, PitchConstants.GOAL_DEPTH, repeatU = 6.5f, repeatV = 2.2f)

        // Load optional external glTF 2.0 .glb player asset if present; otherwise build sculpted 19-bone human rig
        val importedPlayerGlb = GltfModelLoader.loadFromAssetsIfPresent(context, "models/player.glb")
        val highPlayerData = importedPlayerGlb ?: HumanoidMeshBuilder.buildFootballPlayerMesh(highDetail = true)
        val lowPlayerData = HumanoidMeshBuilder.buildFootballPlayerMesh(highDetail = false)
        playerMeshHigh = SkinnedGlMesh(highPlayerData)
        playerMeshLow = SkinnedGlMesh(lowPlayerData)
        hairMeshes = Array(HumanoidMeshBuilder.HAIRSTYLE_COUNT) { idx ->
            SkinnedGlMesh(HumanoidMeshBuilder.buildHairstyleMesh(idx))
        }

        // Static arena textures
        ledTextureId = ProceduralTextureFactory.createLedBoardTexture()
        netTextureId = ProceduralTextureFactory.createGoalNetTexture()

        cachedHomeTeamId = ""
        cachedAwayTeamId = ""
        camInitialized = false
        lastFrameNanos = System.nanoTime()
    }

    private fun resolveUniformLocations() {
        uSkVp = GLES30.glGetUniformLocation(skinnedProgram, "uVpMatrix")
        uSkModel = GLES30.glGetUniformLocation(skinnedProgram, "uModelMatrix")
        uSkBones = GLES30.glGetUniformLocation(skinnedProgram, "uBoneMatrices")
        uSkAtlas = GLES30.glGetUniformLocation(skinnedProgram, "uKitAtlas")
        uSkSkin = GLES30.glGetUniformLocation(skinnedProgram, "uSkinColor")
        uSkHair = GLES30.glGetUniformLocation(skinnedProgram, "uHairColor")
        uSkBoot = GLES30.glGetUniformLocation(skinnedProgram, "uBootColor")
        uSkGlove = GLES30.glGetUniformLocation(skinnedProgram, "uGloveColor")
        uSkIsGk = GLES30.glGetUniformLocation(skinnedProgram, "uIsGoalkeeper")
        uSkSunDir = GLES30.glGetUniformLocation(skinnedProgram, "uSunDir")
        uSkSunCol = GLES30.glGetUniformLocation(skinnedProgram, "uSunColor")
        uSkAmb = GLES30.glGetUniformLocation(skinnedProgram, "uAmbient")
        uSkFlood = GLES30.glGetUniformLocation(skinnedProgram, "uFloodlight")
        uSkCamPos = GLES30.glGetUniformLocation(skinnedProgram, "uCameraPos")
        uSkSky = GLES30.glGetUniformLocation(skinnedProgram, "uSkyColor")

        uBallMvp = GLES30.glGetUniformLocation(ballProgram, "uMvpMatrix")
        uBallModel = GLES30.glGetUniformLocation(ballProgram, "uModelMatrix")
        uBallSunDir = GLES30.glGetUniformLocation(ballProgram, "uSunDir")
        uBallSunCol = GLES30.glGetUniformLocation(ballProgram, "uSunColor")
        uBallAmb = GLES30.glGetUniformLocation(ballProgram, "uAmbient")
        uBallFlood = GLES30.glGetUniformLocation(ballProgram, "uFloodlight")
        uBallCamPos = GLES30.glGetUniformLocation(ballProgram, "uCameraPos")
        uBallSky = GLES30.glGetUniformLocation(ballProgram, "uSkyColor")

        uPitchMvp = GLES30.glGetUniformLocation(pitchProgram, "uMvpMatrix")
        uPitchModel = GLES30.glGetUniformLocation(pitchProgram, "uModelMatrix")
        uPitchGrass = GLES30.glGetUniformLocation(pitchProgram, "uGrassTint")
        uPitchSunCol = GLES30.glGetUniformLocation(pitchProgram, "uSunColor")
        uPitchAmb = GLES30.glGetUniformLocation(pitchProgram, "uAmbient")
        uPitchFlood = GLES30.glGetUniformLocation(pitchProgram, "uFloodlight")
        uPitchSky = GLES30.glGetUniformLocation(pitchProgram, "uSkyColor")
        uPitchCamPos = GLES30.glGetUniformLocation(pitchProgram, "uCameraPos")
        uPitchHighDetail = GLES30.glGetUniformLocation(pitchProgram, "uHighDetail")

        uCrowdVp = GLES30.glGetUniformLocation(crowdProgram, "uVpMatrix")
        uCrowdTime = GLES30.glGetUniformLocation(crowdProgram, "uTime")
        uCrowdExcite = GLES30.glGetUniformLocation(crowdProgram, "uExcitement")
        uCrowdSunDir = GLES30.glGetUniformLocation(crowdProgram, "uSunDir")
        uCrowdSunCol = GLES30.glGetUniformLocation(crowdProgram, "uSunColor")
        uCrowdAmb = GLES30.glGetUniformLocation(crowdProgram, "uAmbient")
        uCrowdCamPos = GLES30.glGetUniformLocation(crowdProgram, "uCameraPos")
        uCrowdSky = GLES30.glGetUniformLocation(crowdProgram, "uSkyColor")

        uTexMvp = GLES30.glGetUniformLocation(texturedProgram, "uMvpMatrix")
        uTexModel = GLES30.glGetUniformLocation(texturedProgram, "uModelMatrix")
        uTexSampler = GLES30.glGetUniformLocation(texturedProgram, "uTexture")
        uTexUvOff = GLES30.glGetUniformLocation(texturedProgram, "uUvOffset")
        uTexTint = GLES30.glGetUniformLocation(texturedProgram, "uTint")
        uTexEmissive = GLES30.glGetUniformLocation(texturedProgram, "uEmissive")
        uTexNetWave = GLES30.glGetUniformLocation(texturedProgram, "uNetWave")
        uTexTime = GLES30.glGetUniformLocation(texturedProgram, "uTime")

        uEntMvp = GLES30.glGetUniformLocation(entityProgram, "uMvpMatrix")
        uEntModel = GLES30.glGetUniformLocation(entityProgram, "uModelMatrix")
        uEntBaseColor = GLES30.glGetUniformLocation(entityProgram, "uBaseColor")
        uEntUseVertColor = GLES30.glGetUniformLocation(entityProgram, "uUseVertexColor")
        uEntEmissive = GLES30.glGetUniformLocation(entityProgram, "uEmissive")
        uEntSunDir = GLES30.glGetUniformLocation(entityProgram, "uSunDir")
        uEntSunCol = GLES30.glGetUniformLocation(entityProgram, "uSunColor")
        uEntAmb = GLES30.glGetUniformLocation(entityProgram, "uAmbient")
        uEntFlood = GLES30.glGetUniformLocation(entityProgram, "uFloodlight")
        uEntCamPos = GLES30.glGetUniformLocation(entityProgram, "uCameraPos")
        uEntSky = GLES30.glGetUniformLocation(entityProgram, "uSkyColor")
    }

    private fun ensureTeamAssetsLoaded(homeTeam: ClubTeam, awayTeam: ClubTeam) {
        if (homeTeam.id == cachedHomeTeamId && awayTeam.id == cachedAwayTeamId) return
        val toDelete = intArrayOf(homeOutfieldAtlas, homeGkAtlas, awayOutfieldAtlas, awayGkAtlas).filter { it != 0 }.toIntArray()
        if (toDelete.isNotEmpty()) GLES30.glDeleteTextures(toDelete.size, toDelete, 0)

        homeOutfieldAtlas = ProceduralTextureFactory.createClubKitAtlas(homeTeam, isGoalkeeper = false)
        homeGkAtlas = ProceduralTextureFactory.createClubKitAtlas(homeTeam, isGoalkeeper = true)
        awayOutfieldAtlas = ProceduralTextureFactory.createClubKitAtlas(awayTeam, isGoalkeeper = false)
        awayGkAtlas = ProceduralTextureFactory.createClubKitAtlas(awayTeam, isGoalkeeper = true)

        crowdMesh?.release()
        crowdMesh = StadiumBuilder.buildAnimatedCrowdMesh(homeTeam.kit.jerseyRgb(), awayTeam.kit.jerseyRgb())

        cachedHomeTeamId = homeTeam.id
        cachedAwayTeamId = awayTeam.id
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES30.glViewport(0, 0, width, height)
        aspectRatio = width.toFloat() / height.coerceAtLeast(1).toFloat()
    }

    override fun onDrawFrame(gl: GL10?) {
        val now = System.nanoTime()
        val dt = ((now - lastFrameNanos) / 1_000_000_000f).coerceIn(0.001f, 0.05f)
        lastFrameNanos = now
        elapsedAnimTime += dt

        onFrameTick(dt, lastRenderNanos)

        val renderStart = System.nanoTime()
        val snap = snapshotProvider()
        val stadium = snap.config.stadium
        val quality = snap.config.graphicsQuality
        activePipelineMode = fallbackController.effectiveMode(snap.config.graphicsPipelineMode)

        ensureTeamAssetsLoaded(snap.config.homeTeam, snap.config.awayTeam)

        GLES30.glClearColor(stadium.skyR, stadium.skyG, stadium.skyB, 1.0f)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)

        updateBroadcastCamera(snap, dt)
        Matrix.perspectiveM(projMatrix, 0, currentFov, aspectRatio, 0.4f, 290f)

        val shakeMag = snap.cameraShake * 0.36f
        val shakeX = if (shakeMag > 0.01f) sin(elapsedAnimTime * 54f) * shakeMag else 0f
        val shakeY = if (shakeMag > 0.01f) cos(elapsedAnimTime * 66f) * shakeMag * 0.6f else 0f

        Matrix.setLookAtM(
            viewMatrix, 0,
            camPosX + shakeX, camPosY + shakeY, camPosZ,
            camTargetX, camTargetY, camTargetZ,
            0f, 1f, 0f
        )
        Matrix.multiplyMM(vpMatrix, 0, projMatrix, 0, viewMatrix, 0)

        // 1. Detailed FIFA Grass Pitch
        GLES30.glDisable(GLES30.GL_CULL_FACE)
        drawPitch(stadium, quality)

        // 2. Stadium Architecture, Floodlights, Cylindrical Goal Frames, Corner Flags, Ground Shadows & Cursors
        prepareEntityShader(stadium)
        drawStadiumArchitecture(snap, stadium, quality)
        drawGoalFrame(-PitchConstants.HALF_LENGTH, -1f)
        drawGoalFrame(PitchConstants.HALF_LENGTH, 1f)
        drawCornerFlags()
        drawPlayerGroundShadowsAndCursors(snap, quality)

        // 3. GPU-Animated 3D Stadium Crowd (1 single draw call!)
        if (quality.renderStandDetails) {
            drawAnimatedCrowd(snap, stadium)
        }

        // 4. All 22 Anatomically Sculpted, 19-Bone GPU-Skinned 3D Football Players
        drawSkinnedPlayers(snap, stadium, quality, dt)

        // 5. High-Res 32-Panel Geodesic Match Football
        drawMatchBall(snap, stadium, quality)

        // 6. Scrolling Textured LED Advertising Boards & Transparent Woven Goal Nets (drawn after opaques)
        drawLedBoardsAndGoalNets(snap, quality)

        lastRenderNanos = System.nanoTime() - renderStart
    }

    /**
     * Professional eFootball-Style Elevated Diagonal Broadcast Camera:
     *  - Telephoto broadcast lens (34° default) keeps players large, crisp & recognizable while showing wide pitch context.
     *  - Sideline gantry parallax (gantryFactor ~0.62) naturally pivots diagonally toward both penalty boxes.
     *  - Smoothly zooms in during final-third attacks & goal opportunities, and sweeps in on goal celebrations.
     *  - Respects user-selectable Camera Zoom (0..10) and Camera Height (0..10) on both Host and Client.
     */
    private fun updateBroadcastCamera(snap: RenderWorldSnapshot, dt: Float) {
        val preset = snap.config.cameraPreset
        val isGoal = snap.phase == MatchPhase.GOAL_CELEBRATION

        // User Zoom (0=widest 1.24x .. 10=closest 0.80x) and Height (0=lower 0.72x .. 10=higher 1.28x)
        val userZoomMul = 1.24f - (snap.config.cameraZoom.coerceIn(0, 10) / 10f) * 0.44f
        val userHeightMul = 0.72f + (snap.config.cameraHeight.coerceIn(0, 10) / 10f) * 0.56f

        // Smooth attack-zone zoom when play enters the final third or penalty box
        val absBallX = abs(snap.ballX)
        val attackFactor = smoothstep(22f, 44f, absBallX)
        val targetAttackZoom = when {
            isGoal -> 0.58f
            else -> 1.0f - attackFactor * 0.16f
        }
        smoothedAttackZoom += (targetAttackZoom - smoothedAttackZoom) * (2.2f * dt).coerceAtMost(1f)

        // Subtle play-direction memory creates an authentic diagonal sideline angle even near midfield
        if (abs(snap.ballVx) > 1.2f) {
            val dir = if (snap.ballVx > 0f) 1f else -1f
            smoothedPlayDirX += (dir - smoothedPlayDirX) * (1.4f * dt).coerceAtMost(1f)
        }

        // Smooth velocity look-ahead + gentle goalmouth pull inside the attacking third
        val leadX = (snap.ballVx * 0.28f).coerceIn(-6.5f, 6.5f)
        val leadZ = (snap.ballVz * 0.16f).coerceIn(-3.2f, 3.2f)
        val goalPullX = if (absBallX > 26f) (if (snap.ballX > 0f) 2.8f else -2.8f) * attackFactor else 0f

        val desiredTargetX = ((snap.ballX + leadX + goalPullX) * 0.90f).coerceIn(-46f, 46f)
        val desiredTargetZ = ((snap.ballZ + leadZ) * 0.72f).coerceIn(-22f, 22f)
        val desiredTargetY = (0.95f + snap.ballY * 0.22f).coerceIn(0.9f, 2.8f)

        // Elevated diagonal sideline gantry position: lags behind X so the camera rotates diagonally into both ends!
        val diagonalOffset = -smoothedPlayDirX * 3.6f * (1f - attackFactor * 0.6f)
        val goalOrbit = if (isGoal) sin(elapsedAnimTime * 1.15f) * 5.2f else 0f

        val desiredPosX = (desiredTargetX * preset.gantryFactor + diagonalOffset + goalOrbit).coerceIn(-36f, 36f)
        val desiredPosY = (preset.height * userHeightMul * smoothedAttackZoom).coerceIn(5.8f, 28f)
        val desiredPosZ = (desiredTargetZ + preset.distance * userZoomMul * smoothedAttackZoom).coerceIn(12f, 52f)

        // Telephoto FOV subtly tightens in goalmouth opportunities and breathes gently on fast counters
        val ballSpeed = abs(snap.ballVx) + abs(snap.ballVz)
        val desiredFov = (preset.fov - attackFactor * 2.4f + (ballSpeed * 0.08f).coerceAtMost(2.2f)) *
            (if (isGoal) 0.90f else 1.0f)

        if (!camInitialized) {
            camTargetX = desiredTargetX; camTargetY = desiredTargetY; camTargetZ = desiredTargetZ
            camPosX = desiredPosX; camPosY = desiredPosY; camPosZ = desiredPosZ
            currentFov = desiredFov
            camInitialized = true
            return
        }

        // Smooth, cinematic damping with zero sudden snaps
        val targetAlpha = (4.4f * dt).coerceAtMost(1f)
        val posAlpha = (3.4f * dt).coerceAtMost(1f)
        val fovAlpha = (2.4f * dt).coerceAtMost(1f)

        camTargetX += (desiredTargetX - camTargetX) * targetAlpha
        camTargetY += (desiredTargetY - camTargetY) * targetAlpha
        camTargetZ += (desiredTargetZ - camTargetZ) * targetAlpha
        camPosX += (desiredPosX - camPosX) * posAlpha
        camPosY += (desiredPosY - camPosY) * posAlpha
        camPosZ += (desiredPosZ - camPosZ) * posAlpha
        currentFov += (desiredFov - currentFov) * fovAlpha
    }

    private fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
        val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    private fun drawPitch(stadium: StadiumPreset, quality: GraphicsQuality) {
val usePbr = activePipelineMode == GraphicsPipelineMode.REALISTIC_PBR && pitchPbrProgram != 0
        val prog = if (usePbr) pitchPbrProgram else pitchProgram
        fun u(name: String, classic: Int) = if (usePbr) GLES30.glGetUniformLocation(prog, name) else classic
        GLES30.glUseProgram(prog)
        Matrix.multiplyMM(mvpMatrix, 0, vpMatrix, 0, identityMatrix, 0)
        GLES30.glUniformMatrix4fv(u("uMvpMatrix", uPitchMvp), 1, false, mvpMatrix, 0)
        GLES30.glUniformMatrix4fv(u("uModelMatrix", uPitchModel), 1, false, identityMatrix, 0)
        GLES30.glUniform3f(u("uGrassTint", uPitchGrass), stadium.grassTintR, stadium.grassTintG, stadium.grassTintB)
        GLES30.glUniform3f(u("uSunColor", uPitchSunCol), stadium.sunR, stadium.sunG, stadium.sunB)
        GLES30.glUniform1f(u("uAmbient", uPitchAmb), stadium.ambientStrength)
        GLES30.glUniform1f(u("uFloodlight", uPitchFlood), stadium.floodlightIntensity)
        GLES30.glUniform3f(u("uSkyColor", uPitchSky), stadium.skyR, stadium.skyG, stadium.skyB)
        GLES30.glUniform3f(u("uCameraPos", uPitchCamPos), camPosX, camPosY, camPosZ)
        GLES30.glUniform1f(uPitchHighDetail, if (quality != GraphicsQuality.LOW) 1f else 0f)
        pitchMesh.draw()
    }

    private fun prepareEntityShader(stadium: StadiumPreset) {
        GLES30.glUseProgram(entityProgram)
        GLES30.glUniform3f(uEntSunDir, stadium.sunX, stadium.sunY, stadium.sunZ)
        GLES30.glUniform3f(uEntSunCol, stadium.sunR, stadium.sunG, stadium.sunB)
        GLES30.glUniform1f(uEntAmb, stadium.ambientStrength)
        GLES30.glUniform1f(uEntFlood, stadium.floodlightIntensity)
        GLES30.glUniform3f(uEntCamPos, camPosX, camPosY, camPosZ)
        GLES30.glUniform3f(uEntSky, stadium.skyR, stadium.skyG, stadium.skyB)
    }

    private fun drawStadiumArchitecture(snap: RenderWorldSnapshot, stadium: StadiumPreset, quality: GraphicsQuality) {
        // Entire multi-tier bowl, VIP boxes, steel canopy roofs & team dugouts in 1 draw call
        Matrix.setIdentityM(modelMatrix, 0)
        submitEntityDraw(stadiumStructureMesh, 1f, 1f, 1f, 1f, useVertColor = true, emissive = false)

        // 4 Towering Corner Floodlight Pylons & Glowing Lamp Arrays
        val lampBright = 0.62f + stadium.floodlightIntensity * 0.38f
        for (c in cornerTowerCoords) {
            drawCylinder(c[0], 15.0f, c[1], 0.62f, 30f, 0f, 0f, 0.24f, 0.28f, 0.34f, 1f)
            drawBox(c[0], 30.0f, c[1], 6.4f, 2.9f, 1.4f, if (c[0] < 0) -35f else 35f, lampBright, lampBright, 1.0f, 1f, emissive = true)

            if (quality == GraphicsQuality.HIGH && stadium.floodlightIntensity > 0.45f) {
                val beamX = c[0] * 0.78f
                val beamZ = c[1] * 0.78f
                drawBox(
                    beamX, 16.0f, beamZ,
                    4.6f, 28f, 4.6f,
                    if (c[0] < 0) -35f else 35f,
                    0.86f, 0.94f, 1.0f,
                    0.045f * stadium.floodlightIntensity,
                    emissive = true
                )
            }
        }
    }

    private fun drawAnimatedCrowd(snap: RenderWorldSnapshot, stadium: StadiumPreset) {
        val mesh = crowdMesh ?: return
        val excitement = when {
            snap.phase == MatchPhase.GOAL_CELEBRATION -> 1.0f
            abs(snap.ballX) > 36f -> 0.52f
            else -> 0.14f
        }
        GLES30.glUseProgram(crowdProgram)
        GLES30.glUniformMatrix4fv(uCrowdVp, 1, false, vpMatrix, 0)
        GLES30.glUniform1f(uCrowdTime, elapsedAnimTime)
        GLES30.glUniform1f(uCrowdExcite, excitement)
        GLES30.glUniform3f(uCrowdSunDir, stadium.sunX, stadium.sunY, stadium.sunZ)
        GLES30.glUniform3f(uCrowdSunCol, stadium.sunR, stadium.sunG, stadium.sunB)
        GLES30.glUniform1f(uCrowdAmb, stadium.ambientStrength)
        GLES30.glUniform3f(uCrowdCamPos, camPosX, camPosY, camPosZ)
        GLES30.glUniform3f(uCrowdSky, stadium.skyR, stadium.skyG, stadium.skyB)
        mesh.draw()
    }

    private fun drawGoalFrame(goalLineX: Float, outSign: Float) {
        val hw = PitchConstants.GOAL_HALF_WIDTH
        val gh = PitchConstants.GOAL_HEIGHT
        val gd = PitchConstants.GOAL_DEPTH
        val postR = 0.068f

        // Cylindrical metallic posts & crossbar
        drawCylinder(goalLineX, gh * 0.5f, -hw, postR, gh, 0f, 0f, 0.96f, 0.97f, 0.99f, 1f)
        drawCylinder(goalLineX, gh * 0.5f, hw, postR, gh, 0f, 0f, 0.96f, 0.97f, 0.99f, 1f)
        drawCylinder(goalLineX, gh, 0f, postR, hw * 2f + postR * 2f, 90f, 0f, 0.96f, 0.97f, 0.99f, 1f)

        // Back support stanchions & ground base rail
        val backX = goalLineX + outSign * gd
        drawCylinder(backX, gh * 0.5f, -hw, 0.042f, gh, 0f, 0f, 0.76f, 0.80f, 0.85f, 1f)
        drawCylinder(backX, gh * 0.5f, hw, 0.042f, gh, 0f, 0f, 0.76f, 0.80f, 0.85f, 1f)
        drawCylinder(goalLineX + outSign * gd * 0.5f, gh, -hw, 0.04f, gd, 0f, 90f, 0.82f, 0.85f, 0.88f, 1f)
        drawCylinder(goalLineX + outSign * gd * 0.5f, gh, hw, 0.04f, gd, 0f, 90f, 0.82f, 0.85f, 0.88f, 1f)
        drawCylinder(backX, 0.05f, 0f, 0.04f, hw * 2f, 90f, 0f, 0.72f, 0.76f, 0.82f, 1f)
    }

    private fun drawCornerFlags() {
        val wave = sin(elapsedAnimTime * 5.2f) * 15f
        for (c in cornerFlagCoords) {
            drawCylinder(c[0], 0.78f, c[1], 0.032f, 1.56f, 0f, 0f, 0.96f, 0.96f, 0.98f, 1f)
            drawBox(c[0] + 0.22f, 1.40f, c[1], 0.44f, 0.28f, 0.03f, wave, 0.98f, 0.72f, 0.06f, 1f, emissive = true)
        }
    }

    private fun drawPlayerGroundShadowsAndCursors(snap: RenderWorldSnapshot, quality: GraphicsQuality) {
        val sunOffsetX = -snap.config.stadium.sunX * 0.34f
        val sunOffsetZ = -snap.config.stadium.sunZ * 0.34f
        val renderShadows = quality.renderShadows

        for (p in snap.players) {
            val isLocalControlled = (snap.localControlledSide == TeamSide.HOME && p.id == snap.activeHomePlayerId) ||
                (snap.localControlledSide == TeamSide.AWAY && p.id == snap.activeAwayPlayerId)
            val isRemoteControlled = snap.config.isLanMultiplayer && (
                (snap.localControlledSide == TeamSide.HOME && p.id == snap.activeAwayPlayerId) ||
                    (snap.localControlledSide == TeamSide.AWAY && p.id == snap.activeHomePlayerId)
                )

            if (isLocalControlled) {
                drawGroundRing(p.x, p.z, 0.92f, 0.05f, 0.94f, 0.52f, 0.92f)
                val ax = p.x + cos(p.facingYaw) * 1.24f
                val az = p.z + sin(p.facingYaw) * 1.24f
                drawBox(ax, 0.04f, az, 0.28f, 0.03f, 0.20f, -p.facingYaw * (180f / PI.toFloat()), 0.98f, 0.76f, 0.05f, 0.94f, emissive = true)
                if (snap.shootCharge > 0.04f) {
                    val barW = 1.45f * snap.shootCharge.coerceIn(0f, 1f)
                    val isHot = snap.shootCharge > 0.72f
                    drawBox(p.x, 0.06f, p.z + 1.10f, 1.52f, 0.03f, 0.20f, 0f, 0.05f, 0.08f, 0.12f, 0.85f, emissive = true)
                    drawBox(
                        p.x - (1.45f - barW) * 0.5f, 0.07f, p.z + 1.10f,
                        barW, 0.04f, 0.15f, 0f,
                        if (isHot) 0.96f else 0.08f, if (isHot) 0.24f else 0.92f, if (isHot) 0.22f else 0.48f, 0.96f,
                        emissive = true
                    )
                }
            } else if (isRemoteControlled) {
                drawGroundRing(p.x, p.z, 0.88f, 0.16f, 0.80f, 0.92f, 0.86f)
            }

            if (renderShadows) {
                // Soft contact ambient-occlusion shadow directly beneath feet
                drawShadowEllipse(p.x, p.z, 0.44f, 0.36f, 0f, 0.34f)
                // Directional stadium-projected shadow cast onto the turf on Medium/High
                val shadowRot = -p.facingYaw * (180f / PI.toFloat())
                drawShadowEllipse(p.x + sunOffsetX, p.z + sunOffsetZ, 0.78f, 0.34f, shadowRot, 0.22f)
            }
        }

        // Height-responsive football ground shadow
        if (renderShadows) {
            val sScale = (0.28f + snap.ballY * 0.05f).coerceAtMost(0.70f)
            val sAlpha = (0.46f - snap.ballY * 0.045f).coerceIn(0.10f, 0.46f)
            drawShadowEllipse(snap.ballX + sunOffsetX * 0.35f, snap.ballZ + sunOffsetZ * 0.35f, sScale, sScale, 0f, sAlpha)
        }
    }

    private fun drawSkinnedPlayers(
        snap: RenderWorldSnapshot,
        stadium: StadiumPreset,
        quality: GraphicsQuality,
        dt: Float
    ) {
val usePbr = activePipelineMode == GraphicsPipelineMode.REALISTIC_PBR && skinnedPbrProgram != 0
        val prog = if (usePbr) skinnedPbrProgram else skinnedProgram
        fun u(name: String, classic: Int) = if (usePbr) GLES30.glGetUniformLocation(prog, name) else classic
        GLES30.glUseProgram(prog)
        GLES30.glUniformMatrix4fv(u("uVpMatrix", uSkVp), 1, false, vpMatrix, 0)
        GLES30.glUniform3f(u("uSunDir", uSkSunDir), stadium.sunX, stadium.sunY, stadium.sunZ)
        GLES30.glUniform3f(u("uSunColor", uSkSunCol), stadium.sunR, stadium.sunG, stadium.sunB)
        GLES30.glUniform1f(u("uAmbient", uSkAmb), stadium.ambientStrength)
        GLES30.glUniform1f(u("uFloodlight", uSkFlood), stadium.floodlightIntensity)
        GLES30.glUniform3f(u("uCameraPos", uSkCamPos), camPosX, camPosY, camPosZ)
        GLES30.glUniform3f(u("uSkyColor", uSkSky), stadium.skyR, stadium.skyG, stadium.skyB)
        GLES30.glUniform3f(u("uGloveColor", uSkGlove), gkGloveColor[0], gkGloveColor[1], gkGloveColor[2])
        GLES30.glUniform1i(u("uKitAtlas", uSkAtlas), 0)

        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        val bodyMesh = if (quality == GraphicsQuality.LOW) playerMeshLow else playerMeshHigh
        val draw3dHair = quality != GraphicsQuality.LOW

        for (p in snap.players) {
            val animator = animators[p.id.coerceIn(0, animators.lastIndex)]
            val isDribbling = (p.id == snap.ballOwnerId && p.speed > 0.45f)

            animator.updateAndBuildSkinMatrices(
                state = p.animState,
                animTime = p.animTime,
                speed = p.speed,
                playerY = p.y,
                isDribbling = isDribbling,
                diveDir = p.diveDir,
                dt = dt
            )

            val atlasTex = when {
                p.side == TeamSide.HOME && p.role == PlayerRole.GK -> homeGkAtlas
                p.side == TeamSide.HOME -> homeOutfieldAtlas
                p.role == PlayerRole.GK -> awayGkAtlas
                else -> awayOutfieldAtlas
            }
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, atlasTex)

            // Individualized athletic proportions & appearance per player
            val heightScale = when (p.role) {
                PlayerRole.GK -> 1.05f
                PlayerRole.DF -> 1.02f + (p.shirtNumber % 3) * 0.012f
                PlayerRole.MF -> 0.98f + (p.shirtNumber % 3) * 0.015f
                PlayerRole.FW -> 0.99f + (p.shirtNumber % 3) * 0.015f
            }
            val widthScale = when (p.role) {
                PlayerRole.GK, PlayerRole.DF -> 1.03f
                else -> 0.98f + (p.id % 3) * 0.02f
            }

            val yawDeg = (-p.facingYaw * (180f / PI.toFloat())) + 90f
            Matrix.setIdentityM(modelMatrix, 0)
            Matrix.translateM(modelMatrix, 0, p.x, p.y, p.z)
            Matrix.rotateM(modelMatrix, 0, yawDeg, 0f, 1f, 0f)
            Matrix.scaleM(modelMatrix, 0, widthScale, heightScale, widthScale)

            GLES30.glUniformMatrix4fv(u("uModelMatrix", uSkModel), 1, false, modelMatrix, 0)
            GLES30.glUniformMatrix4fv(u("uBoneMatrices", uSkBones), SkeletonRig.BONE_COUNT, false, animator.skinMatrices, 0)

            val skin = skinPalette[(p.skinToneIndex + p.id) % skinPalette.size]
            val hair = hairPalette[(p.hairToneIndex + p.shirtNumber) % hairPalette.size]
            val boot = bootPalette[(p.id * 3 + p.shirtNumber) % bootPalette.size]

                     GLES30.glUniform3f(u("uSkinColor", uSkSkin), skin[0], skin[1], skin[2])
            GLES30.glUniform3f(u("uHairColor", uSkHair), hair[0], hair[1], hair[2])
            GLES30.glUniform3f(u("uBootColor", uSkBoot), boot[0], boot[1], boot[2])
            GLES30.glUniform1f(u("uIsGoalkeeper", uSkIsGk), if (p.role == PlayerRole.GK) 1f else 0f)

            // Draw sculpted skinned body
            bodyMesh.draw()

            // Draw individualized 3D hairstyle mesh rigged to HEAD bone
            if (draw3dHair) {
                val hairStyleIdx = (p.id * 5 + p.shirtNumber * 2) % hairMeshes.size
                hairMeshes[hairStyleIdx].draw()
            }
        }
    }

    private fun drawMatchBall(snap: RenderWorldSnapshot, stadium: StadiumPreset, quality: GraphicsQuality) {
        GLES30.glUseProgram(ballProgram)
        GLES30.glUniform3f(uBallSunDir, stadium.sunX, stadium.sunY, stadium.sunZ)
        GLES30.glUniform3f(uBallSunCol, stadium.sunR, stadium.sunG, stadium.sunB)
        GLES30.glUniform1f(uBallAmb, stadium.ambientStrength)
        GLES30.glUniform1f(uBallFlood, stadium.floodlightIntensity)
        GLES30.glUniform3f(uBallCamPos, camPosX, camPosY, camPosZ)
        GLES30.glUniform3f(uBallSky, stadium.skyR, stadium.skyG, stadium.skyB)

        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.translateM(modelMatrix, 0, snap.ballX, snap.ballY, snap.ballZ)
        Matrix.rotateM(modelMatrix, 0, snap.ballSpinX * 57.2958f, 1f, 0f, 0f)
        Matrix.rotateM(modelMatrix, 0, snap.ballSpinZ * 57.2958f, 0f, 0f, 1f)
        Matrix.rotateM(modelMatrix, 0, snap.ballSpinY * 22.0f, 0f, 1f, 0f)
        val r = PitchConstants.BALL_RADIUS * 1.08f // Slightly boosted visibility at broadcast distance while preserving foot contact
        Matrix.scaleM(modelMatrix, 0, r, r, r)
        Matrix.multiplyMM(mvpMatrix, 0, vpMatrix, 0, modelMatrix, 0)

        GLES30.glUniformMatrix4fv(uBallMvp, 1, false, mvpMatrix, 0)
        GLES30.glUniformMatrix4fv(uBallModel, 1, false, modelMatrix, 0)

        if (quality == GraphicsQuality.LOW) ballMeshLow.draw() else ballMeshHigh.draw()
    }

    private fun drawLedBoardsAndGoalNets(snap: RenderWorldSnapshot, quality: GraphicsQuality) {
        GLES30.glUseProgram(texturedProgram)
        GLES30.glUniform1i(uTexSampler, 0)
        GLES30.glUniform1f(uTexTime, elapsedAnimTime)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)

        val isGoal = snap.phase == MatchPhase.GOAL_CELEBRATION
        val scrollU = (elapsedAnimTime * (if (isGoal) 0.45f else 0.08f)) % 1f

        // 1. Scrolling Perimeter LED Advertising Boards
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, ledTextureId)
        GLES30.glUniform2f(uTexUvOff, scrollU, 0f)
        GLES30.glUniform1f(uTexEmissive, 1f)
        GLES30.glUniform1f(uTexNetWave, 0f)
        if (isGoal) {
            val flash = 0.72f + 0.28f * sin(elapsedAnimTime * 14f)
            GLES30.glUniform4f(uTexTint, 1.0f, 0.88f * flash, 0.35f, 1f)
        } else {
            GLES30.glUniform4f(uTexTint, 0.96f, 0.98f, 1.0f, 1f)
        }

        // North main sideline LED board
        drawTexturedPlane(ledLongQuad, 0f, 0.60f, -38.6f, 0f, 0f)
        // West & East goal-end LED boards
        drawTexturedPlane(ledEndQuad, -58.6f, 0.60f, 0f, 0f, 90f)
        drawTexturedPlane(ledEndQuad, 58.6f, 0.60f, 0f, 0f, -90f)

        // 2. Woven Transparent Diamond-Mesh Goal Nets (both ends)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, netTextureId)
        GLES30.glUniform2f(uTexUvOff, 0f, 0f)
        GLES30.glUniform1f(uTexEmissive, 0f)
        GLES30.glUniform1f(uTexNetWave, if (isGoal) 0.14f else 0.015f)
        GLES30.glUniform4f(uTexTint, 0.96f, 0.98f, 1.0f, if (quality == GraphicsQuality.LOW) 0.65f else 0.86f)

        GLES30.glDepthMask(false)
        drawGoalNetPanels(-PitchConstants.HALF_LENGTH, -1f)
        drawGoalNetPanels(PitchConstants.HALF_LENGTH, 1f)
        GLES30.glDepthMask(true)
    }

    private fun drawGoalNetPanels(goalLineX: Float, outSign: Float) {
        val hw = PitchConstants.GOAL_HALF_WIDTH
        val gh = PitchConstants.GOAL_HEIGHT
        val gd = PitchConstants.GOAL_DEPTH
        val backX = goalLineX + outSign * gd
        val midX = goalLineX + outSign * gd * 0.5f

        // Back net
        drawTexturedPlane(netBackQuad, backX, gh * 0.5f, 0f, 0f, if (outSign > 0) 90f else -90f)
        // Left & Right side nets
        drawTexturedPlane(netSideQuad, midX, gh * 0.5f, -hw, 0f, 0f)
        drawTexturedPlane(netSideQuad, midX, gh * 0.5f, hw, 0f, 0f)
        // Top roof net
        drawTexturedPlane(netTopQuad, midX, gh, 0f, 90f, 90f)
    }

    private fun drawTexturedPlane(mesh: TexturedGlMesh, x: Float, y: Float, z: Float, rotXDeg: Float, rotYDeg: Float) {
        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.translateM(modelMatrix, 0, x, y, z)
        if (rotYDeg != 0f) Matrix.rotateM(modelMatrix, 0, rotYDeg, 0f, 1f, 0f)
        if (rotXDeg != 0f) Matrix.rotateM(modelMatrix, 0, rotXDeg, 1f, 0f, 0f)
        Matrix.multiplyMM(mvpMatrix, 0, vpMatrix, 0, modelMatrix, 0)
        GLES30.glUniformMatrix4fv(uTexMvp, 1, false, mvpMatrix, 0)
        GLES30.glUniformMatrix4fv(uTexModel, 1, false, modelMatrix, 0)
        mesh.draw()
    }

    private fun drawGroundRing(x: Float, z: Float, radius: Float, r: Float, g: Float, b: Float, a: Float) {
        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.translateM(modelMatrix, 0, x, 0.03f, z)
        Matrix.scaleM(modelMatrix, 0, radius, 1f, radius)
        submitEntityDraw(ringMesh, r, g, b, a, useVertColor = false, emissive = true)
    }

    private fun drawShadowEllipse(x: Float, z: Float, rx: Float, rz: Float, rotYDeg: Float, alpha: Float) {
        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.translateM(modelMatrix, 0, x, 0.022f, z)
        if (rotYDeg != 0f) Matrix.rotateM(modelMatrix, 0, rotYDeg, 0f, 1f, 0f)
        Matrix.scaleM(modelMatrix, 0, rx, 1f, rz)
        submitEntityDraw(discMesh, 0.02f, 0.04f, 0.03f, alpha, useVertColor = false, emissive = true)
    }

    private fun drawBox(
        x: Float, y: Float, z: Float,
        sx: Float, sy: Float, sz: Float,
        rotYDeg: Float,
        r: Float, g: Float, b: Float, a: Float,
        emissive: Boolean = false
    ) {
        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.translateM(modelMatrix, 0, x, y, z)
        if (rotYDeg != 0f) Matrix.rotateM(modelMatrix, 0, rotYDeg, 0f, 1f, 0f)
        Matrix.scaleM(modelMatrix, 0, sx, sy, sz)
        submitEntityDraw(cubeMesh, r, g, b, a, useVertColor = false, emissive = emissive)
    }

    private fun drawCylinder(
        x: Float, y: Float, z: Float,
        radius: Float, height: Float,
        rotXDeg: Float, rotZDeg: Float,
        r: Float, g: Float, b: Float, a: Float
    ) {
        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.translateM(modelMatrix, 0, x, y, z)
        if (rotXDeg != 0f) Matrix.rotateM(modelMatrix, 0, rotXDeg, 1f, 0f, 0f)
        if (rotZDeg != 0f) Matrix.rotateM(modelMatrix, 0, rotZDeg, 0f, 0f, 1f)
        Matrix.scaleM(modelMatrix, 0, radius, height, radius)
        submitEntityDraw(cylinderMesh, r, g, b, a, useVertColor = false, emissive = false)
    }

    private fun submitEntityDraw(
        mesh: GlMesh,
        r: Float, g: Float, b: Float, a: Float,
        useVertColor: Boolean,
        emissive: Boolean
    ) {
        Matrix.multiplyMM(mvpMatrix, 0, vpMatrix, 0, modelMatrix, 0)
        GLES30.glUniformMatrix4fv(uEntMvp, 1, false, mvpMatrix, 0)
        GLES30.glUniformMatrix4fv(uEntModel, 1, false, modelMatrix, 0)
        GLES30.glUniform4f(uEntBaseColor, r, g, b, a)
        GLES30.glUniform1f(uEntUseVertColor, if (useVertColor) 1f else 0f)
        GLES30.glUniform1f(uEntEmissive, if (emissive) 1f else 0f)
        mesh.draw()
    }
}
