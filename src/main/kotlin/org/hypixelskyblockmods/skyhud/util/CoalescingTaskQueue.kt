package org.hypixelskyblockmods.skyhud.util

import java.util.concurrent.ExecutorService

/** Serializes background work, retaining only the latest pending operation for each cache file. */
internal class CoalescingTaskQueue<K>(
    private val executor: ExecutorService,
    private val onFailure: (K, Exception) -> Unit,
) {
    private val lock = Any()
    private val pending = linkedMapOf<K, () -> Unit>()
    private var running = false

    fun submit(key: K, operation: () -> Unit) {
        synchronized(lock) {
            pending[key] = operation
            if (!running) {
                running = true
                executor.execute(::drain)
            }
        }
    }

    /** Only used before reading a different profile and during client shutdown, never on menu close. */
    fun awaitIdle() {
        executor.submit {}.get()
    }

    private fun drain() {
        while (true) {
            val (key, operation) = synchronized(lock) {
                if (pending.isEmpty()) {
                    running = false
                    return
                }
                val entry = pending.entries.first()
                val next = entry.key to entry.value
                pending.remove(entry.key)
                next
            }
            try {
                operation()
            } catch (exception: Exception) {
                onFailure(key, exception)
            }
        }
    }
}
