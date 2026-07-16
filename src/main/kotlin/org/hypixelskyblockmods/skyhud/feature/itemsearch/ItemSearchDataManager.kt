package org.hypixelskyblockmods.skyhud.feature.itemsearch

import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import org.hypixelskyblockmods.skyhud.feature.equipment.EquipmentRepository
import org.hypixelskyblockmods.skyhud.feature.loadouts.LoadoutRepository
import org.hypixelskyblockmods.skyhud.feature.wardrobe.WardrobeRepository
import org.hypixelskyblockmods.skyhud.integration.skyblockapi.SkyblockApiStorageAdapter

object ItemSearchDataManager {
    fun clearIslandChests() {
        if (SkyblockApiStorageAdapter.currentProfile() == null) {
            status("No SkyBlock profile is currently known.")
            return
        }
        IslandChestRepository.clearCurrentProfile()
        ItemSearchController.cancelIndex()
        status("Cleared island chests for the current SkyBlock profile.")
    }

    fun clearCurrentProfile() {
        val profile = SkyblockApiStorageAdapter.currentProfile()
        if (profile == null) {
            status("No SkyBlock profile is currently known.")
            return
        }
        PlayerInventorySearchRepository.clearCurrentProfile()
        SackOfSacksRepository.clearCurrentProfile()
        IslandChestRepository.clearCurrentProfile()
        LoadoutRepository.clearCurrentProfile()
        WardrobeRepository.sets.clearCurrentProfile()
        EquipmentRepository.sets.clearCurrentProfile()
        ItemSearchController.cancelIndex()
        status("Cleared SkyHUD search data for ${profile.profileName}.")
    }

    private fun status(message: String) {
        Minecraft.getInstance().player?.sendSystemMessage(Component.literal("[SkyHUD] $message"))
    }
}
