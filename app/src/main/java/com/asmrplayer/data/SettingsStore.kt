package com.asmrplayer.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class AppSettings(
    val rootPath: String? = null,
    val scanOnStart: Boolean = true,
    val embedOnImport: Boolean = false,
    val autoScroll: Boolean = true,
    val scrollSpeed: Float = 1.0f,
    val scriptFontScale: Float = 1.0f,
    val themeMode: String = "system",
    val skinStyle: String = "deep_sea",
    val backgroundImage: String? = null,
    val backgroundPreset: String = "none",
    val cardAlpha: Float = 1.0f,
    val backgroundBlur: Float = 0.0f,
    val playerLayout: String = "new",
    val showPlaylist: Boolean = true,
    val playlistAutoScroll: Boolean = true,
    val lastTrackPath: String? = null,
    val lastPositionMs: Long = 0L,
)

private val Context.dataStore by preferencesDataStore(name = "asmr_settings")

class SettingsStore(private val context: Context) {

    private object Keys {
        val ROOT = stringPreferencesKey("root_path")
        val SCAN_ON_START = booleanPreferencesKey("scan_on_start")
        val EMBED_ON_IMPORT = booleanPreferencesKey("embed_on_import")
        val AUTO_SCROLL = booleanPreferencesKey("auto_scroll")
        val SCROLL_SPEED = floatPreferencesKey("scroll_speed")
        val FONT_SCALE = floatPreferencesKey("script_font_scale")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val SKIN_STYLE = stringPreferencesKey("skin_style")
        val BG_IMAGE = stringPreferencesKey("background_image")
        val BG_PRESET = stringPreferencesKey("background_preset")
        val CARD_ALPHA = floatPreferencesKey("card_alpha")
        val BG_BLUR = floatPreferencesKey("background_blur")
        val PLAYER_LAYOUT = stringPreferencesKey("player_layout")
        val SHOW_PLAYLIST = booleanPreferencesKey("show_playlist")
        val PLAYLIST_AUTO_SCROLL = booleanPreferencesKey("playlist_auto_scroll")
        val LAST_TRACK = stringPreferencesKey("last_track")
        val LAST_POS = longPreferencesKey("last_position")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            rootPath = p[Keys.ROOT],
            scanOnStart = p[Keys.SCAN_ON_START] ?: true,
            embedOnImport = p[Keys.EMBED_ON_IMPORT] ?: false,
            autoScroll = p[Keys.AUTO_SCROLL] ?: true,
            scrollSpeed = p[Keys.SCROLL_SPEED] ?: 1.0f,
            scriptFontScale = p[Keys.FONT_SCALE] ?: 1.0f,
            themeMode = p[Keys.THEME_MODE] ?: "system",
            skinStyle = p[Keys.SKIN_STYLE] ?: "deep_sea",
            backgroundImage = p[Keys.BG_IMAGE],
            backgroundPreset = p[Keys.BG_PRESET] ?: "none",
            cardAlpha = p[Keys.CARD_ALPHA] ?: 1.0f,
            backgroundBlur = p[Keys.BG_BLUR] ?: 0.0f,
            playerLayout = p[Keys.PLAYER_LAYOUT] ?: "new",
            showPlaylist = p[Keys.SHOW_PLAYLIST] ?: true,
            playlistAutoScroll = p[Keys.PLAYLIST_AUTO_SCROLL] ?: true,
            lastTrackPath = p[Keys.LAST_TRACK],
            lastPositionMs = p[Keys.LAST_POS] ?: 0L,
        )
    }

    suspend fun setRootPath(path: String?) = context.dataStore.edit { p ->
        if (path == null) p.remove(Keys.ROOT) else p[Keys.ROOT] = path
    }

    suspend fun setScanOnStart(value: Boolean) = context.dataStore.edit { it[Keys.SCAN_ON_START] = value }
    suspend fun setEmbedOnImport(value: Boolean) = context.dataStore.edit { it[Keys.EMBED_ON_IMPORT] = value }
    suspend fun setAutoScroll(value: Boolean) = context.dataStore.edit { it[Keys.AUTO_SCROLL] = value }
    suspend fun setScrollSpeed(value: Float) = context.dataStore.edit { it[Keys.SCROLL_SPEED] = value }
    suspend fun setFontScale(value: Float) = context.dataStore.edit { it[Keys.FONT_SCALE] = value }
    suspend fun setThemeMode(value: String) = context.dataStore.edit { it[Keys.THEME_MODE] = value }
    suspend fun setSkinStyle(value: String) = context.dataStore.edit { it[Keys.SKIN_STYLE] = value }
    suspend fun setShowPlaylist(value: Boolean) = context.dataStore.edit { it[Keys.SHOW_PLAYLIST] = value }
    suspend fun setPlaylistAutoScroll(value: Boolean) = context.dataStore.edit { it[Keys.PLAYLIST_AUTO_SCROLL] = value }

    suspend fun setBackgroundImage(path: String?) = context.dataStore.edit { p ->
        if (path == null) p.remove(Keys.BG_IMAGE) else p[Keys.BG_IMAGE] = path
    }

    suspend fun setBackgroundPreset(value: String) = context.dataStore.edit { it[Keys.BG_PRESET] = value }
    suspend fun setCardAlpha(value: Float) = context.dataStore.edit { it[Keys.CARD_ALPHA] = value }
    suspend fun setBackgroundBlur(value: Float) = context.dataStore.edit { it[Keys.BG_BLUR] = value }
    suspend fun setPlayerLayout(value: String) = context.dataStore.edit { it[Keys.PLAYER_LAYOUT] = value }

    suspend fun saveLastPlayed(path: String?, positionMs: Long) = context.dataStore.edit { p ->
        if (path == null) p.remove(Keys.LAST_TRACK) else p[Keys.LAST_TRACK] = path
        p[Keys.LAST_POS] = positionMs
    }
}
