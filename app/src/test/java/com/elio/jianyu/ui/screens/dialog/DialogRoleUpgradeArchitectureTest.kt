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
    }

    private fun repositoryFile(path: String): File = listOf(
        File(path),
        File("../$path"),
    ).firstOrNull(File::isFile) ?: File(path)
}
