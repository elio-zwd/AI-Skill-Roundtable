# PR09：见域多对话开发交接说明

> 总计划：[`pr-09-jianyu-implementation-plan.md`](./pr-09-jianyu-implementation-plan.md)
>
> 任务清单：[`pr-09-jianyu-implementation-tasks.md`](./pr-09-jianyu-implementation-tasks.md)
>
> 最终 PRD：[`jianyu-prd.md`](../product/jianyu-prd.md)

## 1. 交接目标

PR09 将涉及应用身份、Room、导航、调度、资料成果、安全导入导出和品牌视觉。为避免多个 AI 对话互相覆盖，每个对话只负责一个任务、一个分支和一个 PR，并通过 GitHub PR、Commit 和评论交接。

PR09-01～18 是能力域编号，不等于实际执行顺序。02、10、13、14 已拆成 A / B 子阶段；执行顺序以总路线图和本文串行主链为准。

## 2. 不可违反的共同规则

1. 开始前读取根目录与目标目录适用的 `AGENTS.md`；
2. 检查开放 PR 和近期 Commit；
3. 从当时最新稳定 `main` 创建分支；
4. 不直接修改 `main`；
5. 不向其他任务分支提交；
6. 不强制更新、删除或覆盖他人分支；
7. 写文件前重新读取目标分支最新版本；
8. 只修改当前任务必要文件；
9. 不顺带进行无关重构、依赖升级或全库格式化；
10. 不提交密钥、个人信息、构建产物或本地配置；
11. 不删除测试、降低断言、吞掉异常或绕过安全控制；
12. 未实际执行的测试必须标记为未验证；
13. 未经用户授权不标记 Ready、不合并、不改仓库设置、DNS 或服务器。
14. 另一个 AI 必须先对 PR08-F 修正 Head 完成严格只读复核，随后由用户批准并合并 PR08-F，才可启动 PR09；
15. 用户已授权 PR09-01～15 不等待最终视觉，但 PR09-16 与 17 必须等待视觉门禁。

## 3. Superpowers 与 GitHub 工作流

每个对话开始时检查两个插件是否真实可用。

建议技能：

- 新功能或架构：`Superpowers:brainstorming`；
- 计划：`Superpowers:writing-plans`；
- 执行既定计划：`Superpowers:executing-plans`；
- Bug：`Superpowers:systematic-debugging`；
- 测试驱动：`Superpowers:test-driven-development`；
- 完成前：`Superpowers:verification-before-completion`；
- 代码审查：`Superpowers:requesting-code-review`；
- 收尾：`Superpowers:finishing-a-development-branch`。

技能不可用时必须明确说明，并采用仓库规则允许的流程；不得假装已经使用。若任务 Prompt 明确禁止替代流程，则按该 Prompt 立即停止。

`writing-plans` 产出的路线图不能直接作为施工单。每个对话必须先在自己的分支形成精确到文件、接口、失败测试、命令、原子提交和回滚的执行计划，并通过只读复核后再写生产代码。

## 4. 基线传递格式

上一个 PR 合并后，负责协调的对话必须提供：

```text
仓库：
已合并 PR：
最新 main SHA：
下一个任务：
建议分支：
依赖的接口或 Schema：
禁止修改的文件：
已验证内容：
尚未验证内容：
重点风险：
```

后续对话必须重新从 GitHub 核对，不可只相信口头 SHA。

## 5. 串行主链

以下任务强制串行：

```text
PR09-01 应用身份
→ PR09-02A 核心领域 Schema
→ PR09-02B 资源与生命周期 Schema
→ PR09-03 Repository / 恢复
→ PR09-04 导航壳 与 PR09-05 Skill 目录（仅此处允许有限并行）
→ PR09-07 执行运行
→ PR09-09 资料与个人背景
→ PR09-10A 草稿与成果
→ PR09-10B 音频资产
→ PR09-06 首页与推荐
→ PR09-08 点名与交叉讨论
→ PR09-11 推进议题
→ PR09-12 归档与回收站
→ PR09-13A 备份安全设计
→ PR09-13B 加密导出与快照
→ PR09-14A 隔离导入与差异预览
→ PR09-14B 原子替换与回退
→ PR09-15 隐私与风险终审
→ PR09-16 品牌视觉实现（等待最终视觉）
→ PR09-17 端到端回归
→ PR09-18 发布管理员阶段（单独授权）
```

不得让多个对话同时修改：

- Entity / DAO / Database；
- Repository 事务；
- 根导航图；
- 执行状态机；
- 导入原子替换；
- 应用根主题和正式品牌资产。

## 6. 有限并行条件

只有 03 已合并、接口和所有权已经冻结时，04 导航壳与 05 Skill 目录才可有限并行。其他任务默认串行；如果需要修改共享导航、Database、Repository、执行状态机或应用根文件，04 与 05 也必须改回串行。

并行前必须记录：

- 共享接口 Commit SHA；
- 独占文件清单；
- 不可修改的共享文件；
- 依赖 PR；
- 最终整合顺序。

## 7. 产品契约速查

### 正式术语

- 见域；
- Skill；
- 议题；
- 阶段；
- 资料；
- 阶段总结草稿；
- 成果；
- 推进议题；
- 响应批次；
- 执行运行。

不得把“下一轮”重新作为正式入口，也不得把 `roundIndex` 解释为阶段。

### 关键行为

- 首页支持仅保存议题；
- 用户确认前不运行模型；
- 单 Skill / 多 Skill 并列；
- 多 Skill 默认独立回应；
- 点名和交叉讨论默认单次生效；
- 用户可不生成成果直接推进；
- 推进议题采用三步确认；
- 未运行新阶段允许撤销且无倒计时；
- 草稿跨进程持久保存且不自动过期；
- 运行中归档需要显式选择；
- 普通删除进入无自动过期回收站；
- 归档可恢复继续；
- 个人背景按议题显式带入；
- V1 可保存官方 Skill 组合，不开放自定义 Skill；
- 官方 Skill 组合可保存成员、顺序和可选默认职责；默认职责不得改写官方 Skill 正文、系统边界或安全规则。

### 特殊 Skill

- `office-document-productivity`：办公文档助手，不控制桌面 Office；
- `original-expression-naturalizer`：去AI化助手，只让真实内容更像用户本人表达，不用于规避检测、伪造事实或删除诚信声明。

### 应用身份

```text
applicationId：com.elio.jianyu
namespace：优先同步为 com.elio.jianyu
新 App：独立沙箱和全新数据库
旧包数据：不迁移
```

## 8. 分支和 PR 命名

建议：

```text
refactor/pr-09-01-jianyu-app-identity
feat/pr-09-02a-core-domain-schema
feat/pr-09-02b-resource-lifecycle-schema
feat/pr-09-03-jianyu-repository-recovery
feat/pr-09-04-jianyu-navigation-shell
feat/pr-09-05-official-skill-catalog
feat/pr-09-06-home-recommendation
feat/pr-09-07-execution-run
feat/pr-09-08-directed-cross-discussion
feat/pr-09-09-material-context-source
feat/pr-09-10a-draft-result
feat/pr-09-10b-audio-assets
feat/pr-09-11-advance-issue
feat/pr-09-12-archive-trash
security/pr-09-13a-backup-design
feat/pr-09-13b-encrypted-export-snapshot
feat/pr-09-14a-isolated-import-preview
feat/pr-09-14b-atomic-import-replacement
fix/pr-09-15-privacy-risk-closure
design/pr-09-16-jianyu-ui-system
test/pr-09-17-jianyu-e2e-release-gates
release/pr-09-18-jianyu-public-launch
```

PR 标题采用“英文类型: 中文描述”。

## 9. Commit 规则

示例：

```text
refactor: 迁移见域应用身份与包路径
feat: 建立议题与阶段领域模型
test: 增加阶段撤销与恢复测试
fix: 修复进程重建后运行状态丢失
```

- Commit 保持原子性；
- 不自动添加 `Co-Authored-By`；
- 不把格式化和功能修改混在同一 Commit；
- 需要修复验收问题时追加普通 Commit，不强制覆盖历史。

## 10. PR 描述模板

每个 Draft PR 至少包含：

```markdown
## 背景与目标

## Base 与分支

## 产品 / 技术契约

## 实现方式

## 修改文件

## 测试与验证

## 尚未验证

## 已知风险

## 回滚建议

## 后续依赖

## 本地只读验收步骤
```

必须区分：

- 本地实际执行并通过；
- GitHub CI 已通过；
- 仅静态检查；
- 尚未验证。

## 11. 本地验收 AI 统一要求

```text
请对指定 PR 做完全只读验收：

1. 拉取远端最新代码并检出精确 PR Head；
2. 记录操作系统、Shell、Git、JDK、Gradle、Android 工具版本；
3. 核对 Base、Head、Merge Base、提交和文件范围；
4. 不修改、不格式化、不提交、不推送、不变基、不合并；
5. 执行 git diff --check；
6. 按仓库现有配置执行编译、单测、Lint、Migration、Instrumentation 和安全扫描；
7. 记录命令、退出码和关键日志；
8. 检查产品契约、数据边界、失败恢复和向后兼容；
9. 验收前后确认工作区干净；
10. 将失败项、复现步骤和可能原因反馈给远端开发对话；
11. 不把未执行的测试写成通过。
```

## 12. 任务启动 Prompt 模板

```text
你现在接手 GitHub 仓库 elio-zwd/AI-Skill-Roundtable 的 PR09-XX：<任务名称>。

仓库：https://github.com/elio-zwd/AI-Skill-Roundtable
Base 分支：main
Base SHA：<开始前从 GitHub 重新核对>
目标分支：<任务分支>
目标 PR 标题：<英文类型: 中文描述>
依赖 PR：<已合并 PR 和 Commit>

开始前：
1. 确认 GitHub 和 Superpowers 能力；
2. 读取 AGENTS.md、README、jianyu-prd.md、pr-08f-integration-decisions.md、PR09 总计划和当前任务清单；
3. 检查开放 PR 和同域文件冲突；
4. 读取当前调用链、测试和 CI；
5. 将需求拆成可验证完成条件；
6. 先建立精确到文件、接口、失败测试、命令、提交边界和回滚的执行计划，并完成只读计划复核；
7. 先设计失败场景和测试；
8. 只修改当前任务必要文件；
9. 完成后创建 Draft PR，不合并；
10. 输出完整交付报告和本地只读验收 Prompt。

任务目标：
<从 pr-09-jianyu-implementation-tasks.md 复制对应任务>

禁止：
- 修改其他任务独占域；
- 删除或降低测试；
- 声称未执行的测试通过；
- 修改仓库设置、DNS 或服务器；
- 未经用户授权合并。
```

## 13. 品牌视觉特殊门禁

PR09-16 启动前必须取得用户明确视觉选择。

当前状态：

```text
候选 0：保留
候选 A：推荐深化，未批准
候选 B：保留
候选 C：保留
最终 Logo：未冻结
最终 App Icon：未冻结
最终主视觉：未冻结
```

用户已授权：另一个 AI 对 PR08-F 修正 Head 完成只读复核、用户批准且 PR08-F 合并后，PR09-01～15 可以在最终视觉未确认时实施，但不得写入正式品牌资产。PR09-17 必须等待 PR09-16 完成。
