package com.kiite.player.playback

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
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
 * - 按住文本区拖动 = 移动窗口；
 * - 轻点文本区 = 展开/收起控制条（A− / 百分比 / A+ / 收成球 / 关闭），6 秒无操作自动收起；
 * - 右下角 ◢ 手柄拖动 = 直接调整窗口大小（宽高同时可调，字号不变，能显示更多字）；
 * - 控制条上的「球」= 收成悬浮球：桌面上只留一个小圆点，点一下立刻展开回歌词；
 * - 关闭 = 摘掉窗口并回调 [onRequestClose]（由 ViewModel 把设置关掉，免得再自己挂回来）；
 * - 字号 / 尺寸 / 收起状态分别通过 [onScaleChange] / [onSizeChange] / [onCollapsedChange] 持久化。
 */
class LyricOverlayController(context: Context) {

    companion object {
        private const val MIN_SCALE = 0.7f
        private const val MAX_SCALE = 1.8f
        private const val STEP = 0.1f
        private const val BASE_CURRENT_SP = 15f
        private const val BASE_NEXT_SP = 12f
        private const val CONTROLS_TIMEOUT_MS = 6000L
        private const val MIN_WIDTH_DP = 150f
        private const val MIN_HEIGHT_DP = 52f
        private const val DEFAULT_WIDTH_DP = 240f
        private const val DEFAULT_HEIGHT_DP = 96f
        private const val CONTROLS_HEIGHT_DP = 56
        private const val HANDLE_DP = 26
        private const val BALL_DP = 46
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
    private var controlsBox: View? = null
    private var labelScale: TextView? = null

    private var lastShownLine: Int = -2
    private var lastFallback: String? = null
    // 最近一次 update 的入参：show() 挂上窗口后立即重放，避免窗口先空着等下一个 tick
    private var lastScript: ParsedScript? = null
    private var lastPos: Long = 0L
    private var lastTitle: String? = null

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

    val shown: Boolean get() = attached != null

    init {
        widthPx = dp(DEFAULT_WIDTH_DP.roundToInt())
        heightPx = dp(DEFAULT_HEIGHT_DP.roundToInt())
        posX = dp(24)
        posY = dp(160)
    }

    fun show() {
        if (attached != null) return
        if (!Settings.canDrawOverlays(appContext)) return
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
                x = posX
                y = posY
            }
        }
        // 先按设置里的尺寸/字号准备好，再挂窗口，避免先闪一下默认大小
        if (collapsed) attachBall() else attachExpanded()
        applyTextScale()
        update(lastScript, lastPos, lastTitle)
    }

    fun hide() {
        val v = attached
        attached = null
        if (v != null) runCatching { wm.removeView(v) }
        params = null
        lastShownLine = -2
        lastFallback = null
        // 视图实例留着复用（下次 show 直接 addView），只把控制条收起来
        controlsBox?.visibility = View.GONE
    }

    /** 应用字号倍率（设置里读出的值 / 用户点 A± 后的值）。 */
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
        applyTextMetrics()
        if (!collapsed) relayout()
    }

    /** 收起为悬浮球 / 从球展开。 */
    fun setCollapsed(value: Boolean) {
        if (collapsed == value) return
        collapsed = value
        if (params != null) switchMode()
    }

    private fun applyTextScale() {
        textCurrent?.textSize = BASE_CURRENT_SP * scale
        textNext?.textSize = BASE_NEXT_SP * scale
        labelScale?.text = (scale * 100).roundToInt().toString() + "%"
        applyTextMetrics()
    }

    /**
     * 窗口是固定尺寸的：按「可用高度 ÷ 行高」算正文最多能放几行，
     * 超过就省略号截断，不会出现文字被窗口边缘硬切一半的情况。
     */
    private fun applyTextMetrics() {
        val cur = textCurrent ?: return
        val next = textNext ?: return
        val avail = heightPx - dp(9 + 8)
        val lineH = (BASE_CURRENT_SP * scale * density * 1.4f).coerceAtLeast(1f)
        val nextH = if (next.visibility == View.VISIBLE) {
            BASE_NEXT_SP * scale * density * 1.4f
        } else {
            0f
        }
        cur.maxLines = ((avail - nextH) / lineH).toInt().coerceIn(1, 10)
    }

    private fun controlsVisible(): Boolean = controlsBox?.visibility == View.VISIBLE

    private fun windowHeight(): Int =
        heightPx + if (controlsVisible()) dp(CONTROLS_HEIGHT_DP) else 0

    /** 把当前尺寸/位置写回窗口（尺寸或控制条变化后调用）。 */
    private fun relayout() {
        val p = params ?: return
        val v = attached ?: return
        if (collapsed) return
        p.width = widthPx
        p.height = windowHeight()
        runCatching { wm.updateViewLayout(v, p) }
    }

    /**
     * 每次播放进度 tick（~400ms）调用；只有句子真的变了才改文字，避免每帧布局。
     */
    fun update(script: ParsedScript?, positionMs: Long, fallbackTitle: String?) {
        lastScript = script
        lastPos = positionMs
        lastTitle = fallbackTitle
        val cur = textCurrent ?: return
        val next = textNext ?: return
        val lines = script?.timedLines.orEmpty()
        if (lines.isEmpty()) {
            // 无时间轴：只显示曲目名（一次设置，不每 tick 重复）
            val title = fallbackTitle.orEmpty()
            if (lastFallback != title) {
                lastFallback = title
                lastShownLine = -2
                cur.text = title
                if (next.visibility != View.GONE) {
                    next.visibility = View.GONE
                    applyTextMetrics()
                }
            }
            return
        }
        lastFallback = null
        val i = indexOf(lines, positionMs).coerceIn(0, lines.size - 1)
        if (i == lastShownLine) return
        lastShownLine = i
        cur.text = lines[i].text
        val following = lines.getOrNull(i + 1)?.text
        val showNext = !following.isNullOrBlank()
        if (!showNext) {
            next.visibility = View.GONE
        } else {
            next.visibility = View.VISIBLE
            next.text = following
        }
        applyTextMetrics()
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

    // ---------- 挂载 / 切换形态 ----------

    private fun attachExpanded() {
        val p = params ?: return
        val frame = expanded ?: buildExpanded().also { expanded = it }
        p.width = widthPx
        p.height = windowHeight()
        p.x = posX
        p.y = posY
        runCatching { wm.addView(frame, p) }.onSuccess { attached = frame }
    }

    private fun attachBall() {
        val p = params ?: return
        val b = ball ?: buildBall().also { ball = it }
        p.width = dp(BALL_DP)
        p.height = dp(BALL_DP)
        p.x = posX
        p.y = posY
        runCatching { wm.addView(b, p) }.onSuccess { attached = b }
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
            setColor(0xD91C1C1E.toInt())          // iOS 深灰 ~85%
            setStroke(dp(1), 0x33FFFFFF)
        }
        val current = TextView(ctx).apply {
            textSize = BASE_CURRENT_SP * scale
            setTextColor(0xFFFFFFFF.toInt())
            maxLines = 4
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        val next = TextView(ctx).apply {
            textSize = BASE_NEXT_SP * scale
            setTextColor(0x99FFFFFF.toInt())
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            visibility = View.GONE
        }
        // 关键赋值：show() 之后 update() 靠这两个引用写文字
        textCurrent = current
        textNext = next

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
            setTextColor(0xCCFFFFFF.toInt())
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
            addView(controlButton(ctx, "缩为球") { collapseFromOverlay() })
            addView(controlButton(ctx, "关闭") { closeFromOverlay() })
        }
        box.addView(row1)
        // 右下角被缩放手柄占了 26dp，第二行（收成球 / 关闭）留出避让，免得点不到
        box.addView(row2, LinearLayout.LayoutParams(
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { rightMargin = dp(HANDLE_DP) })
        controlsBox = box

        val column = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
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

        // ---- 右下角缩放手柄 ----
        val handle = TextView(ctx).apply {
            text = "◢"
            textSize = 11f
            setTextColor(0x66FFFFFF)
            gravity = Gravity.BOTTOM or Gravity.END
            this.setPadding(0, 0, dp(3), 0)
        }
        installResizeTouch(handle)

        val frame = FrameLayout(ctx).apply {
            background = bg
            setPadding(dp(14), dp(9), dp(12), dp(8))
            addView(column, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
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
            setColor(0xD91C1C1E.toInt())
            setStroke(dp(1), 0x44FFFFFF)
        }
        val dot = TextView(ctx).apply {
            text = "词"
            textSize = 15f
            gravity = Gravity.CENTER
            setTextColor(0xFFFFFFFF.toInt())
            background = bg
        }
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
                        posX = baseX + dx
                        posY = baseY + dy
                        p.x = posX
                        p.y = posY
                        runCatching { wm.updateViewLayout(v, p) }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    v.isPressed = false
                    if (!dragging) expandFromBall()
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
        var downAt = 0L
        var dragging = false
        target.setOnTouchListener { _, ev ->
            val p = params
            when (ev.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = ev.rawX
                    downY = ev.rawY
                    baseX = posX
                    baseY = posY
                    downAt = System.currentTimeMillis()
                    dragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (p == null) return@setOnTouchListener true
                    val dx = (ev.rawX - downX).toInt()
                    val dy = (ev.rawY - downY).toInt()
                    if (abs(dx) > dp(4) || abs(dy) > dp(4)) {
                        dragging = true
                        posX = baseX + dx
                        posY = baseY + dy
                        p.x = posX
                        p.y = posY
                        runCatching { wm.updateViewLayout(frame, p) }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    // 轻点（未拖动、时间短）= 展开/收起控制条
                    if (!dragging && System.currentTimeMillis() - downAt < 400) {
                        toggleControls()
                    }
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
        val maxW = (appContext.resources.displayMetrics.widthPixels - dp(16))
            .coerceAtLeast(dp(MIN_WIDTH_DP.roundToInt()))
        val maxH = (appContext.resources.displayMetrics.heightPixels / 2)
            .coerceAtLeast(dp(MIN_HEIGHT_DP.roundToInt()))
        var resizing = false
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
                        applyTextMetrics()
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
            setTextColor(0xCCFFFFFF.toInt())
            setPadding(dp(9), dp(4), dp(9), dp(4))
            isClickable = true
            setOnClickListener { onClick() }
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

    private fun dp(v: Int): Int = (v * density).toInt()
}
