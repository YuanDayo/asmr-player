#!/usr/bin/env python3
"""界面令牌门禁：禁止在 UI 层新写硬编码颜色与裸 dp。

为什么不用 ktlint / detekt 的自定义规则：
两者都要引入插件与依赖版本，离线构建与 CI 缓存都会多一层不确定性；
而这条规则本身只是「数一数正则」，用 30 行 Python 更可控，也不需要动构建脚本。

规则（详见 docs/UI-SPEC.md 第 1 节）：

1. `Color(0x...)` —— 页面层颜色必须来自 `MaterialTheme.colorScheme`。
   例外：`ui/theme/` 下定义调色板的文件。
2. 裸 `12.dp` —— 间距/圆角必须走 `Spacing.*` / `Radius.*` / `IconSize.*`。
   例外：`1.dp`（发丝线）不计；`ui/theme/` 下定义令牌的文件不计。

用法：

    python scripts/check_ui_tokens.py                 # 与基线比较，新增即失败
    python scripts/check_ui_tokens.py --write-baseline # 重新生成基线（改造完成后）

基线文件 `.ui-token-baseline.json` 记录每个文件当前的违规数量。这样存量代码
不会被一次性判死，但**任何新增**都会被拦下 —— 这正是「逐步替换」需要的语义。
"""

from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
UI_DIR = ROOT / "app/src/main/java/com/kiite/player/ui"
BASELINE = ROOT / ".ui-token-baseline.json"

# ui/theme/ 是令牌与调色板本身的定义处，天然允许出现具体数值
EXEMPT_PREFIXES = ("ui/theme/",)

COLOR_RE = re.compile(r"Color\(0x")
# 前面不能是数字或小数点：22.dp 里的 "2.dp" 不算一次；"1.dp" 发丝线放行
DP_RE = re.compile(r"(?<![\d.])(\d+)\.dp\b")


def rel(path: Path) -> str:
    return path.relative_to(ROOT).as_posix()


def scan() -> dict[str, dict[str, int]]:
    result: dict[str, dict[str, int]] = {}
    for path in sorted(UI_DIR.rglob("*.kt")):
        name = rel(path)
        if any(name.endswith(p) or f"/{p}" in name for p in EXEMPT_PREFIXES):
            continue
        colors = 0
        dps = 0
        for line in path.read_text(encoding="utf-8").splitlines():
            code = line.split("//")[0]
            colors += len(COLOR_RE.findall(code))
            dps += sum(1 for n in DP_RE.findall(code) if int(n) > 1)
        if colors or dps:
            result[name] = {"color": colors, "dp": dps}
    return result


def load_baseline() -> dict[str, dict[str, int]] | None:
    if not BASELINE.exists():
        return None
    try:
        return json.loads(BASELINE.read_text(encoding="utf-8"))
    except json.JSONDecodeError:
        print(f"! 基线文件解析失败，忽略：{rel(BASELINE)}")
        return None


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--write-baseline", action="store_true")
    args = parser.parse_args()

    current = scan()

    if args.write_baseline:
        BASELINE.write_text(
            json.dumps(current, ensure_ascii=False, indent=2, sort_keys=True) + "\n",
            encoding="utf-8",
        )
        total_c = sum(v["color"] for v in current.values())
        total_d = sum(v["dp"] for v in current.values())
        print(f"已写入基线：{rel(BASELINE)}（{len(current)} 个文件，颜色 {total_c} 处，裸 dp {total_d} 处）")
        return 0

    baseline = load_baseline()
    if baseline is None:
        print("! 没有基线文件，本次只做统计不判失败。需要收紧时运行 --write-baseline 后再提交。")

    regressions: list[str] = []
    for name, counts in sorted(current.items()):
        old = (baseline or {}).get(name, {"color": 0, "dp": 0})
        for kind, label in (("color", "硬编码颜色"), ("dp", "裸 dp")):
            delta = counts[kind] - old.get(kind, 0)
            if delta > 0:
                regressions.append(f"  {name}: {label} +{delta}（现 {counts[kind]}）")

    if regressions:
        print("× 新增了硬编码样式，请改用令牌（Spacing / Radius / IconSize / colorScheme）：")
        print("\n".join(regressions))
        print("\n改造完成后可用 --write-baseline 更新基线。")
        return 1

    total_c = sum(v["color"] for v in current.values())
    total_d = sum(v["dp"] for v in current.values())
    improved = 0
    if baseline:
        for name, counts in current.items():
            old = baseline.get(name)
            if old and (counts["color"] < old.get("color", 0) or counts["dp"] < old.get("dp", 0)):
                improved += 1
    print(f"✓ UI 令牌检查通过：{len(current)} 个 UI 文件，颜色 {total_c} 处、裸 dp {total_d} 处（≤ 基线）")
    if improved:
        print(f"  有 {improved} 个文件比基线更干净了，可以重新生成基线锁定成果。")
    return 0


if __name__ == "__main__":
    sys.exit(main())
