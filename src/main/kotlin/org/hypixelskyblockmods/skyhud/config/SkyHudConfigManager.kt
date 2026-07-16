package org.hypixelskyblockmods.skyhud.config

import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import io.github.notenoughupdates.moulconfig.gui.GuiContext
import io.github.notenoughupdates.moulconfig.gui.GuiElementComponent
import io.github.notenoughupdates.moulconfig.managed.ManagedConfig
import io.github.notenoughupdates.moulconfig.platform.MoulConfigScreenComponent
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import org.hypixelskyblockmods.skyhud.platform.ScreenCompat
import org.hypixelskyblockmods.skyhud.update.SkyHudUpdateChecker

object SkyHudConfigManager {
    private val gson = GsonBuilder().disableHtmlEscaping().create()
    private lateinit var managed: ManagedConfig<SkyHudConfig>

    val config: SkyHudConfig
        get() = managed.instance

    fun initialize() {
        val file = FabricLoader.getInstance().configDir.resolve("skyhud.json").toFile()
        migrateItemSearchCategory(file)
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

    private fun migrateItemSearchCategory(file: File) {
        if (!file.isFile) return
        val root = runCatching {
            file.reader().use(JsonParser::parseReader).asJsonObject
        }.getOrNull() ?: return
        val huds = root.getAsJsonObject("huds") ?: return
        val previous = huds.remove("itemSearch") ?: return
        if (!root.has("itemSearch")) root.add("itemSearch", previous)

        val target = file.toPath()
        val temporary = target.resolveSibling("${file.name}.tmp")
        runCatching {
            Files.writeString(temporary, gson.toJson(root))
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        }.recoverCatching {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING)
        }
    }
}
