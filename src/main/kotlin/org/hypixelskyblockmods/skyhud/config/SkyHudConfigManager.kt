package org.hypixelskyblockmods.skyhud.config

import io.github.notenoughupdates.moulconfig.gui.GuiContext
import io.github.notenoughupdates.moulconfig.gui.GuiElementComponent
import io.github.notenoughupdates.moulconfig.managed.ManagedConfig
import io.github.notenoughupdates.moulconfig.platform.MoulConfigScreenComponent
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import org.hypixelskyblockmods.skyhud.platform.ScreenCompat
import org.hypixelskyblockmods.skyhud.update.SkyHudUpdateChecker

object SkyHudConfigManager {
    private lateinit var managed: ManagedConfig<SkyHudConfig>

    val config: SkyHudConfig
        get() = managed.instance

    fun initialize() {
        val file = FabricLoader.getInstance().configDir.resolve("skyhud.json").toFile()
        managed = ManagedConfig.create(file, SkyHudConfig::class.java) {
            customProcessor<ConfigVersionStatus> { option, _ -> VersionStatusEditor(option) }
        }
        save()
    }

    fun createScreen(parent: Screen?): Screen {
        SkyHudUpdateChecker.refresh()
        return object : MoulConfigScreenComponent(
            Component.literal("SkyHUD Settings"),
            GuiContext(GuiElementComponent(managed.getEditor())),
            parent,
        ) {
            override fun removed() {
                save()
                super.removed()
            }
        }
    }

    fun save() {
        if (::managed.isInitialized) managed.saveToFile()
    }

    fun open() {
        ScreenCompat.setScreen(createScreen(ScreenCompat.currentScreen()))
    }
}
