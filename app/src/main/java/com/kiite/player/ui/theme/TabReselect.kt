package com.kiite.player.ui.theme

import androidx.compose.runtime.compositionLocalOf

/**
 * 「重复点击当前 Tab」信号（UI-SPEC 第 4 节）。
 *
 * 每次点已经有选中的 Tab，计数自增一次；页面用 `LaunchedEffect(tick)` 监听并回到顶部。
 * 0 表示本进程内还没发生过 —— 首次组合时据此跳过，避免一进页面就把列表拽回顶部。
 *
 * 为什么用计数器而不是事件流：Tab 栏在 `AppRoot` 的悬浮底栏里，页面在 `AnimatedContent`
 * 里，两者之间只共享 CompositionLocal 最省事；计数器天然幂等，重复组合不会重复触发。
 * 值 0 之外一律用 `tick > 0` 判断，将来做「长按 Tab」之类的扩展也不冲突。
 */
val LocalTabReselect = compositionLocalOf { 0 }
