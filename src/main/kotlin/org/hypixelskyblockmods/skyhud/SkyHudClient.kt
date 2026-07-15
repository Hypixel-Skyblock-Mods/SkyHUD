package org.hypixelskyblockmods.skyhud

import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import org.hypixelskyblockmods.skyhud.feature.enderchest.EnderChestController
import org.slf4j.LoggerFactory

object SkyHudClient : ClientModInitializer {
    private val logger = LoggerFactory.getLogger("SkyHUD")

    override fun onInitializeClient() {
        ScreenEvents.AFTER_INIT.register(ScreenEvents.AfterInit { client, screen, _, _ ->
            EnderChestController.onScreenOpened(client, screen)
        })
        ClientTickEvents.END_CLIENT_TICK.register(EnderChestController::onClientTick)
        logger.info("SkyHUD initialized")
    }
}
