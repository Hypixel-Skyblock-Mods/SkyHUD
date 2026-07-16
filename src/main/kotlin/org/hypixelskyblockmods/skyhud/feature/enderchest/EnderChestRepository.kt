package org.hypixelskyblockmods.skyhud.feature.enderchest

import java.util.UUID
import net.minecraft.world.inventory.ChestMenu
import net.minecraft.world.item.ItemStack
import org.hypixelskyblockmods.skyhud.integration.skyblockapi.SkyBlockProfileIdentity
import org.hypixelskyblockmods.skyhud.integration.skyblockapi.SkyblockApiStorageAdapter
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
    val updatedAtEpochMillis: Long? = null,
) {
    val page: Int
        get() = key.number
}

object EnderChestRepository {
    private data class StorageProfileKey(
        val accountUuid: UUID,
        val profileName: String,
    )

    private val availablePages = sortedSetOf<StoragePageKey>()
    private var apiPages = emptyMap<StoragePageKey, CachedEnderChestPage>()
    private var loadedProfile: StorageProfileKey? = null
    private var activeIdentity: SkyBlockProfileIdentity? = null
    private var livePageKey: StoragePageKey? = null
    private var liveMenu: ChestMenu? = null
    var hasDiscoveredOverview: Boolean = false
        private set

    fun remember(page: Int, menu: ChestMenu) {
        remember(StoragePageKey.enderChest(page), menu)
    }

    fun remember(key: StoragePageKey, menu: ChestMenu) {
        ensureProfileState()
        if (availablePages.add(key)) activeIdentity?.let { StoragePageCatalog.remember(it, setOf(key)) }
        livePageKey = key
        liveMenu = menu
        refreshApiSnapshot()
    }

    fun rememberEnderChest(page: Int, totalPages: Int, menu: ChestMenu) {
        ensureProfileState()
        val discovered = (1..totalPages).map(StoragePageKey::enderChest)
        if (availablePages.addAll(discovered)) activeIdentity?.let { StoragePageCatalog.remember(it, discovered) }
        remember(StoragePageKey.enderChest(page), menu)
    }

    fun rememberOverview(menu: ChestMenu) {
        ensureProfileState()
        val discovered = sortedSetOf<StoragePageKey>()
        (1..9).map(StoragePageKey::enderChest).filterTo(discovered) { isAvailableOverviewSlot(it, menu) }
        (1..18).map(StoragePageKey::backpack).filterTo(discovered) { isAvailableOverviewSlot(it, menu) }
        val changed = !hasDiscoveredOverview || availablePages != discovered
        availablePages.clear()
        availablePages.addAll(discovered)
        hasDiscoveredOverview = true
        if (changed) activeIdentity?.let { StoragePageCatalog.replaceOverview(it, discovered) }
        livePageKey = null
        liveMenu = null
        refreshApiSnapshot()
    }

    fun refreshApiSnapshot() {
        val profile = ensureProfileState() ?: run {
            apiPages = emptyMap()
            return
        }
        val pages = SkyblockApiStorageAdapter.allPages().associate { page ->
            val rows = ((page.items.size + 8) / 9).coerceIn(1, 5)
            page.key to CachedEnderChestPage(
                key = page.key,
                rows = rows,
                items = page.items,
                updatedAtEpochMillis = page.updatedAtEpochMillis,
            )
        }
        if (profile == currentProfileKey()) apiPages = pages
    }

    fun page(page: Int): CachedEnderChestPage? = page(StoragePageKey.enderChest(page))

    fun page(key: StoragePageKey): CachedEnderChestPage? {
        ensureProfileState()
        val menu = liveMenu
        if (key == livePageKey && menu != null) return livePage(key, menu)
        return apiPages[key]
    }

    fun allPages(): List<StoragePageKey> {
        ensureProfileState()
        val visible = if (hasDiscoveredOverview) {
            availablePages.toMutableSet()
        } else {
            (availablePages + apiPages.keys).toMutableSet()
        }
        livePageKey?.let(visible::add)
        return StoragePagePreferences.order(visible)
    }

    fun searchSnapshot(): List<CachedEnderChestPage> = allPages().mapNotNull(::page).map { page ->
        page.copy(items = page.items.map(ItemStack::copy))
    }

    fun clearLiveBacking() {
        livePageKey = null
        liveMenu = null
    }

    fun resetSession() {
        availablePages.clear()
        apiPages = emptyMap()
        hasDiscoveredOverview = false
        clearLiveBacking()
        activeIdentity = null
        loadedProfile = null
    }

    private fun ensureProfileState(): StorageProfileKey? {
        val identity = SkyblockApiStorageAdapter.currentProfile()
        val profile = identity?.toStorageKey()
        if (loadedProfile != profile) {
            availablePages.clear()
            apiPages = emptyMap()
            hasDiscoveredOverview = false
            clearLiveBacking()
            loadedProfile = profile
            if (identity != null) {
                val saved = StoragePageCatalog.snapshot(identity)
                availablePages.addAll(saved.availablePages)
                hasDiscoveredOverview = saved.overviewDiscovered
            }
        }
        activeIdentity = identity
        return profile
    }

    private fun currentProfileKey(): StorageProfileKey? =
        SkyblockApiStorageAdapter.currentProfile()?.toStorageKey()

    private fun SkyBlockProfileIdentity.toStorageKey() = StorageProfileKey(accountUuid, profileName)

    private fun livePage(key: StoragePageKey, menu: ChestMenu): CachedEnderChestPage {
        val containerSize = menu.rowCount * 9
        val itemRows = menu.rowCount - 1
        val copiedItems = menu.items
            .take(containerSize)
            .drop(9)
            .map(ItemStack::copy)
        return CachedEnderChestPage(key, itemRows, copiedItems)
    }

    private fun isAvailableOverviewSlot(key: StoragePageKey, menu: ChestMenu): Boolean {
        val stack = menu.getSlot(key.overviewSlot).item
        return !stack.isEmpty && !stack.isEmptyStorageSlot()
    }

    private fun ItemStack.isEmptyStorageSlot(): Boolean =
        VanillaItemIds.isItem(this, "red_stained_glass_pane") ||
            VanillaItemIds.isItem(this, "brown_stained_glass_pane") ||
            VanillaItemIds.isItem(this, "gray_dye")
}
