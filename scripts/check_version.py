#!/usr/bin/env python3
"""版本一致性校验：唯一权威源 vs Git Tag vs CHANGELOG vs 正式更新通道。

用法：
    python scripts/check_version.py                 # 只做仓库内部一致性检查
    python scripts/check_version.py v2.5.1-beta3    # 额外校验 Tag 与 versionName 完全一致
    python scripts/check_version.py v2.5.1 --release # 发版模式：额外要求签名配置完整、
                                                     # 正式版必须同步 version.json

规则（见 docs/RELEASE-SPEC.md）：
  1. versionName / versionCode 只在 app/build.gradle.kts 顶部定义；
  2. Tag 去掉开头的 v 后必须与 versionName 完全一致；
  3. 预发布 Tag（含 beta/alpha/rc）↔ versionName 含同样后缀，二者必须同时成立；
  4. CHANGELOG 必须有对应段落，且预发布段落带 [测试版]，正式版段落不带；
  5. 预发布发布不得更新正式更新通道（version.json 的 version 不能等于该预发布版本）。
  7. 版本基线不得随意抬升：上一个版本还是预发布（x.y.z-betaN）且 x.y.z 正式版还没发时，
     下一个版本必须仍在该 x.y.z 上递增（betaN+1）；确需换基线要用户明确要求，
     并加 --allow-base-bump（CI 永远不带这个参数）。
  6. 发版模式：release 变体不得回退 debug 签名；正式版 Tag 的 version.json
     必须已经指向该版本且 versionCode 对齐（预发布不许改 version.json）。

退出码 0 通过，1 不通过（CI 会因此中断发布）。
"""

from __future__ import annotations

import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(Path(__file__).resolve().parent))
import semver  # noqa: E402  （scripts/ 不是包，按脚本目录导入）
GRADLE_KTS = ROOT / "app" / "build.gradle.kts"
GRADLE_GROOVY = ROOT / "app" / "build.gradle"
VERSION_JSON = ROOT / "version.json"

PRERELEASE = re.compile(r"beta|alpha|rc", re.I)


def read_gradle_version() -> tuple[str, int]:
    """从 Gradle 脚本里读出 (versionName, versionCode)。"""
    if GRADLE_KTS.exists():
        text = GRADLE_KTS.read_text(encoding="utf-8")
        name = re.search(r'appVersionName\s*=\s*"([^"]+)"', text) or re.search(
            r'versionName\s*=\s*"([^"]+)"', text
        )
        code = re.search(r"appVersionCode\s*=\s*(\d+)", text) or re.search(
            r"versionCode\s*=\s*(\d+)", text
        )
    elif GRADLE_GROOVY.exists():
        text = GRADLE_GROOVY.read_text(encoding="utf-8")
        name = re.search(r'versionName\s*[= ]\s*["\']([^"\']+)["\']', text)
        code = re.search(r"versionCode\s*[= ]\s*(\d+)", text)
    else:
        raise SystemExit("找不到 app/build.gradle.kts 或 app/build.gradle")
    if not name or not code:
        raise SystemExit("在 Gradle 脚本里读不到 versionName / versionCode")
    return name.group(1).strip(), int(code.group(1))


CHANGELOG_HEADING = re.compile(r"^##\s*\[(?P<version>[^\]]+)\]", re.M)


def changelog_versions(text: str) -> list[str]:
    """CHANGELOG 里出现过的版本号，按文件顺序（新 → 旧）。"""
    return [m.group("version").strip() for m in CHANGELOG_HEADING.finditer(text)]


def check_base_bump(version: str, history: list[str], allow: bool = False) -> list[str]:
    """规则 7：上一个版本还是预发布时，不许把版本基线（x.y.z）换掉。

    场景：2.5.5-beta10 之后只能接 2.5.5-beta11 或 2.5.5 正式版；
    2.5.6-beta1 / 2.6.0-beta1 都要拦，直到 2.5.5 的正式版发布出去。

    历史取自 CHANGELOG（每个发布版本都有段落，且新版本在上），
    不依赖 git tag，浅克隆的 CI 也能判。
    """
    if not history:
        return []
    if version in history:
        idx = history.index(version)
        prev = next((v for v in history[idx + 1 :] if v != version), None)
    else:
        # 新版本还没写进 CHANGELOG：上一个发布就是最新那条
        prev = history[0]
    if prev is None:
        return []  # 仓库里的第一个版本，没有历史可比
    try:
        cur, last = semver.parse(version), semver.parse(prev)
    except ValueError:
        return []  # 版本号本身不合法，由发版模式的 parse 检查报
    print(f"上一个发布 = {prev}")
    if cur.core == last.core:
        return []
    if cur.core < last.core:
        return [f"版本回退：{version} 比上一个发布 {prev} 还旧"]
    if not last.is_prerelease:
        return []  # 上一个基线已经发过正式版，允许抬基线
    if allow:
        print(f"⚠ 上一个版本 {prev} 还是预发布，但已按用户要求允许抬版本基线")
        return []
    major, minor, patch = last.core
    return [
        f"版本基线被抬：上一个发布 {prev} 还是预发布，{major}.{minor}.{patch} 的正式版还没发，"
        f"下一个版本必须仍是 {major}.{minor}.{patch}-betaN（递增 beta 号），不能是 {version}。"
        "规则见 docs/RELEASE-SPEC.md 2.5；确需换基线必须用户明确要求，本地校验加 --allow-base-bump（CI 不带）。"
    ]


def check(tag: str | None, release: bool = False, allow_base_bump: bool = False) -> list[str]:
    errors: list[str] = []
    version, code = read_gradle_version()
    print(f"versionName = {version} / versionCode = {code}")

    # 2 & 3：Tag 与版本后缀一致性
    if tag:
        want = tag.strip().removeprefix("v")
        print(f"tag          = {tag}（比对值 {want}）")
        if want != version:
            errors.append(f"Tag 与 versionName 不一致：{want} != {version}")
        if bool(PRERELEASE.search(want)) != bool(PRERELEASE.search(version)):
            errors.append(f"预发布后缀不对称：tag={want} versionName={version}")

    # 4：CHANGELOG 段落与标记
    sys.path.insert(0, str(Path(__file__).resolve().parent))
    from extract_changelog import MARKER, extract, is_prerelease  # noqa: E402

    changelog = ROOT / "CHANGELOG.md"
    text = changelog.read_text(encoding="utf-8") if changelog.exists() else ""
    body = extract(text, version)
    if body is None:
        errors.append(f"CHANGELOG.md 缺少 ## [{version}] - <日期> 段落")
    else:
        marked = bool(MARKER.search(body))
        if is_prerelease(version) and not marked:
            errors.append(f"{version} 是预发布版本，CHANGELOG 段落必须带 [测试版] 标记")
        if not is_prerelease(version) and marked:
            errors.append(f"{version} 是正式版，CHANGELOG 段落不得出现 [测试版] 标记")

    # 7：版本基线不得随意抬升（只在带 Tag 的发版检查里执行；
    #    main 上的普通提交没有 Tag，此时不判，避免历史版本把 CI 顶红）
    if tag and changelog.exists():
        errors.extend(check_base_bump(version, changelog_versions(text), allow=allow_base_bump))

    # 5：预发布不得动正式更新通道；通道版本不得比当前版本还新
    channel_version, channel_code = "", None
    if VERSION_JSON.exists():
        try:
            vj = json.loads(VERSION_JSON.read_text(encoding="utf-8"))
        except json.JSONDecodeError as e:
            errors.append(f"version.json 不是合法 JSON：{e}")
            vj = {}
        channel_version = str(vj.get("version", "")).strip()
        raw_code = vj.get("versionCode")
        channel_code = raw_code if isinstance(raw_code, int) else None
        print(f"version.json = {channel_version}（versionCode {channel_code}，正式更新通道）")
        if is_prerelease(version) and channel_version == version:
            errors.append("预发布版本把 version.json 指到了自己，普通用户会收到测试包")
        if channel_version:
            try:
                if semver.compare(channel_version, version) > 0:
                    errors.append(f"正式更新通道 {channel_version} 比当前版本 {version} 还新，方向搞反了")
            except ValueError as e:
                errors.append(f"version.json 里的版本号不合法：{e}")
    else:
        print("version.json 不存在，跳过更新通道检查")

    # 6：发版模式 —— 签名配置必须完整，正式版必须同步更新通道
    if release:
        text = (GRADLE_KTS if GRADLE_KTS.exists() else GRADLE_GROOVY).read_text(encoding="utf-8")
        if re.search(r'signingConfigs\.getByName\("debug"\)', text):
            errors.append("release 变体仍在回退 debug 签名，签出来的包装不到用户手机上")
        if "requireReleaseSigning" not in text:
            errors.append("build.gradle.kts 缺少 -PrequireReleaseSigning 守卫")
        if not tag:
            errors.append("发版模式必须传入 Tag")
        else:
            try:
                semver.parse(version)
            except ValueError as e:
                errors.append(str(e))
            if not is_prerelease(version):
                if channel_version != version:
                    errors.append(
                        f"正式版必须把 version.json 更新到 {version}（当前 {channel_version}），"
                        "否则应用内更新不会把它推给用户"
                    )
                if channel_code is not None and channel_code != code:
                    errors.append(
                        f"version.json 的 versionCode（{channel_code}）与 Gradle（{code}）不一致"
                    )

    # 6：源码里不得再出现当前版本号的硬编码
    for path in sorted((ROOT / "app" / "src" / "main").rglob("*")):
        if path.suffix not in {".kt", ".java", ".xml"} or not path.is_file():
            continue
        for lineno, line in enumerate(path.read_text(encoding="utf-8", errors="ignore").splitlines(), 1):
            if version in line:
                rel = path.relative_to(ROOT)
                errors.append(f"{rel}:{lineno} 硬编码了当前版本号 {version}，应改为读 BuildConfig.VERSION_NAME")

    return errors


def main(argv: list[str]) -> int:
    args = [a for a in argv[1:] if not a.startswith("--")]
    release = "--release" in argv[1:]
    allow_base_bump = "--allow-base-bump" in argv[1:]
    tag = args[0].strip() if args and args[0].strip() else None
    errors = check(tag, release=release, allow_base_bump=allow_base_bump)
    if errors:
        for e in errors:
            print(f"::error::{e}" if _in_actions() else f"✗ {e}")
        return 1
    print("✓ 版本一致性检查通过" if not _in_actions() else "::notice::版本一致性检查通过")
    return 0


def _in_actions() -> bool:
    import os

    return os.environ.get("GITHUB_ACTIONS") == "true"


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
