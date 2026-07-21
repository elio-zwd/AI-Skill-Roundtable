# SkillRoundtable 安卓应用离线编译指南

本文档记录了在无 Android Studio 且不联网下载新软件的前提下，利用本地已有的 JDK 17 及离线 Gradle 缓存进行项目编译的技术方案。

## 1. 编译环境配置

当前项目使用的环境依赖及路径配置如下：
* **Android SDK**: 本地 Android SDK 路径
* **JDK 版本**: JDK 17 (路径: 本地 JDK 17 安装目录，如 `C:\path\to\jdk-17`)
* **Gradle 缓存**: `gradle-8.14-bin` (可由 Gradle Wrapper 自动在线下载缓存，或按说明手动配置离线 Wrapper)

---

## 2. 编译前的准备步骤

### 2.1 API 密钥配置 (.env)
项目在编译时需要获取 `GEMINI_API_KEY`。请在项目根目录下创建 `.env` 文件（或将 `.env.example` 复制重命名为 `.env`），并填入您的 API 密钥：
```env
GEMINI_API_KEY=your_actual_api_key
```

### 2.2 项目 JDK 兼容性调整
项目原本配置为 Java 21。为了适配本地已有的 JDK 17，已将 [app/build.gradle.kts](../../app/build.gradle.kts) 进行了以下修改：
```kotlin
compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
kotlinOptions {
    jvmTarget = "17"
}
```

---

## 3. 命令行编译命令

由于项目配置了 Gradle Wrapper，您可以直接通过 PowerShell 终端进行编译：

1. **打开 PowerShell 终端**，并切换到项目根目录。
2. **设置临时环境变量并运行编译**：
   ```powershell
   # 1. 临时将 JDK 17 路径设为 JAVA_HOME 并加入 Path 头部（请替换为您的实际路径）
   $env:JAVA_HOME="C:\path\to\jdk-17"
   $env:Path = "$env:JAVA_HOME\bin;" + $env:Path

   # 2. 运行 Gradle 编译 Debug APK
   .\gradlew.bat assembleDebug
   ```

---

## 4. 编译产物输出

编译成功后，生成的安卓安装包位于：
* **Debug APK 路径**: [app-debug.apk](../../app/build/outputs/apk/debug/app-debug.apk) (约 9.6 MB)
