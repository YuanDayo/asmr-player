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
5. `README.md` 里的版本行与产物名；
6. 本地校验：

```bash
python scripts/check_version.py v<tag> --release
python scripts/extract_changelog.py v<tag> --check
```

7. 提交、打 Tag（`v` + versionName，去掉 v 后必须完全相等）、推送；
8. CI 自动建 Release（预发布自动不带 Latest；已存在同名 Release 会跳过，不覆盖）。

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

| 维度 | 值 |
|---|---|
| 风格 | **iOS 风格**：白底**无描边**卡片，靠「白底 / 浅灰底」对比分层 |
| 圆角 | 12dp（大卡片 20dp，控件 10dp） |
| 浅色底 / 卡片 | `#F2F2F6` / 白 |
| 深色底 / 卡片 | `#000000` / `#1C1C1E` |
| 主字色 / 次字色 | `#1C1C1E`（深色 `#FFFFFF`）/ `#8E8E93` |
| 强调色 | `#5FC2CE`（深色 `#6FC8D4`） |
| 阴影 | **全部禁用**（用户多次强调「不要叠底感」） |

界面文案与注释一律中文；标题用无衬线重字重，编号/标签用等宽。

## 6. 已知问题与待办

### 需要真机验证

- **DLsite 已购作品的下载成功链路**：入口页 302 → `Set-Cookie` 临时令牌 → CDN 直链 → 落盘。
  模拟器连不上 dlsite.com，本轮只验证到失败态文案与界面不被 WebView 盖住。
- **分卷作品（`/number/N/`）逐个下载**：解析与顺序已写，需多文件作品实测。
- **DLsite 搜索 / 识别是否还弹年龄认证**：请求已统一补 `adultchecked=1`，需真机确认。
- **DLsite 登录后「重新抓取全部头图」是否换回各自封面**（错图修复的验收点）。
- **改下载目录后文件是否真的落到新目录**；换头图三个选项（相册 / 重新抓取 / 恢复默认）。
- **通知栏下载进度与取消**：两个下载并行时的两个取消按钮是否会误停另一个。
- **睡眠定时 / A-B 循环**：逻辑有单测，渐停听感与边界精度需真机。
- 视频播放页书签入口、设置页台本校准入口、共享台本多音频校准语义。

### 明确的待办

- **UI 重构（有参考图，未开工）**：左侧抽屉 + 文件夹列表 + 底部迷你条，属于导航模型级改动。
- **批量校准跨集应用**：偏移可复用，单句微调不能直接套。
- **横屏 / 平板**：目前只有内容限宽 760dp，没有双栏。
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
