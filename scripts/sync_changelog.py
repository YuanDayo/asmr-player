"""把根目录 CHANGELOG.md 同步到应用内 assets，并去掉只给维护者看的写作规则段落。

应用内「更新日志」页读的是 `app/src/main/assets/CHANGELOG.md`，根目录那份是权威源。
两份靠手工同步必然漂移（曾经停更在两个版本之前），所以这里做成一键同步 + 可校验。

用法：
    python scripts/sync_changelog.py           # 写入 assets
    python scripts/sync_changelog.py --check   # 只校验，不一致退出码 1（CI 用）
"""

from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SOURCE = ROOT / "CHANGELOG.md"
TARGET = ROOT / "app" / "src" / "main" / "assets" / "CHANGELOG.md"
STRIP_PREFIX = "写作规则"


def render(source_text: str) -> str:
    """去掉开头那段「写作规则」，其余原样保留。"""
    out: list[str] = []
    skipping = False
    for line in source_text.splitlines():
        if line.startswith(STRIP_PREFIX):
            skipping = True
            continue
        if skipping:
            if line.startswith("## ["):
                skipping = False
            else:
                continue
        out.append(line)
    text = re.sub(r"\n{3,}", "\n\n", "\n".join(out))
    return text.rstrip("\n") + "\n"


def main(argv: list[str]) -> int:
    if not SOURCE.exists():
        print(f"找不到 {SOURCE}")
        return 1
    if not TARGET.parent.exists():
        print(f"找不到目录 {TARGET.parent}")
        return 1

    expected = render(SOURCE.read_text(encoding="utf-8"))
    current = TARGET.read_text(encoding="utf-8") if TARGET.exists() else ""

    if "--check" in argv[1:]:
        if current == expected:
            first = next((l for l in expected.splitlines() if l.startswith("## [")), "")
            print(f"OK 应用内更新日志与根目录一致（最新段落 {first.strip()}）")
            return 0
        print("应用内更新日志与根目录 CHANGELOG.md 不一致。")
        print("修：python scripts/sync_changelog.py")
        return 1

    if current == expected:
        print("无需改动，已是同步状态")
        return 0
    TARGET.write_text(expected, encoding="utf-8", newline="\n")
    first = next((l for l in expected.splitlines() if l.startswith("## [")), "")
    print(f"已同步到 {TARGET.relative_to(ROOT)}（最新段落 {first.strip()}）")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
