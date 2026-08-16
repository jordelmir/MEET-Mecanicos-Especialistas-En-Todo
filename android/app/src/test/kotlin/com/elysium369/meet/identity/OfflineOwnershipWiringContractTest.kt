package com.elysium369.meet.identity

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineOwnershipWiringContractTest {
    private fun source(path: String) = File(path).readText()

    @Test
    fun pendingDiagnosticDataIsAlwaysOwnerScoped() {
        val dao = source("src/main/kotlin/com/elysium369/meet/data/local/dao/Daos.kt")
        val worker = source("src/main/kotlin/com/elysium369/meet/core/sync/SyncWorker.kt")

        assertTrue(dao.contains("getPendingSync(ownerPrincipalId: String)"))
        assertTrue(dao.contains("getPendingSyncDtcs(ownerPrincipalId: String)"))
        assertTrue(worker.contains("OfflineOwnership.canSync(entity.ownerPrincipalId, activePrincipal)"))
        assertFalse(worker.contains("user_id = userId"))
    }

    @Test
    fun garageProjectionSwitchesWithActivePrincipal() {
        val viewModel = source("src/main/kotlin/com/elysium369/meet/ui/ObdViewModel.kt")

        assertTrue(viewModel.contains("activePrincipalKernel.activePrincipal.flatMapLatest"))
        assertFalse(viewModel.contains("getVehiclesForUser(currentProviderUserId())"))
    }

    @Test
    fun roomMigrationQuarantinesUnownedLegacyRows() {
        val module = source("src/main/kotlin/com/elysium369/meet/di/AppModule.kt")

        assertTrue(module.contains("MIGRATION_56_57"))
        assertTrue(module.contains("OWNER_UNKNOWN_LEGACY"))
        assertTrue(module.contains("ownerPrincipalId"))
    }
}
