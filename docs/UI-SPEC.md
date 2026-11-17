# UI 规范（Kiite Player）

本文件是界面与交互的唯一标准。新增页面、改列表、加弹层之前先读它；评审时按这里的条目逐项核对。
配套计划书：《Kiite Player UI 统一与操作逻辑优化计划书 v1.0》。

设计原则只有三条：

1. **一套令牌**：间距、圆角、图标、动效全部走 `ui/theme/Dimens.kt`，页面不写魔法值。
2. **一套组件**：列表行、空态、弹层、确认框只有一份实现，页面只做组装。
3. **一套手势**：同一种手势在任何列表里含义相同（见第 4 节）。

---

## 1. 设计令牌

代码位置：`app/src/main/java/com/kiite/player/ui/theme/Dimens.kt`

### 1.1 间距 `Spacing`

| 令牌 | 值 | 用途 |
|---|---|---|
| `Spacing.xxs` | 2dp | 紧贴元素（标签与数字） |
| `Spacing.xs` | 4dp | 图标与文字 |
| `Spacing.sm` | 8dp | 组内元素间隔 |
| `Spacing.md` | 12dp | 列表行左右内边距 |
| `Spacing.lg` | 16dp | 页面左右、卡片内边距 |
| `Spacing.section` | 20dp | 分区之间 |
| `Spacing.xl` | 24dp | 对话框内边距、大块间隔 |
| `Spacing.xxl` | 32dp | 整页空态边距 |
| `Spacing.itemGap` | 6dp | 列表项之间的垂直间隔 |

用法：`padding(horizontal = Spacing.lg)`，**不要**写 `padding(horizontal = 16.dp)`。

### 1.2 圆角 `Radius`

`xs 8 / sm 10 / md 12 / lg 16 / xl 20 / xxl 24 / pill 999`

必须配 `asmrShape(Radius.md)` 使用，不要直接 `RoundedCornerShape(12.dp)` ——
`asmrShape` 会在「iOS 玻璃」皮肤下把小于 10dp 的圆角抬到 10dp，这是皮肤的一部分。

### 1.3 图标与触控

- `IconSize.xs 16 / sm 18 / md 20 / lg 24 / hero 56`
- 播放控制类图标固定 `IconSize.lg`，列表尾部操作 `IconSize.md`，章节播放 `IconSize.sm`。
- 触控目标：`TouchTarget.min = 48dp`，列表行最小高度 `TouchTarget.row = 56dp`。
  图标按钮视觉可以小，**点击区域不能小于 48dp**（`IconButton` 默认就是 48dp，别用 `Modifier.size(30.dp)` 压它）。

### 1.4 动效 `Motion`

- 时长：`SHORT 150`（状态切换）/ `MEDIUM 220`（展开、页面切换）/ `LONG 400`（大范围布局变化）。
- 缓动：进入用 `Motion.Decelerate`，离开用 `Motion.Accelerate`，通用 `Motion.Standard`。
- 列表增删用 `Modifier.animateItem()`；禁止用默认 spring 做跟手操作（中途掉帧很明显）。

### 1.5 颜色

颜色全部来自 `MaterialTheme.colorScheme`。页面层**禁止** `Color(0xFF...)`。
皮肤（EDITORIAL / CLASSIC / BRIGHT / DEEP_SEA / MINT / SAKURA / PAPER）只影响配色与圆角下限，
间距梯度全局一致 —— 所以令牌里没有按皮肤分叉。

---

## 2. 通用组件

包：`com.kiite.player.ui.components`

| 组件 | 用途 | 不要再用 |
|---|---|---|
| `TrackListItem` | 曲目行（曲库 / 队列 / 搜索） | `ListItem` 手写 headline/supporting/leading/trailing |
| `SectionHeader` | 章节、设置分组、结果区块标题 | 各写一份 Row + 竖条 |
| `EmptyState` / `ErrorState` / `LoadingState` | 三态 | 空白页、Toast 报错、转圈 |
| `AppBottomSheet` + `SheetAction` + `SheetSectionLabel` | 排序、筛选、操作菜单 | 零散 `DropdownMenu`、各处自己写 `ModalBottomSheet` |
| `AppDialog` / `ConfirmDialog` | 内容弹窗 / 破坏性二次确认 | 直接 `AlertDialog` 各写一份样式 |
| `AsmrCard` / `asmrShape` / `asmrBorder` / `asmrFrost` | 卡片容器与皮肤材质 | 自写 `Card` + 圆角 |

约定：

- 页面层不得引用另一个页面的私有 Composable。要复用就抽到 `ui/components`。
- 组件默认带 `semantics(mergeDescendants = true)`（列表行），TalkBack 读整行而不是逐元素。
- 每个核心组件在文件里提供 `@Preview` 覆盖浅色 / 深色 / 大字体（新增组件必须带）。

---

## 3. 三态规范

| 状态 | 组件 | 规则 |
|---|---|---|
| 空 | `EmptyState` | 说明「为什么空」+ 一个「接下来能做什么」的主按钮 |
| 加载 | `LoadingState` | 骨架屏优先，形状与真实列表一致，避免加载完成时跳版 |
| 错误 | `ErrorState` | 说明发生了什么 + 重试入口；图标用 `colorScheme.error` |

文案要求：空态不能只写「暂无数据」。必须能回答「我该做什么」。
错误态必须区分「网络/权限/数据本身没有」，不要把三种原因写成同一句「加载失败」。

反馈通道：

- 轻量结果 → Snackbar（`vm.say(...)`）
- 可撤销操作 → Snackbar + `撤销`（`vm.undo` / `emitUndo(UndoRequest(...))`）
- 阻塞性错误 → Dialog
- **禁止 Toast**（`Toast.makeText` 在 `ui/` 里已清零，新代码不要加回来）

---

## 4. 手势语义表（全项目唯一标准）

| 对象 | 单击 | 长按 | 左滑 |
|---|---|---|---|
| 曲目行（曲库/详情） | 播放 | 操作菜单（播放 / 加入播放列表 / 选择台本） | 未启用 |
| 队列行 | 跳转播放 | — | 未启用（尾部有移除按钮） |
| 作品卡 / 列表行 | 进入详情 | 快捷编辑菜单 | 未启用 |
| MiniPlayer | 展开播放页 | — | 未启用（保留关闭按钮位） |
| 播放页封面 | 播放 / 暂停 | 查看大图 | — |

补充规则：

- 破坏性且**不可恢复**（删除播放列表、清空历史）→ 必须 `ConfirmDialog` 二次确认。
- 破坏性但**可恢复**（移出队列）→ 不弹框，执行后 Snackbar 给「撤销」，撤销要还原到原位置。
- 连续点同一 Tab 回到该 Tab 顶部（待实现，见第 6 节）。
- 返回键按层级退：播放/设置 → 曲库 → 作品详情 → 列表 → 退后台；播放页内部的覆盖态（阅读模式、
  大图）各自有更里层的 `BackHandler`，先于外层命中。

---

## 5. 页面骨架

一级页面统一结构：

```
TopBar（标题 + 右侧操作）
  └ 可选：搜索框 / 筛选 Chip 行
列表
MiniPlayer（全局唯一，AppRoot 里的悬浮底栏）
```

- MiniPlayer 只有一份实例，挂在 `AppRoot` 的悬浮底栏里，**页面不得自己再挂一个**。
- 排序 / 筛选入口统一收进 TopBar 右侧图标 → `AppBottomSheet`，禁止页面内散落下拉框。
- 滚动容器的底部留白用 `LocalBottomBarInset.current`，不要写死数值。

---

## 6. 落地状态与待办

已完成（2.5.3 开发批）：

- [x] `Dimens.kt` 令牌 + `LocalSpacing` / `LocalRadius`
- [x] `ui/components` 五个组件（TrackListItem / StateViews / SectionHeader / AppBottomSheet / AppDialog）
- [x] 曲库：空态、目录失效错误态、章节头、曲目行 + 长按菜单
- [x] 队列行统一 + 移出队列可撤销（还原原位置）
- [x] 筛选弹层改 `AppBottomSheet`；设置页分组卡片走令牌、开关行触控目标 48dp
- [x] Toast 清零（`ui/` 内已无 `Toast.makeText`）

待办：

- [ ] MiniPlayer → 播放页的共享元素转场（`SharedTransitionLayout`，Compose 1.7 已有；低端机按设备降级为淡入）
- [ ] 连续点同一 Tab 回到顶部（需要把各页 `LazyListState` 提升或加一个 scroll-to-top 事件）
- [ ] 双指缩放/拖拽排序的把手与抬升阴影（队列拖拽）
- [ ] `@Preview` 补齐到全部核心组件
- [ ] 页面层残留 `xx.dp` 逐步替换（一次一个页面，改完必须真机比对截图）
- [ ] ktlint / detekt 自定义规则：禁止页面层 `Color(0x` 与裸 `dp`

---

## 7. 验收清单

- [ ] 新增页面只组装组件，没有新的列表行 / 空态 / 弹层实现
- [ ] 页面层没有新的硬编码颜色；新增间距用的是 `Spacing.*`
- [ ] 手势行为与本文件第 4 节一致（随机抽 10 条操作路径核对）
- [ ] 删除类操作有撤销，或（不可恢复时）有二次确认
- [ ] 图标按钮可点击区域 ≥ 48dp，都有 `contentDescription`
- [ ] 浅色 / 深色 / 大字体（1.3x）三态下关键信息不截断
- [ ] 加载态用骨架屏，且加载完成不跳版
