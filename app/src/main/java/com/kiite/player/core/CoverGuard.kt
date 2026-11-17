package com.kiite.player.core

/**
 * 判断一张图「配不配当作品头图」。
 *
 * 为什么需要这一步：**只看 URL 不够**。DLsite 的封面图在拿不到会话 / 没有 Referer 时，
 * 会返回一张站内宣传图或占位图（横幅：二次元総合ダウンロードショップ DLsite）。
 * URL 明明是 `…/RJxxxxxx/RJxxxxxx_img_main.jpg`，下回来的字节却是那张横幅，
 * 于是「一堆作品都变成同一张头图」。
 *
 * 所以下载完必须看一眼真实尺寸：作品封面接近方图（4:3 / 3:4 / 1:1），
 * 站内横幅是超宽的（常见 1500×400 以上）。这里只做纯计算，方便单测。
 */
object CoverGuard {

    /** 宽高比超过这个值就当横幅。作品封面最扁也在 16:9 附近，2.2 留了余量。 */
    const val MAX_ASPECT = 2.2f

    /** 小于这个边长的不可能是封面（DLsite 缩略图最小也有 120px 级别）。 */
    const val MIN_SIDE = 96

    enum class Verdict {
        /** 可以当封面。 */
        OK,

        /** 超宽横幅：站内宣传图 / OGP 图。 */
        BANNER,

        /** 太小：图标、占位图。 */
        TOO_SMALL,

        /** 解不出尺寸：不是图片（常见于被重定向到 HTML 年龄确认页）。 */
        UNREADABLE,
    }

    fun judge(width: Int, height: Int): Verdict = when {
        width <= 0 || height <= 0 -> Verdict.UNREADABLE
        minOf(width, height) < MIN_SIDE -> Verdict.TOO_SMALL
        maxOf(width, height).toFloat() / minOf(width, height).toFloat() > MAX_ASPECT -> Verdict.BANNER
        else -> Verdict.OK
    }

    /** 日志用的一句话说明（不要把这句直接给用户看，界面另有文案）。 */
    fun reason(width: Int, height: Int): String = when (judge(width, height)) {
        Verdict.OK -> "尺寸正常 " + width + "×" + height
        Verdict.BANNER -> "疑似站内横幅 " + width + "×" + height + "（比例 " +
            String.format(java.util.Locale.ROOT, "%.1f", maxOf(width, height).toFloat() / minOf(width, height).toFloat()) + "）"
        Verdict.TOO_SMALL -> "图片太小 " + width + "×" + height
        Verdict.UNREADABLE -> "解不出图片尺寸（可能不是图片）"
    }
}
