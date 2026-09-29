package org.hypixelskyblockmods.skyhud.feature.enderchest

import com.google.gson.GsonBuilder
import java.util.UUID
import net.minecraft.world.inventory.ChestMenu
import net.minecraft.world.item.ItemStack
import org.hypixelskyblockmods.skyhud.profile.SkyHudProfileStore
import org.hypixelskyblockmods.skyhud.integration.skyblockapi.SkyBlockProfileIdentity
import org.hypixelskyblockmods.skyhud.integration.skyblockapi.SkyblockApiStorageAdapter
import org.hypixelskyblockmods.skyhud.util.ItemStackSerialization
import org.hypixelskyblockmods.skyhud.util.VanillaItemIds

enum class StoragePageType {
    ENDER_CHEST,
    BACKPACK,
    RIFT,
    ;

    val validNumbers: IntRange
        get() = when (this) {
            ENDER_CHEST -> 1..9
            BACKPACK -> 1..18
            RIFT -> 1..2
        }
}

data class StoragePageKey(
    val type: StoragePageType,
    val number: Int,
) : Comparable<StoragePageKey> {
    val overviewSlot: Int?
        get() = when (type) {
            StoragePageType.ENDER_CHEST -> 8 + number
            StoragePageType.BACKPACK -> 26 + number
            StoragePageType.RIFT -> null
        }

    val displayName: String
        get() = when (type) {
            StoragePageType.ENDER_CHEST -> "ENDER CHEST #$number"
            StoragePageType.BACKPACK -> "BACKPACK #$number"
            StoragePageType.RIFT -> "RIFT STORAGE #$number"
        }

    val navigationCommand: String
        get() = when (type) {
            StoragePageType.ENDER_CHEST -> "enderchest $number"
            StoragePageType.BACKPACK -> "backpack $number"
            StoragePageType.RIFT -> "enderchest $number"
        }

    override fun compareTo(other: StoragePageKey): Int =
        sortIndex().compareTo(other.sortIndex())

    private fun sortIndex(): Int = when (type) {
        StoragePageType.ENDER_CHEST -> number - 1
        StoragePageType.BACKPACK -> 9 + number - 1
        StoragePageType.RIFT -> 27 + number - 1
    }

    companion object {
        fun enderChest(number: Int) = StoragePageKey(StoragePageType.ENDER_CHEST, number)

        fun backpack(number: Int) = StoragePageKey(StoragePageType.BACKPACK, number)

        fun rift(number: Int) = StoragePageKey(StoragePageType.RIFT, number)
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

    fun rememberRift(page: Int, totalPages: Int, menu: ChestMenu) {
        ensureProfileState()
        val discovered = (1..totalPages).map(StoragePageKey::rift)
        val changed = availablePages.removeIf {
            it.type == StoragePageType.RIFT && it.number !in 1..totalPages
        }
        if (availablePages.addAll(discovered) || changed) {
            activeIdentity?.let { StoragePageCatalog.replaceType(it, StoragePageType.RIFT, discovered) }
        }
        remember(StoragePageKey.rift(page), menu)
    }

    fun rememberOverview(menu: ChestMenu) {
        ensureProfileState()
        captureLivePage()
        val discovered = availablePages
            .filterTo(sortedSetOf()) { it.type == StoragePageType.RIFT }
        (1..9).map(StoragePageKey::enderChest).filterTo(discovered) { isAvailableOverviewSlot(it, menu) }
        (1..18).map(StoragePageKey::backpack).filterTo(discovered) { isAvailableOverviewSlot(it, menu) }
        val changed = !hasDiscoveredOverview || availablePages != discovered
        val removedObservedPages = observedPages.keys.removeIf {
            it.type != StoragePageType.RIFT && it !in discovered
        }
        apiPages = apiPages.filterKeys {
            it.type == StoragePageType.RIFT || it in discovered
        }
        availablePages.clear()
        availablePages.addAll(discovered)
        hasDiscoveredOverview = true
        if (changed) activeIdentity?.let { StoragePageCatalog.replaceOverview(it, discovered) }
        if (removedObservedPages && activeIdentity != null) {
            saveAfterEpochMillis = System.currentTimeMillis() + SAVE_DEBOUNCE_MILLIS
        }
        livePageKey = null
        liveMenu = null
        refreshApiSnapshot()
    }

    fun refreshApiSnapshot() {
        val profile = ensureProfileState() ?: return
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
        if (profile == currentProfileKey()) apiPages = mergeApiSnapshot(apiPages, pages)
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
        val visible = availablePages.toMutableSet()
        if (!hasDiscoveredOverview) {
            visible += observedPages.keys
            visible += apiPages.keys
        } else {
            observedPages.keys.filterTo(visible) { it.type == StoragePageType.RIFT }
            apiPages.keys.filterTo(visible) { it.type == StoragePageType.RIFT }
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
        saveAfterEpochMillis = null
        SkyHudProfileStore.clear("storage-pages", profile)
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
        val previous = observedPages[key]
        val itemCount = (menu.rowCount - 1) * 9
        if (
            previous != null && previous.items.any { !it.isEmpty } &&
            (0 until itemCount).all { menu.getSlot(it + 9).item.isEmpty }
        ) return
        val refreshTimestamp = previous?.updatedAtEpochMillis?.let { now - it >= TIMESTAMP_REFRESH_MILLIS } != false
        // Compare live references first; unchanged ticks and menu closes need no stack copies.
        if (
            previous != null && !refreshTimestamp && previous.rows == menu.rowCount - 1 &&
            previous.items.size == itemCount &&
            previous.items.indices.all { ItemStack.matches(previous.items[it], menu.getSlot(it + 9).item) }
        ) return
        observedPages[key] = livePage(key, menu).copy(
            updatedAtEpochMillis = now,
            origin = StoragePageOrigin.SKYHUD_PROFILE,
        )
        if (activeIdentity != null) saveAfterEpochMillis = now + SAVE_DEBOUNCE_MILLIS
    }

    private fun mergeApiSnapshot(
        previous: Map<StoragePageKey, CachedEnderChestPage>,
        incoming: Map<StoragePageKey, CachedEnderChestPage>,
    ): Map<StoragePageKey, CachedEnderChestPage> {
        if (previous.isEmpty()) return incoming
        val merged = previous.toMutableMap()
        incoming.forEach { (key, candidate) ->
            val remembered = merged[key]
            merged[key] = when {
                remembered == null -> candidate
                candidate.items.any { !it.isEmpty } -> candidate
                candidate.updatedAtEpochMillis != null &&
                    candidate.updatedAtEpochMillis > (remembered.updatedAtEpochMillis ?: Long.MIN_VALUE) -> candidate
                else -> remembered
            }
        }
        return merged
    }

    private fun loadObservedPages(profile: SkyBlockProfileIdentity) {
        val json = SkyHudProfileStore.read("storage-pages", profile) ?: return
        runCatching {
            gson.fromJson(json, SavedStoragePages::class.java).pages.forEach { saved ->
                val type = runCatching { StoragePageType.valueOf(saved.type) }.getOrNull() ?: return@forEach
                if (
                    saved.number !in type.validNumbers ||
                    saved.rows !in 1..5 ||
                    saved.updatedAtEpochMillis <= 0
                ) {
                    return@forEach
                }
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
        if (saveAfterEpochMillis == null) return
        val profile = activeIdentity ?: return
        saveAfterEpochMillis = null
        val snapshot = observedPages.values.map { page -> page.copy(items = page.items.map(ItemStack::copy)) }
        val encode = ItemStackSerialization.encoder()
        SkyHudProfileStore.writeAsync("storage-pages", profile) {
            val saved = SavedStoragePages(pages = snapshot.map { page ->
                SavedPage(
                    type = page.key.type.name,
                    number = page.key.number,
                    rows = page.rows,
                    updatedAtEpochMillis = page.updatedAtEpochMillis ?: System.currentTimeMillis(),
                    items = page.items.mapIndexedNotNull { index, stack ->
                        stack.takeUnless(ItemStack::isEmpty)
                            ?.let(encode)
                            ?.takeIf(String::isNotBlank)
                            ?.let { SavedItem(index, it) }
                    }.toMutableList(),
                )
            }.toMutableList())
            gson.toJson(saved)
        }
    }

    private fun isAvailableOverviewSlot(key: StoragePageKey, menu: ChestMenu): Boolean {
        val slot = key.overviewSlot ?: return false
        val stack = menu.getSlot(slot).item
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
    observed.items.none { !it.isEmpty } && api.items.any { !it.isEmpty } -> api
    api.items.none { !it.isEmpty } && observed.items.any { !it.isEmpty } -> observed
    (observed.updatedAtEpochMillis ?: Long.MIN_VALUE) >= (api.updatedAtEpochMillis ?: Long.MIN_VALUE) -> observed
    else -> api
}
