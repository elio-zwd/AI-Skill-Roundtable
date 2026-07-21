package com.example.skillroundtable.roundtable

/**
 * 圆桌脑暴提问预算设置。
 */
data class RoundtableBudget(
    val maxCharactersPerQuestion: Int = 6,
    val maxSearchQueriesPerCharacter: Int = 3,
    val maxApiCallsPerQuestion: Int = 30,
    val maxOutputTokensPerAnswer: Int = 4096
)

/**
 * 线程安全的 API 调用计数追踪器。
 */
class RequestBudgetTracker(val limit: Int) {
    private var used = 0

    /**
     * 尝试消耗 [count] 次 API 请求预算，如果剩余预算足够则返回 true 并累加，否则返回 false。
     */
    @Synchronized
    fun tryConsume(count: Int = 1): Boolean {
        if (used + count <= limit) {
            used += count
            return true
        }
        return false
    }

    /**
     * 针对主回答等 REQUIRED 请求消费预算。
     * 必须保证消费后，剩余的可用预算至少能够承载为后续角色保留的必需请求次数 [reserveForOtherRequired]。
     */
    @Synchronized
    fun tryConsumeRequired(count: Int = 1, reserveForOtherRequired: Int = 0): Boolean {
        if (used + count + reserveForOtherRequired <= limit) {
            used += count
            return true
        }
        return false
    }

    /**
     * 针对可选请求（OPTIONAL，如 Broker, 搜索, 续写, 标题）进行消费预算。
     * 必须保证消费后，剩余的可用预算至少能够承载预留的主回答次数 [reserveForRequired]。
     */
    @Synchronized
    fun tryConsumeOptional(count: Int = 1, reserveForRequired: Int): Boolean {
        if (used + count + reserveForRequired <= limit) {
            used += count
            return true
        }
        return false
    }

    @Synchronized
    fun getUsed(): Int = used

    @Synchronized
    fun getRemaining(): Int = limit - used

    @Synchronized
    fun isExceeded(): Boolean = used >= limit
}
