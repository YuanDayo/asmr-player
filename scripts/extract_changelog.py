#!/usr/bin/env python3
"""从 CHANGELOG.md 里抽出某个版本的正文，用作 GitHub Release 描述，并校验写作规则。

用法：
    python scripts/extract_changelog.py v2.5.1-beta3        # 打印到 stdout
    python scripts/extract_changelog.py 2.5.1 --check       # 只校验（CI 用）

约定（见 docs/RELEASE-SPEC.md 第 4.4 节）：
  - 段标题必须是 `## [<版本>] - <YYYY-MM-DD>`，版本与 Git Tag 去掉开头的 v 后一致；
  - 段落正文到下一个 `## ` 标题为止；
  - 预发布段落必须带 `[测试版]` 或 `[预发布]` 标记；
  - **正式版段落里不得出现「测试版 / beta / 预发布」**：正式版只收录这一版的最终变化，
    不转载 beta 期的中间过程（这条以前只写在文档里，靠人记，真出过正式版 Release
    里混进 beta 文案的事故，所以现在改成硬校验）；
  - 一条改动一行、不超过 [MAX_ITEM_CHARS] 字、不折行；一段最多 [MAX_ITEMS] 条。

退出码：0 成功；1 不合规（CI 会因此失败）。
"""

from __future__ import annotations

import re
import sys

HEADING = re.compile(r"^##\s+\[(?P<ver>[^\]]+)\]", re.M)
PRERELEASE_HINT = re.compile(r"beta|alpha|rc", re.I)
MARKER = re.compile(r"\[(测试版|预发布)\]")
# 正式版段落里一个都不许出现的词（大小写都算）
PRERELEASE_WORDS = re.compile(r"beta|alpha|rc\d|\brc\b|测试版|预发布", re.I)
GROUP_HEADING = re.compile(r"^###\s+")
BULLET = re.compile(r"^- ")

MAX_ITEMS = 10
MAX_ITEM_CHARS = 60
# 「注意」用来说明升级前提（例如要先卸载一次），不是改动清单，单独留一档
ALLOWED_GROUPS = ("新增", "修复", "调整", "注意")


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


def visible_len(item: str) -> int:
    """去掉 markdown 记号后剩下的可见字符数（`- `、`**`、反引号都不算）。"""
    s = item.lstrip("- ").replace("**", "").replace("`", "")
    return len(s.strip())


def lint(version: str, body: str) -> list[str]:
    """返回违规清单；空表示合规。"""
    problems: list[str] = []
    lines = body.splitlines()

    # 1) 预发布 / 正式版的互斥标记
    if is_prerelease(version) and not MARKER.search(body):
        problems.append("预发布版本必须在第一行写 **[测试版]** 或 **[预发布]**")
    if not is_prerelease(version):
        hit = PRERELEASE_WORDS.search(body)
        if hit:
            problems.append(
                f"正式版不得出现「{hit.group(0)}」—— 正式版只写这一版的最终变化，"
                "不转载 beta 段落、不提 beta 版本号"
            )

    # 2) 条目：一行一条、不折行、长度与数量上限（「注意」档是升级前提，不计入改动条数）
    bullets = 0
    group = ""
    for i, line in enumerate(lines):
        if GROUP_HEADING.match(line):
            group = line[4:].strip().split("（")[0]
            continue
        if BULLET.match(line):
            if group != "注意":
                bullets += 1
            n = visible_len(line)
            if n > MAX_ITEM_CHARS:
                problems.append(f"第 {i + 1} 行条目 {n} 字，超过 {MAX_ITEM_CHARS} 字：{line[:24]}…")
            if i + 1 < len(lines) and lines[i + 1].startswith("  "):
                problems.append(f"第 {i + 1} 行条目折行了，压成一行：{line[:24]}…")
    if bullets > MAX_ITEMS:
        problems.append(f"一段 {bullets} 条改动，超过 {MAX_ITEMS} 条，请合并")

    # 3) 只允许固定的几档小标题，且不许出现「无」（凑数的小标题比没有更吵）
    for i, line in enumerate(lines):
        if GROUP_HEADING.match(line) and line[4:].strip().split("（")[0] not in ALLOWED_GROUPS:
            problems.append(f"第 {i + 1} 行小标题 `{line[4:].strip()}` 不合规，只允许 新增 / 修复 / 调整 / 注意")
    if re.search(r"^-\s*无\s*$", body, re.M):
        problems.append("没有内容就删掉整档小标题，不要写「无」")

    return problems


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

    problems = lint(version, body)
    if problems:
        for p in problems:
            print(f"✗ [{version}] {p}", file=sys.stderr)
        print("规则见 CHANGELOG.md 顶部与 docs/RELEASE-SPEC.md 第 4.4 节", file=sys.stderr)
        return 1

    if check_only:
        print(f"OK {version}（{len(body)} 字符）", file=sys.stderr)
        return 0

    print(body if body else f"（{version} 暂无正文）")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
