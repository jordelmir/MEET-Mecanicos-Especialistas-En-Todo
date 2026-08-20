package com.elysium369.meet.vehiclelife.documents

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

interface DigitalGloveboxRepository {
    val documents: StateFlow<List<VehicleDocument>>
    suspend fun saveDocument(doc: VehicleDocument)
    suspend fun deleteDocument(documentId: String)
    suspend fun getDocumentsForVehicle(vehicleId: String): List<VehicleDocument>
    suspend fun getExpiringDocuments(vehicleId: String, withinDays: Long = 30): List<VehicleDocument>
}

@Singleton
class DefaultDigitalGloveboxRepository @Inject constructor() : DigitalGloveboxRepository {
    private val _documents = MutableStateFlow<List<VehicleDocument>>(emptyList())
    override val documents: StateFlow<List<VehicleDocument>> = _documents.asStateFlow()

    override suspend fun saveDocument(doc: VehicleDocument) {
        _documents.value = listOf(doc) + _documents.value.filter { it.documentId != doc.documentId }
    }

    override suspend fun deleteDocument(documentId: String) {
        _documents.value = _documents.value.filter { it.documentId != documentId }
    }

    override suspend fun getDocumentsForVehicle(vehicleId: String): List<VehicleDocument> {
        return _documents.value.filter { it.vehicleId == vehicleId }
    }

    override suspend fun getExpiringDocuments(vehicleId: String, withinDays: Long): List<VehicleDocument> {
        return _documents.value.filter {
            it.vehicleId == vehicleId &&
            it.daysUntilExpiry != null &&
            it.daysUntilExpiry!! in 0..withinDays
        }
    }
}
