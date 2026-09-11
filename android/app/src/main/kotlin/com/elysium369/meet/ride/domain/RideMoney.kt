package com.elysium369.meet.ride.domain

import com.elysium369.meet.core.money.CurrencyCode
import com.elysium369.meet.core.money.Money

@Deprecated(
    message = "Use com.elysium369.meet.core.money.Money instead. This class is retained for backward compatibility only.",
    replaceWith = ReplaceWith(
        "Money",
        "com.elysium369.meet.core.money.Money"
    )
)
typealias RideMoney = Money

@Deprecated(
    message = "Use com.elysium369.meet.core.money.CurrencyCode instead. This class is retained for backward compatibility only.",
    replaceWith = ReplaceWith(
        "CurrencyCode",
        "com.elysium369.meet.core.money.CurrencyCode"
    )
)
typealias CurrencyCode = com.elysium369.meet.core.money.CurrencyCode

object CostaRicaRidePolicy {
    val promotionalGrant: Money = Money.of(
        amountMinor = 100_000,
        currency = "CRC",
    )
}
