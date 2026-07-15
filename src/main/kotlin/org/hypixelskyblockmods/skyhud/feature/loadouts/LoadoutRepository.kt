package org.hypixelskyblockmods.skyhud.feature.loadouts

import net.minecraft.world.inventory.ChestMenu
import net.minecraft.world.item.ItemStack
import org.hypixelskyblockmods.skyhud.util.ItemText
import org.hypixelskyblockmods.skyhud.util.VanillaItemIds

data class CachedLoadout(
    val id: Int,
    val inventorySlot: Int,
    val name: String,
    val selector: ItemStack,
    val items: List<ItemStack>,
    val selected: Boolean,
    val locked: Boolean,
)

data class CachedLoadoutPage(
    val page: Int,
    val loadouts: List<CachedLoadout>,
)

object LoadoutLayout {
    private val rowsPerPage = listOf(4, 4, 1)
    private const val firstIconSlot = 14
    private const val slotsPerRow = 3

    fun iconSlots(page: Int): List<Int> {
        val rows = rowsPerPage.getOrNull(page - 1) ?: return emptyList()
        return buildList {
            repeat(rows) { row ->
                repeat(slotsPerRow) { column ->
                    add(firstIconSlot + row * 9 + column)
                }
            }
        }
    }

    fun loadoutId(page: Int, position: Int): Int = (page - 1) * 12 + position + 1
}

object LoadoutRepository {
    private val pages = sortedMapOf<Int, CachedLoadoutPage>()
    private val detailSlots = listOf(11, 20, 29, 38, 10, 19, 28, 37, 21)

    fun remember(page: Int, menu: ChestMenu) {
        val previous = pages[page]?.loadouts?.associateBy(CachedLoadout::id).orEmpty()
        val loadouts = LoadoutLayout.iconSlots(page).mapIndexed { position, inventorySlot ->
            val selector = menu.getSlot(inventorySlot).item.copy()
            val id = LoadoutLayout.loadoutId(page, position)
            val lore = ItemText.lore(selector)
            val locked = VanillaItemIds.isItem(selector, "red_dye")
            val unused = VanillaItemIds.isItem(selector, "gray_dye") ||
                lore.any { it.contains("You must customize this loadout", ignoreCase = true) }
            val selected = !locked && !unused &&
                lore.none { it.contains("Left-click to equip!", ignoreCase = true) }
            val rememberedItems = previous[id]?.items.orEmpty()
            val items = if (selected) {
                detailSlots.map { slot ->
                    menu.getSlot(slot).item
                        .takeUnless(VanillaItemIds::isGlassPane)
                        ?.copy()
                        ?: ItemStack.EMPTY
                }
            } else {
                rememberedItems
            }

            CachedLoadout(
                id = id,
                inventorySlot = inventorySlot,
                name = selector.hoverName.string.ifBlank { "Loadout $id" },
                selector = selector,
                items = items,
                selected = selected,
                locked = locked,
            )
        }
        pages[page] = CachedLoadoutPage(page, loadouts)
    }

    fun page(page: Int): CachedLoadoutPage? = pages[page]
}
