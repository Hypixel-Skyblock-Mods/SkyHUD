package org.hypixelskyblockmods.skyhud.config

import com.google.gson.annotations.Expose
import io.github.notenoughupdates.moulconfig.ChromaColour
import io.github.notenoughupdates.moulconfig.Config
import io.github.notenoughupdates.moulconfig.annotations.Accordion
import io.github.notenoughupdates.moulconfig.annotations.Category
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorButton
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorColour
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorDropdown
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption
import io.github.notenoughupdates.moulconfig.common.IMinecraft
import io.github.notenoughupdates.moulconfig.common.text.StructuredText
import org.hypixelskyblockmods.skyhud.update.SkyHudUpdateChecker

class SkyHudConfig : Config() {
    @field:Category(
        name = "Dashboard",
        desc = "SkyHUD version and update status.",
    )
    @field:Expose
    @JvmField
    var dashboard = DashboardConfig()

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

class DashboardConfig {
    @field:ConfigOption(
        name = "Version",
        desc = "Shows the installed SkyHUD version and compares it with the latest GitHub release.",
    )
    @field:ConfigVersionStatus
    @JvmField
    @Transient
    var versionStatus: String = ""

    @field:ConfigOption(
        name = "Update Check",
        desc = "Check GitHub Releases again for a newer stable SkyHUD version.",
    )
    @field:ConfigEditorButton(buttonText = "Check")
    @JvmField
    @Transient
    var checkForUpdates = Runnable { SkyHudUpdateChecker.refresh(force = true) }
}

class ThemeConfig {
    @field:Expose
    @field:ConfigOption(
        name = "Preset",
        desc = "Original uses the opaque navy theme. Transparent uses neutral translucent panels that keep the world visible.",
    )
    @field:ConfigEditorDropdown(values = ["Original", "Transparent"])
    @JvmField
    var preset: Int = 1

    @field:Expose
    @field:ConfigOption(
        name = "Main Color",
        desc = "Pick the accent used for outlines, buttons, and scrollbars.",
    )
    @field:ConfigEditorColour
    @JvmField
    var mainColor: ChromaColour = defaultMainColor()

    @field:ConfigOption(
        name = "Reset Main Color",
        desc = "Restore the main color to SkyHUD's default navy accent.",
    )
    @field:ConfigEditorButton(buttonText = "Reset")
    @JvmField
    @Transient
    var resetMainColor = Runnable { mainColor = defaultMainColor() }
}

private fun defaultMainColor(): ChromaColour =
    ChromaColour.fromStaticRGB(0x24, 0x31, 0xA0, 0xFF)

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
