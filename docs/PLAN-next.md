# 下一批开发计划（交给能力较弱的模型执行）

> 本文件是**交接文档**：把当前进度、接下来要做什么、每一步怎么验证、本地文件目录写清楚，
> 让接手的模型能照着做，不用重新摸索。

---

## 0. 当前进度（接手前必读）

### 0.1 已完成、但未提交、未编译验证的改动

上一个模型做到一半被打断，工作区有 **2 个文件有未提交改动**：

| 文件 | 改了什么 | 状态 |
|---|---|---|
| `app/src/main/java/com/kiite/player/ui/theme/Theme.kt` | 新增 `TopBarMetrics`（`safeTop` + `bar`，`total = safeTop + bar`）与 `LocalTopBarMetrics` | 已写入，未验证 |
| `app/src/main/java/com/kiite/player/ui/AppRoot.kt` | 顶栏从 `Scaffold.topBar` 抽成独立浮层 `FloatingTopBar`，改用 `safeDrawing.only(Top+Horizontal)` 避刘海 | 已写入，未验证 |

**第一步就是编译验证这两个文件对齐，并完成「各页面顶部留白」（见 §1）。** 这一步做完，顶栏浮层化才算完整。

### 0.2 尚未开始的需求

下面 §2–§8 的每一类 bug / 需求**都还没动**，逐个做。

---

## 1. 收尾「顶栏浮层化」（继续上一个模型做了一半的活）

### 目标
顶栏不再用 `Scaffold.topBar`（它会按栏高把内容压下去，导致滚动内容进不了顶栏后面，半透明磨砂条没东西可虚化）。改成**浮层**，各页面自己用 `contentPadding(top = 状态栏 + 栏高)` 让首屏。

### 现状（已改的部分）
- `Theme.kt` 已加 `TopBarMetrics` 与 `LocalTopBarMetrics`。
- `AppRoot.kt` 已把顶栏抽成 `FloatingTopBar`（浮层），并在 `CompositionLocalProvider` 里 `LocalTopBarMetrics provides topBarMetrics`。

### 待做
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

---

## 3. 播放页整体毛玻璃

### 文件
`PlayerScreen.kt`、`AsmrCard.kt`、`Theme.kt`

### 排查点
- 播放页卡片 / 面板没接 `LocalHazeState` 的磨砂体系（`Modifier.asmrFrost()`），而曲库 / 设置已接。
- 把播放页的卡片背景换成磨砂（背景层已是 `hazeSource`，直接 `hazeEffect` 取用即可）。

### 预期
播放页卡片 / 底部面板呈现半透明毛玻璃，与曲库 / 设置一致。

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

---

## 7. 深色模式「大卡片包小卡片」

### 文件
`Theme.kt`、`AsmrCard.kt`

### 问题
深色下底部面板（大卡）内嵌曲目信息小卡，两层底色太接近，层次不清。

### 方案
加深内卡底色 / 加描边 / 加投影，拉开内外层对比。保持与浅色模式一致的设计语言。

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

1. **§1 顶栏浮层化收尾**（先编译修好半截改动，再做各页留白）—— 这是其他 bug 的地基。
2. **§2 + §3 播放页**（放大 bug + 毛玻璃）—— 同一文件，一起做。
3. **§4 R 角规范**（全局统一，顺带 §7 深色层次）。
4. **§5 搜索历史**（独立新功能）。
5. **§6 WebView / 403**（独立排查）。
6. **§8 播放栏图片功能**。

每个 § 做完：编译 + `check_ui_tokens.py` + 截图验证 + 提交一个 commit（版本号、CHANGELOG、代码分开发提交）。
