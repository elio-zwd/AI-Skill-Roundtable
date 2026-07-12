package com.example.skillroundtable.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [Character::class, ChatSession::class, Message::class],
    version = 2,
    exportSchema = false
)
abstract class RoundtableDatabase : RoomDatabase() {
    abstract fun characterDao(): CharacterDao
    abstract fun chatDao(): ChatDao

    companion object {
        @Volatile
        private var INSTANCE: RoundtableDatabase? = null

        fun getDatabase(context: Context, scope: CoroutineScope): RoundtableDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    RoundtableDatabase::class.java,
                    "roundtable_database"
                )
                .fallbackToDestructiveMigration()
                .addCallback(DatabaseCallback(scope))
                .build()
                INSTANCE = instance
                instance
            }
        }
    }

    private class DatabaseCallback(
        private val scope: CoroutineScope
    ) : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            INSTANCE?.let { database ->
                scope.launch(Dispatchers.IO) {
                    val characterDao = database.characterDao()
                    val initialCharacters = listOf(
                        Character(
                            id = "zhang_xuefeng",
                            name = "张雪峰",
                            avatar = "👨‍🏫",
                            tagline = "升学志愿与职业规划导师，刺破理想幻泡的实用主义者",
                            systemPrompt = "",
                            skillAssetPath = "skills/zhang_xuefeng.md",
                            order = 1,
                            isActive = true
                        ),
                        Character(
                            id = "elon_musk",
                            name = "埃隆·马斯克",
                            avatar = "🪐",
                            tagline = "SpaceX与特斯拉CEO，用第一性原理与五步工作法重塑现实的硬核科技狂人",
                            systemPrompt = "",
                            skillAssetPath = "skills/elon_musk.md",
                            order = 2,
                            isActive = true
                        ),
                        Character(
                            id = "richard_feynman",
                            name = "理查德·费曼",
                            avatar = "🥁",
                            tagline = "诺贝尔物理学奖得主，拒绝虚荣术语，主张用极简大白话解释一切的科学顽童",
                            systemPrompt = "",
                            skillAssetPath = "skills/richard_feynman.md",
                            order = 3,
                            isActive = true
                        ),
                        Character(
                            id = "charlie_munger",
                            name = "查理·芒格",
                            avatar = "👴",
                            tagline = "用多元思维模型与逆向思考避开愚蠢的终身学习者",
                            systemPrompt = "",
                            skillAssetPath = "skills/charlie_munger.md",
                            order = 4,
                            isActive = true
                        ),
                        Character(
                            id = "naval_ravikant",
                            name = "纳瓦尔",
                            avatar = "🧘",
                            tagline = "硅谷投资人与现代智者，用“特定知识”与“无需许可的杠杆”追求财富与快乐",
                            systemPrompt = "",
                            skillAssetPath = "skills/naval_ravikant.md",
                            order = 5,
                            isActive = true
                        ),
                        Character(
                            id = "steve_jobs",
                            name = "史蒂夫·乔布斯",
                            avatar = "🍎",
                            tagline = "苹果公司联合创始人，站在科技与人文的交汇处的完美主义者",
                            systemPrompt = "",
                            skillAssetPath = "skills/steve_jobs.md",
                            order = 6,
                            isActive = true
                        ),
                        Character(
                            id = "nassim_taleb",
                            name = "纳西姆·塔勒布",
                            avatar = "🏋️",
                            tagline = "《黑天鹅》《反脆弱》作者，关注尾部风险与切肤之痛的风险工程师",
                            systemPrompt = "",
                            skillAssetPath = "skills/nassim_taleb.md",
                            order = 7,
                            isActive = true
                        )
                    )
                    characterDao.insertAll(initialCharacters)
                }
            }
        }
    }
}
