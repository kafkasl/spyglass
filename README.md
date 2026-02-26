# 🔭 Spyglass

Minimal Android camera server. Open source DroidCam replacement.

Streams MJPEG video from your Android phone over HTTP. Designed for headless operation — control everything via `curl`.

## Features

- **MJPEG video stream** at `/video` (compatible with ffmpeg, VLC, leye)
- **JPEG snapshots** at `/snap`
- **JSON status** with battery, disk space, config
- **Runtime config** — switch cameras, change resolution/FPS via HTTP POST
- **Recording** to phone storage with download endpoint
- **Auto-start on boot** via BroadcastReceiver
- **Wake lock** — keeps streaming with screen off
- **Foreground service** — Android won't kill it

## HTTP API

```
GET  /video              MJPEG stream
GET  /snap               Single JPEG snapshot
GET  /status             JSON: config, recording state, battery, disk
GET  /config             Current camera config
POST /config             Update config: {"camera": 0|1, "resolution": "1280x720", "fps": 15}
POST /record/start       Start recording
POST /record/stop        Stop recording
GET  /recordings         List recordings (JSON)
GET  /recordings/<file>  Download a recording
GET  /audio              Audio stream (not yet implemented)
```

## Quick Start

### Build
```bash
make build          # → app/build/outputs/apk/debug/app-debug.apk
```

### Install
```bash
# Via SSH (Termux on phone)
make install        # builds + scp + termux-open

# Via ADB
make adb-install
```

### Use
```bash
# Check status
curl http://<phone>:4747/status

# Take a snapshot
curl http://<phone>:4747/snap > photo.jpg

# Watch live video
ffplay http://<phone>:4747/video
# or
vlc http://<phone>:4747/video

# Switch to front camera
curl -X POST http://<phone>:4747/config -d '{"camera": 1}'

# Use with leye (drop-in DroidCam replacement)
export DROIDCAM_IP=<phone>
leye start
```

## Requirements

- **Phone**: Android 11+ (API 30)
- **Build**: JDK 17, Android SDK (platform 34, build-tools 34)
- **Deploy**: SSH access via Termux + Tailscale, or ADB

## Project Structure

```
app/src/main/kotlin/com/kafkasl/spyglass/
├── Config.kt          # CameraConfig data class + constants
├── FrameBuffer.kt     # Thread-safe single-frame JPEG buffer
├── SpyglassServer.kt  # NanoHTTPD server + MJPEG stream
├── CameraService.kt   # Foreground service (camera + server lifecycle)
├── MainActivity.kt    # Minimal UI (permissions + status display)
└── BootReceiver.kt    # Auto-start on boot
```

## Configuration

Create a `.env` file (see `.env.example`):
```
PHONE_HOST=your-phone-hostname
SSH_PORT=8022
```

## Tests

```bash
make test
```

34 unit tests covering:
- `FrameBuffer` — thread safety, put/get/clear
- `CameraConfig` — JSON serialization, partial updates, defaults
- `SpyglassServer` — all HTTP endpoints, MJPEG streaming, path traversal protection
- `MjpegInputStream` — frame encoding, boundary headers

## License

MIT
