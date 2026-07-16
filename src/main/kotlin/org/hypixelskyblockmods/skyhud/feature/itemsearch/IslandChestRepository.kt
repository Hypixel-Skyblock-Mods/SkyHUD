package org.hypixelskyblockmods.skyhud.feature.itemsearch

import com.google.gson.GsonBuilder
import java.util.UUID
import net.minecraft.core.BlockPos
import net.minecraft.world.item.ItemStack
import org.hypixelskyblockmods.skyhud.integration.skyblockapi.SkyBlockProfileIdentity
import org.hypixelskyblockmods.skyhud.integration.skyblockapi.SkyblockApiItemSearchAdapter
import org.hypixelskyblockmods.skyhud.integration.skyblockapi.SkyblockApiStorageAdapter
import org.hypixelskyblockmods.skyhud.util.ItemStackSerialization

object IslandChestRepository {
    private const val OPEN_BIND_TICKS = 40L
    private const val SAVE_DEBOUNCE_MILLIS = 500L
    private const val HIGHLIGHT_MILLIS = 20_000L
    private const val WARP_HIGHLIGHT_TIMEOUT_MILLIS = 30_000L

    private data class ProfileKey(val accountUuid: UUID, val profileName: String)
    private data class ChestKey(val positions: List<BlockPos>) {
        val identity: String = positions.joinToString(";") { "${it.x},${it.y},${it.z}" }
    }
    private data class ObservedItem(val slot: Int, val stack: ItemStack)
    private data class ChestSnapshot(
        val key: ChestKey,
        val updatedAtEpochMillis: Long,
        val items: List<ObservedItem>,
    )
    private data class PendingOpen(val key: ChestKey, val expiresAtTick: Long)
    private data class ActiveOpen(val key: ChestKey, val containerId: Int)
    private data class PendingHighlight(
        val profile: ProfileKey?,
        val positions: List<BlockPos>,
        val expiresAtEpochMillis: Long,
    )

    private data class SavedPosition(var x: Int = 0, var y: Int = 0, var z: Int = 0)
    private data class SavedItem(var slot: Int = 0, var stack: String = "")
    private data class SavedChest(
        var positions: MutableList<SavedPosition> = mutableListOf(),
        var updatedAtEpochMillis: Long = 0,
        var items: MutableList<SavedItem> = mutableListOf(),
    )
    private data class SavedData(var schemaVersion: Int = 1, var chests: MutableList<SavedChest> = mutableListOf())

    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val chests = linkedMapOf<String, ChestSnapshot>()
    private var loaded = false
    private var loadedProfile: ProfileKey? = null
    private var activeIdentity: SkyBlockProfileIdentity? = null
    private var lastSavedJson: String? = null
    private var saveAfterEpochMillis: Long? = null
    private var tick = 0L
    private var pendingOpen: PendingOpen? = null
    private var activeOpen: ActiveOpen? = null
    private var activeHighlight: PendingHighlight? = null
    private var waitingForIslandHighlight: PendingHighlight? = null

    fun initializeSource() {
        ItemSourceRegistry.register(ItemSourceId.ISLAND_CHESTS, ::snapshot)
    }

    fun onClientTick() {
        tick++
        if (pendingOpen?.expiresAtTick?.let { tick > it } == true) pendingOpen = null
        val now = System.currentTimeMillis()
        val currentProfile = SkyblockApiStorageAdapter.currentProfile()?.let { ProfileKey(it.accountUuid, it.profileName) }
        if (activeHighlight?.profile?.let { it != currentProfile } == true) activeHighlight = null
        if (waitingForIslandHighlight?.profile != currentProfile) waitingForIslandHighlight = null
        if (activeHighlight?.expiresAtEpochMillis?.let { now > it } == true) activeHighlight = null
        waitingForIslandHighlight?.let { waiting ->
            if (now > waiting.expiresAtEpochMillis) {
                waitingForIslandHighlight = null
            } else if (SkyblockApiItemSearchAdapter.isOwnPrivateIsland()) {
                activeHighlight = PendingHighlight(waiting.profile, waiting.positions, now + HIGHLIGHT_MILLIS)
                waitingForIslandHighlight = null
            }
        }
        saveAfterEpochMillis?.let { if (now >= it) saveNow() }
    }

    fun observeChestRightClick(positions: List<BlockPos>) {
        if (!SkyblockApiItemSearchAdapter.isOwnPrivateIsland()) return
        val key = chestKey(positions) ?: return
        pendingOpen = PendingOpen(key, tick + OPEN_BIND_TICKS)
    }

    fun initializeContainer(containerId: Int, items: List<ItemStack>) {
        ensureLoaded()
        val pending = pendingOpen ?: return
        if (tick > pending.expiresAtTick) {
            pendingOpen = null
            return
        }
        val expectedSlots = pending.key.positions.size * 27
        if (items.size != expectedSlots) return
        pendingOpen = null
        activeOpen = ActiveOpen(pending.key, containerId)
        capture(pending.key, items)
    }

    fun onContainerChanged(containerId: Int, items: List<ItemStack>) {
        val active = activeOpen?.takeIf { it.containerId == containerId } ?: return
        val expectedSlots = active.key.positions.size * 27
        if (items.size != expectedSlots) return
        capture(active.key, items)
    }

    fun onContainerClosed() {
        if (activeIdentity == null) activeOpen?.let { chests.remove(it.key.identity) }
        activeOpen = null
        pendingOpen = null
        saveNow()
    }

    fun onBlockChanged(pos: BlockPos, remainsChest: Boolean) {
        if (remainsChest) return
        ensureLoaded()
        val removed = chests.values.filter { snapshot -> snapshot.key.positions.any { it == pos } }
        if (removed.isEmpty()) return
        removed.forEach { chests.remove(it.key.identity) }
        if (activeOpen?.key?.positions?.any { it == pos } == true) activeOpen = null
        scheduleSave()
    }

    fun highlight(positions: List<BlockPos>, afterIslandWarp: Boolean = false) {
        val canonical = chestKey(positions)?.positions ?: return
        val identity = SkyblockApiStorageAdapter.currentProfile()
        val profile = identity?.let { ProfileKey(it.accountUuid, it.profileName) }
        val now = System.currentTimeMillis()
        if (afterIslandWarp) {
            if (profile == null) return
            waitingForIslandHighlight = PendingHighlight(profile, canonical, now + WARP_HIGHLIGHT_TIMEOUT_MILLIS)
            activeHighlight = null
        } else {
            activeHighlight = PendingHighlight(profile, canonical, now + HIGHLIGHT_MILLIS)
            waitingForIslandHighlight = null
        }
    }

    fun highlightedPositions(): List<BlockPos> = activeHighlight
        ?.takeIf { System.currentTimeMillis() <= it.expiresAtEpochMillis }
        ?.positions
        ?.map(BlockPos::immutable)
        .orEmpty()

    fun clearCurrentProfile() {
        val profile = SkyblockApiStorageAdapter.currentProfile() ?: return
        chests.clear()
        activeOpen = null
        pendingOpen = null
        lastSavedJson = null
        saveAfterEpochMillis = null
        SkyBlockProfileStore.clear("island-chests", profile)
    }

    fun resetSession() {
        saveNow()
        loaded = false
        loadedProfile = null
        activeIdentity = null
        lastSavedJson = null
        saveAfterEpochMillis = null
        chests.clear()
        pendingOpen = null
        activeOpen = null
        activeHighlight = null
        waitingForIslandHighlight = null
    }

    fun flush() = saveNow()

    internal fun canonicalPositions(positions: List<BlockPos>): List<BlockPos> = positions
        .distinct()
        .sortedWith(compareBy<BlockPos>({ it.x }, { it.y }, { it.z }))
        .map(BlockPos::immutable)

    private fun snapshot(): List<SearchableItem> {
        ensureLoaded()
        if (!SkyblockApiItemSearchAdapter.isOnSkyBlock()) return emptyList()
        val visible = if (activeIdentity == null) {
            activeOpen?.let { active -> chests[active.key.identity]?.let(::listOf) }.orEmpty()
        } else {
            chests.values.toList()
        }
        val activeKey = activeOpen?.key?.identity
        return visible.flatMap { chest ->
            chest.items.mapNotNull { item ->
                SkyblockApiItemSearchAdapter.searchable(
                    item.stack,
                    item.stack.count.toLong(),
                    ItemSourceId.ISLAND_CHESTS,
                    ItemLocation.IslandChest(chest.key.positions, item.slot),
                    ItemNavigationAction.IslandChest(chest.key.positions),
                    if (chest.key.identity == activeKey) ItemDataOrigin.LIVE_MENU else ItemDataOrigin.LOCAL_OBSERVATION,
                    chest.updatedAtEpochMillis,
                )
            }
        }
    }

    private fun ensureLoaded() {
        val identity = SkyblockApiStorageAdapter.currentProfile()
        val key = identity?.let { ProfileKey(it.accountUuid, it.profileName) }
        if (loaded && loadedProfile == key) return
        loaded = true
        loadedProfile = key
        activeIdentity = identity
        chests.clear()
        lastSavedJson = identity?.let { SkyBlockProfileStore.read("island-chests", it) }
        val json = lastSavedJson ?: return
        runCatching {
            gson.fromJson(json, SavedData::class.java).chests.forEach { saved ->
                val keyForChest = chestKey(saved.positions.map { BlockPos(it.x, it.y, it.z) }) ?: return@forEach
                chests[keyForChest.identity] = ChestSnapshot(
                    keyForChest,
                    saved.updatedAtEpochMillis,
                    saved.items.mapNotNull { item ->
                        ItemStackSerialization.decode(item.stack).takeUnless(ItemStack::isEmpty)?.let { ObservedItem(item.slot, it) }
                    },
                )
            }
        }
    }

    private fun capture(key: ChestKey, stacks: List<ItemStack>) {
        val items = stacks.mapIndexedNotNull { slot, stack -> stack.takeUnless(ItemStack::isEmpty)?.copy()?.let { ObservedItem(slot, it) } }
        val previous = chests[key.identity]
        if (previous != null && observedMatches(previous.items, items)) return
        chests[key.identity] = ChestSnapshot(key, System.currentTimeMillis(), items)
        scheduleSave()
    }

    private fun scheduleSave() {
        if (activeIdentity != null) saveAfterEpochMillis = System.currentTimeMillis() + SAVE_DEBOUNCE_MILLIS
    }

    private fun saveNow() {
        saveAfterEpochMillis = null
        val profile = activeIdentity ?: return
        val saved = SavedData(chests = chests.values.map { chest ->
            SavedChest(
                chest.key.positions.map { SavedPosition(it.x, it.y, it.z) }.toMutableList(),
                chest.updatedAtEpochMillis,
                chest.items.map { SavedItem(it.slot, ItemStackSerialization.encode(it.stack)) }.toMutableList(),
            )
        }.toMutableList())
        val json = gson.toJson(saved)
        if (json == lastSavedJson) return
        if (SkyBlockProfileStore.write("island-chests", profile, json)) lastSavedJson = json
    }

    private fun chestKey(positions: List<BlockPos>): ChestKey? {
        val canonical = canonicalPositions(positions)
        return canonical.takeIf { it.size in 1..2 }?.let(::ChestKey)
    }

    private fun observedMatches(first: List<ObservedItem>, second: List<ObservedItem>): Boolean =
        first.size == second.size && first.zip(second).all { (left, right) ->
            left.slot == right.slot && ItemStack.matches(left.stack, right.stack)
        }
}
