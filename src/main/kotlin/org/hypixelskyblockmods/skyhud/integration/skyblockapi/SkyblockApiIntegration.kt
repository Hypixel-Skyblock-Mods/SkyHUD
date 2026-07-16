package org.hypixelskyblockmods.skyhud.integration.skyblockapi

import net.minecraft.client.Minecraft
import org.hypixelskyblockmods.skyhud.feature.enderchest.EnderChestController
import tech.thatgravyboat.skyblockapi.api.SkyBlockAPI
import tech.thatgravyboat.skyblockapi.api.events.profile.ProfileChangeEvent

object SkyblockApiIntegration {
    fun initialize() {
        SkyBlockAPI.eventBus.register<ProfileChangeEvent> {
            Minecraft.getInstance().execute(EnderChestController::onProfileChanged)
        }
    }
}
