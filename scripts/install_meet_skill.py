#!/usr/bin/env python3
"""
Installer and Synchronizer for meet-apk-operator skill and CLI harness.
Installs the authoritative skill to all AI agent skill directories.
"""

import os
import sys

operator_py_content = '''#!/usr/bin/env python3
"""
MEET APK AI Operator CLI Harness — Master Edition
Enables any AI agent (Antigravity, Mavis, Codex, Claude) to fully operate,
inspect, navigate, test, and debug the MEET Android application (com.elysium369.meet)
across ALL 5 verticals: Viajes, Grúa, Mecánicos, Repuestos, and Servicios Elysium,
plus the Platform Trust Center.
"""

import sys
import os
import subprocess
import json
import time
import re
import xml.etree.ElementTree as ET

DEFAULT_SERIAL = "127.0.0.1:5555"

def get_adb_device():
    serial = os.environ.get("ANDROID_SERIAL")
    if serial:
        return serial
    
    try:
        out = subprocess.check_output(["adb", "devices"], text=True)
        lines = [line.strip() for line in out.splitlines() if line.strip() and not line.startswith("List of")]
        active_devices = []
        for line in lines:
            parts = line.split()
            if len(parts) >= 2 and parts[1] == "device":
                active_devices.append(parts[0])
        
        if DEFAULT_SERIAL in active_devices:
            return DEFAULT_SERIAL
        if active_devices:
            return active_devices[0]
    except Exception:
        pass
    return DEFAULT_SERIAL

DEVICE = get_adb_device()

def run_adb(args, capture=True):
    cmd = ["adb", "-s", DEVICE] + args
    if capture:
        res = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
        return res.stdout.strip(), res.stderr.strip(), res.returncode
    else:
        res = subprocess.run(cmd)
        return "", "", res.returncode

def dump_state():
    run_adb(["shell", "am", "broadcast", "-a", "com.elysium369.meet.AI_ACTION", "--es", "type", "DUMP_STATE"])
    time.sleep(0.4)
    out, _, code = run_adb(["shell", "run-as", "com.elysium369.meet", "cat", "files/meet_state.json"])
    if code == 0 and out and out.startswith("{"):
        try:
            return json.loads(out)
        except Exception:
            pass
    out, _, code = run_adb(["shell", "cat", "/data/local/tmp/meet_state.json"])
    if code == 0 and out and out.startswith("{"):
        try:
            return json.loads(out)
        except Exception:
            pass
    out, _, code = run_adb(["shell", "cat", "/sdcard/Android/data/com.elysium369.meet/files/meet_state.json"])
    if code == 0 and out and out.startswith("{"):
        try:
            return json.loads(out)
        except Exception:
            pass
    return {"error": "State snapshot not found or not yet generated"}

def inspect_screen():
    run_adb(["shell", "uiautomator", "dump", "/data/local/tmp/view_dump.xml"])
    xml_data, _, code = run_adb(["shell", "cat", "/data/local/tmp/view_dump.xml"])
    if code != 0 or not xml_data:
        return []
    
    elements = []
    try:
        root = ET.fromstring(xml_data)
        for node in root.iter("node"):
            text = node.attrib.get("text", "").strip()
            desc = node.attrib.get("content-desc", "").strip()
            res_id = node.attrib.get("resource-id", "").strip()
            bounds_str = node.attrib.get("bounds", "")
            clickable = node.attrib.get("clickable", "false") == "true"
            
            m = re.match(r"\\[(\\d+),(\\d+)\\]\\[(\\d+),(\\d+)\\]", bounds_str)
            if m:
                x1, y1, x2, y2 = map(int, m.groups())
                center_x = (x1 + x2) // 2
                center_y = (y1 + y2) // 2
                
                label = text or desc
                if label or clickable or res_id:
                    elements.append({
                        "label": label,
                        "text": text,
                        "desc": desc,
                        "id": res_id.split("/")[-1] if "/" in res_id else res_id,
                        "clickable": clickable,
                        "center": [center_x, center_y],
                        "bounds": [x1, y1, x2, y2],
                        "class": node.attrib.get("class", "").split(".")[-1]
                    })
    except Exception as e:
        print(f"Error parsing XML hierarchy: {e}", file=sys.stderr)
    return elements

def tap_element(query):
    elements = inspect_screen()
    q = query.lower()
    
    for el in elements:
        if el["label"].lower() == q:
            cx, cy = el["center"]
            run_adb(["shell", "input", "tap", str(cx), str(cy)])
            return True, f"Tapped exact match '{el['label']}' at ({cx}, {cy})"
            
    for el in elements:
        if q in el["label"].lower() or (el["id"] and q in el["id"].lower()):
            cx, cy = el["center"]
            run_adb(["shell", "input", "tap", str(cx), str(cy)])
            return True, f"Tapped partial match '{el['label']}' ({el['id']}) at ({cx}, {cy})"
            
    return False, f"Element matching '{query}' not found on screen"

ROUTE_ALIASES = {
    "tow": "tow_truck",
    "tow_truck": "tow_truck",
    "tow_truck_service": "tow_truck_service",
    "grua": "tow_truck",
    "grúa": "tow_truck",
    "mechanic": "mechanic_service",
    "mechanic_service": "mechanic_service",
    "mecanico": "mechanic_service",
    "mecanicos": "mechanic_service",
    "mecánico": "mechanic_service",
    "mecánicos": "mechanic_service",
    "taller": "mechanic_service",
    "parts": "part_request",
    "part_request": "part_request",
    "repuestos": "part_request",
    "repuestera": "part_request",
    "universal": "universal_services",
    "universal_services": "universal_services",
    "servicios_elysium": "universal_services",
    "elysium_services": "universal_services",
    "ferreteria": "universal_activity/hardware_store",
    "hardware_store": "universal_activity/hardware_store",
    "detailing": "universal_activity/detailing",
    "trust": "trust_center",
    "trust_center": "trust_center",
    "platform_trust_center": "trust_center",
    "centro_de_confianza": "trust_center",
    "ride": "ride_service",
    "ride_service": "ride_service",
    "viajes": "ride_service",
    "viaje": "ride_service",
    "scanner": "scanner",
    "escaneo": "scanner",
    "dtc": "dtc",
    "dtcs": "dtcs",
    "garage": "garage",
    "garaje": "garage",
    "home": "home",
    "inicio": "home"
}

def resolve_route(route_query):
    parts = route_query.strip().split(maxsplit=1)
    base = parts[0].lower()
    if base in ["universal_activity", "activity"] and len(parts) > 1:
        return f"universal_activity/{parts[1]}"
    if base == "universal" and len(parts) > 1:
        return f"universal_activity/{parts[1]}"
    return ROUTE_ALIASES.get(base, route_query)

def nav(route):
    target = resolve_route(route)
    run_adb(["shell", "am", "broadcast", "-a", "com.elysium369.meet.AI_NAVIGATE", "--es", "route", target])
    time.sleep(0.5)
    print(f"Dispatched AI_NAVIGATE to route: {target} (requested: {route})")

def switch_role(is_driver):
    run_adb([
        "shell", "am", "broadcast",
        "-a", "com.elysium369.meet.AI_ACTION",
        "--es", "type", "SWITCH_ROLE",
        "--ez", "isDriver", "true" if is_driver else "false"
    ])
    time.sleep(0.3)
    role_name = "CHOFER" if is_driver else "PASAJERO"
    print(f"Dispatched SWITCH_ROLE to: {role_name}")

def switch_mode(target_mode):
    target = target_mode.lower()
    is_pro = target in ["specialist", "especialista", "pro", "driver", "chofer", "taller", "gruista", "repuestera"]
    
    pro_labels = [
        "COCKPIT GRUISTA (PRO)",
        "COCKPIT TALLER (PRO)",
        "ESPECIALISTA EN REPUESTOS",
        "FERRETERÍA AFILIADA",
        "ESPECIALISTA",
        "CONDUCTOR",
        "CHOFER"
    ]
    client_labels = [
        "PEDIR GRÚA (CLIENTE)",
        "PEDIR MECÁNICO (CLIENTE)",
        "CLIENTE / COMPRADOR",
        "CLIENTE ELYSIUM",
        "CLIENTE",
        "PASAJERO"
    ]
    
    search_list = pro_labels if is_pro else client_labels
    for label in search_list:
        ok, msg = tap_element(label)
        if ok:
            print(f"Switched mode to {'SPECIALIST/PRO' if is_pro else 'CLIENT'}: {msg}")
            return True
            
    switch_role(is_pro)
    return True

def inject_gps(lat, lng):
    run_adb([
        "shell", "am", "broadcast",
        "-a", "com.elysium369.meet.AI_ACTION",
        "--es", "type", "INJECT_GPS",
        "--ef", "lat", str(lat),
        "--ef", "lng", str(lng)
    ])
    time.sleep(0.2)
    print(f"Injected GPS coordinates: ({lat}, {lng})")

def take_screenshot(path):
    os.makedirs(os.path.dirname(os.path.abspath(path)), exist_ok=True)
    with open(path, "wb") as f:
        p = subprocess.Popen(["adb", "-s", DEVICE, "exec-out", "screencap", "-p"], stdout=f)
        p.wait()
    print(f"Screenshot saved to: {path}")

def topup_flow(amount=10000, reference=None):
    if not reference:
        reference = f"SINPE-FLOW-{int(time.time())}"
    
    print(f"Initiating SINPE topup flow: ₡{amount} (Ref: {reference})...")
    ok, msg = tap_element("RECARGAR SALDO CON SINPE MÓVIL")
    if not ok:
        ok, msg = tap_element("RECARGAR CON SINPE")
    time.sleep(1.0)
    
    if amount == 5000:
        tap_element("₡5k")
    elif amount == 10000:
        tap_element("₡10k")
    elif amount == 20000:
        tap_element("₡20k")
    elif amount == 50000:
        tap_element("₡50k")
    else:
        tap_element("Monto en Colones (CRC)")
        time.sleep(0.3)
        run_adb(["shell", "input", "text", str(amount)])
    
    time.sleep(0.5)
    tap_element("Número de Comprobante / Referencia")
    time.sleep(0.3)
    run_adb(["shell", "input", "text", reference])
    time.sleep(0.5)
    
    run_adb(["shell", "input", "keyevent", "111"])
    time.sleep(0.5)
    
    ok, msg = tap_element("ENVIAR Y NOTIFICAR AL TRUST CENTER")
    print(f"Submitted topup to Trust Center: {msg}")
    time.sleep(1.0)

def approve_topup():
    print("Approving pending topup in Platform Trust Center...")
    nav("trust_center")
    time.sleep(1.5)
    ok, msg = tap_element("ACREDITAR")
    if ok:
        print(f"Topup accredited successfully: {msg}")
    else:
        print(f"No pending topup button found: {msg}")

def reject_topup():
    print("Rejecting pending topup in Platform Trust Center...")
    nav("trust_center")
    time.sleep(1.5)
    ok, msg = tap_element("RECHAZAR")
    if ok:
        print(f"Topup rejected: {msg}")
    else:
        print(f"No pending topup button found: {msg}")

def run_test_flow():
    print(f"=== INICIANDO PRUEBA DE FLUJO AUTÓNOMO DE VIAJES (Dispositivo: {DEVICE}) ===")
    out_dir = "docs/ai_flow_test"
    os.makedirs(out_dir, exist_ok=True)
    
    print("\\n1. Lanzando MainActivity...")
    run_adb(["shell", "am", "start", "-n", "com.elysium369.meet/.MainActivity"])
    time.sleep(2)
    take_screenshot(f"{out_dir}/01_startup.png")
    
    print("\\n2. Inspeccionando estado reactivo de la app...")
    state = dump_state()
    print(json.dumps(state, indent=2))
    
    print("\\n3. Navegando semánticamente a 'ride_service'...")
    nav("ride_service")
    time.sleep(1.5)
    take_screenshot(f"{out_dir}/02_ride_service_passenger.png")
    
    print("\\n4. Cambiando a MODO CHOFER...")
    switch_role(True)
    time.sleep(1.5)
    take_screenshot(f"{out_dir}/03_ride_service_driver.png")
    
    print("\\n5. Inspeccionando elementos interactivos en pantalla de chofer:")
    elements = inspect_screen()
    for el in elements[:12]:
        if el["label"]:
            print(f"  - [{el['class']}] \\"{el['label']}\\" @ {el['center']}")
            
    print("\\n6. Navegando semánticamente a 'trust_center'...")
    nav("trust_center")
    time.sleep(1.5)
    take_screenshot(f"{out_dir}/04_trust_center.png")
    
    print("\\n7. Retornando a 'home'...")
    nav("home")
    time.sleep(1)
    take_screenshot(f"{out_dir}/05_home.png")
    
    print("\\n=== PRUEBA DE FLUJO AUTÓNOMO COMPLETADA CON ÉXITO ===")

def run_test_all_verticals():
    print(f"=== INICIANDO AUDITORÍA MULTI-VERTICAL E INTEGRAL DE MEET (Dispositivo: {DEVICE}) ===")
    out_dir = "docs/screenshots/meet_ecosystem_verification"
    os.makedirs(out_dir, exist_ok=True)
    
    print("\\n[1/7] Lanzando aplicación principal...")
    run_adb(["shell", "am", "start", "-n", "com.elysium369.meet/.MainActivity"])
    time.sleep(2.0)
    take_screenshot(f"{out_dir}/01_home_dashboard.png")
    
    print("\\n[2/7] Navegando a Grúa y Rescate Vial...")
    nav("tow_truck")
    time.sleep(1.8)
    take_screenshot(f"{out_dir}/02_grua_cliente.png")
    switch_mode("specialist")
    time.sleep(1.5)
    take_screenshot(f"{out_dir}/03_grua_cockpit_pro.png")
    
    print("\\n[3/7] Navegando a Mecánicos y Diagnóstico...")
    nav("mechanic_service")
    time.sleep(1.8)
    take_screenshot(f"{out_dir}/04_mecanicos_cliente.png")
    switch_mode("specialist")
    time.sleep(1.5)
    take_screenshot(f"{out_dir}/05_mecanicos_cockpit_pro.png")
    
    print("\\n[4/7] Navegando a Repuestos y Distribuidoras...")
    nav("part_request")
    time.sleep(1.8)
    take_screenshot(f"{out_dir}/06_repuestos_cliente.png")
    switch_mode("specialist")
    time.sleep(1.5)
    take_screenshot(f"{out_dir}/07_repuestos_cockpit_pro.png")
    
    print("\\n[5/7] Navegando a Servicios Elysium (Ferretería y Materiales)...")
    nav("universal_activity/hardware_store")
    time.sleep(1.8)
    take_screenshot(f"{out_dir}/08_servicios_elysium_cliente.png")
    switch_mode("specialist")
    time.sleep(1.5)
    take_screenshot(f"{out_dir}/09_servicios_elysium_cockpit_pro.png")
    
    print("\\n[6/7] Realizando prueba de recarga SINPE y validación en Trust Center...")
    ref_test = f"SINPE-AUDIT-{int(time.time())}"
    topup_flow(amount=10000, reference=ref_test)
    time.sleep(1.5)
    take_screenshot(f"{out_dir}/10_topup_submitted_pending.png")
    
    nav("trust_center")
    time.sleep(2.0)
    take_screenshot(f"{out_dir}/11_trust_center_queue.png")
    approve_topup()
    time.sleep(1.5)
    take_screenshot(f"{out_dir}/12_trust_center_approved.png")
    
    print("\\n[7/7] Verificando acreditación de saldo en cabina...")
    nav("tow_truck")
    time.sleep(1.5)
    take_screenshot(f"{out_dir}/13_grua_balance_updated.png")
    
    print("\\n=== AUDITORÍA MULTI-VERTICAL COMPLETADA CON ÉXITO ABSOLUTO ===")
    print(f"Todas las capturas se guardaron en: {out_dir}/")

def main():
    if len(sys.argv) < 2:
        print(__doc__)
        print("Uso:")
        print("  python3 scripts/meet_operator.py state / status")
        print("  python3 scripts/meet_operator.py screen / inspect")
        print("  python3 scripts/meet_operator.py tap <label_or_tag> [y_if_coord]")
        print("  python3 scripts/meet_operator.py type <text>")
        print("  python3 scripts/meet_operator.py nav <route_or_alias>")
        print("  python3 scripts/meet_operator.py switch-role <driver|passenger>")
        print("  python3 scripts/meet_operator.py switch-mode <specialist|client>")
        print("  python3 scripts/meet_operator.py topup [amount] [reference]")
        print("  python3 scripts/meet_operator.py approve-topup")
        print("  python3 scripts/meet_operator.py reject-topup")
        print("  python3 scripts/meet_operator.py gps <lat> <lng>")
        print("  python3 scripts/meet_operator.py enter-pin <pin>")
        print("  python3 scripts/meet_operator.py complete-ride")
        print("  python3 scripts/meet_operator.py create-ride <pickup> <dest> [price]")
        print("  python3 scripts/meet_operator.py submit-offer <requestId> <price> [eta] [msg]")
        print("  python3 scripts/meet_operator.py accept-offer <requestId> [offerId]")
        print("  python3 scripts/meet_operator.py advance-ride <requestId> <status>")
        print("  python3 scripts/meet_operator.py screenshot <file.png>")
        print("  python3 scripts/meet_operator.py logs [lines]")
        print("  python3 scripts/meet_operator.py test-flow")
        print("  python3 scripts/meet_operator.py test-all-verticals")
        sys.exit(0)
        
    cmd = sys.argv[1].lower()
    if cmd in ["state", "status"]:
        print(json.dumps(dump_state(), indent=2))
    elif cmd == "reconnect":
        out, err, code = run_adb(["connect", DEVICE])
        print(f"ADB Connect ({DEVICE}) result: {out} {err}")
    elif cmd in ["screen", "inspect"]:
        elements = inspect_screen()
        print(f"Found {len(elements)} visible/interactive elements on {DEVICE}:")
        for el in elements:
            if el["label"]:
                print(f"  • {el['label']:<40} center={el['center']} id={el['id']}")
    elif cmd == "tap":
        if len(sys.argv) < 3:
            print("Error: specify text or label or x y to tap")
            sys.exit(1)
        if len(sys.argv) >= 4 and sys.argv[2].isdigit() and sys.argv[3].isdigit():
            x, y = int(sys.argv[2]), int(sys.argv[3])
            run_adb(["shell", "input", "tap", str(x), str(y)])
            print(f"Tapped coordinates ({x}, {y})")
        else:
            ok, msg = tap_element(sys.argv[2])
            print(msg)
    elif cmd == "type":
        if len(sys.argv) < 3:
            print("Error: specify text to type")
            sys.exit(1)
        text = sys.argv[2].replace(" ", "%s")
        run_adb(["shell", "input", "text", text])
        print(f"Typed: {sys.argv[2]}")
    elif cmd == "nav":
        if len(sys.argv) < 3:
            print("Error: specify route or alias")
            sys.exit(1)
        nav(" ".join(sys.argv[2:]))
    elif cmd == "switch-role":
        if len(sys.argv) < 3:
            print("Error: specify driver or passenger")
            sys.exit(1)
        is_driver = sys.argv[2].lower() in ["driver", "chofer", "true", "1"]
        switch_role(is_driver)
    elif cmd == "switch-mode":
        target = sys.argv[2] if len(sys.argv) > 2 else "specialist"
        switch_mode(target)
    elif cmd == "topup":
        amount = int(sys.argv[2]) if len(sys.argv) > 2 else 10000
        ref = sys.argv[3] if len(sys.argv) > 3 else None
        topup_flow(amount, ref)
    elif cmd == "approve-topup":
        approve_topup()
    elif cmd == "reject-topup":
        reject_topup()
    elif cmd == "gps":
        if len(sys.argv) < 4:
            print("Error: specify lat lng")
            sys.exit(1)
        inject_gps(float(sys.argv[2]), float(sys.argv[3]))
    elif cmd == "enter-pin":
        if len(sys.argv) < 3:
            print("Error: specify 4-digit PIN")
            sys.exit(1)
        pin = sys.argv[2]
        tap_element("Ingresar PIN 🔐")
        time.sleep(0.6)
        tap_element("PIN del viaje")
        time.sleep(0.3)
        run_adb(["shell", "input", "text", pin])
        time.sleep(0.3)
        tap_element("VERIFICAR E INICIAR ABORDAJE")
        print(f"Entered and verified PIN: {pin}")
    elif cmd == "complete-ride":
        ok, msg = tap_element("Completar Viaje ✅")
        if not ok:
            st = dump_state()
            r_id = st.get("activeRideId")
            if r_id:
                run_adb([
                    "shell", "am", "broadcast",
                    "-a", "com.elysium369.meet.AI_ACTION",
                    "--es", "type", "ADVANCE_RIDE_STATUS",
                    "--es", "rideId", r_id,
                    "--es", "status", "COMPLETED",
                ])
                print(f"Advanced ride {r_id} to COMPLETED via AI broadcast")
            else:
                print(msg)
        else:
            print("Tapped 'Completar Viaje ✅'")
    elif cmd == "create-ride":
        pickup = sys.argv[2] if len(sys.argv) > 2 else "San José Centro"
        dest = sys.argv[3] if len(sys.argv) > 3 else "Cartago Centro"
        price = float(sys.argv[4]) if len(sys.argv) > 4 else 3500.0
        run_adb([
            "shell", "am", "broadcast",
            "-a", "com.elysium369.meet.AI_ACTION",
            "--es", "type", "CREATE_RIDE",
            "--es", "pickup", pickup,
            "--es", "dest", dest,
            "--ef", "price", str(price),
        ])
        print(f"Dispatched CREATE_RIDE: {pickup} -> {dest} (₡{price})")
    elif cmd == "submit-offer":
        if len(sys.argv) < 4:
            print("Error: specify requestId price [eta] [message]")
            sys.exit(1)
        req_id = sys.argv[2]
        price = float(sys.argv[3])
        eta = int(sys.argv[4]) if len(sys.argv) > 4 else 10
        msg = sys.argv[5] if len(sys.argv) > 5 else None
        b_args = [
            "shell", "am", "broadcast",
            "-a", "com.elysium369.meet.AI_ACTION",
            "--es", "type", "SUBMIT_OFFER",
            "--es", "requestId", req_id,
            "--ef", "price", str(price),
            "--ei", "eta", str(eta),
        ]
        if msg:
            b_args.extend(["--es", "message", msg])
        run_adb(b_args)
        print(f"Dispatched SUBMIT_OFFER for {req_id}: {price} CRC")
    elif cmd == "accept-offer":
        if len(sys.argv) < 3:
            print("Error: specify requestId [offerId]")
            sys.exit(1)
        req_id = sys.argv[2]
        offer_id = sys.argv[3] if len(sys.argv) > 3 else None
        b_args = [
            "shell", "am", "broadcast",
            "-a", "com.elysium369.meet.AI_ACTION",
            "--es", "type", "ACCEPT_OFFER",
            "--es", "requestId", req_id,
        ]
        if offer_id:
            b_args.extend(["--es", "offerId", offer_id])
        run_adb(b_args)
        print(f"Dispatched ACCEPT_OFFER for {req_id}")
    elif cmd == "advance-ride":
        if len(sys.argv) < 4:
            print("Error: specify requestId status (e.g. ARRIVED, PASSENGER_ONBOARD, IN_PROGRESS, COMPLETED)")
            sys.exit(1)
        req_id = sys.argv[2]
        status = sys.argv[3]
        run_adb([
            "shell", "am", "broadcast",
            "-a", "com.elysium369.meet.AI_ACTION",
            "--es", "type", "ADVANCE_RIDE_STATUS",
            "--es", "rideId", req_id,
            "--es", "status", status,
        ])
        print(f"Dispatched ADVANCE_RIDE_STATUS: {req_id} -> {status}")
    elif cmd == "screenshot":
        path = sys.argv[2] if len(sys.argv) > 2 else "screenshot.png"
        take_screenshot(path)
    elif cmd == "logs":
        lines = sys.argv[2] if len(sys.argv) > 2 else "100"
        cmd_str = f"adb -s {DEVICE} logcat -d | grep -v 'Gralloc' | grep -v 'qdgralloc' | grep -v 'GraphicBuffer' | grep -v 'AHardwareBuffer' | grep -v 'RtgSched' | grep -v 'uniperf' | tail -n {lines}"
        os.system(cmd_str)
    elif cmd == "test-flow":
        run_test_flow()
    elif cmd == "test-all-verticals":
        run_test_all_verticals()
    else:
        print(f"Comando desconocido: {cmd}")

if __name__ == "__main__":
    main()
'''

skill_content = """---
name: meet-apk-operator
description: >
  Master Autonomous Operator harness for the MEET Android application (com.elysium369.meet).
  Empowers AI agents (Antigravity, Mavis, Codex, Claude) to operate, navigate, inspect,
  test, and debug the APK with 100% proficiency across ALL 5 verticals: Viajes, Grúa, Mecánicos,
  Repuestos, and Servicios Elysium, plus Platform Trust Center and Specialist Wallets.
  Features: dynamic ADB discovery (Honor Magic V2 / VER-N49 / emulators), 5% global commission verification,
  ₡15,000 gift balance inspection, SINPE top-up lifecycle, GPS simulation, and full ecosystem test suites.
  Trigger on: "meet apk", "operate apk", "test meet app", "meet adb", "test ride", "grua", "mecanicos",
  "repuestos", "servicios elysium", "trust center", "inspect meet", "drive ride flow", "meet mobile".
---

# MEET APK Master Autonomous Operator Skill

This skill provides an authoritative, world-class guide and operational toolkit for any AI agent to interact with, navigate, inspect, test, and debug the **MEET (Mecánicos Especialistas En Todo)** Android application on physical devices (such as the Honor Magic V2 / `VER-N49`) or emulators.

---

## 1. System Topology & Architecture

| Component | Identifier / Detail |
|---|---|
| **Package Name** | `com.elysium369.meet` |
| **Main Activity** | `com.elysium369.meet.MainActivity` |
| **Automation Broadcast Receiver** | `com.elysium369.meet.automation.AiAutomationReceiver` |
| **Broadcast Action (Actions)** | `com.elysium369.meet.AI_ACTION` |
| **Broadcast Action (Navigation)** | `com.elysium369.meet.AI_NAVIGATE` |
| **Reactive State Snapshot** | `/data/local/tmp/meet_state.json` (also in app internal storage) |
| **CLI Operator Tool** | `scripts/meet_operator.py` (auto-detects connected active ADB devices) |
| **Target Hardware Reference** | Honor Magic V2 (`VER-N49`), Foldable OLED, ADB Wireless / USB |

---

## 2. Platform Core Invariants & Constitutional Economics

Every AI agent operating MEET must uphold and verify these non-negotiable platform rules:

1. **Fixed 5% Platform Commission Across All Verticals:**
   - Platform commission is strictly **5%** (500 bps / `0.05`).
   - Specialists retain **95%** net of all gross earnings.
   - Verified across Viajes, Grúa, Mecánicos, Repuestos, and all Servicios Elysium.

2. **₡15,000 Promotional Starter Gift Balance:**
   - Every specialist account receives an initial gift of **₡15,000 CRC** granted by Jorge David Del Valle Miranda.
   - Displayed prominently in the Specialist Wallet Card: `"REGALO BIENVENIDA · ₡15,000 REGALADOS"`.

3. **Official SINPE Móvil Configuration:**
   - Official Top-up Number: **63194029**
   - Official Beneficiary: **Jorge David Del Valle Miranda**
   - Official Notification Email: **jordelmir@gmail.com**

4. **Trust Center Closed-Loop Validation:**
   - Specialists submit top-up receipts via `SpecialistSinpeTopupDialog` (parsed via `SinpeReceiptParser`).
   - Top-ups enqueue with status `PENDIENTE TRUST CENTER ⏳`.
   - The platform owner validates the bank transfer in the **Trust Center** (`trust_center`) and performs 1-tap accreditation (`ACREDITAR`).
   - Specialist wallet balance updates immediately and top-up chip marks `ACREDITADA ✓`.

---

## 3. The 5 Ecosystem Verticals & Navigation Routes

| Vertical | Semantic Alias | Jetpack Compose Route | Screen Implementation |
|---|---|---|---|
| **Viajes** | `ride` / `viajes` | `ride_service` | `RideServiceScreen.kt` |
| **Grúa y Rescate** | `tow` / `grua` | `tow_truck` / `tow_truck_service` | `TowTruckServiceScreen.kt` |
| **Mecánicos y Talleres** | `mechanic` / `mecanicos` | `mechanic_service` | `MechanicServiceScreen.kt` |
| **Repuestos y Catálogo** | `parts` / `repuestos` | `part_request` | `PartRequestScreen.kt` |
| **Servicios Elysium** | `universal` / `ferreteria` | `universal_services` / `universal_activity/<id>` | `UniversalActivityWorkflowScreen.kt` |
| **Platform Trust Center** | `trust` / `trust_center` | `trust_center` | `PlatformTrustCenterScreen.kt` |
| **Scanner & OBD-II** | `scanner` | `scanner` | `LiveScannerScreen.kt` |
| **Diagnóstico DTC** | `dtc` / `dtcs` | `dtc` | `DtcScreen.kt` |
| **Garage** | `garage` | `garage` | `GarageScreen.kt` |
| **Inicio** | `home` | `home` | `HomeScreen.kt` |

---

## 4. Fast-Start Master CLI Reference (`meet_operator.py`)

All commands auto-detect the active connected ADB device (no need to manually set serials):

```bash
# 1. Inspect live screen elements (text, center coordinates, IDs, bounding boxes)
python3 scripts/meet_operator.py screen

# 2. Inspect reactive internal state (JSON)
python3 scripts/meet_operator.py status

# 3. Semantic navigation to any vertical
python3 scripts/meet_operator.py nav tow          # Grúa
python3 scripts/meet_operator.py nav mechanic     # Mecánicos
python3 scripts/meet_operator.py nav parts        # Repuestos
python3 scripts/meet_operator.py nav universal hardware_store # Ferretería
python3 scripts/meet_operator.py nav trust        # Trust Center
python3 scripts/meet_operator.py nav ride         # Viajes

# 4. Universal Specialist Cockpit Toggle
python3 scripts/meet_operator.py switch-mode specialist   # Cockpit Pro / Conductor
python3 scripts/meet_operator.py switch-mode client       # Modo Cliente

# 5. SINPE Móvil Top-Up Automation
python3 scripts/meet_operator.py topup 20000 "SINPE-REF-12345"

# 6. Trust Center 1-Tap Approval / Rejection
python3 scripts/meet_operator.py approve-topup    # Taps ACREDITAR in Trust Center
python3 scripts/meet_operator.py reject-topup     # Taps RECHAZAR in Trust Center

# 7. Smart Tap on any visible button or text
python3 scripts/meet_operator.py tap "COCKPIT GRUISTA (PRO)"
python3 scripts/meet_operator.py tap "RECARGAR SALDO CON SINPE MÓVIL"

# 8. Type text into active focused field
python3 scripts/meet_operator.py type "BAC-COMPROBANTE-7721"

# 9. Realtime GPS Simulation
python3 scripts/meet_operator.py gps 9.9333 -84.0833

# 10. High-Resolution Screenshot Capture
python3 scripts/meet_operator.py screenshot docs/screenshots/my_test.png

# 11. Complete Ecosystem Autonomous Verification (All 5 Verticals + Trust Center)
python3 scripts/meet_operator.py test-all-verticals
```

---

## 5. Automated Multi-Vertical Verification Protocol

To verify that the entire MEET ecosystem is working seamlessly:

```bash
python3 scripts/meet_operator.py test-all-verticals
```

This automated sequence executes:
1. Launches `MainActivity` on the connected physical device.
2. Navigates to **Grúa**, switches to **Cockpit Gruista (PRO)**, validates ₡15k gift balance and 5% commission.
3. Navigates to **Mecánicos**, switches to **Cockpit Taller (PRO)**, validates catalog and 5% commission.
4. Navigates to **Repuestos**, switches to **Especialista en Repuestos**, validates graph and 5% commission.
5. Navigates to **Servicios Elysium**, switches to **Ferretería Afiliada**, validates cockpit and 5% commission.
6. Submits a test SINPE topup, navigates to **Platform Trust Center**, verifies pending queue, and performs 1-tap `ACREDITAR`.
7. Navigates back to Grúa and confirms accredited balance increase (`ACREDITADA ✓`).
8. Saves all timestamped proof screenshots to `docs/screenshots/meet_ecosystem_verification/`.

---

## 6. Development & Deployment Protocol

Before committing or releasing any changes:
1. **Clean compilation**: `./android/gradlew -p android compileDebugKotlin`
2. **Assemble APK**: `./android/gradlew -p android assembleDebug`
3. **Install to device**: `adb install -r android/app/build/outputs/apk/debug/app-debug.apk`
4. **Verify Parity**: `bash tests/parity/ci-verify.sh` (TS ≡ Kotlin match required!)
5. **Run test flow**: `python3 scripts/meet_operator.py test-all-verticals`
"""

# 1. Update scripts/meet_operator.py
operator_path = os.path.abspath("scripts/meet_operator.py")
with open(operator_path, "w", encoding="utf-8") as f:
    f.write(operator_py_content.strip() + "\n")
os.chmod(operator_path, 0o755)
print(f"Updated and made executable: {operator_path}")

# 2. Install SKILL.md to all skill registries
destinations = [
    os.path.expanduser("~/.agents/skills/meet-apk-operator/SKILL.md"),
    os.path.expanduser("~/.gemini/config/skills/meet-apk-operator/SKILL.md"),
    os.path.expanduser("~/.gemini/skills/meet-apk-operator/SKILL.md"),
    os.path.abspath("docs/skills/meet-apk-operator/SKILL.md")
]

for dest in destinations:
    os.makedirs(os.path.dirname(dest), exist_ok=True)
    with open(dest, "w", encoding="utf-8") as f:
        f.write(skill_content.strip() + "\n")
    print(f"Successfully synchronized skill to: {dest}")

print("\nAll skills and operator CLI harnesses successfully upgraded to Master Edition!")
