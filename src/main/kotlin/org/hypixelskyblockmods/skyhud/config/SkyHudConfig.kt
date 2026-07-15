package org.hypixelskyblockmods.skyhud.config

import com.google.gson.annotations.Expose
import io.github.notenoughupdates.moulconfig.Config
import io.github.notenoughupdates.moulconfig.annotations.Category
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption
import io.github.notenoughupdates.moulconfig.common.IMinecraft
import io.github.notenoughupdates.moulconfig.common.text.StructuredText

class SkyHudConfig : Config() {
    @field:Category(
        name = "Ender Chest",
        desc = "Configure SkyHUD's modern Ender Chest overview.",
    )
    @field:Expose
    @JvmField
    var enderChest = FeatureToggle(
        enabled = true,
        optionName = "Modern Ender Chest",
        optionDescription = "Replace Hypixel Ender Chest pages with the searchable all-pages overview.",
    )

    @field:Category(
        name = "Wardrobe",
        desc = "Configure SkyHUD's modern Wardrobe interface.",
    )
    @field:Expose
    @JvmField
    var wardrobe = FeatureToggle(
        enabled = true,
        optionName = "Modern Wardrobe",
        optionDescription = "Replace Hypixel's Wardrobe with clean outfit cards and search.",
    )

    override fun getTitle(): StructuredText =
        IMinecraft.INSTANCE.createLiteral("SkyHUD Settings")
}

class FeatureToggle(
    @field:Expose
    @field:ConfigOption(name = "Enabled", desc = "Enable this SkyHUD interface overhaul.")
    @field:ConfigEditorBoolean
    @JvmField
    var enabled: Boolean = true,
    @Transient
    var optionName: String = "Enabled",
    @Transient
    var optionDescription: String = "Enable this interface overhaul.",
)
