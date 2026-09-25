package com.elio.jianyu.ui.screens.mine

import com.elio.jianyu.data.ContextSourceLifecycle
import org.junit.Assert.assertEquals
import org.junit.Test

class PersonalContextDeletionPolicyTest {
    @Test
    fun deletionCanResumeFromEveryIntermediateLifecycle() {
        assertEquals(
            PersonalContextDeletionStep.MARK_DELETED,
            personalContextDeletionStep(ContextSourceLifecycle.ACTIVE),
        )
        assertEquals(
            PersonalContextDeletionStep.MARK_DELETED,
            personalContextDeletionStep(ContextSourceLifecycle.DISABLED),
        )
        assertEquals(
            PersonalContextDeletionStep.MARK_DELETED,
            personalContextDeletionStep(ContextSourceLifecycle.ARCHIVED),
        )
        assertEquals(
            PersonalContextDeletionStep.REQUEST_PURGE,
            personalContextDeletionStep(ContextSourceLifecycle.DELETED),
        )
        assertEquals(
            PersonalContextDeletionStep.PURGE,
            personalContextDeletionStep(ContextSourceLifecycle.PURGE_REQUESTED),
        )
        assertEquals(
            PersonalContextDeletionStep.COMPLETE,
            personalContextDeletionStep(ContextSourceLifecycle.PURGED),
        )
    }
}
