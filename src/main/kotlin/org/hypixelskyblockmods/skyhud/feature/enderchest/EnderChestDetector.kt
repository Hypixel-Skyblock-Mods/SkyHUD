package org.hypixelskyblockmods.skyhud.feature.enderchest

import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.world.inventory.ChestMenu

data class EnderChestTarget(
    val page: Int,
    val totalPages: Int,
    val menu: ChestMenu,
)

object EnderChestDetector {
    private val titlePattern = Regex("^Ender Chest (?:✦ )?\\(([1-9][0-9]*)/([1-9][0-9]*)\\)$")

    fun detect(screen: Screen): EnderChestTarget? {
        val containerScreen = screen as? AbstractContainerScreen<*> ?: return null
        val menu = containerScreen.menu as? ChestMenu ?: return null
        val match = titlePattern.matchEntire(screen.title.string) ?: return null
        val page = match.groupValues[1].toIntOrNull() ?: return null
        val total = match.groupValues[2].toIntOrNull() ?: return null

        // Hypixel reserves the first chest row for page controls. Refuse to
        // replace anything that does not have both controls and item storage.
        if (menu.rowCount !in 2..6 || page !in 1..total || total > 9) return null
        if (menu.slots.size < menu.rowCount * 9 + 36) return null

        return EnderChestTarget(page, total, menu)
    }
}
