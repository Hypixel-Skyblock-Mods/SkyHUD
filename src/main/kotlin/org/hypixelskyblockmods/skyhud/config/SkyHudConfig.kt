package org.hypixelskyblockmods.skyhud.config

import com.google.gson.annotations.Expose
import io.github.notenoughupdates.moulconfig.Config
import io.github.notenoughupdates.moulconfig.annotations.Accordion
import io.github.notenoughupdates.moulconfig.annotations.Category
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean
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

    override fun getTitle(): StructuredText =
        IMinecraft.INSTANCE.createLiteral("SkyHUD Settings")
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
