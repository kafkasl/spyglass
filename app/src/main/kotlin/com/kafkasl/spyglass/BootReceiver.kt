package com.kafkasl.spyglass

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * Starts the camera service on device boot.
 * Requires RECEIVE_BOOT_COMPLETED permission + battery optimization disabled.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.i("SpyglassBoot", "Boot completed — starting camera service")
            val serviceIntent = Intent(context, CameraService::class.java)
            ContextCompat.startForegroundService(context, serviceIntent)
        }
    }
}
