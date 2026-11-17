#!/usr/bin/env python3
"""把页面层的裸 dp 迁移到设计令牌。

为什么需要脚本而不是手工改：
641 处裸 dp 里绝大多数是 2/4/6/8/12/16/20/24/32 这些**令牌已有档位**的值，
手工改既慢又容易漏；真正需要人判断的只有「内容尺寸」（封面高度、列表最大
高度、断点这类），那些不在令牌档位里，脚本只报告、不擅自替换。

四条硬约束（都是踩过坑之后加的）：

1. **按上下文判断语义**。同一个 8dp 可能是 Spacing.sm、Radius.xs 或
   Modifier.size。只看数值替换会把 `asmrShape(8.dp)` 变成
   `asmrShape(Spacing.sm)`——能编译，但语义全错，后来人不敢改。
2. **只在 @Composable 函数体内替换**。`Spacing` / `Radius` 是
   `@Composable @ReadOnlyComposable` 的 getter，在普通函数、object 属性、
   默认参数里用会直接编译失败。计数大括号时必须先剥掉字符串字面量，
   否则日志模板里的 `{}` 会让整段函数被判成「非可组合」。
3. **只做等值替换**，除少数在 SNAP 表里显式登记且已逐页比对过截图的档位。
   默认差 1dp 也不吸附——那样会静默改变视觉，而验收标准是真机截图一致。
4. `Spacer(Modifier.height(12.dp))` 属于**间距**语义，不是图标尺寸。
   之前把它归到 size 导致大量漏改。

用法：
    python scripts/migrate_dp.py              # 干跑，报告可替换项与需人工处理项
    python scripts/migrate_dp.py --apply      # 实际写入
    python scripts/migrate_dp.py --file LibraryScreen.kt --apply
"""

from __future__ import annotations

import argparse
import collections
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
UI_DIR = ROOT / "app/src/main/java/com/kiite/player/ui"

DP_RE = re.compile(r"(?<![\d.])(\d+)\.dp\b")
# 用于剥离字符串/字符字面量后再数大括号
STRING_RE = re.compile(r'"(?:\\.|[^"\\])*"')
CHAR_RE = re.compile(r"'(?:\\.|[^'\\])*'")
BLOCK_COMMENT_RE = re.compile(r"/\*.*?\*/", re.S)

SPACING = {2: "Spacing.xxs", 4: "Spacing.xs", 6: "Spacing.itemGap", 8: "Spacing.sm",
           10: "Spacing.gap10", 12: "Spacing.md", 14: "Spacing.gap14", 16: "Spacing.lg",
           18: "Spacing.gap18", 20: "Spacing.section", 24: "Spacing.xl", 32: "Spacing.xxl"}
RADIUS = {8: "Radius.xs", 10: "Radius.sm",
          12: "Radius.md", 16: "Radius.lg", 20: "Radius.xl", 24: "Radius.xxl",
          999: "Radius.pill"}
ICON = {16: "IconSize.xs", 18: "IconSize.sm", 20: "IconSize.md", 24: "IconSize.lg",
        56: "IconSize.hero"}

# 已逐页比对过截图、允许吸附的偏差（原值 -> 令牌）。只登记差 ≤2dp 且语义一致的。
# 0dp 网格外只差 1dp 的值集中在 3/5/7/9/11/13/15/19/21，都是「手写时随手定的」，
# 吸附后反而让同类元素的间距一致；差 ≥3dp 的一律不动，留作页面局部常量。
SNAP_SPACING = {3: "Spacing.xs", 5: "Spacing.xs", 7: "Spacing.sm", 9: "Spacing.sm",
                11: "Spacing.md", 13: "Spacing.md", 15: "Spacing.lg",
                19: "Spacing.section", 21: "Spacing.section",
                22: "Spacing.xl", 30: "Spacing.xxl"}
SNAP_RADIUS = {6: "Radius.xs", 14: "Radius.md", 18: "Radius.xl"}
SNAP_ICON = {22: "IconSize.md"}
SNAP_TOUCH = {48: "TouchTarget.min"}

SPACING_ARGS = {"start", "end", "top", "bottom", "horizontal", "vertical", "padding",
                "contentPadding", "spacedBy", "space"}
RADIUS_ARGS = {"corner", "radius", "shape", "shape2"}
SIZE_ARGS = {"size"}
TOUCH_ARGS = {"minWidth", "minHeight"}

CALL_SPACING = {"spacedBy", "padding", "offset", "absoluteOffset"}
CALL_RADIUS = {"asmrShape", "RoundedCornerShape", "clip"}
CALL_SIZE = {"size", "width", "height", "requiredSize", "sizeIn"}


def strip_literals(line: str) -> str:
    """剥掉字符串与字符字面量，只留结构，用于数大括号。"""
    no_str = STRING_RE.sub('""', line)
    return CHAR_RE.sub("'_'", no_str)


def classify(pre: str, line: str, value: int) -> str:
    """根据数字左侧的代码判断语义类别。"""
    tail = pre.rstrip()

    # 描边/分隔条：进度条 strokeWidth、BorderStroke 的第一个参数
    if re.search(r"strokeWidth\s*=\s*$", tail):
        return "stroke"
    if re.search(r"BorderStroke\s*\(\s*$", tail):
        return "stroke"

    m = re.search(r"([A-Za-z_.]+)\s*\(\s*$", tail)
    if m:
        fn = m.group(1).split(".")[-1]
        if fn == "asmrShape" or fn == "RoundedCornerShape":
            return "radius"
        if fn == "clip":
            return "radius"
        if fn in CALL_SIZE:
            # Spacer(Modifier.height(12.dp)) 是间距，不是内容尺寸
            if "Spacer(" in line:
                return "spacing"
            # Box/Canvas 上的 2~4dp 高度是分隔条、进度条，不是内容尺寸
            if fn in ("height", "width") and 2 <= value <= 4:
                return "stroke"
            return "size"
        if fn in CALL_SPACING:
            return "spacing"
        return "call:" + fn

    m = re.search(r"([A-Za-z_]\w*)\s*=\s*$", tail)
    if m:
        arg = m.group(1)
        if arg in SPACING_ARGS:
            return "spacing"
        if arg in RADIUS_ARGS:
            return "radius"
        if arg in TOUCH_ARGS:
            return "touch"
        if arg in SIZE_ARGS:
            return "size"
        if arg == "height" and 2 <= value <= 5:
            return "stroke"
        if arg == "max" and "heightIn(" in line:
            return "scrollMax"
        if arg == "max" and "widthIn(" in line:
            return "dialogMax"
        if arg == "min" and "widthIn(" in line:
            return "dialogMin"
        return "arg:" + arg

    return "expr"


STROKE = {2: "Stroke.thin", 3: "Stroke.medium", 4: "Stroke.thick"}
# 弹层内可滚动列表的上限：存量有 300/380/420/430/440 五种，统一到一档
SCROLL_MAX = {300, 380, 420, 430, 440}


def token_for(kind: str, value: int) -> tuple[str | None, bool]:
    """返回 (令牌, 是否为吸附替换)。"""
    if kind == "spacing":
        t = SPACING.get(value)
        if t:
            return t, False
        if value in SNAP_SPACING:
            return SNAP_SPACING[value], True
        return None, False
    if kind == "radius":
        if value == 2:
            return "Radius.xxs", False
        if value in RADIUS and RADIUS[value]:
            return RADIUS[value], False
        if value in SNAP_RADIUS:
            return SNAP_RADIUS[value], True
        return None, False
    if kind == "stroke":
        t = STROKE.get(value)
        return (t, False) if t else (None, False)
    if kind == "scrollMax":
        return ("Containers.scrollMaxHeight", value != 420) if value in SCROLL_MAX else (None, False)
    if kind == "dialogMax":
        return ("Containers.dialogMaxWidth", False) if value == 560 else (None, False)
    if kind == "dialogMin":
        return ("Containers.dialogMinWidth", False) if value == 280 else (None, False)
    if kind == "size":
        if value in ICON:
            return ICON[value], False
        if value in SNAP_ICON:
            return SNAP_ICON[value], True
        return None, False
    if kind == "touch":
        if value in SNAP_TOUCH:
            return SNAP_TOUCH[value], True
        return None, False
    return None, False


def enclosing_composable(lines: list[str]) -> list[bool]:
    """逐行标记：这一行是否位于 @Composable 函数体内。

    用栈而不是单个变量来跟踪，原因是这个项目里有两类容易打脸的结构：

    - **局部函数**。`AsmrOnePickerScreen` 在可组合函数体内声明了 `fun toggleRow(...)`，
      用单个变量记住「当前函数」之后，局部函数一结束就再也回不到外层，整段可组合
      代码全被判成普通函数。
    - **多行签名**。`fun Foo(\\n  a: T,\\n) {` 的函数体大括号出现在几行之后，必须在
      遇到第一个 `{` 时才算进入函数体，否则 flags 会一直错到下一个声明。

    所以每帧记 (声明时深度, 是否 @Composable, 是否已进入函数体)：
    深度涨过声明深度即视为进入；退回到声明深度即弹栈，外层上下文自动恢复。

    注释与字符串先剥掉——否则 `Log.d(TAG, "{}")` 这类模板会把深度算歪。
    """
    flags = [False] * len(lines)
    depth = 0
    stack: list[list] = []      # [声明深度, 是否可组合, 是否已进入函数体]
    pending_composable = False

    joined = BLOCK_COMMENT_RE.sub(lambda m: "\n" * m.group(0).count("\n"), "\n".join(lines))
    scan = joined.splitlines()
    if len(scan) != len(lines):      # 理论上不会发生，兜底退化成原始行
        scan = lines

    for i, line in enumerate(scan):
        code = strip_literals(line.split("//")[0])
        stripped = code.strip()

        # 只有独占一行的注解才算「给下一个声明用」；参数里的
        # `content: @Composable () -> Unit` 不能算，否则下一个 fun 会被误判
        if stripped.startswith("@") and "@Composable" in stripped:
            pending_composable = True

        has_fun = re.search(r"\bfun\s+\w+", code) is not None
        if has_fun:
            while stack and stack[-1][0] >= depth:
                stack.pop()
            stack.append([depth, pending_composable, False])
            pending_composable = False

        flags[i] = bool(stack) and stack[-1][1]

        depth += code.count("{") - code.count("}")
        if stack and depth > stack[-1][0]:
            stack[-1][2] = True
        while stack and stack[-1][2] and depth <= stack[-1][0]:
            stack.pop()

    return flags


def strip_block_comments(lines: list[str]) -> list[str]:
    """把块注释内容抹成空串，保持行数不变。

    KDoc 里常有 `clip(asmrShape(12.dp))` 这种示例，不剥掉会被当成真代码统计，
    既污染报告，也让门禁把文档当成违规。
    """
    out: list[str] = []
    in_block = False
    for line in lines:
        if in_block:
            end = line.find("*/")
            if end < 0:
                out.append("")
                continue
            in_block = False
            line = " " * (end + 2) + line[end + 2:]
        while True:
            start = line.find("/*")
            if start < 0:
                break
            end = line.find("*/", start + 2)
            if end < 0:
                line = line[:start]
                in_block = True
                break
            line = line[:start] + " " * (end + 2 - start) + line[end + 2:]
        out.append(line)
    return out


def process(path: Path) -> tuple[list[str], list[dict]]:
    text = path.read_text(encoding="utf-8")
    lines = text.splitlines()
    scan_lines = strip_block_comments(lines)
    composable = enclosing_composable(lines)
    report: list[dict] = []
    out: list[str] = []

    for i, line in enumerate(lines):
        code, sep, comment = scan_lines[i].partition("//")
        matches = list(DP_RE.finditer(code))
        if not matches:
            out.append(line)
            continue

        new = code
        for m in reversed(matches):
            value = int(m.group(1))
            if value <= 1:
                continue
            kind = classify(code[: m.start()], code, value)
            token, snapped = token_for(kind, value)
            entry = {"file": path.name, "line": i + 1, "value": value, "kind": kind,
                     "token": token, "snapped": snapped, "composable": composable[i],
                     "text": line.strip()}
            if token is None:
                entry["reason"] = "无对应档位"
            elif not composable[i] and not token.startswith(("IconSize", "Stroke", "TouchTarget", "Containers")):
                entry["reason"] = "非 @Composable 上下文"
            else:
                new = new[: m.start()] + token + new[m.end():]
                entry["reason"] = "吸附替换" if snapped else "等值替换"
            report.append(entry)

        out.append(new + sep + comment)

    return out, report


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--apply", action="store_true")
    ap.add_argument("--file", help="只处理某个文件（文件名，不含路径）")
    args = ap.parse_args()

    targets = [p for p in sorted(UI_DIR.rglob("*.kt"))
               if "ui/theme" not in p.as_posix()]
    if args.file:
        targets = [p for p in targets if p.name == args.file]
        if not targets:
            print(f"! 找不到 {args.file}")
            return 2

    total_ok = total_snap = total_skip = 0
    per_file: dict[str, tuple[int, int]] = {}
    skipped: list[dict] = []

    for path in targets:
        out, report = process(path)
        ok = [r for r in report if r["reason"] in ("等值替换", "吸附替换")]
        snap = [r for r in ok if r["reason"] == "吸附替换"]
        skip = [r for r in report if r not in ok]
        total_ok += len(ok)
        total_snap += len(snap)
        total_skip += len(skip)
        skipped.extend(skip)
        if ok:
            per_file[path.name] = (len(ok), len(snap))
        if args.apply and ok:
            path.write_text("\n".join(out) + "\n", encoding="utf-8")

    verb = "已写入" if args.apply else "可替换"
    print(f"{verb}：{total_ok} 处（其中吸附 {total_snap} 处），涉及 {len(per_file)} 个文件")
    for name, (cnt, snap) in sorted(per_file.items(), key=lambda kv: -kv[1][0]):
        print(f"  {cnt:4d}  {name}" + (f"  （吸附 {snap}）" if snap else ""))

    if skipped:
        print(f"\n需人工判断：{total_skip} 处")
        grouped = collections.Counter((s["file"], s["value"], s["kind"], s["reason"])
                                      for s in skipped)
        for (f, v, k, why), cnt in sorted(grouped.items(), key=lambda kv: -kv[1])[:30]:
            print(f"  {cnt:3d}x  {v:4d}.dp  {k:14s}  {why}  {f}")

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
