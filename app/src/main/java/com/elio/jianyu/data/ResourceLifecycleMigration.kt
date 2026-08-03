package com.elio.jianyu.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object ResourceLifecycleMigration {
    val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(database: SupportSQLiteDatabase) {
            createCurrentResourceTables(database)
            createDraftAndArtifactTables(database)
            createAudioAndSkillTables(database)
            createLifecycleTable(database)
            createRequiredParentIndexes(database)
            backfillIssueLifecycle(database)
        }
    }

    private fun createCurrentResourceTables(database: SupportSQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `material_references` (
                `id` TEXT NOT NULL,
                `issueId` TEXT NOT NULL,
                `stageId` TEXT,
                `title` TEXT NOT NULL,
                `sourceType` TEXT NOT NULL,
                `sourceLocator` TEXT,
                `content` TEXT NOT NULL,
                `contentHash` TEXT NOT NULL,
                `sourcePublishedAt` INTEGER,
                `sourceCapturedAt` INTEGER,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                `deletedAt` INTEGER,
                `purgeRequestedAt` INTEGER,
                PRIMARY KEY(`id`),
                FOREIGN KEY(`issueId`) REFERENCES `issues`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT,
                FOREIGN KEY(`stageId`, `issueId`) REFERENCES `stages`(`id`, `issueId`) ON UPDATE NO ACTION ON DELETE RESTRICT
            )
            """.trimIndent()
        )
        database.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_material_references_id_issueId` " +
                "ON `material_references` (`id`, `issueId`)"
        )
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_material_references_issueId` " +
                "ON `material_references` (`issueId`)"
        )
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_material_references_stageId_issueId` " +
                "ON `material_references` (`stageId`, `issueId`)"
        )
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_material_references_sourceLocator` " +
                "ON `material_references` (`sourceLocator`)"
        )
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_material_references_deletedAt` " +
                "ON `material_references` (`deletedAt`)"
        )

        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `material_usage_snapshots` (
                `id` TEXT NOT NULL,
                `issueId` TEXT NOT NULL,
                `stageId` TEXT NOT NULL,
                `runId` TEXT,
                `materialReferenceId` TEXT,
                `titleSnapshot` TEXT NOT NULL,
                `sourceTypeSnapshot` TEXT NOT NULL,
                `sourceLocatorSnapshot` TEXT,
                `contentSnapshot` TEXT,
                `contentHash` TEXT NOT NULL,
                `contentState` TEXT NOT NULL DEFAULT 'available',
                `sourcePublishedAtSnapshot` INTEGER,
                `sourceCapturedAtSnapshot` INTEGER,
                `userConfirmedAt` INTEGER NOT NULL,
                `createdAt` INTEGER NOT NULL,
                PRIMARY KEY(`id`),
                FOREIGN KEY(`issueId`) REFERENCES `issues`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT,
                FOREIGN KEY(`stageId`, `issueId`) REFERENCES `stages`(`id`, `issueId`) ON UPDATE NO ACTION ON DELETE RESTRICT,
                FOREIGN KEY(`runId`, `issueId`, `stageId`) REFERENCES `execution_runs`(`id`, `issueId`, `stageId`) ON UPDATE NO ACTION ON DELETE RESTRICT,
                FOREIGN KEY(`materialReferenceId`) REFERENCES `material_references`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL
            )
            """.trimIndent()
        )
        database.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_material_usage_snapshots_id_issueId` " +
                "ON `material_usage_snapshots` (`id`, `issueId`)"
        )
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_material_usage_snapshots_issueId` " +
                "ON `material_usage_snapshots` (`issueId`)"
        )
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_material_usage_snapshots_stageId_issueId` " +
                "ON `material_usage_snapshots` (`stageId`, `issueId`)"
        )
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_material_usage_snapshots_runId_issueId_stageId` " +
                "ON `material_usage_snapshots` (`runId`, `issueId`, `stageId`)"
        )
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_material_usage_snapshots_materialReferenceId` " +
                "ON `material_usage_snapshots` (`materialReferenceId`)"
        )
        database.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_material_usage_snapshots_runId_materialReferenceId` " +
                "ON `material_usage_snapshots` (`runId`, `materialReferenceId`)"
        )

        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `personal_context_entries` (
                `id` TEXT NOT NULL,
                `title` TEXT NOT NULL,
                `content` TEXT NOT NULL,
                `contentHash` TEXT NOT NULL,
                `isEnabled` INTEGER NOT NULL DEFAULT 1,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                `deletedAt` INTEGER,
                `purgeRequestedAt` INTEGER,
                PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_personal_context_entries_contentHash` " +
                "ON `personal_context_entries` (`contentHash`)"
        )
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_personal_context_entries_deletedAt` " +
                "ON `personal_context_entries` (`deletedAt`)"
        )

        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `personal_context_usage_snapshots` (
                `id` TEXT NOT NULL,
                `issueId` TEXT NOT NULL,
                `stageId` TEXT NOT NULL,
                `runId` TEXT,
                `personalContextEntryId` TEXT,
                `titleSnapshot` TEXT NOT NULL,
                `contentSnapshot` TEXT,
                `contentHash` TEXT NOT NULL,
                `contentState` TEXT NOT NULL DEFAULT 'available',
                `userConfirmedAt` INTEGER NOT NULL,
                `createdAt` INTEGER NOT NULL,
                PRIMARY KEY(`id`),
                FOREIGN KEY(`issueId`) REFERENCES `issues`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT,
                FOREIGN KEY(`stageId`, `issueId`) REFERENCES `stages`(`id`, `issueId`) ON UPDATE NO ACTION ON DELETE RESTRICT,
                FOREIGN KEY(`runId`, `issueId`, `stageId`) REFERENCES `execution_runs`(`id`, `issueId`, `stageId`) ON UPDATE NO ACTION ON DELETE RESTRICT,
                FOREIGN KEY(`personalContextEntryId`) REFERENCES `personal_context_entries`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL
            )
            """.trimIndent()
        )
        database.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_personal_context_usage_snapshots_id_issueId` " +
                "ON `personal_context_usage_snapshots` (`id`, `issueId`)"
        )
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_personal_context_usage_snapshots_issueId` " +
                "ON `personal_context_usage_snapshots` (`issueId`)"
        )
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_personal_context_usage_snapshots_stageId_issueId` " +
                "ON `personal_context_usage_snapshots` (`stageId`, `issueId`)"
        )
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_personal_context_usage_snapshots_runId_issueId_stageId` " +
                "ON `personal_context_usage_snapshots` (`runId`, `issueId`, `stageId`)"
        )
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_personal_context_usage_snapshots_personalContextEntryId` " +
                "ON `personal_context_usage_snapshots` (`personalContextEntryId`)"
        )
        database.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_personal_context_usage_snapshots_runId_personalContextEntryId` " +
                "ON `personal_context_usage_snapshots` (`runId`, `personalContextEntryId`)"
        )
    }

    private fun createDraftAndArtifactTables(database: SupportSQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `stage_summary_drafts` (
                `id` TEXT NOT NULL,
                `issueId` TEXT NOT NULL,
                `stageId` TEXT NOT NULL,
                `content` TEXT NOT NULL,
                `revisionNumber` INTEGER NOT NULL,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                PRIMARY KEY(`id`),
                FOREIGN KEY(`stageId`, `issueId`) REFERENCES `stages`(`id`, `issueId`) ON UPDATE NO ACTION ON DELETE RESTRICT
            )
            """.trimIndent()
        )
        database.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_stage_summary_drafts_issueId_stageId` " +
                "ON `stage_summary_drafts` (`issueId`, `stageId`)"
        )
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_stage_summary_drafts_stageId_issueId` " +
                "ON `stage_summary_drafts` (`stageId`, `issueId`)"
        )

        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `stage_summary_draft_revisions` (
                `id` TEXT NOT NULL,
                `issueId` TEXT NOT NULL,
                `stageId` TEXT NOT NULL,
                `draftIdSnapshot` TEXT NOT NULL,
                `revisionNumber` INTEGER NOT NULL,
                `contentSnapshot` TEXT NOT NULL,
                `createdAt` INTEGER NOT NULL,
                PRIMARY KEY(`id`),
                FOREIGN KEY(`stageId`, `issueId`) REFERENCES `stages`(`id`, `issueId`) ON UPDATE NO ACTION ON DELETE RESTRICT
            )
            """.trimIndent()
        )
        database.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_stage_summary_draft_revisions_id_issueId` " +
                "ON `stage_summary_draft_revisions` (`id`, `issueId`)"
        )
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_stage_summary_draft_revisions_stageId_issueId` " +
                "ON `stage_summary_draft_revisions` (`stageId`, `issueId`)"
        )
        database.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_stage_summary_draft_revisions_issueId_stageId_revisionNumber` " +
                "ON `stage_summary_draft_revisions` (`issueId`, `stageId`, `revisionNumber`)"
        )
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_stage_summary_draft_revisions_draftIdSnapshot` " +
                "ON `stage_summary_draft_revisions` (`draftIdSnapshot`)"
        )

        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `confirmed_artifacts` (
                `id` TEXT NOT NULL,
                `issueId` TEXT NOT NULL,
                `stageId` TEXT NOT NULL,
                `title` TEXT NOT NULL,
                `content` TEXT NOT NULL,
                `artifactType` TEXT NOT NULL,
                `contentFormat` TEXT NOT NULL,
                `confirmedAt` INTEGER NOT NULL,
                `revisionOfArtifactId` TEXT,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                PRIMARY KEY(`id`),
                FOREIGN KEY(`stageId`, `issueId`) REFERENCES `stages`(`id`, `issueId`) ON UPDATE NO ACTION ON DELETE RESTRICT,
                FOREIGN KEY(`revisionOfArtifactId`) REFERENCES `confirmed_artifacts`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT
            )
            """.trimIndent()
        )
        database.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_confirmed_artifacts_id_issueId` " +
                "ON `confirmed_artifacts` (`id`, `issueId`)"
        )
        database.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_confirmed_artifacts_id_issueId_stageId` " +
                "ON `confirmed_artifacts` (`id`, `issueId`, `stageId`)"
        )
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_confirmed_artifacts_stageId_issueId` " +
                "ON `confirmed_artifacts` (`stageId`, `issueId`)"
        )
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_confirmed_artifacts_revisionOfArtifactId` " +
                "ON `confirmed_artifacts` (`revisionOfArtifactId`)"
        )
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_confirmed_artifacts_confirmedAt` " +
                "ON `confirmed_artifacts` (`confirmedAt`)"
        )

        createArtifactMessageSources(database)
        createArtifactRunSources(database)
        createArtifactDraftSources(database)
        createArtifactMaterialSources(database)
    }

    private fun createArtifactMessageSources(database: SupportSQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `artifact_message_sources` (
                `artifactId` TEXT NOT NULL,
                `issueId` TEXT NOT NULL,
                `messageId` INTEGER NOT NULL,
                `createdAt` INTEGER NOT NULL,
                PRIMARY KEY(`artifactId`, `messageId`),
                FOREIGN KEY(`artifactId`, `issueId`) REFERENCES `confirmed_artifacts`(`id`, `issueId`) ON UPDATE NO ACTION ON DELETE RESTRICT,
                FOREIGN KEY(`messageId`, `issueId`) REFERENCES `messages`(`id`, `issueId`) ON UPDATE NO ACTION ON DELETE RESTRICT
            )
            """.trimIndent()
        )
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_artifact_message_sources_artifactId_issueId` " +
                "ON `artifact_message_sources` (`artifactId`, `issueId`)"
        )
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_artifact_message_sources_messageId_issueId` " +
                "ON `artifact_message_sources` (`messageId`, `issueId`)"
        )
    }

    private fun createArtifactRunSources(database: SupportSQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `artifact_run_sources` (
                `artifactId` TEXT NOT NULL,
                `issueId` TEXT NOT NULL,
                `runId` TEXT NOT NULL,
                `createdAt` INTEGER NOT NULL,
                PRIMARY KEY(`artifactId`, `runId`),
                FOREIGN KEY(`artifactId`, `issueId`) REFERENCES `confirmed_artifacts`(`id`, `issueId`) ON UPDATE NO ACTION ON DELETE RESTRICT,
                FOREIGN KEY(`runId`, `issueId`) REFERENCES `execution_runs`(`id`, `issueId`) ON UPDATE NO ACTION ON DELETE RESTRICT
            )
            """.trimIndent()
        )
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_artifact_run_sources_artifactId_issueId` " +
                "ON `artifact_run_sources` (`artifactId`, `issueId`)"
        )
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_artifact_run_sources_runId_issueId` " +
                "ON `artifact_run_sources` (`runId`, `issueId`)"
        )
    }

    private fun createArtifactDraftSources(database: SupportSQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `artifact_draft_sources` (
                `artifactId` TEXT NOT NULL,
                `issueId` TEXT NOT NULL,
                `draftRevisionId` TEXT NOT NULL,
                `createdAt` INTEGER NOT NULL,
                PRIMARY KEY(`artifactId`, `draftRevisionId`),
                FOREIGN KEY(`artifactId`, `issueId`) REFERENCES `confirmed_artifacts`(`id`, `issueId`) ON UPDATE NO ACTION ON DELETE RESTRICT,
                FOREIGN KEY(`draftRevisionId`, `issueId`) REFERENCES `stage_summary_draft_revisions`(`id`, `issueId`) ON UPDATE NO ACTION ON DELETE RESTRICT
            )
            """.trimIndent()
        )
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_artifact_draft_sources_artifactId_issueId` " +
                "ON `artifact_draft_sources` (`artifactId`, `issueId`)"
        )
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_artifact_draft_sources_draftRevisionId_issueId` " +
                "ON `artifact_draft_sources` (`draftRevisionId`, `issueId`)"
        )
    }

    private fun createArtifactMaterialSources(database: SupportSQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `artifact_material_sources` (
                `artifactId` TEXT NOT NULL,
                `issueId` TEXT NOT NULL,
                `materialUsageSnapshotId` TEXT NOT NULL,
                `createdAt` INTEGER NOT NULL,
                PRIMARY KEY(`artifactId`, `materialUsageSnapshotId`),
                FOREIGN KEY(`artifactId`, `issueId`) REFERENCES `confirmed_artifacts`(`id`, `issueId`) ON UPDATE NO ACTION ON DELETE RESTRICT,
                FOREIGN KEY(`materialUsageSnapshotId`, `issueId`) REFERENCES `material_usage_snapshots`(`id`, `issueId`) ON UPDATE NO ACTION ON DELETE RESTRICT
            )
            """.trimIndent()
        )
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_artifact_material_sources_artifactId_issueId` " +
                "ON `artifact_material_sources` (`artifactId`, `issueId`)"
        )
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_artifact_material_sources_materialUsageSnapshotId_issueId` " +
                "ON `artifact_material_sources` (`materialUsageSnapshotId`, `issueId`)"
        )
    }

    private fun createAudioAndSkillTables(database: SupportSQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `audio_assets` (
                `id` TEXT NOT NULL,
                `issueId` TEXT NOT NULL,
                `stageId` TEXT NOT NULL,
                `sourceMessageId` INTEGER,
                `sourceArtifactId` TEXT,
                `storagePath` TEXT NOT NULL,
                `mimeType` TEXT NOT NULL,
                `format` TEXT NOT NULL,
                `sizeBytes` INTEGER NOT NULL,
                `fileState` TEXT NOT NULL DEFAULT 'pending',
                `generationKey` TEXT,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                `deletedAt` INTEGER,
                `purgeRequestedAt` INTEGER,
                PRIMARY KEY(`id`),
                FOREIGN KEY(`stageId`, `issueId`) REFERENCES `stages`(`id`, `issueId`) ON UPDATE NO ACTION ON DELETE RESTRICT,
                FOREIGN KEY(`sourceMessageId`, `issueId`, `stageId`) REFERENCES `messages`(`id`, `issueId`, `stageId`) ON UPDATE NO ACTION ON DELETE RESTRICT,
                FOREIGN KEY(`sourceArtifactId`, `issueId`, `stageId`) REFERENCES `confirmed_artifacts`(`id`, `issueId`, `stageId`) ON UPDATE NO ACTION ON DELETE RESTRICT
            )
            """.trimIndent()
        )
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_audio_assets_stageId_issueId` " +
                "ON `audio_assets` (`stageId`, `issueId`)"
        )
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_audio_assets_sourceMessageId_issueId_stageId` " +
                "ON `audio_assets` (`sourceMessageId`, `issueId`, `stageId`)"
        )
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_audio_assets_sourceArtifactId_issueId_stageId` " +
                "ON `audio_assets` (`sourceArtifactId`, `issueId`, `stageId`)"
        )
        database.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_audio_assets_storagePath` " +
                "ON `audio_assets` (`storagePath`)"
        )
        database.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_audio_assets_generationKey` " +
                "ON `audio_assets` (`generationKey`)"
        )
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_audio_assets_deletedAt` " +
                "ON `audio_assets` (`deletedAt`)"
        )

        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `official_skill_combinations` (
                `id` TEXT NOT NULL,
                `name` TEXT NOT NULL,
                `isEnabled` INTEGER NOT NULL DEFAULT 1,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                `deletedAt` INTEGER,
                PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_official_skill_combinations_name` " +
                "ON `official_skill_combinations` (`name`)"
        )
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_official_skill_combinations_deletedAt` " +
                "ON `official_skill_combinations` (`deletedAt`)"
        )

        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `official_skill_combination_members` (
                `combinationId` TEXT NOT NULL,
                `officialSkillId` TEXT NOT NULL,
                `position` INTEGER NOT NULL,
                `defaultResponsibility` TEXT,
                `createdAt` INTEGER NOT NULL,
                PRIMARY KEY(`combinationId`, `officialSkillId`),
                FOREIGN KEY(`combinationId`) REFERENCES `official_skill_combinations`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT
            )
            """.trimIndent()
        )
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_official_skill_combination_members_combinationId` " +
                "ON `official_skill_combination_members` (`combinationId`)"
        )
        database.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_official_skill_combination_members_combinationId_position` " +
                "ON `official_skill_combination_members` (`combinationId`, `position`)"
        )
    }

    private fun createLifecycleTable(database: SupportSQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `issue_lifecycle` (
                `issueId` TEXT NOT NULL,
                `state` TEXT NOT NULL DEFAULT 'active',
                `previousState` TEXT,
                `stateChangedAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                `archivedAt` INTEGER,
                `trashedAt` INTEGER,
                `purgeRequestedAt` INTEGER,
                PRIMARY KEY(`issueId`),
                FOREIGN KEY(`issueId`) REFERENCES `issues`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT
            )
            """.trimIndent()
        )
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_issue_lifecycle_state` " +
                "ON `issue_lifecycle` (`state`)"
        )
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_issue_lifecycle_purgeRequestedAt` " +
                "ON `issue_lifecycle` (`purgeRequestedAt`)"
        )
    }

    private fun createRequiredParentIndexes(database: SupportSQLiteDatabase) {
        database.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_execution_runs_id_issueId` " +
                "ON `execution_runs` (`id`, `issueId`)"
        )
        database.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_messages_id_issueId` " +
                "ON `messages` (`id`, `issueId`)"
        )
        database.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_messages_id_issueId_stageId` " +
                "ON `messages` (`id`, `issueId`, `stageId`)"
        )
    }

    private fun backfillIssueLifecycle(database: SupportSQLiteDatabase) {
        database.execSQL(
            """
            INSERT OR IGNORE INTO `issue_lifecycle` (
                `issueId`, `state`, `previousState`, `stateChangedAt`, `updatedAt`,
                `archivedAt`, `trashedAt`, `purgeRequestedAt`
            )
            SELECT `id`, 'active', NULL, `updatedAt`, `updatedAt`, NULL, NULL, NULL
            FROM `issues`
            """.trimIndent()
        )
    }
}
