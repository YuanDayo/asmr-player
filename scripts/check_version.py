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


def check(tag: str | None, release: bool = False) -> list[str]:
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
    import semver  # noqa: E402
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
    tag = args[0].strip() if args and args[0].strip() else None
    errors = check(tag, release=release)
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
