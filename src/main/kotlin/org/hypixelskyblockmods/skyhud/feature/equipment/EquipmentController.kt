package org.hypixelskyblockmods.skyhud.feature.equipment

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import org.hypixelskyblockmods.skyhud.config.SkyHudConfigManager
import org.hypixelskyblockmods.skyhud.feature.sets.SetCollectionScreen
import org.hypixelskyblockmods.skyhud.platform.ScreenCompat

object EquipmentController {
    private var activeScreen: SetCollectionScreen? = null

    fun onScreenOpened(client: Minecraft, screen: Screen): Boolean {
        if (!SkyHudConfigManager.config.huds.equipment.enabled) return false
        val target = EquipmentDetector.detect(screen) ?: return false
        EquipmentRepository.sets.remember(target.page, target.menu)

        val overlay = activeScreen ?: SetCollectionScreen(
            screenName = "SkyHUD Equipment",
            heading = "EQUIPMENT",
            searchHint = "Search equipment...",
            setLabel = "SET",
            repository = EquipmentRepository.sets,
            closed = ::onOverlayClosed,
        ).also {
            activeScreen = it
        }
        overlay.bind(target)

        if (ScreenCompat.currentScreen() === screen) {
            ScreenCompat.setScreen(overlay)
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
