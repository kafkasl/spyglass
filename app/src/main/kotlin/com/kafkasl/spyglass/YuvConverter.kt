package com.kafkasl.spyglass

import java.nio.ByteBuffer

/**
 * Converts YUV_420_888 planes to NV21 byte array.
 * Extracted from CameraService for testability.
 */
object YuvConverter {

    /**
     * Convert YUV_420_888 plane data to NV21 format.
     *
     * @param w image width
     * @param h image height
     * @param yBuf Y plane buffer
     * @param uBuf U (Cb) plane buffer
     * @param vBuf V (Cr) plane buffer
     * @param yRowStride Y plane row stride
     * @param uvRowStride UV plane row stride
     * @param uvPixelStride UV plane pixel stride (1=planar, 2=semi-planar)
     * @return NV21 byte array (Y followed by interleaved VU)
     */
    fun yuv420toNv21(
        w: Int, h: Int,
        yBuf: ByteBuffer, uBuf: ByteBuffer, vBuf: ByteBuffer,
        yRowStride: Int, uvRowStride: Int, uvPixelStride: Int
    ): ByteArray {
        val nv21 = ByteArray(w * h * 3 / 2)

        // Copy Y plane row by row (rowStride may include padding)
        var pos = 0
        for (row in 0 until h) {
            yBuf.position(row * yRowStride)
            yBuf.get(nv21, pos, w)
            pos += w
        }

        // Copy UV planes interleaved as VUVU... (NV21)
        val uvH = h / 2
        val uvW = w / 2

        if (uvPixelStride == 2) {
            // Semi-planar: U and V buffers are already interleaved (common case)
            // V plane with pixelStride=2 gives us VUVU... which is exactly NV21
            for (row in 0 until uvH) {
                vBuf.position(row * uvRowStride)
                vBuf.get(nv21, pos, uvW * 2 - 1)
                pos += uvW * 2
                // Last V byte of the row needs the matching U
                if (pos - 1 < nv21.size) {
                    uBuf.position(row * uvRowStride + (uvW - 1) * uvPixelStride)
                    nv21[pos - 1] = uBuf.get()
                }
            }
        } else {
            // Planar: pixelStride == 1, manually interleave V and U
            for (row in 0 until uvH) {
                for (col in 0 until uvW) {
                    val uvIdx = row * uvRowStride + col * uvPixelStride
                    nv21[pos++] = vBuf.get(uvIdx)
                    nv21[pos++] = uBuf.get(uvIdx)
                }
            }
        }

        return nv21
    }
}
