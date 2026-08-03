package com.elio.jianyu.skill.catalog

import com.elio.jianyu.data.OfficialSkillIdValidator

class CatalogOfficialSkillIdValidator(
    private val catalog: OfficialSkillCatalog,
) : OfficialSkillIdValidator {
    override suspend fun isValid(officialSkillId: String): Boolean {
        if (officialSkillId.isBlank()) return false
        if (officialSkillId != officialSkillId.trim()) return false
        return catalog.containsOfficialId(officialSkillId)
    }
}
