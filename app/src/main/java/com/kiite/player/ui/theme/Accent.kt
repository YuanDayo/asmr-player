package com.kiite.player.ui.theme

/**
 * 主色淡底的浓度档位。
 *
 * 同一个语义此前散着三个值：四格选中态 0.10、图标底块与胶囊标签 0.12、
 * 首页那张淡色整卡 0.14 —— 单独看都合理，放在一起就是「每页各写各的」。
 * 收成三档之后至少有两个好处：能搜到「哪些地方用了主色淡底」，
 * 以及调整浓度时改一处、全局一致（不会再出现某页 0.10 另一页 0.14）。
 *
 * 用法：`background(MaterialTheme.colorScheme.primary.copy(alpha = AccentFill.tile))`
 */
object AccentFill {
    /** 选中态：四格 / 分段控件里「当前这一项」的底。 */
    const val selected = 0.10f

    /** 图标底块、胶囊标签（如「已匹配台本」）。 */
    const val tile = 0.12f

    /** 整块淡色底：首页那张「随机混播」卡这类大面积的强调。 */
    const val block = 0.14f
}
