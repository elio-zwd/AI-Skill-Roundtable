# 正式包名与应用品牌资源

## 当前正式标识

Android 应用身份已经完成两次迁移：

```text
最早示例包：com.example.skillroundtable
上一阶段开发包：com.elio.skillroundtable
当前 applicationId / namespace：com.elio.jianyu
当前 Launcher Activity：com.elio.jianyu.MainActivity
当前用户可见名称：见域
```

当前 Kotlin 源码、单元测试和 Instrumentation Test 位于 `com.elio.jianyu` 包路径。仓库名仍为 `AI-Skill-Roundtable`，目标仓库名 `jianyu-workbench` 与官网 `jianyu.my-elio.online` 尚未迁移。

## 安装与数据兼容性

Android 会把不同 `applicationId` 视为不同应用。因此：

- 已安装的 `com.elio.skillroundtable` 不会被 `com.elio.jianyu` APK 原位升级；
- 旧应用的 Room 数据、SharedPreferences、私有文件和 Android Keystore 内容不会自动迁移到新应用；
- 两个应用在开发设备上可以暂时并存；
- 新包首次启动使用独立 Android UID 与私有沙箱；
- 用户需要在新包中重新配置 API Key；
- 当前项目仍处于公开发布前阶段，不增加跨应用数据迁移器；
- 迁移过程不会自动卸载或清空旧包。

需要手动清理旧开发安装时，可执行：

```powershell
adb uninstall com.elio.skillroundtable
```

该命令仅用于开发者主动清理，不属于应用升级流程。

## 应用图标

当前应用继续沿用仓库内原有的 Adaptive Icon：

- 深色背景；
- 青色六边形表示圆桌；
- 三个白色参与者节点；
- 提供圆形图标和 Android 13 monochrome 图层。

该图标由 Android Vector Drawable XML 构成，不依赖第三方 Logo 或外部图片素材，但它不是最终“见域”正式品牌 Logo。PR09-01 只迁移应用名称和技术身份，不修改正式 Logo、App Icon、主题或完整品牌视觉；正式品牌资源由后续独立任务处理。

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

- APK 的 application ID 为 `com.elio.jianyu`；
- Launcher Activity 为 `com.elio.jianyu.MainActivity`；
- Manifest 仍通过相对类名 `.MainActivity` 解析到当前 namespace；
- 活动源码和运行脚本中不再把 `com.elio.skillroundtable` 当作当前包名；
- 旧 FQCN Room Schema 作为历史基线保留，新 FQCN Schema 由后续 Task 4 真实生成并提交。
