package com.elysium369.meet.inspections

import com.elysium369.meet.core.domain.SourceAuthority
import java.security.MessageDigest

object InspectionHub {

    fun getStandardProtocol(type: InspectionType): InspectionProtocol {
        val checks = when (type) {
            InspectionType.PRE_PURCHASE -> listOf(
                InspectionCheck("CHK_VIN_BODY", "Verificación VIN vs Chasis y Placas OEM", "Comprobar que el número VIN no presenta remaches alterados ni soldaduras."),
                InspectionCheck("CHK_STRUCTURAL", "Inspección Estructural de Largueros y Postes", "Verificar ausencia de deformaciones por impacto frontal o lateral."),
                InspectionCheck("CHK_ECU_DTC", "Escaneo OBD2 Completo de Todos los Módulos", "Lectura de DTCs en PCM, TCM, ABS, SRS y BCM."),
                InspectionCheck("CHK_FLUIDS", "Nivel y Estado de Fluidos", "Inspección de aceite de motor, líquido de frenos y refrigerante."),
                InspectionCheck("CHK_COMPRESSION", "Prueba de Balance / Compresión Relativa", "Evaluación de sincronismo y compresión de cilindros.")
            )
            InspectionType.DVIR -> listOf(
                InspectionCheck("DVIR_BRAKES", "Frenos de Servicio y Emergencia", "Comprobación de pedal, tacto y retención en pendiente."),
                InspectionCheck("DVIR_TIRES", "Presión y Profundidad de Neumáticos", "Mínimo 2mm de dibujo y sin deformaciones."),
                InspectionCheck("DVIR_LIGHTS", "Luces de Giro, Frenado y Faros", "Comprobar iluminación 360 grados."),
                InspectionCheck("DVIR_FLUIDS", "Fugas Visibles de Fluidos", "Sin goteos bajo el vano motor.")
            )
            InspectionType.EMISSIONS_READINESS -> listOf(
                InspectionCheck("EMIS_MIL", "Estado del Testigo Check Engine (MIL)", "El foco MIL debe funcionar y apagarse tras el arranque."),
                InspectionCheck("EMIS_CAT", "Monitor de Convertidor Catalítico", "Estado completado en lectura OBD2 Modo $01."),
                InspectionCheck("EMIS_EVAP", "Monitor del Sistema EVAP", "Estado completado sin fugas de vapor."),
                InspectionCheck("EMIS_O2", "Monitor de Sensor de Oxígeno / Lambda", "Respuesta dinámica y calefactores activos.")
            )
            InspectionType.POST_REPAIR -> listOf(
                InspectionCheck("POST_DTC_CLEAR", "Ausencia de Códigos DTC Recurrentes", "Lectura limpia de memoria de fallas post-servicio."),
                InspectionCheck("POST_ROAD_TEST", "Prueba de Ruta y Ciclo de Conducción", "Verificación de comportamiento en condiciones reales de manejo."),
                InspectionCheck("POST_TORQUE", "Verificación de Torques de Seguridad", "Apriete verificado en pernos críticos según spec OEM.")
            )
            InspectionType.ACCIDENT_DAMAGE -> listOf(
                InspectionCheck("ACC_AIRBAGS", "Despliegue de Bolsas de Aire / Pretensores", "Estado de detonadores y centralita SRS."),
                InspectionCheck("ACC_SUSPENSION", "Geometría y Alineación de Tren de Rodaje", "Comprobación de brazos, amortiguadores y rótulas."),
                InspectionCheck("ACC_PANELS", "Paneles Exteriores y Carrocería Afectados", "Registro fotográfico de piezas sustituibles vs reparables.")
            )
        }

        return InspectionProtocol(
            protocolId = "PROTO_${type.name}_V1",
            type = type,
            version = "1.0.0",
            authority = SourceAuthority.SERVICE_PROVIDER,
            checks = checks
        )
    }

    fun signInspectionSession(
        protocolId: String,
        vehicleId: String,
        inspectorId: String,
        completedChecks: List<InspectionCheck>
    ): String {
        val payload = "$protocolId:$vehicleId:$inspectorId:${completedChecks.count { it.isPassed == true }}/${completedChecks.size}"
        return MessageDigest.getInstance("SHA-256")
            .digest(payload.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
}
