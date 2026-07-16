package org.hypixelskyblockmods.skyhud.feature.wardrobe

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import net.minecraft.world.inventory.ChestMenu
import net.minecraft.world.inventory.ContainerInput
import org.hypixelskyblockmods.skyhud.config.SkyHudConfigManager
import org.hypixelskyblockmods.skyhud.feature.sets.SetCollectionScreen
import org.hypixelskyblockmods.skyhud.gui.OverlayMenuTransition
import org.hypixelskyblockmods.skyhud.gui.OverlayTransitionGuard
import org.hypixelskyblockmods.skyhud.platform.ScreenCompat

object WardrobeController {
    private data class PendingAction(val page: Int, val index: Int?)
    private data class PendingSearchHighlight(val page: Int, val setId: Int)

    private var activeScreen: SetCollectionScreen? = null
    private var currentTarget: WardrobeTarget? = null
    private var pendingAction: PendingAction? = null
    private var pendingSearchHighlight: PendingSearchHighlight? = null
    private var showOriginalNext = false
    private var originalMenu: ChestMenu? = null
    private var deferredScreen: Screen? = null
    private val transition = OverlayMenuTransition("wardrobe")

    fun redirectIncoming(client: Minecraft, screen: Screen): Screen {
        if (screen === activeScreen) return screen
        if (onScreenOpened(client, screen)) return activeScreen ?: screen
        val overlay = activeScreen
        if (overlay != null && transition.retainIncoming(screen, overlay)) {
            deferredScreen = screen
            return overlay
        }
        return screen
    }

    fun onScreenOpened(client: Minecraft, screen: Screen): Boolean {
        if (!SkyHudConfigManager.config.huds.wardrobe.enabled) return false
        val target = WardrobeDetector.detect(screen) ?: return false
        if (deferredScreen === screen) deferredScreen = null
        if (originalMenu === target.menu) return false
        if (showOriginalNext) {
            showOriginalNext = false
            originalMenu = target.menu
            activeScreen = null
            currentTarget = null
            return false
        }
        if (transition.isRefreshing() && currentTarget?.menu === target.menu) return activeScreen != null
        transition.onRecognized()
        WardrobeRepository.sets.remember(target.page, target.menu)
        currentTarget = target

        val overlay = activeScreen ?: SetCollectionScreen(
            screenName = "SkyHUD Wardrobe",
            heading = "WARDROBE",
            searchHint = "Search armor...",
            setLabel = "OUTFIT",
            repository = WardrobeRepository.sets,
            renderArmorMannequin = true,
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
        client.execute {
            advancePendingSearchHighlight()
            advancePendingAction()
        }
        return true
    }

    fun onClientTick(client: Minecraft) {
        WardrobeRepository.sets.onClientTick()
        val current = ScreenCompat.currentScreen() ?: return
        if (originalMenu != null) {
            val target = WardrobeDetector.detect(current)
            if (target?.menu === originalMenu) return
            originalMenu = null
        }
        var overlay = activeScreen
        if (overlay == null || current !== overlay) {
            if (!onScreenOpened(client, current)) return
            overlay = activeScreen ?: return
            if (ScreenCompat.currentScreen() !== overlay) ScreenCompat.setScreen(overlay)
        }
        deferredScreen?.let { deferred ->
            if (onScreenOpened(client, deferred)) deferredScreen = null
        }
        if (client.player?.containerMenu !== currentTarget?.menu) transition.scheduleRefresh()
        transition.tick(client, overlay)
        if (transition.acceptsBackingUpdates()) overlay.refreshBackingMenu(client.player?.containerMenu)
    }

    fun onProfileChanged() {
        val client = Minecraft.getInstance()
        val overlay = activeScreen
        val closeContainer = overlay != null || currentTarget != null || originalMenu != null
        transition.clear(overlay)
        activeScreen = null
        currentTarget = null
        pendingAction = null
        pendingSearchHighlight = null
        showOriginalNext = false
        originalMenu = null
        deferredScreen = null
        WardrobeRepository.sets.resetSession()
        if (closeContainer) client.player?.closeContainer()
        if (overlay != null && ScreenCompat.currentScreen() === overlay) ScreenCompat.setScreen(null)
    }

    private fun onOverlayClosed() {
        WardrobeRepository.sets.flush()
        transition.clear(activeScreen)
        activeScreen = null
        currentTarget = null
        pendingAction = null
        pendingSearchHighlight = null
        deferredScreen = null
    }

    private fun requestAction(page: Int, index: Int?) {
        pendingAction = PendingAction(page, index)
        if (!advancePendingAction()) transition.scheduleRefresh()
    }

    fun navigateToSearchResult(page: Int, setId: Int) {
        pendingSearchHighlight = PendingSearchHighlight(page, setId)
        Minecraft.getInstance().player?.connection?.sendCommand("wardrobe")
    }

    private fun advancePendingSearchHighlight(): Boolean {
        val pending = pendingSearchHighlight ?: return false
        val target = currentTarget ?: return false
        val client = Minecraft.getInstance()
        val player = client.player ?: return false
        if (player.containerMenu !== target.menu) return false
        if (target.page == pending.page) {
            activeScreen?.highlightCard(pending.setId)
            pendingSearchHighlight = null
            return true
        }
        val navigationSlot = if (pending.page > target.page) 53 else 45
        transition.arm(activeScreen)
        client.gameMode?.handleContainerInput(target.menu.containerId, navigationSlot, 0, ContainerInput.PICKUP, player)
        return true
    }

    private fun advancePendingAction(): Boolean {
        val action = pendingAction ?: return false
        val target = currentTarget ?: return false
        val client = Minecraft.getInstance()
        val player = client.player ?: return false
        if (player.containerMenu !== target.menu) return false
        if (target.page == action.page) {
            pendingAction = null
            action.index?.let { index ->
                WardrobeRepository.sets.markSelected(action.page, index)
                transition.scheduleRefresh()
                transition.arm(activeScreen)
                client.gameMode?.handleContainerInput(target.menu.containerId, 36 + index, 0, ContainerInput.PICKUP, player)
            }
            return true
        }
        val navigationSlot = if (action.page > target.page) 53 else 45
        transition.arm(activeScreen)
        client.gameMode?.handleContainerInput(target.menu.containerId, navigationSlot, 0, ContainerInput.PICKUP, player)
        return true
    }

    private fun openOriginal() {
        showOriginalNext = true
        deferredScreen = null
        transition.onRecognized()
        OverlayTransitionGuard.arm(activeScreen)
        Minecraft.getInstance().player?.connection?.sendCommand("wardrobe")
    }
}
