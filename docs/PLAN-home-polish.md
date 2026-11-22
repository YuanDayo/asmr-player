# 首页体验优化实施计划书

> 状态：仅计划，未做任何代码改动。
> 范围：底部播放栏毛玻璃一致性、上滑半屏闪帧、主页 DLsite / asmr.one 入口、次级页过渡动画、使用须知与免责声明、首次启动功能介绍。
> 涉及仓库：`asmr-player`（`C:\Users\lipal\WorkBuddy\kiite\asmr-player`）

---

## 0. 总览

| # | 任务 | 优先级 | 涉及文件 | 风险 |
|---|------|--------|----------|------|
| 1 | 底部播放栏按压 / 非按压毛玻璃不一致 | 高 | `AsmrCard.kt`、`AppRoot.kt` | 低（改动集中） |
| 2 | 上滑进入半屏播放页闪一下 | 高 | `AppRoot.kt`、`Theme.kt` | 中（涉及转场时序） |
| 3 | 主页新增 DLsite / asmr.one 入口 | 中 | `HomeScreen.kt`、`AppRoot.kt`、`MainViewModel.kt` | 低 |
| 4 | 主页次级页过渡动画 | 中 | `HomeScreen.kt` | 低 |
| 5 | 使用须知（使用条款）与免责声明 | 中 | `SettingsScreen.kt`、新增文本资源 | 低 |
| 6 | 首次启动功能介绍 | 中 | `AppRoot.kt`、`SettingsStore.kt`、新增 UI | 低 |

六项相互独立，可逐项落地、逐项验证，不互相阻塞。

---

## 1. 底部播放栏毛玻璃一致性

### 1.1 现状与根因

截图对比显示：同一根迷你播放条，**非按压**时毛玻璃明显（身后封面/文字被高斯糊成色块），**按压**时几乎变成一块实色白板，毛玻璃感消失。

根因有三处，叠加放大了这个差异：

1. **`asmrFrost()` 的「滚动时降半径」逻辑误伤按压。**
   `AsmrCard.kt` 第 185–190 行：
   ```kotlin
   val scrolling = LocalScrollActivityHolder.current.scrolling
   val frostStyle = HazeStyle(
       ...
       blurRadius = if (scrolling) CardDim.box10 else CardDim.box20,
   )
   ```
   半径从 20dp 掉到 10dp 只是「变糊一点」，但结合第 2 点，感知上就变成「模糊消失」。

2. **`pressScale` 缩放整个胶囊，缩放会重采样模糊纹理。**
   `AppRoot.kt` 第 1369 行 `MiniPlayer` 的 `Box` 上挂了 `.clickable { onOpen() }`，而 `pressScale` 只挂在里面的播放 `IconButton` 上（第 1426 行）——但整条卡片本身**没有统一的按压状态管理**。问题在于：`asmrFrost` 的 `hazeEffect` 是把「身后内容」捕获成纹理再模糊，**捕获发生在绘制阶段**；一旦卡片发生 `graphicsLayer` 缩放（哪怕只是播放键那 0.88 倍），模糊纹理与卡片的对位关系会被放大/偏移，边缘露出未经模糊的底，读起来就是「一按就坏」。这是 HANDOVER 里「一按磨砂就消失」那条的老问题变体。

3. **`background(asmrBarColor())` 与 `asmrFrost()` 重复叠底。**
   `AppRoot.kt` 第 1329–1330 行：
   ```kotlin
   .background(asmrBarColor())
   .asmrFrost()
   ```
   `asmrFrost()` 内部已经用 `HazeStyle(backgroundColor = scheme.background, tints = [surface α0.72])` 叠了色。外面再叠一层 `asmrBarColor()`（`surface α0.55~0.88`）会让整体更「实」、更白，削弱透底感；按压态下任何一方 alpha 变化都会被这一层进一步抹平。

### 1.2 目标

按压与非按压状态，毛玻璃观感一致：始终是「高斯糊身后内容 + 半透明 tint」，按压只允许**整体轻微变暗/缩 0.96 倍**这种不破坏模糊层的反馈，不允许「模糊消失、变实色」。

### 1.3 实现步骤

**Step 1 — 统一按压反馈源，改挂整卡、去掉对模糊层的缩放冲击。**

在 `MiniPlayer` 里把按压状态提升到整卡：

```kotlin
val pressInteraction = remember { MutableInteractionSource() }
Box(
    Modifier
        .fillMaxWidth()
        .shadow(Elevation.raised, shape)
        .clip(shape)
        // 先上模糊，再叠 tint —— 顺序固定，别再在模糊层外重复 background
        .asmrFrost()
        .glassEdge(shape)
        // 整卡按压：只变暗 + 轻微缩放，缩放要挂在模糊层「之后」的 graphicsLayer，
        // 避免缩放作用于模糊纹理
        .pressScale(pressInteraction, pressedScale = 0.985f)   // 见 Step 2 说明
        .pointerInput(Unit) { /* 现有拖拽逻辑不变 */ }
        .clickable(
            interactionSource = pressInteraction,
            indication = null,
            onClick = onOpen,
        ),
)
```

关键点：
- 播放键的 `IconButton` 继续用自己那个 `playInteraction`，但**不要**再单独 `.pressScale` 缩放整键（改用它自带的涟漪，或保留 0.88 但确认它不触发整卡模糊重采样——因为现在模糊在 `Box` 层，键的缩放只影响图标本身，不影响卡片模糊层，问题其实出在原来 `background`+`asmrFrost` 的顺序和 alpha 叠加，见下）。

**Step 2 — 修 `asmrFrost()` 的滚动降级与按压判定。**

`AsmrCard.kt` 的 `asmrFrost()`（以及 `asmrBackdropBlur()`）里，`scrolling` 分支是「模糊变糊」的元凶之一。改成：

```kotlin
val scrolling = LocalScrollActivityHolder.current.scrolling
val frostStyle = HazeStyle(
    backgroundColor = scheme.background,
    tints = listOf(HazeTint(scheme.surface.copy(alpha = if (light) 0.72f else 0.60f))),
    // 滚动时也保持同一档半径，不再 20→10 跳变；
    // 掉帧由 LocalScrollActivityHolder 之外的「整块纯色」退路兜底（低端机）。
    blurRadius = CardDim.box20,
    noiseFactor = 0f,
)
```

理由：20↔10 的半径跳变本身就是「按压/滚动瞬间糊一下」的来源；把半径恒定，只在「实时毛玻璃整体关闭」时才退纯色，观感更连续。

**Step 3 — 去掉 `MiniPlayer` 里重复的 `background(asmrBarColor())`。**

删掉 `AppRoot.kt` 第 1329 行 `.background(asmrBarColor())`，让 `asmrFrost()` 独立负责「糊 + tint」。`asmrFrost()` 内部在非 editorial 皮肤 / 关闭 liveBlur 时已经会 `background(asmrBarColor())` 兜底（`AsmrCard.kt` 第 179 行），所以删掉不损失退路。

**Step 4 — 按压反馈统一为「暗化 + 轻微缩放」，且缩放幅度收敛。**

把 `pressScale` 的 `pressedScale` 从默认 0.96 收紧（例如整卡 0.985f，播放键保留 0.88f），并保证按压态用 `indication` 或 `drawWithContent` 做一层 `surface` 变暗（alpha 0.04~0.06），让「按下去」有反馈但**不破坏模糊纹理的采样一致性**。

### 1.4 验证

- 用 `scripts/ui_verify.sh` 现有截图模式，分别抓「非按压」与「按压」（`input swipe x y x y 1000` 按住不放再 `screencap`）两张，对比模糊半径是否一致。
- 真机重点验证：手指按住迷你条不放时，模糊不消失、不跳变。
- 回归 `check_ui_tokens.py`（若改了颜色 token）与 `compileDebugKotlin`。

---

## 2. 上滑进入半屏播放页「闪一下」

### 2.1 现象与根因

用户反馈「上滑进入半屏播放页会闪一下」。结合代码，最可能来源是 `AppRoot.kt` 第 347–367 行的转场效应：

```kotlin
LaunchedEffect(tab, playerOpen) {
    ...
    scrollActivity.set(true)                 // 暂停实时磨砂
    delay(Motion.ms(Motion.MEDIUM + 80) * stretch)
    scrollActivity.set(false)                 // 恢复磨砂
    if (coverFlying) { delay(...); coverFlying = false }
}
```

`scrollActivity.set(true)` 会让 `asmrFrost()` 与 `asmrBackdropBlur()` 在「页面正在动」期间**整块退回纯色**（历史上为省掉每帧全屏捕获）。于是上滑那一下：迷你条/播放页的玻璃底「毛玻璃 → 纯色 → 毛玻璃」快速切换，用户看到的就是「闪一下」。

叠加因素：`coverFlying` 窗口内，迷你条封面槽位的灰底板被藏起来（`LocalCoverFlight`），飞行动画开始/结束那一帧封面「有 → 无 → 有」，也会造成视觉闪跳。

### 2.2 目标

上滑全程视觉连续：玻璃底一直保持模糊，封面飞行动画无中间空白帧。

### 2.3 实现步骤

**Step 1 — 把「切页暂停磨砂」的退路从「整块纯色」改成「只收半径、不上色」。**

当前 `asmrFrost()` 在 `!LocalLiveBlur` 时退纯色没问题，但在 `scrolling` 时也退纯色是过度设计（注释里写的是历史版本「整块退回纯色」，后来已改成「只收半径」，但 `scrollActivity` 这条全局路径仍会触发收半径/纯色的分支）。统一为：**滚动时不退纯色、只保持同一半径**（即第 1.3 Step 2 的改动），这样 `scrollActivity.set(true)` 不再引起视觉跳变。

**Step 2 — 缩小 `scrollActivity.set(true)` 的作用窗口。**

`Motion.MEDIUM + 80` 的窗口过长，把「无模糊」的时间拉长。改为只在上滑的**拖动阶段**（`lifting == true`）暂停，松手后立即恢复，且恢复用淡入而非硬切：

```kotlin
LaunchedEffect(lifting, playerOpen) {
    if (lifting) {
        scrollActivity.set(true)
    } else {
        // 松手/补间阶段立刻恢复磨砂，避免「毛玻璃闪回」
        scrollActivity.set(false)
    }
}
```

把原来 `LaunchedEffect(tab, playerOpen)` 里的 `scrollActivity` 控制拆出来，`coverFlying` 窗口逻辑保留但独立。

**Step 3 — 封面飞行窗口内不「藏灰底」，改成「灰底淡出」。**

`LocalCoverFlight` 现在把封面槽位底板设成 `Transparent`（`AppRoot.kt` 第 1383–1388 行），飞行的起终点会「灰方块突然消失」。改为：飞行窗口内让灰底 `animateFloatAsState` 淡到 0 而非瞬时置 `Transparent`，落点同理由 0 淡回。这样封面起飞/落下时底板的消失是连续的。

### 2.4 验证

- 用 `transition` 模式抓上滑进出半屏的连续帧（`mid_f0..f4`、`back_b0..b2`），确认无「纯色 ↔ 毛玻璃」跳变帧。
- 重点看飞行的 f0 与落点帧，灰底是否连续。

---

## 3. 主页新增 DLsite / asmr.one 入口

### 3.1 目标

在首页给 DLsite（已购作品 / 在线）与 asmr.one（免费资源站）各放一个清晰入口，点进去落到真实的 DLsite / asmr.one 页面，不留「假入口」。

### 3.2 落点（复用现有路由，不新造页面）

| 入口 | 目标行为 |
|------|----------|
| DLsite | 复用曲库「DLsite 在线」页：`LibraryIntent` 带 `source = "dlsite"` 进曲库，或直接打开 DLsite 登录/已购子页（`DlsiteDownloadScreen` / 设置里的 DLsite 区块） |
| asmr.one | 复用曲库「asmr.one」在线搜索页：`LibraryIntent(source = "asmrone")` 进曲库在线搜索 |

需要先确认 `LibraryIntent` 是否已有「直接落在 DLsite 在线 / asmr.one」这个字段；若无，新增 `source: String? = null`（取值 `"dlsite"` / `"asmrone"`），曲库 `LibraryContent` 消费时切到对应云端标签。

### 3.3 布局插入点

在首页 `QuickActions`（2×2 快捷入口）之后、`RecentCard` 之前，新增一个 `item(key = "sources")` 区块，两枚并排 `TapCard`：

- **DLsite**：图标 + 「DLsite」标题 + 副标题「已购 · 在线」；点击 `openLibraryWith(LibraryIntent(source = "dlsite", resetFilters = true))`。
- **asmr.one**：图标 + 「asmr.one」标题 + 副标题「免费资源站」；点击 `openLibraryWith(LibraryIntent(source = "asmrone", resetFilters = true))`。

两枚卡片走现有 `TapCard`（自带按压反馈，天然符合第 1 项的观感要求）。

### 3.4 实现细节

- `HomeScreen.kt`：新增 `SourcesSection(onOpenDlsite, onOpenAsmrOne)` 组件，放在 `QuickActions` 与 `RecentCard` 之间；需要 `onOpenLibraryWith` 闭包（已有 `openLibraryWith`）。
- `MainViewModel.kt` / `LibraryScreen.kt`：`LibraryIntent` 增 `source` 字段；曲库消费后把云端来源切到对应 tab（DLsite 在线 / asmr.one）。
- 图标：DLsite 用现有品牌相关图标或 `Icons.Default.ShoppingBag` 类；asmr.one 用 `Icons.Default.CloudDownload` / `Public`。避免引入新图标资源，除非已有。

### 3.5 验证

- `home` / `home2` 模式截图确认两个入口渲染、点击后落到正确云端页。
- 确认「DLsite 未登录」时点击有明确提示（复用现有 `vm.say("请先在设置里登录 DLsite")` 或跳登录页）。

---

## 4. 主页次级页过渡动画

### 4.1 现状

`HomeScreen.kt` 第 437–442 行，二级页直接 `when (subPage) { ... }` 硬切，无进出场动画：

```kotlin
when (subPage) {
    HomeSubPage.HISTORY -> HomeHistoryPage(vm) { subPage = null }
    HomeSubPage.STATS -> HomeStatsPage(vm) { subPage = null }
    HomeSubPage.PURCHASES -> PurchasesPage(vm) { subPage = null }
    null -> Unit
}
```

### 4.2 目标

进入/退出二级页有连贯过渡（从右侧滑入 / 滑出回右侧，或淡入淡出），与播放页转场风格一致，不打断返回键手势。

### 4.3 实现步骤

**Step 1 — 用 `AnimatedContent` 包裹二级页切换。**

```kotlin
AnimatedContent(
    targetState = subPage,
    transitionSpec = {
        if (targetState == null) {
            // 返回主页：二级页向右滑出、主页淡入
            (slideInHorizontally { -it / 3 } + fadeIn())
                .togetherWith(slideOutHorizontally { it / 3 } + fadeOut())
        } else {
            // 进入二级页：主页左移淡出、二级页从右滑入
            (slideInHorizontally { it / 3 } + fadeIn())
                .togetherWith(slideOutHorizontally { -it / 3 } + fadeOut())
        }
    },
    label = "homeSubPage",
) { page ->
    when (page) {
        HomeSubPage.HISTORY -> HomeHistoryPage(vm) { subPage = null }
        HomeSubPage.STATS -> HomeStatsPage(vm) { subPage = null }
        HomeSubPage.PURCHASES -> PurchasesPage(vm) { subPage = null }
        null -> Unit
    }
}
```

注意：`AnimatedContent` 的 `null -> Unit` 分支不能省略主页内容，因为主页 LazyColumn 在 `AnimatedContent` **外面**；这里 `AnimatedContent` 只负责二级页的进出场，主页本身保持原位（用 `Box` 叠放，二级页盖在上面）。

**Step 2 — 用 `Box` 叠层而非 `when` 替换。**

把主页 `LazyColumn` 保留在底层 `Box`，二级页作为上层 `AnimatedVisibility` / `AnimatedContent` 盖上来，`contentAlignment` 默认。这样主页不会在切页时被卸载重建（避免状态丢失与二次扫描）。

**Step 3 — 复用现有动效 token。**

用 `Motion.enter(Motion.SHORT)` / `Motion.exit(Motion.SHORT)`（`ui/theme/MotionTransitions.kt`）而非硬编码 tween，与播放页/曲库转场一致。

### 4.4 验证

- `home2` 模式截图：进历史/统计/已购的中间帧（`animator_duration_scale 10` 拉慢），确认滑入方向、无鬼影。
- 返回键退出动画同理。

---

## 5. 使用须知（使用条款）与免责声明

### 5.1 目标

在设置页新增「使用须知」「免责声明」两个可读条目（或合并为一个「使用条款与免责声明」入口），展示正式文案。文案需要法律向措辞，但本项目是个人 ASMR 播放器，重点覆盖：

- **使用须知 / 使用条款**：本应用仅用于播放用户自有/已授权音频；不提供任何音频内容；用户对导入内容负责；禁止用于侵权用途；DLsite/asmr.one 仅作为用户自行访问的第三方站点入口，本应用不为其内容背书。
- **免责声明**：本应用与 DLsite、asmr.one 等第三方无隶属关系；第三方内容版权归原作者；用户因使用第三方服务产生的问题本应用不承担责任；应用不存储、不上传用户音频。

### 5.2 实现步骤

**Step 1 — 文案落库。**

在 `app/src/main/res/values/strings.xml`（或独立 `values/terms.xml`）新增两段长文本资源（`terms_of_use`、`disclaimer`），避免硬编码在 Kotlin 里，便于后续多语言。

**Step 2 — 设置页新增入口。**

`SettingsScreen.kt` 新增一个分组「关于与法律」，含：
- `使用须知`（打开 `TermsPage`，滚动显示 `terms_of_use`）
- `免责声明`（打开 `DisclaimerPage` 或同一个 `LegalTextPage(title, textRes)` 复用）
- （可选）`隐私说明`：说明本应用本地存储、不上传用户数据。

复用现有 `MarkdownLite`（`ChangelogScreen.kt` 里那个按行解析的轻量渲染器）渲染条款，不引第三方 Markdown 库。

**Step 3 — 页面脚手架。**

新增 `LegalTextPage(title, content)`，用 `HomeSubPageScaffold` 同款二级页脚手架（标题 + 返回 + 可滚动正文），返回键退出。

### 5.3 验证

- `features4` / 新增 `legal` 模式截图确认两条目可点、正文可滚、返回正常。
- 文案走 `check_ui_tokens.py`（若涉及颜色）与 `compileDebugKotlin`。

---

## 6. 首次启动功能介绍

### 6.1 目标

首次启动（或全新安装）时，展示一次功能介绍引导，覆盖核心能力：播放本地音频、DLsite/asmr.one 在线获取、台本阅读、下载与解压入库、设置与主题。之后不再自动弹出（可在「关于」里手动查看）。

### 6.2 现状

`AppSettings.seenVersion` 用于「版本更新后弹更新日志」，`seenVersion == null` 时静默记录不弹（`AppRoot.kt` 第 400–406 行）。没有独立的「首次启动」标记。

### 6.3 实现步骤

**Step 1 — 新增持久化标记。**

`SettingsStore.kt` 的 `AppSettings` 增 `onboardingSeen: Boolean = false`，并在读写映射里加对应 key（`Keys.ONBOARDING_SEEN`）。注意遵循现有 `DataStore` 读写范式（`p[Keys.X] = it` / `p.remove(...)`）。

**Step 2 — 新增引导 UI。**

新建 `OnboardingScreen.kt`（或 `ui/onboarding/`），用 `HorizontalPager`（`androidx.compose.foundation.pager`）做 3–5 页：
1. 欢迎 + 核心定位（本地 ASMR 播放器）
2. 曲库扫描 / 导入
3. DLsite 与 asmr.one 在线获取
4. 台本阅读 / 下载解压
5. 主题与设置 + 「开始使用」

每页一张示意（可用现有图标 + 文案，不造图）+ 底部「下一页 / 跳过 / 开始使用」；圆点指示器。

**Step 3 — 接入启动流程。**

`AppRoot.kt`：在 `onboardingSeen == false` 时，于主 Scaffold 之上叠一层 `OnboardingScreen`，点「开始使用」后 `vm.markOnboardingSeen()`（在 `MainViewModel` 增方法，写 `SettingsStore`）。与 `autoChangelog` 弹窗互斥（首次启动不叠加弹更新日志）。

**Step 4 — 手动查看入口。**

「关于」分组里增「功能介绍」，点开重看引导。

### 6.4 验证

- 新增 `onboarding` 模式：全新 userdata（`WIPE=1`）启动，确认引导出现、翻页、跳过、开始使用后不再出现。
- 非首次启动（保留 userdata）不再弹出。

---

## 7. 实施顺序建议

1. **第 1 项 + 第 2 项**（毛玻璃一致性 + 闪帧）一起做——都动 `AsmrCard.kt` 的 `asmrFrost()` 与 `AppRoot.kt` 的 `MiniPlayer`/转场，共享同一轮回归（`transition` 模式）。
2. **第 3 项 + 第 4 项**（主页入口 + 次级页动画）一起做——都在 `HomeScreen.kt`，一轮 `home`/`home2` 截图回归。
3. **第 5 项**（条款/免责）独立做。
4. **第 6 项**（首次启动引导）最后做，需要 `WIPE=1` 的干净环境验证。

每轮结束后跑：`compileDebugKotlin` → `testDebugUnitTest` → `check_ui_tokens.py` → 对应模拟器模式截图 → 视情况 `git diff --check`。

## 8. 风险与注意

- **模糊层与缩放的交互**（第 1、2 项）是本计划最微妙处：`hazeEffect` 的纹理捕获发生在绘制阶段，任何对模糊层本身做 `graphicsLayer` 缩放/位移都可能引发重采样跳变。原则是「模糊层保持在最底层，缩放/变暗只在模糊层之上、之内的内容层做」。
- **`scrollActivity` 的语义**要统一：它现在是「暂停实时模糊」的全局开关，改完后要确保低端机仍能通过「实时毛玻璃」开关整体退回纯色，不牺牲性能退路。
- **`LibraryIntent.source` 新增字段**需保证旧调用点不破（给默认值 `null`），并确认曲库消费一次清空、不残留（沿用现有「消费即清空」约定）。
- **首启引导与更新日志互斥**：全新安装同时满足 `onboardingSeen==false` 与 `seenVersion==null`，必须只弹引导、不叠更新日志。
- 以上均为计划，落地前建议逐项小步提交，便于回滚。
