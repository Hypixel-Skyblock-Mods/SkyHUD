package org.hypixelskyblockmods.skyhud.config

import io.github.notenoughupdates.moulconfig.common.RenderContext
import io.github.notenoughupdates.moulconfig.common.text.StructuredText
import io.github.notenoughupdates.moulconfig.gui.GuiOptionEditor
import io.github.notenoughupdates.moulconfig.processor.ProcessedOption
import org.hypixelskyblockmods.skyhud.update.SkyHudUpdateChecker

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FIELD)
annotation class ConfigVersionStatus

class VersionStatusEditor(option: ProcessedOption) : GuiOptionEditor(option) {
    override fun render(context: RenderContext, x: Int, y: Int, width: Int) {
        val status = SkyHudUpdateChecker.snapshot
        val font = context.minecraft.defaultFontRenderer

        context.drawDarkRect(x, y, width, height, true)
        context.drawStringCenteredScaledMaxWidth(
            StructuredText.of("Current version: ${status.currentVersion}"),
            font,
            x + width / 2F,
            y + 16F,
            true,
            width - 12,
            0xE0E0E0,
        )
        context.drawStringCenteredScaledMaxWidth(
            StructuredText.of(status.message),
            font,
            x + width / 2F,
            y + 36F,
            true,
            width - 12,
            status.color,
        )
    }

    override fun getHeight(): Int = 52

    override fun fulfillsSearch(word: String): Boolean =
        super.fulfillsSearch(word) || "version update latest github".contains(word)
}
