package org.hypixelskyblockmods.skyhud.feature.wardrobe

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import org.hypixelskyblockmods.skyhud.platform.ScreenCompat

object WardrobeController {
    private var activeScreen: WardrobeScreen? = null

    fun onScreenOpened(client: Minecraft, screen: Screen) {
        val target = WardrobeDetector.detect(screen) ?: return
        WardrobeRepository.remember(target.page, target.menu)

        val overlay = activeScreen ?: WardrobeScreen(::onOverlayClosed).also {
            activeScreen = it
        }
        overlay.bind(target)

        client.execute {
            if (ScreenCompat.currentScreen() === screen) {
                ScreenCompat.setScreen(overlay)
            }
        }
    }

    fun onClientTick(client: Minecraft) {
        val overlay = activeScreen ?: return
        if (ScreenCompat.currentScreen() !== overlay) return
        overlay.refreshBackingMenu(client.player?.containerMenu)
    }

    private fun onOverlayClosed() {
        activeScreen = null
    }
}
