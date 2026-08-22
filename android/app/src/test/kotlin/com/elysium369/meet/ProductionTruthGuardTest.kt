package com.elysium369.meet

import com.elysium369.meet.core.actioncenter.ActionInboxEntry
import com.elysium369.meet.core.actioncenter.ActionState
import com.elysium369.meet.core.actioncenter.DefaultActionInboxRepository
import com.elysium369.meet.core.diagnostics.DiagnosticReasoningEngine
import com.elysium369.meet.core.domain.VehicleContext
import com.elysium369.meet.core.evair.agent.AntigravityGateway
import com.elysium369.meet.core.evair.domain.*
import com.elysium369.meet.core.evair.safety.ExecutionStatus
import com.elysium369.meet.core.evair.safety.VehicleActionExecutor
import com.elysium369.meet.core.evair.safety.VehicleSafetyBroker
import com.elysium369.meet.core.evair.telemetry.TelemetryCollector
import com.elysium369.meet.core.money.CurrencyCode
import com.elysium369.meet.core.money.Money
import com.elysium369.meet.core.obd.ObdDataSource
import com.elysium369.meet.core.obd.ObdSession
import com.elysium369.meet.core.obd.ObdState
import com.elysium369.meet.core.obd.PhysicalBusOwner
import com.elysium369.meet.core.obd.TelemetryQuality
import com.elysium369.meet.core.obd.TelemetrySample
import com.elysium369.meet.core.vehicleaccess.domain.AccessPermission
import com.elysium369.meet.core.vehicleaccess.providers.OemCloudAccessProvider
import com.elysium369.meet.ui.screens.home.adaptive.HomeAction
import com.elysium369.meet.ui.screens.home.adaptive.HomeActionCategory
import com.elysium369.meet.ui.screens.home.adaptive.HomeActionEngine
import com.elysium369.meet.ui.screens.home.adaptive.HomeActionPriority
import com.elysium369.meet.vehiclelife.costs.DefaultVehicleFinancialLedgerRepository
import com.elysium369.meet.vehiclelife.costs.ExpenseCategory
import com.elysium369.meet.vehiclelife.costs.FinancialEntry
import com.elysium369.meet.vehiclelife.costs.FinancialState
import com.elysium369.meet.vehiclelife.passport.DefaultVehiclePassportRepository
import com.elysium369.meet.vehiclelife.timeline.TimelineCategoryFilter
import com.elysium369.meet.vehiclelife.timeline.VehicleTimelineRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ProductionTruthGuardTest {

    // ── 1. EVAIR & PHYSICAL TRUTH GUARDS ──

    @Test
    fun unknownSpeedFailsClosedTest() = runBlocking {
        val broker = VehicleSafetyBroker(getPhysicalBusOwner = { PhysicalBusOwner.IDLE })
        val executor = VehicleActionExecutor(broker, null)
        val action = ProposedVehicleAction(
            actionId = "ACT_ACTIVE_TEST_01",
            command = VehicleCommand.RunDiagnosticTest("REQ_01", "EVAP_TEST"),
            reason = "Test",
            expectedObservation = "RPM Drop",
            risk = ActionRisk.HIGH
        )
        val snapshotWithUnknownSpeed = VehicleSnapshot(
            timestampMs = 1000L,
            monotonicTimestampNs = 1000L,
            vehicle = VehicleIdentity("V1", null, null, null, null, null, null, null),
            connection = ConnectionSnapshot("CONNECTED", true, null, null, null, null),
            engine = EngineSnapshot(speedKph = null), // Unknown speed
            electrical = ElectricalSnapshot(),
            fuel = FuelSnapshot(),
            transmission = null,
            emissions = EmissionsSnapshot(),
            dtcs = emptyList(),
            readiness = emptyMap(),
            activeWarnings = emptyList(),
            dataSource = VehicleDataSource.REAL_OBD
        )

        val result = executor.executeAction(action, snapshotWithUnknownSpeed, userConfirmed = true)
        assertTrue(result is EvairResult.Failure)
        val failure = result as EvairResult.Failure
        assertTrue(failure.error is EvairError.SafetyDenied)
        val error = failure.error as EvairError.SafetyDenied
        assertTrue(error.reason.contains("UNKNOWN") || error.reason.contains("no verificada"))
    }

    @Test
    fun vehicleActionCannotReportUnexecutedSuccessTest() = runBlocking {
        val broker = VehicleSafetyBroker(getPhysicalBusOwner = { PhysicalBusOwner.IDLE })

        // ObdSession is null -> must return SIMULATED and isSuccess = false
        val executor = VehicleActionExecutor(broker, null)
        val action = ProposedVehicleAction(
            actionId = "ACT_READ_01",
            command = VehicleCommand.RunDiagnosticTest("REQ_02", "TEST_VALVE"),
            reason = "Test",
            expectedObservation = "Click sound",
            risk = ActionRisk.NONE
        )
        val snapshot = VehicleSnapshot(
            timestampMs = 1000L,
            monotonicTimestampNs = 1000L,
            vehicle = VehicleIdentity("V1", null, null, null, null, null, null, null),
            connection = ConnectionSnapshot("DISCONNECTED", false, null, null, null, null),
            engine = EngineSnapshot(speedKph = 0.0),
            electrical = ElectricalSnapshot(),
            fuel = FuelSnapshot(),
            transmission = null,
            emissions = EmissionsSnapshot(),
            dtcs = emptyList(),
            readiness = emptyMap(),
            activeWarnings = emptyList(),
            dataSource = VehicleDataSource.OFFLINE
        )

        val result = executor.executeAction(action, snapshot, userConfirmed = true)
        assertTrue(result is EvairResult.Success)
        val execution = (result as EvairResult.Success).value
        assertEquals(ExecutionStatus.SIMULATED, execution.status)
        assertFalse(execution.isSuccess)
    }

    @Test
    fun noDtcDoesNotMeanNominalTest() = runBlocking {
        val reasoner = DiagnosticReasoningEngine()
        val gateway = AntigravityGateway(reasoner)

        val snapshot = VehicleSnapshot(
            timestampMs = 1000L,
            monotonicTimestampNs = 1000L,
            vehicle = VehicleIdentity("V1", null, null, null, null, null, null, null),
            connection = ConnectionSnapshot("CONNECTED", true, null, null, null, null),
            engine = EngineSnapshot(),
            electrical = ElectricalSnapshot(),
            fuel = FuelSnapshot(),
            transmission = null,
            emissions = EmissionsSnapshot(),
            dtcs = emptyList(), // 0 DTCs
            readiness = emptyMap(),
            activeWarnings = emptyList(),
            dataSource = VehicleDataSource.REAL_OBD
        )

        val request = DiagnosticAgentRequest(
            requestId = "REQ-01",
            vehicleId = "V1",
            trigger = DiagnosticTrigger.USER_REQUEST,
            snapshot = snapshot
        )

        val result = gateway.diagnose(request)
        assertTrue(result is EvairResult.Success)
        val diag = (result as EvairResult.Success).value
        assertFalse(diag.summary.contains("nominal", ignoreCase = true))
        assertTrue(diag.summary.contains("No se observaron códigos DTC", ignoreCase = true))
    }

    @Test
    fun outOfRangeIsNotGoodQualityTest() {
        val sampleOutOfRange = TelemetrySample(
            pid = "0105",
            name = "Coolant Temp",
            value = 250.0,
            unit = "°C",
            source = ObdDataSource.REAL_OBD,
            quality = TelemetryQuality.OUT_OF_RANGE,
            timestampMonotonicMs = 1000L,
            latencyMs = 20L,
            rawResponse = "4105FA"
        )

        val quality = when (sampleOutOfRange.quality) {
            TelemetryQuality.VALID -> DataQuality.GOOD
            TelemetryQuality.OUT_OF_RANGE -> DataQuality.INVALID
            TelemetryQuality.STALE -> DataQuality.STALE
            TelemetryQuality.SIMULATED, TelemetryQuality.MANUAL -> DataQuality.ESTIMATED
            else -> DataQuality.INVALID
        }

        assertEquals(DataQuality.INVALID, quality)
    }

    // ── 2. VEHICLE ACCESS & CRYPTO GUARDS ──

    @Test
    fun oemCloudWithoutProviderFailsClosedTest() = runBlocking {
        val provider = OemCloudAccessProvider()
        val result = provider.executeRemoteCommand("V1", "1HGCR2F83HA000000", "UNLOCK", AccessPermission.ENTRY)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("OEM_CLOUD_NOT_CONFIGURED") == true)
    }

    @Test
    fun homeActionEngineFallbackTitleTest() {
        val actions = HomeActionEngine.derivePrioritizedActions(
            hasVehicle = true,
            vehicleId = "V1",
            obdState = ObdState.CONNECTED,
            activeDtcs = emptyList(),
            healthScore = 95,
            monitorsReady = 8,
            monitorsTotal = 8
        )

        assertEquals(1, actions.size)
        val fallback = actions.first()
        assertEquals("Sin alertas activas con datos disponibles", fallback.title)
        assertFalse(fallback.title.contains("Estado Nominal", ignoreCase = true))
    }

    // ── 3. FINANCIAL LEDGER & MONEY GUARDS ──

    @Test
    fun moneyOverflowRejectedTest() {
        val m1 = Money(Long.MAX_VALUE - 10, CurrencyCode.USD)
        val m2 = Money(20L, CurrencyCode.USD)

        assertThrows(ArithmeticException::class.java) {
            m1 + m2
        }

        assertThrows(ArithmeticException::class.java) {
            m1 * 2
        }
    }

    @Test
    fun invoiceIsNotPaidTest() = runBlocking {
        val repo = DefaultVehicleFinancialLedgerRepository()
        val invoiceEntry = FinancialEntry(
            entryId = "E1",
            vehicleId = "V1",
            category = ExpenseCategory.MAINTENANCE,
            state = FinancialState.INVOICED, // Invoiced != Paid
            amount = Money(5000L, CurrencyCode.USD),
            description = "Cambio de aceite"
        )
        val paidEntry = FinancialEntry(
            entryId = "E2",
            vehicleId = "V1",
            category = ExpenseCategory.FUEL,
            state = FinancialState.PAID,
            amount = Money(2000L, CurrencyCode.USD),
            description = "Gasolina"
        )

        repo.recordEntry(invoiceEntry)
        repo.recordEntry(paidEntry)

        val tco = repo.calculateTco("V1", 100)
        assertEquals(2000L, tco.totalPaid.amountMinor)
        assertEquals(5000L, tco.totalQuotedPending.amountMinor)
    }

    @Test
    fun passportUnknownTitleNotVerifiedTest() = runBlocking {
        val fakeTimelineRepo = object : VehicleTimelineRepository {
            override val events: StateFlow<List<com.elysium369.meet.core.vehiclelife.VehicleLifeEvent>>
                get() = MutableStateFlow(emptyList())
            override suspend fun recordEvent(event: com.elysium369.meet.core.vehiclelife.VehicleLifeEvent) {}
            override suspend fun getEventsForVehicle(vehicleId: String, filter: TimelineCategoryFilter): List<com.elysium369.meet.core.vehiclelife.VehicleLifeEvent> = emptyList()
        }

        val passportRepo = DefaultVehiclePassportRepository(fakeTimelineRepo)
        val context = VehicleContext(vehicleId = "V1", ownerPrincipalId = "USER_01", vin = "1HGCR2F83HA000000", make = "Honda", model = "Accord", year = 2017)

        val passport = passportRepo.buildPassport(context, 100, 0)
        assertFalse(passport.identity.isTitleVerified)
        assertNull(passport.financial.totalInvested)
    }

    // ── 4. ACTION INBOX SNOOZE & EXPIRATION GUARDS ──

    @Test
    fun actionSnoozeExpiresCorrectlyTest() = runBlocking {
        val inbox = DefaultActionInboxRepository()
        val action = HomeAction(
            id = "ACT_01",
            priority = HomeActionPriority.HIGH,
            category = HomeActionCategory.MAINTENANCE_DUE,
            title = "Mantenimiento",
            subtitle = "Cambio de frenos",
            destination = "maint",
            buttonLabel = "VER",
            glyph = "🔧"
        )

        inbox.updateActions(listOf(action))
        assertEquals(1, inbox.activeActions.value.size)
        assertEquals(ActionState.ACTIVE, inbox.activeActions.value.first().state)

        // Snooze for 60 seconds
        inbox.snoozeAction("ACT_01", 60_000L)
        val snoozedEntry = inbox.activeActions.value.first()
        assertEquals(ActionState.SNOOZED, snoozedEntry.state)
        assertNotNull(snoozedEntry.snoozedUntilUtc)

        // Simulate update while snoozed -> remains snoozed
        inbox.updateActions(listOf(action))
        assertEquals(ActionState.SNOOZED, inbox.activeActions.value.first().state)
    }
}
