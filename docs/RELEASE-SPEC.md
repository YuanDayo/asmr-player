# asmr-player 版本与发布规范

文档版本：1.2（1.1 基础上补「默认发预发布」通道约定）
适用项目：[YuanDayo/asmr-player](https://github.com/YuanDayo/asmr-player)
用途：约束版本发布、CHANGELOG、Release Notes、应用内版本显示等环节，避免测试版与正式版混用。

给 AI 的强制要求：每次执行版本相关任务前，先读 [AGENTS.md](../AGENTS.md) 与本文件；不得跳过第 6 节验收清单。

---

## 1. 要解决的问题

1. **版本对不上**：README、Releases、CHANGELOG、构建产物里的版本号各自为政。
2. **应用内版本与实际不符**：曾经 `AppInfo.VERSION_NAME` 硬编码为 `2.5.0-beta2`，而实际安装包是 `2.5.1-beta3`——设置页、更新日志标题、检查更新提示全部显示旧版本。
3. **更新日志混用**：正式版与测试版日志没有强制分离，生成正式版说明时容易粘进 beta 原文。

根本原因：版本号没有唯一权威源，且 AI 生成发布内容时缺少硬性流程约束。

---

## 2. 唯一权威源

### 2.1 只在一处手改

唯一权威源：`app/build.gradle.kts` 顶部的两行

```kotlin
val appVersionName = "2.5.1-beta3"
val appVersionCode = 51
```

| 位置 | 规则 |
| --- | --- |
| `app/build.gradle.kts` | 唯一手动修改处 |
| 应用内「关于」/ 设置页 | 必须读 `BuildConfig.VERSION_NAME`（经 `AppInfo.VERSION_NAME`） |
| `README.md` | 不手写当前版本号，改为链接 Releases |
| `CHANGELOG.md` 标题 | 必须与 Git Tag 一致 |
| GitHub Release 标题与描述 | Tag 驱动，描述从 CHANGELOG 提取 |
| `version.json` | 正式更新通道，只能是正式版；测试版发布不得修改 |

### 2.2 Tag 命名

- 测试版：`v2.5.1-beta3`、`v2.5.1-rc1`、`v2.5.1-alpha1`
- 正式版：`v2.5.1`、`v2.5.0`
- Tag 去掉开头的 `v` 后，必须与 `versionName` 完全一致。

### 2.3 强制校验

由 `scripts/check_version.py` 统一实现，两个 workflow 都会调用：

- Tag 含 `beta` / `alpha` / `rc` → `versionName` 必须含相同后缀，Release 必须 Pre-release；
- Tag 为纯正式版 → `versionName` 不得含预发布后缀，Release 必须为 Latest；
- 源码里不得出现当前版本号的字符串字面量；
- 预发布版本的 `version.json.version` 不得等于该版本。

任一条不满足，CI 直接失败，禁止发布。

### 2.4 默认通道是预发布

**默认发预发布，正式版必须由用户明确点名。**

| 用户说法 | 通道 |
| --- | --- |
| 「改完就推送」「发一版」「上线吧」「交付」 | **预发布**（`-betaN`） |
| 「发正式版」「不要 beta」「推正式版」「正式发布」 | 正式版 |

判断依据是用户对**版本性质**的明确表述，不是「发布」这个动作本身。含糊时按预发布走，并在交付说明里写一句「这是 beta，要正式版说一声」。

两条通道的差别：

| 环节 | 预发布 | 正式版（用户点名） |
| --- | --- | --- |
| `appVersionName` | `x.y.z-betaN` | `x.y.z` |
| CHANGELOG 段落 | 首行 `**[测试版]**：` | 只写最终变化，**不出现 beta / 测试版字样** |
| Git Tag | `vx.y.z-betaN` | `vx.y.z` |
| Release | Pre-release | 正式（Latest） |
| `version.json` | **不动** | 必须同步并 purge jsDelivr |
| 发布包 | `assembleRelease` 正式签名 | 同左 |

理由：正式版一旦写进 `version.json`，所有老用户会被推着升级；beta 走 GitHub Release 的预发布通道，想尝鲜的人自己去下，出错也只影响这部分人。签名是同一把，覆盖安装不受影响。

---

## 3. AI 执行前检查

1. 读 `AGENTS.md` 与本文件。
2. **确认本次通道**：用户明确说了「正式版」才走正式版，否则预发布（见 2.4）。这一步先定，后面全是它的推论。
3. 确认 `app/build.gradle.kts` 的 `appVersionName` / `appVersionCode`。
4. 全局搜索硬编码版本号：`grep -rn "2\.\|VERSION_NAME\|当前版本" app/src/main`。
5. 确认应用内「关于」页位置（`app/src/main/java/com/kiite/player/ui/SettingsScreen.kt`）。
6. 确认 `version.json`、`.github/workflows/` 现状。
7. **先输出发现和修改计划，再动代码。**

禁止事项：

- 禁止同时修改业务代码和版本发布流程；
- 禁止把「改版本号、写日志、改 README」塞进同一个巨大 commit；
- 禁止在用户没有明确要求「正式版」时发正式版：不推不带后缀的 Tag、不把 Release 标成 Latest、不改 `version.json`；
- 禁止在测试版发布时更新 `version.json`；
- 禁止正式版 CHANGELOG 出现「测试版 / beta / 预发布」字样（`extract_changelog.py --check` 会拦）。

---

## 4. 改造项与落地位置

| 编号 | 任务 | 落地位置 |
| --- | --- | --- |
| P0-1 | 应用内读 `BuildConfig` | `app/build.gradle.kts`（`buildFeatures.buildConfig = true`）、`AppInfo.kt`、`SettingsScreen.kt` |
| P0-2 | CI 版本一致性校验 | `scripts/check_version.py`、`.github/workflows/validate-version.yml` |
| P1-3 | Release 自动标记 Pre-release | `.github/workflows/release.yml` 的 `release` job |
| P1-4 | 测试版不得更新正式通道 | `check_version.py` 第 5 条规则 |
| P1-5 | CHANGELOG 标题与标记统一 | `CHANGELOG.md` 顶部写作规则 |
| P1-6 | Release Notes 从 CHANGELOG 提取 | `scripts/extract_changelog.py` |
| P2-7 | AI 规则锚点 | `AGENTS.md` |

### 4.1 应用内版本

```kotlin
val VERSION_NAME: String = BuildConfig.VERSION_NAME
```

验收：安装包 `versionName` 与设置页显示一致；全局搜索无当前版本号硬编码。

### 4.2 CI 校验

`.github/workflows/validate-version.yml` 在 Tag 推送与相关文件 PR 时运行：

```bash
python scripts/check_version.py "${GITHUB_REF_NAME}"
```

`release.yml` 的 `build` job 把同样的校验当成第一步跑（在装 JDK 之前），校验不过就不构建、不发布。

另外两条提速约定（2026-11-20 起，改之前 CI 一轮要 7 分半）：

- **单测跑 release 变体**：`testReleaseUnitTest` 与 `assembleRelease` 共用 `compileReleaseKotlin` 的产物。跑 `testDebugUnitTest` 会把整个应用编两遍（debug + release）。本地迭代仍用 debug（`testDebugUnitTest`），CI 用 release。
- **一次 Gradle 调用跑完**：`./gradlew :app:testReleaseUnitTest :app:assembleRelease`，不再分两个 step、不再传 `--no-daemon`。
- **依赖缓存自己管**：GitHub 缓存按 ref 隔离，Tag 读不到别的 Tag 的缓存，只读得到**默认分支**的。缓存 key 只跟 `gradle/libs.versions.toml`、wrapper、`gradle.properties`、`settings.gradle.kts` 走（不含带版本号的 `app/build.gradle.kts`），并配 `restore-keys` 兜底。依赖大改后想立刻刷新缓存，在 main 上手动触发一次 `Release APK` 即可。

想进一步把 Tag 构建压到一分钟内：`~/.gradle/caches` 里含 `build-cache-1`，同一个 commit 第二次构建 27 个任务 FROM-CACHE。做法是**先推 main、等它绿了再打 Tag**（同时推会把两个 workflow 一起叫起来，Tag 那边抢不到缓存）。

验收：

- 推 `v2.5.1-beta3` 且 `versionName=2.5.1-beta3` → 通过；
- 推 `v2.5.1` 但 `versionName=2.5.1-beta3` → 失败；
- 推 `v2.5.1-beta3` 但 `versionName=2.5.1` → 失败。

### 4.3 Release 与预发布标记

Tag 推送后自动创建 Release：`prerelease` 由 Tag 是否含 `-` 决定；描述由 `extract_changelog.py` 从 CHANGELOG 生成。若该 Tag 已有 Release（本地先发过），则跳过，不覆盖。

### 4.4 CHANGELOG 格式（2.5.4 起）

更新日志是**给用户看的**，不是给开发者看的。实现细节、踩坑、复盘写在 `HANDOVER.md`，
不进 CHANGELOG —— 这是 2.5.5 期间反复出现的问题：每个 beta 都写两三屏「为什么以前错」，
用户在应用内「更新日志」里读到的是一屏技术文章，正式版再把 beta 原文汇总一遍就更没人看。

```markdown
## [2.5.5-beta9] - 2026-11-20

**[测试版]**：一句话说清这一版想要解决什么。

### 修复

- **切页时两页同时半透明**：一页退干净，另一页才落位。
- **封面飞行时槽位留一块灰方块**：底板在飞行期间隐藏，只有封面在飞。

## [2.5.5] - 2026-11-21

### 新增

- **段落标题就地编辑**：双击标题即可改，回车确认。

### 修复

- **总集篇播放顺序被打乱**：改按完整路径逐段排序，一卷接一卷播。
```

硬规则（`scripts/extract_changelog.py --check` 会逐条校验，不合规 CI 直接失败）：

| # | 规则 | 机器校验 |
| --- | --- | --- |
| 1 | 段标题 `## [<版本>] - <YYYY-MM-DD>`，版本 == Tag 去 `v` | `check_version.py` |
| 2 | 预发布第一行 `**[测试版]**：<一句话>` | `--check` |
| 3 | **正式版段落里不许出现「测试版 / beta / alpha / rc / 预发布」** | `--check` |
| 4 | 一条改动一行、不超过 60 字、不折行 | `--check` |
| 5 | 一段最多 10 条（「注意」档不计），小标题只允许 新增 / 修复 / 调整 / 注意 | `--check` |
| 6 | 没内容就删掉整档小标题，不许写「- 无」 | `--check` |

- **第 3 条是用户明确要求加进来的**：「正式版不收录测试版更新日志」。正式版只写这一版
  **用户能感知的最终变化**，不转载 beta 段落、不提 beta 版本号、不写「合并 beta1~beta5」。
- 正式版不要求每条都能追溯到某个 beta，也不要求在 beta 期提前写好 —— 发正式版时重新组织。
- 2.5.3 及更早的段落是历史原文，保持不动。

### 4.5 签名（一旦定下就不许变）

覆盖安装靠的是**同一把私钥**。签名一变，老用户只能卸载重装，应用数据全丢。

- 固定密钥库：`kiite-release.jks`（别名 `kiite`），证书 SHA-256 `02:61:C2:EE:F3:80:03:90:88:7B:99:03:52:28:86:4D:50:BE:D0:F7:5E:96:78:24:71:DB:7D:2C:C7:CD:55:00`，与 2.5.0 及之前所有历史版本一致。
- 私钥**不进仓库**：本地放 `_keys/kiite-release.jks` + 仓库根目录 `keystore.properties`（已 gitignore）；CI 走仓库 Secret `SIGNING_STORE_BASE64` / `SIGNING_STORE_PASSWORD` / `SIGNING_KEY_ALIAS` / `SIGNING_KEY_PASSWORD`。
- 发布包一律 `assembleRelease`（`release` 变体，`debuggable=false`），不再发布 debug 变体。
- CI 里有两道闸：Secret 缺失直接失败；构建完用 `apksigner` 校验证书指纹，与上面的常量不符直接失败。

事故复盘（2.5.1-beta4 起）：CI 原先跑 `assembleDebug`，debug keystore 由 runner 每次随机生成，于是每个 CI 包都是新证书——2.5.1-beta4 = `036427eb…`、2.5.1 首发 = 另一个 key、2.5.2-beta1 = `9ab15fb9…`，互相都覆盖不了。现已改为固定密钥库签名。

---

## 5. AI 启动指令模板

```text
你是 asmr-player 项目的发布流程维护助手。

开始前必须：
1. 阅读仓库根目录 AGENTS.md。
2. 阅读 docs/RELEASE-SPEC.md（本文件）。
3. 检查 app/build.gradle.kts 中的 appVersionName / appVersionCode。
4. 全局搜索硬编码版本号和应用内「关于」页面。
5. 确认 version.json 当前指向。
6. 先输出发现和修改计划，不要直接改代码。

硬规则：
- 默认发预发布（-betaN / Pre-release / 不动 version.json）；只有用户明确说「正式版」才走正式通道。
- 版本号只改 app/build.gradle.kts。
- 应用内版本显示必须读 BuildConfig.VERSION_NAME。
- Tag 去掉 v 后必须与 versionName 完全一致。
- beta/alpha/rc Tag 必须 Pre-release，且不得更新 version.json。
- 正式版 CHANGELOG 不得出现「测试版 / beta / 预发布」字样，也不得转载 beta 段落。
- CHANGELOG 一条一行、不超过 60 字、一段最多 10 条（规则与机器校验见第 4.4 节）。
- 版本号、CHANGELOG、README、CI 变更分开提交。
- 不改业务代码；不自行发布正式版；不擅自推正式 Tag。

输出要求：
- 列出将修改的文件和原因。
- 给出验收命令。
- 修改后逐条运行验收清单并报告结果。
```

---

## 6. 最终验收清单

- [ ] 本次通道已确认：用户没点「正式版」就是预发布
- [ ] `app/build.gradle.kts` 是唯一手动改版本号的位置
- [ ] 应用内「关于」页读取 `BuildConfig.VERSION_NAME`
- [ ] 全局搜索无当前版本号硬编码
- [ ] CI 校验 Tag 与 `versionName` 完全一致
- [ ] 正式版 Tag 的 `versionName` 不含 `beta`/`alpha`/`rc`
- [ ] 预发布 Tag 的 `versionName` 含对应后缀
- [ ] 预发布 Release 标记为 Pre-release
- [ ] 测试版发布不更新 `version.json`
- [ ] CHANGELOG 标题格式统一、预发布段落带标记
- [ ] Release Notes 从 CHANGELOG 自动提取
- [ ] 正式版日志里没有「测试版 / beta / 预发布」字样，也没转载 beta 段落
- [ ] 更新日志一条一行、每条 ≤ 60 字、一段 ≤ 10 条（`extract_changelog.py --check` 通过）
- [ ] `AGENTS.md` 已创建并生效
- [ ] 版本号、CHANGELOG、README、CI 变更分开提交
- [ ] release 包签名指纹为 `0261c2ee…5500`（`scripts/apk_cert.py --expect` 验过）
- [ ] APK 内 `versionCode` 与 `appVersionCode` 一致

## 7. version.json 字段

应用内更新的正式通道，`main` 分支根目录。CI 与 `check_version.py` 都会读它。

```json
{
  "version": "2.5.1",
  "versionCode": 53,
  "notes": "一句话更新说明（弹窗里显示）",
  "url": "https://github.com/YuanDayo/asmr-player/releases",
  "apkUrl": "https://github.com/.../releases/download/v2.5.1/com.kiite.player-2.5.1.apk",
  "sha256": "APK 文件摘要（可选，用来提示用户校验下载）"
}
```

- `version` / `versionCode` 必须指向**已发布的正式版**；预发布不许动这个文件。
- `apkUrl` 必须来自真实 Release 资产（不允许拼接猜测），缺省时客户端退回 `url` 页面。
- `sha256` 是**文件摘要**，不是签名证书指纹；签名校验仍以 `docs/SECURITY.md` 的证书为准。
- 客户端比较顺序：有 `versionCode` 就先比它 → 否则 SemVer 比较；正式版用户不接收预发布。
