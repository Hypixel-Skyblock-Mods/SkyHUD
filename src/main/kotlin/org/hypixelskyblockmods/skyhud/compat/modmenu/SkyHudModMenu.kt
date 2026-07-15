package org.hypixelskyblockmods.skyhud.compat.modmenu

import com.terraformersmc.modmenu.api.ConfigScreenFactory
import com.terraformersmc.modmenu.api.ModMenuApi
import org.hypixelskyblockmods.skyhud.config.SkyHudConfigManager

class SkyHudModMenu : ModMenuApi {
    override fun getModConfigScreenFactory(): ConfigScreenFactory<*> =
        ConfigScreenFactory { parent -> SkyHudConfigManager.createScreen(parent) }
}
