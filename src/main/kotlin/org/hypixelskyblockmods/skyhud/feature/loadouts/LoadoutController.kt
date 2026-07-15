package org.hypixelskyblockmods.skyhud.feature.loadouts

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import org.hypixelskyblockmods.skyhud.config.SkyHudConfigManager
import org.hypixelskyblockmods.skyhud.platform.ScreenCompat

object LoadoutController {
    private var activeScreen: LoadoutScreen? = null

    fun onScreenOpened(client: Minecraft, screen: Screen) {
        if (!SkyHudConfigManager.config.loadouts.enabled) return
        val target = LoadoutDetector.detect(screen) ?: return
        LoadoutRepository.remember(target.page, target.menu)

        val overlay = activeScreen ?: LoadoutScreen(::onOverlayClosed).also {
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
