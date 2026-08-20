package com.elysium369.meet.vehiclelife.documents

enum class DocumentType(val displayName: String, val glyph: String) {
    REGISTRATION("Título de Propiedad / Tarjeta de Circulación", "📋"),
    INSURANCE_POLICY("Póliza de Seguro Automotriz", "🛡️"),
    TECHNICAL_INSPECTION("Inspección Técnica Vehicular (RTV/Dekra)", "🔍"),
    MARCHAMO_TAX("Derecho de Circulación / Impuesto Anual", "📑"),
    INVOICE("Factura / Comprobante de Taller", "🧾"),
    WARRANTY_CERTIFICATE("Certificado de Garantía de Pieza/Reparación", "🎖️"),
    CONTRACT("Contrato de Compraventa / Leasing", "🤝"),
    MANUAL("Manual del Propietario / Especificación Técnica", "📖"),
    OTHER("Otro Documento Vehicular", "📁")
}

data class VehicleDocument(
    val documentId: String,
    val vehicleId: String,
    val ownerPrincipalId: String,
    val type: DocumentType,
    val title: String,
    val issuer: String,
    val issueDateUtc: Long?,
    val expiryDateUtc: Long?,
    val fileUri: String?,
    val fileSha256: String?,
    val isVerified: Boolean = false,
    val uploadedAtUtc: Long = System.currentTimeMillis()
) {
    val isExpired: Boolean
        get() = expiryDateUtc != null && expiryDateUtc < System.currentTimeMillis()

    val daysUntilExpiry: Long?
        get() = expiryDateUtc?.let { (it - System.currentTimeMillis()) / (1000 * 60 * 60 * 24) }
}
