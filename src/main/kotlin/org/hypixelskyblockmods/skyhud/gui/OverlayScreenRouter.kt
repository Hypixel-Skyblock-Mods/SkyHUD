package org.hypixelskyblockmods.skyhud.gui

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import org.hypixelskyblockmods.skyhud.feature.enderchest.EnderChestController
import org.hypixelskyblockmods.skyhud.feature.equipment.EquipmentController
import org.hypixelskyblockmods.skyhud.feature.loadouts.LoadoutController
import org.hypixelskyblockmods.skyhud.feature.wardrobe.WardrobeController
import org.hypixelskyblockmods.skyhud.platform.ScreenCompat

object OverlayScreenRouter {
    fun retainCurrentScreenOnClose(): Boolean =
        OverlayTransitionGuard.consumeClose(ScreenCompat.currentScreen())

    fun redirect(client: Minecraft, screen: Screen): Screen {
        OverlayTransitionGuard.onIncomingScreen()
        var redirected = screen
        redirected = EnderChestController.redirectIncoming(client, redirected)
        redirected = EquipmentController.redirectIncoming(client, redirected)
        redirected = LoadoutController.redirectIncoming(client, redirected)
        redirected = WardrobeController.redirectIncoming(client, redirected)
        return redirected
    }
}
