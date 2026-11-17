#!/usr/bin/env python3
"""把剩余的裸 dp 提成**页面局部常量**，放进文件顶部的 `private object XxxDim`。

为什么不都做成全局令牌：
剩下的这些是控件尺寸（图标按钮 34/40/44、播放控制条 30/32、封面 132/280、
断点 600/760），同值不同义，且每个页面只出现几次。硬塞进全局令牌只会让
`Spacing` 变成一张数字表，反而看不出「哪些是设计规范、哪些是历史遗留」。

放成本地常量至少做到三点：能按名字搜到、改一处就全改、门禁不再把它当魔法数字。
命名用「角色+数值」（`iconBtn34`），不做语义美化——因为它们本来就该在下一批
里被归一化成 48dp 触控目标，现在起一个好看的名字反而会让人以为是有意设计。

用法：
    python scripts/localize_dp.py            # 干跑
    python scripts/localize_dp.py --apply
"""

from __future__ import annotations

import argparse
import collections
import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from migrate_dp import DP_RE, process, UI_DIR  # noqa: E402

OBJECT_NAME = {
    "AppRoot.kt": "RootDim",
    "PlayerScreen.kt": "PlayerDim",
    "LibraryScreen.kt": "LibraryDim",
    "SettingsScreen.kt": "SettingsDim",
    "StateViews.kt": "StateDim",
    "AsmrCard.kt": "CardDim",
    "SectionHeader.kt": "HeaderDim",
    "TrackListItem.kt": "TrackDim",
    "AsmrOnePickerScreen.kt": "PickerDim",
    "BookmarksPanel.kt": "BookmarkDim",
    "DlsiteDownloadScreen.kt": "DownloadDim",
    "DownloadProgress.kt": "ProgressDim",
    "ScriptCalibrationUi.kt": "CalibrationDim",
}


def role_for(text: str, kind: str) -> str:
    """从这一行推断这个尺寸是干什么用的，只用于命名。"""
    if "IconButton" in text:
        return "iconBtn"
    if re.search(r"Icon\(", text):
        return "icon"
    if "CircularProgressIndicator" in text:
        return "spinner"
    if "Spacer(" in text:
        return "gap"
    if kind == "radius" or "CoverBox" in text or "MediaBox" in text:
        return "corner"
    if re.search(r"\.height\(|height\s*=", text):
        return "height"
    if re.search(r"\.width\(|width\s*=", text):
        return "width"
    if kind == "stroke":
        return "stroke"
    if kind == "spacing":
        return "pad"
    return "box"


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--apply", action="store_true")
    args = ap.parse_args()

    total = 0
    for path in sorted(UI_DIR.rglob("*.kt")):
        if "ui/theme" in path.as_posix():
            continue
        lines = path.read_text(encoding="utf-8").splitlines()
        _, report = process(path)
        todo = [r for r in report
                if r["reason"] not in ("已替换", "等值替换", "吸附替换")]
        if not todo:
            continue

        obj = OBJECT_NAME.get(path.name, "PageDim")
        # 每个 (角色, 数值) 一个常量；同一行多个同值只建一个
        consts: dict[tuple[str, int], str] = {}
        # 按行归组，从后往前改，避免列偏移
        by_line: dict[int, list[dict]] = collections.defaultdict(list)
        for r in todo:
            by_line[r["line"]].append(r)

        for ln in sorted(by_line, reverse=True):
            entries = by_line[ln]
            line = lines[ln - 1]
            # 一行里可能有多个；按列位置从右往左替换
            spans = []
            for r in entries:
                role = role_for(r["text"], r["kind"])
                key = (role, r["value"])
                name = f"{role}{r['value']}"
                consts.setdefault(key, name)
                spans.append((r["value"], name))
            for value, name in reversed(spans):
                # 只替换这一行里还没被替换的最右一个该值
                idx = line.rfind(f"{value}.dp")
                if idx >= 0:
                    line = line[:idx] + f"{obj}.{name}" + line[idx + len(f"{value}.dp"):]
            lines[ln - 1] = line

        # 生成常量块，按数值排序，注释说明「待归一化」
        block = [
            "",
            "/**",
            f" * {path.stem} 独有的控件尺寸。",
            " *",
            " * 这些不进全局令牌：它们是本页特有的控件大小（图标按钮、进度条、封面档位），",
            " * 同值不同义，硬塞进 Spacing / IconSize 会让令牌表变成一张数字表。",
            " * 集中在这里的目的是可搜、可一处改；真正的归一化（图标按钮统一到 48dp",
            " * 触控目标）留给单独一批，因为会改变布局。",
            " */",
            f"private object {obj} {{",
        ]
        for (role, value), name in sorted(consts.items(), key=lambda kv: (kv[0][0], kv[0][1])):
            block.append(f"    val {name} = {value}.dp")
        block.append("}")
        block.append("")

        # 插到 import 之后、第一个声明之前
        insert_at = 0
        for i, l in enumerate(lines):
            if l.startswith("import "):
                insert_at = i + 1
        lines[insert_at:insert_at] = block

        if not any(l.startswith("import androidx.compose.ui.unit.dp") for l in lines):
            lines.insert(insert_at, "import androidx.compose.ui.unit.dp")

        total += len(todo)
        print(f"{path.name}: {len(todo)} 处 -> {obj}（{len(consts)} 个常量）")
        if args.apply:
            path.write_text("\n".join(lines) + "\n", encoding="utf-8")

    print(f"共 {total} 处")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
