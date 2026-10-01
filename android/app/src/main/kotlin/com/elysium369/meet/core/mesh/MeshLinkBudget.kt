package com.elysium369.meet.core.mesh

import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicBoolean

/** Counts pending connections and authenticated links atomically, before any asynchronous work. */
class MeshLinkBudget(capacity: Int = 4) {
    private val permits = Semaphore(capacity)
    init { require(capacity in 1..16) }
    fun reserve(): Lease? = if (permits.tryAcquire()) Lease { permits.release() } else null
    class Lease internal constructor(private val release: () -> Unit) : AutoCloseable {
        private val closed = AtomicBoolean(false)
        override fun close() { if (closed.compareAndSet(false, true)) release() }
    }
}
