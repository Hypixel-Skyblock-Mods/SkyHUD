package org.hypixelskyblockmods.skyhud.feature.enderchest

import kotlin.math.ceil
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.ChestMenu
import net.minecraft.world.inventory.ContainerInput
import net.minecraft.world.item.ItemStack
import org.hypixelskyblockmods.skyhud.gui.SkyHudTheme

class EnderChestScreen(
    private val closed: () -> Unit,
) : Screen(Component.literal("SkyHUD Storage")) {
    private data class PageBounds(
        val key: StoragePageKey,
        val x: Int,
        val y: Int,
        val width: Int,
        val height: Int,
        val loaded: Boolean,
    )

    private data class InventorySlotBounds(
        val menuSlot: Int,
        val x: Int,
        val y: Int,
    )

    private var currentPage: StoragePageKey? = null
    private var backingMenu: ChestMenu? = null
    private var searchText = ""
    private var scroll = 0.0
    private var maxScroll = 0.0
    private var pageBounds = emptyList<PageBounds>()
    private var inventorySlotBounds = emptyList<InventorySlotBounds>()
    private var draggingScrollbar = false

    private val panelMaxWidth = 590
    private val panelMaxHeight = 430
    private val headerHeight = 42
    private val inventoryHeight = 104
    private val pageColumns = 3
    private val pageWidth = 162
    private val pageGapHorizontal = 20
    private val pageGapVertical = 18
    private val pageTitleHeight = 15
    private val slotSize = 18
    private val slotPitch = 18

    fun bind(target: EnderChestTarget) {
        backingMenu = target.menu
        currentPage = when (target) {
            is EnderChestTarget.Overview -> {
                EnderChestRepository.rememberOverview(target.menu)
                null
            }

            is EnderChestTarget.Page -> {
                val total = target.totalEnderChestPages
                if (total != null) {
                    EnderChestRepository.rememberEnderChest(target.key.number, total, target.menu)
                } else {
                    EnderChestRepository.remember(target.key, target.menu)
                }
                target.key
            }
        }
        scroll = 0.0
    }

    fun refreshBackingMenu(menu: AbstractContainerMenu?) {
        val chestMenu = menu as? ChestMenu ?: return
        if (chestMenu !== backingMenu) return
        val key = currentPage
        if (key == null) {
            EnderChestRepository.rememberOverview(chestMenu)
        } else {
            EnderChestRepository.remember(key, chestMenu)
        }
    }

    override fun init() {
        super.init()
        val panelX = panelX()
        val panelY = panelY()
        val searchWidth = 154
        val search = EditBox(
            font,
            panelX + panelWidth() - searchWidth - 15,
            panelY + 11,
            searchWidth,
            20,
            Component.literal("Search Storage"),
        )
        search.value = searchText
        search.setHint(Component.literal("Search items..."))
        search.setBordered(false)
        search.setTextColor(SkyHudTheme.TEXT)
        search.setTextColorUneditable(SkyHudTheme.TEXT_MUTED)
        search.setResponder {
            searchText = it
            scroll = 0.0
        }
        addRenderableWidget(search)
    }

    override fun extractRenderState(
        graphics: GuiGraphicsExtractor,
        mouseX: Int,
        mouseY: Int,
        delta: Float,
    ) {
        val panelX = panelX()
        val panelY = panelY()
        val panelWidth = panelWidth()
        val panelHeight = panelHeight()
        val panelBottom = panelY + panelHeight
        val inventoryTop = panelBottom - inventoryHeight

        graphics.fill(0, 0, width, height, 0x70000000)
        SkyHudTheme.outlinedRoundedRect(
            graphics,
            panelX,
            panelY,
            panelWidth,
            panelHeight,
            0xF20D0D0D.toInt(),
            SkyHudTheme.PRIMARY,
        )
        graphics.fill(panelX + 1, panelY + headerHeight, panelX + panelWidth - 1, panelY + headerHeight + 1, SkyHudTheme.PRIMARY)
        graphics.text(font, "STORAGE", panelX + 15, panelY + 17, SkyHudTheme.TEXT, false)

        val searchWidth = 154
        SkyHudTheme.outlinedRoundedRect(
            graphics,
            panelX + panelWidth - searchWidth - 20,
            panelY + 7,
            searchWidth + 10,
            28,
            SkyHudTheme.SURFACE,
            SkyHudTheme.BORDER,
        )

        drawPages(graphics, mouseX, mouseY, panelX, panelY, panelWidth, inventoryTop)
        drawInventory(graphics, mouseX, mouseY, panelX, panelWidth, inventoryTop)
        super.extractRenderState(graphics, mouseX, mouseY, delta)

        val carried = backingMenu?.carried
        if (carried != null && !carried.isEmpty) {
            graphics.item(carried, mouseX - 8, mouseY - 8)
            graphics.itemDecorations(font, carried, mouseX - 8, mouseY - 8)
        }
    }

    private fun drawPages(
        graphics: GuiGraphicsExtractor,
        mouseX: Int,
        mouseY: Int,
        panelX: Int,
        panelY: Int,
        panelWidth: Int,
        inventoryTop: Int,
    ) {
        val viewportTop = panelY + headerHeight + 10
        val viewportBottom = inventoryTop - 10
        val viewportHeight = (viewportBottom - viewportTop).coerceAtLeast(1)
        val pages = EnderChestRepository.allPages().filter(::pageMatchesSearch)
        val visibleRows = visibleRowCount(pages)
        val pageHeight = pageTitleHeight + visibleRows * slotPitch
        val rows = ceil(pages.size / pageColumns.toDouble()).toInt()
        val contentHeight = rows * pageHeight + (rows - 1).coerceAtLeast(0) * pageGapVertical
        maxScroll = (contentHeight - viewportHeight).coerceAtLeast(0).toDouble()
        scroll = scroll.coerceIn(0.0, maxScroll)

        val contentWidth = pageColumns * pageWidth + (pageColumns - 1) * pageGapHorizontal
        val startX = panelX + (panelWidth - contentWidth) / 2
        graphics.enableScissor(panelX + 2, viewportTop, panelX + panelWidth - 2, viewportBottom)
        val bounds = ArrayList<PageBounds>(pages.size)
        pages.forEachIndexed { index, key ->
            val x = startX + (index % pageColumns) * (pageWidth + pageGapHorizontal)
            val y = viewportTop + (index / pageColumns) * (pageHeight + pageGapVertical) - scroll.toInt()
            val cached = EnderChestRepository.page(key)
            drawPage(graphics, key, cached, x, y, visibleRows, mouseX, mouseY)
            bounds += PageBounds(key, x, y, pageWidth, pageHeight, cached != null)
        }
        graphics.disableScissor()
        pageBounds = bounds

        if (pages.isEmpty()) {
            val message = if (searchText.isBlank()) {
                "Open Storage to discover your pages"
            } else {
                "No storage items match '$searchText'"
            }
            graphics.text(
                font,
                message,
                panelX + (panelWidth - font.width(message)) / 2,
                viewportTop + 24,
                SkyHudTheme.TEXT_MUTED,
                false,
            )
        }

        drawScrollbar(graphics, mouseX, mouseY, panelX + panelWidth - 9, viewportTop, viewportBottom)
    }

    private fun drawPage(
        graphics: GuiGraphicsExtractor,
        key: StoragePageKey,
        cached: CachedEnderChestPage?,
        x: Int,
        y: Int,
        visibleRows: Int,
        mouseX: Int,
        mouseY: Int,
    ) {
        val gridY = y + pageTitleHeight
        val gridHeight = visibleRows * slotPitch
        val active = key == currentPage
        val hovered = mouseX in x until (x + pageWidth) && mouseY in y until (gridY + gridHeight)

        if (active) {
            drawOutline(graphics, x - 3, gridY - 3, pageWidth + 6, gridHeight + 6, SkyHudTheme.PRIMARY_HOVER, 2)
        }
        graphics.text(font, key.displayName, x, y + 2, SkyHudTheme.TEXT, false)

        repeat(visibleRows * 9) { index ->
            val stack = cached?.items?.getOrNull(index) ?: ItemStack.EMPTY
            val slotX = x + (index % 9) * slotPitch
            val slotY = gridY + (index / 9) * slotPitch
            val slotHovered = mouseX in slotX until (slotX + slotSize) && mouseY in slotY until (slotY + slotSize)
            graphics.fill(
                slotX,
                slotY,
                slotX + slotSize - 1,
                slotY + slotSize - 1,
                when {
                    slotHovered -> SkyHudTheme.SLOT_HOVER
                    stack.isEmpty -> SkyHudTheme.SLOT
                    else -> SkyHudTheme.SLOT_FILLED
                },
            )
            if (!stack.isEmpty) {
                graphics.item(stack, slotX + 1, slotY + 1)
                graphics.itemDecorations(font, stack, slotX + 1, slotY + 1)
                if (searchText.isNotBlank() && itemMatches(stack)) {
                    drawOutline(graphics, slotX, slotY, slotSize - 1, slotSize - 1, SkyHudTheme.PRIMARY_HOVER, 1)
                }
                if (slotHovered) graphics.setTooltipForNextFrame(font, stack, mouseX, mouseY)
            }
        }

        if (cached == null) {
            val label = "OPEN THIS PAGE"
            val labelWidth = font.width(label) + 16
            val buttonX = x + (pageWidth - labelWidth) / 2
            val buttonY = gridY + (gridHeight - 22) / 2
            SkyHudTheme.outlinedRoundedRect(
                graphics,
                buttonX,
                buttonY,
                labelWidth,
                22,
                if (hovered) SkyHudTheme.PRIMARY_HOVER else SkyHudTheme.PRIMARY,
                SkyHudTheme.PRIMARY,
            )
            graphics.text(font, label, buttonX + 8, buttonY + 7, SkyHudTheme.TEXT, false)
        }
    }

    private fun drawInventory(
        graphics: GuiGraphicsExtractor,
        mouseX: Int,
        mouseY: Int,
        panelX: Int,
        panelWidth: Int,
        inventoryTop: Int,
    ) {
        graphics.fill(panelX + 1, inventoryTop, panelX + panelWidth - 1, inventoryTop + 1, SkyHudTheme.BORDER)
        graphics.text(font, "INVENTORY", panelX + 15, inventoryTop + 10, SkyHudTheme.TEXT_MUTED, false)

        val menu = backingMenu ?: return
        val inventoryWidth = 9 * slotPitch
        val startX = panelX + (panelWidth - inventoryWidth) / 2
        val mainY = inventoryTop + 24
        val playerStart = menu.rowCount * 9
        val bounds = ArrayList<InventorySlotBounds>(36)

        repeat(27) { index ->
            val menuSlot = playerStart + index
            val x = startX + (index % 9) * slotPitch
            val y = mainY + (index / 9) * slotPitch
            drawInventorySlot(graphics, menu, menuSlot, x, y, mouseX, mouseY)
            bounds += InventorySlotBounds(menuSlot, x, y)
        }
        repeat(9) { index ->
            val menuSlot = playerStart + 27 + index
            val x = startX + index * slotPitch
            val y = mainY + 3 * slotPitch + 5
            drawInventorySlot(graphics, menu, menuSlot, x, y, mouseX, mouseY)
            bounds += InventorySlotBounds(menuSlot, x, y)
        }
        inventorySlotBounds = bounds
    }

    private fun drawInventorySlot(
        graphics: GuiGraphicsExtractor,
        menu: ChestMenu,
        menuSlot: Int,
        x: Int,
        y: Int,
        mouseX: Int,
        mouseY: Int,
    ) {
        val stack = menu.getSlot(menuSlot).item
        val hovered = mouseX in x until (x + slotSize) && mouseY in y until (y + slotSize)
        graphics.fill(
            x,
            y,
            x + slotSize - 1,
            y + slotSize - 1,
            when {
                hovered -> SkyHudTheme.SLOT_HOVER
                stack.isEmpty -> SkyHudTheme.SLOT
                else -> SkyHudTheme.SLOT_FILLED
            },
        )
        if (!stack.isEmpty) {
            graphics.item(stack, x + 1, y + 1)
            graphics.itemDecorations(font, stack, x + 1, y + 1)
            if (hovered) graphics.setTooltipForNextFrame(font, stack, mouseX, mouseY)
        }
    }

    private fun drawScrollbar(
        graphics: GuiGraphicsExtractor,
        mouseX: Int,
        mouseY: Int,
        x: Int,
        top: Int,
        bottom: Int,
    ) {
        if (maxScroll <= 0.0) return
        val trackHeight = bottom - top
        val thumbHeight = (trackHeight * (trackHeight / (trackHeight + maxScroll))).toInt().coerceAtLeast(24)
        val thumbTravel = trackHeight - thumbHeight
        val thumbY = top + ((scroll / maxScroll) * thumbTravel).toInt()
        SkyHudTheme.roundedRect(graphics, x, top, 3, trackHeight, 0xFF202020.toInt())
        SkyHudTheme.roundedRect(
            graphics,
            x,
            thumbY,
            3,
            thumbHeight,
            if (mouseX in (x - 3)..(x + 6) && mouseY in thumbY..(thumbY + thumbHeight)) {
                SkyHudTheme.PRIMARY_HOVER
            } else {
                SkyHudTheme.PRIMARY
            },
        )
    }

    override fun mouseScrolled(
        mouseX: Double,
        mouseY: Double,
        horizontalAmount: Double,
        verticalAmount: Double,
    ): Boolean {
        if (maxScroll > 0.0) {
            scroll = (scroll - verticalAmount * 30.0).coerceIn(0.0, maxScroll)
            return true
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)
    }

    override fun mouseClicked(click: MouseButtonEvent, doubled: Boolean): Boolean {
        if (super.mouseClicked(click, doubled)) return true
        if (click.button() !in 0..1) return false
        val mouseX = click.x.toInt()
        val mouseY = click.y.toInt()

        val scrollbarX = panelX() + panelWidth() - 9
        val scrollbarTop = panelY() + headerHeight + 10
        val scrollbarBottom = panelY() + panelHeight() - inventoryHeight - 10
        if (click.button() == 0 && mouseX in (scrollbarX - 4)..(scrollbarX + 7) && mouseY in scrollbarTop..scrollbarBottom) {
            draggingScrollbar = true
            updateScrollFromMouse(mouseY, scrollbarTop, scrollbarBottom)
            return true
        }

        inventorySlotBounds.firstOrNull {
            mouseX in it.x until (it.x + slotSize) && mouseY in it.y until (it.y + slotSize)
        }?.let {
            clickBackingSlot(it.menuSlot, click.button(), click.hasShiftDown())
            return true
        }

        val card = pageBounds.firstOrNull {
            mouseX in it.x until (it.x + it.width) && mouseY in it.y until (it.y + it.height)
        } ?: return false
        if (!card.loaded || card.key != currentPage) {
            navigateToPage(card.key)
            return true
        }

        val itemX = mouseX - card.x
        val itemY = mouseY - (card.y + pageTitleHeight)
        val column = itemX / slotPitch
        val row = itemY / slotPitch
        val actualRows = EnderChestRepository.page(card.key)?.rows ?: 0
        if (itemX >= 0 && itemY >= 0 && column in 0..8 && row in 0 until actualRows) {
            clickBackingSlot(9 + row * 9 + column, click.button(), click.hasShiftDown())
        }
        return true
    }

    override fun mouseDragged(click: MouseButtonEvent, dragX: Double, dragY: Double): Boolean {
        if (draggingScrollbar) {
            val top = panelY() + headerHeight + 10
            val bottom = panelY() + panelHeight() - inventoryHeight - 10
            updateScrollFromMouse(click.y.toInt(), top, bottom)
            return true
        }
        return super.mouseDragged(click, dragX, dragY)
    }

    override fun mouseReleased(click: MouseButtonEvent): Boolean {
        if (draggingScrollbar) {
            draggingScrollbar = false
            return true
        }
        return super.mouseReleased(click)
    }

    private fun updateScrollFromMouse(mouseY: Int, top: Int, bottom: Int) {
        val percentage = ((mouseY - top).toDouble() / (bottom - top)).coerceIn(0.0, 1.0)
        scroll = percentage * maxScroll
    }

    private fun navigateToPage(key: StoragePageKey) {
        minecraft.player?.connection?.sendCommand(key.navigationCommand)
    }

    private fun clickBackingSlot(slot: Int, button: Int, quickMove: Boolean) {
        val menu = backingMenu ?: return
        val player = minecraft.player ?: return
        if (player.containerMenu !== menu || slot !in menu.slots.indices) return
        minecraft.gameMode?.handleContainerInput(
            menu.containerId,
            slot,
            button,
            if (quickMove) ContainerInput.QUICK_MOVE else ContainerInput.PICKUP,
            player,
        )
    }

    private fun visibleRowCount(pages: List<StoragePageKey>): Int =
        (pages.mapNotNull { EnderChestRepository.page(it)?.rows }.maxOrNull()
            ?: 4).coerceIn(1, 5)

    private fun pageMatchesSearch(key: StoragePageKey): Boolean {
        if (searchText.isBlank()) return true
        val cached = EnderChestRepository.page(key) ?: return true
        return cached.items.any(::itemMatches)
    }

    private fun itemMatches(stack: ItemStack): Boolean {
        if (searchText.isBlank()) return true
        if (stack.isEmpty) return false
        return searchText.trim().split(Regex("\\s+")).all {
            stack.hoverName.string.contains(it, ignoreCase = true)
        }
    }

    private fun drawOutline(
        graphics: GuiGraphicsExtractor,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        color: Int,
        thickness: Int,
    ) {
        graphics.fill(x, y, x + width, y + thickness, color)
        graphics.fill(x, y + height - thickness, x + width, y + height, color)
        graphics.fill(x, y, x + thickness, y + height, color)
        graphics.fill(x + width - thickness, y, x + width, y + height, color)
    }

    private fun panelWidth(): Int = (width - 20).coerceAtMost(panelMaxWidth).coerceAtLeast(1)

    private fun panelHeight(): Int = (height - 20).coerceAtMost(panelMaxHeight).coerceAtLeast(1)

    private fun panelX(): Int = (width - panelWidth()) / 2

    private fun panelY(): Int = (height - panelHeight()) / 2

    override fun onClose() {
        closed()
        backingMenu = null
        minecraft.player?.closeContainer()
        super.onClose()
    }

    override fun isPauseScreen(): Boolean = false
}
