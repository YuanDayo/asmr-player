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
