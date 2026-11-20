package com.example.game.engine

import com.example.game.model.AnimState
import com.example.game.model.BallState
import com.example.game.model.MatchConfig
import com.example.game.model.MatchHudSnapshot
import com.example.game.model.MatchPhase
import com.example.game.model.MatchStats
import com.example.game.model.PlayerEntity
import com.example.game.model.PlayerInput
import com.example.game.model.PlayerRole
import com.example.game.model.TeamSide
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign
import kotlin.math.sin

data class RenderWorldSnapshot(
    val players: List<RenderPlayer>,
    val ballX: Float,
    val ballY: Float,
    val ballZ: Float,
    val ballVx: Float,
    val ballVz: Float,
    val ballSpinX: Float,
    val ballSpinY: Float,
    val ballSpinZ: Float,
    val ballOwnerId: Int,
    val activeHomePlayerId: Int,
    val activeAwayPlayerId: Int,
    val localControlledSide: TeamSide,
    val phase: MatchPhase,
    val shootCharge: Float,
    val cameraShake: Float,
    val matchClockSec: Float,
    val config: MatchConfig
)

data class RenderPlayer(
    val id: Int,
    val side: TeamSide,
    val role: PlayerRole,
    val shirtNumber: Int,
    val skinToneIndex: Int,
    val hairToneIndex: Int,
    val x: Float,
    val y: Float,
    val z: Float,
    val speed: Float,
    val facingYaw: Float,
    val stamina: Float,
    val animState: AnimState,
    val animTime: Float,
    val diveDir: Float
)

data class NetworkWorldState(
    val homeGoals: Int,
    val awayGoals: Int,
    val elapsedSec: Float,
    val half: Int,
    val phase: MatchPhase,
    val activeHomeId: Int,
    val activeAwayId: Int,
    val ballX: Float,
    val ballY: Float,
    val ballZ: Float,
    val ballVx: Float,
    val ballVy: Float,
    val ballVz: Float,
    val ballOwner: Int,
    val stats: MatchStats,
    val players: List<NetPlayerState>
)

class FootballSimulation(
    initialConfig: MatchConfig = MatchConfig(),
    private val onSoundEvent: ((com.example.game.model.SoundEvent) -> Unit)? = null
) {

    private val lock = Any()
    private val ai = FootballAiController()

    var config: MatchConfig = initialConfig
        private set

    val ball = BallState()
    val players = ArrayList<PlayerEntity>(22)

    var homeScore: Int = 0
        private set
    var awayScore: Int = 0
        private set
    var half: Int = 1
        private set
    var matchElapsedRealSeconds: Float = 0f
        private set
    var phase: MatchPhase = MatchPhase.KICKOFF
        private set
    var phaseTimer: Float = 1.6f
        private set
    var bannerSubtitle: String = ""
        private set
    var isPaused: Boolean = false
    var cameraShake: Float = 0f
        private set

    var activeHomePlayerId: Int = 9
        private set
    var activeAwayPlayerId: Int = 20
        private set

    private var stats = MatchStats()
    private var restartSide: TeamSide = TeamSide.HOME
    private var restartX: Float = 0f
    private var restartZ: Float = 0f
    private var lastPasserId: Int = -1
    private var lastPasserSide: TeamSide = TeamSide.HOME
    private var offsideCheckArmed: Boolean = false
    private var offsideLineAtPass: Float = 0f
    private var offsideCandidateIds: Set<Int> = emptySet()

    // Live inputs (Home = Host/Solo Player 1, Away = Client Player 2 when LAN multiplayer)
    private var homeInput = PlayerInput()
    private var awayInput = PlayerInput()
    private var prevHomeSwitch = false
    private var prevAwaySwitch = false

    val currentStats: MatchStats get() = synchronized(lock) { stats }

    init {
        resetMatch(initialConfig)
    }

    fun resetMatch(newConfig: MatchConfig) = synchronized(lock) {
        config = newConfig
        homeScore = 0
        awayScore = 0
        half = 1
        matchElapsedRealSeconds = 0f
        stats = MatchStats()
        isPaused = false
        homeInput = PlayerInput()
        awayInput = PlayerInput()
        prevHomeSwitch = false
        prevAwaySwitch = false

        buildSquads()
        setupKickoff(TeamSide.HOME, "FIRST HALF")
    }

    fun updateVisualConfig(
        graphicsQuality: com.example.game.model.GraphicsQuality = config.graphicsQuality,
        cameraPreset: com.example.game.model.CameraPreset = config.cameraPreset,
        cameraZoom: Int = config.cameraZoom,
        cameraHeight: Int = config.cameraHeight
    ) = synchronized(lock) {
        config = config.copy(
            graphicsQuality = graphicsQuality,
            cameraPreset = cameraPreset,
            cameraZoom = cameraZoom.coerceIn(0, 10),
            cameraHeight = cameraHeight.coerceIn(0, 10)
        )
    }

    private fun buildSquads() {
        players.clear()
        val homeSlots = config.homeFormation.homeSlots()
        val awaySlots = config.awayFormation.homeSlots()
        val homeTeam = config.homeTeam
        val awayTeam = config.awayTeam

        for (i in 0 until 11) {
            val slot = homeSlots[i]
            val roleSpeed = when (slot.role) {
                PlayerRole.GK -> 6.4f
                PlayerRole.DF -> 7.4f
                PlayerRole.MF -> 7.8f
                PlayerRole.FW -> 8.3f
            }
            val ratingBonus = ((homeTeam.overallRating - 80) * 0.04f)
            players.add(
                PlayerEntity(
                    id = i,
                    side = TeamSide.HOME,
                    slotIndex = i,
                    shirtNumber = slot.shirtNumber,
                    name = homeTeam.playerNames.getOrElse(i) { "Player ${slot.shirtNumber}" },
                    role = slot.role,
                    skinToneIndex = (i * 3 + 1) % 4,
                    hairToneIndex = (i * 5 + 2) % 4,
                    baseSpeed = roleSpeed + ratingBonus
                )
            )
        }

        for (i in 0 until 11) {
            val slot = awaySlots[i]
            val roleSpeed = when (slot.role) {
                PlayerRole.GK -> 6.4f
                PlayerRole.DF -> 7.4f
                PlayerRole.MF -> 7.8f
                PlayerRole.FW -> 8.3f
            }
            val ratingBonus = ((awayTeam.overallRating - 80) * 0.04f)
            players.add(
                PlayerEntity(
                    id = 11 + i,
                    side = TeamSide.AWAY,
                    slotIndex = i,
                    shirtNumber = slot.shirtNumber,
                    name = awayTeam.playerNames.getOrElse(i) { "Player ${slot.shirtNumber}" },
                    role = slot.role,
                    skinToneIndex = (i * 2 + 3) % 4,
                    hairToneIndex = (i * 3 + 1) % 4,
                    baseSpeed = roleSpeed + ratingBonus
                )
            )
        }

        activeHomePlayerId = 9
        activeAwayPlayerId = 20
    }

    fun attackDirection(side: TeamSide): Float {
        // Home attacks +X in 1st half, -X in 2nd half
        val base = if (side == TeamSide.HOME) 1f else -1f
        return if (half == 1) base else -base
    }

    private fun setupKickoff(kickingSide: TeamSide, subtitle: String) {
        phase = MatchPhase.KICKOFF
        phaseTimer = 1.4f
        restartSide = kickingSide
        bannerSubtitle = subtitle
        offsideCheckArmed = false

        ball.x = 0f
        ball.y = PitchConstants.BALL_RADIUS
        ball.z = 0f
        ball.vx = 0f
        ball.vy = 0f
        ball.vz = 0f
        ball.spinX = 0f
        ball.spinY = 0f
        ball.spinZ = 0f
        ball.ownerId = -1

        val homeDir = attackDirection(TeamSide.HOME)
        val awayDir = attackDirection(TeamSide.AWAY)
        val homeSlots = config.homeFormation.homeSlots()
        val awaySlots = config.awayFormation.homeSlots()

        for (p in players) {
            val slot = if (p.side == TeamSide.HOME) homeSlots[p.slotIndex] else awaySlots[p.slotIndex]
            val dir = if (p.side == TeamSide.HOME) homeDir else awayDir
            var px = slot.normX * PitchConstants.HALF_LENGTH * dir
            var pz = slot.normZ * PitchConstants.HALF_WIDTH * (if (dir > 0f) 1f else -1f)
            // Keep everyone in own half and outside center circle unless kickoff striker
            if (p.side == kickingSide && p.slotIndex == 9) {
                px = -0.8f * dir
                pz = 0f
            } else if (hypot(px, pz) < PitchConstants.CENTER_CIRCLE_RADIUS + 1.5f) {
                px = -11.5f * dir
            }
            p.x = px
            p.y = 0f
            p.z = pz
            p.vx = 0f
            p.vz = 0f
            p.facingYaw = if (dir > 0f) 0f else PI.toFloat()
            p.animState = AnimState.IDLE
            p.kickTimer = 0f
            p.tackleTimer = 0f
            p.actionCooldown = 0.25f
        }

        activeHomePlayerId = 9
        activeAwayPlayerId = 20
    }

    private fun setupSetPiece(newPhase: MatchPhase, takingSide: TeamSide, spotX: Float, spotZ: Float, subtitle: String) {
        phase = newPhase
        phaseTimer = 1.35f
        restartSide = takingSide
        restartX = spotX.coerceIn(-PitchConstants.HALF_LENGTH + 0.5f, PitchConstants.HALF_LENGTH - 0.5f)
        restartZ = spotZ.coerceIn(-PitchConstants.HALF_WIDTH + 0.5f, PitchConstants.HALF_WIDTH - 0.5f)
        bannerSubtitle = subtitle
        offsideCheckArmed = false

        ball.x = restartX
        ball.y = PitchConstants.BALL_RADIUS
        ball.z = restartZ
        ball.vx = 0f
        ball.vy = 0f
        ball.vz = 0f
        ball.ownerId = -1

        val dir = attackDirection(takingSide)
        val taker = players
            .filter { it.side == takingSide && (newPhase == MatchPhase.GOAL_KICK || it.role != PlayerRole.GK) }
            .let { list ->
                if (newPhase == MatchPhase.GOAL_KICK) list.firstOrNull { it.role == PlayerRole.GK }
                else if (newPhase == MatchPhase.PENALTY) list.firstOrNull { it.slotIndex == 9 } ?: list.lastOrNull()
                else list.minByOrNull { hypot(it.x - restartX, it.z - restartZ) }
            }

        if (taker != null) {
            taker.x = (restartX - dir * 0.95f).coerceIn(-PitchConstants.HALF_LENGTH + 0.4f, PitchConstants.HALF_LENGTH - 0.4f)
            taker.z = restartZ
            taker.vx = 0f
            taker.vz = 0f
            taker.facingYaw = atan2(-restartZ * 0.25f, dir * 10f)
            if (takingSide == TeamSide.HOME) activeHomePlayerId = taker.id else activeAwayPlayerId = taker.id
        }

        // Push opposing players back by 9.15m for free kicks / penalties / corners
        val minWallDist = if (newPhase == MatchPhase.PENALTY) 11f else 8.5f
        for (p in players) {
            if (p.id == taker?.id || p.role == PlayerRole.GK) continue
            val dx = p.x - restartX
            val dz = p.z - restartZ
            val d = hypot(dx, dz)
            if (p.side != takingSide && d < minWallDist) {
                val safeD = d.coerceAtLeast(0.1f)
                p.x = (restartX + (dx / safeD) * minWallDist).coerceIn(-PitchConstants.HALF_LENGTH + 2f, PitchConstants.HALF_LENGTH - 2f)
                p.z = (restartZ + (dz / safeD) * minWallDist).coerceIn(-PitchConstants.HALF_WIDTH + 2f, PitchConstants.HALF_WIDTH - 2f)
                p.vx = 0f
                p.vz = 0f
            }
        }
    }

    fun setLocalInput(side: TeamSide, input: PlayerInput) = synchronized(lock) {
        if (side == TeamSide.HOME) {
            homeInput = input
        } else {
            awayInput = input
        }
    }

    fun step(dt: Float) = synchronized(lock) {
        if (isPaused || phase == MatchPhase.FULL_TIME) return

        val clampedDt = dt.coerceIn(0.001f, 0.05f)
        if (cameraShake > 0f) {
            cameraShake = max(0f, cameraShake - clampedDt * 2.4f)
        }

        // Update timers on all players
        for (p in players) {
            if (p.actionCooldown > 0f) p.actionCooldown = max(0f, p.actionCooldown - clampedDt)
            if (p.kickTimer > 0f) p.kickTimer = max(0f, p.kickTimer - clampedDt)
            if (p.tackleTimer > 0f) p.tackleTimer = max(0f, p.tackleTimer - clampedDt)
        }

        // Handle non-playing phases
        if (phase != MatchPhase.PLAYING) {
            phaseTimer -= clampedDt
            if (phase == MatchPhase.GOAL_CELEBRATION) {
                for (p in players) {
                    if (p.side == ball.lastTouchSide) {
                        p.animState = AnimState.CELEBRATE
                        p.animTime += clampedDt * 6f
                    }
                }
                updateBallPhysics(clampedDt, checkBoundaries = false)
            }
            if (phaseTimer <= 0f) {
                when (phase) {
                    MatchPhase.KICKOFF -> {
                        phase = MatchPhase.PLAYING
                        bannerSubtitle = ""
                        onSoundEvent?.invoke(com.example.game.model.SoundEvent.WHISTLE_SHORT)
                        // Give kickoff striker crisp touch
                        val strikerId = if (restartSide == TeamSide.HOME) 9 else 20
                        ball.ownerId = strikerId
                        ball.lastTouchSide = restartSide
                        ball.lastTouchPlayerId = strikerId
                    }
                    MatchPhase.GOAL_CELEBRATION -> {
                        setupKickoff(restartSide, "KICK OFF")
                    }
                    MatchPhase.HALF_TIME -> {
                        half = 2
                        setupKickoff(TeamSide.AWAY, "SECOND HALF")
                    }
                    MatchPhase.THROW_IN, MatchPhase.CORNER_KICK, MatchPhase.GOAL_KICK,
                    MatchPhase.FREE_KICK, MatchPhase.OFFSIDE, MatchPhase.PENALTY -> {
                        executeSetPieceRestart()
                    }
                    else -> {
                        phase = MatchPhase.PLAYING
                    }
                }
            }
            return
        }

        // Advance match clock (map matchMinutes real minutes -> 90 football minutes)
        matchElapsedRealSeconds += clampedDt
        val totalMatchRealSeconds = config.matchMinutes * 60f
        if (half == 1 && matchElapsedRealSeconds >= totalMatchRealSeconds * 0.5f) {
            phase = MatchPhase.HALF_TIME
            phaseTimer = 2.6f
            bannerSubtitle = "${config.homeTeam.shortCode} $homeScore - $awayScore ${config.awayTeam.shortCode}"
            onSoundEvent?.invoke(com.example.game.model.SoundEvent.WHISTLE_FULL)
            return
        } else if (half == 2 && matchElapsedRealSeconds >= totalMatchRealSeconds) {
            phase = MatchPhase.FULL_TIME
            bannerSubtitle = "FINAL WHISTLE"
            onSoundEvent?.invoke(com.example.game.model.SoundEvent.WHISTLE_FULL)
            return
        }

        // Handle manual player switch button edge trigger
        if (homeInput.switchPressed && !prevHomeSwitch) {
            activeHomePlayerId = selectNextPlayer(TeamSide.HOME, activeHomePlayerId)
        }
        prevHomeSwitch = homeInput.switchPressed

        if (config.isLanMultiplayer && awayInput.switchPressed && !prevAwaySwitch) {
            activeAwayPlayerId = selectNextPlayer(TeamSide.AWAY, activeAwayPlayerId)
        }
        prevAwaySwitch = awayInput.switchPressed

        // Smart auto-switch when teammate takes possession
        if (ball.ownerId != -1) {
            val owner = players[ball.ownerId]
            if (owner.side == TeamSide.HOME) {
                activeHomePlayerId = owner.id
                stats = stats.copy(homePossessionTicks = stats.homePossessionTicks + 1)
            } else {
                if (config.isLanMultiplayer) activeAwayPlayerId = owner.id
                stats = stats.copy(awayPossessionTicks = stats.awayPossessionTicks + 1)
            }
        }

        // Select closest AI chasers per side
        val homeChasers = ai.selectClosestChasers(players, TeamSide.HOME, ball, activeHomePlayerId)
        val awayChasers = ai.selectClosestChasers(
            players,
            TeamSide.AWAY,
            ball,
            if (config.isLanMultiplayer) activeAwayPlayerId else -1
        )

        // Update all 22 players
        for (p in players) {
            val isHumanControlled = when (p.side) {
                TeamSide.HOME -> p.id == activeHomePlayerId
                TeamSide.AWAY -> config.isLanMultiplayer && p.id == activeAwayPlayerId
            }

            if (isHumanControlled) {
                val input = if (p.side == TeamSide.HOME) homeInput else awayInput
                applyHumanPlayerUpdate(p, input, clampedDt)
            } else {
                val formation = if (p.side == TeamSide.HOME) config.homeFormation else config.awayFormation
                val chasers = if (p.side == TeamSide.HOME) homeChasers else awayChasers
                val isTeamHuman = (p.side == TeamSide.HOME) || config.isLanMultiplayer
                val decision = ai.computePlayerDecision(
                    player = p,
                    allPlayers = players,
                    ball = ball,
                    formation = formation,
                    attackDir = attackDirection(p.side),
                    difficulty = config.difficulty,
                    isTeamHumanControlled = isTeamHuman,
                    isClosestChaser = p.id == chasers.first && (!isTeamHuman || ball.ownerId == -1),
                    isSecondaryPresser = p.id == chasers.second
                )
                applyAiPlayerUpdate(p, decision, clampedDt)
            }
        }

        // Resolve player-to-player collisions
        resolvePlayerCollisions()

        // Update ball physics, dribbling magnet, receptions, saves, and boundaries
        updateBallPhysics(clampedDt, checkBoundaries = true)
    }

    private fun executeSetPieceRestart() {
        val prevPhase = phase
        phase = MatchPhase.PLAYING
        bannerSubtitle = ""

        val takerId = if (restartSide == TeamSide.HOME) activeHomePlayerId else activeAwayPlayerId
        val taker = players.getOrNull(takerId) ?: return
        val dir = attackDirection(restartSide)

        when (prevPhase) {
            MatchPhase.PENALTY -> {
                // Player takes immediate strike toward goal if human didn't hold, or give control for 0.1s
                val oppGoalX = PitchConstants.HALF_LENGTH * dir
                val aimZ = if ((matchElapsedRealSeconds * 10).toInt() % 2 == 0) 2.2f else -2.2f
                val dx = oppGoalX - ball.x
                val dz = aimZ - ball.z
                val n = hypot(dx, dz).coerceAtLeast(0.001f)
                launchBall(taker, dx / n, dz / n, speed = 26.5f, loft = 3.2f, isShot = true)
            }
            MatchPhase.CORNER_KICK -> {
                // Whip corner into the box
                val targetX = (PitchConstants.HALF_LENGTH - 10f) * dir
                val dx = targetX - ball.x
                val dz = -ball.z
                val n = hypot(dx, dz).coerceAtLeast(0.001f)
                launchBall(taker, dx / n, dz / n, speed = 20.5f, loft = 8.4f, isShot = false)
            }
            MatchPhase.GOAL_KICK -> {
                launchBall(taker, dir, (if (taker.z >= 0f) -0.25f else 0.25f), speed = 24f, loft = 9.5f, isShot = false)
            }
            else -> {
                // Throw-in / Free kick / Offside: pass cleanly to nearest open teammate
                val mate = players
                    .filter { it.side == restartSide && it.id != taker.id && it.role != PlayerRole.GK }
                    .minByOrNull { hypot(it.x - taker.x, it.z - taker.z) }
                if (mate != null) {
                    val dx = mate.x - ball.x
                    val dz = mate.z - ball.z
                    val n = hypot(dx, dz).coerceAtLeast(0.001f)
                    launchBall(taker, dx / n, dz / n, speed = 15.5f, loft = if (prevPhase == MatchPhase.THROW_IN) 2.2f else 1.2f, isShot = false)
                } else {
                    ball.ownerId = taker.id
                }
            }
        }
    }

    private fun applyHumanPlayerUpdate(p: PlayerEntity, input: PlayerInput, dt: Float) {
        val ownsBall = ball.ownerId == p.id
        val moveMag = hypot(input.moveX, input.moveY).coerceAtMost(1f)
        val wantsSprint = input.sprint && p.stamina > 0.08f && moveMag > 0.2f

        movePlayerBody(p, input.moveX, input.moveY, wantsSprint, ownsBall, dt)

        // Compute aim vector from joystick or current facing
        val aimX = if (moveMag > 0.15f) input.moveX / moveMag else cos(p.facingYaw)
        val aimZ = if (moveMag > 0.15f) input.moveY / moveMag else sin(p.facingYaw)

        if (ownsBall && p.actionCooldown <= 0f) {
            when {
                input.shootPressed -> {
                    val power = input.shootCharge.coerceIn(0.25f, 1.0f)
                    val oppGoalX = PitchConstants.HALF_LENGTH * attackDirection(p.side)
                    // Blend manual aim with subtle goal-assist for console-like precision
                    val goalDx = oppGoalX - p.x
                    val goalDz = (aimZ * 3.1f) - p.z
                    val gn = hypot(goalDx, goalDz).coerceAtLeast(0.001f)
                    val bx = aimX * 0.45f + (goalDx / gn) * 0.55f
                    val bz = aimZ * 0.55f + (goalDz / gn) * 0.45f
                    val bn = hypot(bx, bz).coerceAtLeast(0.001f)
                    launchBall(
                        kicker = p,
                        dirX = bx / bn,
                        dirZ = bz / bn,
                        speed = 19f + power * 14.5f,
                        loft = 1.6f + power * 6.8f,
                        isShot = true
                    )
                }
                input.throughPressed -> {
                    performAssistedPass(p, aimX, aimZ, isThrough = true, charge = 0.68f)
                }
                input.passPressed -> {
                    performAssistedPass(p, aimX, aimZ, isThrough = false, charge = input.passCharge.coerceIn(0.25f, 1.0f))
                }
            }
        } else if (!ownsBall && p.actionCooldown <= 0f) {
            if (input.tacklePressed) {
                performTackle(p, aimX, aimZ)
            } else if (input.passPressed) {
                // Press / contain nudge toward ball
                val dx = ball.x - p.x
                val dz = ball.z - p.z
                val d = hypot(dx, dz).coerceAtLeast(0.001f)
                if (d < 4.5f) {
                    movePlayerBody(p, dx / d, dz / d, sprint = true, ownsBall = false, dt = dt * 0.4f)
                }
            }
        }
    }

    private fun applyAiPlayerUpdate(p: PlayerEntity, decision: AiActionDecision, dt: Float) {
        val ownsBall = ball.ownerId == p.id

        if (decision.gkDiveDir != 0f && p.role == PlayerRole.GK && p.actionCooldown <= 0f) {
            p.animState = AnimState.GK_DIVE
            p.diveDir = decision.gkDiveDir
            p.tackleTimer = 0.55f
            p.actionCooldown = 0.85f
            p.vz = decision.gkDiveDir * 7.8f
            return
        }

        movePlayerBody(p, decision.moveX, decision.moveZ, decision.sprint, ownsBall, dt)

        if (ownsBall && p.actionCooldown <= 0f) {
            when {
                decision.wantShoot -> {
                    launchBall(
                        kicker = p,
                        dirX = decision.aimX,
                        dirZ = decision.aimZ,
                        speed = 18.5f + decision.kickPower * 12.5f,
                        loft = 1.8f + decision.kickPower * 5.2f,
                        isShot = true
                    )
                }
                decision.wantThrough -> {
                    performAssistedPass(p, decision.aimX, decision.aimZ, isThrough = true, charge = decision.kickPower)
                }
                decision.wantPass -> {
                    performAssistedPass(p, decision.aimX, decision.aimZ, isThrough = false, charge = decision.kickPower)
                }
            }
        } else if (!ownsBall && decision.wantTackle && p.actionCooldown <= 0f) {
            performTackle(p, decision.aimX, decision.aimZ)
        }
    }

    private fun movePlayerBody(
        p: PlayerEntity,
        inputX: Float,
        inputZ: Float,
        sprint: Boolean,
        ownsBall: Boolean,
        dt: Float
    ) {
        // Handle ongoing slide tackle / GK dive inertia
        if (p.tackleTimer > 0f) {
            p.vx *= (1f - 3.2f * dt).coerceAtLeast(0f)
            p.vz *= (1f - 3.2f * dt).coerceAtLeast(0f)
            p.x = (p.x + p.vx * dt).coerceIn(-PitchConstants.HALF_LENGTH - 1.5f, PitchConstants.HALF_LENGTH + 1.5f)
            p.z = (p.z + p.vz * dt).coerceIn(-PitchConstants.HALF_WIDTH - 1.5f, PitchConstants.HALF_WIDTH + 1.5f)
            p.animTime += dt * 5f
            return
        }

        val mag = hypot(inputX, inputZ).coerceAtMost(1f)
        val dribbleFactor = if (ownsBall) 0.91f else 1.0f
        val staminaFactor = 0.78f + p.stamina * 0.22f
        val sprintMul = if (sprint) 1.28f else 1.0f
        val maxSpeed = p.baseSpeed * dribbleFactor * staminaFactor * sprintMul
        val turnBrake = if (mag > 0.08f) GameplayRealismTuning.turnBrakingFactor(p.facingYaw, atan2(inputZ, inputX), hypot(p.vx, p.vz)) else 1.0f
        val targetVx = inputX * maxSpeed * turnBrake
        val targetVz = inputZ * maxSpeed * turnBrake

        // Balanced eFootball-style momentum & acceleration
        val accel = if (mag > 0.08f) 11.5f else 14.0f
        val blend = (accel * dt).coerceAtMost(1f)
        p.vx += (targetVx - p.vx) * blend
        p.vz += (targetVz - p.vz) * blend

        p.x = (p.x + p.vx * dt).coerceIn(-PitchConstants.HALF_LENGTH - 1.8f, PitchConstants.HALF_LENGTH + 1.8f)
        p.z = (p.z + p.vz * dt).coerceIn(-PitchConstants.HALF_WIDTH - 1.8f, PitchConstants.HALF_WIDTH + 1.8f)

        val currentSpeed = hypot(p.vx, p.vz)
        if (currentSpeed > 0.35f) {
            val desiredYaw = atan2(p.vz, p.vx)
            var diff = desiredYaw - p.facingYaw
            while (diff > PI) diff -= (2.0 * PI).toFloat()
            while (diff < -PI) diff += (2.0 * PI).toFloat()
            val turnRate = if (sprint) 8.5f else 13.0f
            p.facingYaw += diff * (turnRate * dt).coerceAtMost(1f)
        } else {
            // Face the ball when idle
            p.facingYaw = atan2(ball.z - p.z, ball.x - p.x)
        }

        // Stamina drain / recovery
        if (sprint && currentSpeed > 4.5f) {
            p.stamina = (p.stamina - 0.085f * dt).coerceAtLeast(0.05f)
        } else {
            val regen = if (currentSpeed < 1.5f) 0.07f else 0.025f
            p.stamina = (p.stamina + regen * dt).coerceAtMost(1.0f)
        }

        // Animation state & cycle speed
        p.animState = when {
            p.kickTimer > 0f -> AnimState.KICK
            currentSpeed > p.baseSpeed * 1.04f -> AnimState.SPRINT
            currentSpeed > 3.2f -> AnimState.RUN
            currentSpeed > 0.45f -> AnimState.WALK
            else -> AnimState.IDLE
        }
        val strideRate = when (p.animState) {
            AnimState.SPRINT -> 11.5f
            AnimState.RUN -> 8.2f
            AnimState.WALK -> 4.6f
            AnimState.KICK -> 9.0f
            else -> 1.8f
        }
        p.animTime += dt * strideRate
    }

    private fun performAssistedPass(
        passer: PlayerEntity,
        aimX: Float,
        aimZ: Float,
        isThrough: Boolean,
        charge: Float
    ) {
        // Find best teammate in the aim cone
        var bestMate: PlayerEntity? = null
        var bestMetric = Float.NEGATIVE_INFINITY
        for (mate in players) {
            if (mate.side != passer.side || mate.id == passer.id) continue
            val dx = mate.x - passer.x
            val dz = mate.z - passer.z
            val dist = hypot(dx, dz)
            if (dist < 3.0f || dist > 45f) continue
            val alignment = (dx / dist) * aimX + (dz / dist) * aimZ
            if (alignment < 0.25f) continue
            val preferredDist = if (isThrough || charge > 0.65f) 24f else 13f
            val metric = alignment * 3.0f - abs(dist - preferredDist) * 0.06f
            if (metric > bestMetric) {
                bestMetric = metric
                bestMate = mate
            }
        }

        // Snapshot offside candidates at the moment of the pass
        val atkDir = attackDirection(passer.side)
        offsideLineAtPass = ai.computeOffsideLineX(players, passer.side, atkDir)
        offsideCandidateIds = players
            .filter { mate ->
                mate.side == passer.side && mate.id != passer.id &&
                    (mate.x * atkDir) > 0.5f && // in opponent's half
                    (mate.x * atkDir) > (ball.x * atkDir) + 0.5f &&
                    (mate.x * atkDir) > (offsideLineAtPass * atkDir) + 0.45f
            }
            .map { it.id }
            .toSet()
        offsideCheckArmed = offsideCandidateIds.isNotEmpty()
        lastPasserId = passer.id
        lastPasserSide = passer.side

        if (bestMate != null) {
            val leadSeconds = if (isThrough) 0.65f else 0.18f
            val leadForward = if (isThrough) atkDir * 5.2f else 0f
            val targetX = (bestMate.x + bestMate.vx * leadSeconds + leadForward)
                .coerceIn(-PitchConstants.HALF_LENGTH + 1.5f, PitchConstants.HALF_LENGTH - 1.5f)
            val targetZ = (bestMate.z + bestMate.vz * leadSeconds)
                .coerceIn(-PitchConstants.HALF_WIDTH + 1.5f, PitchConstants.HALF_WIDTH - 1.5f)
            val dx = targetX - passer.x
            val dz = targetZ - passer.z
            val dist = hypot(dx, dz).coerceAtLeast(0.001f)
            val isLongLoft = !isThrough && (charge > 0.65f || dist > 25f)
            val speed = (12.5f + dist * 0.48f + charge * 4.5f).coerceIn(13.5f, 27.5f)
            val loft = if (isLongLoft) (4.2f + charge * 4.2f) else if (isThrough) 1.1f else 0.5f
            launchBall(passer, dx / dist, dz / dist, speed, loft, isShot = false)

            // Switch active player smoothly to receiver if human team
            if (passer.side == TeamSide.HOME) {
                activeHomePlayerId = bestMate.id
            } else if (config.isLanMultiplayer) {
                activeAwayPlayerId = bestMate.id
            }
        } else {
            val speed = 15f + charge * 11f
            val loft = if (charge > 0.65f) 5.5f else 0.8f
            launchBall(passer, aimX, aimZ, speed, loft, isShot = false)
        }

        stats = if (passer.side == TeamSide.HOME) {
            stats.copy(homePasses = stats.homePasses + 1)
        } else {
            stats.copy(awayPasses = stats.awayPasses + 1)
        }
    }

    private fun launchBall(
        kicker: PlayerEntity,
        dirX: Float,
        dirZ: Float,
        speed: Float,
        loft: Float,
        isShot: Boolean
    ) {
        ball.ownerId = -1
        ball.lastTouchSide = kicker.side
        ball.lastTouchPlayerId = kicker.id
        ball.x = kicker.x + dirX * 0.65f
        ball.z = kicker.z + dirZ * 0.65f
        ball.y = PitchConstants.BALL_RADIUS + 0.08f
        ball.vx = dirX * speed
        ball.vy = loft
        ball.vz = dirZ * speed
        ball.spinY = if (isShot) (kicker.vz * 0.35f).coerceIn(-3f, 3f) else GameplayRealismTuning.passMagnusSpin(kicker.vz, loft, speed)

        kicker.facingYaw = atan2(dirZ, dirX)
        kicker.kickTimer = 0.28f
        kicker.animState = AnimState.KICK
        kicker.actionCooldown = 0.36f

        if (isShot) {
            onSoundEvent?.invoke(com.example.game.model.SoundEvent.KICK_SHOT)
            cameraShake = max(cameraShake, if (speed > 26f) 0.42f else 0.18f)
            offsideCheckArmed = false
            val onTarget = abs(ball.z + (ball.vz / max(abs(ball.vx), 1f)) * abs(PitchConstants.HALF_LENGTH - abs(ball.x))) < PitchConstants.GOAL_HALF_WIDTH + 0.8f
            stats = if (kicker.side == TeamSide.HOME) {
                stats.copy(
                    homeShots = stats.homeShots + 1,
                    homeShotsOnTarget = stats.homeShotsOnTarget + if (onTarget) 1 else 0
                )
            } else {
                stats.copy(
                    awayShots = stats.awayShots + 1,
                    awayShotsOnTarget = stats.awayShotsOnTarget + if (onTarget) 1 else 0
                )
            }
        } else {
            onSoundEvent?.invoke(com.example.game.model.SoundEvent.KICK_PASS)
        }
    }

    private fun performTackle(tackler: PlayerEntity, dirX: Float, dirZ: Float) {
        tackler.tackleTimer = 0.38f
        tackler.actionCooldown = 0.72f
        tackler.animState = AnimState.TACKLE
        tackler.vx = dirX * 10.2f
        tackler.vz = dirZ * 10.2f
        tackler.facingYaw = atan2(dirZ, dirX)
        onSoundEvent?.invoke(com.example.game.model.SoundEvent.TACKLE)

        if (ball.ownerId != -1) {
            val victim = players[ball.ownerId]
            if (victim.side != tackler.side) {
                val dist = hypot(victim.x - tackler.x, victim.z - tackler.z)
                if (dist < 2.25f) {
                    // Check angle: tackling from straight behind risks a foul!
                    val toVictimX = (victim.x - tackler.x) / dist.coerceAtLeast(0.01f)
                    val toVictimZ = (victim.z - tackler.z) / dist.coerceAtLeast(0.01f)
                    val victimFacingDot = toVictimX * cos(victim.facingYaw) + toVictimZ * sin(victim.facingYaw)
                    val distToBall = hypot(ball.x - tackler.x, ball.z - tackler.z)

                    if (victimFacingDot > 0.72f && distToBall > 1.35f) {
                        // Foul committed! Award Free Kick or Penalty
                        stats = if (tackler.side == TeamSide.HOME) {
                            stats.copy(homeFouls = stats.homeFouls + 1)
                        } else {
                            stats.copy(awayFouls = stats.awayFouls + 1)
                        }
                        val victimAttackDir = attackDirection(victim.side)
                        val inOppPenaltyBox = (victim.x * victimAttackDir) > (PitchConstants.HALF_LENGTH - PitchConstants.PENALTY_AREA_DEPTH) &&
                            abs(victim.z) < PitchConstants.PENALTY_AREA_HALF_WIDTH
                        if (inOppPenaltyBox) {
                            val penX = (PitchConstants.HALF_LENGTH - PitchConstants.PENALTY_SPOT_DIST) * victimAttackDir
                            setupSetPiece(MatchPhase.PENALTY, victim.side, penX, 0f, "PENALTY AWARDED")
                        } else {
                            setupSetPiece(MatchPhase.FREE_KICK, victim.side, victim.x, victim.z, "FOUL — FREE KICK")
                        }
                        onSoundEvent?.invoke(com.example.game.model.SoundEvent.WHISTLE_SHORT)
                    } else {
                        // Clean dispossession!
                        ball.ownerId = tackler.id
                        ball.lastTouchSide = tackler.side
                        ball.lastTouchPlayerId = tackler.id
                        victim.actionCooldown = 0.65f
                        stats = if (tackler.side == TeamSide.HOME) {
                            stats.copy(homeTackles = stats.homeTackles + 1)
                        } else {
                            stats.copy(awayTackles = stats.awayTackles + 1)
                        }
                    }
                }
            }
        }
    }

    private fun resolvePlayerCollisions() {
        val minDist = PitchConstants.PLAYER_RADIUS * 2.0f
        for (i in 0 until players.size) {
            val a = players[i]
            for (j in i + 1 until players.size) {
                val b = players[j]
                val dx = b.x - a.x
                val dz = b.z - a.z
                val distSq = dx * dx + dz * dz
                if (distSq > 0.0001f && distSq < minDist * minDist) {
                    val dist = hypot(dx, dz)
                    val overlap = (minDist - dist) * 0.5f
                    val nx = dx / dist
                    val nz = dz / dist
                    a.x -= nx * overlap
                    a.z -= nz * overlap
                    b.x += nx * overlap
                    b.z += nz * overlap
                }
            }
        }
    }

    private fun updateBallPhysics(dt: Float, checkBoundaries: Boolean) {
        if (ball.ownerId != -1) {
            val owner = players[ball.ownerId]
            val ownerSpeed = hypot(owner.vx, owner.vz)
            val fwdX = cos(owner.facingYaw)
            val fwdZ = sin(owner.facingYaw)
            val rightX = -fwdZ
            val rightZ = fwdX
            // Foot-synchronized dribble touch cadence so the ball meets the player's boot realistically
            val strideTouch = if (ownerSpeed > 0.6f) abs(sin(owner.animTime)) * 0.16f else 0f
            val lateralTouch = if (ownerSpeed > 0.6f) sin(owner.animTime) * 0.08f else 0.06f
            val dribbleDist = 0.44f + ownerSpeed * 0.028f + strideTouch
            val targetX = owner.x + fwdX * dribbleDist + rightX * lateralTouch
            val targetZ = owner.z + fwdZ * dribbleDist + rightZ * lateralTouch
            ball.x += (targetX - ball.x) * (20f * dt).coerceAtMost(1f)
            ball.z += (targetZ - ball.z) * (20f * dt).coerceAtMost(1f)
            ball.y = PitchConstants.BALL_RADIUS
            ball.vx = owner.vx
            ball.vy = 0f
            ball.vz = owner.vz
            val rollFactor = dt / PitchConstants.BALL_RADIUS
            ball.spinX += owner.vz * rollFactor
            ball.spinZ -= owner.vx * rollFactor

            // Check standing interception / shoulder barge by opposing player
            for (other in players) {
                if (other.side == owner.side || other.actionCooldown > 0f) continue
                val d = hypot(other.x - ball.x, other.z - ball.z)
                val stealRadius = if (other.role == PlayerRole.GK) 1.25f else 0.85f
                if (d < stealRadius) {
                    ball.ownerId = other.id
                    ball.lastTouchSide = other.side
                    ball.lastTouchPlayerId = other.id
                    owner.actionCooldown = 0.55f
                    break
                }
            }
            if (checkBoundaries) checkOutOfBoundsAndGoals()
            return
        }

        // Free-flight 3D physics with gravity, drag, Magnus spin, and turf friction
        ball.vy -= PitchConstants.GRAVITY * dt
        // Magnus sideways curve in air
        if (abs(ball.spinY) > 0.05f && ball.y > PitchConstants.BALL_RADIUS + 0.1f) {
            ball.vx += -ball.vz * ball.spinY * 0.12f * dt
            ball.vz += ball.vx * ball.spinY * 0.12f * dt
        }

        ball.x += ball.vx * dt
        ball.y += ball.vy * dt
        ball.z += ball.vz * dt

        // Ground collision & rolling friction
        if (ball.y <= PitchConstants.BALL_RADIUS) {
            ball.y = PitchConstants.BALL_RADIUS
            if (ball.vy < -1.4f) {
                ball.vy = -ball.vy * 0.56f
                ball.vx *= 0.90f
                ball.vz *= 0.90f
            } else {
                ball.vy = 0f
                val groundDrag = (1f - 1.38f * dt).coerceAtLeast(0f)
                ball.vx *= groundDrag
                ball.vz *= groundDrag
                ball.spinY *= (1f - 2.2f * dt).coerceAtLeast(0f)
            }
        } else {
            val airDrag = (1f - 0.16f * dt).coerceAtLeast(0f)
            ball.vx *= airDrag
            ball.vz *= airDrag
        }

        // Visual ball rolling spin
        ball.spinX += ball.vz * dt * 3.8f
        ball.spinZ -= ball.vx * dt * 3.8f

        // Goalpost & crossbar collisions
        resolveGoalFrameCollisions()

        // Ball reception & goalkeeper saves
        if (checkBoundaries) {
            for (p in players) {
                if (p.actionCooldown > 0f && p.id == ball.lastTouchPlayerId) continue
                val reachY = if (p.role == PlayerRole.GK) 2.35f else 1.85f
                if (ball.y > reachY) continue
                val catchRadius = when {
                    p.role == PlayerRole.GK && p.animState == AnimState.GK_DIVE -> 1.85f
                    p.role == PlayerRole.GK -> 1.35f
                    else -> 0.95f
                }
                val d = hypot(p.x - ball.x, p.z - ball.z)
                if (d < catchRadius) {
                    // Check offside violation on reception by teammate
                    if (offsideCheckArmed && p.side == lastPasserSide && p.id in offsideCandidateIds) {
                        offsideCheckArmed = false
                        onSoundEvent?.invoke(com.example.game.model.SoundEvent.WHISTLE_SHORT)
                        setupSetPiece(MatchPhase.OFFSIDE, p.side.opposite(), p.x, p.z, "OFFSIDE CALLED")
                        return
                    }
                    offsideCheckArmed = false

                    val ballSpeed = hypot(ball.vx, ball.vz)
                    if (p.role == PlayerRole.GK && ballSpeed > 16f && p.side != ball.lastTouchSide) {
                        // Dramatic parry / deflection save!
                        val defDir = attackDirection(p.side)
                        ball.vx = defDir * 8.5f
                        ball.vz = (if (ball.z >= 0f) 1f else -1f) * 7.5f
                        ball.vy = 3.2f
                        ball.lastTouchSide = p.side
                        ball.lastTouchPlayerId = p.id
                        p.actionCooldown = 0.45f
                        onSoundEvent?.invoke(com.example.game.model.SoundEvent.GK_SAVE)
                        cameraShake = max(cameraShake, 0.25f)
                    } else {
                        ball.ownerId = p.id
                        ball.lastTouchSide = p.side
                        ball.lastTouchPlayerId = p.id // first touch
                        if (p.role != PlayerRole.GK) {
                            val receiverSpeed = hypot(p.vx, p.vz)
                            val knockDist = GameplayRealismTuning.firstTouchKnockDistance(ballSpeed, receiverSpeed, receiverSpeed > p.baseSpeed * 1.05f, p.stamina)
                            val dirYaw = if (receiverSpeed > 0.35f) atan2(p.vz, p.vx) else p.facingYaw
                            ball.x = (p.x + cos(dirYaw) * knockDist).coerceIn(-PitchConstants.HALF_LENGTH + 0.5f, PitchConstants.HALF_LENGTH - 0.5f)
                            ball.z = (p.z + sin(dirYaw) * knockDist).coerceIn(-PitchConstants.HALF_WIDTH + 0.5f, PitchConstants.HALF_WIDTH - 0.5f)
                            ball.y = PitchConstants.BALL_RADIUS
                            ball.vy = 0f
                        }
                    }
                    break
                }
            }
            checkOutOfBoundsAndGoals()
        }
    }

    private fun resolveGoalFrameCollisions() {
        for (goalSign in listOf(-1f, 1f)) {
            val goalX = goalSign * PitchConstants.HALF_LENGTH
            if (abs(ball.x - goalX) < 0.55f) {
                // Left and right posts
                for (postSign in listOf(-1f, 1f)) {
                    val postZ = postSign * PitchConstants.GOAL_HALF_WIDTH
                    val dx = ball.x - goalX
                    val dz = ball.z - postZ
                    val d = hypot(dx, dz)
                    val minD = PitchConstants.BALL_RADIUS + PitchConstants.POST_RADIUS
                    if (d < minD && ball.y < PitchConstants.GOAL_HEIGHT + 0.1f) {
                        val safeD = d.coerceAtLeast(0.001f)
                        val nx = dx / safeD
                        val nz = dz / safeD
                        ball.x = goalX + nx * minD
                        ball.z = postZ + nz * minD
                        val dot = ball.vx * nx + ball.vz * nz
                        if (dot < 0f) {
                            ball.vx -= 1.72f * dot * nx
                            ball.vz -= 1.72f * dot * nz
                            onSoundEvent?.invoke(com.example.game.model.SoundEvent.POST_HIT)
                            cameraShake = max(cameraShake, 0.55f)
                        }
                    }
                }
                // Crossbar
                if (abs(ball.z) < PitchConstants.GOAL_HALF_WIDTH && abs(ball.y - PitchConstants.GOAL_HEIGHT) < PitchConstants.BALL_RADIUS + PitchConstants.POST_RADIUS) {
                    ball.vy = -ball.vy * 0.65f + 1.5f
                    ball.vx = -ball.vx * 0.55f
                    onSoundEvent?.invoke(com.example.game.model.SoundEvent.POST_HIT)
                    cameraShake = max(cameraShake, 0.55f)
                }
            }
            // Dampen ball inside the net
            val insideNetX = if (goalSign > 0f) ball.x > goalX && ball.x < goalX + PitchConstants.GOAL_DEPTH else ball.x < goalX && ball.x > goalX - PitchConstants.GOAL_DEPTH
            if (insideNetX && abs(ball.z) < PitchConstants.GOAL_HALF_WIDTH) {
                val backX = goalX + goalSign * (PitchConstants.GOAL_DEPTH - 0.15f)
                if ((goalSign > 0f && ball.x > backX) || (goalSign < 0f && ball.x < backX)) {
                    ball.x = backX
                    ball.vx *= -0.18f
                    ball.vz *= 0.35f
                }
            }
        }
    }

    private fun checkOutOfBoundsAndGoals() {
        // Check goals first!
        if (abs(ball.x) > PitchConstants.HALF_LENGTH + PitchConstants.BALL_RADIUS &&
            abs(ball.z) < PitchConstants.GOAL_HALF_WIDTH - 0.08f &&
            ball.y < PitchConstants.GOAL_HEIGHT
        ) {
            val scoredInPositiveX = ball.x > 0f
            val homeAttacksPos = attackDirection(TeamSide.HOME) > 0f
            val scoringSide = if (scoredInPositiveX == homeAttacksPos) TeamSide.HOME else TeamSide.AWAY
            if (scoringSide == TeamSide.HOME) homeScore++ else awayScore++
            ball.ownerId = -1
            ball.lastTouchSide = scoringSide
            phase = MatchPhase.GOAL_CELEBRATION
            phaseTimer = 2.8f
            restartSide = scoringSide.opposite()
            val scorerName = players.getOrNull(ball.lastTouchPlayerId)?.name ?: "GOAL"
            val clubCode = if (scoringSide == TeamSide.HOME) config.homeTeam.shortCode else config.awayTeam.shortCode
            bannerSubtitle = "$clubCode — $scorerName"
            cameraShake = 0.72f
            onSoundEvent?.invoke(com.example.game.model.SoundEvent.GOAL_ROAR)
            return
        }

        // Touchlines -> Throw-in
        if (abs(ball.z) > PitchConstants.HALF_WIDTH + PitchConstants.BALL_RADIUS) {
            val sideZ = sign(ball.z) * (PitchConstants.HALF_WIDTH - 0.25f)
            val takingSide = ball.lastTouchSide.opposite()
            setupSetPiece(MatchPhase.THROW_IN, takingSide, ball.x, sideZ, "${if (takingSide == TeamSide.HOME) config.homeTeam.shortCode else config.awayTeam.shortCode} THROW-IN")
            return
        }

        // Goal lines -> Corner Kick or Goal Kick
        if (abs(ball.x) > PitchConstants.HALF_LENGTH + PitchConstants.BALL_RADIUS) {
            val crossedPositiveX = ball.x > 0f
            val defendingSide = if ((attackDirection(TeamSide.HOME) > 0f) == crossedPositiveX) TeamSide.AWAY else TeamSide.HOME
            if (ball.lastTouchSide == defendingSide) {
                val cornerX = sign(ball.x) * (PitchConstants.HALF_LENGTH - 0.6f)
                val cornerZ = sign(ball.z) * (PitchConstants.HALF_WIDTH - 0.6f)
                val takingSide = defendingSide.opposite()
                setupSetPiece(MatchPhase.CORNER_KICK, takingSide, cornerX, cornerZ, "CORNER KICK")
            } else {
                val gkX = sign(ball.x) * (PitchConstants.HALF_LENGTH - PitchConstants.GOAL_AREA_DEPTH)
                setupSetPiece(MatchPhase.GOAL_KICK, defendingSide, gkX, 0f, "GOAL KICK")
            }
        }
    }

    private fun selectNextPlayer(side: TeamSide, currentId: Int): Int {
        return players
            .filter { it.side == side && it.role != PlayerRole.GK && it.id != currentId }
            .minByOrNull { hypot(it.x - ball.x, it.z - ball.z) }
            ?.id ?: currentId
    }

    fun captureRenderSnapshot(): RenderWorldSnapshot = synchronized(lock) {
        val localInput = if (config.localControlledSide == TeamSide.HOME) homeInput else awayInput
        RenderWorldSnapshot(
            players = players.map { p ->
                RenderPlayer(
                    id = p.id,
                    side = p.side,
                    role = p.role,
                    shirtNumber = p.shirtNumber,
                    skinToneIndex = p.skinToneIndex,
                    hairToneIndex = p.hairToneIndex,
                    x = p.x,
                    y = p.y,
                    z = p.z,
                    speed = hypot(p.vx, p.vz),
                    facingYaw = p.facingYaw,
                    stamina = p.stamina,
                    animState = p.animState,
                    animTime = p.animTime,
                    diveDir = p.diveDir
                )
            },
            ballX = ball.x,
            ballY = ball.y,
            ballZ = ball.z,
            ballVx = ball.vx,
            ballVz = ball.vz,
            ballSpinX = ball.spinX,
            ballSpinY = ball.spinY,
            ballSpinZ = ball.spinZ,
            ballOwnerId = ball.ownerId,
            activeHomePlayerId = activeHomePlayerId,
            activeAwayPlayerId = activeAwayPlayerId,
            localControlledSide = config.localControlledSide,
            phase = phase,
            shootCharge = localInput.shootCharge,
            cameraShake = cameraShake,
            matchClockSec = matchElapsedRealSeconds,
            config = config
        )
    }

    fun captureHudSnapshot(): MatchHudSnapshot = synchronized(lock) {
        val totalReal = (config.matchMinutes * 60f).coerceAtLeast(1f)
        val footballSeconds = ((matchElapsedRealSeconds / totalReal) * 90f * 60f).toInt().coerceIn(0, 90 * 60)
        val localSide = config.localControlledSide
        val activeId = if (localSide == TeamSide.HOME) activeHomePlayerId else activeAwayPlayerId
        val activePlayer = players.getOrNull(activeId)
        val localInput = if (localSide == TeamSide.HOME) homeInput else awayInput
        val localHasBall = ball.ownerId != -1 && players.getOrNull(ball.ownerId)?.side == localSide

        MatchHudSnapshot(
            homeScore = homeScore,
            awayScore = awayScore,
            matchClockMinutes = footballSeconds / 60,
            matchClockSeconds = footballSeconds % 60,
            half = half,
            phase = phase,
            bannerSubtitle = bannerSubtitle,
            activePlayerName = activePlayer?.name ?: "",
            activePlayerNumber = activePlayer?.shirtNumber ?: 9,
            activePlayerStamina = activePlayer?.stamina ?: 1f,
            localTeamHasBall = localHasBall,
            shootCharge = localInput.shootCharge,
            passCharge = localInput.passCharge,
            stats = stats,
            radarBallX = (ball.x / PitchConstants.HALF_LENGTH).coerceIn(-1f, 1f),
            radarBallZ = (ball.z / PitchConstants.HALF_WIDTH).coerceIn(-1f, 1f),
            radarHomePlayers = players.filter { it.side == TeamSide.HOME }
                .map { (it.x / PitchConstants.HALF_LENGTH).coerceIn(-1f, 1f) to (it.z / PitchConstants.HALF_WIDTH).coerceIn(-1f, 1f) },
            radarAwayPlayers = players.filter { it.side == TeamSide.AWAY }
                .map { (it.x / PitchConstants.HALF_LENGTH).coerceIn(-1f, 1f) to (it.z / PitchConstants.HALF_WIDTH).coerceIn(-1f, 1f) },
            controlledPlayerNorm = ((activePlayer?.x ?: 0f) / PitchConstants.HALF_LENGTH).coerceIn(-1f, 1f) to
                ((activePlayer?.z ?: 0f) / PitchConstants.HALF_WIDTH).coerceIn(-1f, 1f),
            isPaused = isPaused
        )
    }

    /**
     * Applies an interpolated & reconciled authoritative snapshot received from the Host phone (Client mode).
     */
    fun applyNetworkSnapshot(
        homeGoals: Int,
        awayGoals: Int,
        elapsedSec: Float,
        currentHalf: Int,
        currentPhase: MatchPhase,
        homeActiveId: Int,
        awayActiveId: Int,
        ballX: Float,
        ballY: Float,
        ballZ: Float,
        ballVx: Float,
        ballVy: Float,
        ballVz: Float,
        ballOwner: Int,
        playerStates: List<NetPlayerState>,
        syncedStats: MatchStats? = null,
        interpAlpha: Float = 1.0f
    ) = synchronized(lock) {
        val prevScoreSum = homeScore + awayScore
        val prevPhase = phase

        homeScore = homeGoals
        awayScore = awayGoals
        matchElapsedRealSeconds = elapsedSec
        half = currentHalf
        phase = currentPhase
        activeHomePlayerId = homeActiveId
        activeAwayPlayerId = awayActiveId
        if (syncedStats != null) {
            stats = syncedStats
        }

        // Trigger synchronized audio & banner reactions on Client device
        if (homeGoals + awayGoals > prevScoreSum) {
            cameraShake = 0.7f
            onSoundEvent?.invoke(com.example.game.model.SoundEvent.GOAL_ROAR)
            bannerSubtitle = "${config.homeTeam.shortCode} $homeGoals - $awayGoals ${config.awayTeam.shortCode}"
        } else if (currentPhase != prevPhase) {
            bannerSubtitle = currentPhase.bannerTitle
            when (currentPhase) {
                MatchPhase.PLAYING -> if (prevPhase == MatchPhase.KICKOFF) onSoundEvent?.invoke(com.example.game.model.SoundEvent.WHISTLE_SHORT)
                MatchPhase.HALF_TIME, MatchPhase.FULL_TIME -> onSoundEvent?.invoke(com.example.game.model.SoundEvent.WHISTLE_FULL)
                MatchPhase.FREE_KICK, MatchPhase.OFFSIDE, MatchPhase.PENALTY -> onSoundEvent?.invoke(com.example.game.model.SoundEvent.WHISTLE_SHORT)
                else -> Unit
            }
        }

        if (cameraShake > 0f) {
            cameraShake = max(0f, cameraShake - 0.04f)
        }

        val a = interpAlpha.coerceIn(0.15f, 1.0f)
        ball.x += (ballX - ball.x) * a
        ball.y += (ballY - ball.y) * a
        ball.z += (ballZ - ball.z) * a
        ball.vx = ballVx
        ball.vy = ballVy
        ball.vz = ballVz
        ball.ownerId = ballOwner
        ball.spinX += ballVz * 0.05f
        ball.spinZ -= ballVx * 0.05f

        for (i in 0 until min(players.size, playerStates.size)) {
            val p = players[i]
            val ns = playerStates[i]
            val jumpDist = hypot(ns.x - p.x, ns.z - p.z)
            val blend = if (jumpDist > 4.5f) 1.0f else a
            p.x += (ns.x - p.x) * blend
            p.z += (ns.z - p.z) * blend
            p.facingYaw = ns.yaw
            p.stamina = ns.stamina
            p.animState = ns.animState
            p.animTime = ns.animTime
            p.diveDir = ns.diveDir
        }
    }

    fun captureNetworkWorldState(): NetworkWorldState = synchronized(lock) {
        NetworkWorldState(
            homeGoals = homeScore,
            awayGoals = awayScore,
            elapsedSec = matchElapsedRealSeconds,
            half = half,
            phase = phase,
            activeHomeId = activeHomePlayerId,
            activeAwayId = activeAwayPlayerId,
            ballX = ball.x,
            ballY = ball.y,
            ballZ = ball.z,
            ballVx = ball.vx,
            ballVy = ball.vy,
            ballVz = ball.vz,
            ballOwner = ball.ownerId,
            stats = stats,
            players = snapshotNetworkPlayers()
        )
    }

    fun exportNetworkPlayers(): List<NetPlayerState> = synchronized(lock) {
        snapshotNetworkPlayers()
    }

    private fun snapshotNetworkPlayers(): List<NetPlayerState> =
        players.map {
            NetPlayerState(
                x = it.x,
                z = it.z,
                yaw = it.facingYaw,
                stamina = it.stamina,
                animState = it.animState,
                animTime = it.animTime,
                diveDir = it.diveDir
            )
        }
}

data class NetPlayerState(
    val x: Float,
    val z: Float,
    val yaw: Float,
    val stamina: Float,
    val animState: AnimState,
    val animTime: Float,
    val diveDir: Float = 0f
)
