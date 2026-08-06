package com.elio.jianyu.data

/**
 * 为事务内需要逐项调用挂起 DAO 的少量场景提供确定性顺序遍历。
 * 结果会在当前协程中立即收集，不创建惰性协程或并行数据库读取。
 */
internal suspend fun <T, R> Sequence<T>.flatMap(
    transform: suspend (T) -> Sequence<R>,
): Sequence<R> {
    val result = mutableListOf<R>()
    for (item in this) {
        result += transform(item).toList()
    }
    return result.asSequence()
}
