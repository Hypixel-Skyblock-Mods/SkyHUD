package org.hypixelskyblockmods.skyhud.gui

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen

class OverlayMenuTransition(
    private val reopenCommand: String,
) {
    private var incomingScreensToHide = 0
    private var transitionTicks = 0
    private var refreshTicks = -1

    fun arm(screen: Screen?) {
        incomingScreensToHide = 2
        transitionTicks = 10
        OverlayTransitionGuard.arm(screen)
    }

    fun retainIncoming(screen: Screen): Boolean {
        if (screen !is AbstractContainerScreen<*> || incomingScreensToHide <= 0 || transitionTicks <= 0) return false
        incomingScreensToHide--
        return true
    }

    fun scheduleRefresh() {
        if (refreshTicks < 0) refreshTicks = 2
    }

    fun acceptsBackingUpdates(): Boolean = refreshTicks < 0 && transitionTicks == 0

    fun onRecognized() {
        incomingScreensToHide = 0
        transitionTicks = 0
        refreshTicks = -1
    }

    fun tick(client: Minecraft, screen: Screen?) {
        if (transitionTicks > 0) transitionTicks--
        if (transitionTicks == 0) incomingScreensToHide = 0
        if (refreshTicks < 0) return
        refreshTicks--
        if (refreshTicks > 0) return

        refreshTicks = -1
        arm(screen)
        client.player?.connection?.sendCommand(reopenCommand)
    }

    fun clear(screen: Screen?) {
        incomingScreensToHide = 0
        transitionTicks = 0
        refreshTicks = -1
        OverlayTransitionGuard.clear(screen)
    }
}
