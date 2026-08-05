package com.elio.jianyu.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object CollaborationMigration {
    val MIGRATION_9_10 = object : Migration(9, 10) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE `execution_runs` " +
                    "ADD COLUMN `runKind` TEXT NOT NULL DEFAULT 'standard'",
            )
            db.execSQL("ALTER TABLE `execution_runs` ADD COLUMN `parentRunId` TEXT")
            db.execSQL("ALTER TABLE `execution_runs` ADD COLUMN `discussionId` TEXT")
            db.execSQL(
                "ALTER TABLE `execution_runs` " +
                    "ADD COLUMN `historyScope` TEXT NOT NULL DEFAULT 'full_stage'",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_execution_runs_parentRunId` " +
                    "ON `execution_runs` (`parentRunId`)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_execution_runs_discussionId` " +
                    "ON `execution_runs` (`discussionId`)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_execution_runs_stageId_runKind` " +
                    "ON `execution_runs` (`stageId`, `runKind`)",
            )

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `cross_discussion_sessions` (
                    `id` TEXT NOT NULL,
                    `issueId` TEXT NOT NULL,
                    `stageId` TEXT NOT NULL,
                    `triggerMessageId` INTEGER NOT NULL,
                    `responseRunId` TEXT NOT NULL,
                    `synthesisRunId` TEXT,
                    `integratorSkillId` TEXT NOT NULL,
                    `status` TEXT NOT NULL,
                    `idempotencyKey` TEXT NOT NULL,
                    `successfulParticipantIdsJson` TEXT NOT NULL DEFAULT '[]',
                    `failedParticipantIdsJson` TEXT NOT NULL DEFAULT '[]',
                    `partialSynthesisConfirmedAt` INTEGER,
                    `createdAt` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL,
                    `failureCode` TEXT,
                    PRIMARY KEY(`id`),
                    FOREIGN KEY(`stageId`, `issueId`) REFERENCES `stages`(`id`, `issueId`)
                        ON UPDATE NO ACTION ON DELETE RESTRICT,
                    FOREIGN KEY(`triggerMessageId`) REFERENCES `messages`(`id`)
                        ON UPDATE NO ACTION ON DELETE RESTRICT,
                    FOREIGN KEY(`responseRunId`) REFERENCES `execution_runs`(`id`)
                        ON UPDATE NO ACTION ON DELETE RESTRICT,
                    FOREIGN KEY(`synthesisRunId`) REFERENCES `execution_runs`(`id`)
                        ON UPDATE NO ACTION ON DELETE RESTRICT
                )
                """.trimIndent(),
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_cross_discussion_sessions_stageId_issueId` " +
                    "ON `cross_discussion_sessions` (`stageId`, `issueId`)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_cross_discussion_sessions_triggerMessageId` " +
                    "ON `cross_discussion_sessions` (`triggerMessageId`)",
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_cross_discussion_sessions_responseRunId` " +
                    "ON `cross_discussion_sessions` (`responseRunId`)",
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_cross_discussion_sessions_synthesisRunId` " +
                    "ON `cross_discussion_sessions` (`synthesisRunId`)",
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_cross_discussion_sessions_idempotencyKey` " +
                    "ON `cross_discussion_sessions` (`idempotencyKey`)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_cross_discussion_sessions_stageId_status` " +
                    "ON `cross_discussion_sessions` (`stageId`, `status`)",
            )

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `execution_message_usage_snapshots` (
                    `id` TEXT NOT NULL,
                    `runId` TEXT NOT NULL,
                    `sourceMessageId` INTEGER NOT NULL,
                    `sourceExecutionRunId` TEXT,
                    `sourceParticipantSnapshotId` TEXT,
                    `senderIdSnapshot` TEXT NOT NULL,
                    `senderNameSnapshot` TEXT NOT NULL,
                    `contentSnapshot` TEXT NOT NULL,
                    `contentHash` TEXT NOT NULL,
                    `usageOrder` INTEGER NOT NULL,
                    `usedAt` INTEGER NOT NULL,
                    PRIMARY KEY(`id`),
                    FOREIGN KEY(`runId`) REFERENCES `execution_runs`(`id`)
                        ON UPDATE NO ACTION ON DELETE RESTRICT,
                    FOREIGN KEY(`sourceMessageId`) REFERENCES `messages`(`id`)
                        ON UPDATE NO ACTION ON DELETE RESTRICT
                )
                """.trimIndent(),
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_execution_message_usage_snapshots_runId` " +
                    "ON `execution_message_usage_snapshots` (`runId`)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_execution_message_usage_snapshots_sourceMessageId` " +
                    "ON `execution_message_usage_snapshots` (`sourceMessageId`)",
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS " +
                    "`index_execution_message_usage_snapshots_runId_sourceMessageId` " +
                    "ON `execution_message_usage_snapshots` (`runId`, `sourceMessageId`)",
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS " +
                    "`index_execution_message_usage_snapshots_runId_usageOrder` " +
                    "ON `execution_message_usage_snapshots` (`runId`, `usageOrder`)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS " +
                    "`index_execution_message_usage_snapshots_sourceExecutionRunId` " +
                    "ON `execution_message_usage_snapshots` (`sourceExecutionRunId`)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS " +
                    "`index_execution_message_usage_snapshots_sourceParticipantSnapshotId` " +
                    "ON `execution_message_usage_snapshots` (`sourceParticipantSnapshotId`)",
            )
        }
    }
}
