#!/bin/bash
# Phone setup: Termux SSH over Tailscale with hardened sshd
# Usage: ./setup.sh <hostname>
#
# Prerequisites (do on the phone first):
#   1. Install from F-Droid: Termux, Termux:API, Termux:Boot
#   2. Open Termux:Boot once (registers with Android)
#   3. In Termux: pkg install openssh && passwd && sshd
#   4. Settings → Apps → Battery → Unrestricted for: Termux, Termux:API, Termux:Boot, Tailscale
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
HOST="${1:?Usage: $0 <hostname>}"

echo "=== Setting up $HOST ==="

# --- SSH key ---
echo ""
echo "--- Copying SSH key (will prompt for Termux password) ---"
ssh-copy-id -p 8022 "$HOST"

echo ""
echo "--- Verifying authorized key ---"
ssh -p 8022 "$HOST" "cat ~/.ssh/authorized_keys"
echo ""
read -p "Correct? (y/n): " CONFIRM
[ "$CONFIRM" = "y" ] || exit 1

# --- Detect Tailscale IP ---
echo ""
echo "--- Detecting Tailscale IP ---"
TS_IP=$(ssh -p 8022 "$HOST" bash << 'DETECT'
TS_IP=$(ifconfig tun0 2>/dev/null | grep -oE 'inet [0-9.]+' | awk '{print $2}')
[ -z "$TS_IP" ] && TS_IP=$(ifconfig tailscale0 2>/dev/null | grep -oE 'inet [0-9.]+' | awk '{print $2}')
case "$TS_IP" in
    100.*) echo "$TS_IP" ;;
    *) echo "ERROR: Tailscale IP not in CGNAT range (got: ${TS_IP:-none})" >&2; exit 1 ;;
esac
DETECT
)
echo "Tailscale IP: $TS_IP"

# --- Verify Include directive ---
echo ""
echo "--- Verifying sshd_config Include directive ---"
ssh -p 8022 "$HOST" "grep -q 'Include.*sshd_config.d' /data/data/com.termux/files/usr/etc/ssh/sshd_config" || {
    echo "ERROR: sshd_config does not Include sshd_config.d/*.conf"
    echo "Add this line to /data/data/com.termux/files/usr/etc/ssh/sshd_config:"
    echo "  Include /data/data/com.termux/files/usr/etc/ssh/sshd_config.d/*.conf"
    exit 1
}
echo "Include directive found ✓"

# --- Deploy hardened sshd config ---
echo ""
echo "--- Deploying hardened sshd config ---"

# Substitute $TS_IP in template and upload
sed "s/\\\$TS_IP/$TS_IP/g" "$SCRIPT_DIR/sshd-hardened.conf" | \
    ssh -p 8022 "$HOST" "mkdir -p /data/data/com.termux/files/usr/etc/ssh/sshd_config.d && \
    cat > /data/data/com.termux/files/usr/etc/ssh/sshd_config.d/spyglass.conf"

echo "Config deployed. Verifying..."
ssh -p 8022 "$HOST" "cat /data/data/com.termux/files/usr/etc/ssh/sshd_config.d/spyglass.conf"

# --- Test config before restart ---
echo ""
echo "--- Testing sshd config ---"
ssh -p 8022 "$HOST" "sshd -t" || {
    echo "ERROR: sshd config test failed. Removing bad config."
    ssh -p 8022 "$HOST" "rm /data/data/com.termux/files/usr/etc/ssh/sshd_config.d/spyglass.conf"
    exit 1
}
echo "Config test passed ✓"

# --- Deploy boot script + bashrc guard ---
echo ""
echo "--- Deploying start-sshd wrapper ---"
scp -P 8022 "$SCRIPT_DIR/start-sshd.sh" "$HOST":~/start-sshd.sh
ssh -p 8022 "$HOST" bash << 'DEPLOY'
chmod +x ~/start-sshd.sh

# Termux:Boot uses the wrapper
mkdir -p ~/.termux/boot
ln -sf ~/start-sshd.sh ~/.termux/boot/start-sshd.sh
echo "boot script linked"

# .bashrc guard calls the wrapper
if grep -q 'sshd' ~/.bashrc 2>/dev/null; then
    sed -i '/sshd/d' ~/.bashrc
fi
echo '~/start-sshd.sh 2>/dev/null' >> ~/.bashrc
echo ".bashrc guard updated"
DEPLOY

# --- Restrict authorized_keys to Tailscale IPs ---
echo ""
echo "--- Adding from= restriction to authorized_keys ---"
ssh -p 8022 "$HOST" bash << 'KEYS'
AK=~/.ssh/authorized_keys
if grep -q '^from=' "$AK" 2>/dev/null; then
    echo "from= restriction already present"
else
    # Prefix each key with from= restriction for Tailscale CGNAT + IPv6
    sed -i 's|^ssh-|from="100.64.0.0/10,fd7a:115c:a1e0::/48" ssh-|' "$AK"
    echo "from= restriction added:"
    cat "$AK"
fi
KEYS

# --- Restart sshd safely ---
echo ""
echo "--- Restarting sshd ---"
# Use nohup + background to avoid killing our own session
ssh -p 8022 "$HOST" "nohup sh -c 'sleep 1; pkill -x sshd; sleep 1; sshd' > /dev/null 2>&1 &"
echo "Restart scheduled (1s delay)..."

# --- Add to SSH config ---
echo ""
echo "--- SSH config ---"
if ! grep -qF "Host $HOST" ~/.ssh/config 2>/dev/null; then
    cat >> ~/.ssh/config << EOF

Host $HOST
    HostName $HOST
    Port 8022
    ServerAliveInterval 60
    ServerAliveCountMax 3
EOF
    echo "Added $HOST to ~/.ssh/config"
else
    echo "$HOST already in ~/.ssh/config"
fi

# --- Wait and verify ---
echo ""
echo "--- Verifying (waiting for sshd restart) ---"
sleep 5
for i in 1 2 3; do
    if ssh "$HOST" "echo ok" 2>/dev/null; then
        break
    fi
    echo "Attempt $i: waiting..."
    sleep 3
done

echo ""
echo "=== Verification ==="

echo "Key auth..."
ssh "$HOST" "echo '  ✓ key auth'" 2>/dev/null || echo "  ✗ key auth FAILED"

echo "Password disabled..."
ssh -p 8022 -o PreferredAuthentications=password -o PubkeyAuthentication=no "$HOST" "echo INSECURE" 2>/dev/null \
    && echo "  ✗ PASSWORD AUTH STILL WORKS" || echo "  ✓ password disabled"

echo "Kbd-interactive disabled..."
ssh -p 8022 -o PreferredAuthentications=keyboard-interactive -o PubkeyAuthentication=no "$HOST" "echo INSECURE" 2>/dev/null \
    && echo "  ✗ KBD-INTERACTIVE STILL WORKS" || echo "  ✓ kbd-interactive disabled"

echo "Effective listen address..."
ssh "$HOST" "sshd -T 2>/dev/null | grep -E '^listenaddress|^port'" || echo "  (could not check)"

echo "Lock password (disabling password login at OS level)..."
ssh "$HOST" "passwd -l" 2>/dev/null && echo "  ✓ password locked" || echo "  ⚠ passwd -l not available (harmless — password auth is disabled in sshd)"

echo ""
echo "=== Done! ==="
echo "Phone $HOST is hardened for SSH over Tailscale."
