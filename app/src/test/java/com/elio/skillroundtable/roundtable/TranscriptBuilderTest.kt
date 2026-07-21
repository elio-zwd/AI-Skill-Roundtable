package com.elio.skillroundtable.roundtable

import com.elio.skillroundtable.data.Character
import com.elio.skillroundtable.data.Message
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
        
        assertTrue("第一轮应该包含用户提问", result.contains("用户提问：你好，请问你是谁？"))
        assertTrue("应该指明是第 1 轮发言", result.contains("在第 1 轮发言了"))
        assertFalse("第一轮在消息只有用户提问时，不应包含其他智囊的历史发言", result.contains("智囊「"))
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

        assertTrue("应该包含用户提问", result.contains("用户提问：你好"))
        assertTrue("应该包含已经确认的前序智囊发言", result.contains("智囊「智囊B」在第 1 轮发言"))
        assertTrue("智囊发言内容应正确", result.contains("我是智囊B的发言"))
        assertFalse("应该排除 pending 状态的消息", result.contains("思考中..."))
        assertTrue("应该指明是第 2 轮发言", result.contains("在第 2 轮发言了"))
    }
}
