package com.elio.jianyu.home

import com.elio.jianyu.skill.catalog.OfficialSkillCatalog
import com.elio.jianyu.skill.catalog.OfficialSkillDefinition
import com.elio.jianyu.skill.catalog.OfficialSkillMaterialRequirement
import com.elio.jianyu.skill.catalog.OfficialSkillNetworkRequirement
import com.elio.jianyu.skill.catalog.OfficialSkillPrimaryType
import com.elio.jianyu.skill.catalog.OfficialSkillPrimaryValue
import com.elio.jianyu.skill.catalog.OfficialSkillRiskLevel
import java.util.Locale

object HomeRecommendationPolicy {
    fun recommend(
        catalog: OfficialSkillCatalog,
        request: HomeRecommendationRequest,
    ): HomeRecommendationOutcome {
        val ranked = catalog.skills
            .asSequence()
            .filter { it.availability.recommendable }
            .map { skill -> skill to score(skill, request) }
            .sortedWith(
                compareByDescending<Pair<OfficialSkillDefinition, Int>> { it.second }
                    .thenBy { it.first.defaultOrder }
                    .thenBy { it.first.id },
            )
            .map(Pair<OfficialSkillDefinition, Int>::first)
            .toList()

        if (ranked.isEmpty()) return HomeRecommendationOutcome.NoSuitableSkill

        val executableIds = ranked.filter { it.availability.executable }.mapTo(linkedSetOf()) { it.id }
        val desiredCount = if (
            request.directions.contains(ValueDirection.REALITY_SUPPORT) &&
            request.directions.contains(ValueDirection.THINKING_EXPANSION)
        ) {
            2
        } else {
            1
        }
        val initiallySelected = ranked
            .filter { it.id in executableIds }
            .take(desiredCount)
            .mapTo(linkedSetOf()) { it.id }
        val candidates = ranked.take(MAX_CANDIDATES).mapIndexed { index, definition ->
            definition.toRecommendedSkill(
                position = index,
                selected = definition.id in initiallySelected,
                request = request,
            )
        }

        if (executableIds.isEmpty()) {
            return HomeRecommendationOutcome.NoExecutableSkill(candidates)
        }

        val selectedCount = candidates.count(RecommendedSkill::selected)
        val mode = if (selectedCount > 1) RecommendationMode.MULTI else RecommendationMode.SINGLE
        val expectedOutput = candidates
            .filter(RecommendedSkill::selected)
            .flatMap { listOf(it.expectedOutput) }
            .distinct()
            .joinToString("、")
            .ifBlank { "可编辑的问题分析与下一步" }
        return HomeRecommendationOutcome.Ready(
            HomeRecommendation(
                questionSummary = request.question.trim().take(MAX_SUMMARY_LENGTH),
                directions = request.directions,
                mode = mode,
                modeReason = if (mode == RecommendationMode.MULTI) {
                    "当前问题同时需要现实行动和假设检查，建议由多个 Skill 互补分工。"
                } else {
                    "当前问题可以先由一个匹配度较高的 Skill 聚焦处理，后续仍可邀请其他 Skill。"
                },
                skills = candidates,
                expectedOutput = expectedOutput,
                source = RecommendationSource.LOCAL_CATALOG,
            ),
        )
    }

    fun validateForStart(
        catalog: OfficialSkillCatalog,
        recommendation: HomeRecommendation,
    ): Set<HomeRecommendationValidationError> {
        val errors = linkedSetOf<HomeRecommendationValidationError>()
        val selected = recommendation.skills.filter(RecommendedSkill::selected)
        if (selected.isEmpty()) errors += HomeRecommendationValidationError.EMPTY_SELECTION
        if (selected.map(RecommendedSkill::skillId).distinct().size != selected.size) {
            errors += HomeRecommendationValidationError.DUPLICATE_SKILL
        }
        selected.forEach { item ->
            val definition = catalog.findById(item.skillId)
            if (definition == null) {
                errors += HomeRecommendationValidationError.UNKNOWN_SKILL
            } else if (!definition.availability.executable || !item.executable) {
                errors += HomeRecommendationValidationError.NON_EXECUTABLE_SKILL
            }
            if (item.reason.isBlank()) errors += HomeRecommendationValidationError.BLANK_REASON
            if (item.responsibility.isBlank()) {
                errors += HomeRecommendationValidationError.BLANK_RESPONSIBILITY
            }
        }
        val positions = recommendation.skills.map(RecommendedSkill::position)
        if (positions.distinct().size != positions.size || positions.sorted() != positions.indices.toList()) {
            errors += HomeRecommendationValidationError.INVALID_POSITION
        }
        return errors
    }

    private fun score(
        skill: OfficialSkillDefinition,
        request: HomeRecommendationRequest,
    ): Int {
        var score = 0
        if (request.directions.isEmpty()) score += 10
        if (
            ValueDirection.REALITY_SUPPORT in request.directions &&
            skill.primaryValue in setOf(
                OfficialSkillPrimaryValue.REALITY_SUPPORT,
                OfficialSkillPrimaryValue.BOTH,
            )
        ) {
            score += 40
        }
        if (
            ValueDirection.THINKING_EXPANSION in request.directions &&
            skill.primaryValue in setOf(
                OfficialSkillPrimaryValue.THINKING_EXPANSION,
                OfficialSkillPrimaryValue.BOTH,
            )
        ) {
            score += 40
        }

        val question = request.question.lowercase(Locale.ROOT)
        val searchable = buildList {
            add(skill.id)
            add(skill.nameZh)
            addAll(skill.aliases)
            add(skill.summary)
            addAll(skill.domainTags)
            addAll(skill.scenarioTags)
            addAll(skill.typicalScenarios)
            addAll(skill.outputForms)
        }
        searchable.forEach { value ->
            val normalized = value.lowercase(Locale.ROOT)
            if (normalized.length >= 2 && question.contains(normalized)) score += 8
            normalized.split(SEARCH_SEPARATOR)
                .filter { it.length >= 2 && question.contains(it) }
                .forEach { score += 2 }
        }
        if (skill.availability.executable) score += 3
        return score
    }

    private fun OfficialSkillDefinition.toRecommendedSkill(
        position: Int,
        selected: Boolean,
        request: HomeRecommendationRequest,
    ): RecommendedSkill {
        val directionReason = when (primaryValue) {
            OfficialSkillPrimaryValue.REALITY_SUPPORT -> "偏向现实支持，可把问题转化为具体步骤。"
            OfficialSkillPrimaryValue.THINKING_EXPANSION -> "偏向思维拓展，可检查假设、盲区和替代解释。"
            OfficialSkillPrimaryValue.BOTH -> "同时覆盖现实支持与思维拓展。"
        }
        val matching = buildList {
            addAll(typicalScenarios.take(2))
            addAll(domainTags.take(2))
        }.distinct().joinToString("、")
        val reason = buildString {
            append(directionReason)
            if (matching.isNotBlank()) append(" 匹配场景：").append(matching).append('。')
            if (!availability.executable) append(" 当前只能查看，尚未通过执行门禁。")
        }
        val responsibility = when (primaryValue) {
            OfficialSkillPrimaryValue.REALITY_SUPPORT -> "形成可执行建议、步骤和现实约束清单"
            OfficialSkillPrimaryValue.THINKING_EXPANSION -> "检查关键假设、盲区和不同观点"
            OfficialSkillPrimaryValue.BOTH -> if (
                ValueDirection.THINKING_EXPANSION in request.directions
            ) {
                "兼顾行动方案与反方检查"
            } else {
                "分析问题并形成可编辑的下一步"
            }
        }
        val riskDisclosure = buildList {
            add(riskLevel.toRecommendationRisk().displayName())
            personDisclaimer?.takeIf(String::isNotBlank)?.let(::add)
            addAll(boundaries.take(2))
            nonExecutableReason?.takeIf(String::isNotBlank)?.let(::add)
        }.joinToString("；")
        val freshnessDisclosure = if (
            OfficialSkillMaterialRequirement.TIME_BOUND in materialRequirements ||
            networkRequirement == OfficialSkillNetworkRequirement.REQUIRED
        ) {
            "涉及当前事实或时效信息时必须联网或由用户提供最新资料核验。"
        } else {
            "主要依赖用户当前问题；如涉及变化事实仍需单独核验。"
        }
        return RecommendedSkill(
            skillId = id,
            displayName = nameZh,
            responsibility = responsibility,
            reason = reason,
            risk = riskLevel.toRecommendationRisk(),
            riskDisclosure = riskDisclosure,
            freshnessDisclosure = freshnessDisclosure,
            networkRequirement = networkRequirement.displayName(),
            materialRequirement = materialRequirements.joinToString("、") { it.displayName() },
            expectedOutput = outputForms.firstOrNull()?.takeIf(String::isNotBlank)
                ?: outputTags.firstOrNull()?.takeIf(String::isNotBlank)
                ?: "问题分析",
            executable = availability.executable,
            selected = selected,
            position = position,
        )
    }

    private fun OfficialSkillRiskLevel.toRecommendationRisk(): RecommendationRisk = when (this) {
        OfficialSkillRiskLevel.GENERAL -> RecommendationRisk.GENERAL
        OfficialSkillRiskLevel.SENSITIVE -> RecommendationRisk.SENSITIVE
        OfficialSkillRiskLevel.HIGH_STAKES -> RecommendationRisk.HIGH_STAKES
        OfficialSkillRiskLevel.URGENT -> RecommendationRisk.URGENT
    }

    private fun RecommendationRisk.displayName(): String = when (this) {
        RecommendationRisk.GENERAL -> "一般风险：仍需结合现实条件复核"
        RecommendationRisk.SENSITIVE -> "敏感主题：注意隐私、立场和适用边界"
        RecommendationRisk.HIGH_STAKES -> "高后果主题：保留不确定性并寻求现实专业复核"
        RecommendationRisk.URGENT -> "紧急主题：优先现实安全与及时求助"
    }

    private fun OfficialSkillNetworkRequirement.displayName(): String = when (this) {
        OfficialSkillNetworkRequirement.NOT_NEEDED -> "不需要联网"
        OfficialSkillNetworkRequirement.OPTIONAL -> "联网可选"
        OfficialSkillNetworkRequirement.REQUIRED -> "需要联网核验"
        OfficialSkillNetworkRequirement.PROHIBITED_FOR_MATERIAL -> "资料正文不得联网发送"
    }

    private fun OfficialSkillMaterialRequirement.displayName(): String = when (this) {
        OfficialSkillMaterialRequirement.NONE -> "不需要资料"
        OfficialSkillMaterialRequirement.OPTIONAL -> "资料可选"
        OfficialSkillMaterialRequirement.REQUIRED -> "需要资料"
        OfficialSkillMaterialRequirement.USER_AUTHORIZED -> "仅使用用户授权资料"
        OfficialSkillMaterialRequirement.SENSITIVE -> "可能涉及敏感资料"
        OfficialSkillMaterialRequirement.TIME_BOUND -> "需要核验资料时效"
    }

    private val SEARCH_SEPARATOR = Regex("[^\\p{L}\\p{N}_-]+")
    private const val MAX_CANDIDATES = 4
    private const val MAX_SUMMARY_LENGTH = 160
}
