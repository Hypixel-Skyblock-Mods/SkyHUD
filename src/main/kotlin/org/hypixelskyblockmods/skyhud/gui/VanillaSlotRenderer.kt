package org.hypixelskyblockmods.skyhud.gui

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.inventory.ChestMenu
import net.minecraft.world.item.ItemStack

/** Draws vanilla container slots within an overlay without taking over its input or menu lifecycle. */
class VanillaSlotRenderer(private val title: Component) {
    private var backingMenu: ChestMenu? = null
    private var screen: SlotScreen? = null

    fun bind(menu: ChestMenu) {
        if (backingMenu === menu) return
        backingMenu = menu
        screen = null
    }

    fun draw(
        graphics: GuiGraphicsExtractor,
        stack: ItemStack,
        itemX: Int,
        itemY: Int,
        hovered: Boolean,
        mouseX: Int,
        mouseY: Int,
        backingSlotIndex: Int? = null,
        drawBackground: Boolean = true,
    ) {
        val renderer = screen ?: run {
            val menu = backingMenu ?: return
            val inventory = Minecraft.getInstance().player?.inventory ?: return
            SlotScreen(menu, inventory, title).also { screen = it }
        }
        renderer.draw(graphics, stack, itemX, itemY, hovered, mouseX, mouseY, backingSlotIndex, drawBackground)
    }

    private class SlotScreen(menu: ChestMenu, inventory: Inventory, title: Component) :
        AbstractContainerScreen<ChestMenu>(menu, inventory, title) {
        private val slotSource = VanillaSlotSource()

        fun draw(
            graphics: GuiGraphicsExtractor,
            stack: ItemStack,
            itemX: Int,
            itemY: Int,
            hovered: Boolean,
            mouseX: Int,
            mouseY: Int,
            backingSlotIndex: Int?,
            drawBackground: Boolean,
        ) {
            if (drawBackground) {
                graphics.blit(
                    RenderPipelines.GUI_TEXTURED, CONTAINER_TEXTURE,
                    itemX - 1, itemY - 1, 7f, 17f, 18, 18, 256, 256,
                )
                // A resource pack may draw only the outer border of the shared chest
                // grid. Complete isolated cells with its outer right/bottom edges.
                graphics.blit(
                    RenderPipelines.GUI_TEXTURED, CONTAINER_TEXTURE,
                    itemX + 16, itemY, 168f, 18f, 1, 16, 256, 256,
                )
                graphics.blit(
                    RenderPipelines.GUI_TEXTURED, CONTAINER_TEXTURE,
                    itemX, itemY + 16, 8f, 124f, 16, 1, 256, 256,
                )
                graphics.blit(
                    RenderPipelines.GUI_TEXTURED, CONTAINER_TEXTURE,
                    itemX + 16, itemY + 16, 168f, 124f, 1, 1, 256, 256,
                )
            }

            // Keep the real slot's identity and coordinates for vanilla/mod hooks. Cached
            // items use an isolated preview container that never receives input.
            val backingSlot = backingSlotIndex?.takeIf { it in menu.slots.indices }?.let(menu::getSlot)
            val slot = slotSource.resolve(stack, backingSlot)
            val offsetX = itemX - slot.x
            val offsetY = itemY - slot.y
            graphics.pose().pushMatrix()
            try {
                graphics.pose().translate(offsetX.toFloat(), offsetY.toFloat())
                if (hovered && slot.isHighlightable) {
                    graphics.blitSprite(RenderPipelines.GUI_TEXTURED, HIGHLIGHT_BACK, slot.x - 4, slot.y - 4, 24, 24)
                }
                // Calling the actual vanilla method also runs Skyblocker's slot mixins,
                // including backgrounds, item decorations, and slot text.
                extractSlot(graphics, slot, mouseX - offsetX, mouseY - offsetY)
                if (hovered && slot.isHighlightable) {
                    graphics.blitSprite(RenderPipelines.GUI_TEXTURED, HIGHLIGHT_FRONT, slot.x - 4, slot.y - 4, 24, 24)
                }
            } finally {
                graphics.pose().popMatrix()
            }
        }
    }

    private companion object {
        val CONTAINER_TEXTURE = Identifier.withDefaultNamespace("textures/gui/container/generic_54.png")
        val HIGHLIGHT_BACK = Identifier.withDefaultNamespace("container/slot_highlight_back")
        val HIGHLIGHT_FRONT = Identifier.withDefaultNamespace("container/slot_highlight_front")
    }
}
