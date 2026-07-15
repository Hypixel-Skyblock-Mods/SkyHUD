package org.hypixelskyblockmods.skyhud.feature.wardrobe

import net.minecraft.world.inventory.ChestMenu
import net.minecraft.world.item.ItemStack

data class CachedWardrobeSlot(
    val index: Int,
    val armor: List<ItemStack>,
    val selector: ItemStack,
)

data class CachedWardrobePage(
    val page: Int,
    val slots: List<CachedWardrobeSlot>,
)

object WardrobeRepository {
    private val pages = sortedMapOf<Int, CachedWardrobePage>()

    fun remember(page: Int, menu: ChestMenu) {
        val slots = (0 until 9).map { column ->
            CachedWardrobeSlot(
                index = column,
                armor = (0 until 4).map { row -> menu.getSlot(row * 9 + column).item.copy() },
                selector = menu.getSlot(36 + column).item.copy(),
            )
        }
        pages[page] = CachedWardrobePage(page, slots)
    }

    fun page(page: Int): CachedWardrobePage? = pages[page]
}
