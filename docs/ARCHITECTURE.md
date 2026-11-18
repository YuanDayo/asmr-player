# 架构说明

Kiite Player = 本地 ASMR 音频库播放器 + asmr.one / DLsite 在线抓取与下载。
单 module（`:app`），Kotlin + Jetpack Compose，**没有 DI 框架**，所有依赖在 `MainViewModel` 与各 Controller 里手工组装。

## 1. 分层

```
app/src/main/java/com/kiite/player/
├── AsmrApplication.kt      Application：Coil ImageLoader、全局初始化
├── MainActivity.kt         单 Activity + Compose
├── AppInfo.kt             版本号/作者信息的唯一读取口（BuildConfig）
├── core/     23 files     纯逻辑与模型：扫描、台本解析、标签读写、DLsite 页面解析、SemVer、格式化
├── data/     16 files     持久化与网络：DataStore、JSON 存储、asmr.one / DLsite 客户端、更新检查
├── domain/   12 files     Controller：把 data 层能力包成 StateFlow + 挂起函数，供 UI 调用
├── ui/       18 files     Compose 界面 + MainViewModel（唯一 ViewModel）
├── playback/  7 files     Media3 ExoPlayer、MediaSessionService、A-B 循环、音效、睡眠定时
├── download/  1 file      前台下载服务（asmr.one 与 DLsite 共用一条通知）
├── pdf/       1 file      PDFBox 抽取台本文字
└── util/      2 files     SAF 路径还原、权限
```

调用方向是单向的：`ui → domain → data / core`。`core` 不依赖 Android 之外的东西（少数地方用 `Context`），
也不引用 `domain`，所以可以直接写 JVM 单测。

## 2. 状态与并发

- 每个 Controller 内部持 `MutableStateFlow`，对外暴露 `StateFlow`；UI 用 `collectAsStateWithLifecycle()`。
- 磁盘与网络操作一律在 `Dispatchers.IO`，UI 只在主线程读状态。
- 长任务（下载）跑在应用级 `CoroutineScope`（`SupervisorJob`），不随界面销毁而中断。
- 结果反馈统一走 `vm.say("...")`（底部提示），失败必须说清原因，不许静默。

## 3. 持久化

| 内容 | 位置 | 实现 |
|---|---|---|
| 设置（曲库路径、下载目录、皮肤、开关） | DataStore Preferences | `data/SettingsStore.kt` |
| 播放进度、历史、评分、书签、台本校准、手动关联 | `filesDir` 下的 JSON | `*Store.kt` |
| 自选头图 | `filesDir/custom-covers/` | `data/CoverOverrideStore.kt` |
| 媒体文件与台本 | 用户选的曲库目录 | 直接读写文件 |

**没有数据库**。JSON 存储都走 `kotlinx.serialization`，写文件用临时文件 + rename，避免写坏。

## 4. 关键链路

### 曲库扫描
`LibraryScanner` 遍历目录 → `ProjectGrouper` 把音频归成作品 → `ScriptMatcher` 配台本（扩展名/序号/模糊匹配）
→ `LibraryRepository` 产出 `Project` 列表 → `LibraryController` 暴露给 UI。
扫描是幂等的：文件没变就复用上次解析结果（缓存见 `LibraryRepository`）。

### 播放
`PlaybackController` 持 `PlayerState`（当前曲目、队列、进度）→ `PlaybackService`（`MediaSessionService`）真正持有
ExoPlayer，通知栏与锁屏由 Media3 负责；`AbLoop` / `AudioFxEngine` / `SleepTimer` 在服务侧生效，与界面解耦。

### 在线下载（asmr.one 与 DLsite）
两条链路共用一套体验：`DownloadService` 前台服务 + 通知栏进度 + 取消。
- asmr.one：`AsmrOneClient` 拿直链 → `AsmrOneDownloads` 写 `.kiite-part` → 校验后改名。
- DLsite（已购）：`DlsiteClient.resolveDownload` 请求入口页但**不跟随 302**，把 `Location`（CDN
  直链）与 `Set-Cookie`（临时令牌）一起收下；分卷作品按 `/number/N/` 逐个解析。`DlsiteDownloads`
  按文件顺序下载，每个文件带自己的令牌与 `Referer`。WebView 只用于登录和「手动下载」兜底。
两个下载的状态在 `DownloadService` 里 `combine`，一条通知里给两个取消动作。

### 封面/头图
`DlsiteController` 抓封面时要求页面里出现该作品编号，URL 也必须指向该编号（防止把站内 banner 当封面）；
自选头图记在 `CoverOverrideStore`，优先于抓取结果。

### 应用内更新
`data/UpdateChecker` 按 jsDelivr → raw.githubusercontent → GitHub API → Releases 网页 302 的顺序回退。
版本比较走 `core/SemVer`（`scripts/semver.py` 的 Kotlin 版），并且：
正式版用户不接收 beta/alpha/rc；能拿到 `versionCode` 时以它为准。
信道元数据来自 `version.json`（正式通道）与 GitHub `/releases/latest`（自动排除预发布）。

## 5. UI

`ui/AppRoot.kt` 是导航骨架（Navigation Compose），其余 Screen 都是无状态 Composable + 回调。
`MainViewModel` 是唯一的 ViewModel，聚合各 Controller，暴露 `StateFlow` 与 `fun`；它不写业务逻辑，
只做转发和提示。界面样式集中在 `AppRoot` 的主题与 `asmrShape` 等小工具里，改动前先看现有页面保持一致。

## 6. 测试

- `app/src/test/` 下是纯 JVM 单测（约 330 个），覆盖版本比较、扫描/匹配、标签读写、台本解析、下载状态机等。
- 网络相关测试用自写的 `ServerSocket` 迷你 HTTP 服务（`TinyServer`），不依赖 MockWebServer。
- 跑：`./gradlew :app:testDebugUnitTest`。
- **没有** instrumentation 测试；UI 行为靠真机验证。
