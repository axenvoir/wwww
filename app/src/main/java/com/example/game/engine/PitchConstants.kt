package com.example.game.engine

object PitchConstants {
    const val LENGTH = 105f
    const val WIDTH = 68f
    const val HALF_LENGTH = LENGTH / 2f      // 52.5f
    const val HALF_WIDTH = WIDTH / 2f        // 34.0f

    const val PENALTY_AREA_DEPTH = 16.5f
    const val PENALTY_AREA_HALF_WIDTH = 20.16f
    const val GOAL_AREA_DEPTH = 5.5f
    const val GOAL_AREA_HALF_WIDTH = 9.16f
    const val PENALTY_SPOT_DIST = 11.0f
    const val CENTER_CIRCLE_RADIUS = 9.15f

    const val GOAL_HALF_WIDTH = 3.66f        // 7.32m wide
    const val GOAL_HEIGHT = 2.44f
    const val GOAL_DEPTH = 2.45f
    const val POST_RADIUS = 0.08f

    const val BALL_RADIUS = 0.22f
    const val PLAYER_RADIUS = 0.48f
    const val GRAVITY = 21.0f
    const val FIXED_DT = 1f / 60f
}
