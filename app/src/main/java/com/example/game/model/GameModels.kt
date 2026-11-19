package com.example.game.model

import androidx.compose.ui.graphics.Color

enum class TeamSide(val code: Int) {
    HOME(0),
    AWAY(1);

    fun opposite(): TeamSide = if (this == HOME) AWAY else HOME
}

enum class PlayerRole(val shortName: String) {
    GK("GK"),
    DF("DF"),
    MF("MF"),
    FW("FW")
}

enum class Formation(val displayName: String, val description: String) {
    F_4_3_3("4-3-3", "High-pressing wide attack"),
    F_4_4_2("4-4-2", "Balanced two-striker classic"),
    F_4_2_3_1("4-2-3-1", "Double pivot midfield control");

    /**
     * Normalized home coordinates where x in [-1, 0] is home half (goal at -1),
     * z in [-1, 1] is top-to-bottom touchline.
     */
    fun homeSlots(): List<FormationSlot> = when (this) {
        F_4_3_3 -> listOf(
            FormationSlot(1, PlayerRole.GK, -0.94f, 0.00f),
            FormationSlot(2, PlayerRole.DF, -0.66f, -0.66f),
            FormationSlot(4, PlayerRole.DF, -0.71f, -0.24f),
            FormationSlot(5, PlayerRole.DF, -0.71f, 0.24f),
            FormationSlot(3, PlayerRole.DF, -0.66f, 0.66f),
            FormationSlot(6, PlayerRole.MF, -0.40f, 0.00f),
            FormationSlot(8, PlayerRole.MF, -0.34f, -0.44f),
            FormationSlot(10, PlayerRole.MF, -0.34f, 0.44f),
            FormationSlot(7, PlayerRole.FW, -0.11f, -0.64f),
            FormationSlot(9, PlayerRole.FW, -0.07f, 0.00f),
            FormationSlot(11, PlayerRole.FW, -0.11f, 0.64f)
        )
        F_4_4_2 -> listOf(
            FormationSlot(1, PlayerRole.GK, -0.94f, 0.00f),
            FormationSlot(2, PlayerRole.DF, -0.68f, -0.66f),
            FormationSlot(4, PlayerRole.DF, -0.72f, -0.22f),
            FormationSlot(5, PlayerRole.DF, -0.72f, 0.22f),
            FormationSlot(3, PlayerRole.DF, -0.68f, 0.66f),
            FormationSlot(7, PlayerRole.MF, -0.36f, -0.68f),
            FormationSlot(6, PlayerRole.MF, -0.42f, -0.22f),
            FormationSlot(8, PlayerRole.MF, -0.42f, 0.22f),
            FormationSlot(11, PlayerRole.MF, -0.36f, 0.68f),
            FormationSlot(9, PlayerRole.FW, -0.10f, -0.24f),
            FormationSlot(10, PlayerRole.FW, -0.10f, 0.24f)
        )
        F_4_2_3_1 -> listOf(
            FormationSlot(1, PlayerRole.GK, -0.94f, 0.00f),
            FormationSlot(2, PlayerRole.DF, -0.68f, -0.66f),
            FormationSlot(4, PlayerRole.DF, -0.73f, -0.22f),
            FormationSlot(5, PlayerRole.DF, -0.73f, 0.22f),
            FormationSlot(3, PlayerRole.DF, -0.68f, 0.66f),
            FormationSlot(6, PlayerRole.MF, -0.48f, -0.26f),
            FormationSlot(8, PlayerRole.MF, -0.48f, 0.26f),
            FormationSlot(7, PlayerRole.MF, -0.24f, -0.62f),
            FormationSlot(10, PlayerRole.MF, -0.24f, 0.00f),
            FormationSlot(11, PlayerRole.MF, -0.24f, 0.62f),
            FormationSlot(9, PlayerRole.FW, -0.07f, 0.00f)
        )
    }
}

data class FormationSlot(
    val shirtNumber: Int,
    val role: PlayerRole,
    val normX: Float,
    val normZ: Float
)

enum class StadiumPreset(
    val displayName: String,
    val subtitle: String,
    val skyR: Float,
    val skyG: Float,
    val skyB: Float,
    val ambientStrength: Float,
    val sunX: Float,
    val sunY: Float,
    val sunZ: Float,
    val sunR: Float,
    val sunG: Float,
    val sunB: Float,
    val floodlightIntensity: Float,
    val grassTintR: Float,
    val grassTintG: Float,
    val grassTintB: Float
) {
    NIGHT_FLOODLIT(
        displayName = "Europa Night Arena",
        subtitle = "Floodlit night broadcast",
        skyR = 0.035f, skyG = 0.055f, skyB = 0.105f,
        ambientStrength = 0.42f,
        sunX = 0.25f, sunY = 0.92f, sunZ = 0.30f,
        sunR = 0.94f, sunG = 0.97f, sunB = 1.0f,
        floodlightIntensity = 1.0f,
        grassTintR = 0.10f, grassTintG = 0.47f, grassTintB = 0.19f
    ),
    GOLDEN_HOUR(
        displayName = "Solstice Dusk Park",
        subtitle = "Golden hour evening kickoff",
        skyR = 0.28f, skyG = 0.18f, skyB = 0.22f,
        ambientStrength = 0.48f,
        sunX = -0.65f, sunY = 0.45f, sunZ = 0.50f,
        sunR = 1.0f, sunG = 0.78f, sunB = 0.52f,
        floodlightIntensity = 0.55f,
        grassTintR = 0.14f, grassTintG = 0.48f, grassTintB = 0.16f
    ),
    DAY_CHAMPIONSHIP(
        displayName = "Sovereign Day Stadium",
        subtitle = "Crisp championship afternoon",
        skyR = 0.36f, skyG = 0.62f, skyB = 0.88f,
        ambientStrength = 0.56f,
        sunX = 0.35f, sunY = 0.88f, sunZ = 0.32f,
        sunR = 1.0f, sunG = 0.98f, sunB = 0.92f,
        floodlightIntensity = 0.15f,
        grassTintR = 0.13f, grassTintG = 0.54f, grassTintB = 0.20f
    )
}

enum class GraphicsQuality(val displayName: String, val renderStandDetails: Boolean, val renderShadows: Boolean, val renderNetLines: Boolean) {
    LOW("Low (Battery)", false, false, false),
    MEDIUM("Medium (Balanced)", true, true, false),
    HIGH("High (Broadcast)", true, true, true)
}

enum class CameraPreset(
    val displayName: String,
    val height: Float,
    val distance: Float,
    val fov: Float,
    val gantryFactor: Float
) {
    BROADCAST_DYNAMIC("eFootball Broadcast", 13.0f, 24.5f, 34f, 0.62f),
    BROADCAST_WIDE("Stadium Wide", 17.5f, 31.5f, 37f, 0.68f),
    CLOSE_PRO("Pitchside Duel", 9.8f, 18.5f, 32f, 0.72f)
}

enum class AiDifficulty(val displayName: String, val reactionSpeed: Float, val passAccuracy: Float, val gkReflex: Float) {
    AMATEUR("Amateur", 0.72f, 0.78f, 0.72f),
    PROFESSIONAL("Professional", 0.90f, 0.90f, 0.88f),
    LEGEND("Legend", 1.05f, 0.96f, 0.98f)
}

data class KitColors(
    val jerseyArgb: Long,
    val shortsArgb: Long,
    val accentArgb: Long,
    val gkArgb: Long
) {
    fun jerseyRgb(): FloatArray = argbToRgb(jerseyArgb)
    fun shortsRgb(): FloatArray = argbToRgb(shortsArgb)
    fun accentRgb(): FloatArray = argbToRgb(accentArgb)
    fun gkRgb(): FloatArray = argbToRgb(gkArgb)
    fun composePrimary(): Color = Color(jerseyArgb)
    fun composeAccent(): Color = Color(accentArgb)

    companion object {
        fun argbToRgb(argb: Long): FloatArray {
            val r = ((argb shr 16) and 0xFF) / 255f
            val g = ((argb shr 8) and 0xFF) / 255f
            val b = (argb and 0xFF) / 255f
            return floatArrayOf(r, g, b)
        }
    }
}

data class ClubTeam(
    val id: String,
    val name: String,
    val shortCode: String,
    val city: String,
    val attackRating: Int,
    val midfieldRating: Int,
    val defenseRating: Int,
    val kit: KitColors,
    val playerNames: List<String>
) {
    val overallRating: Int get() = (attackRating + midfieldRating + defenseRating) / 3
}

object ClubCatalog {
    val teams: List<ClubTeam> = listOf(
        ClubTeam(
            id = "valencia_nova",
            name = "FC Nova Madrid",
            shortCode = "NVM",
            city = "Madrid",
            attackRating = 88,
            midfieldRating = 87,
            defenseRating = 85,
            kit = KitColors(0xFFF3F6FA, 0xFFE2E8F0, 0xFFF5B700, 0xFF0EA5E9),
            playerNames = listOf("Cortez", "Alvarez", "Mendez", "Ramos", "Vega", "Serrano", "Navarro", "Blanco", "Ibarra", "Soler", "Morales")
        ),
        ClubTeam(
            id = "nordic_united",
            name = "Atletico Rosso",
            shortCode = "ATR",
            city = "Milan",
            attackRating = 86,
            midfieldRating = 85,
            defenseRating = 87,
            kit = KitColors(0xFFD92638, 0xFF141820, 0xFFFFFFFF, 0xFF22C55E),
            playerNames = listOf("Bellini", "Conti", "Moretti", "De Luca", "Gallo", "Ferrari", "Russo", "Greco", "Esposito", "Romano", "Marino")
        ),
        ClubTeam(
            id = "azure_city",
            name = "Azure City FC",
            shortCode = "AZC",
            city = "Manchester",
            attackRating = 89,
            midfieldRating = 88,
            defenseRating = 86,
            kit = KitColors(0xFF38BDF8, 0xFFFFFFFF, 0xFF0C4A6E, 0xFFF97316),
            playerNames = listOf("Sterling", "Walker", "Barker", "Houghton", "Shaw", "Fletcher", "Bennett", "Palmer", "Vance", "Keane", "Cole")
        ),
        ClubTeam(
            id = "emerald_sporting",
            name = "Sporting Verde",
            shortCode = "SPV",
            city = "Lisbon",
            attackRating = 84,
            midfieldRating = 85,
            defenseRating = 83,
            kit = KitColors(0xFF10B981, 0xFF0B1F16, 0xFFFDE047, 0xFF8B5CF6),
            playerNames = listOf("Silva", "Costa", "Pereira", "Ferreira", "Oliveira", "Mendes", "Rocha", "Sousa", "Teixeira", "Lopes", "Nunes")
        ),
        ClubTeam(
            id = "bavaria_gold",
            name = "Borussia Volt",
            shortCode = "BVT",
            city = "Dortmund",
            attackRating = 86,
            midfieldRating = 84,
            defenseRating = 83,
            kit = KitColors(0xFFFACC15, 0xFF111827, 0xFF111827, 0xFFEC4899),
            playerNames = listOf("Weber", "Keller", "Richter", "Brandt", "Vogel", "Schulz", "Bauer", "Hofmann", "Koch", "Wagner", "Becker")
        ),
        ClubTeam(
            id = "paris_royal",
            name = "Olympique Bleu",
            shortCode = "OLB",
            city = "Lyon",
            attackRating = 87,
            midfieldRating = 86,
            defenseRating = 84,
            kit = KitColors(0xFF1D4ED8, 0xFF1E3A8A, 0xFFEF4444, 0xFFEAB308),
            playerNames = listOf("Laurent", "Moreau", "Girard", "Dupont", "Mercier", "Blanc", "Roux", "Faure", "Chevalier", "Fontaine", "Perrin")
        )
    )

    fun findById(id: String): ClubTeam = teams.firstOrNull { it.id == id } ?: teams.first()
}

data class MatchConfig(
    val homeTeamId: String = ClubCatalog.teams[0].id,
    val awayTeamId: String = ClubCatalog.teams[1].id,
    val homeFormation: Formation = Formation.F_4_3_3,
    val awayFormation: Formation = Formation.F_4_4_2,
    val stadium: StadiumPreset = StadiumPreset.NIGHT_FLOODLIT,
    val matchMinutes: Int = 5,
    val difficulty: AiDifficulty = AiDifficulty.PROFESSIONAL,
    val graphicsQuality: GraphicsQuality = GraphicsQuality.HIGH,
    val cameraPreset: CameraPreset = CameraPreset.BROADCAST_DYNAMIC,
    val cameraZoom: Int = 5,
    val cameraHeight: Int = 5,
    val isLanMultiplayer: Boolean = false,
    val localControlledSide: TeamSide = TeamSide.HOME
) {
    val homeTeam: ClubTeam get() = ClubCatalog.findById(homeTeamId)
    val awayTeam: ClubTeam get() = ClubCatalog.findById(awayTeamId)
}

enum class AnimState(val code: Int) {
    IDLE(0),
    WALK(1),
    RUN(2),
    SPRINT(3),
    KICK(4),
    TACKLE(5),
    GK_DIVE(6),
    CELEBRATE(7);

    companion object {
        fun fromCode(code: Int): AnimState = entries.getOrElse(code) { IDLE }
    }
}

enum class MatchPhase(val code: Int, val bannerTitle: String) {
    KICKOFF(0, "KICK OFF"),
    PLAYING(1, ""),
    GOAL_CELEBRATION(2, "GOAL!"),
    THROW_IN(3, "THROW-IN"),
    CORNER_KICK(4, "CORNER KICK"),
    GOAL_KICK(5, "GOAL KICK"),
    FREE_KICK(6, "FREE KICK"),
    OFFSIDE(7, "OFFSIDE"),
    PENALTY(8, "PENALTY KICK"),
    HALF_TIME(9, "HALF TIME"),
    FULL_TIME(10, "FULL TIME");

    companion object {
        fun fromCode(code: Int): MatchPhase = entries.getOrElse(code) { PLAYING }
    }
}

data class PlayerInput(
    val seq: Int = 0,
    val moveX: Float = 0f,
    val moveY: Float = 0f,
    val sprint: Boolean = false,
    val passPressed: Boolean = false,
    val passCharge: Float = 0f,
    val throughPressed: Boolean = false,
    val shootPressed: Boolean = false,
    val shootCharge: Float = 0f,
    val tacklePressed: Boolean = false,
    val switchPressed: Boolean = false
)

data class BallState(
    var x: Float = 0f,
    var y: Float = 0.22f,
    var z: Float = 0f,
    var vx: Float = 0f,
    var vy: Float = 0f,
    var vz: Float = 0f,
    var spinX: Float = 0f,
    var spinY: Float = 0f,
    var spinZ: Float = 0f,
    var ownerId: Int = -1,
    var lastTouchSide: TeamSide = TeamSide.HOME,
    var lastTouchPlayerId: Int = -1
)

data class PlayerEntity(
    val id: Int,
    val side: TeamSide,
    val slotIndex: Int,
    val shirtNumber: Int,
    val name: String,
    val role: PlayerRole,
    val skinToneIndex: Int,
    val hairToneIndex: Int,
    val baseSpeed: Float,
    var x: Float = 0f,
    var y: Float = 0f,
    var z: Float = 0f,
    var vx: Float = 0f,
    var vz: Float = 0f,
    var facingYaw: Float = 0f,
    var stamina: Float = 1f,
    var animState: AnimState = AnimState.IDLE,
    var animTime: Float = 0f,
    var actionCooldown: Float = 0f,
    var tackleTimer: Float = 0f,
    var kickTimer: Float = 0f,
    var diveDir: Float = 0f
)

data class MatchStats(
    val homePossessionTicks: Int = 1,
    val awayPossessionTicks: Int = 1,
    val homeShots: Int = 0,
    val awayShots: Int = 0,
    val homeShotsOnTarget: Int = 0,
    val awayShotsOnTarget: Int = 0,
    val homePasses: Int = 0,
    val awayPasses: Int = 0,
    val homeTackles: Int = 0,
    val awayTackles: Int = 0,
    val homeFouls: Int = 0,
    val awayFouls: Int = 0
) {
    val homePossessionPct: Int
        get() {
            val total = (homePossessionTicks + awayPossessionTicks).coerceAtLeast(1)
            return ((homePossessionTicks * 100f) / total).toInt().coerceIn(0, 100)
        }
    val awayPossessionPct: Int get() = 100 - homePossessionPct
}

enum class SoundEvent {
    WHISTLE_SHORT,
    WHISTLE_FULL,
    KICK_PASS,
    KICK_SHOT,
    POST_HIT,
    TACKLE,
    GK_SAVE,
    GOAL_ROAR
}

data class MatchHudSnapshot(
    val homeScore: Int = 0,
    val awayScore: Int = 0,
    val matchClockMinutes: Int = 0,
    val matchClockSeconds: Int = 0,
    val half: Int = 1,
    val phase: MatchPhase = MatchPhase.KICKOFF,
    val bannerSubtitle: String = "",
    val activePlayerName: String = "",
    val activePlayerNumber: Int = 9,
    val activePlayerStamina: Float = 1f,
    val localTeamHasBall: Boolean = false,
    val shootCharge: Float = 0f,
    val passCharge: Float = 0f,
    val stats: MatchStats = MatchStats(),
    val radarBallX: Float = 0f,
    val radarBallZ: Float = 0f,
    val radarHomePlayers: List<Pair<Float, Float>> = emptyList(),
    val radarAwayPlayers: List<Pair<Float, Float>> = emptyList(),
    val controlledPlayerNorm: Pair<Float, Float> = 0f to 0f,
    val isPaused: Boolean = false
)
