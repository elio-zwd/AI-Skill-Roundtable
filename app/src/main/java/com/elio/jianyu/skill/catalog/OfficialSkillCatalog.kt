package com.elio.jianyu.skill.catalog

interface OfficialSkillCatalog {
    val skills: List<OfficialSkillDefinition>

    fun findById(officialSkillId: String): OfficialSkillDefinition?

    fun containsOfficialId(officialSkillId: String): Boolean
}

class InMemoryOfficialSkillCatalog(
    skills: List<OfficialSkillDefinition>,
) : OfficialSkillCatalog {
    override val skills: List<OfficialSkillDefinition> = skills
        .sortedWith(compareBy(OfficialSkillDefinition::defaultOrder, OfficialSkillDefinition::id))

    private val skillsById: Map<String, OfficialSkillDefinition> = this.skills.associateBy { it.id }

    init {
        require(this.skills.map { it.id }.distinct().size == this.skills.size) {
            "官方 Skill ID 必须唯一"
        }
    }

    override fun findById(officialSkillId: String): OfficialSkillDefinition? {
        if (officialSkillId.isBlank()) return null
        return skillsById[officialSkillId]
    }

    override fun containsOfficialId(officialSkillId: String): Boolean {
        if (officialSkillId.isBlank()) return false
        return skillsById.containsKey(officialSkillId)
    }
}
