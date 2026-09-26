package com.elio.jianyu.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** 为用户显式带入的官方 Skill 资料保存不可变使用快照。 */
object SkillKnowledgeContextMigration {
    val MIGRATION_14_15: Migration = object : Migration(14, 15) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `skill_knowledge_usage_snapshots` (
                    `id` TEXT NOT NULL,
                    `issueId` TEXT NOT NULL,
                    `stageId` TEXT NOT NULL,
                    `runId` TEXT,
                    `sourceSkillId` TEXT NOT NULL,
                    `documentId` TEXT NOT NULL,
                    `titleSnapshot` TEXT NOT NULL,
                    `relativePathSnapshot` TEXT NOT NULL,
                    `contentSnapshot` TEXT NOT NULL,
                    `contentHash` TEXT NOT NULL,
                    `userConfirmedAt` INTEGER NOT NULL,
                    `createdAt` INTEGER NOT NULL,
                    PRIMARY KEY(`id`),
                    FOREIGN KEY(`issueId`) REFERENCES `issues`(`id`)
                        ON UPDATE NO ACTION ON DELETE RESTRICT,
                    FOREIGN KEY(`stageId`, `issueId`)
                        REFERENCES `stages`(`id`, `issueId`)
                        ON UPDATE NO ACTION ON DELETE RESTRICT,
                    FOREIGN KEY(`runId`, `issueId`, `stageId`)
                        REFERENCES `execution_runs`(`id`, `issueId`, `stageId`)
                        ON UPDATE NO ACTION ON DELETE RESTRICT
                )
                """.trimIndent(),
            )
            database.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS " +
                    "`index_skill_knowledge_usage_snapshots_id_issueId` " +
                    "ON `skill_knowledge_usage_snapshots` (`id`, `issueId`)",
            )
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS " +
                    "`index_skill_knowledge_usage_snapshots_issueId` " +
                    "ON `skill_knowledge_usage_snapshots` (`issueId`)",
            )
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS " +
                    "`index_skill_knowledge_usage_snapshots_stageId_issueId` " +
                    "ON `skill_knowledge_usage_snapshots` (`stageId`, `issueId`)",
            )
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS " +
                    "`index_skill_knowledge_usage_snapshots_runId_issueId_stageId` " +
                    "ON `skill_knowledge_usage_snapshots` (`runId`, `issueId`, `stageId`)",
            )
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS " +
                    "`index_skill_knowledge_usage_snapshots_sourceSkillId_documentId` " +
                    "ON `skill_knowledge_usage_snapshots` (`sourceSkillId`, `documentId`)",
            )
            database.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS " +
                    "`index_skill_knowledge_usage_snapshots_runId_sourceSkillId_documentId` " +
                    "ON `skill_knowledge_usage_snapshots` " +
                    "(`runId`, `sourceSkillId`, `documentId`)",
            )
        }
    }
}
