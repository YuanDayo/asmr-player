"""严格 SemVer 解析与比较（供发布脚本与 CI 校验使用）。

规则按 https://semver.org/lang/zh-CN/ ：
  - 主版本.次版本.修订号 逐段比数字；
  - 有预发布后缀的一律小于同核心版本的正式版（2.5.2-beta1 < 2.5.2）；
  - 预发布段按 '.' 切分，纯数字段按数字比，其余按 ASCII 字典序，数字段小于字母段；
  - 段数少的更小（2.5.2-beta < 2.5.2-beta.1）。
"""

from __future__ import annotations

import re
from dataclasses import dataclass

_PATTERN = re.compile(
    r"^(?P<major>0|[1-9]\d*)\.(?P<minor>0|[1-9]\d*)\.(?P<patch>0|[1-9]\d*)"
    r"(?:-(?P<pre>[0-9A-Za-z-]+(?:\.[0-9A-Za-z-]+)*))?"
    r"(?:\+[0-9A-Za-z-]+(?:\.[0-9A-Za-z-]+)*)?$"
)


@dataclass(frozen=True)
class Version:
    core: tuple[int, int, int]
    prerelease: tuple[str, ...] | None

    @property
    def is_prerelease(self) -> bool:
        return self.prerelease is not None


def parse(value: str) -> Version:
    match = _PATTERN.fullmatch(value.strip().removeprefix("v"))
    if not match:
        raise ValueError(f"不是合法的 SemVer 版本号：{value}")
    pre = match.group("pre")
    return Version(
        (int(match.group("major")), int(match.group("minor")), int(match.group("patch"))),
        tuple(pre.split(".")) if pre else None,
    )


def compare(a: str, b: str) -> int:
    """a 比 b 旧返回 -1，相同返回 0，更新返回 1。"""
    va, vb = parse(a), parse(b)
    if va.core != vb.core:
        return (va.core > vb.core) - (va.core < vb.core)
    if va.prerelease is None and vb.prerelease is None:
        return 0
    if va.prerelease is None:
        return 1
    if vb.prerelease is None:
        return -1
    for xa, xb in zip(va.prerelease, vb.prerelease):
        if xa == xb:
            continue
        na, nb = xa.isdigit(), xb.isdigit()
        if na and nb:
            return (int(xa) > int(xb)) - (int(xa) < int(xb))
        if na != nb:
            return -1 if na else 1
        return (xa > xb) - (xa < xb)
    return (len(va.prerelease) > len(vb.prerelease)) - (len(va.prerelease) < len(vb.prerelease))


def is_prerelease(value: str) -> bool:
    return parse(value).is_prerelease


def normalize(value: str) -> str:
    """去掉开头的 v / 空白，返回用于比较和展示的规范写法。"""
    return value.strip().removeprefix("v")
