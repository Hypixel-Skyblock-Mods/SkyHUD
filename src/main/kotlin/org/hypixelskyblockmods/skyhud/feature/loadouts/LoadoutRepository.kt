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

    val hotmName: String?
        get() = selectorDetail(ItemText.lore(selector), "HOTM").selectedValue

    val hotfName: String?
        get() = selectorDetail(ItemText.lore(selector), "HOTF").selectedValue
}

private enum class SelectorDetailState {
    PRESENT,
    EMPTY,
    UNKNOWN,
}

private data class SelectorDetail(
    val state: SelectorDetailState,
    val value: String? = null,
) {
    val selectedValue: String?
        get() = value.takeIf { state == SelectorDetailState.PRESENT }
}

private fun selectorDetail(lore: List<String>, label: String): SelectorDetail {
    val prefix = "$label:"
    val line = lore.firstOrNull { it.trim().startsWith(prefix, ignoreCase = true) }
        ?: return SelectorDetail(SelectorDetailState.UNKNOWN)
    val value = line.trim().substring(prefix.length).trim()
    return when {
        value.equals("None", ignoreCase = true) || value.equals("Empty", ignoreCase = true) ->
            SelectorDetail(SelectorDetailState.EMPTY)
        value.isNotEmpty() -> SelectorDetail(SelectorDetailState.PRESENT, value)
        else -> SelectorDetail(SelectorDetailState.UNKNOWN)
    }
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
    private val armorLabels = listOf("Helmet", "Chestplate", "Leggings", "Boots")
    private val equipmentSlots = listOf(10, 19, 28, 37)
    private val equipmentLabels = listOf("Necklace", "Cloak", "Belt", "Gloves/Bracelet")
    private const val petSlot = 21
    private const val hotfSlot = 9
    private const val hotmSlot = 18
    private const val powerStoneSlot = 27
    private const val tuningsSlot = 36
    private var loadedProfile: UUID? = null
    private var lastSavedJson: String? = null

    fun remember(page: Int, menu: ChestMenu) {
        ensureLoaded()
        if (LoadoutLayout.iconSlots(page).any { menu.getSlot(it).item.isEmpty }) return
        val previous = pages[page]?.loadouts?.associateBy(CachedLoadout::id).orEmpty()
        val allRemembered = pages.values.flatMap(CachedLoadoutPage::loadouts)
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
            val observedFromAnotherLoadout = allRemembered.firstOrNull {
                it.id != id && !it.empty && ProfileItemCache.stacksMatch(observedItems, it.items)
            }
            val transitionalSelection = selected && observedFromAnotherLoadout != null &&
                (remembered == null || !ProfileItemCache.stacksMatch(observedItems, remembered.items))
            val useObservedDetails = selected && !transitionalSelection
            val armor = observedArmor.mapIndexed { index, observed ->
                mergeDetail(
                    observed = observed,
                    remembered = remembered?.armor?.getOrNull(index),
                    selector = selectorDetail(lore, armorLabels[index]),
                    useObserved = useObservedDetails,
                    retainRemembered = retainRemembered,
                    requireMatchingName = true,
                )
            }
            val equipment = observedEquipment.mapIndexed { index, observed ->
                mergeDetail(
                    observed = observed,
                    remembered = remembered?.equipment?.getOrNull(index),
                    selector = selectorDetail(lore, equipmentLabels[index]),
                    useObserved = useObservedDetails,
                    retainRemembered = retainRemembered,
                    requireMatchingName = true,
                )
            }
            val pet = mergeDetail(
                observedPet,
                remembered?.pet,
                selectorDetail(lore, "Pet"),
                useObservedDetails,
                retainRemembered,
            )
            val hotf = mergeDetail(
                observedHotf,
                remembered?.hotf,
                selectorDetail(lore, "HOTF"),
                useObservedDetails,
                retainRemembered,
            )
            val hotm = mergeDetail(
                observedHotm,
                remembered?.hotm,
                selectorDetail(lore, "HOTM"),
                useObservedDetails,
                retainRemembered,
            )
            val powerStone = mergeDetail(
                observedPowerStone,
                remembered?.powerStone,
                selectorDetail(lore, "Power Stone"),
                useObservedDetails,
                retainRemembered,
            )
            val tunings = mergeDetail(
                observedTunings,
                remembered?.tunings,
                selectorDetail(lore, "Tuning Template Slot"),
                useObservedDetails,
                retainRemembered,
            )

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

    fun markSelected(page: Int, inventorySlot: Int) {
        ensureLoaded()
        pages.replaceAll { cachedPageNumber, cachedPage ->
            cachedPage.copy(
                loadouts = cachedPage.loadouts.map { loadout ->
                    loadout.copy(selected = cachedPageNumber == page && loadout.inventorySlot == inventorySlot)
                },
            )
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
        val profile = ProfileItemCache.currentProfile()
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
        val profile = loadedProfile ?: ProfileItemCache.currentProfile()
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
        if (ProfileItemCache.write("loadouts", profile, json)) lastSavedJson = json
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

    private fun mergeDetail(
        observed: ItemStack,
        remembered: ItemStack?,
        selector: SelectorDetail,
        useObserved: Boolean,
        retainRemembered: Boolean,
        requireMatchingName: Boolean = false,
    ): ItemStack {
        if (!retainRemembered || selector.state == SelectorDetailState.EMPTY) return ItemStack.EMPTY
        if (!useObserved) return remembered ?: ItemStack.EMPTY
        if (observed.isEmpty) {
            return remembered.takeIf { selector.state == SelectorDetailState.PRESENT } ?: ItemStack.EMPTY
        }
        if (requireMatchingName && selector.state == SelectorDetailState.PRESENT &&
            normalizedItemName(observed.hoverName.string) != normalizedItemName(selector.value.orEmpty())
        ) {
            return remembered ?: ItemStack.EMPTY
        }
        return observed
    }

    private fun normalizedItemName(value: String): String =
        value.lowercase().filter(Char::isLetterOrDigit)

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
