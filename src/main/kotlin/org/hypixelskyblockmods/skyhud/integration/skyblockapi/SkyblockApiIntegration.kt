package org.hypixelskyblockmods.skyhud.integration.skyblockapi

import net.minecraft.client.Minecraft
import org.hypixelskyblockmods.skyhud.feature.enderchest.EnderChestController
import org.hypixelskyblockmods.skyhud.feature.equipment.EquipmentController
import org.hypixelskyblockmods.skyhud.feature.loadouts.LoadoutController
import org.hypixelskyblockmods.skyhud.feature.wardrobe.WardrobeController
import tech.thatgravyboat.skyblockapi.api.SkyBlockAPI
import tech.thatgravyboat.skyblockapi.api.events.profile.ProfileChangeEvent

object SkyblockApiIntegration {
    fun initialize() {
        SkyBlockAPI.eventBus.register<ProfileChangeEvent> {
            Minecraft.getInstance().execute {
                EnderChestController.onProfileChanged()
                LoadoutController.onProfileChanged()
                WardrobeController.onProfileChanged()
                EquipmentController.onProfileChanged()
            }
        }
    }
}
