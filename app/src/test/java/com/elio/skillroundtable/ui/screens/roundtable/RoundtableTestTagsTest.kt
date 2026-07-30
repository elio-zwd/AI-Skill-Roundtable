package com.elio.skillroundtable.ui.screens.roundtable

import org.junit.Assert.assertEquals
import org.junit.Test

class RoundtableTestTagsTest {

    @Test
    fun requiredRoundtableTestTags_remainStable() {
        assertEquals(
            setOf(
                "new_session_button",
                "retry_failed_characters_button",
                "dismiss_failed_characters_button",
                "chat_input",
                "send_button",
                "stop_button",
            ),
            RoundtableTestTags.required,
        )
    }
}
