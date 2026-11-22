# 按钮层：回退 or 重做 计划书

> 状态：**已执行方案 A（全部回退）**，2026-11-22 21:2x。
> - 109 处 Miuix 按钮已回退成 Material3 `TextButton` / `IconButton`；
> - 卡片层（`AsmrCard`→`MiuixCard`）、`AsmrMiuixTheme`、`TintedIconTile`、`AccentFill` 保留；
> - 方案 C 的 `AsmrTextButton` 组件做过一版、只在批量条试用，用户看过效果后否决，组件已删除。
> 本文档保留，作为「为什么回退」与「以后要不要做 C」的依据。
> 触发：用户反馈「太丑了」—— 批量条、目标卡、弹窗里的按钮都变成了浅灰底大块。
> 相关文件：`HomeScreen.kt`(13) / `PlayerScreen.kt`(9) / `LibraryScreen.kt`(42) / `SettingsScreen.kt`(45)

---

## 0. 结论先行

丑的根因**不是尺寸**，而是三条叠加：

1. **有底色**：M3 的 `TextButton` 是透明底，Miuix 的 `TextButton` 默认带 `secondaryVariant` 浅灰底。
   一处两处还行，一屏十几处就变成「一堆药丸」——这是观感变重最主要的原因。
2. **内边距翻倍**：Miuix `InsideMargin = PaddingValues(16.dp)`（四边 16），M3 `contentPadding = 12/8`。
   于是同样一行字，Miuix 按钮比原来胖一圈。
3. **字号不可控**：`MiuixTextButton(text: String, …)` 只接受字符串，**字号写死在组件内部**
   （Miuix 的 button 字阶，约 15sp）；项目原来用的是 `labelLarge(14)` / `labelSmall(11)`。
   密集区（批量条、目标卡的 −5/+5）用 15sp 明显过大 —— 这一条**没法靠传参解决**。

附带发现（用于纠正直觉）：Miuix 与 M3 的 `minWidth/minHeight` **完全一样（58 / 40dp）**，
所以「收窄」只能改内边距与最小宽高，改不了「灰块 + 大字号」。上一轮已实测：
收窄后批量条仍显得笨重。

---

## 1. 数据对照

| 项 | Material3 `TextButton`（改前） | Miuix `TextButton`（现在） |
|---|---|---|
| 最小宽 | 58dp | 58dp（相同） |
| 最小高 | 40dp | 40dp（相同） |
| 内边距 | 12 / 8 | **16 / 16** |
| 圆角 | 胶囊（全圆角） | 16dp |
| 底色 | **透明** | **secondaryVariant 浅灰** |
| 文字 | 调用点自定（`Text("x", style=…)`） | **组件内写死，不可传** |
| 图标 | 支持（content 里放 Icon） | **不支持**（只收 String） |

---

## 2. 四个方案

### 方案 A — 全部撤销（回到 beta14 的按钮语言）
- 反向脚本把 109 处 `MiuixTextButton/MiuixIconButton` 改回 `TextButton/IconButton`。
- 保留：`AsmrCard→MiuixCard`、`AsmrMiuixTheme`、`TintedIconTile`、`AccentFill`、`ClassicTicks`（卡片层用户没否定）。
- 观感：按钮回到「透明底 + 小字号」，与 beta14 一致；卡片仍是 Miuix 材质。
- 风险：低。工作量：1 轮脚本 + 4 处手工补回 `style` 参数。
- 缺点：只是回到原样，没解决「四页按钮和 Miuix 卡片不完全同源」的原始诉求。

### 方案 B — 保留 Miuix 按钮，只调参数
- 传 `insideMargin`、`minWidth/minHeight/cornerRadius`（已验证这些参数名可用）。
- **底色做透明**：需要 `colors = ButtonDefaults.textButtonColors(...)`，它要 4 个 `Color`；
  Kotlin 参数名尚未确认（反编译只能看到签名），要试错。
- 上限：**字号仍然改不了**。即使透明底 + 收紧内边距，批量条里 15sp 的中文仍比周围字号大一号。
- 结论：只能改善 60%，不推荐作为唯一方案。

### 方案 C — 自建 `AsmrTextButton`（推荐）
在 `ui/components` 里做一个「项目自己的文字按钮」，外观可控、参数与 M3 对齐：

```kotlin
@Composable
fun AsmrTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    style: TextStyle = MaterialTheme.typography.labelLarge,  // 字号可控（关键）
    contentPadding: PaddingValues = PaddingValues(horizontal = Spacing.md, vertical = Spacing.xs),
    cornerRadius: Dp = Radius.sm,
    filled: Boolean = false,        // 默认透明底，需要强调时才填浅色
    contentColor: Color = MaterialTheme.colorScheme.primary,
)
```
- 视觉规格（四档，按场景取，不再「一处一个样」）：

| 场景 | 字号 | 内边距 H/V | 高度 | 底色 |
|---|---|---|---|---|
| 卡片内联（−5 / +5 / 统计 / 全部） | labelLarge 14 | 8 / 4 | 28 | 透明 |
| 工具区（批量条 7 个操作） | labelMedium 12 | 8 / 4 | 26 | 透明 |
| 弹窗（取消 / 确定 / 删除） | labelLarge 14 | 12 / 8 | 32 | 透明，危险项 `error` 色 |
| 主操作（保存 / 开始使用） | titleSmall 14 semibold | 16 / 10 | 38 | primary 填充 |

- 迁移：复用上一轮的转换脚本（`out/btn_convert.py`），把目标从 `MiuixTextButton` 换成 `AsmrTextButton`，
  再按场景补 `style` / `filled` 两个参数（脚本可对 90% 的调用点自动补默认值）。
- 风险：中（要新增组件 + 一轮全量截图）。
- 收益：**字号与底色都回来了**，且四页共用一套尺寸规格。

### 方案 D — 分层
主操作（保存 / 确定 / 开始使用）用 Miuix `Button`（实心，本来就是 Miuix 语言），
其余文字按钮走方案 C。工作量与 C 接近，但保留了「Miuix 原生主按钮」。

---

## 3. 对比

| | 观感 | 工作量 | 风险 | 解决「灰块」 | 解决「字大」 |
|---|---|---|---|---|---|
| A 撤销 | 回到 beta14 | 小（1 轮） | 低 | ✅ | ✅ |
| B 调参 | 略有改善 | 小 | 中（colors 试错） | ⚠️ 未验证 | ❌ |
| C 自建 | 统一且可控 | 中（1–2 轮） | 中 | ✅ | ✅ |
| D 分层 | 统一 + 主次分明 | 中 | 中 | ✅ | ✅ |

---

## 4. 建议路线

1. **先 A 止损**（本轮就能做完）：把按钮改回 M3，观感立刻回到你说「不丑」的状态；
   卡片/主题/图标块的 Miuix 化保留 —— 那部分你没否定，而且它是四页统一的主体。
2. **再决定要不要 C**：如果希望按钮也吃 Miuix 的圆角与按压反馈，我做 C 的组件 + 规格表，
   你先看一屏截图再决定铺不铺开。**不建议直接铺开**（上一轮就是一次铺 109 处、返工）。
3. A 与 C 互不冲突：C 落地时只是把 A 撤回去的 `TextButton` 再换成 `AsmrTextButton`。

---

## 5. 方案 A 的执行清单（若你选 A）

1. 反向脚本：`MiuixTextButton("X", onClick = Y, …)` → `TextButton(onClick = Y) { Text("X") }`；
   `insideMargin` 映射回 `contentPadding`；`minWidth/minHeight/cornerRadius` 丢弃。
2. 手工补 4 处被丢掉的 `style`（labelSmall / labelMedium）。
3. `MiuixIconButton` → `IconButton`（首页 6、播放页 6）。
4. 删掉 `BatchAction()` 包装（批量条回到直接用 `TextButton`）。
5. 清 import，跑 `compileDebugKotlin` + `testDebugUnitTest` + `check_ui_tokens.py`。
6. 截图回归：首页（首屏 + 目标卡）、批量条、删除弹窗、设置页、播放页 —— 共 5 张。
7. **不撤**：`AsmrCard`(MiuixCard) / `AsmrMiuixTheme` / `TintedIconTile` / `AccentFill` / `ClassicTicks`。

## 6. 验证清单（A 或 C 都适用）

- 目标卡「−5 | 目标 30 分钟 | +5 | 统计」一行不换行、不重叠；
- 批量条按钮行数与高度回到收窄前；
- 弹窗里「取消 / 仅移出曲库」层级清楚（次要 vs 危险）；
- 触控区 ≥ 48dp（紧凑按钮用 `minimumInteractiveComponentSize` 兜住点击范围）；
- 浅色 + 深色各一遍。

---

## 7. 风险与注意

- **不要在未确认视觉前再全量替换**：上一轮 109 处一次性替换，一处参数错（`style` 不可传）就返工一片。
- Miuix 的 `TextButton` 不支持图标，凡带图标的（如「× 退出批量」）无论 A/C 都保持 M3 或改用 `Row + clickable`。
- 若走 C，`AsmrTextButton` 必须**默认透明底** —— 这是这次翻车的直接原因。
