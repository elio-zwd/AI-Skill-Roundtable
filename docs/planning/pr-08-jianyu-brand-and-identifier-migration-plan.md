# PR08：见域品牌与技术标识迁移预留计划

> 状态：**仅记录决策，暂不实施**
> 分支：`docs/pr-08-brand-migration-planning`
> 起始基线：PR #22 Head `056848961e61bc109ab433db045b4c2037005d82`
> 前置依赖：PR #20、PR #21、PR #22 按顺序完成整合并进入最新 `main`

本文用于提前记录已经确认的品牌与技术标识目标，避免前置规划 PR 完成后再次依赖对话记忆。当前分支不得修改 Android 生产代码、Gradle、Room、资源、CI、仓库设置、DNS 或服务器配置。

---

## 1. 已确认名称与发布前提

以下名称正式确定：

| 类型 | 已确认值 |
|---|---|
| 产品中文名 | **见域** |
| Android App 用户可见名称 | **见域** |
| GitHub 仓库目标名称 | **`jianyu-workbench`** |
| 产品官网目标域名 | **`jianyu.my-elio.online`** |
| Android `applicationId` 目标值 | **`com.elio.jianyu`** |
| 产品口号 | **看见更多观点，打开认知边界** |

发布与兼容前提也已确认：

- 当前 App 尚未正式发布；
- 不保留旧 App 作为独立产品；
- 不迁移旧包 `com.elio.skillroundtable` 的 Room 数据；
- 不迁移旧包的 Keystore、偏好设置、私有文件或本地会话；
- 不设计旧包到新包的用户数据桥接；
- 后续按**全新应用身份**切换到 `com.elio.jianyu`；
- 本地开发机上的旧测试安装可直接卸载或清除数据，不作为正式兼容对象。

当前仓库仍名为 `AI-Skill-Roundtable`，当前 App 仍显示“AI 智囊圆桌”，当前 `applicationId` 仍为 `com.elio.skillroundtable`。这些旧值只是尚未执行迁移的开发基线，不是需要长期兼容的正式发布身份。

本文确认的是迁移目标，不代表迁移已经执行。

---

## 2. 当前实施门禁

在以下条件全部满足前，不得执行品牌与技术标识迁移：

1. PR #20 已完成最终审阅并合并；
2. PR #21 已更新到最新 `main`、完成重新验收并合并；
3. PR #22 已更新到最新 `main`、完成重新验收并合并；
4. 本分支已从最新统一基线重新同步，确认没有覆盖已合并文档；
5. PR08-D 已输出用户可见品牌、文案、图标与视觉方案；
6. PR08-E 已输出仓库、包名、namespace、Kotlin 包路径、Room Schema 路径、CI、服务器与域名迁移评估；
7. PR08-F 已整合并冻结最终迁移方案；
8. 用户已明确授权进入 PR09 实施阶段。

前置条件未满足时，本分支只保留规划文档，不打开生产迁移 PR。

PR08-E 不再评估“如何保留旧 App 数据”，而应评估“如何干净、完整地切换到全新应用身份，并消除旧技术标识”。

---

## 3. 未来迁移范围

### 3.1 GitHub 仓库

目标：

```text
elio-zwd/AI-Skill-Roundtable
→ elio-zwd/jianyu-workbench
```

后续需要同步检查：

- 本地 Git `origin`；
- README 中的仓库链接和克隆命令；
- PR、Issue、交接文档和自动化 Prompt 中的固定链接；
- CI Badge、Release 链接和下载链接；
- 服务器部署脚本和其他外部集成；
- 其他并行对话或本地 AI 使用的仓库地址。

仓库正式改名前必须确认所有开放 PR 已处理，避免堆叠 Base、验收 Prompt 和外部引用同时漂移。

### 3.2 App 用户可见品牌

目标：

```xml
<string name="app_name">见域</string>
```

不得只替换桌面名称。PR09 需要系统盘点并更新：

- “AI 智囊圆桌”；
- “智囊”；
- “开始脑暴”；
- “向诸位智囊提问”；
- 仅适用于旧圆桌中心模型的页面标题与操作文案；
- 通知标题、导出标题、分享标题和关于页；
- 启动页、图标、应用内品牌说明；
- `metadata.json` 和其他发布元数据。

历史文档可以保留旧名称，但必须标注为历史开发基线，不得混写为当前产品定义。

### 3.3 README 与仓库文档

迁移完成后，README 主标题应由过渡形式：

```text
# 见域（AI-Skill-Roundtable）
```

收敛为：

```text
# 见域
```

同时更新：

- 仓库名称与目录示例；
- 产品定义与功能状态；
- 官网、隐私政策、服务条款、文档和下载入口；
- 新产品截图与版本说明；
- 旧名称仅作为开发历史的说明；
- 仍未实现的规划内容，避免把目标规格写成当前能力。

### 3.4 官网与服务器

已确认产品官网：

```text
https://jianyu.my-elio.online
```

第一版官网建议至少包含：

- 产品介绍；
- 核心能力；
- GitHub 与下载入口；
- 隐私政策；
- 服务条款；
- 使用文档；
- 版本说明。

以下内容尚未冻结：

- DNS 服务商与具体记录；
- 服务器部署方式；
- Nginx、Caddy 或其他反向代理；
- HTTPS 证书来源与自动续期；
- API 子域名；
- 日志、监控、备份与发布流程。

`api.jianyu.my-elio.online` 可作为后续候选，但未在本文中冻结为正式 API 域名。

### 3.5 Android 全新应用身份

已确认目标：

```kotlin
applicationId = "com.elio.jianyu"
```

当前开发基线仍为：

```kotlin
applicationId = "com.elio.skillroundtable"
```

迁移原则：

- 见域按全新应用身份发布；
- 不要求旧包原地升级；
- 不保留旧包的数据、设置、Keystore 或私有文件；
- 不设计旧包导出后自动导入新包的兼容流程；
- 本地旧开发安装可以直接卸载；
- 正式发布前必须确认仓库中不再把 `com.elio.skillroundtable` 写成目标包名；
- 新包应从首次公开版本开始使用稳定签名、版本策略和正式发布配置。

虽然不做旧数据迁移，正式修改仍必须同步处理：

- `applicationId`；
- `namespace`；
- Manifest 与 Activity 完整限定名；
- Kotlin 源码和测试包路径；
- Room 数据库类完整限定名与 Schema 路径；
- CI 中的包名、Activity 和 Schema 检查；
- `run.ps1`、ADB 启动命令和其他脚本；
- ProGuard／R8 规则；
- 发布元数据、APK 检查和文档。

本决定确认最终改为 `com.elio.jianyu`，但不在当前分支实施。

### 3.6 `namespace`、Kotlin 包路径与 Room

由于不保留旧应用身份和旧用户数据，PR08-E 不需要再比较“只改 `applicationId` 以兼容旧包”的方案。

推荐迁移方向是同步统一：

```text
namespace          → com.elio.jianyu
Kotlin 主包路径    → com/elio/jianyu/
测试包路径         → com/elio/jianyu/
Room Schema 路径   → 与新数据库类完整限定名一致
```

PR08-E 仍需明确：

- 源码与测试文件移动顺序；
- import、Manifest、脚本和 CI 的批量更新方式；
- Room Schema 历史文件是重建、重命名还是保留开发历史说明；
- 是否保持当前数据库版本号，或在首次公开发布前重建干净的初始 Schema；
- Migration Test 中哪些仅用于旧开发包、可以删除或改写；
- 如何证明仓库中没有遗留活动包名引用；
- 如何回滚代码迁移，而不是回滚旧用户数据。

最终做法由 PR08-E 提案、PR08-F 冻结，PR09 实施。

---

## 4. 推荐实施拆分

品牌与技术标识迁移不应作为一个超大 Commit 一次完成，建议拆分为以下原子任务：

1. **品牌文案迁移**：App 名称、页面文字、通知、导出与元数据；
2. **品牌视觉迁移**：图标、启动页、主题标识与截图；
3. **README 与文档迁移**：正式名称、官网、下载与历史说明；
4. **官网首版**：`jianyu.my-elio.online` 的静态或服务端页面；
5. **Android 身份迁移**：直接切换至 `applicationId = com.elio.jianyu`；
6. **namespace／包路径迁移**：同步迁移至 `com.elio.jianyu`；
7. **Room／CI／脚本清理**：更新 Schema 路径、测试、ADB 和包名门禁；
8. **仓库重命名**：开放 PR 与外部链接处理完成后再执行；
9. **最终交叉验证**：CI、全新安装、链接、发布和回滚检查。

每个任务应使用独立分支和 PR；不得未经用户授权合并。

---

## 5. 验收重点

未来实施完成时至少验证：

- App 桌面和应用内用户可见名称统一为“见域”；
- 用户可见界面不再以“智囊圆桌”作为产品最高层定义；
- README、仓库名和官网品牌一致；
- `jianyu.my-elio.online` 可通过 HTTPS 正常访问；
- 隐私政策和服务条款可访问；
- `applicationId`、`namespace` 和 Kotlin 主包统一为 `com.elio.jianyu`；
- 新包可以在未安装旧包的设备上全新安装并正常启动；
- 本地旧开发包可直接卸载，不要求数据继承；
- Room Schema、测试、CI 和发布脚本没有遗留错误路径；
- 活动源码、测试、Manifest、Gradle、脚本和 CI 不再引用旧目标包名；
- Secret scan、Android CI、单测、Lint、Debug/Release 构建通过；
- 真机验证全新安装、启动、核心流程和卸载重装行为；
- 回滚步骤可以恢复代码与构建配置，不涉及恢复旧包用户数据。

---

## 6. 当前分支明确不做

当前分支只允许保存本文，不执行以下操作：

- 不修改 GitHub 仓库名称；
- 不修改 App 显示名或任何 `app/src/` 文件；
- 不修改 `applicationId`、`namespace` 或 Kotlin 包路径；
- 不修改 Room Schema、Migration 或数据库文件；
- 不修改 Gradle、Manifest、CI、测试或脚本；
- 不修改服务器、DNS、HTTPS 或部署配置；
- 不创建正式发布版本；
- 不合并 PR #20、#21、#22；
- 不启动 PR09。

---

## 7. 后续接续方式

当前分支起始于 PR #22 Head，而不是最终 `main`。前置 PR 完成后，应：

1. 重新读取最新 `main`、`AGENTS.md` 和 PR08-F 状态；
2. 检查该分支与最新 `main` 的差异；
3. 仅保留本文和后续获授权的规划更新；
4. 解决堆叠分支带来的历史差异，避免重复提交 PR #20～#22 内容；
5. 再决定是更新本分支，还是从最新 `main` 新建替代分支；
6. 经用户确认后创建正式 Draft PR。

在此之前，本分支仅作为已确认决策的远端记录。
