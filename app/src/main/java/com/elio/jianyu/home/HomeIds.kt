package com.elio.jianyu.home

import java.util.UUID

fun interface HomeIdProvider {
    fun create(): HomeWorkflowIds
}

object UuidHomeIdProvider : HomeIdProvider {
    override fun create(): HomeWorkflowIds {
        val workflowId = "home-workflow-${UUID.randomUUID()}"
        return HomeWorkflowIds(
            workflowId = workflowId,
            issueId = "issue-${UUID.randomUUID()}",
            stageId = "stage-${UUID.randomUUID()}",
            runId = "run-${UUID.randomUUID()}",
            saveIssueIdempotencyKey = "save-$workflowId",
            executionIdempotencyKey = "execute-$workflowId",
        )
    }
}
