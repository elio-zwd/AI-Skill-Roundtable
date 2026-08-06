package com.elio.jianyu.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object StageAdvancementMigration {
    val MIGRATION_10_11 = object : Migration(10, 11) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `stage_advancements` (
                    `stageId` TEXT NOT NULL,
                    `issueId` TEXT NOT NULL,
                    `sourceStageId` TEXT NOT NULL,
                    `operationId` TEXT NOT NULL,
                    `payloadHash` TEXT NOT NULL,
                    `realitySupport` INTEGER NOT NULL,
                    `thinkingExpansion` INTEGER NOT NULL,
                    `objective` TEXT NOT NULL,
                    `expectedOutput` TEXT NOT NULL,
                    `confirmedAt` INTEGER NOT NULL,
                    `createdAt` INTEGER NOT NULL,
                    PRIMARY KEY(`stageId`),
                    FOREIGN KEY(`stageId`, `issueId`) REFERENCES `stages`(`id`, `issueId`)
                        ON UPDATE NO ACTION ON DELETE RESTRICT,
                    FOREIGN KEY(`sourceStageId`, `issueId`) REFERENCES `stages`(`id`, `issueId`)
                        ON UPDATE NO ACTION ON DELETE RESTRICT
                )
                """.trimIndent(),
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_stage_advancements_stageId_issueId` " +
                    "ON `stage_advancements` (`stageId`, `issueId`)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_stage_advancements_sourceStageId_issueId` " +
                    "ON `stage_advancements` (`sourceStageId`, `issueId`)",
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_stage_advancements_operationId` " +
                    "ON `stage_advancements` (`operationId`)",
            )

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `stage_advancement_measures` (
                    `stageId` TEXT NOT NULL,
                    `issueId` TEXT NOT NULL,
                    `measure` TEXT NOT NULL,
                    `position` INTEGER NOT NULL,
                    PRIMARY KEY(`stageId`, `measure`),
                    FOREIGN KEY(`stageId`, `issueId`)
                        REFERENCES `stage_advancements`(`stageId`, `issueId`)
                        ON UPDATE NO ACTION ON DELETE RESTRICT
                )
                """.trimIndent(),
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_stage_advancement_measures_stageId_issueId` " +
                    "ON `stage_advancement_measures` (`stageId`, `issueId`)",
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_stage_advancement_measures_stageId_position` " +
                    "ON `stage_advancement_measures` (`stageId`, `position`)",
            )

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `stage_advancement_skill_members` (
                    `stageId` TEXT NOT NULL,
                    `issueId` TEXT NOT NULL,
                    `officialSkillId` TEXT NOT NULL,
                    `position` INTEGER NOT NULL,
                    `responsibility` TEXT NOT NULL,
                    `sourceRunId` TEXT,
                    `sourceParticipantSnapshotId` TEXT,
                    `catalogVersionBasis` TEXT,
                    `confirmedAt` INTEGER NOT NULL,
                    PRIMARY KEY(`stageId`, `officialSkillId`),
                    FOREIGN KEY(`stageId`, `issueId`)
                        REFERENCES `stage_advancements`(`stageId`, `issueId`)
                        ON UPDATE NO ACTION ON DELETE RESTRICT
                )
                """.trimIndent(),
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_stage_advancement_skill_members_stageId_issueId` " +
                    "ON `stage_advancement_skill_members` (`stageId`, `issueId`)",
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS " +
                    "`index_stage_advancement_skill_members_stageId_position` " +
                    "ON `stage_advancement_skill_members` (`stageId`, `position`)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_stage_advancement_skill_members_sourceRunId` " +
                    "ON `stage_advancement_skill_members` (`sourceRunId`)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS " +
                    "`index_stage_advancement_skill_members_sourceParticipantSnapshotId` " +
                    "ON `stage_advancement_skill_members` (`sourceParticipantSnapshotId`)",
            )

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `stage_advancement_materials` (
                    `stageId` TEXT NOT NULL,
                    `issueId` TEXT NOT NULL,
                    `materialReferenceId` TEXT NOT NULL,
                    `position` INTEGER NOT NULL,
                    `inheritedAt` INTEGER NOT NULL,
                    PRIMARY KEY(`stageId`, `materialReferenceId`),
                    FOREIGN KEY(`stageId`, `issueId`)
                        REFERENCES `stage_advancements`(`stageId`, `issueId`)
                        ON UPDATE NO ACTION ON DELETE RESTRICT,
                    FOREIGN KEY(`materialReferenceId`, `issueId`)
                        REFERENCES `material_references`(`id`, `issueId`)
                        ON UPDATE NO ACTION ON DELETE RESTRICT
                )
                """.trimIndent(),
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_stage_advancement_materials_stageId_issueId` " +
                    "ON `stage_advancement_materials` (`stageId`, `issueId`)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_stage_advancement_materials_materialReferenceId_issueId` " +
                    "ON `stage_advancement_materials` (`materialReferenceId`, `issueId`)",
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_stage_advancement_materials_stageId_position` " +
                    "ON `stage_advancement_materials` (`stageId`, `position`)",
            )

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `stage_advancement_artifacts` (
                    `stageId` TEXT NOT NULL,
                    `issueId` TEXT NOT NULL,
                    `artifactId` TEXT NOT NULL,
                    `position` INTEGER NOT NULL,
                    `inheritedAt` INTEGER NOT NULL,
                    PRIMARY KEY(`stageId`, `artifactId`),
                    FOREIGN KEY(`stageId`, `issueId`)
                        REFERENCES `stage_advancements`(`stageId`, `issueId`)
                        ON UPDATE NO ACTION ON DELETE RESTRICT,
                    FOREIGN KEY(`artifactId`, `issueId`)
                        REFERENCES `confirmed_artifacts`(`id`, `issueId`)
                        ON UPDATE NO ACTION ON DELETE RESTRICT
                )
                """.trimIndent(),
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_stage_advancement_artifacts_stageId_issueId` " +
                    "ON `stage_advancement_artifacts` (`stageId`, `issueId`)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_stage_advancement_artifacts_artifactId_issueId` " +
                    "ON `stage_advancement_artifacts` (`artifactId`, `issueId`)",
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_stage_advancement_artifacts_stageId_position` " +
                    "ON `stage_advancement_artifacts` (`stageId`, `position`)",
            )
        }
    }
}
