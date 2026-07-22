package com.elio.skillroundtable.network

import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InteractionSseAccumulatorTest {

    @Test
    fun modelOutputDeltasAreAccumulatedWhileThoughtDeltasAreIgnored() {
        val accumulator = InteractionSseAccumulator()

        accumulator.accept(
            """{"event_type":"interaction.created","interaction":{"id":"int_123","status":"in_progress"}}"""
        )
        accumulator.accept(
            """{"event_type":"step.start","index":0,"step":{"type":"thought"}}"""
        )
        val thoughtProgress = accumulator.accept(
            """{"event_type":"step.delta","index":0,"delta":{"type":"text","text":"内部思考"}}"""
        )
        accumulator.accept(
            """{"event_type":"step.start","index":1,"step":{"type":"model_output"}}"""
        )
        val first = accumulator.accept(
            """{"event_type":"step.delta","index":1,"delta":{"type":"text","text":"第一段"}}"""
        )
        val second = accumulator.accept(
            """{"event_type":"step.delta","index":1,"delta":{"type":"text","text":"第二段"}}"""
        )
        val completed = accumulator.accept(
            """{"event_type":"interaction.completed","interaction":{"id":"int_123","status":"completed"}}"""
        )

        assertFalse(thoughtProgress.textChanged)
        assertEquals("第一段", first.text)
        assertEquals("第一段第二段", second.text)
        assertEquals("第一段第二段", accumulator.outputText)
        assertEquals("int_123", accumulator.interactionId)
        assertTrue(completed.flushSuggested)
        assertTrue(accumulator.completed)
    }

    @Test
    fun multipleModelOutputStepsRemainInOrder() {
        val accumulator = InteractionSseAccumulator()

        accumulator.accept(
            """{"event_type":"interaction.created","interaction":{"id":"int_multi"}}"""
        )
        accumulator.accept(
            """{"event_type":"step.start","index":2,"step":{"type":"model_output"}}"""
        )
        accumulator.accept(
            """{"event_type":"step.delta","index":2,"delta":{"type":"text","text":"A"}}"""
        )
        accumulator.accept(
            """{"event_type":"step.stop","index":2}"""
        )
        accumulator.accept(
            """{"event_type":"step.start","index":3,"step":{"type":"model_output"}}"""
        )
        accumulator.accept(
            """{"event_type":"step.delta","index":3,"delta":{"type":"text","text":"B"}}"""
        )
        accumulator.accept(
            """{"event_type":"interaction.completed","interaction":{"id":"int_multi"}}"""
        )

        assertEquals("AB", accumulator.outputText)
        assertTrue(accumulator.completed)
    }

    @Test(expected = IOException::class)
    fun failedInteractionIsRejected() {
        val accumulator = InteractionSseAccumulator()
        accumulator.accept(
            """{"event_type":"interaction.failed","interaction":{"id":"int_failed","status":"failed"}}"""
        )
    }
}
