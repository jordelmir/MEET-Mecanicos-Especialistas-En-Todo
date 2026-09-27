package com.elysium369.meet.communications

import kotlinx.serialization.Serializable

@Serializable
data class CommunicationNearbyPacket(val payload:String,val signature:String)
@Serializable
data class CommunicationNearbyMessage(
    val version:Int=1,
    val purpose:String,
    val eventId:String,
    val conversationId:String,
    val senderId:String,
    val senderDeviceId:String,
    val envelope:String?,
    val createdAt:Long,
    val expiresAt:Long,
)
