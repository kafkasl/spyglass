<p align="center">
  <img src="logo.svg" alt="Spyglass" width="600">
</p>

# Spyglass

Minimal Android camera server. Open-source alternative to DroidCam and IP Webcam — no ads, no accounts, fully scriptable.

Runs an HTTP server on port 4747. You get MJPEG streaming, snapshots, and config via `curl`.

## Install

Requires JDK 17 and Android SDK (platform 34).

```bash
git clone https://github.com/kafkasl/spyglass.git && cd spyglass
make build    # → app/build/outputs/apk/debug/app-debug.apk
```

Copy the APK to your phone (email, Drive, USB, whatever) and open it. Or if you have ADB set up: `make adb-install`.

Open the app once to grant camera permissions. After that it auto-starts on boot as a foreground service.

## Usage

```bash
# snapshot
curl http://phone:4747/snap > photo.jpg

# live stream (works with VLC, ffplay, mpv, or any MJPEG client)
vlc http://phone:4747/video

# status
curl http://phone:4747/status
```
```json
{"config":{"camera":0,"resolution":"1280x720","fps":15,"jpegQuality":80},
 "recording":false,"battery":97,"disk":{"freeMB":52302},"uptime":76189}
```

```bash
# switch to front camera
curl -X POST http://phone:4747/config -d '{"camera": 1}'

# record
curl -X POST http://phone:4747/record/start
curl -X POST http://phone:4747/record/stop
curl http://phone:4747/recordings/vid.mp4 > vid.mp4
```

## API

| Endpoint | Method | Returns |
|---|---|---|
| `/video` | GET | MJPEG stream |
| `/snap` | GET | Single JPEG |
| `/status` | GET | JSON: config, battery, disk, uptime |
| `/config` | GET/POST | Read or update config (partial JSON OK) |
| `/record/start` | POST | Start recording |
| `/record/stop` | POST | Stop recording |
| `/recordings` | GET | List recorded files |
| `/recordings/<file>` | GET | Download recording |

## Deploy over SSH

If you prefer deploying over the network instead of ADB (e.g. via Termux + Tailscale):

```bash
cp .env.example .env   # set PHONE_HOST to your phone's hostname/IP
make install            # builds, scp's APK, opens on phone
```

See [`phone-setup/`](phone-setup/) for a hardened SSH setup script.

## Tests

```bash
make test   # 34 unit tests, plain JUnit (no emulator needed)
```

## License

MIT
