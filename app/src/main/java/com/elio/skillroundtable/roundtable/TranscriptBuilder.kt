package com.elio.skillroundtable.roundtable

import com.elio.skillroundtable.data.Character
import com.elio.skillroundtable.data.Message

object TranscriptBuilder {
    /**
     * 构建圆桌脑暴的上下文 Prompt（Transcript）。
     *
     * 规则：
     * 1. 无论轮次多少，都拉取当前用户提问后的所有非 pending 消息。
     * 2. 角色 A 只能看到用户问题。
     * 3. 角色 B 能看到用户问题和角色 A 在当前轮的发言。
     * 4. 角色 C 能看到用户问题、角色 A 和角色 B 在当前轮的发言。
     * 5. 新的用户问题会切断之前的圆桌脑暴历史，只保留最近一次用户提问之后的对话。
     */
    fun build(
        messages: List<Message>,
        currentCharacter: Character,
        roundIndex: Int
    ): String {
        val sb = StringBuilder()
        sb.append("【圆桌会议脑暴记录】\n\n")

        val lastUserIndex = messages.indexOfLast { it.senderId == "user" }
        val lastUserMsg = if (lastUserIndex != -1) messages[lastUserIndex] else null

        if (lastUserMsg != null) {
            sb.append("用户提问：${lastUserMsg.text}\n\n")
            val chatMessages = messages.subList(lastUserIndex + 1, messages.size)
            for (msg in chatMessages) {
                if (msg.isPending) continue
                // 排除当前角色正在生成的这次（其实因为 isPending 被过滤了，但做个双重保障）
                if (msg.senderId == currentCharacter.id && msg.roundIndex == roundIndex) continue
                sb.append("智囊「${msg.senderName}」在第 ${msg.roundIndex} 轮发言：\n${msg.text}\n\n")
            }
        }

        sb.append("现在，轮到你——「${currentCharacter.name}」在第 $roundIndex 轮发言了。\n")
        sb.append("请记住你的设定、说话语气和人设。")
        sb.append("请你站在你自己的专业背景与刺头/支持立场，对用户的提问进行解答，")
        if (roundIndex > 1) {
            sb.append("同时你**必须**参考、评判、补充或反驳前几位智囊在前几轮的发言，展现出真实的脑暴交锋！")
        }
        sb.append("第一句话请直接切入重点，给出明确的判断或观点，千万别废话铺垫！")

        return sb.toString()
    }
}
