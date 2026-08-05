package com.elio.jianyu.data

import androidx.sqlite.db.SupportSQLiteDatabase

internal object LegacyDatabaseMigrationSupport {
    fun createCoreDomainTablesForVersion6(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `issues` (
                `id` TEXT NOT NULL,
                `title` TEXT NOT NULL,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                `legacyChatSessionId` INTEGER,
                PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_issues_legacyChatSessionId` " +
                "ON `issues` (`legacyChatSessionId`)"
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `stages` (
                `id` TEXT NOT NULL,
                `issueId` TEXT NOT NULL,
                `sequenceIndex` INTEGER NOT NULL,
                `title` TEXT NOT NULL,
                `objective` TEXT NOT NULL,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                PRIMARY KEY(`id`),
                FOREIGN KEY(`issueId`) REFERENCES `issues`(`id`)
                    ON UPDATE NO ACTION ON DELETE RESTRICT
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_stages_issueId` ON `stages` (`issueId`)")
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_stages_issueId_sequenceIndex` " +
                "ON `stages` (`issueId`, `sequenceIndex`)"
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_stages_id_issueId` " +
                "ON `stages` (`id`, `issueId`)"
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `execution_runs` (
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
                PRIMARY KEY(`id`),
                FOREIGN KEY(`stageId`, `issueId`) REFERENCES `stages`(`id`, `issueId`)
                    ON UPDATE NO ACTION ON DELETE RESTRICT,
                FOREIGN KEY(`retryOfRunId`) REFERENCES `execution_runs`(`id`)
                    ON UPDATE NO ACTION ON DELETE RESTRICT
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_execution_runs_stageId_issueId` " +
                "ON `execution_runs` (`stageId`, `issueId`)"
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_execution_runs_idempotencyKey` " +
                "ON `execution_runs` (`idempotencyKey`)"
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_execution_runs_id_issueId_stageId` " +
                "ON `execution_runs` (`id`, `issueId`, `stageId`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_execution_runs_triggerMessageId` " +
                "ON `execution_runs` (`triggerMessageId`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_execution_runs_retryOfRunId` " +
                "ON `execution_runs` (`retryOfRunId`)"
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `execution_participant_snapshots` (
                `id` TEXT NOT NULL,
                `runId` TEXT NOT NULL,
                `sourceType` TEXT NOT NULL,
                `sourceId` TEXT NOT NULL,
                `displayName` TEXT NOT NULL,
                `avatar` TEXT NOT NULL,
                `skillAssetPath` TEXT NOT NULL,
                `systemPrompt` TEXT NOT NULL,
                `configurationJson` TEXT NOT NULL,
                `defaultResponsibility` TEXT NOT NULL,
                `position` INTEGER NOT NULL,
                `createdAt` INTEGER NOT NULL,
                PRIMARY KEY(`id`),
                FOREIGN KEY(`runId`) REFERENCES `execution_runs`(`id`)
                    ON UPDATE NO ACTION ON DELETE RESTRICT
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_execution_participant_snapshots_runId` " +
                "ON `execution_participant_snapshots` (`runId`)"
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS " +
                "`index_execution_participant_snapshots_runId_position` " +
                "ON `execution_participant_snapshots` (`runId`, `position`)"
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS " +
                "`index_execution_participant_snapshots_runId_sourceType_sourceId` " +
                "ON `execution_participant_snapshots` (`runId`, `sourceType`, `sourceId`)"
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS " +
                "`index_execution_participant_snapshots_id_runId` " +
                "ON `execution_participant_snapshots` (`id`, `runId`)"
        )
    }

    fun backfillLegacySessionsForVersion6(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            INSERT OR IGNORE INTO `issues` (
                `id`, `title`, `createdAt`, `updatedAt`, `legacyChatSessionId`
            )
            SELECT 'legacy-chat-' || `id`, `title`, `createdAt`, `createdAt`, `id`
            FROM `chat_sessions`
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT OR IGNORE INTO `stages` (
                `id`, `issueId`, `sequenceIndex`, `title`, `objective`, `createdAt`, `updatedAt`
            )
            SELECT
                'legacy-chat-' || `id` || '-stage-0',
                'legacy-chat-' || `id`,
                0,
                '初始阶段',
                '',
                `createdAt`,
                `createdAt`
            FROM `chat_sessions`
            """.trimIndent()
        )
    }

    fun rebuildMessagesForVersion6(db: SupportSQLiteDatabase) {
        db.execSQL("DROP TABLE IF EXISTS `_new_messages`")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `_new_messages` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `chatId` INTEGER NOT NULL,
                `senderId` TEXT NOT NULL,
                `senderName` TEXT NOT NULL,
                `avatar` TEXT NOT NULL,
                `text` TEXT NOT NULL,
                `timestamp` INTEGER NOT NULL,
                `isPending` INTEGER NOT NULL,
                `roundIndex` INTEGER NOT NULL DEFAULT 0,
                `audioFilePath` TEXT,
                `audioFormat` TEXT,
                `audioSizeBytes` INTEGER NOT NULL DEFAULT 0,
                `issueId` TEXT,
                `stageId` TEXT,
                `executionRunId` TEXT,
                `participantSnapshotId` TEXT,
                FOREIGN KEY(`issueId`) REFERENCES `issues`(`id`)
                    ON UPDATE NO ACTION ON DELETE RESTRICT,
                FOREIGN KEY(`stageId`, `issueId`) REFERENCES `stages`(`id`, `issueId`)
                    ON UPDATE NO ACTION ON DELETE RESTRICT,
                FOREIGN KEY(`executionRunId`, `issueId`, `stageId`)
                    REFERENCES `execution_runs`(`id`, `issueId`, `stageId`)
                    ON UPDATE NO ACTION ON DELETE RESTRICT,
                FOREIGN KEY(`participantSnapshotId`, `executionRunId`)
                    REFERENCES `execution_participant_snapshots`(`id`, `runId`)
                    ON UPDATE NO ACTION ON DELETE RESTRICT
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO `_new_messages` (
                `id`, `chatId`, `senderId`, `senderName`, `avatar`, `text`,
                `timestamp`, `isPending`, `roundIndex`, `audioFilePath`,
                `audioFormat`, `audioSizeBytes`, `issueId`, `stageId`,
                `executionRunId`, `participantSnapshotId`
            )
            SELECT
                old.`id`, old.`chatId`, old.`senderId`, old.`senderName`, old.`avatar`, old.`text`,
                old.`timestamp`, old.`isPending`, old.`roundIndex`, old.`audioFilePath`,
                old.`audioFormat`, old.`audioSizeBytes`,
                CASE WHEN EXISTS (
                    SELECT 1 FROM `chat_sessions` session WHERE session.`id` = old.`chatId`
                ) THEN 'legacy-chat-' || old.`chatId` ELSE NULL END,
                CASE WHEN EXISTS (
                    SELECT 1 FROM `chat_sessions` session WHERE session.`id` = old.`chatId`
                ) THEN 'legacy-chat-' || old.`chatId` || '-stage-0' ELSE NULL END,
                NULL,
                NULL
            FROM `messages` old
            """.trimIndent()
        )
        db.execSQL("DROP TABLE `messages`")
        db.execSQL("ALTER TABLE `_new_messages` RENAME TO `messages`")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_messages_issueId` ON `messages` (`issueId`)")
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_messages_stageId_issueId` " +
                "ON `messages` (`stageId`, `issueId`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_messages_executionRunId_issueId_stageId` " +
                "ON `messages` (`executionRunId`, `issueId`, `stageId`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_messages_participantSnapshotId_executionRunId` " +
                "ON `messages` (`participantSnapshotId`, `executionRunId`)"
        )
    }

    fun rebuildCharactersForVersion5(db: SupportSQLiteDatabase) {
        val skillAssetPathExpression = if (columnExists(db, "characters", "skillAssetPath")) {
            "skillAssetPath"
        } else {
            "''"
        }
        val skillDescriptionVectorExpression =
            if (columnExists(db, "characters", "skillDescriptionVector")) {
                "skillDescriptionVector"
            } else {
                "''"
            }
        val voiceConfigExpression = if (columnExists(db, "characters", "voiceConfig")) {
            "COALESCE(voiceConfig, 'Aoede')"
        } else {
            "'Aoede'"
        }
        db.execSQL("DROP TABLE IF EXISTS _new_characters")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS _new_characters (
                id TEXT NOT NULL,
                name TEXT NOT NULL,
                avatar TEXT NOT NULL,
                tagline TEXT NOT NULL,
                systemPrompt TEXT NOT NULL,
                skillAssetPath TEXT NOT NULL,
                `order` INTEGER NOT NULL,
                isActive INTEGER NOT NULL,
                skillDescriptionVector TEXT NOT NULL,
                voiceConfig TEXT NOT NULL DEFAULT 'Aoede',
                PRIMARY KEY(id)
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO _new_characters (
                id, name, avatar, tagline, systemPrompt, skillAssetPath,
                `order`, isActive, skillDescriptionVector, voiceConfig
            )
            SELECT
                id, name, avatar, tagline, systemPrompt, $skillAssetPathExpression,
                `order`, isActive, $skillDescriptionVectorExpression, $voiceConfigExpression
            FROM characters
            """.trimIndent()
        )
        db.execSQL("DROP TABLE characters")
        db.execSQL("ALTER TABLE _new_characters RENAME TO characters")
    }

    fun createCharacterGroupsTable(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS character_groups (
                id TEXT NOT NULL,
                name TEXT NOT NULL,
                description TEXT NOT NULL,
                characterIds TEXT NOT NULL,
                isPreset INTEGER NOT NULL,
                PRIMARY KEY(id)
            )
            """.trimIndent()
        )
    }

    fun seedPresetGroups(db: SupportSQLiteDatabase) {
        val insertSql =
            "INSERT OR IGNORE INTO character_groups " +
                "(id, name, description, characterIds, isPreset) VALUES (?, ?, ?, ?, ?)"
        db.execSQL(
            insertSql,
            arrayOf(
                "silicon_valley_venture",
                "硅谷创投",
                "聚焦商业突破、无需许可的杠杆、高科技创业与去中心化浪潮的硅谷科技狂人与投资导师组合",
                "elon_musk,naval_ravikant,paul_graham,zhang_yiming,changpeng_zhao,tim_cook",
                1
            )
        )
        db.execSQL(
            insertSql,
            arrayOf(
                "philosophy_logic",
                "哲学与心理逻辑",
                "解构认知偏差，关注尾部风险，探究人性和科学底层的跨学科终身学习大师与思考者",
                "richard_feynman,charlie_munger,nassim_taleb,sigmund_freud,andrej_karpathy,ilya_sutskever",
                1
            )
        )
        db.execSQL(
            insertSql,
            arrayOf(
                "traffic_attention",
                "流量与注意力经济",
                "深谙社交媒体、爆款法则、事件营销与流量操盘的全球顶级创作者与博弈专家",
                "mr_beast,justin_sun,donald_trump,feng_ge,x_mentor",
                1
            )
        )
        db.execSQL(
            insertSql,
            arrayOf(
                "planning_growth",
                "规划与个人成长",
                "刺破社会幻泡，推崇做对的事与长期主义的个人成长与升学志愿导师",
                "zhang_xuefeng,duan_yongping,charlie_munger,naval_ravikant",
                1
            )
        )
    }

    fun columnExists(
        db: SupportSQLiteDatabase,
        tableName: String,
        columnName: String
    ): Boolean {
        db.query("PRAGMA table_info(`$tableName`)").use { cursor ->
            val nameIndex = cursor.getColumnIndex("name")
            if (nameIndex != -1) {
                while (cursor.moveToNext()) {
                    if (cursor.getString(nameIndex).equals(columnName, ignoreCase = true)) {
                        return true
                    }
                }
            }
        }
        return false
    }
}
