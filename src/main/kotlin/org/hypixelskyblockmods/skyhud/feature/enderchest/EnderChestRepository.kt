package org.hypixelskyblockmods.skyhud.feature.enderchest

import com.google.gson.GsonBuilder
import java.util.UUID
import net.minecraft.world.inventory.ChestMenu
import net.minecraft.world.item.ItemStack
import org.hypixelskyblockmods.skyhud.util.ProfileItemCache
import org.hypixelskyblockmods.skyhud.util.VanillaItemIds
import org.slf4j.LoggerFactory

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
    private data class SavedPage(
        var type: String = StoragePageType.ENDER_CHEST.name,
        var number: Int = 1,
        var rows: Int = 0,
        var items: MutableList<String> = mutableListOf(),
    )

    private data class SavedStorage(
        var overviewDiscovered: Boolean = false,
        var available: MutableList<String> = mutableListOf(),
        var pages: MutableList<SavedPage> = mutableListOf(),
    )

    private val logger = LoggerFactory.getLogger("SkyHUD Storage Cache")
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val pages = sortedMapOf<StoragePageKey, CachedEnderChestPage>()
    private val availablePages = sortedSetOf<StoragePageKey>()
    private var loadedProfile: UUID? = null
    private var lastSavedJson: String? = null
    var hasDiscoveredOverview: Boolean = false
        private set

    fun remember(page: Int, menu: ChestMenu) {
        remember(StoragePageKey.enderChest(page), menu)
    }

    fun remember(key: StoragePageKey, menu: ChestMenu) {
        ensureLoaded()
        val containerSize = menu.rowCount * 9
        val itemRows = menu.rowCount - 1
        val copiedItems = menu.items
            .take(containerSize)
            .drop(9)
            .map(ItemStack::copy)
        var changed = availablePages.add(key)
        val page = CachedEnderChestPage(key, itemRows, copiedItems)
        val previous = pages[key]
        if (previous == null || previous.rows != page.rows || !ProfileItemCache.stacksMatch(previous.items, page.items)) {
            pages[key] = page
            changed = true
        }
        if (changed) save()
    }

    fun rememberEnderChest(page: Int, totalPages: Int, menu: ChestMenu) {
        ensureLoaded()
        val changed = (1..totalPages).fold(false) { anyChanged, number ->
            availablePages.add(StoragePageKey.enderChest(number)) || anyChanged
        }
        if (changed) save()
        remember(StoragePageKey.enderChest(page), menu)
    }

    fun rememberOverview(menu: ChestMenu) {
        ensureLoaded()
        var changed = !hasDiscoveredOverview
        hasDiscoveredOverview = true
        (1..9).forEach { changed = rememberOverviewSlot(StoragePageKey.enderChest(it), menu) || changed }
        (1..18).forEach { changed = rememberOverviewSlot(StoragePageKey.backpack(it), menu) || changed }
        if (changed) save()
    }

    fun page(page: Int): CachedEnderChestPage? {
        ensureLoaded()
        return pages[StoragePageKey.enderChest(page)]
    }

    fun page(key: StoragePageKey): CachedEnderChestPage? {
        ensureLoaded()
        return pages[key]
    }

    fun allPages(): List<StoragePageKey> {
        ensureLoaded()
        return StoragePagePreferences.order(availablePages + pages.keys)
    }

    fun clear() {
        pages.clear()
        availablePages.clear()
        hasDiscoveredOverview = false
        save()
    }

    private fun rememberOverviewSlot(key: StoragePageKey, menu: ChestMenu): Boolean {
        val stack = menu.getSlot(key.overviewSlot).item
        if (stack.isEmpty) return false
        if (stack.isEmptyStorageSlot()) {
            val availableChanged = availablePages.remove(key)
            val pageChanged = pages.remove(key) != null
            return availableChanged || pageChanged
        } else {
            return availablePages.add(key)
        }
    }

    private fun ensureLoaded() {
        val profile = ProfileItemCache.currentProfile() ?: return
        if (loadedProfile == profile) return
        loadedProfile = profile
        pages.clear()
        availablePages.clear()
        hasDiscoveredOverview = false
        lastSavedJson = ProfileItemCache.read("storage", profile)
        val json = lastSavedJson ?: return
        runCatching {
            val saved = gson.fromJson(json, SavedStorage::class.java)
            hasDiscoveredOverview = saved.overviewDiscovered
            saved.available.mapNotNull(::decodeKey).forEach(availablePages::add)
            saved.pages.forEach { savedPage ->
                val type = runCatching { StoragePageType.valueOf(savedPage.type) }.getOrNull() ?: return@forEach
                val key = StoragePageKey(type, savedPage.number)
                pages[key] = CachedEnderChestPage(key, savedPage.rows, savedPage.items.map(ProfileItemCache::decode))
            }
        }.onFailure {
            logger.warn("Could not load storage cache for $profile", it)
        }
    }

    private fun save() {
        val profile = loadedProfile ?: ProfileItemCache.currentProfile() ?: return
        val saved = SavedStorage(
            overviewDiscovered = hasDiscoveredOverview,
            available = availablePages.map(::encodeKey).toMutableList(),
            pages = pages.values.map { page ->
                SavedPage(
                    type = page.key.type.name,
                    number = page.key.number,
                    rows = page.rows,
                    items = page.items.map(ProfileItemCache::encode).toMutableList(),
                )
            }.toMutableList(),
        )
        val json = gson.toJson(saved)
        if (json == lastSavedJson) return
        lastSavedJson = json
        ProfileItemCache.write("storage", profile, json)
    }

    private fun encodeKey(key: StoragePageKey): String = "${key.type.name}:${key.number}"

    private fun decodeKey(encoded: String): StoragePageKey? {
        val parts = encoded.split(':', limit = 2)
        val type = parts.getOrNull(0)?.let { runCatching { StoragePageType.valueOf(it) }.getOrNull() } ?: return null
        val number = parts.getOrNull(1)?.toIntOrNull() ?: return null
        return StoragePageKey(type, number)
    }

    private fun ItemStack.isEmptyStorageSlot(): Boolean =
        VanillaItemIds.isItem(this, "red_stained_glass_pane") ||
            VanillaItemIds.isItem(this, "brown_stained_glass_pane") ||
            VanillaItemIds.isItem(this, "gray_dye")
}
