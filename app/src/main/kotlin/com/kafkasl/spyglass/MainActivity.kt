package com.kafkasl.spyglass

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.net.Inet4Address
import java.net.NetworkInterface

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private val requiredPermissions = buildList {
        add(Manifest.permission.CAMERA)
        add(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
    }.toTypedArray()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        statusText = TextView(this).apply {
            textSize = 16f
            setPadding(32, 32, 32, 32)
        }
        setContentView(statusText)

        if (hasPermissions()) {
            startService()
        } else {
            ActivityCompat.requestPermissions(this, requiredPermissions, 1)
        }
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<String>, results: IntArray) {
        super.onRequestPermissionsResult(code, perms, results)
        if (results.all { it == PackageManager.PERMISSION_GRANTED }) {
            startService()
        } else {
            statusText.text = "Camera and audio permissions required.\nPlease grant them in Settings."
        }
    }

    private fun hasPermissions() = requiredPermissions.all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun startService() {
        val intent = Intent(this, CameraService::class.java)
        ContextCompat.startForegroundService(this, intent)
        updateStatus()
    }

    private fun updateStatus() {
        val ip = getDeviceIp()
        statusText.text = buildString {
            appendLine("🔭 Spyglass")
            appendLine()
            appendLine("Status: Running")
            appendLine("Stream: http://$ip:$PORT/video")
            appendLine("Snap:   http://$ip:$PORT/snap")
            appendLine("Status: http://$ip:$PORT/status")
            appendLine("Config: http://$ip:$PORT/config")
            appendLine()
            appendLine("Endpoints:")
            appendLine("  GET  /video         MJPEG stream")
            appendLine("  GET  /audio         audio (stub)")
            appendLine("  GET  /snap          JPEG snapshot")
            appendLine("  GET  /status        JSON status")
            appendLine("  GET  /config        current config")
            appendLine("  POST /config        update config")
            appendLine("  POST /record/start  start recording")
            appendLine("  POST /record/stop   stop recording")
            appendLine("  GET  /recordings    list recordings")
        }
    }

    private fun getDeviceIp(): String {
        try {
            NetworkInterface.getNetworkInterfaces()?.toList()?.forEach { iface ->
                iface.inetAddresses?.toList()?.forEach { addr ->
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        return addr.hostAddress ?: "unknown"
                    }
                }
            }
        } catch (_: Exception) {}
        return "unknown"
    }
}
