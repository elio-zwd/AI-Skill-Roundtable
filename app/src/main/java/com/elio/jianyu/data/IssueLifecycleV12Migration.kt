package com.elio.jianyu.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object IssueLifecycleV12Migration {
    val MIGRATION_11_12: Migration = object : Migration(11, 12) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `issue_archive_events` (
                    `id` TEXT NOT NULL,
                    `issueId` TEXT NOT NULL,
                    `archiveOperationId` TEXT NOT NULL,
                    `payloadHash` TEXT NOT NULL,
                    `summaryMarkdown` TEXT NOT NULL,
                    `currentStageIdSnapshot` TEXT,
                    `stageCountSnapshot` INTEGER NOT NULL,
                    `runCountSnapshot` INTEGER NOT NULL,
                    `draftCountSnapshot` INTEGER NOT NULL,
                    `artifactCountSnapshot` INTEGER NOT NULL,
                    `audioAssetCountSnapshot` INTEGER NOT NULL,
                    `archivedAt` INTEGER NOT NULL,
                    `createdAt` INTEGER NOT NULL,
                    PRIMARY KEY(`id`),
                    FOREIGN KEY(`issueId`) REFERENCES `issues`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT
                )
                """.trimIndent(),
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_issue_archive_events_issueId` " +
                    "ON `issue_archive_events` (`issueId`)",
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_issue_archive_events_archiveOperationId` " +
                    "ON `issue_archive_events` (`archiveOperationId`)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_issue_archive_events_issueId_archivedAt` " +
                    "ON `issue_archive_events` (`issueId`, `archivedAt`)",
            )

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `issue_resume_events` (
                    `id` TEXT NOT NULL,
                    `issueId` TEXT NOT NULL,
                    `archiveEventId` TEXT NOT NULL,
                    `resumeOperationId` TEXT NOT NULL,
                    `payloadHash` TEXT NOT NULL,
                    `changeNote` TEXT NOT NULL,
                    `resumedAt` INTEGER NOT NULL,
                    `createdAt` INTEGER NOT NULL,
                    PRIMARY KEY(`id`),
                    FOREIGN KEY(`issueId`) REFERENCES `issues`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT,
                    FOREIGN KEY(`archiveEventId`) REFERENCES `issue_archive_events`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT
                )
                """.trimIndent(),
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_issue_resume_events_issueId` " +
                    "ON `issue_resume_events` (`issueId`)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_issue_resume_events_archiveEventId` " +
                    "ON `issue_resume_events` (`archiveEventId`)",
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_issue_resume_events_resumeOperationId` " +
                    "ON `issue_resume_events` (`resumeOperationId`)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_issue_resume_events_issueId_resumedAt` " +
                    "ON `issue_resume_events` (`issueId`, `resumedAt`)",
            )

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `issue_relations` (
                    `id` TEXT NOT NULL,
                    `sourceIssueId` TEXT,
                    `targetIssueId` TEXT NOT NULL,
                    `sourceArchiveEventId` TEXT,
                    `operationId` TEXT NOT NULL,
                    `payloadHash` TEXT NOT NULL,
                    `relationType` TEXT NOT NULL DEFAULT 'continuation',
                    `createdAt` INTEGER NOT NULL,
                    `sourcePurgedAt` INTEGER,
                    PRIMARY KEY(`id`),
                    FOREIGN KEY(`sourceIssueId`) REFERENCES `issues`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL,
                    FOREIGN KEY(`targetIssueId`) REFERENCES `issues`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT,
                    FOREIGN KEY(`sourceArchiveEventId`) REFERENCES `issue_archive_events`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL
                )
                """.trimIndent(),
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_issue_relations_sourceIssueId` " +
                    "ON `issue_relations` (`sourceIssueId`)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_issue_relations_targetIssueId` " +
                    "ON `issue_relations` (`targetIssueId`)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_issue_relations_sourceArchiveEventId` " +
                    "ON `issue_relations` (`sourceArchiveEventId`)",
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_issue_relations_operationId` " +
                    "ON `issue_relations` (`operationId`)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_issue_relations_sourceIssueId_targetIssueId_relationType` " +
                    "ON `issue_relations` (`sourceIssueId`, `targetIssueId`, `relationType`)",
            )

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `issue_purge_operations` (
                    `id` TEXT NOT NULL,
                    `issueId` TEXT NOT NULL,
                    `operationId` TEXT NOT NULL,
                    `payloadHash` TEXT NOT NULL,
                    `impactHash` TEXT,
                    `state` TEXT NOT NULL DEFAULT 'requested',
                    `requestedAt` INTEGER NOT NULL,
                    `startedAt` INTEGER,
                    `updatedAt` INTEGER NOT NULL,
                    `failedAt` INTEGER,
                    `failureCode` TEXT,
                    `failurePhase` TEXT,
                    `retryCount` INTEGER NOT NULL DEFAULT 0,
                    PRIMARY KEY(`id`),
                    FOREIGN KEY(`issueId`) REFERENCES `issues`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT
                )
                """.trimIndent(),
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_issue_purge_operations_issueId` " +
                    "ON `issue_purge_operations` (`issueId`)",
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_issue_purge_operations_operationId` " +
                    "ON `issue_purge_operations` (`operationId`)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_issue_purge_operations_state` " +
                    "ON `issue_purge_operations` (`state`)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_issue_purge_operations_updatedAt` " +
                    "ON `issue_purge_operations` (`updatedAt`)",
            )

            // v11 中已请求清理但缺少完整状态机的数据保守迁移为可解释失败，要求用户重新查看影响。
            db.execSQL(
                """
                INSERT OR IGNORE INTO `issue_purge_operations` (
                    `id`, `issueId`, `operationId`, `payloadHash`, `impactHash`, `state`,
                    `requestedAt`, `startedAt`, `updatedAt`, `failedAt`, `failureCode`,
                    `failurePhase`, `retryCount`
                )
                SELECT
                    'legacy-purge-' || `issueId`,
                    `issueId`,
                    'legacy-purge-operation-' || `issueId`,
                    'legacy-purge-payload-' || lower(hex(`issueId`)),
                    NULL,
                    'failed_retryable',
                    `purgeRequestedAt`,
                    NULL,
                    `updatedAt`,
                    `purgeRequestedAt`,
                    'legacy_purge_request_requires_review',
                    'impact',
                    0
                FROM `issue_lifecycle`
                WHERE `purgeRequestedAt` IS NOT NULL
                """.trimIndent(),
            )
        }
    }
}
