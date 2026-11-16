# asmr-player 版本与发布规范

文档版本：1.1（在 1.0 基础上按仓库实际情况落地）
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

---

## 3. AI 执行前检查

1. 读 `AGENTS.md` 与本文件。
2. 确认 `app/build.gradle.kts` 的 `appVersionName` / `appVersionCode`。
3. 全局搜索硬编码版本号：`grep -rn "2\.\|VERSION_NAME\|当前版本" app/src/main`。
4. 确认应用内「关于」页位置（`app/src/main/java/com/kiite/player/ui/SettingsScreen.kt`）。
5. 确认 `version.json`、`.github/workflows/` 现状。
6. **先输出发现和修改计划，再动代码。**

禁止事项：

- 禁止同时修改业务代码和版本发布流程；
- 禁止把「改版本号、写日志、改 README」塞进同一个巨大 commit；
- 禁止 AI 自行决定正式版发布、自行推正式 Tag；
- 禁止在测试版发布时更新 `version.json`；
- 禁止正式版 CHANGELOG 直接粘贴 beta 原文。

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

`release.yml` 里还有一个同名 `validate` job 作为前置，`build` 依赖它——校验不过就不构建、不发布。

验收：

- 推 `v2.5.1-beta3` 且 `versionName=2.5.1-beta3` → 通过；
- 推 `v2.5.1` 但 `versionName=2.5.1-beta3` → 失败；
- 推 `v2.5.1-beta3` 但 `versionName=2.5.1` → 失败。

### 4.3 Release 与预发布标记

Tag 推送后自动创建 Release：`prerelease` 由 Tag 是否含 `-` 决定；描述由 `extract_changelog.py` 从 CHANGELOG 生成。若该 Tag 已有 Release（本地先发过），则跳过，不覆盖。

### 4.4 CHANGELOG 格式

```markdown
## [2.5.1-beta3] - 2026-11-15

**[测试版]**：本段说明…

## [2.5.1] - 2026-11-16

正式版说明（只汇总 beta 验证通过的改动）
```

- 预发布段落第一行必须带 `**[测试版]**`；
- 正式版段落不带任何预发布标记；
- 正式版开头建议写「合并 2.5.1-beta1 / beta2 / beta3 中验证通过的改动」。

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
- 版本号只改 app/build.gradle.kts。
- 应用内版本显示必须读 BuildConfig.VERSION_NAME。
- Tag 去掉 v 后必须与 versionName 完全一致。
- beta/alpha/rc Tag 必须 Pre-release，且不得更新 version.json。
- 正式版 CHANGELOG 不得粘贴 beta 原文。
- 版本号、CHANGELOG、README、CI 变更分开提交。
- 不改业务代码；不自行发布正式版；不推正式 Tag。

输出要求：
- 列出将修改的文件和原因。
- 给出验收命令。
- 修改后逐条运行验收清单并报告结果。
```

---

## 6. 最终验收清单

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
- [ ] 正式版日志未粘贴 beta 原文
- [ ] `AGENTS.md` 已创建并生效
- [ ] 版本号、CHANGELOG、README、CI 变更分开提交
