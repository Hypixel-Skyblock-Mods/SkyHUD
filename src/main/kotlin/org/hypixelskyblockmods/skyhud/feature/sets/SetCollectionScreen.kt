package org.hypixelskyblockmods.skyhud.feature.sets

import kotlin.math.ceil
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.ChestMenu
import net.minecraft.world.inventory.ContainerInput
import org.hypixelskyblockmods.skyhud.gui.SkyHudTheme
import org.hypixelskyblockmods.skyhud.util.VanillaItemIds

class SetCollectionScreen(
    screenName: String,
    private val heading: String,
    private val searchHint: String,
    private val setLabel: String,
    private val repository: SetCollectionRepository,
    private val closed: () -> Unit,
) : Screen(Component.literal(screenName)) {
    private data class SetBounds(
        val index: Int,
        val x: Int,
        val y: Int,
        val width: Int,
        val height: Int,
        val clickable: Boolean,
    )

    private var currentPage = 1
    private var totalPages = 1
    private var backingMenu: ChestMenu? = null
    private var searchText = ""
    private var scroll = 0.0
    private var maxScroll = 0.0
    private var setBounds = emptyList<SetBounds>()
    private var draggingScrollbar = false

    private val headerHeight = 54
    private val pageColumns = 3
    private val pageWidth = 230
    private val pageGapHorizontal = 28
    private val pageGapVertical = 30
    private val pageTitleHeight = 19
    private val slotSize = 48
    private val slotGap = 12
    private val itemSize = 16
    private val gridWidth = slotSize * 4 + slotGap * 3

    fun bind(target: SetCollectionTarget) {
        currentPage = target.page
        totalPages = target.totalPages
        backingMenu = target.menu
        repository.remember(target.page, target.menu)
    }

    fun refreshBackingMenu(menu: AbstractContainerMenu?) {
        val chestMenu = menu as? ChestMenu ?: return
        if (chestMenu !== backingMenu) return
        repository.remember(currentPage, chestMenu)
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
            Component.literal("Search $heading"),
        )
        search.value = searchText
        search.setHint(Component.literal(searchHint))
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
        graphics.text(font, heading, 24, 23, SkyHudTheme.TEXT, false)

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

        drawNavigation(graphics, mouseX, mouseY)
        drawSets(graphics, mouseX, mouseY)
        drawScrollbar(graphics, mouseX, mouseY)
        super.extractRenderState(graphics, mouseX, mouseY, delta)
    }

    private fun drawNavigation(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
        if (totalPages <= 1) return
        val previousEnabled = currentPage > 1 && hasNavigationArrow(45)
        val nextEnabled = currentPage < totalPages && hasNavigationArrow(53)
        drawNavigationButton(graphics, width / 2 - 58, 15, "<", previousEnabled, mouseX, mouseY)
        drawNavigationButton(graphics, width / 2 + 30, 15, ">", nextEnabled, mouseX, mouseY)
        val pageLabel = "$currentPage / $totalPages"
        graphics.text(
            font,
            pageLabel,
            width / 2 - font.width(pageLabel) / 2,
            23,
            SkyHudTheme.TEXT_MUTED,
            false,
        )
    }

    private fun drawNavigationButton(
        graphics: GuiGraphicsExtractor,
        x: Int,
        y: Int,
        label: String,
        enabled: Boolean,
        mouseX: Int,
        mouseY: Int,
    ) {
        val hovered = enabled && mouseX in x until (x + 28) && mouseY in y until (y + 24)
        SkyHudTheme.outlinedRoundedRect(
            graphics,
            x,
            y,
            28,
            24,
            when {
                hovered -> SkyHudTheme.PRIMARY_HOVER
                enabled -> SkyHudTheme.PRIMARY
                else -> SkyHudTheme.SURFACE
            },
            if (enabled) SkyHudTheme.PRIMARY else SkyHudTheme.BORDER,
        )
        graphics.text(font, label, x + 14 - font.width(label) / 2, y + 8, SkyHudTheme.TEXT, false)
    }

    private fun drawSets(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
        val page = repository.page(currentPage) ?: return
        val visible = page.slots.filter(::setMatchesSearch)
        val viewportTop = headerHeight + 16
        val viewportBottom = height - 16
        val viewportHeight = (viewportBottom - viewportTop).coerceAtLeast(1)
        val contentWidth = pageColumns * pageWidth + (pageColumns - 1) * pageGapHorizontal
        val startX = (width - contentWidth) / 2
        val pageHeight = pageTitleHeight + slotSize
        val rows = ceil(visible.size / pageColumns.toDouble()).toInt()
        val contentHeight = rows * pageHeight + (rows - 1).coerceAtLeast(0) * pageGapVertical
        maxScroll = (contentHeight - viewportHeight).coerceAtLeast(0).toDouble()
        scroll = scroll.coerceIn(0.0, maxScroll)

        graphics.enableScissor(0, viewportTop, width, viewportBottom)
        val bounds = ArrayList<SetBounds>(visible.size)
        visible.forEachIndexed { position, set ->
            val column = position % pageColumns
            val row = position / pageColumns
            val x = startX + column * (pageWidth + pageGapHorizontal)
            val y = viewportTop + row * (pageHeight + pageGapVertical) - scroll.toInt()
            drawSet(graphics, set, x, y, mouseX, mouseY)
            bounds += SetBounds(set.index, x, y, pageWidth, pageHeight, set.selectable)
        }
        graphics.disableScissor()
        setBounds = bounds

        if (visible.isEmpty()) {
            val message = "No ${heading.lowercase()} sets match ‘$searchText’"
            graphics.text(
                font,
                message,
                width / 2 - font.width(message) / 2,
                viewportTop + 30,
                SkyHudTheme.TEXT_MUTED,
                false,
            )
        }
    }

    private fun drawSet(
        graphics: GuiGraphicsExtractor,
        set: CachedSetSlot,
        x: Int,
        y: Int,
        mouseX: Int,
        mouseY: Int,
    ) {
        val gridX = x + (pageWidth - gridWidth) / 2
        val gridY = y + pageTitleHeight
        if (set.selected) {
            drawOutline(graphics, gridX - 4, gridY - 4, gridWidth + 8, slotSize + 8, SkyHudTheme.PRIMARY_HOVER)
        }

        graphics.text(
            font,
            "$setLabel ${set.id}",
            x,
            y + 2,
            if (set.locked) SkyHudTheme.TEXT_MUTED else SkyHudTheme.TEXT,
            false,
        )

        val itemInset = (slotSize - itemSize) / 2
        repeat(4) { index ->
            val stack = set.items[index]
            val slotX = gridX + index * (slotSize + slotGap)
            val hovered = mouseX in slotX until (slotX + slotSize) && mouseY in gridY until (gridY + slotSize)
            graphics.fill(
                slotX,
                gridY,
                slotX + slotSize,
                gridY + slotSize,
                when {
                    hovered -> SkyHudTheme.SLOT_HOVER
                    stack.isEmpty -> SkyHudTheme.SLOT
                    else -> SkyHudTheme.SLOT_FILLED
                },
            )
            if (!stack.isEmpty) {
                val itemX = slotX + itemInset
                val itemY = gridY + itemInset
                graphics.item(stack, itemX, itemY)
                graphics.itemDecorations(font, stack, itemX, itemY)
                if (hovered) graphics.setTooltipForNextFrame(font, stack, mouseX, mouseY)
            }
        }
    }

    private fun drawOutline(
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
        if (click.button() != 0) return false
        val mouseX = click.x.toInt()
        val mouseY = click.y.toInt()

        if (mouseX in (width - 21)..(width - 6) && mouseY in (headerHeight + 8)..(height - 8)) {
            draggingScrollbar = true
            updateScrollFromMouse(mouseY)
            return true
        }
        if (mouseY in 15 until 39) {
            if (mouseX in (width / 2 - 58) until (width / 2 - 30)) {
                clickNavigationSlot(45)
                return true
            }
            if (mouseX in (width / 2 + 30) until (width / 2 + 58)) {
                clickNavigationSlot(53)
                return true
            }
        }

        val set = setBounds.firstOrNull {
            mouseX in it.x until (it.x + it.width) && mouseY in it.y until (it.y + it.height)
        } ?: return false
        if (set.clickable) clickBackingSlot(36 + set.index)
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

    private fun clickNavigationSlot(slot: Int) {
        if (!hasNavigationArrow(slot)) return
        clickBackingSlot(slot)
    }

    private fun hasNavigationArrow(slot: Int): Boolean =
        backingMenu?.getSlot(slot)?.item?.let { VanillaItemIds.isItem(it, "arrow") } == true

    private fun clickBackingSlot(slot: Int) {
        val menu = backingMenu ?: return
        val player = minecraft.player ?: return
        if (player.containerMenu !== menu || slot !in 0 until 54) return
        minecraft.gameMode?.handleContainerInput(
            menu.containerId,
            slot,
            0,
            ContainerInput.PICKUP,
            player,
        )
    }

    private fun setMatchesSearch(set: CachedSetSlot): Boolean {
        if (searchText.isBlank()) return true
        val terms = searchText.trim().split(Regex("\\s+"))
        return terms.all { term ->
            set.items.any { !it.isEmpty && it.hoverName.string.contains(term, ignoreCase = true) }
        }
    }

    override fun onClose() {
        closed()
        backingMenu = null
        minecraft.player?.closeContainer()
        super.onClose()
    }

    override fun isPauseScreen(): Boolean = false
}
