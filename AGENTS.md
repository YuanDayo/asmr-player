# AI 操作规则

本文件是 AI 在本仓库动手前必须先读的规则。完整文档：
[docs/AI-ONBOARDING.md](docs/AI-ONBOARDING.md)（上手流程）、
[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)、
[docs/DEVELOPMENT-GUIDE.md](docs/DEVELOPMENT-GUIDE.md)、
[docs/UI-SPEC.md](docs/UI-SPEC.md)（界面令牌、通用组件、手势语义）、
[docs/RELEASE-SPEC.md](docs/RELEASE-SPEC.md)（版本与发布）、
[docs/SECURITY.md](docs/SECURITY.md)（签名与密钥）、
[docs/MACHINE-SETUP.md](docs/MACHINE-SETUP.md)（换机器 / 新电脑搭建环境）。

## 版本与发布（硬规则）

1. 版本号唯一权威源：`app/build.gradle.kts` 顶部的 `appVersionName` / `appVersionCode`。其他地方一律不得出现当前版本号的字符串字面量。
2. 应用内版本显示必须读 `BuildConfig.VERSION_NAME`（经由 `AppInfo.VERSION_NAME`），不得硬编码。
3. Git Tag 去掉开头的 `v` 后必须与 `versionName` 完全一致。
4. Tag 含 `beta` / `alpha` / `rc` 时：Release 必须为 Pre-release，且**不得**修改 `version.json`（正式更新通道）。
5. 正式版 CHANGELOG 只汇总该周期内 beta 验证通过的改动，禁止粘贴 beta 原文；预发布段落以 `**[测试版]**：` 开头。
6. GitHub Release 描述由 `scripts/extract_changelog.py` 从 `CHANGELOG.md` 提取，不再手写。
7. 版本号、CHANGELOG、README、CI 变更分开发提交，不要塞进一个巨大 commit。
8. 发布包必须用固定密钥库签名：`assembleRelease` + `kiite-release.jks`（证书 SHA-256 `0261c2ee…5500`，详见 docs/SECURITY.md）。**禁止发布 debug 变体**，debug keystore 因机而异，换一台机器签出来的包装不到用户手机上。
9. 正式版发布必须同步 `version.json`（`version` + `versionCode`），发布后 purge jsDelivr；预发布不许动它。
10. 应用内「更新日志」页读的是 `app/src/main/assets/CHANGELOG.md`，改完根目录 CHANGELOG 必须跑 `python scripts/sync_changelog.py`（CI 会校验两份是否一致）。

## 界面与交互（硬规则）

1. 间距 / 圆角 / 图标 / 动效只用 `ui/theme/Dimens.kt` 的令牌（`Spacing.*`、`Radius.*`、`IconSize.*`、`Motion.*`），页面层不新增裸 `xx.dp` 与 `Color(0x…)`。
2. 列表行、空/加载/错误三态、底部弹层、确认框只能复用 `ui/components`（`TrackListItem`、`StateViews`、`SectionHeader`、`AppBottomSheet`、`AppDialog`），不得在页面里另写一份。
3. 同一种手势在任何列表里含义必须一致，见 `docs/UI-SPEC.md` 第 4 节手势语义表。
4. 可恢复的破坏性操作（移出队列）走 Snackbar 撤销；不可恢复的（删播放列表）必须二次确认。**禁止 Toast**。
5. 新增核心组件要带 `@Preview`（浅色 / 深色 / 大字体），图标按钮可点击区域 ≥ 48dp。
6. 裸 `xx.dp` / `Color(0x…)` 有门禁：`python scripts/check_ui_tokens.py` 对照 `.ui-token-baseline.json`，
   CI 的 validate 阶段会拦「新增」。每改造完一个页面就跑 `--write-baseline` 把成果锁进基线，
   基线数量只允许下降，不允许为了过检查而放宽。

## 动手前必做

0. 新机器 / 刚接手先跑 `python scripts/doctor.py --net`，缺 JDK、SDK、密钥库或网络会直接列出来；搭建步骤见 `docs/MACHINE-SETUP.md`。
1. 读本文件 + `docs/AI-ONBOARDING.md`。
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
python scripts/doctor.py --net                      # 环境自检：JDK / SDK / 密钥库 / 网络 / 版本
python scripts/test_semver.py                      # 版本比较逻辑自测
python scripts/check_version.py v<tag> --release   # Tag/versionName/CHANGELOG/更新通道/签名配置
python scripts/extract_changelog.py v<tag> --check # Release 描述能否提取、标记是否合规
python scripts/sync_changelog.py --check           # 应用内更新日志是否与根目录一致
./gradlew :app:testDebugUnitTest :app:assembleRelease -PrequireReleaseSigning=true --console=plain
python scripts/apk_cert.py app/build/outputs/apk/release/com.kiite.player-<tag>.apk \
  --expect 0261c2eef3800390887b99035228864d50bed0f75e96782471db7d2cc7cd5500
```

构建产物名固定为 `com.kiite.player-<versionName>.apk`，用它核对 APK 里的 `versionName` 与设置页显示是否一致。
