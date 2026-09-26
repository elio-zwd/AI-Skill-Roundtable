package com.elio.jianyu.ui.screens.resources

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.elio.jianyu.data.ContextSourceLifecycle
import com.elio.jianyu.result.ArtifactLibraryItem
import com.elio.jianyu.result.ArtifactLibrarySnapshot
import com.elio.jianyu.result.ArtifactType
import com.elio.jianyu.skill.knowledge.SkillKnowledgeDocumentType
import com.elio.jianyu.ui.navigation.ResourceTab
import com.elio.jianyu.ui.theme.SkillRoundtableTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.assertEquals

@RunWith(AndroidJUnit4::class)
class ResourcesScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun resourcesLibraryDoesNotExposePersonalContextAsPeerTab() {
        composeRule.setContent {
            SkillRoundtableTheme {
                ResourcesScreen(
                    selectedTab = ResourceTab.MATERIALS,
                    onSelectTab = {},
                    onOpenSettings = {},
                    state = ResourcesUiState.Content(
                        section = ResourceLibrarySection.PERSONAL_CONTEXTS,
                    ),
                )
            }
        }

        composeRule.onNodeWithText("个人背景").assertDoesNotExist()
        composeRule.onNodeWithTag(ResourcesTestTags.PERSONAL_CONTEXT_LIBRARY).assertDoesNotExist()
        composeRule.onNodeWithText("资料必须关联会话，可选关联对话节点；已关联不等于自动发送。")
            .assertIsDisplayed()
    }

    @Test
    fun deletedMaterialOffersRestoreAndPurgeActions() {
        composeRule.setContent {
            SkillRoundtableTheme {
                ResourcesScreen(
                    selectedTab = ResourceTab.MATERIALS,
                    onSelectTab = {},
                    onOpenSettings = {},
                    state = ResourcesUiState.Content(
                        lifecycles = setOf(ContextSourceLifecycle.DELETED),
                        materials = listOf(
                            MaterialUiItem(
                                id = "material-1",
                                issueId = "issue-1",
                                stageId = null,
                                title = "已删除资料",
                                sourceType = "note",
                                sourceLocator = null,
                                contentPreview = "正文",
                                content = "正文",
                                sourcePublishedAt = null,
                                sourceCapturedAt = 1L,
                                sensitive = false,
                                lifecycle = ContextSourceLifecycle.DELETED,
                                updatedAt = 1L,
                            ),
                        ),
                    ),
                )
            }
        }

        composeRule.onNodeWithTag(ResourcesTestTags.material("material-1")).assertIsDisplayed()
        composeRule.onNodeWithText("恢复").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("彻底清除").performScrollTo().performClick()
    }

    @Test
    fun overviewShowsRealObjectsAndRoutesToTheirLibraries() {
        var requestedPage = ""
        val material = material("material-overview", sensitive = false)
        val artifact = artifact("artifact-overview")

        composeRule.setContent {
            SkillRoundtableTheme {
                ResourcesScreen(
                    showOverview = true,
                    selectedTab = ResourceTab.MATERIALS,
                    onSelectTab = {},
                    onOpenSettings = {},
                    state = ResourcesUiState.Content(materials = listOf(material)),
                    artifactState = ArtifactLibraryUiState.Content(
                        ArtifactLibrarySnapshot(listOf(artifact), emptyList()),
                    ),
                    onShowMaterials = { requestedPage = "materials" },
                    onShowArtifacts = { requestedPage = "artifacts" },
                )
            }
        }

        composeRule.onNodeWithTag(ResourcesOverviewTestTags.SCREEN).assertIsDisplayed()
        composeRule.onNodeWithTag(ResourcesOverviewTestTags.material(material.id)).assertIsDisplayed()
        composeRule.onNodeWithTag(ResourcesOverviewTestTags.artifact(artifact.artifactId)).assertIsDisplayed()

        composeRule.onNodeWithTag(ResourcesOverviewTestTags.ARTIFACT_SUMMARY).performClick()
        composeRule.runOnIdle { assertEquals("artifacts", requestedPage) }
    }

    @Test
    fun overviewShowsIndependentSkillKnowledgeEntry() {
        var requested = false
        composeRule.setContent {
            SkillRoundtableTheme {
                ResourcesScreen(
                    showOverview = true,
                    selectedTab = ResourceTab.MATERIALS,
                    onSelectTab = {},
                    onOpenSettings = {},
                    skillKnowledgeState = SkillKnowledgeUiState.Content(
                        documents = listOf(skillKnowledgeDocument()),
                    ),
                    onShowSkillKnowledge = { requested = true },
                )
            }
        }

        composeRule.onNodeWithTag(ResourcesOverviewTestTags.SKILL_KNOWLEDGE)
            .assertIsDisplayed()
            .performClick()
        composeRule.runOnIdle { assertEquals(true, requested) }
    }

    @Test
    fun skillKnowledgeDetailIsReadOnlyAndCanBeAddedToConversation() {
        var selectedSkillId = ""
        val document = skillKnowledgeDocument()
        composeRule.setContent {
            SkillRoundtableTheme {
                ResourcesScreen(
                    showSkillKnowledge = true,
                    skillKnowledgeState = SkillKnowledgeUiState.Content(
                        documents = listOf(document),
                        selectedDocumentId = document.documentId,
                    ),
                    selectedTab = ResourceTab.MATERIALS,
                    onSelectTab = {},
                    onOpenSettings = {},
                    onUseSkillKnowledgeInConversation = { selection ->
                        selectedSkillId = selection.skillId
                    },
                )
            }
        }

        composeRule.onNodeWithTag(SkillKnowledgeTestTags.DETAIL).assertIsDisplayed()
        composeRule.onNodeWithText("Skill 角色：Richard Feynman").assertIsDisplayed()
        composeRule.onNodeWithText("references/research.md", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Explain with concrete examples first.").assertIsDisplayed()
        composeRule.onNodeWithText("编辑").assertDoesNotExist()
        composeRule.onNodeWithText("删除").assertDoesNotExist()
        composeRule.onNodeWithText("归档").assertDoesNotExist()
        composeRule.onNodeWithText("允许网络发送").assertDoesNotExist()
        composeRule.onNodeWithText("敏感资料确认").assertDoesNotExist()
        composeRule.onNodeWithTag(SkillKnowledgeTestTags.USE_IN_CONVERSATION)
            .assertIsDisplayed()
            .performClick()
        composeRule.runOnIdle { assertEquals("richard_feynman", selectedSkillId) }
    }

    @Test
    fun addSheetExposesOnlyApprovedMaterialSources() {
        var selectedKind = ""
        composeRule.setContent {
            SkillRoundtableTheme {
                ResourcesScreen(
                    showOverview = true,
                    addMaterialSheetVisible = true,
                    selectedTab = ResourceTab.MATERIALS,
                    onSelectTab = {},
                    onOpenSettings = {},
                    onChooseMaterialKind = { selectedKind = it },
                )
            }
        }

        composeRule.onNodeWithTag(ResourcesTestTags.ADD_SHEET).assertIsDisplayed()
        composeRule.onNodeWithText("添加链接").performClick()
        composeRule.runOnIdle { assertEquals("url", selectedKind) }
        composeRule.onNodeWithText("个人背景").assertDoesNotExist()
    }

    @Test
    fun materialDetailHidesSensitivePreviewAndKeepsInputBoundaryVisible() {
        val material = material("sensitive", sensitive = true)
        composeRule.setContent {
            SkillRoundtableTheme {
                ResourcesScreen(
                    showOverview = true,
                    selectedTab = ResourceTab.MATERIALS,
                    onSelectTab = {},
                    onOpenSettings = {},
                    state = ResourcesUiState.Content(
                        materials = listOf(material),
                        selectedMaterialId = material.id,
                    ),
                )
            }
        }

        composeRule.onNodeWithTag(ResourcesTestTags.MATERIAL_DETAIL).assertIsDisplayed()
        composeRule.onNodeWithText("敏感内容已隐藏，点击编辑后查看。").assertIsDisplayed()
        composeRule.onNodeWithText(
            "资料只是对话的输入与依据，不会自动发送给任何 Skill 角色。",
        ).assertIsDisplayed()
    }

    @Test
    fun sensitiveMaterialCardNeverExposesItsPreview() {
        val material = material("card-sensitive", sensitive = true)
        composeRule.setContent {
            SkillRoundtableTheme {
                ResourcesScreen(
                    selectedTab = ResourceTab.MATERIALS,
                    onSelectTab = {},
                    onOpenSettings = {},
                    state = ResourcesUiState.Content(materials = listOf(material)),
                )
            }
        }

        composeRule.onNodeWithText("敏感内容已隐藏").assertIsDisplayed()
        composeRule.onNodeWithText("预览正文").assertDoesNotExist()
    }

    @Test
    fun materialSearchNoResultIsDifferentFromEmptyLibrary() {
        composeRule.setContent {
            SkillRoundtableTheme {
                ResourcesScreen(
                    selectedTab = ResourceTab.MATERIALS,
                    onSelectTab = {},
                    onOpenSettings = {},
                    state = ResourcesUiState.Content(
                        query = "找不到",
                        materials = listOf(material("existing", sensitive = false)),
                    ),
                )
            }
        }

        composeRule.onNodeWithText("暂无匹配资料").assertIsDisplayed()
        composeRule.onNodeWithText("暂无资料").assertDoesNotExist()
    }

    @Test
    fun materialCardShowsConversationTitleInsteadOfInternalIds() {
        composeRule.setContent {
            SkillRoundtableTheme {
                ResourcesScreen(
                    selectedTab = ResourceTab.MATERIALS,
                    onSelectTab = {},
                    onOpenSettings = {},
                    state = ResourcesUiState.Content(
                        materials = listOf(
                            material("friendly", sensitive = false).copy(
                                issueTitle = "我的旅行计划",
                                stageTitle = "第一轮梳理",
                            ),
                        ),
                    ),
                )
            }
        }

        composeRule.onNodeWithText("我的旅行计划").assertIsDisplayed()
        composeRule.onNodeWithText("第一轮梳理").assertIsDisplayed()
        composeRule.onNodeWithText("issue-1").assertDoesNotExist()
    }

    @Test
    fun materialEditorDisablesSaveWhileOperationIsInProgress() {
        composeRule.setContent {
            SkillRoundtableTheme {
                ResourcesScreen(
                    showOverview = true,
                    selectedTab = ResourceTab.MATERIALS,
                    onSelectTab = {},
                    onOpenSettings = {},
                    state = ResourcesUiState.Content(
                        issues = listOf(ResourceIssueOption("issue-1", "测试会话", emptyList())),
                        operationInProgress = true,
                        editor = ResourceEditorDraft(
                            sourceType = com.elio.jianyu.data.ContextSourceType.MATERIAL,
                            sourceKind = "excerpt",
                            issueId = "issue-1",
                            title = "待保存资料",
                            content = "正文",
                        ),
                    ),
                )
            }
        }

        composeRule.onNodeWithText("保存中…").assertIsNotEnabled()
    }

    @Test
    fun existingMaterialEditorDoesNotOfferFakeConversationReassignment() {
        composeRule.setContent {
            SkillRoundtableTheme {
                ResourcesScreen(
                    showOverview = true,
                    selectedTab = ResourceTab.MATERIALS,
                    onSelectTab = {},
                    onOpenSettings = {},
                    state = ResourcesUiState.Content(
                        issues = listOf(
                            ResourceIssueOption("issue-1", "原会话", listOf(ResourceStageOption("stage-1", "原节点"))),
                            ResourceIssueOption("issue-2", "另一个会话", emptyList()),
                        ),
                        editor = ResourceEditorDraft(
                            sourceType = com.elio.jianyu.data.ContextSourceType.MATERIAL,
                            sourceId = "material-1",
                            issueId = "issue-1",
                            stageId = "stage-1",
                            sourceKind = "excerpt",
                            title = "已有资料",
                            content = "正文",
                            expectedUpdatedAt = 1L,
                        ),
                    ),
                )
            }
        }

        composeRule.onNodeWithText("原会话").assertIsDisplayed()
        composeRule.onNodeWithText("原节点").assertIsDisplayed()
        composeRule.onNodeWithText("另一个会话").assertDoesNotExist()
        composeRule.onNodeWithText("已有资料的归属不会在编辑时迁移；如需更换归属，请新建一条资料。")
            .assertIsDisplayed()
    }

    @Test
    fun linkEditorUsesUserFacingFieldsAndDisablesSaveWithoutConversation() {
        composeRule.setContent {
            SkillRoundtableTheme {
                ResourcesScreen(
                    showOverview = true,
                    selectedTab = ResourceTab.MATERIALS,
                    onSelectTab = {},
                    onOpenSettings = {},
                    state = ResourcesUiState.Content(
                        editor = ResourceEditorDraft(
                            sourceType = com.elio.jianyu.data.ContextSourceType.MATERIAL,
                            sourceKind = "url",
                        ),
                    ),
                )
            }
        }

        composeRule.onNodeWithTag(ResourcesTestTags.EDITOR).assertIsDisplayed()
        composeRule.onNodeWithText("添加链接").assertIsDisplayed()
        composeRule.onNodeWithText("所属会话").assertIsDisplayed()
        composeRule.onNodeWithText(
            "当前没有可用会话。请先返回【对话】开始一个会话，再添加资料。",
        ).assertIsDisplayed()
        composeRule.onNodeWithText("保存").assertIsNotEnabled()
        composeRule.onNodeWithText("所属议题").assertDoesNotExist()
    }

    private fun skillKnowledgeDocument() = SkillKnowledgeDocumentUiItem(
        documentId = "feynman-research",
        skillId = "richard_feynman",
        skillName = "Richard Feynman",
        relativePath = "references/research.md",
        title = "Feynman research",
        type = SkillKnowledgeDocumentType.KNOWLEDGE,
        contentHash = "hash",
        content = "Explain with concrete examples first.",
    )

    private fun material(id: String, sensitive: Boolean) = MaterialUiItem(
        id = id,
        issueId = "issue-1",
        stageId = null,
        title = "资料-$id",
        sourceType = "excerpt",
        sourceLocator = null,
        contentPreview = "预览正文",
        content = "完整正文",
        sourcePublishedAt = null,
        sourceCapturedAt = 1L,
        sensitive = sensitive,
        lifecycle = ContextSourceLifecycle.ACTIVE,
        updatedAt = 2L,
    )

    private fun artifact(id: String) = ArtifactLibraryItem(
        artifactId = id,
        issueId = "issue-1",
        issueTitle = "测试会话",
        stageId = "stage-1",
        stageTitle = "当前节点",
        title = "成果-$id",
        contentSummary = "成果摘要",
        artifactType = ArtifactType.GENERAL_SUMMARY,
        rawArtifactType = ArtifactType.GENERAL_SUMMARY.storageValue,
        confirmedAt = 2L,
        revisionOfArtifactId = null,
        revisionNumber = 1,
        latest = true,
    )
}
