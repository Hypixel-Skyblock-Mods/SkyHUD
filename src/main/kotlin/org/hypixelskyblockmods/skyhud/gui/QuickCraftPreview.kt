package org.hypixelskyblockmods.skyhud.gui

import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.ItemStack

/** A display-only prediction; backing slots and the carried stack stay server-owned. */
internal data class QuickCraftPreview(val slots: Map<Int, ItemStack>, val carried: ItemStack) {
    companion object {
        fun create(carried: ItemStack, slots: List<Slot>, button: Int): QuickCraftPreview {
            if (carried.isEmpty || slots.isEmpty()) return QuickCraftPreview(emptyMap(), carried)
            val type = if (button == 0) 0 else 1
            val placeCount = AbstractContainerMenu.getQuickCraftPlaceCount(slots.size, type, carried)
            var remaining = carried.count
            val predicted = slots.associate { slot ->
                val existing = if (slot.item.isEmpty) 0 else slot.item.count
                val limit = minOf(carried.maxStackSize, slot.getMaxStackSize(carried))
                val added = minOf(placeCount, (limit - existing).coerceAtLeast(0), remaining)
                remaining -= added
                slot.index to carried.copyWithCount(existing + added)
            }
            return QuickCraftPreview(predicted, carried.copyWithCount(remaining))
        }
    }
}
