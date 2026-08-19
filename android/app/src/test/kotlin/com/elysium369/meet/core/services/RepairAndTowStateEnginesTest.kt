package com.elysium369.meet.core.services

import com.elysium369.meet.core.services.kernel.ServiceRole
import com.elysium369.meet.core.services.parts.CompatibilityConfidence
import com.elysium369.meet.core.services.parts.PartCommandAction
import com.elysium369.meet.core.services.parts.PartRequestStatusV2
import com.elysium369.meet.core.services.parts.PartsStateEngine
import com.elysium369.meet.core.services.repair.RepairAction
import com.elysium369.meet.core.services.repair.RepairState
import com.elysium369.meet.core.services.repair.RepairStateEngine
import com.elysium369.meet.core.services.tow.TowAction
import com.elysium369.meet.core.services.tow.TowState
import com.elysium369.meet.core.services.tow.TowStateEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class RepairAndTowStateEnginesTest {

    @Test
    fun testRepairLifecycleTransitions() {
        val requestId = UUID.randomUUID()
        val offerId = UUID.randomUUID()

        // 1. DRAFT -> PUBLISHED (Customer only)
        val published = RepairStateEngine.getNextState(RepairState.DRAFT, RepairAction.Publish(requestId), ServiceRole.CUSTOMER)
        assertEquals(RepairState.PUBLISHED, published)

        // 2. Technician submits offer -> OFFER_RECEIVED
        val offerReceived = RepairStateEngine.getNextState(RepairState.PUBLISHED, RepairAction.ReceiveOffer(offerId), ServiceRole.TECHNICIAN)
        assertEquals(RepairState.OFFER_RECEIVED, offerReceived)

        // 3. Customer accepts offer -> OFFER_ACCEPTED
        val accepted = RepairStateEngine.getNextState(RepairState.OFFER_RECEIVED, RepairAction.AcceptOffer(offerId), ServiceRole.CUSTOMER)
        assertEquals(RepairState.OFFER_ACCEPTED, accepted)

        // 4. Technician starts route -> IN_ROUTE
        val inRoute = RepairStateEngine.getNextState(RepairState.OFFER_ACCEPTED, RepairAction.StartRoute, ServiceRole.TECHNICIAN)
        assertEquals(RepairState.IN_ROUTE, inRoute)

        // 5. Customer cannot start route
        val invalidStart = RepairStateEngine.getNextState(RepairState.OFFER_ACCEPTED, RepairAction.StartRoute, ServiceRole.CUSTOMER)
        assertNull(invalidStart)

        // 6. Inspection -> Diagnosis -> Repair in progress -> Completed -> Validation Pending -> Customer confirmed -> Closed
        val inspection = RepairStateEngine.getNextState(RepairState.IN_ROUTE, RepairAction.StartInspection, ServiceRole.TECHNICIAN)
        assertEquals(RepairState.INSPECTION_STARTED, inspection)

        val diag = RepairStateEngine.getNextState(RepairState.INSPECTION_STARTED, RepairAction.ConfirmDiagnosis("hash123"), ServiceRole.TECHNICIAN)
        assertEquals(RepairState.DIAGNOSIS_CONFIRMED, diag)

        val inProgress = RepairStateEngine.getNextState(RepairState.DIAGNOSIS_CONFIRMED, RepairAction.ResumeRepair, ServiceRole.TECHNICIAN)
        assertEquals(RepairState.REPAIR_IN_PROGRESS, inProgress)

        val validationPending = RepairStateEngine.getNextState(RepairState.REPAIR_IN_PROGRESS, RepairAction.CompleteTechnicianWork("before", "after"), ServiceRole.TECHNICIAN)
        assertEquals(RepairState.VALIDATION_PENDING, validationPending)

        val validated = RepairStateEngine.getNextState(RepairState.VALIDATION_PENDING, RepairAction.SubmitPostScanValidation("scanHash", 1), ServiceRole.TECHNICIAN)
        assertEquals(RepairState.CUSTOMER_CONFIRMED, validated)

        val closed = RepairStateEngine.getNextState(RepairState.CUSTOMER_CONFIRMED, RepairAction.CloseWorkOrder, ServiceRole.CUSTOMER)
        assertEquals(RepairState.CLOSED, closed)
    }

    @Test
    fun testTowStateEngineTransitions() {
        val operatorId = UUID.randomUUID()
        val assigned = TowStateEngine.getNextState(TowState.REQUESTED, TowAction.AssignOperator(operatorId, "UNIT_01"), ServiceRole.PLATFORM_ADMIN)
        assertEquals(TowState.ASSIGNED, assigned)

        val enRoute = TowStateEngine.getNextState(TowState.ASSIGNED, TowAction.StartEnRoute, ServiceRole.TOW_OPERATOR)
        assertEquals(TowState.EN_ROUTE, enRoute)

        val arrived = TowStateEngine.getNextState(TowState.EN_ROUTE, TowAction.ConfirmArrival, ServiceRole.TOW_OPERATOR)
        assertEquals(TowState.ARRIVED, arrived)

        val loading = TowStateEngine.getNextState(TowState.ARRIVED, TowAction.StartLoading, ServiceRole.TOW_OPERATOR)
        assertEquals(TowState.LOADING, loading)

        val loaded = TowStateEngine.getNextState(TowState.LOADING, TowAction.ConfirmLoaded("hash"), ServiceRole.TOW_OPERATOR)
        assertEquals(TowState.LOADED, loaded)

        val inTransit = TowStateEngine.getNextState(TowState.LOADED, TowAction.StartTransit, ServiceRole.TOW_OPERATOR)
        assertEquals(TowState.IN_TRANSIT, inTransit)

        val atDest = TowStateEngine.getNextState(TowState.IN_TRANSIT, TowAction.ArrivedAtDestination, ServiceRole.TOW_OPERATOR)
        assertEquals(TowState.ARRIVED_DESTINATION, atDest)

        val unloading = TowStateEngine.getNextState(TowState.ARRIVED_DESTINATION, TowAction.StartUnloading, ServiceRole.TOW_OPERATOR)
        assertEquals(TowState.UNLOADING, unloading)

        val delivered = TowStateEngine.getNextState(TowState.UNLOADING, TowAction.ConfirmDelivered("hash"), ServiceRole.TOW_OPERATOR)
        assertEquals(TowState.DELIVERED, delivered)

        val completed = TowStateEngine.getNextState(TowState.DELIVERED, TowAction.CompleteService, ServiceRole.CUSTOMER)
        assertEquals(TowState.COMPLETED, completed)
    }

    @Test
    fun testPartsCompatibilityConfidenceTiers() {
        assertEquals(
            CompatibilityConfidence.EXACT,
            CompatibilityConfidence.evaluate(vinMatched = true, oemMatched = true, catalogExact = true, specsMatched = true, hasConflict = false)
        )
        assertEquals(
            CompatibilityConfidence.HIGH,
            CompatibilityConfidence.evaluate(vinMatched = false, oemMatched = false, catalogExact = true, specsMatched = true, hasConflict = false)
        )
        assertEquals(
            CompatibilityConfidence.PROBABLE,
            CompatibilityConfidence.evaluate(vinMatched = false, oemMatched = false, catalogExact = false, specsMatched = true, hasConflict = false)
        )
        assertEquals(
            CompatibilityConfidence.CONFLICTED,
            CompatibilityConfidence.evaluate(vinMatched = true, oemMatched = true, catalogExact = true, specsMatched = true, hasConflict = true)
        )
    }
}
