# Spyglass Build Report

## What Was Built

**Spyglass** — a minimal Android camera server that replaces DroidCam. Streams MJPEG video over HTTP from an Android phone, controllable entirely via `curl`.

**Location**: `../kafkasl/spyglass/`  
**Package**: `com.kafkasl.spyglass`  
**Name rationale**: "Spyglass" = small telescope for watching/spying. Fits the `leye` ("I spy with my little eye") project theme.

## Files Created

| File | Purpose |
|------|---------|
| `build.gradle.kts` (root) | AGP 8.7.3 + Kotlin 2.0.21 |
| `app/build.gradle.kts` | App config: minSdk 30, targetSdk 34, deps (CameraX 1.3.4, NanoHTTPD 2.3.1) |
| `app/src/main/AndroidManifest.xml` | Permissions, foreground service, boot receiver |
| `Config.kt` | `CameraConfig` data class + JSON serde + constants |
| `FrameBuffer.kt` | Thread-safe single-frame JPEG buffer (RW lock) |
| `SpyglassServer.kt` | NanoHTTPD server with all endpoints + `MjpegInputStream` |
| `CameraService.kt` | `LifecycleService`: camera→JPEG pipeline, wake lock, notifications |
| `MainActivity.kt` | Permission grants + status display (IP, URLs) |
| `BootReceiver.kt` | `BOOT_COMPLETED` → start service |
| `Makefile` | `build`, `test`, `install` (SSH), `adb-install`, `status`, `snap`, `clean` |
| `README.md` | Public-facing docs |
| `AGENTS.md` | Agent/developer guide |
| `LICENSE` | MIT |
| `.gitignore` | Standard Android ignores + `.env` |
| `.env.example` | `PHONE_HOST`, `SSH_PORT` |

### Test Files

| File | Tests | What's Covered |
|------|-------|----------------|
| `FrameBufferTest.kt` | 6 | Empty buffer, put/get, frame numbering, latest-wins, clear, concurrent writes |
| `ConfigTest.kt` | 7 | Defaults, JSON roundtrip, partial update, current-config merge, bad input, empty JSON |
| `SpyglassServerTest.kt` | 18 | All HTTP endpoints via real server, MJPEG streaming, path traversal block |
| `MjpegInputStreamTest.kt` | 3 | Boundary headers, multi-frame, byte-level read |
| **Total** | **34** | **All pass ✓** |

## Environment Setup (Installed)

| What | Where | Why |
|------|-------|-----|
| `openjdk@17` | `/opt/homebrew/opt/openjdk@17` | AGP 8.7.3 requires JDK 17 (JDK 24 was too new) |
| `android-commandlinetools` | brew cask | For `sdkmanager` |
| Android SDK platform 34 | `~/Library/Android/sdk/platforms/android-34` | Compile target |
| Android build-tools 34.0.0 | `~/Library/Android/sdk/build-tools/34.0.0` | Build toolchain |
| Android platform-tools | `~/Library/Android/sdk/platform-tools` | `adb` |
| `gradle` 9.3.1 (brew) | Used to generate wrapper | `gradlew` uses 8.9 |

## What Works

- ✅ `./gradlew assembleDebug` — produces APK in 3s (clean build ~10s)
- ✅ `./gradlew testDebugUnitTest` — 34/34 tests pass
- ✅ `make build` / `make test` — convenience wrappers
- ✅ `make install` — builds + deploys to phone via SSH
- ✅ Drop-in DroidCam replacement: same port 4747, same `/video` MJPEG path

## What's Stubbed / TODO

| Feature | Status | Notes |
|---------|--------|-------|
| MJPEG `/video` | ✅ Full | CameraX → YUV → JPEG → MJPEG stream |
| `/snap` | ✅ Full | Returns latest JPEG frame |
| `/status` | ✅ Full | Battery, disk, config, uptime |
| `/config` GET/POST | ✅ Full | Partial updates, re-binds camera |
| `/record/start\|stop` | ⚠️ Stub | Sets flag only, no actual MediaRecorder |
| `/recordings` | ✅ Full | Lists/downloads files from recordings dir |
| `/audio` | ❌ Stub | Returns 501 |
| Boot auto-start | ✅ Full | BroadcastReceiver registered |
| Wake lock | ✅ Full | 24h partial wake lock |
| Adaptive icon | ❌ | Uses system `ic_menu_camera` |

## Compatibility with leye

Spyglass serves MJPEG on the same URL that `leye` already expects:
```
http://<phone>:4747/video
```

No changes needed to `leye/main.go`. Just set `DROIDCAM_IP` to the phone's Tailscale IP and run `leye start`.
