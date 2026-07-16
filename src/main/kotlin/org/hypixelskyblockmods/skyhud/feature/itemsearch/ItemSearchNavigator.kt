package org.hypixelskyblockmods.skyhud.feature.itemsearch

import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import org.hypixelskyblockmods.skyhud.config.SkyHudConfigManager
import org.hypixelskyblockmods.skyhud.feature.enderchest.EnderChestController
import org.hypixelskyblockmods.skyhud.feature.equipment.EquipmentController
import org.hypixelskyblockmods.skyhud.feature.loadouts.LoadoutController
import org.hypixelskyblockmods.skyhud.feature.wardrobe.WardrobeController
import org.hypixelskyblockmods.skyhud.integration.skyblockapi.SkyblockApiItemSearchAdapter
import org.hypixelskyblockmods.skyhud.platform.ScreenCompat

object ItemSearchNavigator {
    fun navigate(item: SearchableItem) {
        when (val action = item.action) {
            ItemNavigationAction.None -> status("${item.location.label} is informational; there is no stable direct route.")
            is ItemNavigationAction.Command -> navigateCommand(item, action.command)
            is ItemNavigationAction.Storage -> navigateStorage(item, action)
            is ItemNavigationAction.Collection -> {
                ItemSearchController.closeForNavigation()
                when (action.type) {
                    CollectionType.LOADOUT -> LoadoutController.navigateToSearchResult(action.page, action.setId)
                    CollectionType.WARDROBE -> WardrobeController.navigateToSearchResult(action.page, action.setId)
                    CollectionType.EQUIPMENT -> EquipmentController.navigateToSearchResult(action.page, action.setId)
                }
            }
            is ItemNavigationAction.Inventory -> {
                if (action.realm != SkyblockApiItemSearchAdapter.currentRealm()) {
                    status("${item.location.label} is in the other realm and is informational here.")
                    return
                }
                val player = Minecraft.getInstance().player ?: return
                ItemSearchController.closeForNavigation()
                ScreenCompat.setScreen(HighlightedInventoryScreen(player, action.slot))
            }
            is ItemNavigationAction.IslandChest -> navigateIsland(action)
        }
    }

    private fun navigateCommand(item: SearchableItem, command: String) {
        val inRift = SkyblockApiItemSearchAdapter.currentRealm() == InventoryRealm.RIFT
        val requiresNormalRealm = item.source in setOf(
            ItemSourceId.EQUIPPED,
            ItemSourceId.ACCESSORY_BAG,
            ItemSourceId.SACKS,
            ItemSourceId.SACK_OF_SACKS,
            ItemSourceId.VAULT,
        )
        if (requiresNormalRealm && inRift) {
            status("${item.location.label} cannot be opened from the Rift.")
            return
        }
        ItemSearchController.closeForNavigation()
        Minecraft.getInstance().player?.connection?.sendCommand(command)
    }

    private fun navigateStorage(item: SearchableItem, action: ItemNavigationAction.Storage) {
        if (action.rift) {
            if (SkyblockApiItemSearchAdapter.currentRealm() != InventoryRealm.RIFT) {
                status("Rift Storage can only be opened while in the Rift.")
                return
            }
            ItemSearchController.closeForNavigation()
            Minecraft.getInstance().player?.connection?.sendCommand("ec ${action.page.number}")
            return
        }
        val stale = item.updatedAtEpochMillis?.let { updated ->
            item.origin != ItemDataOrigin.LIVE_MENU && item.origin != ItemDataOrigin.LIVE_PLAYER &&
                System.currentTimeMillis() - updated > 86_400_000L
        } == true
        ItemSearchController.closeForNavigation()
        EnderChestController.navigateToSearchResult(action.page, action.itemIndex, item.stack, stale)
    }

    private fun navigateIsland(action: ItemNavigationAction.IslandChest) {
        val config = SkyHudConfigManager.config.huds.itemSearch
        if (SkyblockApiItemSearchAdapter.isOwnPrivateIsland()) {
            ItemSearchController.closeForNavigation()
            IslandChestRepository.highlight(action.positions)
            return
        }
        if (!config.warpToIsland) {
            status("Enable Warp to Island in Item Search settings to warp before highlighting this chest.")
            return
        }
        ItemSearchController.closeForNavigation()
        IslandChestRepository.highlight(action.positions, afterIslandWarp = true)
        Minecraft.getInstance().player?.connection?.sendCommand("warp island")
    }

    private fun status(message: String) {
        Minecraft.getInstance().player?.sendSystemMessage(Component.literal("[SkyHUD] $message"))
    }
}
