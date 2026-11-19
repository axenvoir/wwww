package com.example.game.model

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "matchday_lan_settings")

data class UserSettings(
    val stadium: StadiumPreset = StadiumPreset.NIGHT_FLOODLIT,
    val graphicsQuality: GraphicsQuality = GraphicsQuality.HIGH,
    val cameraPreset: CameraPreset = CameraPreset.BROADCAST_DYNAMIC,
    val cameraZoom: Int = 5,
    val cameraHeight: Int = 5,
    val matchMinutes: Int = 5,
    val difficulty: AiDifficulty = AiDifficulty.PROFESSIONAL,
    val lastManualIp: String = "192.168.43.1",
    val soundEnabled: Boolean = true,
    val showPerfHud: Boolean = false
)

class SettingsRepository(private val context: Context) {
    private object Keys {
        val STADIUM = stringPreferencesKey("stadium")
        val GRAPHICS = stringPreferencesKey("graphics_quality")
        val CAMERA = stringPreferencesKey("camera_preset")
        val CAMERA_ZOOM = intPreferencesKey("camera_zoom")
        val CAMERA_HEIGHT = intPreferencesKey("camera_height")
        val MATCH_MINUTES = intPreferencesKey("match_minutes")
        val DIFFICULTY = stringPreferencesKey("ai_difficulty")
        val LAST_MANUAL_IP = stringPreferencesKey("last_manual_ip")
        val SOUND_ENABLED = booleanPreferencesKey("sound_enabled")
        val SHOW_PERF_HUD = booleanPreferencesKey("show_perf_hud")
    }

    val settingsFlow: Flow<UserSettings> = context.settingsDataStore.data.map { prefs ->
        UserSettings(
            stadium = prefs[Keys.STADIUM]?.let { runCatching { StadiumPreset.valueOf(it) }.getOrNull() }
                ?: StadiumPreset.NIGHT_FLOODLIT,
            graphicsQuality = prefs[Keys.GRAPHICS]?.let { runCatching { GraphicsQuality.valueOf(it) }.getOrNull() }
                ?: GraphicsQuality.HIGH,
            cameraPreset = prefs[Keys.CAMERA]?.let { runCatching { CameraPreset.valueOf(it) }.getOrNull() }
                ?: CameraPreset.BROADCAST_DYNAMIC,
            cameraZoom = (prefs[Keys.CAMERA_ZOOM] ?: 5).coerceIn(0, 10),
            cameraHeight = (prefs[Keys.CAMERA_HEIGHT] ?: 5).coerceIn(0, 10),
            matchMinutes = prefs[Keys.MATCH_MINUTES] ?: 5,
            difficulty = prefs[Keys.DIFFICULTY]?.let { runCatching { AiDifficulty.valueOf(it) }.getOrNull() }
                ?: AiDifficulty.PROFESSIONAL,
            lastManualIp = prefs[Keys.LAST_MANUAL_IP] ?: "192.168.43.1",
            soundEnabled = prefs[Keys.SOUND_ENABLED] ?: true,
            showPerfHud = prefs[Keys.SHOW_PERF_HUD] ?: false
        )
    }

    suspend fun updateStadium(preset: StadiumPreset) {
        context.settingsDataStore.edit { it[Keys.STADIUM] = preset.name }
    }

    suspend fun updateGraphicsQuality(quality: GraphicsQuality) {
        context.settingsDataStore.edit { it[Keys.GRAPHICS] = quality.name }
    }

    suspend fun updateCameraPreset(preset: CameraPreset) {
        context.settingsDataStore.edit { it[Keys.CAMERA] = preset.name }
    }

    suspend fun updateCameraZoom(zoom: Int) {
        context.settingsDataStore.edit { it[Keys.CAMERA_ZOOM] = zoom.coerceIn(0, 10) }
    }

    suspend fun updateCameraHeight(height: Int) {
        context.settingsDataStore.edit { it[Keys.CAMERA_HEIGHT] = height.coerceIn(0, 10) }
    }

    suspend fun updateMatchMinutes(minutes: Int) {
        context.settingsDataStore.edit { it[Keys.MATCH_MINUTES] = minutes }
    }

    suspend fun updateDifficulty(difficulty: AiDifficulty) {
        context.settingsDataStore.edit { it[Keys.DIFFICULTY] = difficulty.name }
    }

    suspend fun updateLastManualIp(ip: String) {
        context.settingsDataStore.edit { it[Keys.LAST_MANUAL_IP] = ip.trim() }
    }

    suspend fun updateSoundEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.SOUND_ENABLED] = enabled }
    }

    suspend fun updateShowPerfHud(show: Boolean) {
        context.settingsDataStore.edit { it[Keys.SHOW_PERF_HUD] = show }
    }
}
