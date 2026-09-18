#!/usr/bin/env /usr/bin/python3
"""
MEET Permanent Wireless ADB Auto-Connect Daemon for macOS
Automatically discovers Android device via mDNS / Zeroconf (_adb-tls-connect._tcp)
or TCP port 5555 whenever it is on the same Wi-Fi, creates a local bridge
bypassing macOS Sequoia Local Network Privacy, and connects ADB automatically.
"""

import sys
import os
import time
import socket
import select
import threading
import subprocess
import re

LOG_FILE = os.path.expanduser("~/.meet_adb_daemon.log")
PID_FILE = "/tmp/meet_adb_auto_daemon.pid"

def log(msg):
    ts = time.strftime("%Y-%m-%d %H:%M:%S")
    line = f"[{ts}] {msg}"
    print(line, flush=True)
    try:
        with open(LOG_FILE, "a") as f:
            f.write(line + "\n")
    except Exception:
        pass

def notify_user(title, message):
    try:
        apple_script = f'display notification "{message}" with title "{title}" sound name "Glass"'
        subprocess.run(["/usr/bin/osascript", "-e", apple_script], capture_output=True)
    except Exception:
        pass

def is_port_open(host, port, timeout=0.8):
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
        try:
            src.close()
        except:
            pass
        try:
            dst.close()
        except:
            pass

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

active_bridge_servers = {}

def start_local_bridge(remote_host, remote_port, local_port):
    if local_port in active_bridge_servers:
        return
    if is_port_open("127.0.0.1", local_port, timeout=0.3):
        return
    try:
        server = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        server.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        server.bind(("127.0.0.1", local_port))
        server.listen(10)
        active_bridge_servers[local_port] = server
        log(f"[+] Local bridge active: 127.0.0.1:{local_port} -> {remote_host}:{remote_port}")

        def listen_loop():
            while local_port in active_bridge_servers:
                try:
                    csock, _ = server.accept()
                    threading.Thread(target=bridge_worker, args=(csock, remote_host, remote_port), daemon=True).start()
                except Exception:
                    break

        threading.Thread(target=listen_loop, daemon=True).start()
    except Exception as e:
        log(f"[-] Could not bind bridge on 127.0.0.1:{local_port}: {e}")

def get_connected_wireless_devices():
    try:
        res = subprocess.run(["adb", "devices"], capture_output=True, text=True, timeout=5)
        lines = res.stdout.splitlines()
        online = []
        for line in lines:
            if "127.0.0.1:" in line and "device" in line and "offline" not in line:
                endpoint = line.split()[0]
                online.append(endpoint)
        return online
    except Exception:
        return []

def discover_adb_mdns():
    try:
        proc = subprocess.Popen(
            ["/usr/bin/dns-sd", "-B", "_adb-tls-connect._tcp", "local."],
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True
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

def resolve_mdns_instance(instance):
    try:
        proc = subprocess.Popen(
            ["/usr/bin/dns-sd", "-L", instance, "_adb-tls-connect._tcp", "local."],
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True
        )
        time.sleep(1.2)
        proc.terminate()
        out, _ = proc.communicate()
        match = re.search(r"can be reached at ([^:]+):([0-9]+)", out)
        if match:
            host = match.group(1).rstrip(".")
            port = int(match.group(2))
            return host, port
    except Exception:
        pass
    return None, None

def main():
    log("=== MEET Wireless ADB Auto-Daemon Started ===")
    
    with open(PID_FILE, "w") as f:
        f.write(str(os.getpid()))

    last_notified_device = None

    while True:
        try:
            connected = get_connected_wireless_devices()

            # Discover all Android devices via mDNS
            instances = discover_adb_mdns()
            for inst in instances:
                host, port = resolve_mdns_instance(inst)
                if host and port:
                    endpoint = f"127.0.0.1:{port}"
                    if endpoint in connected:
                        continue
                    log(f"[*] Discovered Android Wireless Debugging on {host}:{port}")
                    # Start local bridge on that port
                    start_local_bridge(host, port, port)
                    time.sleep(0.3)
                    
                    # Connect via adb
                    log(f"[*] Executing adb connect {endpoint}...")
                    cres = subprocess.run(["adb", "connect", endpoint], capture_output=True, text=True, timeout=5)
                    log(f"[*] adb response: {cres.stdout.strip()}")
                    time.sleep(0.5)

                    updated_connected = get_connected_wireless_devices()
                    if endpoint in updated_connected:
                        connected.append(endpoint)
                        if last_notified_device != f"{host}:{port}":
                            notify_user("MEET Wireless ADB", f"Dispositivo conectado inalámbricamente: {host}:{port}")
                            last_notified_device = f"{host}:{port}"

            # Also check fallback port 5555 on Android-2.local, Android.local, or 192.168.1.10/15
            if "127.0.0.1:5555" not in connected:
                for candidate_host in ["Android.local", "Android-2.local", "192.168.1.10", "192.168.1.15"]:
                    if is_port_open(candidate_host, 5555, timeout=0.3):
                        log(f"[*] Port 5555 open on {candidate_host}")
                        start_local_bridge(candidate_host, 5555, 5555)
                        time.sleep(0.3)
                        cres = subprocess.run(["adb", "connect", "127.0.0.1:5555"], capture_output=True, text=True, timeout=5)
                        log(f"[*] adb response (5555): {cres.stdout.strip()}")
                        time.sleep(0.5)
                        if "127.0.0.1:5555" in get_connected_wireless_devices():
                            if last_notified_device != f"{candidate_host}:5555":
                                notify_user("MEET Wireless ADB", f"Dispositivo conectado inalámbricamente (puerto 5555): {candidate_host}")
                                last_notified_device = f"{candidate_host}:5555"
                            break

        except Exception as e:
            log(f"[-] Daemon loop error: {e}")

        time.sleep(3)

if __name__ == "__main__":
    main()
