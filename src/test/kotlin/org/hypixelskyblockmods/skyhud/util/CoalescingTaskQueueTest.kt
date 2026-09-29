package org.hypixelskyblockmods.skyhud.util

import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.io.TempDir

@Timeout(10)
class CoalescingTaskQueueTest {
    private val executor = Executors.newSingleThreadExecutor()
    private val failures = CopyOnWriteArrayList<Exception>()
    private val queue = CoalescingTaskQueue<String>(executor) { _, exception -> failures += exception }

    @AfterEach
    fun stopWorker() {
        executor.shutdownNow()
    }

    @Test
    fun `slow persistence never blocks the caller thread`() {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val caller = Thread.currentThread().threadId()
        var worker = caller
        queue.submit("storage") {
            worker = Thread.currentThread().threadId()
            started.countDown()
            assertTrue(release.await(5, TimeUnit.SECONDS))
        }
        try {
            assertTrue(started.await(5, TimeUnit.SECONDS))
            assertNotEquals(caller, worker)
            // A second close/save still returns while the first disk operation is blocked.
            queue.submit("storage") {}
            assertEquals(1L, release.count)
        } finally {
            release.countDown()
        }
        queue.awaitIdle()
        assertTrue(failures.isEmpty())
    }

    @Test
    fun `only newest pending snapshots are saved and profiles remain separate`() {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val written = CopyOnWriteArrayList<String>()
        queue.submit("in-flight") {
            started.countDown()
            assertTrue(release.await(5, TimeUnit.SECONDS))
        }
        try {
            assertTrue(started.await(5, TimeUnit.SECONDS))
            queue.submit("account-a/profile-a/storage") { written += "old" }
            queue.submit("account-a/profile-a/storage") { written += "new" }
            queue.submit("account-a/profile-b/storage") { written += "other profile" }
        } finally {
            release.countDown()
        }
        queue.awaitIdle()
        assertEquals(listOf("new", "other profile"), written)
        assertTrue(failures.isEmpty())
    }

    @Test
    fun `clear cannot be undone by an earlier in-flight save`(@TempDir directory: Path) {
        val file = directory.resolve("storage.json")
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        queue.submit("storage") {
            started.countDown()
            assertTrue(release.await(5, TimeUnit.SECONDS))
            Files.writeString(file, "old snapshot")
        }
        try {
            assertTrue(started.await(5, TimeUnit.SECONDS))
            queue.submit("storage") { Files.writeString(file, "pending snapshot") }
            queue.submit("storage") { Files.deleteIfExists(file) }
        } finally {
            release.countDown()
        }
        queue.awaitIdle()
        assertFalse(Files.exists(file))
        assertTrue(failures.isEmpty())
    }

    @Test
    fun `a failed save does not stop subsequent cache writes`() {
        val written = CopyOnWriteArrayList<String>()
        queue.submit("broken") { throw IllegalStateException("Disk failure") }
        queue.submit("healthy") { written += "saved" }

        queue.awaitIdle()

        assertEquals(listOf("saved"), written)
        assertEquals(1, failures.size)
        assertEquals("Disk failure", failures.single().message)
    }
}
