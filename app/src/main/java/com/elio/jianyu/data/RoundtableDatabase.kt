package com.elio.jianyu.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.elio.jianyu.telemetry.PrivacySafeLogger
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        Character::class,
        ChatSession::class,
        Message::class,
        CharacterGroup::class,
        IssueEntity::class,
        StageEntity::class,
        ExecutionRunEntity::class,
        ExecutionParticipantSnapshotEntity::class,
        ExecutionParticipantStateEntity::class,
        ExecutionRunBudgetEntity::class,
        MaterialReferenceEntity::class,
        MaterialUsageSnapshotEntity::class,
        PersonalContextEntryEntity::class,
        PersonalContextUsageSnapshotEntity::class,
        StageSummaryDraftEntity::class,
        StageSummaryDraftRevisionEntity::class,
        ConfirmedArtifactEntity::class,
        ArtifactMessageSourceEntity::class,
        ArtifactRunSourceEntity::class,
        ArtifactDraftSourceEntity::class,
        ArtifactMaterialSourceEntity::class,
        AudioAssetEntity::class,
        OfficialSkillCombinationEntity::class,
        OfficialSkillCombinationMemberEntity::class,
        IssueLifecycleEntity::class,
        IssueArchiveEventEntity::class,
        IssueResumeEventEntity::class,
        IssueRelationEntity::class,
        IssuePurgeOperationEntity::class,
        CrossDiscussionSessionEntity::class,
        ExecutionMessageUsageSnapshotEntity::class,
        StageAdvancementEntity::class,
        StageAdvancementMeasureEntity::class,
        StageAdvancementSkillMemberEntity::class,
        StageAdvancementMaterialEntity::class,
        StageAdvancementArtifactEntity::class,
    ],
    version = 14,
    exportSchema = true,
)
@TypeConverters(
    CoreDomainConverters::class,
    ResourceLifecycleConverters::class,
    ExecutionRuntimeConverters::class,
    CollaborationConverters::class,
    StageAdvancementConverters::class,
    IssueLifecycleV12Converters::class,
)
abstract class RoundtableDatabase : RoomDatabase() {
    private val explicitlyClosed = AtomicBoolean(false)

    internal val isExplicitlyClosed: Boolean
        get() = explicitlyClosed.get()

    final override fun close() {
        explicitlyClosed.set(true)
        super.close()
    }

    abstract fun characterDao(): CharacterDao
    abstract fun chatDao(): ChatDao
    abstract fun characterGroupDao(): CharacterGroupDao
    abstract fun coreDomainDao(): CoreDomainDao
    abstract fun resourceLifecycleDao(): ResourceLifecycleDao
    internal abstract fun jianyuRepositoryDao(): JianyuRepositoryDao
    internal abstract fun collaborationDao(): CollaborationDao
    internal abstract fun stageAdvancementDao(): StageAdvancementDao
    internal abstract fun issueLifecycleV12Dao(): IssueLifecycleV12Dao

    companion object {
        @Volatile
        private var INSTANCE: RoundtableDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                if (!LegacyDatabaseMigrationSupport.columnExists(
                        db,
                        "characters",
                        "skillAssetPath",
                    )
                ) {
                    db.execSQL(
                        "ALTER TABLE characters " +
                            "ADD COLUMN skillAssetPath TEXT NOT NULL DEFAULT ''",
                    )
                }
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                if (!LegacyDatabaseMigrationSupport.columnExists(
                        db,
                        "characters",
                        "skillDescriptionVector",
                    )
                ) {
                    db.execSQL(
                        "ALTER TABLE characters " +
                            "ADD COLUMN skillDescriptionVector TEXT NOT NULL DEFAULT ''",
                    )
                }
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                if (!LegacyDatabaseMigrationSupport.columnExists(db, "messages", "roundIndex")) {
                    db.execSQL(
                        "ALTER TABLE messages " +
                            "ADD COLUMN roundIndex INTEGER NOT NULL DEFAULT 0",
                    )
                }
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                if (!LegacyDatabaseMigrationSupport.columnExists(db, "messages", "roundIndex")) {
                    db.execSQL(
                        "ALTER TABLE messages " +
                            "ADD COLUMN roundIndex INTEGER NOT NULL DEFAULT 0",
                    )
                }
                if (!LegacyDatabaseMigrationSupport.columnExists(db, "messages", "audioFilePath")) {
                    db.execSQL("ALTER TABLE messages ADD COLUMN audioFilePath TEXT")
                }
                if (!LegacyDatabaseMigrationSupport.columnExists(db, "messages", "audioFormat")) {
                    db.execSQL("ALTER TABLE messages ADD COLUMN audioFormat TEXT")
                }
                if (!LegacyDatabaseMigrationSupport.columnExists(db, "messages", "audioSizeBytes")) {
                    db.execSQL(
                        "ALTER TABLE messages " +
                            "ADD COLUMN audioSizeBytes INTEGER NOT NULL DEFAULT 0",
                    )
                }
                LegacyDatabaseMigrationSupport.rebuildCharactersForVersion5(db)
                LegacyDatabaseMigrationSupport.createCharacterGroupsTable(db)
                LegacyDatabaseMigrationSupport.seedPresetGroups(db)
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                LegacyDatabaseMigrationSupport.createCoreDomainTablesForVersion6(db)
                LegacyDatabaseMigrationSupport.backfillLegacySessionsForVersion6(db)
                LegacyDatabaseMigrationSupport.rebuildMessagesForVersion6(db)
            }
        }

        val MIGRATION_6_7: Migration = ResourceLifecycleMigration.MIGRATION_6_7
        val MIGRATION_7_8: Migration = ExecutionRuntimeMigration.MIGRATION_7_8
        val MIGRATION_8_9: Migration = MaterialContextMigration.MIGRATION_8_9
        val MIGRATION_9_10: Migration = CollaborationMigration.MIGRATION_9_10
        val MIGRATION_10_11: Migration = StageAdvancementMigration.MIGRATION_10_11
        val MIGRATION_11_12: Migration = IssueLifecycleV12Migration.MIGRATION_11_12
        val MIGRATION_12_13: Migration = ExecutionThinkingPolicyMigration.MIGRATION_12_13
        val MIGRATION_13_14: Migration = ExecutionApiUsageMigration.MIGRATION_13_14

        val ALL_MIGRATIONS: Array<Migration> = arrayOf(
            MIGRATION_1_2,
            MIGRATION_2_3,
            MIGRATION_3_4,
            MIGRATION_4_5,
            MIGRATION_5_6,
            MIGRATION_6_7,
            MIGRATION_7_8,
            MIGRATION_8_9,
            MIGRATION_9_10,
            MIGRATION_10_11,
            MIGRATION_11_12,
            MIGRATION_12_13,
            MIGRATION_13_14,
        )

        fun getDatabase(context: Context, scope: CoroutineScope): RoundtableDatabase {
            val current = INSTANCE
            if (current != null && !current.isExplicitlyClosed) {
                return current
            }
            return synchronized(this) {
                val synchronizedCurrent = INSTANCE
                if (synchronizedCurrent != null && !synchronizedCurrent.isExplicitlyClosed) {
                    synchronizedCurrent
                } else {
                    if (synchronizedCurrent?.isExplicitlyClosed == true) {
                        INSTANCE = null
                    }
                    buildDatabase(context, scope).also { instance ->
                        INSTANCE = instance
                    }
                }
            }
        }

        /**
         * 仅允许生命周期所有者关闭它持有的当前单例。
         *
         * 先在同一 companion 锁内摘除 [INSTANCE]，再关闭旧实例；并发 `getDatabase` 会等待关闭
         * 完成后创建新实例，不能拿到半关闭对象。
         */
        internal fun closeAndClear(expected: RoundtableDatabase) {
            synchronized(this) {
                check(INSTANCE === expected) {
                    "只能关闭当前 RoundtableDatabase 单例"
                }
                INSTANCE = null
                expected.close()
            }
        }

        private fun buildDatabase(
            context: Context,
            scope: CoroutineScope,
        ): RoundtableDatabase = Room.databaseBuilder(
            context.applicationContext,
            RoundtableDatabase::class.java,
            "roundtable_database",
        )
            .addMigrations(*ALL_MIGRATIONS)
            .addCallback(DatabaseCallback(scope, context.applicationContext))
            .build()
    }

    private class DatabaseCallback(
        private val scope: CoroutineScope,
        private val context: Context,
    ) : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            scope.launch(Dispatchers.IO) {
                val configs = com.elio.jianyu.skill.SkillLoader.loadSkillsConfig(context)
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
                                config.voiceConfig,
                            ),
                        )
                    }
                    LegacyDatabaseMigrationSupport.seedPresetGroups(db)
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
