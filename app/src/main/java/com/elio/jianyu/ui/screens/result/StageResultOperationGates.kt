package com.elio.jianyu.ui.screens.result

import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class StageDraftSaveGate {
    private val mutex = Mutex()

    suspend fun <T> run(block: suspend () -> T): T = mutex.withLock {
        block()
    }
}

internal class StageArtifactConfirmationGate {
    private val inFlight = AtomicBoolean(false)

    fun tryStart(): Boolean = inFlight.compareAndSet(false, true)

    fun finish() {
        inFlight.set(false)
    }
}
