package com.kafkasl.spyglass

import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Integration tests for SpyglassServer.
 * Starts a real HTTP server on a random port and makes actual HTTP requests.
 */
class SpyglassServerTest {

    private lateinit var server: SpyglassServer
    private lateinit var frameBuffer: FrameBuffer
    private lateinit var tmpDir: File
    private var port = 0
    private var configApplied: CameraConfig? = null
    private var recordStarted = false
    private var recordStopped = false

    @Before
    fun setUp() {
        frameBuffer = FrameBuffer()
        tmpDir = File(System.getProperty("java.io.tmpdir"), "spyglass_test_${System.currentTimeMillis()}")
        tmpDir.mkdirs()
        configApplied = null
        recordStarted = false
        recordStopped = false

        // Use port 0 to get a random available port
        server = SpyglassServer(
            port = 0,
            frameBuffer = frameBuffer,
            onConfigChange = { configApplied = it },
            onRecordStart = { recordStarted = true; true },
            onRecordStop = { recordStopped = true; true },
            statusProvider = {
                JSONObject().apply {
                    put("config", server.config.toJson())
                    put("recording", server.isRecording)
                    put("battery", 85)
                    put("uptime", 123)
                }
            },
            recordingsDir = tmpDir
        )
        server.start()
        port = server.listeningPort
    }

    @After
    fun tearDown() {
        server.stop()
        tmpDir.deleteRecursively()
    }

    private fun get(path: String): Pair<Int, String> {
        val conn = URL("http://localhost:$port$path").openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = 2000
        conn.readTimeout = 2000
        return try {
            val body = conn.inputStream.bufferedReader().readText()
            conn.responseCode to body
        } catch (e: Exception) {
            try { conn.responseCode to (conn.errorStream?.bufferedReader()?.readText() ?: "") }
            catch (_: Exception) { 500 to e.message.orEmpty() }
        } finally {
            conn.disconnect()
        }
    }

    private fun post(path: String, body: String): Pair<Int, String> {
        val conn = URL("http://localhost:$port$path").openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/json")
        conn.connectTimeout = 2000
        conn.readTimeout = 2000
        conn.outputStream.use { it.write(body.toByteArray()) }
        return try {
            val resp = conn.inputStream.bufferedReader().readText()
            conn.responseCode to resp
        } catch (e: Exception) {
            try { conn.responseCode to (conn.errorStream?.bufferedReader()?.readText() ?: "") }
            catch (_: Exception) { 500 to e.message.orEmpty() }
        } finally {
            conn.disconnect()
        }
    }

    // --- /status ---

    @Test
    fun `GET status returns JSON with config`() {
        val (code, body) = get("/status")
        assertEquals(200, code)
        val json = JSONObject(body)
        assertTrue(json.has("config"))
        assertTrue(json.has("recording"))
        assertTrue(json.has("battery"))
        assertEquals(85, json.getInt("battery"))
    }

    // --- /config ---

    @Test
    fun `GET config returns default config`() {
        val (code, body) = get("/config")
        assertEquals(200, code)
        val json = JSONObject(body)
        assertEquals(0, json.getInt("camera"))
        assertEquals("1280x720", json.getString("resolution"))
        assertEquals(15, json.getInt("fps"))
    }

    @Test
    fun `POST config updates camera settings`() {
        val (code, body) = post("/config", """{"camera": 1, "resolution": "640x480", "fps": 30}""")
        assertEquals(200, code)
        val json = JSONObject(body)
        assertTrue(json.getBoolean("ok"))

        // Verify callback was invoked
        assertNotNull(configApplied)
        assertEquals(1, configApplied!!.cameraId)
        assertEquals(640, configApplied!!.width)
        assertEquals(480, configApplied!!.height)
        assertEquals(30, configApplied!!.fps)

        // Verify subsequent GET reflects new config
        val (code2, body2) = get("/config")
        assertEquals(200, code2)
        val json2 = JSONObject(body2)
        assertEquals(1, json2.getInt("camera"))
        assertEquals("640x480", json2.getString("resolution"))
    }

    @Test
    fun `POST config partial update`() {
        val (code, _) = post("/config", """{"fps": 10}""")
        assertEquals(200, code)
        assertEquals(10, server.config.fps)
        assertEquals(1280, server.config.width) // unchanged
    }

    // --- /snap ---

    @Test
    fun `GET snap with no frame returns 503`() {
        val (code, body) = get("/snap")
        assertEquals(503, code)
        val json = JSONObject(body)
        assertTrue(json.getString("error").contains("No frame"))
    }

    @Test
    fun `GET snap with frame returns JPEG`() {
        val fakeJpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 1, 2, 3, 0xFF.toByte(), 0xD9.toByte())
        frameBuffer.put(fakeJpeg)

        val conn = URL("http://localhost:$port/snap").openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        val bytes = conn.inputStream.readBytes()
        assertEquals(200, conn.responseCode)
        assertEquals("image/jpeg", conn.contentType)
        assertArrayEquals(fakeJpeg, bytes)
        conn.disconnect()
    }

    // --- /record/* ---

    @Test
    fun `POST record start`() {
        val (code, body) = post("/record/start", "")
        assertEquals(200, code)
        assertTrue(JSONObject(body).getBoolean("ok"))
        assertTrue(recordStarted)
        assertTrue(server.isRecording)
    }

    @Test
    fun `POST record stop`() {
        // Start first
        post("/record/start", "")
        assertTrue(server.isRecording)

        val (code, body) = post("/record/stop", "")
        assertEquals(200, code)
        assertTrue(JSONObject(body).getBoolean("ok"))
        assertTrue(recordStopped)
        assertFalse(server.isRecording)
    }

    @Test
    fun `POST record start when already recording`() {
        post("/record/start", "")
        val (code, body) = post("/record/start", "")
        assertEquals(200, code)
        assertTrue(JSONObject(body).has("message"))
    }

    @Test
    fun `POST record stop when not recording`() {
        val (code, body) = post("/record/stop", "")
        assertEquals(200, code)
        assertTrue(JSONObject(body).has("message"))
    }

    // --- /recordings ---

    @Test
    fun `GET recordings empty`() {
        val (code, body) = get("/recordings")
        assertEquals(200, code)
        val json = JSONObject(body)
        assertEquals(0, json.getJSONArray("recordings").length())
    }

    @Test
    fun `GET recordings lists files`() {
        File(tmpDir, "clip1.mp4").writeText("fake1")
        File(tmpDir, "clip2.mp4").writeText("fake2")

        val (code, body) = get("/recordings")
        assertEquals(200, code)
        val arr = JSONObject(body).getJSONArray("recordings")
        assertEquals(2, arr.length())
        val names = (0 until arr.length()).map { arr.getJSONObject(it).getString("name") }
        assertTrue(names.contains("clip1.mp4"))
        assertTrue(names.contains("clip2.mp4"))
    }

    @Test
    fun `GET recordings file download`() {
        File(tmpDir, "test.mp4").writeText("video-content")
        val (code, body) = get("/recordings/test.mp4")
        assertEquals(200, code)
        assertEquals("video-content", body)
    }

    @Test
    fun `GET recordings path traversal blocked`() {
        val (code, body) = get("/recordings/../../../etc/passwd")
        assertEquals(403, code)
        assertTrue(JSONObject(body).getString("error").contains("Invalid"))
    }

    @Test
    fun `GET recordings nonexistent file`() {
        val (code, _) = get("/recordings/nope.mp4")
        assertEquals(404, code)
    }

    // --- /video (MJPEG) ---

    @Test
    fun `GET video returns MJPEG content type`() {
        // Put a frame so the stream has data
        val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 1, 2, 3)
        frameBuffer.put(jpeg)

        val conn = URL("http://localhost:$port/video").openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = 2000
        conn.readTimeout = 2000

        assertEquals(200, conn.responseCode)
        assertTrue(conn.contentType.startsWith("multipart/x-mixed-replace"))
        assertTrue(conn.contentType.contains("spyglass_frame"))

        // Keep feeding frames so stream reads don't block
        val feeder = Thread {
            repeat(20) {
                Thread.sleep(30)
                frameBuffer.put(jpeg)
            }
        }
        feeder.start()

        // Read enough bytes to see a full MJPEG frame header
        val out = java.io.ByteArrayOutputStream()
        val stream = conn.inputStream
        val buf = ByteArray(64)
        var total = 0
        while (total < 200) {
            val n = stream.read(buf)
            if (n <= 0) break
            out.write(buf, 0, n)
            total += n
        }
        val header = out.toString()
        assertTrue("Expected boundary in: $header", header.contains("--spyglass_frame"))
        assertTrue("Expected image/jpeg in: $header", header.contains("image/jpeg"))

        conn.disconnect()
        feeder.join(2000)
    }

    // --- /audio ---

    @Test
    fun `GET audio returns not implemented`() {
        val (code, body) = get("/audio")
        assertEquals(501, code)
        assertTrue(JSONObject(body).getString("error").contains("not yet implemented"))
    }

    // --- 404 ---

    @Test
    fun `GET unknown path returns 404`() {
        val (code, body) = get("/nonexistent")
        assertEquals(404, code)
        assertTrue(JSONObject(body).getString("error").contains("Not found"))
    }
}
