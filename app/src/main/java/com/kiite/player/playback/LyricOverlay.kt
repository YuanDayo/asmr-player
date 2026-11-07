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

/**
 * 台本悬浮窗（桌面歌词）：播放时把当前台本行投到桌面上。
 *
 * 用应用上下文直接挂 WindowManager（TYPE_APPLICATION_OVERLAY），不需要单独 Service ——
 * 播放本身由前台 Service 保活进程，悬浮窗随进程存活。需要「显示在其他应用上层」权限，
 * 未授权时 show() 静默无效（设置页在开启开关前会先引导授权）。
 */
class LyricOverlayController(context: Context) {

    private val appContext = context.applicationContext
    private val wm = appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var root: View? = null
    private var textCurrent: TextView? = null
    private var textNext: TextView? = null
    private var params: WindowManager.LayoutParams? = null
    private var lastShownLine: Int = -2
    private var lastFallback: String? = null
    // 最近一次 update 的入参：show() 挂上窗口后立即重放，避免窗口先空着等下一个 tick
    private var lastScript: ParsedScript? = null
    private var lastPos: Long = 0L
    private var lastTitle: String? = null

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
        update(lastScript, lastPos, lastTitle)
    }

    fun hide() {
        val v = root ?: return
        root = null
        textCurrent = null
        textNext = null
        params = null
        lastShownLine = -2
        lastFallback = null
        runCatching { wm.removeView(v) }
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
            textSize = 15f
            setTextColor(0xFFFFFFFF.toInt())
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        val next = TextView(ctx).apply {
            textSize = 12f
            setTextColor(0x99FFFFFF.toInt())
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            visibility = View.GONE
        }
        // 关键赋值：show() 之后 update() 靠这两个引用写文字
        textCurrent = current
        textNext = next
        val column = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = bg
            setPadding(dp(14), dp(9), dp(14), dp(9))
            addView(current)
            addView(next)
        }
        // 可拖动：按住滑动调整位置；点击无行为（不抢焦点）
        var downX = 0f
        var downY = 0f
        var baseX = 0
        var baseY = 0
        column.setOnTouchListener { v, ev ->
            when (ev.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = ev.rawX
                    downY = ev.rawY
                    baseX = p.x
                    baseY = p.y
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (ev.rawX - downX).toInt()
                    val dy = (ev.rawY - downY).toInt()
                    if (abs(dx) > 4 || abs(dy) > 4) {
                        p.x = baseX + dx
                        p.y = baseY + dy
                        runCatching { wm.updateViewLayout(v, p) }
                    }
                    true
                }
                else -> false
            }
        }
        return column
    }

    private fun dp(v: Int): Int =
        (v * appContext.resources.displayMetrics.density).toInt()
}
