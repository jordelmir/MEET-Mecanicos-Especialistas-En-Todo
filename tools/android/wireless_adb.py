#!/usr/bin/env /usr/bin/python3
"""
MEET Wireless ADB Bridge & Connector for macOS
Bypasses macOS Sequoia Local Network Privacy (Errno 65 / EHOSTUNREACH)
by routing localhost:PORT -> Phone_WiFi_IP:PORT via Apple-signed Python runtime.
Supports auto-discovery for:
- Honor Magic V2 (VER-N49 / Android.local)
- Xiaomi Redmi Note 10 Pro (M2101K6R / Android-2.local)
"""

import sys
import os
import time
import socket
import select
import threading
import subprocess
import re
import argparse

DEFAULT_PORT = 5555

def run_cmd(cmd):
    try:
        res = subprocess.run(cmd, shell=True, capture_output=True, text=True, timeout=10)
        return res.stdout.strip()
    except Exception:
        return ""

def is_port_open(host, port, timeout=0.6):
    s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    s.settimeout(timeout)
    try:
        s.connect((host, port))
        s.close()
        return True
    except Exception:
        return False

def forward(src, dst):
    try:
        while True:
            r, _, _ = select.select([src], [], [], 60)
            if not r:
                continue
            data = src.recv(65536)
            if not data:
                break
            dst.sendall(data)
    except Exception:
        pass
    finally:
        try: src.close()
        except: pass
        try: dst.close()
        except: pass

def bridge_worker(client_sock, remote_host, remote_port):
    try:
        remote_sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        remote_sock.connect((remote_host, remote_port))
        t1 = threading.Thread(target=forward, args=(client_sock, remote_sock), daemon=True)
        t2 = threading.Thread(target=forward, args=(remote_sock, client_sock), daemon=True)
        t1.start()
        t2.start()
    except Exception:
        client_sock.close()

def start_local_bridge(remote_host, remote_port, local_port):
    if is_port_open("127.0.0.1", local_port, timeout=0.2):
        return
    
    server = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    server.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    server.bind(("127.0.0.1", local_port))
    server.listen(10)
    print(f"[+] Bridge active: 127.0.0.1:{local_port} -> {remote_host}:{remote_port}")

    def loop():
        while True:
            try:
                csock, _ = server.accept()
                threading.Thread(target=bridge_worker, args=(csock, remote_host, remote_port), daemon=True).start()
            except Exception:
                break

    threading.Thread(target=loop, daemon=True).start()

def discover_mdns_instances():
    try:
        proc = subprocess.Popen(
            ["/usr/bin/dns-sd", "-B", "_adb-tls-connect._tcp", "local."],
            stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True
        )
        time.sleep(1.2)
        proc.terminate()
        out, _ = proc.communicate()
        instances = []
        for line in out.splitlines():
            if "Add" in line and "_adb-tls-connect._tcp." in line:
                parts = line.split()
                instances.append(parts[-1])
        return list(set(instances))
    except Exception:
        return []

def resolve_instance(instance):
    try:
        proc = subprocess.Popen(
            ["/usr/bin/dns-sd", "-L", instance, "_adb-tls-connect._tcp", "local."],
            stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True
        )
        time.sleep(1.2)
        proc.terminate()
        out, _ = proc.communicate()
        match = re.search(r"can be reached at ([^:]+):([0-9]+)", out)
        if match:
            host = match.group(1).rstrip(".")
            port = int(match.group(2))
            model_match = re.search(r"name=([^\s]+)", out)
            model = model_match.group(1) if model_match else "Android"
            return host, port, model
    except Exception:
        pass
    return None, None, None

def get_connected_devices():
    output = run_cmd("adb devices -l")
    devices = []
    for line in output.splitlines():
        if "device" in line and "offline" not in line and not line.startswith("List of"):
            parts = line.split()
            if parts:
                devices.append(parts[0])
    return devices

def print_device_table():
    output = run_cmd("adb devices -l")
    print("\n==================== ACTIVE ADB DEVICES ====================")
    for line in output.splitlines():
        if not line.strip() or line.startswith("List of"):
            continue
        serial = line.split()[0]
        label = "Android Device"
        if "VER-N49" in line or "HNVER" in line:
            label = "HONOR MAGIC V2 (VER-N49) [FOLDABLE OLED]"
        elif "M2101K6R" in line or "sweet" in line:
            label = "XIAOMI REDMI NOTE 10 PRO (M2101K6R)"
        print(f" • {serial:20} -> {label} | {line}")
    print("============================================================\n")

def connect_all(target_filter="all"):
    print(f"[*] Discovering Android devices on local Wi-Fi (Filter: {target_filter})...")
    instances = discover_mdns_instances()
    connected = get_connected_devices()
    found_targets = []

    for inst in instances:
        host, port, model = resolve_instance(inst)
        if not host or not port:
            continue
        
        is_honor = "VER-N49" in model or "Android.local" in host or "A2VQ" in inst
        is_xiaomi = "M2101" in model or "sweet" in model or "Android-2.local" in host

        if target_filter == "honor" and not is_honor:
            continue
        if target_filter == "xiaomi" and not is_xiaomi:
            continue

        endpoint = f"127.0.0.1:{port}"
        found_targets.append({
            "host": host,
            "port": port,
            "model": model,
            "endpoint": endpoint,
            "label": "Honor Magic V2" if is_honor else ("Xiaomi" if is_xiaomi else model)
        })

    if not found_targets:
        candidates = []
        if target_filter in ("all", "honor"):
            candidates.append(("Android.local", 5555, "Honor Magic V2"))
            candidates.append(("192.168.1.10", 5555, "Honor Magic V2"))
        if target_filter in ("all", "xiaomi"):
            candidates.append(("Android-2.local", 5555, "Xiaomi"))
            candidates.append(("192.168.1.100", 5555, "Xiaomi"))
            candidates.append(("192.168.1.15", 5555, "Xiaomi"))

        for host, port, label in candidates:
            if is_port_open(host, port, timeout=0.3):
                found_targets.append({
                    "host": host,
                    "port": port,
                    "model": label,
                    "endpoint": f"127.0.0.1:{port}",
                    "label": label
                })
                break

    for target in found_targets:
        endpoint = target["endpoint"]
        label = target["label"]
        if endpoint in connected:
            print(f"[✓] {label} ({endpoint}) is already connected!")
            continue

        print(f"[*] Starting bridge for {label} (127.0.0.1:{target['port']} -> {target['host']}:{target['port']})...")
        start_local_bridge(target["host"], target["port"], target["port"])
        time.sleep(0.3)
        res = run_cmd(f"adb connect {endpoint}")
        print(f"[*] adb response: {res}")

    time.sleep(0.8)
    print_device_table()

def main():
    parser = argparse.ArgumentParser(description="MEET Wireless ADB Bridge & Connector")
    parser.add_argument("--honor", action="store_true", help="Connect specifically to Honor Magic V2")
    parser.add_argument("--xiaomi", action="store_true", help="Connect specifically to Xiaomi")
    parser.add_argument("--all", action="store_true", help="Connect all discovered devices")
    parser.add_argument("--status", action="store_true", help="Display connected device table")
    args = parser.parse_args()

    if args.status:
        print_device_table()
        return

    target = "all"
    if args.honor:
        target = "honor"
    elif args.xiaomi:
        target = "xiaomi"

    connect_all(target)

if __name__ == "__main__":
    main()
