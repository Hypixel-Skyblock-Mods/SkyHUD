package org.hypixelskyblockmods.skyhud.feature.enderchest

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import org.hypixelskyblockmods.skyhud.config.SkyHudConfigManager
import org.hypixelskyblockmods.skyhud.platform.ScreenCompat

object EnderChestController {
    private var activeScreen: EnderChestScreen? = null

    fun onScreenOpened(client: Minecraft, screen: Screen) {
        if (!SkyHudConfigManager.config.huds.enderChest.enabled) return
        val target = EnderChestDetector.detect(screen) ?: return
        EnderChestRepository.remember(target.page, target.menu)

        val overlay = activeScreen ?: EnderChestScreen(::onOverlayClosed).also {
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
