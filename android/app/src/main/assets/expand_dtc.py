import json

dtcs = []

# P0100-P0199
for i in range(100, 200):
    dtcs.append({
        "code": f"P0{i}",
        "descriptionEs": f"Problema de rango/rendimiento de circuito en sistema de aire/combustible ({i})",
        "descriptionEn": f"Mass or Volume Air Flow Sensor A Circuit ({i})",
        "system": "ENGINE",
        "severity": "MODERATE",
        "possibleCauses": "Sensor defectuoso, cableado",
        "urgency": "CAUTION"
    })

# P0200-P0299
for i in range(200, 300):
    dtcs.append({
        "code": f"P0{i}",
        "descriptionEs": f"Falla en inyector o circuito de combustible ({i})",
        "descriptionEn": f"Injector Circuit ({i})",
        "system": "ENGINE",
        "severity": "CRITICAL",
        "possibleCauses": "Inyector tapado, falta de pulso",
        "urgency": "STOP_DRIVING"
    })

# B0001-B0020
for i in range(1, 21):
    dtcs.append({
        "code": f"B{str(i).zfill(4)}",
        "descriptionEs": f"Problema en sistema SRS/Airbag frontal ({i})",
        "descriptionEn": f"Driver Frontal Stage 1 Deployment Control ({i})",
        "system": "BODY",
        "severity": "CRITICAL",
        "possibleCauses": "Conector amarillo suelto, módulo dañado",
        "urgency": "STOP_DRIVING"
    })

with open("android/app/src/main/assets/dtc_database_es.json", "w") as f:
    json.dump(dtcs, f, indent=2)

