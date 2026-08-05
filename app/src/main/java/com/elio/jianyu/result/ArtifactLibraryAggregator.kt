package com.elio.jianyu.result

import com.elio.jianyu.data.ConfirmedArtifactEntity
import com.elio.jianyu.data.IssueRecoverySnapshot

object ArtifactLibraryAggregator {
    fun aggregate(
        snapshots: List<IssueRecoverySnapshot>,
    ): ArtifactLibrarySnapshot {
        val items = mutableListOf<ArtifactLibraryItem>()
        val problems = mutableListOf<ArtifactRevisionProblem>()

        snapshots.forEach { snapshot ->
            val artifacts = snapshot.resources.artifacts
            val resolution = ArtifactRevisionResolver.resolve(artifacts)
            problems += resolution.problems
            val byId = artifacts.associateBy { it.id }
            val stagesById = snapshot.core.stages.associateBy { it.id }
            artifacts.forEach { artifact ->
                items += ArtifactLibraryItem(
                    artifactId = artifact.id,
                    issueId = artifact.issueId,
                    issueTitle = snapshot.core.issue.title,
                    stageId = artifact.stageId,
                    stageTitle = stagesById[artifact.stageId]?.title ?: "阶段不可用",
                    title = artifact.title,
                    contentSummary = artifact.content.lineSequence()
                        .firstOrNull { it.isNotBlank() }
                        ?.trim()
                        .orEmpty()
                        .take(120),
                    artifactType = ArtifactType.fromStorageValue(artifact.artifactType),
                    rawArtifactType = artifact.artifactType,
                    confirmedAt = artifact.confirmedAt,
                    revisionOfArtifactId = artifact.revisionOfArtifactId,
                    revisionNumber = revisionNumber(artifact, byId),
                    latest = artifact.id in resolution.latestArtifactIds,
                )
            }
        }

        return ArtifactLibrarySnapshot(
            items = items.sortedWith(
                compareByDescending<ArtifactLibraryItem> { it.confirmedAt }
                    .thenBy { it.artifactId },
            ),
            revisionProblems = problems.distinctBy { it.code to it.artifactIds.sorted() },
        )
    }

    fun visibleItems(
        snapshot: ArtifactLibrarySnapshot,
        query: String = "",
        types: Set<ArtifactType> = emptySet(),
        includeHistory: Boolean = false,
    ): List<ArtifactLibraryItem> {
        val normalizedQuery = query.trim()
        return snapshot.items.filter { item ->
            (includeHistory || item.latest) &&
                (types.isEmpty() || item.artifactType in types) &&
                (
                    normalizedQuery.isEmpty() ||
                        item.title.contains(normalizedQuery, ignoreCase = true) ||
                        item.contentSummary.contains(normalizedQuery, ignoreCase = true) ||
                        item.issueTitle.contains(normalizedQuery, ignoreCase = true) ||
                        item.stageTitle.contains(normalizedQuery, ignoreCase = true)
                    )
        }
    }

    private fun revisionNumber(
        artifact: ConfirmedArtifactEntity,
        byId: Map<String, ConfirmedArtifactEntity>,
    ): Int {
        var count = 1
        var current = artifact
        val visited = mutableSetOf(current.id)
        while (true) {
            val parentId = current.revisionOfArtifactId ?: break
            val parent = byId[parentId] ?: break
            if (parent.issueId != artifact.issueId || parent.stageId != artifact.stageId) break
            if (!visited.add(parent.id)) break
            count += 1
            current = parent
        }
        return count
    }
}
