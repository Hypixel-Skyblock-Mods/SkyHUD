package org.hypixelskyblockmods.skyhud.feature.itemsearch

import com.mojang.blaze3d.platform.InputConstants
import java.util.concurrent.CompletableFuture
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.network.chat.Component
import org.hypixelskyblockmods.skyhud.config.ItemSearchSourceConfig
import org.hypixelskyblockmods.skyhud.config.SkyHudConfigManager
import org.hypixelskyblockmods.skyhud.integration.skyblockapi.SkyBlockProfileIdentity
import org.hypixelskyblockmods.skyhud.integration.skyblockapi.SkyblockApiItemSearchAdapter
import org.hypixelskyblockmods.skyhud.integration.skyblockapi.SkyblockApiStorageAdapter
import org.hypixelskyblockmods.skyhud.mixin.ContainerScreenAccessor
import org.hypixelskyblockmods.skyhud.platform.ScreenCompat
import org.slf4j.LoggerFactory

object ItemSearchController {
    data class ViewState(
        val index: ItemSearchIndex = ItemSearchIndex.EMPTY,
        val loading: Boolean = false,
        val failures: Map<ItemSourceId, Throwable> = emptyMap(),
    )

    private val logger = LoggerFactory.getLogger("SkyHUD Item Search")
    private var generation = 0L
    private var keyWasDown = false
    private var viewState = ViewState()
    private var activeScreen: ItemSearchScreen? = null
    private var sessionQuery = ""
    private var sessionCategory = ItemSourceCategory.ALL

    fun state(): ViewState = viewState

    fun open(query: String? = null) {
        val config = SkyHudConfigManager.config.huds.itemSearch
        if (!config.enabled) {
            status("Item Search is disabled in SkyHUD settings.")
            return
        }
        if (!SkyblockApiItemSearchAdapter.isOnSkyBlock()) {
            status("Item Search is only available on SkyBlock.")
            return
        }
        if (!config.preserveLastSearch) {
            sessionQuery = ""
            sessionCategory = ItemSourceCategory.ALL
        }
        if (query != null) sessionQuery = query.trim()
        val screen = ItemSearchScreen(sessionQuery, sessionCategory)
        activeScreen = screen
        ScreenCompat.setScreen(screen)
        rebuild(screen)
    }

    fun rebuild(screen: ItemSearchScreen? = activeScreen) {
        val targetScreen = screen ?: return
        if (!SkyblockApiItemSearchAdapter.isOnSkyBlock()) return
        val token = ++generation
        val profile = SkyblockApiStorageAdapter.currentProfile()
        viewState = ViewState(loading = true)
        targetScreen.onIndexUpdated()
        val enabled = enabledSources(SkyHudConfigManager.config.huds.itemSearch.sources)
        val sourceSnapshot = ItemSourceRegistry.snapshot(enabled)
        sourceSnapshot.failures.forEach { (source, failure) -> logger.warn("Item Search source ${source.displayName} is unavailable", failure) }
        CompletableFuture.supplyAsync { ItemSearchIndex.build(sourceSnapshot.items) }
            .whenComplete { index, failure ->
                Minecraft.getInstance().execute {
                    if (token != generation || profile != SkyblockApiStorageAdapter.currentProfile() || activeScreen !== targetScreen) return@execute
                    if (failure != null) {
                        logger.error("Could not aggregate the Item Search index", failure)
                        viewState = ViewState(failures = sourceSnapshot.failures + (ItemSourceId.INVENTORY to failure))
                    } else {
                        viewState = ViewState(index ?: ItemSearchIndex.EMPTY, failures = sourceSnapshot.failures)
                    }
                    targetScreen.onIndexUpdated()
                }
            }
    }

    fun onClientTick(client: Minecraft) {
        val config = SkyHudConfigManager.config.huds.itemSearch
        val down = config.enabled && InputConstants.isKeyDown(client.window, config.keybind)
        if (down && !keyWasDown && ScreenCompat.currentScreen() == null) open()
        keyWasDown = down
    }

    fun onScreenKeyPressed(screen: Screen, key: Int): Boolean {
        val config = SkyHudConfigManager.config.huds.itemSearch
        if (!config.enabled || key != config.keybind || screen.focused is EditBox) return false
        if (screen is ItemSearchScreen) {
            screen.onClose()
            return true
        }
        if (screen !is AbstractContainerScreen<*> || !SkyblockApiItemSearchAdapter.isOnSkyBlock()) return false
        val hovered = (screen as ContainerScreenAccessor).skyhudHoveredSlot()?.item
        val query = hovered?.takeUnless { it.isEmpty }?.let(SkyblockApiItemSearchAdapter::cleanName).orEmpty()
        Minecraft.getInstance().player?.closeContainer()
        Minecraft.getInstance().execute { open(query) }
        return true
    }

    fun remember(query: String, category: ItemSourceCategory) {
        sessionQuery = query
        sessionCategory = category
    }

    fun screenClosed(screen: ItemSearchScreen) {
        if (activeScreen === screen) activeScreen = null
        generation++
    }

    fun closeForNavigation() {
        generation++
        val screen = activeScreen
        activeScreen = null
        if (screen != null && ScreenCompat.currentScreen() === screen) ScreenCompat.setScreen(null)
    }

    fun cancelIndex() {
        generation++
        viewState = ViewState()
        activeScreen?.onIndexUpdated()
    }

    fun onProfileChanged() {
        generation++
        viewState = ViewState()
        sessionQuery = ""
        sessionCategory = ItemSourceCategory.ALL
        val screen = activeScreen
        activeScreen = null
        if (screen != null && ScreenCompat.currentScreen() === screen) ScreenCompat.setScreen(null)
        Minecraft.getInstance().player?.closeContainer()
    }

    private fun enabledSources(config: ItemSearchSourceConfig): Set<ItemSourceId> = buildSet {
        if (config.inventory) addAll(listOf(ItemSourceId.INVENTORY, ItemSourceId.EQUIPPED, ItemSourceId.RIFT))
        if (config.storage) add(ItemSourceId.STORAGE)
        if (config.loadouts) add(ItemSourceId.LOADOUTS)
        if (config.wardrobe) add(ItemSourceId.WARDROBE)
        if (config.equipmentWardrobe) add(ItemSourceId.EQUIPMENT_WARDROBE)
        if (config.accessoryBag) add(ItemSourceId.ACCESSORY_BAG)
        if (config.sacks) addAll(listOf(ItemSourceId.SACKS, ItemSourceId.SACK_OF_SACKS))
        if (config.vault) add(ItemSourceId.VAULT)
        if (config.forge) add(ItemSourceId.FORGE)
        if (config.museum) add(ItemSourceId.MUSEUM)
        if (config.islandChests) add(ItemSourceId.ISLAND_CHESTS)
        if (config.installedParts) add(ItemSourceId.INSTALLED_PARTS)
    }

    private fun status(message: String) {
        Minecraft.getInstance().player?.sendSystemMessage(Component.literal("[SkyHUD] $message"))
    }
}
