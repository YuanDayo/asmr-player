# AI 操作规则

本文件是 AI 在本仓库动手前必须先读的规则。完整文档：
[docs/AI-ONBOARDING.md](docs/AI-ONBOARDING.md)（上手流程）、
[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)、
[docs/DEVELOPMENT-GUIDE.md](docs/DEVELOPMENT-GUIDE.md)、
[docs/UI-SPEC.md](docs/UI-SPEC.md)（界面令牌、通用组件、手势语义）、
[docs/MOTION-SPEC.md](docs/MOTION-SPEC.md)（动效令牌、具名转场、降级与回归清单）、
[docs/RELEASE-SPEC.md](docs/RELEASE-SPEC.md)（版本与发布）、
[docs/SECURITY.md](docs/SECURITY.md)（签名与密钥）、
[docs/MACHINE-SETUP.md](docs/MACHINE-SETUP.md)（换机器 / 新电脑搭建环境）。

## 版本与发布（硬规则）

1. **默认发预发布，正式版要用户点名**：用户没有明确说出「正式版 / 发正式版 / 不要 beta」时，一律按测试版交付 —— `versionName` 带 `-betaN`、CHANGELOG 段落以 `**[测试版]**：` 开头、Release 建为 Pre-release、**不动 `version.json`**。用户说「改完就推送」「发一版」「上线」都算默认通道，不算正式版授权。
2. 版本号唯一权威源：`app/build.gradle.kts` 顶部的 `appVersionName` / `appVersionCode`。其他地方一律不得出现当前版本号的字符串字面量。
3. 应用内版本显示必须读 `BuildConfig.VERSION_NAME`（经由 `AppInfo.VERSION_NAME`），不得硬编码。
4. Git Tag 去掉开头的 `v` 后必须与 `versionName` 完全一致。
5. Tag 含 `beta` / `alpha` / `rc` 时：Release 必须为 Pre-release，且**不得**修改 `version.json`（正式更新通道）。
6. 正式版 CHANGELOG 只汇总该周期内 beta 验证通过的改动，禁止粘贴 beta 原文；预发布段落以 `**[测试版]**：` 开头。
7. GitHub Release 描述由 `scripts/extract_changelog.py` 从 `CHANGELOG.md` 提取，不再手写。
8. 版本号、CHANGELOG、README、CI 变更分开发提交，不要塞进一个巨大 commit。
9. 发布包必须用固定密钥库签名：`assembleRelease` + `kiite-release.jks`（证书 SHA-256 `0261c2ee…5500`，详见 docs/SECURITY.md）。**禁止发布 debug 变体**，debug keystore 因机而异，换一台机器签出来的包装不到用户手机上。
10. 正式版发布必须同步 `version.json`（`version` + `versionCode`），发布后 purge jsDelivr；预发布不许动它。
11. 应用内「更新日志」页读的是 `app/src/main/assets/CHANGELOG.md`，改完根目录 CHANGELOG 必须跑 `python scripts/sync_changelog.py`（CI 会校验两份是否一致）。

## 界面与交互（硬规则）

1. 间距 / 圆角 / 图标 / 动效只用令牌（`Spacing.*`、`Radius.*`、`IconSize.*`、`Motion.*`），页面层不新增裸 `xx.dp`、`Color(0x…)`、`tween(...)`。转场组合从 `ui/theme/MotionTransitions.kt` 挑具名函数，不要在页面里现拼 `fadeIn + slideIn`。
2. 列表行、空/加载/错误三态、底部弹层、确认框只能复用 `ui/components`（`TrackListItem`、`StateViews`、`SectionHeader`、`AppBottomSheet`、`AppDialog`），不得在页面里另写一份。
3. 同一种手势在任何列表里含义必须一致，见 `docs/UI-SPEC.md` 第 4 节手势语义表。
4. 可恢复的破坏性操作（移出队列）走 Snackbar 撤销；不可恢复的（删播放列表）必须二次确认。**禁止 Toast**。
5. 新增核心组件要带 `@Preview`（浅色 / 深色 / 大字体），图标按钮可点击区域 ≥ 48dp。
6. 裸 `xx.dp` / `Color(0x…)` / `tween(…)` 都有门禁：`python scripts/check_ui_tokens.py` 对照 `.ui-token-baseline.json`，
   CI 的 validate 阶段会拦「新增」。每改造完一个页面就跑 `--write-baseline` 把成果锁进基线，
   基线数量只允许下降，不允许为了过检查而放宽。
   当前基线是 **0**：页面层已经没有裸 dp、硬编码颜色和裸动效 spec，新增就会失败。
7. 「具名常量不算魔法数字」：本页独有的控件尺寸（图标按钮、封面档位）写成
   `private object XxxDim { val iconBtn34 = 34.dp }`，门禁放行 —— 有名字、能 grep、能一处改就够了，
   不必为一次性数值硬造全局令牌。真正的间距/圆角/描边必须用 `Spacing.*` / `Radius.*` / `Stroke.*`。
8. 令牌**跨家族误用**会被门禁判失败：`asmrShape(Spacing.xl)` 这种能编译但语义错的写法，
   之前批量替换时真出过。

## 批量改界面的工具

- `scripts/migrate_dp.py`：按上下文把裸 dp 换成令牌（区分间距/圆角/图标/描边/弹层容器），
  只改 `@Composable` 函数体内，默认干跑；`--apply` 才写盘。
- `scripts/localize_dp.py`：剩下的控件尺寸提成本页 `private object`。
- `scripts/fix_token_imports.py`：补齐令牌 import（按字母序插入）。
- `scripts/png_diff.py`：两张截图的像素差异（自带 PNG 解码，不依赖 Pillow），
  默认跳过顶部状态栏 —— 那里的时钟每次都不一样。
- `scripts/ui_verify.sh`：一次调用内起模拟器、装包、驱动界面、截图拉回。
  必须整段跑：这个环境里跨命令的 adb 连接会掉成 offline。

## 动手前必做

0. 新机器 / 刚接手先跑 `python scripts/doctor.py --net`，缺 JDK、SDK、密钥库或网络会直接列出来；搭建步骤见 `docs/MACHINE-SETUP.md`。
1. 读本文件 + `docs/AI-ONBOARDING.md`。
2. 看 `app/build.gradle.kts` 的 `appVersionName` / `appVersionCode` 当前值。
3. 全局搜一遍硬编码版本号（`grep -rn "版本号\|VERSION_NAME" app/src/main`）。
4. 确认 `version.json` 当前指向哪个版本（它只服务正式更新通道）。
5. 确认 `.github/workflows/` 里的校验与发布流程。
6. **确认本次是哪个通道**：用户明确说了正式版才走正式版，否则预发布。这条决定版本号后缀、CHANGELOG 标记、Release 类型和要不要动 `version.json`。
7. **先输出发现和修改计划，再动代码。**

## 禁止

- 禁止同时改业务代码和发布流程。
- 禁止在用户**没有明确要求**「正式版」的情况下发布正式版：不推不带后缀的 Tag、不把 Release 标成 Latest、不改 `version.json`。拿不准就走 beta 通道问一句。
- 禁止跳过 `scripts/check_version.py` 的验收。

## 本地验收命令

```bash
python scripts/doctor.py --net                      # 环境自检：JDK / SDK / 密钥库 / 网络 / 版本
python scripts/test_semver.py                      # 版本比较逻辑自测
python scripts/check_version.py v<tag> --release   # Tag/versionName/CHANGELOG/更新通道/签名配置
#   ↑ `--release` 是「发版模式」：额外校验签名配置、正式版必须同步 version.json。
#     两个通道都用它；它不会把 beta 当成正式版，也不会替你决定通道。
python scripts/extract_changelog.py v<tag> --check # Release 描述能否提取、标记是否合规
python scripts/sync_changelog.py --check           # 应用内更新日志是否与根目录一致
./gradlew :app:testDebugUnitTest :app:assembleRelease -PrequireReleaseSigning=true --console=plain
python scripts/apk_cert.py app/build/outputs/apk/release/com.kiite.player-<tag>.apk \
  --expect 0261c2eef3800390887b99035228864d50bed0f75e96782471db7d2cc7cd5500
```

构建产物名固定为 `com.kiite.player-<versionName>.apk`，用它核对 APK 里的 `versionName` 与设置页显示是否一致。
