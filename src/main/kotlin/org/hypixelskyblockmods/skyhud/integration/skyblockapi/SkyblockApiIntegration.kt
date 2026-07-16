package org.hypixelskyblockmods.skyhud.integration.skyblockapi

import net.minecraft.client.Minecraft
import net.minecraft.world.entity.player.Inventory
import org.hypixelskyblockmods.skyhud.feature.enderchest.EnderChestController
import org.hypixelskyblockmods.skyhud.feature.equipment.EquipmentController
import org.hypixelskyblockmods.skyhud.feature.itemsearch.ItemSourceRegistry
import org.hypixelskyblockmods.skyhud.feature.itemsearch.PlayerInventorySearchRepository
import org.hypixelskyblockmods.skyhud.feature.itemsearch.SackOfSacksRepository
import org.hypixelskyblockmods.skyhud.feature.itemsearch.SkyHudRepositoryItemSources
import org.hypixelskyblockmods.skyhud.feature.loadouts.LoadoutController
import org.hypixelskyblockmods.skyhud.feature.wardrobe.WardrobeController
import tech.thatgravyboat.skyblockapi.api.SkyBlockAPI
import tech.thatgravyboat.skyblockapi.api.events.profile.ProfileChangeEvent
import tech.thatgravyboat.skyblockapi.api.events.screen.InventoryChangeEvent
import tech.thatgravyboat.skyblockapi.api.events.screen.PlayerInventoryChangeEvent

object SkyblockApiIntegration {
    fun initialize() {
        ItemSourceRegistry.clear()
        SkyblockApiItemSearchAdapter.initializeSources()
        SkyHudRepositoryItemSources.initialize()
        PlayerInventorySearchRepository.initializeSource()
        SackOfSacksRepository.initializeSource()
        SkyBlockAPI.eventBus.register<PlayerInventoryChangeEvent> {
            Minecraft.getInstance().execute(PlayerInventorySearchRepository::onInventoryChanged)
        }
        SkyBlockAPI.eventBus.register<InventoryChangeEvent> { event ->
            if (!event.title.equals("Sack of Sacks", ignoreCase = true)) return@register
            val items = event.inventory
                .filterNot { it.container is Inventory }
                .mapNotNull { slot ->
                    slot.item.takeUnless { it.isEmpty || !SkyblockApiItemSearchAdapter.hasSkyBlockId(it) }
                        ?.let { slot.index to it.copy() }
                }
            Minecraft.getInstance().execute { SackOfSacksRepository.remember(items) }
        }
        SkyBlockAPI.eventBus.register<ProfileChangeEvent> {
            Minecraft.getInstance().execute {
                EnderChestController.onProfileChanged()
                LoadoutController.onProfileChanged()
                WardrobeController.onProfileChanged()
                EquipmentController.onProfileChanged()
                PlayerInventorySearchRepository.resetSession()
                SackOfSacksRepository.resetSession()
            }
        }
    }
}
