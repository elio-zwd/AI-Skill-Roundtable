# SkillRoundtable 安卓应用离线编译指南

本文档记录了在无 Android Studio 且不联网下载新软件的前提下，利用本地已有的 JDK 17 及离线 Gradle 缓存进行项目编译的技术方案。

## 1. 编译环境配置

当前项目使用的环境依赖及路径配置如下：
* **Android SDK**: `D:\sdk`
* **JDK 版本**: JDK 17 (路径: `D:\My_Elio\dev-tools\jdk-17.0.19+10`)
* **Gradle 缓存**: `gradle-8.14-all` (位于全局缓存 `C:\Users\70455\.gradle\wrapper\dists\gradle-8.14-all\6wgqdj9jb0pzygy0k43l508lk\gradle-8.14`)

---

## 2. 编译前的准备步骤

### 2.1 API 密钥配置 (.env)
项目在编译时需要获取 `GEMINI_API_KEY`。请在项目根目录下创建 `.env` 文件（或将 `.env.example` 复制重命名为 `.env`），并填入您的 API 密钥：
```env
GEMINI_API_KEY=your_actual_api_key
```

### 2.2 项目 JDK 兼容性调整
项目原本配置为 Java 21。为了适配本地已有的 JDK 17，已将 [app/build.gradle.kts](file:///d:/My_Elio/AI-Skill-Roundtable/app/build.gradle.kts) 进行了以下修改：
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

由于项目已配置并拷贝了 `palmformance` 对应的 Gradle 8.14-all Wrapper，您可以直接通过 PowerShell 终端进行纯本地离线编译：

1. **打开 PowerShell 终端**，并切换到项目根目录 `d:\My_Elio\AI-Skill-Roundtable`。
2. **设置临时环境变量并运行编译**：
   ```powershell
   # 1. 临时将 JDK 17 路径设为 JAVA_HOME 并加入 Path 头部
   $env:JAVA_HOME="D:\My_Elio\dev-tools\jdk-17.0.19+10"
   $env:Path = "$env:JAVA_HOME\bin;" + $env:Path

   # 2. 运行 Gradle 编译 Debug APK
   .\gradlew.bat assembleDebug
   ```

---

## 4. 编译产物输出

编译成功后，生成的安卓安装包位于：
* **Debug APK 路径**: [app-debug.apk](file:///d:/My_Elio/AI-Skill-Roundtable/app/build/outputs/apk/debug/app-debug.apk) (约 9.6 MB)
