package org.hypixelskyblockmods.skyhud.feature.wardrobe

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import org.hypixelskyblockmods.skyhud.config.SkyHudConfigManager
import org.hypixelskyblockmods.skyhud.feature.sets.SetCollectionScreen
import org.hypixelskyblockmods.skyhud.platform.ScreenCompat

object WardrobeController {
    private var activeScreen: SetCollectionScreen? = null

    fun onScreenOpened(client: Minecraft, screen: Screen) {
        if (!SkyHudConfigManager.config.wardrobe.enabled) return
        val target = WardrobeDetector.detect(screen) ?: return
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
