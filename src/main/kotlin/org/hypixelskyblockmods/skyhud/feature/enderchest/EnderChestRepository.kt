package org.hypixelskyblockmods.skyhud.feature.enderchest

import net.minecraft.world.inventory.ChestMenu
import net.minecraft.world.item.ItemStack

data class CachedEnderChestPage(
    val page: Int,
    val rows: Int,
    val items: List<ItemStack>,
)

object EnderChestRepository {
    private val pages = sortedMapOf<Int, CachedEnderChestPage>()

    fun remember(page: Int, menu: ChestMenu) {
        val containerSize = menu.rowCount * 9
        val itemRows = menu.rowCount - 1
        val copiedItems = menu.items
            .take(containerSize)
            .drop(9)
            .map(ItemStack::copy)
        pages[page] = CachedEnderChestPage(page, itemRows, copiedItems)
    }

    fun page(page: Int): CachedEnderChestPage? = pages[page]

    fun clear() = pages.clear()
}
