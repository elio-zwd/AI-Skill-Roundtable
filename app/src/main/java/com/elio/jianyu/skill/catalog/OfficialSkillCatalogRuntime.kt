package com.elio.jianyu.skill.catalog

import android.content.Context

data class OfficialSkillCatalogRuntime(
    val catalog: OfficialSkillCatalog,
    val preferences: OfficialSkillPreferences,
    val validator: CatalogOfficialSkillIdValidator,
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
            OfficialSkillCatalogRuntimeResult.Success(
                OfficialSkillCatalogRuntime(
                    catalog = catalog,
                    preferences = SharedPreferencesOfficialSkillPreferences(
                        context = context.applicationContext,
                        catalog = catalog,
                    ),
                    validator = CatalogOfficialSkillIdValidator(catalog),
                ),
            )
        }
    }
}
