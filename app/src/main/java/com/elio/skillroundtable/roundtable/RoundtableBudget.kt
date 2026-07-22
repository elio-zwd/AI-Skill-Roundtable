package com.elio.skillroundtable.roundtable

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
 *
 * 除实际调用计数外，还维护下一步必须保留的 REQUIRED 请求数量。这样即使标题等
 * OPTIONAL 请求异步启动，也不能消耗为当前轮剩余角色或下一整轮预留的额度。
 */
class RequestBudgetTracker(val limit: Int) {
    private var used = 0
    private var requiredReserve = 0
    private var closed = false

    /**
     * 兼容旧调用的普通消费。关闭后不再允许产生新请求。
     */
    @Synchronized
    fun tryConsume(count: Int = 1): Boolean {
        if (!closed && used + count <= limit) {
            used += count
            return true
        }
        return false
    }

    /**
     * 针对主回答等 REQUIRED 请求消费预算。
     * 必须保证消费后，剩余可用预算至少能够承载后续角色的必需请求次数
     * [reserveForOtherRequired]。
     */
    @Synchronized
    fun tryConsumeRequired(count: Int = 1, reserveForOtherRequired: Int = 0): Boolean {
        val normalizedReserve = reserveForOtherRequired.coerceAtLeast(0)
        if (!closed && used + count + normalizedReserve <= limit) {
            used += count
            // 当前 REQUIRED 请求完成消费后，全局保护额度可以随剩余角色数量递减。
            requiredReserve = minOf(requiredReserve, normalizedReserve)
            return true
        }
        return false
    }

    /**
     * 针对可选请求（OPTIONAL，如 Broker、搜索、续写、标题）消费预算。
     * 除调用方声明的 [reserveForRequired] 外，还必须遵守追踪器维护的全局保护额度。
     */
    @Synchronized
    fun tryConsumeOptional(count: Int = 1, reserveForRequired: Int): Boolean {
        val effectiveReserve = maxOf(requiredReserve, reserveForRequired.coerceAtLeast(0))
        if (!closed && used + count + effectiveReserve <= limit) {
            used += count
            return true
        }
        return false
    }

    /**
     * 设置下一步至少需要保护的 REQUIRED 请求数量。
     */
    @Synchronized
    fun setRequiredReserve(count: Int) {
        requiredReserve = count.coerceAtLeast(0)
    }

    @Synchronized
    fun getRequiredReserve(): Int = requiredReserve

    /**
     * 当前问题已无法完整完成下一步时关闭预算。保留真实 used 计数，但拒绝后续请求。
     */
    @Synchronized
    fun close() {
        closed = true
    }

    @Synchronized
    fun getUsed(): Int = used

    @Synchronized
    fun getRemaining(): Int = limit - used

    @Synchronized
    fun isExceeded(): Boolean = closed || used >= limit
}
