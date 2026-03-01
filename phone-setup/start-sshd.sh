#!/data/data/com.termux/files/usr/bin/sh
# Wait for Tailscale IP in CGNAT range (100.64.0.0/10) before starting sshd.
# Used by both Termux:Boot and .bashrc guard.

# Already running?
pgrep -x sshd > /dev/null && exit 0

# Wait up to 30s for Tailscale
i=0
while [ $i -lt 30 ]; do
    TS_IP=$(ifconfig tun0 2>/dev/null | grep -oE 'inet [0-9.]+' | awk '{print $2}')
    [ -z "$TS_IP" ] && TS_IP=$(ifconfig tailscale0 2>/dev/null | grep -oE 'inet [0-9.]+' | awk '{print $2}')

    # Verify it's in Tailscale CGNAT range (100.64.0.0/10 = 100.64-127.x.x.x)
    case "$TS_IP" in
        100.*) sshd; exit $? ;;
    esac

    sleep 1
    i=$((i + 1))
done

echo "start-sshd: Tailscale not ready after 30s, sshd not started" >&2
exit 1
