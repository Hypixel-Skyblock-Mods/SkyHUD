package org.hypixelskyblockmods.skyhud.feature.enderchest

import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.ChestMenu
import net.minecraft.world.inventory.ContainerInput
import net.minecraft.world.item.ItemStack
import org.hypixelskyblockmods.skyhud.config.SkyHudConfigManager
import org.hypixelskyblockmods.skyhud.gui.SkyHudBackdrop
import org.hypixelskyblockmods.skyhud.gui.SkyHudControls
import org.hypixelskyblockmods.skyhud.gui.SkyHudTheme

class EnderChestScreen(
    private val editOriginal: () -> Unit,
    private val beginMenuTransition: () -> Unit,
    private val closed: () -> Unit,
) : Screen(Component.literal("SkyHUD Storage")) {
    private data class PageBounds(
        val key: StoragePageKey,
        val x: Int,
        val y: Int,
        val width: Int,
        val height: Int,
        val loaded: Boolean,
        val starX: Int,
        val starY: Int,
        val starSize: Int,
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
    private var highlightedPage: StoragePageKey? = null
    private var highlightedItemIndex: Int? = null
    private var highlightUntilEpochMillis = 0L

    private val panelMaxWidth = 574
    private val panelMaxHeight = 430
    private val headerHeight = 24
    private val inventoryHeight = 132
    private val pageColumns = 3
    private val pageWidth = 180
    private val pageGapHorizontal = 6
    private val pageGapVertical = 8
    private val pageTitleHeight = 13
    private val slotSize = 20
    private val slotPitch = 20
    private val inventorySlotSize = 23
    private val inventorySlotPitch = 24
    private val toolkitButtonSize = 18
    private val toolkitButtonGap = 3
    private val contentEdgeGap = 3
    private val inventorySidePadding = 6

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

    fun highlightItem(page: StoragePageKey, itemIndex: Int) {
        highlightedPage = page
        highlightedItemIndex = itemIndex
        highlightUntilEpochMillis = System.currentTimeMillis() + 10_000L
        searchText = ""
    }

    override fun init() {
        super.init()
        val panelX = panelX()
        val panelY = panelY()
        val searchWidth = 140
        val search = EditBox(
            font,
            searchX(panelX, panelWidth(), searchWidth),
            panelY + 7,
            searchWidth,
            12,
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

    override fun extractBackground(
        graphics: GuiGraphicsExtractor,
        mouseX: Int,
        mouseY: Int,
        delta: Float,
    ) {
        val panelY = panelY()
        val inventoryTop = inventoryTop()
        SkyHudBackdrop.renderPanelBlur(
            graphics,
            SkyHudBackdrop.Region(panelX(), panelY, panelWidth(), inventoryTop - panelY + 1),
            SkyHudBackdrop.Region(inventoryPanelX(), inventoryTop, inventoryPanelWidth(), inventoryHeight),
        )
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
        val inventoryTop = inventoryTop()
        val storagePanelHeight = inventoryTop - panelY + 1
        val inventoryPanelX = inventoryPanelX()
        val inventoryPanelWidth = inventoryPanelWidth()

        graphics.fill(0, 0, width, height, SkyHudTheme.SCREEN_DIM)
        SkyHudTheme.outlinedRoundedRect(
            graphics,
            panelX,
            panelY,
            panelWidth,
            storagePanelHeight,
            SkyHudTheme.PANEL,
            SkyHudTheme.PRIMARY,
        )
        graphics.fill(panelX + 1, panelY + headerHeight, panelX + panelWidth - 1, panelY + headerHeight + 1, SkyHudTheme.DIVIDER)
        val titleX = panelX + 8
        val editX = headerEditX(panelX, "STORAGE")
        val editHovered = mouseX in editX until (editX + 33) && mouseY in (panelY + 4) until (panelY + 20)
        graphics.text(font, "STORAGE", titleX, panelY + 8, SkyHudTheme.TEXT, false)
        SkyHudTheme.outlinedRoundedRect(
            graphics,
            editX,
            panelY + 4,
            33,
            16,
            if (editHovered) SkyHudTheme.CONTROL_HOVER else SkyHudTheme.CONTROL,
            SkyHudTheme.PRIMARY,
        )
        SkyHudControls.centeredText(graphics, font, "EDIT", editX, panelY + 4, 33, 16, SkyHudTheme.TEXT)
        SkyHudControls.settingsButton(graphics, mouseX, mouseY, headerConfigX(panelX, "STORAGE"), panelY + 4)

        val searchWidth = 140
        SkyHudTheme.outlinedRoundedRect(
            graphics,
            searchX(panelX, panelWidth, searchWidth) - 4,
            panelY + 3,
            searchWidth + 8,
            18,
            SkyHudTheme.SURFACE,
            SkyHudTheme.BORDER,
        )
        drawToolkitButtons(graphics, mouseX, mouseY, panelX, panelY, panelWidth, searchWidth)

        SkyHudTheme.outlinedRoundedRect(
            graphics,
            inventoryPanelX,
            inventoryTop,
            inventoryPanelWidth,
            inventoryHeight,
            SkyHudTheme.PANEL,
            SkyHudTheme.PRIMARY,
        )

        drawPages(graphics, mouseX, mouseY, panelX, panelY, panelWidth, inventoryTop)
        drawInventory(graphics, mouseX, mouseY, inventoryPanelX, inventoryTop)
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
        val viewportTop = panelY + headerHeight + contentEdgeGap
        val viewportBottom = inventoryTop - contentEdgeGap
        val viewportHeight = (viewportBottom - viewportTop).coerceAtLeast(1)
        val pages = EnderChestRepository.allPages().filter(::pageMatchesSearch)
        val pageRows = pages.chunked(pageColumns)
        val rowHeights = pageRows.map { row ->
            row.maxOfOrNull { pageHeight(pageRowCount(it)) } ?: 0
        }
        val contentHeight = rowHeights.sum() + (rowHeights.size - 1).coerceAtLeast(0) * pageGapVertical
        maxScroll = (contentHeight - viewportHeight).coerceAtLeast(0).toDouble()
        scroll = scroll.coerceIn(0.0, maxScroll)
        if (System.currentTimeMillis() > highlightUntilEpochMillis) {
            highlightedPage = null
            highlightedItemIndex = null
        } else {
            val highlightedRow = pages.indexOf(highlightedPage).takeIf { it >= 0 }?.div(pageColumns)
            if (highlightedRow != null) {
                val rowTop = rowHeights.take(highlightedRow).sum() + highlightedRow * pageGapVertical
                val rowBottom = rowTop + rowHeights[highlightedRow]
                scroll = when {
                    rowTop < scroll -> rowTop.toDouble()
                    rowBottom > scroll + viewportHeight -> (rowBottom - viewportHeight).toDouble()
                    else -> scroll
                }.coerceIn(0.0, maxScroll)
            }
        }

        val contentWidth = pageColumns * pageWidth + (pageColumns - 1) * pageGapHorizontal
        val startX = panelX + (panelWidth - contentWidth) / 2
        graphics.enableScissor(panelX + 2, viewportTop, panelX + panelWidth - 2, viewportBottom)
        val bounds = ArrayList<PageBounds>(pages.size)
        var rowY = viewportTop - scroll.toInt()
        pageRows.forEachIndexed { rowIndex, row ->
            row.forEachIndexed { column, key ->
                val x = startX + column * (pageWidth + pageGapHorizontal)
                val pageRowsForKey = pageRowCount(key)
                val height = pageHeight(pageRowsForKey)
                val cached = EnderChestRepository.page(key)
                drawPage(graphics, key, cached, x, rowY, pageRowsForKey, mouseX, mouseY)
                val starSize = 11
                bounds += PageBounds(
                    key,
                    x,
                    rowY,
                    pageWidth,
                    height,
                    cached != null,
                    x + pageWidth - starSize,
                    rowY,
                    starSize,
                )
            }
            rowY += rowHeights[rowIndex] + pageGapVertical
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

    private fun drawToolkitButtons(
        graphics: GuiGraphicsExtractor,
        mouseX: Int,
        mouseY: Int,
        panelX: Int,
        panelY: Int,
        panelWidth: Int,
        searchWidth: Int,
    ) {
        val farmingX = farmingToolkitX(panelX, panelWidth, searchWidth)
        val huntingX = farmingX - toolkitButtonSize - toolkitButtonGap
        drawToolkitButton(graphics, ToolkitIcons.hunting, "Hunting Toolkit", huntingX, panelY + 3, mouseX, mouseY)
        drawToolkitButton(graphics, ToolkitIcons.farming, "Farming Toolkit", farmingX, panelY + 3, mouseX, mouseY)
    }

    private fun drawToolkitButton(
        graphics: GuiGraphicsExtractor,
        icon: ItemStack,
        label: String,
        x: Int,
        y: Int,
        mouseX: Int,
        mouseY: Int,
    ) {
        val hovered = mouseX in x until (x + toolkitButtonSize) && mouseY in y until (y + toolkitButtonSize)
        SkyHudTheme.outlinedRoundedRect(
            graphics,
            x,
            y,
            toolkitButtonSize,
            toolkitButtonSize,
            if (hovered) SkyHudTheme.SURFACE_RAISED else SkyHudTheme.SURFACE,
            if (hovered) SkyHudTheme.PRIMARY_HOVER else SkyHudTheme.BORDER,
        )
        graphics.item(icon, x + 1, y + 1)
        if (hovered) graphics.setTooltipForNextFrame(Component.literal(label), mouseX, mouseY)
    }

    private fun drawPage(
        graphics: GuiGraphicsExtractor,
        key: StoragePageKey,
        cached: CachedEnderChestPage?,
        x: Int,
        y: Int,
        rows: Int,
        mouseX: Int,
        mouseY: Int,
    ) {
        val gridY = y + pageTitleHeight
        val gridHeight = rows * slotPitch
        val active = key == currentPage
        val hovered = mouseInPageViewport(mouseX, mouseY) &&
            mouseX in x until (x + pageWidth) && mouseY in y until (gridY + gridHeight)

        if (active) {
            drawOutline(graphics, x - 2, gridY - 2, pageWidth + 4, gridHeight + 4, SkyHudTheme.PRIMARY_HOVER, 1)
        }
        graphics.text(font, key.displayName, x, y + 1, SkyHudTheme.TEXT, false)

        val favorite = StoragePagePreferences.isFavorite(key)
        val star = if (favorite) "★" else "☆"
        val starX = x + pageWidth - 10
        val starHovered = mouseInPageViewport(mouseX, mouseY) &&
            mouseX in (starX - 1) until (starX + 10) && mouseY in y until (y + 11)
        graphics.text(
            font,
            star,
            starX,
            y + 1,
            if (favorite || starHovered) 0xFFFFD86B.toInt() else SkyHudTheme.TEXT_MUTED,
            false,
        )
        if (starHovered) {
            graphics.setTooltipForNextFrame(
                Component.literal(if (favorite) "Remove from favorites" else "Move to favorites"),
                mouseX,
                mouseY,
            )
        }

        repeat(rows * 9) { index ->
            val stack = cached?.items?.getOrNull(index) ?: ItemStack.EMPTY
            val slotX = x + (index % 9) * slotPitch
            val slotY = gridY + (index / 9) * slotPitch
            val slotHovered = mouseInPageViewport(mouseX, mouseY) &&
                mouseX in slotX until (slotX + slotSize) && mouseY in slotY until (slotY + slotSize)
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
                graphics.item(stack, slotX + 2, slotY + 2)
                graphics.itemDecorations(font, stack, slotX + 2, slotY + 2)
                if (searchText.isNotBlank() && itemMatches(stack)) {
                    drawOutline(graphics, slotX, slotY, slotSize - 1, slotSize - 1, SkyHudTheme.PRIMARY_HOVER, 1)
                }
                if (slotHovered) graphics.setTooltipForNextFrame(font, stack, mouseX, mouseY)
            }
            if (key == highlightedPage && index == highlightedItemIndex) {
                drawOutline(graphics, slotX, slotY, slotSize - 1, slotSize - 1, SkyHudTheme.PRIMARY_HOVER, 2)
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
                if (hovered) SkyHudTheme.CONTROL_HOVER else SkyHudTheme.CONTROL,
                SkyHudTheme.PRIMARY,
            )
            graphics.text(font, label, buttonX + 8, buttonY + 7, SkyHudTheme.TEXT, false)
        }
    }

    private fun drawInventory(
        graphics: GuiGraphicsExtractor,
        mouseX: Int,
        mouseY: Int,
        inventoryPanelX: Int,
        inventoryTop: Int,
    ) {
        graphics.text(font, "INVENTORY", inventoryPanelX + inventorySidePadding, inventoryTop + 9, SkyHudTheme.TEXT_MUTED, false)

        val menu = backingMenu ?: return
        val startX = inventoryPanelX + inventorySidePadding
        val mainY = inventoryTop + 22
        val playerStart = menu.rowCount * 9
        val bounds = ArrayList<InventorySlotBounds>(36)

        repeat(27) { index ->
            val menuSlot = playerStart + index
            val x = startX + (index % 9) * inventorySlotPitch
            val y = mainY + (index / 9) * inventorySlotPitch
            drawInventorySlot(graphics, menu, menuSlot, x, y, mouseX, mouseY)
            bounds += InventorySlotBounds(menuSlot, x, y)
        }
        repeat(9) { index ->
            val menuSlot = playerStart + 27 + index
            val x = startX + index * inventorySlotPitch
            val y = mainY + 3 * inventorySlotPitch + 5
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
        val hovered = mouseX in x until (x + inventorySlotSize) && mouseY in y until (y + inventorySlotSize)
        graphics.fill(
            x,
            y,
            x + inventorySlotSize,
            y + inventorySlotSize,
            when {
                hovered -> SkyHudTheme.SLOT_HOVER
                stack.isEmpty -> SkyHudTheme.SLOT
                else -> SkyHudTheme.SLOT_FILLED
            },
        )
        if (!stack.isEmpty) {
            val itemInset = (inventorySlotSize - 16) / 2
            graphics.item(stack, x + itemInset, y + itemInset)
            graphics.itemDecorations(font, stack, x + itemInset, y + itemInset)
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
        SkyHudTheme.roundedRect(graphics, x, top, 3, trackHeight, SkyHudTheme.SCROLLBAR_TRACK)
        SkyHudTheme.roundedRect(
            graphics,
            x,
            thumbY,
            3,
            thumbHeight,
            if (mouseX in (x - 3)..(x + 6) && mouseY in thumbY..(thumbY + thumbHeight)) {
                SkyHudTheme.SCROLLBAR_THUMB_HOVER
            } else {
                SkyHudTheme.SCROLLBAR_THUMB
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
        if (
            click.button() == 0 &&
            mouseX in headerEditX(panelX(), "STORAGE") until (headerEditX(panelX(), "STORAGE") + 33) &&
            mouseY in (panelY() + 4) until (panelY() + 20)
        ) {
            editOriginal()
            return true
        }
        if (
            click.button() == 0 &&
            mouseX in headerConfigX(panelX(), "STORAGE") until (headerConfigX(panelX(), "STORAGE") + 16) &&
            mouseY in (panelY() + 4) until (panelY() + 20)
        ) {
            onClose()
            SkyHudConfigManager.open()
            return true
        }

        if (click.button() == 0 && mouseY in (panelY() + 3) until (panelY() + 3 + toolkitButtonSize)) {
            val farmingX = farmingToolkitX(panelX(), panelWidth(), 140)
            val huntingX = farmingX - toolkitButtonSize - toolkitButtonGap
            when {
                mouseX in huntingX until (huntingX + toolkitButtonSize) -> {
                    beginMenuTransition()
                    minecraft.player?.connection?.sendCommand("huntingtoolkit")
                    return true
                }

                mouseX in farmingX until (farmingX + toolkitButtonSize) -> {
                    beginMenuTransition()
                    minecraft.player?.connection?.sendCommand("farmingtoolkit")
                    return true
                }
            }
        }

        val scrollbarX = panelX() + panelWidth() - 9
        val scrollbarTop = panelY() + headerHeight + contentEdgeGap
        val scrollbarBottom = inventoryTop() - contentEdgeGap
        if (click.button() == 0 && mouseX in (scrollbarX - 4)..(scrollbarX + 7) && mouseY in scrollbarTop..scrollbarBottom) {
            draggingScrollbar = true
            updateScrollFromMouse(mouseY, scrollbarTop, scrollbarBottom)
            return true
        }

        inventorySlotBounds.firstOrNull {
            mouseX in it.x until (it.x + inventorySlotSize) && mouseY in it.y until (it.y + inventorySlotSize)
        }?.let {
            clickBackingSlot(it.menuSlot, click.button(), click.hasShiftDown())
            return true
        }

        if (!mouseInPageViewport(mouseX, mouseY)) return false

        val card = pageBounds.firstOrNull {
            mouseX in it.x until (it.x + it.width) && mouseY in it.y until (it.y + it.height)
        } ?: return false
        if (
            click.button() == 0 &&
            mouseX in card.starX until (card.starX + card.starSize) &&
            mouseY in card.starY until (card.starY + card.starSize)
        ) {
            StoragePagePreferences.toggleFavorite(card.key)
            return true
        }
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
            val top = panelY() + headerHeight + contentEdgeGap
            val bottom = inventoryTop() - contentEdgeGap
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
        beginMenuTransition()
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

    private fun pageRowCount(key: StoragePageKey): Int =
        (EnderChestRepository.page(key)?.rows ?: 4).coerceIn(1, 5)

    private fun pageHeight(rows: Int): Int = pageTitleHeight + rows * slotPitch

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

    private fun inventoryTop(): Int = panelY() + panelHeight() - inventoryHeight + 3

    private fun searchX(panelX: Int, panelWidth: Int, searchWidth: Int): Int =
        panelX + panelWidth - searchWidth - 11

    private fun headerEditX(panelX: Int, heading: String): Int =
        panelX + 8 + font.width(heading) + 7

    private fun headerConfigX(panelX: Int, heading: String): Int =
        headerEditX(panelX, heading) + 36

    private fun farmingToolkitX(panelX: Int, panelWidth: Int, searchWidth: Int): Int =
        searchX(panelX, panelWidth, searchWidth) - 4 - toolkitButtonSize - 5

    private fun mouseInPageViewport(mouseX: Int, mouseY: Int): Boolean =
        mouseX in (panelX() + 2) until (panelX() + panelWidth() - 2) &&
            mouseY in (panelY() + headerHeight + contentEdgeGap) until (inventoryTop() - contentEdgeGap)

    private fun inventoryPanelWidth(): Int = 9 * inventorySlotPitch + 2 * inventorySidePadding

    private fun inventoryPanelX(): Int = (width - inventoryPanelWidth()) / 2

    override fun onClose() {
        closed()
        backingMenu = null
        minecraft.player?.closeContainer()
        super.onClose()
    }

    override fun isPauseScreen(): Boolean = false
}
