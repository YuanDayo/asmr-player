# 开发约定

## 1. 环境

| 项 | 值 |
|---|---|
| JDK | 17 |
| Android SDK | compileSdk / targetSdk 35，minSdk 26 |
| Kotlin | 见 `gradle/libs.versions.toml` |
| 构建 | `./gradlew`（不要用 IDE 的隐藏缓存目录） |

环境变量（缺一不可，否则 Gradle 找不到 SDK 或重复下载）：

```bash
export JAVA_HOME=<jdk17>
export ANDROID_HOME=<sdk>
export GRADLE_USER_HOME=<gradle 缓存目录>
```

## 2. 代码风格

- Kotlin 官方风格（4 空格缩进，行宽约 110），不加无意义的空行。
- **注释写"为什么"，不写"是什么"**。踩过的坑、反直觉的地方必须写清，例如
  `// 这里必须先 rename 再删，否则 Android 11 上会丢文件`。
- 公开类型/复杂函数写 KDoc，一两句说清职责与调用场景。
- 类名不缩写（`AsmrOneController` 而不是 `AoC`）；布尔量读起来是句子（`hasReleaseSigning`）。
- 不用 `!!`；可空值一律显式处理。捕获异常要给用户能看懂的原因，不许 `catch (_: Exception) {}` 空吞。
- 中文标点用于界面文案与注释；代码标识符一律英文。

## 3. Compose

- Screen 写无状态 Composable（参数进、回调出），状态从 `MainViewModel` 的 `StateFlow` 收集。
- 列表项加 `key`，长列表用 `LazyColumn`；不要在 Composable 里做 IO 或起长任务。
- 尺寸/圆角/间距复用 `AppRoot.kt` 里的既有常量与 `asmrShape()`，别各写一套。
- 新增页面先在 `AppRoot` 的导航图里挂上，再写 Screen。

## 4. 协程

- IO 用 `withContext(Dispatchers.IO)`，或 Controller 里注入的作用域；不要在主线程读文件。
- 长任务挂在应用级作用域（下载/播放），界面销毁不能中断。
- 取消要向上传播：`ensureActive()` / `isActive` 检查，`CancellationException` 不许吞。
- 不用 `GlobalScope`。

## 5. 状态与错误

- 状态用 `StateFlow<T>`，UI 只读；改动集中在一处，避免多个入口写同一份状态。
- 用户可见的失败统一 `vm.say("...")`，包含"哪个操作失败 + 为什么"；技术细节进 `AppLog`。
- `AppLog` 只记关键事件（下载开始/结束、扫描结果、更新检查），不要刷屏日志。

## 6. 单元测试

- 纯逻辑/状态机必须能在 JVM 上测：不 new `Context`，把 Android 依赖通过参数传进去。
- 网络测试用仓库里自带的 `TinyServer`（`ServerSocket`），不要引 MockWebServer。
- 涉及时间的逻辑注入时钟或用 `TestScope` 的虚拟时间，别 `Thread.sleep`。
- 命名说明行为：`betaUserGetsStable()`、`stableUserIgnoresBeta()`。
- 提交前跑：`./gradlew :app:testDebugUnitTest`。

## 7. 兼容性

- minSdk 26：`java.time`、`Files` 可用；`MANAGE_EXTERNAL_STORAGE` 在 30+ 需要引导授权。
- 存储访问优先 SAF（`OpenDocumentTree`），并用 `util/SafPath` 还原成真实路径。
- 前台服务类型按 Android 14 要求声明（`dataSync` / `mediaPlayback`），改服务要同步改 manifest。

## 8. 提交

- 一个提交只做一件事：`fix(...)` / `feat(...)` / `docs(...)` / `chore(...)`。
- 提交信息写清"改了什么 + 为什么"，别只写"update"。
- 业务代码与发布流程（CI、版本号、CHANGELOG）分开提交。
- 推 CI 工作流文件需要带 `workflow` scope 的凭据，否则会被 GitHub 拒。
