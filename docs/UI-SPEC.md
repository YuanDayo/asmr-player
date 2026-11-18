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
| `Spacing.barClearance` | 4dp | 滚动内容与悬浮底栏之间的额外留白 |
| `Spacing.gap10` | 10dp | 卡片纵向内边距（半档） |
| `Spacing.gap14` | 14dp | 封面与文字的间隔（半档） |
| `Spacing.gap18` | 18dp | 作品卡横向内边距（半档） |

最后三个是**半档**：不在 4dp 网格上，但存量代码里用量很大且语义各自固定。
收进令牌的理由不是「好看」，而是此前同一类卡片的内边距在不同页面分别是 14/16/18，
谁都不知道哪个才是对的。收进来之后至少保证「同一个名字到哪都是同一个数」。

用法：`padding(horizontal = Spacing.lg)`，**不要**写 `padding(horizontal = 16.dp)`。

### 1.2 圆角 `Radius`

`xxs 2 / xs 8 / sm 10 / md 12 / lg 16 / xl 20 / xxl 24 / pill 999`

必须配 `asmrShape(Radius.md)` 使用，不要直接 `RoundedCornerShape(12.dp)` ——
`asmrShape` 会在「iOS 玻璃」皮肤下把小于 10dp 的圆角抬到 10dp，这是皮肤的一部分。

### 1.3 图标与触控

- `IconSize.xs 16 / sm 18 / md 20 / lg 24 / hero 56`
- 播放控制类图标固定 `IconSize.lg`，列表尾部操作 `IconSize.md`，章节播放 `IconSize.sm`。
- 触控目标：`TouchTarget.min = 48dp`，列表行最小高度 `TouchTarget.row = 56dp`。
  图标按钮视觉可以小，**点击区域不能小于 48dp**（`IconButton` 默认就是 48dp，别用 `Modifier.size(30.dp)` 压它）。

### 1.4 描边与容器

| 令牌 | 值 | 用途 |
|---|---|---|
| `Stroke.thin` | 2dp | 分隔线、进度条、选中描边 |
| `Stroke.medium` | 3dp | 章节左侧竖条等细指示条 |
| `Stroke.thick` | 4dp | 加粗进度条、卡片强调边 |
| `Containers.scrollMaxHeight` | 420dp | 弹层内可滚动列表的最大高度 |
| `Containers.dialogMinWidth` / `dialogMaxWidth` | 280 / 560dp | 对话框宽度区间 |

`scrollMaxHeight` 收这一档之前，同类弹层列表的上限散落着 300/380/420/430/440 五种值，
同一台手机上不同弹层的收起高度能差 140dp。1dp 发丝线不建令牌（门禁也放行）。

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
| 队列行首把手 | — | 按住上下拖动 = 调整播放顺序（拖拽中该行抬起） | — |
| 作品卡 / 列表行 | 进入详情 | 快捷编辑菜单 | 未启用 |
| MiniPlayer | 展开播放页 | — | 未启用（保留关闭按钮位） |
| 播放页封面 | 播放 / 暂停 | 查看大图 | — |

补充规则：

- 破坏性且**不可恢复**（删除播放列表、清空历史）→ 必须 `ConfirmDialog` 二次确认。
- 破坏性但**可恢复**（移出队列）→ 不弹框，执行后 Snackbar 给「撤销」，撤销要还原到原位置。
- 连续点同一 Tab 回到该 Tab 顶部（`LocalTabReselect`：曲库/设置已生效）。
- **左右划切换一级标签**（曲库 ⇄ 播放 ⇄ 设置）：横向位移 ≥ 72dp 且明显大于纵向才触发。
  手势挂在页面内容层（`AppRoot.swipeBetweenTabs`），里层先拿事件 —— 列表纵向滚动、chip 行、
  滑块、图片翻页谁消费了就不抢；没接管时不消费任何事件，点击/长按语义不变。
  新增横向可拖组件时不用改它，只要组件自己消费事件即可。
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
- `LocalBottomBarInset` 是「窗口底边 → 悬浮栏顶部 → 再留一段空气」的**总占位**，
  必须含系统手势条：`手势条 + 栏底下内边距 + 导航胶囊 + （迷你条） + Spacing.md`。
  以前用 `栏高 + 14dp` 顶替手势条，手势条在多数机型是 24dp，于是播放页走带正好探进
  胶囊顶部 10dp —— 走带中间那颗播放键被压掉一条边（2.5.5 用户截图）。
  这个值只在「迷你条在不在」翻转时变一次，**不跟动画走**，否则页面会逐帧重排。
- **播放页三档（紧凑 / 大封面 / 全页大图）都必须给「播放列表」和「图片」留落点**：
  同一组四格入口在每个档位都得点得开。全页大图的办法是让面板顶替封面区、四格与走带原地不动
  —— 只把状态切过去却没有绘制位置，用户看到的是「按钮没反应」（2.5.3 踩过）。
  同理，新加的任何面板别默认「只在大封面档存在」。

---

## 6. 落地状态与待办

已完成（2.5.3 开发批）：

- [x] `Dimens.kt` 令牌 + `LocalSpacing` / `LocalRadius`
- [x] `ui/components` 五个组件（TrackListItem / StateViews / SectionHeader / AppBottomSheet / AppDialog）
- [x] 曲库：空态、目录失效错误态、章节头、曲目行 + 长按菜单
- [x] 队列行统一 + 移出队列可撤销（还原原位置）
- [x] 筛选弹层改 `AppBottomSheet`；设置页分组卡片走令牌、开关行触控目标 48dp
- [x] Toast 清零（`ui/` 内已无 `Toast.makeText`）
- [x] **连续点同一 Tab 回到顶部**：`LocalTabReselect` 计数，曲库/设置各自监听；曲库有二级页面时先退回列表
- [x] **队列拖拽排序**：行首 `DragHandle` 把手 + 抬升阴影，`PlaybackController.queueMove(path, ±1)` 按路径定位，重排走 `animateItem()`
- [x] **`@Preview` 补齐**：列表行（浅/深/1.3x）、三态、分区标题（两套皮肤）、弹层与对话框（抽出内容框以便预览）
- [x] **令牌门禁**：`scripts/check_ui_tokens.py` + `.ui-token-baseline.json`，CI 的 validate 阶段拦截新增的 `Color(0x` 与裸 dp
- [x] **MiniPlayer → 播放页的共享元素转场**：`SharedTransitionLayout` + 两个 CompositionLocal；
      迷你条改为常驻 `AnimatedVisibility`，切页动画期间它仍在组合中，封面才能飞过去（见 6.1）
- [x] **页面层裸 dp 清零**：641 → 0。490 处等值替换成令牌、34 处 ±2dp 内吸附归一、
      其余提成本页具名常量（`PlayerDim` / `LibraryDim` 等）
- [x] **门禁升级**：具名常量放行、KDoc 代码示例不再误报、新增「令牌跨家族误用」检查

待办：

- [ ] 图标按钮尺寸归一：`PlayerDim.iconBtn34/40/44` 这些还贴着「历史遗留」标签，
      按 1.3 节应当收敛到 48dp 触控目标。会改变布局，需要一页一页调并真机比对
- [ ] 队列拖到列表边缘时自动滚动

### 6.1 共享元素转场是怎么成立的

关键在于「两个封面必须同时在场」：

- MiniPlayer 原来是 `if (tab != 1) { AnimatedVisibility(...) }`，切到播放页时整个子树被摘掉，
  元素在这一帧就不存在了，`sharedBounds` 只能得到硬切；
- 改成 `AnimatedVisibility(visible = tab != 1 && current != null)` 之后，退出动画（220ms，
  与 tab 切换同长）期间它仍在组合中，播放页的封面同时出现，两者才能配对。

两个作用域经 CompositionLocal 下发（`LocalSharedTransitionScope` / `LocalSharedElementScope`），
因为播放页的封面藏在 `PlayerScreen → TrackCard → CoverBox` 三层之下，逐层传参会把业务参数污染成动画参数。
取不到作用域时扩展函数原样返回 Modifier，所以 Preview、单测、未套 `SharedTransitionLayout` 的宿主都照常渲染。

降级策略不看机型，看系统「动画时长缩放」：为 0 视为用户关了动画，直接不做共享元素。
这比按 SDK 版本猜性能可靠得多 —— 开发者选项和无障碍设置里关掉动画的人比想象中多。

**圆角必须交给覆盖层自己裁**：过渡期间元素画在 `SharedTransitionLayout` 的覆盖层里，
两端各自的 `clip()`（父 Box 的圆角）不参与这层绘制 —— 只做 `sharedBounds` 的话，飞行途中
是一块方形的图，落地才变回圆角。所以要显式给 `clipInOverlayDuringTransition`。
按 dp 写死也不对：起点 36dp 圆角 8dp（比例 0.22）、终点 132dp 圆角 14dp（比例 0.11），
同一个 dp 值在一头必然不对；现在按短边比例算（0.15，上限 16dp），全程连续，两端各差 2～3dp。

**切页卡顿**：转场那 220ms 里实时磨砂每帧都要把身后内容重新捕获 + 高斯，是「切到播放页
顿一下」的主要来源。`MainScaffold` 在换标签时把 `ScrollActivity` 置为 true，磨砂退回
半透明纯色，转场结束 80ms 后恢复（`asmrFrost` 里判断）。


---

## 7. 验收清单

- [ ] 新增页面只组装组件，没有新的列表行 / 空态 / 弹层实现
- [ ] 页面层没有新的硬编码颜色；新增间距用的是 `Spacing.*`
- [ ] 手势行为与本文件第 4 节一致（随机抽 10 条操作路径核对）
- [ ] 删除类操作有撤销，或（不可恢复时）有二次确认
- [ ] 图标按钮可点击区域 ≥ 48dp，都有 `contentDescription`
- [ ] 浅色 / 深色 / 大字体（1.3x）三态下关键信息不截断
- [ ] 加载态用骨架屏，且加载完成不跳版
