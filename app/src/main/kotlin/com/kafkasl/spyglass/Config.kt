package com.kafkasl.spyglass

import org.json.JSONObject

/** Runtime-mutable camera configuration. Thread-safe via @Volatile + copy-on-write. */
data class CameraConfig(
    val cameraId: Int = 0,         // 0=back, 1=front
    val width: Int = 1280,
    val height: Int = 720,
    val fps: Int = 15,
    val jpegQuality: Int = 80      // 1-100
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("camera", cameraId)
        put("resolution", "${width}x${height}")
        put("fps", fps)
        put("jpegQuality", jpegQuality)
    }

    companion object {
        fun fromJson(json: JSONObject, current: CameraConfig = CameraConfig()): CameraConfig {
            val res = json.optString("resolution", "${current.width}x${current.height}")
            val parts = res.split("x")
            return CameraConfig(
                cameraId = json.optInt("camera", current.cameraId),
                width = parts.getOrNull(0)?.toIntOrNull() ?: current.width,
                height = parts.getOrNull(1)?.toIntOrNull() ?: current.height,
                fps = json.optInt("fps", current.fps),
                jpegQuality = json.optInt("jpegQuality", current.jpegQuality)
            )
        }
    }
}

const val PORT = 4747
const val CHANNEL_ID = "spyglass_channel"
const val NOTIFICATION_ID = 1
