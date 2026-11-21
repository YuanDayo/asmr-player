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
 * 一级标签切换：**串行**交接（先退干净，再进），进场带方向位移。
 *
 * 这里不再做「交叉淡变」，这是这一轮改掉的核心问题：两张页面**同时半透明**时，
 * 卡片的半透明底色（`LocalCardAlpha` 0.78）和磨砂栏会叠在一起，一眼能看出两层内容
 * （曲库的筛选 chip 压在播放页封面上、迷你条的灰底压在走带上），用户读到的就是
 * 「不连贯 / 变成方块」。交叉淡变只在两个页面都是不透明整块时才成立，我们这个不是。
 *
 * 现在的时间线：
 *
 * | 时间 | 发生什么 |
 * | --- | --- |
 * | 0–90ms | 旧页淡出（加速），不留位移 —— 退出不需要表意 |
 * | 80–300ms | 新页淡入（减速）+ 从方向侧推进 1/12 屏 |
 *
 * 交接缝 90ms ≈ 5 帧，肉眼看不到「空一帧」，但足够让旧页退到 0。
 * 位移只挂在进场那一层：两层同时位移才会「错位」，只动一层就是「一页落位」。
 *
 * 曲线与 [sharedBoundsSpec] 同为减速：封面在页面上飞，页面曲线不同步会看到封面在漂。
 */
fun tabTransition(forward: Boolean): ContentTransform {
    val dir = if (forward) 1 else -1
    val enter = slideInHorizontally(Motion.enterAfter(Motion.FLASH, Motion.MEDIUM)) { full ->
        dir * full / 12
    } + fadeIn(Motion.enterAfter(Motion.FLASH, Motion.MEDIUM))
    val exit = fadeOut(Motion.exit(Motion.FLASH))
    return enter togetherWith exit
}

/**
 * 进出播放页：**整页从屏幕底部升起 / 整页滑回屏幕底部**，像一张盖在主页上的 sheet。
 *
 * 这一版把原来的「交叉淡变 + 1/40 屏位移」换掉了，原因是「播放页是基础页面上的一层」
 * 这个模型需要**底页留在原地**：
 *
 * - 进播放页：曲库**一点不动**（`ExitTransition.None`），播放页从屏幕下沿整页推上来，
 *   途中能看见曲库被一帧帧盖住 —— 读起来就是「拉开了一层」；
 * - 回曲库：播放页整页滑下去（`EnterTransition.None` 让曲库原本就铺在那儿），
 *   曲库从上层下面露出来，没有任何淡变，所以不会出现「两层半透明叠成一块灰」。
 *
 * 以前那套淡变的根因在这里：播放页背后是整屏封面、卡片底又是半透明的玻璃，
 * 两层同时半透明时，屏幕上就是「曲库的卡片 + 播放页的走带」糊在一起 —— 用户读成
 * 「割裂」。现在只让一层动、另一层静止，交叉淡变彻底不需要了。
 *
 * 位移给满一屏、时长用 MEDIUM：整页距离用 FLASH(90ms) 会像掉帧，用 LONG 又显得拖。
 */
fun playerTransition(enteringPlayer: Boolean): ContentTransform {
    return if (enteringPlayer) {
        slideInVertically(Motion.enter(Motion.MEDIUM)) { full -> full } togetherWith
            ExitTransition.None
    } else {
        EnterTransition.None togetherWith
            slideOutVertically(Motion.exit(Motion.MEDIUM)) { full -> full }
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
