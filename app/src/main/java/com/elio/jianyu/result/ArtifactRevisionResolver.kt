package com.elio.jianyu.result

import com.elio.jianyu.data.ConfirmedArtifactEntity

object ArtifactRevisionResolver {
    fun resolve(
        artifacts: List<ConfirmedArtifactEntity>,
    ): ArtifactRevisionResolution {
        val ordered = artifacts.sortedWith(
            compareBy<ConfirmedArtifactEntity> { it.confirmedAt }.thenBy { it.id },
        )
        val byId = ordered.associateBy { it.id }
        val validParentByChild = linkedMapOf<String, String>()
        val problems = mutableListOf<ArtifactRevisionProblem>()

        ordered.forEach { artifact ->
            val parentId = artifact.revisionOfArtifactId ?: return@forEach
            when {
                parentId == artifact.id -> problems += problem(
                    ArtifactRevisionProblemCode.SELF_CYCLE,
                    artifact.id,
                )
                byId[parentId] == null -> problems += problem(
                    ArtifactRevisionProblemCode.ORPHAN_REFERENCE,
                    artifact.id,
                    parentId,
                )
                byId.getValue(parentId).issueId != artifact.issueId -> problems += problem(
                    ArtifactRevisionProblemCode.CROSS_ISSUE,
                    artifact.id,
                    parentId,
                )
                byId.getValue(parentId).stageId != artifact.stageId -> problems += problem(
                    ArtifactRevisionProblemCode.CROSS_STAGE,
                    artifact.id,
                    parentId,
                )
                else -> validParentByChild[artifact.id] = parentId
            }
        }

        val childrenByParent = validParentByChild.entries
            .groupBy(keySelector = { it.value }, valueTransform = { it.key })
            .mapValues { (_, childIds) -> childIds.sorted() }
        childrenByParent.forEach { (parentId, childIds) ->
            if (childIds.size > 1) {
                problems += ArtifactRevisionProblem(
                    code = ArtifactRevisionProblemCode.FORK,
                    artifactIds = (childIds + parentId).toSet(),
                )
            }
        }

        val cycleKeys = mutableSetOf<String>()
        ordered.forEach { artifact ->
            val path = mutableListOf<String>()
            val indexById = mutableMapOf<String, Int>()
            var currentId: String? = artifact.id
            while (currentId != null) {
                val existingIndex = indexById[currentId]
                if (existingIndex != null) {
                    val cycleIds = path.subList(existingIndex, path.size).toSet()
                    val key = cycleIds.sorted().joinToString("|")
                    if (cycleKeys.add(key)) {
                        problems += ArtifactRevisionProblem(
                            ArtifactRevisionProblemCode.CYCLE,
                            cycleIds,
                        )
                    }
                    break
                }
                indexById[currentId] = path.size
                path += currentId
                currentId = validParentByChild[currentId]
            }
        }

        val roots = ordered.filter { it.id !in validParentByChild }
        val chains = roots.map { root ->
            val versions = mutableListOf<ConfirmedArtifactEntity>()
            val visited = mutableSetOf<String>()
            var current: ConfirmedArtifactEntity? = root
            while (current != null && visited.add(current.id)) {
                versions += current
                val childIds = childrenByParent[current.id].orEmpty()
                current = if (childIds.size == 1) byId[childIds.single()] else null
            }
            ArtifactRevisionChain(
                rootArtifactId = root.id,
                versions = versions,
            )
        }
        val latestArtifactIds = ordered.asSequence()
            .filter { childrenByParent[it.id].isNullOrEmpty() }
            .map { it.id }
            .toSet()

        return ArtifactRevisionResolution(
            allArtifacts = ordered,
            chains = chains,
            latestArtifactIds = latestArtifactIds,
            problems = problems.distinctBy { it.code to it.artifactIds.sorted() },
        )
    }

    private fun problem(
        code: ArtifactRevisionProblemCode,
        vararg artifactIds: String,
    ) = ArtifactRevisionProblem(code, artifactIds.toSet())
}
