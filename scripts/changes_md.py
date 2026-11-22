#!/usr/bin/env python3
"""打印一次改动的文件清单（Markdown，按 新增 / 修改 / 删除 / 重命名 分组）。

给 review 用：CHANGELOG 是给用户看的（不写文件名），这里是给看代码的人看的（必须是路径）。
规则见 AGENTS.md「改动清单（Review 硬规则）」。

用法：
    python scripts/changes_md.py                 # 工作区未提交的改动（含未跟踪文件）
    python scripts/changes_md.py HEAD~1..HEAD    # 某段提交
    python scripts/changes_md.py v2.6.0-beta14..HEAD
    python scripts/changes_md.py <sha>           # 单个提交（等价 <sha>^..<sha>）

只依赖 git，不读构建产物。输出可以直接贴进 commit / PR 描述。
"""
from __future__ import annotations

import argparse
import subprocess
import sys

GROUPS = [
    ("A", "新增"),
    ("M", "修改"),
    ("D", "删除"),
    ("R", "重命名"),
    ("C", "复制"),
    ("T", "类型变更"),
]


def git(*args: str) -> str:
    r = subprocess.run(
        ["git", *args],
        capture_output=True,
        text=True,
        encoding="utf-8",
        errors="replace",
    )
    if r.returncode != 0:
        sys.exit("git " + " ".join(args) + " 失败：" + r.stderr.strip())
    return r.stdout


def has_rev(rev: str) -> bool:
    r = subprocess.run(
        ["git", "rev-parse", "--verify", "--quiet", rev + "^{commit}"],
        capture_output=True,
        text=True,
    )
    return r.returncode == 0


def parse_name_status(text: str) -> list[tuple[str, str, str]]:
    """`git diff --name-status` → [(状态字母, 旧路径, 新路径)]。"""
    out = []
    for line in text.splitlines():
        if not line.strip():
            continue
        parts = line.split("\t")
        code = parts[0][0]
        if code in ("R", "C") and len(parts) >= 3:
            out.append((code, parts[1], parts[2]))
        elif len(parts) >= 2:
            out.append((code, parts[1], parts[1]))
    return out


def numstat(text: str) -> dict[str, tuple[int, int]]:
    """`git diff --numstat` → {新路径: (增, 删)}；二进制文件记为 (-1, -1)。"""
    stats: dict[str, tuple[int, int]] = {}
    for line in text.splitlines():
        parts = line.split("\t")
        if len(parts) < 3:
            continue
        add, dele, path = parts[0], parts[1], parts[2]
        try:
            stats[path] = (int(add), int(dele))
        except ValueError:  # 二进制："-" "-"
            stats[path] = (-1, -1)
    return stats


def count_lines(path: str) -> int:
    try:
        with open(path, "rb") as f:
            return f.read().count(b"\n") + 1
    except OSError:
        return 0


def main() -> int:
    ap = argparse.ArgumentParser(description="按分组打印改动文件清单（Markdown）")
    ap.add_argument("rev", nargs="?", help="提交或范围，缺省看工作区")
    ap.add_argument(
        "--staged",
        action="store_true",
        help="只看已暂存的改动（写 commit message 时用）",
    )
    args = ap.parse_args()

    rev = args.rev
    if args.staged and rev:
        ap.error("--staged 不能和 rev 一起用")
    range_args: list[str]
    if args.staged:
        range_args = ["--cached"]
        rev = None
    elif not rev:
        range_args = ["HEAD"]
    else:
        if ".." in rev:
            range_args = [rev]
        elif has_rev(rev + "^"):
            range_args = [rev + "^", rev]
        else:  # 首个提交没有父，和空树比
            empty_tree = "4b825dc642cb6eb9a060e54bf8d69288fbee4904"
            range_args = [empty_tree, rev]

    name_status = git("diff", "--name-status", "-M", *range_args)
    entries = parse_name_status(name_status)
    stats = numstat(git("diff", "--numstat", "-M", *range_args))

    untracked: list[str] = []
    if not rev and not args.staged:
        untracked = [
            p
            for p in git("ls-files", "--others", "--exclude-standard").splitlines()
            if p.strip()
        ]
        entries += [("A", p, p) for p in untracked]

    if not entries:
        print("（没有改动）")
        return 0

    total_add = sum(v[0] for k, v in stats.items() if v[0] >= 0)
    total_del = sum(v[1] for k, v in stats.items() if v[1] >= 0)
    for p in untracked:
        total_add += count_lines(p)

    scope = "已暂存" if args.staged else (f"`{rev}`" if rev else "工作区")
    print(f"### 改动清单 · {scope}（{len(entries)} 个文件，+{total_add} / −{total_del} 行）")
    print()

    order = {k: i for i, (k, _) in enumerate(GROUPS)}
    labels = dict(GROUPS)
    for code in sorted({c for c, _, _ in entries}, key=lambda c: order.get(c, 99)):
        title = labels.get(code, code)
        items = [(o, n) for c, o, n in entries if c == code]
        print(f"**{title}（{len(items)}）**")
        print()
        for old, new in items:
            shown = f"`{old}` → `{new}`" if code in ("R", "C") else f"`{new}`"
            add, dele = stats.get(new, (None, None))
            if add is None and new in untracked:
                add, dele = count_lines(new), 0
            if add is not None and add >= 0:
                print(f"- {shown} · +{add}/−{dele}")
            else:
                print(f"- {shown} · 二进制")
        print()

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
