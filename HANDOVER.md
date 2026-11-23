# 交接文档（Kiite Player）

> 本文记录**当前进度、环境限制、踩过的坑、下一步计划**。  
> 静态规则拆到了 `docs/` 下，先读 `docs/AI-ONBOARDING.md`。  
> **版本号以仓库实时内容为准**：`app/build.gradle.kts`、`version.json`、CHANGELOG、Tag。

## 0. 一句话现状

本地曲库播放器 + asmr.one / DLsite 在线抓取与下载，Kotlin + Compose 单 module。  
发版已自动化：推 `v*` Tag → CI 校验 → 单测 → 固定密钥签 release 包 → 校验指纹 → 自动建 Release。  
签名事故（每个 CI 包一个新证书）已修复，见 `docs/SECURITY.md` 第 5 节。

## 1. 项目位置与分支

- 仓库：`YuanDayo/asmr-player`（public），默认分支 `main`。
- 本地一般开在 `asmr-player/` 子目录；`main` 与历史分支 `asmr` 并存。
- 工作区里仓库外还有 `testdata/`（测试曲库）、`promo/`（宣传图）、`tools/`（图标/海报/发版脚本），都不进仓库。

## 2. 构建 / 测试 / 模拟器

```bash
export JAVA_HOME=<jdk17>
export ANDROID_HOME=<sdk>
export GRADLE_USER_HOME=<gradle 缓存>

./gradlew :app:testDebugUnitTest :app:assembleDebug --offline --console=plain   # 本地自测
./gradlew :app:assembleRelease -PrequireReleaseSigning=true --console=plain     # 出发布包
```

- 发布包产物在 `app/build/outputs/apk/release/com.kiite.player-<版本>.apk`。
- `assembleRelease` 会跑 `lintVitalRelease`；离线且本地没缓存 lint 依赖时会失败，联网跑一次即可。
- 模拟器：`emulator.exe -avd asmr ...`（启动不要接管道），adb 命令注意路径转换。  
  **模拟器连不上 `api.asmr.one` / DLsite**（IPv6 路由问题），在线链路只能真机验。
- 本机模拟器是 **纯软件渲染**：启动命令行带 `-gpu swiftshader_indirect`，AVD
  `config.ini` 里 `hw.gpu.enabled = no`（`dumpsys SurfaceFlinger` 能看到 SwiftShader）。
  这就是「模拟器频繁无响应」的根因——SystemUI 自己也会 ANR，不只是 App。  
  之前 WorkBuddy 顺滑，大概率是 GPU 加速启动；本机要根治得用 host/auto GPU 重启模拟器。
- App 侧已做减负：`MainActivity.isEmulator()`（ranchu/goldfish 指纹）下强制关实时毛玻璃
  （`liveBlur = settings.liveBlur && !isEmulator()`），真机不受影响。修后 App 启动完 CPU
  从 ~98% 回落到 0%，不再自己拖垮整机。SystemUI 残留的 ANR 弹窗用
  `adb shell am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS` 关掉。
  这类 ANR 先看 `top`：App 长期 0% CPU = 系统卡不是死锁；整机 SystemUI 一起 ANR 就重启模拟器。

## 3. 发版流程

**先定通道：默认预发布。** 用户没明确说「正式版」，就走 `x.y.z-betaN` + Pre-release + 不动  
`version.json`；「改完就推送」「发一版」都算默认通道，不算正式版授权（详见 `docs/RELEASE-SPEC.md` 2.4）。

版本号**只有一处**要改：`app/build.gradle.kts` 的 `appVersionName` / `appVersionCode`。  
其他位置一律读 `AppInfo.VERSION_NAME` / `AppInfo.VERSION_CODE`，硬编码会被 `check_version.py` 拦下。

然后按顺序：

1. 改 `app/build.gradle.kts` 的版本号；
2. 写 `CHANGELOG.md` 段落（预发布首行带 `**[测试版]**：`，正式版不带）；
3. **正式版**：把 `version.json` 的 `version` / `versionCode` 改成新版本（预发布**不许**动它）；
4. 同步 `app/src/main/assets/CHANGELOG.md`（应用内「更新日志」页读的是 assets 里那份）；
5. 本地校验：

```bash
python scripts/check_version.py v<tag> --release
python scripts/extract_changelog.py v<tag> --check
```

1. 提交、打 Tag（`v` + versionName，去掉 v 后必须完全相等）、推送；
2. CI 自动建 Release（预发布自动不带 Latest；已存在同名 Release 会跳过，不覆盖）。

> 版本号、CHANGELOG、README、CI 变更**分开提交**，别塞进一个巨大 commit。

### ⚠️ 正式版发布后必须 purge jsDelivr

应用内更新的第一数据源是 jsDelivr，它会缓存 `@main`。不刷新会返回旧 `version.json`，  
导致更新检查误判「已是最新」：

```bash
curl 'https://purge.jsdelivr.net/gh/YuanDayo/asmr-player@main/version.json'
curl 'https://cdn.jsdelivr.net/gh/YuanDayo/asmr-player@main/version.json'   # 应看到新版本号
```

## 4. 代码结构

见 `docs/ARCHITECTURE.md`。要点：`core`（纯逻辑）→ `data`（存储/网络）→ `domain`（Controller + StateFlow）  
→ `ui`（Compose + 唯一 ViewModel）；`playback` 与 `download` 是两个前台服务；没有 DI 框架，手工组装。

**首页是入口页，不是展示页**（2026-11-22 起）：首页每一个可点元素都必须落到真实页面或一次真实播放，
不允许出现「按下去什么都不发生」的卡片。现状映射：

| 首页入口 | 落点 |
| --- | --- |
| 最近播放 / 「最近在听」的「全部」 | 首页内二级页 `HomeSubPage.HISTORY`（复用 `BookmarksPanel` 的 `HistoryList`） |
| 最近在听（整块） | `continueFromHistory`，文件失效时置灰并标「文件已失效」 |
| 累计收听 / 收听时长 / 今日目标「统计」 | 二级页 `HomeSubPage.STATS`（今日 / 7 天 / 30 天 / 近 14 天柱状图 / 连续天数 / 最常听） |
| 最近添加 / 最常播放 | 曲库 + `LibraryIntent(sortMode = recent/plays, asc = false)` |
| 随机播放 / 随机混播 | 真播（`playRandom`），只出迷你条，不弹半屏 |
| 建议封面 | 曲库作品详情（`LibraryIntent(projectPath = …)`） |
| 社团 / 声优 chip | 曲库 + `LibraryIntent(circle / voiceActor)`，落地后筛选行出现「社团 · X」可撤标签 |
| 昵称 / 头像 | 设置 · 个人资料（`SettingsScreen(requestProfile = true)` 深链） |
| 已购作品 | 二级页 `HomeSubPage.PURCHASES` |

跨页条件走一条通道：`MainViewModel.requestLibrary(LibraryIntent)` → 曲库消费一次即清空。
`LibraryContent` 只负责「打开某部作品」（并调 `clearLibraryProjectIntent()` 摘掉该项），
排序与筛选由 `ProjectList` 消费 —— 不这么拆，从详情返回列表会被残留的 `projectPath` 再弹回详情。


## 5. 设计规范（用户明确要求）

| 维度        | 值                                      |
| --------- | -------------------------------------- |
| 风格        | **iOS 风格**：白底**无描边**卡片，靠「白底 / 浅灰底」对比分层 |
| 圆角        | 12dp（大卡片 20dp，控件 10dp）                 |
| 浅色底 / 卡片  | `#F2F2F6` / 白                          |
| 深色底 / 卡片  | `#000000` / `#1C1C1E`                  |
| 主字色 / 次字色 | `#1C1C1E`（深色 `#FFFFFF`）/ `#8E8E93`     |
| 强调色       | `#5FC2CE`（深色 `#6FC8D4`）                |
| 阴影        | **全部禁用**（用户多次强调「不要叠底感」）                |

界面文案与注释一律中文；标题用无衬线重字重，编号/标签用等宽。

## 6. 已知问题与待办

> 下一批开发计划见 [docs/PLAN-next.md](docs/PLAN-next.md)：§1 顶栏浮层化（含大标题进滚动容器）、
> §2 播放页放大、§3 播放页毛玻璃已完成，接着做 §4 R 角规范、§5 搜索历史、§6 WebView 手势 / 403。

### 明确的待办

- **批量校准跨集应用**：偏移可复用，单句微调不能直接套。
- **更新体验**：`UpdateInfo` 已带 APK 直链与 SHA-256 字段，后续可在设置页做「下载并校验」。

### 第二批修复（2.6.1-beta1，2026-11-23）

计划书 `docs/FIX-PLAN-BATCH2-2026-11-23.md` 里的六件事已落地，实现要点：

- **查看图片漏子目录**：`imagesOfCurrent()` 原来 `listFiles()` 单层枚举。现在走
  `core/collectProjectImages()` 手写递归（同层先文件后子目录、两层都按文件名排序），
  深度 ≤ 4、总数 ≤ 200、跳过 `.` 开头的隐藏目录。顺序稳定所以观感不变。
- **首启引导直接跳条款**：`seen=true && version=0`（旧版本装过、没读过条款）以前判成
  「只补条款」。判断收到 `core/onboardingTrigger()`，这一档改走完整流程；
  只有 `0 < version < 当前版本` 才 `LEGAL_ONLY`。抽成纯函数是为了能单测（6 条）。
- **同意状态看不到**：条款页顶部加「同意状态 · 已同意 / 未同意」，判据全项目只有一个
  `onboardingVersion >= ONBOARDING_GUIDE_VERSION`；`markOnboardingDone()` 改成先改内存态
  再落盘，消掉「刚同意完进设置还是未同意」的那一帧。
- **DLsite 每次要重新验证**：登录态原来只活在 WebView 的 CookieManager 里（session cookie
  会被系统清掉），启动时也没人验证。现在验证通过时把 Cookie 快照存 `filesDir/dlsite-cookies.json`，
  启动 `restoreSession()` 先补回再联网验证；`logout()` 连快照一起删，否则「退出登录」会被
  下次启动写回来。**真机才能验**（模拟器连不上 DLsite）。
- **收听统计**：
  - `history.json` 新增 `hourlyListenedMs`（key = `0`..`23`，只在小时上聚合，不记日期）——
    这张图看的是「习惯几点听」，按日期×小时分会变成几千个键，而 history.json 是高频重写文件。
  - 统计页改成 日 / 周 / 月 / 总 四档（**没有「年」**：按天数据只留 90 天，一年的柱子大半是 0，
    看着像「没听过」而不是「没数据」），柱状图颗粒度跟着档位变（14 天 / 8 周 / 3 月 / 12 周）。
  - 数据卡补齐收听天数、日均、连续、听过作品、听完章节、累计与今日目标。

自测：`testDebugUnitTest` 新增 13 条（图片收集 7 + 引导判断 6，另有 PlayHistory 5 条时段/区间），
模拟器跑了 `onboarding`（清数据后确认先出功能页）、`legal`（条款页显示已同意）、`home`（统计页渲染正常）。
截图在 `.verify/b2-onboarding`、`.verify/b2-legal`、`.verify/b2-stats`（不进仓库）。

### 桌面歌词浮窗（2.6.1-beta2，2026-11-23）

计划书 `docs/FIX-PLAN-LYRIC-OVERLAY-2026-11-23.md`。P0/P1/P2 三档都做了，实现要点：

- **位置持久化**：`AppSettings` 加 `lyricPosX/lyricPosY`（dp，`-1` = 没存过）。拖动**松手**才写盘
  （拖动过程中每帧写 DataStore 会把它写成瓶颈）；启动时 `setPosition()` 在 `show()` **之前**灌进去，
  避免先闪一下默认位置。窗口在 `show()` 里设了 `title=KiiteLyricOverlay` 并打 `KiiteLyric` 日志——
  浮窗的位置没有任何界面能查到，出问题只能靠这两样。
- **坐标系坑（重要）**：`LayoutParams.y` 不是屏幕坐标。`TYPE_APPLICATION_OVERLAY` 默认按 systemBars
  内缩，实测窗口 frame 比 `y` 多出一个状态栏高度（+128px）。所以可用区域要从
  `currentWindowMetrics.bounds - systemBars insets` 算（`refreshArea()`，缓存起来，拖动时每帧都要用），
  不能用 `displayMetrics` 的整屏高度——否则拖到底会把窗口压到导航栏外面。
- **贴合逻辑**：`core/OverlayGeometry`（clamp / snapToEdge / lineProgress / remainingSec）是纯函数，
  单测 10 条。拖动中每帧 clamp，松手先 snapToEdge 再 clamp（顺序不能反）。横竖屏切换用 400ms 的
  `update()` tick 顺带发现（`checkScreenChanged()`），不额外注册回调。
- **外观**：深色仍是原来的深灰底白字（`0xD91C1C1E` 原样保留），浅色改浅底深字。
  视图是复用的，所以 `applyAppearance()` 靠几个 tint 列表反刷颜色，不重建视图。
- **内容**：顶部小标用曲名（`ParsedScript` 没有章节结构，这是最接近「上下文」的东西），
  右侧「还剩 N 秒」（向上取整，没有终点就不显示），底部 2dp 进度条（<1% 变化不重排）。
  空台本不再是空白：曲名 + 「本曲暂无时间轴台本」。
- **交互**：单击/双击都交给 `GestureDetector`（自己按时间差判会和拖动打架），代价是单击展开控制条
  延后到双击窗口过去（约 300ms）。控制条加「回 App」，与双击共用 `onRequestOpenApp`（宿主用
  `singleTop + REORDER_TO_FRONT` 拉起 MainActivity，不新开页、不丢播放状态）。

自测：`testDebugUnitTest` 新增 10 条（`OverlayGeometryTest`）；`scripts/ui_verify.sh lyric` 在模拟器上
验了位置落盘/恢复（settle `px=0,1842 dp=0,702` → 重启后 `show px=0,1842`，frame 完全一致）、
贴左吸附、浅/深色两套配色、控制条、以及「App 退到桌面浮窗仍在」。**没验到的**：双击回 App ——
这套镜像会把后台应用浮窗窗口的触摸丢掉（前景正常），注入不进双击；真机需要点一次确认。
排查过程留下的 `scripts/ui_verify.sh ldiag` 模式可以直接打窗口 frame、触摸日志与 prefs 键。

## 7. 环境限制

- 某些机器/网络会劫持 `dlsite.com`、`github.com`，导致 DLsite 抓取与 GitHub API 无法自测；  
  `api.github.com`、`cdn.jsdelivr.net` 通常可用。
- `curl` 在本机对 `api.github.com` 常返回 000，改用 Python urllib / PowerShell。
- 推送 `.github/workflows/*` 需要带 `workflow` scope 的凭据，默认凭据只有 `repo` 会被拒。
- 文件是 CRLF，做批量字符串替换前先统一换行。

## 8. 踩过的坑（避免重犯）

1. **签名**：CI 曾用 `assembleDebug`，debug keystore 每个 runner 随机生成 → 每个包一个新证书，  
   互相覆盖安装全部失败。现在固定密钥库 + 三道门禁（见 `docs/SECURITY.md`）。
2. **图标四角发黑**：源 PNG 是 RGB 无 alpha，四角烤死纯黑；要先探测真实数据再动手。
3. **自适应图标裁掉角色**：前景满铺 108dp，系统只显示中间约 66dp。
4. **「检查更新」没反应**：逻辑和状态都写了，弹窗 UI 从没接上。功能完成 = 界面真的接上了。
5. **全屏退出按钮点不到**：`PlayerView` 的 `SurfaceView` 在独立图层，会盖住 Compose 控件，按钮要放视频外。
6. **`LocalTabReselect` 是全局计数，不是事件**：页面首次组合时它可能已经是 1/2/3（用户上一轮在别处
   重复点过同一标签）。旧写法 `if (tabReselect == 0) return` 会把「这次进入」当成「再点一次当前标签」——
   首页深链打开「个人资料」当场被清成设置主页、首页打开的作品详情被 `selectProject(null)` 关掉。
   现在三页（首页 / 曲库 / 设置）都改成 `remember { mutableIntStateOf(tabReselect) }` 记住初值、只对变化反应。
6. **`runCatching{}.getOrNull()` 把失败压成空值**，网络故障被显示成「已是最新」——必须区分"无更新"与"请求失败"。
7. **jsDelivr 缓存 `@main`**：改了 `version.json` 不 purge，更新检查会误判。
8. **版本号散落多处**是历史包袱，现在单点定义 + CI 抓硬编码，别再退回去。
9. **`ui_verify.sh` 传 POSIX 路径给 adb.exe**：`adb.exe` 只认 `C:/...`，而脚本里设了  
   `MSYS_NO_PATHCONV=1` 关掉自动转换 → `install` / `pull` 全部静默失败，整轮验证其实跑的是  
   设备上的**旧包**（2026-11-17 踩过，差点拿旧截图当验收）。现在脚本会把 `/c/...` 换成  
   `C:/...`，装包不成功直接 exit 1，截图/界面树拉回失败会打警告。
10. **把「发布」当成「发正式版」**：用户说「改完直接推送」时，AI 曾直接发了正式版并写了  
    `version.json`，等于替用户决定了推送给所有老用户。现在默认预发布，正式版要用户明确点名。
11. **WebView 会把整屏盖掉**：Chromium 默认渲染会让 WebView 单开一层盖住窗口，应用自己的  
    顶栏/进度条全被吞（用户看到的「点下载后黑屏」就是这个）。`DlsiteDownloadScreen` 的兜底  
    网页已改 `LAYER_TYPE_SOFTWARE`；以后任何「WebView + 自己的顶栏」都先想这一条。
12. **DLsite 直链认临时令牌，不认登录会话**：入口页 302 时 `Set-Cookie` 下发的令牌才是直链的  
    凭证，带登录 Cookie 去重放会 403。解析入口时**不要跟 302**，把 `Location` 与 `Set-Cookie`  
    一起收下（`DlsiteClient.resolveDownload`）。
13. **`tap_text` 是子串匹配**：说明文字里含「下载」时会先命中说明文字，点了等于没点。按钮用  
    `tap_text_exact`；自动点击前先确认没有被后台任务遮罩挡住（`wait_text`）。
14. **「已下载」只记编号＝永久假记录**：文件被删了编号还在，作品就再也不会回到「已购未下载」  
    列表里（2.5.5-beta3 用户反馈）。凡是「本机有没有」的判断都要落回到**磁盘**：  
    `DownloadedLedger` 记编号 + 落盘路径，查询时按文件是否存在判；老台账（只有编号）按编号去  
    下载目录回捞一次，捞不到就算未下载。装载时**不要**顺手删条目 —— 启动那一刻存储权限可能  
    还没就绪，`exists()` 全 false 会把整份台账清空。
15. **全页 `AnimatedContent` 的放大转场，原点别写中心**：曲库点作品的「放大」用的是  
    `scaleIn(transformOrigin = 卡片中心/内容区)`，原点来自卡片自己的 `LayoutCoordinates`。  
    卡片位置**只在点击那一帧取**（`ZoomOrigin.capture()`），不要在 `onGloballyPositioned` 里  
    往 snapshot state 写 Rect —— 列表滚动时那个回调每帧都跑，写 state 就是每帧重组一个列表项  
    （见 2.5.5-beta2 掉帧事故）。宽屏左右分栏不做缩放，只用淡入淡出。
16. **Tag 构建永远读不到别的 Tag 的缓存**：GitHub 缓存按 ref 隔离，`v2.5.5-beta4` 读不到  
    `beta3` 存的缓存；Tag 唯一能读到的是**默认分支**的。而 `setup-java` 的 `cache: gradle`  
    key 里含 `app/build.gradle.kts` 的哈希 —— 那个文件每次都带版本号，于是每个 Tag 都必然  
    miss（日志里那句 `gradle cache is not found` 就是它），等于每次发版重下一遍依赖、重解压  
    一遍 AAR。现改成显式 `actions/cache`：key 只跟依赖清单走，配 `restore-keys` 兜底，  
    在 main 上手动触发一次即可给后续所有 Tag 预热。
17. **`~/.gradle/caches` 里包含 `build-cache-1`**（`org.gradle.caching=true` 已开），所以把  
    这个目录整个缓存下来，同一个 commit 第二次构建会 27 个任务 FROM-CACHE、29 秒跑完  
    （整轮 55 秒）。想让 Tag 构建也这么快，得让它在推送前就已被 main 构建过并落盘 ——  
    目前 `git push origin main vX` 是同时推的，两个 workflow 同时起跑，Tag 那边抢不到。  
    所以发版习惯可以改成「先推 main，绿了再打 Tag」，Tag 一跑就是缓存命中。
18. **beta 线没发正式版就把基线抬了**：2.5.5-beta9 之后直接发成 2.6.0-beta1，用户装的还是 2.5.4，等于跳过一个从没出现过的正式版。现在 `check_version.py` 规则 7 会在打 Tag 时拦下这种抬基线（历史从 CHANGELOG 读），确需换基线要用户明确要求并加 `--allow-base-bump`。规则见 `docs/RELEASE-SPEC.md` 2.5。
19. **DLsite 直链末段是通用名**：落盘若照抄 URL 最后一段，几部作品会写成同一个 zip、解压互相覆盖，目录名也没有编号 → 曲库认不出作品。现在下载一律按 `RJ编号 + 标题.zip` 命名（`DlsiteDownloader.displayName`），分卷加「第N部分」，解压时去掉分卷后缀合并到一个文件夹；老台账里没编号的 zip 在装载时按「编号 + 标题」改名（只在归属唯一、目标不存在时动）。
20. **没有 RJ 编号的本地作品也能识别**：作品详情页新增「按标题识别」，用文件夹名搜 DLsite 后让用户选结果；确认的「归一化标题 → 编号」映射存在 `dlsite-title-links.json`，重扫/重启后自动生效。模拟器连不上 DLsite，这条只能在真机验网络部分。
19. **浮层顶栏 + 固定大标题 = 两头不讨好**：顶栏不占布局位时，页面只能自己让出「栏高」那一截，
   固定头部下面就会多出约 48dp 空档（IO 截图里最明显）。正解是把大标题当列表第一项塞进
   滚动容器（iOS large title），列表就不用再让 `contentPadding(top = bar)` —— 那截留白本身
   就是空档。别在「固定头部 + 列表让多少」之间反复调数值。
20. **宽屏双栏别让顶栏进磨砂**：宽屏左栏标题固定、不会滚走，「小标题接手」那套不成立；磨砂条
   一淡入就正好盖在那颗固定的大标题上（第一版实测被糊掉一半）。`header == null` 的宽屏分支
   要用 `TopBarClear()` 把进度钉在 0，窄屏才走 `TrackTopBar`。
21. **`hazeEffect` 不能挂在 `hazeSource` 的同一层里**：AppRoot 把整个页面内容注册成了磨砂源，
   在页面内部的卡片上直接 `asmrFrost()` 采到的源里就有这张卡本身 —— 糊的是自己，等于没糊。
   要玻璃就**自己带一份 HazeState**：只把身后那一层（封面 / 背景图）`hazeSource`，卡片再取用。
   另外别用带 tint 的 `asmrFrost()` 给卡片上底色，卡片应当「模糊层 + 自己的半透明底色」两层叠
   （见 `asmrBackdropBlur` + 播放页卡片的三层结构）。
22. **整页玻璃要三处一起改**：只把卡片的底色弄半透明没用，播放页还有两道保险在挡 ——
   `PlayerArtwork` 的 `alpha = 1f - p`（展开就把封面淡没了）、底部那层 `0.92f` 压暗渐变、
   以及不透明的 `expandedColor`。三处（封面残留透明度 / 压暗层按进度退、`graphicsLayer` 别每帧
   重组 / 展开底色半透明）都放才透得出来。加太猛正文会飘，实测「封面 0.70 + 底色 0.70」是个平衡点。
   还有：测试曲库封面是纯渐变，模糊看不出来容易误判成「没生效」，验玻璃一定塞张有纹理的真封面。
23. **R 角别开第二套形状家族**：`asmrShape()` 是唯一入口，它在玻璃皮肤下返回
   `SmoothRoundedCornerShape`（≥10dp）。页面里再出现 `SmoothRoundedCornerShape(...)` 或直接
   `RoundedCornerShape(Radius.md)`，就会出现「这一行是连续曲率、那一行是正圆角」的折角差 ——
   肉眼看不出是哪写的，只知道「R 角不统一」。`Theme.kt` 的 `Shapes` 也必须由 `RadiusScale()`
   派生，别另写 14/18 那种没在令牌上的数字。底部弹层用 `asmrTopShape`，别自己拼 topStart/topEnd。
24. **搜索历史只在「提交」时记**：`onValueChange` 里记会把用户打字的每个前缀都存成一条
   （`R`、`RJ`、`RJ10`…）。要接 `ImeAction.Search` + `KeyboardActions`，敲回车或点键盘搜索键
   才 `recordSearch()`；另外「从搜索结果点进作品」也要补记一条，那才是真实意图。
25. **二级页要自己上报状态才能禁用外层手势**：`swipeBetweenTabs` 在最外层，二级页开关却藏在
   `LibraryScreen` 内部（`downloadCode/buyCode/asmrBrowse/asmrPick`）。别去 WebView 里拦手势 ——
   用回调把布尔值上报给 `AppRoot`，以后新增二级页只接同一个 `onOverlayChange`。
26. **DLsite 汉化版的购买记录挂在原作编号上**：拿汉化编号打 `/download/` 入口，服务端只会判定
   「没买」并 403，不是 cookie 或 `adultchecked` 的问题。`resolveDownload` 要按
   `[请求编号] + originalCode + translationCode` 逐个重试，且**只在 403 / LOGIN_REQUIRED 上换编号**
   （网络错误换编号会把真问题藏起来）。
27. **深色下的卡片层次别靠渐变描边**：`asmrBorder` 的渐变在近黑底上几乎不可见，两张卡就糊成一块。
   深色分支改成「卡面 `lerp(surface, White, 0.07)` 抬一档 + `outlineVariant` 55% 的 1dp 实边」，
   浅色仍走原渐变，两边互不影响。
28. **封面单击别再配双击**：`pointerInput { detectTapGestures(onTap = 切档, onDoubleTap = 看大图) }`
   的 onTap 会被压到 300ms 双击窗口后才触发，用户点一下像没反应——这就是「大小封面切换卡顿」。
   封面上只留 `clickable { 切档 }`（立即响应）；「全屏看封面」仍在「更多 → 全屏看封面」。
29. **模拟器这次的坑是渲染跟不上导致 ANR**：半分辨率 + 全套毛玻璃时主线程频繁卡在
   `ViewRootImpl.performTraversals`（Surface.isValid），系统每几秒弹一次「Kiite Player isn't
   responding」，会把点击吃掉。对策：`wm size 540x1200`（验完记得 `wm size reset`）、先
   `input tap <Wait坐标>` 再操作、必要时空跑 `settings put global *_animation_scale 0`。
   另外上一轮留下的桌面歌词悬浮窗会抢点击，可临时 `appops set com.kiite.player
   SYSTEM_ALERT_WINDOW deny`，验完记得 allow。
30. **倍速这类数值参数别平铺成砖**：7~9 块倍率砖既占两行，又看不出「现在几倍」。收成
   「减 / 当前值 / 加」一行，按 `SPEED_STEPS` 逐档走，到头直接禁用按钮（`enabled` 判首尾），
   不要越界后静默无反应。抽屉里它可以作为整行块插在某个分组之后（`MoreIconGrid.inlineBlocks`），
   下拉菜单宽度放不下两个按钮，就只显示当前值 + 把人送去抽屉。
31. **EQ 只有一份实现**：设置页那段音效（预设 / 五段 / 低音 / 环绕 / 响度）已抽成
   `ui/components/AudioFxControls.kt`，播放页弹层与设置页都调它，不要各写一套滑块。
32. **作品详细要能降级**：DLsite 信息依赖联网与登录，取不到时 `vm.dlsiteOf()` 返回 null，
   弹层必须退化成「只有本地文件」，仍要显示编号（来自目录名）、格式、体积、台本数与路径。
   地址字段用 `project.path` 查，不要用 `track.path`（那是单条音频）。
33. **别在滚动时摘掉模糊**：`theme/ScrollActivity.kt` 的 `scrolling` 只配用来**收半径**，
   不要 `return this.background(asmrBarColor())` —— 手指一按下拖动，磨砂就变成一块实色，
   用户读到的就是「毛玻璃一按就坏」。收 20dp → 10dp 已经能省掉大半每帧成本。
34. **深色列表行不要自己套卡**：外层已经是 `MiuixSettingsGroup` 的深色大卡，行再贴一张
   `asmrRowColor()` 就成「大卡包小卡」。深色分支走透明行 + 分隔线，浅色保留行卡。
35. **详情页操作按钮别直接用 Miuix Pill**：淡灰底 + 6dp 间距在浅色页面上既看不清边界又显挤。
   统一走 `DetailAction`（`heightIn(min = TouchTarget.min)` 即 48dp、pill、主操实心 /
   副操作 surfaceVariant + 发丝边、间距 8dp）。写 `40.dp` 会被 `check_ui_tokens.py` 记为新增裸 dp。
   注意 `asmrBorder` 是**无接收者的** `@Composable fun asmrBorder(shape): Modifier`，
   写成 `Modifier.asmrBorder(...)` 会编译不过。
36. **播放页卡片上沿别把 pad 减两次**：`PlayerScreen` 的 `drawWithContent` 里
   `cardH = fixed + avail * p + pad` 又配 `top = size.height - pad - cardH`，展开态
   上沿 = `topReservePx - pad`，整张卡（尤其大封面顶部）被顶到悬浮顶栏后面，就是
   「大封面顶部被遮挡」。`pad` 是底部让位，只能减一次：`cardH = fixed + avail * p`。
   修完后 `top = topReservePx`，封面顶部正好贴着顶栏下沿。
37. **大小封面切换卡顿（已修）**：定位为 `detectTapGestures(onTap, onDoubleTap)` 的单击
   300ms 等待——点一下要等双击窗口过期才切档，观感就是「异常卡顿」。已把封面改成
   `clickable` 立即切档，双击全屏移到「更多 → 全屏看封面」（见 28）。72↔132dp 仍用
   `animateDpAsState` 逐帧重排，但真机上主要痛点是那 300ms；若再报掉帧，再考虑把
   尺寸动画换成 graphicsLayer scale / 固定 Coil 请求尺寸，别让整卡逐帧重排。
38. **悬浮顶栏 / 底栏别用 `if` 硬切**：折叠播放页或切沉浸时 `chromeHidden` 一翻，
   顶栏和导航胶囊原来 `if` 一摘就「啪」地消失，和卡片慢慢收起的节奏脱节。改成
   `AnimatedVisibility`：顶栏 `fadeIn(SHORT)+slideIn(-it)` / `fadeOut(FLASH)+slideOut(-it)`，
   底栏 `fadeIn(SHORT)+slideIn(it/4)` / `fadeOut(FLASH)+slideOut(it/4)`。注意底栏外层
   是 AnimatedVisibility，内层迷你条仍保留自己的 AnimatedVisibility（共享元素配对靠它）；
   折叠时 `miniBarShown` 已经 false，外层只负责导航胶囊，不会双动画。
39. **设置页主页 ↔ 子页别 `if/else` 硬切**：`SettingsScreen` 原来直接换组合，进子页
   「跳一下」。用 `Crossfade(targetState=open, Motion.standard(SHORT))` 淡切 150ms；
   设置页是整页滚动内容，用淡变而不是整屏平移（平移每帧整页重绘）。

40. **搜索改成独立搜索页**：顶栏放大镜原来是把搜索框插进曲库列表第一项（`ProjectList`
   内部状态），翻两页就看不清。现在 `LibraryScreen` 里加 `searchPageOpen`，点放大镜开一层
   全屏 `AnimatedVisibility(pagePushIn/pagePushOut)` 的搜索页：自带「← 搜索」顶栏 +
   `ProjectList(searchOnly = true, header = null)`。`searchOnly` 只保留「大搜索框 + 历史 +
   命中标签 + 结果」：不铺 root/filter 头（传 `header = null`）、空查询不把整个曲库当结果铺、
   不显示「已购未下载」。**坑**：`ProjectList` 里有一句 `if (header == null) filterBlock()`
   是给宽屏左栏固定筛选头用的，`header = null` 的搜索页会因此多出一整条「搜索框 + 排序 +
   筛选」——必须写成 `if (header == null && !searchOnly)`。另外搜索页要 `vm` 层把
   `libraryOverlay` 置真（`onOverlayChange`）并把 `FloatingTopBar` 的可见性加上
   `!libraryOverlay`，否则曲库那颗「曲库」小标题会浮在搜索页上面、并且吃掉顶部 40dp 的点击
   （返回键就在那一段）。搜索框用 `LibrarySearchField(autoFocus = true)` 一进页面弹键盘。

41. **搜索页标签做成两级（本地 / 云端）**：一级是「本地 | 云端」分段控件（一个圆角容器两半，
   选中一半填 primary），二级才是具体栏目：本地 = 曲目 / 作品，云端 = DLsite / asmr.one。
   底下仍是同一份 `searchTab: Int(0..3)`，只按它落在哪一段来呈现，所以「哪些结果上屏」的
   判断一行都不用改。切来源时要**挑真有命中的那一栏**（本地：`trackHits` 空就落「作品」，
   否则会把人送到一屏空白）；云端固定先落 DLsite，由 `LaunchedEffect` 触发在线搜索
   （原来 `if (searchTab == 2)` 没判空查询，空串也会发一次，已补 `query.isNotBlank()`）。

42. **封面尺寸补间必须固定 Coil 的解码尺寸**：`AsyncImage` 不传尺寸时，Coil 按当前布局
   约束算请求尺寸——卡片封面在 72↔132dp 之间补间时，每一帧都是一个新尺寸、新 request key，
   一路 cache miss + 重新裁切解码，一秒钟能产生十几个请求。这就是「点封面切档卡顿」的真凶
   （第 37 条末尾预判的那条）。现在 `CoverBox` 多了 `requestSize` 参数，`TrackCard` 传
   `PlayerDim.box132`：只按最大档解码一次，之后纯 GPU 缩放，布局补间照旧。
   顺带一条：**动画相位不要读在组合阶段**——`EqStrip` 原来是 `phase by animateFloat` 再在
   组合里算 14 个 Bar 的高度，播放中每帧重组 + 重排整条 Row；改成读 `State<Float>` 的
   `Canvas`（draw 阶段读），每帧只重绘。

43. **顶栏「呼吸」那 8dp 没有栏面**：`TopBarMetrics.bar = 内容行 40dp + Spacing.sm`，
   页面留白用 `total`，但 `FloatingTopBar` 的栏面只有 `matchParentSize` 的 40dp（insets 之外）。
   普通页面那 8dp 是页面底色看不出，播放页背后是整屏封面，就成了「顶栏与卡片之间夹一条封面」
   （真机截图里很显眼）。修法别指望给顶栏外层加 padding 撑高：`matchParentSize` 对齐的是
   Box 的内容尺寸，padding 不进去；稳妥做法是播放页自己把这段补上——卡片 `drawWithContent`
   里在 clip 之前 `drawRect(0..top)`，颜色取 `collapsedColor→expandedColor` 插值、alpha 跟
   `p` 走（折叠态 p=0 不画，沉浸感不变），和卡片同色，看不出接缝。

44. **播放页入口从底栏挪到迷你条**：底栏只剩「曲库 / 设置」（`FloatingNavBar` 两格，
   页面下标仍是 0/1/2，用 `slotOf` / `pageOf` 映射），点「播放」Tab 不再进播放页 ——
   `MiniPlayerCard(onOpen)` 是唯一入口，进之前强制 `setPlayerExpanded(false)`，所以永远
   落在半屏；上滑才是全屏（沿用原来的 `progress` 卡片长大）。
   **坑一**：`showFloatingBar` 原来只挡 `immersive && tab == 1`，于是全屏播放页里导航胶囊
   还浮在走带上，真机图里播放键被压掉一条边（「部分被遮挡」）。现在直接 `tab != 1` 不给出场，
   播放页不再有底栏 —— 出口只剩返回键。
   **坑二**：`padOf` 展开态原来额外让 78dp 给胶囊（`sheetBarReserve`），胶囊撤掉后这 78dp
   就成了底部空条 + 走带被顶高；改成「手势条 + 20dp 呼吸」（`sheetBottomBreath`），
   折叠/展开同一个值。
   **坑三**：全屏的返回键别画在播放页自己那层 —— 展开态卡片从 `topReservePx` 起铺满整页，
   画在 Box 里的按钮会被卡片盖住（实测看不见）。放到 `FloatingTopBar` 的 `onBack` 参数里，
   在标题左边，栏里唯一常亮的可点元素。
   顺带：播放页没有底栏了，Snackbar 的底边距要按 `RootDim.playerControlsReserve`（132dp）
   抬到走带之上，否则提示条正好盖住播放键。

45. **播放页改成「盖在主页上的一层」**：`playerTransition` 原来是「曲库淡 90ms + 播放页
   升 1/40 屏淡入」的交叉淡变。播放页背后是整屏封面、卡片底又是半透明玻璃，两层同时半透明
   就会叠成一块灰 —— 用户直接说「很割裂」。现在进播放页时曲库**一点不动**
   （`ExitTransition.None`），播放页整页 `slideInVertically { it }` 从屏幕下沿推上来；
   退出反过来（播放页整页滑下去，曲库 `EnterTransition.None` 原地露出来）。
   教训：只有一层该动的时候，别用交叉淡变去「掩饰」另一层。

46. **一个手势轴串起「全屏 → 半屏 → 主页」**：`progress`（0 折叠 / 1 展开）之外多了
   `dismissed`（0 原位 / 1 整页推出屏幕）。`dragState` 里按 delta 分派——折叠到底还往下拖，
   就推进 `dismissed`（整页跟着手指走，`.graphicsLayer { translationY = dismissed * size.height }`），
   松手过 0.22 屏才 `onExit()`，没过就回弹。这样「半屏下滑回主页」不是按一下跳转，而是跟手。
   拖拽只挂在抓手与曲目卡头上（台本卡里有可滚列表，整页 draggable 会把滚动抢走）。

47. **档位切换要用共享元素，不要 Crossfade**：`Crossfade(isBig)` 是两套完整布局同时半透明，
   抓帧就是「台本、四格、走带各画两遍」的鬼影。现在换成 `AnimatedContent` + **每一档各给一个
   自己的 `AnimatedVisibilityScope`**（`CompositionLocalProvider(LocalSharedElementScope provides this)`），
   大图档与卡片档的封面用同一个 key、注册在不同 scope 里 —— 配对成立，封面走 `boundsTransform`
   位置与尺寸一起插值（72 ⇄ 132 ⇄ 整页大图），不再跳变；其它元素只做一次 90ms 淡入淡出。
   注意：**同一个 key 注册在同一个 scope 里两次会让配对失效**（这是 43/44 轮留下的结论），
   分 scope 才是跨档配对的关键。诊断看 `adb logcat -s KiiteCover`：`match=true` 出现在两档
   交接的那几帧就说明配对成立。

48. **更新日志弹窗要轻量渲染，别把 Markdown 原样铺出来**：`ChangelogDialog` 原来把整份
   文件塞进一个 `Text`，`##`、`**`、`[文字](链接)` 全部原样露出。现在 `ChangelogScreen.kt`
   里有一个按行解析的 `MarkdownLite`（标题 / 版本段 / 小标题 / 列表 / 行内粗体与代码），
   并且**从第一条版本记录开始渲染**——文件开头那段是写给人看的排版规则，不该出现在弹窗里。
   不引第三方库：语法面很窄（就这几种），引一个 1~2MB 的库不划算。

49. **四项体验改造：设置合并 / 台本留白 / 解压进度 / 曲库删除**。
   - 设置页不再单列「账号 / DLsite」：个人资料行 summary 带 DLsite 登录态，
     `ProfilePage` 内直接嵌 `DlsitePage`；设置主页仍能从下载管理进解压。
   - 台本放大后顶部大片留白不是顶栏造成的：阅读模式已经隐藏 chrome、`topReservePx=0`，
     但 `sheetHeight(reservePx = sheetControlsMin)` 仍给走带留 132dp，卡片 `avail` 又减了固定块，
     `morph` 进场插值也把卡片顶边压低。阅读模式统一走 `reservePx=0` / `avail=pageH-pad` /
     `cardH0=avail*p` / `m=1f`，卡片才真正贴着状态栏下方。
     `ReadModeBar` 自己有退出箭头，外层 AppRoot 的 `⌄` 必须靠 `vm.readerMode` 一起隐藏，
     否则两颗箭头叠在一起；退出播放页时记得清状态。
   - 解压入库新增独立 `ExtractState`：空文件提示、运行中 `x/y` + 当前文件名 + 进度条、
     完成/失败文案与「知道了」；按钮运行中禁用。别复用下载态，两条链路会互相覆盖。
   - 曲库删除分两档：默认只写 `hidden-projects.json`（重扫不复活），勾选才 `deleteRecursively`。
     `withManualLinks()` 里统一过滤隐藏作品，删除时同步清播放队列/必要时停止播放。
     安全边界是 canonical path：拒绝根目录本身与根目录外路径，设置页「曲库与扫描」可恢复。
