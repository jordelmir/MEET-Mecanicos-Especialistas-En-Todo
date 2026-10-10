package com.elysium369.meet.provider.domain.models

import org.json.JSONArray
import org.json.JSONObject

/**
 * ══════════════════════════════════════════════════════════════════════
 *  P R O V I D E R   S E R V I C E   O F F E R I N G   E N G I N E
 *  ──────────────────────────────────────────────────────────────
 *  Contrato formal y matemático para los oferentes de servicios.
 *  Permite definir con exactitud técnica:
 *  - Tarifas por hora, diagnóstico base y recargo por km en ₡ CRC.
 *  - Políticas de repuestos y materiales (incluidos, cliente provee, o costo+margen).
 *  - Sub-servicios específicos con tiempos estimados y materiales requeridos.
 *  - Equipamiento certificado y condiciones de garantía forense.
 * ══════════════════════════════════════════════════════════════════════
 */

enum class ProviderDomainCategory(val id: String, val title: String, val icon: String) {
    AUTOMOTIVE_MECHANIC("AUTOMOTIVE_MECHANIC", "Mecánica Automotriz & Taller", "🚗"),
    TOW_TRUCK("TOW_TRUCK", "Grúa & Rescate Vial", "🏗️"),
    LOCAL_COMMERCE_GROCERY("LOCAL_COMMERCE_GROCERY", "Pulpería & Minisúper", "🏪"),
    SODA_RESTAURANT("SODA_RESTAURANT", "Soda & Restaurante Típico", "🍳"),
    PLUMBING_WATER("PLUMBING_WATER", "Plomería & Tuberías", "🚰"),
    ELECTRICAL_RESIDENTIAL("ELECTRICAL_RESIDENTIAL", "Electricidad & Redes", "⚡"),
    LOCKSMITH_SECURITY("LOCKSMITH_SECURITY", "Cerrajería & Cerraduras", "🔑"),
    HOME_MAINTENANCE("HOME_MAINTENANCE", "Mantenimiento del Hogar", "🛠️"),
    HARDWARE_STORE("HARDWARE_STORE", "Ferretería & Materiales", "🔩"),
    AUTO_DETAILING("AUTO_DETAILING", "Lavado & Detailing", "✨"),
    BATTERY_JUMPSTART("BATTERY_JUMPSTART", "Batería & Arranque", "🔋"),
    PARTS_STORE("PARTS_STORE", "Repuestos & Autopartes", "⚙️"),
    COURIER_DELIVERY("COURIER_DELIVERY", "Mensajería & Entregas", "🛵"),
    MOVING_FREIGHT("MOVING_FREIGHT", "Mudanzas & Fletes", "📦"),
    PROFESSIONAL_SERVICES("PROFESSIONAL_SERVICES", "Servicios Profesionales", "💼"),
    EDUCATION_TUTORING("EDUCATION_TUTORING", "Tutorías & Clases", "📚");

    companion object {
        fun fromId(id: String): ProviderDomainCategory {
            val clean = id.trim().uppercase()
            return when {
                clean in listOf("AUTOMOTIVE_MECHANIC", "AUTO_MECHANICAL", "MECHANICAL", "MECHANIC", "TALLER") -> AUTOMOTIVE_MECHANIC
                clean in listOf("TOW_TRUCK", "AUTO_TOW", "TOW", "ROADSIDE", "GRUA") -> TOW_TRUCK
                clean in listOf("LOCAL_COMMERCE_GROCERY", "PULPERIA", "PULPERIA_GROCERIES", "MINISUPER") -> LOCAL_COMMERCE_GROCERY
                clean in listOf("SODA_RESTAURANT", "SODA", "SODA_TRADITIONAL_FOOD", "RESTAURANT") -> SODA_RESTAURANT
                clean in listOf("PLUMBING_WATER", "PLUMBING", "FONTANERIA", "PLOMERIA") -> PLUMBING_WATER
                clean in listOf("ELECTRICAL_RESIDENTIAL", "ELECTRICAL", "ELECTRICAL_HOME", "ELECTRICIDAD") -> ELECTRICAL_RESIDENTIAL
                clean in listOf("LOCKSMITH_SECURITY", "LOCKSMITH", "HARDWARE_LOCKSMITH", "CERRAJERIA") -> LOCKSMITH_SECURITY
                clean in listOf("HARDWARE_STORE", "HARDWARE", "HARDWARE_MATERIALS", "FERRETERIA") -> HARDWARE_STORE
                clean in listOf("AUTO_DETAILING", "DETAILING", "VEHICLE_DETAILING", "LAVADO") -> AUTO_DETAILING
                clean in listOf("BATTERY_JUMPSTART", "BATTERY", "ARRANQUE") -> BATTERY_JUMPSTART
                clean in listOf("PARTS_STORE", "PARTS", "AUTO_PARTS", "REPUESTOS") -> PARTS_STORE
                clean in listOf("COURIER_DELIVERY", "COURIER", "MENSAJERIA", "EXPRESS") -> COURIER_DELIVERY
                clean in listOf("MOVING_FREIGHT", "MOVING", "MUDANZAS", "FLETE") -> MOVING_FREIGHT
                clean in listOf("PROFESSIONAL_SERVICES", "PROFESSIONAL", "ACCOUNTING", "LEGAL", "SOFTWARE") -> PROFESSIONAL_SERVICES
                clean in listOf("EDUCATION_TUTORING", "EDUCATION", "TUTORING", "CLASES") -> EDUCATION_TUTORING
                clean in listOf("HOME_MAINTENANCE", "HOME", "HOME_CLEANING", "LIMPIEZA", "PINTURA") -> HOME_MAINTENANCE
                else -> entries.firstOrNull { it.id.equals(clean, ignoreCase = true) } ?: AUTOMOTIVE_MECHANIC
            }
        }
    }
}

enum class MaterialSupplyPolicy(val id: String, val description: String) {
    MATERIALS_INCLUDED("MATERIALS_INCLUDED", "Mano de obra y materiales incluidos en la cotización"),
    CLIENT_SUPPLIED("CLIENT_SUPPLIED", "Solo mano de obra; el cliente adquiere los repuestos o materiales"),
    AT_COST_WITH_MARGIN("AT_COST_WITH_MARGIN", "Materiales facturados al costo con margen transparente (+10%)");

    companion object {
        fun fromId(id: String): MaterialSupplyPolicy {
            return entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: MATERIALS_INCLUDED
        }
    }
}

data class OfferedSubService(
    val id: String,
    val name: String,
    val description: String,
    val estimatedHours: Double,
    val suggestedPriceCrc: Long,
    val typicalMaterials: List<String>,
    val isEnabled: Boolean = true,
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("name", name)
            put("description", description)
            put("estimatedHours", estimatedHours)
            put("suggestedPriceCrc", suggestedPriceCrc)
            put("typicalMaterials", JSONArray(typicalMaterials))
            put("isEnabled", isEnabled)
        }
    }

    companion object {
        fun fromJson(json: JSONObject): OfferedSubService {
            val matsArray = json.optJSONArray("typicalMaterials")
            val mats = mutableListOf<String>()
            if (matsArray != null) {
                for (i in 0 until matsArray.length()) {
                    mats.add(matsArray.optString(i))
                }
            }
            return OfferedSubService(
                id = json.optString("id", java.util.UUID.randomUUID().toString()),
                name = json.optString("name", "Servicio"),
                description = json.optString("description", ""),
                estimatedHours = json.optDouble("estimatedHours", 1.0),
                suggestedPriceCrc = json.optLong("suggestedPriceCrc", 15000L),
                typicalMaterials = mats,
                isEnabled = json.optBoolean("isEnabled", true)
            )
        }
    }
}

/**
 * Registro inmutable de transacciones en la Billetera del Proveedor de Servicios.
 */
data class ProviderWalletTransaction(
    val id: String,
    val entryType: String, // "WELCOME_BONUS", "CONSTITUTIONAL_FEE_5_PERCENT", "TOPUP_SINPE_CONFIRMED"
    val amountCrc: Long,
    val direction: String, // "CREDIT" or "DEBIT"
    val description: String,
    val timestamp: Long = System.currentTimeMillis(),
    val referenceId: String = ""
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("entryType", entryType)
            put("amountCrc", amountCrc)
            put("direction", direction)
            put("description", description)
            put("timestamp", timestamp)
            put("referenceId", referenceId)
        }
    }

    companion object {
        fun fromJson(json: JSONObject): ProviderWalletTransaction {
            return ProviderWalletTransaction(
                id = json.optString("id", java.util.UUID.randomUUID().toString()),
                entryType = json.optString("entryType", "WELCOME_BONUS"),
                amountCrc = json.optLong("amountCrc", 5000L),
                direction = json.optString("direction", "CREDIT"),
                description = json.optString("description", "Bono Constitucional MEET"),
                timestamp = json.optLong("timestamp", System.currentTimeMillis()),
                referenceId = json.optString("referenceId", "")
            )
        }
    }
}

data class ProviderServiceProfileData(
    val domainCategory: ProviderDomainCategory = ProviderDomainCategory.AUTOMOTIVE_MECHANIC,
    val hourlyLaborRateCrc: Long = 15000L,
    val baseDiagnosticFeeCrc: Long = 18000L,
    val ratePerKmCrc: Long = 1200L,
    val materialPolicy: MaterialSupplyPolicy = MaterialSupplyPolicy.MATERIALS_INCLUDED,
    val materialMarkupPercent: Double = 10.0,
    val warrantyDays: Int = 90,
    val warrantyKm: Int = 5000,
    val emergencySurchargePercent: Double = 25.0,
    val operatingRadiusKm: Double = 25.0,
    val certifiedEquipment: List<String> = emptyList(),
    val subServices: List<OfferedSubService> = emptyList(),
    // ── Billetera Constitucional & Saldo Operativo (5% Comisión MEET) ──
    val walletBalanceCrc: Long = 5000L, // Bono de bienvenida inicial de ₡5,000 CRC otorgado a todos
    val platformCommissionBps: Long = 500L, // 5.0% Constitucional (500 bps)
    val walletTransactions: List<ProviderWalletTransaction> = listOf(
        ProviderWalletTransaction(
            id = "starter-bonus",
            entryType = "WELCOME_BONUS",
            amountCrc = 5000L,
            direction = "CREDIT",
            description = "Bono Constitucional de Bienvenida MEET (₡5,000 CRC)",
            timestamp = System.currentTimeMillis(),
            referenceId = "START-5000"
        )
    ),
) {
    /**
     * Calcula la comisión constitucional exacta del 5% (500 bps) sobre el monto bruto.
     * Mínimo 1 CRC.
     */
    fun calculateFee(grossAmountCrc: Long): Long {
        if (grossAmountCrc <= 0) return 0L
        val fee = (grossAmountCrc * platformCommissionBps) / 10000L
        return if (fee <= 0) 1L else fee
    }

    /**
     * Verifica si el proveedor cuenta con saldo suficiente para cubrir la comisión del 5% del trabajo.
     */
    fun canAcceptJob(grossAmountCrc: Long): Boolean {
        val requiredFee = calculateFee(grossAmountCrc)
        return walletBalanceCrc >= requiredFee
    }

    fun hasSufficientBalance(requiredFeeCrc: Long): Boolean {
        return walletBalanceCrc >= requiredFeeCrc
    }

    /**
     * Deduce la comisión constitucional del 5% del saldo y registra la transacción en el ledger inmutable.
     * Retorna el nuevo perfil con el saldo debitado o null si los fondos son insuficientes.
     */
    fun withDeductedFee(
        grossAmountCrc: Long,
        serviceId: String,
        categoryName: String = domainCategory.name
    ): Pair<ProviderServiceProfileData, Long>? {
        val fee = calculateFee(grossAmountCrc)
        if (walletBalanceCrc < fee) return null

        val tx = ProviderWalletTransaction(
            id = java.util.UUID.randomUUID().toString(),
            entryType = "CONSTITUTIONAL_FEE_5_PERCENT",
            amountCrc = fee,
            direction = "DEBIT",
            description = "Comisión 5% servicio #$serviceId ($categoryName)",
            timestamp = System.currentTimeMillis(),
            referenceId = serviceId
        )
        val updatedProfile = copy(
            walletBalanceCrc = walletBalanceCrc - fee,
            walletTransactions = listOf(tx) + walletTransactions
        )
        return Pair(updatedProfile, fee)
    }

    /**
     * Recarga saldo a la billetera (SINPE Móvil u otro canal de pago verificado).
     */
    fun withTopUp(amountCrc: Long, reference: String): ProviderServiceProfileData {
        val tx = ProviderWalletTransaction(
            id = java.util.UUID.randomUUID().toString(),
            entryType = "TOPUP_SINPE_CONFIRMED",
            amountCrc = amountCrc,
            direction = "CREDIT",
            description = "Recarga de saldo SINPE Móvil ($reference)",
            timestamp = System.currentTimeMillis(),
            referenceId = reference
        )
        return copy(
            walletBalanceCrc = walletBalanceCrc + amountCrc,
            walletTransactions = listOf(tx) + walletTransactions
        )
    }

    fun toJsonString(): String {
        val root = JSONObject().apply {
            put("domainCategory", domainCategory.id)
            put("hourlyLaborRateCrc", hourlyLaborRateCrc)
            put("baseDiagnosticFeeCrc", baseDiagnosticFeeCrc)
            put("ratePerKmCrc", ratePerKmCrc)
            put("materialPolicy", materialPolicy.id)
            put("materialMarkupPercent", materialMarkupPercent)
            put("warrantyDays", warrantyDays)
            put("warrantyKm", warrantyKm)
            put("emergencySurchargePercent", emergencySurchargePercent)
            put("operatingRadiusKm", operatingRadiusKm)
            put("certifiedEquipment", JSONArray(certifiedEquipment))

            val servicesArray = JSONArray()
            subServices.forEach { servicesArray.put(it.toJson()) }
            put("subServices", servicesArray)

            put("walletBalanceCrc", walletBalanceCrc)
            put("platformCommissionBps", platformCommissionBps)

            val txArray = JSONArray()
            walletTransactions.forEach { txArray.put(it.toJson()) }
            put("walletTransactions", txArray)
        }
        return root.toString()
    }

    companion object {
        fun fromJsonString(raw: String): ProviderServiceProfileData {
            if (raw.isBlank() || !raw.trim().startsWith("{")) {
                return defaultTemplateForCategory(ProviderDomainCategory.AUTOMOTIVE_MECHANIC)
            }
            return try {
                val json = JSONObject(raw)
                val domain = ProviderDomainCategory.fromId(json.optString("domainCategory", "AUTOMOTIVE_MECHANIC"))
                val equipArray = json.optJSONArray("certifiedEquipment")
                val equip = mutableListOf<String>()
                if (equipArray != null) {
                    for (i in 0 until equipArray.length()) {
                        equip.add(equipArray.optString(i))
                    }
                }
                val servicesArray = json.optJSONArray("subServices")
                val services = mutableListOf<OfferedSubService>()
                if (servicesArray != null) {
                    for (i in 0 until servicesArray.length()) {
                        val itemObj = servicesArray.optJSONObject(i)
                        if (itemObj != null) {
                            services.add(OfferedSubService.fromJson(itemObj))
                        }
                    }
                }

                val balance = json.optLong("walletBalanceCrc", 5000L)
                val commBps = json.optLong("platformCommissionBps", 500L)
                val txArray = json.optJSONArray("walletTransactions")
                val txList = mutableListOf<ProviderWalletTransaction>()
                if (txArray != null) {
                    for (i in 0 until txArray.length()) {
                        val tObj = txArray.optJSONObject(i)
                        if (tObj != null) {
                            txList.add(ProviderWalletTransaction.fromJson(tObj))
                        }
                    }
                }
                if (txList.isEmpty()) {
                    txList.add(
                        ProviderWalletTransaction(
                            id = "starter-bonus",
                            entryType = "WELCOME_BONUS",
                            amountCrc = 5000L,
                            direction = "CREDIT",
                            description = "Bono Constitucional de Bienvenida MEET (₡5,000 CRC)",
                            timestamp = System.currentTimeMillis(),
                            referenceId = "START-5000"
                        )
                    )
                }

                ProviderServiceProfileData(
                    domainCategory = domain,
                    hourlyLaborRateCrc = json.optLong("hourlyLaborRateCrc", 15000L),
                    baseDiagnosticFeeCrc = json.optLong("baseDiagnosticFeeCrc", 18000L),
                    ratePerKmCrc = json.optLong("ratePerKmCrc", 1200L),
                    materialPolicy = MaterialSupplyPolicy.fromId(json.optString("materialPolicy", "MATERIALS_INCLUDED")),
                    materialMarkupPercent = json.optDouble("materialMarkupPercent", 10.0),
                    warrantyDays = json.optInt("warrantyDays", 90),
                    warrantyKm = json.optInt("warrantyKm", 5000),
                    emergencySurchargePercent = json.optDouble("emergencySurchargePercent", 25.0),
                    operatingRadiusKm = json.optDouble("operatingRadiusKm", 25.0),
                    certifiedEquipment = equip,
                    subServices = if (services.isNotEmpty()) services else defaultTemplateForCategory(domain).subServices,
                    walletBalanceCrc = balance,
                    platformCommissionBps = commBps,
                    walletTransactions = txList
                )
            } catch (_: Exception) {
                defaultTemplateForCategory(ProviderDomainCategory.AUTOMOTIVE_MECHANIC)
            }
        }

        /**
         * Plantillas con coherencia técnica y matemática real por categoría para Costa Rica.
         */
        fun defaultTemplateForCategory(category: ProviderDomainCategory): ProviderServiceProfileData {
            return when (category) {
                ProviderDomainCategory.AUTOMOTIVE_MECHANIC -> ProviderServiceProfileData(
                    domainCategory = category,
                    hourlyLaborRateCrc = 15000L,
                    baseDiagnosticFeeCrc = 20000L,
                    ratePerKmCrc = 1200L,
                    materialPolicy = MaterialSupplyPolicy.MATERIALS_INCLUDED,
                    warrantyDays = 90,
                    warrantyKm = 5000,
                    certifiedEquipment = listOf("Escáner OBD-II Bidireccional", "Compresímetro Digital", "Prensa Hidráulica 20T", "Torquímetro Calibrado"),
                    subServices = listOf(
                        OfferedSubService("brakes", "Frenos: Pastillas, Discos y Purga ABS", "Revisión integral de pastillas, rectificación de discos y purga con líquido DOT4.", 1.5, 25000L, listOf("Pastillas cerámicas OEM", "Líquido frenos DOT4", "Limpiador frenos")),
                        OfferedSubService("tuneup", "Afinamiento de Motor e Inyección", "Limpieza de inyectores por ultrasonido, cambio de bujías y filtros de aire/gasolina.", 2.0, 35000L, listOf("Bujías de iridio/platino", "Filtro de aire", "Filtro combustible", "Limpia-inyectores")),
                        OfferedSubService("suspension", "Suspensión, Amortiguadores y Rótulas", "Reemplazo de amortiguadores, bujes de tijeta y terminales de dirección.", 3.0, 45000L, listOf("Amortiguadores de gas", "Guardapolvos", "Bujes poliuretano")),
                        OfferedSubService("scan_dekra", "Diagnóstico OBD-II y Pre-ITV Dekra", "Escaneo completo de módulos ECU/TCM/ABS y prueba de analizador de gases según Decreto 37372-MOPT.", 1.0, 20000L, listOf("Reporte digital certificado con hash")),
                        OfferedSubService("clutch", "Embrague / Clutch Completo", "Bajada de caja, rectificación de volante, reemplazo de disco, plato y collarín.", 5.0, 75000L, listOf("Kit de embrague (disco/plato/collarín)", "Aceite de transmisión 75W-90")),
                        OfferedSubService("ac_recharge", "Aire Acondicionado Automotriz", "Carga de refrigerante R134a, aceite sintético PAG y detección de fugas UV.", 1.0, 25000L, listOf("Gas refrigerante R134a", "Aceite PAG-46", "Tinte UV detector"))
                    )
                )

                ProviderDomainCategory.TOW_TRUCK -> ProviderServiceProfileData(
                    domainCategory = category,
                    hourlyLaborRateCrc = 0L,
                    baseDiagnosticFeeCrc = 25000L, // Tarifa enganche
                    ratePerKmCrc = 1500L,          // Tarifa por km rodado
                    materialPolicy = MaterialSupplyPolicy.MATERIALS_INCLUDED,
                    warrantyDays = 7,
                    warrantyKm = 0,
                    certifiedEquipment = listOf("Plataforma Hidráulica Basculante", "Winche Eléctrico 12,000 lbs", "Dollys de Arrastre para 4x4", "Cinchas Reglamentarias MOPT"),
                    subServices = listOf(
                        OfferedSubService("tow_platform", "Remolque en Plataforma Hidráulica", "Traslado seguro de vehículo averiado o colisionado sin rodar ruedas.", 1.0, 25000L, listOf("Seguro de carga incluido", "Cinchas de rueda")),
                        OfferedSubService("tow_winch", "Rescate con Winche Fuera de Vía", "Extracción de vehículo atrapado en zanja, barro o fuera de calzada.", 1.5, 35000L, listOf("Poleas de reenvío", "Cable de acero / plasma")),
                        OfferedSubService("tow_jumpstart", "Paso de Corriente y Auxilio 12V", "Arranque de emergencia con booster profesional y revisión de alternador.", 0.5, 12000L, listOf("Booster de litio 2000A", "Probador de batería"))
                    )
                )

                ProviderDomainCategory.LOCAL_COMMERCE_GROCERY -> ProviderServiceProfileData(
                    domainCategory = category,
                    hourlyLaborRateCrc = 0L,
                    baseDiagnosticFeeCrc = 1500L,  // Tarifa fija de despacho
                    ratePerKmCrc = 400L,
                    materialPolicy = MaterialSupplyPolicy.MATERIALS_INCLUDED,
                    warrantyDays = 1,
                    warrantyKm = 0,
                    certifiedEquipment = listOf("Refrigeración Comercial", "Balanza Electrónica Certificada MEIC", "Bolsas Biodegradables"),
                    subServices = listOf(
                        OfferedSubService("pulperia_basket", "Canasta Básica y Abarrotes", "Arroz, frijoles negros/rojos, café costarricense, azúcar, sal y aceite.", 0.2, 5000L, listOf("Bolsa biodegradable")),
                        OfferedSubService("pulperia_dairy", "Lácteos, Embutidos y Huevos", "Leche fresca, queso tierno de Turrialba, natilla y huevos de granja.", 0.2, 4500L, listOf("Empaque térmico")),
                        OfferedSubService("pulperia_drinks", "Bebidas Frías, Snacks y Panadería", "Gaseosas, jugos naturales, botanas, pan artesanal y repostería.", 0.2, 3500L, listOf("Bolsa de papel")),
                        OfferedSubService("pulperia_hygiene", "Aseo del Hogar y Cuidado Personal", "Detergentes, papel higiénico, jabón de baño y farmacia básica.", 0.2, 4000L, listOf("Bolsa sellada"))
                    )
                )

                ProviderDomainCategory.SODA_RESTAURANT -> ProviderServiceProfileData(
                    domainCategory = category,
                    hourlyLaborRateCrc = 0L,
                    baseDiagnosticFeeCrc = 1800L,  // Tarifa de despacho y empaque
                    ratePerKmCrc = 450L,
                    materialPolicy = MaterialSupplyPolicy.MATERIALS_INCLUDED,
                    warrantyDays = 1,
                    warrantyKm = 0,
                    certifiedEquipment = listOf("Cocina Industrial en Acero Inoxidable", "Termo-selladora de Alimentos", "Carné de Manipulación de Alimentos al Día"),
                    subServices = listOf(
                        OfferedSubService("soda_casado", "Casado Tradicional Costarricense", "Arroz, frijoles, plátano maduro, ensalada, picadillo y carne a elegir (bistec/chuleta/pollo/pescado).", 0.4, 4200L, listOf("Contenedor térmico compostable", "Cubiertos ecológicos")),
                        OfferedSubService("soda_pinto", "Desayuno Típico Gallo Pinto", "Gallo pinto tradicional con huevos al gusto, natilla, queso frito, maduro y pan/tortillas.", 0.3, 3500L, listOf("Envase hermético")),
                        OfferedSubService("soda_fast_food", "Comida Rápida Criolla", "Hamburguesa casera con papas fritas, chalupas o tacos ticos con repollo y salsa lizano.", 0.3, 3800L, listOf("Caja de cartón kraft")),
                        OfferedSubService("soda_natural_drinks", "Batidos y Refrescos Naturales", "Horchata, tamarindo, maracuyá, cas, o guanábana en agua o leche.", 0.1, 1500L, listOf("Vaso sellado antiderrame"))
                    )
                )

                ProviderDomainCategory.PLUMBING_WATER -> ProviderServiceProfileData(
                    domainCategory = category,
                    hourlyLaborRateCrc = 12000L,
                    baseDiagnosticFeeCrc = 15000L, // Visita técnica
                    ratePerKmCrc = 800L,
                    materialPolicy = MaterialSupplyPolicy.AT_COST_WITH_MARGIN,
                    warrantyDays = 60,
                    certifiedEquipment = listOf("Termofusora PPR para Agua Caliente", "Sonda Destapadora Eléctrica K-400", "Manómetro de Presión Hidráulica"),
                    subServices = listOf(
                        OfferedSubService("plumbing_leak", "Detección y Reparación de Fugas", "Localización de fuga oculta en tubería PVC/PPR y cambio de tramo dañado.", 2.0, 28000L, listOf("Tubos PVC SDR-26", "Uniones y codos", "Pegamento Tangit")),
                        OfferedSubService("plumbing_faucet", "Instalación de Grifería y Lavatorios", "Colocación de llaves monomando, sifones de desagüe y mangueras flexibles.", 1.0, 18000L, listOf("Teflón alta densidad", "Mangueras trenzadas 1/2")),
                        OfferedSubService("plumbing_clog", "Desobstrucción Mecánica de Drenajes", "Pase de sonda mecánica motorizada en fregaderos, inodoros o cañerías principales.", 1.5, 25000L, listOf("Desengrasante industrial enzimático"))
                    )
                )

                ProviderDomainCategory.ELECTRICAL_RESIDENTIAL -> ProviderServiceProfileData(
                    domainCategory = category,
                    hourlyLaborRateCrc = 14000L,
                    baseDiagnosticFeeCrc = 15000L,
                    ratePerKmCrc = 800L,
                    materialPolicy = MaterialSupplyPolicy.CLIENT_SUPPLIED,
                    warrantyDays = 90,
                    certifiedEquipment = listOf("Multímetro Digital Fluke True-RMS", "Pinza Amperimétrica", "Detector de Voltaje Sin Contacto"),
                    subServices = listOf(
                        OfferedSubService("elec_short", "Atención de Cortocircuito y Avería", "Diagnóstico de fuga a tierra, balanceo de fases y restablecimiento seguro.", 1.5, 25000L, listOf("Cinta aislante 3M Super 33+", "Conectores roscados")),
                        OfferedSubService("elec_breaker", "Cambio de Breakers y Centro de Carga", "Sustitución de breaker disparado por sobrecalentamiento y reapriete de bornes.", 1.0, 18000L, listOf("Breaker enchufable Square D / General Electric")),
                        OfferedSubService("elec_shower", "Instalación de Ducha Eléctrica 220V/110V", "Línea independiente desde centro de carga con cable calibre 10 AWG y tierra física.", 2.0, 30000L, listOf("Cable THHN #10", "Ducha eléctrica", "Breaker bipolar 40A"))
                    )
                )

                ProviderDomainCategory.LOCKSMITH_SECURITY -> ProviderServiceProfileData(
                    domainCategory = category,
                    hourlyLaborRateCrc = 12000L,
                    baseDiagnosticFeeCrc = 15000L,
                    ratePerKmCrc = 800L,
                    materialPolicy = MaterialSupplyPolicy.MATERIALS_INCLUDED,
                    warrantyDays = 90,
                    certifiedEquipment = listOf("Ganzúas Profesionales Lishi", "Máquina Duplicadora Electrónica", "Fresadora de Cerraduras"),
                    subServices = listOf(
                        OfferedSubService("lock_open", "Apertura Urgente de Puerta / Vehículo", "Apertura técnica no destructiva por ganzuado o decodificación.", 0.5, 20000L, listOf("Lubricante grafito para cilindros")),
                        OfferedSubService("lock_replace", "Suministro e Instalación de Cerradura", "Instalación de cerradura de alta seguridad con cerrojo de doble paso.", 1.0, 32000L, listOf("Cerradura Yale / Schlage con 3 llaves")),
                        OfferedSubService("lock_rekey", "Cambio de Combinación (Amaestramiento)", "Modificación interna de pernos del cilindro para nuevas llaves.", 1.0, 22000L, listOf("Pernos de latón calibrados", "Llaves nuevas"))
                    )
                )

                ProviderDomainCategory.HOME_MAINTENANCE -> ProviderServiceProfileData(
                    domainCategory = category,
                    hourlyLaborRateCrc = 10000L,
                    baseDiagnosticFeeCrc = 12000L,
                    ratePerKmCrc = 600L,
                    materialPolicy = MaterialSupplyPolicy.CLIENT_SUPPLIED,
                    warrantyDays = 30,
                    certifiedEquipment = listOf("Escalera de Extensión Fibra de Vidrio", "Pistola de Pintura Airless", "Juego de Herramientas Eléctricas Dewalt"),
                    subServices = listOf(
                        OfferedSubService("home_paint", "Pintura Interior / Exterior", "Preparación de superficie, lijado, sellador y dos manos de pintura.", 4.0, 45000L, listOf("Plásticos protectores", "Cinta masking tape")),
                        OfferedSubService("home_drywall", "Reparación de Gypsum y Cielorraso", "Parcheo de humedad, empastado y lijado liso.", 2.0, 25000L, listOf("Pasta gypsum", "Cinta de malla", "Lijas finas"))
                    )
                )

                ProviderDomainCategory.HARDWARE_STORE -> ProviderServiceProfileData(
                    domainCategory = category,
                    hourlyLaborRateCrc = 0L,
                    baseDiagnosticFeeCrc = 2000L, // Despacho de ferretería
                    ratePerKmCrc = 500L,
                    materialPolicy = MaterialSupplyPolicy.MATERIALS_INCLUDED,
                    warrantyDays = 30,
                    certifiedEquipment = listOf("Montacargas de Patio", "Cortadora de Metales / Tubería", "Facturación Electrónica Hacienda D-104"),
                    subServices = listOf(
                        OfferedSubService("hard_pipe", "Tubos y Accesorios PVC / CPVC", "Suministro de tubería de 1/2\", 3/4\", 2\", codos, tees, uniones y pegamento.", 0.5, 12000L, listOf("Tubo PVC SDR-26", "Pegamento Tangit", "Teflón")),
                        OfferedSubService("hard_elec", "Conductores y Dispositivos Eléctricos", "Cableado THHN, interruptores, tomacorrientes y cajas termomagnéticas.", 0.5, 15000L, listOf("Cable THHN #12", "Tomacorriente Leviton", "Breaker")),
                        OfferedSubService("hard_cement", "Materiales Pesados y Cemento", "Sacos de cemento Holcim/Cemex, arena calibrada, piedra y varillas de acero.", 1.0, 25000L, listOf("Cemento Tipo GU", "Varilla corrugada #3", "Arena"))
                    )
                )

                ProviderDomainCategory.AUTO_DETAILING -> ProviderServiceProfileData(
                    domainCategory = category,
                    hourlyLaborRateCrc = 12000L,
                    baseDiagnosticFeeCrc = 15000L,
                    ratePerKmCrc = 700L,
                    materialPolicy = MaterialSupplyPolicy.MATERIALS_INCLUDED,
                    warrantyDays = 14,
                    certifiedEquipment = listOf("Pulidora Roto-Orbital Rupes", "Hidrolavadora de Alta Presión Kärcher", "Máquina de Extracción de Tapicería", "Generador de Ozono"),
                    subServices = listOf(
                        OfferedSubService("detail_wash", "Lavado Premium y Encerado Cerámico", "Descontaminación con barra de arcilla, lavado a mano con espuma pH neutro y cera.", 2.0, 22000L, listOf("Shampoo pH neutro", "Sellador cerámico", "Toallas microfibra")),
                        OfferedSubService("detail_seats", "Lavado Profundo de Tapicería e Interior", "Inyección y succión de sillones, alfombras, techo y desinfección con ozono.", 3.0, 35000L, listOf("Desengrasante textil", "Acondicionador de cuero", "Neutralizador olores")),
                        OfferedSubService("detail_polish", "Pulido y Corrección de Pintura 2 Pasos", "Eliminación de microrrayas (swirls), abrillantado y protección de capa transparente.", 4.5, 55000L, listOf("Compuesto pulidor fino", "Pads de microfibra/espuma", "Sellador"))
                    )
                )

                ProviderDomainCategory.BATTERY_JUMPSTART -> ProviderServiceProfileData(
                    domainCategory = category,
                    hourlyLaborRateCrc = 8000L,
                    baseDiagnosticFeeCrc = 12000L,
                    ratePerKmCrc = 800L,
                    materialPolicy = MaterialSupplyPolicy.MATERIALS_INCLUDED,
                    warrantyDays = 365,
                    warrantyKm = 20000,
                    certifiedEquipment = listOf("Analizador de Baterías Digital Midtronics", "Booster de Litio Profesional NOCO 3000A", "Probador de Carga de Alternador"),
                    subServices = listOf(
                        OfferedSubService("bat_jump", "Paso de Corriente de Emergencia", "Auxilio en ruta, conexión segura con supresor de picos y prueba de alternador.", 0.4, 12000L, listOf("Prueba de diagnóstico impresa")),
                        OfferedSubService("bat_install", "Suministro e Instalación de Batería Nueva", "Instalación a domicilio de batería sellada libre de mantenimiento con garantía.", 0.8, 48000L, listOf("Batería sellada 12V (Grupo 24/35/48)", "Terminales de bronce", "Protector antisulfato"))
                    )
                )

                ProviderDomainCategory.PARTS_STORE -> ProviderServiceProfileData(
                    domainCategory = category,
                    hourlyLaborRateCrc = 0L,
                    baseDiagnosticFeeCrc = 2500L,
                    ratePerKmCrc = 600L,
                    materialPolicy = MaterialSupplyPolicy.MATERIALS_INCLUDED,
                    warrantyDays = 90,
                    certifiedEquipment = listOf("Catálogo Electrónico de Partes EPC", "Escáner de Código de Barras / VIN", "Almacén Climatizado"),
                    subServices = listOf(
                        OfferedSubService("parts_brakes", "Repuestos de Frenos y Fricción", "Pastillas cerámicas, discos ventilados y zapatas garantizados por chasis/VIN.", 0.5, 28000L, listOf("Pastillas OEM", "Discos")),
                        OfferedSubService("parts_filters", "Kit de Filtros y Mantenimiento", "Filtro de aire, aceite, combustible y cabina para modelo específico.", 0.3, 16000L, listOf("Kit de filtros")),
                        OfferedSubService("parts_suspension", "Componentes de Suspensión y Dirección", "Amortiguadores, rótulas, terminales y bujes con especificación OEM.", 0.5, 45000L, listOf("Amortiguadores par delantero"))
                    )
                )

                ProviderDomainCategory.COURIER_DELIVERY -> ProviderServiceProfileData(
                    domainCategory = category,
                    hourlyLaborRateCrc = 0L,
                    baseDiagnosticFeeCrc = 2000L,
                    ratePerKmCrc = 500L,
                    materialPolicy = MaterialSupplyPolicy.MATERIALS_INCLUDED,
                    warrantyDays = 1,
                    certifiedEquipment = listOf("Cajón Térmico / Maletín Impermeable", "Soporte de Navegación GPS", "Cámara para Evidencia de Entrega"),
                    subServices = listOf(
                        OfferedSubService("courier_docs", "Envío Express de Documentos y Facturas", "Entrega punto a punto con firma digital y comprobante fotográfico.", 0.5, 3500L, listOf("Sobre protector sellado")),
                        OfferedSubService("courier_parcel", "Entrega de Paquetería y Compras", "Transporte seguro de encomiendas de hasta 15 kg.", 0.6, 4500L, listOf("Embalaje y cinchas de sujeción"))
                    )
                )

                ProviderDomainCategory.MOVING_FREIGHT -> ProviderServiceProfileData(
                    domainCategory = category,
                    hourlyLaborRateCrc = 8000L,
                    baseDiagnosticFeeCrc = 25000L,
                    ratePerKmCrc = 1200L,
                    materialPolicy = MaterialSupplyPolicy.MATERIALS_INCLUDED,
                    warrantyDays = 7,
                    certifiedEquipment = listOf("Camión de Carga con Rampa Hidráulica", "Carretilla de Carga 300 kg", "Mantas de Mudanza y Plástico Stretch"),
                    subServices = listOf(
                        OfferedSubService("move_freight", "Flete Local y Transporte de Muebles", "Traslado de enseres, electrodomésticos o mercadería con estiba segura.", 2.0, 35000L, listOf("Mantas protectoras", "Cinchas de trinquete")),
                        OfferedSubService("move_full", "Mudanza Integral con Carga y Descarga", "Equipo de estibadores para subir, acomodar y proteger pertenencias.", 4.0, 75000L, listOf("Plástico burbuja", "Cajas reforzadas"))
                    )
                )

                ProviderDomainCategory.PROFESSIONAL_SERVICES -> ProviderServiceProfileData(
                    domainCategory = category,
                    hourlyLaborRateCrc = 20000L,
                    baseDiagnosticFeeCrc = 25000L,
                    ratePerKmCrc = 0L,
                    materialPolicy = MaterialSupplyPolicy.CLIENT_SUPPLIED,
                    warrantyDays = 90,
                    certifiedEquipment = listOf("Firma Digital Certificada BCCR", "Software Contable / Tributario Homologado", "Conexión Encriptada VPN"),
                    subServices = listOf(
                        OfferedSubService("prof_tax", "Declaración de Impuestos y Facturación", "Preparación de declaraciones IVA (D-104), renta (D-101) y conciliación fiscal.", 1.5, 25000L, listOf("Comprobante con firma digital")),
                        OfferedSubService("prof_legal", "Elaboración de Contratos y Asesoría Legal", "Redacción de contratos de alquiler, compraventa vehicular o servicios con validez jurídica.", 2.0, 35000L, listOf("Instrumento legal firmado"))
                    )
                )

                ProviderDomainCategory.EDUCATION_TUTORING -> ProviderServiceProfileData(
                    domainCategory = category,
                    hourlyLaborRateCrc = 10000L,
                    baseDiagnosticFeeCrc = 10000L,
                    ratePerKmCrc = 500L,
                    materialPolicy = MaterialSupplyPolicy.MATERIALS_INCLUDED,
                    warrantyDays = 30,
                    certifiedEquipment = listOf("Pizarra Digital Interactiva", "Material Didáctico Homologado MEP", "Plataforma de Videoconferencia HD"),
                    subServices = listOf(
                        OfferedSubService("tutor_stem", "Tutoría de Matemáticas y Ciencias", "Refuerzo escolar y colegial (7° a 11° año) enfocado en resolución de problemas.", 1.0, 10000L, listOf("Guías de práctica y exámenes")),
                        OfferedSubService("tutor_lang", "Clases de Idiomas (Inglés Conversacional)", "Práctica oral, gramática aplicada y preparación para certificaciones.", 1.0, 12000L, listOf("Material auditivo y fichas"))
                    )
                )
            }
        }
    }
}
