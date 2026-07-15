package org.hypixelskyblockmods.skyhud.feature.enderchest

import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.world.inventory.ChestMenu

sealed interface EnderChestTarget {
    val menu: ChestMenu

    data class Overview(
        override val menu: ChestMenu,
    ) : EnderChestTarget

    data class Page(
        val key: StoragePageKey,
        val totalEnderChestPages: Int?,
        override val menu: ChestMenu,
    ) : EnderChestTarget
}

object EnderChestDetector {
    private val enderChestPattern = Regex("^Ender Chest (?:✦ )?\\(([1-9][0-9]*)/([1-9][0-9]*)\\)$")
    private val backpackPattern = Regex("^.+Backpack (?:✦ )?\\(Slot #([1-9][0-9]*)\\)$")

    fun detect(screen: Screen): EnderChestTarget? {
        val containerScreen = screen as? AbstractContainerScreen<*> ?: return null
        val menu = containerScreen.menu as? ChestMenu ?: return null
        val title = screen.title.string

        if (title == "Storage") {
            if (menu.rowCount != 6 || menu.slots.size < 90) return null
            val overviewSlots = (9..17) + (27..44)
            if (overviewSlots.none { !menu.getSlot(it).item.isEmpty }) return null
            return EnderChestTarget.Overview(menu)
        }

        val enderChestMatch = enderChestPattern.matchEntire(title)
        if (enderChestMatch != null) {
            val page = enderChestMatch.groupValues[1].toIntOrNull() ?: return null
            val total = enderChestMatch.groupValues[2].toIntOrNull() ?: return null
            if (!validPageMenu(menu) || page !in 1..total || total > 9) return null
            return EnderChestTarget.Page(StoragePageKey.enderChest(page), total, menu)
        }

        val backpackMatch = backpackPattern.matchEntire(title) ?: return null
        val page = backpackMatch.groupValues[1].toIntOrNull() ?: return null
        if (!validPageMenu(menu) || page !in 1..18) return null
        return EnderChestTarget.Page(StoragePageKey.backpack(page), null, menu)
    }

    private fun validPageMenu(menu: ChestMenu): Boolean {
        // Storage pages reserve the first chest row for page controls.
        if (menu.rowCount !in 2..6) return false
        if (menu.slots.size < menu.rowCount * 9 + 36) return false
        return true
    }
}
