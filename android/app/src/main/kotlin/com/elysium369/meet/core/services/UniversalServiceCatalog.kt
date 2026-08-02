package com.elysium369.meet.core.services

/**
 * Data-driven service ontology shared by physical, digital and hybrid work.
 * A custom entry is always available so the network can accept legitimate
 * needs without pretending that a hard-coded list represents every trade.
 */
data class UniversalServiceDefinition(
    val id: String,
    val domain: String,
    val name: String,
    val modalities: Set<UniversalServiceModality>,
    val icon: String,
    val riskTier: String = "STANDARD",
)

enum class UniversalServiceModality(val label: String) {
    PHYSICAL("A domicilio"),
    DIGITAL("Remoto / digital"),
    HYBRID("Híbrido"),
}

object UniversalServiceCatalog {
    private val allModalities = UniversalServiceModality.entries.toSet()

    val definitions = listOf(
        service("home_cleaning", "Hogar", "Limpieza residencial", "🧹", physical()),
        service("plumbing", "Hogar", "Plomería", "🚰", physical()),
        service("electrical_home", "Hogar", "Electricidad residencial", "⚡", physical(), "ELEVATED"),
        service("painting", "Hogar", "Pintura y acabados", "🎨", physical()),
        service("carpentry", "Hogar", "Carpintería", "🪚", physical()),
        service("gardening", "Hogar", "Jardinería", "🌿", physical()),
        service("appliance_repair", "Hogar", "Reparación de electrodomésticos", "🔧", physical()),
        service("moving", "Logística", "Mudanzas y carga", "📦", physical()),
        service("courier", "Logística", "Mensajería y entregas", "🛵", physical()),
        service("personal_transport", "Movilidad", "Transporte de personas", "🚘", physical(), "ELEVATED"),
        service("roadside", "Movilidad", "Asistencia vial", "🚛", physical(), "ELEVATED"),
        service("mechanical", "Automotriz", "Mecánica y diagnóstico", "🛠️", physical(), "ELEVATED"),
        service("vehicle_detailing", "Automotriz", "Lavado y detailing", "✨", physical()),
        service("pet_care", "Cuidado", "Cuidado de mascotas", "🐾", physical()),
        service("elder_support", "Cuidado", "Acompañamiento de adultos mayores", "🤝", physical(), "ELEVATED"),
        service("childcare", "Cuidado", "Cuidado infantil", "🧸", physical(), "RESTRICTED"),
        service("beauty", "Bienestar", "Belleza y barbería", "✂️", physical()),
        service("fitness", "Bienestar", "Entrenamiento personal", "🏋️", hybrid()),
        service("tutoring", "Educación", "Tutorías y clases", "📚", hybrid()),
        service("translation", "Profesional", "Traducción", "🌐", digital()),
        service("accounting", "Profesional", "Contabilidad", "🧾", digital(), "ELEVATED"),
        service("legal", "Profesional", "Orientación legal", "⚖️", hybrid(), "RESTRICTED"),
        service("graphic_design", "Digital", "Diseño gráfico", "🖌️", digital()),
        service("software", "Digital", "Software y automatización", "💻", digital()),
        service("it_support", "Digital", "Soporte técnico", "🛰️", hybrid()),
        service("photo_video", "Creativo", "Fotografía y video", "📸", hybrid()),
        service("events", "Eventos", "Producción de eventos", "🎪", physical()),
        service("food", "Alimentos", "Comida y catering", "🍽️", physical(), "ELEVATED"),
        service("security", "Seguridad", "Seguridad privada", "🛡️", physical(), "RESTRICTED"),
        UniversalServiceDefinition("custom", "Otros", "Otro servicio", allModalities, "✦"),
    )

    fun search(query: String): List<UniversalServiceDefinition> {
        val normalized = query.trim().lowercase()
        if (normalized.isBlank()) return definitions
        return definitions.filter {
            it.name.lowercase().contains(normalized) ||
                it.domain.lowercase().contains(normalized)
        }
    }

    fun domains(): List<String> = definitions.map(UniversalServiceDefinition::domain).distinct()

    private fun service(
        id: String,
        domain: String,
        name: String,
        icon: String,
        modalities: Set<UniversalServiceModality>,
        riskTier: String = "STANDARD",
    ) = UniversalServiceDefinition(id, domain, name, modalities, icon, riskTier)

    private fun physical() = setOf(UniversalServiceModality.PHYSICAL)
    private fun digital() = setOf(UniversalServiceModality.DIGITAL)
    private fun hybrid() = allModalities
}
