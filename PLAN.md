# Spyglass — Plan

## Completed

### Mi A3 setup
- [x] Install Termux:Boot (APK from F-Droid repo, scp + manual install)
- [x] Open Termux:Boot once, battery → unrestricted
- [x] Install Spyglass APK via `adb install` over Tailscale (silent, no taps)
- [x] Grant camera + audio + notification permissions
- [x] Enable wireless ADB: `adb tcpip 5555` (one-time USB, remote installs forever)
- [x] Fix YUV→NV21 conversion (green/magenta artifacts), extract testable `YuvConverter`
- [x] Verify: `/status`, `/snap`, `/video` all working

### Wireless ADB (Mi A3)
```bash
export PATH="$HOME/Library/Android/sdk/platform-tools:$PATH"
adb connect 100.100.122.107:5555
adb install app/build/outputs/apk/debug/app-debug.apk
```

## Next — OSS packaging

### README.md
- [ ] What Spyglass is (one-paragraph pitch)
- [ ] Why it exists (DroidCam replacement: open-source, no ads, headless, API-first)
- [ ] Quick start (install APK → grant permissions → curl endpoints)
- [ ] All HTTP endpoints with examples
- [ ] Config options explained
- [ ] Building from source
- [ ] Deploying to a phone (ADB, SSH+termux-open)

### OSS cleanup
- [ ] Review what's in the repo — remove personal/local files
- [ ] LICENSE (Apache 2.0 or MIT)
- [ ] .gitignore audit
- [ ] GitHub release with pre-built APK

## Pending — Pixel 5 setup

### Needs phone in hand
- [ ] Install Termux, Termux:API, Termux:Boot from F-Droid
- [ ] In Termux: `pkg install openssh && passwd && sshd`
- [ ] Battery → Unrestricted for Termux, Termux:API, Termux:Boot, Tailscale
- [ ] From Mac: `bash phone-setup/setup.sh pixel-5`
- [ ] Install Spyglass APK (via ADB or termux-open)
- [ ] Grant permissions, verify endpoints

## Pending — features

- [ ] `leye start` integration test (drop-in DroidCam replacement)
- [ ] Implement actual recording (MediaRecorder)
- [ ] Audio streaming at `/audio` (currently 501 stub)
- [ ] Fix `/config` POST — "Not in application's main thread" error
- [ ] Proper app icon
- [ ] Migrate from deprecated `setTargetResolution` to `ResolutionSelector`
