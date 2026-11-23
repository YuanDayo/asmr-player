# Kiite Player 修复计划书（第二批 · 可直接交给 AI 执行）

> 生成日期：2026-11-23
> 范围约束：**只修下面 6 件事，不碰无关功能，最小改动、可回归验证。**
> 目标读者：接手的 AI / 开发者。本计划书只描述「查什么、改哪、怎么验证」，不含补丁。
> 附：最后一条是「自定义方向的建议」，供团队决策，非本次必改。

---

## 0. 执行前必读（环境与规范，与上批一致）

1. 仓库根：`app/src/main/java/com/kiite/player`。规则入口见根 `AGENTS.md`。
2. 版本/日志规范见 `docs/RELEASE-SPEC.md` 与 `AGENTS.md`：默认发**预发布**；更新日志一条一行 ≤60 字、小标题只允许「新增/修复/调整/注意」、不发「- 无」。
3. 本地构建（绝对路径 + `-p`）：
   ```bash
   export JAVA_HOME=/d/android-dev/jdk/jdk-17.0.20.1+1
   export ANDROID_HOME=/d/android-dev/sdk
   export GRADLE_USER_HOME=/d/android-dev/gradle-home
   ./gradlew -p <repo> :app:testDebugUnitTest :app:assembleDebug --offline --console=plain
   ```
4. UI 回归用 `scripts/ui_verify.sh <apk> <out> <mode>`（一次脚本跑完，勿接管道；装完核对 `versionName`）。
5. 交付时给**文件路径清单**（`python scripts/changes_md.py` 生成，新增/修改/删除分组）。

---

## 问题一：播放页「查看图片」只识别主目录、子文件夹图片漏掉

### 1.1 现象
作品目录的子文件夹里也有图片，但「查看图片」只列出当前曲目所在目录 + 作品根目录**浅层**的图片，子文件夹里的图片显示不全。

### 1.2 根因（已定位到具体代码）
- `domain/PlaybackController.kt:402-416` 的 `imagesOfCurrent()`：
  ```kotlin
  val dirs = listOfNotNull(t.folderPath.takeIf { it.isNotBlank() }, projectRoot).distinct()
  for (d in dirs) {
      val dir = File(d)
      dir.listFiles()?.sortedBy { it.name }?.forEach { f ->
          if (f.isFile && isImagePath(f.name)) out.add(f.absolutePath)
      }
  }
  ```
  `dir.listFiles()` 是**单层枚举，不递归**。作品结构若是「根目录/子文件夹A/子文件夹B/*.jpg」，这些嵌套图片永远进不来。
- 辅助确认：`core/LibraryScanner.kt` 的 `walk()` 扫描阶段**根本不收集图片**（只收 audio/script/archive），所以图片列表完全依赖播放时的 `imagesOfCurrent()` 现算，一旦这里不递归就漏。

### 1.3 需检查的模块
| 模块 | 文件 | 关注点 |
|---|---|---|
| 图片收集 | `domain/PlaybackController.kt` | `imagesOfCurrent()` 第 402-416 行 |
| 图片扩展名 | `core/LibraryScanner.kt` | `IMAGE_EXT`、`isImagePath()`（第 12-17 行） |
| 图片墙/查看器 | `ui/PlayerScreen.kt` | `ImagePanel`(3250)、`ImageViewer`(3293) 消费列表 |
| 入口 | `ui/MainViewModel.kt:1369` | `imagesOfCurrent()` 转发 |

### 1.4 修复策略（最小改动）
把 `imagesOfCurrent()` 的目录枚举从「单层」改为「递归」，并限制深度/数量，避免误伤性能：
1. 新增一个私有递归 helper（如 `collectImages(dir: File, out: MutableList<String>, depth: Int)`），用 `dir.walkTopDown()` 或手写递归，只收 `isImagePath` 的文件。
2. 设**深度上限**（建议 ≤ 4 层）与**数量上限**（建议 ≤ 200 张），防超大目录卡顿。
3. 保持去重（`out.distinct()`）与按文件名排序，观感与现在一致。

### 1.5 实施步骤
1. 改 `PlaybackController.imagesOfCurrent()` 为递归收集（加深度/数量护栏）。
2. 编译 + 现有单测（若有 `imagesOfCurrent` 相关测试则更新）。
3. 用真实多级目录作品验证。

### 1.6 验证方法与验收标准
- **验证**：构造「主目录 + 子文件夹A/B 各有图片」的作品，播放后点「查看图片」。
- **验收标准**：主目录与所有子文件夹的图片都出现，顺序稳定；数量达上限时不卡顿、不崩溃；无图片作品仍显示「这个作品没有图片」。

---

## 问题二：初次下载没走功能引导，直接跳到「使用须知与免责声明」

### 2.1 现象
全新安装首次进入，没看到 5 页功能说明，直接落到「使用须知与免责声明」条款页。

### 2.2 根因（已定位）
`ui/AppRoot.kt:431-444` 的触发逻辑：
```kotlin
LaunchedEffect(settings.onboardingSeen, settings.onboardingVersion) {
    val seen = settings.onboardingSeen ?: return@LaunchedEffect
    when {
        !seen -> { onboardingLegalOnly = false; onboardingOpen = true }
        settings.onboardingVersion < ONBOARDING_GUIDE_VERSION -> {
            onboardingLegalOnly = true; onboardingOpen = true   // ← 只补条款
        }
    }
}
```
而 `data/SettingsStore.kt:250` 读盘是 `onboardingSeen = p[...] ?: false`，`onboardingVersion = p[...] ?: 0`。

「初次下载」若是**升级安装**（旧版已把 `onboardingSeen` 写成 true、但没写 `onboardingVersion`），则 `onboardingSeen=true` 且 `onboardingVersion=0 < 1` → 命中 `legalOnly=true` 分支，**跳过功能页、直接进条款页**。

另一条路径：`SettingsStore.kt:432` 的 `overwriteAll` 里 `p[ONBOARDING_SEEN] = value.onboardingSeen ?: true`，备份恢复会把 seen 写成 true、version 保持旧值 → 同样触发 legalOnly。

### 2.3 需检查的模块
| 模块 | 文件 | 关注点 |
|---|---|---|
| 触发逻辑 | `ui/AppRoot.kt` | 第 431-444 行两档判断 |
| 读盘兜底 | `data/SettingsStore.kt` | 第 250-252 行、`AppSettings` 第 117-125 行 |
| 备份恢复 | `data/SettingsStore.kt` | 第 432-434 行 `overwriteAll` |
| 内容版本 | `ui/OnboardingScreen.kt` | `ONBOARDING_GUIDE_VERSION`(108) |

### 2.4 修复策略（最小改动，二选一，优先 A）
- **策略 A（推荐）**：把「升级安装」与「全新安装」的语义对齐。核心是让 `onboardingVersion` 成为「是否真正走完过新版引导」的唯一判断，`onboardingSeen` 仅作为「是否看过功能页」的辅助。具体：
  1. 全新安装（`onboardingSeen == false`）→ 完整流程（legalOnly=false），**这是现状正确的部分，保持**。
  2. 关键修复：升级安装若 `onboardingVersion == 0`（从未走完过新版含条款的引导），应**也走完整流程或至少补功能页**，而不是只补条款。将 `when` 分支改为：
     - `onboardingSeen == false` → 完整流程；
     - `onboardingSeen == true && onboardingVersion < 1` → 需要区分「老用户只补条款」还是「新用户补功能+条款」。**问题本质是「新用户被误判成老用户」**，需要引入一个能区分二者的依据。
- **策略 B（更稳）**：引入独立标记区分「是否已经同意过条款」与「是否看过功能页」。将 `onboardingVersion` 的默认语义固定为「0=从没见过任何引导」，并让「功能页看过」与「条款同意」分别落盘。这样升级安装时：功能页没看过 → 走完整；条款没同意 → 补条款。避免布尔+版本号的二义性。

> 决策建议：优先排查「初次下载」到底是全新安装还是升级安装。若是升级安装（旧版本就有 `onboarding_seen=true`），策略 A 的「`onboardingSeen==true && version<1`」分支需要重新定义触发条件——建议改为「`version<1` 时，功能页 + 条款页都走（完整流程）」，因为 version 落后本身就说明这一版引导用户从没看全过。这最简单、副作用最小。

### 2.5 实施步骤
1. 明确「升级安装」下 `onboardingVersion` 落后的预期行为（完整流程 vs 只补条款）。
2. 改 `AppRoot.kt:431-444` 的分支，消除「新安装被当成只补条款」的误判。
3. 核对 `overwriteAll` 里 onboarding 字段的迁移，保证备份恢复不触发错误的 legalOnly。
4. 编译 + 单测（`VersionConsistencyTest` 是否覆盖 onboarding，若无则补纯函数测试）。

### 2.6 验证方法与验收标准
- **验证**：全新安装、以及「旧版本数据 + 升级安装」两种路径分别冷启动。
- **验收标准**：
  - 全新安装 → 先 5 页功能说明，再条款页。
  - 旧版本升级（没看过新版引导）→ 完整流程（功能页 + 条款），不直接跳条款。
  - 已经同意过条款的老用户 → 不再被打扰。
  - 「跳过」功能页后仍必须过条款页。

---

## 问题三：同意协议后，设置页重新打开仍是「未同意」状态

### 3.1 现象
在首启引导里勾选同意、点「同意并开始使用」后，到设置页再打开「使用须知/免责声明」，仍显示「未同意」。

### 3.2 根因（已定位）
- 「同意」动作只写 `onboardingSeen=true` + `onboardingVersion=1`（`MainViewModel.kt:1732` → `SettingsStore.setOnboardingDone(version)`，第 367-369 行）。
- 但**没有独立、可查询的「条款同意」持久化字段**；设置页的 `TERMS`/`DISCLAIMER` 子页是纯文本（`SettingsScreen.kt:623-624` 直接 `LegalTextPage(...)`），**根本没有「同意状态」的展示**。
- 用户感知的「未同意」源于：设置页没有一个「已同意」的标识或状态，或「同意」写入与读取之间有时序/竞态（`viewModelScope.launch` 异步写盘，用户立刻切设置页时尚未落盘）。

### 3.3 需检查的模块
| 模块 | 文件 | 关注点 |
|---|---|---|
| 同意写盘 | `data/SettingsStore.kt` | `setOnboardingDone`(367)、`overwriteAll`(432) |
| 同意入口 | `ui/MainViewModel.kt` | `markOnboardingDone`(1732) |
| 设置页条款 | `ui/SettingsScreen.kt` | 第 623-624 行、`SettingsPage.TERMS/DISCLAIMER` |
| 引导条款 | `ui/OnboardingScreen.kt` | `OnboardingLegalStep`(236) 勾选状态 |

### 3.4 修复策略（最小改动）
1. 增加一个可查询的「条款已同意」状态：复用现有 `onboardingVersion >= ONBOARDING_GUIDE_VERSION` 作为「已同意条款」的判据（因为 version 1 就代表「同意并走完」）。
2. 在设置页「使用须知 / 免责声明」条目（或子页顶部）**显示一个「已同意 / 未同意」的只读状态**，状态来自 `settings.onboardingVersion >= 1`。这样用户能明确看到自己已经同意过。
3. 若「同意写盘是异步导致未落盘」是主因，把 `setOnboardingDone` 的写盘改成**先本地 `_settings` 立即更新、再落盘**（或确保引导关闭前 `markOnboardingDone()` 已完成），消除竞态。
4. 统一语义：`onboardingVersion >= 1` ≡ 「已同意使用须知与免责声明」，全项目只用这一个判据，避免两处各写一套。

### 3.5 实施步骤
1. 在设置页条款子页增加「已同意/未同意」状态显示（读 `settings.onboardingVersion`）。
2. 消除 `markOnboardingDone` 的写盘竞态（必要时同步更新内存态）。
3. 编译 + 验证。

### 3.6 验证方法与验收标准
- **验证**：首启同意 → 立即进设置 → 打开「使用须知/免责声明」。
- **验收标准**：设置页明确显示「已同意」；重启后仍为「已同意」；未同意过的全新安装显示「未同意」。

---

## 问题四：DLsite 每次应用退出后都要重新验证

### 4.1 现象
DLsite 登录成功后，退出应用再进，登录态丢失，需要重新验证/登录。

### 4.2 根因（已定位）
- 登录态完全依赖 WebView 的 `CookieManager`（`data/DlsiteStore.kt:90-103` 的 `dlsiteCookieHeader()` 实时从 CookieManager 收集），没有独立持久化。
- `domain/DlsiteController.kt:607-608`：`_loginState = MutableStateFlow(UNKNOWN)`，启动后必须 `refreshLogin()` 联网验证（第 613-620 行）才变成 `LOGGED_IN`。
- `ui/DlsiteLoginScreen.kt:90-91` 有 `setAcceptCookie(true)` + `setAcceptThirdPartyCookies(true)`，理论上 CookieManager 会持久化，但：
  - DLsite 的登录会话 Cookie **可能是 session cookie（无过期时间）**，Android 部分机型 / 系统清理 WebView 数据后 session cookie 不保留；
  - `dlsiteCookiesSnapshot()` / `restoreDlsiteCookies()`（`DlsiteStore.kt:116-138`）**只在备份恢复流程用到，启动时没有自动 restore**；
  - 冷启动网络慢/无网时，`refreshLogin()` 验证失败 → 状态停在 `UNKNOWN`，UI 表现为「未登录/需重新验证」。

### 4.3 需检查的模块
| 模块 | 文件 | 关注点 |
|---|---|---|
| Cookie 收集 | `data/DlsiteStore.kt` | `dlsiteCookieHeader`(90)、`dlsiteCookiesSnapshot`(116)、`restoreDlsiteCookies`(128) |
| 登录态 | `domain/DlsiteController.kt` | `_loginState`(607)、`refreshLogin`(613) |
| 登录 WebView | `ui/DlsiteLoginScreen.kt` | Cookie 开关(90-91) |
| 启动初始化 | `domain/DlsiteController.kt` / `MainViewModel` init | 是否有「启动即 restore + 验证」 |

### 4.4 修复策略（最小改动）
1. **登录成功后主动持久化 Cookie**：在登录完成（`refreshLogin` 判定 LOGGED_IN 后，或登录页点「完成」时）调用 `dlsiteCookiesSnapshot()` 把各子域 Cookie 存到本地文件（如 `filesDir/dlsite-cookies.json`）。
2. **应用启动时自动 restore**：`DlsiteController` 初始化 / `MainViewModel.init` 里，先 `restoreDlsiteCookies(快照)` 写回 CookieManager，再 `refreshLogin()` 验证。这样即便系统清了 WebView 数据，也能从本地快照恢复会话。
3. **（可选）降级 UNKNOWN 显示**：`refreshLogin` 失败且本地有 Cookie 快照时，不直接显示「未登录」，而是显示「登录状态待验证」并允许重试，避免无网时误判为登出。
4. 若确认是 session cookie 不持久，可考虑在登录页 `onPageFinished` 后主动 `CookieManager.flush()`。

### 4.5 实施步骤
1. 在 `DlsiteStore` 增加 `saveCookiesSnapshot` / `loadCookiesSnapshot`（复用现有 snapshot 结构）。
2. 登录成功路径调用保存；启动路径调用恢复。
3. 编译 + 真机验证（模拟器 IPv6 连不上 DLsite，登录态验证需真机）。

### 4.6 验证方法与验收标准
- **验证**：真机登录 DLsite → 完全退出（杀进程）→ 重进 → 观察是否仍需重新登录。
- **验收标准**：登录后重启应用，登录态保持（`LOGGED_IN`），无需重新验证；清 Cookie/清数据后确实需要重新登录；无网时状态可区分「待验证」与「未登录」。

---

## 问题五：收听统计不够具体

### 5.1 现象
当前收听统计只有「今日/近7天/近30天/累计 + 近14天柱状图 + 连续天数 + 最常听」，维度单一、颗粒度粗，用户希望像读书 App「阅读总览」（见参考图）那样更具体。

### 5.2 现状（已定位）
- 统计页：`ui/HomeScreen.kt:936-1110` 的 `HomeStatsPage`。
- 数据源：`history.json`（`HistoryFile.dailyListenedMs: Map<String,Long>` 按天，`totalListenedMs`，`records`）——**目前只有「按天」时长，没有「按小时」分布，也没有「按月×星期」的矩阵**，因此做不出参考图里的「时长分布柱状图（按小时）」和「热力图（月×星期）」。
- 计算工具：`core/PlayHistory.kt` 的 `HistoryMath`（`dayKey`/`addListened`/`formatDuration` 等）。

### 5.3 需检查的模块
| 模块 | 文件 | 关注点 |
|---|---|---|
| 统计页 UI | `ui/HomeScreen.kt` | `HomeStatsPage`(936)、`HomeStatCard`(831) |
| 数据模型 | `data/HistoryStore.kt` | `HistoryFile`(12) 字段 |
| 计算 | `core/PlayHistory.kt` | `HistoryMath` |
| 落点 | `domain/HistoryController.kt` | `addListened` 写入时机与粒度 |

### 5.4 修复策略（最小改动，分两档）
**第一档（本次建议做，改动小、收益大）**：
1. 增加**维度切换**：日 / 周 / 月 / 年 / 总（参考图顶部 Tab）。各维度复用 `dailyListenedMs` 聚合。
2. 增加**收听时长分布（按小时）**：需在 `HistoryFile` 增加 `hourlyListenedMs: Map<String,Long>`（key=`yyyy-MM-dd-HH` 或按小时累加），`addListened` 时同步累加；展示为参考图那种「24 小时柱状图」。
3. 增加统计卡片：收听天数、累计收听、听完作品数（`records.finished` 计数）、在听作品数、平均每次时长等（参考图「阅读数据」卡片）。

**第二档（可选，工作量大）**：
4. **热力图（月 × 星期）**：需按 `LocalDate` 生成「近 12 个月 × 星期几」的矩阵，用 `dailyListenedMs` 的日期即可算，无需新增存储；用颜色深浅表示时长（参考图「阅读热力图」）。
5. 每维度下的明细（如「年」视图展示每月汇总、「周」视图展示每日）。

### 5.5 实施步骤
1. 若做「按小时分布」，先扩展 `HistoryFile` + `HistoryMath.addListened` 落点（注意兼容旧 `history.json`，`ignoreUnknownKeys` 已开）。
2. 在 `HomeStatsPage` 增加维度切换与新增卡片/图表。
3. 复用现有 `AsmrCard` / 柱状图渲染风格（`HomeDim.chart` 等）。
4. 编译 + 单测（`PlayHistoryTest` 补 `addListened` 按小时/按天覆盖）。

### 5.6 验证方法与验收标准
- **验证**：播放若干曲目后进统计页，切换各维度观察数据正确性。
- **验收标准**：日/周/月/年/总切换正常；按小时分布与真实收听一致；卡片数值正确；旧数据兼容不崩。

---

## 问题六（自定义方向建议）：收听统计的「更具体」产品方向

> 本项为**建议**，供团队决策，是否纳入、纳入哪些由你定。

参考图（读书 App「阅读总览」）给出的可借鉴方向：

1. **维度切换（日/周/月/年/总）**：对应 ASMR 场景即「今天/本周/本月/今年/累计」，顶部 Tab + 左右箭头切区间。
2. **时长分布柱状图**：读书按「小时」分布；ASMR 可做成「按 24 小时分布」（习惯睡前听的人能看到峰值在 22-24 点），或「按星期几分布」。
3. **热力图（月 × 星期）**：一屏看整年「哪天听得多」，对「睡前 ASMR 习惯」特别直观。
4. **统计卡片升级**：收听时间、收听天数、累计听过（部）、听完（部）、在听（部）——对应 ASMR 的「作品数 / 章节数」。
5. **目标闭环**：现有「今日目标分钟数」+ 进度，可加「连续达标天数」「本周达标」等。
6. **最常听/最爱社团/声优**：已有「最常听」，可扩到「最常听的社团 / 声优」（依赖 `records` 里的 `projectName`，若标签/社团信息不足需补元数据）。

**建议优先级**：①维度切换 → ②按小时/星期分布 → ③统计卡片升级 → ④热力图 → ⑤目标闭环。前两档改动集中在 `HomeStatsPage` + `HistoryMath` + `HistoryFile`，风险可控、复用现有数据（`dailyListenedMs` + `records`），其中「按小时分布」需新增一个 `hourlyListenedMs` 字段。

---

## 回归测试场景（覆盖本批改动）

| 场景 | 前置 | 步骤 | 预期 |
|---|---|---|---|
| 图片·多级目录 | 作品含子文件夹图片 | 播放 → 查看图片 | 主目录 + 子文件夹图片都出现 |
| 图片·超大目录 | 作品图片 > 200 张 | 查看图片 | 达上限不卡顿不崩溃 |
| 引导·全新安装 | 清数据重装 | 冷启动 | 先功能页 → 条款页 |
| 引导·升级安装 | 旧版数据(seen=true,ver=0) | 升级冷启动 | 完整流程，不直接跳条款 |
| 引导·跳过 | 功能页点跳过 | 观察 | 仍必须过条款页 |
| 条款·同意持久化 | 同意后 | 立即/重启进设置看条款 | 显示「已同意」 |
| DLsite·登录持久 | 真机登录 | 杀进程重进 | 保持登录，无需重验 |
| DLsite·清数据 | 清 Cookie/数据 | 重进 | 需重新登录 |
| 统计·维度切换 | 有播放记录 | 切日/周/月/年/总 | 数据正确 |
| 统计·按小时分布 | 有播放记录 | 查看分布 | 与真实收听一致 |
| 统计·旧数据兼容 | 旧 history.json | 升级打开统计 | 不崩、数据不丢 |

---

## 交付物与验收清单

- [ ] 问题一：子文件夹图片纳入「查看图片」，有深度/数量护栏。
- [ ] 问题二：全新/升级安装首启都走完整引导，不误跳条款。
- [ ] 问题三：设置页可看到「已同意」，重启后保持。
- [ ] 问题四：DLsite 登录态跨退出保持，清数据后需重登。
- [ ] 问题五：统计增加维度切换 + 按小时分布 + 卡片（按第一档）。
- [ ] 各改动通过 `testDebugUnitTest` 与 `assembleDebug`。
- [ ] 更新日志按规范分「新增/修复/调整/注意」追加。
- [ ] 交付时附文件路径清单（`scripts/changes_md.py`）。
- [ ] 版本号按默认预发布通道递增，非授权不发正式版。
