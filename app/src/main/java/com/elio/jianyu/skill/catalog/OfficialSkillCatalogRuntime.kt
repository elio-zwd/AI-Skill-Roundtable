package com.elio.jianyu.skill.catalog

import android.content.Context

data class OfficialSkillCatalogRuntime(
    val catalog: OfficialSkillCatalog,
    val preferences: OfficialSkillPreferences,
    val validator: CatalogOfficialSkillIdValidator,
    val executionEligibility: OfficialSkillExecutionEligibility,
)

sealed interface OfficialSkillCatalogRuntimeResult {
    data class Success(
        val runtime: OfficialSkillCatalogRuntime,
    ) : OfficialSkillCatalogRuntimeResult

    data class Failure(
        val message: String,
        val cause: Throwable? = null,
    ) : OfficialSkillCatalogRuntimeResult
}

fun createOfficialSkillCatalogRuntime(
    context: Context,
): OfficialSkillCatalogRuntimeResult {
    return when (val loaded = OfficialSkillCatalogParser.loadFromAssets(context.applicationContext)) {
        is OfficialSkillCatalogLoadResult.Failure -> OfficialSkillCatalogRuntimeResult.Failure(
            message = loaded.message,
            cause = loaded.cause,
        )
        is OfficialSkillCatalogLoadResult.Success -> {
            val catalog = loaded.catalog
            val eligibility = OfficialSkillExecutionEligibility(
                catalog = catalog,
                assetReader = AndroidOfficialSkillAssetReader(context.applicationContext),
            )
            val failures = catalog.skills
                .filter { it.availability.executable }
                .map(eligibility::audit)
                .filterNot(OfficialSkillExecutionEligibilityResult::eligible)
            if (failures.isNotEmpty()) {
                OfficialSkillCatalogRuntimeResult.Failure(
                    message = failures.joinToString(separator = "; ") { result ->
                        val issue = result.issues.first()
                        "${result.skillId}:${issue.code.reasonCode}"
                    },
                )
            } else {
                OfficialSkillCatalogRuntimeResult.Success(
                    OfficialSkillCatalogRuntime(
                        catalog = catalog,
                        preferences = SharedPreferencesOfficialSkillPreferences(
                            context = context.applicationContext,
                            catalog = catalog,
                        ),
                        validator = CatalogOfficialSkillIdValidator(catalog),
                        executionEligibility = eligibility,
                    ),
                )
            }
        }
    }
}
