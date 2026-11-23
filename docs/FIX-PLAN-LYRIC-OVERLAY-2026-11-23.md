# 桌面歌词（台本悬浮窗）优化计划书

> 生成时间：2026-11-23
> 目标：将「桌面歌词」从当前的基础可用态，优化到「状态可持久、外观随主题、内容有上下文」的可长期使用态。
> 范围：仅桌面歌词悬浮窗（`playback/LyricOverlay.kt`）及其宿主接线，不涉及播放器内核、台本解析、DLsite 等其它模块。
> 约束：最小改动、可独立交付、不破坏现有 editorial 皮肤与「台本悬浮窗」既有行为。

---

## 一、现状与代码地图

桌面歌词实现在 `app/src/main/java/com/kiite/player/playback/LyricOverlay.kt`（`LyricOverlayController`，676 行），是**手写 `TextView` 挂在 `WindowManager`（`TYPE_APPLICATION_OVERLAY`）上的悬浮窗**，不是系统歌词、也不是 Compose 组件。

已具备能力：
- 显示「当前句 + 下一句」两行文字，纯文本。
- 拖动（`installDragTouch`）、右下角 `◢` 手柄缩放（`installResizeTouch`）。
- 收成悬浮球 / 展开（`setCollapsed` → `buildBall`/`switchMode`）。
- A± 调字号（`scale`，0.7–1.8，`STEP=0.1`）。
- 6 秒自动收起控制条（`CONTROLS_TIMEOUT_MS=6000`）。
- 字号随窗口宽度联动（`sizeScale()` + `effectiveScale()`），文字自动截断不切半行（`applyTextMetrics`）。

宿主接线（`MainViewModel.kt`）：
- `lyricOverlay` 实例：`MainViewModel.kt:195`。
- 启动恢复：`MainViewModel.kt:1870-1872` 调 `setSize` / `setScale` / `setCollapsed`。
- 持久化回调：`MainViewModel.kt:1883-1890` 挂 `onScaleChange` / `onSizeChange` / `onCollapsedChange`。

持久化字段（`SettingsStore.kt`，已存在并接通）：
- `lyricScale`（默认 1.0f）、`lyricWidth`（240f）、`lyricHeight`（96f）、`lyricCollapsed`（false）。

---

## 二、问题清单（按严重度排序）

### P0-1 · 悬浮窗位置不持久化（最高优先）
- **现象**：用户把浮窗拖到习惯的位置，退出应用/重启后，浮窗又跳回默认位置 `(24, 160)dp`。
- **根因**：`LyricOverlay.kt:81-82` `posX/posY` 是运行时变量，`init`（`:101-102`）写死 `dp(24)/dp(160)`；`hide()` 不清它们但 `params=null` 后下次 `show()` 用当前内存值；**进程重启后 `init` 又重置回默认**。没有 `onPositionChange` 回调，也没有从设置恢复位置的代码。
- **影响面**：`SettingsStore` 缺 `lyricPosX/lyricPosY` 字段；`MainViewModel` 缺对应接线；`LyricOverlayController` 缺位置回调与恢复入口。

### P0-2 · 位置无屏幕边界钳制
- **现象**：横竖屏切换、分辨率变化、外接屏后，已保存的位置可能落到可视区外，浮窗「消失」。
- **根因**：`resize` 有 `maxW/maxH` 钳制（`:557-560`），但**位置（posX/posY）在拖动、恢复时都没有 clamp 到屏幕内**。

### P1-1 · 外观硬编码、不随主题
- **现象**：无论 App 深色/浅色、经典/editorial 皮肤，浮窗都是「深灰底白字」（iOS 风），浅色主题下显得突兀，夜间无降亮度。
- **根因**：`LyricOverlay.kt` 中背景 `0xD91C1C1E`（`:425`、`:445`）、描边 `0x44FFFFFF`（`:446`）、文字 `0xFFFFFFFF`（`:452`）全部硬编码，未读 `themeMode` / `skinStyle`。

### P1-2 · 内容单薄、无时间上下文
- **现象**：只有「当前句 + 下一句」，无法感知进度（还剩多久）、当前章节、以及台本为空时的可读提示。
- **根因**：`update()` 只用 `ParsedScript` 的当前/下一行；`timedLines` 里的 `startMs`（时间轴）没有用于显示剩余时间或细进度条；无章节名、无空台本降级文案。

### P2-1 · 缺「回 App」入口与双击跳转
- **现象**：浮窗悬浮时用户想回到播放页要手动切后台，无快捷入口。
- **根因**：仅 `onRequestClose` 回调，无「回 App」动作（`Intent` 拉起播放页 / 双击文本）。

### P2-2 · 刘海屏 / 锁屏避让缺失
- **现象**：部分机型浮窗可拖进刘海/状态栏区域，视觉被遮挡。
- **根因**：未读 `WindowInsets` 的 `displayCutout`，未做 safe area 偏移。

---

## 三、优化方向与实施方案

> 优先级：P0（必做，体验核心痛点）→ P1（价值增量）→ P2（体验打磨）。每档可独立交付、独立验收。

---

### P0 · 状态持久化 + 边界钳制（收益最大、改动最小）

**目标**：浮窗位置、字号、尺寸、收起态在重启后完全恢复；位置永远落在可视区。

**涉及模块**
| 模块 | 文件 | 改动 |
|---|---|---|
| 持久化字段 | `data/SettingsStore.kt` | 新增 `lyricPosX`/`lyricPosY`（Int，默认 -1 表示「未设置，用默认」） |
| 回调暴露 | `playback/LyricOverlay.kt` | 新增 `onPositionChange: ((Int, Int) -> Unit)?`；拖动/球拖动结束时 `invoke` |
| 恢复入口 | `playback/LyricOverlay.kt` | 新增 `setPosition(x, y)`：clamp 到屏幕后写 `posX/posY`，`show()` 前调用 |
| 宿主接线 | `ui/MainViewModel.kt` | 启动 `setPosition`；挂 `onPositionChange` 写盘 |
| 边界钳制 | `playback/LyricOverlay.kt` | 新增 `clampPosition(x, y)`：`x ∈ [0, screenW - widthPx]`，`y ∈ [0, screenH - windowHeight()]` |

**实施步骤**
1. `SettingsStore`：`AppSettings` 增加 `lyricPosX: Int = -1`、`lyricPosY: Int = -1`；`fromMap` 读盘、`overwriteAll` 写盘各补两行；新增 `Keys.LYRIC_POS_X`/`LYRIC_POS_Y`。
2. `LyricOverlayController`：
   - 新增 `onPositionChange` 回调字段。
   - `installDragTouch` 与 `buildBall` 的 `ACTION_UP`（拖动结束）处 `onPositionChange?.invoke(posX, posY)`。
   - 新增 `setPosition(xDp, yDp)`：转 px → `clampPosition` → 写 `posX/posY`；若已 `attached` 则 `updateViewLayout`。
   - 新增 `clampPosition(x, y)`（见上）复用 `screenW/screenH`。
3. `MainViewModel`：
   - 启动块（`:1870` 附近）加 `lyricOverlay.setPosition(s.lyricPosX, s.lyricPosY)`（`-1` 时跳过，用默认）。
   - 挂 `lyricOverlay.onPositionChange = { x, y -> … 写盘 … }`，与现有 `onScaleChange` 等并列。
4. 确认 `show()` 里 `params.x/y = posX/posY` 用的是恢复后的值（当前已如此，只需确保 `setPosition` 在 `show()` 前被调）。

**验证方法**
- 单测：`setPosition` 传入越界值（负数、超大值）→ 断言 clamp 后落在 `[0, screenW-width]`。
- 手测：拖浮窗到左上角 → 退出 App 进程 → 重开 → 浮窗回到左上角（非默认 24,160）。

**验收标准**
- 位置/字号/尺寸/收起态四者重启后全部恢复。
- 任意越界坐标均被拉回可视区，浮窗永不「消失」。

---

### P1 · 内容与时间轴 + 主题外观

**目标**：给浮窗加「剩余时间 / 章节名 / 空台本提示」；外观跟随 App 深浅色与皮肤。

**涉及模块**
| 模块 | 文件 | 改动 |
|---|---|---|
| 时间轴显示 | `playback/LyricOverlay.kt` | `update()` 用 `timedLines.startMs` 算剩余时间，显示一行细进度/剩余秒 |
| 章节名 | `playback/LyricOverlay.kt` | 若 `ParsedScript` 有章节，当前行上方加小标 |
| 空台本降级 | `playback/LyricOverlay.kt` | 无时间轴时显示「本曲无时间轴台本」+ 曲名 |
| 主题色 | `playback/LyricOverlay.kt` | 背景/描边/文字改为读 `themeMode`+`skinStyle`（深浅两套） |
| 透明度/圆角 | `playback/LyricOverlay.kt` | 背景透明度、圆角从设置读（可选，P2） |

**实施步骤**
1. 先做**时间轴**：`update()` 已拿到 `lastPos` 与当前行 `startMs`，用下一行 `startMs - lastPos` 算「本句剩余」，显示为右侧小字或底部 1dp 细进度条（复用现有 `lastPos`，无需新增存储）。
2. **章节名**：确认 `ParsedScript` 是否携带章节字段，若有则渲染小标；若无，此条降级为「曲名」。
3. **空台本**：`update()` 里当无 `timedLines` 时，`textCurrent` 显示曲名、`textNext` 显示提示文案（文案进 `strings.xml`）。
4. **主题**：把硬编码色改为函数 `overlayBg()/overlayText()/overlayStroke()`，输入 `themeMode`（深/浅）与 `skinStyle`（classic/editorial）；浅色主题用「浅底深字」，深色主题保持「深底浅字」；editorial 保持现状避免回归。

**验证方法**
- 手测：播放带时间轴台本 → 浮窗显示剩余时间随播放递减；切到无台本曲目 → 显示降级文案。
- 手测：设置里切浅色/深色 → 浮窗配色切换；经典皮肤下配色正常。

**验收标准**
- 剩余时间/进度条正确、与播放位置同步。
- 无台本时不再「只显示一行空」，有可读提示。
- 深浅色切换后浮窗配色可读，editorial 皮肤无回归。

---

### P2 · 交互与避让（体验打磨）

**目标**：双击回 App、刘海屏/锁屏避让、拖动吸附边缘。

**涉及模块**
| 模块 | 文件 | 改动 |
|---|---|---|
| 回 App | `playback/LyricOverlay.kt` | 双击文本 → `onRequestOpenApp` 回调 / 直接 `Intent` 拉起播放页 |
| 避让 | `playback/LyricOverlay.kt` | `show()` 读 `WindowInsets` 的 `displayCutout`，`y` 偏移 safe top |
| 边缘吸附 | `playback/LyricOverlay.kt` | 拖动 `ACTION_UP` 时若距左/右边缘 < 阈值，吸附贴边 |

**实施步骤**
1. 双击：给 `texts` 加双击手势，触发宿主回调拉起播放页（`MainActivity` 已有 `intent`）。
2. 避让：`show()` 里用 `params.insets` 或读 `displayCutout.safeInsetTop`，`clampPosition` 的 `y` 下限改为 safe top。
3. 吸附：`installDragTouch` 松手时按屏幕中线判断贴左/右。

**验证方法**
- 手测：双击文本回播放页；刘海机型拖到顶部不越界；拖近边缘自动吸附。

**验收标准**
- 回 App 入口可用；刘海/锁屏下浮窗不被遮挡；吸附体验自然。

---

## 四、回归测试场景

1. **首次开启**：全新安装 → 播放 → 开桌面歌词 → 浮窗出现在默认位置、默认字号、展开态。
2. **位置持久**：拖浮窗到左上角 → 杀掉进程 → 重开 → 位置、字号、尺寸、收起态全部恢复。
3. **收起态持久**：收成悬浮球 → 重启 → 仍为球，且球位置也恢复。
4. **横竖屏切换**：保存的位置在竖屏可见 → 切横屏 → 浮窗仍在可视区（被 clamp）。
5. **越界恢复**：人为把持久化坐标写成超大值 → 启动后浮窗被拉回可视区，不消失。
6. **深/浅色主题**：切主题 → 浮窗配色切换且文字可读；editorial 皮肤下无回归。
7. **无台本曲目**：播放无时间轴台本的曲 → 显示曲名 + 提示文案，不空白。
8. **带时间轴曲目**：剩余时间随播放递减，切句时当前句高亮、下一句正确。
9. **缩放 + 字号联动**：拖动 `◢` 缩放窗口 → 字号随宽度联动，文字不溢出、不切半行。
10. **双击回 App**：浮窗悬浮时双击文本 → 回到播放页。
11. **控制条自动收起**：展开控制条 6 秒后自动收起，与持久化态不冲突。

---

## 五、交付物与验收清单

- [ ] `SettingsStore.kt` 新增 `lyricPosX/lyricPosY` 字段（读盘/写盘/Key 三处齐全）。
- [ ] `LyricOverlayController` 新增 `onPositionChange` 回调 + `setPosition` + `clampPosition`。
- [ ] `MainViewModel` 启动恢复位置 + 挂位置回调写盘。
- [ ] 时间轴剩余时间/进度条显示。
- [ ] 无台本降级文案（`strings.xml`）。
- [ ] 主题色函数化，深浅色/皮肤适配。
- [ ] （P2）双击回 App、刘海避让、边缘吸附。
- [ ] 单测：`setPosition`/`clampPosition` 越界钳制。
- [ ] 回归场景 1–11 全通过。
- [ ] 文件路径清单（`scripts/changes_md.py` 生成，交付规则要求）。

---

## 六、范围边界（明确不做）

- 不做「逐字卡拉 OK / 双语 / 注音」——超出当前台本数据结构与 ASMR 场景需求。
- 不做毛玻璃底（`RenderEffect`/截图）——成本高、收益低，P3 以后再议。
- 不改台本解析器 `ParsedScript` 的数据结构（除非 P1 章节名确实需要，且独立评估）。
- 不动 editorial 皮肤的既有玻璃视觉。
