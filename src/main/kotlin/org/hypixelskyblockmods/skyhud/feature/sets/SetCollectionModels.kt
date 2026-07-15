package org.hypixelskyblockmods.skyhud.feature.sets

import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.world.inventory.ChestMenu
import net.minecraft.world.item.ItemStack
import org.hypixelskyblockmods.skyhud.util.VanillaItemIds

data class SetCollectionTarget(
    val page: Int,
    val totalPages: Int,
    val menu: ChestMenu,
)

data class CachedSetSlot(
    val page: Int,
    val index: Int,
    val id: Int,
    val items: List<ItemStack>,
    val selector: ItemStack,
    val selected: Boolean,
    val locked: Boolean,
    val selectable: Boolean,
)

data class SetCard(
    val page: Int,
    val index: Int,
    val id: Int,
    val set: CachedSetSlot?,
)

data class CachedSetPage(
    val page: Int,
    val slots: List<CachedSetSlot>,
)

class SetCollectionRepository {
    private val pages = sortedMapOf<Int, CachedSetPage>()
    private val equippedPattern = Regex("^Slot [0-9]+: Equipped$", RegexOption.IGNORE_CASE)

    fun remember(page: Int, menu: ChestMenu) {
        val slots = (0 until 9).map { column ->
            val items = (0 until 4).map { row ->
                menu.getSlot(row * 9 + column).item
                    .takeUnless(VanillaItemIds::isGlassPane)
                    ?.copy()
                    ?: ItemStack.EMPTY
            }
            val selector = menu.getSlot(36 + column).item.copy()
            val selected = VanillaItemIds.isItem(selector, "lime_dye") ||
                equippedPattern.matches(selector.hoverName.string)
            val locked = VanillaItemIds.isItem(selector, "red_dye")

            CachedSetSlot(
                page = page,
                index = column,
                id = (page - 1) * 9 + column + 1,
                items = items,
                selector = selector,
                selected = selected,
                locked = locked,
                selectable = selected || (!locked && items.any { !it.isEmpty }),
            )
        }
        pages[page] = CachedSetPage(page, slots)
    }

    fun page(page: Int): CachedSetPage? = pages[page]

    fun allSets(totalPages: Int): List<SetCard> = buildList {
        (1..totalPages).forEach { page ->
            val cachedByIndex = pages[page]?.slots?.associateBy(CachedSetSlot::index).orEmpty()
            repeat(9) { index ->
                add(SetCard(page, index, (page - 1) * 9 + index + 1, cachedByIndex[index]))
            }
        }
    }
}

object SetCollectionDetection {
    fun detect(screen: Screen, titlePatterns: List<Regex>): SetCollectionTarget? {
        val containerScreen = screen as? AbstractContainerScreen<*> ?: return null
        val menu = containerScreen.menu as? ChestMenu ?: return null
        val match = titlePatterns.firstNotNullOfOrNull { it.matchEntire(screen.title.string) } ?: return null
        val page = match.groupValues[1].toIntOrNull() ?: return null
        val totalPages = match.groupValues[2].toIntOrNull() ?: return null

        if (menu.rowCount != 6 || totalPages !in 1..3 || page !in 1..totalPages) return null
        if (menu.slots.size < 90) return null
        if ((36..44).none { !menu.getSlot(it).item.isEmpty }) return null

        return SetCollectionTarget(page, totalPages, menu)
    }
}
