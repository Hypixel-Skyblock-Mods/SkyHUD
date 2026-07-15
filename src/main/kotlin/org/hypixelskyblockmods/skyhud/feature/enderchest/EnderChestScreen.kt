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
    private val footerHeight = 22
    private val cardWidth = 176
    private val cardGap = 10
    private val cardHeaderHeight = 25
    private val slotSize = 18

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
        val searchWidth = minOf(210, width / 3)
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

        graphics.text(font, "ENDER CHEST", 24, 17, SkyHudTheme.TEXT, false)
        graphics.text(
            font,
            "All pages in one place  •  Page $currentPage of $totalPages",
            24,
            31,
            SkyHudTheme.TEXT_MUTED,
            false,
        )

        val searchWidth = minOf(210, width / 3)
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

        graphics.fill(0, height - footerHeight, width, height, 0xFF101010.toInt())
        graphics.text(
            font,
            "Click an unopened page to load it without leaving SkyHUD",
            24,
            height - 15,
            SkyHudTheme.TEXT_MUTED,
            false,
        )
        super.extractRenderState(graphics, mouseX, mouseY, delta)
    }

    private fun drawPages(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
        val viewportTop = headerHeight + 12
        val viewportBottom = height - footerHeight - 8
        val viewportHeight = (viewportBottom - viewportTop).coerceAtLeast(1)
        val availableWidth = width - 54
        val columns = (availableWidth / (cardWidth + cardGap)).coerceIn(1, 3)
        val contentWidth = columns * cardWidth + (columns - 1) * cardGap
        val startX = (width - contentWidth) / 2 - 4
        val visiblePages = (1..totalPages).filter(::pageMatchesSearch)
        val cardHeight = cardHeaderHeight + 5 * slotSize + 8
        val rows = ceil(visiblePages.size / columns.toDouble()).toInt()
        val contentHeight = rows * cardHeight + (rows - 1).coerceAtLeast(0) * cardGap
        maxScroll = (contentHeight - viewportHeight).coerceAtLeast(0).toDouble()
        scroll = scroll.coerceIn(0.0, maxScroll)

        graphics.enableScissor(0, viewportTop, width, viewportBottom)
        val bounds = ArrayList<PageBounds>(visiblePages.size)
        visiblePages.forEachIndexed { index, page ->
            val column = index % columns
            val row = index / columns
            val x = startX + column * (cardWidth + cardGap)
            val y = viewportTop + row * (cardHeight + cardGap) - scroll.toInt()
            val cached = EnderChestRepository.page(page)
            drawPageCard(graphics, cached, page, x, y, mouseX, mouseY)
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
        mouseX: Int,
        mouseY: Int,
    ) {
        val cardHeight = cardHeaderHeight + 5 * slotSize + 8
        val active = page == currentPage
        val hovered = mouseX in x until (x + cardWidth) && mouseY in y until (y + cardHeight)
        SkyHudTheme.outlinedRoundedRect(
            graphics,
            x,
            y,
            cardWidth,
            cardHeight,
            if (hovered) SkyHudTheme.SURFACE_RAISED else SkyHudTheme.SURFACE,
            if (active) SkyHudTheme.PRIMARY_HOVER else SkyHudTheme.BORDER,
        )

        if (active) graphics.fill(x + 1, y + 1, x + 4, y + cardHeight - 1, SkyHudTheme.PRIMARY)
        graphics.text(font, "PAGE $page", x + 10, y + 9, SkyHudTheme.TEXT, false)
        graphics.text(
            font,
            if (cached == null) "NOT OPENED" else if (active) "OPEN" else "CACHED",
            x + cardWidth - 56,
            y + 9,
            if (active) 0xFF8EB5F4.toInt() else SkyHudTheme.TEXT_MUTED,
            false,
        )

        if (cached == null) {
            drawUnopenedPage(graphics, page, x, y, hovered)
            return
        }

        val queryActive = searchText.isNotBlank()
        cached.items.take(45).forEachIndexed { index, stack ->
            val slotX = x + 7 + (index % 9) * slotSize
            val slotY = y + cardHeaderHeight + 2 + (index / 9) * slotSize
            val slotHovered = mouseX in slotX until (slotX + 16) && mouseY in slotY until (slotY + 16)
            graphics.fill(
                slotX - 1,
                slotY - 1,
                slotX + 17,
                slotY + 17,
                if (slotHovered) SkyHudTheme.SLOT_HOVER else SkyHudTheme.SLOT,
            )
            if (!stack.isEmpty) {
                graphics.item(stack, slotX, slotY)
                graphics.itemDecorations(font, stack, slotX, slotY)
                if (queryActive && itemMatches(stack)) {
                    drawSlotOutline(graphics, slotX - 1, slotY - 1, SkyHudTheme.PRIMARY_HOVER)
                }
                if (slotHovered) {
                    graphics.setTooltipForNextFrame(font, stack, mouseX, mouseY)
                }
            }
        }
    }

    private fun drawUnopenedPage(
        graphics: GuiGraphicsExtractor,
        page: Int,
        x: Int,
        y: Int,
        hovered: Boolean,
    ) {
        val buttonX = x + 18
        val buttonY = y + 48
        val buttonWidth = cardWidth - 36
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
        graphics.text(
            font,
            "Loads page $page in the background",
            x + 17,
            buttonY + 39,
            SkyHudTheme.TEXT_MUTED,
            false,
        )
    }

    private fun drawSlotOutline(graphics: GuiGraphicsExtractor, x: Int, y: Int, color: Int) {
        graphics.fill(x, y, x + 18, y + 1, color)
        graphics.fill(x, y + 17, x + 18, y + 18, color)
        graphics.fill(x, y, x + 1, y + 18, color)
        graphics.fill(x + 17, y, x + 18, y + 18, color)
    }

    private fun drawScrollbar(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
        if (maxScroll <= 0.0) return
        val top = headerHeight + 12
        val bottom = height - footerHeight - 8
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

        if (mouseX in (width - 21)..(width - 6) && mouseY in (headerHeight + 8)..(height - footerHeight)) {
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
        if (column !in 0..8 || row !in 0..4) return true
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
        val top = headerHeight + 12
        val bottom = height - footerHeight - 8
        val percentage = ((mouseY - top).toDouble() / (bottom - top)).coerceIn(0.0, 1.0)
        scroll = percentage * maxScroll
    }

    private fun navigateToPage(page: Int) {
        minecraft.player?.connection?.sendCommand("enderchest $page")
    }

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
