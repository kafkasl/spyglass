package com.kafkasl.spyglass

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ConfigTest {

    @Test
    fun `default config`() {
        val c = CameraConfig()
        assertEquals(0, c.cameraId)
        assertEquals(1280, c.width)
        assertEquals(720, c.height)
        assertEquals(15, c.fps)
        assertEquals(80, c.jpegQuality)
    }

    @Test
    fun `toJson roundtrip`() {
        val c = CameraConfig(cameraId = 1, width = 640, height = 480, fps = 30, jpegQuality = 50)
        val json = c.toJson()
        assertEquals(1, json.getInt("camera"))
        assertEquals("640x480", json.getString("resolution"))
        assertEquals(30, json.getInt("fps"))
        assertEquals(50, json.getInt("jpegQuality"))
    }

    @Test
    fun `fromJson full override`() {
        val json = JSONObject().apply {
            put("camera", 1)
            put("resolution", "640x480")
            put("fps", 30)
            put("jpegQuality", 50)
        }
        val c = CameraConfig.fromJson(json)
        assertEquals(1, c.cameraId)
        assertEquals(640, c.width)
        assertEquals(480, c.height)
        assertEquals(30, c.fps)
        assertEquals(50, c.jpegQuality)
    }

    @Test
    fun `fromJson partial override keeps defaults`() {
        val json = JSONObject().apply { put("fps", 10) }
        val c = CameraConfig.fromJson(json)
        assertEquals(0, c.cameraId) // default
        assertEquals(1280, c.width) // default
        assertEquals(720, c.height) // default
        assertEquals(10, c.fps) // overridden
        assertEquals(80, c.jpegQuality) // default
    }

    @Test
    fun `fromJson with current config`() {
        val current = CameraConfig(cameraId = 1, width = 640, height = 480, fps = 5, jpegQuality = 90)
        val json = JSONObject().apply { put("fps", 20) }
        val c = CameraConfig.fromJson(json, current)
        assertEquals(1, c.cameraId) // kept from current
        assertEquals(640, c.width) // kept from current
        assertEquals(480, c.height) // kept from current
        assertEquals(20, c.fps) // overridden
        assertEquals(90, c.jpegQuality) // kept from current
    }

    @Test
    fun `fromJson bad resolution falls back`() {
        val json = JSONObject().apply { put("resolution", "bad") }
        val c = CameraConfig.fromJson(json)
        assertEquals(1280, c.width) // falls back to default
        assertEquals(720, c.height) // falls back to default
    }

    @Test
    fun `fromJson empty object returns defaults`() {
        val c = CameraConfig.fromJson(JSONObject())
        assertEquals(CameraConfig(), c)
    }
}
