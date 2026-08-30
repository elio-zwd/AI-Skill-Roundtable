package com.elio.jianyu.roundtable

import com.elio.jianyu.data.Character
import com.elio.jianyu.data.Message

object TranscriptBuilder {
    enum class ResponseMode {
        INDEPENDENT,
        CROSS_DISCUSSION,
    }

    /**
     * 构建 Skill 角色回答的上下文 Prompt（Transcript）。
     *
     * 规则：
     * 1. 默认独立回应只使用用户消息和当前角色自己的设定，不注入其他角色输出。
     * 2. 用户显式发起交叉讨论时，才把当前问题后的角色观点作为讨论输入。
     * 3. 响应批次只用于内部恢复，不形成主从、正反方或必须继承的领导性结论。
     */
    fun build(
        messages: List<Message>,
        currentCharacter: Character,
        roundIndex: Int,
        responseMode: ResponseMode = ResponseMode.INDEPENDENT,
    ): String {
        val sb = StringBuilder()
        sb.append("【Skill 角色对话上下文】\n\n")

        val lastUserIndex = messages.indexOfLast { it.senderId == "user" }
        val lastUserMsg = if (lastUserIndex != -1) messages[lastUserIndex] else null

        if (lastUserMsg != null) {
            val priorUserMessages = messages
                .take(lastUserIndex)
                .filter { it.senderId == "user" && !it.isPending }
                .takeLast(6)
            if (priorUserMessages.isNotEmpty()) {
                sb.append("用户此前明确表达的信息：\n")
                priorUserMessages.forEach { message ->
                    sb.append("- ${message.text}\n")
                }
                sb.append("\n")
            }
            sb.append("用户当前请求：${lastUserMsg.text}\n\n")

            if (responseMode == ResponseMode.CROSS_DISCUSSION) {
                val roleMessages = messages
                    .subList(lastUserIndex + 1, messages.size)
                    .filterNot { message ->
                        message.isPending ||
                            (message.senderId == currentCharacter.id && message.roundIndex == roundIndex)
                    }
                if (roleMessages.isNotEmpty()) {
                    sb.append("用户已显式发起交叉讨论。以下观点是本次讨论的明确输入：\n\n")
                    roleMessages.forEach { message ->
                        sb.append("Skill 角色「${message.senderName}」的观点：\n${message.text}\n\n")
                    }
                }
            }
        }

        sb.append("你是 Skill 角色「${currentCharacter.name}」。请保持自己的角色设定、思考方式和表达风格。\n")
        if (responseMode == ResponseMode.INDEPENDENT) {
            sb.append("请独立形成判断。不要假设其他角色已经给出结论，也不要为了制造差异而强行反对。")
        } else {
            sb.append("请针对上面的具体观点进行回应，说明赞同、补充或质疑的依据与适用条件。")
        }
        sb.append("第一句话直接切入重点，给出清晰判断；证据不足时明确说明不确定性。")

        return sb.toString()
    }
}
