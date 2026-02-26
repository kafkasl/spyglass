# Pixel 5 Setup Plan

## Context
- Pixel 5 is on Tailscale (resolves as `pixel-5`, IP `100.118.45.38`)
- No Termux, no SSH, no ADB — just Tailscale running
- This is a primary phone — no root, no wireless ADB, SSH via Tailscale only
- Mi A3 (`a3`) already has Termux+SSH, needs Termux:Boot from F-Droid + Spyglass install

## On the Pixel 5 (need phone in hand)

### 1. Install apps from F-Droid
- [ ] Termux
- [ ] Termux:API
- [ ] Termux:Boot (open it once so Android registers it)

### 2. In Termux
```bash
pkg install openssh
passwd          # set a temporary password (will disable after key copy)
sshd            # start SSH server
```

### 3. Battery optimization
Settings → Apps → Battery → Unrestricted for:
- [ ] Termux
- [ ] Termux:API
- [ ] Termux:Boot
- [ ] Tailscale

### 4. From Mac
```bash
cd ~/go/github.com/kafkasl/spyglass
bash scripts/phone-setup.sh
# Choose: pixel-5
# This will: copy SSH key, configure sshd to Tailscale-only, disable password auth,
# add to ~/.ssh/config, deploy spyglass.apk
```

### 5. Back on phone
- [ ] Tap install when spyglass.apk dialog appears
- [ ] Open Spyglass → grant camera + audio + notification permissions

### 6. Verify from Mac
```bash
ssh pixel-5 "echo ok"                    # SSH works
curl http://pixel-5:4747/status           # Spyglass running
curl http://pixel-5:4747/snap > test.jpg  # Snapshot works
ffplay http://pixel-5:4747/video          # Video stream works
```

## Mi A3 (while you have phones out)

### Needs physical tap
- [ ] Install Termux:Boot from F-Droid (boot script already at `~/.termux/boot/start-sshd.sh`)
- [ ] Open Termux:Boot once
- [ ] Battery → Unrestricted for Termux:Boot
- [ ] `ssh a3 "termux-open ~/storage/downloads/spyglass.apk"` → tap install
- [ ] Open Spyglass → grant permissions

### Verify
```bash
curl http://a3:4747/status
leye start   # drop-in DroidCam replacement test
```
