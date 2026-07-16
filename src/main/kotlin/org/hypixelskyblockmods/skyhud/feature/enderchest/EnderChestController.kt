package org.hypixelskyblockmods.skyhud.feature.enderchest

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import net.minecraft.world.inventory.ChestMenu
import net.minecraft.world.item.ItemStack
import org.hypixelskyblockmods.skyhud.config.SkyHudConfigManager
import org.hypixelskyblockmods.skyhud.gui.OverlayTransitionGuard
import org.hypixelskyblockmods.skyhud.platform.ScreenCompat

object EnderChestController {
    private data class PendingSearchHighlight(
        val profileAccount: java.util.UUID?,
        val profileName: String?,
        val page: StoragePageKey,
        val itemIndex: Int,
        val expectedStack: ItemStack,
        val stale: Boolean,
    )

    private var activeScreen: EnderChestScreen? = null
    private var pendingOverviewReturn: StoragePageKey? = null
    private var overviewRequestInFlight = false
    private var showOriginalNext = false
    private var originalMenu: ChestMenu? = null
    private var pendingSearchHighlight: PendingSearchHighlight? = null

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
        if (target is EnderChestTarget.Page) resolveSearchHighlight(target, overlay)

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
        EnderChestRepository.onClientTick()
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

    fun onProfileChanged() {
        val client = Minecraft.getInstance()
        val overlay = activeScreen
        pendingOverviewReturn = null
        overviewRequestInFlight = false
        showOriginalNext = false
        originalMenu = null
        pendingSearchHighlight = null
        OverlayTransitionGuard.clear(overlay)
        activeScreen = null
        EnderChestRepository.resetSession()
        if (overlay != null) {
            client.player?.closeContainer()
            if (ScreenCompat.currentScreen() === overlay) ScreenCompat.setScreen(null)
        }
    }

    fun navigateToSearchResult(page: StoragePageKey, itemIndex: Int, expectedStack: ItemStack, stale: Boolean) {
        val identity = org.hypixelskyblockmods.skyhud.integration.skyblockapi.SkyblockApiStorageAdapter.currentProfile()
        pendingSearchHighlight = PendingSearchHighlight(
            identity?.accountUuid,
            identity?.profileName,
            page,
            itemIndex,
            expectedStack.copyWithCount(1),
            stale,
        )
        Minecraft.getInstance().player?.connection?.sendCommand(page.navigationCommand)
    }

    private fun resolveSearchHighlight(target: EnderChestTarget.Page, overlay: EnderChestScreen) {
        val pending = pendingSearchHighlight ?: return
        if (target.key != pending.page) return
        val identity = org.hypixelskyblockmods.skyhud.integration.skyblockapi.SkyblockApiStorageAdapter.currentProfile()
        if (identity?.accountUuid != pending.profileAccount || identity?.profileName != pending.profileName) {
            pendingSearchHighlight = null
            return
        }
        val liveItems = target.menu.items.take(target.menu.rowCount * 9).drop(9)
        val exact = liveItems.getOrNull(pending.itemIndex)
        val resolvedIndex = when {
            exact != null && stacksMatch(exact, pending.expectedStack) -> pending.itemIndex
            pending.stale -> liveItems.indices.filter { stacksMatch(liveItems[it], pending.expectedStack) }.singleOrNull()
            else -> null
        }
        pendingSearchHighlight = null
        if (resolvedIndex != null) {
            overlay.highlightItem(target.key, resolvedIndex)
        } else {
            Minecraft.getInstance().player?.sendSystemMessage(
                net.minecraft.network.chat.Component.literal("[SkyHUD] That item moved since this page was cached."),
            )
        }
    }

    private fun stacksMatch(first: ItemStack, second: ItemStack): Boolean =
        !first.isEmpty && ItemStack.matches(first.copyWithCount(1), second.copyWithCount(1))

    private fun onOverlayClosed() {
        EnderChestRepository.flush()
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
