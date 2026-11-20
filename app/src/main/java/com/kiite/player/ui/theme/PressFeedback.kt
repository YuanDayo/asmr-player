package com.kiite.player.ui.theme

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

/**
 * 按压反馈：手指按下时整块缩到 [pressedScale]，松开弹回。
 *
 * 为什么单独抽一个 modifiers：参考站（0M_P OfficialSite）里所有可点元素在按下时都会
 * 轻微「陷一下」（`active:scale-90` 一类的写法），这让触摸有实感 —— 而现在这边除了
 * 涟漪以外没有任何按下的形体反馈，像在点一张贴纸。
 *
 * 性能上的克制（这条是踩过坑才定下来的，见 HANDOVER 2.5.5-beta2）：
 * - 缩放只挂在**按压期间**，松手即回到 1f，静止时这一层没有任何每帧变化；
 * - 不要把它套到「一屏几十个」的列表行上 —— 一次性几十个图层是另一回事；
 *   只给迷你条、导航项、主控按钮这类「一次只有一个」的元素用。
 *
 * 同一份 interactionSource 必须交给 `clickable` / `IconButton`，否则收不到按压事件：
 *
 * ```
 * val interaction = remember { MutableInteractionSource() }
 * Row(Modifier.pressScale(interaction).clickable(interaction, null) { ... })
 * ```
 */
@Composable
fun Modifier.pressScale(
    interaction: InteractionSource,
    pressedScale: Float = 0.96f,
): Modifier {
    if (!Animations.enabled()) return this
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        // 按下要跟手（短），回弹稍长一点才像「弹回来」而不是「跳回去」。
        animationSpec = Motion.standard(if (pressed) Motion.SHORT else Motion.MEDIUM),
        label = "pressScale",
    )
    return this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}
