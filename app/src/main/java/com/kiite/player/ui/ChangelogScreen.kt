package com.kiite.player.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.kiite.player.AppInfo
import com.kiite.player.ui.theme.Containers
import com.kiite.player.ui.theme.Spacing

/** 读取打包进 assets 的 CHANGELOG.md。 */
fun readChangelog(context: android.content.Context): String = runCatching {
    context.assets.open("CHANGELOG.md").bufferedReader().use { it.readText() }
}.getOrDefault("（没有找到更新日志）")

/** 应用内更新日志。 */
@Composable
fun ChangelogDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var text by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { text = readChangelog(context) }

    AlertDialog(
        onDismissRequest = onDismiss,
        tonalElevation = 0.dp,
        confirmButton = { TextButton(onClick = onDismiss) { Text("知道了") } },
        title = { Text("更新日志 · v" + AppInfo.VERSION_NAME) },
        text = {
            Box(
                Modifier
                    .heightIn(max = Containers.scrollMaxHeight)
                    .verticalScroll(rememberScrollState()),
            ) {
                MarkdownLite(text)
            }
        },
    )
}

// ── 轻量 Markdown 渲染 ───────────────────────────────────────────────────────
//
// 只覆盖 CHANGELOG.md 真正用到的那几种标记：段标题 `## [版本] - 日期`、
// 小标题 `### 新增`、列表 `- xxx` / `1. xxx`、行内 `**粗体**`、`` `代码` ``、
// `[文字](链接)`。
//
// 刻意不引第三方 Markdown 库：这份文本是应用自己打包的固定文件，语法面很窄，
// 引一个 1~2MB 的库只为渲染十几行字不划算；第三方库的默认排版（大标题、代码块
// 底色）跟对话框的密度也对不上。以前是把整段原文塞进一个 Text —— `##`、`**`
// 原样露出来，用户读到的是一份「源代码」而不是更新说明。

/** 一行解析出来的块类型。 */
private sealed interface MdBlock {
    /** `# 更新日志` 这类整份文档的标题：对话框自己已经有标题，这里不再重复。 */
    data class DocTitle(val text: String) : MdBlock

    /** `## [2.6.0-beta8] - 2026-11-21`：一段版本记录的头。 */
    data class Section(val version: String, val date: String) : MdBlock

    /** `### 新增 / 修复 / 调整`。 */
    data class Heading(val text: String) : MdBlock

    /** 普通正文段落。 */
    data class Body(val text: String) : MdBlock

    /** `- xxx`。 */
    data class Bullet(val text: String) : MdBlock

    /** `1. xxx`。 */
    data class Numbered(val index: String, val text: String) : MdBlock

    /** 空行。 */
    data object Gap : MdBlock
}

private val SectionRegex = Regex("""^##\s*\[?([^\]]+)\]?\s*[-–—]?\s*(.*)$""")
private val NumberedRegex = Regex("""^(\d+)[.)]\s+(.*)$""")

private fun parseMdBlocks(md: String): List<MdBlock> {
    val out = ArrayList<MdBlock>()
    md.lines().forEach { raw ->
        val t = raw.trim()
        when {
            t.isEmpty() -> if (out.isNotEmpty() && out.last() != MdBlock.Gap) out += MdBlock.Gap

            t.startsWith("# ") -> out += MdBlock.DocTitle(t.removePrefix("# ").trim())

            t.startsWith("## ") -> {
                val m = SectionRegex.find(t)
                if (m != null) {
                    out += MdBlock.Section(m.groupValues[1].trim(), m.groupValues[2].trim())
                } else {
                    out += MdBlock.Heading(t.removePrefix("## ").trim())
                }
            }

            t.startsWith("### ") -> out += MdBlock.Heading(t.removePrefix("### ").trim())

            t.startsWith("- ") || t.startsWith("* ") -> out += MdBlock.Bullet(t.substring(2).trim())

            NumberedRegex.matches(t) -> {
                val m = NumberedRegex.find(t)!!
                out += MdBlock.Numbered(m.groupValues[1], m.groupValues[2].trim())
            }

            else -> out += MdBlock.Body(t)
        }
    }
    while (out.isNotEmpty() && out.last() == MdBlock.Gap) out.removeAt(out.lastIndex)
    return out
}

/**
 * 行内标记 → AnnotatedString。
 *
 * 只认三种：`**粗**`、`` `代码` ``、`[文字](链接)`。链接只保留文字：对话框里点不了，
 * 露出完整 URL 反而会把一行字撑成三行。
 */
private fun mdInline(text: String): AnnotatedString = buildAnnotatedString {
    var i = 0
    val bold = SpanStyle(fontWeight = FontWeight.SemiBold)
    val mono = SpanStyle(fontFamily = FontFamily.Monospace)
    while (i < text.length) {
        when {
            text.startsWith("**", i) -> {
                val end = text.indexOf("**", i + 2)
                if (end < 0) {
                    append(text.substring(i))
                    i = text.length
                } else {
                    withStyle(bold) { append(text.substring(i + 2, end)) }
                    i = end + 2
                }
            }

            text[i] == '`' -> {
                val end = text.indexOf('`', i + 1)
                if (end < 0) {
                    append(text.substring(i))
                    i = text.length
                } else {
                    withStyle(mono) { append(text.substring(i + 1, end)) }
                    i = end + 1
                }
            }

            text[i] == '[' -> {
                val close = text.indexOf(']', i)
                if (close > 0 && close + 1 < text.length && text[close + 1] == '(') {
                    val rp = text.indexOf(')', close + 2)
                    if (rp > 0) {
                        withStyle(SpanStyle(textDecoration = TextDecoration.Underline)) {
                            append(text.substring(i + 1, close))
                        }
                        i = rp + 1
                    } else {
                        append(text[i])
                        i++
                    }
                } else {
                    append(text[i])
                    i++
                }
            }

            else -> {
                append(text[i])
                i++
            }
        }
    }
}

/**
 * 更新日志的轻量排版。
 *
 * 段落间距全部走 Spacing 令牌：`scripts/check_ui_tokens.py` 会拦裸 dp。
 */
@Composable
private fun MarkdownLite(markdown: String) {
    // 文件开头那段是「写作规则」（给维护者看的：怎么排版、怎么发版），
    // 弹窗里读它没有任何意义 —— 从第一条版本记录开始渲染。
    val blocks = remember(markdown) {
        val all = parseMdBlocks(markdown)
        val first = all.indexOfFirst { it is MdBlock.Section }
        if (first <= 0) all else all.subList(first, all.size)
    }
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth()) {
        blocks.forEach { block ->
            when (block) {
                is MdBlock.DocTitle -> Unit

                is MdBlock.Section -> {
                    Spacer(Modifier.height(Spacing.md))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            block.version,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = scheme.primary,
                        )
                        if (block.date.isNotBlank()) {
                            Spacer(Modifier.width(Spacing.sm))
                            Text(
                                block.date,
                                style = MaterialTheme.typography.labelSmall,
                                color = scheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                is MdBlock.Heading -> {
                    Spacer(Modifier.height(Spacing.gap10))
                    Text(
                        block.text,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = scheme.onSurface,
                    )
                }

                is MdBlock.Body -> {
                    Spacer(Modifier.height(Spacing.xs))
                    Text(
                        mdInline(block.text),
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                    )
                }

                is MdBlock.Bullet -> {
                    Spacer(Modifier.height(Spacing.xs))
                    Row {
                        Text(
                            "•",
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.primary,
                            modifier = Modifier.width(Spacing.sm),
                        )
                        Text(
                            mdInline(block.text),
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurface,
                        )
                    }
                }

                is MdBlock.Numbered -> {
                    Spacer(Modifier.height(Spacing.xs))
                    Row {
                        Text(
                            block.index + ".",
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.primary,
                            modifier = Modifier.width(Spacing.gap14),
                        )
                        Text(
                            mdInline(block.text),
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                }

                MdBlock.Gap -> Spacer(Modifier.height(Spacing.sm))
            }
        }
    }
}
