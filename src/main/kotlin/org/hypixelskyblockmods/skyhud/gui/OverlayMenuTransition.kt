package org.hypixelskyblockmods.skyhud.gui

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen

class OverlayMenuTransition(
    private val reopenCommand: String,
) {
    private var incomingScreensToHide = 0
    private var transitionTicks = 0
    private var closeTicks = -1
    private var reopenTicks = -1
    private var retryTicks = -1
    private var attemptsRemaining = 0

    fun arm(screen: Screen?) {
        incomingScreensToHide = 2
        transitionTicks = 10
        OverlayTransitionGuard.arm(screen)
    }

    fun retainIncoming(screen: Screen, retainedScreen: Screen): Boolean {
        if (screen !is AbstractContainerScreen<*>) return false
        if (!isRefreshing() && (incomingScreensToHide <= 0 || transitionTicks <= 0)) return false
        if (!isRefreshing()) incomingScreensToHide--
        OverlayTransitionGuard.arm(retainedScreen)
        return true
    }

    fun scheduleRefresh() {
        if (isRefreshing()) return
        attemptsRemaining = 3
        closeTicks = 1
    }

    fun isRefreshing(): Boolean = closeTicks >= 0 || reopenTicks >= 0 || retryTicks >= 0

    fun acceptsBackingUpdates(): Boolean = !isRefreshing() && transitionTicks == 0

    fun onRecognized() {
        incomingScreensToHide = 0
        transitionTicks = 0
        clearRefresh()
    }

    fun tick(client: Minecraft, screen: Screen?) {
        if (isRefreshing()) OverlayTransitionGuard.arm(screen)
        if (transitionTicks > 0) transitionTicks--
        if (transitionTicks == 0) incomingScreensToHide = 0

        when {
            closeTicks >= 0 -> {
                closeTicks--
                if (closeTicks <= 0) {
                    closeTicks = -1
                    arm(screen)
                    client.player?.closeContainer()
                    reopenTicks = 3
                }
            }
            reopenTicks >= 0 -> {
                reopenTicks--
                if (reopenTicks <= 0) {
                    reopenTicks = -1
                    arm(screen)
                    client.player?.connection?.sendCommand(reopenCommand)
                    attemptsRemaining--
                    retryTicks = 20
                }
            }
            retryTicks >= 0 -> {
                retryTicks--
                if (retryTicks <= 0) {
                    retryTicks = -1
                    if (attemptsRemaining > 0) closeTicks = 1
                }
            }
        }
    }

    fun clear(screen: Screen?) {
        incomingScreensToHide = 0
        transitionTicks = 0
        clearRefresh()
        OverlayTransitionGuard.clear(screen)
    }

    private fun clearRefresh() {
        closeTicks = -1
        reopenTicks = -1
        retryTicks = -1
        attemptsRemaining = 0
    }
}
