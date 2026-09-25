package com.elysium369.meet.commerce.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * ══════════════════════════════════════════════════════════════════════
 *  C O M M E R C E   O R D E R   E N T I T Y
 *  ──────────────────────────────────────────────────────────────
 *  Elysium Vanguard Local Commerce & Triangular Delivery Engine:
 *  1. Cliente: Pide abarrotes en pulpería o comida criolla en soda.
 *  2. Comercio: Prepara el pedido y avisa cuando está listo.
 *  3. Repartidor / Chofer MEET: Recoge en pulpería/soda, transporta
 *     con GPS en tiempo real y entrega al cliente con PIN de seguridad.
 *  4. Escrow Dual: Subtotal al comercio + tarifa de envío al chofer.
 * ══════════════════════════════════════════════════════════════════════
 */
@Entity(
    tableName = "commerce_orders",
    indices = [
        Index("status"),
        Index("customerId"),
        Index("merchantId"),
        Index("courierId"),
    ]
)
@Serializable
data class CommerceOrderEntity(
    @PrimaryKey val orderId: String,
    val commerceType: String,             // "PULPERIA", "SODA_RESTAURANT"
    val merchantId: String,
    val merchantName: String,
    val merchantPhone: String,
    val merchantAddress: String,
    val merchantLat: Double = 0.0,
    val merchantLng: Double = 0.0,
    val customerId: String,
    val customerName: String,
    val customerPhone: String,
    val deliveryAddress: String,
    val deliveryLat: Double = 0.0,
    val deliveryLng: Double = 0.0,
    val itemsJson: String,                // Resumen o lista JSON de artículos pedidos
    val itemsSubtotalMinor: Long = 0L,    // Monto para la pulpería o soda (en CRC)
    val deliveryFeeMinor: Long = 0L,      // Monto para el chofer/repartidor MEET (en CRC)
    val totalAmountMinor: Long = 0L,      // Total cliente = subtotal + envío
    val currency: String = "CRC",
    val paymentMethod: String = "CASH",   // "SINPE", "CASH", "CARD"
    val paymentStatus: String = "PENDING",// "PENDING", "ESCROW_HELD", "RELEASED", "REFUNDED"
    val courierId: String? = null,
    val courierName: String? = null,
    val courierPhone: String? = null,
    val courierVehicle: String? = null,   // Ej: "Motocicleta Yamaha YBR 125 (Placa M-8921)"
    val deliveryPin: String = "1234",     // PIN de 4 dígitos para entrega segura
    val status: String = "PLACED",        // "PLACED", "CONFIRMED", "PREPARING", "READY_FOR_PICKUP", "COURIER_ASSIGNED", "IN_TRANSIT", "ARRIVED", "DELIVERED", "CANCELLED"
    val createdAt: Long = System.currentTimeMillis(),
    val readyAt: Long? = null,
    val pickedUpAt: Long? = null,
    val deliveredAt: Long? = null,
    val completedAt: Long? = null,
    val integrityHash: String? = null,    // Hash forense SHA-256 de cierre
    val ratingStars: Int? = null,
    val reviewNotes: String? = null,
)
