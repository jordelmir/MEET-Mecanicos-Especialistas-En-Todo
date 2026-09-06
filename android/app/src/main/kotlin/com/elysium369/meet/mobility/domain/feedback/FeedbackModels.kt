package com.elysium369.meet.mobility.domain.feedback

import java.time.Instant
import java.util.UUID

data class TripRating(
    val ratingId: UUID,
    val tripId: UUID,
    val reviewerId: UUID,
    val subjectId: UUID,
    val rating: Int,
    val comment: String? = null,
    val createdAt: Instant,
) {
    init {
        require(rating in 1..5) { "Rating must be between 1 and 5 stars, inclusive" }
        require(reviewerId != subjectId) { "Reviewer cannot rate themselves" }
        comment?.let {
            require(it.length <= 500) { "Comment cannot exceed 500 characters" }
        }
    }
}

enum class LostItemCaseState {
    OPEN,
    CONTACT_ATTEMPTED,
    RETURN_COORDINATED,
    RETURNED,
    UNRESOLVED,
    CLOSED,
}

data class LostItemCase(
    val caseId: UUID,
    val tripId: UUID,
    val riderId: UUID,
    val driverId: UUID,
    val itemDescription: String,
    val state: LostItemCaseState,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    init {
        require(itemDescription.isNotBlank()) { "Item description cannot be blank" }
        require(riderId != driverId) { "Rider and Driver cannot be identical" }
    }
}

enum class SupportCasePriority {
    LOW,
    MEDIUM,
    HIGH,
    URGENT,
}

enum class SupportCaseState {
    OPEN,
    INVESTIGATING,
    RESOLVED,
    CLOSED,
}

data class SupportCase(
    val caseId: UUID,
    val userId: UUID,
    val tripId: UUID? = null,
    val category: String,
    val priority: SupportCasePriority,
    val state: SupportCaseState,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    init {
        require(category.isNotBlank()) { "Support case category cannot be blank" }
    }
}
