package org.hypixelskyblockmods.skyhud

import com.mojang.brigadier.arguments.StringArgumentType
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.fabricmc.fabric.api.client.command.v2.ClientCommands
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.minecraft.client.Minecraft
import net.minecraft.gizmos.GizmoStyle
import net.minecraft.gizmos.Gizmos
import org.hypixelskyblockmods.skyhud.config.SkyHudConfigManager
import org.hypixelskyblockmods.skyhud.feature.enderchest.EnderChestController
import org.hypixelskyblockmods.skyhud.feature.equipment.EquipmentController
import org.hypixelskyblockmods.skyhud.feature.loadouts.LoadoutController
import org.hypixelskyblockmods.skyhud.feature.itemsearch.PlayerInventorySearchRepository
import org.hypixelskyblockmods.skyhud.feature.itemsearch.IslandChestRepository
import org.hypixelskyblockmods.skyhud.feature.itemsearch.ItemSearchController
import org.hypixelskyblockmods.skyhud.feature.itemsearch.ItemSearchDataManager
import org.hypixelskyblockmods.skyhud.feature.wardrobe.WardrobeController
import org.hypixelskyblockmods.skyhud.gui.SkyHudBackdrop
import org.hypixelskyblockmods.skyhud.gui.SkyHudTheme
import org.hypixelskyblockmods.skyhud.integration.skyblockapi.SkyblockApiIntegration
import org.slf4j.LoggerFactory

object SkyHudClient : ClientModInitializer {
    private val logger = LoggerFactory.getLogger("SkyHUD")

    override fun onInitializeClient() {
        SkyHudConfigManager.initialize()
        SkyblockApiIntegration.initialize()
        ClientCommandRegistrationCallback.EVENT.register { dispatcher, _ ->
            dispatcher.register(
                ClientCommands.literal("skyhud")
                    .executes {
                        Minecraft.getInstance().execute(SkyHudConfigManager::open)
                        1
                    }
                    .then(
                        ClientCommands.literal("search")
                            .executes {
                                Minecraft.getInstance().execute { ItemSearchController.open() }
                                1
                            }
                            .then(
                                ClientCommands.literal("reset-island-chests").executes {
                                    Minecraft.getInstance().execute(ItemSearchDataManager::clearIslandChests)
                                    1
                                },
                            )
                            .then(
                                ClientCommands.argument("query", StringArgumentType.greedyString()).executes { context ->
                                    val query = StringArgumentType.getString(context, "query")
                                    Minecraft.getInstance().execute { ItemSearchController.open(query) }
                                    1
                                },
                            ),
                    ),
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
        ClientTickEvents.END_CLIENT_TICK.register { PlayerInventorySearchRepository.onClientTick() }
        ClientTickEvents.END_CLIENT_TICK.register { IslandChestRepository.onClientTick() }
        ClientTickEvents.END_CLIENT_TICK.register(ItemSearchController::onClientTick)
        LevelRenderEvents.BEFORE_GIZMOS.register {
            val accent = SkyHudTheme.PRIMARY and 0x00FFFFFF
            val style = GizmoStyle.strokeAndFill(0xFF000000.toInt() or accent, 2.0f, 0x30000000 or accent)
            IslandChestRepository.highlightedPositions().forEach { position ->
                Gizmos.cuboid(position, style).setAlwaysOnTop()
            }
        }
        ClientLifecycleEvents.CLIENT_STOPPING.register {
            SkyHudConfigManager.save()
            PlayerInventorySearchRepository.flush()
            IslandChestRepository.flush()
            SkyHudBackdrop.close()
        }
        logger.info("SkyHUD initialized")
    }
}
