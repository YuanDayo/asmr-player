package com.kiite.player.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.kiite.player.ui.theme.Spacing

/**
 * 法律文本页：使用须知 / 免责声明。
 *
 * 排版直接复用更新日志那套 `MarkdownLite`（`###` 小标题、`- ` 列表、`**粗体**`）：
 * 这两个页面段落多、层级浅，再引一个 Markdown 库只为渲染几段字不划算，
 * 而现在「条款」和「更新日志」的观感也自然是一致的。
 *
 * 外壳不在这里画：它挂在设置页的二级页脚手架里（标题 + 返回 + 可滚动正文），
 * 和「外观与皮肤」「关于」这些子页共用同一套进出场与返回键行为。
 */
@Composable
internal fun LegalTextPage(@StringRes textRes: Int) {
    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        MarkdownLite(stringResource(textRes))
    }
}
