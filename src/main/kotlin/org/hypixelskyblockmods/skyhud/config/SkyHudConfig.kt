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
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorKeybind
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption
import io.github.notenoughupdates.moulconfig.common.IMinecraft
import io.github.notenoughupdates.moulconfig.common.text.StructuredText
import com.mojang.blaze3d.platform.InputConstants
import org.hypixelskyblockmods.skyhud.feature.itemsearch.ItemSearchDataManager
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
        name = "Item Search",
        desc = "Find items across the current SkyBlock profile without moving them.",
    )
    @field:Accordion
    @field:Expose
    @JvmField
    var itemSearch = ItemSearchConfig()

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

class ItemSearchConfig {
    @field:Expose
    @field:ConfigOption(name = "Enabled", desc = "Enable SkyHUD's profile-aware item finder.")
    @field:ConfigEditorBoolean
    @JvmField
    var enabled: Boolean = true

    @field:Expose
    @field:ConfigOption(name = "Keybind", desc = "Open Item Search while on SkyBlock.")
    @field:ConfigEditorKeybind(defaultKey = InputConstants.KEY_O)
    @JvmField
    var keybind: Int = InputConstants.KEY_O

    @field:Expose
    @field:ConfigOption(name = "Preserve Last Search", desc = "Keep the query and category when reopening Item Search during this client session.")
    @field:ConfigEditorBoolean
    @JvmField
    var preserveLastSearch: Boolean = true

    @field:Expose
    @field:ConfigOption(name = "Match Lore", desc = "Include item lore in search matching.")
    @field:ConfigEditorBoolean
    @JvmField
    var matchLore: Boolean = true

    @field:Expose
    @field:ConfigOption(name = "Match SkyBlock IDs", desc = "Include internal SkyBlock item IDs in search matching.")
    @field:ConfigEditorBoolean
    @JvmField
    var matchIds: Boolean = true

    @field:Expose
    @field:ConfigOption(name = "Match Locations", desc = "Include source and location names in search matching.")
    @field:ConfigEditorBoolean
    @JvmField
    var matchLocations: Boolean = false

    @field:Expose
    @field:ConfigOption(name = "Stale Warnings", desc = "Mark cached observations older than 24 hours.")
    @field:ConfigEditorBoolean
    @JvmField
    var staleWarnings: Boolean = true

    @field:Expose
    @field:ConfigOption(name = "Warp to Island", desc = "Allow island-chest results to run /warp island before highlighting. No walking or chest opening is performed.")
    @field:ConfigEditorBoolean
    @JvmField
    var warpToIsland: Boolean = false

    @field:ConfigOption(name = "Sources", desc = "Choose which item sources are included in the index.")
    @field:Accordion
    @field:Expose
    @JvmField
    var sources = ItemSearchSourceConfig()

    @field:ConfigOption(name = "Clear Island Chests", desc = "Delete only SkyHUD's island-chest observations for the active SkyBlock profile.")
    @field:ConfigEditorButton(buttonText = "Clear")
    @JvmField
    @Transient
    var clearIslandChests = Runnable(ItemSearchDataManager::clearIslandChests)

    @field:ConfigOption(name = "Clear Current Profile Search Data", desc = "Delete SkyHUD-owned item-search observations for the active profile. SkyblockAPI caches are never changed.")
    @field:ConfigEditorButton(buttonText = "Clear")
    @JvmField
    @Transient
    var clearCurrentProfile = Runnable(ItemSearchDataManager::clearCurrentProfile)
}

class ItemSearchSourceConfig {
    @field:Expose @field:ConfigOption(name = "Inventory", desc = "Normal inventory and armor observations.") @field:ConfigEditorBoolean @JvmField var inventory = true
    @field:Expose @field:ConfigOption(name = "Equipped Equipment", desc = "Normal-realm equipped equipment from SkyblockAPI.") @field:ConfigEditorBoolean @JvmField var equippedEquipment = true
    @field:Expose @field:ConfigOption(name = "Storage", desc = "Ender Chest and backpack pages.") @field:ConfigEditorBoolean @JvmField var storage = true
    @field:Expose @field:ConfigOption(name = "Rift", desc = "Rift inventory, armor, equipment, and Storage.") @field:ConfigEditorBoolean @JvmField var rift = true
    @field:Expose @field:ConfigOption(name = "Loadouts", desc = "SkyHUD Loadout observations.") @field:ConfigEditorBoolean @JvmField var loadouts = true
    @field:Expose @field:ConfigOption(name = "Wardrobe", desc = "Armor Wardrobe observations.") @field:ConfigEditorBoolean @JvmField var wardrobe = true
    @field:Expose @field:ConfigOption(name = "Equipment Wardrobe", desc = "Equipment Wardrobe observations.") @field:ConfigEditorBoolean @JvmField var equipmentWardrobe = true
    @field:Expose @field:ConfigOption(name = "Accessory Bag", desc = "Cached Accessory Bag contents.") @field:ConfigEditorBoolean @JvmField var accessoryBag = true
    @field:Expose @field:ConfigOption(name = "Sacks", desc = "Cached aggregate Sack contents.") @field:ConfigEditorBoolean @JvmField var sacks = true
    @field:Expose @field:ConfigOption(name = "Sack of Sacks", desc = "Legitimately observed Sack of Sacks items.") @field:ConfigEditorBoolean @JvmField var sackOfSacks = true
    @field:Expose @field:ConfigOption(name = "Personal Vault", desc = "Cached Personal Vault contents.") @field:ConfigEditorBoolean @JvmField var vault = true
    @field:Expose @field:ConfigOption(name = "Forge", desc = "Cached Forge slots.") @field:ConfigEditorBoolean @JvmField var forge = true
    @field:Expose @field:ConfigOption(name = "Museum", desc = "Cached Museum contents.") @field:ConfigEditorBoolean @JvmField var museum = true
    @field:Expose @field:ConfigOption(name = "Island Chests", desc = "Legitimately opened private-island chests.") @field:ConfigEditorBoolean @JvmField var islandChests = true
    @field:Expose @field:ConfigOption(name = "Installed Parts", desc = "Installed drill and fishing-rod parts.") @field:ConfigEditorBoolean @JvmField var installedParts = true
}

class FeatureToggle(
    @field:Expose
    @field:ConfigOption(name = "Enabled", desc = "Enable this SkyHUD interface overhaul.")
    @field:ConfigEditorBoolean
    @JvmField
    var enabled: Boolean = true,
)
