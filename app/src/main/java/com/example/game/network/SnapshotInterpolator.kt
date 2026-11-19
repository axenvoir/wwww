package com.example.game.network

import com.example.game.engine.NetPlayerState
import com.example.game.engine.PitchConstants
import com.example.game.model.PlayerInput
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

data class BufferedClientInput(
    val input: PlayerInput,
    val dt: Float
)

/**
 * Stage 4 Networking Core:
 * 1) Time-aligned UDP Snapshot Interpolation Buffer (jitter-adaptive render delay + bounded dead-reckoning).
 * 2) Client-Side Input Prediction & Authoritative Host Reconciliation for the local Away player.
 * 3) Rolling packet-loss and arrival jitter telemetry.
 */
class SnapshotInterpolator {

    private val lock = Any()
    private val snapshotRing = ArrayDeque<DecodedWorldSnapshot>(16)
    private val unackedInputs = ArrayDeque<BufferedClientInput>(64)

    private var latestSeq = -1
    private var expectedPackets = 0
    private var receivedPackets = 0
    private var lastArrivalMs = 0L
    private var avgInterArrivalMs = 33f
    private var jitterMs = 4f

    // Smoothed predicted coordinates for Client's locally controlled player (Away)
    private var predPlayerId = -1
    private var predX = 0f
    private var predZ = 0f
    private var predYaw = 0f
    private var hasPredictionAnchor = false

    val packetLossPct: Int
        get() = synchronized(lock) {
            if (expectedPackets <= 0) 0
            else (((expectedPackets - receivedPackets).coerceAtLeast(0) * 100f) / expectedPackets)
                .toInt().coerceIn(0, 100)
        }

    val measuredJitterMs: Int
        get() = synchronized(lock) { jitterMs.toInt().coerceIn(0, 250) }

    fun reset() = synchronized(lock) {
        snapshotRing.clear()
        unackedInputs.clear()
        latestSeq = -1
        expectedPackets = 0
        receivedPackets = 0
        lastArrivalMs = 0L
        avgInterArrivalMs = 33f
        jitterMs = 4f
        hasPredictionAnchor = false
        predPlayerId = -1
    }

    fun pushSnapshot(snapshot: DecodedWorldSnapshot) = synchronized(lock) {
        if (snapshot.seq <= latestSeq) return

        // Track packet loss & inter-arrival jitter
        if (latestSeq >= 0) {
            val deltaSeq = snapshot.seq - latestSeq
            expectedPackets += deltaSeq
            receivedPackets += 1
            if (expectedPackets > 200) {
                expectedPackets /= 2
                receivedPackets /= 2
            }
            if (lastArrivalMs > 0L) {
                val interval = (snapshot.receivedAtMs - lastArrivalMs).toFloat().coerceIn(1f, 300f)
                val diff = abs(interval - avgInterArrivalMs)
                jitterMs += (diff - jitterMs) * 0.15f
                avgInterArrivalMs += (interval - avgInterArrivalMs) * 0.12f
            }
        } else {
            expectedPackets = 1
            receivedPackets = 1
        }
        latestSeq = snapshot.seq
        lastArrivalMs = snapshot.receivedAtMs

        snapshotRing.addLast(snapshot)
        while (snapshotRing.size > 12) {
            snapshotRing.removeFirst()
        }

        // Server Reconciliation: discard inputs already processed by Host and replay unacked inputs
        while (unackedInputs.isNotEmpty() && unackedInputs.first().input.seq <= snapshot.lastAckedClientInputSeq) {
            unackedInputs.removeFirst()
        }

        val controlledIdx = snapshot.activeAwayId
        val authPlayer = snapshot.players.getOrNull(controlledIdx)
        if (authPlayer != null) {
            var rx = authPlayer.x
            var rz = authPlayer.z
            var ryaw = authPlayer.yaw
            for (buffered in unackedInputs) {
                val speed = if (buffered.input.sprint) 9.8f else 7.6f
                rx = (rx + buffered.input.moveX * speed * buffered.dt)
                    .coerceIn(-PitchConstants.HALF_LENGTH - 1.5f, PitchConstants.HALF_LENGTH + 1.5f)
                rz = (rz + buffered.input.moveY * speed * buffered.dt)
                    .coerceIn(-PitchConstants.HALF_WIDTH - 1.5f, PitchConstants.HALF_WIDTH + 1.5f)
                if (hypot(buffered.input.moveX, buffered.input.moveY) > 0.18f) {
                    ryaw = atan2(buffered.input.moveY, buffered.input.moveX)
                }
            }

            if (!hasPredictionAnchor || predPlayerId != controlledIdx) {
                predPlayerId = controlledIdx
                predX = rx
                predZ = rz
                predYaw = ryaw
                hasPredictionAnchor = true
            } else {
                val err = hypot(rx - predX, rz - predZ)
                // Hard snap only on large teleport (kickoff/set-piece), otherwise smooth reconcile
                val reconcileAlpha = if (err > 2.8f) 1.0f else 0.35f
                predX += (rx - predX) * reconcileAlpha
                predZ += (rz - predZ) * reconcileAlpha
                predYaw = lerpAngle(predYaw, ryaw, 0.45f)
            }
        }
    }

    /**
     * Records a client input frame and predicts the controlled player forward immediately for zero input lag.
     */
    fun recordAndPredictLocalInput(input: PlayerInput, dt: Float) = synchronized(lock) {
        val clampedDt = dt.coerceIn(0.005f, 0.05f)
        unackedInputs.addLast(BufferedClientInput(input, clampedDt))
        while (unackedInputs.size > 48) {
            unackedInputs.removeFirst()
        }
        if (hasPredictionAnchor) {
            val speed = if (input.sprint) 9.8f else 7.6f
            predX = (predX + input.moveX * speed * clampedDt)
                .coerceIn(-PitchConstants.HALF_LENGTH - 1.5f, PitchConstants.HALF_LENGTH + 1.5f)
            predZ = (predZ + input.moveY * speed * clampedDt)
                .coerceIn(-PitchConstants.HALF_WIDTH - 1.5f, PitchConstants.HALF_WIDTH + 1.5f)
            if (hypot(input.moveX, input.moveY) > 0.18f) {
                predYaw = lerpAngle(predYaw, atan2(input.moveY, input.moveX), 0.35f)
            }
        }
    }

    /**
     * Samples an interpolated (or short-horizon extrapolated) world snapshot for the given local timestamp.
     */
    fun sampleInterpolatedWorld(nowMs: Long = System.currentTimeMillis()): DecodedWorldSnapshot? = synchronized(lock) {
        if (snapshotRing.isEmpty()) return null

        // Adaptive interpolation delay: 50ms baseline + 1.5x jitter, clamped to 45..115ms
        val interpDelayMs = (50f + jitterMs * 1.5f).coerceIn(45f, 115f).toLong()
        val renderTimeMs = nowMs - interpDelayMs

        if (snapshotRing.size == 1) {
            return applyLocalPredictionOverride(snapshotRing.last())
        }

        // Find pair (prev, next) bracketing renderTimeMs
        var prev: DecodedWorldSnapshot = snapshotRing.first()
        var next: DecodedWorldSnapshot = snapshotRing.last()
        var foundBracket = false
        for (i in 0 until snapshotRing.size - 1) {
            val a = snapshotRing[i]
            val b = snapshotRing[i + 1]
            if (renderTimeMs in a.receivedAtMs..b.receivedAtMs) {
                prev = a
                next = b
                foundBracket = true
                break
            }
        }

        if (!foundBracket) {
            // If renderTimeMs is ahead of our newest snapshot (e.g., dropped packet), gently extrapolate ball up to 90ms
            val newest = snapshotRing.last()
            val extrapSec = ((renderTimeMs - newest.receivedAtMs).coerceIn(0L, 90L)) / 1000f
            if (extrapSec <= 0.001f) {
                return applyLocalPredictionOverride(newest)
            }
            val extrapBallX = (newest.ballX + newest.ballVx * extrapSec)
                .coerceIn(-PitchConstants.HALF_LENGTH - 2f, PitchConstants.HALF_LENGTH + 2f)
            val extrapBallY = max(PitchConstants.BALL_RADIUS, newest.ballY + newest.ballVy * extrapSec - 0.5f * PitchConstants.GRAVITY * extrapSec * extrapSec)
            val extrapBallZ = (newest.ballZ + newest.ballVz * extrapSec)
                .coerceIn(-PitchConstants.HALF_WIDTH - 2f, PitchConstants.HALF_WIDTH + 2f)
            return applyLocalPredictionOverride(
                newest.copy(ballX = extrapBallX, ballY = extrapBallY, ballZ = extrapBallZ)
            )
        }

        val span = (next.receivedAtMs - prev.receivedAtMs).coerceAtLeast(1L).toFloat()
        val t = ((renderTimeMs - prev.receivedAtMs) / span).coerceIn(0f, 1f)

        val count = min(prev.players.size, next.players.size)
        val interpPlayers = ArrayList<NetPlayerState>(count)
        for (i in 0 until count) {
            val p0 = prev.players[i]
            val p1 = next.players[i]
            val jump = hypot(p1.x - p0.x, p1.z - p0.z)
            if (jump > 5.0f) {
                interpPlayers.add(p1)
            } else {
                interpPlayers.add(
                    NetPlayerState(
                        x = lerp(p0.x, p1.x, t),
                        z = lerp(p0.z, p1.z, t),
                        yaw = lerpAngle(p0.yaw, p1.yaw, t),
                        stamina = lerp(p0.stamina, p1.stamina, t),
                        animState = p1.animState,
                        animTime = lerp(p0.animTime, p1.animTime, t),
                        diveDir = lerp(p0.diveDir, p1.diveDir, t)
                    )
                )
            }
        }

        val ballJump = hypot(next.ballX - prev.ballX, next.ballZ - prev.ballZ)
        val bx = if (ballJump > 8f) next.ballX else lerp(prev.ballX, next.ballX, t)
        val by = if (ballJump > 8f) next.ballY else lerp(prev.ballY, next.ballY, t)
        val bz = if (ballJump > 8f) next.ballZ else lerp(prev.ballZ, next.ballZ, t)

        val blended = next.copy(
            ballX = bx,
            ballY = by,
            ballZ = bz,
            players = interpPlayers
        )
        return applyLocalPredictionOverride(blended)
    }

    private fun applyLocalPredictionOverride(snapshot: DecodedWorldSnapshot): DecodedWorldSnapshot {
        if (!hasPredictionAnchor) return snapshot
        val idx = snapshot.activeAwayId
        if (idx !in snapshot.players.indices || idx != predPlayerId) return snapshot
        val updated = snapshot.players.toMutableList()
        val orig = updated[idx]
        updated[idx] = orig.copy(x = predX, z = predZ, yaw = predYaw)
        return snapshot.copy(players = updated)
    }

    companion object {
        fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

        fun lerpAngle(a: Float, b: Float, t: Float): Float {
            var diff = b - a
            while (diff > PI) diff -= (2.0 * PI).toFloat()
            while (diff < -PI) diff += (2.0 * PI).toFloat()
            return a + diff * t
        }
    }
}
