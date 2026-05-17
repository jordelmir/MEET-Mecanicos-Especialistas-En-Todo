package com.elysium369.meet.data.local

// AUTO-GENERATED DATABASE OF WORKSHOP REPAIR GUIDES
object KnowledgeBaseData {
    val guides = mapOf(
        "P0101" to RepairGuide(
            dtc = "P0101",
            systemAffected = "Air/Fuel Ratio",
            possibleCauses = listOf("Sensor MAF sucio", "Fuga de vacío severa", "Problema en cableado del MAF"),
            symptoms = listOf("Ralentí inestable", "Humo negro", "Consumo excesivo"),
            recommendedSolution = """📘 MANUAL DE TALLER:

1. Verifique que no haya fugas entre el MAF y el cuerpo de aceleración.
2. Limpie el MAF con limpiador especial.
3. Verifique voltaje del MAF con escáner (debe subir progresivamente al acelerar).""",
            actionPlan = listOf(
                PrioritizedTask("Limpieza de MAF", "Extraer y aplicar limpiador de contactos.", 15, true), PrioritizedTask("Búsqueda de fugas", "Revisar mangueras de admisión.", 20, false)
            ),
            costEstimate = CostEstimate(10.0, 150.0, "USD", "Limpiador MAF o reemplazo del sensor.")
        ),
        "P0102" to RepairGuide(
            dtc = "P0102",
            systemAffected = "Air/Fuel Ratio",
            possibleCauses = listOf("Sensor MAF desconectado", "Sensor MAF dañado", "Fusible del MAF quemado"),
            symptoms = listOf("El motor puede apagarse al arrancar", "Ralentí inestable", "Luz MIL encendida"),
            recommendedSolution = """📘 MANUAL DE TALLER:

1. Inspeccione el conector del sensor MAF. Verifique que esté firmemente conectado y sin corrosión.
2. Mida el voltaje de alimentación del MAF (generalmente 12V o 5V según fabricante).
3. Reemplace el sensor si tiene alimentación pero no hay señal (0V constantes).""",
            actionPlan = listOf(
                PrioritizedTask("Inspección de Conector", "Verificar arnés del sensor de flujo de masa de aire.", 10, true), PrioritizedTask("Prueba de Voltaje", "Usar multímetro en el pin de señal del MAF.", 20, true)
            ),
            costEstimate = CostEstimate(0.0, 200.0, "USD", "Reparación de cableado o reemplazo de sensor.")
        ),
        "P0113" to RepairGuide(
            dtc = "P0113",
            systemAffected = "Air/Fuel Ratio",
            possibleCauses = listOf("Sensor IAT desconectado", "Cableado IAT roto", "Sensor IAT defectuoso"),
            symptoms = listOf("Dificultad de arranque en frío", "Humo negro en el escape", "Consumo elevado"),
            recommendedSolution = """📘 MANUAL DE TALLER:

1. El código indica 'Voltaje Alto' en el circuito del IAT (Sensor de Temperatura de Aire de Admisión). Esto casi siempre significa un CIRCUITO ABIERTO (cable cortado o desconectado).
2. Verifique que el sensor IAT esté enchufado.
3. Usando el escáner, vea la lectura del IAT. Si lee -40°C, confirme circuito abierto.""",
            actionPlan = listOf(
                PrioritizedTask("Revisar Lectura en Vivo", "Comprobar si el escáner marca -40°C constantes.", 5, true), PrioritizedTask("Continuidad de Circuito", "Medir continuidad desde el IAT hasta el ECU.", 30, false)
            ),
            costEstimate = CostEstimate(0.0, 50.0, "USD", "Reparación de cableado o sensor IAT económico.")
        ),
        "P0128" to RepairGuide(
            dtc = "P0128",
            systemAffected = "Cooling System",
            possibleCauses = listOf("Termostato del motor abierto", "Nivel de refrigerante muy bajo", "Sensor ECT defectuoso"),
            symptoms = listOf("La calefacción no calienta bien", "El motor tarda mucho en llegar a temperatura normal", "Consumo alto"),
            recommendedSolution = """📘 MANUAL DE TALLER:

1. El termostato se ha quedado pegado en posición ABIERTA.
2. Monitoree la temperatura del refrigerante. Si después de 15 minutos manejando no supera los 80°C (176°F), reemplace el termostato.
3. Verifique el nivel de refrigerante en el radiador (en frío).""",
            actionPlan = listOf(
                PrioritizedTask("Reemplazo de Termostato", "Purgar sistema de refrigeración y cambiar termostato.", 60, true), PrioritizedTask("Prueba de Sensor ECT", "Asegurar que el sensor lea temperatura real.", 10, false)
            ),
            costEstimate = CostEstimate(30.0, 150.0, "USD", "Termostato, empaque y refrigerante nuevo.")
        ),
        "P0135" to RepairGuide(
            dtc = "P0135",
            systemAffected = "Exhaust / O2 Sensors",
            possibleCauses = listOf("Calentador del Sensor O2 B1S1 quemado", "Fusible del calentador del O2 fundido", "Falso contacto en conector"),
            symptoms = listOf("Mayor consumo en los primeros 5 minutos de manejo", "Emisiones altas en frío"),
            recommendedSolution = """📘 MANUAL DE TALLER:

1. El circuito del calentador interno del Sensor de Oxígeno B1S1 (Antes del catalizador) ha fallado.
2. Revise el fusible dedicado al 'O2 Heater' en la caja de fusibles.
3. Desconecte el sensor O2 y mida la resistencia entre los dos cables del mismo color (usualmente blancos o negros). Si marca infinito/circuito abierto (OL), el sensor está averiado y debe reemplazarse.""",
            actionPlan = listOf(
                PrioritizedTask("Revisión de Fusibles", "Chequear fusible de calentador de sensores de oxígeno.", 10, true), PrioritizedTask("Medir Resistencia", "Comprobar resistencia del calefactor interno del O2.", 15, true)
            ),
            costEstimate = CostEstimate(5.0, 120.0, "USD", "Reemplazo de fusible o sensor O2 completo.")
        ),
        "P0171" to RepairGuide(
            dtc = "P0171",
            systemAffected = "Air/Fuel Ratio (Bank 1)",
            possibleCauses = listOf("Fuga de vacío (Múltiple, mangueras PCV)", "Sensor MAF sucio", "Presión de combustible baja"),
            symptoms = listOf("Pérdida de potencia", "Ralentí alto/errático", "Motor se apaga al frenar"),
            recommendedSolution = """📘 MANUAL DE TALLER:

1. REVISAR AJUSTES DE COMBUSTIBLE (TRIMS): Vea los 'Fuel Trims' (STFT y LTFT). Si STFT y LTFT suman >+10% en ralentí, pero bajan a casi 0% a 2500 RPM, tiene una FUGA DE VACÍO.
2. LIMPIEZA DEL MAF: Inspeccione los filamentos calientes y limpie.
3. PRUEBA DE VACÍO: Inyecte humo presurizado para observar escapes.""",
            actionPlan = listOf(
                PrioritizedTask("Análisis de Fuel Trims", "Leer STFT y LTFT en ralentí vs 2500 RPM.", 10, true), PrioritizedTask("Limpieza de MAF", "Limpiar sensor MAF.", 15, false), PrioritizedTask("Prueba de humo", "Buscar fugas con máquina de humo.", 45, true)
            ),
            costEstimate = CostEstimate(10.0, 250.0, "USD", "Desde un bote limpia-MAF hasta empacaduras de admisión.")
        ),
        "P0172" to RepairGuide(
            dtc = "P0172",
            systemAffected = "Air/Fuel Ratio (Bank 1)",
            possibleCauses = listOf("Inyector goteando/abierto", "Sensor MAF fuera de rango (lee mucho aire falso)", "Regulador de presión de combustible dañado"),
            symptoms = listOf("Humo negro excesivo", "Bujías carbonizadas", "Olor a combustible"),
            recommendedSolution = """📘 MANUAL DE TALLER:

1. El sistema está inyectando demasiado combustible (Mezcla Rica).
2. Revise la presión de combustible; si está muy alta, el regulador está fallando.
3. Monitoree inyectores y revise bujías del banco 1. Si una está negra y húmeda, ese inyector está goteando.""",
            actionPlan = listOf(
                PrioritizedTask("Presión de combustible", "Medir presión en rampa de inyección.", 20, true), PrioritizedTask("Inspección de Bujías", "Revisar signos de exceso de gasolina en el banco 1.", 30, false)
            ),
            costEstimate = CostEstimate(80.0, 350.0, "USD", "Cambio de inyector o regulador de presión.")
        ),
        "P0300" to RepairGuide(
            dtc = "P0300",
            systemAffected = "Ignition / Fuel System",
            possibleCauses = listOf("Baja presión de combustible", "Fugas de vacío severas", "Múltiples bobinas/bujías desgastadas", "Distribución saltada"),
            symptoms = listOf("Motor inestable en ralentí", "Pérdida de potencia", "Check Engine parpadeando"),
            recommendedSolution = """📘 MANUAL DE TALLER:

1. Revisar Contadores de Fallos: Utilice Modo 06 para leer contadores por cilindro.
2. Verificación de Presión de Combustible: Verifique presión en rampa de inyectores.
3. Prueba de Fuga de Vacío: Realice prueba de humo.
NOTA: Un MIL parpadeante destruirá el convertidor catalítico. ¡No conduzca!""",
            actionPlan = listOf(
                PrioritizedTask("Escanear Modo 06", "Ver contadores de fallos por cilindro.", 10, true), PrioritizedTask("Presión de combustible", "Medir presión en rampa.", 30, true), PrioritizedTask("Prueba de máquina de humo", "Buscar fugas de vacío.", 45, false)
            ),
            costEstimate = CostEstimate(60.0, 300.0, "USD", "Diagnóstico inicial y pruebas de humo/compresión.")
        ),
        "P0301" to RepairGuide(
            dtc = "P0301",
            systemAffected = "Cylinder 1",
            possibleCauses = listOf("Bujía del cilindro 1 defectuosa", "Bobina de encendido del cilindro 1 defectuosa", "Inyector del cilindro 1 obstruido", "Baja compresión en cilindro 1"),
            symptoms = listOf("Tirones bajo carga", "Ralentí áspero", "Check engine parpadeando"),
            recommendedSolution = """📘 PROCEDIMIENTO DE TALLER (DIAGNÓSTICO CRUZADO):

1. PRUEBA DE BOBINA: Intercambie la bobina del Cilindro 1 con la del Cilindro 2. Borre DTC y maneje. Si el fallo se mueve, cambie la bobina.
2. PRUEBA DE BUJÍA: Si el fallo persiste, revise y cambie la bujía 1.
3. INYECTOR Y COMPRESIÓN: Revise el inyector y compresión si encendido está bien.""",
            actionPlan = listOf(
                PrioritizedTask("Intercambio de Bobinas", "Mover bobina de Cilindro 1", 15, true), PrioritizedTask("Inspección de Bujía", "Extraer y revisar bujía del cilindro 1", 20, false)
            ),
            costEstimate = CostEstimate(20.0, 150.0, "USD", "Reemplazo individual de bujía o bobina.")
        ),
        "P0302" to RepairGuide(
            dtc = "P0302",
            systemAffected = "Cylinder 2",
            possibleCauses = listOf("Bujía del cilindro 2 defectuosa", "Bobina de encendido del cilindro 2 defectuosa", "Inyector del cilindro 2 obstruido", "Baja compresión en cilindro 2"),
            symptoms = listOf("Tirones bajo carga", "Ralentí áspero", "Check engine parpadeando"),
            recommendedSolution = """📘 PROCEDIMIENTO DE TALLER (DIAGNÓSTICO CRUZADO):

1. PRUEBA DE BOBINA: Intercambie la bobina del Cilindro 2 con la del Cilindro 3. Borre DTC y maneje. Si el fallo se mueve, cambie la bobina.
2. PRUEBA DE BUJÍA: Si el fallo persiste, revise y cambie la bujía 2.
3. INYECTOR Y COMPRESIÓN: Revise el inyector y compresión si encendido está bien.""",
            actionPlan = listOf(
                PrioritizedTask("Intercambio de Bobinas", "Mover bobina de Cilindro 2", 15, true), PrioritizedTask("Inspección de Bujía", "Extraer y revisar bujía del cilindro 2", 20, false)
            ),
            costEstimate = CostEstimate(20.0, 150.0, "USD", "Reemplazo individual de bujía o bobina.")
        ),
        "P0303" to RepairGuide(
            dtc = "P0303",
            systemAffected = "Cylinder 3",
            possibleCauses = listOf("Bujía del cilindro 3 defectuosa", "Bobina de encendido del cilindro 3 defectuosa", "Inyector del cilindro 3 obstruido", "Baja compresión en cilindro 3"),
            symptoms = listOf("Tirones bajo carga", "Ralentí áspero", "Check engine parpadeando"),
            recommendedSolution = """📘 PROCEDIMIENTO DE TALLER (DIAGNÓSTICO CRUZADO):

1. PRUEBA DE BOBINA: Intercambie la bobina del Cilindro 3 con la del Cilindro 4. Borre DTC y maneje. Si el fallo se mueve, cambie la bobina.
2. PRUEBA DE BUJÍA: Si el fallo persiste, revise y cambie la bujía 3.
3. INYECTOR Y COMPRESIÓN: Revise el inyector y compresión si encendido está bien.""",
            actionPlan = listOf(
                PrioritizedTask("Intercambio de Bobinas", "Mover bobina de Cilindro 3", 15, true), PrioritizedTask("Inspección de Bujía", "Extraer y revisar bujía del cilindro 3", 20, false)
            ),
            costEstimate = CostEstimate(20.0, 150.0, "USD", "Reemplazo individual de bujía o bobina.")
        ),
        "P0304" to RepairGuide(
            dtc = "P0304",
            systemAffected = "Cylinder 4",
            possibleCauses = listOf("Bujía del cilindro 4 defectuosa", "Bobina de encendido del cilindro 4 defectuosa", "Inyector del cilindro 4 obstruido", "Baja compresión en cilindro 4"),
            symptoms = listOf("Tirones bajo carga", "Ralentí áspero", "Check engine parpadeando"),
            recommendedSolution = """📘 PROCEDIMIENTO DE TALLER (DIAGNÓSTICO CRUZADO):

1. PRUEBA DE BOBINA: Intercambie la bobina del Cilindro 4 con la del Cilindro 1. Borre DTC y maneje. Si el fallo se mueve, cambie la bobina.
2. PRUEBA DE BUJÍA: Si el fallo persiste, revise y cambie la bujía 4.
3. INYECTOR Y COMPRESIÓN: Revise el inyector y compresión si encendido está bien.""",
            actionPlan = listOf(
                PrioritizedTask("Intercambio de Bobinas", "Mover bobina de Cilindro 4", 15, true), PrioritizedTask("Inspección de Bujía", "Extraer y revisar bujía del cilindro 4", 20, false)
            ),
            costEstimate = CostEstimate(20.0, 150.0, "USD", "Reemplazo individual de bujía o bobina.")
        ),
        "P0305" to RepairGuide(
            dtc = "P0305",
            systemAffected = "Cylinder 5",
            possibleCauses = listOf("Bujía del cilindro 5 defectuosa", "Bobina de encendido del cilindro 5 defectuosa", "Inyector del cilindro 5 obstruido", "Baja compresión en cilindro 5"),
            symptoms = listOf("Tirones bajo carga", "Ralentí áspero", "Check engine parpadeando"),
            recommendedSolution = """📘 PROCEDIMIENTO DE TALLER (DIAGNÓSTICO CRUZADO):

1. PRUEBA DE BOBINA: Intercambie la bobina del Cilindro 5 con la del Cilindro 6. Borre DTC y maneje. Si el fallo se mueve, cambie la bobina.
2. PRUEBA DE BUJÍA: Si el fallo persiste, revise y cambie la bujía 5.
3. INYECTOR Y COMPRESIÓN: Revise el inyector y compresión si encendido está bien.""",
            actionPlan = listOf(
                PrioritizedTask("Intercambio de Bobinas", "Mover bobina de Cilindro 5", 15, true), PrioritizedTask("Inspección de Bujía", "Extraer y revisar bujía del cilindro 5", 20, false)
            ),
            costEstimate = CostEstimate(20.0, 150.0, "USD", "Reemplazo individual de bujía o bobina.")
        ),
        "P0306" to RepairGuide(
            dtc = "P0306",
            systemAffected = "Cylinder 6",
            possibleCauses = listOf("Bujía del cilindro 6 defectuosa", "Bobina de encendido del cilindro 6 defectuosa", "Inyector del cilindro 6 obstruido", "Baja compresión en cilindro 6"),
            symptoms = listOf("Tirones bajo carga", "Ralentí áspero", "Check engine parpadeando"),
            recommendedSolution = """📘 PROCEDIMIENTO DE TALLER (DIAGNÓSTICO CRUZADO):

1. PRUEBA DE BOBINA: Intercambie la bobina del Cilindro 6 con la del Cilindro 5. Borre DTC y maneje. Si el fallo se mueve, cambie la bobina.
2. PRUEBA DE BUJÍA: Si el fallo persiste, revise y cambie la bujía 6.
3. INYECTOR Y COMPRESIÓN: Revise el inyector y compresión si encendido está bien.""",
            actionPlan = listOf(
                PrioritizedTask("Intercambio de Bobinas", "Mover bobina de Cilindro 6", 15, true), PrioritizedTask("Inspección de Bujía", "Extraer y revisar bujía del cilindro 6", 20, false)
            ),
            costEstimate = CostEstimate(20.0, 150.0, "USD", "Reemplazo individual de bujía o bobina.")
        ),
        "P0307" to RepairGuide(
            dtc = "P0307",
            systemAffected = "Cylinder 7",
            possibleCauses = listOf("Bujía del cilindro 7 defectuosa", "Bobina de encendido del cilindro 7 defectuosa", "Inyector del cilindro 7 obstruido", "Baja compresión en cilindro 7"),
            symptoms = listOf("Tirones bajo carga", "Ralentí áspero", "Check engine parpadeando"),
            recommendedSolution = """📘 PROCEDIMIENTO DE TALLER (DIAGNÓSTICO CRUZADO):

1. PRUEBA DE BOBINA: Intercambie la bobina del Cilindro 7 con la del Cilindro 8. Borre DTC y maneje. Si el fallo se mueve, cambie la bobina.
2. PRUEBA DE BUJÍA: Si el fallo persiste, revise y cambie la bujía 7.
3. INYECTOR Y COMPRESIÓN: Revise el inyector y compresión si encendido está bien.""",
            actionPlan = listOf(
                PrioritizedTask("Intercambio de Bobinas", "Mover bobina de Cilindro 7", 15, true), PrioritizedTask("Inspección de Bujía", "Extraer y revisar bujía del cilindro 7", 20, false)
            ),
            costEstimate = CostEstimate(20.0, 150.0, "USD", "Reemplazo individual de bujía o bobina.")
        ),
        "P0308" to RepairGuide(
            dtc = "P0308",
            systemAffected = "Cylinder 8",
            possibleCauses = listOf("Bujía del cilindro 8 defectuosa", "Bobina de encendido del cilindro 8 defectuosa", "Inyector del cilindro 8 obstruido", "Baja compresión en cilindro 8"),
            symptoms = listOf("Tirones bajo carga", "Ralentí áspero", "Check engine parpadeando"),
            recommendedSolution = """📘 PROCEDIMIENTO DE TALLER (DIAGNÓSTICO CRUZADO):

1. PRUEBA DE BOBINA: Intercambie la bobina del Cilindro 8 con la del Cilindro 7. Borre DTC y maneje. Si el fallo se mueve, cambie la bobina.
2. PRUEBA DE BUJÍA: Si el fallo persiste, revise y cambie la bujía 8.
3. INYECTOR Y COMPRESIÓN: Revise el inyector y compresión si encendido está bien.""",
            actionPlan = listOf(
                PrioritizedTask("Intercambio de Bobinas", "Mover bobina de Cilindro 8", 15, true), PrioritizedTask("Inspección de Bujía", "Extraer y revisar bujía del cilindro 8", 20, false)
            ),
            costEstimate = CostEstimate(20.0, 150.0, "USD", "Reemplazo individual de bujía o bobina.")
        ),
        "P0335" to RepairGuide(
            dtc = "P0335",
            systemAffected = "Ignition / Sensors",
            possibleCauses = listOf("Sensor CKP (Cigüeñal) defectuoso", "Cableado del CKP en corto o roto", "Anillo reluctor del cigüeñal dañado"),
            symptoms = listOf("El motor gira pero no arranca (No Crank / No Start)", "El vehículo se apaga repentinamente en marcha", "El tacómetro no marca RPM al dar arranque"),
            recommendedSolution = """📘 MANUAL DE TALLER:

1. Revise el escáner en flujo de datos (Live Data) mientras da arranque. Si las RPM muestran 0, la computadora no está 'viendo' girar el motor.
2. Inspeccione el cableado cerca del sensor CKP, suele derretirse con el escape o dañarse por aceite.
3. Pruebe la señal del CKP con un osciloscopio (para sensores Hall o inductivos).""",
            actionPlan = listOf(
                PrioritizedTask("Leer RPM en vivo", "Dar arranque y verificar si el sensor registra giro.", 5, true), PrioritizedTask("Inspección visual del CKP", "Revisar cables cerca del bloque del motor.", 15, true)
            ),
            costEstimate = CostEstimate(50.0, 250.0, "USD", "Reemplazo de sensor de posición de cigüeñal (CKP).")
        ),
        "P0340" to RepairGuide(
            dtc = "P0340",
            systemAffected = "Ignition / Sensors",
            possibleCauses = listOf("Sensor CMP (Árbol de levas) defectuoso", "Sincronización de banda/cadena de distribución incorrecta", "Problema eléctrico en el CMP"),
            symptoms = listOf("Arranque prolongado", "Falta de potencia en altas RPM", "Consumo excesivo"),
            recommendedSolution = """📘 MANUAL DE TALLER:

1. El ECU usa el CMP para inyección secuencial. Verifique si el motor arranca pero tarda más de lo normal.
2. Revise la correlación entre CMP y CKP si es posible con osciloscopio.
3. Si el motor tiene una cadena de distribución estirada o salto de diente, este código aparecerá permanentemente.""",
            actionPlan = listOf(
                PrioritizedTask("Chequeo de distribución", "Verificar marcas de tiempo del motor.", 120, true), PrioritizedTask("Sustituir CMP", "Reemplazar sensor de árbol de levas si el tiempo es correcto.", 30, false)
            ),
            costEstimate = CostEstimate(60.0, 800.0, "USD", "Desde un simple sensor hasta un kit de distribución completo.")
        ),
        "P0420" to RepairGuide(
            dtc = "P0420",
            systemAffected = "Catalytic Converter (Bank 1)",
            possibleCauses = listOf("Catalizador derretido/ineficiente", "Sensor O2 B1S2 defectuoso", "Fugas de escape antes del catalizador"),
            symptoms = listOf("Falta de potencia extrema a altas RPM (si tapado)", "Olor a azufre (huevos podridos)", "Falla prueba de emisiones"),
            recommendedSolution = """📘 MANUAL DE TALLER:

1. PRUEBA DEL SENSOR O2: Vea la gráfica del 'Sensor O2 B1S2'. Si oscila igual que el S1 (0.1V a 0.9V), el catalizador ESTÁ MUERTO.
2. VERIFICACIÓN DE FUGAS: Busque tictac cerca del colector.
¡ATENCIÓN! Reemplazar catalizador sin arreglar causa raíz lo dañará nuevamente.""",
            actionPlan = listOf(
                PrioritizedTask("Monitoreo de Sensores O2", "Graficar B1S1 y B1S2 simultáneamente.", 20, true), PrioritizedTask("Inspección de fugas de escape", "Revisar colector y empacaduras.", 30, false)
            ),
            costEstimate = CostEstimate(250.0, 1500.0, "USD", "Posible reemplazo de catalizador.")
        ),
        "P0442" to RepairGuide(
            dtc = "P0442",
            systemAffected = "EVAP System",
            possibleCauses = listOf("Tapa de combustible floja o empaque dañado", "Mangueras del sistema EVAP agrietadas", "Válvula de purga EVAP no sella"),
            symptoms = listOf("Ligero olor a gasolina cerca del tanque", "Luz de Check Engine encendida"),
            recommendedSolution = """📘 MANUAL DE TALLER:

1. CÓDIGO FÁCIL: Fuga PEQUEÑA detectada.
2. Revise la tapa del tanque de gasolina. Límpiela y apriétela hasta escuchar clics. Borre el código y maneje por unos días.
3. Si regresa, usar máquina de humo en el sistema EVAP para encontrar micro-fugas en mangueras.""",
            actionPlan = listOf(
                PrioritizedTask("Revisar tapón de gasolina", "Inspeccionar empaque y apretar.", 5, true), PrioritizedTask("Prueba de humo EVAP", "Buscar micro-fugas en el sistema de evaporación.", 60, false)
            ),
            costEstimate = CostEstimate(0.0, 150.0, "USD", "Apretar tapa de gas, o reemplazar mangueras EVAP.")
        ),
        "P0455" to RepairGuide(
            dtc = "P0455",
            systemAffected = "EVAP System",
            possibleCauses = listOf("Tapa de combustible faltante o totalmente abierta", "Manguera EVAP principal rota o desconectada", "Válvula de ventilación (Vent Valve) atascada abierta"),
            symptoms = listOf("Fuerte olor a vapores de gasolina", "Check Engine iluminado"),
            recommendedSolution = """📘 MANUAL DE TALLER:

1. Fuga GRANDE detectada en EVAP.
2. Generalmente es una manguera completamente desconectada en el canister trasero o la válvula de purga atascada al 100% abierta.
3. Inspeccione visualmente el Canister bajo el vehículo y compruebe si la válvula de purga en el motor hace vacío constante sin ser activada.""",
            actionPlan = listOf(
                PrioritizedTask("Inspección visual del Canister", "Revisar mangueras principales bajo el auto.", 15, true), PrioritizedTask("Probar válvula de purga", "Verificar si sella correctamente.", 15, true)
            ),
            costEstimate = CostEstimate(30.0, 200.0, "USD", "Reemplazo de válvula de purga o canister de carbón.")
        ),
        "P0500" to RepairGuide(
            dtc = "P0500",
            systemAffected = "Vehicle Speed / ABS",
            possibleCauses = listOf("Sensor de Velocidad (VSS) defectuoso", "Sensor de rueda ABS averiado", "Engranaje del VSS en transmisión roto"),
            symptoms = listOf("El velocímetro no funciona o fluctúa erráticamente", "La transmisión automática hace cambios bruscos", "Pérdida de Cruise Control"),
            recommendedSolution = """📘 MANUAL DE TALLER:

1. En vehículos antiguos, revise el sensor VSS montado en la transmisión.
2. En vehículos modernos (Can Bus), la velocidad se calcula mediante los sensores de rueda ABS. Verifique con el escáner si algún sensor de rueda marca 0 km/h mientras el vehículo está en movimiento.""",
            actionPlan = listOf(
                PrioritizedTask("Leer Sensores de Rueda", "Monitorear velocidad de las 4 ruedas en vivo.", 15, true), PrioritizedTask("Revisar sensor VSS", "Inspección de cableado y sensor en caja de cambios.", 20, false)
            ),
            costEstimate = CostEstimate(40.0, 250.0, "USD", "Sensor de rueda ABS o Sensor VSS nuevo.")
        )
    )
}
