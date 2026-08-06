package com.elio.jianyu.skill.catalog

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.elio.jianyu.execution.ExecutionSkillSelection
import com.elio.jianyu.execution.OfficialCatalogExecutionSkillResolver
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OfficialSkillExecutionManifestV2AndroidTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun all44ProductionSkillsResolveFromPackagedAssets() = runBlocking {
        val runtime = OfficialSkillCatalogRuntime.initialize(context)
        assertTrue(runtime is OfficialSkillCatalogRuntimeResult.Success)
        val catalog = (runtime as OfficialSkillCatalogRuntimeResult.Success).runtime.catalog
        val resolver = OfficialCatalogExecutionSkillResolver(context, catalog)
        val resolvedIds = mutableListOf<String>()

        catalog.skills.sortedBy(OfficialSkillDefinition::defaultOrder).forEachIndexed { index, skill ->
            val snapshots = resolver.resolve(
                runId = "v2-resolver-$index",
                selections = listOf(
                    ExecutionSkillSelection(
                        officialSkillId = skill.id,
                        defaultResponsibility = "验证 ${skill.id} 正式执行资产",
                        executionContext = fullyConfirmedContext(),
                    ),
                ),
                createdAt = 1_800_000_000_000L + index,
            )
            assertEquals(skill.id, snapshots.single().sourceId)
            assertEquals(skill.assetPath, snapshots.single().skillAssetPath)
            assertTrue(snapshots.single().systemPrompt.isNotBlank())
            resolvedIds += snapshots.single().sourceId
        }

        assertEquals(44, resolvedIds.size)
        assertEquals(44, resolvedIds.distinct().size)
        assertEquals(catalog.skills.sortedBy { it.defaultOrder }.map { it.id }, resolvedIds)
    }

    @Test
    fun requiredConsentFailureStopsBeforeParticipantSnapshotCreation() = runBlocking {
        val runtime = OfficialSkillCatalogRuntime.initialize(context)
            as OfficialSkillCatalogRuntimeResult.Success
        val person = runtime.runtime.catalog.skills.first {
            it.primaryType == OfficialSkillPrimaryType.PERSON_PERSPECTIVE
        }
        val resolver = OfficialCatalogExecutionSkillResolver(context, runtime.runtime.catalog)

        val result = runCatching {
            resolver.resolve(
                runId = "person-without-disclaimer",
                selections = listOf(
                    ExecutionSkillSelection(
                        officialSkillId = person.id,
                        executionContext = fullyConfirmedContext().copy(
                            personDisclaimerConfirmed = false,
                        ),
                    ),
                ),
                createdAt = 1_800_000_000_100L,
            )
        }

        assertTrue(result.isFailure)
        val failure = result.exceptionOrNull()
        assertTrue(failure is com.elio.jianyu.execution.InvalidExecutionSkillException)
        assertEquals(
            "person_disclaimer_confirmation_required",
            (failure as com.elio.jianyu.execution.InvalidExecutionSkillException).reasonCode,
        )
    }

    private fun fullyConfirmedContext() = OfficialSkillExecutionContext(
        materialProvided = true,
        materialAuthorized = true,
        sensitiveMaterialConfirmed = true,
        networkAuthorized = true,
        containsRestrictedMaterial = false,
        materialMayLeaveDevice = false,
        highStakesConfirmed = true,
        personDisclaimerConfirmed = true,
        contextCharacters = 1_024,
        maxContextCharacters = 20_000,
        selectedMode = OfficialSkillExecutionSelectedMode.SINGLE,
        stageExecutable = true,
    )
}
