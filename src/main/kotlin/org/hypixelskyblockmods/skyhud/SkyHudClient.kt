package org.hypixelskyblockmods.skyhud

import net.fabricmc.api.ClientModInitializer
import org.slf4j.LoggerFactory

object SkyHudClient : ClientModInitializer {
    private val logger = LoggerFactory.getLogger("SkyHUD")

    override fun onInitializeClient() {
        logger.info("SkyHUD initialized")
    }
}
