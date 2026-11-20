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
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.TransformOrigin

/**
 * 具名转场：把「这类动作该长什么样」固定下来，页面里只挑名字，不再各自拼 tween。
 *
 * 命名约定：
 * - [asmrEnter] / [asmrExit]：卡片、悬浮条这类「有体积」的元素进出场（高度 + 透明度）；
 * - [collapseEnter] / [collapseExit]：页面骨架收放（头部、四格、走带），给内容腾地方；
 * - [tabTransition]：一级标签切换，按方向轻微位移，避免整页硬切；
 * - [fadeThrough]：同一位置换内容（播放/暂停图标、面板），只淡不位移，不叠加两层动画；
 * - [popIn] / [popOut]：浮层提示（「回到当前进度」这类胶囊按钮）；
 * - [sharedBoundsSpec]：共享元素飞行，曲线与时长跟切页对齐；
 * - [zoomIn] / [zoomOut] / [veilIn] / [veilOut]：曲库点作品 → 详情（从卡片位置放大）；
 * - [pagePushIn] / [pagePushOut] / [pageRevealIn]：二级页（下载 / 作品页 / 选文件）推入推出。
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

/**
 * 一级标签切换：按方向轻微位移 + 淡入淡出。
 *
 * 这里的三个数都是踩着观感调的，不是随手写的：
 *
 * - **进场位移缩到 1/12 屏**（原 1/8）。整屏平移 60dp 加淡入，看起来像「两张纸在互相错开」，
 *   而不是「你要的那一页上来了」。位移越小，注意力越留在内容本身。
 * - **进场曲线用减速**（不是标准曲线）。起步就有速度、最后 30% 慢慢落位，
 *   是「跟手」的来源；标准曲线的两端都慢，观感上更机械。
 * - **退场只留 1/24 屏位移，并且更早淡完**（SHORT，加速）。两页同时半透明的那一小段
 *   会糊成一片，糊多久由退场决定 —— 退场必须先退干净，否则进场的利落全被抵消。
 *
 * 曲线必须与 [sharedBoundsSpec] 一致：播放页封面是在页面上飞的，页面在平移，
 * 两者曲线不同步就会看到封面在屏幕上「漂」。
 */
fun tabTransition(forward: Boolean): ContentTransform {
    val dir = if (forward) 1 else -1
    val enter = slideInHorizontally(Motion.enter(Motion.MEDIUM)) { full -> dir * full / 12 } +
        fadeIn(Motion.enter(Motion.MEDIUM))
    val exit = slideOutHorizontally(Motion.exit(Motion.MEDIUM)) { full -> -dir * full / 24 } +
        fadeOut(Motion.exit(Motion.SHORT))
    return enter togetherWith exit
}

/**
 * 进出播放页：**不横向平移**，只做「从迷你条升起 / 落回迷你条」的原地浮现。
 *
 * 为什么播放页不能用 [tabTransition]：那一版是给「两个平行列表页」设计的，位移表达的是
 * 「你换了一页」。但进出播放页时，屏幕上已经有一样东西在飞了 —— 迷你条封面正沿共享元素
 * 飞到播放页封面的位置。页面同时也横移 1/12 屏，会让封面的落点跟着一路漂移，
 * 观感上像「封面在半路上被谁拽了一下」。
 *
 * 所以这里把页面的运动交给纵向：起点在迷你条那一侧（屏幕下方），
 * 位移只有 1/40 屏。它不承担「表意」，只负责让页面从底栏那个方向上浮出来，
 * 和封面的飞行方向对齐 —— 一横一纵两条线同时跑，谁都不干净。
 *
 * 退场更快更短（1/60 屏 + SHORT）：用户按的是「回去」，不是「看动画」；
 * 而且迷你条此刻正在落位，页面退得慢就会有一帧压在迷你条上。
 */
fun playerTransition(enteringPlayer: Boolean): ContentTransform {
    return if (enteringPlayer) {
        // 进播放页：只有播放页在动（升起 + 淡入），曲库原地快速退干净。
        //
        // 曲库的淡出用 enter() 的减速曲线而不是 exit() 的加速曲线：加速曲线「先慢后快」，
        // 前 100ms 里它几乎还是满不透明，和刚淡入的播放页叠在一起，读起来是「两张页面
        // 重影」（实测抓帧能看到曲库的筛选条压在封面上）。减速曲线一开始就掉得很快，
        // 剩下的尾子只是极淡的一层，视觉上等于「让位」而不是「交叉溶解」。
        val enter = slideInVertically(Motion.enter(Motion.MEDIUM)) { full -> full / 40 } +
            fadeIn(Motion.enter(Motion.MEDIUM))
        enter togetherWith fadeOut(Motion.enter(Motion.SHORT))
    } else {
        // 回曲库：曲库原地淡进来，播放页沉下去 —— 同样是只让一层带位移。
        val exit = slideOutVertically(Motion.exit(Motion.SHORT)) { full -> full / 60 } +
            fadeOut(Motion.enter(Motion.SHORT))
        fadeIn(Motion.enter(Motion.SHORT)) togetherWith exit
    }
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
 * 两者曲线不同步就会看起来「封面在屏幕上漂」。切页用减速曲线，这里就跟着用减速。
 */
fun sharedBoundsSpec(): FiniteAnimationSpec<Rect> = Motion.enter(Motion.MEDIUM)

// ---------------- 曲库：列表 ↔ 作品详情 ----------------

/**
 * 作品详情「从被点的卡片放大进来」。
 *
 * 为什么用缩放原点、不用共享元素：卡片是「封面 + 标题 + 摘要」的三段结构，详情页是一整页
 * 列表，两端没有一一对应的元素（详情页的封面是 132dp，比卡片封面 150dp 还小，飞过去是
 * 「缩」不是「放」）。真正要看的是「点哪儿、从哪儿长出来」，这只需要换一个缩放原点 ——
 * 一个 `graphicsLayer` 的变换，不重排、不重建，掉帧风险比容器变形低一个量级
 * （逐帧重排的历史教训见 HANDOVER 里 2.5.5-beta2 那批）。
 *
 * 与 [veilOut] 配对：**只让一层动**。两层一起缩放会在中途糊成一片，看起来像画面在抖。
 */
fun zoomIn(origin: TransformOrigin): EnterTransition =
    fadeIn(Motion.enter(Motion.MEDIUM)) +
        scaleIn(Motion.enter(Motion.MEDIUM), initialScale = 0.85f, transformOrigin = origin)

fun zoomOut(origin: TransformOrigin): ExitTransition =
    fadeOut(Motion.exit(Motion.SHORT)) +
        scaleOut(Motion.exit(Motion.MEDIUM), targetScale = 0.88f, transformOrigin = origin)

/**
 * 被盖住 / 重新露出来的那一层：只淡，不缩放。
 *
 * 退出比进入短（150ms 对 220ms）：缩放层还没落位时，底下那层要已经「安静」了，
 * 否则两个半透明的画面叠在一起，最后一次缩放会显得脏。
 */
fun veilIn(): EnterTransition = fadeIn(Motion.enter(Motion.MEDIUM))

fun veilOut(): ExitTransition = fadeOut(Motion.exit(Motion.SHORT))

// ---------------- 二级页（下载 / 作品页 / 选文件） ----------------

/**
 * 二级页推入：从右侧推进 1/6 屏 + 淡入。
 *
 * 不是整屏位移：整屏平移时每一帧都在整页重绘，1/6 屏足够表意（iOS 用整屏是因为它连
 * 底层页面一起推，我们底层曲库保持挂载，推太多反而露馅）。
 * 返回时退出走 1/6 屏、加速曲线 —— 退得快一点，用户想的是「回去」而不是「看动画」。
 */
fun pagePushIn(): EnterTransition =
    slideInHorizontally(Motion.enter(Motion.MEDIUM)) { full -> full / 6 } +
        fadeIn(Motion.enter(Motion.SHORT))

fun pagePushOut(): ExitTransition =
    slideOutHorizontally(Motion.exit(Motion.MEDIUM)) { full -> full / 6 } +
        fadeOut(Motion.exit(Motion.SHORT))

/** 被二级页盖住的曲库：进来时原地淡入，不要反向平移（那会像两页在错位）。 */
fun pageRevealIn(): EnterTransition = fadeIn(Motion.enter(Motion.MEDIUM))
