# 本机构建环境（已验证）

这台机器上已经装好并跑通了一整套 Android 工具链，路径如下：

| 组件 | 路径 |
|---|---|
| JDK 17 | `D:\android-dev\jdk\jdk-17.0.20.1+1` |
| Android SDK | `D:\android-dev\sdk`（platform-tools 37.0.1 / platforms;android-35 / build-tools;35.0.0） |
| Gradle 8.11.1 | `D:\android-dev\gradle\gradle-8.11.1`（项目里也带了 wrapper） |
| Gradle 依赖缓存 | `D:\android-dev\gradle-home` |
| adb | `C:\platform-tools\adb.exe` |
| 模拟器 AVD | `asmr`（system-images;android-35;google_apis;x86_64） |

## 构建 + 跑测试

```powershell
$env:JAVA_HOME='D:\android-dev\jdk\jdk-17.0.20.1+1'
$env:ANDROID_HOME='D:\android-dev\sdk'
$env:ANDROID_SDK_ROOT='D:\android-dev\sdk'
$env:GRADLE_USER_HOME='D:\android-dev\gradle-home'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"

cd <项目目录>\asmr-player
.\gradlew.bat testDebugUnitTest assembleDebug --console=plain
# 产物：app\build\outputs\apk\debug\com.asmrplayer-1.4.1.apk
```

## 在模拟器里跑

```powershell
$adb='C:\platform-tools\adb.exe'
& 'D:\android-dev\sdk\emulator\emulator.exe' -avd asmr -no-window -no-audio -no-boot-anim -gpu swiftshader_indirect -no-snapshot

# 等 sys.boot_completed 变成 1 之后：
& $adb install -r app\build\outputs\apk\debug\com.asmrplayer-1.4.1.apk
& $adb shell appops set com.asmrplayer MANAGE_EXTERNAL_STORAGE allow
& $adb shell am start -n com.asmrplayer/.MainActivity
```

## 已验证结论（在本机模拟器上实测）

- `testDebugUnitTest`：**76 个单元测试全部通过**。
- APK 安装并启动成功，无崩溃；ExoPlayer / MediaSession 正常初始化。
- 把 4 个音频 + 3 份台本（lrc / txt）推入 `/sdcard/ASMR/WhaleAlbum`，扫描结果为
  **音频 4、已配台本 3**，且 `01 deep sea.wav ↔ 01 deep sea.lrc`、
  `02 deep sea.wav ↔ 02 deep sea.txt`、`03 no script.wav ↔ 未匹配` 全部正确。
- 播放 `01 deep sea` 后播放页正确显示 LRC 三行台本，媒体会话状态为 PLAYING。
- 点「写入台本标签」后把 mp3 拉回电脑检查：新增了 `USLT` 帧（encoding=1 / lang=eng），
  歌词内容正确，且原有 `TIT2`/`TPE1` 帧完好。
- 撤销「所有文件访问权限」后重启，应用正确显示授权引导页。
## 第二轮改动（本次，已在模拟器实测）

| 需求 | 实现 | 验证方式 |
|---|---|---|
| 选择器「用其他应用打开」 | SAF `OpenDocument` / `OpenDocumentTree` + `SafPath` 把 Uri 还原成本地路径 | 点按钮后前台变为 `com.google.android.documentsui/PickActivity` |
| 专辑封面 | `CoverArtFinder`（文件夹封面）+ 内嵌封面 `APIC/PICTURE/covr` 提取 | 带 cover.png 的项目显示缩略图；播放页大图；迷你条小图 |
| 通知跳转应用 | MediaSession `setSessionActivity` | `dumpsys notification` 见 `contentIntent = PendingIntent ... startActivity` |
| 多格式统合进播放页 | 播放列表 = 当前总项目全部曲目 | 同一作品 MP3/WAV/FLAC 出现在同一列表 |
| 皮肤自定义 | 4 配色 × 深色三态 × 5 背景预设 + 自定义背景图 | 切「深色 + 樱花 + 夜樱」后截图确认生效 |
| 设置开关 + 播放列表 | 新增外观/播放列表分区与开关 | 设置页可见并可切换 |
| **bug：同项目不同子文件夹的台本扫不到** | `ScriptMatcher` 新增 `SAME_PROJECT` 档（按曲库根目录算总项目） | `audio/01_track.mp3` 成功匹配 `script/01_track.txt` |

单元测试新增：sibling 匹配、跨项目不匹配、readme 不作为共用台本、项目分组、手动指定回落、
封面查找、三种容器内嵌封面往返（共 56 个）。
