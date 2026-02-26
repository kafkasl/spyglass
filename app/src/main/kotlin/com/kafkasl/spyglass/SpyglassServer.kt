package com.kafkasl.spyglass

import android.os.BatteryManager
import android.os.Environment
import android.os.StatFs
import fi.iki.elonen.NanoHTTPD
import org.json.JSONArray
import org.json.JSONObject
import java.io.*

/**
 * HTTP server exposing camera endpoints.
 * Decoupled from Android framework for testability — camera ops go through callbacks.
 */
class SpyglassServer(
    port: Int = PORT,
    private val frameBuffer: FrameBuffer,
    private val onConfigChange: ((CameraConfig) -> Unit)? = null,
    private val onRecordStart: (() -> Boolean)? = null,
    private val onRecordStop: (() -> Boolean)? = null,
    private val statusProvider: (() -> JSONObject)? = null,
    private val recordingsDir: File? = null
) : NanoHTTPD(port) {

    @Volatile
    var config: CameraConfig = CameraConfig()

    @Volatile
    var isRecording: Boolean = false

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri.trimEnd('/')
        val method = session.method

        return try {
            when {
                method == Method.GET && uri == "/video" -> serveMjpeg()
                method == Method.GET && uri == "/snap" -> serveSnap()
                method == Method.GET && uri == "/status" -> serveStatus()
                method == Method.GET && uri == "/config" -> serveGetConfig()
                method == Method.POST && uri == "/config" -> servePostConfig(session)
                method == Method.POST && uri == "/record/start" -> serveRecordStart()
                method == Method.POST && uri == "/record/stop" -> serveRecordStop()
                method == Method.GET && uri == "/recordings" -> serveRecordingsList()
                method == Method.GET && uri.startsWith("/recordings/") -> serveRecordingFile(uri)
                method == Method.GET && uri == "/audio" -> serveAudioStub()
                else -> jsonError(Response.Status.NOT_FOUND, "Not found: $uri")
            }
        } catch (e: Exception) {
            jsonError(Response.Status.INTERNAL_ERROR, e.message ?: "Internal error")
        }
    }

    // --- MJPEG stream ---

    private fun serveMjpeg(): Response {
        val boundary = "spyglass_frame"
        val stream = MjpegInputStream(frameBuffer, boundary, config.fps)
        return newChunkedResponse(
            Response.Status.OK,
            "multipart/x-mixed-replace; boundary=$boundary",
            stream
        )
    }

    // --- Snapshot ---

    private fun serveSnap(): Response {
        val pair = frameBuffer.get()
            ?: return jsonError(Response.Status.SERVICE_UNAVAILABLE, "No frame available")
        val (jpeg, _) = pair
        return newFixedLengthResponse(
            Response.Status.OK, "image/jpeg",
            ByteArrayInputStream(jpeg), jpeg.size.toLong()
        )
    }

    // --- Status ---

    private fun serveStatus(): Response {
        val json = statusProvider?.invoke() ?: JSONObject().apply {
            put("config", config.toJson())
            put("recording", isRecording)
        }
        return jsonResponse(json)
    }

    // --- Config ---

    private fun serveGetConfig(): Response = jsonResponse(config.toJson())

    private fun servePostConfig(session: IHTTPSession): Response {
        val body = readBody(session)
        val json = JSONObject(body)
        val newConfig = CameraConfig.fromJson(json, config)
        config = newConfig
        onConfigChange?.invoke(newConfig)
        return jsonResponse(JSONObject().apply {
            put("ok", true)
            put("config", newConfig.toJson())
        })
    }

    // --- Recording ---

    private fun serveRecordStart(): Response {
        if (isRecording) return jsonResponse(JSONObject().put("ok", true).put("message", "Already recording"))
        val ok = onRecordStart?.invoke() ?: false
        if (ok) isRecording = true
        return jsonResponse(JSONObject().put("ok", ok))
    }

    private fun serveRecordStop(): Response {
        if (!isRecording) return jsonResponse(JSONObject().put("ok", true).put("message", "Not recording"))
        val ok = onRecordStop?.invoke() ?: false
        if (ok) isRecording = false
        return jsonResponse(JSONObject().put("ok", ok))
    }

    private fun serveRecordingsList(): Response {
        val dir = recordingsDir ?: return jsonResponse(JSONObject().put("recordings", JSONArray()))
        val files = dir.listFiles()?.sortedByDescending { it.lastModified() } ?: emptyList()
        val arr = JSONArray()
        files.forEach { f ->
            arr.put(JSONObject().apply {
                put("name", f.name)
                put("size", f.length())
                put("modified", f.lastModified())
            })
        }
        return jsonResponse(JSONObject().put("recordings", arr))
    }

    private fun serveRecordingFile(uri: String): Response {
        val name = uri.removePrefix("/recordings/")
        if (name.contains("..") || name.contains("/")) {
            return jsonError(Response.Status.FORBIDDEN, "Invalid path")
        }
        val dir = recordingsDir ?: return jsonError(Response.Status.NOT_FOUND, "No recordings directory")
        val file = File(dir, name)
        if (!file.exists()) return jsonError(Response.Status.NOT_FOUND, "File not found: $name")
        return newFixedLengthResponse(
            Response.Status.OK, "video/mp4",
            FileInputStream(file), file.length()
        )
    }

    // --- Audio (stub) ---

    private fun serveAudioStub(): Response =
        jsonError(Response.Status.NOT_IMPLEMENTED, "Audio streaming not yet implemented")

    // --- Helpers ---

    companion object {
        fun readBody(session: IHTTPSession): String {
            val contentLength = session.headers["content-length"]?.toIntOrNull() ?: 0
            if (contentLength == 0) return "{}"
            val buf = ByteArray(contentLength)
            session.inputStream.read(buf, 0, contentLength)
            return String(buf)
        }

        fun jsonResponse(json: JSONObject): Response =
            newFixedLengthResponse(Response.Status.OK, "application/json", json.toString(2))

        fun jsonError(status: Response.Status, message: String): Response =
            newFixedLengthResponse(status, "application/json",
                JSONObject().put("error", message).toString(2))
    }
}

/**
 * InputStream that yields MJPEG frames continuously.
 * Each connected client gets its own instance.
 */
class MjpegInputStream(
    private val buffer: FrameBuffer,
    private val boundary: String,
    private val fps: Int
) : InputStream() {

    private var currentFrame: ByteArray? = null
    private var pos = 0
    private var lastFrameNumber: Long = -1
    private val frameDelay = if (fps > 0) 1000L / fps else 66L

    override fun read(): Int {
        while (true) {
            val frame = currentFrame
            if (frame != null && pos < frame.size) {
                return frame[pos++].toInt() and 0xFF
            }
            // Need next frame
            if (!loadNextFrame()) return -1
        }
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (len == 0) return 0
        while (true) {
            val frame = currentFrame
            if (frame != null && pos < frame.size) {
                val available = frame.size - pos
                val toRead = minOf(len, available)
                System.arraycopy(frame, pos, b, off, toRead)
                pos += toRead
                return toRead
            }
            if (!loadNextFrame()) return -1
        }
    }

    private fun loadNextFrame(): Boolean {
        // Wait for a new frame, respecting FPS
        var attempts = 0
        while (attempts < 100) { // ~10 seconds max wait
            val pair = buffer.get()
            if (pair != null && pair.second != lastFrameNumber) {
                val (jpeg, num) = pair
                lastFrameNumber = num
                // Build MJPEG frame with boundary
                val header = "--$boundary\r\nContent-Type: image/jpeg\r\nContent-Length: ${jpeg.size}\r\n\r\n"
                val footer = "\r\n"
                val out = ByteArrayOutputStream(header.length + jpeg.size + footer.length)
                out.write(header.toByteArray())
                out.write(jpeg)
                out.write(footer.toByteArray())
                currentFrame = out.toByteArray()
                pos = 0
                return true
            }
            try { Thread.sleep(frameDelay) } catch (_: InterruptedException) { return false }
            attempts++
        }
        return false
    }
}
