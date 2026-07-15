package org.hypixelskyblockmods.skyhud.feature.enderchest

import net.minecraft.world.inventory.ChestMenu
import net.minecraft.world.item.ItemStack
import org.hypixelskyblockmods.skyhud.util.VanillaItemIds

enum class StoragePageType {
    ENDER_CHEST,
    BACKPACK,
}

data class StoragePageKey(
    val type: StoragePageType,
    val number: Int,
) : Comparable<StoragePageKey> {
    val overviewSlot: Int
        get() = when (type) {
            StoragePageType.ENDER_CHEST -> 8 + number
            StoragePageType.BACKPACK -> 26 + number
        }

    val displayName: String
        get() = when (type) {
            StoragePageType.ENDER_CHEST -> "ENDER CHEST #$number"
            StoragePageType.BACKPACK -> "BACKPACK #$number"
        }

    val navigationCommand: String
        get() = when (type) {
            StoragePageType.ENDER_CHEST -> "enderchest $number"
            StoragePageType.BACKPACK -> "backpack $number"
        }

    override fun compareTo(other: StoragePageKey): Int =
        sortIndex().compareTo(other.sortIndex())

    private fun sortIndex(): Int = when (type) {
        StoragePageType.ENDER_CHEST -> number - 1
        StoragePageType.BACKPACK -> 9 + number - 1
    }

    companion object {
        fun enderChest(number: Int) = StoragePageKey(StoragePageType.ENDER_CHEST, number)

        fun backpack(number: Int) = StoragePageKey(StoragePageType.BACKPACK, number)
    }
}

data class CachedEnderChestPage(
    val key: StoragePageKey,
    val rows: Int,
    val items: List<ItemStack>,
) {
    val page: Int
        get() = key.number
}

object EnderChestRepository {
    private val pages = sortedMapOf<StoragePageKey, CachedEnderChestPage>()
    private val availablePages = sortedSetOf<StoragePageKey>()
    var hasDiscoveredOverview: Boolean = false
        private set

    fun remember(page: Int, menu: ChestMenu) {
        remember(StoragePageKey.enderChest(page), menu)
    }

    fun remember(key: StoragePageKey, menu: ChestMenu) {
        val containerSize = menu.rowCount * 9
        val itemRows = menu.rowCount - 1
        val copiedItems = menu.items
            .take(containerSize)
            .drop(9)
            .map(ItemStack::copy)
        availablePages += key
        pages[key] = CachedEnderChestPage(key, itemRows, copiedItems)
    }

    fun rememberEnderChest(page: Int, totalPages: Int, menu: ChestMenu) {
        (1..totalPages).forEach { availablePages += StoragePageKey.enderChest(it) }
        remember(StoragePageKey.enderChest(page), menu)
    }

    fun rememberOverview(menu: ChestMenu) {
        hasDiscoveredOverview = true
        (1..9).forEach { rememberOverviewSlot(StoragePageKey.enderChest(it), menu) }
        (1..18).forEach { rememberOverviewSlot(StoragePageKey.backpack(it), menu) }
    }

    fun page(page: Int): CachedEnderChestPage? = pages[StoragePageKey.enderChest(page)]

    fun page(key: StoragePageKey): CachedEnderChestPage? = pages[key]

    fun allPages(): List<StoragePageKey> = StoragePagePreferences.order(availablePages + pages.keys)

    fun clear() {
        pages.clear()
        availablePages.clear()
        hasDiscoveredOverview = false
    }

    private fun rememberOverviewSlot(key: StoragePageKey, menu: ChestMenu) {
        val stack = menu.getSlot(key.overviewSlot).item
        if (stack.isEmpty) return
        if (stack.isEmptyStorageSlot()) {
            availablePages -= key
            pages -= key
        } else {
            availablePages += key
        }
    }

    private fun ItemStack.isEmptyStorageSlot(): Boolean =
        VanillaItemIds.isItem(this, "red_stained_glass_pane") ||
            VanillaItemIds.isItem(this, "brown_stained_glass_pane") ||
            VanillaItemIds.isItem(this, "gray_dye")
}
