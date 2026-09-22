package com.elio.jianyu.data

/** 备份导入的纯领域载荷；UI 只负责文件选择、密码和结果展示。 */
data class BackupImportPayload(
    val issues: List<BackupImportIssue>,
    val materials: List<BackupImportMaterial>,
    val personalContexts: List<BackupImportPersonalContext>,
)

data class BackupImportIssue(
    val id: String,
    val title: String,
    val messages: List<BackupImportMessage>,
    val artifacts: List<BackupImportArtifact>,
)

data class BackupImportMessage(val sender: String, val text: String)

data class BackupImportArtifact(val id: String, val title: String, val type: String, val content: String)

data class BackupImportMaterial(val id: String, val title: String, val sourceType: String, val content: String)

data class BackupImportPersonalContext(
    val id: String,
    val title: String,
    val sensitive: Boolean,
    val content: String,
)

data class BackupImportStats(
    val issues: Int,
    val artifacts: Int,
    val materials: Int,
    val personalContexts: Int,
)

/**
 * 合并备份数据，不删除或覆盖现有行。重复记录交给 Repository 的幂等/冲突规则处理。
 * 旧聊天的消息没有正式执行 Run，因此以可追溯 Markdown 成果保存，避免伪造执行元数据。
 */
suspend fun JianyuRepository.importBackup(payload: BackupImportPayload): BackupImportStats {
    val stamp = System.currentTimeMillis()
    val issueTargets = linkedMapOf<String, Pair<String, String>>()
    var issueCount = 0
    var artifactCount = 0
    var materialCount = 0
    var personalCount = 0

    payload.issues.forEach { exported ->
        val safeOriginalId = exported.id.replace(Regex("[^A-Za-z0-9_-]"), "_")
        val issueId = if (exported.id.startsWith("legacy-chat-")) {
            "restored-dialog-$safeOriginalId-$stamp"
        } else {
            exported.id.ifBlank { "restored-dialog-$safeOriginalId-$stamp" }
        }
        val stageId = "$issueId-stage-0"
        val result = saveIssue(
            SaveIssueCommand(
                issueId = issueId,
                title = exported.title.ifBlank { "导入对话" },
                initialStageId = stageId,
                initialStageTitle = "导入对话节点",
                initialObjective = "从本地备份恢复的对话资料",
                createdAt = stamp,
            )
        )
        if (result is RepositoryResult.Success) {
            issueTargets[exported.id] = issueId to stageId
            issueCount++
            val transcript = exported.messages.joinToString("\n\n") { message ->
                "### ${message.sender}\n${message.text}"
            }
            if (transcript.isNotBlank()) {
                val transcriptArtifact = ConfirmedArtifactEntity(
                    id = "restored-transcript-$safeOriginalId-$stamp",
                    issueId = issueId,
                    stageId = stageId,
                    title = "${exported.title.ifBlank { "对话" }}（消息记录）",
                    content = transcript,
                    artifactType = "restored_conversation",
                    contentFormat = "markdown",
                    confirmedAt = stamp,
                    createdAt = stamp,
                    updatedAt = stamp,
                )
                if (confirmArtifact(
                        ConfirmArtifactCommand(transcriptArtifact, ArtifactSources())
                    ) is RepositoryResult.Success
                ) artifactCount++
            }
            exported.artifacts.forEach { artifact ->
                val restoredArtifact = ConfirmedArtifactEntity(
                    id = artifact.id.ifBlank { "restored-artifact-$safeOriginalId-$stamp" },
                    issueId = issueId,
                    stageId = stageId,
                    title = artifact.title.ifBlank { "导入成果" },
                    content = artifact.content,
                    artifactType = artifact.type.ifBlank { "restored_artifact" },
                    contentFormat = "markdown",
                    confirmedAt = stamp,
                    createdAt = stamp,
                    updatedAt = stamp,
                )
                if (confirmArtifact(
                        ConfirmArtifactCommand(restoredArtifact, ArtifactSources())
                    ) is RepositoryResult.Success
                ) artifactCount++
            }
        }
    }

    val defaultTarget = issueTargets.values.firstOrNull() ?: run {
        val issueId = "restored-import-$stamp"
        val stageId = "$issueId-stage-0"
        if (saveIssue(
                SaveIssueCommand(
                    issueId = issueId,
                    title = "导入备份资料",
                    initialStageId = stageId,
                    initialStageTitle = "导入资料节点",
                    initialObjective = "承载从本地备份导入的资料",
                    createdAt = stamp,
                )
            ) is RepositoryResult.Success
        ) issueId to stageId else null
    }

    payload.materials.forEach { material ->
        val target = issueTargets[material.id] ?: defaultTarget ?: return@forEach
        val result = createMaterial(
            CreateMaterialCommand(
                id = material.id.ifBlank { "restored-material-$stamp-$materialCount" },
                issueId = target.first,
                stageId = target.second,
                title = material.title.ifBlank { "导入资料" },
                sourceType = material.sourceType.ifBlank { "backup" },
                content = material.content,
                sourceCapturedAt = stamp,
                createdAt = stamp,
            )
        )
        if (result is RepositoryResult.Success) materialCount++
    }
    payload.personalContexts.forEach { personal ->
        val result = createPersonalContext(
            CreatePersonalContextCommand(
                id = personal.id.ifBlank { "restored-personal-$stamp-$personalCount" },
                title = personal.title.ifBlank { "导入个人背景" },
                content = personal.content,
                sensitive = personal.sensitive,
                createdAt = stamp,
            )
        )
        if (result is RepositoryResult.Success) personalCount++
    }
    return BackupImportStats(issueCount, artifactCount, materialCount, personalCount)
}
