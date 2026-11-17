# 签名与发布安全

覆盖安装靠的是**同一把私钥**。签名一变，所有已安装用户只能卸载重装（设置全丢）。本文是硬约束。

## 1. 正式签名证书

| 项 | 值 |
|---|---|
| 密钥库文件 | `kiite-release.jks`（不进仓库） |
| 别名 | `kiite` |
| 证书 SHA-256 | `02:61:C2:EE:F3:80:03:90:88:7B:99:03:52:28:86:4D:50:BE:D0:F7:5E:96:78:24:71:DB:7D:2C:C7:CD:55:00` |
| 证书 SHA-1 | `28:99:64:32:25:B9:DE:FF:38:05:DB:80:D3:78:18:CD:A3:BF:4C:79` |
| 算法 | RSA 2048 / SHA256withRSA |
| 证书 DN | `C=US, O=Android, CN=Android Debug` |

> DN 里带 "Android Debug" 是历史原因：这把密钥是从最初那台机器的 `~/.android/debug.keystore`
> 原样迁移出来的（`keytool -importkeystore` 只换别名与口令，不换密钥对与证书）。
> **保留它是刻意的**——换新证书等于让所有老用户重装一次。别为了"名字好看"重新生成。

## 2. 私钥放哪

| 场景 | 位置 |
|---|---|
| 本地构建 | `_keys/kiite-release.jks` + 仓库根 `keystore.properties`（已在 `.gitignore`） |
| CI 构建 | 仓库 Secret：`SIGNING_STORE_BASE64` / `SIGNING_STORE_PASSWORD` / `SIGNING_KEY_ALIAS` / `SIGNING_KEY_PASSWORD` |

`keystore.properties` 形状：

```properties
storeFile=<绝对路径>/kiite-release.jks
storePassword=<口令>
keyAlias=kiite
keyPassword=<口令>
```

**必须异地备份密钥库与口令**。GitHub Secret 只能写不能读，丢了就再也改不了签名，
所有人只能卸载重装。备份建议：至少两台设备 + 一份离线存储。

## 3. 门禁（三道）

1. **Gradle**：`app/build.gradle.kts` 里 `release` 变体的 `signingConfig` 只有在四项信息齐全时才创建，
   否则为 `null`；`assembleRelease` / `bundleRelease` / `packageRelease` 上挂了守卫，缺密钥在打包前直接失败。
   传 `-PrequireReleaseSigning=true` 时在**配置阶段**就报错。debug 变体不受影响，本地随便跑。
2. **CI Secret 检查**：`release.yml` 的 build job 逐个检查四个 Secret，缺一个就 fail，绝不产出无签名包。
3. **CI 指纹校验**：构建后 `apksigner verify --print-certs` 取 SHA-256，必须等于上面那个常量，不符直接 fail。
   同时用 `aapt dump badging` 校验包内 `versionName` / `versionCode` 与 Gradle 一致。

## 4. 怎么验

```bash
# 本地产物 / 任意 Release 资产（远端只取尾部 512KB，不用下整包）
python scripts/apk_cert.py app/build/outputs/apk/release/com.kiite.player-<版本>.apk \
  --expect 0261c2eef3800390887b99035228864d50bed0f75e96782471db7d2cc7cd5500

# 用官方工具再验一次（顺带看 v2/v3 方案是否通过）
$ANDROID_HOME/build-tools/<ver>/apksigner verify --print-certs <apk>
```

注意：**Release 资产的 SHA-256 是文件摘要，不是签名指纹**，两者不能互相替代。

## 5. 历史指纹（事故记录）

| 版本 | 证书 SHA-256 | 来源 |
|---|---|---|
| 2.5.0 及更早、2.5.1-beta2/beta3、2.5.1（重建包）、2.5.2-beta1 起 | `0261c2ee…5500` | 本机 `~/.android/debug.keystore`，即现在的固定密钥 |
| 2.5.1-beta4 | `036427eb…` | CI runner 临时 debug key |
| 2.5.1 首发（CI 包，已删） | 另一个临时 key | 同上 |
| 2.5.2-beta1 首发资产（已替换） | `9ab15fb9…` | 同上 |

**事故根因**：`release.yml` 曾经跑 `assembleDebug`。debug keystore 由 runner 每次新建、密钥随机，
于是每个 CI 包都是新证书，互相都覆盖不了，正式发出去也装不到老用户身上。
修复方式：固定密钥库 + `assembleRelease` + 三道门禁。**永远不要再发 debug 变体。**

> 装过 `2.5.1-beta4` 或 `2.5.1` 首发 CI 包的用户，需要卸载一次再装新版本；之后不会再遇到。

## 6. 禁止

- 禁止重新生成/轮换签名密钥（除非确认愿意让所有用户重装，并且有过公告）。
- 禁止把 `.jks`、口令、`keystore.properties` 提交进仓库或贴进日志/issue。
- 禁止在 CI 里用 debug keystore 签 release 包，或"临时"跳过指纹校验步骤。
- 禁止手工往已存在的 Release 上覆盖资产来"修"签名问题——正确做法是发新版本。
