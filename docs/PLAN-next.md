# 下一批开发计划（交给能力较弱的模型执行）

> 本文件是**交接文档**：把当前进度、接下来要做什么、每一步怎么验证、本地文件目录写清楚，
> 让接手的模型能照着做，不用重新摸索。

---

## 0. 当前进度（接手前必读）

### 0.1 上一个模型留下的半成品（已收尾）

上一轮被打断时工作区有 **2 个文件有未提交改动**，本轮已经编译验证并提交：

| 文件 | 改了什么 | 状态 |
|---|---|---|
| `app/src/main/java/com/kiite/player/ui/theme/Theme.kt` | 新增 `TopBarMetrics`（`safeTop` + `bar`，`total = safeTop + bar`）与 `LocalTopBarMetrics` | 已提交，编译通过 |
| `app/src/main/java/com/kiite/player/ui/AppRoot.kt` | 顶栏从 `Scaffold.topBar` 抽成独立浮层 `FloatingTopBar`，改用 `safeDrawing.only(Top+Horizontal)` 避刘海 | 已提交，编译通过 |

「各页面顶部留白」也一并做完了，细节见 §1 的执行结果。

### 0.2 尚未开始的需求

§1（含 §1.1 大标题进滚动容器）、§2、§3（含 §3.1 展开态整页玻璃）、§4 已完成；
**§5–§8 还没动**，逐个做。

---

## 1. 收尾「顶栏浮层化」（继续上一个模型做了一半的活）

### 目标
顶栏不再用 `Scaffold.topBar`（它会按栏高把内容压下去，导致滚动内容进不了顶栏后面，半透明磨砂条没东西可虚化）。改成**浮层**，各页面自己用 `contentPadding(top = 状态栏 + 栏高)` 让首屏。

### 现状（已改的部分）
- `Theme.kt` 已加 `TopBarMetrics` 与 `LocalTopBarMetrics`。
- `AppRoot.kt` 已把顶栏抽成 `FloatingTopBar`（浮层），并在 `CompositionLocalProvider` 里 `LocalTopBarMetrics provides topBarMetrics`。

### 执行结果（2026-11-21，本机 Windows + 模拟器）

§1 已完成，见提交 `feat(ui): 各页面让出浮层顶栏`。与原计划的差异（都是照着截图改的）：

- **`Theme.kt` 漏 import `Dp`**：上一个提交的 `TopBarMetrics` 用了 `Dp` 没 import，一直编译不过，补上。
- **曲库**：窄屏 / 宽屏两个根的 `Column` 让出 `safeTop`；`ProjectList` 的 `LazyColumn` 加 `contentPadding(top = bar)`；`ProjectDetail` 改成**整列**让出 `bar`，它的 `LazyColumn` 没再加 —— 上面那排「返回 / 写入台本标签」是固定行，整列让一次就够，列表再加会多出一截空档。
- **设置**：`SettingsHome` 只让 `safeTop`，不让整条栏高 —— 让 `total` 会在「设置」大标题上方留一条 48dp 空带，看着像漏了一格；`SettingsSubPage` 顶上是返回行，按 `total` 让位。
- **播放**：新增 `topBarReserve`（沉浸模式为 0），同时扣进 `avail`、`travel`、`sheetHeight` 三处几何计算，只改 draw 不改测量会顶掉封面卡。

遗留观察：固定头部 + 列表之间会多出约 48dp 空档（详情页最明显），这是「浮层不吃首屏」换来的；彻底顺掉得把大标题挪进滚动容器（iOS 那种做法），不在 §1 范围。→ **已解决，见 §1.1。**

未验证：横屏双栏（模拟器这次 `System UI isn't responding`，轮转后 adb 掉线），下次上真机或稳态模拟器再补一张。→ **已验，见 §1.1。**

### 1.1 大标题进滚动容器（iOS large title）

用户看过 §1 的截图后要求「把大标题挪进滚动容器（iOS 那种）」，即宁可动头部结构也要把 48dp 空档消掉。已完成：

- **窄屏曲库**：`RootHeader` 不再固定在顶，改由 `ProjectList` 的 `header` 参数当 `LazyColumn` 第一项（`item(key = "root-header")`），筛选头紧随其后（`item(key = "filter-header")`）。上滚时两者一起滚进磨砂条后面。
- **列表顶部留白删掉**：`contentPadding` 只留 `bottom`。那截 `top = bar` 就是空档来源 —— 头部已经占着顶部，列表再让一截就多出一块空白。
- **筛选头用 `@Composable` 局部 lambda 打包**（`filterBlock`），同一段代码窄屏进列表、宽屏留在栏外，避免复制两份。顺手去掉原来「上滑收起筛选头」的 `AnimatedVisibility`（现在它自己滚走）。
- **不加新滚动组件**：`TrackTopBar(LazyListState)` 按 `firstVisibleItemScrollOffset / 56dp` 推进，第 2 项起直接给 1；标题成为第 0 项后语义天然对齐，顶栏组件一行没改。
- **窄屏作品详情补磨砂**：大标题不再给详情页顶部垫位，透明栏会留一条空带，所以 `ProjectDetail` 加 `frostTopBar`（窄屏传 true，`TopBarAlwaysFrosted()`），宽屏右栏不强制。
- **宽屏顶栏保持透明**：宽屏左栏标题固定不滚，「小标题接手」不成立；磨砂条一淡入就会盖住那颗固定的大标题（实测第一版就是这样糊掉的），所以 `header == null` 时改调 `TopBarClear()`。

**横屏双栏实测**（1080x2400 模拟器轮转，这次没有再 ANR）：左栏标题 / 统计 / 筛选行固定，列表在下方滚动、顶栏不介入；右栏点开作品正常，返回行落在状态栏下。截图 `verify-topbar/w4_wide_scrolled.png`、`w10_wide_detail.png`。

### 原计划步骤（已完成，留档）
1. **编译**：`./gradlew -p <repo> :app:compileDebugKotlin --offline --console=plain`（先只编译，不打包），修括号 / import 对齐。
2. **给各页面加顶部留白**（核心，逐页做）：

   **曲库 `LibraryScreen.kt`**
   - 根容器（`LibraryContent` 的 `Box` / `Column(Modifier.fillMaxSize())`）加 `safeTop` 留白（用 `LocalTopBarMetrics.current.safeTop`，或等价 `WindowInsets` padding）。
   - `ProjectList` 的 `LazyColumn` 加 `contentPadding(top = LocalTopBarMetrics.current.bar)`。
   - `ProjectDetail` 的 `LazyColumn`（新/旧版）同样加 `top = bar`。
   - `RootHeader`（大标题 + 筛选行）是固定头部，只需让出 `safeTop`，不放进滚动 contentPadding。

   **设置 `SettingsScreen.kt`**
   - 设置是整页可滚、没有固定头部，所以主页 `SettingsHome` 与子页 `SettingsSubPage` 的 `verticalScroll` 容器加 `contentPadding(top = LocalTopBarMetrics.current.total)`。
   - 注意：设置跑在 Miuix 主题里，`LocalTopBarMetrics` 是 app 主题的 CompositionLocal，确认它在 Miuix 主题包裹内仍可取到（若取不到，在外层先 `val m = LocalTopBarMetrics.current` 再传进内层）。

   **播放 `PlayerScreen.kt`**
   - 展开态让位（`safeTop + bar`），折叠态仍铺满（沉浸封面要顶到屏幕边）。
   - 横屏 `wide` 分支同样处理。

3. **验证**：`scripts/ui_verify.sh` 抓曲库 / 设置 / 播放页首屏与滚动后截图，确认：
   - 首屏内容不被顶栏压住；
   - 上滑后内容能滚进磨砂条后面并被虚化（毛玻璃不再「消失」）。

### 这一步同时解决的 bug
- **「点按滑动时毛玻璃消失」**：旧结构里滚动内容进不了顶栏后面，磨砂条底下只有背景，看起来像「毛玻璃消失」。浮层化 + 内容穿过顶栏后自然恢复。
- **「沉浸模式异性屏顶部遮挡」**：旧代码只避 `statusBars`，没避刘海/挖孔。改用 `safeDrawing.only(Top+Horizontal)` 后不再遮挡。

---

## 2. 播放页「点放大台本 / 播放列表没反应」

### 文件
`app/src/main/java/com/kiite/player/ui/PlayerScreen.kt`

### 排查点
- `readMode`（阅读模式，点「台本全屏阅读」图标）、`showPlaylist`（播放列表）两个布尔状态的切换逻辑。
- `contentKind`（台本 / 播放列表 / 图片）与 `Crossfade` 的 targetState。
- 放大逻辑可能与「播放页两档」（折叠/展开）的 `progress` 状态互相干扰：阅读模式/全页大图应强制展开，但当前可能没生效。

### 预期
点「放大台本」→ 台本卡整页展开；点「播放列表」→ 列表整页展开；点图片 → 图片浏览器。

### 执行结果（2026-11-21）

已完成，见提交 `fix(player): 播放列表 / 台本全屏从折叠态也有落点`。两个真实成因都不是「开关没切」：

- **`canCollapse` 少了两个条件**：折叠态点「播放列表」时头部四格被收起，面板拿到 `avail * progress = 0` 的高度，屏幕上什么都不变。改成 `!isBig && !reading && !showPlaylist && !showImages`，靠既有的 `open = expanded || !canCollapse` + `LaunchedEffect(open) { progress.animateTo(...) }` 自动展开。
- **头部高度量错了节点**：`onSizeChanged` 原先挂在内层 `Box`，节点退出组合后最后一次尺寸留在 MutableState，`fixedOf()` 一直以为那几百 dp 还在，面板只能长到一半 —— 这才是「放大台本只把头部藏起来」的观感。把 `onSizeChanged` 移到 `AnimatedVisibility` 自身，`headerPx` / `quickPx` 合并成 `topBlockPx`。

实测（logcat 打点确认 `topBlock 674→291→0`、`animate done at=1.0`，打点已删）：折叠态点播放列表会展开并铺满；「回到台本」回到折叠小卡；阅读模式卡片从 y≈1450 长到 y≈590。

---

## 3. 播放页整体毛玻璃

### 文件
`PlayerScreen.kt`、`AsmrCard.kt`、`Theme.kt`

### 排查点
- 播放页卡片 / 面板没接 `LocalHazeState` 的磨砂体系（`Modifier.asmrFrost()`），而曲库 / 设置已接。
- 把播放页的卡片背景换成磨砂（背景层已是 `hazeSource`，直接 `hazeEffect` 取用即可）。

### 预期
播放页卡片 / 底部面板呈现半透明毛玻璃，与曲库 / 设置一致。

### 执行结果（2026-11-21）

已按计划做掉，但**原计划的写法不能直接用**：播放页的卡片本身就在全局 `LocalHazeState` 的
`hazeSource`（AppRoot 的内容层）**里面**，在它上面挂 `asmrFrost()` 等于「自己糊自己」——
采到的源里已经有这张卡。所以改成页面自己带一份磨砂源：

- **独立磨砂源**：`PlayerScreen` 里 `remember { HazeState() }`，只把全屏封面那一层
  （`PlayerArtwork` 外面包的一层 `Box`）注册成 `hazeSource`，卡片再对它取用。
- **卡片表面重构成三层**：原来那张卡是一层 `drawWithContent` 里「裁 → 画底色 → 画内容」，
  现在拆成 `Box { 玻璃层 / 底色层 / 内容 Column }`，整块仍由同一个 `clipPath` 裁到卡片形状。
  底色层用 `drawBehind` 画，进度仍然只在绘制相位读（不触发重组）。
  ⚠️ 坐标空间从「页面去掉 pad」变成「整页」，`cardH` 的上限和 `top` 都要减掉 `pad`，
  否则卡片会矮一截／压到导航区。
- **新的 `Modifier.asmrBackdropBlur()`**（`AsmrCard.kt`）：只有模糊、**不带 tint 也不加底色**，
  卡片自己的半透明底色盖在上面 —— 底色多透，玻璃就多明显。非 iOS 玻璃皮肤、关掉实时毛玻璃、
  页面在滚动这三种情况都返回 no-op，退回原来的纯色底。
- **折叠态底色 0.86 → 0.62**（仅限「玻璃皮肤 + 实时毛玻璃」同时开着时）：不透就没玻璃感；
  关掉模糊的情况仍用 0.86，保证字清楚。

实测：折叠态卡片能透出封面、四格与曲目卡仍压在它上面；横屏双栏、阅读模式、
播放列表整页都验过，卡片高度与底部让位没有变化。截图 `verify-topbar/p9_final_collapsed.png`、
`p4_player_landscape.png`。

遗留：测试曲库的封面都是纯渐变占位图，**模糊本身看不出来**（只能看出半透明），有纹理的真封面
才会显出磨砂颗粒；真要逐像素确认，得往测试曲库塞一张真封面再截一次。

### 3.1 展开态也透出封面（整页玻璃）

用户看过折叠态的效果后追加要求：展开态整页也要是玻璃，不能是纯色板。三处一起改，缺一不可：

- **封面不再退场**：`PlayerArtwork` 原来 `alpha = 1f - p`，展开到 0 就全没了。改成
  `1f - p * 0.30f` —— 展开后还剩 0.70，留在玻璃底下当底纹（顺手保留那点 1.06 缩放）。
- **底部压暗跟着退**：那层 `verticalGradient(0.92f)` 原先是给折叠态卡片垫字的，展开成整页
  后会拖出一条很重的暗带。把它单独包一层 `graphicsLayer { alpha = 1f - progress() * 0.68f }`，
  展开时只剩约 0.30。
  ⚠️ 用 `graphicsLayer` 而不是把 alpha 传进 `Brush`：`graphicsLayer` 只在绘制相位读进度，
  不会每帧重组（和卡片底色同一套做法）。
- **展开态底色半透明**：`expandedColor` 从 `scheme.background` 改成
  `background.copy(alpha = 0.70f)`，同样只在「玻璃皮肤 + 实时毛玻璃」时生效，其余皮肤仍然不透明。

实测（换了一张有纹理的真封面才看出来）：折叠态卡片能透出窗外的光斑与模糊轮廓；展开态整页
是奶白玻璃，底部能看出台灯暖光与窗外冷色的模糊块，正文与走带文字对比度正常；横屏双栏
（`q10_land_player.png`）结构不变。截图 `q4_collapsed_real.png`、`q6_expanded_b.png`。

配比调过一轮：先试「封面 0.58 / 底色 0.74」，展开后几乎只剩白底；改到「0.70 / 0.70」才有玻璃感。
再往上加会开始影响浅色封面下的正文对比，就停在这里。

---

## 4. R 角 / UI 规范统一

### 问题
- 底部卡片 R 角、内部选中胶囊、封面缩略图圆角不一致。
- 各页面 R 角不统一。

### 方案
1. 统一到 `Radius.*` 令牌（`ui/theme/Dimens.kt`），禁止页面裸写圆角。
2. 制定「同心圆角」规则：**内层 R = 外层 R − 内边距**。
3. 把规则写进 `docs/UI-SPEC.md`（新增一节「圆角规范」）。
4. 逐个页面过一遍，把不一致的圆角统一。

### 验证
`python scripts/check_ui_tokens.py` 不新增违规；截图对比圆角一致。

### 执行结果（2026-11-21）

做完了，实际动手的位置比计划书列的多一处（**形状家族**），这处才是「R 角看着不统一」的主因：

- **收口到唯一入口**：`asmrShape()` 以前只在「调用了它的地方」生效，`Theme.kt` 里的
  `SmoothRoundedCornerShape` 与页面直接 `RoundedCornerShape(...)` 各写各的 —— 曲库的
  `ProjectListRow`、对齐面板、队列行用的是连续曲率（squircle），别的行是正圆角，并排看角上
  就是折角差。现在 `asmrShape` 在玻璃皮肤下统一返回 squircle（≥10dp），页面全部改调它；
  `SmoothRoundedCornerShape` 不再在页面出现（`ui/components/TrackListItem.kt`、
  `ui/LibraryScreen.kt` 的 6 处已换）。
- **M3 Shapes 与令牌对齐**：`Theme.kt` 原来写死 `extraSmall 10 / small 12 / medium 14 /
  large 18 / extraLarge 24`，和 `Radius`（10/12/16/20/24）差着两档 —— `AsmrCard` 吃
  `shapes.large`(18)、页面手写的卡吃 `Radius.xl`(20)，同一屏能差 2dp。改成由 `RadiusScale()`
  派生：玻璃 10/12/16/20/24，经典 8/10/12/16/20。
- **底部弹层只有上沿圆角**：新增 `asmrTopShape(Radius.xxl)`，`AppBottomSheet` 不再用
  `RoundedCornerShape(topStart…, topEnd…)` 自己拼（那样在玻璃皮肤下没有 squircle）。
- **播放页去掉私有档位**：`PlayerDim.corner14 / corner24 / corner28` 删掉，改 `Radius.xs`
  （行封面）/ `Radius.xxl`（大图与卡片顶角）。顺手修掉一个真错位：卡片表面的形状是
  `Radius.xxl`(24)，而画裁剪路径用的是 `corner28`，两者不同心，玻璃层在圆角处能露出 4dp。
  路径半径现在提前在组合期取好（`sheetCornerPx`），绘制相位读不到 `Radius`（@Composable）。
- **同心圆角**：`Dimens.kt` 新增 `concentricRadius(outer, inset)`，写进 `docs/UI-SPEC.md`
  的「圆角规范」。落地一处真例子：底栏胶囊内边距 4dp，选中胶囊从写死的 `Radius.lg`(16)
  改成 `concentricRadius(Radius.xxl, 4)` = 20，两层弧这才同心。

`check_ui_tokens.py` 0 违规。截图 `s1_lib_final.png`（曲库行卡/缩略图/底栏胶囊同一套）、
`r8_sheet.png`（弹层顶角）、`r2_player.png`（播放页卡片顶角）、`r5_settings.png`（作品详情）。

---

## 5. 搜索历史 + 点搜索后隐藏作品列表

### 文件
`LibraryScreen.kt`（搜索逻辑）、新增 `data/SearchHistoryStore.kt`

### 方案
1. **点搜索后隐藏下方作品列表**：搜索框获得输入时，只显示「搜索历史」+「命中的单曲」，不显示全部作品卡。清空搜索词后恢复。
2. **搜索历史**：
   - 新增持久化存储 `data/SearchHistoryStore.kt`（参考 `TagStore.kt` / `RatingStore.kt` 的写法，`SharedPreferences` 或 JSON 文件均可）。
   - 记录用户提交过的搜索词（去重、最近优先、上限 N 条）。
   - 历史条目可点击（直接填入搜索）、可清空。

### 验证
- 点搜索 → 作品列表消失，显示历史；输入词提交 → 历史更新；清空历史生效；重启 app 历史仍在。

### 执行结果（2026-11-21）
- 新增 `data/SearchHistoryStore.kt`：JSON 文件 `search_history.json`，`load()` / `save()` 全用 `runCatching` 包住，坏了就当空历史，不让搜索页起不来。
- `MainViewModel` 增加 `searchHistory` StateFlow 与 `loadSearchHistory()` / `recordSearch()` / `removeSearch()` / `clearSearchHistory()`，上限 20 条、去重忽略大小写、最近优先；跟 `loadSmartCollections()` 一起在 `init` 里加载。
- `LibrarySearchField` 加 `onSubmit` 回调 + `ImeAction.Search` + `KeyboardActions`，**只在按搜索键/回车时记录**（`onValueChange` 里记会把「RJ10」记成半截词）；提交后清焦点收键盘。
- 新增 `SearchHistoryRow`（"搜索历史" + `FilterChip` + 「清空」），只在「搜索框展开且 `query` 为空」时出现，窄屏/宽屏两个分支都接。
- 作品卡的渲染条件原来挂在 `trackHits.isEmpty() || searchTab == 1` 上，改成 `query.isBlank() || searchTab == 1`：搜索框一有字，作品卡整体不再参与布局。曲目未命中时补一条 `hit-empty` 提示，避免只剩一片空白。
- 从搜索结果点进作品时也记一条历史（`toggle` 里补的），因为用户真实意图就是「我搜过这个」。
- 模拟器验证：搜 `RJ1000` → 作品卡全消失、只剩 tabs + 空提示；清空后出现「搜索历史 / RJ1000 / 清空」；杀进程重进历史仍在；点「清空」历史行消失。

---

## 6. WebView 右滑误入播放页 + DLsite 汉化版 403

### 6.1 右滑误入播放页
- **现象**：DLsite 浏览页 / asmr.one 下载页（WebView 二级页）右滑会切到播放页。
- **根因**：`AppRoot.kt` 的外层 `swipeBetweenTabs` 手势在这些二级页仍生效。
- **修复**：在这些二级页打开时禁用 `swipeBetweenTabs`（`enabled` 条件里排除 WebView 二级页），或让 WebView 自身拦截横向手势。
- **相关文件**：`AppRoot.kt`（`swipeBetweenTabs`）、`AsmrOnePickerScreen.kt`、`DlsiteDownloadScreen.kt`、`DlsiteLoginScreen.kt`。

### 6.2 汉化版已购作品 403
- **现象**：下载带汉化版本（有 `translation_info.original_workno`）的 DLsite 已购作品返回 403。
- **文件**：`data/DlsiteClient.kt`、`core/DlsiteDownloader.kt`、`domain/DlsiteDownloads.kt`。
- **排查**：汉化版下载时应使用原始作品编号（`original_workno`）而非汉化版编号去取直链 / 临时令牌；直链只认令牌（用登录会话 Cookie 会 403）。确认请求时 `instanceFollowRedirects=false`、收 `Location` + `Set-Cookie`、补 `adultchecked=1` 的逻辑在汉化版路径上没漏。

### 执行结果（2026-11-21）
**6.1**
- 根因确认：`AppRoot` 外层的 `swipeBetweenTabs` 对所有 tab 内容生效，而 DLsite 下载页 / asmr.one 选择页都是 tab 内的全屏 WebView，手势自然照样切页。
- 二级页的开关状态藏在 `LibraryScreen` 内部（`downloadCode` / `buyCode` / `asmrBrowse` / `asmrPick`），所以用回调上报：`LibraryScreen(vm, onOverlayChange)` → `LibraryContent(vm, onOverlayChange)`，`LaunchedEffect(四个状态)` 上报布尔；`AppRoot` 收进 `libraryOverlay`，`swipeBetweenTabs(enabled = !videoFull && !videoMode && !libraryOverlay)`。
- 没有改 WebView 自身的横向手势：`onOverlayChange` 这一层是唯一开关，以后再加二级页只要接同一个回调。

**6.2**
- 根因跟计划书猜的一致但更具体：`DlsiteClient.resolveDownload` 只拿**请求编号**（汉化版编号）去打 `/download/=product_id/` 入口，而购买记录挂在**原作编号**上，服务端判定「没买」→ 403。
- 改成多编号重试：`resolveDownload(code, sessionCookie, altCodes: List<String> = emptyList())`，候选 = `[code] + altCodes` 去重，每个编号把 `DlsiteAuth.downloadPageUrls` 的两个分区入口都跑一遍；**只在 403 / LOGIN_REQUIRED 这类判定上换下一个编号**（网络错误换编号没有意义，会把真问题藏起来）。成功时用的若不是首个编号，记一条 AppLog 便于以后复查。
- 调用侧 `domain/DlsiteController.kt` 的 `startPurchasedDownload(code)` 从 `_works.value[code.uppercase()]` 取 `DlsiteWork`，`altCodes = listOfNotNull(meta?.originalCode, meta?.translationCode).filterNot { it.equals(code, true) }.distinct()`。`originalCode` 早已在 `core/Dlsite.kt` 的 `DlsiteParse.parse` 里从 `translation_info.original_workno` 解析，不用再加解析层。
- `adultchecked` / `instanceFollowRedirects=false` / 收 `Location` + `Set-Cookie` 的既有逻辑没动。
- 验证边界：`assembleDebug` 通过；真实下载没跑——需要登录会话 + 已购的汉化作品，本机没有。这条只能算「逻辑闭环、未跑通真实链路」。

---

## 7. 深色模式「大卡片包小卡片」

### 文件
`Theme.kt`、`AsmrCard.kt`

### 问题
深色下底部面板（大卡）内嵌曲目信息小卡，两层底色太接近，层次不清。

### 方案
加深内卡底色 / 加描边 / 加投影，拉开内外层对比。保持与浅色模式一致的设计语言。

### 执行结果（2026-11-21）
- 只改 `ui/AsmrCard.kt` 的 `AsmrCard`：用 `MaterialTheme.colorScheme.background.luminance() < 0.5f` 判深色，深色下卡面底 = `lerp(surface, Color.White, 0.07f)`（往白抬 7%，正好比背景亮一档，又不至于变成灰块）。
- 深色下描边从 `asmrBorder` 渐变换成 `outlineVariant.copy(alpha = 0.55f)` 的 1dp `border`：渐变描边在深色里几乎看不见，实心细边才托得住卡形；浅色仍走原渐变，视觉没变。
- 已验证：曲库深色实拍（`.tmp/verify-topbar/v23_lib_dark_clean.png`），大卡片与小卡片层次清楚；浅色对照无变化。

---

## 8. 播放页最简播放栏 + 图片功能

### 问题
- 上方图片遮挡太多，且不能点开全屏查看。
- 最简播放栏缺少「更换图片 / 查看作品文件夹内图片」入口。

### 方案
1. 减少上方图片遮挡；封面可点击全屏查看（图片浏览器）。
2. 最简播放栏增加两个入口：
   - **更换图片**：打开图片选择，替换封面（复用现有的封面覆盖逻辑 `CoverOverrideStore` / `vm.setCover`）。
   - **查看作品文件夹内图片**：打开图片浏览器，浏览当前作品文件夹里的图片（`vm.imagesOfCurrent()` 已有）。
- **相关文件**：`PlayerScreen.kt`（最简播放栏、`showImages`、图片浏览器）、`MainViewModel.kt`（`imagesOfCurrent`）、`data/CoverOverrideStore.kt`。

### 执行结果（2026-11-21）
本轮落地了「能看到、能换」这一半，入口放在原有四格里而不是硬塞新格子：
- **全屏看封面**：曲目卡封面原来是单击切档（紧凑 → 大封面 → 全页大图），这个交互不能丢，所以改成单击切档 / **双击**全屏看图；`pointerInput` + `detectTapGestures(onTap, onDoubleTap)`，不能用 `clickable`（它吞掉第二次点击）。同一张图也能从「更多 → 显示 → 全屏看封面」进（`coverViewer` 状态 + 复用 `ImageViewer`，`onSave` 留空）。
- **换封面图 / 换一张封面**：新增菜单项，`rememberLauncherForActivityResult(GetContent())` 选图后走 `vm.setCustomCoverFromUri(workProject, uri)`，与作品详情页「换头图」共用 `CoverOverrideStore`（复制一份，不动曲库原图）；文案会按 `vm.hasCustomCover()` 在两种说法间切换。
- **查看图片**：原有「查看图片」保留（`vm.imagesOfCurrent()` 非空才显示），空态文案仍是「这个作品没有图片」。
- 已验证：半分辨率模拟器实拍（`.tmp/verify-topbar/w7_more_bottom.png`）确认「显示」分组下依次是 全屏看封面 / 换封面图 / 查看图片 / 沉浸模式；`w9_clean.png` 确认「换封面图」真的拉起系统图片选择器。
- **没做/待定**：①「减少上方图片遮挡」没有定案 —— 折叠态大图是 §3.1 用户点名要的 0.70 半透底纹，动它就是改上一轮的结论；②紧凑档（`playerLayout == "classic"`）四格已满行，再加两个常驻入口会挤成六格，所以选择放进「更多」抽屉。若要求入口常驻在栏上，需要先定「替换哪一格」或「改成两行排布」。

### 补充（同日）：更多抽屉改图标砖
- 用户后续确认「遮挡与常驻入口都不动」，另外把「更多」抽屉从长列表改成**图标排列、每行 4 列**。
- 做法：`PlayerScreen` 里把动作收敛成一份 `moreGroups`（分组名 → `MoreItem(icon, label, selected, onClick)`），
  抽屉用 `MoreIconGrid` 画 4 列图标砖（行尾用等权 `Spacer` 补位，保证每格等宽），
  曲目卡「⋯」的下拉仍从同一份数据生成带图标的行 —— 加一项只改 `moreGroups`，两份入口不会跑偏。
- 选中态（当前倍数 / 查看图片 / 沉浸模式）用 `primary` 10% 底 + `primary` 图标文字；倍速从原来的 chips 变成 7 块图标砖，仍按 4 列排。
- 已实拍验证（半分辨率）：`播放 / 播放倍数 / 台本 / 显示` 四个分组、每行 4 格，`1.0` 选中态正常。

---

## 本地文件目录（关键路径）

```
C:\Users\lipal\WorkBuddy\kiite\asmr-player\     ← 仓库根（git 在此）
├─ AGENTS.md、docs\AI-ONBOARDING.md、docs\UI-SPEC.md、
│  docs\MOTION-SPEC.md、docs\RELEASE-SPEC.md、docs\SECURITY.md   ← 动手前先读
├─ app\src\main\java\com\kiite\player\ui\
│   ├─ AppRoot.kt          ← 顶栏浮层/Scaffold/右滑手势/悬浮底栏（§1、§6）
│   ├─ LibraryScreen.kt    ← 曲库/搜索/作品详情（§1、§5、§6）
│   ├─ SettingsScreen.kt   ← 设置（§1）
│   ├─ PlayerScreen.kt     ← 播放页两档/台本/播放列表/图片/毛玻璃（§1、§2、§3、§8）
│   ├─ AsmrCard.kt         ← asmrFrost/asmrBarColor/卡片底色（§3、§7）
│   ├─ ui\theme\Theme.kt   ← LocalTopBarMetrics/Haze/皮肤（§1、§3、§7）
│   ├─ ui\theme\Dimens.kt  ← Spacing/Radius/IconSize 令牌（§4）
│   ├─ ui\theme\Motion.kt、MotionTransitions.kt、SharedTransition.kt、TopBarScroll.kt
├─ app\src\main\java\com\kiite\player\data\       ← 存储（搜索历史放这里，§5）
├─ app\src\main\java\com\kiite\player\data\DlsiteClient.kt、
│  core\DlsiteDownloader.kt、domain\DlsiteDownloads.kt   ← 403 排查（§6）
├─ scripts\ui_verify.sh、check_ui_tokens.py、check_version.py、
│  extract_changelog.py、sync_changelog.py、doctor.py
└─ _keys\kiite-release.jks、keystore.properties   ← 签名（勿动）
```

---

## 每个改动都要遵守的仓库铁律（摘要）

1. **先读** `AGENTS.md` + `docs/AI-ONBOARDING.md`，再动手。
2. **版本**：默认预发布 `x.y.z-betaN`，只有用户明说「正式版」才走正式版；版本号唯一权威源 `app/build.gradle.kts`。
3. **更新日志**：正式版段落不得出现「beta/alpha/rc/测试版/预发布」；一条一行 ≤60 字、一段 ≤10 条、小标题只用 新增/修复/调整/注意。发版前跑 `check_version.py` + `extract_changelog.py --check`。
4. **UI 令牌门禁**：`scripts/check_ui_tokens.py` 禁止新写裸 `Color(0x…)` / 裸 `N.dp` / 裸 `tween/spring`，用 `Spacing.*` / `Radius.*` / `Motion.*`。当前基线是 0。
5. **签名**：发版只 `assembleRelease`（禁 debug 变体），固定密钥库 `_keys/kiite-release.jks`。
6. **构建**（绝对路径 + `-p`）：
   ```
   export JAVA_HOME=/d/android-dev/jdk/jdk-17.0.20.1+1
   export ANDROID_HOME=/d/android-dev/sdk
   export GRADLE_USER_HOME=/d/android-dev/gradle-home
   ./gradlew -p <repo> :app:testDebugUnitTest :app:assembleDebug --offline --console=plain
   ```
7. **模拟器验证**：`scripts/ui_verify.sh <apk> <out> <mode>` 一次跑完，别跨命令连 adb（会 offline）。
8. **禁止**同时改业务代码和发布流程；禁止在用户没明说正式版时发正式版；禁止跳过 `check_version.py` 验收。

---

## 建议的执行顺序

- ~~1. **§1 顶栏浮层化收尾**~~ —— 已完成（含 §1.1 大标题进滚动容器）。
- ~~2. **§2 播放页「点放大台本 / 播放列表没反应」**~~ —— 已完成。
- ~~3. **§3 播放页整体毛玻璃**~~ —— 已完成（页面自带磨砂源 + 卡片三层重构 + §3.1 展开态整页玻璃）。
- ~~4. **§4 R 角规范**~~ —— 已完成（唯一形状入口 + Shapes/令牌对齐 + 同心圆角）。
- ~~5. **§5 搜索历史**~~ —— 已完成（历史持久化 + 搜索时隐藏作品卡）。
- ~~6. **§6 WebView 右滑 / 汉化版 403**~~ —— 已完成（二级页禁用切页手势 + 多编号重试；403 真实链路未跑）。
- ~~7. **§7 深色模式层次**~~ —— 已完成（卡面抬亮 + 实心细描边，深色实拍验证）。
- ~~8. **§8 播放页图片功能**~~ —— 已完成可验证部分（双击封面全屏看 + 更多里的换封面 / 查看图片；「遮挡」与「栏上常驻入口」待定）。

每个 § 做完：编译 + `check_ui_tokens.py` + 截图验证 + 提交一个 commit（版本号、CHANGELOG、代码分开发提交）。

发版：默认预发布（beta），`2.6.0-beta4` 的内容已在本地就绪（版本号 / CHANGELOG / 签名包 / tag 都齐），只差 `git push origin main v2.6.0-beta4`。
