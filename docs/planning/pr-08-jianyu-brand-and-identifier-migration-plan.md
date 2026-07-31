# PR08：见域品牌与技术标识迁移预留计划

> 状态：**仅记录决策，暂不实施**
> 分支：`docs/pr-08-brand-migration-planning`
> 起始基线：PR #22 Head `056848961e61bc109ab433db045b4c2037005d82`
> 前置依赖：PR #20、PR #21、PR #22 按顺序完成整合并进入最新 `main`

本文用于提前记录已经确认的品牌与技术标识目标，避免前置规划 PR 完成后再次依赖对话记忆。当前分支不得修改 Android 生产代码、Gradle、Room、资源、CI、仓库设置、DNS 或服务器配置。

---

## 1. 已确认名称

以下名称正式确定：

| 类型 | 已确认值 |
|---|---|
| 产品中文名 | **见域** |
| Android App 用户可见名称 | **见域** |
| GitHub 仓库目标名称 | **`jianyu-workbench`** |
| 产品官网目标域名 | **`jianyu.my-elio.online`** |
| Android `applicationId` 目标值 | **`com.elio.jianyu`** |
| 产品口号 | **看见更多观点，打开认知边界** |

补充说明：

- 当前仓库仍名为 `AI-Skill-Roundtable`；
- 当前 App 仍显示“AI 智囊圆桌”；
- 当前 `applicationId` 仍为 `com.elio.skillroundtable`；
- 上述旧值在本阶段仅作为兼容基线存在，不代表目标产品名称回退；
- 本文确认的是迁移目标，不代表迁移已经执行。

---

## 2. 当前实施门禁

在以下条件全部满足前，不得执行品牌与技术标识迁移：

1. PR #20 已完成最终审阅并合并；
2. PR #21 已更新到最新 `main`、完成重新验收并合并；
3. PR #22 已更新到最新 `main`、完成重新验收并合并；
4. 本分支已从最新统一基线重新同步，确认没有覆盖已合并文档；
5. PR08-D 已输出用户可见品牌、文案、图标与视觉方案；
6. PR08-E 已输出仓库、包名、数据、签名、CI、服务器与域名迁移评估；
7. PR08-F 已整合并冻结最终迁移方案；
8. 用户已明确授权进入 PR09 实施阶段。

前置条件未满足时，本分支只保留规划文档，不打开生产迁移 PR。

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

历史文档可以保留旧名称，但必须标注为历史基线，不得混写为当前产品定义。

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
- 旧品牌迁移说明；
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

### 3.5 Android `applicationId`

已确认后续目标：

```kotlin
applicationId = "com.elio.jianyu"
```

当前值仍为：

```kotlin
applicationId = "com.elio.skillroundtable"
```

重要影响：

- Android 会把新的 `applicationId` 视为新的应用身份；
- 不能直接依赖原地升级继承旧 App 的 Room 数据、Keystore、偏好设置和私有文件；
- 若需要保留旧用户数据，必须在正式修改前设计并验证导出／导入、迁移桥接或其他明确方案；
- 签名证书、版本策略、发布渠道和旧包处理方式必须在 PR08-E 中说明；
- 不得仅修改 `applicationId` 而遗漏 CI、脚本、测试、Manifest、Room Schema 和发布元数据。

本决定确认“最终改为 `com.elio.jianyu`”，但不在当前分支实施。

### 3.6 `namespace`、Kotlin 包路径与 Room

以下项目尚未单独冻结最终做法：

- `namespace` 是否同时改为 `com.elio.jianyu`；
- Kotlin 包路径是否迁移至 `com/elio/jianyu/`；
- Room 数据库类的完整限定名；
- `app/schemas/` 下的 Schema 路径；
- Migration Test 与历史数据库兼容策略；
- ProGuard／R8 规则；
- 测试包、脚本和 CI 中的旧包名引用。

PR08-E 应比较“只改 `applicationId`”与“同步迁移 namespace 和包路径”两种方案，并给出推荐、风险和回滚方式。PR08-F 冻结后才允许实施。

---

## 4. 推荐实施拆分

品牌与技术标识迁移不应作为一个超大 Commit 一次完成，建议拆分为以下原子任务：

1. **品牌文案迁移**：App 名称、页面文字、通知、导出与元数据；
2. **品牌视觉迁移**：图标、启动页、主题标识与截图；
3. **README 与文档迁移**：正式名称、官网、下载与历史说明；
4. **官网首版**：`jianyu.my-elio.online` 的静态或服务端页面；
5. **Android 身份迁移**：`applicationId = com.elio.jianyu` 及兼容方案；
6. **namespace／包路径迁移**：若 PR08-E 与 PR08-F 决定同步执行；
7. **仓库重命名**：开放 PR 与外部链接处理完成后再执行；
8. **最终交叉验证**：CI、安装、数据迁移、链接、发布和回滚检查。

每个任务应使用独立分支和 PR；不得未经用户授权合并。

---

## 5. 验收重点

未来实施完成时至少验证：

- App 桌面和应用内用户可见名称统一为“见域”；
- 用户可见界面不再以“智囊圆桌”作为产品最高层定义；
- README、仓库名和官网品牌一致；
- `jianyu.my-elio.online` 可通过 HTTPS 正常访问；
- 隐私政策和服务条款可访问；
- `applicationId` 确为 `com.elio.jianyu`；
- 安装、升级或数据迁移行为与冻结方案一致；
- Room Schema、Migration Test、CI 和发布脚本没有遗留错误路径；
- Secret scan、Android CI、单测、Lint、Debug/Release 构建通过；
- 真机验证新旧安装关系、数据处理和启动行为；
- 回滚步骤可执行且不会误删用户数据。

---

## 6. 当前分支明确不做

当前分支只允许保存本文，不执行以下操作：

- 不修改 GitHub 仓库名称；
- 不修改 App 显示名或任何 `app/src/` 文件；
- 不修改 `applicationId`、`namespace` 或 Kotlin 包路径；
- 不修改 Room Schema、Migration 或数据库文件；
- 不修改 Gradle、Manifest、CI、测试或脚本；
- 不修改服务器、DNS、HTTPS 或部署配置；
- 不创建正式发布或迁移版本；
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
