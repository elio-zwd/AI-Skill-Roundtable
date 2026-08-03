package com.elio.jianyu.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JianyuRepositoryContractTest {
    @Test
    fun lifecycleArchiveAndTrashTransitionsPreservePreviousState() {
        val active = lifecycle(state = IssueLifecycleState.ACTIVE)

        val archived = resolveLifecycleTransition(active, LifecycleAction.ARCHIVE, 20L)
        assertEquals(IssueLifecycleState.ARCHIVED, archived.state)
        assertEquals(20L, archived.archivedAt)
        assertNull(archived.previousState)

        val trashed = resolveLifecycleTransition(archived, LifecycleAction.MOVE_TO_TRASH, 30L)
        assertEquals(IssueLifecycleState.TRASHED, trashed.state)
        assertEquals(IssueLifecycleState.ARCHIVED, trashed.previousState)
        assertEquals(30L, trashed.trashedAt)

        val restored = resolveLifecycleTransition(trashed, LifecycleAction.RESTORE_FROM_TRASH, 40L)
        assertEquals(IssueLifecycleState.ARCHIVED, restored.state)
        assertNull(restored.previousState)
        assertNull(restored.trashedAt)
    }

    @Test
    fun repeatedLifecycleTargetIsIdempotent() {
        val archived = lifecycle(
            state = IssueLifecycleState.ARCHIVED,
            archivedAt = 20L,
            changedAt = 20L
        )

        val repeated = resolveLifecycleTransition(archived, LifecycleAction.ARCHIVE, 99L)

        assertEquals(archived, repeated)
    }

    @Test(expected = IllegalArgumentException::class)
    fun purgeRequestRequiresTrashedState() {
        resolveLifecycleTransition(
            lifecycle(state = IssueLifecycleState.ACTIVE),
            LifecycleAction.REQUEST_PURGE,
            20L
        )
    }

    @Test
    fun participantPayloadRejectsDuplicateSourceAndPosition() {
        val first = participant(id = "p-1", sourceId = "skill-1", position = 0)
        val duplicateSource = participant(id = "p-2", sourceId = "skill-1", position = 1)
        val duplicatePosition = participant(id = "p-3", sourceId = "skill-3", position = 0)

        assertFalse(validateParticipantPayload(listOf(first, duplicateSource)))
        assertFalse(validateParticipantPayload(listOf(first, duplicatePosition)))
        assertTrue(validateParticipantPayload(listOf(first)))
    }

    @Test
    fun repositoryErrorsNeverCarrySensitivePayloadFields() {
        val error = RepositoryError.StorageFailure(
            operation = "recover_issue",
            retryable = true
        )

        val rendered = error.toString()
        assertFalse(rendered.contains("content", ignoreCase = true))
        assertFalse(rendered.contains("prompt", ignoreCase = true))
        assertFalse(rendered.contains("apiKey", ignoreCase = true))
    }

    private fun lifecycle(
        state: IssueLifecycleState,
        archivedAt: Long? = null,
        changedAt: Long = 10L
    ): IssueLifecycleEntity {
        return IssueLifecycleEntity(
            issueId = "issue-1",
            state = state,
            previousState = null,
            stateChangedAt = changedAt,
            updatedAt = changedAt,
            archivedAt = archivedAt,
            trashedAt = null,
            purgeRequestedAt = null
        )
    }

    private fun participant(
        id: String,
        sourceId: String,
        position: Int
    ): ExecutionParticipantSnapshotEntity {
        return ExecutionParticipantSnapshotEntity(
            id = id,
            runId = "run-1",
            sourceType = "official_skill",
            sourceId = sourceId,
            displayName = sourceId,
            avatar = "A",
            skillAssetPath = "skills/$sourceId/SKILL.md",
            systemPrompt = "system",
            configurationJson = "{}",
            defaultResponsibility = "",
            position = position,
            createdAt = 10L
        )
    }
}
