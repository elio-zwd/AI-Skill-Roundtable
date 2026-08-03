package com.elio.jianyu.data

import androidx.room.TypeConverter

enum class SnapshotContentState(val storageValue: String) {
    AVAILABLE("available"),
    PURGED("purged")
}

enum class AudioFileState(val storageValue: String) {
    PENDING("pending"),
    AVAILABLE("available"),
    MISSING("missing"),
    FAILED("failed")
}

enum class IssueLifecycleState(val storageValue: String) {
    ACTIVE("active"),
    ARCHIVED("archived"),
    TRASHED("trashed")
}

class ResourceLifecycleConverters {
    @TypeConverter
    fun snapshotContentStateToStorage(value: SnapshotContentState): String = value.storageValue

    @TypeConverter
    fun storageToSnapshotContentState(value: String): SnapshotContentState {
        return SnapshotContentState.entries.firstOrNull { it.storageValue == value }
            ?: throw IllegalArgumentException("未知的快照内容状态")
    }

    @TypeConverter
    fun audioFileStateToStorage(value: AudioFileState): String = value.storageValue

    @TypeConverter
    fun storageToAudioFileState(value: String): AudioFileState {
        return AudioFileState.entries.firstOrNull { it.storageValue == value }
            ?: throw IllegalArgumentException("未知的音频文件状态")
    }

    @TypeConverter
    fun issueLifecycleStateToStorage(value: IssueLifecycleState): String = value.storageValue

    @TypeConverter
    fun storageToIssueLifecycleState(value: String): IssueLifecycleState {
        return IssueLifecycleState.entries.firstOrNull { it.storageValue == value }
            ?: throw IllegalArgumentException("未知的议题生命周期状态")
    }
}

data class ArtifactSources(
    val messages: List<ArtifactMessageSourceEntity> = emptyList(),
    val runs: List<ArtifactRunSourceEntity> = emptyList(),
    val draftRevisions: List<ArtifactDraftSourceEntity> = emptyList(),
    val materials: List<ArtifactMaterialSourceEntity> = emptyList()
)

fun validateAudioAssetSource(
    sourceMessageId: Long?,
    sourceArtifactId: String?
) {
    require((sourceMessageId == null) != (sourceArtifactId == null)) {
        "音频资产必须且只能关联一个来源"
    }
}

fun validateOfficialCombinationMembers(members: List<OfficialSkillCombinationMemberEntity>) {
    require(members.all { it.officialSkillId.isNotBlank() }) { "官方 Skill ID 不能为空" }
    require(members.all { it.position >= 0 }) { "成员顺序不能为负数" }
    require(members.map { it.officialSkillId }.distinct().size == members.size) {
        "同一组合不能重复保存官方 Skill"
    }
    require(members.map { it.position }.distinct().size == members.size) {
        "同一组合的成员顺序必须唯一"
    }
}

fun validateArtifactRevision(
    artifactId: String,
    revisionOfArtifactId: String?
) {
    require(revisionOfArtifactId == null || revisionOfArtifactId != artifactId) {
        "成果不能修订自身"
    }
}

internal fun validateConfirmedUsage(confirmedAt: Long) {
    require(confirmedAt > 0L) { "只有用户明确确认的内容才能记录为已使用" }
}

internal fun validateArtifactSources(
    artifact: ConfirmedArtifactEntity,
    sources: ArtifactSources
) {
    val allMatchArtifact = sources.messages.all {
        it.artifactId == artifact.id && it.issueId == artifact.issueId
    } && sources.runs.all {
        it.artifactId == artifact.id && it.issueId == artifact.issueId
    } && sources.draftRevisions.all {
        it.artifactId == artifact.id && it.issueId == artifact.issueId
    } && sources.materials.all {
        it.artifactId == artifact.id && it.issueId == artifact.issueId
    }
    require(allMatchArtifact) { "成果来源必须属于当前成果及同一议题" }
}
