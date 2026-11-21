package com.kiite.player.ui.theme

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * 顶栏的收起进度：0 = 页面停在顶部（顶栏透明、只看见内容里的大标题），
 * 1 = 已经上滑（顶栏变半透明磨砂条、小标题淡入）。
 *
 * 为什么不放在 AppRoot 里用 `remember`：滚动状态在**页面**里（每个页面一个
 * LazyListState / ScrollState），而顶栏在 AppRoot。中间需要一层共享对象，
 * 页面滚到哪儿就写进来，顶栏自己读。
 *
 * 为什么是 0..1 的浮点而不是布尔：顶栏要**逐帧**跟着滚动手感变透明，
 * 布尔只能得到「啪一下出现」。写入侧用 `distinctUntilChanged` 压掉重复值，
 * 读取侧在绘制相位读 State，不触发重组。
 */
@Stable
class TopBarScroll {
    var progress by mutableFloatStateOf(0f)
        private set

    fun set(value: Float) {
        val v = value.coerceIn(0f, 1f)
        // 1% 的抖动不值得写一次快照
        if (kotlin.math.abs(progress - v) > 0.01f) progress = v
    }
}

/** 取不到时给一个常量 0：预览、单测里顶栏就是「透明」状态，不需要滚动源。 */
val LocalTopBarScroll = compositionLocalOf { TopBarScroll() }

/** 大标题滚出到「收起」所需要的距离：标题块高度的一小半，手感上就是「往上推一下」。 */
private val CollapseDistance = 56.dp

/** 列表页上报滚动进度（LazyColumn）。 */
@Composable
fun TrackTopBar(state: LazyListState, distance: Dp = CollapseDistance) {
    val holder = LocalTopBarScroll.current
    val distancePx = with(LocalDensity.current) { distance.toPx() }
    androidx.compose.runtime.LaunchedEffect(state, holder, distancePx) {
        snapshotFlow {
            // 已经是第 2 项之后了，肯定收起；还在第 0 项时按像素线性推进
            if (state.firstVisibleItemIndex > 0) 1f
            else (state.firstVisibleItemScrollOffset / distancePx).coerceIn(0f, 1f)
        }.distinctUntilChanged().collect { holder.set(it) }
    }
}

/** 列表页上报滚动进度（普通 Column + verticalScroll）。 */
@Composable
fun TrackTopBar(state: ScrollState, distance: Dp = CollapseDistance) {
    val holder = LocalTopBarScroll.current
    val distancePx = with(LocalDensity.current) { distance.toPx() }
    androidx.compose.runtime.LaunchedEffect(state, holder, distancePx) {
        snapshotFlow { (state.value / distancePx).coerceIn(0f, 1f) }
            .distinctUntilChanged()
            .collect { holder.set(it) }
    }
}

/**
 * 没有滚动源的页面（播放页）：顶栏固定为磨砂状态。
 *
 * 播放页的内容不是一条列表，没有「大标题滚走」这件事；它的沉浸感由两档卡片负责。
 */
@Composable
fun TopBarAlwaysFrosted() {
    val holder = LocalTopBarScroll.current
    androidx.compose.runtime.LaunchedEffect(holder) { holder.set(1f) }
}
