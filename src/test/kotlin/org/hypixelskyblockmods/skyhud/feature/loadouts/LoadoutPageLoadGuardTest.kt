package org.hypixelskyblockmods.skyhud.feature.loadouts

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LoadoutPageLoadGuardTest {
    @Test
    fun `page opening before its items does not trigger a refresh`() {
        val guard = LoadoutPageLoadGuard()
        guard.begin(100)

        // The new backing menu exists, but the detector cannot accept it yet.
        for (now in listOf(150L, 600L, 1_500L)) {
            assertTrue(guard.isWaiting(now))
            assertFalse(guard.shouldRefresh(backingMenuChanged = true, now))
        }

        // Its items arrive and the controller recognizes and binds the page.
        guard.clear()
        assertFalse(guard.isWaiting(1_550))
        assertFalse(guard.shouldRefresh(backingMenuChanged = false, 1_550))
    }

    @Test
    fun `a page that never finishes loading eventually allows recovery`() {
        val guard = LoadoutPageLoadGuard()
        guard.begin(100)
        assertFalse(guard.shouldRefresh(backingMenuChanged = true, 2_099))
        assertTrue(guard.shouldRefresh(backingMenuChanged = true, 2_100))
        assertFalse(guard.isWaiting(2_100))
    }

    @Test
    fun `a navigation click with no server response also recovers`() {
        val guard = LoadoutPageLoadGuard()
        guard.begin(100)
        assertFalse(guard.shouldRefresh(backingMenuChanged = false, 2_099))
        assertTrue(guard.shouldRefresh(backingMenuChanged = false, 2_100))
        // The timeout is consumed; it does not keep restarting recovery.
        assertFalse(guard.shouldRefresh(backingMenuChanged = false, 2_150))
    }

    @Test
    fun `each step of multi page navigation gets time to receive its items`() {
        val guard = LoadoutPageLoadGuard()
        guard.begin(100)
        guard.clear() // The intermediate page is recognized.
        guard.begin(1_500)
        assertFalse(guard.shouldRefresh(backingMenuChanged = true, 2_500))
        assertTrue(guard.shouldRefresh(backingMenuChanged = true, 3_500))
    }

    @Test
    fun `leaving navigation restores normal stale menu recovery`() {
        val guard = LoadoutPageLoadGuard()
        guard.begin(100)
        guard.clear() // Closed overlay, changed profile, or entered native UI.
        assertFalse(guard.isWaiting(150))
        assertTrue(guard.shouldRefresh(backingMenuChanged = true, 150))
    }
}
