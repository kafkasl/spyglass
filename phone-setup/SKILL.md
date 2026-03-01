---
name: phone-setup
description: Set up an Android phone with SSH over Tailscale. Hardened sshd, key-only auth, Tailscale-only binding. Use when setting up a new phone for remote access or troubleshooting an existing one.
---

# Phone Setup — SSH over Tailscale

Set up an Android phone as a headless remote server, accessible only via Tailscale with hardened SSH.

## Quick Start

```bash
# On the phone (one-time, needs screen):
# 1. Install from F-Droid: Termux, Termux:API, Termux:Boot
# 2. Open Termux:Boot once
# 3. In Termux: pkg install openssh && passwd && sshd
# 4. Battery → Unrestricted for: Termux, Termux:API, Termux:Boot, Tailscale

# From Mac:
cd phone-setup
./setup.sh <tailscale-hostname>
```

The script handles: SSH key copy → Include verification → hardened sshd config → boot wrapper → `from=` key restriction → safe restart → verification.

## Files

| File | Purpose |
|------|---------|
| `setup.sh` | Automation script — run from Mac after phone prerequisites are done |
| `sshd-hardened.conf` | Template config with `$TS_IP` placeholder — the single source of truth |
| `start-sshd.sh` | Boot/bashrc wrapper — waits for Tailscale before starting sshd |

## Security Model

### Defense in depth (3 layers)

**Layer 1 — Network binding**: sshd binds only to the Tailscale IP (`ListenAddress`). LAN/internet connections are refused at TCP level.

**Layer 2 — Key restriction**: `authorized_keys` has `from="100.64.0.0/10,fd7a:115c:a1e0::/48"` prefix. Even if sshd accidentally binds to `0.0.0.0`, the key only works from Tailscale IPs.

**Layer 3 — Authentication lockdown**: key-only auth, password locked at OS level (`passwd -l`).

### sshd hardening (`sshd-hardened.conf`)

| Setting | Why |
|---------|-----|
| `ListenAddress $TS_IP` | Binds to Tailscale IP only |
| `AuthenticationMethods publickey` | Key-only. No passwords, no keyboard-interactive |
| `PasswordAuthentication no` | Explicit disable |
| `KbdInteractiveAuthentication no` | Closes the keyboard-interactive fallback |
| `PermitEmptyPasswords no` | Belt for empty passwords |
| `HostbasedAuthentication no` | Disable host-based auth |
| `MaxAuthTries 3` | Limits brute-force per connection |
| `MaxSessions 3` | Limits concurrent sessions |
| `LoginGraceTime 20` | 20s to authenticate or get disconnected |
| `MaxStartups 3:30:10` | Rate-limit unauthenticated connections |
| `ClientAliveInterval 60` | Detect dead sessions |
| `ClientAliveCountMax 2` | Drop after 2 missed keepalives |
| `AllowTcpForwarding no` | No port forwarding |
| `AllowAgentForwarding no` | No SSH agent forwarding |
| `AllowStreamLocalForwarding no` | No Unix socket forwarding |
| `PermitTunnel no` | No VPN tunneling |
| `X11Forwarding no` | No X11 |
| `IgnoreRhosts yes` | Ignore legacy .rhosts files |

### Boot wrapper (`start-sshd.sh`)

The boot script does NOT blindly run `sshd`. It:
1. Checks if sshd is already running (exits if so)
2. Waits up to 30s for a Tailscale IP on `tun0` or `tailscale0`
3. Verifies the IP is in Tailscale's CGNAT range (`100.*`)
4. Only then starts sshd

This prevents starting an unhardened sshd before Tailscale is ready.

## Gotchas

### sshd_config Include directive
The setup verifies that Termux's main `sshd_config` has `Include ...sshd_config.d/*.conf`. If missing, the hardened config in `spyglass.conf` is silently ignored. The script checks this and fails with instructions if not found.

### Tailscale interface name
- Most Android phones: `tun0`
- Some devices: `tailscale0`
- Both `setup.sh` and `start-sshd.sh` try both automatically

### Phone goes to sleep
Tailscale may disconnect when the screen is off. Battery optimization → Unrestricted helps. If SSH stops responding:
1. Wake the phone screen
2. Check Tailscale is connected
3. Open Termux (`.bashrc` guard will run `start-sshd.sh`)

### termux-open needs storage
Run `termux-setup-storage` in Termux first (grants access to `~/storage/downloads`).

### F-Droid "old SDK" warning
All Termux packages target API 28 deliberately. The warning is harmless.

### Password is locked
After setup, `passwd -l` locks the Termux password. To unlock for local use: `passwd -u` then `passwd` to set a new one.

## Verification

The script verifies automatically. To re-check manually:

```bash
# Key auth works
ssh <hostname> "echo ok"

# Password auth disabled
ssh -p 8022 -o PreferredAuthentications=password -o PubkeyAuthentication=no <hostname> "echo INSECURE"
# → "Permission denied (publickey)"

# Kbd-interactive disabled
ssh -p 8022 -o PreferredAuthentications=keyboard-interactive -o PubkeyAuthentication=no <hostname> "echo INSECURE"
# → "Permission denied (publickey)"

# LAN access blocked
ssh -p 8022 <lan-ip> "echo INSECURE"
# → "Connection refused"

# Effective config
ssh <hostname> "sshd -T | grep -E '^listenaddress|^port|^passwordauth|^authenticationmethods'"

# authorized_keys has from= restriction
ssh <hostname> "cat ~/.ssh/authorized_keys"
# → should start with from="100.64.0.0/10,fd7a:115c:a1e0::/48"
```

## Troubleshooting

| Symptom | Cause | Fix |
|---------|-------|-----|
| `Connection refused` on Tailscale IP | sshd not running | Open Termux on phone, run `~/start-sshd.sh` |
| `Connection timed out` | Tailscale disconnected | Wake phone, check Tailscale app |
| `Permission denied (publickey)` | Wrong key or `from=` mismatch | Check `authorized_keys`, verify connecting from Tailscale IP |
| sshd crashes on start | Bad config | `sshd -t` to test, check `sshd_config.d/spyglass.conf` |
| `No such file or directory` for config dir | Missing directory | `mkdir -p /data/data/com.termux/files/usr/etc/ssh/sshd_config.d` |
| Config ignored, listening on 0.0.0.0 | Missing Include in main sshd_config | Add `Include /data/data/com.termux/files/usr/etc/ssh/sshd_config.d/*.conf` to main config |
| sshd won't start at boot | Tailscale not ready in 30s | Check Tailscale auto-start, increase timeout in `start-sshd.sh` |

## Tailscale ACLs (optional, recommended)

For additional network-level restriction, add a Tailscale ACL rule limiting which devices can reach the phone's SSH port. This is configured in the Tailscale admin console, not on the phone.
