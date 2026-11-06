package com.kiite.player.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import com.kiite.player.ui.theme.LocalScrollActivityHolder
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged

/** 列表滚动时上报：滚动中暂停实时毛玻璃（LazyColumn）。 */
@Composable
fun TrackScroll(state: LazyListState) {
    val activity = LocalScrollActivityHolder.current
    LaunchedEffect(state, activity) {
        snapshotFlow { state.isScrollInProgress }
            .distinctUntilChanged()
            .collectLatest { active ->
                if (active) {
                    activity.set(true)
                } else {
                    // 稍等再恢复：连续拖动 / 惯性滚动之间不要来回切
                    delay(140)
                    activity.set(false)
                }
            }
    }
}

/** 列表滚动时上报：滚动中暂停实时毛玻璃（verticalScroll）。 */
@Composable
fun TrackScroll(state: ScrollState) {
    val activity = LocalScrollActivityHolder.current
    LaunchedEffect(state, activity) {
        snapshotFlow { state.isScrollInProgress }
            .distinctUntilChanged()
            .collectLatest { active ->
                if (active) {
                    activity.set(true)
                } else {
                    delay(140)
                    activity.set(false)
                }
            }
    }
}
