package com.elio.jianyu.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object ExecutionRuntimeMigration {
    val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(database: SupportSQLiteDatabase) {
            createParticipantStates(database)
            createRunBudgets(database)
            backfillLegacyRuntime(database)
        }
    }

    private fun createParticipantStates(database: SupportSQLiteDatabase) {
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
    }

    private fun createRunBudgets(database: SupportSQLiteDatabase) {
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

    /**
     * v7 没有参与者运行状态和持久预算，不能可靠判断历史调用次数。
     *
     * 迁移不伪造调用消耗：旧根预算以 used=0、closed=1 保存；因此旧历史可查看、
     * 可审计，但不能通过重启获得新的免费调用。旧活跃状态收敛为显式可恢复状态，
     * 旧 Pending 原位关闭并保留已有文本，不自动发送任何网络请求。
     */
    private fun backfillLegacyRuntime(database: SupportSQLiteDatabase) {
        database.execSQL(
            """
            INSERT OR IGNORE INTO `execution_run_budgets` (
                `rootRunId`, `maxApiCalls`, `usedApiCalls`, `reservedRequiredCalls`,
                `maxCharacters`, `maxSearchQueriesPerCharacter`,
                `maxOutputTokensPerAnswer`, `closed`, `updatedAt`
            )
            SELECT
                run.`id`, 30, 0, 0, 6, 3, 4096, 1, run.`updatedAt`
            FROM `execution_runs` run
            WHERE run.`retryOfRunId` IS NULL
            """.trimIndent(),
        )

        database.execSQL(
            """
            INSERT OR IGNORE INTO `execution_participant_states` (
                `participantSnapshotId`, `runId`, `status`, `attemptCount`,
                `outputMessageId`, `startedAt`, `finishedAt`, `lastErrorCode`,
                `lastErrorMessage`, `hasIncompleteOutput`, `updatedAt`
            )
            SELECT
                participant.`id`,
                participant.`runId`,
                CASE
                    WHEN EXISTS (
                        SELECT 1 FROM `messages` message
                        WHERE message.`executionRunId` = participant.`runId`
                          AND message.`participantSnapshotId` = participant.`id`
                          AND message.`isPending` = 0
                          AND LENGTH(TRIM(message.`text`)) > 0
                    ) THEN 'succeeded'
                    WHEN run.`status` = 'stopped' THEN 'stopped'
                    WHEN run.`status` = 'failed' THEN 'failed'
                    ELSE 'retryable'
                END,
                CASE
                    WHEN run.`startedAt` IS NOT NULL OR EXISTS (
                        SELECT 1 FROM `messages` message
                        WHERE message.`executionRunId` = participant.`runId`
                          AND message.`participantSnapshotId` = participant.`id`
                    ) THEN 1
                    ELSE 0
                END,
                (
                    SELECT message.`id` FROM `messages` message
                    WHERE message.`executionRunId` = participant.`runId`
                      AND message.`participantSnapshotId` = participant.`id`
                    ORDER BY message.`timestamp` DESC, message.`id` DESC
                    LIMIT 1
                ),
                run.`startedAt`,
                COALESCE(run.`finishedAt`, run.`stoppedAt`, run.`updatedAt`),
                CASE
                    WHEN EXISTS (
                        SELECT 1 FROM `messages` message
                        WHERE message.`executionRunId` = participant.`runId`
                          AND message.`participantSnapshotId` = participant.`id`
                          AND message.`isPending` = 0
                          AND LENGTH(TRIM(message.`text`)) > 0
                    ) THEN NULL
                    WHEN run.`status` = 'stopped' THEN 'user_stopped'
                    WHEN run.`status` = 'failed' THEN COALESCE(run.`failureCode`, 'provider_error')
                    ELSE 'process_interrupted'
                END,
                CASE
                    WHEN EXISTS (
                        SELECT 1 FROM `messages` message
                        WHERE message.`executionRunId` = participant.`runId`
                          AND message.`participantSnapshotId` = participant.`id`
                          AND message.`isPending` = 0
                          AND LENGTH(TRIM(message.`text`)) > 0
                    ) THEN NULL
                    WHEN run.`status` = 'stopped' THEN '历史运行已由用户停止。'
                    WHEN run.`status` = 'failed' THEN COALESCE(
                        run.`failureMessage`,
                        '历史运行失败，未保存可恢复的调用细节。'
                    )
                    ELSE '历史运行缺少持久执行状态，已安全收敛为中断状态。'
                END,
                CASE
                    WHEN EXISTS (
                        SELECT 1 FROM `messages` message
                        WHERE message.`executionRunId` = participant.`runId`
                          AND message.`participantSnapshotId` = participant.`id`
                          AND message.`isPending` = 1
                          AND LENGTH(TRIM(message.`text`)) > 0
                    ) THEN 1
                    ELSE 0
                END,
                run.`updatedAt`
            FROM `execution_participant_snapshots` participant
            INNER JOIN `execution_runs` run ON run.`id` = participant.`runId`
            """.trimIndent(),
        )

        database.execSQL(
            """
            UPDATE `messages`
            SET `isPending` = 0
            WHERE `executionRunId` IS NOT NULL AND `isPending` = 1
            """.trimIndent(),
        )

        database.execSQL(
            """
            UPDATE `execution_runs`
            SET
                `status` = 'retryable',
                `finishedAt` = COALESCE(`finishedAt`, `updatedAt`),
                `failureCode` = 'process_interrupted',
                `failureMessage` = '历史运行缺少持久执行状态，已安全收敛为中断状态。'
            WHERE `status` IN ('not_started', 'running', 'partial_success')
            """.trimIndent(),
        )
    }
}
