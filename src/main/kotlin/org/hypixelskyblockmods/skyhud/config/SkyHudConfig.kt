package org.hypixelskyblockmods.skyhud.config

import com.google.gson.annotations.Expose
import io.github.notenoughupdates.moulconfig.ChromaColour
import io.github.notenoughupdates.moulconfig.Config
import io.github.notenoughupdates.moulconfig.annotations.Accordion
import io.github.notenoughupdates.moulconfig.annotations.Category
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorColour
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorDropdown
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption
import io.github.notenoughupdates.moulconfig.common.IMinecraft
import io.github.notenoughupdates.moulconfig.common.text.StructuredText

class SkyHudConfig : Config() {
    @field:Category(
        name = "HUDS",
        desc = "Configure SkyHUD's interface overhauls.",
    )
    @field:Expose
    @JvmField
    var huds = HudConfig()

    @field:Category(
        name = "Theme",
        desc = "Choose the SkyHUD appearance and accent color.",
    )
    @field:Expose
    @JvmField
    var theme = ThemeConfig()

    override fun getTitle(): StructuredText =
        IMinecraft.INSTANCE.createLiteral("SkyHUD Settings")
}

class ThemeConfig {
    @field:Expose
    @field:ConfigOption(
        name = "Preset",
        desc = "Original is fully dark. Transparent keeps the panels readable while showing more of the world.",
    )
    @field:ConfigEditorDropdown(values = ["Original", "Transparent"])
    @JvmField
    var preset: Int = 0

    @field:Expose
    @field:ConfigOption(
        name = "Main Color",
        desc = "Pick the accent used for outlines, buttons, and scrollbars.",
    )
    @field:ConfigEditorColour
    @JvmField
    var mainColor: ChromaColour = ChromaColour.fromStaticRGB(0x1E, 0x3A, 0x69, 0xFF)
}

class HudConfig {
    @field:ConfigOption(
        name = "Ender Chest",
        desc = "Configure SkyHUD's modern Ender Chest overview.",
    )
    @field:Accordion
    @field:Expose
    @JvmField
    var enderChest = FeatureToggle()

    @field:ConfigOption(
        name = "Loadouts",
        desc = "Configure SkyHUD's modern Loadouts interface.",
    )
    @field:Accordion
    @field:Expose
    @JvmField
    var loadouts = FeatureToggle()

    @field:ConfigOption(
        name = "Wardrobe",
        desc = "Configure SkyHUD's modern Wardrobe interface.",
    )
    @field:Accordion
    @field:Expose
    @JvmField
    var wardrobe = FeatureToggle()

    @field:ConfigOption(
        name = "Equipment",
        desc = "Configure SkyHUD's modern Equipment Sets interface.",
    )
    @field:Accordion
    @field:Expose
    @JvmField
    var equipment = FeatureToggle()
}

class FeatureToggle(
    @field:Expose
    @field:ConfigOption(name = "Enabled", desc = "Enable this SkyHUD interface overhaul.")
    @field:ConfigEditorBoolean
    @JvmField
    var enabled: Boolean = true,
)
