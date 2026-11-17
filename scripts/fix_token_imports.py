#!/usr/bin/env python3
"""给迁移后的文件补齐 `com.kiite.player.ui.theme.*` 令牌 import。

迁移脚本只改表达式不动 import，所以每个用到新令牌的文件都要补一行。
按现有 import 的字母序插入，避免 diff 里出现无意义的顺序漂移。
"""

from __future__ import annotations

import re
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
UI_DIR = ROOT / "app/src/main/java/com/kiite/player/ui"

TOKENS = ["Spacing", "Radius", "IconSize", "TouchTarget", "Elevation", "Motion",
          "Stroke", "Containers"]

# 只在「当成令牌用」的位置才算引用：Spacing.xxx / Radius.xxx / IconSize.xxx
USE_RE = {t: re.compile(r"(?<![\w.])" + t + r"\.") for t in TOKENS}
IMPORT_RE = re.compile(r"^import\s+([\w.]+)\s*$")


def fix(path: Path) -> list[str]:
    text = path.read_text(encoding="utf-8")
    lines = text.splitlines()

    # 去掉注释再看引用，避免把注释里提到的名字算进去
    body = "\n".join(l.split("//")[0] for l in lines)
    body = re.sub(r"/\*.*?\*/", "", body, flags=re.S)

    needed = [t for t in TOKENS if USE_RE[t].search(body)]
    if not needed:
        return []

    existing = {m.group(1) for m in (IMPORT_RE.match(l) for l in lines) if m}
    missing = [t for t in needed if f"com.kiite.player.ui.theme.{t}" not in existing]
    if not missing:
        return []

    imports = [i for i, l in enumerate(lines) if IMPORT_RE.match(l)]
    if not imports:
        return []

    for token in missing:
        fq = f"com.kiite.player.ui.theme.{token}"
        # 插到第一个字母序大于它的 import 之前
        insert_at = None
        for i in imports:
            name = IMPORT_RE.match(lines[i]).group(1)
            if name > fq:
                insert_at = i
                break
        if insert_at is None:
            insert_at = imports[-1] + 1
        lines.insert(insert_at, f"import {fq}")
        imports = [i for i, l in enumerate(lines) if IMPORT_RE.match(l)]

    path.write_text("\n".join(lines) + "\n", encoding="utf-8")
    return missing


def main() -> int:
    total = 0
    for path in sorted(UI_DIR.rglob("*.kt")):
        # ui/theme/ 本身就是令牌定义处，包内引用不需要 import
        if "ui/theme" in path.as_posix():
            continue
        added = fix(path)
        if added:
            total += len(added)
            print(f"{path.name}: +{', '.join(added)}")
    print(f"共补充 {total} 条 import")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
