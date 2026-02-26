package com.kafkasl.spyglass

import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * Thread-safe single-frame buffer for the latest JPEG.
 * Producer (camera) writes, multiple consumers (MJPEG clients) read.
 */
class FrameBuffer {
    private val lock = ReentrantReadWriteLock()
    private var frame: ByteArray? = null
    private var frameNumber: Long = 0

    fun put(jpeg: ByteArray) = lock.write {
        frame = jpeg
        frameNumber++
    }

    /** Returns (jpeg, frameNumber) or null if no frame yet. */
    fun get(): Pair<ByteArray, Long>? = lock.read {
        frame?.let { it to frameNumber }
    }

    fun clear() = lock.write {
        frame = null
        frameNumber = 0
    }
}
