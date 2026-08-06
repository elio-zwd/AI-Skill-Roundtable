package com.elio.jianyu.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

enum class StageAdvancementMeasure(val storageValue: String) {
    CLARIFY_NEXT_STEP("clarify_next_step"),
    FORM_EXECUTION_PLAN("form_execution_plan"),
    ANALYZE_EXECUTION_OBSTACLES("analyze_execution_obstacles"),
    GENERATE_DELIVERABLE("generate_deliverable"),
    SET_CHECKPOINTS("set_checkpoints"),
    INTRODUCE_COUNTERARGUMENT("introduce_counterargument"),
    FIND_MISSING_PERSPECTIVES("find_missing_perspectives"),
    CHECK_KEY_ASSUMPTIONS("check_key_assumptions"),
    COMPARE_POSITIONS("compare_positions"),
    DEEPEN_QUESTION("deepen_question"),
    CUSTOM_OBJECTIVE("custom_objective"),
}

class StageAdvancementConverters {
    @TypeConverter
    fun measureToStorageValue(value: StageAdvancementMeasure): String = value.storageValue

    @TypeConverter
    fun storageValueToMeasure(value: String): StageAdvancementMeasure =
        StageAdvancementMeasure.entries.firstOrNull { it.storageValue == value }
            ?: throw IllegalArgumentException("Unknown stage advancement measure: $value")
}

@Entity(
    tableName = "stage_advancements",
    foreignKeys = [
        ForeignKey(
            entity = StageEntity::class,
            parentColumns = ["id", "issueId"],
            childColumns = ["stageId", "issueId"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = StageEntity::class,
            parentColumns = ["id", "issueId"],
            childColumns = ["sourceStageId", "issueId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["stageId", "issueId"], unique = true),
        Index(value = ["sourceStageId", "issueId"]),
        Index(value = ["operationId"], unique = true),
    ],
)
data class StageAdvancementEntity(
    @PrimaryKey val stageId: String,
    val issueId: String,
    val sourceStageId: String,
    val operationId: String,
    val payloadHash: String,
    val realitySupport: Boolean,
    val thinkingExpansion: Boolean,
    val objective: String,
    val expectedOutput: String,
    val confirmedAt: Long,
    val createdAt: Long,
)

@Entity(
    tableName = "stage_advancement_measures",
    primaryKeys = ["stageId", "measure"],
    foreignKeys = [
        ForeignKey(
            entity = StageAdvancementEntity::class,
            parentColumns = ["stageId", "issueId"],
            childColumns = ["stageId", "issueId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["stageId", "issueId"]),
        Index(value = ["stageId", "position"], unique = true),
    ],
)
data class StageAdvancementMeasureEntity(
    val stageId: String,
    val issueId: String,
    val measure: StageAdvancementMeasure,
    val position: Int,
)

@Entity(
    tableName = "stage_advancement_skill_members",
    primaryKeys = ["stageId", "officialSkillId"],
    foreignKeys = [
        ForeignKey(
            entity = StageAdvancementEntity::class,
            parentColumns = ["stageId", "issueId"],
            childColumns = ["stageId", "issueId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["stageId", "issueId"]),
        Index(value = ["stageId", "position"], unique = true),
        Index(value = ["sourceRunId"]),
        Index(value = ["sourceParticipantSnapshotId"]),
    ],
)
data class StageAdvancementSkillMemberEntity(
    val stageId: String,
    val issueId: String,
    val officialSkillId: String,
    val position: Int,
    val responsibility: String,
    val sourceRunId: String? = null,
    val sourceParticipantSnapshotId: String? = null,
    val catalogVersionBasis: String? = null,
    val confirmedAt: Long,
)

@Entity(
    tableName = "stage_advancement_materials",
    primaryKeys = ["stageId", "materialReferenceId"],
    foreignKeys = [
        ForeignKey(
            entity = StageAdvancementEntity::class,
            parentColumns = ["stageId", "issueId"],
            childColumns = ["stageId", "issueId"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = MaterialReferenceEntity::class,
            parentColumns = ["id", "issueId"],
            childColumns = ["materialReferenceId", "issueId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["stageId", "issueId"]),
        Index(value = ["materialReferenceId", "issueId"]),
        Index(value = ["stageId", "position"], unique = true),
    ],
)
data class StageAdvancementMaterialEntity(
    val stageId: String,
    val issueId: String,
    val materialReferenceId: String,
    val position: Int,
    val inheritedAt: Long,
)

@Entity(
    tableName = "stage_advancement_artifacts",
    primaryKeys = ["stageId", "artifactId"],
    foreignKeys = [
        ForeignKey(
            entity = StageAdvancementEntity::class,
            parentColumns = ["stageId", "issueId"],
            childColumns = ["stageId", "issueId"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = ConfirmedArtifactEntity::class,
            parentColumns = ["id", "issueId"],
            childColumns = ["artifactId", "issueId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["stageId", "issueId"]),
        Index(value = ["artifactId", "issueId"]),
        Index(value = ["stageId", "position"], unique = true),
    ],
)
data class StageAdvancementArtifactEntity(
    val stageId: String,
    val issueId: String,
    val artifactId: String,
    val position: Int,
    val inheritedAt: Long,
)

data class StageAdvancementSkillPlan(
    val officialSkillId: String,
    val position: Int,
    val responsibility: String,
    val sourceRunId: String? = null,
    val sourceParticipantSnapshotId: String? = null,
    val catalogVersionBasis: String? = null,
)

data class AdvanceIssueCommand(
    val operationId: String,
    val issueId: String,
    val sourceStageId: String,
    val newStageId: String,
    val newStageTitle: String,
    val objective: String,
    val realitySupport: Boolean,
    val thinkingExpansion: Boolean,
    val measures: List<StageAdvancementMeasure>,
    val expectedOutput: String,
    val roster: List<StageAdvancementSkillPlan>,
    val inheritedMaterialIds: List<String>,
    val inheritedArtifactIds: List<String>,
    val confirmedAt: Long,
)

data class StageAdvancementSnapshot(
    val stage: StageEntity,
    val advancement: StageAdvancementEntity,
    val measures: List<StageAdvancementMeasureEntity>,
    val roster: List<StageAdvancementSkillMemberEntity>,
    val materials: List<StageAdvancementMaterialEntity>,
    val artifacts: List<StageAdvancementArtifactEntity>,
)

data class AdvanceIssueResult(
    val snapshot: StageAdvancementSnapshot,
)

object StageAdvancementPolicy {
    private val realityOrder = listOf(
        StageAdvancementMeasure.CLARIFY_NEXT_STEP,
        StageAdvancementMeasure.FORM_EXECUTION_PLAN,
        StageAdvancementMeasure.ANALYZE_EXECUTION_OBSTACLES,
        StageAdvancementMeasure.GENERATE_DELIVERABLE,
        StageAdvancementMeasure.SET_CHECKPOINTS,
    )
    private val thinkingOrder = listOf(
        StageAdvancementMeasure.INTRODUCE_COUNTERARGUMENT,
        StageAdvancementMeasure.FIND_MISSING_PERSPECTIVES,
        StageAdvancementMeasure.CHECK_KEY_ASSUMPTIONS,
        StageAdvancementMeasure.COMPARE_POSITIONS,
        StageAdvancementMeasure.DEEPEN_QUESTION,
    )
    private val stableOrder = realityOrder + thinkingOrder + StageAdvancementMeasure.CUSTOM_OBJECTIVE

    fun normalize(command: AdvanceIssueCommand): AdvanceIssueCommand {
        require(command.operationId.isNotBlank())
        require(command.issueId.isNotBlank())
        require(command.sourceStageId.isNotBlank())
        require(command.newStageId.isNotBlank())
        require(command.sourceStageId != command.newStageId)
        require(command.newStageTitle.isNotBlank())
        require(command.objective.isNotBlank())
        require(command.expectedOutput.isNotBlank())
        require(command.confirmedAt > 0L)
        require(command.realitySupport || command.thinkingExpansion)
        require(command.measures.isNotEmpty())
        require(command.measures.distinct().size == command.measures.size)
        require(command.roster.map { it.officialSkillId }.distinct().size == command.roster.size)
        require(command.roster.map { it.position }.distinct().size == command.roster.size)
        require(command.roster.all {
            it.officialSkillId.isNotBlank() && it.position >= 0 && it.responsibility.isNotBlank()
        })
        require(command.inheritedMaterialIds.all(String::isNotBlank))
        require(command.inheritedMaterialIds.distinct().size == command.inheritedMaterialIds.size)
        require(command.inheritedArtifactIds.all(String::isNotBlank))
        require(command.inheritedArtifactIds.distinct().size == command.inheritedArtifactIds.size)

        val allowed = buildSet {
            if (command.realitySupport) addAll(realityOrder)
            if (command.thinkingExpansion) addAll(thinkingOrder)
            add(StageAdvancementMeasure.CUSTOM_OBJECTIVE)
        }
        require(command.measures.all { it in allowed })

        return command.copy(
            operationId = command.operationId.trim(),
            issueId = command.issueId.trim(),
            sourceStageId = command.sourceStageId.trim(),
            newStageId = command.newStageId.trim(),
            newStageTitle = command.newStageTitle.trim(),
            objective = command.objective.trim(),
            expectedOutput = command.expectedOutput.trim(),
            measures = stableOrder.filter(command.measures::contains),
            roster = command.roster
                .sortedWith(compareBy<StageAdvancementSkillPlan>({ it.position }, { it.officialSkillId }))
                .mapIndexed { index, member -> member.copy(position = index) },
        )
    }
}

object StageAdvancementPayloadHasher {
    fun hash(command: AdvanceIssueCommand): String {
        val normalized = StageAdvancementPolicy.normalize(command)
        val fields = buildList {
            add(normalized.operationId)
            add(normalized.issueId)
            add(normalized.sourceStageId)
            add(normalized.newStageId)
            add(normalized.newStageTitle)
            add(normalized.objective)
            add(normalized.realitySupport.toString())
            add(normalized.thinkingExpansion.toString())
            normalized.measures.forEach { add(it.storageValue) }
            add(normalized.expectedOutput)
            normalized.roster.forEach { member ->
                add(member.officialSkillId)
                add(member.position.toString())
                add(member.responsibility)
                add(member.sourceRunId.orEmpty())
                add(member.sourceParticipantSnapshotId.orEmpty())
                add(member.catalogVersionBasis.orEmpty())
            }
            normalized.inheritedMaterialIds.forEach(::add)
            normalized.inheritedArtifactIds.forEach(::add)
        }
        val digest = MessageDigest.getInstance("SHA-256")
        fields.forEach { field ->
            val bytes = field.toByteArray(StandardCharsets.UTF_8)
            digest.update(bytes.size.toString().toByteArray(StandardCharsets.US_ASCII))
            digest.update(':'.code.toByte())
            digest.update(bytes)
            digest.update('\n'.code.toByte())
        }
        return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    }
}
