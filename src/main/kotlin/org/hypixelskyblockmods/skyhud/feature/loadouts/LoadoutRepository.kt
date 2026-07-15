package org.hypixelskyblockmods.skyhud.feature.loadouts

import com.google.gson.GsonBuilder
import java.util.UUID
import net.minecraft.world.inventory.ChestMenu
import net.minecraft.world.inventory.ContainerInput
import net.minecraft.world.item.ItemStack
import org.hypixelskyblockmods.skyhud.util.ItemText
import org.hypixelskyblockmods.skyhud.util.ProfileItemCache
import org.hypixelskyblockmods.skyhud.util.VanillaItemIds
import org.slf4j.LoggerFactory

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
    private data class SavedLoadout(
        var id: Int = 1,
        var page: Int = 1,
        var inventorySlot: Int = 0,
        var name: String = "",
        var selector: String = "",
        var armor: MutableList<String> = mutableListOf(),
        var equipment: MutableList<String> = mutableListOf(),
        var pet: String = "",
        var hotm: String = "",
        var hotf: String = "",
        var powerStone: String = "",
        var tunings: String = "",
        var selected: Boolean = false,
        var locked: Boolean = false,
        var empty: Boolean = false,
        var renameButton: Int = 1,
        var renameInput: String = ContainerInput.PICKUP.name,
    )

    private data class SavedLoadoutCache(
        var loadouts: MutableList<SavedLoadout> = mutableListOf(),
    )

    private val logger = LoggerFactory.getLogger("SkyHUD Loadout Cache")
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val pages = sortedMapOf<Int, CachedLoadoutPage>()
    private val armorSlots = listOf(11, 20, 29, 38)
    private val equipmentSlots = listOf(10, 19, 28, 37)
    private const val petSlot = 21
    private const val hotfSlot = 9
    private const val hotmSlot = 18
    private const val powerStoneSlot = 27
    private const val tuningsSlot = 36
    private var loadedProfile: UUID? = null
    private var lastSavedJson: String? = null

    fun remember(page: Int, menu: ChestMenu) {
        ensureLoaded()
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
            val observedArmor = armorSlots.map { menu.loadoutItem(it) }
            val observedEquipment = equipmentSlots.map { menu.loadoutItem(it) }
            val observedPet = menu.loadoutItem(petSlot)
            val observedHotf = menu.loadoutItem(hotfSlot)
            val observedHotm = menu.loadoutItem(hotmSlot)
            val observedPowerStone = menu.loadoutItem(powerStoneSlot)
            val observedTunings = menu.loadoutItem(tuningsSlot)
            val observedItems = observedArmor + observedEquipment +
                listOf(observedPet, observedHotm, observedHotf, observedPowerStone, observedTunings)
            val previousSelected = previous.values.firstOrNull { it.selected && it.id != id }
            val transitionalSelection = selected && previousSelected != null &&
                ProfileItemCache.stacksMatch(observedItems, previousSelected.items) &&
                (remembered == null || !ProfileItemCache.stacksMatch(observedItems, remembered.items))
            val useObservedDetails = selected && !transitionalSelection
            val armor = when {
                useObservedDetails -> observedArmor
                retainRemembered -> remembered?.armor.orEmpty()
                else -> List(4) { ItemStack.EMPTY }
            }
            val equipment = if (useObservedDetails) {
                observedEquipment
            } else if (retainRemembered) {
                remembered?.equipment.orEmpty()
            } else {
                List(4) { ItemStack.EMPTY }
            }
            val pet = if (useObservedDetails) observedPet else remembered?.pet.takeIf { retainRemembered } ?: ItemStack.EMPTY
            val hotf = if (useObservedDetails) observedHotf else remembered?.hotf.takeIf { retainRemembered } ?: ItemStack.EMPTY
            val hotm = if (useObservedDetails) observedHotm else remembered?.hotm.takeIf { retainRemembered } ?: ItemStack.EMPTY
            val powerStone = if (useObservedDetails) {
                observedPowerStone
            } else if (retainRemembered) {
                remembered?.powerStone ?: ItemStack.EMPTY
            } else {
                ItemStack.EMPTY
            }
            val tunings = if (useObservedDetails) {
                observedTunings
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
        val cachedPage = CachedLoadoutPage(page, loadouts)
        if (!pageMatches(pages[page], cachedPage)) {
            pages[page] = cachedPage
            save()
        }
    }

    fun page(page: Int): CachedLoadoutPage? {
        ensureLoaded()
        return pages[page]
    }

    fun allLoadouts(totalPages: Int): List<LoadoutCard> = buildList {
        ensureLoaded()
        (1..totalPages).forEach { page ->
            val cachedById = pages[page]?.loadouts?.associateBy(CachedLoadout::id).orEmpty()
            LoadoutLayout.iconSlots(page).forEachIndexed { position, inventorySlot ->
                val id = LoadoutLayout.loadoutId(page, position)
                add(LoadoutCard(id, page, inventorySlot, cachedById[id]))
            }
        }
    }

    private fun ensureLoaded() {
        val profile = ProfileItemCache.currentProfile() ?: return
        if (loadedProfile == profile) return
        loadedProfile = profile
        pages.clear()
        lastSavedJson = ProfileItemCache.read("loadouts", profile)
        val json = lastSavedJson ?: return
        runCatching {
            val saved = gson.fromJson(json, SavedLoadoutCache::class.java)
            saved.loadouts.groupBy(SavedLoadout::page).forEach { (page, loadouts) ->
                pages[page] = CachedLoadoutPage(
                    page,
                    loadouts.sortedBy(SavedLoadout::id).map { loadout ->
                        CachedLoadout(
                            id = loadout.id,
                            page = loadout.page,
                            inventorySlot = loadout.inventorySlot,
                            name = loadout.name,
                            selector = ProfileItemCache.decode(loadout.selector),
                            armor = loadout.armor.map(ProfileItemCache::decode),
                            equipment = loadout.equipment.map(ProfileItemCache::decode),
                            pet = ProfileItemCache.decode(loadout.pet),
                            hotm = ProfileItemCache.decode(loadout.hotm),
                            hotf = ProfileItemCache.decode(loadout.hotf),
                            powerStone = ProfileItemCache.decode(loadout.powerStone),
                            tunings = ProfileItemCache.decode(loadout.tunings),
                            selected = loadout.selected,
                            locked = loadout.locked,
                            empty = loadout.empty,
                            renameAction = LoadoutClickAction(
                                loadout.renameButton,
                                runCatching { ContainerInput.valueOf(loadout.renameInput) }.getOrDefault(ContainerInput.PICKUP),
                            ),
                        )
                    },
                )
            }
        }.onFailure {
            logger.warn("Could not load loadout cache for $profile", it)
        }
    }

    private fun save() {
        val profile = loadedProfile ?: ProfileItemCache.currentProfile() ?: return
        val saved = SavedLoadoutCache(
            pages.values.flatMap(CachedLoadoutPage::loadouts).map { loadout ->
                SavedLoadout(
                    id = loadout.id,
                    page = loadout.page,
                    inventorySlot = loadout.inventorySlot,
                    name = loadout.name,
                    selector = ProfileItemCache.encode(loadout.selector),
                    armor = loadout.armor.map(ProfileItemCache::encode).toMutableList(),
                    equipment = loadout.equipment.map(ProfileItemCache::encode).toMutableList(),
                    pet = ProfileItemCache.encode(loadout.pet),
                    hotm = ProfileItemCache.encode(loadout.hotm),
                    hotf = ProfileItemCache.encode(loadout.hotf),
                    powerStone = ProfileItemCache.encode(loadout.powerStone),
                    tunings = ProfileItemCache.encode(loadout.tunings),
                    selected = loadout.selected,
                    locked = loadout.locked,
                    empty = loadout.empty,
                    renameButton = loadout.renameAction.button,
                    renameInput = loadout.renameAction.input.name,
                )
            }.toMutableList(),
        )
        val json = gson.toJson(saved)
        if (json == lastSavedJson) return
        lastSavedJson = json
        ProfileItemCache.write("loadouts", profile, json)
    }

    private fun pageMatches(previous: CachedLoadoutPage?, current: CachedLoadoutPage): Boolean {
        if (previous == null || previous.loadouts.size != current.loadouts.size) return false
        return previous.loadouts.zip(current.loadouts).all { (first, second) ->
            first.id == second.id &&
                first.page == second.page &&
                first.inventorySlot == second.inventorySlot &&
                first.name == second.name &&
                first.selected == second.selected &&
                first.locked == second.locked &&
                first.empty == second.empty &&
                first.renameAction == second.renameAction &&
                ItemStack.matches(first.selector, second.selector) &&
                ProfileItemCache.stacksMatch(first.items, second.items)
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
