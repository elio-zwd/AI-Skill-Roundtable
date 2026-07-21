from pathlib import Path
import json
import re
import shutil
import sys
import xml.etree.ElementTree as ET

root = Path.cwd()
old_package = "com.example.skillroundtable"
new_package = "com.elio.skillroundtable"
old_path = "com/example/skillroundtable"
new_path = "com/elio/skillroundtable"
temporary_files = {
    root / "tools/apply_pr04e_migration.py",
    root / ".github/workflows/export-source-snapshot.yml",
}

# Replace active package references and documentation paths.
for path in sorted(root.rglob("*")):
    if not path.is_file() or ".git" in path.parts or path in temporary_files:
        continue
    try:
        text = path.read_text(encoding="utf-8")
    except (UnicodeDecodeError, OSError):
        continue
    updated = text.replace(old_package, new_package).replace(old_path, new_path)
    if updated != text:
        path.write_text(updated, encoding="utf-8", newline="")

# Preserve the compatibility sentence describing the previous installed ID.
planning = root / "docs/planning/pr-04-release-ci-quality.md"
planning_text = planning.read_text(encoding="utf-8").replace(
    "无法直接继承旧 `com.elio.skillroundtable` 安装包的数据",
    "无法直接继承旧 `com.example.skillroundtable` 安装包的数据",
)
planning.write_text(planning_text, encoding="utf-8", newline="")

# Move source roots so paths and Kotlin package declarations stay aligned.
for source_set in ("main", "test", "androidTest"):
    source = root / f"app/src/{source_set}/java/com/example/skillroundtable"
    destination = root / f"app/src/{source_set}/java/com/elio/skillroundtable"
    if not source.is_dir():
        raise RuntimeError(f"Missing source package directory: {source}")
    destination.parent.mkdir(parents=True, exist_ok=True)
    if destination.exists():
        raise RuntimeError(f"Destination already exists: {destination}")
    shutil.move(str(source), str(destination))

    current = source.parent
    stop = root / f"app/src/{source_set}/java"
    while current != stop and current.exists():
        try:
            current.rmdir()
        except OSError:
            break
        current = current.parent

old_schema = root / "app/schemas/com.example.skillroundtable.data.RoundtableDatabase"
new_schema = root / "app/schemas/com.elio.skillroundtable.data.RoundtableDatabase"
if not old_schema.is_dir():
    raise RuntimeError(f"Missing Room schema directory: {old_schema}")
if new_schema.exists():
    raise RuntimeError(f"Room schema destination already exists: {new_schema}")
shutil.move(str(old_schema), str(new_schema))

manifest = root / "app/src/main/AndroidManifest.xml"
manifest_text = manifest.read_text(encoding="utf-8")
default_icon = 'android:icon="@android:drawable/sym_def_app_icon"'
if default_icon not in manifest_text:
    raise RuntimeError("Default Android icon reference was not found")
manifest_text = manifest_text.replace(
    default_icon,
    'android:icon="@mipmap/ic_launcher"\n        android:roundIcon="@mipmap/ic_launcher_round"',
)
manifest.write_text(manifest_text, encoding="utf-8", newline="")

resources = {
    "app/src/main/res/values/ic_launcher_background.xml": '''<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="ic_launcher_background">#0F172A</color>
</resources>
''',
    "app/src/main/res/drawable/ic_launcher_foreground.xml": '''<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path
        android:fillColor="#22D3EE"
        android:pathData="M54,31 L74,42 L74,65 L54,77 L34,65 L34,42 Z" />
    <path
        android:fillColor="#0F172A"
        android:pathData="M54,43 L63,48 L63,59 L54,65 L45,59 L45,48 Z" />
    <path
        android:fillColor="#FFFFFF"
        android:pathData="M54,18 A7,7 0,1 1,54,32 A7,7 0,1 1,54,18 Z" />
    <path
        android:fillColor="#FFFFFF"
        android:pathData="M25,63 A7,7 0,1 1,25,77 A7,7 0,1 1,25,63 Z" />
    <path
        android:fillColor="#FFFFFF"
        android:pathData="M83,63 A7,7 0,1 1,83,77 A7,7 0,1 1,83,63 Z" />
    <path
        android:fillColor="#A5F3FC"
        android:pathData="M48,34 L60,34 L64,40 L44,40 Z" />
    <path
        android:fillColor="#A5F3FC"
        android:pathData="M20,79 L30,79 L36,86 L14,86 Z" />
    <path
        android:fillColor="#A5F3FC"
        android:pathData="M78,79 L88,79 L94,86 L72,86 Z" />
</vector>
''',
    "app/src/main/res/drawable/ic_launcher_monochrome.xml": '''<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path
        android:fillColor="#000000"
        android:fillType="evenOdd"
        android:pathData="M54,31 L74,42 L74,65 L54,77 L34,65 L34,42 Z M54,43 L45,48 L45,59 L54,65 L63,59 L63,48 Z M54,18 A7,7 0,1 1,54,32 A7,7 0,1 1,54,18 Z M25,63 A7,7 0,1 1,25,77 A7,7 0,1 1,25,63 Z M83,63 A7,7 0,1 1,83,77 A7,7 0,1 1,83,63 Z" />
</vector>
''',
    "app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml": '''<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/ic_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
</adaptive-icon>
''',
    "app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml": '''<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/ic_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
</adaptive-icon>
''',
    "app/src/main/res/mipmap-anydpi-v33/ic_launcher.xml": '''<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/ic_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
    <monochrome android:drawable="@drawable/ic_launcher_monochrome" />
</adaptive-icon>
''',
    "app/src/main/res/mipmap-anydpi-v33/ic_launcher_round.xml": '''<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/ic_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
    <monochrome android:drawable="@drawable/ic_launcher_monochrome" />
</adaptive-icon>
''',
    "docs/environment/package-and-branding.md": '''# 正式包名与应用品牌资源

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
.\\gradlew.bat compileDebugKotlin
.\\gradlew.bat testDebugUnitTest
.\\gradlew.bat lintDebug
.\\gradlew.bat assembleDebug
.\\gradlew.bat assembleRelease
```

涉及 Room Schema 与设备能力的验证继续由 API 30 Emulator CI 执行：

```powershell
.\\gradlew.bat connectedDebugAndroidTest
```

构建后还应确认：

- APK 的 application ID 为 `com.elio.skillroundtable`；
- Launcher Activity 为 `com.elio.skillroundtable.MainActivity`；
- `app/schemas/com.elio.skillroundtable.data.RoundtableDatabase/5.json` 未产生非预期结构变化；
- 活动源码、脚本和构建配置中不再引用示例包名。
''',
}

for relative_path, content in resources.items():
    path = root / relative_path
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content, encoding="utf-8", newline="")

# Add a durable CI gate for package, launcher and schema migration completeness.
ci = root / ".github/workflows/android-ci.yml"
ci_text = ci.read_text(encoding="utf-8")
needle = '''      - name: Assemble debug APK
        run: ./gradlew --no-daemon --stacktrace assembleDebug

'''
insert = '''      - name: Assemble debug APK
        run: ./gradlew --no-daemon --stacktrace assembleDebug

      - name: Verify official package and launcher branding
        shell: bash
        run: |
          set -euo pipefail

          if grep -RIn \\
            --exclude-dir=build \\
            --include='*.kt' \\
            --include='*.kts' \\
            --include='*.ps1' \\
            --include='AndroidManifest.xml' \\
            'com\\.example\\.skillroundtable' app/src app/build.gradle.kts run.ps1; then
            echo "::error::Active source or build configuration still references the example package."
            exit 1
          fi

          test -f app/schemas/com.elio.skillroundtable.data.RoundtableDatabase/5.json
          test ! -e app/schemas/com.example.skillroundtable.data.RoundtableDatabase
          test -f app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml
          test -f app/src/main/res/mipmap-anydpi-v33/ic_launcher.xml

          debug_apk="$(find app/build/outputs/apk/debug -maxdepth 1 -type f -name '*.apk' -print -quit)"
          if [[ -z "${debug_apk}" ]]; then
            echo "::error::Debug APK was not generated."
            exit 1
          fi

          aapt="$(find "${ANDROID_HOME}/build-tools" -type f -name aapt -print | sort -V | tail -n 1)"
          if [[ -z "${aapt}" ]]; then
            echo "::error::Unable to locate aapt in the Android SDK."
            exit 1
          fi

          badging="$("${aapt}" dump badging "${debug_apk}")"
          grep -Fq "package: name='com.elio.skillroundtable'" <<<"${badging}"
          grep -Fq "launchable-activity: name='com.elio.skillroundtable.MainActivity'" <<<"${badging}"

'''
if needle not in ci_text:
    raise RuntimeError("Android CI insertion point was not found")
ci.write_text(ci_text.replace(needle, insert, 1), encoding="utf-8", newline="")

# Static validation before the migration commit is created.
errors = []
for xml_file in root.glob("app/src/main/**/*.xml"):
    try:
        ET.parse(xml_file)
    except Exception as exc:
        errors.append(f"Invalid XML {xml_file.relative_to(root)}: {exc}")

for schema_file in root.glob("app/schemas/**/*.json"):
    try:
        json.loads(schema_file.read_text(encoding="utf-8"))
    except Exception as exc:
        errors.append(f"Invalid JSON {schema_file.relative_to(root)}: {exc}")

for source_set in ("main", "test", "androidTest"):
    base = root / f"app/src/{source_set}/java"
    for kotlin_file in base.rglob("*.kt"):
        text = kotlin_file.read_text(encoding="utf-8")
        match = re.search(r"^package\s+([\w.]+)", text, re.MULTILINE)
        if not match:
            errors.append(f"Missing package declaration: {kotlin_file.relative_to(root)}")
            continue
        expected = "/".join(match.group(1).split("."))
        actual = kotlin_file.parent.relative_to(base).as_posix()
        if actual != expected:
            errors.append(f"Path/package mismatch: {kotlin_file.relative_to(root)} -> {match.group(1)}")
        if old_package in text:
            errors.append(f"Old package remains in Kotlin: {kotlin_file.relative_to(root)}")

required = (
    "app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml",
    "app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml",
    "app/src/main/res/mipmap-anydpi-v33/ic_launcher.xml",
    "app/src/main/res/mipmap-anydpi-v33/ic_launcher_round.xml",
    "app/src/main/res/drawable/ic_launcher_foreground.xml",
    "app/src/main/res/drawable/ic_launcher_monochrome.xml",
    "app/schemas/com.elio.skillroundtable.data.RoundtableDatabase/5.json",
)
for relative_path in required:
    if not (root / relative_path).is_file():
        errors.append(f"Missing required file: {relative_path}")

if (root / "app/schemas/com.example.skillroundtable.data.RoundtableDatabase").exists():
    errors.append("Old Room schema directory remains")

active_files = [
    root / "app/build.gradle.kts",
    root / "run.ps1",
    root / "app/src/main/AndroidManifest.xml",
]
active_files.extend(root.glob("app/src/**/*.kt"))
for active_file in active_files:
    if old_package in active_file.read_text(encoding="utf-8"):
        errors.append(f"Old package remains in active file: {active_file.relative_to(root)}")

if errors:
    print("\n".join(errors), file=sys.stderr)
    sys.exit(1)

print("PR04-E package and branding migration validated")
