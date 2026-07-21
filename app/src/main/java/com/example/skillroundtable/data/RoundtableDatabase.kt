package com.example.skillroundtable.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.skillroundtable.telemetry.PrivacySafeLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [Character::class, ChatSession::class, Message::class, CharacterGroup::class],
    version = 5,
    exportSchema = true
)
abstract class RoundtableDatabase : RoomDatabase() {
    abstract fun characterDao(): CharacterDao
    abstract fun chatDao(): ChatDao
    abstract fun characterGroupDao(): CharacterGroupDao

    companion object {
        @Volatile
        private var INSTANCE: RoundtableDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                if (!columnExists(db, "characters", "skillAssetPath")) {
                    db.execSQL(
                        "ALTER TABLE characters " +
                            "ADD COLUMN skillAssetPath TEXT NOT NULL DEFAULT ''"
                    )
                }
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                if (!columnExists(db, "characters", "skillDescriptionVector")) {
                    db.execSQL(
                        "ALTER TABLE characters " +
                            "ADD COLUMN skillDescriptionVector TEXT NOT NULL DEFAULT ''"
                    )
                }
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                if (!columnExists(db, "messages", "roundIndex")) {
                    db.execSQL(
                        "ALTER TABLE messages " +
                            "ADD COLUMN roundIndex INTEGER NOT NULL DEFAULT 0"
                    )
                }
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                if (!columnExists(db, "messages", "roundIndex")) {
                    db.execSQL(
                        "ALTER TABLE messages " +
                            "ADD COLUMN roundIndex INTEGER NOT NULL DEFAULT 0"
                    )
                }
                if (!columnExists(db, "messages", "audioFilePath")) {
                    db.execSQL("ALTER TABLE messages ADD COLUMN audioFilePath TEXT")
                }
                if (!columnExists(db, "messages", "audioFormat")) {
                    db.execSQL("ALTER TABLE messages ADD COLUMN audioFormat TEXT")
                }
                if (!columnExists(db, "messages", "audioSizeBytes")) {
                    db.execSQL(
                        "ALTER TABLE messages " +
                            "ADD COLUMN audioSizeBytes INTEGER NOT NULL DEFAULT 0"
                    )
                }

                rebuildCharactersForVersion5(db)
                createCharacterGroupsTable(db)
                seedPresetGroups(db)
            }
        }

        val ALL_MIGRATIONS: Array<Migration> = arrayOf(
            MIGRATION_1_2,
            MIGRATION_2_3,
            MIGRATION_3_4,
            MIGRATION_4_5
        )

        private fun rebuildCharactersForVersion5(db: SupportSQLiteDatabase) {
            val skillAssetPathExpression = if (columnExists(db, "characters", "skillAssetPath")) {
                "skillAssetPath"
            } else {
                "''"
            }
            val skillDescriptionVectorExpression =
                if (columnExists(db, "characters", "skillDescriptionVector")) {
                    "skillDescriptionVector"
                } else {
                    "''"
                }
            val voiceConfigExpression = if (columnExists(db, "characters", "voiceConfig")) {
                "COALESCE(voiceConfig, 'Aoede')"
            } else {
                "'Aoede'"
            }

            db.execSQL("DROP TABLE IF EXISTS _new_characters")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS _new_characters (
                    id TEXT NOT NULL,
                    name TEXT NOT NULL,
                    avatar TEXT NOT NULL,
                    tagline TEXT NOT NULL,
                    systemPrompt TEXT NOT NULL,
                    skillAssetPath TEXT NOT NULL,
                    `order` INTEGER NOT NULL,
                    isActive INTEGER NOT NULL,
                    skillDescriptionVector TEXT NOT NULL,
                    voiceConfig TEXT NOT NULL DEFAULT 'Aoede',
                    PRIMARY KEY(id)
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO _new_characters (
                    id, name, avatar, tagline, systemPrompt, skillAssetPath,
                    `order`, isActive, skillDescriptionVector, voiceConfig
                )
                SELECT
                    id, name, avatar, tagline, systemPrompt, $skillAssetPathExpression,
                    `order`, isActive, $skillDescriptionVectorExpression, $voiceConfigExpression
                FROM characters
                """.trimIndent()
            )
            db.execSQL("DROP TABLE characters")
            db.execSQL("ALTER TABLE _new_characters RENAME TO characters")
        }

        private fun createCharacterGroupsTable(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS character_groups (
                    id TEXT NOT NULL,
                    name TEXT NOT NULL,
                    description TEXT NOT NULL,
                    characterIds TEXT NOT NULL,
                    isPreset INTEGER NOT NULL,
                    PRIMARY KEY(id)
                )
                """.trimIndent()
            )
        }

        private fun seedPresetGroups(db: SupportSQLiteDatabase) {
            val insertSql =
                "INSERT OR IGNORE INTO character_groups " +
                    "(id, name, description, characterIds, isPreset) VALUES (?, ?, ?, ?, ?)"

            db.execSQL(
                insertSql,
                arrayOf(
                    "silicon_valley_venture",
                    "硅谷创投",
                    "聚焦商业突破、无需许可的杠杆、高科技创业与去中心化浪潮的硅谷科技狂人与投资导师组合",
                    "elon_musk,naval_ravikant,paul_graham,zhang_yiming,changpeng_zhao,tim_cook",
                    1
                )
            )
            db.execSQL(
                insertSql,
                arrayOf(
                    "philosophy_logic",
                    "哲学与心理逻辑",
                    "解构认知偏差，关注尾部风险，探究人性和科学底层的跨学科终身学习大师与思考者",
                    "richard_feynman,charlie_munger,nassim_taleb,sigmund_freud,andrej_karpathy,ilya_sutskever",
                    1
                )
            )
            db.execSQL(
                insertSql,
                arrayOf(
                    "traffic_attention",
                    "流量与注意力经济",
                    "深谙社交媒体、爆款法则、事件营销与流量操盘的全球顶级创作者与博弈专家",
                    "mr_beast,justin_sun,donald_trump,feng_ge,x_mentor",
                    1
                )
            )
            db.execSQL(
                insertSql,
                arrayOf(
                    "planning_growth",
                    "规划与个人成长",
                    "刺破社会幻泡，推崇做对的事与长期主义的个人成长与升学志愿导师",
                    "zhang_xuefeng,duan_yongping,charlie_munger,naval_ravikant",
                    1
                )
            )
        }

        private fun columnExists(
            db: SupportSQLiteDatabase,
            tableName: String,
            columnName: String
        ): Boolean {
            db.query("PRAGMA table_info(`$tableName`)").use { cursor ->
                val nameIndex = cursor.getColumnIndex("name")
                if (nameIndex != -1) {
                    while (cursor.moveToNext()) {
                        if (cursor.getString(nameIndex).equals(columnName, ignoreCase = true)) {
                            return true
                        }
                    }
                }
            }
            return false
        }

        fun getDatabase(context: Context, scope: CoroutineScope): RoundtableDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    RoundtableDatabase::class.java,
                    "roundtable_database"
                )
                    .addMigrations(*ALL_MIGRATIONS)
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
            scope.launch(Dispatchers.IO) {
                val configs = com.example.skillroundtable.skill.SkillLoader.loadSkillsConfig(context)
                db.beginTransaction()
                try {
                    configs.forEach { config ->
                        db.execSQL(
                            "INSERT OR REPLACE INTO characters " +
                                "(id, name, avatar, tagline, systemPrompt, skillAssetPath, `order`, " +
                                "isActive, skillDescriptionVector, voiceConfig) " +
                                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                            arrayOf(
                                config.id,
                                config.name,
                                config.avatar,
                                config.tagline,
                                "",
                                config.skillAssetPath,
                                config.order,
                                if (config.isActive) 1 else 0,
                                config.descriptionVector.joinToString(","),
                                config.voiceConfig
                            )
                        )
                    }

                    seedPresetGroups(db)
                    db.setTransactionSuccessful()
                    PrivacySafeLogger.d("RoundtableDatabase", "Database seed completed")
                } catch (error: Exception) {
                    PrivacySafeLogger.e("RoundtableDatabase", "Database seed failed", error)
                } finally {
                    db.endTransaction()
                }
            }
        }
    }
}
