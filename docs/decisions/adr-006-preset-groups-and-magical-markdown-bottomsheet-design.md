# ADR 006: 智囊角色预设与自定义分组持久化及画像 Markdown 详情设计

## 1. 背景 (Context)
随着 GitHub 名人角色大举扩充至 20 人，大厅操作的繁琐度成倍增长。为了提升用户挑选角色的体验，亟需设计一套能够将常用角色进行“一键预置分组”与“用户自定义另存分组”的持久化机制。
同时，原来的纯 Emoji 头像（如 🪐）视觉质感廉价，用户已在网页端将 20 位智囊全部生成为了“低饱和度莫兰迪极简插画风”的 AI 肖像 (JPG 格式)。必须对头像加载与大厅角色详情画像（包括思维模型 SKILL.md 解压展示）进行重构，营造 Editorial 杂志级的高级感。

---

## 2. 决策与设计方案 (Decisions)

### 2.1 角色预设与自定义分组数据层 (Room Schema v4)
* **数据库实体设计**：新建 `CharacterGroup` 实体：
  ```kotlin
  @Entity(tableName = "character_groups")
  data class CharacterGroup(
      @PrimaryKey val id: String,
      val name: String,
      val description: String,
      val characterIds: String, // 逗号分隔的 Character ID 集合，例如 "elon_musk,richard_feynman"
      val isPreset: Boolean     // 官方预置分组 (true) / 用户自定义分组 (false)
  )
  ```
  该设计有效避免了在 Room 中声明复杂的多对多 `@Relation` 实体，在开发效率和后期扩展上达到了最佳平衡。
* **数据注入 (Seeding)**：数据库升级为版本 `4`，并在 `RoomDatabase.Callback` 的 `onCreate()` 钩子中，利用 SQLite 自动向分组表中写入 4 大高品味内置预设：
  1. **硅谷创投**：埃隆·马斯克, 史蒂夫·乔布斯, 保罗·格雷厄姆, 安德烈·卡帕斯, 伊利亚·苏茨克维尔
  2. **哲学与心理逻辑**：理查德·费曼, 查理·芒格, 纳瓦尔, 纳西姆·塔勒布, 西格蒙德·弗洛伊德
  3. **流量与注意力经济**：唐纳德·特朗普, 吉米·唐纳森 (MrBeast), 孙宇晨, 峰哥亡命天涯
  4. **规划与个人成长**：张雪峰, 蒂姆·库克, 段永平, 张一鸣, 赵长鹏 (CZ)

### 2.2 零依赖流式图片解码加载 (`CharacterAvatar`)
由于构建配置中未引入 `Coil` 或 `Glide` 等重型异步图片库，为避免依赖冲突和安装包体积膨胀，基于 Compose 运行时和 Android 原生流式接口编写零依赖解码组件：
* **核心加载逻辑**：
  ```kotlin
  val bitmap = remember(avatar) {
      runCatching {
          context.assets.open(avatar).use { stream ->
              BitmapFactory.decodeStream(stream)?.asImageBitmap()
          }
      }.getOrNull()
  }
  ```
* **容错降级**：若图片读取失败或配置路径为空，头像将自动 fallback 为取角色名末尾汉字的 Monogram 圆形背景大字徽标，具有极佳的视觉兜底弹性。

### 2.3 基于首屏杂志风与极简 MarkdownRender 的画像详情
当点击卡片行时，自底向上弹出 `ModalBottomSheet`。详情页的视觉呈现分为上下两部分：
* **上半部分 (杂志级排版)**：以 80dp 的超大莫兰迪 AI 肖像为中心，附带超大加粗姓名，下方使用 `PrimaryAccent` 微光底槽包覆的斜体引用气泡优雅展示智囊的 `Tagline`（一句话精神画像）。
* **下半部分 (Markdown 极简排版)**：直接使用 `loadSkill` 解压并剥离 assets 下角色的 `SKILL.md` 正文，通过轻量级按行解析器 `MarkdownRender` 重新绘制核心标题（加粗强调）、无序列表（以 SecondaryAccent 颜色的点号 `•` 前导）和段落，极大地提升了思维模型和 DNA 呈现的呼吸感与阅读体验。

---

## 3. 后果 (Consequences)
* **性能表现**：利用 `remember(avatar)` 对 assets 解码得到的 ImageBitmap 进行了进程内图元级缓存，多次滑动、席位列表渲染时 CPU 与内存波动极低，杜绝了 UI 卡顿。
* **可维护性**：所有 20 个角色头像的后缀变更为 `.jpg` 并实现全量物理覆盖，使得打包脚本 `extract_skills_metadata.py` 的职责保持专一，后续增减角色和改动头像只需覆盖对应物理图片即可，实现彻底解耦。
