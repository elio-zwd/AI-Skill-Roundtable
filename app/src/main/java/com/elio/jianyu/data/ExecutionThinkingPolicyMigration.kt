package com.elio.jianyu.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object ExecutionThinkingPolicyMigration {
    val MIGRATION_12_13: Migration = object : Migration(12, 13) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE `issues` ADD COLUMN `defaultThinkingPolicy` " +
                    "TEXT NOT NULL DEFAULT 'auto'",
            )

            val tableOrder = dependentDropOrder(db, "execution_runs")
            val backups = tableOrder.mapIndexed { index, tableName ->
                backupTable(
                    db = db,
                    tableName = tableName,
                    temporaryTableName = "_v13_backup_$index",
                )
            }

            tableOrder.dropLast(1).forEach { tableName ->
                db.execSQL("DROP TABLE ${quoteIdentifier(tableName)}")
            }
            // 重试 Run 的自引用外键会阻止删除父 Run；完整数据已在临时表中保留。
            db.execSQL("UPDATE `execution_runs` SET `retryOfRunId` = NULL")
            db.execSQL("DROP TABLE `execution_runs`")

            createExecutionRunsVersion13Table(db)
            restoreExecutionRuns(db, backups.last().temporaryTableName)
            tableOrder.dropLast(1).asReversed().forEach { tableName ->
                restoreTable(db, backups.first { it.tableName == tableName })
            }
            createExecutionRunsVersion13Indexes(db)
            backups
                .filterNot { it.tableName == "execution_runs" }
                .flatMap(TableBackup::indexSql)
                .forEach(db::execSQL)
            backups.forEach { backup ->
                db.execSQL("DROP TABLE ${quoteIdentifier(backup.temporaryTableName)}")
            }
        }
    }

    private fun dependentDropOrder(
        db: SupportSQLiteDatabase,
        rootTable: String,
    ): List<String> {
        val parentsByChild = tableNames(db).associateWith { tableName ->
            foreignKeyParents(db, tableName)
        }
        val childrenByParent = buildMap<String, MutableList<String>> {
            parentsByChild.forEach { (child, parents) ->
                parents.forEach { parent -> getOrPut(parent) { mutableListOf() }.add(child) }
            }
        }
        val visited = mutableSetOf<String>()
        val order = mutableListOf<String>()

        fun visit(tableName: String) {
            if (!visited.add(tableName)) return
            childrenByParent[tableName]
                .orEmpty()
                .filterNot { it == tableName }
                .forEach(::visit)
            order += tableName
        }

        visit(rootTable)
        return order
    }

    private fun tableNames(db: SupportSQLiteDatabase): List<String> = db.query(
        "SELECT `name` FROM `sqlite_master` " +
            "WHERE `type` = 'table' AND `name` NOT LIKE 'sqlite_%'",
    ).use { cursor ->
        buildList {
            while (cursor.moveToNext()) {
                add(cursor.getString(cursor.getColumnIndexOrThrow("name")))
            }
        }
    }

    private fun foreignKeyParents(
        db: SupportSQLiteDatabase,
        tableName: String,
    ): Set<String> = db.query(
        "PRAGMA foreign_key_list(${quoteIdentifier(tableName)})",
    ).use { cursor ->
        buildSet {
            while (cursor.moveToNext()) {
                add(cursor.getString(cursor.getColumnIndexOrThrow("table")))
            }
        }
    }

    private fun backupTable(
        db: SupportSQLiteDatabase,
        tableName: String,
        temporaryTableName: String,
    ): TableBackup {
        val createSql = scalarString(
            db,
            "SELECT `sql` FROM `sqlite_master` WHERE `type` = 'table' AND `name` = ?",
            arrayOf(tableName),
        )
        val indexSql = db.query(
            "SELECT `sql` FROM `sqlite_master` " +
                "WHERE `type` = 'index' AND `tbl_name` = ? AND `sql` IS NOT NULL",
            arrayOf(tableName),
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(cursor.getString(cursor.getColumnIndexOrThrow("sql")))
                }
            }
        }
        db.execSQL("DROP TABLE IF EXISTS ${quoteIdentifier(temporaryTableName)}")
        db.execSQL(
            "CREATE TEMP TABLE ${quoteIdentifier(temporaryTableName)} AS " +
                "SELECT * FROM ${quoteIdentifier(tableName)}",
        )
        return TableBackup(tableName, temporaryTableName, createSql, indexSql)
    }

    private fun restoreTable(
        db: SupportSQLiteDatabase,
        backup: TableBackup,
    ) {
        db.execSQL(backup.createSql)
        db.execSQL(
            "INSERT INTO ${quoteIdentifier(backup.tableName)} " +
                "SELECT * FROM ${quoteIdentifier(backup.temporaryTableName)}",
        )
    }

    private fun restoreExecutionRuns(
        db: SupportSQLiteDatabase,
        temporaryTableName: String,
    ) {
        db.execSQL(
            """
            INSERT INTO `execution_runs` (
                `id`, `issueId`, `stageId`, `triggerMessageId`, `idempotencyKey`, `status`,
                `retryOfRunId`, `createdAt`, `updatedAt`, `startedAt`, `finishedAt`, `stoppedAt`,
                `failureCode`, `failureMessage`, `runKind`, `parentRunId`, `discussionId`,
                `historyScope`, `actualModelId`, `actualThinkingLevel`, `thinkingLevelSource`
            )
            SELECT
                `id`, `issueId`, `stageId`, `triggerMessageId`, `idempotencyKey`, `status`,
                `retryOfRunId`, `createdAt`, `updatedAt`, `startedAt`, `finishedAt`, `stoppedAt`,
                `failureCode`, `failureMessage`, `runKind`, `parentRunId`, `discussionId`,
                `historyScope`,
                'gemini-3.6-flash',
                CASE
                    WHEN `runKind` IN ('cross_discussion_response', 'cross_discussion_synthesis')
                        THEN 'high'
                    ELSE 'medium'
                END,
                'auto_routed'
            FROM ${quoteIdentifier(temporaryTableName)}
            """.trimIndent(),
        )
    }

    private fun createExecutionRunsVersion13Table(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE `execution_runs` (
                `id` TEXT NOT NULL,
                `issueId` TEXT NOT NULL,
                `stageId` TEXT NOT NULL,
                `triggerMessageId` INTEGER,
                `idempotencyKey` TEXT NOT NULL,
                `status` TEXT NOT NULL DEFAULT 'not_started',
                `retryOfRunId` TEXT,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                `startedAt` INTEGER,
                `finishedAt` INTEGER,
                `stoppedAt` INTEGER,
                `failureCode` TEXT,
                `failureMessage` TEXT,
                `runKind` TEXT NOT NULL DEFAULT 'standard',
                `parentRunId` TEXT,
                `discussionId` TEXT,
                `historyScope` TEXT NOT NULL DEFAULT 'full_stage',
                `actualModelId` TEXT NOT NULL,
                `actualThinkingLevel` TEXT NOT NULL,
                `thinkingLevelSource` TEXT NOT NULL,
                PRIMARY KEY(`id`),
                FOREIGN KEY(`stageId`, `issueId`) REFERENCES `stages`(`id`, `issueId`)
                    ON UPDATE NO ACTION ON DELETE RESTRICT,
                FOREIGN KEY(`retryOfRunId`) REFERENCES `execution_runs`(`id`)
                    ON UPDATE NO ACTION ON DELETE RESTRICT
            )
            """.trimIndent(),
        )
    }

    private fun createExecutionRunsVersion13Indexes(db: SupportSQLiteDatabase) {
        listOf(
            "CREATE INDEX `index_execution_runs_stageId_issueId` " +
                "ON `execution_runs` (`stageId`, `issueId`)",
            "CREATE UNIQUE INDEX `index_execution_runs_idempotencyKey` " +
                "ON `execution_runs` (`idempotencyKey`)",
            "CREATE UNIQUE INDEX `index_execution_runs_id_issueId` " +
                "ON `execution_runs` (`id`, `issueId`)",
            "CREATE UNIQUE INDEX `index_execution_runs_id_issueId_stageId` " +
                "ON `execution_runs` (`id`, `issueId`, `stageId`)",
            "CREATE INDEX `index_execution_runs_triggerMessageId` " +
                "ON `execution_runs` (`triggerMessageId`)",
            "CREATE INDEX `index_execution_runs_retryOfRunId` " +
                "ON `execution_runs` (`retryOfRunId`)",
            "CREATE INDEX `index_execution_runs_parentRunId` " +
                "ON `execution_runs` (`parentRunId`)",
            "CREATE INDEX `index_execution_runs_discussionId` " +
                "ON `execution_runs` (`discussionId`)",
            "CREATE INDEX `index_execution_runs_stageId_runKind` " +
                "ON `execution_runs` (`stageId`, `runKind`)",
        ).forEach(db::execSQL)
    }

    private fun scalarString(
        db: SupportSQLiteDatabase,
        sql: String,
        bindArgs: Array<String>,
    ): String = db.query(sql, bindArgs).use { cursor ->
        check(cursor.moveToFirst()) { "迁移备份时找不到数据库对象" }
        cursor.getString(0)
    }

    private fun quoteIdentifier(identifier: String): String =
        "\"${identifier.replace("\"", "\"\"")}\""

    private data class TableBackup(
        val tableName: String,
        val temporaryTableName: String,
        val createSql: String,
        val indexSql: List<String>,
    )
}
