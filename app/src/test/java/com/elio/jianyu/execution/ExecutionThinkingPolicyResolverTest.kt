package com.elio.jianyu.execution

import com.elio.jianyu.data.ExecutionRunKind
import com.elio.jianyu.data.ExecutionThinkingLevel
import com.elio.jianyu.data.ExecutionThinkingSource
import com.elio.jianyu.data.IssueThinkingPolicy
import org.junit.Assert.assertEquals
import org.junit.Test

class ExecutionThinkingPolicyResolverTest {
    @Test
    fun roundOverrideWinsAndNeverChangesIssueDefault() {
        val resolved = ExecutionThinkingPolicyResolver.resolve(
            issueDefault = IssueThinkingPolicy.LOW,
            roundOverride = IssueThinkingPolicy.HIGH,
            runKind = ExecutionRunKind.STANDARD,
        )

        assertEquals(ExecutionThinkingLevel.HIGH, resolved.level)
        assertEquals(ExecutionThinkingSource.ROUND_USER_OVERRIDE, resolved.source)
    }

    @Test
    fun fixedIssueDefaultWinsOverAutomaticRouting() {
        val resolved = ExecutionThinkingPolicyResolver.resolve(
            issueDefault = IssueThinkingPolicy.MINIMAL,
            roundOverride = null,
            runKind = ExecutionRunKind.CROSS_DISCUSSION_RESPONSE,
        )

        assertEquals(ExecutionThinkingLevel.MINIMAL, resolved.level)
        assertEquals(ExecutionThinkingSource.ISSUE_USER_DEFAULT, resolved.source)
    }

    @Test
    fun autoUsesHighForEntireCrossDiscussion() {
        val response = ExecutionThinkingPolicyResolver.resolve(
            issueDefault = IssueThinkingPolicy.AUTO,
            roundOverride = null,
            runKind = ExecutionRunKind.CROSS_DISCUSSION_RESPONSE,
        )
        val synthesis = ExecutionThinkingPolicyResolver.resolve(
            issueDefault = IssueThinkingPolicy.AUTO,
            roundOverride = null,
            runKind = ExecutionRunKind.CROSS_DISCUSSION_SYNTHESIS,
        )

        assertEquals(ExecutionThinkingLevel.HIGH, response.level)
        assertEquals(response.level, synthesis.level)
        assertEquals(ExecutionThinkingSource.AUTO_ROUTED, response.source)
    }
}
