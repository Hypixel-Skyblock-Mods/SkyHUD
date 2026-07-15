package org.hypixelskyblockmods.skyhud.gui

import net.minecraft.client.gui.GuiGraphicsExtractor
import org.hypixelskyblockmods.skyhud.config.SkyHudConfigManager

object SkyHudTheme {
    private val transparent: Boolean
        get() = SkyHudConfigManager.config.theme.preset == 1

    val SCREEN_DIM: Int
        get() = if (transparent) 0x30000000 else 0x70000000
    val PANEL: Int
        get() = if (transparent) 0x880D0D0D.toInt() else 0xF20D0D0D.toInt()
    val BACKGROUND: Int
        get() = if (transparent) 0x780D0D0D else 0xFF0D0D0D.toInt()
    val SURFACE: Int
        get() = if (transparent) 0xB8141414.toInt() else 0xFF141414.toInt()
    val SURFACE_RAISED: Int
        get() = if (transparent) 0xD01A1A1A.toInt() else 0xFF1A1A1A.toInt()
    val EMPTY_SURFACE: Int
        get() = if (transparent) 0xA0101010.toInt() else 0xFF101010.toInt()
    val BORDER: Int
        get() = if (transparent) 0xD0292929.toInt() else 0xFF292929.toInt()
    val PRIMARY: Int
        get() = runCatching {
            0xFF000000.toInt() or (SkyHudConfigManager.config.theme.mainColor.getEffectiveColourRGB() and 0x00FFFFFF)
        }.getOrDefault(0xFF1E3A69.toInt())
    val PRIMARY_HOVER: Int
        get() = brighten(PRIMARY, 1.3f)
    const val TEXT = 0xFFF4F6FA.toInt()
    const val TEXT_MUTED = 0xFF9097A3.toInt()
    val SLOT: Int
        get() = if (transparent) 0xB8090909.toInt() else 0xFF090909.toInt()
    val SLOT_FILLED: Int
        get() = if (transparent) withAlpha(mix(0xFF0B0B0B.toInt(), PRIMARY, 0.22f), 0xC8) else mix(0xFF0B0B0B.toInt(), PRIMARY, 0.22f)
    val SLOT_HOVER: Int
        get() = if (transparent) withAlpha(mix(0xFF202020.toInt(), PRIMARY, 0.45f), 0xE0) else mix(0xFF202020.toInt(), PRIMARY, 0.45f)
    val SCROLLBAR_TRACK: Int
        get() = if (transparent) 0x80202020.toInt() else 0xFF202020.toInt()

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

    private fun brighten(color: Int, factor: Float): Int = withAlpha(
        ((component(color, 16) * factor).toInt().coerceAtMost(255) shl 16) or
            ((component(color, 8) * factor).toInt().coerceAtMost(255) shl 8) or
            (component(color, 0) * factor).toInt().coerceAtMost(255),
        0xFF,
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
