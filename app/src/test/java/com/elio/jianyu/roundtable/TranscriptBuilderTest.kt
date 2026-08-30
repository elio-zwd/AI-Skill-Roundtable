package com.elio.jianyu.roundtable

import com.elio.jianyu.data.Character
import com.elio.jianyu.data.Message
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TranscriptBuilderTest {

    @Test
    fun testBuildFirstRound() {
        val messages = listOf(
            Message(id = 1, chatId = 100, senderId = "user", senderName = "User", avatar = "U", text = "你好，请问你是谁？")
        )
        val character = Character(
            id = "char_a",
            name = "智囊A",
            avatar = "A",
            tagline = "测试智囊",
            systemPrompt = "系统设定",
            skillAssetPath = "skills/char_a/SKILL.md",
            order = 1
        )

        val result = TranscriptBuilder.build(messages, character, roundIndex = 1)
        
        assertTrue("第一轮应该包含用户当前请求", result.contains("用户当前请求：你好，请问你是谁？"))
        assertTrue("应该明确当前 Skill 角色", result.contains("Skill 角色「智囊A」"))
        assertTrue("默认模式应该要求独立形成判断", result.contains("请独立形成判断"))
    }

    @Test
    fun testBuildSubsequentRoundExcludePending() {
        val messages = listOf(
            Message(id = 1, chatId = 100, senderId = "user", senderName = "User", avatar = "U", text = "你好"),
            Message(id = 2, chatId = 100, senderId = "char_b", senderName = "智囊B", avatar = "B", text = "我是智囊B的发言", roundIndex = 1),
            Message(id = 3, chatId = 100, senderId = "char_a", senderName = "智囊A", avatar = "A", text = "思考中...", isPending = true, roundIndex = 2)
        )
        val character = Character(
            id = "char_a",
            name = "智囊A",
            avatar = "A",
            tagline = "测试",
            systemPrompt = "系统设定",
            skillAssetPath = "skills/char_a/SKILL.md",
            order = 1
        )

        val result = TranscriptBuilder.build(messages, character, roundIndex = 2)

        assertTrue("应该包含用户当前请求", result.contains("用户当前请求：你好"))
        assertFalse("默认独立回应不得注入其他角色输出", result.contains("我是智囊B的发言"))
        assertFalse("应该排除 pending 状态的消息", result.contains("思考中..."))
    }

    @Test
    fun crossDiscussionIncludesExplicitRoleViewpoints() {
        val messages = listOf(
            Message(id = 1, chatId = 100, senderId = "user", senderName = "User", avatar = "U", text = "你好"),
            Message(id = 2, chatId = 100, senderId = "char_b", senderName = "角色B", avatar = "B", text = "观点B", roundIndex = 1),
        )
        val character = Character(
            id = "char_a",
            name = "角色A",
            avatar = "A",
            tagline = "测试",
            systemPrompt = "系统设定",
            order = 1,
        )

        val result = TranscriptBuilder.build(
            messages = messages,
            currentCharacter = character,
            roundIndex = 2,
            responseMode = TranscriptBuilder.ResponseMode.CROSS_DISCUSSION,
        )

        assertTrue(result.contains("用户已显式发起交叉讨论"))
        assertTrue(result.contains("Skill 角色「角色B」的观点"))
        assertTrue(result.contains("观点B"))
    }
}
