package org.hypixelskyblockmods.skyhud.feature.loadouts

/** Keeps a requested page open while its inventory contents arrive separately. */
internal class LoadoutPageLoadGuard {
    private var waitingUntil: Long? = null

    fun begin(now: Long) {
        waitingUntil = now + 2_000L
    }

    fun isWaiting(now: Long): Boolean = waitingUntil?.let { now < it } == true

    fun shouldRefresh(backingMenuChanged: Boolean, now: Long): Boolean {
        if (isWaiting(now)) return false
        if (waitingUntil != null) {
            clear()
            // Recover even if the server never opened the requested page.
            return true
        }
        return backingMenuChanged
    }

    fun clear() {
        waitingUntil = null
    }
}
