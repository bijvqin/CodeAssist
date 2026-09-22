# 无广告 fork 说明

本仓库是 [tyron12233/CodeAssist](https://github.com/tyron12233/CodeAssist) 的 fork，
唯一目的：**让广告默认关闭**，并用自己的签名密钥出 APK。

上游是 GPL-3.0-or-later，修改和重新分发都是许可证允许的。

## 一、改了什么（共 3 个文件）

### 1. `app/ide-ui-components/src/commonMain/kotlin/dev/ide/ui/ads/AdController.kt` ← 唯一的功能改动

只改了 `initialAdsEnabled()` 这一个私有函数的函数体。

上游行为：

- 偏好 `ads.enabled` 默认值是 `true`；
- 而且只要 `AdHost.installStamp`（Android 上是安装包的 `lastUpdateTime`）变了，
  就**强制把偏好写回 `true`** —— 也就是每次全新安装和每次升级，广告都会自己开回来。

改成：

```kotlin
private fun initialAdsEnabled(backend: IdeBackend, host: AdHost): Boolean =
    backend.settings.preference(ADS_ENABLED_PREF)?.toBooleanStrictOrNull() ?: false
```

即：默认 **关**，且安装/升级不再重置。已存的偏好依然生效，所以
**设置 → Privacy → "Show ads" 开关照旧可用**，想支持作者可以自己打开。

> 为什么选这个函数：它是所有广告的唯一总闸上游。`AdController.adsActive`
> = `host.available && adsEnabled`，而 `AdSlot`（12 个原生广告位）和
> `shouldShowLessonInterstitial`（全屏插屏）第一行都在查它。改这一处，全覆盖。

### 2. `app/ide-ui-components/src/desktopTest/.../AdControllerInstallResetTest.kt` ← 测试

上游这个测试断言的正是"升级后广告重新打开"（`updateTurnsAdsBackOnOnce`），
改了逻辑它必然失败。已重写为断言新契约：默认关、升级不重置、显式打开后能持久生效。

### 3. `app/ide-ui-screens/src/desktopTest/kotlin/dev/ide/ui/FakeAds.kt` ← 测试

`fakeAdController()` 原本靠"默认开"让 6 个快照测试画出广告块（它们要检查广告位的边距对齐）。
加了一句 `.apply { updateAdsEnabled(true) }` 显式打开，快照保持不变。

### 附：`.github/workflows/noads-apk.yml` ← 新增，不动上游文件

编 APK 用。详见文件头注释。

## 二、没有改的东西

- **没有删 AdMob 依赖**，广告 SDK 仍在包里，只是不展示。
  想让 SDK 完全不初始化，另一条路是把 `app/ide-android/.../MainActivity.kt`
  里的 `AndroidAdHost(...)` 换成 `AdHost.None` —— 但那样设置里的开关也会消失。
- **没有改 `applicationId`**（仍是 `com.tyron.code`）。好处是能当官方版的替代品；
  代价是签名不同，**首次安装必须先卸载官方版**（装前先用 App 内的项目备份导出）。
  想和官方版并存，就在 `defaultConfig` 里加 `applicationIdSuffix = ".noads"`。

## 三、上游更新时会不会覆盖我们的改动

**不会被静默覆盖**，git 不会那样做。同步上游有两种结果：

1. 上游没碰这 3 个文件 → 直接干净合并，我们的改动原样保留。
2. 上游也改了这 3 个文件 → **冲突**，同步会停下来让你手工解决，不会偷偷覆盖。

我们的功能改动刻意压到了**一个函数、一个 hunk**，所以只有上游正好动
`initialAdsEnabled` 才会冲突（概率不高，且真冲突时按上面第 1 节重新套一遍即可）。
两个测试文件冲突时通常直接取我们这边。

同步命令（第一次先加上游 remote）：

```bash
git remote add upstream https://github.com/tyron12233/CodeAssist.git
git fetch upstream
git merge upstream/main
```

⚠️ **别在 GitHub 网页上点 "Discard commits"** —— 那个按钮会把我们的改动直接扔掉。
"Sync fork" 在有冲突时会拒绝并提示用命令行，按上面走就行。

## 四、怎么出 APK

1. 在 GitHub 仓库 **Settings → Secrets and variables → Actions** 加 4 个 secret：
   `RELEASE_KEYSTORE_BASE64`、`RELEASE_STORE_PASSWORD`、`RELEASE_KEY_ALIAS`、`RELEASE_KEY_PASSWORD`。
   没有 keystore 就先本地生成一个（密码自己定，别丢，丢了以后就没法覆盖升级了）：

   ```bash
   keytool -genkeypair -v -keystore release.jks -alias codeassist \
     -keyalg RSA -keysize 2048 -validity 10000
   base64 -w0 release.jks > release.jks.b64   # 这个文件的内容填进 RELEASE_KEYSTORE_BASE64
   ```

2. 仓库 **Actions** 标签页启用 workflow（fork 默认是禁用的）。
3. 跑 **No-Ads APK** → 手动 `Run workflow`，或者每次 push 到 main 自动触发。
4. 从这次运行的 **Artifacts** 下载 `codeassist-noads-release-apk`。

没配 secret 时会退化成编 `profile` 变体：一样不可调试、速度正常，
但用的是 CI 每次随机生成的 debug key，**不同次的包装不上互相覆盖**，只适合先试试。
