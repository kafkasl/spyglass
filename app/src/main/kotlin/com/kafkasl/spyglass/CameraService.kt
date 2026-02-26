package com.kafkasl.spyglass

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.os.*
import android.util.Log
import android.util.Size
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleService
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.Executors

private const val TAG = "SpyglassService"

class CameraService : LifecycleService() {

    private var server: SpyglassServer? = null
    private val frameBuffer = FrameBuffer()
    private var wakeLock: PowerManager.WakeLock? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private var recordingsDir: File? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        recordingsDir = File(getExternalFilesDir(null), "recordings").also { it.mkdirs() }
        startForeground(NOTIFICATION_ID, buildNotification("Starting..."))
        acquireWakeLock()
        startServer()
        startCamera(server?.config ?: CameraConfig())
    }

    override fun onDestroy() {
        server?.stop()
        server = null
        cameraProvider?.unbindAll()
        cameraExecutor.shutdown()
        releaseWakeLock()
        frameBuffer.clear()
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        return START_STICKY
    }

    // --- Server ---

    private fun startServer() {
        server = SpyglassServer(
            port = PORT,
            frameBuffer = frameBuffer,
            onConfigChange = ::applyConfig,
            onRecordStart = ::startRecording,
            onRecordStop = ::stopRecording,
            statusProvider = ::buildStatus,
            recordingsDir = recordingsDir
        ).also { it.start() }
        Log.i(TAG, "HTTP server started on port $PORT")
        updateNotification("Streaming on :$PORT")
    }

    // --- Camera ---

    private fun startCamera(config: CameraConfig) {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            val provider = future.get()
            cameraProvider = provider
            bindCamera(provider, config)
        }, ContextCompat.getMainExecutor(this))
    }

    private fun bindCamera(provider: ProcessCameraProvider, config: CameraConfig) {
        provider.unbindAll()

        val selector = if (config.cameraId == 1)
            CameraSelector.DEFAULT_FRONT_CAMERA
        else
            CameraSelector.DEFAULT_BACK_CAMERA

        @Suppress("DEPRECATION")
        val analysis = ImageAnalysis.Builder()
            .setTargetResolution(Size(config.width, config.height))
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
            .build()

        analysis.setAnalyzer(cameraExecutor) { imageProxy ->
            val jpeg = imageProxyToJpeg(imageProxy, config.jpegQuality)
            if (jpeg != null) frameBuffer.put(jpeg)
            imageProxy.close()
        }

        try {
            provider.bindToLifecycle(this as LifecycleOwner, selector, analysis)
            Log.i(TAG, "Camera bound: ${config.width}x${config.height} @ ${config.fps}fps, camera=${config.cameraId}")
        } catch (e: Exception) {
            Log.e(TAG, "Camera bind failed", e)
        }
    }

    private fun applyConfig(config: CameraConfig) {
        cameraProvider?.let { bindCamera(it, config) }
        updateNotification("Streaming on :$PORT (${config.width}x${config.height})")
    }

    // --- Recording (stub — saves snapshots, real recording needs MediaRecorder) ---

    @Volatile private var recordingActive = false

    private fun startRecording(): Boolean {
        // TODO: implement proper MediaRecorder-based recording
        recordingActive = true
        return true
    }

    private fun stopRecording(): Boolean {
        recordingActive = false
        return true
    }

    // --- Status ---

    private fun buildStatus(): JSONObject {
        val bm = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val battery = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)

        val stat = StatFs(Environment.getDataDirectory().path)
        val freeBytes = stat.availableBlocksLong * stat.blockSizeLong
        val totalBytes = stat.blockCountLong * stat.blockSizeLong

        return JSONObject().apply {
            put("config", (server?.config ?: CameraConfig()).toJson())
            put("recording", server?.isRecording ?: false)
            put("battery", battery)
            put("disk", JSONObject().apply {
                put("freeBytes", freeBytes)
                put("totalBytes", totalBytes)
                put("freeMB", freeBytes / (1024 * 1024))
            })
            put("uptime", SystemClock.elapsedRealtime() / 1000)
        }
    }

    // --- YUV to JPEG ---

    private fun imageProxyToJpeg(image: ImageProxy, quality: Int): ByteArray? {
        return try {
            val yBuffer = image.planes[0].buffer
            val uBuffer = image.planes[1].buffer
            val vBuffer = image.planes[2].buffer

            val ySize = yBuffer.remaining()
            val uSize = uBuffer.remaining()
            val vSize = vBuffer.remaining()

            val nv21 = ByteArray(ySize + uSize + vSize)
            yBuffer.get(nv21, 0, ySize)
            vBuffer.get(nv21, ySize, vSize)
            uBuffer.get(nv21, ySize + vSize, uSize)

            val yuvImage = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
            val out = ByteArrayOutputStream()
            yuvImage.compressToJpeg(Rect(0, 0, image.width, image.height), quality, out)
            out.toByteArray()
        } catch (e: Exception) {
            Log.w(TAG, "JPEG conversion failed", e)
            null
        }
    }

    // --- Notification ---

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, "Camera Server",
            NotificationManager.IMPORTANCE_LOW
        ).apply { description = "Spyglass camera server status" }
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
    }

    private fun buildNotification(text: String): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pending = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Spyglass")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentIntent(pending)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, buildNotification(text))
    }

    // --- Wake Lock ---

    private fun acquireWakeLock() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "spyglass:streaming").apply {
            acquire(24 * 60 * 60 * 1000L) // 24h max
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }
}
