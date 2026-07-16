package org.hypixelskyblockmods.skyhud

import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.fabricmc.fabric.api.client.command.v2.ClientCommands
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.minecraft.client.Minecraft
import org.hypixelskyblockmods.skyhud.config.SkyHudConfigManager
import org.hypixelskyblockmods.skyhud.feature.enderchest.EnderChestController
import org.hypixelskyblockmods.skyhud.feature.equipment.EquipmentController
import org.hypixelskyblockmods.skyhud.feature.loadouts.LoadoutController
import org.hypixelskyblockmods.skyhud.feature.wardrobe.WardrobeController
import org.hypixelskyblockmods.skyhud.gui.SkyHudBackdrop
import org.hypixelskyblockmods.skyhud.integration.skyblockapi.SkyblockApiIntegration
import org.slf4j.LoggerFactory

object SkyHudClient : ClientModInitializer {
    private val logger = LoggerFactory.getLogger("SkyHUD")

    override fun onInitializeClient() {
        SkyHudConfigManager.initialize()
        SkyblockApiIntegration.initialize()
        ClientCommandRegistrationCallback.EVENT.register { dispatcher, _ ->
            dispatcher.register(
                ClientCommands.literal("skyhud").executes {
                    Minecraft.getInstance().execute(SkyHudConfigManager::open)
                    1
                },
            )
        }
        ScreenEvents.AFTER_INIT.register(ScreenEvents.AfterInit { client, screen, _, _ ->
            EnderChestController.onScreenOpened(client, screen)
            EquipmentController.onScreenOpened(client, screen)
            LoadoutController.onScreenOpened(client, screen)
            WardrobeController.onScreenOpened(client, screen)
        })
        ClientTickEvents.END_CLIENT_TICK.register(EnderChestController::onClientTick)
        ClientTickEvents.END_CLIENT_TICK.register(EquipmentController::onClientTick)
        ClientTickEvents.END_CLIENT_TICK.register(LoadoutController::onClientTick)
        ClientTickEvents.END_CLIENT_TICK.register(WardrobeController::onClientTick)
        ClientLifecycleEvents.CLIENT_STOPPING.register {
            SkyHudConfigManager.save()
            SkyHudBackdrop.close()
        }
        logger.info("SkyHUD initialized")
    }
}
