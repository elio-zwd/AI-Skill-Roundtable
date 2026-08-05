package com.elio.jianyu.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResourceLifecycleModelTest {
    @Test
    fun storageEnumsUseStableLowercaseValues() {
        assertEquals(
            setOf("available", "purged"),
            SnapshotContentState.entries.map { it.storageValue }.toSet()
        )
        assertEquals(
            setOf("pending", "available", "missing", "failed", "canceled"),
            AudioFileState.entries.map { it.storageValue }.toSet()
        )
        assertEquals(
            setOf("active", "archived", "trashed"),
            IssueLifecycleState.entries.map { it.storageValue }.toSet()
        )
    }

    @Test
    fun audioAssetRequiresExactlyOneSource() {
        validateAudioAssetSource(sourceMessageId = 10L, sourceArtifactId = null)
        validateAudioAssetSource(sourceMessageId = null, sourceArtifactId = "artifact-1")

        assertFailsWithIllegalArgument {
            validateAudioAssetSource(sourceMessageId = null, sourceArtifactId = null)
        }
        assertFailsWithIllegalArgument {
            validateAudioAssetSource(sourceMessageId = 10L, sourceArtifactId = "artifact-1")
        }
    }

    @Test
    fun officialCombinationRejectsDuplicateSkillAndPosition() {
        val duplicateSkill = listOf(
            member(skillId = "skill-a", position = 0),
            member(skillId = "skill-a", position = 1)
        )
        val duplicatePosition = listOf(
            member(skillId = "skill-a", position = 0),
            member(skillId = "skill-b", position = 0)
        )

        assertFailsWithIllegalArgument {
            validateOfficialCombinationMembers(duplicateSkill)
        }
        assertFailsWithIllegalArgument {
            validateOfficialCombinationMembers(duplicatePosition)
        }
    }

    @Test
    fun artifactCannotReviseItself() {
        validateArtifactRevision(artifactId = "artifact-2", revisionOfArtifactId = "artifact-1")
        validateArtifactRevision(artifactId = "artifact-1", revisionOfArtifactId = null)

        assertFailsWithIllegalArgument {
            validateArtifactRevision(
                artifactId = "artifact-1",
                revisionOfArtifactId = "artifact-1"
            )
        }
    }

    @Test
    fun validationErrorsDoNotEchoSensitiveSnapshotContent() {
        val sensitiveContent = "private-personal-context-value"
        val error = runCatching {
            validateAudioAssetSource(sourceMessageId = null, sourceArtifactId = null)
        }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
        assertFalse(error?.message.orEmpty().contains(sensitiveContent))
    }

    private fun member(
        skillId: String,
        position: Int
    ): OfficialSkillCombinationMemberEntity {
        return OfficialSkillCombinationMemberEntity(
            combinationId = "combination-1",
            officialSkillId = skillId,
            position = position,
            defaultResponsibility = null,
            createdAt = 100L
        )
    }

    private fun assertFailsWithIllegalArgument(block: () -> Unit) {
        val error = runCatching(block).exceptionOrNull()
        assertTrue("预期 IllegalArgumentException，实际为 $error", error is IllegalArgumentException)
    }
}
