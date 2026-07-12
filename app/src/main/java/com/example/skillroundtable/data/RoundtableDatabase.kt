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
                        Character(
                            id = config.id,
                            name = config.name,
                            avatar = config.avatar,
                            tagline = config.tagline,
                            systemPrompt = "", // 初始置空，由 ViewModel 在运行时动态载入最新 Prompt 并回填
                            skillAssetPath = config.skillAssetPath,
                            order = config.order,
                            isActive = config.isActive
                        )
                    }
                    if (initialCharacters.isNotEmpty()) {
                        characterDao.insertAll(initialCharacters)
                    }
                }
            }
        }
    }
}
