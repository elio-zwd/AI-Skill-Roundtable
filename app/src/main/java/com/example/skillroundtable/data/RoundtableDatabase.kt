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
    entities = [Character::class, ChatSession::class, Message::class, CharacterGroup::class],
    version = 5,
    exportSchema = false
)
abstract class RoundtableDatabase : RoomDatabase() {
    abstract fun characterDao(): CharacterDao
    abstract fun chatDao(): ChatDao
    abstract fun characterGroupDao(): CharacterGroupDao

    companion object {
        @Volatile
        private var INSTANCE: RoundtableDatabase? = null

        val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE messages ADD COLUMN roundIndex INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_4_5 = object : androidx.room.migration.Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE messages ADD COLUMN audioFilePath TEXT")
                db.execSQL("ALTER TABLE messages ADD COLUMN audioFormat TEXT")
                db.execSQL("ALTER TABLE messages ADD COLUMN audioSizeBytes INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE characters ADD COLUMN voiceConfig TEXT NOT NULL DEFAULT 'Aoede'")
            }
        }

        fun getDatabase(context: Context, scope: CoroutineScope): RoundtableDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    RoundtableDatabase::class.java,
                    "roundtable_database"
                )
                .addMigrations(MIGRATION_3_4, MIGRATION_4_5)
                .fallbackToDestructiveMigration()
                .addCallback(DatabaseCallback(scope, context.applicationContext))
                .build()
                INSTANCE = instance
                instance
            }
        }
    }

    private class DatabaseCallback(
        private val scope: CoroutineScope,
        private val context: Context
    ) : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            INSTANCE?.let { database ->
                scope.launch(Dispatchers.IO) {
                    val characterDao = database.characterDao()
                    // 动态从 skills_config.json 中获取初始角色配置，规避在代码中硬编码任何角色数据
                    val configs = com.example.skillroundtable.skill.SkillLoader.loadSkillsConfig(context)
                    val initialCharacters = configs.map { config ->
                        val vectorStr = config.descriptionVector.joinToString(",")
                        Character(
                            id = config.id,
                            name = config.name,
                            avatar = config.avatar,
                            tagline = config.tagline,
                            systemPrompt = "", // 初始置空，由 ViewModel 在运行时动态载入最新 Prompt 并回填
                            skillAssetPath = config.skillAssetPath,
                            order = config.order,
                            isActive = config.isActive,
                            skillDescriptionVector = vectorStr,
                            voiceConfig = config.voiceConfig
                        )
                    }
                    if (initialCharacters.isNotEmpty()) {
                        characterDao.insertAll(initialCharacters)
                    }

                    // Seeding 初始的 4 个特色预设分组
                    val groupDao = database.characterGroupDao()
                    val presetGroups = listOf(
                        CharacterGroup(
                            id = "silicon_valley_venture",
                            name = "硅谷创投",
                            description = "聚焦商业突破、无需许可的杠杆、高科技创业与去中心化浪潮的硅谷科技狂人与投资导师组合",
                            characterIds = "elon_musk,naval_ravikant,paul_graham,zhang_yiming,changpeng_zhao,tim_cook",
                            isPreset = true
                        ),
                        CharacterGroup(
                            id = "philosophy_logic",
                            name = "哲学与心理逻辑",
                            description = "解构认知偏差，关注尾部风险，探究人性和科学底层的跨学科终身学习大师与思考者",
                            characterIds = "richard_feynman,charlie_munger,nassim_taleb,sigmund_freud,andrej_karpathy,ilya_sutskever",
                            isPreset = true
                        ),
                        CharacterGroup(
                            id = "traffic_attention",
                            name = "流量与注意力经济",
                            description = "深谙社交媒体、爆款法则、事件营销与流量操盘的全球顶级创作者与博弈专家",
                            characterIds = "mr_beast,justin_sun,donald_trump,feng_ge,x_mentor",
                            isPreset = true
                        ),
                        CharacterGroup(
                            id = "planning_growth",
                            name = "规划与个人成长",
                            description = "刺破社会幻泡，推崇做对的事与长期主义的个人成长与升学志愿导师",
                            characterIds = "zhang_xuefeng,duan_yongping,charlie_munger,naval_ravikant",
                            isPreset = true
                        )
                    )
                    groupDao.insertAll(presetGroups)
                }
            }
        }
    }
}
