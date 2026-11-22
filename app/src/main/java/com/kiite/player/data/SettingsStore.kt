package com.kiite.player.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable

@Serializable
data class AppSettings(
    val rootPath: String? = null,
    val scanOnStart: Boolean = true,
    val embedOnImport: Boolean = false,
    val autoScroll: Boolean = true,
    val scrollSpeed: Float = 1.0f,
    val scriptFontScale: Float = 1.0f,
    val themeMode: String = "light",
    val skinStyle: String = "kiite",
    val backgroundImage: String? = null,
    val backgroundPreset: String = "none",
    val cardAlpha: Float = 1.0f,
    val backgroundBlur: Float = 0.0f,
    val seenVersion: String? = null,
    val downloadDir: String? = null,
    val immersive: Boolean = false,
    val purchaseAsmrOnly: Boolean = true,
    val sortMode: String = "name",
    val sortAsc: Boolean = true,
    val accentColor: String = "default",
    val backgroundDim: Float = 0.70f,
    val playbackSpeed: Float = 1.0f,
    val playerLayout: String = "new",
    val showPlaylist: Boolean = true,
    val playlistAutoScroll: Boolean = true,
    val lastTrackPath: String? = null,
    val lastPositionMs: Long = 0L,
    /** 启动时自动检查到新版本也不弹窗（仍可在「关于」里手动检查）。 */
    val noUpdatePrompt: Boolean = false,
    /** 播放模式：sequence 顺序 / loop 列表循环 / shuffle 随机。 */
    val playMode: String = "sequence",
    /** 曲库作品显示方式：list 列表 / medium 中图标 / large 大图标。 */
    val projectLayout: String = "list",
    /** 作品详情页：new 新版（头图+简介，选集二级页）/ old 旧版（等同改动前的布局）。 */
    val detailLayout: String = "new",
    /** 上次的播放队列（换行分隔的绝对路径），空串表示没有。 */
    val playQueue: String = "",
    /** 上次播放到的那一首（绝对路径）。 */
    val playQueueCurrent: String = "",
    /** 视频手势引导是否已经展示过（只弹一次）。 */
    val videoGestureGuideSeen: Boolean = false,
    /** 实时毛玻璃（高斯模糊磨砂）。低端设备上可关闭以恢复满帧，关闭后磨砂栏退回半透明纯色。 */
    val liveBlur: Boolean = true,
    /**
     * 简化动效：把全局动效时长压到一半（`Motion.scale`）。
     * 系统层面把「动画时长缩放」设成 0 的人本来就看不到动画，这个开关是给
     * 「系统没关、但自己觉得切页花哨」的人准备的。
     */
    val simplifiedMotion: Boolean = false,
    /** 隐藏 R18（成人向）作品：从曲库列表过滤；明确筛选「成人」时仍显示。 */
    val hideR18: Boolean = true,
    /** 台本悬浮窗（桌面歌词）：播放时在桌面悬浮显示当前台本行。 */
    val lyricOverlay: Boolean = false,
    /** 台本悬浮窗字号倍率（0.7–1.8），供桌面控制条 A− / A+ 调节并持久化。 */
    val lyricScale: Float = 1.0f,
    /** 台本悬浮窗宽度（dp）：桌面右下角手柄拖拽缩放后持久化。 */
    val lyricWidth: Float = 240f,
    /** 台本悬浮窗正文区高度（dp），不含展开后的控制条。 */
    val lyricHeight: Float = 96f,
    /** 台本悬浮窗是否收成了桌面上的悬浮球。 */
    val lyricCollapsed: Boolean = false,
    /** 睡眠定时到点前的渐停时长（毫秒），0 表示到点硬停。 */
    val sleepFadeMs: Long = 10_000L,
    /** 音效总开关：关闭时效果器整体释放，输出与未开音效完全一致（不改源文件）。 */
    val eqEnabled: Boolean = false,
    /** 五段均衡增益（dB，逗号分隔）；空串 = 没存过（全零）。 */
    val eqGains: String = "",
    /** 低音增强强度 0–1000。 */
    val eqBass: Int = 0,
    /** 环绕声强度 0–1000。 */
    val eqVirtualizer: Int = 0,
    /** 响度增益（mB，0–3000）。 */
    val eqLoudness: Int = 0,
    /** 台本行高倍率（0.8–2.0，乘在基础行高上）。 */
    val scriptLineHeight: Float = 1.0f,
    /** 台本字体：default 系统默认 / serif 衬线 / mono 等宽。 */
    val scriptFontFamily: String = "default",
    /** 台本阅读主题：follow 跟随应用 / paper 纸张 / night 夜读。 */
    val scriptTheme: String = "follow",
    /**
     * 播放页上次退出时停在哪一档：1 半屏（折叠卡片）/ 2 全屏（展开）。
     * 点迷你播放条时回到这一档；落盘是为了杀进程重进也还在。
     */
    val playerStop: Int = 1,
    /** 首页「今日收听目标」的目标分钟数。 */
    val dailyGoalMinutes: Int = 30,
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
        val SEEN_VERSION = stringPreferencesKey("seen_version")
        val DOWNLOAD_DIR = stringPreferencesKey("download_dir")
        val IMMERSIVE = booleanPreferencesKey("immersive")
        val PURCHASE_ASMR_ONLY = booleanPreferencesKey("purchase_asmr_only")
        val SORT_MODE = stringPreferencesKey("sort_mode")
        val SORT_ASC = booleanPreferencesKey("sort_asc")
        val ACCENT_COLOR = stringPreferencesKey("accent_color")
        val BACKGROUND_DIM = floatPreferencesKey("background_dim")
        val PLAYBACK_SPEED = floatPreferencesKey("playback_speed")
        val PLAYER_LAYOUT = stringPreferencesKey("player_layout")
        val SHOW_PLAYLIST = booleanPreferencesKey("show_playlist")
        val PLAYLIST_AUTO_SCROLL = booleanPreferencesKey("playlist_auto_scroll")
        val LAST_TRACK = stringPreferencesKey("last_track")
        val LAST_POS = longPreferencesKey("last_position")
        val NO_UPDATE_PROMPT = booleanPreferencesKey("no_update_prompt")
        val PLAY_MODE = stringPreferencesKey("play_mode")
        val PROJECT_LAYOUT = stringPreferencesKey("project_layout")
        val DETAIL_LAYOUT = stringPreferencesKey("detail_layout")
        val PLAY_QUEUE = stringPreferencesKey("play_queue")
        val PLAY_QUEUE_CURRENT = stringPreferencesKey("play_queue_current")
        val VIDEO_GESTURE_GUIDE = booleanPreferencesKey("video_gesture_guide_seen")
        val LIVE_BLUR = booleanPreferencesKey("live_blur")
        val SIMPLIFIED_MOTION = booleanPreferencesKey("simplified_motion")
        val HIDE_R18 = booleanPreferencesKey("hide_r18")
        val LYRIC_OVERLAY = booleanPreferencesKey("lyric_overlay")
        val LYRIC_SCALE = floatPreferencesKey("lyric_scale")
        val LYRIC_WIDTH = floatPreferencesKey("lyric_width")
        val LYRIC_HEIGHT = floatPreferencesKey("lyric_height")
        val LYRIC_COLLAPSED = booleanPreferencesKey("lyric_collapsed")
        val SLEEP_FADE_MS = longPreferencesKey("sleep_fade_ms")
        val EQ_ENABLED = booleanPreferencesKey("eq_enabled")
        val EQ_GAINS = stringPreferencesKey("eq_gains")
        val EQ_BASS = intPreferencesKey("eq_bass")
        val EQ_VIRTUALIZER = intPreferencesKey("eq_virtualizer")
        val EQ_LOUDNESS = intPreferencesKey("eq_loudness")
        val SCRIPT_LINE_HEIGHT = floatPreferencesKey("script_line_height")
        val SCRIPT_FONT_FAMILY = stringPreferencesKey("script_font_family")
        val SCRIPT_THEME = stringPreferencesKey("script_theme")
        val PLAYER_STOP = intPreferencesKey("player_stop")
        val DAILY_GOAL = intPreferencesKey("daily_goal_minutes")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            rootPath = p[Keys.ROOT],
            scanOnStart = p[Keys.SCAN_ON_START] ?: true,
            embedOnImport = p[Keys.EMBED_ON_IMPORT] ?: false,
            autoScroll = p[Keys.AUTO_SCROLL] ?: true,
            scrollSpeed = p[Keys.SCROLL_SPEED] ?: 1.0f,
            scriptFontScale = p[Keys.FONT_SCALE] ?: 1.0f,
            themeMode = p[Keys.THEME_MODE] ?: "light",
            skinStyle = p[Keys.SKIN_STYLE] ?: "kiite",
            backgroundImage = p[Keys.BG_IMAGE],
            backgroundPreset = p[Keys.BG_PRESET] ?: "none",
            cardAlpha = p[Keys.CARD_ALPHA] ?: 1.0f,
            backgroundBlur = p[Keys.BG_BLUR] ?: 0.0f,
            seenVersion = p[Keys.SEEN_VERSION],
            downloadDir = p[Keys.DOWNLOAD_DIR],
            immersive = p[Keys.IMMERSIVE] ?: false,
            purchaseAsmrOnly = p[Keys.PURCHASE_ASMR_ONLY] ?: true,
            sortMode = p[Keys.SORT_MODE] ?: "name",
            sortAsc = p[Keys.SORT_ASC] ?: true,
            accentColor = p[Keys.ACCENT_COLOR] ?: "default",
            backgroundDim = p[Keys.BACKGROUND_DIM] ?: 0.70f,
            playbackSpeed = p[Keys.PLAYBACK_SPEED] ?: 1.0f,
            playerLayout = p[Keys.PLAYER_LAYOUT] ?: "new",
            showPlaylist = p[Keys.SHOW_PLAYLIST] ?: true,
            playlistAutoScroll = p[Keys.PLAYLIST_AUTO_SCROLL] ?: true,
            lastTrackPath = p[Keys.LAST_TRACK],
            lastPositionMs = p[Keys.LAST_POS] ?: 0L,
            noUpdatePrompt = p[Keys.NO_UPDATE_PROMPT] ?: false,
            playMode = p[Keys.PLAY_MODE] ?: "sequence",
            projectLayout = p[Keys.PROJECT_LAYOUT] ?: "list",
            detailLayout = p[Keys.DETAIL_LAYOUT] ?: "new",
            playQueue = p[Keys.PLAY_QUEUE] ?: "",
            playQueueCurrent = p[Keys.PLAY_QUEUE_CURRENT] ?: "",
            videoGestureGuideSeen = p[Keys.VIDEO_GESTURE_GUIDE] ?: false,
            liveBlur = p[Keys.LIVE_BLUR] ?: true,
            simplifiedMotion = p[Keys.SIMPLIFIED_MOTION] ?: false,
            hideR18 = p[Keys.HIDE_R18] ?: true,
            lyricOverlay = p[Keys.LYRIC_OVERLAY] ?: false,
            lyricScale = p[Keys.LYRIC_SCALE] ?: 1.0f,
            lyricWidth = p[Keys.LYRIC_WIDTH] ?: 240f,
            lyricHeight = p[Keys.LYRIC_HEIGHT] ?: 96f,
            lyricCollapsed = p[Keys.LYRIC_COLLAPSED] ?: false,
            sleepFadeMs = p[Keys.SLEEP_FADE_MS] ?: 10_000L,
            eqEnabled = p[Keys.EQ_ENABLED] ?: false,
            eqGains = p[Keys.EQ_GAINS] ?: "",
            eqBass = p[Keys.EQ_BASS] ?: 0,
            eqVirtualizer = p[Keys.EQ_VIRTUALIZER] ?: 0,
            eqLoudness = p[Keys.EQ_LOUDNESS] ?: 0,
            scriptLineHeight = p[Keys.SCRIPT_LINE_HEIGHT] ?: 1.0f,
            scriptFontFamily = p[Keys.SCRIPT_FONT_FAMILY] ?: "default",
            scriptTheme = p[Keys.SCRIPT_THEME] ?: "follow",
            playerStop = ((p[Keys.PLAYER_STOP] ?: 1)).let { if (it == 2) 2 else 1 },
            dailyGoalMinutes = p[Keys.DAILY_GOAL] ?: 30,
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
    suspend fun setPlaybackSpeed(value: Float) = context.dataStore.edit { it[Keys.PLAYBACK_SPEED] = value }
    suspend fun setAccentColor(value: String) = context.dataStore.edit { it[Keys.ACCENT_COLOR] = value }
    suspend fun setSeenVersion(value: String) = context.dataStore.edit { it[Keys.SEEN_VERSION] = value }

    suspend fun setNoUpdatePrompt(value: Boolean) = context.dataStore.edit { it[Keys.NO_UPDATE_PROMPT] = value }

    suspend fun setPlayMode(value: String) = context.dataStore.edit { it[Keys.PLAY_MODE] = value }

    suspend fun setProjectLayout(value: String) = context.dataStore.edit { it[Keys.PROJECT_LAYOUT] = value }

    suspend fun setDetailLayout(value: String) = context.dataStore.edit { it[Keys.DETAIL_LAYOUT] = value }

    /** 保存播放队列（换行分隔路径 + 当前曲目），下次启动恢复。 */
    suspend fun setPlayQueue(paths: List<String>, current: String?) {
        context.dataStore.edit {
            it[Keys.PLAY_QUEUE] = paths.joinToString("\n")
            it[Keys.PLAY_QUEUE_CURRENT] = current.orEmpty()
        }
    }

    suspend fun setImmersive(value: Boolean) = context.dataStore.edit { it[Keys.IMMERSIVE] = value }
    suspend fun setPurchaseAsmrOnly(value: Boolean) = context.dataStore.edit { it[Keys.PURCHASE_ASMR_ONLY] = value }
    suspend fun setSortMode(value: String) = context.dataStore.edit { it[Keys.SORT_MODE] = value }
    suspend fun setSortAsc(value: Boolean) = context.dataStore.edit { it[Keys.SORT_ASC] = value }

    suspend fun setDownloadDir(value: String?) = context.dataStore.edit { p ->
        if (value == null) p.remove(Keys.DOWNLOAD_DIR) else p[Keys.DOWNLOAD_DIR] = value
    }
    suspend fun setBackgroundDim(value: Float) = context.dataStore.edit { it[Keys.BACKGROUND_DIM] = value }
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

    suspend fun setVideoGestureGuideSeen(value: Boolean) =
        context.dataStore.edit { it[Keys.VIDEO_GESTURE_GUIDE] = value }

    suspend fun setLiveBlur(value: Boolean) = context.dataStore.edit { it[Keys.LIVE_BLUR] = value }

    suspend fun setSimplifiedMotion(value: Boolean) =
        context.dataStore.edit { it[Keys.SIMPLIFIED_MOTION] = value }

    suspend fun setHideR18(value: Boolean) = context.dataStore.edit { it[Keys.HIDE_R18] = value }

    suspend fun setLyricOverlay(value: Boolean) = context.dataStore.edit { it[Keys.LYRIC_OVERLAY] = value }

    suspend fun setLyricScale(value: Float) = context.dataStore.edit { it[Keys.LYRIC_SCALE] = value }

    suspend fun setLyricSize(widthDp: Float, heightDp: Float) = context.dataStore.edit {
        it[Keys.LYRIC_WIDTH] = widthDp
        it[Keys.LYRIC_HEIGHT] = heightDp
    }

    suspend fun setLyricCollapsed(value: Boolean) =
        context.dataStore.edit { it[Keys.LYRIC_COLLAPSED] = value }

    suspend fun setSleepFadeMs(value: Long) = context.dataStore.edit { it[Keys.SLEEP_FADE_MS] = value }

    suspend fun setEqEnabled(value: Boolean) = context.dataStore.edit { it[Keys.EQ_ENABLED] = value }
    suspend fun setEqGains(value: String) = context.dataStore.edit { it[Keys.EQ_GAINS] = value }
    suspend fun setEqBass(value: Int) = context.dataStore.edit { it[Keys.EQ_BASS] = value }
    suspend fun setEqVirtualizer(value: Int) = context.dataStore.edit { it[Keys.EQ_VIRTUALIZER] = value }
    suspend fun setEqLoudness(value: Int) = context.dataStore.edit { it[Keys.EQ_LOUDNESS] = value }

    suspend fun setScriptLineHeight(value: Float) = context.dataStore.edit { it[Keys.SCRIPT_LINE_HEIGHT] = value }
    suspend fun setScriptFontFamily(value: String) = context.dataStore.edit { it[Keys.SCRIPT_FONT_FAMILY] = value }
    suspend fun setScriptTheme(value: String) = context.dataStore.edit { it[Keys.SCRIPT_THEME] = value }

    /** 记录播放页退出时的落点（1 半屏 / 2 全屏），点迷你条时按它还原。 */
    suspend fun setPlayerStop(value: Int) =
        context.dataStore.edit { it[Keys.PLAYER_STOP] = if (value == 2) 2 else 1 }

    suspend fun setDailyGoalMinutes(value: Int) =
        context.dataStore.edit { it[Keys.DAILY_GOAL] = value.coerceIn(5, 720) }

    /**
     * 备份恢复用：把一整份 [AppSettings] 写回 DataStore。
     * 逐键覆盖，没写入的键会保留原值 —— 老备份缺新字段时不会把新功能清掉。
     */
    suspend fun overwriteAll(value: AppSettings) = context.dataStore.edit { p ->
        value.rootPath?.let { p[Keys.ROOT] = it } ?: p.remove(Keys.ROOT)
        p[Keys.SCAN_ON_START] = value.scanOnStart
        p[Keys.EMBED_ON_IMPORT] = value.embedOnImport
        p[Keys.AUTO_SCROLL] = value.autoScroll
        p[Keys.SCROLL_SPEED] = value.scrollSpeed
        p[Keys.FONT_SCALE] = value.scriptFontScale
        p[Keys.THEME_MODE] = value.themeMode
        p[Keys.SKIN_STYLE] = value.skinStyle
        value.backgroundImage?.let { p[Keys.BG_IMAGE] = it } ?: p.remove(Keys.BG_IMAGE)
        p[Keys.BG_PRESET] = value.backgroundPreset
        p[Keys.CARD_ALPHA] = value.cardAlpha
        p[Keys.BG_BLUR] = value.backgroundBlur
        value.seenVersion?.let { p[Keys.SEEN_VERSION] = it } ?: p.remove(Keys.SEEN_VERSION)
        value.downloadDir?.let { p[Keys.DOWNLOAD_DIR] = it } ?: p.remove(Keys.DOWNLOAD_DIR)
        p[Keys.IMMERSIVE] = value.immersive
        p[Keys.PURCHASE_ASMR_ONLY] = value.purchaseAsmrOnly
        p[Keys.SORT_MODE] = value.sortMode
        p[Keys.SORT_ASC] = value.sortAsc
        p[Keys.ACCENT_COLOR] = value.accentColor
        p[Keys.BACKGROUND_DIM] = value.backgroundDim
        p[Keys.PLAYBACK_SPEED] = value.playbackSpeed
        p[Keys.PLAYER_LAYOUT] = value.playerLayout
        p[Keys.SHOW_PLAYLIST] = value.showPlaylist
        p[Keys.PLAYLIST_AUTO_SCROLL] = value.playlistAutoScroll
        value.lastTrackPath?.let { p[Keys.LAST_TRACK] = it } ?: p.remove(Keys.LAST_TRACK)
        p[Keys.LAST_POS] = value.lastPositionMs
        p[Keys.NO_UPDATE_PROMPT] = value.noUpdatePrompt
        p[Keys.PLAY_MODE] = value.playMode
        p[Keys.PROJECT_LAYOUT] = value.projectLayout
        p[Keys.DETAIL_LAYOUT] = value.detailLayout
        p[Keys.PLAY_QUEUE] = value.playQueue
        p[Keys.PLAY_QUEUE_CURRENT] = value.playQueueCurrent
        p[Keys.VIDEO_GESTURE_GUIDE] = value.videoGestureGuideSeen
        p[Keys.LIVE_BLUR] = value.liveBlur
        p[Keys.SIMPLIFIED_MOTION] = value.simplifiedMotion
        p[Keys.HIDE_R18] = value.hideR18
        p[Keys.LYRIC_OVERLAY] = value.lyricOverlay
        p[Keys.LYRIC_SCALE] = value.lyricScale
        p[Keys.LYRIC_WIDTH] = value.lyricWidth
        p[Keys.LYRIC_HEIGHT] = value.lyricHeight
        p[Keys.LYRIC_COLLAPSED] = value.lyricCollapsed
        p[Keys.SLEEP_FADE_MS] = value.sleepFadeMs
        p[Keys.EQ_ENABLED] = value.eqEnabled
        p[Keys.EQ_GAINS] = value.eqGains
        p[Keys.EQ_BASS] = value.eqBass
        p[Keys.EQ_VIRTUALIZER] = value.eqVirtualizer
        p[Keys.EQ_LOUDNESS] = value.eqLoudness
        p[Keys.SCRIPT_LINE_HEIGHT] = value.scriptLineHeight
        p[Keys.SCRIPT_FONT_FAMILY] = value.scriptFontFamily
        p[Keys.SCRIPT_THEME] = value.scriptTheme
        p[Keys.PLAYER_STOP] = if (value.playerStop == 2) 2 else 1
        p[Keys.DAILY_GOAL] = value.dailyGoalMinutes.coerceIn(5, 720)
    }
}
