# Spyglass — Agent Guide

Minimal Android camera server (DroidCam replacement) for the [leye](https://github.com/kafkasl/leye) project.

## Build & Test

```bash
# Required env (set in Makefile, but useful to know)
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
export ANDROID_HOME=$HOME/Library/Android/sdk

# Build
make build    # or: ./gradlew assembleDebug

# Test
make test     # or: ./gradlew testDebugUnitTest

# APK location
app/build/outputs/apk/debug/app-debug.apk
```

## Architecture

### Source layout
```
app/src/main/kotlin/com/kafkasl/spyglass/
├── Config.kt          # CameraConfig data class, constants (PORT=4747, etc.)
├── FrameBuffer.kt     # Thread-safe JPEG frame buffer (ReentrantReadWriteLock)
├── SpyglassServer.kt  # NanoHTTPD HTTP server + MjpegInputStream
├── CameraService.kt   # Android foreground service (camera + server lifecycle)
├── MainActivity.kt    # Minimal UI — permissions + status text
└── BootReceiver.kt    # BOOT_COMPLETED broadcast → start service
```

### Key design decisions
- **SpyglassServer is decoupled from Android**: takes callbacks (`onConfigChange`, `onRecordStart`, etc.) and a `FrameBuffer`. This makes it testable with pure JUnit (no Robolectric needed for server tests).
- **FrameBuffer** is the single shared state between camera (producer) and HTTP clients (consumers). Uses ReadWriteLock, not synchronization.
- **MjpegInputStream** is created per-client via `newChunkedResponse`. Each client polls FrameBuffer at the configured FPS rate.
- **CameraService** is a `LifecycleService` so CameraX can bind to its lifecycle. It holds the wake lock and notification.

### HTTP endpoints (port 4747)
```
GET  /video              → MJPEG stream (multipart/x-mixed-replace)
GET  /snap               → Single JPEG
GET  /status             → JSON (config, recording, battery, disk, uptime)
GET  /config             → Current CameraConfig as JSON
POST /config             → Update config (partial JSON OK)
POST /record/start|stop  → Recording control
GET  /recordings         → List recorded files
GET  /recordings/<file>  → Download recording
GET  /audio              → 501 Not Implemented (stub)
```

### Config JSON format
```json
{
  "camera": 0,           // 0=back, 1=front
  "resolution": "1280x720",
  "fps": 15,
  "jpegQuality": 80
}
```

## Tests

34 unit tests in `app/src/test/`:
- **FrameBufferTest** — empty, put/get, increment, clear, concurrent writes
- **ConfigTest** — defaults, toJson/fromJson roundtrip, partial updates, bad input
- **SpyglassServerTest** — all endpoints via real HTTP (starts server on port 0), path traversal, MJPEG streaming
- **MjpegInputStreamTest** — boundary headers, multi-frame reads, byte-level read

## Deploy to phone

```bash
# Via SSH + Termux
make install   # scp APK → phone, termux-open

# Via ADB
make adb-install

# Verify
curl http://<phone>:4747/status
```

## Integration with leye

Spyglass is a drop-in DroidCam replacement. Same port (4747), same MJPEG at `/video`:
```bash
export DROIDCAM_IP=<phone-tailscale-ip>
leye start   # connects to http://<phone>:4747/video
```

## TODO / Known limitations
- Audio streaming (`/audio`) is stubbed out — needs AudioRecord + AAC/Opus encoding
- Recording is stubbed — needs MediaRecorder integration
- No adaptive icon (uses system camera drawable)
- `setTargetResolution` is deprecated in CameraX — should migrate to ResolutionSelector
- No thermal/battery-aware throttling yet (v2 feature)
