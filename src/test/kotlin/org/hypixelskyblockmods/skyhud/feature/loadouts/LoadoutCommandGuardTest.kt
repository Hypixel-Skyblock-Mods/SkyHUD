package org.hypixelskyblockmods.skyhud.feature.loadouts

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LoadoutCommandGuardTest {
    @Test
    fun `named commands and aliases keep the server flow native`() {
        for (command in listOf("loadout Farming", "loadouts Farming", "/LOADOUT Farming Set", "  loadout\t3  ")) {
            val guard = LoadoutCommandGuard()
            assertTrue(guard.onCommandSent(command, 100))
            assertTrue(guard.shouldKeepNative(100))
            // No menu may be open while the server is still processing the command.
            assertTrue(guard.shouldKeepNative(1_000))
        }
    }

    @Test
    fun `bare commands restore the custom browser immediately`() {
        for (command in listOf("loadout", "loadouts", "/LOADOUTS  ")) {
            val guard = LoadoutCommandGuard()
            guard.onCommandSent("loadout Farming", 100)
            assertTrue(guard.onCommandSent(command, 200))
            assertFalse(guard.shouldKeepNative(200))
        }
    }

    @Test
    fun `unrelated commands neither start nor clear the bypass`() {
        val guard = LoadoutCommandGuard()
        for (command in listOf("", "loadoutsettings Farming", "skyhud", "say loadout Farming")) {
            assertFalse(guard.onCommandSent(command, 100))
            assertFalse(guard.shouldKeepNative(100))
        }
        guard.onCommandSent("loadout Farming", 100)
        assertFalse(guard.onCommandSent("party hello", 200))
        assertTrue(guard.shouldKeepNative(200))
    }

    @Test
    fun `rejected commands expire without a menu ever opening`() {
        val guard = LoadoutCommandGuard()
        guard.onCommandSent("loadout Missing", 100)
        assertTrue(guard.shouldKeepNative(10_099))
        assertFalse(guard.shouldKeepNative(10_100))
    }

    @Test
    fun `profile changes clear the bypass`() {
        val guard = LoadoutCommandGuard()
        guard.onCommandSent("loadout Farming", 100)
        guard.clear()
        assertFalse(guard.shouldKeepNative(200))
    }
}
