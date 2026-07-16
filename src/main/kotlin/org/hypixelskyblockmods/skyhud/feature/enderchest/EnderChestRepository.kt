package org.hypixelskyblockmods.skyhud.feature.enderchest

import com.google.gson.GsonBuilder
import java.util.UUID
import net.minecraft.world.inventory.ChestMenu
import net.minecraft.world.item.ItemStack
import org.hypixelskyblockmods.skyhud.feature.itemsearch.SkyBlockProfileStore
import org.hypixelskyblockmods.skyhud.integration.skyblockapi.SkyBlockProfileIdentity
import org.hypixelskyblockmods.skyhud.integration.skyblockapi.SkyblockApiStorageAdapter
import org.hypixelskyblockmods.skyhud.util.ItemStackSerialization
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
    val origin: StoragePageOrigin = StoragePageOrigin.SKYBLOCK_API,
) {
    val page: Int
        get() = key.number
}

enum class StoragePageOrigin {
    LIVE_MENU,
    SKYHUD_PROFILE,
    SKYBLOCK_API,
}

object EnderChestRepository {
    private const val SAVE_DEBOUNCE_MILLIS = 500L
    private const val TIMESTAMP_REFRESH_MILLIS = 60_000L

    private data class StorageProfileKey(
        val accountUuid: UUID,
        val profileName: String,
    )

    private data class SavedItem(var index: Int = 0, var stack: String = "")
    private data class SavedPage(
        var type: String = StoragePageType.ENDER_CHEST.name,
        var number: Int = 1,
        var rows: Int = 1,
        var updatedAtEpochMillis: Long = 0,
        var items: MutableList<SavedItem> = mutableListOf(),
    )
    private data class SavedStoragePages(
        var schemaVersion: Int = 1,
        var pages: MutableList<SavedPage> = mutableListOf(),
    )

    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val availablePages = sortedSetOf<StoragePageKey>()
    private val observedPages = linkedMapOf<StoragePageKey, CachedEnderChestPage>()
    private var apiPages = emptyMap<StoragePageKey, CachedEnderChestPage>()
    private var loadedProfile: StorageProfileKey? = null
    private var activeIdentity: SkyBlockProfileIdentity? = null
    private var lastSavedJson: String? = null
    private var saveAfterEpochMillis: Long? = null
    private var livePageKey: StoragePageKey? = null
    private var liveMenu: ChestMenu? = null
    var hasDiscoveredOverview: Boolean = false
        private set

    fun remember(page: Int, menu: ChestMenu) {
        remember(StoragePageKey.enderChest(page), menu)
    }

    fun remember(key: StoragePageKey, menu: ChestMenu) {
        ensureProfileState()
        captureLivePage()
        if (availablePages.add(key)) activeIdentity?.let { StoragePageCatalog.remember(it, setOf(key)) }
        livePageKey = key
        liveMenu = menu
        captureLivePage()
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
        captureLivePage()
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
                origin = StoragePageOrigin.SKYBLOCK_API,
            )
        }
        if (profile == currentProfileKey()) apiPages = pages
    }

    fun page(page: Int): CachedEnderChestPage? = page(StoragePageKey.enderChest(page))

    fun page(key: StoragePageKey): CachedEnderChestPage? {
        ensureProfileState()
        val menu = liveMenu
        if (key == livePageKey && menu != null) return livePage(key, menu)
        return cachedPage(key)
    }

    fun allPages(): List<StoragePageKey> {
        ensureProfileState()
        val visible = if (hasDiscoveredOverview) {
            availablePages.toMutableSet()
        } else {
            (availablePages + observedPages.keys + apiPages.keys).toMutableSet()
        }
        livePageKey?.let(visible::add)
        return StoragePagePreferences.order(visible)
    }

    fun searchSnapshot(): List<CachedEnderChestPage> = allPages().mapNotNull(::page).map { page ->
        page.copy(items = page.items.map(ItemStack::copy))
    }

    fun clearLiveBacking() {
        captureLivePage()
        saveNow()
        livePageKey = null
        liveMenu = null
    }

    fun resetSession() {
        captureLivePage()
        saveNow()
        availablePages.clear()
        observedPages.clear()
        apiPages = emptyMap()
        hasDiscoveredOverview = false
        livePageKey = null
        liveMenu = null
        activeIdentity = null
        loadedProfile = null
        lastSavedJson = null
        saveAfterEpochMillis = null
    }

    fun onClientTick() {
        captureLivePage()
        val deadline = saveAfterEpochMillis ?: return
        if (System.currentTimeMillis() >= deadline) saveNow()
    }

    fun flush() {
        captureLivePage()
        saveNow()
    }

    fun clearCurrentProfile() {
        val profile = SkyblockApiStorageAdapter.currentProfile() ?: return
        observedPages.clear()
        loadedProfile = profile.toStorageKey()
        activeIdentity = profile
        lastSavedJson = null
        saveAfterEpochMillis = null
        SkyBlockProfileStore.clear("storage-pages", profile)
    }

    private fun ensureProfileState(): StorageProfileKey? {
        val identity = SkyblockApiStorageAdapter.currentProfile()
        val profile = identity?.toStorageKey()
        if (loadedProfile != profile) {
            val carryUnknownLivePage = loadedProfile == null && profile != null
            val previousLiveKey = livePageKey
            val previousLiveMenu = liveMenu
            captureLivePage()
            saveNow()
            availablePages.clear()
            observedPages.clear()
            apiPages = emptyMap()
            hasDiscoveredOverview = false
            livePageKey = null
            liveMenu = null
            loadedProfile = profile
            lastSavedJson = null
            saveAfterEpochMillis = null
            if (identity != null) {
                val saved = StoragePageCatalog.snapshot(identity)
                availablePages.addAll(saved.availablePages)
                hasDiscoveredOverview = saved.overviewDiscovered
                loadObservedPages(identity)
            }
            activeIdentity = identity
            if (carryUnknownLivePage && previousLiveKey != null && previousLiveMenu != null) {
                livePageKey = previousLiveKey
                liveMenu = previousLiveMenu
                captureLivePage()
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
        return CachedEnderChestPage(key, itemRows, copiedItems, origin = StoragePageOrigin.LIVE_MENU)
    }

    private fun cachedPage(key: StoragePageKey): CachedEnderChestPage? {
        return preferredStoragePage(observedPages[key], apiPages[key])
    }

    private fun captureLivePage() {
        val key = livePageKey ?: return
        val menu = liveMenu ?: return
        val now = System.currentTimeMillis()
        val observed = livePage(key, menu).copy(
            updatedAtEpochMillis = now,
            origin = StoragePageOrigin.SKYHUD_PROFILE,
        )
        val previous = observedPages[key]
        val refreshTimestamp = previous?.updatedAtEpochMillis?.let { now - it >= TIMESTAMP_REFRESH_MILLIS } != false
        if (pagesMatch(previous, observed) && !refreshTimestamp) return
        observedPages[key] = observed
        if (activeIdentity != null) saveAfterEpochMillis = now + SAVE_DEBOUNCE_MILLIS
    }

    private fun loadObservedPages(profile: SkyBlockProfileIdentity) {
        lastSavedJson = SkyBlockProfileStore.read("storage-pages", profile)
        val json = lastSavedJson ?: return
        runCatching {
            gson.fromJson(json, SavedStoragePages::class.java).pages.forEach { saved ->
                val type = runCatching { StoragePageType.valueOf(saved.type) }.getOrNull() ?: return@forEach
                val validNumbers = if (type == StoragePageType.ENDER_CHEST) 1..9 else 1..18
                if (saved.number !in validNumbers || saved.rows !in 1..5 || saved.updatedAtEpochMillis <= 0) return@forEach
                val items = MutableList(saved.rows * 9) { ItemStack.EMPTY }
                saved.items.forEach { item ->
                    if (item.index in items.indices) items[item.index] = ItemStackSerialization.decode(item.stack)
                }
                val key = StoragePageKey(type, saved.number)
                observedPages[key] = CachedEnderChestPage(
                    key,
                    saved.rows,
                    items,
                    saved.updatedAtEpochMillis,
                    StoragePageOrigin.SKYHUD_PROFILE,
                )
            }
        }
    }

    private fun saveNow() {
        saveAfterEpochMillis = null
        val profile = activeIdentity ?: return
        val saved = SavedStoragePages(pages = observedPages.values.map { page ->
            SavedPage(
                type = page.key.type.name,
                number = page.key.number,
                rows = page.rows,
                updatedAtEpochMillis = page.updatedAtEpochMillis ?: System.currentTimeMillis(),
                items = page.items.mapIndexedNotNull { index, stack ->
                    stack.takeUnless(ItemStack::isEmpty)
                        ?.let(ItemStackSerialization::encode)
                        ?.takeIf(String::isNotBlank)
                        ?.let { SavedItem(index, it) }
                }.toMutableList(),
            )
        }.toMutableList())
        val json = gson.toJson(saved)
        if (json == lastSavedJson) return
        if (SkyBlockProfileStore.write("storage-pages", profile, json)) lastSavedJson = json
    }

    private fun pagesMatch(first: CachedEnderChestPage?, second: CachedEnderChestPage): Boolean {
        if (first == null || first.rows != second.rows || first.items.size != second.items.size) return false
        return first.items.zip(second.items).all { (left, right) -> ItemStack.matches(left, right) }
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

internal fun preferredStoragePage(
    observed: CachedEnderChestPage?,
    api: CachedEnderChestPage?,
): CachedEnderChestPage? = when {
    observed == null -> api
    api == null -> observed
    (observed.updatedAtEpochMillis ?: Long.MIN_VALUE) >= (api.updatedAtEpochMillis ?: Long.MIN_VALUE) -> observed
    else -> api
}
