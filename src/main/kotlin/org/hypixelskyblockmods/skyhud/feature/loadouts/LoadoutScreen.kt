package org.hypixelskyblockmods.skyhud.feature.loadouts

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
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.ChestMenu
import net.minecraft.world.inventory.ContainerInput
import net.minecraft.world.item.ItemStack
import net.minecraft.world.entity.player.PlayerSkin
import net.minecraft.world.level.Level
import org.hypixelskyblockmods.skyhud.gui.SkyHudTheme
import org.hypixelskyblockmods.skyhud.util.VanillaItemIds

class LoadoutScreen(
    private val closed: () -> Unit,
) : Screen(Component.literal("SkyHUD Loadouts")) {
    private data class LoadoutBounds(
        val inventorySlot: Int,
        val x: Int,
        val y: Int,
        val width: Int,
        val height: Int,
        val clickable: Boolean,
        val editX: Int,
        val editY: Int,
        val editSize: Int,
    )

    private var currentPage = 1
    private var totalPages = 1
    private var backingMenu: ChestMenu? = null
    private var searchText = ""
    private var scroll = 0.0
    private var maxScroll = 0.0
    private var loadoutBounds = emptyList<LoadoutBounds>()
    private var draggingScrollbar = false
    private val mannequins = mutableMapOf<Int, LoadoutMannequin>()

    private val headerHeight = 54
    private val pageColumns = 5
    private val maximumPageWidth = 154
    private val minimumPageWidth = 112
    private val pageGapHorizontal = 12
    private val pageGapVertical = 24
    private val pageTitleHeight = 18
    private val cardHeight = 142
    private val slotSize = 22
    private val itemSize = 16
    private val editSize = 20

    fun bind(target: LoadoutTarget) {
        currentPage = target.page
        totalPages = target.totalPages
        backingMenu = target.menu
        LoadoutRepository.remember(target.page, target.menu)
        scroll = 0.0
    }

    fun refreshBackingMenu(menu: AbstractContainerMenu?) {
        val chestMenu = menu as? ChestMenu ?: return
        if (chestMenu !== backingMenu) return
        LoadoutRepository.remember(currentPage, chestMenu)
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
            Component.literal("Search Loadouts"),
        )
        search.value = searchText
        search.setHint(Component.literal("Search loadouts..."))
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
        graphics.text(font, "LOADOUTS", 24, 23, SkyHudTheme.TEXT, false)

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
        drawLoadouts(graphics, mouseX, mouseY)
        drawScrollbar(graphics, mouseX, mouseY)
        super.extractRenderState(graphics, mouseX, mouseY, delta)
    }

    private fun drawNavigation(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
        if (totalPages <= 1) return
        val previousEnabled = hasNavigationArrow(45) && currentPage > 1
        val nextEnabled = hasNavigationArrow(53) && currentPage < totalPages
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

    private fun drawLoadouts(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
        val page = LoadoutRepository.page(currentPage) ?: return
        val visible = page.loadouts.filter(::loadoutMatchesSearch)
        val viewportTop = headerHeight + 16
        val viewportBottom = height - 16
        val viewportHeight = (viewportBottom - viewportTop).coerceAtLeast(1)
        val pageWidth = loadoutWidth()
        val contentWidth = pageColumns * pageWidth + (pageColumns - 1) * pageGapHorizontal
        val startX = (width - contentWidth) / 2
        val pageHeight = pageTitleHeight + cardHeight
        val rows = ceil(visible.size / pageColumns.toDouble()).toInt()
        val contentHeight = rows * pageHeight + (rows - 1).coerceAtLeast(0) * pageGapVertical
        maxScroll = (contentHeight - viewportHeight).coerceAtLeast(0).toDouble()
        scroll = scroll.coerceIn(0.0, maxScroll)

        graphics.enableScissor(0, viewportTop, width, viewportBottom)
        val bounds = ArrayList<LoadoutBounds>(visible.size)
        visible.forEachIndexed { position, loadout ->
            val column = position % pageColumns
            val row = position / pageColumns
            val x = startX + column * (pageWidth + pageGapHorizontal)
            val y = viewportTop + row * (pageHeight + pageGapVertical) - scroll.toInt()
            drawLoadout(graphics, loadout, x, y, pageWidth, mouseX, mouseY)
            val cardY = y + pageTitleHeight
            bounds += LoadoutBounds(
                inventorySlot = loadout.inventorySlot,
                x = x,
                y = y,
                width = pageWidth,
                height = pageHeight,
                clickable = !loadout.locked,
                editX = x + pageWidth - editSize - 7,
                editY = cardY + 7,
                editSize = editSize,
            )
        }
        graphics.disableScissor()
        loadoutBounds = bounds

        if (visible.isEmpty()) {
            val message = "No loadouts match ‘$searchText’"
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

    private fun drawLoadout(
        graphics: GuiGraphicsExtractor,
        loadout: CachedLoadout,
        x: Int,
        y: Int,
        pageWidth: Int,
        mouseX: Int,
        mouseY: Int,
    ) {
        val cardY = y + pageTitleHeight
        val cardHovered = mouseX in x until (x + pageWidth) && mouseY in cardY until (cardY + cardHeight)
        SkyHudTheme.roundedRect(
            graphics,
            x,
            cardY,
            pageWidth,
            cardHeight,
            if (cardHovered && !loadout.locked) SkyHudTheme.SURFACE_RAISED else SkyHudTheme.SURFACE,
        )
        if (loadout.selected) {
            drawOutline(graphics, x - 2, cardY - 2, pageWidth + 4, cardHeight + 4, SkyHudTheme.PRIMARY_HOVER)
        }

        graphics.text(
            font,
            clipText(loadout.name, pageWidth),
            x,
            y + 2,
            if (loadout.locked) SkyHudTheme.TEXT_MUTED else SkyHudTheme.TEXT,
            false,
        )

        val petX = x + 8
        val petY = cardY + 8
        drawItemSlot(graphics, loadout.pet, petX, petY, mouseX, mouseY)

        val editX = x + pageWidth - editSize - 7
        val editY = cardY + 7
        drawEditButton(graphics, editX, editY, loadout.locked, mouseX, mouseY)

        val equipmentX = x + pageWidth - slotSize - 8
        val equipmentTop = cardY + 38
        loadout.equipment.forEachIndexed { index, stack ->
            drawItemSlot(graphics, stack, equipmentX, equipmentTop + index * 25, mouseX, mouseY)
        }

        val playerLeft = x + 31
        val playerRight = equipmentX - 3
        val playerTop = cardY + 27
        val playerBottom = cardY + cardHeight - 7
        if (playerRight > playerLeft) {
            drawArmorMannequin(
                graphics,
                loadout,
                playerLeft,
                playerTop,
                playerRight,
                playerBottom,
                mouseX,
                mouseY,
            )
        }
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
        val inset = (slotSize - itemSize) / 2
        graphics.item(stack, x + inset, y + inset)
        graphics.itemDecorations(font, stack, x + inset, y + inset)
        if (hovered) graphics.setTooltipForNextFrame(font, stack, mouseX, mouseY)
    }

    private fun drawEditButton(
        graphics: GuiGraphicsExtractor,
        x: Int,
        y: Int,
        locked: Boolean,
        mouseX: Int,
        mouseY: Int,
    ) {
        val hovered = !locked && mouseX in x until (x + editSize) && mouseY in y until (y + editSize)
        SkyHudTheme.roundedRect(
            graphics,
            x,
            y,
            editSize,
            editSize,
            when {
                hovered -> SkyHudTheme.PRIMARY_HOVER
                locked -> SkyHudTheme.SLOT
                else -> SkyHudTheme.PRIMARY
            },
        )
        val icon = "✎"
        graphics.text(
            font,
            icon,
            x + (editSize - font.width(icon)) / 2,
            y + 6,
            if (locked) SkyHudTheme.TEXT_MUTED else SkyHudTheme.TEXT,
            false,
        )
        if (hovered) graphics.setTooltipForNextFrame(Component.literal("Edit loadout"), mouseX, mouseY)
    }

    private fun drawArmorMannequin(
        graphics: GuiGraphicsExtractor,
        loadout: CachedLoadout,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        mouseX: Int,
        mouseY: Int,
    ) {
        val level = minecraft.level ?: return
        val mannequin = mannequins.getOrPut(loadout.id) {
            LoadoutMannequin(
                level,
                minecraft.playerSkinRenderCache(),
                minecraft.player?.skin ?: ClientMannequin.DEFAULT_SKIN,
            )
        }
        val armorSlots = listOf(
            EquipmentSlot.HEAD,
            EquipmentSlot.CHEST,
            EquipmentSlot.LEGS,
            EquipmentSlot.FEET,
        )
        armorSlots.forEachIndexed { index, slot ->
            mannequin.setItemSlot(slot, loadout.armor.getOrNull(index) ?: ItemStack.EMPTY)
        }
        mannequin.tickCount = minecraft.player?.tickCount ?: 0

        val entityScale = ((right - left) * 0.72).toInt().coerceIn(30, 44)
        graphics.enableScissor(left, top, right, bottom)
        InventoryScreen.extractEntityInInventoryFollowsMouse(
            graphics,
            left,
            top,
            right,
            bottom,
            entityScale,
            0f,
            mouseX.toFloat(),
            mouseY.toFloat(),
            mannequin,
        )
        graphics.disableScissor()

        if (mouseX !in left until right || mouseY !in top until bottom) return
        val sectionHeight = (bottom - top).coerceAtLeast(1)
        val armorIndex = ((mouseY - top) * 4 / sectionHeight).coerceIn(0, 3)
        val hoveredArmor = loadout.armor.getOrNull(armorIndex) ?: return
        if (!hoveredArmor.isEmpty) {
            graphics.setTooltipForNextFrame(font, hoveredArmor, mouseX, mouseY)
        }
    }

    private fun loadoutWidth(): Int =
        ((width - 48 - (pageColumns - 1) * pageGapHorizontal) / pageColumns)
            .coerceIn(minimumPageWidth, maximumPageWidth)

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
        val mouseX = click.x.toInt()
        val mouseY = click.y.toInt()

        if (click.button() == 0 &&
            mouseX in (width - 21)..(width - 6) &&
            mouseY in (headerHeight + 8)..(height - 8)
        ) {
            draggingScrollbar = true
            updateScrollFromMouse(mouseY)
            return true
        }
        if (click.button() == 0 && mouseY in 15 until 39) {
            if (mouseX in (width / 2 - 58) until (width / 2 - 30)) {
                clickNavigationSlot(45)
                return true
            }
            if (mouseX in (width / 2 + 30) until (width / 2 + 58)) {
                clickNavigationSlot(53)
                return true
            }
        }

        val loadout = loadoutBounds.firstOrNull {
            mouseX in it.x until (it.x + it.width) && mouseY in it.y until (it.y + it.height)
        } ?: return false
        if (!loadout.clickable) return true
        val editHovered = mouseX in loadout.editX until (loadout.editX + loadout.editSize) &&
            mouseY in loadout.editY until (loadout.editY + loadout.editSize)
        when {
            click.button() == 0 && editHovered -> clickBackingSlot(loadout.inventorySlot, 1)
            click.button() == 0 -> clickBackingSlot(loadout.inventorySlot, 0)
            click.button() == 1 -> clickBackingSlot(loadout.inventorySlot, 1)
            else -> return false
        }
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
        clickBackingSlot(slot, 0)
    }

    private fun hasNavigationArrow(slot: Int): Boolean =
        backingMenu?.getSlot(slot)?.item?.let { VanillaItemIds.isItem(it, "arrow") } == true

    private fun clickBackingSlot(slot: Int, button: Int) {
        val menu = backingMenu ?: return
        val player = minecraft.player ?: return
        if (player.containerMenu !== menu || slot !in 0 until 54) return
        minecraft.gameMode?.handleContainerInput(
            menu.containerId,
            slot,
            button,
            ContainerInput.PICKUP,
            player,
        )
    }

    private fun loadoutMatchesSearch(loadout: CachedLoadout): Boolean {
        if (searchText.isBlank()) return true
        val terms = searchText.trim().split(Regex("\\s+"))
        return terms.all { term ->
            loadout.name.contains(term, ignoreCase = true) ||
                loadout.items.any { !it.isEmpty && it.hoverName.string.contains(term, ignoreCase = true) }
        }
    }

    private fun clipText(text: String, maxWidth: Int): String {
        if (font.width(text) <= maxWidth) return text
        var clipped = text
        while (clipped.isNotEmpty() && font.width("$clipped...") > maxWidth) {
            clipped = clipped.dropLast(1)
        }
        return "$clipped..."
    }

    override fun onClose() {
        closed()
        backingMenu = null
        mannequins.clear()
        minecraft.player?.closeContainer()
        super.onClose()
    }

    override fun isPauseScreen(): Boolean = false
}

private class LoadoutMannequin(
    level: Level,
    skinRenderCache: PlayerSkinRenderCache,
    private val displaySkin: PlayerSkin,
) : ClientMannequin(level, skinRenderCache) {
    override fun isSpectator(): Boolean = false

    override fun shouldShowName(): Boolean = false

    override fun getSkin(): PlayerSkin = displaySkin
}
