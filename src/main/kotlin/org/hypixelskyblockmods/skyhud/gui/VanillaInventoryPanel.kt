package org.hypixelskyblockmods.skyhud.gui

import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier

/** The player inventory section of vanilla's chest screen, at its original size. */
internal object VanillaInventoryPanel {
    const val WIDTH = 176
    const val HEIGHT = 96
    const val SLOT_SIZE = 18
    const val SIDE_PADDING = 7
    const val MAIN_TOP = 13
    const val HOTBAR_GAP = 4

    private val texture = Identifier.withDefaultNamespace("textures/gui/container/generic_54.png")

    fun draw(graphics: GuiGraphicsExtractor, font: Font, x: Int, y: Int) {
        graphics.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, 0f, 126f, WIDTH, HEIGHT, 256, 256)
        graphics.text(font, Component.translatable("container.inventory"), x + 8, y + 3, 0xFF404040.toInt(), false)
    }
}
