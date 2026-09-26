package com.elio.jianyu.ui.screens.dialog

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DialogRoleUpgradeArchitectureTest {
    @Test
    fun dialogAddRole_usesOfficialCompatibilityBridgeInsteadOfLegacySilentLookup() {
        val route = repositoryFile(
            "app/src/main/java/com/elio/jianyu/ui/screens/dialog/DialogRoute.kt",
        ).readText()
        val viewModel = repositoryFile(
            "app/src/main/java/com/elio/jianyu/viewmodel/RoundtableViewModel.kt",
        ).readText()

        assertTrue(route.contains("viewModel.addSkillRoleToCurrentSessionAwait(event.skillId)"))
        assertFalse(route.contains("viewModel.addSkillRoleToCurrentSession(event.skillId)"))
        assertFalse(viewModel.contains("fun addSkillRoleToCurrentSession(skillId: String)"))
        assertTrue(viewModel.contains("ensureOfficialParticipantCharacters(stored)"))
        assertTrue(viewModel.contains("OfficialSkillConversationRoleAdapter(application, charRepo)"))
        assertTrue(viewModel.contains("officialAdapter.ensureCompatibleCharacter(officialDefinition)"))
        assertFalse(
            viewModel.contains(
                "if (charRepo.getCharacterById(skillId) != null) return@forEach",
            ),
        )

        val legacyConfig = repositoryFile("app/src/main/assets/skills_config.json").readText()
        assertTrue(
            legacyConfig.contains(
                "\"skillAssetPath\": \"skills/official/zhang_xuefeng/SKILL.md\"",
            ),
        )
        assertFalse(
            legacyConfig.contains(
                "\"skillAssetPath\": \"skills/zhangxuefeng-skill-main/SKILL.md\"",
            ),
        )
    }

    private fun repositoryFile(path: String): File = listOf(
        File(path),
        File("../$path"),
    ).firstOrNull(File::isFile) ?: File(path)
}
