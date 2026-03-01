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
- [x] Install Termux:Boot from F-Droid (boot script already at `~/.termux/boot/start-sshd.sh`)
- [x] Open Termux:Boot once
- [x] Battery → Unrestricted for Termux:Boot
- [x] Install Spyglass APK (via `adb install` over Tailscale — no tap needed)
- [x] Open Spyglass → grant permissions

### Wireless ADB (enabled)
```bash
# Enabled via USB once, now permanent over Tailscale
adb connect 100.100.122.107:5555
adb install app/build/outputs/apk/debug/app-debug.apk  # silent install, no taps
```

### Verify
- [x] `curl http://100.100.122.107:4747/status` — working (1280x720@15fps, battery 100%)
- [x] `/snap` — JPEG OK
- [x] `/video` — MJPEG streaming OK
- [ ] `leye start` — drop-in DroidCam replacement test
