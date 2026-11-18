#!/usr/bin/env bash
# 标签页切换的掉帧探针：起模拟器 → 装包 → 分三组来回切 → 读 gfxinfo。
#
# 为什么要分组：只有分开测「曲库↔设置」（两侧都轻）和「曲库↔播放」（播放页重），
# 才能判断卡顿是「转场本身」还是「播放页首帧重组」。合成一个总数只能看见平均值。
#
# 用法：scripts/jank_probe.sh <apk> <输出目录> [轮数]
set -u
ADB=/d/android-dev/sdk/platform-tools/adb.exe
export MSYS_NO_PATHCONV=1
winpath() { printf '%s' "$1" | sed -E 's#^/([a-zA-Z])/#\1:/#'; }
APK="$(winpath "${1:?需要 apk 路径}")"
OUT="$(winpath "${2:?需要输出目录}")"
ROUNDS="${3:-4}"
S=emulator-5554
mkdir -p "$OUT"

# 底部导航三个按钮的中心（1080x2400）
X_LIB=180; X_PLAY=540; X_SET=897; Y_NAV=2288

"$ADB" start-server >/dev/null 2>&1
( cd /d/android-dev/sdk/emulator && ./emulator.exe -avd asmr -no-window -no-audio \
    -no-boot-anim -no-snapshot -gpu swiftshader_indirect >/tmp/emu.log 2>&1 ) &
for i in $(seq 1 120); do
  "$ADB" devices | grep -q "$S[[:space:]]*device" && break
  sleep 2
done
for i in $(seq 1 90); do
  [ "$("$ADB" -s "$S" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r\n')" = "1" ] && break
  sleep 2
done

INSTALL_OUT=$("$ADB" -s "$S" install -r -g "$APK" 2>&1 | tail -1)
echo "安装：$INSTALL_OUT"
case "$INSTALL_OUT" in *Success*) ;; *) echo "!! 安装失败"; exit 1 ;; esac
"$ADB" -s "$S" shell appops set --uid com.kiite.player MANAGE_EXTERNAL_STORAGE allow >/dev/null 2>&1
"$ADB" -s "$S" shell settings put global window_animation_scale 1 >/dev/null 2>&1
"$ADB" -s "$S" shell settings put global transition_animation_scale 1 >/dev/null 2>&1
"$ADB" -s "$S" shell settings put global animator_duration_scale 1 >/dev/null 2>&1

"$ADB" -s "$S" shell am force-stop com.kiite.player >/dev/null 2>&1
"$ADB" -s "$S" shell am start -n com.kiite.player/.MainActivity >/dev/null 2>&1
sleep 8

probe() {  # probe <名字> <起点 x> <终点 x>
  local name="$1" from="$2" to="$3" i
  "$ADB" -s "$S" shell input tap $from $Y_NAV >/dev/null 2>&1
  sleep 2
  "$ADB" -s "$S" shell dumpsys gfxinfo com.kiite.player reset >/dev/null 2>&1
  sleep 1
  for i in $(seq 1 "$ROUNDS"); do
    "$ADB" -s "$S" shell input tap $to $Y_NAV >/dev/null 2>&1
    sleep 2
    "$ADB" -s "$S" shell input tap $from $Y_NAV >/dev/null 2>&1
    sleep 2
  done
  "$ADB" -s "$S" shell dumpsys gfxinfo com.kiite.player > "$OUT/gfx_$name.txt" 2>&1
  echo "-- $name"
  grep -E "Total frames|Janky frames:|50th percentile|90th percentile|99th percentile|Number Slow UI thread|Number Slow issue draw" "$OUT/gfx_$name.txt" | sed 's/^/   /'
}

probe "lib_set" $X_LIB $X_SET
probe "lib_play" $X_LIB $X_PLAY
probe "play_set" $X_PLAY $X_SET
echo "== 原始统计在 $OUT"
