package com.elio.jianyu.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object MaterialContextMigration {
    val MIGRATION_8_9 = object : Migration(8, 9) {
        override fun migrate(database: SupportSQLiteDatabase) {
            migrateMaterials(database)
            migratePersonalContexts(database)
            migrateUsageSnapshots(database)
        }
    }

    private fun migrateMaterials(database: SupportSQLiteDatabase) {
        database.execSQL(
            "ALTER TABLE `material_references` ADD COLUMN `lifecycleState` " +
                "TEXT NOT NULL DEFAULT 'active'",
        )
        database.execSQL(
            "ALTER TABLE `material_references` ADD COLUMN `sensitive` " +
                "INTEGER NOT NULL DEFAULT 0",
        )
        database.execSQL("ALTER TABLE `material_references` ADD COLUMN `disabledAt` INTEGER")
        database.execSQL("ALTER TABLE `material_references` ADD COLUMN `archivedAt` INTEGER")
        database.execSQL("ALTER TABLE `material_references` ADD COLUMN `purgedAt` INTEGER")
        database.execSQL(
            """
            UPDATE `material_references`
            SET `lifecycleState` = CASE
                WHEN `purgeRequestedAt` IS NOT NULL THEN 'purge_requested'
                WHEN `deletedAt` IS NOT NULL THEN 'deleted'
                ELSE 'active'
            END
            """.trimIndent(),
        )
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_material_references_lifecycleState` " +
                "ON `material_references` (`lifecycleState`)",
        )
    }

    private fun migratePersonalContexts(database: SupportSQLiteDatabase) {
        database.execSQL(
            "ALTER TABLE `personal_context_entries` ADD COLUMN `lifecycleState` " +
                "TEXT NOT NULL DEFAULT 'active'",
        )
        database.execSQL(
            "ALTER TABLE `personal_context_entries` ADD COLUMN `sensitive` " +
                "INTEGER NOT NULL DEFAULT 0",
        )
        database.execSQL("ALTER TABLE `personal_context_entries` ADD COLUMN `disabledAt` INTEGER")
        database.execSQL("ALTER TABLE `personal_context_entries` ADD COLUMN `archivedAt` INTEGER")
        database.execSQL("ALTER TABLE `personal_context_entries` ADD COLUMN `purgedAt` INTEGER")
        database.execSQL(
            """
            UPDATE `personal_context_entries`
            SET `lifecycleState` = CASE
                WHEN `purgeRequestedAt` IS NOT NULL THEN 'purge_requested'
                WHEN `deletedAt` IS NOT NULL THEN 'deleted'
                WHEN `isEnabled` = 0 THEN 'disabled'
                ELSE 'active'
            END
            """.trimIndent(),
        )
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_personal_context_entries_lifecycleState` " +
                "ON `personal_context_entries` (`lifecycleState`)",
        )
    }

    private fun migrateUsageSnapshots(database: SupportSQLiteDatabase) {
        database.execSQL(
            "ALTER TABLE `material_usage_snapshots` ADD COLUMN `networkAllowed` " +
                "INTEGER NOT NULL DEFAULT 0",
        )
        database.execSQL(
            "ALTER TABLE `material_usage_snapshots` ADD COLUMN `sensitive` " +
                "INTEGER NOT NULL DEFAULT 1",
        )
        database.execSQL(
            "ALTER TABLE `personal_context_usage_snapshots` ADD COLUMN `networkAllowed` " +
                "INTEGER NOT NULL DEFAULT 0",
        )
        database.execSQL(
            "ALTER TABLE `personal_context_usage_snapshots` ADD COLUMN `sensitive` " +
                "INTEGER NOT NULL DEFAULT 1",
        )
    }
}
