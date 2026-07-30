# GitHub 检索批次 002

检索时间：2026-07-30（Asia/Shanghai）

覆盖方向：办公/人事/合同、求职、专利与软著、产品、行业合规、Windows 文档、Skill 格式与供应链安全。

## 可作为能力方向参考的来源

- `claude-office-skills/skills`：合同、HR、财务、文档、研究、邮件、会议、项目与办公流程。
- `proficientlyjobs/proficiently-claude-skills`：求职、简历定制、求职信与申请记录；MIT 声明。
- `Fokkyp/claude-skills`、`Fokkyp/SoftwareCopyright-Skill`、`handsomestWei/patent-disclosure-skill`：中文产品、软著、专利材料场景。
- `product-on-purpose/pm-skills`：产品发现、PRD、验证流程；Apache-2.0 声明。
- `tinh2/skills-hub-registry`：行业合规、供应链、制造、运营等候选入口；只作发现目录，逐项接入前须核验上游。
- `dachent/skills`：Windows Office 自动化参考；因脚本与上游来源复杂，暂不转换为 Android App Skill。

## 格式与安全参考

- `anthropics/skills`、`microsoft/skills`、`NVIDIA/skills` 和 GitHub Copilot SDK 文档均采用“独立目录 + YAML frontmatter + SKILL.md 正文”的基本结构。
- `NousResearch/hermes-agent` 明确提示对第三方 Skill 扫描数据外传、提示注入、破坏性命令与供应链信号；本研究区因此只生成无脚本、无外部访问要求的文本型草案。

## 排除/暂缓

- 规避 AIGC 检测、替考、泄题、代写欺骗、规避招聘/风控以及未经授权的人格蒸馏：不纳入 App 草案。
- 医疗急救、投资交易和法律定论：只保留“教育/整理/转介”潜力，接入前需要独立的专业审查。
