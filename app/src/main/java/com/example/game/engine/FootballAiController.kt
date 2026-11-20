package com.example.game.engine

import com.example.game.model.AiDifficulty
import com.example.game.model.AnimState
import com.example.game.model.BallState
import com.example.game.model.Formation
import com.example.game.model.PlayerEntity
import com.example.game.model.PlayerRole
import com.example.game.model.TeamSide
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign

data class AiActionDecision(
    val moveX: Float = 0f,
    val moveZ: Float = 0f,
    val sprint: Boolean = false,
    val wantPass: Boolean = false,
    val wantThrough: Boolean = false,
    val wantShoot: Boolean = false,
    val wantTackle: Boolean = false,
    val kickPower: Float = 0.5f,
    val aimX: Float = 1f,
    val aimZ: Float = 0f,
    val gkDiveDir: Float = 0f
)

class FootballAiController {

    fun computePlayerDecision(
        player: PlayerEntity,
        allPlayers: List<PlayerEntity>,
        ball: BallState,
        formation: Formation,
        attackDir: Float, // +1f attacks +X, -1f attacks -X
        difficulty: AiDifficulty,
        isTeamHumanControlled: Boolean,
        isClosestChaser: Boolean,
        isSecondaryPresser: Boolean
    ): AiActionDecision {
        if (player.role == PlayerRole.GK) {
            return computeGoalkeeperDecision(player, allPlayers, ball, attackDir, difficulty)
        }

        val ownsBall = ball.ownerId == player.id
        if (ownsBall) {
            return computeBallCarrierAi(player, allPlayers, ball, attackDir, difficulty)
        }

        val teamHasBall = ball.ownerId != -1 && allPlayers.getOrNull(ball.ownerId)?.side == player.side
        val distToBall = hypot(ball.x - player.x, ball.z - player.z)

        // If this player is the designated closest chaser or loose-ball challenger
        if (isClosestChaser || (isSecondaryPresser && !teamHasBall) || (!teamHasBall && distToBall < 7.5f)) {
            val leadT = min(0.45f, distToBall / 14f); val jockeyMode = isSecondaryPresser && !isClosestChaser && !teamHasBall && ball.ownerId != -1; val ownGoalX = -PitchConstants.HALF_LENGTH * attackDir; val gd = hypot(ownGoalX - ball.x, -ball.z).coerceAtLeast(0.001f)
            val targetX = (if (jockeyMode) ball.x + ((ownGoalX - ball.x) / gd) * 3.8f else ball.x + ball.vx * leadT).coerceIn(-PitchConstants.HALF_LENGTH + 1f, PitchConstants.HALF_LENGTH - 1f)
            val targetZ = (if (jockeyMode) ball.z + (-ball.z / gd) * 3.8f else ball.z + ball.vz * leadT).coerceIn(-PitchConstants.HALF_WIDTH + 1f, PitchConstants.HALF_WIDTH - 1f)
            val dx = targetX - player.x
            val dz = targetZ - player.z
            val d = hypot(dx, dz).coerceAtLeast(0.001f)
            val shouldTackle = !teamHasBall && ball.ownerId != -1 && distToBall < 1.55f && ((cos(player.facingYaw) * (ball.x - player.x) + sin(player.facingYaw) * (ball.z - player.z)) / distToBall.coerceAtLeast(0.001f)) > 0.55f &&
                player.actionCooldown <= 0f && (!isTeamHumanControlled || isSecondaryPresser)
            return AiActionDecision(
                moveX = dx / d,
                moveZ = dz / d,
                sprint = distToBall > 4.0f && player.stamina > 0.25f,
                wantTackle = shouldTackle,
                aimX = dx / d,
                aimZ = dz / d
            )
        }

        // Compute dynamic tactical anchor based on formation & ball coordinates
        val slots = formation.homeSlots()
        val slot = slots[player.slotIndex.coerceIn(0, slots.lastIndex)]
        // Base coordinates aligned to attackDir (+1 attacks right, -1 attacks left)
        val baseX = slot.normX * PitchConstants.HALF_LENGTH * attackDir
        val baseZ = slot.normZ * PitchConstants.HALF_WIDTH * (if (attackDir > 0f) 1f else -1f)

        val ballNormX = (ball.x / PitchConstants.HALF_LENGTH).coerceIn(-1f, 1f)
        val ballNormZ = (ball.z / PitchConstants.HALF_WIDTH).coerceIn(-1f, 1f)

        var targetX = baseX
        var targetZ = baseZ

        if (teamHasBall) {
            // Attacking shape: push forward intelligently into space & create passing triangles
            val pushForward = when (player.role) {
                PlayerRole.DF -> 14f
                PlayerRole.MF -> 20f
                PlayerRole.FW -> 26f
                PlayerRole.GK -> 0f
            }
            targetX += attackDir * pushForward + ballNormX * 15f
            targetZ += ballNormZ * 7.5f

            // Keep forwards just onside of the opponent's second-last defender
            if (player.role == PlayerRole.FW) {
                val offsideLine = computeOffsideLineX(allPlayers, player.side, attackDir)
                if (attackDir > 0f) {
                    targetX = min(targetX, offsideLine - 1.2f)
                } else {
                    targetX = max(targetX, offsideLine + 1.2f)
                }
            }
        } else {
            // Defending shape: compact block shifting toward ball side
            val shiftFactor = when (player.role) {
                PlayerRole.DF -> 0.55f
                PlayerRole.MF -> 0.70f
                PlayerRole.FW -> 0.50f
                PlayerRole.GK -> 0f
            }
            targetX = baseX * 0.72f + ball.x * shiftFactor - attackDir * 7f
            targetZ = baseZ * 0.65f + ball.z * 0.42f

            if (isSecondaryPresser) {
                // Step up into passing lane near ball
                targetX = targetX * 0.5f + ball.x * 0.5f
                targetZ = targetZ * 0.5f + ball.z * 0.5f
            }
        }

        // Clamp within playable pitch margins
        targetX = targetX.coerceIn(-PitchConstants.HALF_LENGTH + 3.5f, PitchConstants.HALF_LENGTH - 3.5f)
        targetZ = targetZ.coerceIn(-PitchConstants.HALF_WIDTH + 2.5f, PitchConstants.HALF_WIDTH - 2.5f)

        val dx = targetX - player.x
        val dz = targetZ - player.z
        val dist = hypot(dx, dz)

        if (dist < 1.1f) {
            return AiActionDecision(moveX = 0f, moveZ = 0f)
        }

        val speedWeight = (dist / 4.5f).coerceIn(0.35f, 1.0f) * difficulty.reactionSpeed.coerceAtMost(1.0f)
        return AiActionDecision(
            moveX = (dx / dist) * speedWeight,
            moveZ = (dz / dist) * speedWeight,
            sprint = dist > 11.5f && player.stamina > 0.35f
        )
    }

    private fun computeBallCarrierAi(
        player: PlayerEntity,
        allPlayers: List<PlayerEntity>,
        ball: BallState,
        attackDir: Float,
        difficulty: AiDifficulty
    ): AiActionDecision {
        val oppGoalX = PitchConstants.HALF_LENGTH * attackDir
        val distToGoal = hypot(oppGoalX - player.x, -player.z)

        // Check shooting opportunity inside attacking third
        if (distToGoal < 24.5f && abs(player.z) < 18.5f && player.actionCooldown <= 0f) {
            val goalCornerZ = if (player.z > 0f) -2.3f else 2.3f
            val aimDx = oppGoalX - player.x
            val aimDz = goalCornerZ - player.z
            val norm = hypot(aimDx, aimDz).coerceAtLeast(0.001f)
            val power = ((distToGoal / 28f) * 0.65f + 0.30f).coerceIn(0.40f, 0.92f)
            return AiActionDecision(
                wantShoot = true,
                kickPower = power,
                aimX = aimDx / norm,
                aimZ = aimDz / norm
            )
        }

        // Check opponent pressure
        val nearestOppDist = allPlayers
            .filter { it.side != player.side }
            .minOfOrNull { hypot(it.x - player.x, it.z - player.z) } ?: 99f

        // Evaluate passing / through-ball options when pressured or advancing
        if ((nearestOppDist < 5.2f || (player.role == PlayerRole.DF && distToGoal > 62f)) && player.actionCooldown <= 0f) {
            val bestMate = findBestPassTarget(player, allPlayers, attackDir)
            if (bestMate != null) {
                val dx = bestMate.x - player.x
                val dz = bestMate.z - player.z
                val d = hypot(dx, dz).coerceAtLeast(0.001f)
                val isThrough = bestMate.role == PlayerRole.FW && (dx * attackDir) > 7f && d > 10f
                val power = (d / 32f).coerceIn(0.32f, 0.85f)
                return AiActionDecision(
                    wantPass = !isThrough,
                    wantThrough = isThrough,
                    kickPower = power,
                    aimX = dx / d,
                    aimZ = dz / d
                )
            }
        }

        // Dribble toward opponent goal while steering slightly away from nearest defender
        var driveX = oppGoalX - player.x
        var driveZ = (player.z * 0.45f) - player.z
        val norm = hypot(driveX, driveZ).coerceAtLeast(0.001f)
        driveX /= norm
        driveZ /= norm

        val closestOpp = allPlayers
            .filter { it.side != player.side }
            .minByOrNull { hypot(it.x - player.x, it.z - player.z) }
        if (closestOpp != null) {
            val odx = player.x - closestOpp.x
            val odz = player.z - closestOpp.z
            val od = hypot(odx, odz)
            if (od < 5.5f && od > 0.1f) {
                driveX += (odx / od) * 0.45f
                driveZ += (odz / od) * 0.65f
            }
        }

        val finalNorm = hypot(driveX, driveZ).coerceAtLeast(0.001f)
        return AiActionDecision(
            moveX = driveX / finalNorm,
            moveZ = driveZ / finalNorm,
            sprint = nearestOppDist > 4.5f && player.stamina > 0.35f && difficulty.reactionSpeed >= 0.85f
        )
    }

    private fun computeGoalkeeperDecision(
        gk: PlayerEntity, allPlayers: List<PlayerEntity>,
        ball: BallState,
        attackDir: Float,
        difficulty: AiDifficulty
    ): AiActionDecision {
        val ownGoalX = -PitchConstants.HALF_LENGTH * attackDir
        // If GK owns the ball, clear/pass forward immediately
        if (ball.ownerId == gk.id && gk.actionCooldown <= 0f) { val openMate = allPlayers.filter { it.id != gk.id && it.side == gk.side && (it.role == PlayerRole.DF || it.role == PlayerRole.MF) && hypot(it.x - gk.x, it.z - gk.z) <= 28f && allPlayers.none { opp -> opp.side != gk.side && hypot(opp.x - it.x, opp.z - it.z) < 6.5f } }.minByOrNull { hypot(it.x - gk.x, it.z - gk.z) }; if (openMate != null) { val mdx = openMate.x - gk.x; val mdz = openMate.z - gk.z; val md = hypot(mdx, mdz).coerceAtLeast(0.001f); return AiActionDecision(wantPass = true, kickPower = (md / 36f).coerceIn(0.28f, 0.52f), aimX = mdx / md, aimZ = mdz / md) }
            val aimZ = if (gk.z >= 0f) -0.35f else 0.35f
            val n = hypot(attackDir, aimZ)
            return AiActionDecision(
                wantPass = true,
                kickPower = 0.72f,
                aimX = attackDir / n,
                aimZ = aimZ / n
            )
        }

        // Check if a fast shot is incoming toward our goal mouth
        val movingTowardGoal = (ball.vx * (-attackDir)) > 5.5f
        val distToGoalLine = abs(ball.x - ownGoalX)
        if (movingTowardGoal && distToGoalLine < 24f && ball.ownerId == -1) {
            val timeToLine = abs((ownGoalX - ball.x) / ball.vx.coerceAtLeast(0.1f).let {
                if (abs(ball.vx) < 0.1f) 0.1f else ball.vx
            })
            if (timeToLine in 0.05f..1.35f) {
                val predictedZ = (ball.z + ball.vz * timeToLine).coerceIn(-PitchConstants.GOAL_HALF_WIDTH - 0.6f, PitchConstants.GOAL_HALF_WIDTH + 0.6f)
                val dz = predictedZ - gk.z
                if (abs(dz) > 0.55f && distToGoalLine < 13f && gk.actionCooldown <= 0f) {
                    return AiActionDecision(
                        moveX = 0f,
                        moveZ = sign(dz),
                        sprint = true,
                        gkDiveDir = sign(dz)
                    )
                }
                val dx = (ownGoalX + attackDir * 1.4f) - gk.x
                val d = hypot(dx, dz).coerceAtLeast(0.001f)
                return AiActionDecision(
                    moveX = dx / d,
                    moveZ = dz / d,
                    sprint = true
                )
            }
        }

        // Sweeper rush if loose ball is right inside the 6-yard/penalty area
        val distBallToGoal = hypot(ball.x - ownGoalX, ball.z)
        if (distBallToGoal < 11.5f && ball.ownerId == -1 && ball.y < 1.8f) {
            val dx = ball.x - gk.x
            val dz = ball.z - gk.z
            val d = hypot(dx, dz).coerceAtLeast(0.001f)
            return AiActionDecision(moveX = dx / d, moveZ = dz / d, sprint = true)
        }

        // Default goalkeeper positioning: bisect angle between ball and goal center
        val targetX = ownGoalX + attackDir * (if (distBallToGoal < 26f) 2.8f else 1.6f)
        val targetZ = (ball.z * 0.24f).coerceIn(-PitchConstants.GOAL_HALF_WIDTH * 0.72f, PitchConstants.GOAL_HALF_WIDTH * 0.72f)
        val dx = targetX - gk.x
        val dz = targetZ - gk.z
        val d = hypot(dx, dz)
        if (d < 0.35f) return AiActionDecision()
        val weight = (d / 2.2f).coerceIn(0.4f, 1.0f) * difficulty.gkReflex
        return AiActionDecision(
            moveX = (dx / d) * weight,
            moveZ = (dz / d) * weight,
            sprint = d > 2.2f
        )
    }

    private fun findBestPassTarget(
        passer: PlayerEntity,
        allPlayers: List<PlayerEntity>,
        attackDir: Float
    ): PlayerEntity? {
        var best: PlayerEntity? = null
        var bestScore = Float.NEGATIVE_INFINITY
        for (mate in allPlayers) {
            if (mate.side != passer.side || mate.id == passer.id || mate.role == PlayerRole.GK) continue
            val dx = mate.x - passer.x
            val dz = mate.z - passer.z
            val dist = hypot(dx, dz)
            if (dist < 5.5f || dist > 34f) continue

            val forwardProgress = dx * attackDir
            val oppNearTarget = allPlayers
                .filter { it.side != passer.side }
                .minOfOrNull { hypot(it.x - mate.x, it.z - mate.z) } ?: 15f

            val score = forwardProgress * 1.15f + oppNearTarget * 2.1f - abs(dist - 15f) * 0.45f
            if (score > bestScore) {
                bestScore = score
                best = mate
            }
        }
        return best
    }

    fun computeOffsideLineX(
        allPlayers: List<PlayerEntity>,
        attackingSide: TeamSide,
        attackDir: Float
    ): Float {
        val defendingX = allPlayers
            .filter { it.side != attackingSide }
            .map { it.x }
            .sortedBy { if (attackDir > 0f) -it else it }
        // Second-last defender (usually last outfield defender ahead of GK)
        return defendingX.getOrNull(1) ?: 0f
    }

    fun selectClosestChasers(
        allPlayers: List<PlayerEntity>,
        side: TeamSide,
        ball: BallState,
        excludeId: Int
    ): Pair<Int, Int> {
        val candidates = allPlayers
            .filter { it.side == side && it.role != PlayerRole.GK && it.id != excludeId }
            .sortedBy { hypot(it.x - ball.x, it.z - ball.z) }
        val first = candidates.getOrNull(0)?.id ?: -1
        val second = candidates.getOrNull(1)?.id ?: -1
        return first to second
    }

    fun yawFromVector(dx: Float, dz: Float): Float = atan2(dz, dx)
}
