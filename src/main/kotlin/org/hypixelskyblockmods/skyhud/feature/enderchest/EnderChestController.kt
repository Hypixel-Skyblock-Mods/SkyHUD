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
    }

    fun onClientTick(client: Minecraft) {
        val current = ScreenCompat.currentScreen() ?: return
        var overlay = activeScreen
        if (overlay == null || current !== overlay) {
            onScreenOpened(client, current)
            overlay = activeScreen ?: return
            if (ScreenCompat.currentScreen() !== overlay) return
        }
        overlay.refreshBackingMenu(client.player?.containerMenu)
    }

    private fun onOverlayClosed() {
        activeScreen = null
    }
}
