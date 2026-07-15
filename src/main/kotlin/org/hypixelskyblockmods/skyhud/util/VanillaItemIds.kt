package org.hypixelskyblockmods.skyhud.util

import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.item.ItemStack

object VanillaItemIds {
    fun isItem(stack: ItemStack, path: String): Boolean =
        !stack.isEmpty && BuiltInRegistries.ITEM.getKey(stack.item).path == path
}
