package com.kafkasl.spyglass

import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer

/**
 * Tests for YUV_420_888 → NV21 conversion.
 * Exercises both semi-planar (pixelStride=2) and planar (pixelStride=1) paths.
 */
class YuvConverterTest {

    // --- Helpers ---

    /** Build a Y plane buffer with optional row padding. */
    private fun makeYPlane(w: Int, h: Int, rowStride: Int, fill: (row: Int, col: Int) -> Byte): ByteBuffer {
        val buf = ByteBuffer.allocate(rowStride * h)
        for (row in 0 until h) {
            for (col in 0 until w) {
                buf.put(row * rowStride + col, fill(row, col))
            }
        }
        return buf
    }

    /** Build planar U or V buffer (pixelStride=1). */
    private fun makePlanarUVPlane(uvW: Int, uvH: Int, rowStride: Int, fill: (row: Int, col: Int) -> Byte): ByteBuffer {
        val buf = ByteBuffer.allocate(rowStride * uvH)
        for (row in 0 until uvH) {
            for (col in 0 until uvW) {
                buf.put(row * rowStride + col, fill(row, col))
            }
        }
        return buf
    }

    /** Build semi-planar interleaved VU buffer (pixelStride=2, as seen from V plane perspective). */
    private fun makeSemiPlanarBuffers(uvW: Int, uvH: Int, rowStride: Int,
                                      fillV: (row: Int, col: Int) -> Byte,
                                      fillU: (row: Int, col: Int) -> Byte): Pair<ByteBuffer, ByteBuffer> {
        // In semi-planar, V and U share one buffer: V0 U0 V1 U1 ...
        // V plane starts at offset 0, U plane starts at offset 1, both have pixelStride=2
        val size = rowStride * (uvH - 1) + uvW * 2  // last row may not need full stride
        val backing = ByteArray(size)
        for (row in 0 until uvH) {
            for (col in 0 until uvW) {
                val base = row * rowStride + col * 2
                backing[base] = fillV(row, col)
                if (base + 1 < backing.size) {
                    backing[base + 1] = fillU(row, col)
                }
            }
        }
        val vBuf = ByteBuffer.wrap(backing)
        val uBuf = ByteBuffer.wrap(backing, 1, backing.size - 1).slice()
        return vBuf to uBuf
    }

    // --- Tests: planar path (pixelStride=1) ---

    @Test
    fun `planar 4x4 produces correct NV21`() {
        val w = 4; val h = 4
        val uvW = 2; val uvH = 2

        val yBuf = makeYPlane(w, h, w) { r, c -> ((r * w + c) and 0xFF).toByte() }
        val uBuf = makePlanarUVPlane(uvW, uvH, uvW) { r, c -> (100 + r * uvW + c).toByte() }
        val vBuf = makePlanarUVPlane(uvW, uvH, uvW) { r, c -> (200 + r * uvW + c).toByte() }

        val nv21 = YuvConverter.yuv420toNv21(w, h, yBuf, uBuf, vBuf, w, uvW, 1)

        assertEquals(w * h * 3 / 2, nv21.size)

        // Y plane: first w*h bytes
        for (r in 0 until h) {
            for (c in 0 until w) {
                assertEquals("Y[$r,$c]", ((r * w + c) and 0xFF).toByte(), nv21[r * w + c])
            }
        }

        // UV plane: interleaved V, U pairs
        val uvStart = w * h
        for (r in 0 until uvH) {
            for (c in 0 until uvW) {
                val idx = uvStart + (r * uvW + c) * 2
                assertEquals("V[$r,$c]", (200 + r * uvW + c).toByte(), nv21[idx])
                assertEquals("U[$r,$c]", (100 + r * uvW + c).toByte(), nv21[idx + 1])
            }
        }
    }

    @Test
    fun `planar with row padding`() {
        val w = 4; val h = 4; val yRowStride = 8; val uvRowStride = 4
        val uvW = 2; val uvH = 2

        val yBuf = makeYPlane(w, h, yRowStride) { r, c -> (r * 10 + c).toByte() }
        val uBuf = makePlanarUVPlane(uvW, uvH, uvRowStride) { _, c -> (50 + c).toByte() }
        val vBuf = makePlanarUVPlane(uvW, uvH, uvRowStride) { _, c -> (150 + c).toByte() }

        val nv21 = YuvConverter.yuv420toNv21(w, h, yBuf, uBuf, vBuf, yRowStride, uvRowStride, 1)

        // Y data should skip padding
        assertEquals(0.toByte(), nv21[0])   // row0 col0
        assertEquals(3.toByte(), nv21[3])   // row0 col3
        assertEquals(10.toByte(), nv21[4])  // row1 col0 (skipped padding at positions 4-7 in buffer)
    }

    // --- Tests: semi-planar path (pixelStride=2) ---

    @Test
    fun `semi-planar 4x4 produces correct NV21`() {
        val w = 4; val h = 4
        val uvW = 2; val uvH = 2
        val uvRowStride = uvW * 2  // 4

        val yBuf = makeYPlane(w, h, w) { r, c -> ((r * w + c) and 0xFF).toByte() }
        val (vBuf, uBuf) = makeSemiPlanarBuffers(uvW, uvH, uvRowStride,
            fillV = { r, c -> (200 + r * uvW + c).toByte() },
            fillU = { r, c -> (100 + r * uvW + c).toByte() }
        )

        val nv21 = YuvConverter.yuv420toNv21(w, h, yBuf, uBuf, vBuf, w, uvRowStride, 2)

        assertEquals(w * h * 3 / 2, nv21.size)

        // Y plane
        for (i in 0 until w * h) {
            assertEquals("Y[$i]", (i and 0xFF).toByte(), nv21[i])
        }

        // UV plane: V, U interleaved
        val uvStart = w * h
        for (r in 0 until uvH) {
            for (c in 0 until uvW) {
                val idx = uvStart + (r * uvW + c) * 2
                assertEquals("V[$r,$c]", (200 + r * uvW + c).toByte(), nv21[idx])
                assertEquals("U[$r,$c]", (100 + r * uvW + c).toByte(), nv21[idx + 1])
            }
        }
    }

    @Test
    fun `semi-planar with row padding`() {
        val w = 4; val h = 4
        val uvW = 2; val uvH = 2
        val uvRowStride = 8  // extra padding

        val yBuf = makeYPlane(w, h, w) { _, _ -> 128.toByte() }
        // Build semi-planar with padded rows
        val backingSize = uvRowStride * (uvH - 1) + uvW * 2
        val backing = ByteArray(backingSize)
        for (row in 0 until uvH) {
            for (col in 0 until uvW) {
                backing[row * uvRowStride + col * 2] = (200 + col).toByte()       // V
                if (row * uvRowStride + col * 2 + 1 < backing.size)
                    backing[row * uvRowStride + col * 2 + 1] = (100 + col).toByte() // U
            }
        }
        val vBuf = ByteBuffer.wrap(backing)
        val uBuf = ByteBuffer.wrap(backing, 1, backing.size - 1).slice()

        val nv21 = YuvConverter.yuv420toNv21(w, h, yBuf, uBuf, vBuf, w, uvRowStride, 2)

        val uvStart = w * h
        // Row 0: V0=200, U0=100, V1=201, U1=101
        assertEquals(200.toByte(), nv21[uvStart])
        assertEquals(100.toByte(), nv21[uvStart + 1])
        assertEquals(201.toByte(), nv21[uvStart + 2])
        assertEquals(101.toByte(), nv21[uvStart + 3])
    }

    // --- Edge cases ---

    @Test
    fun `minimum 2x2 image`() {
        val w = 2; val h = 2
        val yBuf = makeYPlane(w, h, w) { _, _ -> 42.toByte() }
        val uBuf = makePlanarUVPlane(1, 1, 1) { _, _ -> 10.toByte() }
        val vBuf = makePlanarUVPlane(1, 1, 1) { _, _ -> 20.toByte() }

        val nv21 = YuvConverter.yuv420toNv21(w, h, yBuf, uBuf, vBuf, w, 1, 1)

        assertEquals(6, nv21.size) // 2*2 + 2*2/4*2 = 4 + 2
        // Y
        assertEquals(42.toByte(), nv21[0])
        assertEquals(42.toByte(), nv21[3])
        // VU
        assertEquals(20.toByte(), nv21[4])
        assertEquals(10.toByte(), nv21[5])
    }

    @Test
    fun `output size is correct for various dimensions`() {
        for ((w, h) in listOf(2 to 2, 4 to 4, 16 to 16, 640 to 480, 1280 to 720)) {
            val yBuf = ByteBuffer.allocate(w * h)
            val uBuf = ByteBuffer.allocate(w / 2 * h / 2)
            val vBuf = ByteBuffer.allocate(w / 2 * h / 2)
            val nv21 = YuvConverter.yuv420toNv21(w, h, yBuf, uBuf, vBuf, w, w / 2, 1)
            assertEquals("${w}x${h}", w * h * 3 / 2, nv21.size)
        }
    }
}
