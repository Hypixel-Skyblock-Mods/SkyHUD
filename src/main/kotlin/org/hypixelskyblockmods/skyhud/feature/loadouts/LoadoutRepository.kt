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
    val armor: List<ItemStack>,
    val equipment: List<ItemStack>,
    val pet: ItemStack,
    val selected: Boolean,
    val locked: Boolean,
) {
    val items: List<ItemStack>
        get() = armor + equipment + pet
}

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
    private val armorSlots = listOf(11, 20, 29, 38)
    private val equipmentSlots = listOf(10, 19, 28, 37)
    private const val petSlot = 21

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
            val remembered = previous[id]
            val armor = if (selected) armorSlots.map { menu.loadoutItem(it) } else remembered?.armor.orEmpty()
            val equipment = if (selected) {
                equipmentSlots.map { menu.loadoutItem(it) }
            } else {
                remembered?.equipment.orEmpty()
            }
            val pet = if (selected) menu.loadoutItem(petSlot) else remembered?.pet ?: ItemStack.EMPTY

            CachedLoadout(
                id = id,
                inventorySlot = inventorySlot,
                name = selector.hoverName.string.ifBlank { "Loadout $id" },
                selector = selector,
                armor = armor,
                equipment = equipment,
                pet = pet,
                selected = selected,
                locked = locked,
            )
        }
        pages[page] = CachedLoadoutPage(page, loadouts)
    }

    fun page(page: Int): CachedLoadoutPage? = pages[page]

    private fun ChestMenu.loadoutItem(slot: Int): ItemStack =
        getSlot(slot).item
            .takeUnless(VanillaItemIds::isGlassPane)
            ?.copy()
            ?: ItemStack.EMPTY
}
