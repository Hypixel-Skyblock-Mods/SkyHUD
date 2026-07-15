package org.hypixelskyblockmods.skyhud.gui

import net.minecraft.client.gui.GuiGraphicsExtractor

object SkyHudTheme {
    const val BACKGROUND = 0xFF0D0D0D.toInt()
    const val SURFACE = 0xFF141414.toInt()
    const val SURFACE_RAISED = 0xFF1A1A1A.toInt()
    const val BORDER = 0xFF292929.toInt()
    const val PRIMARY = 0xFF1E3A69.toInt()
    const val PRIMARY_HOVER = 0xFF294F8E.toInt()
    const val TEXT = 0xFFF4F6FA.toInt()
    const val TEXT_MUTED = 0xFF9097A3.toInt()
    const val SLOT = 0xFF090909.toInt()
    const val SLOT_FILLED = 0xFF0B1221.toInt()
    const val SLOT_HOVER = 0xFF22334F.toInt()

    fun roundedRect(
        graphics: GuiGraphicsExtractor,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        color: Int,
    ) {
        if (width <= 0 || height <= 0) return
        graphics.fill(x + 1, y, x + width - 1, y + height, color)
        graphics.fill(x, y + 1, x + width, y + height - 1, color)
    }

    fun outlinedRoundedRect(
        graphics: GuiGraphicsExtractor,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        fill: Int = SURFACE,
        outline: Int = BORDER,
    ) {
        roundedRect(graphics, x, y, width, height, outline)
        roundedRect(graphics, x + 1, y + 1, width - 2, height - 2, fill)
    }
}
