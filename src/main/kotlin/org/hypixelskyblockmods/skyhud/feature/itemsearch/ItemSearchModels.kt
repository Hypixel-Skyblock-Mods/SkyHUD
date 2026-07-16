package org.hypixelskyblockmods.skyhud.feature.itemsearch

import net.minecraft.core.BlockPos
import net.minecraft.world.item.ItemStack
import org.hypixelskyblockmods.skyhud.feature.enderchest.StoragePageKey

enum class ItemSourceCategory {
    ALL,
    STORAGE,
    INVENTORY,
    WARDROBE_EQUIPMENT,
    SACKS,
    ISLAND_CHESTS,
    MUSEUM,
    RIFT,
    OTHER,
}

enum class ItemSourceId(val displayName: String, val category: ItemSourceCategory) {
    INVENTORY("Inventory", ItemSourceCategory.INVENTORY),
    EQUIPPED("Equipped", ItemSourceCategory.INVENTORY),
    STORAGE("Storage", ItemSourceCategory.STORAGE),
    RIFT("Rift", ItemSourceCategory.RIFT),
    WARDROBE("Wardrobe", ItemSourceCategory.WARDROBE_EQUIPMENT),
    EQUIPMENT_WARDROBE("Equipment Wardrobe", ItemSourceCategory.WARDROBE_EQUIPMENT),
    LOADOUTS("Loadouts", ItemSourceCategory.WARDROBE_EQUIPMENT),
    ACCESSORY_BAG("Accessory Bag", ItemSourceCategory.OTHER),
    SACKS("Sacks", ItemSourceCategory.SACKS),
    SACK_OF_SACKS("Sack of Sacks", ItemSourceCategory.SACKS),
    VAULT("Personal Vault", ItemSourceCategory.OTHER),
    FORGE("Forge", ItemSourceCategory.OTHER),
    MUSEUM("Museum", ItemSourceCategory.MUSEUM),
    ISLAND_CHESTS("Island Chests", ItemSourceCategory.ISLAND_CHESTS),
    INSTALLED_PARTS("Installed Parts", ItemSourceCategory.OTHER),
}

enum class ItemDataOrigin {
    LIVE_PLAYER,
    LIVE_MENU,
    SKYBLOCK_API_PROFILE,
    LOCAL_OBSERVATION,
    DERIVED,
}

enum class InventoryRealm {
    NORMAL,
    RIFT,
}

sealed interface ItemLocation {
    val label: String
    val identity: String

    data class Inventory(val realm: InventoryRealm, val slot: Int, val equipped: Boolean = false) : ItemLocation {
        override val label = "${realm.displayName()} ${if (equipped) "equipment" else "inventory"} slot ${slot + 1}"
        override val identity = "inventory:${realm.name}:$equipped:$slot"
    }

    data class Storage(val page: StoragePageKey, val itemIndex: Int, val rift: Boolean = false) : ItemLocation {
        override val label = if (rift) "Rift Storage #${page.number}, slot ${itemIndex + 1}" else "${page.displayName}, slot ${itemIndex + 1}"
        override val identity = "storage:$rift:${page.type.name}:${page.number}:$itemIndex"
    }

    data class Collection(val collection: String, val page: Int, val setId: Int, val itemIndex: Int) : ItemLocation {
        override val label = "$collection #$setId, item ${itemIndex + 1}"
        override val identity = "collection:$collection:$page:$setId:$itemIndex"
    }

    data class Container(val container: String, val page: Int? = null, val slot: Int? = null) : ItemLocation {
        override val label = buildString {
            append(container)
            page?.let { append(" page $it") }
            slot?.let { append(", slot ${it + 1}") }
        }
        override val identity = "container:$container:$page:$slot"
    }

    data class IslandChest(val positions: List<BlockPos>, val slot: Int) : ItemLocation {
        private val first = positions.first()
        override val label = "Island chest at ${first.x}, ${first.y}, ${first.z}, slot ${slot + 1}"
        override val identity = "island:${positions.joinToString(";") { "${it.x},${it.y},${it.z}" }}:$slot"
    }

    data class Generic(override val label: String, override val identity: String = "generic:$label") : ItemLocation
}

private fun InventoryRealm.displayName(): String = if (this == InventoryRealm.RIFT) "Rift" else "Normal"

sealed interface ItemNavigationAction {
    data object None : ItemNavigationAction
    data class Command(val command: String) : ItemNavigationAction
    data class Storage(val page: StoragePageKey, val itemIndex: Int, val rift: Boolean = false) : ItemNavigationAction
    data class Collection(val type: CollectionType, val page: Int, val setId: Int, val itemIndex: Int) : ItemNavigationAction
    data class Inventory(val realm: InventoryRealm, val slot: Int) : ItemNavigationAction
    data class IslandChest(val positions: List<BlockPos>) : ItemNavigationAction
}

enum class CollectionType(val command: String) {
    LOADOUT("loadouts"),
    WARDROBE("wardrobe"),
    EQUIPMENT("eq"),
}

data class ItemFingerprint(
    val vanillaId: String,
    val skyblockId: String?,
    val cleanName: String,
    val componentHash: String,
)

data class SearchableItem(
    val stack: ItemStack,
    val amount: Long,
    val source: ItemSourceId,
    val location: ItemLocation,
    val action: ItemNavigationAction = ItemNavigationAction.None,
    val origin: ItemDataOrigin,
    val updatedAtEpochMillis: Long? = null,
    val fingerprint: ItemFingerprint,
    val searchableName: String,
    val searchableLore: List<String> = emptyList(),
    val skyblockId: String? = null,
    val rarityOrdinal: Int? = null,
    val estimatedValue: Long? = null,
) {
    fun defensiveCopy(): SearchableItem = copy(stack = stack.copy(), location = location.copyLocation(), action = action.copyAction())
}

data class ItemSearchEntry(
    val fingerprint: ItemFingerprint,
    val displayStack: ItemStack,
    val totalAmount: Long,
    val estimatedValue: Long?,
    val rarityOrdinal: Int?,
    val locations: List<SearchableItem>,
) {
    val name: String get() = locations.firstOrNull()?.searchableName ?: displayStack.hoverName.string
    val oldestUpdateEpochMillis: Long? get() = locations.mapNotNull(SearchableItem::updatedAtEpochMillis).minOrNull()
    val hasUnknownAge: Boolean get() = locations.any { it.updatedAtEpochMillis == null && it.origin != ItemDataOrigin.LIVE_MENU && it.origin != ItemDataOrigin.LIVE_PLAYER }
    fun isStale(now: Long, staleAfterMillis: Long): Boolean = locations.any {
        val updated = it.updatedAtEpochMillis ?: return@any false
        it.origin != ItemDataOrigin.LIVE_MENU && it.origin != ItemDataOrigin.LIVE_PLAYER && now - updated > staleAfterMillis
    }
}

private fun ItemLocation.copyLocation(): ItemLocation = when (this) {
    is ItemLocation.Inventory -> copy()
    is ItemLocation.Storage -> copy()
    is ItemLocation.Collection -> copy()
    is ItemLocation.Container -> copy()
    is ItemLocation.IslandChest -> copy(positions = positions.map(BlockPos::immutable))
    is ItemLocation.Generic -> copy()
}

private fun ItemNavigationAction.copyAction(): ItemNavigationAction = when (this) {
    ItemNavigationAction.None -> this
    is ItemNavigationAction.Command -> copy()
    is ItemNavigationAction.Storage -> copy()
    is ItemNavigationAction.Collection -> copy()
    is ItemNavigationAction.Inventory -> copy()
    is ItemNavigationAction.IslandChest -> copy(positions = positions.map(BlockPos::immutable))
}
