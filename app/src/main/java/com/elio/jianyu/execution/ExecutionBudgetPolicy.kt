package com.elio.jianyu.execution

/**
 * 预算预判仅用于在进入 Repository 前快速拒绝；数据库原子更新仍是最终事实源。
 */
object ExecutionBudgetPolicy {
    fun canConsume(
        snapshot: ExecutionBudgetSnapshot,
        kind: ExecutionBudgetCallKind,
        count: Int,
        reserveAfter: Int,
    ): Boolean {
        require(count > 0)
        require(reserveAfter >= 0)
        if (snapshot.closed) return false
        val requiredCapacity = when (kind) {
            ExecutionBudgetCallKind.REQUIRED -> reserveAfter
            ExecutionBudgetCallKind.OPTIONAL -> maxOf(
                snapshot.reservedRequiredCalls,
                reserveAfter,
            )
        }
        return snapshot.usedApiCalls + count + requiredCapacity <= snapshot.maxApiCalls
    }

    fun consume(
        snapshot: ExecutionBudgetSnapshot,
        kind: ExecutionBudgetCallKind,
        count: Int,
        reserveAfter: Int,
    ): ExecutionBudgetSnapshot? {
        if (!canConsume(snapshot, kind, count, reserveAfter)) return null
        return snapshot.copy(
            usedApiCalls = snapshot.usedApiCalls + count,
            reservedRequiredCalls = when (kind) {
                ExecutionBudgetCallKind.REQUIRED -> reserveAfter
                ExecutionBudgetCallKind.OPTIONAL -> snapshot.reservedRequiredCalls
            },
        )
    }
}
