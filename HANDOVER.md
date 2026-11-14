# 开发交接文档（Kiite Player）

> 截止时间：v1.6 正式版发布后。下一个会话从这份文档开始读即可。

## 0. 一句话现状

Android ASMR/音声播放器，已更名 **Kiite Player**（包名 `com.kiite.player`）。
当前版本 **v2.4.0 正式版**（versionCode 36，commit 0a5dfb8，tag `v2.4.0`），
228 条单元测试全过。**已作为正式版发布**（GitHub Release 非 Pre-release，
version.json 已推 2.4.0/36 并 purge jsDelivr，应用内更新已推送普通用户）。
下一个 versionCode 是 **37**。

## 1. 项目位置

| 项 | 值 |
|---|---|
| 仓库根目录 | `C:\Users\lipal\Documents\deepseek-harness\default-workspace\asmr-player` |
| GitHub | `YuanDayo/asmr-player` |
| 包名 / namespace | `com.kiite.player` |
| 源码目录 | `app/src/main/java/com/kiite/player/` |
| 单元测试 | `app/src/test/java/com/kiite/player/` |
| Git 分支 | `main`（主）、`asmr`（每次发版强制指向同一提交） |
| 标签 | `v1.0` … `v1.6`（另有一个常驻的 `asmr` 标签） |

**注意**：目录名和 GitHub 仓库名仍是 `asmr-player`，只有应用名与包名改成了 Kiite Player。

## 2. 构建 / 测试 / 模拟器

### 环境变量（每次都要设）

```powershell
$env:JAVA_HOME='D:\android-dev\jdk\jdk-17.0.20.1+1'
$env:ANDROID_HOME='D:\android-dev\sdk'
$env:ANDROID_SDK_ROOT='D:\android-dev\sdk'
$env:GRADLE_USER_HOME='D:\android-dev\gradle-home'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
```

### 常用命令

```powershell
# 测试 + 构建（产物：app\build\outputs\apk\debug\com.kiite.player-<版本>.apk）
& 'D:\android-dev\gradle\gradle-8.11.1\bin\gradle.bat' -p <仓库根> testDebugUnitTest assembleDebug --console=plain

# 只看错误
... 2>&1 | Select-String -Pattern '^e: |FAILED|BUILD '
```

### 模拟器

```powershell
# 启动（AVD 名：asmr）
& 'D:\android-dev\sdk\emulator\emulator.exe' -avd asmr -no-window -no-audio -no-boot-anim -gpu swiftshader_indirect -no-snapshot
# 关闭
& 'C:\platform-tools\adb.exe' emu kill
```

ADB 在 `C:\platform-tools\adb.exe`。测试曲库树在仓库外的 `..\testdata\`（含若干 RJ 作品样本与台本）。

## 3. 发版流程（**必须逐条走完**）

版本号改 **6 个地方**，漏一个就会出现「应用内版本与实际不符」或「更新检查失效」：

1. `app/build.gradle.kts` → `appVersionName` / `appVersionCode`
2. `app/src/main/java/com/kiite/player/AppInfo.kt` → `VERSION_NAME`
3. **`version.json`（仓库根）** → `version` 字段（应用内更新读它）
4. `CHANGELOG.md` → 新增小节，**然后复制到** `app/src/main/assets/CHANGELOG.md`（应用内更新日志读 assets 里那份）
5. `README.md` → 版本行与产物名
6. `BUILD-LOCAL.md` → 产物名

然后：

```powershell
# 提交 + 打标签 + 推送
git add -A; git commit -m '...'
git tag -f asmr HEAD; git tag -f v<版本> HEAD
git push origin main
git push --force origin refs/tags/asmr refs/tags/v<版本>
```

### ⚠️ 发版后必须 purge jsDelivr

应用内更新的**第一数据源是 jsDelivr CDN**，而它会缓存 `@main` 的内容。
**不刷新的话它会返回旧的 `version.json`，导致更新检查误判为「已是最新」**：

```powershell
curl.exe 'https://purge.jsdelivr.net/gh/YuanDayo/asmr-player@main/version.json'
# 验证：应返回新版本号
curl.exe 'https://cdn.jsdelivr.net/gh/YuanDayo/asmr-player@main/version.json'
```

### 创建 GitHub Release + 上传 APK

凭据从 Windows 凭据管理器现取（脚本里**不含令牌**）：

```powershell
$in = @('protocol=https','host=github.com','') -join [Environment]::NewLine
$cred = $in | & 'D:\android-dev\git\cmd\git.exe' credential fill
$token = ($cred | Select-String '^password=').Line.Substring(9).Trim()
```

可复用的脚本在 `tools\gh-release-*.ps1`（每个版本一个，照着改 tag/APK 路径即可）。
流程：POST `/repos/{repo}/releases` 建 Release → POST `https://uploads.github.com/.../assets?name=...` 传 APK。

## 4. 代码结构

```
app/src/main/java/com/kiite/player/
├── core/          纯 Kotlin（无 Android 依赖，全部可单测）
│   ├── LibraryScanner.kt    扫描、作品/章节归并、同名多格式合并、压缩包识别
│   ├── ScriptParser.kt      台本解析（txt/md/docx/pdf/vtt + 行首时间标注）
│   ├── ScriptMatcher.kt     台本六级匹配
│   ├── ScriptCalibration.kt 台本时间轴校准（只存增量，按行数净化）
│   ├── PlayHistory.kt       播放历史 / 书签的纯逻辑（播放次数、听完、续播点）
│   ├── Dlsite.kt            DLsite 作品页解析、DlsiteWork 模型
│   ├── DlsiteAuth.kt        登录态判断、购买记录解析、各类 URL
│   ├── Rating.kt            成人/全年龄判定
│   ├── TrackTags.kt         MP3/FLAC/MP4 标签读写
│   └── ...
├── data/          Android 相关
│   ├── LibraryRepository.kt 文件系统操作、zip 解压（extractZip 支持指定目标目录）
│   ├── SettingsStore.kt     DataStore 偏好
│   ├── RatingStore.kt       人工分级（filesDir/ratings.txt）
│   ├── HistoryStore.kt      播放历史（filesDir/history.json）
│   ├── BookmarkStore.kt     书签（filesDir/bookmarks.json）
│   ├── ScriptCalibrationStore.kt 台本校准（filesDir/script_calibration.json）
│   ├── DlsiteClient.kt      DLsite 抓取（带 Cookie）
│   ├── DlsiteStore.kt       作品元数据缓存
│   └── UpdateChecker.kt     应用内更新（四源回退）
├── playback/      Media3 服务与控制器
│   ├── PlaybackService.kt   常驻播放服务：ExoPlayer、定时停止、A-B 循环都在这里
│   ├── SleepTimer.kt        睡眠定时纯逻辑（渐停、剩余时间、文案）
│   ├── AbLoop.kt            A-B 区间纯逻辑
│   └── LyricOverlayController.kt 桌面台本悬浮窗
├── ui/            Compose 界面
│   ├── AppRoot.kt           Scaffold、顶栏、底栏、迷你条、全局弹窗
│   ├── LibraryScreen.kt     曲库、搜索、筛选、批量分类
│   ├── PlayerScreen.kt      播放页（曲目卡 / 四格入口 / 台词卡 / 走带 / 阅读模式 / 视频全屏）
│   ├── SettingsScreen.kt    多级设置菜单、DLsite 页、关于页
│   ├── DlsiteLoginScreen.kt 整页 WebView 登录
│   ├── DlsiteDownloadScreen.kt 下载页 WebView + 拦截下载
│   ├── ScriptCalibrationUi.kt 播放页顶部校准条（逐句 / 偏移）
│   ├── BookmarksPanel.kt    书签 + 历史双页
│   ├── theme/Theme.kt       皮肤、配色、形状、字体
│   └── AsmrCard.kt          卡片、边框、底色辅助（**现在 asmrBorder 恒为空**）
└── util/
```

**状态与逻辑分层**：`ui/MainViewModel.kt` 只保留状态出口和转发，具体规则下沉到几个控制器
（都是普通类，靠 lambda 注入依赖，不反向持有 ViewModel，可单独看、单独测）：

| 控制器 | 管什么 |
|---|---|
| `ui/LibraryController.kt` | 扫描、`rawScan` + `manualLinks` 覆盖层、按作品/章节取曲目、压缩包解压 |
| `ui/PlaybackController.kt` | MediaController、播放状态、播放模式、队列增删/恢复、多格式变体、进度存档 |
| `ui/ScriptController.kt` | 台本加载、匹配状态、时间轴校准 |
| `ui/HistoryController.kt` | 播放历史、收听时长、续播 |
| `ui/BookmarkController.kt` | 书签增删改、跳转 |
| `ui/WorkMetaController.kt` | 分级、自定义标签、作品名/收藏/梗概、单集改名与隐藏 |
| `ui/DlsiteController.kt` | DLsite 识别、登录、已购同步、应用内下载 |

后台行为（定时停止、A-B 循环）不放在 ViewModel：判定在 `PlaybackService` 里跑，
ViewModel 只转发命令、取状态——切页面 / 界面重建都不会中断循环或定时。


## 5. 设计规范（用户明确要求，改动务必遵守）

| 维度 | 值 |
|---|---|
| 风格 | **iOS 风格**：白底**无描边**卡片，靠「白底 / 浅灰底」对比分层 |
| 圆角 | **12dp**（大卡片 20dp，控件 10dp） |
| 浅色底 | `#F2F2F6`（iOS 灰） |
| 深色底 | `#000000`，卡片 `#1C1C1E` |
| 主字色 | `#1C1C1E` / 深色 `#FFFFFF` |
| 次字色 | `#8E8E93`（iOS secondary） |
| 强调色 | `#5FC2CE`（耳机青**降饱和**后的值，深色 `#6FC8D4`） |
| **阴影** | **全部禁用**（用户多次强调「不要叠底感」） |

- 界面文案、注释一律**中文**
- 标题用无衬线重字重，编号/标签用等宽字体
- 参考素材：用户曾发过 iOS 风格 HTML 示例与真机截图，设计均据此对齐

## 6. 已知问题与待办

### 未验证的部分（我无法在本地验证，需真机）

- **声优解析**：正则按 `/fsr/=/voice/` 推断，**从未用真实作品页 HTML 核对过**。若「声优」筛选恒为空，拿真实 HTML 修正则。
- **DLsite 应用内下载**：本机 DNS 劫持 dlsite.com，我一次都没跑通过。用户反馈「能下载、能解压」，但**下载直链的 Cookie 白名单、blob 下载等分支未实测**。
- **视频播放**：无样片。用户已确认「可以播放」。
- **应用内更新**：刚做好，用户尚未反馈结果。jsDelivr 源已实测可用。
- **睡眠定时 / A-B 循环**：纯逻辑（`SleepTimer` / `AbLoop`）有单测；渐停听感、A-B 边界精度需真机确认。
- **通知栏定时提示**：用 `SleepNotificationProvider` 继承 `DefaultMediaNotificationProvider`，
  只覆写正文（`createNotification` 是 final，改不了别的）。倒计时靠服务每秒调
  `onUpdateNotification` 催重建。**只能在真机上看效果**：
  锁屏、通知被折叠、厂商 ROM 换皮通知样式都可能让这行字显示不全。
  想加「取消定时」按钮的话要走 `addNotificationActions` + 自定义 SessionCommand，
  没真机验之前不建议动。
- **台本时间轴校准**：单句对齐取的是「播放器实时位置」，误差约 ±100ms（界面轮询 400ms 已绕开）；
  真机上是否够用要看用户反馈。
- **批量校准 / 阅读模式**：逻辑（`CalibrationMath.shiftRange`）有 19 条单测，
  但两者都是纯 UI 交互，只能真机验：批量面板在窄屏上「全部 / 当前句之后 / 自定义」三个
  FilterChip 会不会换行、阅读模式在刘海屏上顶栏会不会被系统栏压住。
- **播放页版式（2.3.1）**：在 1080×2400 / 420dpi 的模拟器上逐屏截图核过
  （曲目卡、四格、台词卡、播放列表页内切换、更多菜单、台本全屏阅读），
  但**深色主题与「版式」皮肤未在模拟器上切过**，两种皮肤下卡片底色与描边需要真机确认。

### 明确的待办

- **P1 剩余项**：视频播放页的书签入口、设置页的台本校准入口、
  共享台本文件（多个音频共用一个 txt）的校准语义、B 点设到曲目最末尾的边界处理。
- **批量校准跨集应用**：目前批量只作用于「当前这一集的台本」。同一作品多集若共用同一份
  台本（或录音整体晚同样的量），需要「把本集偏移套到同作品其他集」——难点是各集台本
  行数可能不同，偏移可以复用、单句微调不能。
- **UI 重构（用户发过参考图，尚未开工）**：参考图是**左侧抽屉导航 + 文件夹列表 + 底部迷你条**。
  现有结构是**底部三标签（曲库/播放/设置）**，这是**导航模型级别的改动**，不是调样式。
  建议顺序：① 抽屉骨架 ② 顶栏语义 ③ 文件夹视图 ④ 曲目行 ⑤ 底栏改造 ⑥ 横屏双栏。
  参考图核心特征：文件夹行 = 图标 + 名称 + 「N 首」 + 右侧三点菜单；颜色沿用现有冷灰 + 降饱和青。
- **横屏 / 平板**：目前只做了「内容限宽 760dp 居中」，**没有双栏布局**。
- **冒号小事项**：仓库/目录改名（如需与 Kiite Player 统一，远程 remote 也要改）。

## 7. 环境限制（重要）

- **本机 DNS 劫持** `dlsite.com` 与 `github.com`（含 `raw.githubusercontent.com`）。
  所以 **DLsite 抓取、GitHub API/raw 我都无法自测**。
- `api.github.com`、`cdn.jsdelivr.net` 可用（curl 需加 `--ssl-no-revoke`）。
- `web_fetch` 对上述域名无效；用 `curl.exe --ssl-no-revoke`。
- 本会话的沙箱策略为 `danger-full-access`，**审批提示已禁用**——不要传 `sandbox_permissions`，会被直接拒绝。
- 文件是 **CRLF**；用 Node 做字符串替换前先 `.replace(/\r\n/g, '\n')`。

## 8. 踩过的坑（避免重犯）

1. **图标四角发黑**：我以为是透明像素，实际源 PNG 是 RGB 无 alpha，**四角是烤死的纯黑**。
   → 修法是自己套圆角遮罩刷白。**教训：先探测真实数据，别凭假设动手。**
2. **自适应图标裁掉角色**：前景满铺 108dp，系统只显示中间约 66dp。→ 前景要缩到 80% 居中。
3. **沉浸模式被困**：用户说「底栏消失」，我直接改成了隐藏顶栏——**但行为本来是对的**，
   真正的问题是**播放页空态不渲染头部，退出口不存在**。
   → **教训：用户报症状时，先问清是「行为错」还是「入口丢了」。**
4. **「检查更新」没反应**：我写完了检查逻辑和 ViewModel 状态，**但弹窗 UI 从来没写**。
   → **教训：功能完成 = 界面真的接上了。做完 grep 一次 UI 层是否引用了新状态。**
5. **全屏退出按钮点不到**：`PlayerView` 用 `SurfaceView`，处于**独立图层，会盖住叠在它上面的 Compose 控件**。
   → 退出按钮必须放在视频**外面**。
6. **`runCatching{}.getOrNull()` 把失败和空结果压成同一个值**，导致网络故障被显示成「已是最新」。
   → 要区分「成功但无更新」与「请求失败」。
7. **jsDelivr 缓存 `@main`**：更新 version.json 后不 purge，更新检查会误判。

## 9. 工作区其他内容

| 路径 | 说明 |
|---|---|
| `promo/` | 宣传图：更新海报 / UI 展示 / 使用指南（1.5 时期制作，可能需按 1.6 重做） |
| `promo/raw/` | 历次真机截图 |
| `tools/make-*.py` | 图标生成、海报生成的 Python 脚本 |
| `tools/gh-release-*.ps1` | GitHub Release 创建与附件上传脚本 |
| `tools/gh-mark-prerelease.ps1` | 把旧版本标为 Pre-release |
| `testdata/` | 测试曲库树（模拟器用） |

（以上均在**仓库外的工作区**，除 `tools/make-promo.py` 等历史文件外不进仓库。）
