package org.hypixelskyblockmods.skyhud.feature.enderchest

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import net.minecraft.world.inventory.ChestMenu
import org.hypixelskyblockmods.skyhud.config.SkyHudConfigManager
import org.hypixelskyblockmods.skyhud.gui.OverlayTransitionGuard
import org.hypixelskyblockmods.skyhud.platform.ScreenCompat

object EnderChestController {
    private var activeScreen: EnderChestScreen? = null
    private var pendingOverviewReturn: StoragePageKey? = null
    private var overviewRequestInFlight = false
    private var showOriginalNext = false
    private var originalMenu: ChestMenu? = null

    fun redirectIncoming(client: Minecraft, screen: Screen): Screen {
        if (screen === activeScreen) return screen
        return if (onScreenOpened(client, screen)) activeScreen ?: screen else screen
    }

    fun onScreenOpened(client: Minecraft, screen: Screen): Boolean {
        if (!SkyHudConfigManager.config.huds.enderChest.enabled) return false
        val target = EnderChestDetector.detect(screen) ?: return false
        if (originalMenu === target.menu) return false
        if (showOriginalNext) {
            showOriginalNext = false
            originalMenu = target.menu
            if (target is EnderChestTarget.Overview) EnderChestRepository.rememberOverview(target.menu)
            activeScreen = null
            EnderChestRepository.clearLiveBacking()
            return false
        }
        var commandAfterReplacement: String? = null
        when (target) {
            is EnderChestTarget.Overview -> {
                EnderChestRepository.rememberOverview(target.menu)
                commandAfterReplacement = pendingOverviewReturn?.navigationCommand
                pendingOverviewReturn = null
                overviewRequestInFlight = false
            }
            is EnderChestTarget.Page -> {
                val total = target.totalEnderChestPages
                if (total != null) {
                    EnderChestRepository.rememberEnderChest(target.key.number, total, target.menu)
                } else {
                    EnderChestRepository.remember(target.key, target.menu)
                }
                if (!EnderChestRepository.hasDiscoveredOverview && !overviewRequestInFlight) {
                    pendingOverviewReturn = target.key
                    overviewRequestInFlight = true
                    commandAfterReplacement = "storage"
                }
            }
        }

        val overlay = activeScreen ?: EnderChestScreen(::openOriginal, ::beginMenuTransition, ::onOverlayClosed).also {
            activeScreen = it
        }
        overlay.bind(target)

        if (ScreenCompat.currentScreen() === screen) {
            ScreenCompat.setScreen(overlay)
        }
        commandAfterReplacement?.let { command ->
            client.execute {
                beginMenuTransition()
                client.player?.connection?.sendCommand(command)
            }
        }
        return true
    }

    fun onClientTick(client: Minecraft) {
        val current = ScreenCompat.currentScreen() ?: return
        if (originalMenu != null) {
            val target = EnderChestDetector.detect(current)
            if (target?.menu === originalMenu) return
            originalMenu = null
        }
        var overlay = activeScreen
        if (overlay == null || current !== overlay) {
            if (!onScreenOpened(client, current)) return
            overlay = activeScreen ?: return
            if (ScreenCompat.currentScreen() !== overlay) ScreenCompat.setScreen(overlay)
        }
        EnderChestRepository.refreshApiSnapshot()
        overlay.refreshBackingMenu(client.player?.containerMenu)
    }

    private fun onOverlayClosed() {
        OverlayTransitionGuard.clear(activeScreen)
        activeScreen = null
        EnderChestRepository.clearLiveBacking()
    }

    private fun beginMenuTransition() {
        OverlayTransitionGuard.arm(activeScreen)
    }

    private fun openOriginal() {
        pendingOverviewReturn = null
        overviewRequestInFlight = false
        showOriginalNext = true
        beginMenuTransition()
        Minecraft.getInstance().player?.connection?.sendCommand("storage")
    }
}
