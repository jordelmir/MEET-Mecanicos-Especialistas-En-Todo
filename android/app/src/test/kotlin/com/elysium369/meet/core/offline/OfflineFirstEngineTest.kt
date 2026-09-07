package com.elysium369.meet.core.offline

import org.junit.Assert.*
import org.junit.Test

class OfflineFirstEngineTest {

    @Test
    fun `enqueue always succeeds regardless of connectivity`() {
        val engine = OfflineFirstEngine()
        engine.updateConnectivity(ConnectivityState.OFFLINE)
        val op = engine.createOperation(
            OperationType.CREATE, "job", "job-1", """{"title":"Fix pipe"}""",
        )
        assertTrue(op.isPending)
        assertEquals(1, engine.pendingCount)
    }

    @Test
    fun `operations queued by priority`() {
        val engine = OfflineFirstEngine()
        engine.createOperation(OperationType.UPDATE, "profile", "p1", "{}", OperationPriority.LOW)
        engine.createOperation(OperationType.PAYMENT, "payment", "pay1", "{}", OperationPriority.CRITICAL)
        engine.createOperation(OperationType.CREATE, "job", "j1", "{}", OperationPriority.NORMAL)
        val pending = engine.pendingOperations()
        assertEquals(OperationPriority.CRITICAL, pending.first().priority)
        assertEquals(OperationPriority.LOW, pending.last().priority)
    }

    @Test
    fun `sync succeeds when online`() {
        val engine = OfflineFirstEngine()
        engine.updateConnectivity(ConnectivityState.ONLINE)
        engine.createOperation(OperationType.CREATE, "job", "j1", "{}")
        engine.createOperation(OperationType.UPDATE, "job", "j2", "{}")
        val result = engine.sync()
        assertEquals(2, result.synced)
        assertTrue(result.isFullySync)
        assertEquals(0, engine.pendingCount)
    }

    @Test
    fun `sync fails when offline`() {
        val engine = OfflineFirstEngine()
        engine.updateConnectivity(ConnectivityState.OFFLINE)
        engine.createOperation(OperationType.CREATE, "job", "j1", "{}")
        val result = engine.sync()
        assertEquals(0, result.synced)
        assertEquals(1, engine.pendingCount) // still pending
    }

    @Test
    fun `sync works when degraded`() {
        val engine = OfflineFirstEngine()
        engine.updateConnectivity(ConnectivityState.DEGRADED)
        engine.createOperation(OperationType.CREATE, "job", "j1", "{}")
        val result = engine.sync()
        assertEquals(1, result.synced)
    }

    @Test
    fun `SMS encoding and decoding roundtrip`() {
        val engine = OfflineFirstEngine()
        val op = engine.createOperation(
            OperationType.CREATE, "emergency", "em-1", "{}",
            OperationPriority.CRITICAL,
        )
        val sms = engine.encodeForSms(op)
        assertTrue(sms.length <= 160) // SMS limit
        val decoded = engine.decodeFromSms(sms)
        assertNotNull(decoded)
        assertEquals(OperationType.CREATE, decoded!!.type)
    }

    @Test
    fun `conflict resolution resolves record`() {
        val engine = OfflineFirstEngine()
        engine.addConflict(ConflictRecord(
            "op-1", "job", "j1", "local-v1", "remote-v2",
        ))
        assertEquals(1, engine.unresolvedConflicts.size)
        assertTrue(engine.resolveConflict("op-1", ConflictStrategy.LAST_WRITE_WINS))
        assertEquals(0, engine.unresolvedConflicts.size)
    }

    @Test
    fun `integrity hash is computed for every operation`() {
        val engine = OfflineFirstEngine()
        val op = engine.createOperation(OperationType.CREATE, "x", "1", "{}")
        assertTrue(op.integrityHash.startsWith("sha256-"))
    }

    @Test
    fun `diagnostics report reflects engine state`() {
        val engine = OfflineFirstEngine()
        engine.updateConnectivity(ConnectivityState.MESH_ONLY)
        engine.createOperation(OperationType.PAYMENT, "p", "1", "{}", OperationPriority.CRITICAL)
        val diag = engine.diagnostics()
        assertEquals(ConnectivityState.MESH_ONLY, diag.connectivity)
        assertEquals(1, diag.pendingOperations)
        assertEquals(1, diag.criticalPending)
    }

    @Test
    fun `all 5 connectivity states exist`() {
        assertEquals(5, ConnectivityState.entries.size)
    }
}
