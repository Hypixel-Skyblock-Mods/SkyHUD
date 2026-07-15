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
) : Screen(Component.literal("SkyHUD Ender Chest")) {
    private data class PageBounds(
        val page: Int,
        val x: Int,
        val y: Int,
        val width: Int,
        val height: Int,
        val loaded: Boolean,
    )

    private var currentPage = 1
    private var totalPages = 9
    private var backingMenu: ChestMenu? = null
    private var searchText = ""
    private var scroll = 0.0
    private var maxScroll = 0.0
    private var pageBounds = emptyList<PageBounds>()
    private var draggingScrollbar = false

    private val headerHeight = 54
    private val pageColumns = 3
    private val pageWidth = 230
    private val pageGapHorizontal = 28
    private val pageGapVertical = 30
    private val pageTitleHeight = 19
    private val slotSize = 22
    private val slotGap = 4
    private val itemSize = 16

    fun bind(target: EnderChestTarget) {
        currentPage = target.page
        totalPages = target.totalPages
        backingMenu = target.menu
        EnderChestRepository.remember(target.page, target.menu)
    }

    fun refreshBackingMenu(menu: AbstractContainerMenu?) {
        val chestMenu = menu as? ChestMenu ?: return
        if (chestMenu !== backingMenu) return
        EnderChestRepository.remember(currentPage, chestMenu)
    }

    override fun init() {
        super.init()
        val searchWidth = minOf(240, width / 3)
        val search = EditBox(
            font,
            width - searchWidth - 24,
            18,
            searchWidth,
            20,
            Component.literal("Search Ender Chest"),
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
        graphics.fill(0, 0, width, height, SkyHudTheme.BACKGROUND)
        graphics.fill(0, 0, width, headerHeight, 0xFF101010.toInt())
        graphics.fill(0, headerHeight - 1, width, headerHeight, SkyHudTheme.PRIMARY)

        graphics.text(font, "ENDER CHEST", 24, 23, SkyHudTheme.TEXT, false)

        val searchWidth = minOf(240, width / 3)
        SkyHudTheme.outlinedRoundedRect(
            graphics,
            width - searchWidth - 30,
            13,
            searchWidth + 12,
            30,
            SkyHudTheme.SURFACE,
            SkyHudTheme.BORDER,
        )

        drawPages(graphics, mouseX, mouseY)
        drawScrollbar(graphics, mouseX, mouseY)
        super.extractRenderState(graphics, mouseX, mouseY, delta)
    }

    private fun drawPages(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
        val viewportTop = headerHeight + 16
        val viewportBottom = height - 16
        val viewportHeight = (viewportBottom - viewportTop).coerceAtLeast(1)
        val contentWidth = pageColumns * pageWidth + (pageColumns - 1) * pageGapHorizontal
        val startX = (width - contentWidth) / 2
        val visiblePages = (1..totalPages).filter(::pageMatchesSearch)
        val visibleRows = visibleRowCount()
        val pageHeight = pageHeight(visibleRows)
        val rows = ceil(visiblePages.size / pageColumns.toDouble()).toInt()
        val contentHeight = rows * pageHeight + (rows - 1).coerceAtLeast(0) * pageGapVertical
        maxScroll = (contentHeight - viewportHeight).coerceAtLeast(0).toDouble()
        scroll = scroll.coerceIn(0.0, maxScroll)

        graphics.enableScissor(0, viewportTop, width, viewportBottom)
        val bounds = ArrayList<PageBounds>(visiblePages.size)
        visiblePages.forEachIndexed { index, page ->
            val column = index % pageColumns
            val row = index / pageColumns
            val x = startX + column * (pageWidth + pageGapHorizontal)
            val y = viewportTop + row * (pageHeight + pageGapVertical) - scroll.toInt()
            val cached = EnderChestRepository.page(page)
            drawPage(graphics, cached, page, x, y, visibleRows, mouseX, mouseY)
            bounds += PageBounds(page, x, y, pageWidth, pageHeight, cached != null)
        }
        graphics.disableScissor()
        pageBounds = bounds

        if (visiblePages.isEmpty()) {
            graphics.text(
                font,
                "No Ender Chest items match ‘$searchText’",
                width / 2 - font.width("No Ender Chest items match ‘$searchText’") / 2,
                viewportTop + 30,
                SkyHudTheme.TEXT_MUTED,
                false,
            )
        }
    }

    private fun drawPage(
        graphics: GuiGraphicsExtractor,
        cached: CachedEnderChestPage?,
        page: Int,
        x: Int,
        y: Int,
        visibleRows: Int,
        mouseX: Int,
        mouseY: Int,
    ) {
        val gridY = y + pageTitleHeight
        val gridHeight = gridHeight(visibleRows)
        val active = page == currentPage
        val hovered = mouseX in x until (x + pageWidth) && mouseY in y until (gridY + gridHeight)

        if (active) {
            drawPageOutline(
                graphics,
                x - 4,
                gridY - 4,
                pageWidth + 8,
                gridHeight + 8,
                SkyHudTheme.PRIMARY_HOVER,
            )
        }

        graphics.text(font, "PAGE $page", x, y + 2, SkyHudTheme.TEXT, false)

        if (cached == null) {
            drawUnopenedPage(graphics, x, gridY, gridHeight, hovered)
            return
        }

        val queryActive = searchText.isNotBlank()
        val itemInset = (slotSize - itemSize) / 2
        repeat(visibleRows * 9) { index ->
            val stack = cached.items.getOrNull(index) ?: ItemStack.EMPTY
            val slotX = x + (index % 9) * (slotSize + slotGap)
            val slotY = gridY + (index / 9) * (slotSize + slotGap)
            val slotHovered = mouseX in slotX until (slotX + slotSize) && mouseY in slotY until (slotY + slotSize)
            graphics.fill(
                slotX,
                slotY,
                slotX + slotSize,
                slotY + slotSize,
                when {
                    slotHovered -> SkyHudTheme.SLOT_HOVER
                    stack.isEmpty -> SkyHudTheme.SLOT
                    else -> SkyHudTheme.SLOT_FILLED
                },
            )
            if (!stack.isEmpty) {
                val itemX = slotX + itemInset
                val itemY = slotY + itemInset
                graphics.item(stack, itemX, itemY)
                graphics.itemDecorations(font, stack, itemX, itemY)
                if (queryActive && itemMatches(stack)) {
                    drawSlotOutline(graphics, slotX, slotY, slotSize, SkyHudTheme.PRIMARY_HOVER)
                }
                if (slotHovered) {
                    graphics.setTooltipForNextFrame(font, stack, mouseX, mouseY)
                }
            }
        }
    }

    private fun drawUnopenedPage(
        graphics: GuiGraphicsExtractor,
        x: Int,
        gridY: Int,
        gridHeight: Int,
        hovered: Boolean,
    ) {
        val buttonX = x + 38
        val buttonY = gridY + (gridHeight - 30) / 2
        val buttonWidth = pageWidth - 76
        SkyHudTheme.outlinedRoundedRect(
            graphics,
            buttonX,
            buttonY,
            buttonWidth,
            30,
            if (hovered) SkyHudTheme.PRIMARY_HOVER else SkyHudTheme.PRIMARY,
            if (hovered) 0xFF5479B5.toInt() else SkyHudTheme.PRIMARY,
        )
        val label = "OPEN THIS PAGE"
        graphics.text(
            font,
            label,
            buttonX + (buttonWidth - font.width(label)) / 2,
            buttonY + 11,
            SkyHudTheme.TEXT,
            false,
        )
    }

    private fun drawPageOutline(
        graphics: GuiGraphicsExtractor,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        color: Int,
    ) {
        val thickness = 2
        graphics.fill(x, y, x + width, y + thickness, color)
        graphics.fill(x, y + height - thickness, x + width, y + height, color)
        graphics.fill(x, y, x + thickness, y + height, color)
        graphics.fill(x + width - thickness, y, x + width, y + height, color)
    }

    private fun drawSlotOutline(graphics: GuiGraphicsExtractor, x: Int, y: Int, size: Int, color: Int) {
        graphics.fill(x, y, x + size, y + 1, color)
        graphics.fill(x, y + size - 1, x + size, y + size, color)
        graphics.fill(x, y, x + 1, y + size, color)
        graphics.fill(x + size - 1, y, x + size, y + size, color)
    }

    private fun drawScrollbar(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
        if (maxScroll <= 0.0) return
        val top = headerHeight + 16
        val bottom = height - 16
        val trackHeight = bottom - top
        val thumbHeight = (trackHeight * (trackHeight / (trackHeight + maxScroll))).toInt().coerceAtLeast(28)
        val thumbTravel = trackHeight - thumbHeight
        val thumbY = top + ((scroll / maxScroll) * thumbTravel).toInt()
        val x = width - 16
        SkyHudTheme.roundedRect(graphics, x, top, 4, trackHeight, 0xFF202020.toInt())
        SkyHudTheme.roundedRect(
            graphics,
            x,
            thumbY,
            4,
            thumbHeight,
            if (mouseX in (x - 3)..(x + 7) && mouseY in thumbY..(thumbY + thumbHeight)) {
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
            scroll = (scroll - verticalAmount * 34.0).coerceIn(0.0, maxScroll)
            return true
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)
    }

    override fun mouseClicked(click: MouseButtonEvent, doubled: Boolean): Boolean {
        if (super.mouseClicked(click, doubled)) return true
        val mouseX = click.x.toInt()
        val mouseY = click.y.toInt()
        if (click.button() != 0) return false

        if (mouseX in (width - 21)..(width - 6) && mouseY in (headerHeight + 8)..(height - 8)) {
            draggingScrollbar = true
            updateScrollFromMouse(mouseY)
            return true
        }

        val card = pageBounds.firstOrNull {
            mouseX in it.x until (it.x + it.width) && mouseY in it.y until (it.y + it.height)
        } ?: return false

        if (!card.loaded || card.page != currentPage) {
            navigateToPage(card.page)
            return true
        }

        val itemX = mouseX - card.x
        val itemY = mouseY - (card.y + pageTitleHeight)
        if (itemX < 0 || itemY < 0) return true
        val slotPitch = slotSize + slotGap
        val column = itemX / slotPitch
        val row = itemY / slotPitch
        if (column !in 0..8 || row !in 0 until visibleRowCount()) return true
        if (itemX % slotPitch >= slotSize || itemY % slotPitch >= slotSize) return true
        clickBackingSlot(9 + row * 9 + column, click.button())
        return true
    }

    override fun mouseDragged(click: MouseButtonEvent, dragX: Double, dragY: Double): Boolean {
        if (draggingScrollbar) {
            updateScrollFromMouse(click.y.toInt())
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

    private fun updateScrollFromMouse(mouseY: Int) {
        val top = headerHeight + 16
        val bottom = height - 16
        val percentage = ((mouseY - top).toDouble() / (bottom - top)).coerceIn(0.0, 1.0)
        scroll = percentage * maxScroll
    }

    private fun navigateToPage(page: Int) {
        minecraft.player?.connection?.sendCommand("enderchest $page")
    }

    private fun visibleRowCount(): Int {
        val cachedRows = (1..totalPages)
            .mapNotNull { EnderChestRepository.page(it)?.rows }
            .maxOrNull()
        return (cachedRows ?: backingMenu?.rowCount?.minus(1) ?: 4).coerceIn(1, 5)
    }

    private fun gridHeight(rows: Int): Int = rows * slotSize + (rows - 1).coerceAtLeast(0) * slotGap

    private fun pageHeight(rows: Int): Int = pageTitleHeight + gridHeight(rows)

    private fun clickBackingSlot(slot: Int, button: Int) {
        val menu = backingMenu ?: return
        val player = minecraft.player ?: return
        if (player.containerMenu !== menu || slot !in 0 until (menu.rowCount * 9)) return
        minecraft.gameMode?.handleContainerInput(
            menu.containerId,
            slot,
            button,
            ContainerInput.PICKUP,
            player,
        )
    }

    private fun pageMatchesSearch(page: Int): Boolean {
        if (searchText.isBlank()) return true
        val cached = EnderChestRepository.page(page) ?: return true
        return cached.items.any(::itemMatches)
    }

    private fun itemMatches(stack: ItemStack): Boolean {
        if (searchText.isBlank()) return true
        if (stack.isEmpty) return false
        val name = stack.hoverName.string
        return searchText
            .trim()
            .split(Regex("\\s+"))
            .all { name.contains(it, ignoreCase = true) }
    }

    override fun onClose() {
        closed()
        backingMenu = null
        minecraft.player?.closeContainer()
        super.onClose()
    }

    override fun isPauseScreen(): Boolean = false
}
