package com.elysium369.meet.commerce.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object para órdenes de comercio local (Pulperías & Sodas)
 * y despacho triangular con repartidores de la red MEET.
 */
@Dao
interface CommerceOrderDao {

    @Query("SELECT * FROM commerce_orders ORDER BY createdAt DESC")
    fun getAllOrders(): Flow<List<CommerceOrderEntity>>

    @Query("SELECT * FROM commerce_orders WHERE status NOT IN ('DELIVERED', 'CANCELLED') ORDER BY createdAt DESC")
    fun getActiveOrders(): Flow<List<CommerceOrderEntity>>

    @Query("SELECT * FROM commerce_orders WHERE status IN ('DELIVERED', 'CANCELLED') ORDER BY createdAt DESC")
    fun getCompletedOrders(): Flow<List<CommerceOrderEntity>>

    @Query("SELECT * FROM commerce_orders WHERE customerId = :customerId AND status NOT IN ('DELIVERED', 'CANCELLED') ORDER BY createdAt DESC")
    fun getActiveOrdersForCustomer(customerId: String): Flow<List<CommerceOrderEntity>>

    @Query("SELECT * FROM commerce_orders WHERE customerId = :customerId AND status IN ('DELIVERED', 'CANCELLED') ORDER BY createdAt DESC")
    fun getCompletedOrdersForCustomer(customerId: String): Flow<List<CommerceOrderEntity>>

    @Query("SELECT * FROM commerce_orders WHERE merchantId = :merchantId AND status NOT IN ('DELIVERED', 'CANCELLED') ORDER BY createdAt DESC")
    fun getActiveOrdersForMerchant(merchantId: String): Flow<List<CommerceOrderEntity>>

    @Query("SELECT * FROM commerce_orders WHERE status IN ('READY_FOR_PICKUP', 'COURIER_ASSIGNED', 'IN_TRANSIT', 'ARRIVED') ORDER BY createdAt DESC")
    fun getActiveCourierMissions(): Flow<List<CommerceOrderEntity>>

    @Query("SELECT * FROM commerce_orders WHERE orderId = :orderId")
    suspend fun getOrderById(orderId: String): CommerceOrderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrder(order: CommerceOrderEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrders(orders: List<CommerceOrderEntity>)

    @Query("UPDATE commerce_orders SET status = :status, readyAt = :readyAt WHERE orderId = :orderId")
    suspend fun updateMerchantStatus(orderId: String, status: String, readyAt: Long? = null)

    @Query("UPDATE commerce_orders SET status = 'COURIER_ASSIGNED', courierId = :courierId, courierName = :courierName, courierPhone = :courierPhone, courierVehicle = :courierVehicle WHERE orderId = :orderId")
    suspend fun assignCourier(orderId: String, courierId: String, courierName: String, courierPhone: String, courierVehicle: String)

    @Query("UPDATE commerce_orders SET status = :status, pickedUpAt = :pickedUpAt WHERE orderId = :orderId")
    suspend fun updateCourierTransitStatus(orderId: String, status: String, pickedUpAt: Long? = null)

    @Query("UPDATE commerce_orders SET status = 'DELIVERED', deliveredAt = :deliveredAt, completedAt = :deliveredAt, paymentStatus = 'RELEASED', integrityHash = :integrityHash WHERE orderId = :orderId")
    suspend fun completeDeliveryWithPin(orderId: String, deliveredAt: Long, integrityHash: String)

    @Query("UPDATE commerce_orders SET status = 'CANCELLED', paymentStatus = 'REFUNDED' WHERE orderId = :orderId")
    suspend fun cancelOrder(orderId: String)

    @Query("UPDATE commerce_orders SET ratingStars = :stars, reviewNotes = :review WHERE orderId = :orderId")
    suspend fun rateOrder(orderId: String, stars: Int, review: String?)
}
