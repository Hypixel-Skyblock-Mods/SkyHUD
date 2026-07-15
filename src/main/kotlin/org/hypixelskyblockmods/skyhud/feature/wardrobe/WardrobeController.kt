package org.hypixelskyblockmods.skyhud.feature.wardrobe

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import org.hypixelskyblockmods.skyhud.config.SkyHudConfigManager
import org.hypixelskyblockmods.skyhud.feature.sets.SetCollectionScreen
import org.hypixelskyblockmods.skyhud.platform.ScreenCompat

object WardrobeController {
    private var activeScreen: SetCollectionScreen? = null

    fun onScreenOpened(client: Minecraft, screen: Screen): Boolean {
        if (!SkyHudConfigManager.config.huds.wardrobe.enabled) return false
        val target = WardrobeDetector.detect(screen) ?: return false
        WardrobeRepository.sets.remember(target.page, target.menu)

        val overlay = activeScreen ?: SetCollectionScreen(
            screenName = "SkyHUD Wardrobe",
            heading = "WARDROBE",
            searchHint = "Search armor...",
            setLabel = "OUTFIT",
            repository = WardrobeRepository.sets,
            closed = ::onOverlayClosed,
        ).also {
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
