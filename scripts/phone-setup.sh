#!/bin/bash
# Spyglass phone setup — run steps manually on each phone
# This is a REFERENCE SCRIPT, not meant to be run end-to-end automatically.

set -e

cat << 'INSTRUCTIONS'
=== Phone Setup (do on the phone screen) ===

1. Install from F-Droid:
   - Termux
   - Termux:API
   - Termux:Boot

2. Open Termux, run:
   pkg install openssh

3. Set password:
   passwd

4. Battery optimization → Unrestricted for:
   - Termux
   - Termux:API
   - Termux:Boot

5. Open Termux:Boot once (so Android registers it for autostart)

=== Then run from Mac (this script) ===
INSTRUCTIONS

echo ""
read -p "Which phone? (a3 / pixel-5): " PHONE

if [ "$PHONE" = "a3" ]; then
    HOST="a3"
    echo "A3: Termux already set up, just need Termux:Boot from F-Droid"
    echo "Boot script already at ~/.termux/boot/start-sshd.sh"
elif [ "$PHONE" = "pixel-5" ]; then
    HOST="pixel-5"
    echo ""
    echo "--- Copying SSH key ---"
    ssh-copy-id -p 8022 "$HOST"

    echo ""
    echo "--- Configuring sshd to Tailscale only ---"
    ssh -p 8022 "$HOST" bash << 'REMOTE'
# Get Tailscale IP
TS_IP=$(ip -4 addr show tailscale0 2>/dev/null | grep -oP '(?<=inet )\S+' | cut -d/ -f1)
if [ -z "$TS_IP" ]; then
    echo "ERROR: No Tailscale interface found. Is Tailscale running?"
    exit 1
fi
echo "Tailscale IP: $TS_IP"

# Configure sshd
mkdir -p ~/.ssh
cat > ~/.ssh/sshd_config << EOF
ListenAddress $TS_IP
Port 8022
PasswordAuthentication no
EOF

# Create boot script
mkdir -p ~/.termux/boot
cat > ~/.termux/boot/start-sshd.sh << 'BOOT'
#!/data/data/com.termux/files/usr/bin/sh
sshd
BOOT
chmod +x ~/.termux/boot/start-sshd.sh

# Restart sshd with new config
pkill sshd 2>/dev/null || true
sshd
echo "Done! sshd listening on $TS_IP:8022 only"
REMOTE

    echo ""
    echo "--- Adding to SSH config ---"
    if ! grep -q "Host pixel-5" ~/.ssh/config 2>/dev/null; then
        cat >> ~/.ssh/config << 'EOF'

Host pixel-5
    HostName pixel-5
    Port 8022
    ServerAliveInterval 60
    ServerAliveCountMax 3
EOF
        echo "Added pixel-5 to ~/.ssh/config"
    else
        echo "pixel-5 already in ~/.ssh/config"
    fi
else
    echo "Unknown phone: $PHONE"
    exit 1
fi

echo ""
echo "--- Deploying Spyglass APK ---"
SCRIPT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
APK="$SCRIPT_DIR/app/build/outputs/apk/debug/app-debug.apk"
if [ -f "$APK" ]; then
    scp -P 8022 "$APK" "$HOST":~/storage/downloads/spyglass.apk
    ssh -p 8022 "$HOST" "termux-open ~/storage/downloads/spyglass.apk"
    echo "APK sent — tap Install on phone"
else
    echo "APK not found. Run 'make build' first."
fi
