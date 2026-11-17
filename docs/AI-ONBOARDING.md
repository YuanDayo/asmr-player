# AI 上手手册

任何 AI / 新协作者接手这个仓库，先按本文走一遍。**本文不写死版本号**，读仓库实时内容为准。

## 1. 必读顺序（15 分钟内能看完）

| 顺序 | 文件 | 看什么 |
|---|---|---|
| 1 | `AGENTS.md` | 硬规则、禁止事项、本地验收命令 |
| 2 | `docs/AI-ONBOARDING.md`（本文） | 上手流程 |
| 3 | `docs/ARCHITECTURE.md` | 代码分层与关键链路 |
| 4 | `docs/DEVELOPMENT-GUIDE.md` | 代码风格、协程、测试规范 |
| 5 | `docs/RELEASE-SPEC.md` | 版本与发布规则 |
| 6 | `docs/SECURITY.md` | 签名密钥、Secret、门禁 |
| 7 | `HANDOVER.md` | 当前进度、已知问题、下一步 |

## 2. 每次动手前必须重新读取的实时状态

文档会过期，**代码与已发布的 Release 才是事实**：

```bash
# 当前开发版本（唯一权威源）
grep -n 'appVersionName\|appVersionCode' app/build.gradle.kts
# 正式更新通道
cat version.json
# 最近提交
git log --oneline -10
# 最近 Tag 与 Release（有 gh 时）
gh release list --limit 10 ; git tag --sort=-creatordate | head -10
```

版本号、`version.json`、Tag、Release 四者关系见 `docs/RELEASE-SPEC.md`；对不上时先改文档，别改代码硬凑。

## 3. 首个回合的输出要求

动手改代码之前，先给出：

1. 当前版本 / 正式通道 / 最新 Release 的实际情况；
2. 你读到的相关模块与文件路径；
3. 你的修改计划（分几步、每步改哪些文件、怎么验证）；
4. 明确的风险点（签名、更新通道、数据迁移、兼容性）。

得到确认后再动代码。

## 4. 硬边界（违反会伤到已安装用户）

- **不许改签名密钥**：证书指纹变了，所有人只能卸载重装。见 `docs/SECURITY.md`。
- **不许硬编码版本号**：一律读 `AppInfo.VERSION_NAME` / `AppInfo.VERSION_CODE`。
- **不许在预发布时改 `version.json`**：那是正式更新通道，改了等于把测试包推给普通用户。
- **不许发布 debug 变体**：debug keystore 每台机器都不一样。
- **不许覆盖已有 Release**：同名 Release 存在就跳过。
- 不要把私钥、口令、token 写进仓库或日志。

## 5. 工作方式

- 复杂改造先拆阶段（安全 → 更新机制 → 发布流程 → 文档），一个阶段一次提交，别一个大 commit 混所有东西。
- 改业务代码与改发布流程**分开提交**。
- 每个阶段结束都要跑：单测 → 版本校验 → 构建；发布相关还要验签名指纹。
- 发现问题先读代码确认，不要凭印象重写。文档与代码冲突时以代码 + 已发布产物为准，并顺手修文档。

## 6. 常用命令

```bash
# 单元测试 + 组包（debug，本地自测）
./gradlew :app:testDebugUnitTest :app:assembleDebug --offline --console=plain

# 发布用（release 变体，必须能拿到 keystore.properties 或 SIGNING_* 环境变量）
./gradlew :app:assembleRelease -PrequireReleaseSigning=true --console=plain

# 版本/日志/签名配置校验
python scripts/check_version.py v<tag> --release
python scripts/extract_changelog.py v<tag> --check
python scripts/test_semver.py

# 验签名指纹（必须等于 docs/SECURITY.md 里那个值）
$ANDROID_HOME/build-tools/<ver>/apksigner verify --print-certs app/build/outputs/apk/release/*.apk
```

## 7. 常见坑

- 构建环境变量（JAVA_HOME / ANDROID_HOME / GRADLE_USER_HOME）不设好，Gradle 会去找不存在的 SDK。
- `assembleRelease` 触发 `lintVitalRelease`，离线且本地没缓存 lint 依赖时会失败；联网跑一次即可。
- Android 模拟器连不上 `api.asmr.one` / DLsite（IPv6 路由问题），**在线链路只能真机验证**，别在模拟器上断言"网络没问题"。
- Release 资产的 SHA-256 只是文件摘要，**不能**当签名指纹用。
