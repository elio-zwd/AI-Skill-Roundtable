package com.elio.jianyu.execution

import android.content.Context
import com.elio.jianyu.data.ExecutionParticipantSnapshotEntity
import com.elio.jianyu.network.CreateInteractionRequest
import com.elio.jianyu.network.InteractionGenerationConfig
import com.elio.jianyu.network.InteractionStreamingClient
import com.elio.jianyu.network.Tool
import com.elio.jianyu.network.keys.ApiKeyScheduler
import com.elio.jianyu.roundtable.RequestBudgetTracker
import kotlinx.serialization.json.JsonPrimitive

data class ExecutionNetworkRequest(
    val sessionId: Long,
    val participant: ExecutionParticipantSnapshotEntity,
    val modelRequest: ExecutionModelRequest,
    val model: String,
    val thinkingLevel: String,
    /** 仅用于进程内的短链索引，不会发送给 Provider。 */
    val interactionChainKey: String,
    val maxOutputTokens: Int,
    val searchMode: SearchMode,
) {
    init {
        require(sessionId > 0L)
        require(model.isNotBlank())
        require(thinkingLevel in setOf("minimal", "low", "medium", "high"))
        require(interactionChainKey.isNotBlank())
        require(maxOutputTokens > 0)
    }
}

data class ExecutionNetworkResult(
    val providerInteractionId: String,
    val outputText: String,
    val providerModel: String? = null,
)

fun interface PreparedExecutionNetworkCall {
    suspend fun execute(
        onAttemptStarted: suspend () -> Unit,
        onTextUpdate: suspend (String) -> Unit,
    ): ExecutionNetworkResult
}

/**
 * 网络边界先准备 Key 尝试计划，再由调用方创建 Pending 消息。
 * 准备失败（例如无 Key）时不得留下悬挂 Pending。
 */
fun interface ExecutionNetworkGateway {
    suspend fun prepare(request: ExecutionNetworkRequest): PreparedExecutionNetworkCall
}

/**
 * 复用现有 Interactions 流式客户端的生产适配器。
 *
 * [RequestBudgetTracker] 仅作为旧客户端的传输层防御计数器，不是业务预算事实源；
 * 每次真实网络尝试前必须先执行 [PreparedExecutionNetworkCall.execute] 的
 * `onAttemptStarted`，由 Coordinator 原子消费 Room 预算。
 */
class InteractionExecutionNetworkGateway(
    context: Context,
) : ExecutionNetworkGateway {
    private val appContext = context.applicationContext

    override suspend fun prepare(request: ExecutionNetworkRequest): PreparedExecutionNetworkCall {
        val attemptPlan = ApiKeyScheduler.createAttemptPlan(
            context = appContext,
            sessionId = request.sessionId,
        )
        if (attemptPlan.isEmpty()) throw NoExecutionApiKeyException()

        return PreparedExecutionNetworkCall { onAttemptStarted, onTextUpdate ->
            val response = InteractionStreamingClient.createInteraction(
                context = appContext,
                request = CreateInteractionRequest(
                    model = request.model,
                    input = JsonPrimitive(request.modelRequest.userContent),
                    systemInstruction = request.modelRequest.systemInstruction +
                        request.searchMode.instruction,
                    tools = request.searchMode.googleSearchTool,
                    generationConfig = InteractionGenerationConfig(
                        maxOutputTokens = request.maxOutputTokens,
                        thinkingLevel = request.thinkingLevel,
                    ),
                    store = true,
                ),
                sessionId = request.sessionId,
                attemptPlan = attemptPlan,
                tracker = RequestBudgetTracker(TRANSPORT_GUARD_LIMIT),
                operationName = OPERATION_NAME,
                interactionChainKey = request.interactionChainKey,
                isRequired = true,
                reserveForRequired = 0,
                onAttemptStarted = onAttemptStarted,
                onTextUpdate = onTextUpdate,
            )
            ExecutionNetworkResult(
                providerInteractionId = response.id,
                outputText = response.outputText,
                providerModel = response.model,
            )
        }
    }

    private companion object {
        const val OPERATION_NAME = "JianyuExecution"
        const val TRANSPORT_GUARD_LIMIT = 1_000_000
    }
}

private val SearchMode.googleSearchTool: List<Tool>?
    get() = if (this == SearchMode.OFF) null else listOf(Tool(type = "google_search"))

private val SearchMode.instruction: String
    get() = when (this) {
        SearchMode.OFF -> """

            本次执行未提供 Google Search 工具。不得声称已经联网检索、实时核验或访问了某个来源。
        """.trimIndent()
        SearchMode.AUTO -> """

            本次执行提供了 Google Search 工具。仅在回答依赖时效性或外部可核验事实时使用该工具；
            未检索的信息不得表述为已核验事实。
        """.trimIndent()
        SearchMode.ON -> """

            用户已开启联网搜索。请先使用 Google Search 工具检索或核验，再回答问题；
            未检索的信息不得表述为已核验事实。
        """.trimIndent()
    }
