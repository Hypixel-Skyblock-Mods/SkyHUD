package org.hypixelskyblockmods.skyhud.feature.equipment

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import net.minecraft.world.inventory.ChestMenu
import net.minecraft.world.inventory.ContainerInput
import org.hypixelskyblockmods.skyhud.config.SkyHudConfigManager
import org.hypixelskyblockmods.skyhud.feature.sets.SetCollectionScreen
import org.hypixelskyblockmods.skyhud.gui.OverlayTransitionGuard
import org.hypixelskyblockmods.skyhud.platform.ScreenCompat

object EquipmentController {
    private data class PendingAction(val page: Int, val index: Int?)

    private var activeScreen: SetCollectionScreen? = null
    private var currentTarget: EquipmentTarget? = null
    private var pendingAction: PendingAction? = null
    private var showOriginalNext = false
    private var originalMenu: ChestMenu? = null

    fun redirectIncoming(client: Minecraft, screen: Screen): Screen {
        if (screen === activeScreen) return screen
        return if (onScreenOpened(client, screen)) activeScreen ?: screen else screen
    }

    fun onScreenOpened(client: Minecraft, screen: Screen): Boolean {
        if (!SkyHudConfigManager.config.huds.equipment.enabled) return false
        val target = EquipmentDetector.detect(screen) ?: return false
        if (originalMenu === target.menu) return false
        if (showOriginalNext) {
            showOriginalNext = false
            originalMenu = target.menu
            activeScreen = null
            currentTarget = null
            return false
        }
        EquipmentRepository.sets.remember(target.page, target.menu)
        currentTarget = target

        val overlay = activeScreen ?: SetCollectionScreen(
            screenName = "SkyHUD Equipment",
            heading = "EQUIPMENT",
            searchHint = "Search equipment...",
            setLabel = "SET",
            repository = EquipmentRepository.sets,
            renderArmorMannequin = false,
            requestAction = ::requestAction,
            editOriginal = ::openOriginal,
            closed = ::onOverlayClosed,
        ).also {
            activeScreen = it
        }
        overlay.bind(target)

        if (ScreenCompat.currentScreen() === screen) {
            ScreenCompat.setScreen(overlay)
        }
        client.execute(::advancePendingAction)
        return true
    }

    fun onClientTick(client: Minecraft) {
        val current = ScreenCompat.currentScreen() ?: return
        if (originalMenu != null) {
            val target = EquipmentDetector.detect(current)
            if (target?.menu === originalMenu) return
            originalMenu = null
        }
        var overlay = activeScreen
        if (overlay == null || current !== overlay) {
            if (!onScreenOpened(client, current)) return
            overlay = activeScreen ?: return
            if (ScreenCompat.currentScreen() !== overlay) ScreenCompat.setScreen(overlay)
        }
        overlay.refreshBackingMenu(client.player?.containerMenu)
    }

    private fun onOverlayClosed() {
        OverlayTransitionGuard.clear(activeScreen)
        activeScreen = null
        currentTarget = null
        pendingAction = null
    }

    private fun requestAction(page: Int, index: Int?) {
        pendingAction = PendingAction(page, index)
        advancePendingAction()
    }

    private fun advancePendingAction() {
        val action = pendingAction ?: return
        val target = currentTarget ?: return
        val client = Minecraft.getInstance()
        val player = client.player ?: return
        if (player.containerMenu !== target.menu) return
        if (target.page == action.page) {
            pendingAction = null
            action.index?.let { index ->
                OverlayTransitionGuard.arm(activeScreen)
                client.gameMode?.handleContainerInput(target.menu.containerId, 36 + index, 0, ContainerInput.PICKUP, player)
            }
            return
        }
        val navigationSlot = if (action.page > target.page) 53 else 45
        OverlayTransitionGuard.arm(activeScreen)
        client.gameMode?.handleContainerInput(target.menu.containerId, navigationSlot, 0, ContainerInput.PICKUP, player)
    }

    private fun openOriginal() {
        showOriginalNext = true
        OverlayTransitionGuard.arm(activeScreen)
        Minecraft.getInstance().player?.connection?.sendCommand("eq")
    }
}
