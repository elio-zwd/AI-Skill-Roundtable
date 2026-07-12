# Bug 记录：Gradle 版本不匹配导致构建评估失败

## 1. 问题现象
在无 Android Studio 的干净项目上，尝试使用本地旧版 `Gradle 7.6.4` 运行 `gradle wrapper` 命令生成新版 Gradle 脚本时，出现以下错误阻断：
```
FAILURE: Build failed with an exception.

* What went wrong:
An exception occurred applying plugin request [id: 'com.android.application', version: '8.7.2']
> Failed to apply plugin 'com.android.internal.version-check'.
   > Minimum supported Gradle version is 8.9. Current version is 7.6.4.
```
并且在随后执行 `.\gradlew.bat` 时报出命令找不到的错误。

---

## 2. 原因分析
1. **构建评估期校验机制**：Gradle 在执行任何 task（哪怕是纯粹用于升级/生成 Wrapper 的 `wrapper` 任务）之前，都会先对整个项目进行配置评估（Evaluation Phase）。
2. **插件兼容性硬性限制**：在评估期，配置加载了 [app/build.gradle.kts](file:///d:/My_Elio/AI-Skill-Roundtable/app/build.gradle.kts) 并应用了版本为 `8.7.2` 的 Android Application 插件。该插件内部有硬编码检查，要求 Gradle 版本必须 $\ge 8.9$。而当前命令启动所使用的是 `Gradle 7.6.4`，因此触发了崩溃，使得 `wrapper` 任务根本没有机会运行去生成新的脚本。

---

## 3. 解决方案

### 方案一：绕过项目评估生成 Wrapper（未采用）
在空目录中运行 `gradle wrapper` 生成通用脚本后再拷贝回项目，但相对繁琐。

### 方案二：借用已有项目的 Wrapper（已采用）
1. 发现本地同级项目 `palmformance` 已经配置了完美的 `Gradle 8.14-all` 并且其本地缓存目录已处于解压可用状态。
2. 直接通过 PowerShell 拷贝其 Wrapper 脚本及配置：
   ```powershell
   Copy-Item "D:\01_EK_Projects\palmformance\android\gradlew" "d:\My_Elio\AI-Skill-Roundtable\gradlew"
   Copy-Item "D:\01_EK_Projects\palmformance\android\gradlew.bat" "d:\My_Elio\AI-Skill-Roundtable\gradlew.bat"
   New-Item -ItemType Directory -Force -Path "d:\My_Elio\AI-Skill-Roundtable\gradle\wrapper"
   Copy-Item "D:\01_EK_Projects\palmformance\android\gradle\wrapper\*" "d:\My_Elio\AI-Skill-Roundtable\gradle\wrapper" -Recurse -Force
   ```
3. 拷贝后，由于本地已经有 `gradle-8.14-all` 的全局缓存，直接运行 `.\gradlew.bat assembleDebug` 会直接复用缓存，不仅避免了版本报错，还实现了**完全离线秒级启动**。
