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
winpath() { printf '%s' "$1" | sed -E 's#^/([a-zA-Z])/#\1:/#'; }
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

shot() {
  "$ADB" -s "$S" shell screencap -p /sdcard/"$1".png >/dev/null 2>&1
  "$ADB" -s "$S" pull /sdcard/"$1".png "$OUT/$1.png" >/dev/null 2>&1 \
    || echo "  ! 截图拉回失败 $1（$OUT 是 Windows 路径吗？）"
  echo "  截图 $1"
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
INSTALL_OUT=$("$ADB" -s "$S" install -r -g "$APK" 2>&1 | tail -1)
echo "  $INSTALL_OUT"
case "$INSTALL_OUT" in
  *Success*) ;;
  *) echo "!! 安装失败，检查 APK 路径（要传 C:/... 而不是 /c/...）"; exit 1 ;;
esac
"$ADB" -s "$S" shell appops set --uid com.kiite.player MANAGE_EXTERNAL_STORAGE allow >/dev/null 2>&1
echo "  已装版本：$("$ADB" -s "$S" shell dumpsys package com.kiite.player | grep -m1 versionName | tr -d '\r')"

if [ "$MODE" = "setup" ]; then
  echo "== 造测试曲库"
  "$ADB" -s "$S" shell mkdir -p /sdcard/Music/asmr 2>/dev/null
  for w in "RJ01234567 深夜の耳かき喫茶" "RJ07654321 雨音と珈琲"; do
    code=${w%% *}; name=${w#* }
    "$ADB" -s "$S" shell mkdir -p "/sdcard/Music/asmr/${code}_${name}" 2>/dev/null
    for i in 01_track 02_track 03_track; do
      "$ADB" -s "$S" shell "echo x > '/sdcard/Music/asmr/${code}_${name}/${i}.mp3'" 2>/dev/null
    done
    "$ADB" -s "$S" shell "echo sc > '/sdcard/Music/asmr/${code}_${name}/台本.txt'" 2>/dev/null
  done
  "$ADB" -s "$S" shell am force-stop com.kiite.player
  "$ADB" -s "$S" shell am start -n com.kiite.player/.MainActivity >/dev/null 2>&1
  sleep 6
fi

echo "== 启动应用"
"$ADB" -s "$S" shell am force-stop com.kiite.player >/dev/null 2>&1
"$ADB" -s "$S" shell am start -n com.kiite.player/.MainActivity >/dev/null 2>&1
sleep 6

# 首启可能弹更新日志
if tap_text "知道了"; then sleep 2; fi
# 权限页：appops 已设好，点「重新检测」让它重读
if tap_text "已授权，重新检测"; then sleep 3; fi
if tap_text "知道了"; then sleep 2; fi

if [ "$MODE" = "setup" ] || [ "$MODE" = "select" ]; then
  tap_text "选择文件夹" || true
  sleep 3
  # 选择器会记住上次目录，通常直接就停在 Music/asmr；不在的话再逐级进
  if ! tap_text "选此文件夹"; then
    for step in "Music" "asmr"; do tap_text "$step" || true; sleep 1; done
  fi
  shot "00_picker"
  tap_text "选此文件夹" || true
  echo "  等待扫描"
  sleep 12
  shot "00_selected"
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
  # 一次 adb shell 里连拍：分多次 adb 调用的话，间隔抖动比动画本身还大
  "$ADB" -s "$S" shell 'input tap 540 2288; sleep 0.55; screencap -p /sdcard/f1.png; sleep 0.5; screencap -p /sdcard/f2.png; sleep 0.55; screencap -p /sdcard/f3.png; sleep 0.9; screencap -p /sdcard/f4.png' >/dev/null 2>&1
  for f in f1 f2 f3 f4; do
    "$ADB" -s "$S" pull /sdcard/$f.png "$OUT/mid_$f.png" >/dev/null 2>&1 \
      || echo "  ! 拉回失败 $f"
  done
  echo "  截图 mid_f1 … mid_f4（转场中途）"
  sleep 1
  shot "t3_player"
  "$ADB" -s "$S" shell settings put global animator_duration_scale 1 >/dev/null 2>&1
else
  shot "01_library_top"
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
