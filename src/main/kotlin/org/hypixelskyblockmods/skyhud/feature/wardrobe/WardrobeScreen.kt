package org.hypixelskyblockmods.skyhud.feature.wardrobe

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
import org.hypixelskyblockmods.skyhud.util.VanillaItemIds

class WardrobeScreen(
    private val closed: () -> Unit,
) : Screen(Component.literal("SkyHUD Wardrobe")) {
    private data class OutfitBounds(
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
    private var outfitBounds = emptyList<OutfitBounds>()
    private var draggingScrollbar = false

    private val headerHeight = 54
    private val footerHeight = 42
    private val cardWidth = 126
    private val cardHeight = 105
    private val cardGap = 10
    private val slotSize = 18

    fun bind(target: WardrobeTarget) {
        currentPage = target.page
        totalPages = target.totalPages
        backingMenu = target.menu
        WardrobeRepository.remember(target.page, target.menu)
    }

    fun refreshBackingMenu(menu: AbstractContainerMenu?) {
        val chestMenu = menu as? ChestMenu ?: return
        if (chestMenu !== backingMenu) return
        WardrobeRepository.remember(currentPage, chestMenu)
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
            Component.literal("Search Wardrobe"),
        )
        search.value = searchText
        search.setHint(Component.literal("Search armor..."))
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
        graphics.text(font, "WARDROBE", 24, 17, SkyHudTheme.TEXT, false)
        graphics.text(
            font,
            "Outfit overview  •  Page $currentPage of $totalPages",
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

        drawOutfits(graphics, mouseX, mouseY)
        drawScrollbar(graphics, mouseX, mouseY)
        drawFooter(graphics, mouseX, mouseY)
        super.extractRenderState(graphics, mouseX, mouseY, delta)
    }

    private fun drawOutfits(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
        val page = WardrobeRepository.page(currentPage) ?: return
        val visible = page.slots.filter(::outfitMatchesSearch)
        val viewportTop = headerHeight + 12
        val viewportBottom = height - footerHeight - 8
        val viewportHeight = (viewportBottom - viewportTop).coerceAtLeast(1)
        val columns = ((width - 54) / (cardWidth + cardGap)).coerceIn(1, 6)
        val contentWidth = columns * cardWidth + (columns - 1) * cardGap
        val startX = (width - contentWidth) / 2 - 4
        val rows = ceil(visible.size / columns.toDouble()).toInt()
        val contentHeight = rows * cardHeight + (rows - 1).coerceAtLeast(0) * cardGap
        maxScroll = (contentHeight - viewportHeight).coerceAtLeast(0).toDouble()
        scroll = scroll.coerceIn(0.0, maxScroll)

        graphics.enableScissor(0, viewportTop, width, viewportBottom)
        val bounds = ArrayList<OutfitBounds>(visible.size)
        visible.forEachIndexed { position, outfit ->
            val column = position % columns
            val row = position / columns
            val x = startX + column * (cardWidth + cardGap)
            val y = viewportTop + row * (cardHeight + cardGap) - scroll.toInt()
            val clickable = drawOutfitCard(graphics, outfit, x, y, mouseX, mouseY)
            bounds += OutfitBounds(outfit.index, x, y, cardWidth, cardHeight, clickable)
        }
        graphics.disableScissor()
        outfitBounds = bounds

        if (visible.isEmpty()) {
            val message = "No wardrobe slots match ‘$searchText’"
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

    private fun drawOutfitCard(
        graphics: GuiGraphicsExtractor,
        outfit: CachedWardrobeSlot,
        x: Int,
        y: Int,
        mouseX: Int,
        mouseY: Int,
    ): Boolean {
        val selected = VanillaItemIds.isItem(outfit.selector, "lime_dye")
        val selectable = selected || VanillaItemIds.isItem(outfit.selector, "pink_dye")
        val hovered = mouseX in x until (x + cardWidth) && mouseY in y until (y + cardHeight)
        SkyHudTheme.outlinedRoundedRect(
            graphics,
            x,
            y,
            cardWidth,
            cardHeight,
            if (hovered) SkyHudTheme.SURFACE_RAISED else SkyHudTheme.SURFACE,
            if (selected) SkyHudTheme.PRIMARY_HOVER else SkyHudTheme.BORDER,
        )
        if (selected) graphics.fill(x + 1, y + 1, x + 4, y + cardHeight - 1, SkyHudTheme.PRIMARY)

        val wardrobeNumber = (currentPage - 1) * 9 + outfit.index + 1
        graphics.text(font, "OUTFIT $wardrobeNumber", x + 9, y + 9, SkyHudTheme.TEXT, false)
        graphics.text(
            font,
            if (selected) "ACTIVE" else if (selectable) "READY" else "LOCKED",
            x + cardWidth - 44,
            y + 9,
            if (selected) 0xFF8EB5F4.toInt() else SkyHudTheme.TEXT_MUTED,
            false,
        )

        outfit.armor.forEachIndexed { index, stack ->
            val slotX = x + 26 + index * slotSize
            val slotY = y + 30
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
                if (slotHovered) graphics.setTooltipForNextFrame(font, stack, mouseX, mouseY)
            }
        }

        val buttonX = x + 9
        val buttonY = y + 62
        val buttonWidth = cardWidth - 18
        SkyHudTheme.outlinedRoundedRect(
            graphics,
            buttonX,
            buttonY,
            buttonWidth,
            27,
            when {
                !selectable -> 0xFF202020.toInt()
                hovered -> SkyHudTheme.PRIMARY_HOVER
                else -> SkyHudTheme.PRIMARY
            },
            if (selectable) SkyHudTheme.PRIMARY else SkyHudTheme.BORDER,
        )
        val label = when {
            selected -> "UNEQUIP"
            selectable -> "EQUIP OUTFIT"
            else -> "UNAVAILABLE"
        }
        graphics.text(
            font,
            label,
            buttonX + (buttonWidth - font.width(label)) / 2,
            buttonY + 10,
            if (selectable) SkyHudTheme.TEXT else SkyHudTheme.TEXT_MUTED,
            false,
        )
        return selectable
    }

    private fun drawFooter(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
        val y = height - footerHeight
        graphics.fill(0, y, width, height, 0xFF101010.toInt())

        val previousEnabled = currentPage > 1 && backingMenu?.getSlot(45)?.item?.let {
            VanillaItemIds.isItem(it, "arrow")
        } == true
        val nextEnabled = currentPage < totalPages && backingMenu?.getSlot(53)?.item?.let {
            VanillaItemIds.isItem(it, "arrow")
        } == true
        drawPageButton(graphics, width / 2 - 100, y + 8, 84, "PREVIOUS", previousEnabled, mouseX, mouseY)
        drawPageButton(graphics, width / 2 + 16, y + 8, 84, "NEXT", nextEnabled, mouseX, mouseY)
        val pageLabel = "$currentPage / $totalPages"
        graphics.text(font, pageLabel, width / 2 - font.width(pageLabel) / 2, y + 17, SkyHudTheme.TEXT_MUTED, false)
    }

    private fun drawPageButton(
        graphics: GuiGraphicsExtractor,
        x: Int,
        y: Int,
        width: Int,
        label: String,
        enabled: Boolean,
        mouseX: Int,
        mouseY: Int,
    ) {
        val hovered = enabled && mouseX in x until (x + width) && mouseY in y until (y + 25)
        SkyHudTheme.outlinedRoundedRect(
            graphics,
            x,
            y,
            width,
            25,
            when {
                hovered -> SkyHudTheme.PRIMARY_HOVER
                enabled -> SkyHudTheme.PRIMARY
                else -> 0xFF202020.toInt()
            },
            if (enabled) SkyHudTheme.PRIMARY else SkyHudTheme.BORDER,
        )
        graphics.text(
            font,
            label,
            x + (width - font.width(label)) / 2,
            y + 9,
            if (enabled) SkyHudTheme.TEXT else SkyHudTheme.TEXT_MUTED,
            false,
        )
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
        if (click.button() != 0) return false
        val mouseX = click.x.toInt()
        val mouseY = click.y.toInt()

        if (mouseX in (width - 21)..(width - 6) && mouseY in (headerHeight + 8)..(height - footerHeight)) {
            draggingScrollbar = true
            updateScrollFromMouse(mouseY)
            return true
        }

        val footerY = height - footerHeight + 8
        if (mouseY in footerY until (footerY + 25)) {
            if (mouseX in (width / 2 - 100) until (width / 2 - 16)) {
                clickNavigationSlot(45)
                return true
            }
            if (mouseX in (width / 2 + 16) until (width / 2 + 100)) {
                clickNavigationSlot(53)
                return true
            }
        }

        val outfit = outfitBounds.firstOrNull {
            mouseX in (it.x + 9) until (it.x + it.width - 9) &&
                mouseY in (it.y + 62) until (it.y + 89)
        } ?: return false
        if (outfit.clickable) clickBackingSlot(36 + outfit.index)
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

    private fun clickNavigationSlot(slot: Int) {
        val menu = backingMenu ?: return
        if (!VanillaItemIds.isItem(menu.getSlot(slot).item, "arrow")) return
        clickBackingSlot(slot)
    }

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

    private fun outfitMatchesSearch(outfit: CachedWardrobeSlot): Boolean {
        if (searchText.isBlank()) return true
        val terms = searchText.trim().split(Regex("\\s+"))
        return outfit.armor.any { stack ->
            !stack.isEmpty && terms.all { stack.hoverName.string.contains(it, ignoreCase = true) }
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
