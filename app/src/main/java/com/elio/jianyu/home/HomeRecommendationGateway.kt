package com.elio.jianyu.home

import com.elio.jianyu.skill.catalog.OfficialSkillCatalog

fun interface HomeRecommendationGateway {
    suspend fun recommend(request: HomeRecommendationRequest): HomeRecommendationOutcome
}

class LocalCatalogHomeRecommendationGateway(
    private val catalog: OfficialSkillCatalog,
) : HomeRecommendationGateway {
    override suspend fun recommend(request: HomeRecommendationRequest): HomeRecommendationOutcome =
        HomeRecommendationPolicy.recommend(catalog, request)
}
