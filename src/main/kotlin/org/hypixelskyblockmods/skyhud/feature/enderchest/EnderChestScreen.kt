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
    private val cardWidth = 194
    private val cardGap = 14
    private val cardHeaderHeight = 24
    private val slotSize = 20
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
        val contentWidth = pageColumns * cardWidth + (pageColumns - 1) * cardGap
        val startX = (width - contentWidth) / 2
        val visiblePages = (1..totalPages).filter(::pageMatchesSearch)
        val visibleRows = visibleRowCount()
        val cardHeight = cardHeight(visibleRows)
        val rows = ceil(visiblePages.size / pageColumns.toDouble()).toInt()
        val contentHeight = rows * cardHeight + (rows - 1).coerceAtLeast(0) * cardGap
        maxScroll = (contentHeight - viewportHeight).coerceAtLeast(0).toDouble()
        scroll = scroll.coerceIn(0.0, maxScroll)

        graphics.enableScissor(0, viewportTop, width, viewportBottom)
        val bounds = ArrayList<PageBounds>(visiblePages.size)
        visiblePages.forEachIndexed { index, page ->
            val column = index % pageColumns
            val row = index / pageColumns
            val x = startX + column * (cardWidth + cardGap)
            val y = viewportTop + row * (cardHeight + cardGap) - scroll.toInt()
            val cached = EnderChestRepository.page(page)
            drawPageCard(graphics, cached, page, x, y, visibleRows, mouseX, mouseY)
            bounds += PageBounds(page, x, y, cardWidth, cardHeight, cached != null)
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

    private fun drawPageCard(
        graphics: GuiGraphicsExtractor,
        cached: CachedEnderChestPage?,
        page: Int,
        x: Int,
        y: Int,
        visibleRows: Int,
        mouseX: Int,
        mouseY: Int,
    ) {
        val cardHeight = cardHeight(visibleRows)
        val active = page == currentPage
        val hovered = mouseX in x until (x + cardWidth) && mouseY in y until (y + cardHeight)
        val fill = if (hovered) SkyHudTheme.SURFACE_RAISED else SkyHudTheme.SURFACE
        if (active) {
            SkyHudTheme.roundedRect(graphics, x, y, cardWidth, cardHeight, SkyHudTheme.PRIMARY_HOVER)
            SkyHudTheme.roundedRect(graphics, x + 2, y + 2, cardWidth - 4, cardHeight - 4, fill)
        } else {
            SkyHudTheme.outlinedRoundedRect(graphics, x, y, cardWidth, cardHeight, fill, SkyHudTheme.BORDER)
        }

        graphics.text(font, "PAGE $page", x + 10, y + 8, SkyHudTheme.TEXT, false)

        if (cached == null) {
            drawUnopenedPage(graphics, x, y, cardHeight, hovered)
            return
        }

        val queryActive = searchText.isNotBlank()
        val itemInset = (slotSize - itemSize) / 2
        cached.items.take(visibleRows * 9).forEachIndexed { index, stack ->
            val slotX = x + 7 + (index % 9) * slotSize
            val slotY = y + cardHeaderHeight + 2 + (index / 9) * slotSize
            val slotHovered = mouseX in slotX until (slotX + slotSize) && mouseY in slotY until (slotY + slotSize)
            graphics.fill(
                slotX,
                slotY,
                slotX + slotSize,
                slotY + slotSize,
                if (slotHovered) SkyHudTheme.SLOT_HOVER else SkyHudTheme.SLOT,
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
        y: Int,
        cardHeight: Int,
        hovered: Boolean,
    ) {
        val buttonX = x + 24
        val buttonY = y + cardHeaderHeight + (cardHeight - cardHeaderHeight - 30) / 2
        val buttonWidth = cardWidth - 48
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

        val itemX = mouseX - (card.x + 7)
        val itemY = mouseY - (card.y + cardHeaderHeight + 2)
        if (itemX < 0 || itemY < 0) return true
        val column = itemX / slotSize
        val row = itemY / slotSize
        if (column !in 0..8 || row !in 0 until visibleRowCount()) return true
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

    private fun cardHeight(rows: Int): Int = cardHeaderHeight + rows * slotSize + 8

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
