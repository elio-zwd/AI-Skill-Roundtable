package com.elio.jianyu.skill.catalog

import kotlinx.serialization.Serializable

@Serializable
enum class OfficialSkillPrimaryType {
    PERSON_PERSPECTIVE,
    PROFESSIONAL_ADVISOR,
    TASK_ASSISTANT,
    WORKFLOW_CAPABILITY,
}

@Serializable
enum class OfficialSkillPrimaryValue {
    REALITY_SUPPORT,
    THINKING_EXPANSION,
    BOTH,
}

@Serializable
enum class OfficialSkillUseMode {
    SINGLE_ONLY,
    SINGLE_PREFERRED,
    MULTI_PREFERRED,
    BOTH,
}

@Serializable
enum class OfficialSkillNetworkRequirement {
    NOT_NEEDED,
    OPTIONAL,
    REQUIRED,
    PROHIBITED_FOR_MATERIAL,
}

@Serializable
enum class OfficialSkillMaterialRequirement {
    NONE,
    OPTIONAL,
    REQUIRED,
    USER_AUTHORIZED,
    SENSITIVE,
    TIME_BOUND,
}

@Serializable
enum class OfficialSkillRiskLevel {
    GENERAL,
    SENSITIVE,
    HIGH_STAKES,
    URGENT,
}

@Serializable
enum class OfficialSkillPublicationStatus {
    BLOCKED_REWORK,
    ORIGINALITY_OR_LICENSE_REVIEW,
    NOTICE_AND_DISCLOSURE_REQUIRED,
    PUBLISHABLE,
}

@Serializable
enum class OfficialSkillSourceStatus {
    EXISTING_ASSET_REVIEW_REQUIRED,
    ORIGINAL_DESIGN_REQUIRED,
    IMPLEMENTATION_SOURCE_PENDING,
    VERIFIED_IMPLEMENTATION_SOURCE,
}

@Serializable
data class OfficialSkillAvailability(
    val v1Target: Boolean,
    val hasAsset: Boolean,
    val discoverable: Boolean,
    val searchable: Boolean,
    val recommendable: Boolean,
    val executable: Boolean,
)

@Serializable
data class OfficialSkillDefinition(
    val id: String,
    val nameZh: String,
    val aliases: List<String> = emptyList(),
    val summary: String,
    val primaryType: OfficialSkillPrimaryType,
    val primaryValue: OfficialSkillPrimaryValue,
    val domainTags: List<String>,
    val scenarioTags: List<String>,
    val inputTags: List<String>,
    val outputTags: List<String>,
    val useMode: OfficialSkillUseMode,
    val networkRequirement: OfficialSkillNetworkRequirement,
    val materialRequirements: List<OfficialSkillMaterialRequirement>,
    val riskLevel: OfficialSkillRiskLevel,
    val publicationStatus: OfficialSkillPublicationStatus,
    val sourceStatus: OfficialSkillSourceStatus,
    val availability: OfficialSkillAvailability,
    val typicalScenarios: List<String>,
    val inputRequirements: List<String>,
    val outputForms: List<String>,
    val boundaries: List<String>,
    val nonExecutableReason: String? = null,
    val personDisclaimer: String? = null,
    val integrityBoundaries: List<String> = emptyList(),
    val sourceSummary: String,
    val assetPath: String? = null,
    val defaultOrder: Int,
) {
    val isOfficialCandidate: Boolean
        get() = id.isNotBlank()
}

@Serializable
data class OfficialSkillCatalogManifest(
    val schemaVersion: Int,
    val catalogId: String,
    val generatedFrom: String,
    val skills: List<OfficialSkillDefinition>,
)

sealed interface OfficialSkillCatalogLoadResult {
    data class Success(
        val catalog: OfficialSkillCatalog,
    ) : OfficialSkillCatalogLoadResult

    data class Failure(
        val message: String,
        val cause: Throwable? = null,
    ) : OfficialSkillCatalogLoadResult
}

data class OfficialSkillCatalogValidationIssue(
    val code: String,
    val skillId: String? = null,
    val detail: String,
)

@Serializable
data class RecentOfficialSkillUse(
    val skillId: String,
    val usedAt: Long,
)

data class OfficialSkillUseRequest(
    val skillId: String,
    val intent: String? = null,
)
