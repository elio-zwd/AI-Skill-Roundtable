package com.elio.jianyu.ui.screens.result

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StageResultOperationGatesTest {
    @Test
    fun saveGateSerializesDoubleSaveInsteadOfRunningSameRevisionConcurrently() = runBlocking {
        val gate = StageDraftSaveGate()
        val firstStarted = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        val order = mutableListOf<Int>()

        val first = async {
            gate.run {
                order += 1
                firstStarted.complete(Unit)
                releaseFirst.await()
                order += 2
            }
        }
        firstStarted.await()
        val second = async {
            gate.run { order += 3 }
        }
        yield()

        assertEquals(listOf(1), order)
        releaseFirst.complete(Unit)
        first.await()
        second.await()
        assertEquals(listOf(1, 2, 3), order)
    }

    @Test
    fun confirmationGateRejectsSecondClickUntilFirstOperationFinishes() {
        val gate = StageArtifactConfirmationGate()

        assertTrue(gate.tryStart())
        assertFalse(gate.tryStart())
        gate.finish()
        assertTrue(gate.tryStart())
        gate.finish()
    }
}
