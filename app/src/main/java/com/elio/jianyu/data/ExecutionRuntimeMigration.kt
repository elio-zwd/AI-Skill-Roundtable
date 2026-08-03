package com.elio.jianyu.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object ExecutionRuntimeMigration {
    val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `execution_participant_states` (
                    `participantSnapshotId` TEXT NOT NULL,
                    `runId` TEXT NOT NULL,
                    `status` TEXT NOT NULL DEFAULT 'queued',
                    `attemptCount` INTEGER NOT NULL DEFAULT 0,
                    `outputMessageId` INTEGER,
                    `startedAt` INTEGER,
                    `finishedAt` INTEGER,
                    `lastErrorCode` TEXT,
                    `lastErrorMessage` TEXT,
                    `hasIncompleteOutput` INTEGER NOT NULL DEFAULT 0,
                    `updatedAt` INTEGER NOT NULL,
                    PRIMARY KEY(`participantSnapshotId`),
                    FOREIGN KEY(`participantSnapshotId`, `runId`)
                        REFERENCES `execution_participant_snapshots`(`id`, `runId`)
                        ON UPDATE NO ACTION ON DELETE RESTRICT,
                    FOREIGN KEY(`outputMessageId`)
                        REFERENCES `messages`(`id`)
                        ON UPDATE NO ACTION ON DELETE RESTRICT
                )
                """.trimIndent(),
            )
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS " +
                    "`index_execution_participant_states_participantSnapshotId_runId` " +
                    "ON `execution_participant_states` (`participantSnapshotId`, `runId`)",
            )
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_execution_participant_states_runId` " +
                    "ON `execution_participant_states` (`runId`)",
            )
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_execution_participant_states_runId_status` " +
                    "ON `execution_participant_states` (`runId`, `status`)",
            )
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_execution_participant_states_outputMessageId` " +
                    "ON `execution_participant_states` (`outputMessageId`)",
            )

            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `execution_run_budgets` (
                    `rootRunId` TEXT NOT NULL,
                    `maxApiCalls` INTEGER NOT NULL,
                    `usedApiCalls` INTEGER NOT NULL DEFAULT 0,
                    `reservedRequiredCalls` INTEGER NOT NULL DEFAULT 0,
                    `maxCharacters` INTEGER NOT NULL,
                    `maxSearchQueriesPerCharacter` INTEGER NOT NULL,
                    `maxOutputTokensPerAnswer` INTEGER NOT NULL,
                    `closed` INTEGER NOT NULL DEFAULT 0,
                    `updatedAt` INTEGER NOT NULL,
                    PRIMARY KEY(`rootRunId`),
                    FOREIGN KEY(`rootRunId`) REFERENCES `execution_runs`(`id`)
                        ON UPDATE NO ACTION ON DELETE RESTRICT
                )
                """.trimIndent(),
            )
        }
    }
}
