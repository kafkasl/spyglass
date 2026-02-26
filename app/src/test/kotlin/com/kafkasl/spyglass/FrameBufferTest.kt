package com.kafkasl.spyglass

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger

class FrameBufferTest {

    @Test
    fun `empty buffer returns null`() {
        val buf = FrameBuffer()
        assertNull(buf.get())
    }

    @Test
    fun `put then get returns frame`() {
        val buf = FrameBuffer()
        val data = byteArrayOf(1, 2, 3)
        buf.put(data)
        val (frame, num) = buf.get()!!
        assertArrayEquals(data, frame)
        assertEquals(1L, num)
    }

    @Test
    fun `frame number increments`() {
        val buf = FrameBuffer()
        buf.put(byteArrayOf(1))
        assertEquals(1L, buf.get()!!.second)
        buf.put(byteArrayOf(2))
        assertEquals(2L, buf.get()!!.second)
        buf.put(byteArrayOf(3))
        assertEquals(3L, buf.get()!!.second)
    }

    @Test
    fun `latest frame wins`() {
        val buf = FrameBuffer()
        buf.put(byteArrayOf(1))
        buf.put(byteArrayOf(2))
        buf.put(byteArrayOf(3))
        assertArrayEquals(byteArrayOf(3), buf.get()!!.first)
    }

    @Test
    fun `clear resets buffer`() {
        val buf = FrameBuffer()
        buf.put(byteArrayOf(1))
        buf.clear()
        assertNull(buf.get())
    }

    @Test
    fun `concurrent writers do not corrupt`() {
        val buf = FrameBuffer()
        val threads = 10
        val writes = 100
        val latch = CountDownLatch(threads)
        val errors = AtomicInteger(0)

        repeat(threads) { t ->
            Thread {
                try {
                    repeat(writes) { w ->
                        buf.put(byteArrayOf(t.toByte(), w.toByte()))
                    }
                } catch (_: Exception) {
                    errors.incrementAndGet()
                } finally {
                    latch.countDown()
                }
            }.start()
        }

        latch.await()
        assertEquals(0, errors.get())
        // Should have some frame
        assertNotNull(buf.get())
        assertEquals((threads * writes).toLong(), buf.get()!!.second)
    }
}
