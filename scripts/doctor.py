"""开发环境自检：换机器/新电脑先跑这个，缺什么直接说清楚。

用法：
    python scripts/doctor.py           # 检查环境（不联网）
    python scripts/doctor.py --net     # 额外检查能否连上 GitHub

不依赖第三方库。退出码 0 表示「可以开始干活」，1 表示有阻塞项。
"""

from __future__ import annotations

import json
import os
import re
import shutil
import subprocess
import sys
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
MIN_JDK = 17
COMPILE_SDK = 35

ok_list: list[str] = []
warn_list: list[str] = []
fail_list: list[str] = []


def ok(msg: str) -> None:
    ok_list.append(msg)
    print(f"  [OK]   {msg}")


def warn(msg: str) -> None:
    warn_list.append(msg)
    print(f"  [警告] {msg}")


def fail(msg: str) -> None:
    fail_list.append(msg)
    print(f"  [缺失] {msg}")


def run(cmd: list[str], cwd: Path | None = None) -> tuple[int, str]:
    try:
        p = subprocess.run(
            cmd,
            cwd=str(cwd or ROOT),
            capture_output=True,
            text=True,
            encoding="utf-8",
            errors="replace",
            timeout=60,
        )
        return p.returncode, (p.stdout or "") + (p.stderr or "")
    except (OSError, subprocess.SubprocessError) as e:
        return 127, str(e)


def header(title: str) -> None:
    print(f"\n== {title} ==")


def check_python() -> None:
    header("Python")
    v = sys.version_info
    if v >= (3, 10):
        ok(f"Python {v.major}.{v.minor}.{v.micro}（{sys.executable}）")
    elif v >= (3, 9):
        warn(f"Python {v.major}.{v.minor} 可用，但仓库脚本按 3.10+ 写的，建议升级")
    else:
        fail(f"Python {v.major}.{v.minor} 过旧，需要 3.9 以上")


def check_git() -> None:
    header("Git 与仓库")
    if not shutil.which("git"):
        fail("找不到 git，请先安装 Git（Windows 建议同时装 Git Bash）")
        return
    code, out = run(["git", "rev-parse", "--abbrev-ref", "HEAD"])
    if code != 0:
        fail(f"{ROOT} 不是 git 仓库，或 git 不可用")
        return
    branch = out.strip()
    code, out = run(["git", "log", "--oneline", "-1"])
    ok(f"仓库正常 · 分支 {branch} · 最新提交 {out.strip()[:70]}")
    code, out = run(["git", "status", "--short"])
    if out.strip():
        warn(f"工作区有未提交改动（{len(out.strip().splitlines())} 个文件）")
    else:
        ok("工作区干净")


def java_major(version_text: str) -> int | None:
    m = re.search(r'version "(\d+)(?:\.(\d+))?', version_text)
    if not m:
        return None
    major = int(m.group(1))
    return 11 if major == 1 and m.group(2) else major


def check_java() -> None:
    header("JDK")
    java_home = os.environ.get("JAVA_HOME", "").strip()
    if java_home:
        exe = Path(java_home) / "bin" / ("java.exe" if os.name == "nt" else "java")
        if not exe.exists():
            fail(f"JAVA_HOME 指向的路径没有 java：{java_home}")
            return
        code, out = run([str(exe), "-version"])
    else:
        if not shutil.which("java"):
            fail(f"没装 JDK，或 JAVA_HOME 未设置（需要 JDK {MIN_JDK}）")
            return
        code, out = run(["java", "-version"])
        warn("JAVA_HOME 未设置，当前用的是 PATH 里的 java，建议显式设置")

    major = java_major(out)
    if major is None:
        fail(f"读不出 java 版本：{out.strip().splitlines()[0][:80] if out.strip() else '(无输出)'}")
    elif major < MIN_JDK:
        fail(f"JDK {major} 太旧，AGP 8.x 需要 JDK {MIN_JDK} 以上")
    else:
        ok(f"JDK {major}{'（JAVA_HOME=' + java_home + '）' if java_home else ''}")


def android_sdk_dir() -> tuple[Path | None, str]:
    for key in ("ANDROID_HOME", "ANDROID_SDK_ROOT"):
        val = os.environ.get(key, "").strip()
        if val:
            return Path(val), key
    local = ROOT / "local.properties"
    if local.exists():
        for line in local.read_text(encoding="utf-8").splitlines():
            if line.strip().startswith("sdk.dir="):
                return Path(line.split("=", 1)[1].strip().replace("\\\\", "\\")), "local.properties"
    return None, ""


def check_android() -> None:
    header("Android SDK")
    sdk, source = android_sdk_dir()
    if sdk is None:
        fail("ANDROID_HOME 未设置，local.properties 里也没有 sdk.dir")
        return
    if not sdk.exists():
        fail(f"{source} 指向的 SDK 目录不存在：{sdk}")
        return
    ok(f"SDK 目录 {sdk}（来源 {source}）")

    platform = sdk / "platforms" / f"android-{COMPILE_SDK}"
    if platform.exists():
        ok(f"platform android-{COMPILE_SDK}")
    else:
        fail(f"缺 platforms/android-{COMPILE_SDK}（compileSdk 需要它）")

    bt = sdk / "build-tools"
    versions = sorted((p.name for p in bt.iterdir() if p.is_dir()), key=_ver_key) if bt.exists() else []
    if not versions:
        fail("缺 build-tools（apksigner / aapt2 都在里面）")
    elif _ver_key(versions[-1]) >= _ver_key("35.0.0"):
        ok(f"build-tools {versions[-1]}")
    else:
        warn(f"build-tools 最高只有 {versions[-1]}，建议装 35.0.0")

    adb = sdk / "platform-tools" / ("adb.exe" if os.name == "nt" else "adb")
    if adb.exists():
        ok("platform-tools（adb）")
    else:
        warn("缺 platform-tools，真机调试/安装会失败")


def _ver_key(v: str) -> tuple:
    parts = re.findall(r"\d+", v)
    return tuple(int(x) for x in parts) + (0,) * (3 - len(parts))


def check_signing() -> None:
    header("签名（只有发版才需要）")
    props = ROOT / "keystore.properties"
    store = None
    if props.exists():
        for line in props.read_text(encoding="utf-8", errors="replace").splitlines():
            if line.strip().startswith("storeFile="):
                store = line.split("=", 1)[1].strip().replace("\\\\", "\\")
                break
    env_store = os.environ.get("SIGNING_STORE_FILE", "").strip()
    store = store or env_store or None

    if not store:
        warn("没有密钥库配置 —— 改代码/单测/debug 包不受影响，assembleRelease 会直接失败")
        return
    path = Path(store)
    if not path.is_absolute():
        path = (ROOT / store).resolve()
    if not path.exists():
        fail(f"密钥库路径存在但文件不在：{path}")
        return
    ok(f"密钥库 {path.name}（{path.stat().st_size} 字节）")

    src = "keystore.properties" if props.exists() else "环境变量"
    missing = [
        k
        for k in ("storePassword", "keyAlias", "keyPassword")
        if not props.exists() and not os.environ.get(
            {"storePassword": "SIGNING_STORE_PASSWORD", "keyAlias": "SIGNING_KEY_ALIAS", "keyPassword": "SIGNING_KEY_PASSWORD"}[k]
        )
    ]
    if missing:
        fail(f"{src} 缺字段：{', '.join(missing)}")
    else:
        ok(f"签名配置齐全（{src}）")


def check_project() -> None:
    header("项目状态")
    gradle = ROOT / "app" / "build.gradle.kts"
    text = gradle.read_text(encoding="utf-8") if gradle.exists() else ""
    name = re.search(r'appVersionName\s*=\s*"([^"]+)"', text)
    code = re.search(r"appVersionCode\s*=\s*(\d+)", text)
    if name and code:
        ok(f"当前开发版本 {name.group(1)} / {code.group(1)}")
    else:
        fail("app/build.gradle.kts 里找不到 appVersionName / appVersionCode")

    vj = ROOT / "version.json"
    if vj.exists():
        try:
            d = json.loads(vj.read_text(encoding="utf-8"))
            ok(f"正式更新通道 {d.get('version')}（versionCode {d.get('versionCode')}）")
        except json.JSONDecodeError:
            fail("version.json 不是合法 JSON")
    else:
        warn("version.json 不存在")

    wrapper = ROOT / "gradlew"
    jar = ROOT / "gradle" / "wrapper" / "gradle-wrapper.jar"
    if wrapper.exists() and jar.exists():
        ok("Gradle wrapper 完整")
    else:
        fail("缺 gradlew 或 gradle-wrapper.jar，无法构建")

    if not (ROOT / "app" / "build").exists():
        warn("还没构建过；首次构建需要联网（之后可加 --offline）")


def check_net() -> None:
    header("网络（--net）")
    for name, url in (("GitHub API", "https://api.github.com"), ("Maven Central", "https://repo1.maven.org/maven2/")):
        try:
            req = urllib.request.Request(url, headers={"User-Agent": "kiite-doctor"})
            urllib.request.urlopen(req, timeout=15).read(1)
            ok(f"能连 {name}")
        except Exception as e:  # noqa: BLE001
            warn(f"连不上 {name}：{type(e).__name__} —— 首次构建和检查更新会受影响")


def main(argv: list[str]) -> int:
    print(f"Kiite Player 环境自检 · 仓库 {ROOT}")
    check_python()
    check_git()
    check_java()
    check_android()
    check_signing()
    check_project()
    if "--net" in argv[1:]:
        check_net()

    print("\n== 汇总 ==")
    print(f"  通过 {len(ok_list)} 项 / 警告 {len(warn_list)} 项 / 缺失 {len(fail_list)} 项")
    if fail_list:
        print("\n阻塞项：")
        for m in fail_list:
            print(f"  - {m}")
        print("\n修完再跑一次。环境搭建步骤见 docs/MACHINE-SETUP.md。")
        return 1
    if warn_list:
        print("\n可以干活；上面的警告不影响改代码，但发版/联网前留意。")
    else:
        print("\n环境就绪。下一步：./gradlew :app:testDebugUnitTest :app:assembleDebug --console=plain")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
