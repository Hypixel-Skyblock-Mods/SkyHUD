package org.hypixelskyblockmods.skyhud.feature.sets

import kotlin.math.ceil
import net.minecraft.client.entity.ClientMannequin
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.InventoryScreen
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.client.renderer.PlayerSkinRenderCache
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.player.PlayerSkin
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.ChestMenu
import net.minecraft.world.inventory.ContainerInput
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import org.hypixelskyblockmods.skyhud.gui.SkyHudTheme

class SetCollectionScreen(
    screenName: String,
    private val heading: String,
    private val searchHint: String,
    private val setLabel: String,
    private val repository: SetCollectionRepository,
    private val renderArmorMannequin: Boolean,
    private val requestAction: (page: Int, index: Int?) -> Unit,
    private val editOriginal: () -> Unit,
    private val closed: () -> Unit,
) : Screen(Component.literal(screenName)) {
    private data class SetBounds(
        val card: SetCard,
        val x: Int,
        val y: Int,
        val width: Int,
        val height: Int,
    )

    private var currentPage = 1
    private var totalPages = 1
    private var backingMenu: ChestMenu? = null
    private var searchText = ""
    private var scroll = 0.0
    private var maxScroll = 0.0
    private var setBounds = emptyList<SetBounds>()
    private var draggingScrollbar = false
    private val mannequins = mutableMapOf<Int, SetMannequin>()

    private val panelMaxWidth = 620
    private val panelMaxHeight = 430
    private val headerHeight = 24
    private val columns = 5
    private val cardGap = 6
    private val rowGap = 9
    private val titleHeight = 13
    private val cardHeight = if (renderArmorMannequin) 126 else 70
    private val slotSize = 20

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
        val searchWidth = 140
        val search = EditBox(
            font,
            searchX(panelX(), panelWidth(), searchWidth),
            panelY() + 7,
            searchWidth,
            12,
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
        val panelX = panelX()
        val panelY = panelY()
        val panelWidth = panelWidth()
        val panelHeight = panelHeight()
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
        drawHeader(graphics, mouseX, mouseY, panelX, panelY, panelWidth)
        drawSets(graphics, mouseX, mouseY, panelX, panelY, panelWidth, panelHeight)
        super.extractRenderState(graphics, mouseX, mouseY, delta)
    }

    private fun drawHeader(
        graphics: GuiGraphicsExtractor,
        mouseX: Int,
        mouseY: Int,
        panelX: Int,
        panelY: Int,
        panelWidth: Int,
    ) {
        val titleX = panelX + 8
        val editX = headerEditX(panelX)
        val editHovered = mouseX in editX until (editX + 33) && mouseY in (panelY + 4) until (panelY + 20)
        graphics.text(font, heading, titleX, panelY + 8, SkyHudTheme.TEXT, false)
        SkyHudTheme.outlinedRoundedRect(
            graphics,
            editX,
            panelY + 4,
            33,
            16,
            if (editHovered) SkyHudTheme.PRIMARY_HOVER else SkyHudTheme.PRIMARY,
            SkyHudTheme.PRIMARY,
        )
        graphics.text(font, "EDIT", editX + 5, panelY + 8, SkyHudTheme.TEXT, false)

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
    }

    private fun drawSets(
        graphics: GuiGraphicsExtractor,
        mouseX: Int,
        mouseY: Int,
        panelX: Int,
        panelY: Int,
        panelWidth: Int,
        panelHeight: Int,
    ) {
        val visible = repository.allSets(totalPages).filter(::setMatchesSearch)
        val viewportTop = panelY + headerHeight + 6
        val viewportBottom = panelY + panelHeight - 7
        val viewportHeight = viewportBottom - viewportTop
        val contentWidth = panelWidth - 24
        val cardWidth = (contentWidth - (columns - 1) * cardGap) / columns
        val startX = panelX + (panelWidth - (columns * cardWidth + (columns - 1) * cardGap)) / 2
        val fullCardHeight = titleHeight + cardHeight
        val rows = ceil(visible.size / columns.toDouble()).toInt()
        val contentHeight = rows * fullCardHeight + (rows - 1).coerceAtLeast(0) * rowGap
        maxScroll = (contentHeight - viewportHeight).coerceAtLeast(0).toDouble()
        scroll = scroll.coerceIn(0.0, maxScroll)

        graphics.enableScissor(panelX + 2, viewportTop, panelX + panelWidth - 2, viewportBottom)
        val bounds = ArrayList<SetBounds>(visible.size)
        visible.forEachIndexed { position, card ->
            val x = startX + (position % columns) * (cardWidth + cardGap)
            val y = viewportTop + (position / columns) * (fullCardHeight + rowGap) - scroll.toInt()
            drawSet(graphics, card, x, y, cardWidth, mouseX, mouseY)
            bounds += SetBounds(card, x, y, cardWidth, fullCardHeight)
        }
        graphics.disableScissor()
        setBounds = bounds
        drawScrollbar(graphics, mouseX, mouseY, panelX + panelWidth - 8, viewportTop, viewportBottom)

        if (visible.isEmpty()) {
            val message = "No ${heading.lowercase()} sets match '$searchText'"
            graphics.text(font, message, panelX + (panelWidth - font.width(message)) / 2, viewportTop + 24, SkyHudTheme.TEXT_MUTED, false)
        }
    }

    private fun drawSet(
        graphics: GuiGraphicsExtractor,
        card: SetCard,
        x: Int,
        y: Int,
        width: Int,
        mouseX: Int,
        mouseY: Int,
    ) {
        val set = card.set
        val cardY = y + titleHeight
        val empty = set?.items?.all(ItemStack::isEmpty) != false
        val locked = set?.locked == true
        val hovered = mouseX in x until (x + width) && mouseY in cardY until (cardY + cardHeight)
        SkyHudTheme.roundedRect(
            graphics,
            x,
            cardY,
            width,
            cardHeight,
            when {
                set == null || empty || locked -> 0xFF101010.toInt()
                hovered -> SkyHudTheme.SURFACE_RAISED
                else -> SkyHudTheme.SURFACE
            },
        )
        if (set?.selected == true) drawOutline(graphics, x - 1, cardY - 1, width + 2, cardHeight + 2, SkyHudTheme.PRIMARY_HOVER)
        graphics.text(
            font,
            "$setLabel ${card.id}",
            x,
            y + 1,
            if (set == null || empty || locked) SkyHudTheme.TEXT_MUTED else SkyHudTheme.TEXT,
            false,
        )

        if (set == null) {
            if (card.index == 0) drawLoadPageButton(graphics, card.page, x, cardY, width, mouseX, mouseY)
            return
        }

        if (renderArmorMannequin) {
            if (!empty) drawArmorMannequin(graphics, set, x + 10, cardY + 5, x + width - 10, cardY + cardHeight - 5, mouseX, mouseY)
            if (empty) drawCenteredLabel(graphics, "EMPTY", x, cardY, width)
        } else {
            val gridWidth = 4 * slotSize + 3 * 4
            val gridX = x + (width - gridWidth) / 2
            val gridY = cardY + (cardHeight - slotSize) / 2
            set.items.forEachIndexed { index, stack ->
                drawItemSlot(graphics, stack, gridX + index * (slotSize + 4), gridY, mouseX, mouseY)
            }
            if (empty) drawCenteredLabel(graphics, "EMPTY", x, cardY, width)
        }
    }

    private fun drawLoadPageButton(
        graphics: GuiGraphicsExtractor,
        page: Int,
        x: Int,
        cardY: Int,
        width: Int,
        mouseX: Int,
        mouseY: Int,
    ) {
        val label = "LOAD PAGE $page"
        val buttonWidth = (font.width(label) + 12).coerceAtMost(width - 12)
        val buttonX = x + (width - buttonWidth) / 2
        val buttonY = cardY + (cardHeight - 20) / 2
        val hovered = mouseX in buttonX until (buttonX + buttonWidth) && mouseY in buttonY until (buttonY + 20)
        SkyHudTheme.outlinedRoundedRect(
            graphics,
            buttonX,
            buttonY,
            buttonWidth,
            20,
            if (hovered) SkyHudTheme.PRIMARY_HOVER else SkyHudTheme.PRIMARY,
            SkyHudTheme.PRIMARY,
        )
        graphics.text(font, label, buttonX + (buttonWidth - font.width(label)) / 2, buttonY + 6, SkyHudTheme.TEXT, false)
    }

    private fun drawCenteredLabel(graphics: GuiGraphicsExtractor, label: String, x: Int, y: Int, width: Int) {
        graphics.text(font, label, x + (width - font.width(label)) / 2, y + cardHeight / 2, SkyHudTheme.TEXT_MUTED, false)
    }

    private fun drawItemSlot(
        graphics: GuiGraphicsExtractor,
        stack: ItemStack,
        x: Int,
        y: Int,
        mouseX: Int,
        mouseY: Int,
    ) {
        val hovered = mouseX in x until (x + slotSize) && mouseY in y until (y + slotSize)
        graphics.fill(
            x,
            y,
            x + slotSize,
            y + slotSize,
            when {
                hovered -> SkyHudTheme.SLOT_HOVER
                stack.isEmpty -> SkyHudTheme.SLOT
                else -> SkyHudTheme.SLOT_FILLED
            },
        )
        if (stack.isEmpty) return
        graphics.item(stack, x + 2, y + 2)
        graphics.itemDecorations(font, stack, x + 2, y + 2)
        if (hovered) graphics.setTooltipForNextFrame(font, stack, mouseX, mouseY)
    }

    private fun drawArmorMannequin(
        graphics: GuiGraphicsExtractor,
        set: CachedSetSlot,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        mouseX: Int,
        mouseY: Int,
    ) {
        val level = minecraft.level ?: return
        val mannequin = mannequins.getOrPut(set.id) {
            SetMannequin(level, minecraft.playerSkinRenderCache(), minecraft.player?.skin ?: ClientMannequin.DEFAULT_SKIN)
                .also { it.id = -200_000 - set.id }
        }
        listOf(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET).forEachIndexed { index, slot ->
            mannequin.setItemSlot(slot, set.items.getOrNull(index) ?: ItemStack.EMPTY)
        }
        mannequin.tickCount = minecraft.player?.tickCount ?: 0
        val scale = ((right - left) * 0.66).toInt().coerceIn(30, 46)
        graphics.enableScissor(left, top, right, bottom)
        InventoryScreen.extractEntityInInventoryFollowsMouse(
            graphics,
            left,
            top,
            right,
            bottom,
            scale,
            0f,
            mouseX.toFloat(),
            mouseY.toFloat(),
            mannequin,
        )
        graphics.disableScissor()
        if (mouseX !in left until right || mouseY !in top until bottom) return
        val armorIndex = ((mouseY - top) * 4 / (bottom - top).coerceAtLeast(1)).coerceIn(0, 3)
        set.items.getOrNull(armorIndex)?.takeUnless(ItemStack::isEmpty)?.let {
            graphics.setTooltipForNextFrame(font, it, mouseX, mouseY)
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
            if (mouseX in (x - 3)..(x + 6) && mouseY in thumbY..(thumbY + thumbHeight)) SkyHudTheme.PRIMARY_HOVER else SkyHudTheme.PRIMARY,
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
        if (click.button() != 0) return false
        val mouseX = click.x.toInt()
        val mouseY = click.y.toInt()
        val panelX = panelX()
        val panelY = panelY()
        if (mouseX in headerEditX(panelX) until (headerEditX(panelX) + 33) && mouseY in (panelY + 4) until (panelY + 20)) {
            editOriginal()
            return true
        }

        val viewportTop = panelY + headerHeight + 6
        val viewportBottom = panelY + panelHeight() - 7
        val scrollbarX = panelX + panelWidth() - 8
        if (mouseX in (scrollbarX - 4)..(scrollbarX + 7) && mouseY in viewportTop..viewportBottom) {
            draggingScrollbar = true
            updateScrollFromMouse(mouseY, viewportTop, viewportBottom)
            return true
        }

        val bounds = setBounds.firstOrNull {
            mouseX in it.x until (it.x + it.width) && mouseY in it.y until (it.y + it.height)
        } ?: return false
        val set = bounds.card.set
        if (set == null) {
            requestAction(bounds.card.page, null)
        } else if (set.selectable) {
            if (set.page == currentPage) clickBackingSlot(36 + set.index) else requestAction(set.page, set.index)
        }
        return true
    }

    override fun mouseDragged(click: MouseButtonEvent, dragX: Double, dragY: Double): Boolean {
        if (draggingScrollbar) {
            updateScrollFromMouse(click.y.toInt(), panelY() + headerHeight + 6, panelY() + panelHeight() - 7)
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
        scroll = (((mouseY - top).toDouble() / (bottom - top)).coerceIn(0.0, 1.0) * maxScroll)
    }

    private fun clickBackingSlot(slot: Int) {
        val menu = backingMenu ?: return
        val player = minecraft.player ?: return
        if (player.containerMenu !== menu || slot !in 0 until 54) return
        minecraft.gameMode?.handleContainerInput(menu.containerId, slot, 0, ContainerInput.PICKUP, player)
    }

    private fun setMatchesSearch(card: SetCard): Boolean {
        if (searchText.isBlank()) return true
        val terms = searchText.trim().split(Regex("\\s+"))
        return terms.all { term ->
            "$setLabel ${card.id}".contains(term, ignoreCase = true) ||
                card.set?.items?.any { !it.isEmpty && it.hoverName.string.contains(term, ignoreCase = true) } == true
        }
    }

    private fun drawOutline(graphics: GuiGraphicsExtractor, x: Int, y: Int, width: Int, height: Int, color: Int) {
        graphics.fill(x, y, x + width, y + 1, color)
        graphics.fill(x, y + height - 1, x + width, y + height, color)
        graphics.fill(x, y, x + 1, y + height, color)
        graphics.fill(x + width - 1, y, x + width, y + height, color)
    }

    private fun panelWidth(): Int = (width - 20).coerceAtMost(panelMaxWidth).coerceAtLeast(1)

    private fun panelHeight(): Int = (height - 20).coerceAtMost(panelMaxHeight).coerceAtLeast(1)

    private fun panelX(): Int = (width - panelWidth()) / 2

    private fun panelY(): Int = (height - panelHeight()) / 2

    private fun searchX(panelX: Int, panelWidth: Int, searchWidth: Int): Int =
        panelX + panelWidth - searchWidth - 11

    private fun headerEditX(panelX: Int): Int =
        panelX + 8 + font.width(heading) + 7

    override fun onClose() {
        closed()
        backingMenu = null
        mannequins.clear()
        minecraft.player?.closeContainer()
        super.onClose()
    }

    override fun isPauseScreen(): Boolean = false
}

private class SetMannequin(
    level: Level,
    skinRenderCache: PlayerSkinRenderCache,
    private val displaySkin: PlayerSkin,
) : ClientMannequin(level, skinRenderCache) {
    override fun isSpectator(): Boolean = false

    override fun shouldShowName(): Boolean = false

    override fun getSkin(): PlayerSkin = displaySkin
}
