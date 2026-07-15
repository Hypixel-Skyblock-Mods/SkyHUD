package org.hypixelskyblockmods.skyhud.gui

import net.minecraft.client.gui.screens.Screen

object OverlayTransitionGuard {
    private var retainedScreen: Screen? = null

    fun arm(screen: Screen?) {
        retainedScreen = screen
    }

    fun onIncomingScreen() {
        retainedScreen = null
    }

    fun consumeClose(currentScreen: Screen?): Boolean {
        val retained = retainedScreen ?: return false
        retainedScreen = null
        return retained === currentScreen
    }

    fun clear(screen: Screen?) {
        if (retainedScreen === screen) retainedScreen = null
    }
}
