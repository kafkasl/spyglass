# TODO

## Immediate — deploy & verify
- [ ] Install APK on phone (already at `~/storage/downloads/spyglass.apk`, needs tap)
- [ ] Grant camera + audio + notification permissions (first launch)
- [ ] Verify: `curl http://a3:4747/status`, `/snap`, `ffplay /video`
- [ ] Test `leye start` integration (drop-in DroidCam replacement)
- [ ] Enable wireless ADB: USB once → `adb tcpip 5555` (remote installs forever)

## Short term
- [ ] Implement actual recording (MediaRecorder, not just a flag)
- [ ] Audio streaming at `/audio` (currently 501 stub)
- [ ] Migrate from deprecated `setTargetResolution` to `ResolutionSelector`
- [ ] Proper app icon

## Later (v2)
- [ ] Torch/flash control
- [ ] Thermal/battery-aware throttling
- [ ] Zoom, focus, exposure controls
- [ ] Root the Mi A3 for fully headless management
