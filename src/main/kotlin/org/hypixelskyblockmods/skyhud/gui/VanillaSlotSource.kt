package org.hypixelskyblockmods.skyhud.gui

import net.minecraft.world.SimpleContainer
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.ItemStack

/** Keeps snapshots separate from live slots, including filtered empty menu items. */
internal class VanillaSlotSource {
    private val preview = PreviewSlot()

    fun resolve(stack: ItemStack, backingSlot: Slot?): Slot {
        if (backingSlot != null && ItemStack.matches(backingSlot.item, stack)) return backingSlot
        preview.stack = stack
        return preview
    }

    private class PreviewSlot : Slot(SimpleContainer(1), 0, 0, 0) {
        var stack: ItemStack = ItemStack.EMPTY

        // Avoid SimpleContainer.setItem: it can clamp counts and mutate the cached stack.
        override fun getItem(): ItemStack = stack
        override fun mayPlace(stack: ItemStack): Boolean = false
        override fun mayPickup(player: Player): Boolean = false
    }
}
