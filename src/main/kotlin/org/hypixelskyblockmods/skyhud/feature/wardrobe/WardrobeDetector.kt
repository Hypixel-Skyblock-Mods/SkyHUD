package org.hypixelskyblockmods.skyhud.feature.wardrobe

import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.world.inventory.ChestMenu
import org.hypixelskyblockmods.skyhud.util.VanillaItemIds

data class WardrobeTarget(
    val page: Int,
    val totalPages: Int,
    val menu: ChestMenu,
)

object WardrobeDetector {
    private val titlePattern = Regex("^Wardrobe \\(([1-9][0-9]*)/([1-9][0-9]*)\\)$")

    fun detect(screen: Screen): WardrobeTarget? {
        val containerScreen = screen as? AbstractContainerScreen<*> ?: return null
        val menu = containerScreen.menu as? ChestMenu ?: return null
        val match = titlePattern.matchEntire(screen.title.string) ?: return null
        val page = match.groupValues[1].toIntOrNull() ?: return null
        val total = match.groupValues[2].toIntOrNull() ?: return null

        if (menu.rowCount != 6 || page !in 1..total || total > 20) return null
        if (menu.slots.size < 90) return null

        // Wardrobe outfit actions are the dye buttons in slots 36..44. This
        // prevents a coincidentally named six-row chest from being replaced.
        val hasWardrobeSelector = (36..44).any { slot ->
            val stack = menu.getSlot(slot).item
            VanillaItemIds.isItem(stack, "pink_dye") || VanillaItemIds.isItem(stack, "lime_dye")
        }
        if (!hasWardrobeSelector) return null

        return WardrobeTarget(page, total, menu)
    }
}
