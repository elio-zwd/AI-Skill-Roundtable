package com.elio.jianyu.runtime

import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeLeaseRegistryTest {
    @Test
    fun readyGenerationAllowsConcurrentLeasesAndCountsThem() {
        val registry = RuntimeLeaseRegistry()
        registry.openGeneration(1L)

        val first = registry.tryAcquire(1L)
        val second = registry.tryAcquire(1L)

        assertTrue(first != null)
        assertTrue(second != null)
        assertEquals(2, registry.activeLeaseCount(1L))
        first?.close()
        second?.close()
        assertEquals(0, registry.activeLeaseCount(1L))
    }

    @Test
    fun maintenanceStopsNewLeasesWithoutForcingExistingLeaseRelease() {
        val registry = RuntimeLeaseRegistry()
        registry.openGeneration(4L)
        val existing = requireNotNull(registry.tryAcquire(4L))

        assertTrue(registry.stopAccepting(4L))

        assertNull(registry.tryAcquire(4L))
        assertEquals(1, registry.activeLeaseCount(4L))
        existing.close()
        assertEquals(0, registry.activeLeaseCount(4L))
    }

    @Test
    fun maintenanceWaitsUntilEveryExistingLeaseIsReleased() = runBlocking {
        val registry = RuntimeLeaseRegistry()
        registry.openGeneration(7L)
        val first = requireNotNull(registry.tryAcquire(7L))
        val second = requireNotNull(registry.tryAcquire(7L))
        registry.stopAccepting(7L)

        val waiting = async {
            registry.awaitReleased(7L)
            true
        }
        yield()
        assertFalse(waiting.isCompleted)

        first.close()
        yield()
        assertFalse(waiting.isCompleted)

        second.close()
        assertTrue(waiting.await())
    }

    @Test
    fun leaseCloseIsIdempotent() {
        val registry = RuntimeLeaseRegistry()
        registry.openGeneration(2L)
        val lease = requireNotNull(registry.tryAcquire(2L))

        lease.close()
        lease.close()

        assertEquals(0, registry.activeLeaseCount(2L))
    }

    @Test
    fun oldGenerationLeaseCannotChangeNewGenerationCount() {
        val registry = RuntimeLeaseRegistry()
        registry.openGeneration(10L)
        val oldLease = requireNotNull(registry.tryAcquire(10L))
        registry.stopAccepting(10L)
        registry.openGeneration(11L)
        val newLease = requireNotNull(registry.tryAcquire(11L))

        oldLease.close()

        assertEquals(0, registry.activeLeaseCount(10L))
        assertEquals(1, registry.activeLeaseCount(11L))
        newLease.close()
    }

    @Test
    fun cancelingWaiterDoesNotClearAnotherOwnersLease() = runBlocking {
        val registry = RuntimeLeaseRegistry()
        registry.openGeneration(12L)
        val lease = requireNotNull(registry.tryAcquire(12L))
        registry.stopAccepting(12L)

        val waiting = async { registry.awaitReleased(12L) }
        yield()
        waiting.cancel()

        assertEquals(1, registry.activeLeaseCount(12L))
        lease.close()
        assertEquals(0, registry.activeLeaseCount(12L))
    }

    @Test
    fun wrongGenerationCannotStopCurrentGeneration() {
        val registry = RuntimeLeaseRegistry()
        registry.openGeneration(20L)

        assertFalse(registry.stopAccepting(19L))
        assertTrue(registry.tryAcquire(20L) != null)
    }
}
