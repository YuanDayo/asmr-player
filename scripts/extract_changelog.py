#!/usr/bin/env python3
"""从 CHANGELOG.md 里抽出某个版本的正文，用作 GitHub Release 描述。

用法：
    python scripts/extract_changelog.py v2.5.1-beta3        # 打印到 stdout
    python scripts/extract_changelog.py 2.5.1 --check       # 只校验段落存在

约定（见 docs/RELEASE-SPEC.md 第 5 节）：
  - 段标题必须是 `## [<版本>] - <YYYY-MM-DD>`，版本与 Git Tag 去掉开头的 v 后一致；
  - 段落正文到下一个 `## ` 标题为止；
  - 预发布段落必须带 `[测试版]` 或 `[预发布]` 标记，正式版段落不得带。

退出码：0 成功；1 找不到段落或标记不合规（CI 会因此失败）。
"""

from __future__ import annotations

import re
import sys

HEADING = re.compile(r"^##\s+\[(?P<ver>[^\]]+)\]", re.M)
PRERELEASE_HINT = re.compile(r"beta|alpha|rc", re.I)
MARKER = re.compile(r"\[(测试版|预发布)\]")


def is_prerelease(version: str) -> bool:
    return bool(PRERELEASE_HINT.search(version))


def extract(text: str, version: str) -> str | None:
    """返回该版本段落正文（不含标题行），找不到返回 None。"""
    for m in HEADING.finditer(text):
        if m.group("ver").strip() != version:
            continue
        body_start = text.find("\n", m.end())
        if body_start < 0:
            return ""
        nxt = HEADING.search(text, body_start)
        body = text[body_start : nxt.start() if nxt else len(text)]
        return body.strip("\n").rstrip()
    return None


def main(argv: list[str]) -> int:
    args = [a for a in argv[1:] if not a.startswith("--")]
    check_only = "--check" in argv[1:]
    if not args:
        print("用法：extract_changelog.py <tag> [--check]", file=sys.stderr)
        return 2

    version = args[0].strip().removeprefix("v")
    try:
        text = open("CHANGELOG.md", encoding="utf-8").read()
    except OSError as e:
        print(f"读不到 CHANGELOG.md：{e}", file=sys.stderr)
        return 1

    body = extract(text, version)
    if body is None:
        print(f"未找到 {version} 的 CHANGELOG 段落（标题应为 ## [{version}] - YYYY-MM-DD）", file=sys.stderr)
        return 1

    if is_prerelease(version) and not MARKER.search(body):
        print(f"{version} 是预发布版本，段落里必须带 [测试版] 或 [预发布] 标记", file=sys.stderr)
        return 1
    if not is_prerelease(version) and MARKER.search(body):
        print(f"{version} 是正式版，段落里不得出现 [测试版]/[预发布] 标记", file=sys.stderr)
        return 1

    if check_only:
        print(f"OK {version}（{len(body)} 字符）", file=sys.stderr)
        return 0

    print(body if body else f"（{version} 暂无正文）")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
