#!/usr/bin/env /usr/bin/python3
"""
MEET Wireless ADB Bridge & Connector for macOS
Bypasses macOS Sequoia Local Network Privacy (Errno 65 / EHOSTUNREACH)
by routing localhost:5555 -> Phone_WiFi_IP:5555 via Apple-signed Python runtime.
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

CACHE_FILE = os.path.expanduser("~/.meet_wireless_adb_ip")
DEFAULT_PORT = 5555
PID_FILE = "/tmp/meet_adb_bridge.pid"

def run_cmd(cmd):
    try:
        res = subprocess.run(cmd, shell=True, capture_output=True, text=True, timeout=10)
        return res.stdout.strip()
    except Exception:
        return ""

def is_port_open(host, port, timeout=1.0):
    s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    s.settimeout(timeout)
    try:
        s.connect((host, port))
        s.close()
        return True
    except Exception:
        return False

def get_cached_ip():
    if os.path.exists(CACHE_FILE):
        try:
            with open(CACHE_FILE, "r") as f:
                ip = f.read().strip()
                if ip:
                    return ip
        except Exception:
            pass
    return "192.168.1.15"

def save_cached_ip(ip):
    try:
        with open(CACHE_FILE, "w") as f:
            f.write(ip.strip())
    except Exception:
        pass

def is_wireless_connected():
    state = run_cmd("adb -s 127.0.0.1:5555 get-state")
    return state == "device"

def get_usb_serials():
    output = run_cmd("adb devices -l")
    serials = []
    for line in output.splitlines():
        if "usb:" in line and "device" in line:
            parts = line.split()
            if parts:
                serials.append(parts[0])
    return serials

def get_ip_from_usb(serial):
    ip_out = run_cmd(f"adb -s {serial} shell ip -f inet addr show wlan0")
    match = re.search(r"inet\s+([0-9]+\.[0-9]+\.[0-9]+\.[0-9]+)", ip_out)
    if match:
        ip = match.group(1)
        save_cached_ip(ip)
        return ip
    return None

def scan_subnet_for_adb(subnet_prefix="192.168.1.", port=DEFAULT_PORT):
    print(f"[*] Scanning {subnet_prefix}1-254 for ADB port {port}...")
    found_ip = None
    lock = threading.Lock()

    def probe(host):
        nonlocal found_ip
        if found_ip:
            return
        if is_port_open(host, port, timeout=0.4):
            with lock:
                if not found_ip:
                    found_ip = host

    threads = []
    for i in range(1, 255):
        ip = f"{subnet_prefix}{i}"
        t = threading.Thread(target=probe, args=(ip,))
        threads.append(t)
        t.start()

    for t in threads:
        t.join(timeout=1.0)

    return found_ip

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

def run_bridge_server(remote_host, remote_port, local_port=DEFAULT_PORT):
    server = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    server.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    server.bind(("127.0.0.1", local_port))
    server.listen(10)
    print(f"[+] Bridge active: 127.0.0.1:{local_port} -> {remote_host}:{remote_port}")
    
    with open(PID_FILE, "w") as f:
        f.write(str(os.getpid()))

    try:
        while True:
            client_sock, _ = server.accept()
            threading.Thread(target=bridge_worker, args=(client_sock, remote_host, remote_port), daemon=True).start()
    except KeyboardInterrupt:
        pass
    finally:
        server.close()
        if os.path.exists(PID_FILE):
            os.remove(PID_FILE)

def stop_bridge():
    if os.path.exists(PID_FILE):
        try:
            with open(PID_FILE, "r") as f:
                pid = int(f.read().strip())
            os.kill(pid, 9)
            os.remove(PID_FILE)
            print(f"[+] Stopped bridge process (PID {pid}).")
        except Exception as e:
            print(f"[-] Error stopping PID: {e}")
    else:
        # Check lsof
        pids = run_cmd("lsof -ti :5555").split()
        for p in pids:
            try:
                os.kill(int(p), 9)
            except:
                pass
        print("[*] Cleaned up port 5555 listeners.")

def main():
    parser = argparse.ArgumentParser(description="MEET Wireless ADB Connector")
    parser.add_argument("--server", action="store_true", help="Run the bridge server in foreground")
    parser.add_argument("--ip", type=str, default="", help="Override target phone IP")
    parser.add_argument("--stop", action="store_true", help="Stop running bridge daemon")
    parser.add_argument("--restart", action="store_true", help="Restart bridge and reconnect")
    args = parser.parse_args()

    if args.stop:
        stop_bridge()
        run_cmd("adb disconnect 127.0.0.1:5555")
        return

    if args.restart:
        stop_bridge()
        run_cmd("adb disconnect 127.0.0.1:5555")
        time.sleep(0.5)

    # 1. Quick check: Is wireless ADB already functioning perfectly?
    if not args.restart and is_wireless_connected():
        print("[+] Wireless ADB is already connected and operational!")
        devices = run_cmd("adb devices -l")
        print("\n--- Current ADB Devices ---")
        print(devices)
        print("---------------------------\n")
        return

    # 2. Find target IP
    target_ip = args.ip
    usb_serials = get_usb_serials()

    if not target_ip and usb_serials:
        target_ip = get_ip_from_usb(usb_serials[0])
        if target_ip:
            print(f"[+] Phone WiFi IP detected via USB: {target_ip}")

    if not target_ip:
        cached = get_cached_ip()
        if is_port_open(cached, DEFAULT_PORT, timeout=0.8):
            target_ip = cached
            print(f"[+] Phone listening at cached IP: {target_ip}")

    if not target_ip:
        target_ip = scan_subnet_for_adb()
        if target_ip:
            print(f"[+] Discovered phone IP via scan: {target_ip}")
            save_cached_ip(target_ip)

    # If port 5555 is not open on phone, but USB is connected, enable it:
    if target_ip and not is_port_open(target_ip, DEFAULT_PORT, timeout=0.8) and usb_serials:
        print(f"[*] Port {DEFAULT_PORT} not open on {target_ip}. Enabling tcpip via USB...")
        run_cmd(f"adb -s {usb_serials[0]} tcpip {DEFAULT_PORT}")
        time.sleep(1)

    if not target_ip:
        print("[-] Could not find phone with ADB port 5555 open.")
        print("[-] Please connect phone via USB once or provide IP with --ip <ip>")
        sys.exit(1)

    if args.server:
        run_bridge_server(target_ip, DEFAULT_PORT)
        return

    # 3. Ensure local bridge daemon is running
    if not is_port_open("127.0.0.1", DEFAULT_PORT, timeout=0.5):
        print(f"[*] Starting wireless bridge (127.0.0.1:5555 -> {target_ip}:5555)...")
        cmd = f"/usr/bin/python3 \"{os.path.abspath(__file__)}\" --server --ip {target_ip} > /tmp/meet_adb_bridge.log 2>&1 &"
        os.system(cmd)
        time.sleep(1)

    # 4. Connect ADB to 127.0.0.1:5555
    print("[*] Connecting adb to 127.0.0.1:5555...")
    out = run_cmd("adb connect 127.0.0.1:5555")
    print(out)
    time.sleep(0.5)

    devices = run_cmd("adb devices -l")
    print("\n--- Current ADB Devices ---")
    print(devices)
    print("---------------------------\n")

    if is_wireless_connected():
        print(" SUCCESS: Wireless debugging is READY! You can now unplug USB-C.")
    else:
        print(" Note: If status is 'offline', try running with --restart")

if __name__ == "__main__":
    main()
