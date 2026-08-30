package com.elio.jianyu.roundtable

/**
 * 多 Skill 角色对话的单次回答与参与者设置。
 */
data class RoundtableBudget(
    val maxCharactersPerQuestion: Int = 15,
    val maxSearchQueriesPerCharacter: Int = 3,
    val maxOutputTokensPerAnswer: Int = 4096
)

/**
 * 线程安全的 API 调用计数器，只记录实际尝试次数，不会因累计次数拒绝请求。
 */
class RequestBudgetTracker {
    private var used = 0

    /**
     * 记录一次请求。
     */
    @Synchronized
    fun tryConsume(count: Int = 1): Boolean {
        require(count > 0)
        used += count
        return true
    }

    /**
     * 记录主回答等必需请求。
     */
    @Synchronized
    fun tryConsumeRequired(count: Int = 1): Boolean = tryConsume(count)

    /**
     * 记录可选请求（如搜索、续写、标题）。
     */
    @Synchronized
    fun tryConsumeOptional(count: Int = 1): Boolean = tryConsume(count)

    @Synchronized
    fun getUsed(): Int = used

}
