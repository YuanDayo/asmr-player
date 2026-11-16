# AI 操作规则

本文件是 AI 在本仓库动手前必须先读的规则。配套的完整说明见 [docs/RELEASE-SPEC.md](docs/RELEASE-SPEC.md)。

## 版本与发布（硬规则）

1. 版本号唯一权威源：`app/build.gradle.kts` 顶部的 `appVersionName` / `appVersionCode`。其他地方一律不得出现当前版本号的字符串字面量。
2. 应用内版本显示必须读 `BuildConfig.VERSION_NAME`（经由 `AppInfo.VERSION_NAME`），不得硬编码。
3. Git Tag 去掉开头的 `v` 后必须与 `versionName` 完全一致。
4. Tag 含 `beta` / `alpha` / `rc` 时：Release 必须为 Pre-release，且**不得**修改 `version.json`（正式更新通道）。
5. 正式版 CHANGELOG 只汇总该周期内 beta 验证通过的改动，禁止粘贴 beta 原文；预发布段落以 `**[测试版]**：` 开头。
6. GitHub Release 描述由 `scripts/extract_changelog.py` 从 `CHANGELOG.md` 提取，不再手写。
7. 版本号、CHANGELOG、README、CI 变更分开发提交，不要塞进一个巨大 commit。

## 动手前必做

1. 读本文件 + `docs/RELEASE-SPEC.md`。
2. 看 `app/build.gradle.kts` 的 `appVersionName` / `appVersionCode` 当前值。
3. 全局搜一遍硬编码版本号（`grep -rn "版本号\|VERSION_NAME" app/src/main`）。
4. 确认 `version.json` 当前指向哪个版本（它只服务正式更新通道）。
5. 确认 `.github/workflows/` 里的校验与发布流程。
6. **先输出发现和修改计划，再动代码。**

## 禁止

- 禁止同时改业务代码和发布流程。
- 禁止 AI 自行决定发正式版、自行推正式 Tag、自行把预发布标记为 Latest。
- 禁止在测试版发布时更新 `version.json`。
- 禁止跳过 `scripts/check_version.py` 的验收。

## 本地验收命令

```bash
python scripts/check_version.py v<tag>            # Tag 与 versionName、CHANGELOG、更新通道一致性
python scripts/extract_changelog.py v<tag> --check # Release 描述能否提取、标记是否合规
./gradlew :app:testDebugUnitTest :app:assembleDebug --offline --console=plain
unzip -p app/build/outputs/apk/debug/com.kiite.player-<tag>.apk AndroidManifest.xml >/dev/null 2>&1 || true
```

构建产物名固定为 `com.kiite.player-<versionName>.apk`，用它核对 APK 里的 `versionName` 与设置页显示是否一致。
