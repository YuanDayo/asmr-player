# 页面风格统一 + R18 过滤 + 标题识别 实施计划书

> 状态：仅计划，未做任何代码改动。
> 范围：三件事 —— (1) 各页面视觉风格不统一；(2) 「隐藏 R18」只在曲库生效、主页仍显示；(3) 「按标题识别」很久也识别不成功。
> 涉及仓库：`asmr-player`（`C:\Users\lipal\WorkBuddy\kiite\asmr-player`）

---

## 0. 问题总览与根因定位

| # | 用户反馈 | 根因 | 影响面 |
|---|----------|------|--------|
| A | 各页面风格不统一 | 首页 / 曲库 / 设置 / 作品详情各自用了不同的视觉语言（三色点、蓝色 squircle 图标块、蓝色 badge、描边图标混用），且深浅色、R 角、玻璃观感在页间不一致 | 全局观感 |
| B | 「隐藏 R18」只在曲库生效，主页仍显示 | `hideR18` 过滤只写在 `LibraryScreen` 的列表 `base` 里；首页「建议 / 随机播放 / 热门社团声优」直接吃原始 `scan` 与原始 DLsite 元数据，不过滤 | 首页 |
| C | 按标题识别很久也不成功 | `searchByTitle` → `DlsiteClient.search(keyword, cookie)` 依赖 DLsite 站内搜索页 HTML 解析；站内搜索易被年龄确认/风控拦截、无编号标题的关键词清洗后召回差、且没有超时反馈与可重试路径 | 作品详情 |

三项相互独立，可分别落地、分别验证。

---

## A. 各页面风格统一

### A.1 现状诊断（结合 4 张截图）

- **设置页**：iOS 分组列表 + 蓝色 squircle 图标块 + 深灰卡片，是「iOS 玻璃皮肤」语言。
- **曲库页**：蓝色 badge（「已匹配台本」）+ 描边工具图标 + 深色卡片，又是另一套。
- **作品详情页**：蓝色胶囊主按钮 + 深灰次要按钮 + 描边图标，又不一样。
- **首页**：顶部三色小条（蓝/绿/橙 `EditorialTicks`）+ 蓝色 iOS 点缀 + 卡片，混进了第三种语言。

结论：**同一种皮肤（iOS 玻璃）下，不同页面各自发明了局部样式**，没有走同一套 token / 组件。这才是「风格不统一」的本质 —— 不是皮肤本身的问题，而是页面没有统一消费皮肤提供的组件与令牌。

### A.2 目标

在**默认皮肤（iOS 玻璃 / 深浅色跟随系统）**下，四个页面共用同一套视觉语言：
- 强调色：统一用 `colorScheme.primary` 一个来源（去掉首页三色点这类「皮肤外」装饰，或让三色点只存在于「经典」皮肤）；
- 图标：统一一种图标语言（描边 vs 实心、是否带底色块），不要一个页面 squircle 底块、另一个页面纯描边；
- 卡片 / 圆角 / 玻璃：统一走 `AsmrCard`、`Radius.*`、`asmrFrost`/`asmrBarColor`，不要页面自己 `background(surface)` 手搓。

### A.3 实现步骤

**Step 1 — 建立「页面级样式审计表」。**

先人工/脚本过一遍四个页面用到的样式原语，列出「非 token」用法（`Color(0x…)`、裸 `RoundedCornerShape`、裸 `background`、`Icons.Default.X` 与 `Icons.Filled.X` 混用、局部 alpha 魔数）。这份表是后面改动的清单，也作为回归 checklist。

**Step 2 — 统一强调色来源，清理皮肤外装饰。**

- 首页 `EditorialTicks`（`AsmrCard.kt` 第 313–330 行）三色点：**已定 —— 只在「经典」皮肤保留，iOS 玻璃（editorial）皮肤下不显示**（用户 2026-11-22 拍板）。实现上即让 `EditorialTicks` 在 `LocalEditorial.current == true` 时直接 `return`（该函数现在本就是 `if (!LocalEditorial.current) return` 的相反判定，需把「显示条件」从 editorial 反转为 classic，或改由调用点按皮肤决定是否渲染）。参考图首页是纯 iOS 蓝 + 克制配色，玻璃皮肤下首页不再出现三色点。
- 全项目把 `Color(0x...)` 字面量收编到 `theme/` 的 token（若已存在则引用，不存在则新增到统一处），`check_ui_tokens.py` 已有门禁，扩充它扫描 `Color(0x` 字面量。

**Step 3 — 统一图标语言。**

定义两条规则并贯彻：
- 「可点入口图标」= 同一风格（例如统一 `Icons.Outlined.*` 或统一 `Icons.Default.*` + `tint = primary`，不带底色块）；
- 需要「图标底块」的场景（设置分组、快捷入口）统一抽一个 `TintedIconTile(icon, tint)` 组件，四页共用，而不是设置页手搓 squircle、首页手搓图标行。

**Step 4 — 统一卡片与玻璃。**

- 所有「可点卡片」统一 `TapCard` / `AsmrCard`；设置页的分组卡如果现在自己画的 `background`，改成 `AsmrCard` 或复用 `SectionCard`。
- 悬浮栏（迷你条、底导航、顶栏）统一 `asmrBarColor()` + `asmrFrost()`，禁止页面自行 `background(surface.copy(alpha=…))`。

**Step 5 — 深浅色一致性自查。**

截图全是深色，但 `themeMode` 默认 `light`（`SettingsStore.kt` 第 23 行）。确认「跟随系统」时深色下四页的 `surface`/`background`/`onSurfaceVariant` 层级一致；尤其作品详情页的深灰按钮（#2A2A2A 一档）是否与其它页的 `surfaceVariant` 是同一 token，不是手搓色值。

### A.4 验证

- 四页各截一组（浅色 + 深色各一遍），对比强调色、图标、卡片 R 角、玻璃是否一致。
- `check_ui_tokens.py` 扩扫色值字面量，跑绿。
- `compileDebugKotlin` + `testDebugUnitTest`。

---

## B. 「隐藏 R18」主页不生效

### B.1 根因

`hideR18` 的过滤逻辑只存在于 `LibraryScreen.kt` 第 1003 行：

```kotlin
val hitR18Hide = !settings.hideR18 || ratingFilter == 1 ||
    rating != com.kiite.player.core.WorkRating.R18
```

首页三个入口直接绕过它：
1. **「建议」区**（`HomeScreen.kt` 第 152 行）：`scan?.projects.orEmpty().shuffled().take(8)`，无过滤。
2. **「随机播放」**（`MainViewModel.kt` 第 964–973 行）：`library.scan.value?.tracks.orEmpty().shuffled()`，无过滤。
3. **「热门社团 / 声优」**（`MainViewModel.kt` 第 433–440 行）：`knownCircles()`/`knownVoiceActors()` 遍历**全部** `dlsite.value`，R18 作品的社团/声优也一起出现。

且设置页文案（`SettingsScreen.kt` 第 1150 行）写的是「隐藏后**从曲库列表**过滤」，把范围说窄了 —— 用户自然以为「隐藏 R18」应该全局生效。

### B.2 目标

「隐藏 R18」成为**全局**语义：曲库列表、首页建议、随机播放、热门社团/声优、以及其它任何按作品/社团/声优聚合展示的地方，都统一按同一套「是否为 R18」判定过滤；用户主动筛「成人」时（曲库筛选）仍可看到。

### B.3 实现步骤

**Step 1 — 抽出统一的「作品是否应隐藏」判定。**

在 `MainViewModel`（或 `LibraryController`）新增单一事实来源：

```kotlin
/** 作品是否应因「隐藏 R18」被过滤（主动筛成人时不隐藏）。 */
fun shouldHideR18(project: ProjectEntry, forceShowR18: Boolean = false): Boolean {
    if (!settings.value.hideR18) return false
    if (forceShowR18) return false
    return ratingOf(project) == WorkRating.R18
}
```

曲库 `LibraryScreen` 第 1003 行改成调用它（`forceShowR18 = ratingFilter == 1`），保证曲库和首页共用同一判定，避免两处各自实现跑偏。

**Step 2 — 首页「建议」过滤。**

`HomeScreen.kt` 第 152 行：

```kotlin
val suggestProjects = remember(scan, suggestSeed, settings.hideR18) {
    scan?.projects.orEmpty()
        .filterNot { vm.shouldHideR18(it) }
        .shuffled()
        .take(8)
}
```

注意：过滤要在 `shuffled().take(8)` **之前**，否则抽满 8 个之后过滤会不足 8 个；`remember` key 里带上 `settings.hideR18`。

**Step 3 — 首页「随机播放」过滤。**

`MainViewModel.playRandom()` 里，把 `scan.tracks` 先按曲目所属 project 过滤掉 R18：

```kotlin
fun playRandom() {
    val scan = library.scan.value ?: return
    val hidden = scan.projects.filter { shouldHideR18(it) }.map { it.path }.toSet()
    val tracks = scan.tracks.filterNot { it.projectPath in hidden }
    if (tracks.isEmpty()) { say("曲库里没有可播放的音频（R18 已隐藏）"); return }
    val shuffled = tracks.shuffled()
    playTrack(shuffled.first(), queue = shuffled)
}
```

（`TrackEntry` 是否有 `projectPath` 字段需确认；若无，按曲目文件路径反查所属 project，`LibraryController` 里应有 `projectOf(track)` 一类方法，用现成的。）

**Step 4 — 「热门社团 / 声优」过滤。**

`knownCircles()` / `knownVoiceActors()` 改为只统计**未隐藏**作品的社团/声优。需要作品→R18 的映射：DLsite 元数据里有 `r18` 字段（`DlsiteController` 已在用 `enriched.r18`）。把 `hideR18` 闭包传进去，或在 `MainViewModel` 层先算出「可见作品集合」再聚合。

```kotlin
fun knownCircles(): List<String> {
    val hidden = library.scan.value?.projects
        ?.filter { shouldHideR18(it) }?.map { it.path }?.toSet() ?: emptySet()
    return dlsite.value.values
        .filterNot { it.path in hidden || (it.r18 && settings.value.hideR18) }
        .mapNotNull { it.circle }.filter { it.isNotBlank() }.distinct().sorted()
}
```

（具体字段名以 `DlsiteWork` 定义为准，可能有 `r18: Boolean` 或通过 `WorkRating` 推导，需在实现时对齐。）

**Step 5 — 更新设置页文案。**

`SettingsScreen.kt` 第 1150 行改为全局语义，例如：

> 按 DLsite 识别结果与手动分级判断；隐藏后从**曲库、首页建议、随机播放、热门社团/声优**等处过滤，主动筛选「成人」时仍会显示。未分类的作品不受影响。

### B.4 验证

- 造一个 R18 作品 + 一个全年龄作品，分别验证：曲库、首页建议、随机播放、热门社团/声优四处都看不到 R18 作品；关闭开关后四处都恢复。
- 曲库主动筛「成人」时 R18 仍可见。
- `compileDebugKotlin` + `testDebugUnitTest`（补 `shouldHideR18` 单测）。

---

## C. 「按标题识别」很久也不成功

### C.1 根因

`searchByTitle`（`DlsiteController.kt` 第 872–910 行）走 `DlsiteClient.search(keyword, cookie)`，后者（`DlsiteClient.kt` 第 532–555 行）做的是**站内搜索页 HTML 抓取 + 正则解析**。识别「很久也不成功」的可能来源，按概率排序：

1. **站内搜索被年龄确认 / 风控拦截**：`get(DlsiteSearch.url(...))` 请求的是 `/maniax/fsr/=/keyword/…`，R18 关键词容易命中年龄确认页（`isAgeGate` → 直接 error），或触发风控返回登录页（`looksLoggedOut` → error）。用户没登录 DLsite 或 Cookie 过期时，搜索**永远失败**，且当前实现没有「去登录」的引导，用户只能干等。
2. **无编号标题召回差**：`DlsiteTitleKey.of()` 会把标题里所有分隔符、编号、分卷后缀清掉，只剩一段小写关键词。DLsite 站内搜索对「纯标题」的召回不稳定（同名/翻译名多、排序靠 trend），`rank()` 只能对**已经返回**的结果排序，救不了「没搜到」。
3. **无进度/超时反馈**：`searchByTitle` 只有 `loading` 一个态，网络慢时界面一直转圈，没有「搜索超时 / 建议改用编号 / 建议登录」的出口，体感就是「很久也没成功」。

### C.2 目标

- 识别成功率提升：优先走**官方 JSON 接口**（`/product.json?workno=RJ…`，页面 JS 自己用的，不做年龄确认、不依赖登录）作为兜底/主路径；只有拿到编号时才用它。
- 无编号标题：给出「先去登录 DLsite」「换个关键词」「按编号识别」的明确引导，而不是无限转圈。
- 有明确超时与失败反馈，不「很久没反应」。

### C.3 实现步骤

**Step 1 — 优先识别文件夹名里的编号。**

`DlsiteTitleKey.of()` 现在**把 RJ 编号删掉了**。但「按标题识别」的文件夹名里如果本来就有 `RJ01234567`，应该直接走编号识别（`fetch(RJ…)`，官方 JSON 接口，又快又准），根本不用去搜。改法：在 `searchByTitle` 开头先用一个 `CODE` 正则抽编号，抽到就直接 `fetchOne(code)` 并结束，跳过站内搜索。

```kotlin
fun searchByTitle(projectPath: String) {
    val project = projects().firstOrNull { it.path == projectPath } ?: return
    val code = DlsiteTitleKey.extractCode(project.name)   // 新增：抽编号
    if (code != null) { fetchOne(code); return }           // 直接识别，不做搜索
    // …原来走 DlsiteClient.search 的逻辑
}
```

（`DlsiteTitleKey` 已有 `CODE` 正则，把 `of()` 里「删编号」拆出一个 `extractCode()` 复用即可。）

**Step 2 — 站内搜索加超时与可重试出口。**

- `DlsiteClient.search` 内部 `get()` 已有 15s/20s 超时，但 `searchByTitle` 没有把「超时/失败」区分呈现。在 `TitleMatches` 增加 `error` 时带上可操作建议：
  - `isAgeGate` → 「DLsite 需要年龄确认，请先登录并完成年龄确认」
  - `looksLoggedOut` → 「DLsite 登录态失效，请重新登录」
  - 网络异常 → 「搜索超时，请检查网络后重试」
- 界面（`LibraryScreen.kt` 第 2968 起的弹窗）在 `error` 态下给「重试」和「改用编号」两个按钮。

**Step 3 — 无编号标题的召回增强（可选，真机验证）。**

- 用 DLsite 官方 JSON 搜索接口替代/补充 HTML 站内搜索。DLsite 的 `product.json` 是按 `workno` 查单作品；若站内搜索接口不稳定，优先方案是：**引导用户提供编号**，而非硬啃标题搜索。把「按标题识别」的文案改为「优先在文件夹名里保留 RJ 编号；纯标题会尝试站内搜索，可能因年龄确认失败」。

**Step 4 — 增加识别结果落库后的即时反馈。**

`chooseTitleMatch` 已经 `fetchOne(code)` 抓元数据并写映射；确认抓取成功后 `notify("已识别为 RJ…，标题和封面已更新")`，失败则提示「元数据抓取失败，可稍后重试」。让用户明确知道「识别成没成」，而不是弹窗关闭后无声无息。

### C.4 验证

- 真机（模拟器连不上 DLsite）验证：
  1. 文件夹名带 `RJ` 编号 → 点「按标题识别」秒识别；
  2. 纯标题（无编号）→ 站内搜索能返回结果时正常选；被年龄确认拦时给出「去登录」引导而非转圈；
  3. 断网 → 明确超时提示 + 重试按钮。
- 单测：`DlsiteTitleKey.extractCode` 与 `searchByTitle` 的编号直通分支（用本地 mock 的 `DlsiteClient`）。

---

## 1. 实施顺序建议

1. **B（R18 全局过滤）** 优先 —— 改动小、纯逻辑、可单测，且是明确的「行为 bug」。
2. **C（标题识别）** 其次 —— 核心是「编号直通」+「失败可操作」，真机验证。
3. **A（风格统一）** 最后 —— 面最大、最需要逐页回归与视觉比对，放最后单独推进，避免与功能改动互相干扰。

每项落地后：`compileDebugKotlin` → `testDebugUnitTest` → `check_ui_tokens.py` → 对应模拟器/真机截图 → `git diff --check`。

## 2. 风险与注意

- **B 的 `shouldHideR18` 单点化**是关键：曲库与首页必须调同一个函数，否则又会各写一套、再跑偏。`ratingOf` 依赖 DLsite 识别 + 手动分级，未识别作品 `rating` 是 UNKNOWN 不隐藏，保持现状语义。
- **C 的编号直通**要复用 `DlsiteClient.fetch`（官方 JSON 接口），**不要**引入新的抓取路径；站内搜索只在「无编号」时才兜底。
- **A 涉及视觉**，改完必须浅色+深色各截一组对比。三色点处置**已定**：只在「经典」皮肤保留、iOS 玻璃皮肤下不显示（`EditorialTicks` 显示条件从 editorial 反转为 classic）。其余视觉改动落地前仍建议逐项小步提交，便于回滚。
- 以上均为计划，落地前建议逐项小步提交，便于回滚。
