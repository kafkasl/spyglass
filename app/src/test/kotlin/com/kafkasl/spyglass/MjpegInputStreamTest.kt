package com.kafkasl.spyglass

import org.junit.Assert.*
import org.junit.Test

class MjpegInputStreamTest {

    @Test
    fun `reads a single frame with boundary headers`() {
        val buf = FrameBuffer()
        val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 1, 2, 3, 0xFF.toByte(), 0xD9.toByte())
        buf.put(jpeg)

        val stream = MjpegInputStream(buf, "testbound", 30)
        val data = ByteArray(512)
        val n = stream.read(data, 0, data.size)
        assertTrue(n > 0)

        val result = String(data, 0, n)
        assertTrue(result.contains("--testbound"))
        assertTrue(result.contains("Content-Type: image/jpeg"))
        assertTrue(result.contains("Content-Length: ${jpeg.size}"))
    }

    @Test
    fun `reads multiple frames as new data arrives`() {
        val buf = FrameBuffer()
        val stream = MjpegInputStream(buf, "bound", 100)

        // Put first frame
        buf.put(byteArrayOf(1, 2))
        val data1 = ByteArray(256)
        val n1 = stream.read(data1, 0, data1.size)
        assertTrue(n1 > 0)
        assertTrue(String(data1, 0, n1).contains("--bound"))

        // Put second frame
        buf.put(byteArrayOf(3, 4))
        val data2 = ByteArray(256)
        val n2 = stream.read(data2, 0, data2.size)
        assertTrue(n2 > 0)
        assertTrue(String(data2, 0, n2).contains("--bound"))
    }

    @Test
    fun `single byte read works`() {
        val buf = FrameBuffer()
        buf.put(byteArrayOf(42))
        val stream = MjpegInputStream(buf, "b", 100)

        // Should be able to read byte by byte
        val firstByte = stream.read()
        assertTrue(firstByte >= 0)
        assertEquals('-'.code, firstByte) // starts with --b boundary
    }
}
