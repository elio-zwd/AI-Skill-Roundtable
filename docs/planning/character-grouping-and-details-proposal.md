# AI 智囊圆桌：角色分组与画像详情系统方案说明书 (Proposal)

为了应对圆桌应用角色大幅扩增（当前已达 20 个角色）所带来的“选择困难”、“分组繁琐”和“人物背景辨识模糊”等痛点，本项目计划引入**角色预设与自定义分组**以及**角色详情画像（Dossier）**两大核心系统。

---

## 1. 业务价值与痛点分析
1. **多角色管理成本高**：每次提问可能只需要特定领域的专家（如“硅谷创投组”或“财富与逻辑组”），手动一个个去勾选和反选 20 个角色操作极度繁琐。
2. **角色背景辨识度低**：很多新增的名人（如安德烈·卡帕斯、保罗·格雷厄姆等）普通用户不一定熟悉他们的心智模型和决策逻辑，需要一个微型的“角色维基/画册”来告知用户其思维背景。
3. **分组的持久化需求**：用户常需要针对特定问题建立自己专属的“智囊小分队”，这些搭配需要可以被保存和一键复用。

---

## 2. 详细技术方案

### 2.1 数据层设计 (Data & Storage)

#### A. 新建分组表 `CharacterGroup`
在 Room 中建立 `character_groups` 表，支持持久化存储预设与自定义角色分组。
```kotlin
@Entity(tableName = "character_groups")
data class CharacterGroup(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,                  // 分组名称（如 "硅谷硬核创投", "心智模型与哲学"）
    val description: String = "",       // 分组简短一句话介绍
    val isPreset: Boolean = false,     // 是否为系统内置的只读预设分组
    val characterIds: String,          // 逗号分隔的成员ID列表（如 "elon_musk,steve_jobs,paul_graham"）
    val order: Int = 99                // UI 上的排序
)
```

#### B. 数据库版本升级与 Seeding 策略
* **数据库版本**：Room 数据库版本从 `3` 升级至 `4`。
* **数据迁移 (Migration)**：利用 `fallbackToDestructiveMigration()` 确保新增表能直接被初始化入库。
* **预设分组 Seeding**：在 `RoundtableDatabase.kt` 的 `DatabaseCallback` 中，除了读取 `skills_config.json` 初始化角色，新增插入 4 大核心预设分组：
  1. **硅谷硬核创投 (Tech & VC)**：马斯克、乔布斯、保罗·格雷厄姆、卡帕斯、伊利亚、库克。
  2. **哲学、风险与思维模型 (Philosophy & Risk)**：费曼、芒格、纳瓦尔、塔勒布、弗洛伊德。
  3. **自媒体与注意力浪潮 (Media & Traffic)**：张雪峰、MrBeast、孙宇晨、X 增长导师、峰哥。
  4. **顶级商业运作 (Mega Business)**：查理·芒格、张一鸣、段永平、赵长鹏 CZ、蒂姆·库克。

---

### 2.2 逻辑层设计 (Business Logic & ViewModel)

#### A. 分组管理
在 `RoundtableViewModel.kt` 中加入以下状态和控制逻辑：
```kotlin
// UI 观察的所有分组列表（Flow）
val characterGroups: StateFlow<List<CharacterGroup>> = repository.allGroups.stateIn(...)

// 应用某个分组：将该分组内的角色设为活跃(isActive=true)，其余设为不活跃(isActive=false)
fun applyCharacterGroup(group: CharacterGroup) {
    viewModelScope.launch {
        val targetIds = group.characterIds.split(",").toSet()
        val allChars = characterDao.getAllCharactersList() // 阻塞获取
        val updatedList = allChars.map { char ->
            char.copy(isActive = targetIds.contains(char.id))
        }
        characterDao.insertAll(updatedList)
    }
}

// 保存当前选中的活跃角色作为自定义分组
fun saveCurrentActiveAsGroup(name: String, description: String = "") {
    viewModelScope.launch {
        val activeIds = characterDao.getActiveCharacters().map { it.id }.joinToString(",")
        val newGroup = CharacterGroup(
            name = name,
            description = description,
            isPreset = false,
            characterIds = activeIds
        )
        repository.insertGroup(newGroup)
    }
}

// 删除自定义分组
fun deleteGroup(groupId: Long) {
    viewModelScope.launch {
        repository.deleteGroupById(groupId)
    }
}
```

#### B. 角色画像详情加载器 (Dynamic Asset Loader)
为了保持 Room 数据库的轻量化，大段的思维模型（`SKILL.md` 正文）无需全部写入本地 SQLite。
* 我们设计一个挂载在 ViewModel 或 Repository 的工具类：
```kotlin
fun loadCharacterSkillContent(context: Context, assetPath: String): String {
    return try {
        context.assets.open(assetPath).bufferedReader().use { it.readText() }
    } catch (e: Exception) {
        "加载详情失败：${e.message}"
    }
}
```
* **解析展示**：前端拿到 Markdown 文本后，可以使用简单的 Compose 文本展示，或集成輕量级 Markdown 渲染器，将 `SKILL.md` 中 YAML 以外的“心智模型”和“DNA”规整美观地呈现出来。

---

### 2.3 UI/UX 展现与交互设计

#### A. 智囊大厅 (Character Hall) 改造
* **顶部 Chip 滚动栏**：
  在大厅顶部或者勾选框上方，展示一个水平滑动的 `LazyRow`，平铺所有的分组 Chip（带不同的底色，例如预设分组使用 PrimaryContainer，自定义分组使用 SecondaryContainer）。
* **一键切换**：
  点击任意 Chip 触发 `applyCharacterGroup`，并带有流畅的淡入淡出动画过渡更新勾选状态。
* **分组持久化操作**：
  如果当前的勾选状态发生了变化，在顶部出现一个“💾 保存为分组”的微型按钮，点击后弹出 Dialog 让用户输入“分组名称”和“简短介绍”。
* **自定义分组的移除**：
  长按自定义分组的 Chip，弹出“删除该分组”确认提示。

#### B. 角色画像详情卡片 (BottomSheet)
* **交互路径**：
  在 `CharacterHallScreen` 的每一个角色列表中，除了“勾选框”之外，点击角色的整行区域（或信息图标 `(i)`）会弹出 `ModalBottomSheet`。
* **BottomSheet 内容排版**：
  1. **头部 (Header)**：超大 Emoji 头像、大号加粗姓名、分类标签。
  2. **一句话简评**：带有引用样式的 `tagline`（大号倾斜字体，极具呼吸感）。
  3. **思维底座 (Cognitive DNA)**：
     - 动态加载其 `SKILL.md` 中的思维模型列表。
     - 用分段折叠面板 (Accordion) 展示核心心智模型（例如马斯克的“第一性原理”、“五步工作法”）。
  4. **快速启用按钮**：卡片底部配备一个“启用此智囊/禁用此智囊”的大按钮，方便用户一边看画像一边配置。

---

## 3. 实施计划 (Roadmap)

### 阶段一：数据底座与脚本升级 (Day 1)
- [ ] 升级数据库 Room 版本到 `4`，编写 `CharacterGroup` 的 Entity/Dao/Repository。
- [ ] 在 Seeding Callback 中加入预置的分组数据包。

### 阶段二：业务逻辑与读取器开发 (Day 2)
- [ ] 编写 Assets 下的 `SKILL.md` 动态文本解析器，能够剔除 Frontmatter 头部的 YAML，只保留纯正文提供给前端。
- [ ] 实现 ViewModel 中的 `applyCharacterGroup` 和 `saveCurrentActiveAsGroup` 逻辑。

### 阶段三：Compose UI 精美界面重构 (Day 3)
- [ ] 实现顶部的水平滑动分组 Chip 栏。
- [ ] 编写 `CharacterDetailBottomSheet` 用于呈现精美的角色画像详情。
- [ ] 调试 Room 触发机制，确保自定义分组的即时持久化和删除。
