package org.hypixelskyblockmods.skyhud.feature.itemsearch

import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component

object ItemSearchNavigator {
    fun navigate(item: SearchableItem) {
        Minecraft.getInstance().player?.sendSystemMessage(Component.literal("[SkyHUD] ${item.location.label}"))
    }
}
