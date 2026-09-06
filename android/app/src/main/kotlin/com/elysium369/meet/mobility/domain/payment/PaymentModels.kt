package com.elysium369.meet.mobility.domain.payment

import com.elysium369.meet.mobility.domain.models.Money
import java.time.Instant
import java.util.UUID

enum class PaymentMethodType {
    CASH,
    CARD_TOKEN,
    WALLET,
    CORPORATE_ACCOUNT,
    PROMO_CREDIT,
    SINPE_MOVIL,
}

enum class PaymentAuthorizationState {
    PENDING,
    AUTHORIZED,
    DECLINED,
    EXPIRED,
    CANCELLED,
    CAPTURED,
}

data class PaymentAuthorization(
    val authorizationId: UUID,
    val tripId: UUID?,
    val riderId: UUID,
    val provider: String,
    val providerAuthRef: String?,
    val amount: Money,
    val state: PaymentAuthorizationState,
    val createdAt: Instant,
)
