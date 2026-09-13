#!/usr/bin/env bash
# MEET Wireless ADB Auto-Connect Service Manager for macOS
set -e

PLIST_NAME="com.meet.adb.wireless.plist"
PLIST_PATH="$HOME/Library/LaunchAgents/$PLIST_NAME"
INSTALL_DIR="$HOME/.local/bin"
SCRIPT_SRC="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/meet_adb_auto_daemon.py"
SCRIPT_DEST="$INSTALL_DIR/meet_adb_auto_daemon.py"
LOG_FILE="$HOME/.meet_adb_daemon.log"

case "$1" in
    install|start)
        echo "[*] Syncing daemon script to $SCRIPT_DEST..."
        mkdir -p "$INSTALL_DIR"
        cp "$SCRIPT_SRC" "$SCRIPT_DEST"
        chmod +x "$SCRIPT_DEST"

        echo "[*] Creating LaunchAgent in $PLIST_PATH..."
        mkdir -p "$HOME/Library/LaunchAgents"
        cat << PLIST_EOF > "$PLIST_PATH"
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>Label</key>
    <string>com.meet.adb.wireless</string>
    <key>ProgramArguments</key>
    <array>
        <string>/usr/bin/python3</string>
        <string>$SCRIPT_DEST</string>
    </array>
    <key>RunAtLoad</key>
    <true/>
    <key>KeepAlive</key>
    <true/>
    <key>StandardOutPath</key>
    <string>$LOG_FILE</string>
    <key>StandardErrorPath</key>
    <string>$LOG_FILE</string>
    <key>EnvironmentVariables</key>
    <dict>
        <key>PATH</key>
        <string>/opt/homebrew/bin:/usr/local/bin:/usr/bin:/bin:/usr/sbin:/sbin</string>
    </dict>
</dict>
</plist>
PLIST_EOF
        echo "[*] Loading service with launchctl..."
        launchctl unload "$PLIST_PATH" 2>/dev/null || true
        launchctl load -w "$PLIST_PATH"
        echo "[+] Service installed and started permanently! It will auto-run on login."
        ;;
    stop|uninstall)
        echo "[*] Stopping service..."
        launchctl unload "$PLIST_PATH" 2>/dev/null || true
        rm -f "$PLIST_PATH"
        pkill -f "meet_adb_auto_daemon.py" 2>/dev/null || true
        echo "[+] Service stopped and uninstalled."
        ;;
    status)
        echo "=== LaunchAgent Status ==="
        launchctl list | grep "com.meet.adb.wireless" || echo "Service not running in launchctl"
        echo ""
        echo "=== Connected ADB Devices ==="
        adb devices -l
        ;;
    logs)
        tail -n 30 -f "$LOG_FILE"
        ;;
    *)
        echo "Usage: $0 {start|stop|status|logs}"
        exit 1
        ;;
esac
