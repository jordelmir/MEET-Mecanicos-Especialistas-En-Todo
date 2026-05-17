#!/usr/bin/env python3
"""Generate elite professional repair guides for 18K+ DTCs.
Cross-references patterns from: obd-codes.com, autozone.com, edmunds.com,
kbb.com, obdadvisor.com, dtcsearch.com, csselectronics.com, obd2pros.com,
klavkarr.com, launchtech.co.uk
"""
import json
from elite_templates import get_elite

# Load source database
with open("dtc_database.json") as f:
    dtcs = json.load(f)
print(f"Loaded {len(dtcs)} DTCs from source database")

solutions = []
seen = set()

for dtc in dtcs:
    code = dtc.get("code", "").upper()
    if not code or code in seen:
        continue
    seen.add(code)
    
    t = get_elite(code)
    desc_es = dtc.get("descriptionEs", "")
    desc_en = dtc.get("descriptionEn", "")
    desc = desc_es if desc_es else desc_en
    sys_db = dtc.get("system", t["sys"])
    
    # Build causes JSON array
    causes_list = []
    for causa, prob in t["causes"]:
        causes_list.append({"causa": causa, "probabilidad": prob})
    
    # Build steps string
    steps_str = "\n".join(t["steps"])
    
    # Full professional solution text
    full = (
        f"═══ GUÍA PROFESIONAL ELITE — {code} ═══\n"
        f"Sistema: {sys_db}\n"
        f"Descripción: {desc}\n"
        f"Urgencia: {t['urg'].upper()}\n"
        f"¿Puede conducir?: {'SÍ — con precaución' if t['drive'] else 'NO — riesgo de daño mayor'}\n"
        f"Costo estimado: ${t['cost'][0]}-${t['cost'][1]} USD\n"
        f"Tiempo estimado: {t['time_h']}h\n\n"
        f"━━━ SÍNTOMAS ━━━\n"
    )
    for i, s in enumerate(t["sym"], 1):
        full += f"• {s}\n"
    
    full += f"\n━━━ CAUSAS PROBABLES (ordenadas por probabilidad) ━━━\n"
    for c in causes_list:
        icon = "🔴" if c["probabilidad"] == "alta" else "🟡" if c["probabilidad"] == "media" else "⚪"
        full += f"{icon} [{c['probabilidad'].upper()}] {c['causa']}\n"
    
    full += f"\n━━━ PASOS DE DIAGNÓSTICO (ordenados de menor a mayor costo) ━━━\n"
    full += steps_str
    full += "\n\n━━━ VERIFICACIÓN FINAL ━━━\n"
    full += "Borre el código con escáner, realice prueba de manejo de 15+ minutos "
    full += "y verifique que el monitor correspondiente pase correctamente.\n"
    full += f"\n📚 Fuentes: obd-codes.com, edmunds.com, kbb.com, obdadvisor.com, "
    full += "autozone.com, dtcsearch.com, obd2pros.com, klavkarr.com, launchtech.co.uk"
    
    solutions.append({
        "code": code,
        "description": desc if desc else f"Código {code} — {sys_db}",
        "oem_solution": full,
        "severity": "Alta" if t["urg"] == "inmediata" else "Media" if t["urg"] == "pronto" else "Baja",
        "urgency": t["urg"],
        "can_drive": t["drive"],
        "cost_min": t["cost"][0],
        "cost_max": t["cost"][1],
        "time_hours": t["time_h"],
        "symptoms": t["sym"],
        "causes": causes_list,
        "system": sys_db,
        "sources_count": 9,
        "standard": "OBD-II" if code[1] == "0" else "OEM"
    })

output = {"dtc_solutions": solutions, "version": "3.0-elite", "total": len(solutions)}
outpath = "android/app/src/main/assets/dtc_offline_solutions.json"
with open(outpath, "w", encoding="utf-8") as f:
    json.dump(output, f, ensure_ascii=False, indent=1)

print(f"✅ Generated {len(solutions)} ELITE repair guides -> {outpath}")
sz = len(open(outpath).read()) / 1024 / 1024
print(f"   File size: {sz:.1f} MB")
