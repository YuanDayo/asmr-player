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
import android.widget.LinearLayout
import android.widget.TextView
import com.kiite.player.core.ParsedScript
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 台本悬浮窗（桌面歌词）：播放时把当前台本行投到桌面上。
 *
 * 用应用上下文直接挂 WindowManager（TYPE_APPLICATION_OVERLAY），不需要单独 Service ——
 * 播放本身由前台 Service 保活进程，悬浮窗随进程存活。需要「显示在其他应用上层」权限，
 * 未授权时 show() 静默无效（设置页在开启开关前会先引导授权）。
 *
 * 交互：
 * - 按住文本区拖动可移动位置；
 * - 点一下文本区展开/收起控制条（A− / 百分比 / A+ / 关闭），5 秒无操作自动收起；
 * - 关闭 = 摘掉窗口并回调 [onRequestClose]（由 ViewModel 把设置关掉，免得再自己挂回来）；
 * - 字号倍率通过 [onScaleChange] 交给 ViewModel 持久化。
 */
class LyricOverlayController(context: Context) {

    companion object {
        private const val MIN_SCALE = 0.7f
        private const val MAX_SCALE = 1.8f
        private const val STEP = 0.1f
        private const val BASE_CURRENT_SP = 15f
        private const val BASE_NEXT_SP = 12f
        private const val CONTROLS_TIMEOUT_MS = 5000L
    }

    private val appContext = context.applicationContext
    private val wm = appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var root: View? = null
    private var textCurrent: TextView? = null
    private var textNext: TextView? = null
    private var controls: View? = null
    private var labelScale: TextView? = null
    private var params: WindowManager.LayoutParams? = null
    private var lastShownLine: Int = -2
    private var lastFallback: String? = null
    // 最近一次 update 的入参：show() 挂上窗口后立即重放，避免窗口先空着等下一个 tick
    private var lastScript: ParsedScript? = null
    private var lastPos: Long = 0L
    private var lastTitle: String? = null
    private var scale: Float = 1.0f

    /** 用户在浮窗上点了「关闭」：由宿主把 lyricOverlay 设置关掉。 */
    var onRequestClose: (() -> Unit)? = null

    /** 用户调了字号：由宿主持久化（0.7–1.8）。 */
    var onScaleChange: ((Float) -> Unit)? = null

    val shown: Boolean get() = root != null

    fun show() {
        if (root != null) return
        if (!Settings.canDrawOverlays(appContext)) return
        val p = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(24)
            y = dp(160)
        }
        val view = buildView(p)
        root = view
        params = p
        runCatching { wm.addView(view, p) }
        // 关键：textCurrent/textNext 在 buildView 里赋值；挂上后立即重放最近文本
        applyTextScale()
        update(lastScript, lastPos, lastTitle)
    }

    fun hide() {
        val v = root ?: return
        root = null
        textCurrent = null
        textNext = null
        controls = null
        labelScale = null
        params = null
        lastShownLine = -2
        lastFallback = null
        runCatching { wm.removeView(v) }
    }

    /** 应用字号倍率（设置里读出的值 / 用户点 A± 后的值）。 */
    fun setScale(value: Float) {
        val clamped = value.coerceIn(MIN_SCALE, MAX_SCALE)
        if (abs(clamped - scale) < 0.001f) return
        scale = clamped
        applyTextScale()
    }

    private fun applyTextScale() {
        textCurrent?.textSize = BASE_CURRENT_SP * scale
        textNext?.textSize = BASE_NEXT_SP * scale
        labelScale?.text = (scale * 100).roundToInt().toString() + "%"
    }

    /** 每次播放进度 tick（~400ms）调用；只有句子真的变了才改文字，避免每帧布局。 */
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
                next.visibility = View.GONE
            }
            return
        }
        lastFallback = null
        val i = indexOf(lines, positionMs).coerceIn(0, lines.size - 1)
        if (i == lastShownLine) return
        lastShownLine = i
        cur.text = lines[i].text
        val following = lines.getOrNull(i + 1)?.text
        if (following.isNullOrBlank()) {
            next.visibility = View.GONE
        } else {
            next.visibility = View.VISIBLE
            next.text = following
        }
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

    @SuppressLint("ClickableViewAccessibility")
    private fun buildView(p: WindowManager.LayoutParams): View {
        val ctx = appContext
        val bg = GradientDrawable().apply {
            cornerRadius = dp(14).toFloat()
            setColor(0xCC1C1C1E.toInt())          // iOS 深灰 80%
            setStroke(dp(1), 0x33FFFFFF)
        }
        val current = TextView(ctx).apply {
            textSize = BASE_CURRENT_SP * scale
            setTextColor(0xFFFFFFFF.toInt())
            maxLines = 2
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

        // 文本区：负责拖动 + 轻点展开控制条
        val textColumn = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            addView(current)
            addView(next)
        }

        // 控制条：A− / 百分比（点击复位 100%）/ A+ / 关闭
        val scaleLabel = TextView(ctx).apply {
            textSize = 12f
            setTextColor(0xCCFFFFFF.toInt())
            text = (scale * 100).roundToInt().toString() + "%"
            setPadding(dp(10), dp(6), dp(10), dp(6))
            isClickable = true
            setOnClickListener { changeScale(1.0f) }
        }
        labelScale = scaleLabel
        val controlsRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            visibility = View.GONE
            addView(controlButton(ctx, "A−") { changeScale(scale - STEP) })
            addView(scaleLabel)
            addView(controlButton(ctx, "A+") { changeScale(scale + STEP) })
            addView(controlButton(ctx, "关闭") { closeFromOverlay() })
        }
        controls = controlsRow

        val column = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = bg
            setPadding(dp(14), dp(9), dp(14), dp(7))
            addView(textColumn)
            addView(controlsRow)
        }

        // 拖动 + 单击：按住文本区滑动调整位置，轻点展开/收起控制条
        var downX = 0f
        var downY = 0f
        var baseX = 0
        var baseY = 0
        var downAt = 0L
        var dragging = false
        textColumn.setOnTouchListener { _, ev ->
            when (ev.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = ev.rawX
                    downY = ev.rawY
                    baseX = p.x
                    baseY = p.y
                    downAt = System.currentTimeMillis()
                    dragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (ev.rawX - downX).toInt()
                    val dy = (ev.rawY - downY).toInt()
                    if (abs(dx) > dp(4) || abs(dy) > dp(4)) {
                        dragging = true
                        p.x = baseX + dx
                        p.y = baseY + dy
                        runCatching { wm.updateViewLayout(column, p) }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    // 轻点（未拖动、时间短）= 展开/收起控制条
                    if (!dragging && System.currentTimeMillis() - downAt < 400) {
                        toggleControls(controlsRow)
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> true
                else -> false
            }
        }
        return column
    }

    private fun controlButton(ctx: Context, label: String, onClick: () -> Unit): TextView =
        TextView(ctx).apply {
            text = label
            textSize = 12f
            setTextColor(0xCCFFFFFF.toInt())
            setPadding(dp(10), dp(6), dp(10), dp(6))
            isClickable = true
            setOnClickListener { onClick() }
        }

    private fun changeScale(next: Float) {
        scale = next.coerceIn(MIN_SCALE, MAX_SCALE)
        applyTextScale()
        onScaleChange?.invoke(scale)
        // 调完字号顺延自动收起计时
        controls?.let { c -> if (c.visibility == View.VISIBLE) scheduleControlsHide(c) }
    }

    private fun closeFromOverlay() {
        hide()
        onRequestClose?.invoke()
    }

    private fun toggleControls(c: View) {
        if (c.visibility == View.VISIBLE) {
            c.visibility = View.GONE
            c.removeCallbacks(hideControlsRunnable)
        } else {
            c.visibility = View.VISIBLE
            scheduleControlsHide(c)
        }
    }

    private fun scheduleControlsHide(c: View) {
        c.removeCallbacks(hideControlsRunnable)
        c.postDelayed(hideControlsRunnable, CONTROLS_TIMEOUT_MS)
    }

    private val hideControlsRunnable = Runnable {
        controls?.visibility = View.GONE
    }

    private fun dp(v: Int): Int =
        (v * appContext.resources.displayMetrics.density).toInt()
}
