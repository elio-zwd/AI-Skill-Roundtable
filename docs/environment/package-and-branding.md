# 正式包名与应用品牌资源

## 正式标识

PR04-E 将 Android 应用从示例标识迁移为正式标识：

```text
旧 applicationId / namespace：com.example.skillroundtable
新 applicationId / namespace：com.elio.skillroundtable
```

源码、单元测试、Instrumentation Test、`BuildConfig` 引用、运行脚本和 Room Schema 路径均使用新包名。

## 安装与数据兼容性

Android 会把不同 `applicationId` 视为不同应用。因此：

- 已安装的 `com.example.skillroundtable` 不会被新 APK 原位升级；
- 旧应用的 Room 数据、设置和 Android Keystore 内容不会自动迁移到新应用；
- 两个应用在开发设备上可以暂时并存；
- 当前项目仍处于公开发布前阶段，本次迁移作为正式发布基线，不增加跨应用数据迁移器。

需要清理旧开发安装时，可执行：

```powershell
adb uninstall com.example.skillroundtable
```

## 应用图标

应用不再引用 Android 系统默认图标，改用仓库内自有的 Adaptive Icon：

- 深色背景；
- 青色六边形表示圆桌；
- 三个白色参与者节点；
- 提供圆形图标和 Android 13 monochrome 图层。

图标由 Android Vector Drawable XML 构成，不依赖第三方 Logo、图片下载或外部版权素材。

## 验证重点

```powershell
.\gradlew.bat compileDebugKotlin
.\gradlew.bat testDebugUnitTest
.\gradlew.bat lintDebug
.\gradlew.bat assembleDebug
.\gradlew.bat assembleRelease
```

涉及 Room Schema 与设备能力的验证继续由 API 30 Emulator CI 执行：

```powershell
.\gradlew.bat connectedDebugAndroidTest
```

构建后还应确认：

- APK 的 application ID 为 `com.elio.skillroundtable`；
- Launcher Activity 为 `com.elio.skillroundtable.MainActivity`；
- `app/schemas/com.elio.skillroundtable.data.RoundtableDatabase/5.json` 未产生非预期结构变化；
- 活动源码、脚本和构建配置中不再引用示例包名。
