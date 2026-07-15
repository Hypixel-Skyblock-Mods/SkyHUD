package org.hypixelskyblockmods.skyhud.feature.enderchest

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import org.hypixelskyblockmods.skyhud.config.SkyHudConfigManager
import org.hypixelskyblockmods.skyhud.platform.ScreenCompat

object EnderChestController {
    private var activeScreen: EnderChestScreen? = null

    fun onScreenOpened(client: Minecraft, screen: Screen): Boolean {
        if (!SkyHudConfigManager.config.huds.enderChest.enabled) return false
        val target = EnderChestDetector.detect(screen) ?: return false
        when (target) {
            is EnderChestTarget.Overview -> EnderChestRepository.rememberOverview(target.menu)
            is EnderChestTarget.Page -> {
                val total = target.totalEnderChestPages
                if (total != null) {
                    EnderChestRepository.rememberEnderChest(target.key.number, total, target.menu)
                } else {
                    EnderChestRepository.remember(target.key, target.menu)
                }
            }
        }

        val overlay = activeScreen ?: EnderChestScreen(::onOverlayClosed).also {
            activeScreen = it
        }
        overlay.bind(target)

        client.execute {
            if (ScreenCompat.currentScreen() === screen) {
                ScreenCompat.setScreen(overlay)
            }
        }
        return true
    }

    fun onClientTick(client: Minecraft) {
        val current = ScreenCompat.currentScreen() ?: return
        var overlay = activeScreen
        if (overlay == null || current !== overlay) {
            if (!onScreenOpened(client, current)) return
            overlay = activeScreen ?: return
            if (ScreenCompat.currentScreen() !== overlay) ScreenCompat.setScreen(overlay)
        }
        overlay.refreshBackingMenu(client.player?.containerMenu)
    }

    private fun onOverlayClosed() {
        activeScreen = null
    }
}
