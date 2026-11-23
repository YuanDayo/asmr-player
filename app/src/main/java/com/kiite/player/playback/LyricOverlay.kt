package com.kiite.player.playback

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.provider.Settings
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.kiite.player.R
import com.kiite.player.core.OverlayGeometry
import com.kiite.player.core.ParsedScript
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * 台本悬浮窗（桌面歌词）：播放时把当前台本行投到桌面上。
 *
 * 用应用上下文直接挂 WindowManager（TYPE_APPLICATION_OVERLAY），不需要单独 Service ——
 * 播放本身由前台 Service 保活进程，悬浮窗随进程存活。需要「显示在其他应用上层」权限，
 * 未授权时 show() 静默无效（设置页 / 播放页在开启前会先引导授权）。
 *
 * 交互：
 * - 按住文本区拖动 = 移动窗口，松手时贴边吸附；拖动与恢复都经 [OverlayGeometry.clamp]，
 *   永远落在可视区（刘海/状态栏以下），不会「拖到屏幕外就找不回来」；
 * - 轻点文本区 = 展开/收起控制条（A− / 百分比 / A+ / 回 App / 收成球 / 关闭），6 秒无操作自动收起；
 * - **双击文本区 = 回到 App**（浮窗在桌面时不用再去切后台）；
 * - 右下角 ◢ 手柄拖动 = 直接调整窗口大小（宽高同时可调，字号不变，能显示更多字）；
 * - 控制条上的「球」= 收成悬浮球：桌面上只留一个小圆点，点一下立刻展开回歌词；
 * - 关闭 = 摘掉窗口并回调 [onRequestClose]（由 ViewModel 把设置关掉，免得再自己挂回来）；
 * - 字号 / 尺寸 / 收起状态 / 位置分别通过 [onScaleChange] / [onSizeChange] /
 *   [onCollapsedChange] / [onPositionChange] 持久化，启动时由宿主灌回来。
 *
 * 内容：
 * - 顶部一行小字显示曲名（台本解析结果里没有章节结构，这一行就是「这是哪一部」）；
 * - 右侧显示当前句还剩几秒，底部一道 2dp 细进度条跟着走；
 * - 没有时间轴台本时不再是「一行空白」：显示曲名 + 一句说明。
 */
class LyricOverlayController(context: Context) {

    companion object {
        private const val MIN_SCALE = 0.7f
        private const val MAX_SCALE = 1.8f
        private const val STEP = 0.1f
        private const val BASE_CURRENT_SP = 15f
        private const val BASE_NEXT_SP = 12f
        private const val BASE_META_SP = 10f
        private const val CONTROLS_TIMEOUT_MS = 6000L
        private const val MIN_WIDTH_DP = 150f
        private const val MIN_HEIGHT_DP = 52f
        private const val DEFAULT_WIDTH_DP = 240f
        private const val DEFAULT_HEIGHT_DP = 96f
        private const val CONTROLS_HEIGHT_DP = 56
        private const val HANDLE_DP = 26
        private const val BALL_DP = 46
        /** 顶部小标那一行占的高度（dp），算正文可用行数时要扣掉。 */
        private const val META_ROW_DP = 20
        private const val PROGRESS_DP = 2
        /** 松手时离左右边缘多近就吸过去（dp）。 */
        private const val SNAP_DP = 10
    }

    private val appContext = context.applicationContext
    private val wm = appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val density = appContext.resources.displayMetrics.density

    private var params: WindowManager.LayoutParams? = null
    /** 当前挂在窗口上的 View（展开的歌词框，或收起后的悬浮球）。 */
    private var attached: View? = null

    private var expanded: FrameLayout? = null
    private var ball: View? = null
    private var textCurrent: TextView? = null
    private var textNext: TextView? = null
    private var metaRow: LinearLayout? = null
    private var textMeta: TextView? = null
    private var textRemain: TextView? = null
    private var progressTrack: View? = null
    private var progressFill: View? = null
    private var controlsBox: View? = null
    private var labelScale: TextView? = null

    /** 底色/描边需要跟着主题改，得留着引用（视图是复用的，不重建）。 */
    private var frameBg: GradientDrawable? = null
    private var ballBg: GradientDrawable? = null
    /** 跟随主题着色的文字：主句 / 次句 / 控制条 / 手柄。 */
    private val tintPrimary = ArrayList<TextView>()
    private val tintSecondary = ArrayList<TextView>()
    private val tintControl = ArrayList<TextView>()
    private val tintFaint = ArrayList<TextView>()

    private var darkTheme: Boolean = true

    private var lastShownLine: Int = -2
    private var lastFallback: String? = null
    // 最近一次 update 的入参：show() 挂上窗口后立即重放，避免窗口先空着等下一个 tick
    private var lastScript: ParsedScript? = null
    private var lastPos: Long = 0L
    private var lastTitle: String? = null
    /** 上一次算出来的进度（0..1，-1 = 未知），只有变化超过 1% 才重新布宽度。 */
    private var progressFrac: Float = -1f
    /** 上一次看到的屏幕尺寸：横竖屏切换 / 改分辨率后用它发现「该重新钳制了」。 */
    private var lastScreenW: Int = 0
    private var lastScreenH: Int = 0
    /** 可用区域（px）缓存：拖动时每帧都要钳制，不能每帧去查 currentWindowMetrics。 */
    private var areaW: Int = 0
    private var areaH: Int = 0

    private var scale: Float = 1.0f
    /** 正文区宽高（不含展开后的控制条）。 */
    private var widthPx: Int = 0
    private var heightPx: Int = 0
    private var collapsed: Boolean = false
    /** 窗口位置：收起/展开切换时窗口要重新 addView，位置得自己记住。 */
    private var posX: Int = Int.MIN_VALUE
    private var posY: Int = Int.MIN_VALUE

    /** 用户在浮窗上点了「关闭」：由宿主把 lyricOverlay 设置关掉。 */
    var onRequestClose: (() -> Unit)? = null

    /** 用户调了字号：由宿主持久化（0.7–1.8）。 */
    var onScaleChange: ((Float) -> Unit)? = null

    /** 用户拖拽缩放了窗口：由宿主持久化（dp）。 */
    var onSizeChange: ((Float, Float) -> Unit)? = null

    /** 用户把窗口收成了悬浮球（或从球展开）：由宿主持久化。 */
    var onCollapsedChange: ((Boolean) -> Unit)? = null

    /** 用户拖动完窗口：由宿主持久化（dp，相对屏幕左上角）。 */
    var onPositionChange: ((Int, Int) -> Unit)? = null

    /** 用户双击浮窗想回 App：由宿主把 Activity 拉到前台。 */
    var onRequestOpenApp: (() -> Unit)? = null

    val shown: Boolean get() = attached != null

    init {
        widthPx = dp(DEFAULT_WIDTH_DP.roundToInt())
        heightPx = dp(DEFAULT_HEIGHT_DP.roundToInt())
        posX = dp(24)
        posY = dp(160)
    }

    fun show() {
        if (attached != null) return
        if (!Settings.canDrawOverlays(appContext)) {
            android.util.Log.w("KiiteLyric", "show(): 没有「显示在其他应用上层」权限")
            return
        }
        if (params == null) {
            params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                // 给窗口起个名字：dumpsys window 里就能看到它，调试位置/尺寸时不用猜
                title = "KiiteLyricOverlay"
                x = posX
                y = posY
            }
        }
        // 先按设置里的尺寸/字号准备好，再挂窗口，避免先闪一下默认大小
        if (collapsed) attachBall() else attachExpanded()
        applyTextScale()
        update(lastScript, lastPos, lastTitle)
        // 浮窗位置没有任何界面能显示（连 uiautomator 都看不到它），出问题时只能靠日志
        if (areaW <= 0 || areaH <= 0) refreshArea()
        android.util.Log.i(
            "KiiteLyric",
            "show px=$posX,$posY size=${windowWidthPx()}x${windowHeightPx()} " +
                "area=${areaW}x${areaH} collapsed=$collapsed",
        )
    }

    fun hide() {
        val v = attached
        attached = null
        if (v != null) runCatching { wm.removeView(v) }
        params = null
        lastShownLine = -2
        lastFallback = null
        // 进度条下次重新挂窗时要从头量宽度
        progressFrac = -1f
        // 视图实例留着复用（下次 show 直接 addView），只把控制条收起来
        controlsBox?.visibility = View.GONE
    }

    /** 设置里读出 / A± 手动调的字号倍率（不含随窗口大小的缩放）。 */
    fun setScale(value: Float) {
        val clamped = value.coerceIn(MIN_SCALE, MAX_SCALE)
        if (abs(clamped - scale) < 0.001f) return
        scale = clamped
        applyTextScale()
    }

    /** 应用窗口尺寸（dp）：拖拽手柄松手后 / 启动时从设置读出。 */
    fun setSize(widthDp: Float, heightDp: Float) {
        val w = dp(max(MIN_WIDTH_DP, widthDp).roundToInt())
        val h = dp(max(MIN_HEIGHT_DP, heightDp).roundToInt())
        if (w == widthPx && h == heightPx) return
        widthPx = w
        heightPx = h
        // 字号跟着窗口宽度走，宽度变了字号也要重算
        applyTextScale()
        // 窗口变大后右边缘可能被顶出屏幕：位置得跟着重新钳制
        applyClampAndLayout()
    }

    /**
     * 恢复上次停放的位置（dp）。负值 = 没存过，保持现在的默认位置。
     *
     * 会先钳制再写：存的是旧屏幕的坐标（换过分辨率、转过屏），直接套上去可能已经越界。
     */
    fun setPosition(xDp: Int, yDp: Int) {
        if (xDp < 0 || yDp < 0) return
        val target = clampPosition(dp(xDp), dp(yDp))
        if (target.x == posX && target.y == posY) return
        posX = target.x
        posY = target.y
        pushPosition()
    }

    /**
     * 外观跟随 App 的深浅色。
     *
     * 深色下与原来的「深灰底白字」完全一致（editorial 皮肤不会因此变样）；
     * 浅色下换成浅底深字 —— 桌面歌词是盖在别人界面上的，浅色主题里还挂一块黑玻璃最扎眼。
     */
    fun setAppearance(dark: Boolean, skinStyle: String) {
        if (darkTheme == dark) return
        darkTheme = dark
        applyAppearance()
    }

    /**
     * 跟窗口宽度成比例的字号缩放：默认宽 240dp 时是 1.0×，
     * 拖到 360dp ≈ 1.5×、拖到 180dp ≈ 0.75×。这样「拖大 = 字更大」符合直觉。
     * 夹在 0.55–2.6 之间，避免极端尺寸下字小到看不清或大到溢出。
     */
    private fun sizeScale(): Float =
        (widthPx / (DEFAULT_WIDTH_DP * density)).coerceIn(0.55f, 2.6f)

    /** 实际生效倍率 = 手动倍率 × 窗口尺寸倍率。 */
    private fun effectiveScale(): Float = (scale * sizeScale()).coerceIn(0.5f, 3.2f)

    /** 收起为悬浮球 / 从球展开。 */
    fun setCollapsed(value: Boolean) {
        if (collapsed == value) return
        collapsed = value
        if (params != null) switchMode()
    }

    private fun applyTextScale() {
        val eff = effectiveScale()
        textCurrent?.textSize = BASE_CURRENT_SP * eff
        textNext?.textSize = BASE_NEXT_SP * eff
        textMeta?.textSize = BASE_META_SP * eff.coerceAtMost(1.4f)
        textRemain?.textSize = BASE_META_SP * eff.coerceAtMost(1.4f)
        // 控制条上的百分比显示的是「手动倍率」（A± 的值），尺寸带来的缩放不混进来，免得看不懂
        labelScale?.text = (scale * 100).roundToInt().toString() + "%"
        applyTextMetrics()
    }

    /**
     * 窗口是固定尺寸的：按「可用高度 ÷ 行高」算正文最多能放几行，
     * 超过就省略号截断，不会出现文字被窗口边缘硬切一半的情况。
     * 字号随窗口缩放后行高变了，所以每次改尺寸/字号都要重算。
     */
    private fun applyTextMetrics() {
        val cur = textCurrent ?: return
        val next = textNext ?: return
        val eff = effectiveScale()
        // 顶部小标那一行不是正文，得从可用高度里扣掉
        val metaH = if (metaRow?.visibility == View.VISIBLE) dp(META_ROW_DP) else 0
        val avail = heightPx - dp(9 + 8) - metaH
        val lineH = (BASE_CURRENT_SP * eff * density * 1.4f).coerceAtLeast(1f)
        // 下一句要占几行：中文按「一个字符 ≈ 一个字号宽」粗估，最多 2 行
        val nextText = if (next.visibility == View.VISIBLE) next.text?.toString().orEmpty() else ""
        val nextLines = if (nextText.isEmpty()) {
            0
        } else {
            val charW = (BASE_NEXT_SP * eff * density).coerceAtLeast(1f)
            val perLine = ((widthPx - dp(26)) / charW).toInt().coerceAtLeast(1)
            ((nextText.length + perLine - 1) / perLine).coerceIn(1, 2)
        }
        val nextH = nextLines * BASE_NEXT_SP * eff * density * 1.4f
        cur.maxLines = ((avail - nextH) / lineH).toInt().coerceIn(1, 12)
    }

    private fun controlsVisible(): Boolean = controlsBox?.visibility == View.VISIBLE

    private fun windowHeight(): Int =
        heightPx + if (controlsVisible()) dp(CONTROLS_HEIGHT_DP) else 0

    /** 当前挂在屏幕上的那个窗口的宽（收起时是球，展开时是正文宽度）。 */
    private fun windowWidthPx(): Int = if (collapsed) dp(BALL_DP) else widthPx

    private fun windowHeightPx(): Int = if (collapsed) dp(BALL_DP) else windowHeight()

    /** 把当前尺寸/位置写回窗口（尺寸或控制条变化后调用）。 */
    private fun relayout() {
        val p = params ?: return
        val v = attached ?: return
        if (collapsed) return
        p.width = widthPx
        p.height = windowHeight()
        runCatching { wm.updateViewLayout(v, p) }
    }

    // ---------- 位置：钳制 / 吸附 / 落盘 ----------

    /**
     * 顶部安全区：状态栏高度与刘海取较大者。
     *
     * 悬浮窗压不进状态栏（系统栏永远在它上面），所以那一条高度的位置是「看得见但摸不到」的
     * —— 允许拖进去就等于让窗口上半截凭空消失。
     */
    private val safeTopPx: Int by lazy {
        val cutout = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching {
                wm.currentWindowMetrics.windowInsets.displayCutout?.safeInsetTop
            }.getOrNull() ?: 0
        } else {
            0
        }
        val statusBar = runCatching {
            val id = appContext.resources.getIdentifier("status_bar_height", "dimen", "android")
            if (id > 0) appContext.resources.getDimensionPixelSize(id) else 0
        }.getOrDefault(0)
        max(cutout, statusBar).coerceAtLeast(0)
    }

    private fun clampPosition(x: Int, y: Int): OverlayGeometry.Pos {
        if (areaW <= 0 || areaH <= 0) refreshArea()
        return OverlayGeometry.clamp(
            x = x,
            y = y,
            screenW = areaW,
            screenH = areaH,
            w = windowWidthPx(),
            h = windowHeightPx(),
        )
    }

    /**
     * 窗口真正能放的区域（px），缓存起来。
     *
     * 不能直接用 displayMetrics：`TYPE_APPLICATION_OVERLAY` 默认按 systemBars 内缩，
     * 实际窗口在屏幕坐标里比 LayoutParams.y 多出一个状态栏高度（实测 +128px）。
     * 拿整屏高度去钳制，会把窗口一路压到导航栏外面 —— 而用户看到的只是「拖到底就没了」。
     *
     * 缓存是因为拖动时每帧都要钳制，而 currentWindowMetrics 是跨进程查询。
     */
    private fun refreshArea() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val metrics = wm.currentWindowMetrics
            val bars = metrics.windowInsets.getInsetsIgnoringVisibility(
                android.view.WindowInsets.Type.systemBars(),
            )
            areaW = (metrics.bounds.width() - bars.left - bars.right).coerceAtLeast(1)
            areaH = (metrics.bounds.height() - bars.top - bars.bottom).coerceAtLeast(1)
            return
        }
        // API 26–29：拿不到 insets，只能按「整屏减去状态栏」估
        val dm = appContext.resources.displayMetrics
        areaW = dm.widthPixels
        areaH = (dm.heightPixels - safeTopPx).coerceAtLeast(1)
    }

    /** 只写窗口参数，不落盘（拖动过程中每帧都走这里）。 */
    private fun pushPosition() {
        val p = params ?: return
        p.x = posX
        p.y = posY
        attached?.let { runCatching { wm.updateViewLayout(it, p) } }
    }

    /** 尺寸变化 / 屏幕尺寸变化后：重新钳制并按需更新窗口。 */
    private fun applyClampAndLayout() {
        val c = clampPosition(posX, posY)
        if (c.x == posX && c.y == posY) {
            if (!collapsed) relayout()
            return
        }
        posX = c.x
        posY = c.y
        if (params != null && attached != null) pushPosition() else if (!collapsed) relayout()
    }

    /**
     * 松手：先贴边再钳制，然后把 dp 坐标交给宿主持久化。
     *
     * 顺序不能反：先钳制后吸附的话，吸附后的位置可能又越界（贴到 maxX 之后窗口宽度
     * 刚好变过），得再钳一次。
     */
    private fun settlePosition() {
        if (areaW <= 0 || areaH <= 0) refreshArea()
        val snapped = OverlayGeometry.snapToEdge(posX, areaW, windowWidthPx(), dp(SNAP_DP))
        val c = clampPosition(snapped, posY)
        posX = c.x
        posY = c.y
        pushPosition()
        val dpX = (posX / density).roundToInt()
        val dpY = (posY / density).roundToInt()
        android.util.Log.i("KiiteLyric", "settle px=$posX,$posY dp=$dpX,$dpY")
        onPositionChange?.invoke(dpX, dpY)
    }

    /**
     * 横竖屏切换 / 改分辨率：屏幕尺寸变了就把窗口拉回可视区。
     * 每 400ms 的 update() 都会调一次，只是个 int 比较。
     */
    private fun checkScreenChanged() {
        val dm = appContext.resources.displayMetrics
        if (dm.widthPixels == lastScreenW && dm.heightPixels == lastScreenH) return
        lastScreenW = dm.widthPixels
        lastScreenH = dm.heightPixels
        // 横竖屏 / 分辨率变了：可用区域也跟着变（导航栏换边）
        refreshArea()
        applyClampAndLayout()
        // 尺寸变了进度条的长度也要重算
        progressFrac = -1f
    }

    // ---------- 外观（深浅色） ----------

    private fun bgColor(): Int = if (darkTheme) 0xD91C1C1E.toInt() else 0xF2F7F8FA.toInt()
    private fun strokeColor(): Int = if (darkTheme) 0x33FFFFFF else 0x1F000000
    private fun ballStrokeColor(): Int = if (darkTheme) 0x44FFFFFF else 0x29000000
    private fun textPrimaryColor(): Int = if (darkTheme) 0xFFFFFFFF.toInt() else 0xFF1B1C1E.toInt()
    private fun textSecondaryColor(): Int = if (darkTheme) 0x99FFFFFF.toInt() else 0x991B1C1E.toInt()
    private fun textControlColor(): Int = if (darkTheme) 0xCCFFFFFF.toInt() else 0xCC1B1C1E.toInt()
    private fun textFaintColor(): Int = if (darkTheme) 0x66FFFFFF else 0x661B1C1E
    private fun trackColor(): Int = if (darkTheme) 0x26FFFFFF else 0x14000000

    private fun barDrawable(): GradientDrawable = GradientDrawable().apply {
        cornerRadius = dp(1).toFloat()
        setColor(if (darkTheme) 0xB3FFFFFF.toInt() else 0xB31B1C1E.toInt())
    }

    private fun applyAppearance() {
        frameBg?.let {
            it.setColor(bgColor())
            it.setStroke(dp(1), strokeColor())
        }
        ballBg?.let {
            it.setColor(bgColor())
            it.setStroke(dp(1), ballStrokeColor())
        }
        tintPrimary.forEach { it.setTextColor(textPrimaryColor()) }
        tintSecondary.forEach { it.setTextColor(textSecondaryColor()) }
        tintControl.forEach { it.setTextColor(textControlColor()) }
        tintFaint.forEach { it.setTextColor(textFaintColor()) }
        textMeta?.setTextColor(textSecondaryColor())
        textRemain?.setTextColor(textSecondaryColor())
        progressTrack?.background = GradientDrawable().apply {
            cornerRadius = dp(1).toFloat()
            setColor(trackColor())
        }
        progressFill?.background = barDrawable()
    }

    /**
     * 每次播放进度 tick（~400ms）调用；句子没变就不动文字，只更新「还剩几秒」与进度条。
     */
    fun update(script: ParsedScript?, positionMs: Long, fallbackTitle: String?) {
        lastScript = script
        lastPos = positionMs
        lastTitle = fallbackTitle
        checkScreenChanged()
        val cur = textCurrent ?: return
        val next = textNext ?: return
        val lines = script?.timedLines.orEmpty()
        if (lines.isEmpty()) {
            // 无时间轴：曲名 + 一句说明（以前只显示曲名，看着像「台本丢了」）
            val title = fallbackTitle.orEmpty()
            if (lastFallback != title) {
                lastFallback = title
                lastShownLine = -2
                if (title.isBlank()) {
                    cur.text = appContext.getString(R.string.lyric_idle)
                    if (next.visibility != View.GONE) {
                        next.visibility = View.GONE
                        applyTextMetrics()
                    }
                } else {
                    cur.text = title
                    next.text = appContext.getString(R.string.lyric_no_script)
                    if (next.visibility != View.VISIBLE) {
                        next.visibility = View.VISIBLE
                        applyTextMetrics()
                    }
                }
                showMeta(null)
            }
            setProgress(null)
            return
        }
        lastFallback = null
        val i = indexOf(lines, positionMs).coerceIn(0, lines.size - 1)
        val line = lines[i]
        // 本句终点：台本自带 endMs 优先，否则拿下一句的起点当终点（最后一句没有终点）
        val endMs = line.endMs ?: lines.getOrNull(i + 1)?.startMs
        if (i != lastShownLine) {
            lastShownLine = i
            cur.text = line.text
            val following = lines.getOrNull(i + 1)?.text
            val showNext = !following.isNullOrBlank()
            if (!showNext) {
                next.visibility = View.GONE
            } else {
                next.visibility = View.VISIBLE
                next.text = following
            }
            showMeta(script?.title?.takeIf { it.isNotBlank() } ?: fallbackTitle)
            applyTextMetrics()
        }
        setRemain(OverlayGeometry.remainingSec(positionMs, endMs))
        setProgress(OverlayGeometry.lineProgress(positionMs, line.startMs, endMs))
    }

    /** 最后一个 startMs <= positionMs 的行号（与播放页 TimedIndex 同逻辑）。 */
    private fun indexOf(lines: List<com.kiite.player.core.TimedLine>, positionMs: Long): Int {
        var lo = 0
        var hi = lines.size - 1
        var ans = 0
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (lines[mid].startMs <= positionMs) {
                ans = mid
                lo = mid + 1
            } else {
                hi = mid - 1
            }
        }
        return ans
    }

    /** 顶部小标：曲名（台本没有章节结构，这一行就是「这是哪一部」）。 */
    private fun showMeta(title: String?) {
        val meta = textMeta ?: return
        val t = title?.trim().orEmpty()
        if (meta.text?.toString() != t) meta.text = t
        refreshMetaVisibility()
    }

    /** 剩余秒数：只在显示值变化时才改文字（每 tick 都改会白掉一帧布局）。 */
    private fun setRemain(sec: Long?) {
        val v = textRemain ?: return
        val want = if (sec == null) {
            ""
        } else {
            appContext.getString(R.string.lyric_remaining, sec)
        }
        if (v.text?.toString() != want) v.text = want
        refreshMetaVisibility()
    }

    /** 小标与剩余时间共用一行：有任何一个有内容就把这行亮出来。 */
    private fun refreshMetaVisibility() {
        val row = metaRow ?: return
        val hasMeta = textMeta?.text?.isNotBlank() == true
        val hasRemain = textRemain?.text?.isNotBlank() == true
        val want = if (hasMeta || hasRemain) View.VISIBLE else View.GONE
        if (row.visibility != want) {
            row.visibility = want
            applyTextMetrics()
        }
    }

    /** 底部细进度条：null = 这一句没有终点，藏起来。 */
    private fun setProgress(frac: Float?) {
        val track = progressTrack ?: return
        val fill = progressFill ?: return
        if (frac == null) {
            if (track.visibility != View.GONE) {
                track.visibility = View.GONE
                progressFrac = -1f
            }
            return
        }
        if (track.visibility != View.VISIBLE) {
            track.visibility = View.VISIBLE
            progressFrac = -1f
        }
        // 宽度还没量出来（刚挂上窗口那一两帧）：不记 frac，等下一 tick 再补
        val trackW = track.width
        if (trackW <= 0) return
        // 1% 以内不动：每 tick 都重建 LayoutParams 会让整行重新测量
        if (abs(frac - progressFrac) < 0.01f) return
        progressFrac = frac
        fill.layoutParams = FrameLayout.LayoutParams(
            (trackW * frac).roundToInt(),
            FrameLayout.LayoutParams.MATCH_PARENT,
        )
    }

    // ---------- 挂载 / 切换形态 ----------

    private fun attachExpanded() {
        val p = params ?: return
        val frame = expanded ?: buildExpanded().also { expanded = it }
        p.width = widthPx
        p.height = windowHeight()
        p.x = posX
        p.y = posY
        runCatching { wm.addView(frame, p) }
            .onSuccess { attached = frame }
            // 静默失败过一次就查了很久（窗口不出现却毫无线索）：这里至少留一行日志
            .onFailure { android.util.Log.w("KiiteLyric", "addView(expanded) 失败", it) }
    }

    private fun attachBall() {
        val p = params ?: return
        val b = ball ?: buildBall().also { ball = it }
        p.width = dp(BALL_DP)
        p.height = dp(BALL_DP)
        p.x = posX
        p.y = posY
        runCatching { wm.addView(b, p) }
            .onSuccess { attached = b }
            .onFailure { android.util.Log.w("KiiteLyric", "addView(ball) 失败", it) }
    }

    /** 收起 ⇄ 展开：WindowManager 不支持换 View，只能摘掉再挂另一个。 */
    private fun switchMode() {
        val p = params ?: return
        attached?.let { old ->
            p.x = posX
            p.y = posY
            runCatching { wm.removeView(old) }
        }
        attached = null
        if (collapsed) {
            attachBall()
            return
        }
        attachExpanded()
        applyTextScale()
        // 展开后立刻重放一次文本：收起期间 update 写进的是同一个 TextView，但确保行号状态一致
        lastShownLine = -2
        update(lastScript, lastPos, lastTitle)
    }

    // ---------- 展开态视图 ----------

    @SuppressLint("ClickableViewAccessibility")
    private fun buildExpanded(): FrameLayout {
        val ctx = appContext
        val bg = GradientDrawable().apply {
            cornerRadius = dp(14).toFloat()
            setColor(bgColor())
            setStroke(dp(1), strokeColor())
        }
        frameBg = bg
        val eff = effectiveScale()
        val current = TextView(ctx).apply {
            textSize = BASE_CURRENT_SP * eff
            setTextColor(textPrimaryColor())
            // 不锁单行：宽度窄了就自动折行，行数上限由 applyTextMetrics 按高度算
            isSingleLine = false
            maxLines = 4
            ellipsize = android.text.TextUtils.TruncateAt.END
            setLineSpacing(0f, 1.15f)
        }
        val next = TextView(ctx).apply {
            textSize = BASE_NEXT_SP * eff
            setTextColor(textSecondaryColor())
            isSingleLine = false
            // 下一句也允许折到两行：长句子不会被一刀切在一行里
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
            visibility = View.GONE
        }
        // 关键赋值：show() 之后 update() 靠这两个引用写文字
        textCurrent = current
        textNext = next
        tintPrimary.add(current)
        tintSecondary.add(next)

        // ---- 顶部小标：曲名（左）+ 剩余秒（右） ----
        val meta = TextView(ctx).apply {
            textSize = BASE_META_SP * eff.coerceAtMost(1.4f)
            setTextColor(textSecondaryColor())
            isSingleLine = true
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        val remain = TextView(ctx).apply {
            textSize = BASE_META_SP * eff.coerceAtMost(1.4f)
            setTextColor(textSecondaryColor())
            isSingleLine = true
            setPadding(dp(8), 0, 0, 0)
        }
        textMeta = meta
        textRemain = remain
        val metaLine = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(meta, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(remain, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ))
            visibility = View.GONE
        }
        metaRow = metaLine

        val texts = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            addView(current)
            addView(next)
        }

        // ---- 控制条：两行，窄窗口也塞得下 ----
        val box = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            gravity = Gravity.END
        }
        val scaleLabel = TextView(ctx).apply {
            textSize = 12f
            setTextColor(textControlColor())
            text = (scale * 100).roundToInt().toString() + "%"
            setPadding(dp(10), dp(4), dp(10), dp(4))
            isClickable = true
            setOnClickListener { changeScale(1.0f) }
        }
        labelScale = scaleLabel
        val row1 = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            addView(controlButton(ctx, "A−") { changeScale(scale - STEP) })
            addView(scaleLabel)
            addView(controlButton(ctx, "A+") { changeScale(scale + STEP) })
        }
        val row2 = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            addView(controlButton(ctx, getString(R.string.lyric_open_app)) {
                onRequestOpenApp?.invoke()
            })
            addView(controlButton(ctx, "缩为球") { collapseFromOverlay() })
            addView(controlButton(ctx, "关闭") { closeFromOverlay() })
        }
        box.addView(row1)
        // 右下角被缩放手柄占了 26dp，第二行（回 App / 收成球 / 关闭）留出避让，免得点不到
        box.addView(row2, LinearLayout.LayoutParams(
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { rightMargin = dp(HANDLE_DP) })
        controlsBox = box

        val column = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            addView(
                metaLine,
                LinearLayout.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
            addView(
                texts,
                LinearLayout.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f),
            )
            addView(
                box,
                LinearLayout.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(CONTROLS_HEIGHT_DP),
                ),
            )
        }

        // ---- 底部细进度条：一句话内的进度，比「还剩几秒」更直观 ----
        val track = FrameLayout(ctx).apply {
            visibility = View.GONE
            background = GradientDrawable().apply {
                cornerRadius = dp(1).toFloat()
                setColor(trackColor())
            }
        }
        val fill = View(ctx).apply { background = barDrawable() }
        track.addView(fill, FrameLayout.LayoutParams(0, FrameLayout.LayoutParams.MATCH_PARENT))
        progressTrack = track
        progressFill = fill

        // ---- 右下角缩放手柄 ----
        val handle = TextView(ctx).apply {
            text = "◢"
            textSize = 11f
            setTextColor(textFaintColor())
            gravity = Gravity.BOTTOM or Gravity.END
            this.setPadding(0, 0, dp(3), 0)
        }
        installResizeTouch(handle)
        tintFaint.add(handle)

        val frame = FrameLayout(ctx).apply {
            background = bg
            setPadding(dp(14), dp(9), dp(12), dp(8))
            addView(column, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ))
            addView(track, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                dp(PROGRESS_DP),
                Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL,
            ))
            addView(handle, FrameLayout.LayoutParams(dp(HANDLE_DP), dp(HANDLE_DP), Gravity.BOTTOM or Gravity.END))
        }

        installDragTouch(texts, frame)
        return frame
    }

    // ---- 收起态：悬浮球 ----

    @SuppressLint("ClickableViewAccessibility")
    private fun buildBall(): View {
        val ctx = appContext
        val bg = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(bgColor())
            setStroke(dp(1), ballStrokeColor())
        }
        ballBg = bg
        val dot = TextView(ctx).apply {
            text = "词"
            textSize = 15f
            gravity = Gravity.CENTER
            setTextColor(textPrimaryColor())
            background = bg
        }
        tintPrimary.add(dot)
        var downX = 0f
        var downY = 0f
        var baseX = 0
        var baseY = 0
        var dragging = false
        dot.setOnTouchListener { v, ev ->
            val p = params
            when (ev.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = ev.rawX
                    downY = ev.rawY
                    baseX = posX
                    baseY = posY
                    dragging = false
                    v.isPressed = true
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (p == null) return@setOnTouchListener true
                    val dx = (ev.rawX - downX).toInt()
                    val dy = (ev.rawY - downY).toInt()
                    if (abs(dx) > dp(4) || abs(dy) > dp(4)) {
                        dragging = true
                        val c = clampPosition(baseX + dx, baseY + dy)
                        posX = c.x
                        posY = c.y
                        pushPosition()
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    v.isPressed = false
                    if (dragging) settlePosition() else expandFromBall()
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    v.isPressed = false
                    true
                }
                else -> false
            }
        }
        return dot
    }

    // ---------- 手势 ----------

    @SuppressLint("ClickableViewAccessibility")
    private fun installDragTouch(target: View, frame: View) {
        var downX = 0f
        var downY = 0f
        var baseX = 0
        var baseY = 0
        var dragging = false
        // 单击/双击都交给 GestureDetector：自己按时间差判「是不是双击」会和拖动打架，
        // 而它内部已经用 touch slop 把「拖着走」的序列判成 scroll 了。
        // 代价：单击（展开控制条）要等 300ms 的双击窗口过去才生效。
        val gestures = GestureDetector(appContext, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true

            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                toggleControls()
                return true
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                onRequestOpenApp?.invoke()
                return true
            }
        })
        target.setOnTouchListener { _, ev ->
            val p = params
            gestures.onTouchEvent(ev)
            when (ev.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = ev.rawX
                    downY = ev.rawY
                    baseX = posX
                    baseY = posY
                    dragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (p == null) return@setOnTouchListener true
                    val dx = (ev.rawX - downX).toInt()
                    val dy = (ev.rawY - downY).toInt()
                    if (abs(dx) > dp(4) || abs(dy) > dp(4)) {
                        dragging = true
                        // 拖动过程中就钳制：否则手指划出屏幕外，窗口会跟到「看不见的地方」
                        val c = clampPosition(baseX + dx, baseY + dy)
                        posX = c.x
                        posY = c.y
                        pushPosition()
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (dragging) settlePosition()
                    true
                }
                MotionEvent.ACTION_CANCEL -> true
                else -> false
            }
        }
    }

    /** 右下角手柄：按住斜着拖 = 改窗口宽高。 */
    @SuppressLint("ClickableViewAccessibility")
    private fun installResizeTouch(handle: View) {
        var downX = 0f
        var downY = 0f
        var baseW = 0
        var baseH = 0
        var resizing = false
        if (areaW <= 0 || areaH <= 0) refreshArea()
        val maxW = (areaW - dp(16)).coerceAtLeast(dp(MIN_WIDTH_DP.roundToInt()))
        val maxH = (areaH / 2).coerceAtLeast(dp(MIN_HEIGHT_DP.roundToInt()))
        handle.setOnTouchListener { _, ev ->
            val p = params ?: return@setOnTouchListener false
            when (ev.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = ev.rawX
                    downY = ev.rawY
                    baseW = widthPx
                    baseH = heightPx
                    resizing = true
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val w = (baseW + (ev.rawX - downX)).toInt()
                        .coerceIn(dp(MIN_WIDTH_DP.roundToInt()), maxW)
                    val h = (baseH + (ev.rawY - downY)).toInt()
                        .coerceIn(dp(MIN_HEIGHT_DP.roundToInt()), maxH)
                    if (w != widthPx || h != heightPx) {
                        widthPx = w
                        heightPx = h
                        p.width = w
                        p.height = windowHeight()
                        attached?.let { runCatching { wm.updateViewLayout(it, p) } }
                        // 字号跟着宽度走：拖大一点字就大一点，同时正文按新宽度重新折行
                        applyTextScale()
                        // 变宽后右边缘可能顶出屏幕
                        val c = clampPosition(posX, posY)
                        if (c.x != posX || c.y != posY) {
                            posX = c.x
                            posY = c.y
                            pushPosition()
                        }
                        progressFrac = -1f
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (resizing) {
                        resizing = false
                        onSizeChange?.invoke(widthPx / density, heightPx / density)
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    resizing = false
                    true
                }
                else -> false
            }
        }
    }

    // ---------- 控制条 ----------

    private fun controlButton(ctx: Context, label: String, onClick: () -> Unit): TextView =
        TextView(ctx).apply {
            text = label
            textSize = 12f
            setTextColor(textControlColor())
            setPadding(dp(9), dp(4), dp(9), dp(4))
            isClickable = true
            setOnClickListener { onClick() }
            tintControl.add(this)
        }

    private fun changeScale(next: Float) {
        scale = next.coerceIn(MIN_SCALE, MAX_SCALE)
        applyTextScale()
        onScaleChange?.invoke(scale)
        // 调完字号顺延自动收起计时
        if (controlsVisible()) scheduleControlsHide()
    }

    private fun collapseFromOverlay() {
        if (!collapsed) {
            switchModeCollapsedByUser()
        }
    }

    private fun expandFromBall() {
        if (!collapsed) return
        collapsed = false
        switchMode()
        onCollapsedChange?.invoke(false)
    }

    private fun switchModeCollapsedByUser() {
        collapsed = true
        switchMode()
        onCollapsedChange?.invoke(true)
    }

    private fun closeFromOverlay() {
        hide()
        onRequestClose?.invoke()
    }

    private fun toggleControls() {
        val box = controlsBox ?: return
        if (box.visibility == View.VISIBLE) {
            box.visibility = View.GONE
            box.removeCallbacks(hideControlsRunnable)
        } else {
            box.visibility = View.VISIBLE
            scheduleControlsHide()
        }
        relayout()
    }

    private fun scheduleControlsHide() {
        val box = controlsBox ?: return
        box.removeCallbacks(hideControlsRunnable)
        box.postDelayed(hideControlsRunnable, CONTROLS_TIMEOUT_MS)
    }

    private val hideControlsRunnable = Runnable {
        if (controlsBox?.visibility == View.VISIBLE) {
            controlsBox?.visibility = View.GONE
            relayout()
        }
    }

    private fun getString(resId: Int): String = appContext.getString(resId)

    private fun dp(v: Int): Int = (v * density).toInt()
}
