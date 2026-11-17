# 换机器 / 新电脑搭建开发环境

适用场景：在另一台电脑上继续用 AI 开发本项目。

**一句话结论**：代码、规则、文档、AI 上手手册全在 git 里，`git clone` 就有一份完整的；
真正不在 git 里的只有两样 —— **签名私钥**和**推送凭据**。其余是构建环境，重装一次即可。

---

## 1. 哪些东西在 git 里，哪些不在

| 内容 | 在 git 里？ | 说明 |
|---|---|---|
| 全部源码、资源、脚本 | ✅ | `git clone` 即得 |
| `AGENTS.md`、`docs/*`、`HANDOVER.md` | ✅ | AI 接手所需的规则与进度都在这里 |
| `version.json`、`CHANGELOG.md`、工作流 | ✅ | 发布体系完整可迁移 |
| 签名密钥库 `*.jks` | ❌ | `.gitignore` 里，见 `docs/SECURITY.md` |
| `keystore.properties`（签名口令） | ❌ | 同上 |
| 推送用的 PAT / GitHub 登录态 | ❌ | 每台机器各自授权 |
| AI 的项目记忆 `.workbuddy/memory/` | ❌ | 在工作目录里，不在仓库里 |
| Gradle 依赖缓存（约 3 GB）、Android SDK（约 5 GB） | ❌ | **不要拷**，让新机器自己下 |

架构、分层、关键链路见 `docs/ARCHITECTURE.md`，不必依赖旧机器的任何东西。

---

## 2. 新机器要装什么

| 依赖 | 版本要求 | 备注 |
|---|---|---|
| JDK | 17 | AGP 8.x 要求；本机用的是 Temurin 17 |
| Android SDK | platform `android-35` + `build-tools;35.0.0` | 另需 `platform-tools`（adb） |
| Git | 任意较新版本 | Windows 下用 Git Bash |
| Python | 3.9+ | 跑 `scripts/*.py` 校验脚本 |
| GitHub CLI（可选） | 任意 | 有 `gh` 时发布与查 Release 更省事 |

首次构建**必须联网一次**下载 Gradle 依赖；之后可加 `--offline`。别整包拷 `~/.gradle` 或
`GRADLE_USER_HOME`，跨机器/跨系统的路径与元数据容易坏事。

---

## 3. 拉代码 + 首次构建

```bash
# 1) 克隆（仓库是公开的，读不用登录）
git clone https://github.com/YuanDayo/asmr-player.git
cd asmr-player

# 2) 自检：缺什么会直接告诉你
python scripts/doctor.py

# 3) 配环境变量（按你自己的路径改；Windows Git Bash 写法）
export JAVA_HOME=/d/android-dev/jdk/jdk-17.0.20.1+1
export ANDROID_HOME=/d/android-dev/sdk
export GRADLE_USER_HOME=/d/android-dev/gradle-home

# 4) 首次构建（联网，别加 --offline）
./gradlew :app:testDebugUnitTest :app:assembleDebug --console=plain
```

`local.properties` 里也可以写 `sdk.dir=D:/path/to/sdk` 代替 `ANDROID_HOME`；两者取其一即可
（该文件已在 `.gitignore`，不会被提交）。

---

## 4. 恢复签名能力（只有发版才需要）

日常改代码、跑单测、装 debug 包**都不需要密钥库**。只有 `assembleRelease` 需要，而且是硬约束：
拿不到密钥库会直接构建失败，不会静默签成别的证书（见 `docs/SECURITY.md`）。

把备份的 `kiite-release.jks` 放到新机器上任意位置，然后三选一：

**方式 A：仓库根放 `keystore.properties`（最省事）**

```properties
storeFile=D:\\path\\to\\kiite-release.jks
storePassword=<口令>
keyAlias=kiite
keyPassword=<口令>
```

注意 `\` 在 properties 里是转义符，Windows 路径要写成双反斜杠，或直接用正斜杠 `D:/path/to/x.jks`。

**方式 B：只配环境变量**（适合不想在磁盘上留配置文件）

```bash
export SIGNING_STORE_FILE='D:/path/to/kiite-release.jks'
export SIGNING_STORE_PASSWORD='<口令>'
export SIGNING_KEY_ALIAS='kiite'
export SIGNING_KEY_PASSWORD='<口令>'
```

**方式 C：CI 上跑** —— 仓库已有 4 个 Secret，推 Tag 即可，本机不需要私钥。

验证一定不能省：

```bash
./gradlew :app:assembleRelease -PrequireReleaseSigning=true --console=plain
python scripts/apk_cert.py app/build/outputs/apk/release/*.apk \
  --expect 0261c2eef3800390887b99035228864d50bed0f75e96782471db7d2cc7cd5500
```

指纹对不上就说明用错了密钥库，**这时候发出去等于让所有用户卸载重装**，必须停下来查。

---

## 5. 恢复推送能力

读代码不用登录；推代码需要凭据。两种任选：

- **`gh auth login`**（推荐，最省心）：自动带 `repo` + `workflow` scope。
- **PAT**：classic token，勾 `repo` + `workflow`。**没有 `workflow` scope 就推不动
  `.github/workflows/*`**，会报 `refusing to allow ... without workflow scope`。

推送时不要在命令行里贴 token 明文，用环境变量或 `gh` 的凭据存储：

```bash
git -c http.extraheader="AUTHORIZATION: basic $(printf 'x-access-token:%s' "$TOKEN" | base64 -w0)" push origin main
```

`.workbuddy/gh-pat.txt` 这类文件是**本机私货**，不要提交、不要随代码走。

---

## 6. 搬 AI 的项目记忆（可选但建议）

`.workbuddy/memory/` 在**工作目录**里而不是仓库里，所以不跟着 git 走。想让新机器上的 AI
记得历史决策（签名事故、踩过的坑、发版习惯），把整个目录拷到新工作目录的同名位置：

```
<新工作目录>/.workbuddy/memory/     ← 整个目录复制过去
<新工作目录>/asmr-player/           ← git clone 出来的仓库
```

不拷也不会失控：仓库里的 `HANDOVER.md` 就是为此存在的兜底，记录当前进度、环境限制、
踩坑清单与待办。**记忆可以丢，HANDOVER 不会丢。**

---

## 7. 新机器上给 AI 的开场白

把下面这段直接发给新机器上的 AI（替换尖括号内容）：

```
这是 Kiite Player（Android，Kotlin + Compose）的仓库，当前目录 <路径>/asmr-player。
请先按 docs/AI-ONBOARDING.md 走一遍：读 AGENTS.md → docs/AI-ONBOARDING.md →
docs/ARCHITECTURE.md → docs/DEVELOPMENT-GUIDE.md → docs/RELEASE-SPEC.md →
docs/SECURITY.md → HANDOVER.md，然后跑 python scripts/doctor.py 确认环境。
在动手改任何代码之前，先输出：① 当前版本 / 正式通道 / 最新 Release 的实时值；
② 与我要做的事相关的模块和文件路径；③ 分几步改、每步怎么验证；④ 风险点（签名、
更新通道、数据迁移、兼容性）。等我说可以再动代码。
```

`AGENTS.md` 是硬规则入口，任何 AI 都应先读它；文档与代码冲突时以**代码和已发布产物**为准。

---

## 8. 迁移检查清单

- [ ] `git clone` 完成，`git log --oneline -1` 有内容
- [ ] `python scripts/doctor.py` 全绿（JDK / SDK / 密钥库 / 网络）
- [ ] `./gradlew :app:testDebugUnitTest :app:assembleDebug` 通过（首次需联网）
- [ ] 需要发版时：`keystore.properties` 或 `SIGNING_*` 就位，`assembleRelease` 通过且指纹为 `0261c2ee…5500`
- [ ] 推送凭据可用（`gh auth status` 或能 `git push` 一个空提交）
- [ ] `.github/workflows/*` 能推得动（缺 `workflow` scope 会在这里暴露）
- [ ] `HANDOVER.md` 已读，知道当前进度与待办
- [ ] 旧机器上**确认密钥库至少另有一份备份**，再考虑清理

---

## 9. 别做的事

- 别把 `*.jks` / `keystore.properties` / PAT 提交进仓库或粘进聊天记录。
- 别把 `~/.gradle`、`ANDROID_HOME` 整目录拷到新机器（跨系统路径、重复占用十几个 GB）。
- 别用 debug 变体发版 —— debug keystore 每台机器都不同，签出来的包谁也覆盖不上。
- 别指望旧机器的 `local.properties`、`.idea/`、`build/` 有用，它们都在 `.gitignore` 里，
  新机器重新生成即可。
- 别在预发布时改 `version.json`（那是正式更新通道）。
