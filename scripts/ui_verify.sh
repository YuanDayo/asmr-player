#!/usr/bin/env bash
# 模拟器验证流程：起模拟器 → 装包 → 按模式操作 → 截图拉回。
#
# 为什么写成一个脚本：这个环境里命令跑在独立沙箱，模拟器进程与后续 adb 命令
# 不在同一网络命名空间，跨命令连接必然掉成 offline。把 emulator 与 adb 放进
# 同一次调用，连接才稳定；脚本结束模拟器会跟着退出，但 userdata 会保留
# （-no-snapshot 只跳过快照，不重置镜像），所以「选曲库目录」这类状态可以跨轮次复用。
#
# 用法：
#   scripts/ui_verify.sh <apk> <输出目录> setup       # 首次：建测试曲库并选中
#   scripts/ui_verify.sh <apk> <输出目录> shots       # 截图各页面
#   scripts/ui_verify.sh <apk> <输出目录> transition  # 切页转场连拍
#   WIPE=1 ... setup                                  # 需要干净环境时重置 userdata
set -u
ADB=/d/android-dev/sdk/platform-tools/adb.exe
export MSYS_NO_PATHCONV=1
APK="${1:?需要 apk 路径}"
OUT="${2:?需要输出目录}"
MODE="${3:-shots}"
S=emulator-5554

# adb.exe 是 Windows 原生程序，只认 C:/... 这种路径。脚本里 MSYS_NO_PATHCONV=1
# 又关掉了自动转换，所以这里手动把 /c/xxx 换成 C:/xxx —— 否则 install / pull 会静默失败。
winpath() { printf '%s' "$1" | sed -E 's#^/([a-zA-Z])/#\1:/#' | sed -E 's#^([a-z]):#\U\1:#'; }
APK="$(winpath "$APK")"
OUT="$(winpath "$OUT")"

mkdir -p "$OUT"

start_emu() {
  "$ADB" start-server >/dev/null 2>&1
  WIPE_FLAG=""
  [ "${WIPE:-0}" = "1" ] && WIPE_FLAG="-wipe-data"
  ( cd /d/android-dev/sdk/emulator && ./emulator.exe -avd asmr -no-window -no-audio \
      -no-boot-anim $WIPE_FLAG -no-snapshot -gpu swiftshader_indirect >/tmp/emu.log 2>&1 ) &
  for i in $(seq 1 120); do
    "$ADB" devices | grep -q "$S[[:space:]]*device" && break
    sleep 2
  done
  for i in $(seq 1 90); do
    [ "$("$ADB" -s "$S" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r\n')" = "1" ] && break
    sleep 2
  done
}

dump_ui() {  # 把界面树拉到 $OUT/ui.xml
  "$ADB" -s "$S" shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
  "$ADB" -s "$S" pull /sdcard/ui.xml "$OUT/ui.xml" >/dev/null 2>&1 \
    || echo "  ! 界面树拉回失败（$OUT 是 Windows 路径吗？）"
}

wait_text() {  # wait_text "文字" [最多秒] —— 等某个文字出现
  local want="$1" max="${2:-40}" i=0
  while [ "$i" -lt "$max" ]; do
    dump_ui
    if python - "$OUT/ui.xml" "$want" <<'PY'
import sys
try:
    s=open(sys.argv[1],encoding='utf-8',errors='ignore').read()
except OSError:
    sys.exit(1)
sys.exit(0 if sys.argv[2] in s else 1)
PY
    then return 0; fi
    sleep 3; i=$((i+3))
  done
  return 1
}

tap_text_exact() {  # tap_text_exact "文字" —— 只匹配完全相等的文本
  # 为什么需要：`已购未下载 1 部（点「下载」…）` 这种说明文字里也含「下载」，
  # 子串匹配会先命中说明文字，点了等于没点（本轮踩过）。按钮要按全等找。
  local want="$1"
  dump_ui
  local xy
  xy=$(python - "$OUT/ui.xml" "$want" <<'PY'
import re,sys
path,want=sys.argv[1],sys.argv[2]
try:
    s=open(path,encoding='utf-8',errors='ignore').read()
except OSError:
    sys.exit()
for m in re.finditer(r'text="([^"]*)"[^>]*?bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', s):
    if m.group(1).strip()==want:
        print((int(m.group(2))+int(m.group(4)))//2, (int(m.group(3))+int(m.group(5)))//2); break
PY
)
  if [ -n "$xy" ]; then
    "$ADB" -s "$S" shell input tap $xy >/dev/null 2>&1
    return 0
  fi
  echo "  ! 找不到「$want」（全等）"
  return 1
}

tap_text() {  # tap_text "文字" —— 按无障碍文本定位并点击中心
  local want="$1"
  dump_ui
  local xy
  xy=$(python - "$OUT/ui.xml" "$want" <<'PY'
import re,sys
path,want=sys.argv[1],sys.argv[2]
try:
    s=open(path,encoding='utf-8',errors='ignore').read()
except OSError:
    sys.exit()
for m in re.finditer(r'text="([^"]*)"[^>]*?bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', s):
    if want in m.group(1):
        x=(int(m.group(2))+int(m.group(4)))//2
        y=(int(m.group(3))+int(m.group(5)))//2
        print(x,y); break
PY
)
  if [ -n "$xy" ]; then
    "$ADB" -s "$S" shell input tap $xy >/dev/null 2>&1
    return 0
  fi
  echo "  ! 找不到「$want」"
  return 1
}

trace_center() {  # trace_center "无障碍描述" —— 只打印坐标不点击，供「同一 shell 里连点带拍」用
  local want="$1"
  dump_ui
  python - "$OUT/ui.xml" "$want" <<'PY'
import re,sys
path,want=sys.argv[1],sys.argv[2]
try:
    s=open(path,encoding='utf-8',errors='ignore').read()
except OSError:
    sys.exit()
for m in re.finditer(r'content-desc="([^"]*)"[^>]*?bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', s):
    if want in m.group(1):
        print((int(m.group(2))+int(m.group(4)))//2, (int(m.group(3))+int(m.group(5)))//2)
        break
PY
}

text_center() {  # text_center "文字" —— 只打印坐标不点击（同 trace_center，按可见文字找）
  local want="$1"
  dump_ui
  python - "$OUT/ui.xml" "$want" <<'PY'
import re,sys
path,want=sys.argv[1],sys.argv[2]
try:
    s=open(path,encoding='utf-8',errors='ignore').read()
except OSError:
    sys.exit()
for m in re.finditer(r'text="([^"]*)"[^>]*?bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', s):
    if want in m.group(1):
        print((int(m.group(2))+int(m.group(4)))//2, (int(m.group(3))+int(m.group(5)))//2)
        break
PY
}

tap_desc() {  # tap_desc "无障碍描述" —— 图标按钮没有文字，只能按 content-desc 定位
  local want="$1"
  dump_ui
  local xy
  xy=$(python - "$OUT/ui.xml" "$want" <<'PY'
import re,sys
path,want=sys.argv[1],sys.argv[2]
try:
    s=open(path,encoding='utf-8',errors='ignore').read()
except OSError:
    sys.exit()
for m in re.finditer(r'content-desc="([^"]*)"[^>]*?bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', s):
    if want in m.group(1):
        x=(int(m.group(2))+int(m.group(4)))//2
        y=(int(m.group(3))+int(m.group(5)))//2
        print(x,y); break
PY
)
  if [ -n "$xy" ]; then
    "$ADB" -s "$S" shell input tap $xy >/dev/null 2>&1
    return 0
  fi
  echo "  ! 找不到描述「$want」"
  return 1
}

tap_desc_exact() {  # tap_desc_exact "描述" —— 只匹配完全相等，避免「播放」命中「顺序播放（点按切换）」
  local want="$1"
  dump_ui
  local xy
  xy=$(python - "$OUT/ui.xml" "$want" <<'PY'
import re,sys
path,want=sys.argv[1],sys.argv[2]
try:
    s=open(path,encoding='utf-8',errors='ignore').read()
except OSError:
    sys.exit()
for m in re.finditer(r'content-desc="([^"]*)"[^>]*?bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', s):
    if m.group(1).strip()==want:
        print((int(m.group(2))+int(m.group(4)))//2, (int(m.group(3))+int(m.group(5)))//2); break
PY
)
  if [ -n "$xy" ]; then
    "$ADB" -s "$S" shell input tap $xy >/dev/null 2>&1
    return 0
  fi
  echo "  ! 找不到描述「$want」（全等）"
  return 1
}

shot() {
  "$ADB" -s "$S" shell screencap -p /sdcard/"$1".png >/dev/null 2>&1
  "$ADB" -s "$S" pull /sdcard/"$1".png "$OUT/$1.png" >/dev/null 2>&1 \
    || echo "  ! 截图拉回失败 $1（$OUT 是 Windows 路径吗？）"
  echo "  截图 $1"
}

# 桌面歌词浮窗的位置：只认日志。
#
# 为什么不用 uiautomator / dumpsys：浮窗是 TYPE_APPLICATION_OVERLAY 的独立窗口，
# 界面树里根本没有它；这套镜像的 `dumpsys window windows` 又不打 mFrame
# （两个坑都踩过，脚本一度误判成「浮窗没显示」）。控制器因此在 show() 与拖动松手时
# 各打一行 KiiteLyric 日志，格式固定：show px=x,y size=w x h / settle px=x,y dp=dx,dy
lyric_show() {
  "$ADB" -s "$S" shell logcat -d -s KiiteLyric:* 2>/dev/null | tr -d '\r' \
    | grep "show px=" | tail -1 \
    | sed -E 's/.*show px=([0-9]+),([0-9]+) size=([0-9]+)x([0-9]+).*/\1 \2 \3 \4/'
}

# 浮窗在**屏幕坐标**下的 frame（触摸注入用这套坐标）。
#
# 为什么要单独一个：LayoutParams.y 是「去掉 systemBars 之后」的坐标系，实测窗口
# frame 比 show 日志里的 py 多出一个状态栏高度（128px）。拿 py 直接去点会点在窗口
# 上沿外面 2px —— 事件穿透到下面的应用，看着就像「浮窗拖不动」。
lyric_frame_input() {
  "$ADB" -s "$S" shell dumpsys input 2>/dev/null | tr -d '\r' \
    | grep -m1 "KiiteLyricOverlay" \
    | sed -E 's/.*frame=\[(-?[0-9]+),(-?[0-9]+)\]\[(-?[0-9]+),(-?[0-9]+)\].*/\1 \2 \3 \4/'
}

lyric_settle() {
  "$ADB" -s "$S" shell logcat -d -s KiiteLyric:* 2>/dev/null | tr -d '\r' \
    | grep "settle px=" | tail -1 \
    | sed -E 's/.*settle px=([0-9]+),([0-9]+) dp=([0-9]+),([0-9]+).*/px=\1,\2 dp=\3,\4/'
}

# 当前拿到焦点的窗口（判断「双击有没有把 App 拉回来」）。
focus_line() {
  "$ADB" -s "$S" shell dumpsys window 2>/dev/null | tr -d '\r' \
    | grep -m1 "mCurrentFocus" | sed -E 's/^ +//'
}

# 走完首启引导（给不验引导本身的模式用）。
#
# 「跳过」只跳过 5 页功能说明，后面还有一页条款：必须勾选「我已阅读并同意…」，
# 按钮才会从禁用变成「同意并开始使用」。少这一步，后面的截图全被引导盖着。
# 每一步都容错：引导不在场时这些点击全部落空，不影响别的模式。
dismiss_onboarding() {
  tap_text "跳过" >/dev/null 2>&1 || true
  sleep 2
  tap_text "我已阅读并同意" >/dev/null 2>&1 || true
  sleep 1
  tap_text "同意并开始使用" >/dev/null 2>&1 || true
  sleep 2
}

seed_library() {  # 造测试曲库：两个作品、各 3 集、其中一集带「时间轴台本」
  "$ADB" -s "$S" shell mkdir -p /sdcard/Music/asmr 2>/dev/null
  local LRC_B64
  LRC_B64=$(printf '[00:00.00]第一句：这件事要从很久以前说起\n[00:03.00]第二句：那天的雨下得很大\n[00:06.00]第三句：她把伞收起来，走进了屋檐\n[00:09.00]第四句：风吹过走廊，纸门轻轻响了一下\n[00:12.00]第五句：没有人说话，只有远处的车声\n[00:15.00]第六句：他说，先坐下吧\n[00:18.00]第七句：木地板在脚下发出细小的声音\n[00:21.00]第八句：茶倒进杯子里，热气慢慢升起来\n[00:24.00]第九句：窗外的树影在墙上晃\n[00:27.00]第十句：后来他们谁也没有再提起那一天\n' | base64 -w0)
  for w in "RJ01234567 深夜の耳かき喫茶" "RJ07654321 雨音と珈琲"; do
    code=${w%% *}; name=${w#* }
    "$ADB" -s "$S" shell mkdir -p "/sdcard/Music/asmr/${code}_${name}" 2>/dev/null
    for i in 01_track 02_track 03_track; do
      "$ADB" -s "$S" shell "echo x > '/sdcard/Music/asmr/${code}_${name}/${i}.mp3'" 2>/dev/null
    done
    "$ADB" -s "$S" shell "echo sc > '/sdcard/Music/asmr/${code}_${name}/台本.txt'" 2>/dev/null
    "$ADB" -s "$S" shell "echo $LRC_B64 | base64 -d > '/sdcard/Music/asmr/${code}_${name}/01_track.lrc'" 2>/dev/null
  done
  "$ADB" -s "$S" shell am force-stop com.kiite.player >/dev/null 2>&1
  "$ADB" -s "$S" shell am start -n com.kiite.player/.MainActivity >/dev/null 2>&1
}

echo "== 启动模拟器"
start_emu
# 关掉窗口动画，共享元素动画是 Compose 自己驱动的，不受影响
"$ADB" -s "$S" shell settings put global window_animation_scale 0 >/dev/null 2>&1
"$ADB" -s "$S" shell settings put global transition_animation_scale 0 >/dev/null 2>&1
"$ADB" -s "$S" shell settings put global animator_duration_scale 1 >/dev/null 2>&1

echo "== 安装"
# adb.exe 不认 /c/... 这种 POSIX 路径，装不上时以前只会静默继续 —— 于是整整一轮
# 验证跑的都是设备上的旧包（2026-11-17 踩过）。这里必须硬失败。
INSTALL_OUT=$("$ADB" -s "$S" install -r -g "$APK" 2>&1 | tr -d '\r' | grep -v '^$' | tail -3)
echo "  ${INSTALL_OUT:-（安装命令没有任何输出）}"
case "$INSTALL_OUT" in
  *Success*) ;;
  *) echo "!! 安装失败，检查 APK 路径（要传 C:/... 而不是 /c/...）"; exit 1 ;;
esac
"$ADB" -s "$S" shell appops set --uid com.kiite.player MANAGE_EXTERNAL_STORAGE allow >/dev/null 2>&1
# 桌面歌词是 TYPE_APPLICATION_OVERLAY，没这个 appop 时 show() 会静默不挂窗口
"$ADB" -s "$S" shell appops set --uid com.kiite.player SYSTEM_ALERT_WINDOW allow >/dev/null 2>&1
echo "  已装版本：$("$ADB" -s "$S" shell dumpsys package com.kiite.player | grep -m1 versionName | tr -d '\r')"

# 验「全新安装」但不想付 `-wipe-data` 的代价（它要重建 7GB 的 userdata，
# 宿主盘不够时模拟器直接起不来）时用它：只清应用自己的数据，
# DataStore 回到空 —— 对首启引导来说和全新安装等价。
if [ "${CLEAR_APP:-0}" = "1" ]; then
  "$ADB" -s "$S" shell pm clear com.kiite.player >/dev/null 2>&1
  echo "  已清空应用数据（等价于全新安装）"
fi

if [ "$MODE" = "setup" ]; then
  echo "== 造测试曲库"
  seed_library
  sleep 6
fi

echo "== 启动应用"
"$ADB" -s "$S" shell am force-stop com.kiite.player >/dev/null 2>&1
"$ADB" -s "$S" shell am start -n com.kiite.player/.MainActivity >/dev/null 2>&1
sleep 6

# 首启可能弹更新日志
if tap_text "知道了"; then sleep 2; fi
# 首启引导：全新安装时它盖在最上层，别的模式先走完它，
# 它自己的验收交给 `onboarding` 模式（那边要抓引导本身）。
if [ "$MODE" != "onboarding" ]; then
  dismiss_onboarding
fi
# 权限页：appops 已设好，点「重新检测」让它重读
if tap_text "已授权，重新检测"; then sleep 3; fi
if tap_text "知道了"; then sleep 2; fi

if [ "$MODE" = "setup" ] || [ "$MODE" = "select" ]; then
  # 默认落在首页：空态的「选择文件夹」按钮在曲库页里，先切过去
  "$ADB" -s "$S" shell input tap 540 2288 >/dev/null 2>&1
  sleep 2
  tap_text "选择文件夹" || true
  sleep 3
  # 系统选择器的按钮文案随设备语言变：中文「选此文件夹」/ 英文「USE THIS FOLDER」。
  # WIPE 后设备语言会回到英文，所以两种都要试。
  picked=0
  for label in "选此文件夹" "使用此文件夹" "USE THIS FOLDER" "Use this folder"; do
    if tap_text "$label"; then picked=1; break; fi
  done
  if [ "$picked" = "0" ]; then
    # 选择器没停在目标目录：逐级进 Music/asmr 后再试一次
    for step in "Music" "asmr"; do tap_text "$step" || true; sleep 1; done
    for label in "选此文件夹" "使用此文件夹" "USE THIS FOLDER" "Use this folder"; do
      if tap_text "$label"; then picked=1; break; fi
    done
  fi
  shot "00_picker"
  echo "  选择器结果：$([ "$picked" = "1" ] && echo 已确认目录 || echo 没点到确认按钮)"
  echo "  等待扫描"
  sleep 12
  shot "00_selected"
elif [ "$MODE" = "ctrl" ]; then
  # 播放页下方操控栏的可点性（2026-11-22 用户反馈「点播放键没反应」）。
  #
  # 判据不靠播放状态（测试曲目是 echo 出来的假 mp3，解码会失败、状态会回弹），
  # 只认「点了有没有东西出来」：书签 → 书签面板；更多 → 菜单。
  # 先把下半屏的语义节点连同 bounds 打出来，方便对比「看到的」和「能点的」是否一回事。
  seed_library
  sleep 9
  "$ADB" -s "$S" shell input tap 540 2288 >/dev/null 2>&1   # 曲库 tab
  sleep 3
  "$ADB" -s "$S" shell input tap 300 900 >/dev/null 2>&1     # 第一个作品
  sleep 3
  tap_text "01_track" || true
  sleep 4
  shot "50_detail_playing"
  "$ADB" -s "$S" shell input keyevent KEYCODE_BACK >/dev/null 2>&1
  sleep 2
  # 点迷你条进播放页：按曲名点（迷你条那一格的位置随后续布局会变，别写死 y）
  tap_text_exact "01_track" || tap_text "01_track" || true
  sleep 4
  shot "51_player"
  dump_ui
  echo "  —— 播放页所有带文字的节点（重点看底部有没有重复） ——"
  python - "$OUT/ui.xml" <<'PY'
import re, sys, collections
s = open(sys.argv[1], encoding='utf-8', errors='ignore').read()
rows = []
for m in re.finditer(r'<node[^>]*>', s):
    t = m.group(0)
    txt = re.search(r'text="([^"]*)"', t)
    desc = re.search(r'content-desc="([^"]*)"', t)
    b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', t)
    label = ((txt.group(1) if txt else '') or (desc.group(1) if desc else '')).strip()
    if not b or not label:
        continue
    x1, y1, x2, y2 = (int(b.group(i)) for i in range(1, 5))
    rows.append((label, x1, y1, x2, y2, 'clickable="true"' in t))
for label, x1, y1, x2, y2, ck in sorted(rows, key=lambda r: r[2]):
    print(f"    {label:<14} [{x1},{y1}]-[{x2},{y2}] clickable={ck}")
dups = [k for k, v in collections.Counter(r[0] for r in rows).items() if v > 1]
print("  ! 重复文字：", dups if dups else "无")
PY
  tap_text "书签" || true
  sleep 2
  shot "52_after_bookmark"
  "$ADB" -s "$S" shell input keyevent KEYCODE_BACK >/dev/null 2>&1
  sleep 2
  tap_text_exact "更多" || true
  sleep 2
  shot "53_after_more"
  "$ADB" -s "$S" shell input keyevent KEYCODE_BACK >/dev/null 2>&1
  sleep 1
  # 走带那排（顺序播放 / 上一首 / 播放 / 下一首 / A-B）比书签那行更低 100px，
  # 单独验这一排：A-B 会弹菜单，是纯 UI 的判据。播放/上一首/下一首不能用状态判 ——
  # 测试曲目是 echo 出来的假 mp3，播放器本身就在 error 态，不响应也说明不了点击有没有到。
  tap_text_exact "A-B" || tap_desc_exact "A-B" || true
  sleep 2
  shot "54_after_ab"
  dump_ui
  python - "$OUT/ui.xml" <<'PY'
import sys
s = open(sys.argv[1], encoding='utf-8', errors='ignore').read()
for key in ("把 A 点设到当前位置", "清除 A-B 区间", "A-B"):
    print(f"    {key}: {'出现' if key in s else '没有'}")
PY
elif [ "$MODE" = "play" ]; then
  # 补封面图：没有封面时迷你条与播放页显示的都是占位图标，共享元素不会触发
  # 注意：这里必须在设备端展开通配符。写在宿主机 bash 里会匹配到本地路径，
  # 结果是「一条都没推上去」却没有任何报错。
  "$ADB" -s "$S" push "${COVER:?需要 COVER=封面路径}" /sdcard/cover.png >/dev/null 2>&1
  "$ADB" -s "$S" shell 'for d in /sdcard/Music/asmr/RJ*/; do cp /sdcard/cover.png "${d}cover.png"; done'
  "$ADB" -s "$S" shell ls /sdcard/Music/asmr/RJ*/cover.png
  "$ADB" -s "$S" shell am force-stop com.kiite.player >/dev/null 2>&1
  "$ADB" -s "$S" shell am start -n com.kiite.player/.MainActivity >/dev/null 2>&1
  sleep 8
  # 顶栏刷新：封面路径是扫描时缓存的，新增图片必须重扫才会被认到
  "$ADB" -s "$S" shell input tap 767 308 >/dev/null 2>&1
  sleep 8
  shot "19_after_refresh"
  # 进第二个作品，点第一首开播，再回曲库让迷你条出现
  "$ADB" -s "$S" shell input tap 300 900 >/dev/null 2>&1
  sleep 3
  tap_text "01_track" || true
  sleep 4
  shot "20_detail_playing"
  "$ADB" -s "$S" shell input tap 180 2288 >/dev/null 2>&1
  sleep 2
  shot "21_library_mini"
  # 切到播放页：点下去后 120ms 抓一帧，看封面是否处在飞行中
  "$ADB" -s "$S" shell "input tap 540 2288; sleep 0.12; screencap -p /sdcard/t1.png" >/dev/null 2>&1
  "$ADB" -s "$S" pull /sdcard/t1.png "$OUT/t1.png" >/dev/null 2>&1
  echo "  截图 t1（转场中途）"
  sleep 2
  shot "22_player"
elif [ "$MODE" = "ux" ]; then
  # 曲目卡「更多」菜单的皮肤一致性 + 左右划切页
  "$ADB" -s "$S" shell input tap 540 2288 >/dev/null 2>&1
  sleep 3
  tap_desc "更多" || true
  sleep 2
  shot "40_menu"
  "$ADB" -s "$S" shell input keyevent KEYCODE_BACK >/dev/null 2>&1
  sleep 1
  # 左划 → 下一标签（播放 → 设置）
  "$ADB" -s "$S" shell input swipe 900 1300 200 1300 280 >/dev/null 2>&1
  sleep 2
  shot "41_swipe_left"
  # 右划 → 上一标签（设置 → 播放）
  "$ADB" -s "$S" shell input swipe 200 1300 900 1300 280 >/dev/null 2>&1
  sleep 2
  shot "42_swipe_right"
  # 纵向滚动不应该被切页手势抢走
  "$ADB" -s "$S" shell input tap 180 2288 >/dev/null 2>&1
  sleep 2
  "$ADB" -s "$S" shell input swipe 540 1700 540 900 300 >/dev/null 2>&1
  sleep 2
  shot "43_vertical_scroll"
elif [ "$MODE" = "home" ]; then
  # 首页每个入口都要落到真实页面：历史页 / 统计页 / 曲库带排序 / 随机播放只出迷你条
  # 模拟器偶尔弹 System UI ANR 或更新日志，先清掉挡路的弹窗
  for _ in 1 2 3; do
    tap_text "Wait" >/dev/null 2>&1 || true
    tap_text "知道了" >/dev/null 2>&1 || true
    sleep 1
  done
  "$ADB" -s "$S" shell input tap 180 2288 >/dev/null 2>&1
  sleep 3
  shot "50_home"
  # 首页中段：目标卡的 −5 / +5 / 统计、备份卡的导出/导入（Miuix 文字按钮的最小宽度会撑宽一整行）
  "$ADB" -s "$S" shell input swipe 540 1500 540 900 300 >/dev/null 2>&1
  sleep 2
  shot "50b_home_scrolled"
  "$ADB" -s "$S" shell input swipe 540 900 540 1600 300 >/dev/null 2>&1
  sleep 2
  tap_text "最近播放" || true
  sleep 3
  shot "51_history"
  "$ADB" -s "$S" shell input keyevent KEYCODE_BACK >/dev/null 2>&1
  sleep 2
  tap_text "累计收听" || true
  sleep 3
  shot "52_stats"
  "$ADB" -s "$S" shell input keyevent KEYCODE_BACK >/dev/null 2>&1
  sleep 2
  tap_text "最近添加" || true
  sleep 5
  shot "53_library_recent"
  "$ADB" -s "$S" shell input tap 180 2288 >/dev/null 2>&1
  sleep 2
  tap_text "随机播放" || true
  sleep 5
  shot "54_shuffle_mini"
elif [ "$MODE" = "home2" ]; then
  # 首页深链：昵称/头像 → 设置·个人资料；建议封面 → 曲库作品详情
  # 模拟器偶发 ANR / 更新日志弹窗，每次操作前清一遍
  for _ in 1 2 3; do
    tap_text "Wait" >/dev/null 2>&1 || true
    tap_text "知道了" >/dev/null 2>&1 || true
    sleep 1
  done
  "$ADB" -s "$S" shell input tap 180 2288 >/dev/null 2>&1
  sleep 3
  for _ in 1 2; do tap_text "Wait" >/dev/null 2>&1 || true; done
  tap_text "欢迎，" || true
  sleep 3
  shot "60_profile"
  "$ADB" -s "$S" shell input keyevent KEYCODE_BACK >/dev/null 2>&1
  sleep 2
  "$ADB" -s "$S" shell input tap 180 2288 >/dev/null 2>&1
  sleep 3
  # 把「建议」横排带上来看清标签，再按项目名点（封面本身没有文字，只能按坐标，容易点偏）
  "$ADB" -s "$S" shell input swipe 540 1700 540 1100 280 >/dev/null 2>&1
  sleep 3
  for _ in 1 2; do tap_text "Wait" >/dev/null 2>&1 || true; done
  tap_text "测试作品3" || tap_text "测试作品6" || tap_text "测试作品1" || true
  sleep 4
  shot "61_suggestion_detail"
  # 从详情返回：必须落在曲库列表，不能被残留的「打开某部作品」意图再弹回详情
  "$ADB" -s "$S" shell input keyevent KEYCODE_BACK >/dev/null 2>&1
  sleep 3
  shot "62_back_from_detail"
elif [ "$MODE" = "biglist" ]; then
  # 验「全页大图下能不能看播放列表」：进播放页 → 点封面切到大图 → 点四格里的播放列表 → 再回台本
  "$ADB" -s "$S" shell input tap 540 2288 >/dev/null 2>&1
  sleep 3
  shot "30_player_entry"
  # 封面即布局开关：默认「大封面」档点一次进全页大图
  tap_desc "专辑封面" || true
  sleep 2
  shot "31_big_cover"
  tap_text "播放列表" || true
  sleep 2
  shot "32_big_playlist"
  tap_text "查看台本" || true
  sleep 2
  shot "33_back_to_cover"
elif [ "$MODE" = "read" ]; then
  # 台本放大：卡片形式连续放大（不是整页换一套界面），退出后回到原样
  #
  # 测试曲目本身没有台本，阅读模式入口根本不会出现。先推一份 LRC 让它有「带时间轴的台本」，
  # 再重启应用触发扫描（scanOnStart）。
  if [ "${SEED:-0}" = "1" ]; then
    echo "== 先造测试曲库"
    seed_library
    sleep 12
  fi
  TRACK=$("$ADB" -s "$S" shell 'ls /sdcard/Music/asmr/*/*.mp3 2>/dev/null | head -1' | tr -d '\r')
  if [ -n "$TRACK" ]; then
    # 曲库里的实际文件名可能与作品名无关（例如 01_本編.mp3），
    # 给每首歌都配一份同名 LRC，点哪一首都能进阅读模式。
    printf '[00:00.00]第一句：这件事要从很久以前说起\n[00:03.00]第二句：那天的雨下得很大\n[00:06.00]第三句：她把伞收起来，走进了屋檐\n[00:09.00]第四句：风吹过走廊，纸门轻轻响了一下\n[00:12.00]第五句：没有人说话，只有远处的车声\n[00:15.00]第六句：他说，先坐下吧\n[00:18.00]第七句：木地板在脚下发出细小的声音\n[00:21.00]第八句：茶倒进杯子里，热气慢慢升起来\n[00:24.00]第九句：窗外的树影在墙上晃\n[00:27.00]第十句：后来他们谁也没有再提起那一天\n' > "$OUT/test.lrc"
    "$ADB" -s "$S" push "$OUT/test.lrc" /sdcard/kiite-test.lrc >/dev/null 2>&1
    "$ADB" -s "$S" shell 'for f in /sdcard/Music/asmr/*/*.[mM][pP]3; do [ -f "$f" ] && cp /sdcard/kiite-test.lrc "${f%.*}.lrc"; done' >/dev/null 2>&1
    echo "  已给每首音频放置同名台本"
    "$ADB" -s "$S" shell am force-stop com.kiite.player >/dev/null 2>&1
    "$ADB" -s "$S" shell am start -n com.kiite.player/.MainActivity >/dev/null 2>&1
    sleep 9
  fi
  # 冷启动后没有「正在播放」：先到曲库点开第一个作品的第一首，再回播放页
  # 底部三格是 首页 / 曲库 / 设置，曲库在中间（x≈540）；点 180 会留在首页。
  "$ADB" -s "$S" shell input tap 540 2288 >/dev/null 2>&1
  sleep 3
  # 按名字点作品，别按坐标：列表行高随���皮与字号变，写死 y 迟早点空
  tap_text "深夜の耳かき喫茶" || tap_text "RJ01234567" || true
  sleep 4
  tap_text "01_track" || tap_text "01_本編" || true
  sleep 5
  # 迷你条在导航胶囊上方（y≈2060），点 2288 只会点到「曲库」标签。
  "$ADB" -s "$S" shell input tap 540 2060 >/dev/null 2>&1
  sleep 3
  shot "50_card_normal"
  # 迷你条点开是半屏折叠态，台本卡高度为 0，放大入口自然不在树上；先上滑展开。
  # 从曲目卡那一段起手（y≈1450）：从 1700 起手会正好按到「定时停止」并弹出菜单。
  "$ADB" -s "$S" shell input swipe 540 1450 540 700 300 >/dev/null 2>&1
  sleep 3
  shot "51_player_expanded"
  XY=$(trace_center "台本全屏阅读")
  if [ -z "$XY" ]; then
    echo "  ! 没找到「台本全屏阅读」：当前曲目可能没有带时间轴的台本"
  else
    echo "  台本放大按钮在 $XY"
    # 动画放慢 10 倍（220ms → 2.2s），连点带拍才抓得到放大的中间帧
    "$ADB" -s "$S" shell settings put global animator_duration_scale 10 >/dev/null 2>&1
    "$ADB" -s "$S" shell "input tap $XY; sleep 0.5; screencap -p /sdcard/r1.png; sleep 0.6; screencap -p /sdcard/r2.png; sleep 1.6; screencap -p /sdcard/r3.png" >/dev/null 2>&1
    for f in r1 r2 r3; do
      "$ADB" -s "$S" pull /sdcard/$f.png "$OUT/read_$f.png" >/dev/null 2>&1 || echo "  ! 拉回失败 $f"
    done
    "$ADB" -s "$S" shell settings put global animator_duration_scale 1 >/dev/null 2>&1
    # 进场 morph 是按「放大 10 倍时」算出的时长启动的，恢复倍率后还要等它走完，
    # 否则抓到的是卡片还没长满的中间帧（看着就像顶部留白）。
    sleep 6
    shot "53_read_full"
    # 侧滑返回退出阅读模式
    "$ADB" -s "$S" shell input keyevent KEYCODE_BACK >/dev/null 2>&1
    sleep 2
    shot "54_read_exit"
  fi
elif [ "$MODE" = "transition" ]; then
  # 共享元素飞行检查：先确保当前档位有曲目卡（全页大图那档没有共享封面），
  # 再把动画调慢 10 倍（220ms → 2.2s），才抓得到中途那几帧。
  "$ADB" -s "$S" shell input tap 540 2288 >/dev/null 2>&1
  sleep 3
  tap_desc "专辑封面" || true   # 封面即布局开关：大图档点一次回到紧凑档
  sleep 2
  shot "t0_player_layout"
  "$ADB" -s "$S" shell input tap 180 2288 >/dev/null 2>&1
  sleep 2
  shot "t0_library"
  "$ADB" -s "$S" shell settings put global animator_duration_scale 10 >/dev/null 2>&1
  # 一次 adb shell 里连拍：分多次 adb 调用的话，间隔抖动比动画本身还大。
  # f0 抓的是交接口（旧页淡出、新页还没起跑）—— 2.5.5-beta9 之前的交叉淡变就是在
  # 这一帧暴露的：两页同时半透明，灰底卡片叠成一片。先看 f0 再看后面几帧。
  "$ADB" -s "$S" shell 'input tap 540 2288; sleep 0.18; screencap -p /sdcard/f0.png; sleep 0.37; screencap -p /sdcard/f1.png; sleep 0.5; screencap -p /sdcard/f2.png; sleep 0.55; screencap -p /sdcard/f3.png; sleep 0.9; screencap -p /sdcard/f4.png' >/dev/null 2>&1
  for f in f0 f1 f2 f3 f4; do
    "$ADB" -s "$S" pull /sdcard/$f.png "$OUT/mid_$f.png" >/dev/null 2>&1 \
      || echo "  ! 拉回失败 $f"
  done
  echo "  截图 mid_f0 … mid_f4（转场中途）"
  sleep 1
  shot "t3_player"
  # 回程单独抓：封面从播放页飞回迷你条、曲库淡回来，是另一条时间线
  # （进播放页时迷你条在退场，回曲库时它在进场，灰底最容易在这一侧露出来）。
  "$ADB" -s "$S" shell 'input tap 180 2288; sleep 0.2; screencap -p /sdcard/b0.png; sleep 0.45; screencap -p /sdcard/b1.png; sleep 0.6; screencap -p /sdcard/b2.png' >/dev/null 2>&1
  for f in b0 b1 b2; do
    "$ADB" -s "$S" pull /sdcard/$f.png "$OUT/back_$f.png" >/dev/null 2>&1 \
      || echo "  ! 拉回失败 $f"
  done
  echo "  截图 back_b0 … back_b2（回程中途）"
  sleep 1
  shot "t4_back_to_library"
  "$ADB" -s "$S" shell settings put global animator_duration_scale 1 >/dev/null 2>&1
elif [ "$MODE" = "onboarding" ]; then
  # 首启引导（配合 WIPE=1 跑）：5 页功能说明 → 条款页（勾选同意）→ 重启不再出现。
  # 判据：90 有标题与「跳过」；92 最后一页按钮是「继续」；
  #       93 条款页（使用须知 + 免责声明，按钮默认禁用）；94 勾选后；
  #       95 落地主页；96 冷启动后没有任何引导（说明 seen + version 已落盘）。
  sleep 3
  shot "90_guide_p1"
  tap_text "下一页" || true
  sleep 2
  shot "91_guide_p2"
  tap_text "下一页" || true
  sleep 2
  tap_text "下一页" || true
  sleep 2
  tap_text "下一页" || true
  sleep 2
  shot "92_guide_last"
  tap_text "继续" || true
  sleep 2
  shot "93_legal_top"
  # 条款正文要能滚（内容很长，不能只在第一屏自说自话）
  "$ADB" -s "$S" shell input swipe 540 1700 540 900 300 >/dev/null 2>&1
  sleep 1
  shot "93b_legal_scrolled"
  # 未勾选时按钮是禁用的，先勾上
  tap_text "我已阅读并同意" || true
  sleep 1
  shot "94_legal_agreed"
  tap_text "同意并开始使用" || true
  sleep 3
  shot "95_after_guide"
  # 冷启动：onboarding_seen 写盘之后不该再出现
  "$ADB" -s "$S" shell am force-stop com.kiite.player >/dev/null 2>&1
  sleep 2
  "$ADB" -s "$S" shell am start -n com.kiite.player/.MainActivity >/dev/null 2>&1
  sleep 6
  shot "96_relaunch_no_guide"
elif [ "$MODE" = "polish" ]; then
  # 本轮首页打磨的四件事：
  #   1) 首页两个在线入口落到真实云端搜索页（来源已预选）；
  #   2) 首页二级页的进出场（慢放抓中间帧）；
  #   3) 设置里的使用须知 / 免责声明能读、能返回；
  #   4) 迷你条按压前后磨砂一致（同一位置按住不放再截图）。
  # 前置：曲库里已经有条目（先跑 setup）。
  "$ADB" -s "$S" shell input tap 180 2288 >/dev/null 2>&1
  sleep 3
  shot "a0_home"
  tap_text "DLsite" || true
  sleep 4
  shot "a1_from_dlsite"
  # 搜索框进入即聚焦，键盘在场时第一下 BACK 只收键盘（系统行为，不是页面没关）
  "$ADB" -s "$S" shell input keyevent KEYCODE_BACK >/dev/null 2>&1
  sleep 1
  "$ADB" -s "$S" shell input keyevent KEYCODE_BACK >/dev/null 2>&1
  sleep 3
  # 搜索页关掉后停在曲库标签，回首页再点第二个入口
  "$ADB" -s "$S" shell input tap 180 2288 >/dev/null 2>&1
  sleep 3
  tap_text "asmr.one" || true
  sleep 4
  shot "a2_from_asmrone"
  "$ADB" -s "$S" shell input keyevent KEYCODE_BACK >/dev/null 2>&1
  sleep 1
  "$ADB" -s "$S" shell input keyevent KEYCODE_BACK >/dev/null 2>&1
  sleep 3
  "$ADB" -s "$S" shell input tap 180 2288 >/dev/null 2>&1
  sleep 2
  # 二级页转场：慢放 10 倍，连拍进 / 退各两帧
  QT=$(text_center "最近播放")
  if [ -z "$QT" ]; then
    echo "  ! 首页没找到「最近播放」，跳过转场连拍"
  else
    "$ADB" -s "$S" shell settings put global animator_duration_scale 10 >/dev/null 2>&1
    "$ADB" -s "$S" shell "input tap $QT; sleep 0.2; screencap -p /sdcard/h0.png; sleep 0.5; screencap -p /sdcard/h1.png" >/dev/null 2>&1
    for f in h0 h1; do
      "$ADB" -s "$S" pull /sdcard/$f.png "$OUT/sub_$f.png" >/dev/null 2>&1 || echo "  ! 拉回失败 $f"
    done
    echo "  截图 sub_h0 / sub_h1（进二级页中途）"
    sleep 2
    "$ADB" -s "$S" shell 'input keyevent KEYCODE_BACK; sleep 0.2; screencap -p /sdcard/h2.png; sleep 0.5; screencap -p /sdcard/h3.png' >/dev/null 2>&1
    for f in h2 h3; do
      "$ADB" -s "$S" pull /sdcard/$f.png "$OUT/sub_$f.png" >/dev/null 2>&1 || echo "  ! 拉回失败 $f"
    done
    echo "  截图 sub_h2 / sub_h3（退出二级页中途）"
    "$ADB" -s "$S" shell settings put global animator_duration_scale 1 >/dev/null 2>&1
  fi
  sleep 2
  # 迷你条按压：先放一首，让迷你条在场
  "$ADB" -s "$S" shell input tap 540 2288 >/dev/null 2>&1
  sleep 2
  tap_text "RJ01234567" || tap_text "Music" || true
  sleep 3
  tap_text "01_track" || true
  sleep 5
  "$ADB" -s "$S" shell input tap 180 2288 >/dev/null 2>&1
  sleep 3
  shot "a6_mini_idle"
  # 同一位置按住不放（input swipe 起终点相同 = 长按）再截一张，对比磨砂有没有变实。
  # 落点按迷你条的副标题（目录名）找：它在首页只出现在迷你条里。
  MB=$(text_center "RJ01234567_")
  if [ -n "$MB" ]; then
    "$ADB" -s "$S" shell "(input swipe $MB $MB 1600 &) ; sleep 0.7; screencap -p /sdcard/press.png" >/dev/null 2>&1
    "$ADB" -s "$S" pull /sdcard/press.png "$OUT/a7_mini_pressed.png" >/dev/null 2>&1 || echo "  ! 拉回失败 press"
    echo "  截图 a7_mini_pressed（按住迷你条，落点 $MB）"
  else
    echo "  ! 没找到迷你条副标题，跳过按压截图"
  fi
elif [ "$MODE" = "legal" ]; then
  # 设置里的「关于与法律」：两条正文能读、能返回；「关于 → 功能介绍」能重看引导。
  # 注意：从子页返回时设置主页是重新组合的，滚动位置会回到顶部 ——
  # 所以每找一个更靠下的条目都要重新滑一次。
  tap_text "设置" || "$ADB" -s "$S" shell input tap 897 2288 >/dev/null 2>&1
  sleep 3
  "$ADB" -s "$S" shell input swipe 540 1700 540 900 300 >/dev/null 2>&1
  sleep 2
  tap_text_exact "使用须知" || true
  sleep 3
  shot "b0_terms"
  "$ADB" -s "$S" shell input swipe 540 1800 540 900 300 >/dev/null 2>&1
  sleep 2
  "$ADB" -s "$S" shell input keyevent KEYCODE_BACK >/dev/null 2>&1
  sleep 3
  "$ADB" -s "$S" shell input swipe 540 1800 540 800 300 >/dev/null 2>&1
  sleep 2
  tap_text_exact "免责声明" || true
  sleep 3
  shot "b1_disclaimer"
  "$ADB" -s "$S" shell input keyevent KEYCODE_BACK >/dev/null 2>&1
  sleep 3
  "$ADB" -s "$S" shell input swipe 540 1900 540 700 300 >/dev/null 2>&1
  sleep 2
  "$ADB" -s "$S" shell input swipe 540 1900 540 700 300 >/dev/null 2>&1
  sleep 2
  shot "b2_settings_bottom"
  # 清理缓存：确认框 → 清理 → 提示条。验的是「清完之后应用还能用」（封面会重新生成），
  # 以及提示里说清楚没动用户数据。
  if tap_text_exact "清理缓存"; then
    sleep 2
    shot "b2b_cache_dialog"
    tap_text_exact "清理" || true
    sleep 3
    shot "b2c_cache_cleared"
  fi
  tap_text_exact "关于" || true
  sleep 3
  tap_text "功能介绍" || true
  sleep 3
  shot "b3_replay_guide"
  # 重看也是完整流程：功能页 → 条款页；注意这里不写盘（不该把 version 改回去）
  dismiss_onboarding
  sleep 2
  shot "b4_after_replay"
elif [ "$MODE" = "skin" ]; then
  # 「经典（旧版）」皮肤下的底栏验收：导航胶囊 + 迷你播放条要看得见边界。
  # 前置：已经跑过 setup（曲库里有条目、根目录已选）。
  #
  # 经典皮肤没有玻璃也没有磨砂，栏面是一块不透明 surface；浅色下它和页面底色
  # 几乎同色，所以判据是**有没有一道细描边**把胶囊与迷你条分出来。
  "$ADB" -s "$S" shell input tap 897 2288 >/dev/null 2>&1
  sleep 3
  tap_text "外观与皮肤" || true
  sleep 3
  shot "k0_appearance"
  tap_text "经典（旧版）" || true
  sleep 3
  shot "k1_classic_settings"
  # 子页返回 → 设置主页 → 首页
  "$ADB" -s "$S" shell input keyevent KEYCODE_BACK >/dev/null 2>&1
  sleep 2
  "$ADB" -s "$S" shell input tap 180 2288 >/dev/null 2>&1
  sleep 3
  shot "k2_classic_home"
  # 曲库 → 点一首 → 返回：制造迷你条，看它在经典皮肤下的底色与描边
  "$ADB" -s "$S" shell input tap 540 2288 >/dev/null 2>&1
  sleep 3
  tap_text "Music" || tap_text "RJ01234567" || true
  sleep 3
  tap_text "01_track" || true
  sleep 5
  shot "k3_classic_detail"
  "$ADB" -s "$S" shell input keyevent KEYCODE_BACK >/dev/null 2>&1
  sleep 3
  shot "k4_classic_mini"
  # 回归：切回 iOS 玻璃（默认）再看一眼同一条迷你条。
  # 模拟器上「实时毛玻璃」被强制关掉（软件 GL 跑不动 Haze），所以这里看到的是
  # asmrBarColor + glassEdge 的兜底形态，与经典皮肤的差别就是「描边的语言」。
  "$ADB" -s "$S" shell input tap 897 2288 >/dev/null 2>&1
  sleep 3
  tap_text "外观与皮肤" || true
  sleep 3
  tap_text "iOS 玻璃（新）" || true
  sleep 3
  shot "k5_editorial_settings"
  "$ADB" -s "$S" shell input keyevent KEYCODE_BACK >/dev/null 2>&1
  sleep 2
  "$ADB" -s "$S" shell input tap 540 2288 >/dev/null 2>&1
  sleep 3
  shot "k6_editorial_mini"
elif [ "$MODE" = "batch" ]; then
  # 批量模式：横幅要在悬浮顶栏下方完整可见（退出/已选/全选那一行不能被磨砂条压住），
  # 且横幅里要有「删除」入口。前置：曲库已有条目（先跑 setup）。
  # 按坐标点底栏中间的「曲库」（tap_text「曲库」会先命中设置里的「曲库与扫描」）
  "$ADB" -s "$S" shell input tap 540 2288 >/dev/null 2>&1
  sleep 3
  XY=$(text_center "章 ·")
  [ -z "$XY" ] && XY=$(text_center "RJ01234567")
  if [ -z "$XY" ]; then
    echo "  ! 曲库里没找到作品卡（先跑 setup）"
  else
    echo "  长按作品进入批量（$XY）"
    "$ADB" -s "$S" shell input swipe $XY $XY 900 >/dev/null 2>&1
    sleep 3
    shot "c0_batch_top"
    # 上滑让顶栏淡入成磨砂条：以前这一下正好把横幅第一行压掉
    "$ADB" -s "$S" shell input swipe 540 1400 540 1000 300 >/dev/null 2>&1
    sleep 3
    shot "c1_batch_scrolled"
    tap_text_exact "全选" || true
    sleep 2
    shot "c2_batch_all"
    tap_text_exact "删除" || true
    sleep 3
    shot "c3_delete_confirm"
    # 勾上「同时删除本地文件」看红色警示，再取消（不真删，曲库留给后续验证）
    tap_text "同时删除本地文件" || true
    sleep 2
    shot "c4_delete_with_files"
    tap_text_exact "取消" || true
    sleep 2
    tap_text "退出批量" || true
    sleep 2
    shot "c5_after_exit"
  fi
elif [ "$MODE" = "player" ]; then
  # 播放页卡片材质（MiuixCard 化之后）：先放一首，再从迷你条点进播放页。
  # 前置：曲库有条目（先跑 setup）。
  "$ADB" -s "$S" shell input tap 540 2288 >/dev/null 2>&1
  sleep 3
  tap_text "Music" || tap_text "RJ01234567" || true
  sleep 3
  tap_text "01_track" || true
  sleep 6
  shot "p0_detail"
  # 迷你条在底部（约 y=2100），点它的标题区打开播放页
  "$ADB" -s "$S" shell input tap 400 2100 >/dev/null 2>&1
  sleep 4
  shot "p1_player"
  "$ADB" -s "$S" shell input swipe 540 1500 540 700 300 >/dev/null 2>&1
  sleep 3
  shot "p2_player_up"
  "$ADB" -s "$S" shell input keyevent KEYCODE_BACK >/dev/null 2>&1
  sleep 3
  shot "p3_back"
elif [ "$MODE" = "style" ]; then
  # 曲库 + 设置两页的「按钮层」验收：列表、批量条、删除确认框、设置主页、条款页。
  "$ADB" -s "$S" shell input tap 540 2288 >/dev/null 2>&1
  sleep 3
  shot "s0_library"
  XY=$(text_center "章 ·")
  [ -z "$XY" ] && XY=$(text_center "RJ01234567")
  if [ -n "$XY" ]; then
    "$ADB" -s "$S" shell input swipe $XY $XY 900 >/dev/null 2>&1
    sleep 3
    shot "s1_batch"
    tap_text_exact "全选" || true
    sleep 2
    tap_text_exact "删除" || true
    sleep 3
    shot "s2_delete_dialog"
    tap_text_exact "取消" || true
    sleep 2
    tap_text "退出批量" || true
    sleep 2
  else
    echo "  ! 曲库没有作品卡，跳过批量截图"
  fi
  "$ADB" -s "$S" shell input tap 897 2288 >/dev/null 2>&1
  sleep 3
  shot "s3_settings"
  "$ADB" -s "$S" shell input swipe 540 1800 540 900 300 >/dev/null 2>&1
  sleep 2
  tap_text_exact "使用须知" || true
  sleep 3
  shot "s4_terms"
elif [ "$MODE" = "zoom" ]; then
  # 曲库点作品的放大转场（2.5.5-beta4）：慢放 10 倍，进 / 退各抓中间帧。
  # 判据：z1/z2 里详情页应比整屏小一圈、并且是以被点的卡片为中心放大的；
  # 退场 zb1/zb2 应缩回同一个方向，绝不能是硬切。
  "$ADB" -s "$S" shell input tap 180 2288 >/dev/null 2>&1   # 回曲库标签
  sleep 3
  shot "70_library"
  # 作品卡的摘要行统一是「N 章 · M 首 · K 份台本」，按它找最稳；
  # 「已购未下载」那一行不是作品卡（点了没反应），别优先命中它。
  XY=$(text_center "章 ·")
  [ -z "$XY" ] && XY=$(text_center "RJ01234567")
  [ -z "$XY" ] && XY=$(text_center "深夜の耳かき喫茶")
  if [ -z "$XY" ]; then
    echo "  ! 曲库里没找到作品卡（先跑 setup 造曲库）"
  else
    echo "  作品卡在 $XY"
    # 放慢 60 倍（220ms → 13s）：缩放只在前 1/4 里走完，放 10 倍时 0.4 秒就已经到 95%，
    # 抓不到「明显小一圈」的那一帧；要判断原点对不对，必须把开头冻住看。
    "$ADB" -s "$S" shell settings put global animator_duration_scale 60 >/dev/null 2>&1
    "$ADB" -s "$S" shell "input tap $XY; sleep 0.3; screencap -p /sdcard/z1.png; sleep 0.5; screencap -p /sdcard/z2.png; sleep 1.2; screencap -p /sdcard/z3.png" >/dev/null 2>&1
    for f in z1 z2 z3; do
      "$ADB" -s "$S" pull /sdcard/$f.png "$OUT/zoom_$f.png" >/dev/null 2>&1 || echo "  ! 拉回失败 $f"
    done
    echo "  截图 zoom_z1 … zoom_z3（放大中途）"
    sleep 2
    shot "71_detail"
    # 系统返回键：既验「详情页能被返回键退出」，也抓缩回去的中间帧
    "$ADB" -s "$S" shell "input keyevent KEYCODE_BACK; sleep 0.4; screencap -p /sdcard/zb1.png; sleep 0.6; screencap -p /sdcard/zb2.png" >/dev/null 2>&1
    for f in zb1 zb2; do
      "$ADB" -s "$S" pull /sdcard/$f.png "$OUT/zoom_$f.png" >/dev/null 2>&1 || echo "  ! 拉回失败 $f"
    done
    echo "  截图 zoom_zb1 / zoom_zb2（缩回中途）"
    "$ADB" -s "$S" shell settings put global animator_duration_scale 1 >/dev/null 2>&1
    sleep 2
    shot "72_back_to_list"
  fi
elif [ "$MODE" = "bottom" ]; then
  # 底部两条：走带有没有被导航胶囊压住 / 是否左右对称；迷你条进出场的中间帧。
  # 需要曲库已有条目（先跑 setup）且能点进作品。
  tap_text "Music" || tap_text "RJ01234567" || true
  sleep 3
  tap_text "01_track" || true
  sleep 4
  shot "60_detail"
  # 回曲库：迷你条在场，这是「阴影该在」的那一帧
  "$ADB" -s "$S" shell input tap 180 2288 >/dev/null 2>&1
  sleep 2
  shot "61_library_mini"
  "$ADB" -s "$S" shell settings put global animator_duration_scale 10 >/dev/null 2>&1
  # 一次 shell 里连拍：跨 adb 调用的间隔抖动比动画本身还大
  "$ADB" -s "$S" shell 'input tap 540 2288; sleep 0.4; screencap -p /sdcard/b1.png; sleep 0.5; screencap -p /sdcard/b2.png; sleep 0.6; screencap -p /sdcard/b3.png' >/dev/null 2>&1
  for f in b1 b2 b3; do
    "$ADB" -s "$S" pull /sdcard/$f.png "$OUT/out_$f.png" >/dev/null 2>&1 || echo "  ! 拉回失败 $f"
  done
  echo "  截图 out_b1 … out_b3（迷你条收起 + 切页中途）"
  "$ADB" -s "$S" shell settings put global animator_duration_scale 1 >/dev/null 2>&1
  sleep 3
  shot "62_player"
elif [ "$MODE" = "sheet" ]; then
  # 播放页两档：折叠（沉浸封面 + 底部卡片）/ 展开（完整布局）。
  # 判据：
  #   p1 折叠：只有全屏封面 + 一张压低的卡片，App 顶栏与导航胶囊都不在；
  #   p2 上滑：完整页面（曲目卡 + 四格 + 台本 + 走带），导航胶囊回来；
  #   p3 下滑：回到折叠；
  #   p5 记忆：展开 → 切到曲库 → 回来，仍是展开。
  # 拖动只挂在卡片头部（曲目卡/抓手那一带），所以滑动起点要落在卡片上半部。
  # 启动即扫描：曲库还在扫时点标签会被「正在扫描」那层盖住，白屏一样，
  # 这里等扫描收尾再动（扫描条消失前点播放，p1 拍到的会是曲库）。
  sleep 12
  shot "p0_before"
  "$ADB" -s "$S" shell input tap 540 2288 >/dev/null 2>&1     # 播放标签
  sleep 5
  shot "p1_collapsed"
  "$ADB" -s "$S" shell input swipe 540 1550 540 700 300 >/dev/null 2>&1
  sleep 3
  shot "p2_expanded"
  "$ADB" -s "$S" shell input swipe 540 500 540 1700 300 >/dev/null 2>&1
  sleep 3
  shot "p3_collapsed_again"
  # 用返回键展开一次（展开 → 返回应该先收卡片而不是回曲库）
  "$ADB" -s "$S" shell input swipe 540 1550 540 700 300 >/dev/null 2>&1
  sleep 3
  "$ADB" -s "$S" shell input keyevent KEYCODE_BACK >/dev/null 2>&1
  sleep 3
  shot "p4_back_collapses"
  # 记忆：展开 → 切曲库 → 回播放
  "$ADB" -s "$S" shell input swipe 540 1550 540 700 300 >/dev/null 2>&1
  sleep 3
  "$ADB" -s "$S" shell input tap 180 2288 >/dev/null 2>&1
  sleep 3
  shot "p5_library"
  "$ADB" -s "$S" shell input tap 540 2288 >/dev/null 2>&1
  sleep 4
  shot "p6_back_expanded"
  # snackbar（调试/提示条）会盖住底部走带，等它自己消失再拍一张，才看得清布局
  sleep 5
  shot "p7_settled"
  # 可打断：展开动画跑到一半按返回，卡片要从当前高度掉头，不能跳变也不能僵住。
  # 慢放 10 倍（220ms → 2.2s）才抓得到中途。
  "$ADB" -s "$S" shell settings put global animator_duration_scale 10 >/dev/null 2>&1
  "$ADB" -s "$S" shell 'input swipe 540 1550 540 700 300; sleep 0.7; input keyevent KEYCODE_BACK; sleep 0.5; screencap -p /sdcard/i1.png; sleep 0.8; screencap -p /sdcard/i2.png' >/dev/null 2>&1
  for f in i1 i2; do
    "$ADB" -s "$S" pull /sdcard/$f.png "$OUT/interrupt_$f.png" >/dev/null 2>&1 || echo "  ! 拉回失败 $f"
  done
  echo "  截图 interrupt_i1 / i2（半途反向）"
  "$ADB" -s "$S" shell settings put global animator_duration_scale 1 >/dev/null 2>&1
  sleep 2
  shot "p8_after_interrupt"
elif [ "$MODE" = "topbar" ]; then
  # 大标题 + 滚动虚化顶栏 + 「更多」底部抽屉。
  # 判据：停在顶部时顶栏完全透明（只留右上角主题开关），内容里是大标题；
  #      推一下之后磨砂条淡入、大标题滚走；「更多」是底部抽屉而不是下拉菜单。
  shot "t1_library_top"
  "$ADB" -s "$S" shell input swipe 540 900 540 400 300 >/dev/null 2>&1
  sleep 2
  shot "t2_library_scrolled"
  tap_text "设置" || true
  sleep 3
  shot "t3_settings_top"
  "$ADB" -s "$S" shell input swipe 540 900 540 500 300 >/dev/null 2>&1
  sleep 2
  shot "t4_settings_scrolled"
  # 播放页：从底栏进（坐标比文字稳：设置页里也有「播放与列表」之类同名词）
  "$ADB" -s "$S" shell input tap 540 2288 >/dev/null 2>&1
  sleep 4
  shot "t5_player"
  "$ADB" -s "$S" shell input swipe 540 1550 540 700 300 >/dev/null 2>&1
  sleep 3
  tap_desc "更多" || true
  sleep 3
  shot "t6_more_sheet"
elif [ "$MODE" = "dlsitedl" ]; then
  # DLsite 下载改走应用内后要确认的两件事：
  #  1. 设置页 → DLsite 有「下载」入口（不再只有跳网页）；
  #  2. 曲库主页「已购未下载」点「下载」，进的是应用内下载页，等不到地址时给得出可读原因、
  #     且不会停在黑屏上（模拟器连不上 dlsite.com，这里必然走到失败态，正好看文案）。
  #
  # 购买记录是登录后才有的，这里直接写一份到应用私有目录（debug 包可 run-as）：
  # 曲库主页据此出现「已购未下载 1 部」和「下载」按钮。
  # 用 base64 从宿主直接灌进 data 目录：先推到 /sdcard 再 cp 会被应用的存储沙箱挡住。
  PURCHASES_B64=$(printf '[{"code":"RJ01234567","title":"深夜の耳かき喫茶","asmr":true}]' | base64 -w0)
  # 顺手把这个作品的元数据也写进缓存：否则启动会去补抓封面，弹一层「DLsite 元数据 1/1」
  # 的遮罩，把后面要点的「下载」挡住（模拟器连不上 dlsite.com，这层遮罩要等它超时才消失）。
  WORKS_B64=$(printf '[{"code":"RJ01234567","title":"深夜の耳かき喫茶"}]' | base64 -w0)
  "$ADB" -s "$S" shell am force-stop com.kiite.player >/dev/null 2>&1
  "$ADB" -s "$S" shell "run-as com.kiite.player sh -c 'echo $PURCHASES_B64 | base64 -d > files/dlsite-purchases.json'" 2>&1 | tail -2
  "$ADB" -s "$S" shell "run-as com.kiite.player sh -c 'echo $WORKS_B64 | base64 -d > files/dlsite-works.json'" 2>&1 | tail -2
  "$ADB" -s "$S" shell run-as com.kiite.player cat files/dlsite-purchases.json
  "$ADB" -s "$S" shell am start -n com.kiite.player/.MainActivity >/dev/null 2>&1
  sleep 7
  shot "70_library_pending"
  tap_text_exact "下载" || true
  sleep 2
  shot "71_download_start"
  # 模拟器连不上 dlsite.com：等它自己超时落到失败态，看文案与两条出路
  wait_text "重试" 70 || true
  shot "72_download_failed"
  tap_text_exact "在网页里打开" || true
  sleep 8
  shot "73_web_fallback"
  # 回主页面用标签而不是 BACK：二级页若没打开，BACK 会直接退出应用（验证脚本自己坑过）
  "$ADB" -s "$S" shell input keyevent KEYCODE_BACK >/dev/null 2>&1
  sleep 2
  tap_text "曲库" || true
  sleep 2
  tap_text "设置" || true
  sleep 2
  shot "74_settings"
  # DLsite 已并入「个人资料」：这里直接进子页看账号与下载区块
  tap_text_exact "个人资料" || true
  sleep 3
  shot "75_dlsite_page"
elif [ "$MODE" = "ldiag" ]; then
  # 桌面歌词现场诊断：
  #   A) 单击 → 控制条出现（触摸链路通不通）；
  #   B) 双击 → 控制条**不**出现（说明 GestureDetector 认出了双击，走了「回 App」那条分支）；
  #   C) 把 theme_mode 改成 dark 再启动 → 浮窗应该是深底浅字。
  #
  # 为什么双击只能这样验：把 App 退到后台（桌面）后，这套镜像会把浮窗窗口的触摸
  # 丢掉（前景正常、后台收不到），「双击把 App 拉回来」没法在模拟器上完整走一遍。
  sleep 8
  PB=/data/user/0/com.kiite.player/files/datastore/asmr_settings.preferences_pb
  echo "  起始 show：$(lyric_show)"
  FI=$(lyric_frame_input)
  echo "  屏幕 frame：$FI"
  if [ -n "$FI" ]; then
    set -- $FI
    CX=$(( ($1 + $3) / 2 )); CY=$(( ($2 + $4) / 2 ))
    echo "  A) 单击一次（$CX,$CY）"
    "$ADB" -s "$S" shell input tap $CX $CY >/dev/null 2>&1
    sleep 2
    shot "d1_single_tap"
    echo "  等控制条自动收起（6 秒）"
    sleep 8
    echo "  B) 同一个 shell 里连点两下 = 双击"
    "$ADB" -s "$S" shell "input tap $CX $CY; input tap $CX $CY" >/dev/null 2>&1
    sleep 3
    shot "d2_double_tap"
  fi
  # C) 直接改 theme_mode（DataStore 的 protobuf：Value { string = 5 } → tag 0x2A）
  "$ADB" -s "$S" shell am force-stop com.kiite.player >/dev/null 2>&1
  sleep 2
  "$ADB" -s "$S" shell run-as com.kiite.player cat $PB > "$OUT/prefs_old.pb" 2>/dev/null
  python - "$OUT/prefs_old.pb" "$OUT/prefs_new.pb" <<'PYEOF'
import sys

def varint(n):
    out = b''
    while True:
        b = n & 0x7F
        n >>= 7
        out += bytes([b | 0x80]) if n else bytes([b])
        if not n:
            return out

def entry_str(key, s):
    v = s.encode()
    value = b'\x2a' + varint(len(v)) + v          # Value { string = 5 }
    e = b'\x0a' + varint(len(key)) + key + b'\x12' + varint(len(value)) + value
    return b'\x0a' + varint(len(e)) + e

old = open(sys.argv[1], 'rb').read()
open(sys.argv[2], 'wb').write(old + entry_str(b'theme_mode', 'dark'))
print("  prefs %d → %d 字节（追加 theme_mode=dark）" % (len(old), len(old) + len(entry_str(b'theme_mode', 'dark'))))
PYEOF
  # 重定向要写在 run-as 里面，否则是 shell(uid 2000) 去写应用私有文件 → Permission denied
  "$ADB" -s "$S" shell "run-as com.kiite.player sh -c 'cat > $PB'" < "$OUT/prefs_new.pb"
  echo "  写回后大小：$("$ADB" -s "$S" shell run-as com.kiite.player wc -c $PB | tr -d '\r')"
  "$ADB" -s "$S" shell am start -n com.kiite.player/.MainActivity >/dev/null 2>&1
  sleep 10
  echo "  show：$(lyric_show)"
  shot "d3_dark"
elif [ "$MODE" = "lyric" ]; then
  # 桌面歌词（台本悬浮窗）：
  #   1) 开启后是独立窗口，切页 / 回桌面都还在；
  #   2) 顶部小标 = 曲名、右侧 = 还剩几秒、底部 = 细进度条（看截图）；
  #   3) 拖动 → 松手贴左边 + 位置落盘；杀进程重开回到拖动后的位置；
  #   4) 浅色→深色浮窗跟着换底色；双击回 App。
  #
  # 判据：位置一律读日志（show / settle 各打一行）、触摸坐标一律读 `dumpsys input`
  # 里的屏幕 frame。两个坑都踩过：uiautomator 的界面树里没有浮窗；LayoutParams.y 又
  # 不等于屏幕坐标（差一个状态栏高度），拿它去点会点在窗口上沿外面，事件穿透到下面。
  #
  # 前置：先跑过 setup（曲库目录已选）。开关在 设置 → 台本与音频 → 台本悬浮窗 一节。
  seed_library
  sleep 10
  "$ADB" -s "$S" shell input tap 897 2288 >/dev/null 2>&1   # 设置
  sleep 3
  tap_text "台本与音频" || true
  sleep 3
  # 这一节在页面下半部，先推两下把它带进视野
  "$ADB" -s "$S" shell input swipe 540 1700 540 900 300 >/dev/null 2>&1
  sleep 1
  "$ADB" -s "$S" shell input swipe 540 1700 540 900 300 >/dev/null 2>&1
  sleep 2
  # 已经开着就别再点（再点一次等于关掉）
  if [ -z "$(lyric_show)" ]; then
    tap_text "在桌面显示台本悬浮窗" || true
    sleep 3
  fi
  shot "l0_switch"
  "$ADB" -s "$S" shell input tap 180 2288 >/dev/null 2>&1   # 回首页
  sleep 4
  echo "  起始 show：$(lyric_show)"
  FI=$(lyric_frame_input)
  if [ -z "$FI" ]; then
    echo "  ! 找不到浮窗窗口：开关 / 权限 / hasMedia 至少一个没到位"
  else
    echo "  起始屏幕 frame：$FI"
    set -- $FI
    CX=$(( ($1 + $3) / 2 )); CY=$(( ($2 + $4) / 2 ))
    # 拖到左下角：松手应该贴左边（SNAP_DP 以内直接吸到 x=0）
    "$ADB" -s "$S" shell input swipe $CX $CY 40 2100 700 >/dev/null 2>&1
    sleep 3
    echo "  拖动后 settle：$(lyric_settle)"
    echo "  拖动后 frame：$(lyric_frame_input)"
    shot "l2_dragged"
    # 回桌面：浮窗是独立窗口，App 退到后台它仍然在
    "$ADB" -s "$S" shell input keyevent KEYCODE_HOME >/dev/null 2>&1
    sleep 3
    shot "l3_over_launcher"
    # 双击回 App：两次点之间只有几百毫秒，用 motionevent 分开送
    # （input tap 每次起一个进程，慢到超过双击窗口 300ms）
    FI2=$(lyric_frame_input)
    [ -z "$FI2" ] && FI2=$FI
    set -- $FI2
    CX=$(( ($1 + $3) / 2 )); CY=$(( ($2 + $4) / 2 ))
    "$ADB" -s "$S" shell "input motionevent DOWN $CX $CY; input motionevent UP $CX $CY; input motionevent DOWN $CX $CY; input motionevent UP $CX $CY" >/dev/null 2>&1
    sleep 3
    echo "  双击后焦点：$(focus_line)"
    shot "l4_after_double_tap"
    # 双击没回来就点一下浮窗展开控制条，把「回 App / 缩为球 / 关闭」拍下来
    if ! focus_line | grep -q com.kiite.player; then
      "$ADB" -s "$S" shell input tap $CX $CY >/dev/null 2>&1
      sleep 2
      shot "l4b_controls"
    fi
    # 主题：设置页停在上次滚到的位置，先滚回顶部再点「外观与皮肤」
    "$ADB" -s "$S" shell am start -n com.kiite.player/.MainActivity >/dev/null 2>&1
    sleep 3
    "$ADB" -s "$S" shell input tap 897 2288 >/dev/null 2>&1
    sleep 3
    for _ in 1 2 3; do
      "$ADB" -s "$S" shell input swipe 540 500 540 2000 200 >/dev/null 2>&1
      sleep 1
    done
    tap_text "外观与皮肤" || true
    sleep 3
    tap_text "深色" || true
    sleep 3
    "$ADB" -s "$S" shell input keyevent KEYCODE_BACK >/dev/null 2>&1
    sleep 2
    "$ADB" -s "$S" shell input tap 180 2288 >/dev/null 2>&1
    sleep 3
    shot "l6_dark_theme"
    # 位置持久化：杀进程重开，浮窗应回到拖动后的位置（不是默认的 dp 24,160）
    "$ADB" -s "$S" shell am force-stop com.kiite.player >/dev/null 2>&1
    sleep 2
    "$ADB" -s "$S" shell am start -n com.kiite.player/.MainActivity >/dev/null 2>&1
    sleep 10
    echo "  重启后 show：$(lyric_show)  ← 应与上面的 settle 一致"
    echo "  重启后 frame：$(lyric_frame_input)"
    shot "l7_after_restart"
    echo "== ANR 检查（模拟器慢，偶尔弹「无响应」；有就是我们的锅） =="
    "$ADB" -s "$S" shell logcat -d 2>/dev/null | tr -d '\r' \
      | grep -E "ANR in com.kiite.player|Reason:" | tail -6
  fi
elif [ "$MODE" = "features4" ]; then
  # 本轮四项改动的界面验收：设置项合并、解压进度提示、台本放大、曲库删除作品。
  for _ in 1 2 3; do
    tap_text "Wait" >/dev/null 2>&1 || true
    tap_text "知道了" >/dev/null 2>&1 || true
    sleep 1
  done
  tap_text "设置" || true
  sleep 3
  shot "80_settings_home"
  tap_text_exact "个人资料" || true
  sleep 3
  shot "81_profile_dlsite"
  "$ADB" -s "$S" shell input keyevent KEYCODE_BACK >/dev/null 2>&1
  sleep 2
  tap_text_exact "下载管理" || true
  sleep 3
  tap_text_exact "解压并入库" || true
  sleep 3
  shot "82_extract_result"
  "$ADB" -s "$S" shell input keyevent KEYCODE_BACK >/dev/null 2>&1
  sleep 2
  tap_text_exact "曲库与扫描" || true
  sleep 3
  shot "83_hidden_list"
  "$ADB" -s "$S" shell input keyevent KEYCODE_BACK >/dev/null 2>&1
  sleep 2
  # 标签按坐标点：tap_text「曲库」会命中「曲库与扫描」，而迷你条正好挡在列表上方，
  # 按到它就直接开半屏播放页了（上一轮就踩了这个坑）。
  "$ADB" -s "$S" shell input tap 540 2288 >/dev/null 2>&1
  sleep 3
  tap_text "测试作品" || tap_text "RJ01234567" || true
  sleep 4
  tap_text_exact "删除" || true
  sleep 3
  shot "84_delete_dialog"
  tap_text_exact "仅移出曲库" || true
  sleep 4
  shot "85_after_delete"
  "$ADB" -s "$S" shell input tap 864 2288 >/dev/null 2>&1
  sleep 2
  tap_text_exact "曲库与扫描" || true
  sleep 3
  "$ADB" -s "$S" shell input swipe 540 1700 540 900 300 >/dev/null 2>&1
  sleep 2
  shot "86_restore_list"
  tap_text_exact "恢复" || true
  sleep 4
  shot "87_restored"
else
  "$ADB" -s "$S" shell input swipe 540 1600 540 700 300 >/dev/null 2>&1
  sleep 1
  shot "02_library_scrolled"
  # 点第一个作品进详情
  "$ADB" -s "$S" shell input tap 300 900 >/dev/null 2>&1
  sleep 2
  shot "03_detail"
  "$ADB" -s "$S" shell input keyevent KEYCODE_BACK >/dev/null 2>&1
  sleep 1
  "$ADB" -s "$S" shell input tap 897 2288 >/dev/null 2>&1
  sleep 2
  shot "04_settings"
  "$ADB" -s "$S" shell input keyevent KEYCODE_BACK >/dev/null 2>&1
  "$ADB" -s "$S" shell input tap 540 2288 >/dev/null 2>&1
  sleep 2
  shot "05_player"
fi

echo "== 完成：$OUT"
