package com.elio.jianyu.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** 将执行预算改为不可拦截的调用次数记录，并保留历史已调用次数。 */
object ExecutionApiUsageMigration {
    val MIGRATION_13_14: Migration = object : Migration(13, 14) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                """
                CREATE TABLE `execution_run_budgets_new` (
                    `rootRunId` TEXT NOT NULL,
                    `usedApiCalls` INTEGER NOT NULL DEFAULT 0,
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
            database.execSQL(
                """
                INSERT INTO `execution_run_budgets_new` (
                    `rootRunId`, `usedApiCalls`, `maxCharacters`,
                    `maxSearchQueriesPerCharacter`, `maxOutputTokensPerAnswer`,
                    `closed`, `updatedAt`
                )
                SELECT
                    `rootRunId`, `usedApiCalls`, `maxCharacters`,
                    `maxSearchQueriesPerCharacter`, `maxOutputTokensPerAnswer`,
                    `closed`, `updatedAt`
                FROM `execution_run_budgets`
                """.trimIndent(),
            )
            database.execSQL("DROP TABLE `execution_run_budgets`")
            database.execSQL(
                "ALTER TABLE `execution_run_budgets_new` RENAME TO `execution_run_budgets`",
            )
        }
    }
}
