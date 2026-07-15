package org.hypixelskyblockmods.skyhud.feature.loadouts

import net.minecraft.world.inventory.ChestMenu
import net.minecraft.world.inventory.ContainerInput
import net.minecraft.world.item.ItemStack
import org.hypixelskyblockmods.skyhud.util.ItemText
import org.hypixelskyblockmods.skyhud.util.VanillaItemIds

data class CachedLoadout(
    val id: Int,
    val page: Int,
    val inventorySlot: Int,
    val name: String,
    val selector: ItemStack,
    val armor: List<ItemStack>,
    val equipment: List<ItemStack>,
    val pet: ItemStack,
    val hotm: ItemStack,
    val hotf: ItemStack,
    val powerStone: ItemStack,
    val tunings: ItemStack,
    val selected: Boolean,
    val locked: Boolean,
    val empty: Boolean,
    val renameAction: LoadoutClickAction,
) {
    val items: List<ItemStack>
        get() = armor + equipment + listOf(pet, hotm, hotf, powerStone, tunings)
}

data class LoadoutClickAction(
    val button: Int,
    val input: ContainerInput,
) {
    companion object {
        val LEFT = LoadoutClickAction(0, ContainerInput.PICKUP)
        val RIGHT = LoadoutClickAction(1, ContainerInput.PICKUP)
    }
}

data class LoadoutCard(
    val id: Int,
    val page: Int,
    val inventorySlot: Int,
    val loadout: CachedLoadout?,
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
    private val armorSlots = listOf(11, 20, 29, 38)
    private val equipmentSlots = listOf(10, 19, 28, 37)
    private const val petSlot = 21
    private const val hotfSlot = 9
    private const val hotmSlot = 18
    private const val powerStoneSlot = 27
    private const val tuningsSlot = 36

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
            val retainRemembered = !locked && !unused
            val armor = when {
                selected -> armorSlots.map { menu.loadoutItem(it) }
                retainRemembered -> remembered?.armor.orEmpty()
                else -> List(4) { ItemStack.EMPTY }
            }
            val equipment = if (selected) {
                equipmentSlots.map { menu.loadoutItem(it) }
            } else if (retainRemembered) {
                remembered?.equipment.orEmpty()
            } else {
                List(4) { ItemStack.EMPTY }
            }
            val pet = if (selected) menu.loadoutItem(petSlot) else remembered?.pet.takeIf { retainRemembered } ?: ItemStack.EMPTY
            val hotf = if (selected) menu.loadoutItem(hotfSlot) else remembered?.hotf.takeIf { retainRemembered } ?: ItemStack.EMPTY
            val hotm = if (selected) menu.loadoutItem(hotmSlot) else remembered?.hotm.takeIf { retainRemembered } ?: ItemStack.EMPTY
            val powerStone = if (selected) {
                menu.loadoutItem(powerStoneSlot)
            } else if (retainRemembered) {
                remembered?.powerStone ?: ItemStack.EMPTY
            } else {
                ItemStack.EMPTY
            }
            val tunings = if (selected) {
                menu.loadoutItem(tuningsSlot)
            } else if (retainRemembered) {
                remembered?.tunings ?: ItemStack.EMPTY
            } else {
                ItemStack.EMPTY
            }

            CachedLoadout(
                id = id,
                page = page,
                inventorySlot = inventorySlot,
                name = selector.hoverName.string.ifBlank { "Loadout $id" },
                selector = selector,
                armor = armor,
                equipment = equipment,
                pet = pet,
                hotm = hotm,
                hotf = hotf,
                powerStone = powerStone,
                tunings = tunings,
                selected = selected,
                locked = locked,
                empty = unused,
                renameAction = renameAction(lore),
            )
        }
        pages[page] = CachedLoadoutPage(page, loadouts)
    }

    fun page(page: Int): CachedLoadoutPage? = pages[page]

    fun allLoadouts(totalPages: Int): List<LoadoutCard> = buildList {
        (1..totalPages).forEach { page ->
            val cachedById = pages[page]?.loadouts?.associateBy(CachedLoadout::id).orEmpty()
            LoadoutLayout.iconSlots(page).forEachIndexed { position, inventorySlot ->
                val id = LoadoutLayout.loadoutId(page, position)
                add(LoadoutCard(id, page, inventorySlot, cachedById[id]))
            }
        }
    }

    private fun ChestMenu.loadoutItem(slot: Int): ItemStack =
        getSlot(slot).item
            .takeUnless(VanillaItemIds::isGlassPane)
            ?.copy()
            ?: ItemStack.EMPTY

    private fun renameAction(lore: List<String>): LoadoutClickAction {
        val instruction = lore.firstOrNull { it.contains("rename", ignoreCase = true) }?.lowercase()
            ?: return LoadoutClickAction.RIGHT
        return when {
            "middle" in instruction -> LoadoutClickAction(2, ContainerInput.CLONE)
            "shift" in instruction -> LoadoutClickAction(
                if ("right" in instruction) 1 else 0,
                ContainerInput.QUICK_MOVE,
            )
            "left" in instruction -> LoadoutClickAction.LEFT
            else -> LoadoutClickAction.RIGHT
        }
    }
}
