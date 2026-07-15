package org.hypixelskyblockmods.skyhud.gui

import net.minecraft.client.gui.GuiGraphicsExtractor
import org.hypixelskyblockmods.skyhud.config.SkyHudConfigManager

object SkyHudTheme {
    val transparent: Boolean
        get() = SkyHudConfigManager.config.theme.preset == 1

    val SCREEN_DIM: Int
        get() = if (transparent) 0x00000000 else 0x70000000
    val PANEL: Int
        get() = if (transparent) 0x98060606.toInt() else mix(0xFF0D0D0D.toInt(), PRIMARY, 0.20f)
    val BACKGROUND: Int
        get() = if (transparent) 0xA0060606.toInt() else mix(0xFF0D0D0D.toInt(), PRIMARY, 0.18f)
    val SURFACE: Int
        get() = if (transparent) 0x88101010.toInt() else mix(0xFF141414.toInt(), PRIMARY, 0.18f)
    val SURFACE_RAISED: Int
        get() = if (transparent) 0xA0181818.toInt() else mix(0xFF1A1A1A.toInt(), PRIMARY, 0.22f)
    val EMPTY_SURFACE: Int
        get() = if (transparent) 0x70101010 else mix(0xFF101010.toInt(), PRIMARY, 0.14f)
    val BORDER: Int
        get() = if (transparent) 0x80383838.toInt() else mix(0xFF292929.toInt(), PRIMARY, 0.35f)
    val PRIMARY: Int
        get() = runCatching {
            val color = SkyHudConfigManager.config.theme.mainColor.getEffectiveColourRGB() and 0x00FFFFFF
            withAlpha(color, if (transparent) 0xB0 else 0xFF)
        }.getOrDefault(0xFF1E3A69.toInt())
    val PRIMARY_HOVER: Int
        get() = brighten(PRIMARY, 1.3f)
    const val TEXT = 0xFFF4F6FA.toInt()
    const val TEXT_MUTED = 0xFF9097A3.toInt()
    val SLOT: Int
        get() = if (transparent) 0x70000000 else mix(0xFF090909.toInt(), PRIMARY, 0.12f)
    val SLOT_FILLED: Int
        get() = if (transparent) 0x90080808.toInt() else mix(0xFF0B0B0B.toInt(), PRIMARY, 0.28f)
    val SLOT_HOVER: Int
        get() = if (transparent) 0xB0282828.toInt() else mix(0xFF202020.toInt(), PRIMARY, 0.50f)
    val SCROLLBAR_TRACK: Int
        get() = if (transparent) 0x50000000 else mix(0xFF202020.toInt(), PRIMARY, 0.16f)

    fun roundedRect(
        graphics: GuiGraphicsExtractor,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        color: Int,
    ) {
        if (width <= 0 || height <= 0) return
        if (width <= 2 || height <= 2) {
            graphics.fill(x, y, x + width, y + height, color)
            return
        }
        graphics.fill(x + 1, y, x + width - 1, y + 1, color)
        graphics.fill(x, y + 1, x + width, y + height - 1, color)
        graphics.fill(x + 1, y + height - 1, x + width - 1, y + height, color)
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

    private fun brighten(color: Int, factor: Float): Int = withAlpha(
        ((component(color, 16) * factor).toInt().coerceAtMost(255) shl 16) or
            ((component(color, 8) * factor).toInt().coerceAtMost(255) shl 8) or
            (component(color, 0) * factor).toInt().coerceAtMost(255),
        (color ushr 24) and 0xFF,
    )

    private fun mix(first: Int, second: Int, amount: Float): Int = withAlpha(
        (mixComponent(first, second, 16, amount) shl 16) or
            (mixComponent(first, second, 8, amount) shl 8) or
            mixComponent(first, second, 0, amount),
        0xFF,
    )

    private fun mixComponent(first: Int, second: Int, shift: Int, amount: Float): Int =
        (component(first, shift) * (1f - amount) + component(second, shift) * amount).toInt().coerceIn(0, 255)

    private fun component(color: Int, shift: Int): Int = (color ushr shift) and 0xFF

    private fun withAlpha(color: Int, alpha: Int): Int = (alpha shl 24) or (color and 0x00FFFFFF)
}
