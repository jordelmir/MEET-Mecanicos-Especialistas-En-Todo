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
    HOME_MAINTENANCE("HOME_MAINTENANCE", "Mantenimiento del Hogar", "🛠️");

    companion object {
        fun fromId(id: String): ProviderDomainCategory {
            return entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: AUTOMOTIVE_MECHANIC
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
) {
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
                    subServices = if (services.isNotEmpty()) services else defaultTemplateForCategory(domain).subServices
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
            }
        }
    }
}
