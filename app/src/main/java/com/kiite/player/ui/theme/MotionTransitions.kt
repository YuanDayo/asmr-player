package com.kiite.player.ui.theme

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.ui.geometry.Rect

/**
 * 具名转场：把「这类动作该长什么样」固定下来，页面里只挑名字，不再各自拼 tween。
 *
 * 命名约定：
 * - [asmrEnter] / [asmrExit]：卡片、悬浮条这类「有体积」的元素进出场（高度 + 透明度）；
 * - [collapseEnter] / [collapseExit]：页面骨架收放（头部、四格、走带），给内容腾地方；
 * - [tabTransition]：一级标签切换，按方向轻微位移，避免整页硬切；
 * - [fadeThrough]：同一位置换内容（播放/暂停图标、面板），只淡不位移，不叠加两层动画；
 * - [popIn] / [popOut]：浮层提示（「回到当前进度」这类胶囊按钮）；
 * - [sharedBoundsSpec]：共享元素飞行，曲线与时长跟切页对齐。
 */

/** 卡片 / 悬浮条进出场：短促减速，比默认 spring 更跟手，中途掉帧也不明显。 */
fun asmrEnter(): EnterTransition =
    expandVertically(Motion.standard(Motion.MEDIUM)) + fadeIn(Motion.enter(Motion.SHORT))

fun asmrExit(): ExitTransition =
    shrinkVertically(Motion.exit(Motion.MEDIUM)) + fadeOut(Motion.exit(Motion.SHORT))

/**
 * 骨架收放：头部 / 四格 / 走带在阅读模式、播放列表铺满时原地让位。
 *
 * 高度变化的曲线用标准曲线（不是减速），因为它同时是「内容区在长大」的驱动，
 * 减速会让最后一帧显得发飘。
 */
fun collapseEnter(): EnterTransition =
    expandVertically(Motion.standard(Motion.MEDIUM)) + fadeIn(Motion.enter(Motion.SHORT))

fun collapseExit(): ExitTransition =
    shrinkVertically(Motion.standard(Motion.MEDIUM)) + fadeOut(Motion.exit(Motion.SHORT))

/** 一级标签切换：按方向轻微位移（1/8 屏）+ 淡入淡出，页面之间有连贯感。 */
fun tabTransition(forward: Boolean): ContentTransform {
    val enter = slideInHorizontally(Motion.enter(Motion.MEDIUM)) { full ->
        if (forward) full / 8 else -full / 8
    } + fadeIn(Motion.enter(Motion.MEDIUM))
    val exit = slideOutHorizontally(Motion.exit(Motion.MEDIUM)) { full ->
        if (forward) -full / 8 else full / 8
    } + fadeOut(Motion.exit(Motion.SHORT))
    return enter togetherWith exit
}

/**
 * 同一位置换内容：只淡不位移。
 *
 * 播放/暂停图标如果叠上缩放或位移，会被看成「两个图标在打架」；纯淡入淡出
 * 更像同一个按钮换了状态。
 */
fun fadeThrough(): ContentTransform =
    fadeIn(Motion.enter(Motion.SHORT)) togetherWith fadeOut(Motion.exit(Motion.SHORT))

/** 浮层提示（胶囊按钮）：轻微放大后落位，比纯淡入更容易被注意到。 */
fun popIn(): EnterTransition =
    fadeIn(Motion.enter(Motion.SHORT)) + scaleIn(Motion.enter(Motion.MEDIUM), initialScale = 0.85f)

fun popOut(): ExitTransition =
    fadeOut(Motion.exit(Motion.SHORT)) + scaleOut(Motion.exit(Motion.SHORT))

/**
 * 共享元素飞行的时长/曲线。
 *
 * 必须和一级标签切换同一条曲线：封面从迷你条飞进播放页时，页面本身也在滑，
 * 两者曲线不同步就会看起来「封面在屏幕上漂」。
 */
fun sharedBoundsSpec(): FiniteAnimationSpec<Rect> = Motion.standard(Motion.MEDIUM)
